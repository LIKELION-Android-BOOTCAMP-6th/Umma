# Demo Scenario — FLOW-AI-CHAT

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름. 사용자 여정 관점으로 묶어 애자일 스프린트 작업 배정 단위로도 사용.
> 이슈 단위(CHAT-001~008) AC 체크리스트는 `docs/User_FlowDB/FLOW_AI_CHAT/` 의 이슈 문서를 참조한다.
>
> 각 시나리오 = 한 명(또는 한 페어)이 스프린트 안에 완결할 수 있는 유저 가치 한 덩어리.
> 시나리오는 의존성 순서로 배열되어 있어 위에서 아래로 차곡차곡 쌓아 올릴 수 있다.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: AI Chat 카드까지 진입 가능한 시드 계정 (Initial Setup 완료 + `selectedLearningLanguage` 존재)
> - Mock 토글: `ChatRepository` / `RealtimeRepository` fake 구현 — fixture 가 partial / final transcript 와 `AIEvent.StateChanged` 를 시뮬레이션 (현재 미존재, DASH 스타일 `RepositoryModule` 토글 추가 필요)
> - Mock fixture 가 필요한 분기: 시나리오 5(견고화) 의 모든 분기, 시나리오 1·3·4 의 실패 분기. happy path 는 Real 로 시연
> - 발표 후 Real 바인딩으로 원복

---

## 시나리오 1 — 한 번 말하고 한 번 듣기 (단일 턴 MVP)

> **무엇을 하는가** — AI Chat 화면에 들어가서 마이크 버튼을 한 번 눌러 말하면, AI가 음성으로 한 번 답해준다.
> **유저 가치** — "이 앱이랑 진짜 말이 된다"를 사용자가 처음 체감하는 가장 작은 단위. 이게 동작하지 않으면 뒤 시나리오는 의미가 없다.

**포함 이슈**
- `CHAT-001` — AI Chat 진입 + 초기 상태 구성 (selectedLearningLanguage / LangState 로드, 자막 Off 기본값)
- `CHAT-002` — 마이크 권한 확인 + PTT 버튼 활성화
- `CHAT-003` — Push-to-Talk 음성 입력 (press / release)
- `CHAT-004` — 중앙 비주얼 피드백 (Idle / Recording / Speaking)
- `CHAT-005` — AI 응답 음성 출력 (Thinking → Speaking → Ready)

**의존성**: 없음. FLOW-AI-CHAT에서 가장 먼저 만들 수 있는 단위.
**예상 규모**: L — 마이크 캡처 / Firebase Live API 연결 / AI 응답 재생이 한 사이클로 돌아야 하므로 RT-001 · RT-002 인프라까지 함께 붙어야 한다.

**데모 흐름**
1. Dashboard에서 AI 대화 카드 클릭 → AI Chat 화면 진입 (Loading)
2. 진입 직후 자막 Off / 중앙 비주얼 Idle / PTT 버튼 대기 상태 확인
3. PTT 버튼 첫 탭 → OS 마이크 권한 다이얼로그 → "허용" 선택
4. PTT press → 중앙 비주얼이 Recording 으로 전환되고, 음성 입력 강도(0.0~1.0)가 비주얼에 반영됨
5. PTT release → AI 응답 대기 (Thinking) → AI 음성 출력 (Speaking) → Idle 복귀

**핵심 분기**
- **성공**: 진입 → 권한 허용 → PTT → AI 응답 → Idle 의 한 사이클이 깨끗하게 닫힌다.
- **권한 거부 (`CHAT-002` Error)**: PTT 버튼이 비활성화되고, 권한 안내 UI + 시스템 설정 진입 경로가 제공된다. 앱은 크래시하지 않는다.
- **응답 실패 (`CHAT-005` Error)**: Error 상태 + Retry 버튼. 재시도 시 같은 turn 기준으로 다시 요청.

---

## 시나리오 2 — 여러 턴 이어가며 자막 보기

