# Test Sheet — FLOW-AI-CHAT

> 데모 시나리오: [`docs/Sprint2/Demo/DEMO_FLOW_AI_CHAT.md`](../DEMO_FLOW_AI_CHAT.md), [`docs/Sprint2/Demo/DEMO_REAL_INTEGRATED_FLOW.md`](../DEMO_REAL_INTEGRATED_FLOW.md)
> 사용법: 각 행의 "수행 절차"대로 실행 후 "기대 결과" 만족 여부를 **Pass / Fail** 표기. 실패 시 비고에 1~2줄 재현 메모 + GitHub Issue 링크.
>
> **Real / Mock 구분**:
> - `devDebug`: Firebase Live API 기반 실제 음성 대화 품질, AI 응답 재생, Correction handoff까지 확인한다.
> - `mockDebug`: `ChatDemoPresetConfig.activePreset`을 바꿔 화면 상태 전이, 장애, cleanup, handoff 신호를 재현한다.
> - `mockDebug`는 real AI 응답 품질 검증용이 아니다. preset 로그는 logcat `ChatMockPreset` 태그로 확인한다.

---

## 사전 준비

### Real 검증
- Android Studio Build Variant: `devDebug`
- Google 로그인 가능한 테스트 계정
- Firestore 사용자 초기 설정 완료 또는 신규 설정 가능 상태
- 마이크 권한 허용 가능 상태
- 네트워크 연결 정상
- 조용한 환경 또는 이어폰 권장

### Mock 검증
- Android Studio Build Variant: `mockDebug`
- preset 변경 위치:

```kotlin
// app/src/main/java/com/app/umma/data/repository/fake/demo/chat/ChatDemoPreset.kt
object ChatDemoPresetConfig {
    val activePreset: ChatDemoPreset = ChatDemoPreset.HandoffSuccess
}
```

- preset 변경 후 앱 재빌드/재실행
- logcat 필터: `ChatMockPreset`

---

## 커버되는 AC 체크리스트

> `[x]` = 본 테스트 시트에 포함됨 / `[ ]` = 본 시트에 없음(스코프 밖 또는 추후 보강).

### CHAT-001 진입 및 초기 상태
- [x] AC1: Dashboard에서 AI Chat 화면으로 진입할 수 있다.
- [x] AC2: 현재 선택된 학습 언어 기준으로 Chat 세션이 시작된다.
- [x] AC3: Chat 진입 중 Loading/Guard 상태가 표시된다.
- [x] AC4: 세션 준비 완료 후 대화 가능 상태가 된다.
- [x] AC5: 세션 시작 실패 시 Error/Retry 상태가 표시된다.

### CHAT-002 마이크 권한
- [x] AC1: 최초 PTT 입력 시 마이크 권한 요청이 표시된다.
- [x] AC2: 권한 허용 후 녹음을 시작할 수 있다.
- [x] AC3: 권한 거부 시 안내 메시지가 표시된다.
- [x] AC4: 영구 거부 시 설정 이동 CTA가 표시된다.

### CHAT-003 PTT 입력
- [x] AC1: PTT press 시 녹음 상태가 된다.
- [x] AC2: 사용자 발화 중 입력 애니메이션이 표시된다.
- [x] AC3: PTT release 시 녹음 상태와 입력 애니메이션이 종료된다.
- [x] AC4: 음성 frame이 Live session으로 전송된다.

### CHAT-004 중앙 시각화 / 자막 / 응답
- [x] AC1: 사용자 입력 중 input animation이 표시된다.
- [x] AC2: AI 음성 응답 중 output animation이 표시된다.
- [x] AC3: AI 음성 응답이 재생된다.
- [x] AC4: 자막 On 상태에서 마지막 확정 user/AI turn이 표시된다.
- [x] AC5: AI 응답 후 다시 대화 가능 상태로 돌아온다.

### CHAT-005 저장 및 Correction handoff
- [x] AC1: USER final transcript가 SessionMemory에 저장된다.
- [x] AC2: USER final turn 이후 `correctionAvailable=true` 신호가 전달된다.
- [x] AC3: Dashboard 복귀 후 교정 대기 카드가 교정 가능 상태로 표시된다.
- [x] AC4: Correction 화면 진입 가능 상태가 된다.
- [x] AC5: duplicate final turn은 중복 저장/중복 handoff되지 않는다.

### CHAT-006 장애 / 재연결 / cleanup
- [x] AC1: SessionInterrupted 발생 시 reconnecting 상태로 전환된다.
- [x] AC2: 자동 복구 성공 시 READY/IDLE로 복귀한다.
- [x] AC3: 복구 실패 시 retry 가능한 error UI가 표시된다.
- [x] AC4: fatal error는 non-recoverable error로 표시된다.
- [x] AC5: 녹음 중 장애가 발생해도 마이크가 정리된다.
- [x] AC6: 화면 이탈 시 녹음과 음성 재생이 즉시 정리된다.

