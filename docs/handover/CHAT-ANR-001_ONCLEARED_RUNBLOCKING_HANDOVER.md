# [CHAT-ANR-001] AI Chat 종료 경계 `runBlocking` ANR 후보 전달

> 대상: AI Chat / Realtime / Audio 리소스 정리 담당자
> 발견 로그: `2026-06-02 09:49:27` ActivityManager ANR
> 관련 화면: `com.app.umma/.MainActivity`
> 관련 파일: `app/src/main/java/com/app/umma/presentation/chat/ChatViewModel.kt`

---

## 1. 요약

`com.app.umma`에서 입력 이벤트 처리 지연 ANR이 발생했다.

```text
ANR in com.app.umma (com.app.umma/.MainActivity)
Reason: Input dispatching timed out
Waited 10001ms for MotionEvent
```

사용자가 화면을 터치했지만 앱 메인 스레드가 약 10초 동안 응답하지 못해 Android가 ANR로 판정한 상황이다.
크래시는 아니지만 사용자 입장에서는 앱이 멈춘 것으로 보이며, 반복되면 Play Console ANR 지표에 잡히는 중상급 이슈다.

현재 첨부된 ActivityManager 요약 로그만으로 원인을 100% 확정할 수는 없다.
다만 Chat 종료 경계의 `runBlocking`이 메인 스레드 blocking을 만들 수 있는 구조라 우선 점검 대상으로 전달한다.

---

## 2. 로그 해석

### 2.1. 핵심 증상

```text
Reason: Input dispatching timed out
Waited 10001ms for MotionEvent
```

의미:

- 사용자가 터치 또는 제스처를 입력했다.
- Android input dispatcher가 해당 이벤트를 앱에 전달했다.
- 앱이 10초 동안 응답하지 못했다.
- 시스템이 ANR로 기록했다.

### 2.2. 로그상 특징

```text
CPU usage:
2.2% 32416/com.app.umma
```

앱 프로세스 CPU 사용률은 높지 않다.
따라서 CPU를 계속 태우는 무한 루프보다는, 메인 스레드가 어떤 작업 완료를 기다리거나 lock / IO / 동기 coroutine 경계에 묶였을 가능성이 더 높다.

---

## 3. 우선 점검 후보

### 3.1. 위치

`app/src/main/java/com/app/umma/presentation/chat/ChatViewModel.kt`

`ChatViewModel.onCleared()`

```kotlin
override fun onCleared() {
    super.onCleared()
    outputLevelJob?.cancel()
    outputPlaybackJob?.cancel()

    runBlocking {
        stopChatInternal(resetUiState = false)
    }
}
```

### 3.2. 왜 위험한가

`onCleared()`는 화면 back stack 제거, bottom navigation 이동, Activity 종료/재생성 같은 UI 생명주기 경계에서 호출될 수 있다.
이 지점에서 `runBlocking`으로 suspend 정리 작업이 끝날 때까지 기다리면 호출 스레드를 blocking한다.

`onCleared()`가 메인 스레드에서 호출되는 상황이라면, 정리 작업이 늦어지는 동안 터치 입력 처리도 같이 멈출 수 있다.
이 패턴은 `Input dispatching timed out` ANR의 후보가 된다.

### 3.3. `stopChatInternal` 내부 작업

현재 `runBlocking` 안에서 호출되는 `stopChatInternal(resetUiState = false)`는 다음 작업을 수행한다.

```text
eventJob cancel
recordJob cancel
audioRecorder.stopRecording()
cancelPendingUserTurnUseCase()
audioPlayer.stopPlaying()
stopSessionUseCase(clearAppSession = false)
pendingTurnSaveCount reset
```

특히 아래 작업은 구현에 따라 지연될 수 있다.

- 오디오 녹음 종료
- 오디오 재생 종료
- Realtime transport 종료
- session mutex 획득
- WebSocket close / callback 경계
- repository 내부 coroutine 정리

이 중 하나라도 오래 걸리면 `runBlocking`이 메인 스레드를 붙잡을 수 있다.

---

## 4. 권장 수정 방향

### 4.1. 핵심 원칙

`onCleared()`에서 사용자 입력 응답성을 막으면서까지 종료 정리를 동기 완료 보장하지 않는다.

종료 정리는 필요하지만, 화면 생명주기보다 긴 scope에서 best-effort로 비동기 처리하는 방향을 권장한다.

### 4.2. 예시 수정안

