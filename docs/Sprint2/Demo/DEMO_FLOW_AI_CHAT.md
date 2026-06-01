# Demo Scenario - FLOW-AI-CHAT

이 문서는 AI Chat 화면을 데모/QA하는 기준이다.

현재 Chat 검증은 두 갈래로 나눈다.

- Real 정상 대화: `devDebug`에서 Firebase Live API 기반으로 확인한다.
- 재현성 있는 상태 전이/장애/handoff: `mockDebug`에서 `ChatDemoPreset`을 바꿔 확인한다.

`mockDebug` preset은 real AI 응답 품질 검증용이 아니다. preset은 화면 상태 전이, Correction handoff, 장애/cleanup을 안정적으로 재현하기 위한 도구다.

관련 코드:

- `app/src/main/java/com/app/umma/data/repository/ChatRepositoryImpl.kt`
- `app/src/main/java/com/app/umma/presentation/chat/ChatViewModel.kt`
- `app/src/main/java/com/app/umma/presentation/chat/ChatScreen.kt`
- `app/src/main/java/com/app/umma/data/repository/fake/demo/chat/ChatDemoPreset.kt`
- `app/src/main/java/com/app/umma/data/repository/fake/demo/chat/ChatDemoFixtures.kt`
- `app/src/main/java/com/app/umma/data/repository/fake/FakeChatRepository.kt`
- `docs/Sprint2/Demo/TestSheet/TEST_FLOW_CHAT.md`

## 1. 실행 기준

Real 정상 대화는 `devDebug` variant에서 확인한다.

- 실제 Firebase Live API 연결
- 실제 음성 인식/AI 응답/음성 재생
- Dashboard/Correction으로 이어지는 실제 handoff
- Android Studio에서는 `Build Variants > :app > devDebug`를 선택한 뒤 실행한다.

재현성 있는 상태 전이 데모는 `mockDebug` variant에서 확인한다.

```bash
./gradlew :app:installMockDebug
```

Android Studio에서는 `Build Variants > :app > mockDebug`를 선택한 뒤 실행한다.

데모에서 real 품질이 중요하면 `devDebug`, 특정 상태 재현성이 중요하면 `mockDebug`를 사용한다.

## 2. 현재 구현 상태

- Real Chat은 Firebase Live API에서 내려오는 `inputTranscription`, `outputTranscription`, `AudioResponse`를 사용한다.
- PTT release 직후 별도의 `AIState.THINKING` 화면을 필수로 보지 않는다.
- 현재 기준은 release 후 녹음 상태와 input animation이 종료되고, AI 응답 수신 시 음성 재생과 output animation이 표시되는 것이다.
- USER final turn이 저장되면 `correctionAvailable=true` handoff가 발생한다.
- Correction suggestion 생성 품질은 Chat 데모 범위가 아니라 Correction 데모 범위다.
- 화면 이탈 시 `stopChat()` 경로로 녹음과 재생을 정리한다.
- Mock preset 확인 로그는 logcat `ChatMockPreset` 태그를 사용한다.

## 3. Real 정상 대화 흐름

Variant: `devDebug`

1. Dashboard에서 AI Chat으로 진입한다.
2. Chat 화면이 세션 준비 완료 상태가 되는지 확인한다.
3. 최초 진입 사용자라면 `Pick 5 Topics` 다이얼로그에서 관심 주제 5개를 선택한다.
4. 마이크 권한 요청이 표시되면 허용한다.
5. PTT 버튼을 눌러 사용자 발화를 시작한다.
6. 사용자 발화 중 input animation이 표시되는지 확인한다.
7. PTT 버튼을 다시 눌러 발화를 종료한다.
8. 녹음 상태와 input animation이 종료되는지 확인한다.
9. AI 음성 응답이 재생되는지 확인한다.
10. AI 발화 중 output animation이 표시되는지 확인한다.
11. 자막 버튼을 켜 마지막 확정 User/AI turn이 표시되는지 확인한다.
12. 1~2턴 추가 대화 후 Dashboard로 복귀한다.
13. Dashboard에서 교정 대기 카드가 교정 가능 상태인지 확인한다.
14. Correction 화면으로 진입 가능한지 확인한다.