> **무엇을 하는가** — 한 세션 안에서 사용자가 여러 턴 대화를 이어가고, 필요할 때 자막을 켜서 마지막 확정 턴 한 줄을 본다.
> **유저 가치** — 1회성 데모를 넘어 "실제 대화가 흘러간다"는 체감을 만든다. 자막은 보조 도구이지 누적 로그가 아니다.

**포함 이슈**
- `CHAT-005` — AI 응답 출력 반복 (turn 누적)
- `CHAT-006` — 자막 토글 + 마지막 확정 턴만 표시 (partial transcript 누적 금지)

**의존성**: 시나리오 1
**예상 규모**: S~M — 단일 턴 사이클이 안정적이라면 자막 표시 정책과 토글 UI 추가 정도.

**데모 흐름**
1. 시나리오 1 종료 후 자막이 Off 인 기본 상태 확인
2. PTT 로 추가 2~3 턴 대화. 매 턴마다 Recording → Speaking → Idle 사이클이 흔들림 없이 동작
3. 자막 토글 On → 마지막 확정 턴(user 또는 assistant) 한 줄만 표시
4. 다음 턴 진행 → 자막이 새 마지막 턴으로 교체 (직전 턴은 사라짐, 누적되지 않음)
5. 자막 Off → 자막 영역 숨김. 다시 On → 직전 마지막 자막 복원

**핵심 분기**
- **성공**: 자막은 항상 직전 확정 턴 1개만 노출. partial transcript 가 화면에 누적되지 않는다.
- **자막 지연 (`CHAT-006` Edge)**: final transcript 가 늦게 와도 화면이 깜빡이지 않고, 도착 전까지 이전 마지막 자막을 유지한다.
- **빠른 토글 반복 (`CHAT-006` Edge)**: 토글을 빠르게 켰다/껐다 해도 화면이 흔들리거나 재구성되지 않는다.

---

## 시나리오 3 — 대화 내용이 다음 학습으로 이어지도록 저장

> **무엇을 하는가** — 확정된 user / assistant turn 을 Session Memory 에 저장해서, AI Chat 을 떠난 뒤에도 Correction 흐름에서 같은 대화 맥락을 다시 쓸 수 있게 한다.
> **유저 가치** — AI Chat → Correction 으로 이어지는 핵심 다리. 이게 없으면 교정 입력 자체가 없다.

**포함 이슈**
- `CHAT-007` — 확정 turn 저장 연동 (final user / final assistant turn 만 저장, partial transcript 필터링)

**의존성**: 시나리오 2. 동시에 RT-003 (Session Memory append) 과 SYS-LEARNING-STATE-INFRA 의 계약이 준비되어 있어야 함.
**예상 규모**: M — UI 작업보다 인프라 계약과의 정확한 연결이 핵심.

**데모 흐름**
1. 시나리오 2 흐름으로 user / assistant 확정 턴 2~3개 누적
2. logcat 또는 디버그 영역에서 턴이 끝날 때마다 `append user` / `append assistant` 로그가 1회씩 찍히는지 확인
3. partial transcript 이벤트는 저장 호출 흐름에 흘러들지 않는 것 확인
4. AI Chat 종료 후 Correction 진입 시 동일 turn 맥락이 후보 추출 입력으로 보이는지 후속 시나리오에서 확인

**핵심 분기**
- **성공**: final user / final assistant turn 만 정확히 저장되고, Correction 의 `recentFullContext` 가 채워진다.
- **저장 실패 (`CHAT-007` PendingSync)**: 화면은 크래시 없이 정상 동작하고, 해당 turn 은 local pending 상태로 남는다.
- **같은 turn 중복 도착 (`CHAT-007` Edge)**: assistant final turn 이 중복으로 와도 두 번 저장되지 않는다.

---

## 시나리오 4 — 잠시 끊고 돌아와도 이어 쓰기

> **무엇을 하는가** — 사용자가 AI Chat 을 떠났다가 다시 들어오거나, 백그라운드에 갔다가 돌아왔을 때 안전하게 정리되고 자연스럽게 재개된다.
> **유저 가치** — 일상에서는 알람·전화·다른 앱으로 화면을 떠나는 일이 흔하다. 이걸 못 견디면 실제 사용에서 신뢰가 깨진다.

