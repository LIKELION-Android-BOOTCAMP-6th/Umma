# Demo Scenario - FLOW-WATCH-REMOTE-MIC

이 문서는 Umma Wear OS Companion V1의 `워치 remote mic 데모/QA 기준`이다.

V1 워치 정책은 다음을 전제로 한다.

- 워치는 `독립 AI 클라이언트`가 아니다.
- 세션 시작/종료, 인증, Realtime token, WebSocket transport, usage sync는 휴대폰이 담당한다.
- 워치는 `휴대폰 AI 대화에 attach/detach 되는 remote mic + 상태 UI` 역할만 담당한다.
- 워치의 주 입력은 PTT다.
- 워치에서는 SRS 학습, long transcript, 독립 review/session UX를 제공하지 않는다.
- 워치가 제공하는 핵심 기능은 `remote mic`, `응답 수신`, `상태 기반 오류 복구`다.

관련 문서:

- `docs/System_FlowDB/SYS_WATCH_INFRA.md`
- `docs/System_FlowDB/SYS_REALTIME_INFRA.md`
- `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-002_Stream.md`
- `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-004_Reconnect.md`
- `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- `app/src/main/java/com/app/umma/presentation/chat/ChatViewModel.kt`
- `wear/src/main/java/com/app/umma/wear/presentation/WearChatViewModel.kt`
- `wear/src/main/java/com/app/umma/wear/data/repository/WearChatRepositoryImpl.kt`

---

## 1. 실행 기준

- 폰 앱은 로그인된 상태여야 한다.
- 폰과 워치는 정상적으로 페어링되어 있어야 한다.
- 폰의 AI Chat 화면은 warm 상태여야 한다.
- 워치에서 마이크 권한이 허용되어 있어야 한다.
- 폰과 워치 사이 연결 경로는 사용 가능해야 한다.

데모에서 확인할 핵심은 아래 네 가지다.

- 워치 PTT 입력이 폰의 기존 AI Chat 파이프라인으로 정상 유입되는지
- 워치가 `Ready / Listening / Thinking / Speaking / Error` 상태를 명확히 보여주는지
- 업링크 실패, 네트워크 실패, Thinking stuck 상황에서 워치 UX가 복구 가능한지
- 워치가 휴대폰 세션의 companion remote mic로 동작하는지

---

## 2. 현재 데모 범위

- 포함:
  - 워치 연결 화면
  - 연결 가능한 휴대폰 검색
  - 특정 휴대폰 선택
  - 워치 attach / detach
  - on-screen PTT
  - 워치 오디오 업링크
  - 휴대폰 AI 응답 자동 재생
  - 상태 기반 대화 UI
  - 네트워크/연결 오류 문구
  - Thinking timeout 복구
- 제외:
  - watch direct cloud AI session
  - 워치 단독 SRS 학습
  - replay 버튼 기반 UX
  - recent history / expandable history
  - open-on-phone 최종 handoff UX
  - long transcript UI
  - media button / hardware key PTT

---

## 3. 데모 시나리오

### 시나리오 1 - 워치 연결 화면 진입

1. 워치 앱에서 AI Chat entry를 연다.
2. 연결 화면이 먼저 보이는지 확인한다.
3. 연결 가능한 휴대폰이 있으면 대상 정보가 보이는지 확인한다.
4. 휴대폰 연결 버튼이 노출되는지 확인한다.

기대 결과:

- 기본 진입 화면은 대화 화면이 아니라 연결 화면이다.
- 연결 전에는 안내 문구가 표시된다.
- 연결 가능한 대상이 없으면 연결 불가 문구가 표시된다.
- 여러 대가 있으면 대상 변경/선택 흐름이 가능하다.

---

### 시나리오 2 - 워치 attach 성공

1. 휴대폰에서 AI Chat을 warm 상태로 만든다.
2. 워치에서 휴대폰 연결 버튼을 누른다.
3. 연결 중 안내 문구가 표시되는지 확인한다.
4. attach 성공 후 대화 가능한 상태로 전환되는지 확인한다.

기대 결과:

- 연결 중에는 attach pending 상태가 표시된다.
- attach 성공 후 워치는 usable 상태가 된다.
- 대화 화면은 `Ready` 상태로 진입한다.
- PTT 버튼이 활성화된다.

---

### 시나리오 3 - PTT로 1턴 대화

1. 워치에서 PTT 버튼을 누른다.
2. 워치가 `Listening` 상태로 전환되는지 확인한다.
3. 사용자가 발화한다.
4. PTT를 종료한다.
5. 워치가 `Thinking` 상태로 전환되는지 확인한다.
6. AI 응답 수신 시 `Speaking` 상태로 전환되는지 확인한다.
7. 재생 종료 후 다시 `Ready`로 돌아오는지 확인한다.

기대 결과:

- 워치 오디오가 폰의 기존 AI Chat 파이프라인으로 전달된다.
- USER turn commit은 폰 세션에서 수행된다.
- 응답은 워치에서 자동 재생된다.
- 별도 replay 버튼 없이도 1턴 UX가 완결된다.

---

## 4. 예외 / 복구 시나리오

### 시나리오 4 - 폰 warm-up 전 attach 시도

1. 휴대폰에서 AI Chat을 열지 않은 상태로 둔다.
2. 워치에서 휴대폰 연결 버튼을 누른다.

기대 결과:

- attach는 성공하지 않는다.
- 워치에는 사용자 친화적인 안내 문구가 표시된다.
- raw 에러 메시지가 그대로 노출되지 않는다.

---

### 시나리오 5 - 네트워크 실패 상태

1. 휴대폰 네트워크를 끈다.
2. 워치에서 연결 시도 또는 응답 요청 흐름을 진행한다.

기대 결과:

- `Unable to resolve host ...` 같은 raw 에러가 그대로 보이지 않는다.
- 워치에는 네트워크 확인/재시도 문구가 노출된다.

---

### 시나리오 6 - 업링크 채널 종료 중 녹음 실패

1. 워치에서 녹음을 시작한다.
2. 녹음 도중 워치-폰 입력 채널이 끊기도록 만든다.
3. 워치 상태 변화를 확인한다.

기대 결과:

- 앱이 크래시하지 않는다.
- 녹음 상태가 정리된다.
- 워치에는 recoverable error 문구가 노출된다.
- 연결이 살아 있으면 바로 다시 PTT를 누를 수 있다.

---

### 시나리오 7 - Thinking stuck 복구

1. 워치에서 발화를 종료한다.
2. `Thinking` 상태 이후 응답 이벤트가 오지 않는 상황을 만든다.
3. 15초 이상 유지되는지 확인한다.

기대 결과:

- 워치 측 watchdog이 `Thinking` stuck를 감지한다.
- 워치가 복구 command를 시도한다.
- UI는 recoverable error로 전환된다.
- 사용자는 다시 PTT를 누를 수 있다.
- 늦게 도착한 stale `Thinking` 이벤트로 다시 상태가 꼬이지 않는다.

---

### 시나리오 8 - reconnect 성공

1. 대화 중 일시적인 연결 끊김을 만든다.
2. reconnect 성공 경로를 유도한다.
3. 워치 상태 복구를 확인한다.

기대 결과:

- 워치는 `Reconnecting` 또는 복구 가능한 상태를 표시한다.
- reconnect 성공 시 다시 usable 상태로 돌아온다.
- 이후 추가 PTT 입력이 가능하다.

---

### 시나리오 9 - reconnect 실패

1. 대화 중 연결 끊김을 만든다.
2. reconnect가 실패하도록 유도한다.

기대 결과:

- direct cloud fallback은 발생하지 않는다.
- 워치에는 오류 상태와 재시도 가능한 UX가 노출된다.

---

### 시나리오 10 - detach / 연결 해제

1. 워치가 attach된 상태를 만든다.
2. 워치에서 연결 해제 버튼을 누른다.

기대 결과:

- 워치는 즉시 연결 전 초기 화면으로 돌아간다.
- 이전 연결 성공 상태 문구가 남지 않는다.
- PTT는 비활성화된다.

---

## 5. 데모 체크포인트

| Test ID | 확인 항목 | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-WM-01 | 연결 화면 진입 | 워치 앱 진입 | 연결 안내 화면 노출 |  |  |
| TC-WM-02 | attach 성공 | 휴대폰 warm 상태 후 연결 버튼 | Ready 상태 진입 |  |  |
| TC-WM-03 | PTT 1턴 | press -> speak -> release | Listening -> Thinking -> Speaking/Ready |  |  |
| TC-WM-04 | warm-up 미충족 | 폰 chat 미오픈 상태에서 attach | 안내 문구 노출, raw 에러 미노출 |  |  |
| TC-WM-05 | 네트워크 실패 | 폰 네트워크 off | 사용자 친화적 오류 문구 노출 |  |  |
| TC-WM-06 | 업링크 실패 복구 | 녹음 중 채널 종료 | 크래시 없이 오류 복구 |  |  |
| TC-WM-07 | Thinking timeout | 응답 이벤트 미도착 유도 | 15초 후 recoverable error 전환 |  |  |
| TC-WM-08 | reconnect 성공 | 일시 끊김 후 복구 | usable 상태 복구 |  |  |
| TC-WM-09 | reconnect 실패 | 복구 실패 유도 | fallback 없이 오류 UX 유지 |  |  |
| TC-WM-10 | detach | 연결 해제 버튼 | 초기 연결 화면으로 복귀 |  |  |

---

## 6. 합격 기준

- 워치 PTT 입력이 폰의 기존 AI Chat 파이프라인으로 정상 연결된다.
- 워치 응답은 자동 재생되며, 1턴 대화 UX가 자연스럽다.
- 연결 전/연결 중/Listening/Thinking/Speaking/Error 상태가 명확히 구분된다.
- raw 네트워크/호스트 오류가 그대로 노출되지 않는다.
- 업링크 실패나 Thinking stuck 상황에서도 앱이 멈추거나 크래시하지 않는다.
- direct watch session이나 direct cloud fallback 없이 companion remote mic 모델을 유지한다.
