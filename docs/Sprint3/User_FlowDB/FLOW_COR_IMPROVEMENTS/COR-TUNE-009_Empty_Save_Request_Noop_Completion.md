# [Improvement] COR-TUNE-009 품질 필터 후 빈 저장 요청 no-op 완료 처리

## User Story

사용자는 교정 카드를 선택해 저장했을 때, 품질 필터로 실제 저장할 카드가 0개가 되더라도 저장 실패나 Retry처럼 보이지 않기를 기대한다.
Umma는 저장 가치가 없는 카드를 복습에 남기지 않으면서도, 사용자가 처리한 교정 세션은 자연스럽게 완료 상태로 닫는다.
이 정리는 사용자에게는 "저장할 카드는 없었지만 정리는 완료됨"으로 보이며, LangState 점수나 교정 근거를 왜곡하지 않는다.

---

# 배경

COR-TUNE-007에서 `PrepareSaveRequestUseCase`는 선택된 suggestion을 저장 요청으로 정리하면서 중복·저품질 카드를 제외한다.
이 과정에서 모든 카드가 제외되면 `CorrectionSaveRequest.flashcards`가 빈 목록이 될 수 있다.

기존 `CorrectionRepositoryImpl.saveFlashcards()`는 저장소 계약상 `flashcards.isNotEmpty()`를 require로 강제한다.
따라서 빈 저장 요청이 그대로 repository까지 내려가면 사용자는 저장 실패가 아닌 상황에서도 `CompleteCorrectionUseCase` 실패와 Retry를 보게 된다.

또한 단순히 `correctionResult=null`로 LangState 분석을 호출하는 방식은 안전하지 않다.
recent user turn은 남아 있으므로 "교정 0개인 좋은 발화"처럼 점수에 반영될 수 있다.
빈 저장 요청은 저장과 LangState 분석을 모두 건너뛰고, correctionAvailable 신호만 false로 닫아야 한다.

---

# 완료 기준(AC)

- [ ] `PrepareSaveRequestUseCase` 품질 필터로 모든 선택 suggestion이 제외되어 `flashcards=emptyList()`가 되어도 저장 실패/Retry로 보이지 않는다.
- [ ] 빈 `CorrectionSaveRequest`는 `CorrectionRepository.saveFlashcards()`로 전달하지 않는다.
- [ ] 저장 가능한 Flashcard가 0개인 경우 `CompleteCorrectionUseCase`는 no-op 완료 성공을 반환한다.
- [ ] no-op 완료 시 LangState 점수/교정 근거 분석은 수행하지 않는다.
- [ ] no-op 완료 시 `correctionAvailable=false` 신호만 반영해 같은 교정 세션이 다시 노출되지 않게 한다.
- [ ] 일부 suggestion만 품질 필터를 통과한 경우 LangState/Compression에는 저장 대상에 남은 suggestion만 반영한다.
- [ ] 기존 저장 / rollback / Firestore sync 흐름은 무손상으로 유지한다.
- [ ] 저장 카드 0개 완료 토스트는 “저장할 학습 카드가 없어 정리만 완료했어요!”로 표시한다.

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-005_Save_Request.md`
- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md`
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-007_Correction_Flashcard_Dedup_Quality.md`
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md`

---

# 핵심 결정