```kotlin
override fun onCleared() {
    super.onCleared()
    outputLevelJob?.cancel()
    outputPlaybackJob?.cancel()

    applicationScope.launch {
        runCatching {
            stopChatInternal(resetUiState = false)
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to stop chat resources after ViewModel cleared", throwable)
        }
    }
}
```

설명:

- `runBlocking` 제거
- `ApplicationScope` 또는 IO 기반 app-level scope에서 정리 수행
- 정리 실패는 앱 흐름을 막지 않고 warning log로 관찰 가능하게 유지
- ViewModel scope는 `onCleared()` 후 취소되므로 사용하지 않는 편이 안전함

### 4.3. 추가 검토 포인트

위 예시를 그대로 적용하기 전 담당 파트에서 아래를 확인해야 한다.

- `stopChatInternal`이 app-level scope에서 실행되어도 UI state update 부작용이 없는지
- `audioRecorder.stopRecording()` / `audioPlayer.stopPlaying()`이 호출 스레드 제약을 갖는지
- `stopSessionUseCase(clearAppSession = false)`가 이미 내부에서 mutex / network / websocket close를 안전하게 처리하는지
- 종료 중 새 Chat 진입이 발생했을 때 이전 session 정리와 새 session 시작이 충돌하지 않는지
- `eventJob` / `recordJob` cancel 이후 cleanup 순서가 유지되어야 하는지

---

## 5. 책임 경계

이 문서는 ANR 후보 전달용이다.
Chat 파트 외부에서 `ChatViewModel`을 직접 수정하지 않는다.

담당 파트에서 결정해야 하는 항목:

- `onCleared()` cleanup의 동기 완료 보장이 실제로 필요한지
- 필요하다면 어떤 작업만 동기로 남기고 어떤 작업을 비동기로 넘길지
- Audio / Realtime transport close의 안전한 dispatcher
- session cleanup 실패 시 retry 또는 다음 Chat 진입 시 복구 정책

---

## 6. 검증 방법

### 6.1. 빌드

```powershell
.\gradlew.bat compileDevDebugKotlin
```

### 6.2. 재현 시나리오

수정 후 아래 시나리오를 반복 확인한다.

1. Chat 화면 진입
2. PTT 또는 Realtime session 활성화
3. 채팅 중 뒤로가기
4. bottom navigation으로 다른 탭 이동
5. 화면 회전
6. 앱 백그라운드 전환
7. 네트워크 불안정 상태에서 Chat 종료

확인할 것:

- `Input dispatching timed out` 재발 여부
- Chat 이탈 직후 터치 응답 지연 여부
- 오디오 녹음/재생 리소스 누수 여부
- Realtime session이 정상 close 되는지
- 다음 Chat 진입 시 pending session / stale session이 남지 않는지

### 6.3. ANR trace 확인

ActivityManager 요약 로그만으로는 확정이 어렵다.
가능하면 실제 ANR trace를 확인한다.

확인 대상:

```text
/data/anr/anr_*
Play Console ANR trace
Android Studio App Quality Insights
```

main thread stack에서 아래 키워드가 잡히는지 확인한다.

```text
runBlocking
ChatViewModel.onCleared
stopChatInternal
stopSession
audioRecorder.stopRecording
audioPlayer.stopPlaying
Mutex.withLock
```

---

## 7. 현재 확인된 상태

- `compileDevDebugKotlin`은 통과 확인됨.
- `testDevDebugUnitTest`는 실패했으나, 본 ANR 후보와 무관한 기존 테스트 fake 구현 누락으로 보임.
- 실패 내용은 `FlashcardRepository` / `CorrectionFlashcardLocalDataSource` 계열 fake가 신규 `getFlashcards(...)` abstract member를 구현하지 않은 컴파일 오류다.

---

## 8. 결론

이번 ANR 로그는 `MainActivity` 입력 응답 지연으로 기록되었지만, 현재 요약 로그만으로 특정 코드 라인을 확정할 수는 없다.

다만 `ChatViewModel.onCleared()`의 `runBlocking { stopChatInternal(...) }`은 UI 생명주기 경계에서 메인 스레드를 막을 수 있는 구조다.
Chat 종료, 화면 이동, Activity 재생성, 네트워크 불안정 상황과 겹치면 ANR 후보가 될 수 있으므로 Chat 담당 파트에서 우선 점검 및 비동기 cleanup 전환을 검토한다.
