# [COR-006-A] LangStateUpdateInput 구성 책임 인계

> 대상: SYS-CORRECTION-INFRA 소유자(부팀장) 정원화
> 보낸 사람: `feature/correction` 브랜치 작업자 김태환
> 관련 이슈: #107 (2-4.COR-006-A Flashcard 저장 완료 처리)

---

## 1. 배경 — 왜 이 문서가 필요한가

`COR-006-A` 구현 중 **설계 문서와 실제 코드 시그니처가 정합되지 않는 충돌**이 발견되었다.
이번 PR은 충돌 지점을 임의 해석하지 않고 화면 레이어 골격만 완성한 뒤, 인프라 측 결정을 인계하는 형태로 마무리한다("충돌 지점을 먼저 정리하여 정합성을 맞춘 뒤 작업 진행" 원칙).

### 1.1. 설계 문서 측 (User FlowDB)

`docs/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md` 본문:

> ViewModel은 완료 결과를 Done / Retry 상태로 연결한다.
> **제외 범위: LangState 업데이트 입력 생성 및 적용**

즉 ViewModel(=화면 레이어)이 `LangStateUpdateInput` 을 직접 만들거나 적용하면 안 된다는 게 설계 의도다.

### 1.2. 코드 측 (Domain)

[`CompleteCorrectionInput`](../../app/src/main/java/com/example/umma/domain/model/correction/CorrectionCompletionModels.kt):

```kotlin
data class CompleteCorrectionInput(
    val selectedSuggestions: List<CorrectionSuggestion>,
    val langStateUpdateInput: LangStateUpdateInput,  // ← 필수 필드
    val requestedAt: Long = System.currentTimeMillis(),
)
```

`langStateUpdateInput`이 **non-nullable 필수 필드**라 호출자가 무조건 만들어 줘야 한다. 그런데 그 안의 `sessionMemoryKey` / `analysisEventId` / `recentUserTurns(ConversationTurn)` / `flashcardReviewEvents` 출처가 현재 `CorrectionViewModel` 에는 없고, 헬퍼 UseCase 도 정의되어 있지 않다.

### 1.3. 결론

설계 문서(out-of-scope) ↔ 코드 시그니처(필수) ↔ ViewModel 의 정보 보유 상태(미보유) 셋이 동시에 어긋난다.
ViewModel 에서 임시로 입력을 조립해 호출하면 LangState/Statistics 단계에 더미 데이터가 흘러가 후속 학습 지표를 오염시킬 위험이 있어 이번 PR 에서는 **실제 호출을 보류**한다.

---

## 2. 이번 PR 이 도입한 placeholder

### 2.1. 위치

[`CorrectionViewModel.launchCompletion`](../../app/src/main/java/com/example/umma/presentation/correction/CorrectionViewModel.kt) 내부 `viewModelScope.launch { ... }` 블록.

```kotlin
completionJob = viewModelScope.launch {
    // TODO(SCI-001): SYS-CORRECTION-INFRA 의 CompleteCorrectionInput.langStateUpdateInput 필드가
    //  옵셔널화 / UseCase 내부 조립 / 헬퍼 UseCase 신설 중 하나로 보정되는 즉시, 아래 stubCompletion 을
    //  실제 completeCorrection(input) 호출로 교체한다. ...
    val result = stubCompletion(launch.saveRequest)
    logCompletionResult(result)
    _uiState.update { current -> current.applyCompletionOutcome(result) }
}
```

### 2.2. 동작

- `CompleteCorrectionUseCase` 는 ViewModel 에 주입만 받아 두고 실제 호출은 안 한다(`@Suppress("UnusedPrivateProperty")`).
- 대신 `stubCompletion(request)` 가 `CorrectionSaveRequest.flashcards.map { it.suggestionId }` 를 그대로 `savedFlashcardIds` 로 흉내 낸 `CompleteCorrectionResult` 를 `Result.success` 로 돌려준다.
- 화면은 이 결과를 받아 `Phase.Done` 으로 전환, 카드 목록 / 저장 버튼을 닫고 "저장이 완료되었어요 (N개 카드)" 텍스트를 표시한다.
- `completionResult`(즉 `savedFlashcardIds`) 가 stub 값이라는 점만 빼면 화면 흐름과 회귀 테스트는 모두 정상 동작한다.

### 2.3. 회귀 테스트

- [`CorrectionUiStateTest`](../../app/src/test/java/com/example/umma/presentation/correction/CorrectionUiStateTest.kt) — `computeCompletionLaunch` / `openCompletionWindow` / `applyCompletionOutcome` / `canSave` 가드(`isCompleting`, `Phase.Done`).

---

## 3. 부팀장이 결정할 옵션

