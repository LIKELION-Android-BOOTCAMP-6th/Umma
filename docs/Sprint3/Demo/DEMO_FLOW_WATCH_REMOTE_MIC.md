# Demo Scenario - FLOW-WATCH-REMOTE-MIC

이 문서는 Umma Wear OS Companion V1의 `워치 원격 마이크 AI 대화 데모/QA 기준`이다.

V1 원격 마이크 정책은 다음을 전제로 한다.

- 워치는 `독립 AI 클라이언트`가 아니다.
- 폰이 AI 세션, 인증, Realtime token 발급, WebSocket transport, usage sync, Session Memory의 유일한 소유자다.
- 워치는 `원격 마이크 + 경량 채팅 UI + PTT 입력 장치`만 담당한다.
- 워치 대화 중 폰 화면이 꺼져도 active chat은 유지되며, 이를 위해 활성 대화 동안만 폰 Foreground Service를 사용한다.

관련 문서:

- `docs/System_FlowDB/SYS_WATCH_INFRA.md`
- `docs/System_FlowDB/SYS_REALTIME_INFRA.md`
- `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-002_Stream.md`
- `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-004_Reconnect.md`
- `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- `app/src/main/java/com/app/umma/presentation/chat/ChatViewModel.kt`
- `app/src/main/java/com/app/umma/data/repository/ChatRepositoryImpl.kt`

---

## 1. 실행 기준

- 폰 앱은 로그인 상태여야 한다.
- 폰과 워치는 정상 페어링되어 있어야 한다.
- 폰은 인터넷 연결 가능 상태여야 한다.
- 워치와 폰 사이 브릿지 연결이 가능해야 한다.
- 폰 마이크 권한 및 알림 권한은 허용 상태여야 한다.

데모에서 확인할 핵심은 아래 네 가지다.

- 워치 PTT 입력이 폰의 기존 AI Chat 파이프라인으로 흘러가는지
- 폰 화면 off 상태에서도 active chat이 유지되는지
- 워치 UI가 `audio-first + expandable history` 정책을 지키는지
- 연결 실패/복구가 direct cloud fallback 없이 처리되는지

---

## 2. 현재 데모 범위

- 포함:
  - 워치 채팅 진입
  - 물리 버튼 또는 화면 PTT
  - 워치 오디오 업로드
  - 폰 AI 세션 응답
  - replay
  - 최근 이력 펼치기
  - open-on-phone handoff
- 제외:
  - watch direct cloud AI session
  - partial subtitle 메인 노출
  - 이어폰 media button PTT
  - AI 음성 자동 재생
  - long transcript UI

---

## 3. 기본 데모 시나리오

### 시나리오 1 - 워치에서 AI Chat 진입

1. 워치 앱에서 AI Chat entry를 연다.
2. 폰 연결 상태를 확인한다.
3. 워치 채팅 화면이 `Ready` 또는 `Connecting`에서 정상 전이되는지 확인한다.

기대 결과:

- 브릿지 연결이 가능하면 워치가 usable 상태가 된다.
- 폰이 세션 준비를 담당하고, 워치는 UI 상태만 렌더링한다.

---

### 시나리오 2 - PTT로 1턴 대화 완료

1. 워치에서 PTT를 누른다.
2. 워치가 `Listening/Recording` 상태를 보여주는지 확인한다.
3. 사용자가 짧게 발화한다.
4. PTT를 놓는다.
5. 워치가 `Thinking` 상태로 전환되는지 확인한다.
6. AI 응답이 준비되면 `Speaking` 또는 replay 가능 상태가 되는지 확인한다.
7. 사용자가 필요하면 replay를 탭한다.

기대 결과:

- 워치 오디오 frame이 폰으로 전달된다.
- 폰이 기존 Realtime session에 음성을 전달한다.
- USER turn commit은 폰에서 수행된다.
- 워치에서는 partial subtitle이 아니라 상태 중심 피드백이 보인다.

---

### 시나리오 3 - 최근 이력 펼치기

1. 1턴 이상의 대화를 완료한다.
2. 워치에서 history 펼치기 버튼을 누른다.
3. 최근 finalized turn이 표시되는지 확인한다.
4. 다시 접기 버튼으로 history를 닫는다.

기대 결과:

- 기본은 접힘 상태다.
- 펼쳤을 때 최근 4개 turn까지만 보인다.
- 각 turn 텍스트는 160자 이내로 절삭된다.
- partial transcript는 표시되지 않는다.

---

### 시나리오 4 - Open on Phone handoff

1. 워치에서 1~2턴 대화를 진행한다.
2. 사용자가 `폰에서 이어서 하기`를 누른다.
3. 폰이 AI Chat route로 열리는지 확인한다.
4. 기존 app session이 이어지는지 확인한다.

기대 결과:

- 워치와 폰이 서로 다른 세션을 두 개 만들지 않는다.
- 동일 app session을 기준으로 폰 화면으로 handoff된다.

---

## 4. 백그라운드/화면 꺼짐 시나리오

### 시나리오 5 - 폰 화면 off 상태에서 active chat 유지

1. 워치에서 AI Chat을 시작한다.
2. PTT로 한 턴을 진행하는 도중 또는 AI 응답 대기 중에 폰 화면을 끈다.
3. 워치에서 상태 변화가 유지되는지 확인한다.
4. AI 응답이 끝날 때까지 세션이 끊기지 않는지 확인한다.

기대 결과:

- 폰 Foreground Service가 active chat 동안 유지된다.
- 폰 화면이 꺼져도 워치 대화는 지속된다.
- 세션은 direct watch session이 아니라 phone-owned session으로 유지된다.

---

### 시나리오 6 - idle timeout 종료

1. 워치에서 한 턴 대화를 완료한다.
2. 마지막 AI 응답 이후 추가 입력 없이 대기한다.
3. 30초 유휴 이후 세션 및 서비스가 내려가는지 확인한다.
4. 이후 다시 PTT를 누르면 새 active 상태로 복귀 가능한지 확인한다.

기대 결과:

- active chat이 끝나면 서비스가 상시 남지 않는다.
- idle timeout 이후 자원 정리가 수행된다.
- 다음 대화는 정상적으로 재시작 가능하다.

---

## 5. 예외 시나리오

### 시나리오 7 - 폰 연결 끊김

1. 워치 대화 중 폰 연결을 끊는다.
2. 워치가 `RecoverableError` 또는 연결 끊김 상태를 보여주는지 확인한다.
3. direct cloud fallback이 발생하지 않는지 확인한다.

기대 결과:

- direct AI fallback 없이 안전하게 실패한다.
- 워치에는 재시도 또는 open-on-phone CTA가 제공된다.

---

### 시나리오 8 - reconnect 성공

1. 워치 대화 중 일시적인 연결 끊김을 유발한다.
2. 폰 reconnect가 성공하는지 확인한다.
3. 워치가 다시 `Ready/Idle` 또는 usable 상태로 복구되는지 확인한다.

기대 결과:

- 세션 복구가 가능하면 워치 UI가 자연스럽게 회복된다.
- 복구 이후 추가 PTT 입력이 가능하다.

---

### 시나리오 9 - replay 가능한 AI audio 없음

1. AI 응답은 완료됐지만 watch replay cache가 없는 상태를 만든다.
2. 워치 화면에서 replay 버튼 노출 상태를 확인한다.

기대 결과:

- replay 가능한 오디오가 없으면 버튼이 숨겨지거나 비활성화된다.
- UI가 잘못된 재생 상태로 보이지 않는다.

---

### 시나리오 10 - privacy 제한

1. 워치에서 history를 펼친다.
2. lock 상태 또는 민감도 제한 상태를 가정한다.
3. full transcript가 그대로 노출되지 않는지 확인한다.

기대 결과:

- partial transcript는 보이지 않는다.
- 민감 상황에서는 요약/절삭 정책이 우선 적용된다.

---

## 6. 데모 체크포인트

| Test ID | 확인 항목 | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-WM-01 | 워치 채팅 진입 | 워치 앱에서 AI Chat 오픈 | Ready 또는 Connecting 진입 | | |
| TC-WM-02 | PTT 1턴 대화 | press -> speak -> release | Listening -> Thinking -> Speaking/Replay 상태 전이 | | |
| TC-WM-03 | 이력 펼치기 | history expand | 최근 finalized turn 최대 4개 표시 | | |
| TC-WM-04 | Open on Phone | handoff 버튼 탭 | 폰이 동일 app session 기준으로 열림 | | |
| TC-WM-05 | 화면 off 지속 | 대화 중 폰 화면 off | active chat 유지 | | |
| TC-WM-06 | idle timeout | 대화 종료 후 대기 | 30초 후 서비스/세션 정리 | | |
| TC-WM-07 | 연결 끊김 | 폰 연결 해제 | direct fallback 없이 복구 안내 | | |
| TC-WM-08 | reconnect 성공 | 일시 끊김 후 복구 | usable 상태 복귀 | | |
| TC-WM-09 | replay 없음 | 오디오 캐시 없음 상태 | replay 버튼 숨김/비활성 | | |
| TC-WM-10 | privacy 제한 | 민감 상태에서 history 확인 | full transcript 미노출 | | |

---

## 7. 합격 기준

- 워치 입력이 폰의 기존 AI Chat 파이프라인으로 안정적으로 연결된다.
- active chat 동안 폰 화면이 꺼져도 대화가 끊기지 않는다.
- 워치 UI는 상태 중심이고, partial subtitle을 메인 화면에 노출하지 않는다.
- 이력은 finalized turn만 제한적으로 표시된다.
- 연결 끊김, replay 없음, privacy 제한 같은 예외 상황에서도 UX가 무너지지 않는다.

