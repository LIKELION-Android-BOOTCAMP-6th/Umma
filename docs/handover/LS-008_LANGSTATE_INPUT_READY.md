# [LS-008] LangStateUpdateInput 조립 계약 준비 완료

> 대상: `COR-006-A` / `COR-006-B` 후속 작업자
> 보낸 사람: `SYS-LEARNING-STATE-INFRA` 작업자
> 관련 이슈: #138 (`LS-008 LangStateUpdateInput 조립 정책 제공`), #107 (`COR-006-A Flashcard 저장 완료 처리`)

---

## 1. 요약

Correction 완료 흐름에서 필요한 `LangStateUpdateInput` 조립 계약은 LearningState 책임 범위에서 준비되었다.

새로 추가된 계약은 다음 파일에 있다.

- [`BuildLangStateUpdateInputUseCase`](../../app/src/main/java/com/app/umma/domain/usecase/learningstate/BuildLangStateUpdateInputUseCase.kt)
- [`BuildLangStateUpdateInputUseCaseTest`](../../app/src/test/java/com/app/umma/domain/usecase/learningstate/BuildLangStateUpdateInputUseCaseTest.kt)

이 작업은 `CorrectionViewModel`, `CompleteCorrectionUseCase`, `domain/usecase/correction` 파일을 수정하지 않는다.
Correction 쪽 후속 작업자는 아래 계약을 호출부에 연결하면 된다.

---

## 2. LS-008에서 준비한 것

### 2.1. 조립 UseCase

```kotlin
class BuildLangStateUpdateInputUseCase @Inject constructor() {
    operator fun invoke(
        command: BuildLangStateUpdateInputCommand
    ): Result<LangStateUpdateInput>
}
```

이 UseCase는 `LangStateUpdateInput`의 필드 의미와 검증 규칙만 책임진다.
Session Memory 조회, 선택 카드 조회, `CompleteCorrectionUseCase` 호출은 이 UseCase의 책임이 아니다.

### 2.2. 입력 Command

```kotlin
data class BuildLangStateUpdateInputCommand(
    val uid: String,
    val lang: LangCode,
    val selectedLang: LangCode,
    val currentState: LangState?,
    val correctionContextTurns: List<SessionTurn>,
    val stableEventParts: List<String>,
    val analyzedAt: Long,
    val correctionResult: CorrectionResult? = null,
    val correctionAvailableOverride: Boolean? = null,
    val forceReanalysis: Boolean = false
)
```

caller가 이미 확보한 domain 값만 넘기는 형태다.
Correction 저장 요청 모델이나 화면 상태 모델을 직접 참조하지 않도록 설계했다.

---

## 3. 고정된 정책

### 3.1. `sessionMemoryKey`

`sessionMemoryKey`는 개별 `LiveSession.sessionId`가 아니라 사용자와 학습 언어 기준으로 만든다.

```text
{uid}_{languageCode}
예: uid-1_en
```

이 값은 같은 사용자의 같은 학습 언어에 누적된 Session Memory 스코프를 가리킨다.

### 3.2. `analysisEventId`

`analysisEventId`는 재시도 시 같은 완료 요청이 중복 반영되지 않도록 stable fingerprint로 만든다.

fingerprint 재료:

- `uid`
- `lang`
- caller가 넘긴 `stableEventParts`
- USER turn의 `turnId`, `text`, `createdAt`, `tokenCount`, `durationMs`, `confidence`

`requestedAt`처럼 매 호출마다 바뀔 수 있는 값은 fingerprint에 넣지 않는다.

### 3.3. `recentUserTurns`

`recentUserTurns`는 RT-003 correction context의 `SessionTurn` 중 `USER` 발화만 변환한다.

제외 대상:

- AI turn
- 공백 USER turn

변환 후에는 LS-006이 사용하는 `ConversationTurn`만 남긴다.

### 3.4. `flashcardReviewEvents`

Correction 완료 흐름에서는 복습 이벤트가 발생하지 않는다.
따라서 `flashcardReviewEvents`는 항상 `emptyList()`로 고정된다.
SRS 복습 이벤트 반영은 SRS 흐름의 별도 책임이다.

---

## 4. COR-006 후속 작업자가 해야 할 일

### 4.1. 호출부에서 확보해야 하는 값