- 빈 저장 요청 판단은 `CompleteCorrectionUseCase`에서 수행한다. `CorrectionRepository`는 여전히 빈 저장 요청을 잘못된 저장 호출로 방어한다.
- 품질 필터로 저장 가능한 카드가 0개가 되면 Flashcard 저장소를 호출하지 않는 no-op 완료 성공으로 처리한다.
- no-op 완료에서는 LangState batch 분석, statistics history, Flashcard summary 갱신, Session Memory compression을 호출하지 않는다.
- no-op 완료에서는 `ApplyCorrectionSignalUpdateUseCase`로 `SessionSummary`와 `DashSummary`의 `correctionAvailable=false`만 반영한다.
- 일부 카드만 저장 대상에 남은 경우에는 저장 대상에 남은 suggestion만 `CorrectionResult`와 compression payload에 사용한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| `PrepareSaveRequestUseCase` | 선택 suggestion을 저장 요청으로 정리하고 중복·저품질 카드를 제외한다. 전부 제외되어도 `Result.success(empty flashcards)`를 반환할 수 있다. |
| `CompleteCorrectionUseCase` | 빈 저장 요청을 저장 실패가 아닌 no-op 완료로 흡수한다. 저장 대상 suggestion 기준으로 LangState/Compression 입력을 좁힌다. |
| `CorrectionRepository` | 실제 Flashcard 저장 계약을 유지한다. 빈 저장 요청 직접 호출은 실패로 방어한다. |
| `ApplyCorrectionSignalUpdateUseCase` | no-op 완료 시 correctionAvailable 신호만 가볍게 닫는다. LangState 점수 분석은 담당하지 않는다. |
| `CorrectionViewModel` | 저장 카드 0개 완료 토스트를 사용자에게 자연스러운 문구로 보여준다. |

---

# 주요 작업

1. **빈 저장 요청 no-op 완료 분기**
   - `CompleteCorrectionUseCase`에서 `saveRequest.flashcards.isEmpty()`를 확인한다.
   - 빈 경우 `CorrectionRepository.saveFlashcards()`를 호출하지 않는다.
   - `CompleteCorrectionResult(savedFlashcardIds=emptyList(), pendingSyncFlashcardIds=emptyList())`로 성공 반환한다.

2. **correctionAvailable 신호 닫기**
   - no-op 경로에서 `ApplyCorrectionSignalUpdateUseCase`를 호출해 `correctionAvailable=false`를 반영한다.
   - 신호 반영 실패는 교정 세션을 닫지 못한 것이므로 완료 실패로 반환한다.

3. **LangState/Compression 입력 정합**
   - no-op 경로에서는 LangState 분석을 호출하지 않는다.
   - 일반 저장 경로에서는 `saveRequest.flashcards.suggestionId`에 남은 suggestion만 `CorrectionResult`와 compression payload 생성에 사용한다.

4. **사용자 문구 변경**
   - 저장 카드 수가 0개인 완료 결과의 Dashboard toast를 “저장할 학습 카드가 없어 정리만 완료했어요!”로 변경한다.

5. **문서 정합성 보강**
   - COR-005/COR-006의 일반 “0개면 완료 파이프라인 미진입” 계약에 COR-TUNE-009 품질 필터 후 0개 no-op 예외를 명시한다.
   - COR-TUNE-007에는 빈 결과 완료 정책을 COR-TUNE-009로 분리해 참조할 수 있게 정리한다.

---

# 예외 처리

- 사용자가 아무 suggestion도 선택하지 않은 경우는 기존처럼 실패 처리한다.
- 선택 suggestion은 있었지만 모두 품질 필터로 제외된 경우는 no-op 완료 성공 처리한다.
- no-op 완료는 학습 점수에 교정 0개 또는 선택 suggestion 전체를 반영하지 않는다.
- 일부만 품질 필터를 통과한 경우 제외된 suggestion은 저장, LangState 근거, compression payload 어디에도 섞지 않는다.
- repository 직접 빈 저장 요청은 기존 require 계약대로 실패한다.

---

# 검증 기준

- 단위 테스트:
  - 전부 제외 → `CompleteCorrectionUseCase` success.
  - 전부 제외 → save/update/history/compression/summary 미호출, correction signal만 호출.
  - correction signal update 실패 → failure.
  - 일부 제외 → LangState `correctionResult`가 저장 대상 suggestion 기준인지 확인.
- 회귀:
  - repository 직접 빈 save request fail-fast 유지.
  - 기존 저장 / rollback / Firestore sync 흐름 유지.
- `:app:testDevDebugUnitTest --tests "com.app.umma.domain.usecase.correction.CompleteCorrectionUseCaseTest"`
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