---

## 테스트 시트

> Real 테스트를 먼저 수행해 실제 사용자 흐름을 확인하고, 이후 Mock 테스트로 재현 어려운 상태 전이를 검증한다.

| Test ID | 구분 | 시나리오 | 연결 AC | 사전 조건 | 수행 절차 | 기대 결과 | Pass/Fail | 비고 |
|---|---|---|---|---|---|---|---|---|
| TC-CH-01 | Real | Chat 진입 | CHAT-001 AC1·3·4 | `devDebug`, 로그인 완료, Dashboard 진입 상태 | Dashboard에서 AI 대화 카드 클릭 | Chat 화면 진입 → 진입 중 guard/loading 표시 → 세션 준비 완료 후 대화 가능 상태 | | |
| TC-CH-02 | Real | 학습 언어 반영 | CHAT-001 AC2 | `devDebug`, Dashboard에서 selectedLearningLanguage 확인 가능 | Dashboard 언어 selector로 학습 언어 변경 후 AI Chat 진입 | 변경된 학습 언어 기준으로 세션 시작. 실제 발화/응답이 해당 언어 컨텍스트를 따른다 | | |
| TC-CH-03 | Real | 관심 주제 다이얼로그 | CHAT-001 AC4 | `devDebug`, 관심 주제 미설정 계정 | Chat 최초 진입 | `Pick 5 Topics` 다이얼로그 표시 → 5개 선택 전 저장 불가 → 5개 선택 후 `Done` 가능 | | |
| TC-CH-04 | Real | 마이크 권한 허용 | CHAT-002 AC1·2 | `devDebug`, 앱 마이크 권한 미허용 상태 | PTT 버튼 클릭 → 권한 요청에서 허용 | 권한 요청 표시 후 허용하면 녹음 시작 가능 | | |
| TC-CH-05 | Real | 마이크 권한 거부 | CHAT-002 AC3 | `devDebug`, 앱 마이크 권한 미허용 상태 | PTT 버튼 클릭 → 권한 요청에서 거부 | 권한 필요 안내가 표시되고 녹음은 시작되지 않음 | | |
| TC-CH-06 | Real | 마이크 권한 영구 거부 | CHAT-002 AC4 | `devDebug`, Android 설정에서 마이크 권한 영구 거부 상태 | Chat 진입 후 PTT 버튼 클릭 | 권한 안내 + 설정 이동 CTA 표시 | | |
| TC-CH-07 | Real | PTT 입력 시작 | CHAT-003 AC1·2·4 / CHAT-004 AC1 | `devDebug`, 마이크 권한 허용, Chat READY | PTT 버튼 클릭 후 2~3초 발화 | 녹음 상태 표시, OS 마이크 indicator 표시, input animation 반응 | | |
| TC-CH-08 | Real | PTT 입력 종료 | CHAT-003 AC3 | TC-CH-07 진행 중 | PTT 버튼 다시 클릭 또는 release 동작 수행 | 녹음 상태 종료, input animation 정지, OS 마이크 indicator 내려감 | | |
| TC-CH-09 | Real | AI 음성 응답 | CHAT-004 AC2·3·5 | `devDebug`, 한 문장 이상 발화 완료 | PTT release 후 AI 응답 수신 대기 | AI 음성 응답 재생, output animation 표시, 응답 종료 후 Ready/IDLE 상태 복귀 | | |
| TC-CH-10 | Real | 자막 표시 | CHAT-004 AC4 | `devDebug`, user/AI 한 턴 이상 완료 | 자막 버튼 클릭 | 마지막 확정 User transcript와 Umma Tutor transcript 표시. 이전 partial은 남지 않음 | | |
| TC-CH-11 | Real | 1~2턴 연속 대화 | CHAT-004 AC5 / CHAT-005 AC1 | `devDebug`, 한 턴 완료 상태 | 같은 방식으로 1~2턴 추가 발화 | 각 턴 후 대화 가능 상태로 복귀하고 final transcript가 갱신됨 | | |
| TC-CH-12 | Real | Correction handoff | CHAT-005 AC1·2·3·4 | `devDebug`, USER final turn 1개 이상 완료 | Chat에서 Dashboard 복귀 → 교정 대기 카드 확인 → Correction 진입 | 교정 대기 카드가 교정 가능 상태로 표시되고 Correction 화면 진입 가능 | | |
| TC-CH-13 | Real | 화면 이탈 중 녹음 cleanup | CHAT-006 AC6 | `devDebug`, 녹음 진행 중 | 녹음 중 뒤로가기 또는 다른 화면 이동 | `stopChat()` 경로로 녹음 정리, OS 마이크 indicator 즉시 내려감, 재진입 시 녹음 상태 남지 않음 | | |
| TC-CH-14 | Real | 화면 이탈 중 재생 cleanup | CHAT-006 AC6 | `devDebug`, AI 음성 응답 재생 중 | 재생 중 뒤로가기 또는 다른 화면 이동 | 음성 재생 즉시 중지, 재진입 시 이전 재생 queue가 남지 않음 | | |
| TC-CH-15 | Mock | HandoffSuccess | CHAT-005 AC1·2·3·4 | `mockDebug`, `activePreset = HandoffSuccess`, logcat `ChatMockPreset` | Chat 진입 → 마이크 버튼 입력 → Dashboard/Correction 경로 확인 | user partial/final 표시, `final user turn emitted` 로그, 교정 가능 상태 반영 | | |
| TC-CH-16 | Mock | SaveSignalOnly | CHAT-005 AC2·3·4 | `mockDebug`, `activePreset = SaveSignalOnly`, logcat `ChatMockPreset` | Chat 진입 → 마이크 버튼 입력 → Dashboard/Correction 경로 확인 | partial 없이 final만 발생, `save_signal_only emitting final user turn for correction signal` 로그, 교정 가능 상태 반영 | | |
| TC-CH-17 | Mock | HandoffDuplicateFinal | CHAT-005 AC5 | `mockDebug`, `activePreset = HandoffDuplicateFinal`, logcat `ChatMockPreset` | Chat 진입 → 마이크 버튼 입력 | `handoff_duplicate_final emitting duplicate final events`, `duplicate final emitted`, `final user turn emitted` 로그 확인. 교정 가능 상태가 중복 없이 유지됨 | | |
| TC-CH-18 | Mock | SilentInputNoFinal | CHAT-005 AC1·2 | `mockDebug`, `activePreset = SilentInputNoFinal`, logcat `ChatMockPreset` | Chat 진입 → 마이크 버튼 입력 → Dashboard/Correction 경로 확인 | partial만 표시, `silent_input_no_final completed without final transcription` 로그, 새 교정 가능 신호 없음 | | |
| TC-CH-19 | Mock | ReconnectSuccess | CHAT-006 AC1·2 | `mockDebug`, `activePreset = ReconnectSuccess`, logcat `ChatMockPreset` | Chat READY 후 마이크 버튼 입력 | `SessionInterrupted -> Reconnected -> IDLE` 로그 확인. 화면은 READY/IDLE로 복귀 | | |
| TC-CH-20 | Mock | ReconnectFailed | CHAT-006 AC1·3 | `mockDebug`, `activePreset = ReconnectFailed`, logcat `ChatMockPreset` | Chat READY 후 마이크 버튼 입력 | retry 가능한 error UI 표시, `reconnect_failed emitted SessionInterrupted -> ReconnectFailed` 로그 확인 | | |
| TC-CH-21 | Mock | RecordingInterrupted | CHAT-006 AC5 | `mockDebug`, `activePreset = RecordingInterrupted`, logcat `ChatMockPreset` | Chat READY 후 마이크 버튼 입력 | 녹음 중단, retry 가능한 error UI 표시, OS 마이크 indicator 내려감, `recording_interrupted emitted LISTENING -> SessionInterrupted -> ReconnectFailed` 로그 확인 | | |
| TC-CH-22 | Mock | FatalError | CHAT-006 AC4 | `mockDebug`, `activePreset = FatalError`, logcat `ChatMockPreset` | Chat 진입 | non-recoverable error 상태 표시, `fatal_error emitted on startSession` 로그 확인 | | |

---

## 판정 기준

- Real happy path는 `devDebug`에서만 Pass 처리한다. `mockDebug`에서 AI 응답 품질, 실제 음성 인식 정확도, Firebase Live latency는 판정하지 않는다.
- `PTT release 후 AI 응답 대기 상태`는 별도 필수 판정 항목이 아니다. 현재 구현 기준은 release 직후 녹음 종료 확인, 이후 응답 수신 시 AI 음성/출력 애니메이션 확인이다.
- Correction end-to-end 생성 품질은 이 시트의 범위가 아니다. Chat 시트에서는 USER final 저장과 `correctionAvailable=true` handoff까지만 판정한다.
- 장애 preset은 실제 네트워크 장애를 만들지 않고 `AIEvent` 기반 ViewModel/UI 반응을 검증한다.