COR-006 완료 파이프라인을 실제 호출로 바꿀 때 다음 값을 준비해야 한다.

| 값 | 출처 |
| --- | --- |
| `uid` | 현재 로그인 사용자 |
| `lang` | 저장 요청 또는 현재 Correction 대상 언어 |
| `selectedLang` | `GlobalLangState.userPref.selectedLang` |
| `currentState` | `GlobalLangState.langStates[lang]` |
| `correctionContextTurns` | RT-003 `GetCorrectionContextUseCase(lang)` |
| `stableEventParts` | 선택된 `CorrectionSuggestion.id` 또는 저장될 flashcard 식별 재료 |
| `analyzedAt` | 완료 요청 시각 |
| `correctionResult` | 선택된 교정 결과를 LS-006 최소 입력으로 요약한 값 |
| `correctionAvailableOverride` | Correction 완료 시 `false` |

### 4.2. 연결 예시

아래 코드는 방향을 설명하기 위한 예시다. 실제 파일 구조와 상태 모델에 맞춰 조정한다.

```kotlin
val langStateInput = buildLangStateUpdateInput(
    BuildLangStateUpdateInputCommand(
        uid = uid,
        lang = request.lang,
        selectedLang = selectedLang,
        currentState = currentState,
        correctionContextTurns = correctionContextTurns,
        stableEventParts = selectedSuggestions.map { it.id },
        analyzedAt = request.requestedAt,
        correctionResult = correctionResult,
        correctionAvailableOverride = false,
    )
).getOrThrow()

val result = completeCorrection(
    CompleteCorrectionInput(
        selectedSuggestions = selectedSuggestions,
        langStateUpdateInput = langStateInput,
        requestedAt = request.requestedAt,
    )
)
```

### 4.3. placeholder 제거

현재 Correction 화면 쪽에는 완료 흐름을 화면에서 확인하기 위한 `stubCompletion`이 남아 있다.
COR-006 후속 작업에서 실제 `CompleteCorrectionUseCase` 호출로 교체하면 된다.

제거 대상:

- `stubCompletion(...)`
- `@Suppress("UnusedPrivateProperty")`
- `completeCorrection` 미사용 상태

---

## 5. 실패 처리 기준

`BuildLangStateUpdateInputUseCase`는 다음 경우 `Result.failure`를 반환한다.

- `uid`가 비어 있음
- `selectedLang`와 업데이트 대상 `lang`이 다름
- `currentState`가 없음
- correction context에 의미 있는 USER turn이 없음
- `stableEventParts`가 비어 있음

COR-006 후속 작업에서는 이 실패를 완료 실패/Retry 흐름으로 연결해야 한다.
실패했는데 `stubCompletion`처럼 Done으로 보내면 LangState와 Summary가 갱신되지 않은 완료 상태가 될 수 있다.

---

## 6. 테스트 완료 기준

LS-008에서는 아래 명령으로 컴파일과 단위 테스트를 확인했다.

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" bash ./gradlew :app:compileDevDebugKotlin :app:testDevDebugUnitTest --tests com.example.umma.domain.usecase.learningstate.BuildLangStateUpdateInputUseCaseTest
```

검증한 내용:

- `sessionMemoryKey`가 `uid + language` 스코프로 생성됨
- `analysisEventId`가 재시도 시 동일하게 유지됨
- `recentUserTurns`에 USER turn만 포함됨
- 공백 USER turn과 AI turn은 제외됨
- 빈 context, 언어 불일치, `currentState` 없음, uid 공백, stable event 재료 없음은 실패 처리됨

---

## 7. 남은 작업

LS-008은 조립 계약까지만 제공한다.
다음 작업은 COR-006 쪽에서 처리한다.

- Correction 완료 호출부에서 `BuildLangStateUpdateInputUseCase` 주입
- `GlobalLangState`, RT-003 context, 선택된 suggestion으로 `BuildLangStateUpdateInputCommand` 구성
- `CompleteCorrectionInput.langStateUpdateInput`에 조립 결과 전달
- `stubCompletion` 제거
- 조립 실패 시 COR-006-B Retry / Error 상태 연결
- 실제 완료 성공 후 COR-007 Dashboard 복귀 및 pending sync/compression 상태 연결