**포함 이슈**
- `CHAT-008` — 종료 시 녹음/재생 정리, 재진입 시 앱 상태 복원 또는 새 LiveSession 전환, 저장 중 상태 보호

**의존성**: 시나리오 3 (저장 흐름이 있어야 "저장 중 이탈" 보호가 의미를 가짐)
**예상 규모**: M — 종료 cleanup 과 복원 로직, 새 LiveSession 전환을 분리해서 다뤄야 한다.

**데모 흐름**
1. 시나리오 2~3 흐름을 진행 중 뒤로가기로 화면 이탈 → 녹음과 AI 음성 재생이 즉시 중지되는지 확인 (`cleanup` 로그)
2. Dashboard 경유 후 다시 AI Chat 진입
3. 같은 선택 언어 기준으로 앱 상태 복원 시도 → 직전 자막 / turn 컨텍스트가 자연스럽게 이어지는지 확인 (Success)
4. 네트워크 변동 등으로 복원이 어려운 상황 시뮬레이션 → 새 LiveSession 으로 자연스럽게 전환 (Fallback)
5. 진행 중이던 final turn 저장 요청이 누락되거나 화면을 깨뜨리지 않는지 확인

**핵심 분기**
- **성공**: 복원 OK 또는 새 세션 OK — 사용자 입장에선 어느 쪽이든 자연스러운 재개로 보인다.
- **복구 실패 반복 (`CHAT-008` Edge)**: 재연결 실패가 반복되더라도 앱이 멈추지 않고 안전한 fallback 안내가 유지된다.
- **저장 중 이탈 (`CHAT-007` × `CHAT-008`)**: 저장이 끝나거나 pending 으로 보호된 뒤에야 화면이 정리된다.

---

## 시나리오 5 — 외부 조건이 망가져도 앱이 안 깨짐 (견고화 패스)

> **무엇을 하는가** — 권한 영구 거부 / 네트워크 끊김 / 언어 컨텍스트 누락 / 응답 실패 / 저장 실패 같은 외부 문제 상황 전반을 견고하게 처리한다.
> **유저 가치** — happy path 만 있는 데모를 넘어 "실패해도 안 깨지는 앱" 이라는 기본 신뢰를 만든다. 분기별로 작게 나눠 분산 작업 가능.

**포함 이슈**
- `CHAT-001` Error — selectedLearningLanguage 가 없을 때 안전한 Error 화면
- `CHAT-002` Error — 마이크 권한 영구 거부 시 안내 + 시스템 설정 진입 경로
- `CHAT-005` Error / Retry — AI 응답 실패 시 Error 상태와 같은 turn 기준 재시도
- `CHAT-007` PendingSync — 저장 실패 시 local pending 보호와 화면 크래시 방지

**의존성**: 시나리오 1~4 의 happy path 가 먼저 깔려 있어야 의미를 가진다.
**예상 규모**: S~M — 이슈별로 분산해서 작업 가능. 페어 한 명이 한두 분기를 잡는 식으로 쪼개기 좋다.

**데모 흐름**
1. selectedLearningLanguage 가 없는 상태로 진입 → Error 안내 + Dashboard 복귀 경로 표시
2. 마이크 권한이 영구 거부된 상태에서 PTT 시도 → 권한 안내 UI + 시스템 설정 진입 경로 노출
3. AI 응답 진행 도중 네트워크를 끊음 → Error 상태 + 재시도 액션, 재시도 성공 시 정상 복구
4. final turn 저장이 실패하는 상태로 진입 → 화면은 정상 동작하고, turn 은 local pending 으로 남음

**핵심 분기**
- **1차 합격선**: 모든 외부 실패가 앱 크래시로 이어지지 않는다.
- **2차 합격선**: 각 실패 상황에서 사용자가 다음에 무엇을 할 수 있는지(Retry / 설정 이동 / 새 세션 / 닫기)가 화면에서 명확히 보인다.