합격 기준:

- 실제 음성 입력이 Live session으로 전송된다.
- AI 음성 응답이 재생된다.
- 응답 종료 후 다시 대화 가능한 상태로 돌아온다.
- USER final turn 저장 후 Correction handoff가 반영된다.
- `PTT release 후 AI 응답 대기 상태`는 필수 판정 항목이 아니다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-01 | CHAT-001 AC1·3·4 | Dashboard에서 AI 대화 카드 클릭 | Chat 화면 진입, guard/loading 후 대화 가능 상태 | | |
| TC-CH-02 | CHAT-001 AC2 | Dashboard 언어 변경 후 Chat 진입 | 변경된 학습 언어 기준으로 대화 컨텍스트 반영 | | |
| TC-CH-03 | CHAT-001 AC4 | 관심 주제 미설정 계정으로 Chat 최초 진입 | `Pick 5 Topics` 표시, 5개 선택 후 `Done` 가능 | | |
| TC-CH-04 | CHAT-002 AC1·2 | 최초 PTT 입력 후 마이크 권한 허용 | 권한 허용 후 녹음 시작 가능 | | |
| TC-CH-07 | CHAT-003 AC1·2·4 / CHAT-004 AC1 | PTT 입력 중 발화 | 녹음 상태, OS mic indicator, input animation 표시 | | |
| TC-CH-08 | CHAT-003 AC3 | PTT release 또는 재클릭 | 녹음 종료, input animation 정지, OS mic indicator 내려감 | | |
| TC-CH-09 | CHAT-004 AC2·3·5 | USER 발화 후 AI 응답 수신 | AI 음성 재생, output animation, 응답 후 Ready/IDLE 복귀 | | |
| TC-CH-10 | CHAT-004 AC4 | 자막 버튼 On | 마지막 확정 User/AI turn 표시 | | |
| TC-CH-12 | CHAT-005 AC1·2·3·4 | Chat 후 Dashboard/Correction 확인 | 교정 대기 카드 활성, Correction 진입 가능 | | |

## 4. Mock preset 선택

활성 preset은 `ChatDemoPresetConfig.activePreset` 하나로 결정한다.

```kotlin
object ChatDemoPresetConfig {
    val activePreset: ChatDemoPreset = ChatDemoPreset.HandoffSuccess
}
```

값을 바꾼 뒤 앱을 다시 빌드/실행한다.

## 5. Preset 목록

| Preset | 목적 | 트리거 | 기대 결과 |
| --- | --- | --- | --- |
| `HandoffSuccess` | Chat에서 Correction으로 넘어갈 수 있는 신호 생성 | Chat 진입 후 마이크 버튼 입력 | user partial/final 표시, turn 저장, `correctionAvailable=true` |
| `SaveSignalOnly` | Correction 진입 신호만 빠르게 확인 | Chat 진입 후 마이크 버튼 입력 | user final만 발생, Correction 신호 전달 |
| `HandoffDuplicateFinal` | 같은 final turn 중복 방어 확인 | Chat 진입 후 마이크 버튼 입력 | 같은 `turnId` final 2회 emit, 저장/신호는 1회만 처리되어야 함 |
| `RecordingInterrupted` | 녹음 중 장애와 마이크 cleanup 확인 | Chat 진입 후 마이크 버튼 입력 | recording 중단, retry 가능한 error 상태 |
| `SilentInputNoFinal` | 입력은 있었지만 final transcript가 없는 케이스 확인 | Chat 진입 후 마이크 버튼 입력 | partial만 발생, Correction 신호 없음 |
| `ReconnectSuccess` | 세션 중 장애 후 자동 복구 상태 확인 | Chat 진입 후 마이크 버튼 입력 | `RECONNECTING` 진입 후 `READY/IDLE` 복귀 |
| `ReconnectFailed` | 세션 중 장애 후 복구 실패 UI 확인 | Chat 진입 후 마이크 버튼 입력 | retry 가능한 error 상태 유지 |
| `FatalError` | 복구 불가 일반 에러 확인 | Chat 진입 | non-recoverable error 상태 |

## 6. Mock 기본 데모 흐름

### Scenario A - Chat에서 Correction 신호까지

