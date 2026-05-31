# [PoC] CHAT-POC-001 OpenAI Realtime Push-to-talk 검증

## User Story

AI Chat 엔진을 전환하기 전에, `gpt-realtime-mini`가 MVP에서 필요한 수동 turn 제어와 사용자/AI 자막 분리 요구사항을 실제 Android 앱 구조에서 만족하는지 확인해야 한다.

이번 작업은 production 전환이 아니라 기술 검증이다. 당시에는 기존 Firebase AI Logic 구현을 유지하고 OpenAI Realtime을 별도 PoC 경로로 연결해 전환 가능성과 구조 변경 범위를 판단했다.

후속 `CHAT-ENGINE-001`에서 PoC 결과를 바탕으로 AI Chat realtime transport는 OpenAI Realtime 단일 경로로 전환되었다. 이 문서는 PoC 당시의 판단 근거와 측정 기준을 보존한다.

---

## 완료 기준(AC)

- [ ] `gpt-realtime-mini`로 Realtime 세션을 시작할 수 있다.
- [ ] 첫 번째 마이크 버튼 이후 사용자 오디오를 전송할 수 있다.
- [ ] 두 번째 마이크 버튼 전까지 AI 응답이 시작되지 않는다.
- [ ] 두 번째 마이크 버튼에서 `input_audio_buffer.commit`으로 사용자 발화를 확정할 수 있다.
- [ ] 사용자 발화 종료 후 사용자 transcript를 받을 수 있다.
- [ ] 사용자 transcript 수신 후 `response.create`로 AI 응답을 시작할 수 있다.
- [ ] AI 음성 응답 중 AI transcript delta를 받을 수 있다.
- [ ] 사용자 transcript와 AI transcript가 이벤트 레벨에서 구분된다.
- [ ] 첫 AI 음성 응답까지의 지연시간과 usage를 확인할 수 있다.
- [ ] PoC 결과를 기준으로 OpenAI 전환 여부와 `CHAT-FIX-001-C`, `CHAT-FIX-001-D` 구현 방향을 결정할 수 있다.

---

## 기준 문서

- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- 마이크 버튼 상태 UX: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-C_Mic_Button_State_UX.md`
- final 자막 대화형 표시: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-D_Final_Subtitle_Conversation_UX.md`
- OpenAI Realtime conversations: https://developers.openai.com/api/docs/guides/realtime-conversations
- OpenAI Realtime cost guide: https://developers.openai.com/api/docs/guides/realtime-costs
- OpenAI pricing: https://developers.openai.com/api/docs/pricing

---

## 범위

### 포함

- OpenAI Realtime 최소 연결 경로 구성
- `gpt-realtime-mini` 기준 수동 push-to-talk 흐름 검증
- 사용자 오디오 append / commit / response 생성 흐름 검증
- 사용자 transcript와 AI transcript delta 도착 시점 확인
- 첫 AI audio 지연시간과 usage 로그 확인
- 현재 `ChatRepository` / `AIEvent` 구조 아래로 매핑 가능한지 검토

### 제외

- 기존 Firebase AI Logic 구현 제거
- OpenAI Realtime production 전환 확정
- 운영용 token 발급 서버 완성
- LangState prompt tuning 구현
- SessionMemory 저장 정책 변경
- correctionAvailable 신호 정책 변경

---

## PoC 기준 동작

```text
AI Chat PoC 진입
→ OpenAI Realtime 세션 시작
→ 마이크 버튼 첫 클릭
→ 사용자 오디오 append 시작
→ 사용자 발화
→ 마이크 버튼 두 번째 클릭
→ input_audio_buffer.commit
→ 사용자 transcript 수신
→ response.create
→ AI audio response 수신
→ AI transcript delta 수신
→ response.done에서 usage 확인
```

---

## 측정 항목

| 항목 | 확인 이유 |
| --- | --- |
| 두 번째 버튼 전 AI 응답 여부 | toggle-to-talk에서 사용자 종료 입력 전 AI가 끼어들지 않는지 확인 |
| response.create 후 첫 AI audio 지연 | 실제 대화 UX가 데모 가능한 수준인지 확인 |
| 사용자 transcript 도착 시점 | 사용자 발화 종료 후 자막을 먼저 표시할 수 있는지 확인 |
| AI transcript delta 도착 시점 | AI가 말하는 동안 자막을 누적 표시할 수 있는지 확인 |
| transcript role 구분 | user / AI 자막을 안정적으로 분리할 수 있는지 확인 |
| usage 로그 | 실제 비용 추정이 가능한지 확인 |