세 가지 옵션 중 하나로 인프라를 보정해 주십쇼. ViewModel 측 코드는 옵션이 정해진 뒤 **placeholder 자리만 실제 호출로 교체하는 후속 PR** 로 마무리됩니다.

### 옵션 A — `langStateUpdateInput` 옵셔널화 (가장 가벼움)

`CompleteCorrectionInput.langStateUpdateInput` 을 `LangStateUpdateInput?` 으로 내려, null 이면 `CompleteCorrectionUseCase` 내부에서 LangState 적용 / Statistics 기록 / Session compression 단계를 **skip** 한다.

- 장점: ViewModel 시그니처 변경 없음. 화면 레이어가 정말로 "Done/Retry 만 연결" 책임만 진다.
- 단점: LS-006 정책상 LangState 갱신이 빠진 완료가 정합성에 문제가 없는지 확인 필요. (현재 정책상 빠지면 `correctionAvailable` 플래그가 false 로 안 내려가서 같은 세션이 재진입 가능해 보임 — 검토 부탁드립니다.)

### 옵션 B — `CompleteCorrectionUseCase` 내부 조립

`langStateUpdateInput` 필드 자체를 제거하고, UseCase 내부에서 `GetCurrentUserUidUseCase` / `ObserveLearningStateUseCase` / `GetCorrectionContextUseCase` / `SessionMemoryRepository` / `Uuid` 생성기 등을 직접 의존해 조립한다.

- 장점: 화면이 `selectedSuggestions` + `requestedAt` 만 넘기면 됨. 설계 문서 의도와 가장 정합.
- 단점: `sessionMemoryKey` / `analysisEventId` 의 출처 정책을 SYS-CORRECTION-INFRA 가 단독 결정해야 함(현재 SYS_CORRECTION_INFRA.md 가 이 부분을 다루지 않음).

### 옵션 C — 도메인 헬퍼 UseCase 신설

`BuildLangStateUpdateInputUseCase` (또는 동등한 이름) 를 신설해 `CompleteCorrectionUseCase` 내부에서 호출. 외부에서 보면 옵션 B 와 같으나 책임 분리가 더 명확.

- 장점: 향후 다른 화면(예: 백그라운드 완료, 자동 동기화)이 같은 헬퍼를 재사용 가능.
- 단점: 가장 손이 많이 감.

---

## 4. 영향 받는 후속 백로그

| 백로그 | 영향 |
| --- | --- |
| **COR-006-B** (완료 실패 / Retry 처리) | 실제 호출이 살아 있어야 실패 분기를 의미 있게 테스트 가능. 인프라 보정 PR 이 머지된 뒤 작업 가능. |
| **COR-007-A** (Dashboard 복귀) | `Phase.Done` 진입 시점을 1회성 이벤트로 변환해야 하는데, stub 결과로도 진입 자체는 일어나므로 인프라 보정과 독립적으로 진행 가능. 다만 `completionResult.sessionMemoryKey` 등을 후속 화면에서 쓸 가능성이 있다면 인프라 보정 이후가 안전. |
| **COR-007-B** (sync/compression pending 비차단) | `pendingSyncFlashcardIds` / `sessionCompressionPending` 등이 실제 값으로 채워져야 의미가 있음. 인프라 보정 PR 이 선결. |

---

## 5. 후속 PR 연결법

인프라 보정 PR 이 머지되면, 다음 두 줄 정도의 변경으로 placeholder 가 사라집니다.

```kotlin
// CorrectionViewModel.kt - launchCompletion 내부
- val result = stubCompletion(launch.saveRequest)
+ val result = completeCorrection(
+     CompleteCorrectionInput(
+         selectedSuggestions = /* state.suggestions 중 선택분 */,
+         /* 옵션 A 면 langStateUpdateInput = null, 옵션 B/C 면 필드 자체가 없음 */
+         requestedAt = launch.saveRequest.requestedAt,
+     )
+ )
```

`stubCompletion(...)` 과 `@Suppress("UnusedPrivateProperty")` 어노테이션도 함께 제거됩니다.

---

## 6. 참고

- 본 PR 의 화면 골격, Done 전환, 중복 방지 가드는 모두 `CorrectionUiStateTest` 로 회귀 검증됨.
- 설계 문서 원문: [`docs/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md`](../User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md)
- 인프라 문서 원문: [`docs/System_FlowDB/SYS_CORRECTION_INFRA.md`](../System_FlowDB/SYS_CORRECTION_INFRA.md)
- "충돌 지점을 먼저 정리하여 정합성을 맞춘 뒤 작업 진행" 원칙에 따라 본 PR 은 placeholder + 인계 문서로 마무리. 옵션 결정 후 조치해 주시면 후속 PR에 바로 연결.