Preset: `HandoffSuccess`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.HandoffSuccess`로 설정한다.
2. `mockDebug`로 앱을 실행한다.
3. Dashboard에서 AI Chat으로 진입한다.
4. Chat 화면이 `READY` 상태인지 확인한다.
5. 마이크 버튼을 눌러 입력을 트리거한다.
6. user partial text가 표시되는지 확인한다.
7. user final text가 표시되는지 확인한다.
8. logcat에서 `ChatMockPreset` 태그의 `handoff_success emitting final user turn`과 `final user turn emitted` 로그를 확인한다.
9. Dashboard 또는 Correction 진입 경로에서 교정 가능 상태가 반영되는지 확인한다.

합격 기준:

- Chat 화면이 멈추지 않는다.
- user final turn이 1회 저장된다.
- `correctionAvailable=true` 신호가 올라간다.
- AI 응답 재생까지 포함한 real happy path 검증은 `devDebug`에서 확인한다.
- Correction suggestion 생성 자체는 이 시나리오의 검증 범위가 아니다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-15 | CHAT-005 AC1·2·3·4 | `HandoffSuccess`에서 마이크 버튼 입력 | user partial/final 표시 | | |
| TC-CH-15 | CHAT-005 AC1·2 | logcat `ChatMockPreset` 확인 | `handoff_success emitting final user turn`, `final user turn emitted` 로그 | | |
| TC-CH-15 | CHAT-005 AC3·4 | Dashboard/Correction 경로 확인 | 교정 가능 상태 반영, Correction 진입 가능 | | |

### Scenario B - Correction 신호만 빠르게 확인

Preset: `SaveSignalOnly`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.SaveSignalOnly`로 설정한다.
2. `mockDebug`로 앱을 실행한다.
3. Chat 화면에 진입한다.
4. 마이크 버튼을 눌러 입력을 트리거한다.
5. partial 없이 user final만 발생하는지 확인한다.
6. logcat에서 `ChatMockPreset` 태그의 `save_signal_only emitting final user turn for correction signal` 로그를 확인한다.
7. Dashboard 또는 Correction 진입 경로에서 교정 가능 상태가 반영되는지 확인한다.

합격 기준:

- AI 응답 없이도 user final turn 저장과 Correction 신호 전달이 완료된다.
- 데모에서 "Chat 이후 Correction으로 넘어갈 수 있는 상태"만 빠르게 만들 수 있다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-16 | CHAT-005 AC2·3·4 | `SaveSignalOnly`에서 마이크 버튼 입력 | partial 없이 user final 발생 | | |
| TC-CH-16 | CHAT-005 AC2 | logcat `ChatMockPreset` 확인 | `save_signal_only emitting final user turn for correction signal` 로그 | | |
| TC-CH-16 | CHAT-005 AC3·4 | Dashboard/Correction 경로 확인 | 교정 가능 상태 반영 | | |

### Scenario C - 중복 final turn 방어

Preset: `HandoffDuplicateFinal`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.HandoffDuplicateFinal`로 설정한다.
2. Chat 화면에 진입한다.
3. 마이크 버튼을 눌러 입력을 트리거한다.
4. logcat에서 `ChatMockPreset` 태그의 `handoff_duplicate_final emitting duplicate final events` 로그를 확인한다.
5. logcat에서 `duplicate final emitted`와 `final user turn emitted` 로그가 같은 `turnId` 기준으로 남는지 확인한다.
6. Dashboard 또는 Correction 진입 경로에서 교정 가능 상태가 중복 없이 유지되는지 확인한다.

합격 기준:

- 같은 `turnId` final event가 2회 들어와도 turn 저장은 1회만 일어난다.
- Correction 신호도 같은 turn 기준으로 중복 처리되지 않는다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-17 | CHAT-005 AC5 | `HandoffDuplicateFinal`에서 마이크 버튼 입력 | 같은 `turnId` final 2회 emit | | |
| TC-CH-17 | CHAT-005 AC5 | logcat `ChatMockPreset` 확인 | `duplicate final emitted`, `final user turn emitted` 로그 | | |
| TC-CH-17 | CHAT-005 AC5 | Dashboard/Correction 경로 확인 | 교정 가능 상태가 중복 없이 유지 | | |

## 7. Mock 장애 데모 흐름

### Scenario D - 장애 후 자동 복구

Preset: `ReconnectSuccess`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.ReconnectSuccess`로 설정한다.
2. Chat 화면에 진입한다.
3. 화면이 `READY`가 된 뒤 마이크 버튼을 눌러 장애를 트리거한다.
4. `RECONNECTING` 상태가 잠깐 반영되는지 확인한다.
5. 다시 `READY/IDLE` 상태로 복귀하는지 확인한다.