---

## 결과 판정 기준

| 결과 | 조건 | 후속 결정 |
| --- | --- | --- |
| A. 전환 후보 | 수동 turn 제어, 자막 분리, 지연시간, usage 확인이 모두 가능 | `CHAT-FIX-001-C`, `CHAT-FIX-001-D`를 OpenAI Realtime 기준으로 설계 |
| B. 조건부 가능 | 핵심 UX는 가능하지만 token 발급, event 매핑, 오디오 포맷 변환 등 구조 보강이 필요 | 구조 보강 이슈를 먼저 만들고 전환 여부 재검토 |
| C. 보류 | 지연이 크거나 자막 순서가 불안정하거나 현재 구조와 매핑 난이도가 큼 | OpenAI 전환 보류, Firebase 유지 또는 다른 PoC 검토 |

---

## 구현 메모

- 기존 `ChatRepositoryImpl`은 유지한다.
- PoC 구현은 별도 repository 구현체 또는 별도 variant/module binding으로 분리한다.
- OpenAI 서버 이벤트는 가능한 한 기존 `AIEvent`로 매핑해 현재 ViewModel 구조와의 적합성을 확인한다.
- Android 앱에 OpenAI API key를 직접 포함하지 않는다.
- PoC에서 필요한 token 발급/연결 경로는 최소 수준으로 준비하되, 운영용 보안/배포 구성은 별도 작업으로 분리한다.

### 보안 임시 설정 주의

OpenAI / Firebase 양쪽 모두 PoC 접근성을 우선해 임시 설정이 포함되어 있다. 아래 항목은 PoC 종료 후 반드시 재검토한다.

#### OpenAI

- API key는 PoC 속도를 위해 개인 사용자 소유(`You`)로 생성했다.
- API key 권한은 초기 연결 오류를 줄이기 위해 `All`로 생성했다.
- 운영 또는 장기 테스트 전에는 팀/서비스 계정 소유 key로 교체하고, 가능한 범위에서 권한을 제한한다.
- 터미널에 잘못 노출된 key는 폐기하고 새 key를 발급했다. 같은 사고가 다시 발생하면 즉시 revoke 후 재발급한다.
- 결제는 PoC용으로 최소 크레딧과 월 한도를 설정했다. 장기 테스트 전에는 팀 비용 정책과 사용량 모니터링 기준을 다시 확인한다.

#### Firebase / Cloud Functions

- `realtimeToken` Cloud Function의 공개 액세스 허용은 PoC 중 실제 Android 앱 호출을 확인하기 위한 임시 설정이다.
- 공개 액세스 상태에서는 URL을 아는 사용자가 token 발급 함수를 호출할 수 있으므로 장시간 방치하지 않는다.
- PoC 테스트가 끝나면 Cloud Run / Cloud Functions 보안 설정을 다시 `인증 필요`로 되돌린다.
- 운영 또는 장기 테스트 전에는 Firebase Auth ID token 검증, App Check, userId별 rate limit 중 최소 하나 이상을 적용한다.
- 이 보안 보강은 `CHAT-POC-001`의 production 전환 범위가 아니라 후속 이슈로 분리한다.

### 실행 설정

아래 설정은 PoC 당시 dev 환경에서 OpenAI 경로를 선택하기 위한 기록이다.
`CHAT-ENGINE-001` 이후에는 `OPENAI_REALTIME_ENABLED` 플래그를 사용하지 않고, dev AI Chat은 OpenAI Realtime 경로를 기본으로 사용한다.

```properties
OPENAI_REALTIME_TOKEN_URL=https://your-dev-token-endpoint.example.com/realtime-token
OPENAI_REALTIME_MODEL=gpt-realtime-mini
```

---

## 검증 기준

- 두 번째 버튼 전 AI audio/transcript 이벤트가 발생하지 않는다.
- 두 번째 버튼 이후에만 AI audio response가 시작된다.
- 사용자 transcript와 AI transcript가 역할별로 분리되어 수신된다.
- 사용자 transcript를 AI transcript보다 먼저 표시할 수 있는지 판단 가능하다.
- AI transcript delta를 “말하는 중 자막”처럼 표시할 수 있는지 판단 가능하다.
- response 완료 시 usage를 확인할 수 있다.
- PoC 결과를 A/B/C 중 하나로 정리할 수 있다.