합격 기준:

- 세션 시작 직후 장애가 발생하지 않는다.
- 사용자가 입력을 트리거한 뒤 장애 이벤트가 발생한다.
- 복구 성공 후 화면은 정상 대화 가능 상태로 돌아온다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-19 | CHAT-006 AC1·2 | `ReconnectSuccess`에서 READY 후 마이크 버튼 입력 | `RECONNECTING` 후 READY/IDLE 복귀 | | |
| TC-CH-19 | CHAT-006 AC1·2 | logcat `ChatMockPreset` 확인 | `reconnect_success emitted SessionInterrupted -> Reconnected -> IDLE` 로그 | | |

주의:

- 현재 mock은 지연 없이 이벤트를 emit하므로 `RECONNECTING` 상태는 짧게 보일 수 있다.
- 화면 체감보다 `ChatMockPreset` 로그의 `reconnect_success emitted SessionInterrupted -> Reconnected -> IDLE` 확인이 더 안정적인 검증 기준이다.

### Scenario E - 녹음 중 장애와 cleanup

Preset: `RecordingInterrupted`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.RecordingInterrupted`로 설정한다.
2. Chat 화면에 진입한다.
3. 화면이 `READY`가 된 뒤 마이크 버튼을 눌러 녹음을 시작한다.
4. `SessionInterrupted` 이후 retry 가능한 error 상태로 전환되는지 확인한다.
5. OS 마이크 표시가 내려가는지 확인한다.
6. 화면의 input level이 0으로 돌아오는지 확인한다.

합격 기준:

- 녹음 중 장애가 발생해도 `isRecording=false`가 된다.
- `AudioInput.stopRecording()` 또는 record job cancellation 경로로 마이크가 정리된다.
- final user turn이 없으므로 Correction 신호가 올라가지 않는다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-21 | CHAT-006 AC5 | `RecordingInterrupted`에서 READY 후 마이크 버튼 입력 | 녹음 중단, retry 가능한 error UI 표시 | | |
| TC-CH-21 | CHAT-006 AC5 | OS mic indicator / input level 확인 | mic indicator 내려감, input level 0 복귀 | | |
| TC-CH-21 | CHAT-006 AC5 | logcat `ChatMockPreset` 확인 | `recording_interrupted emitted LISTENING -> SessionInterrupted -> ReconnectFailed` 로그 | | |

### Scenario F - final transcript 없는 입력

Preset: `SilentInputNoFinal`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.SilentInputNoFinal`로 설정한다.
2. Chat 화면에 진입한다.
3. 마이크 버튼을 눌러 입력을 트리거한다.
4. user partial만 잠깐 반영되고 final transcript가 없는지 확인한다.
5. logcat에서 `ChatMockPreset` 태그의 `silent_input_no_final completed without final transcription` 로그를 확인한다.
6. Dashboard 또는 Correction 진입 경로에서 교정 가능 상태가 새로 생기지 않는지 확인한다.

합격 기준:

- partial transcript만으로 SessionMemory 저장이 발생하지 않는다.
- Correction 신호가 올라가지 않는다.
- 화면은 에러 없이 `READY/IDLE`로 유지된다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-18 | CHAT-005 AC1·2 | `SilentInputNoFinal`에서 마이크 버튼 입력 | user partial만 표시, final 없음 | | |
| TC-CH-18 | CHAT-005 AC1·2 | logcat `ChatMockPreset` 확인 | `silent_input_no_final completed without final transcription` 로그 | | |
| TC-CH-18 | CHAT-005 AC2 | Dashboard/Correction 경로 확인 | 새 교정 가능 신호 없음 | | |

### Scenario G - 장애 후 복구 실패

Preset: `ReconnectFailed`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.ReconnectFailed`로 설정한다.
2. Chat 화면에 진입한다.
3. 화면이 `READY`가 된 뒤 마이크 버튼을 눌러 장애를 트리거한다.
4. error 화면 또는 retry 가능한 error 상태가 유지되는지 확인한다.
5. `isRecoverableError=true`에 해당하는 retry UI가 보이는지 확인한다.

합격 기준:

- `SessionInterrupted` 이후 `ReconnectFailed`가 반영된다.
- 화면이 다시 `READY`로 덮이지 않는다.
- 사용자가 재시도 액션을 인지할 수 있다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-20 | CHAT-006 AC1·3 | `ReconnectFailed`에서 READY 후 마이크 버튼 입력 | retry 가능한 error UI 표시 | | |
| TC-CH-20 | CHAT-006 AC3 | retry UI 확인 | 사용자가 재시도 액션을 인지 가능 | | |
| TC-CH-20 | CHAT-006 AC1·3 | logcat `ChatMockPreset` 확인 | `reconnect_failed emitted SessionInterrupted -> ReconnectFailed` 로그 | | |

### Scenario H - 복구 불가 일반 에러

Preset: `FatalError`

1. `ChatDemoPresetConfig.activePreset = ChatDemoPreset.FatalError`로 설정한다.
2. Chat 화면에 진입한다.
3. 진입 직후 일반 error 상태로 전환되는지 확인한다.

합격 기준:

- `isRecoverableError=false` 상태로 처리된다.
- retry 가능한 reconnect 실패와 구분된다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-22 | CHAT-006 AC4 | `FatalError`에서 Chat 진입 | non-recoverable error 상태 표시 | | |
| TC-CH-22 | CHAT-006 AC4 | retry UI 여부 확인 | recoverable retry UI와 구분됨 | | |
| TC-CH-22 | CHAT-006 AC4 | logcat `ChatMockPreset` 확인 | `fatal_error emitted on startSession` 로그 | | |

## 8. 화면 이탈 정리 확인

Real과 mock 모두에서 확인한다.

1. Chat 화면에서 마이크 입력 중 뒤로 가기 또는 다른 탭 이동을 수행한다.
2. OS 마이크 표시가 즉시 내려가는지 확인한다.
3. AI 음성 재생 중 화면을 이탈했을 때 재생이 멈추는지 확인한다.
4. 다시 Chat에 진입했을 때 이전 녹음/재생 상태가 남아 있지 않은지 확인한다.

합격 기준:

- `stopChat()`이 호출되어 녹음과 재생이 정리된다.
- `AudioInput.stopRecording()`으로 활성 `AudioRecord`가 즉시 stop/release 된다.
- `AudioOutput.stopPlaying()`으로 queue와 `AudioTrack` buffer가 clear 된다.

AC/QA 시트:

| Test ID | AC | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-CH-13 | CHAT-006 AC6 | 녹음 중 뒤로가기 또는 다른 화면 이동 | OS mic indicator 즉시 내려감, 재진입 시 녹음 상태 없음 | | |
| TC-CH-14 | CHAT-006 AC6 | AI 음성 재생 중 뒤로가기 또는 다른 화면 이동 | 재생 즉시 중지, 재진입 시 이전 queue 없음 | | |

## 9. 데모 선택 가이드

Real 통합 데모:

- `devDebug`
- `DEMO_REAL_INTEGRATED_FLOW.md` 기준으로 진행한다.
- 실제 AI 응답 품질, latency, 음성 재생, 자막을 확인한다.

Mock 발표용 기본 preset:

- `HandoffSuccess`

Correction 신호만 빠르게 만들 때:

- `SaveSignalOnly`

장애 화면을 확실히 보여줄 때:

- `ReconnectFailed`

녹음 cleanup을 QA할 때:

- `RecordingInterrupted`

Correction 신호 오발행을 QA할 때:

- `SilentInputNoFinal`

중복 방어를 QA할 때:

- `HandoffDuplicateFinal`

테스트 체크리스트:

- `docs/Sprint2/Demo/TestSheet/TEST_FLOW_CHAT.md`를 기준으로 Pass/Fail을 기록한다.
