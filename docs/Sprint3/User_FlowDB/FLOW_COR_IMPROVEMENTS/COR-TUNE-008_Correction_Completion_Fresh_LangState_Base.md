# [Improvement] COR-TUNE-008 교정 완료 시 최신 LangState 기준 분석 (stale snapshot 덮어쓰기 방지)

## User Story

사용자는 교정 결과를 검토하다 저장할 때, 그 사이 다른 경로(원격 동기화 등)로 갱신된 자신의 학습 상태가 사라지지 않기를 기대한다.
Umma는 교정 완료 시 화면이 들고 있던 오래된 학습 상태가 아니라 저장 직전의 최신 학습 상태를 기준으로 분석·저장해, 갱신된 지표를 덮어쓰지 않는다.
이 정리는 사용자에게 보이지 않으며, 기존 저장/완료 흐름은 그대로다.

---

# 배경

교정 완료는 `CorrectionViewModel.launchCompletion()`이 `BuildLangStateUpdateInputCommand`를 조립하고,
`CompleteCorrectionUseCase`가 local-first 저장 + LangState/Summary 갱신 + Firestore sync를 묶는다.
LangState 분석(`DefaultLangStateAnalysisPolicy`)은 `command.currentState`를 base로 이동평균/evidence를 계산하고,
`LearningStateRepoImpl.updateLanguageState()`가 그 결과(`preparedState`)로 `langStates[lang]`을 **통째로 교체**한다.

문제는 base로 쓰는 `currentState`가 화면이 동결한 스냅샷이라는 점이다.
교정 생성이 시작되면 ViewModel은 `generationLaunched` 가드로 이후 `GlobalLangState` emit을 무시하므로,
화면이 들고 있는 `langStateSnapshot`은 **Generating 진입 시점에 동결**된다(사용자가 카드를 보는 시간 = 수 분 가능).
사용자가 카드를 검토하는 동안 원격 sync 등이 `langStates[lang]`을 갱신했더라도,
완료 저장은 동결된 옛 스냅샷을 base로 계산한 `preparedState`로 최신값을 **덮어쓰는 lost update**가 발생한다.

저장소는 stale 입력을 인지해 중복 분석(dedup)만 방어할 뿐, 분석 base를 최신으로 재기준화하지는 않는다.
이 작업은 완료 직전에 최신 LangState를 다시 읽어 분석 base로 사용해 lost update 창을 닫는다.

---

# 완료 기준(AC)

- [ ] 교정 완료 시 분석 base(`currentState`)로 화면이 동결한 snapshot이 아니라 최신 LangState(`langStates[lang]`)를 사용한다.
- [ ] 교정 결과 검토 중 다른 경로(원격 sync 등)가 같은 언어 LangState를 갱신했을 때, 완료 저장이 그 최신값을 덮어쓰지 않는다.
- [ ] 완료 재시도/재진입 idempotency(`analysisEventId` dedup)는 그대로 유지된다.
- [ ] 최신 LangState 조회 실패 시 동결 snapshot fallback으로 완료 자체는 막지 않는다.
- [ ] 기존 저장 / rollback / Firestore sync 흐름은 무손상으로 유지한다.

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md` (suggestion → 저장 계약)
- `docs/handover/LS-008_LANGSTATE_INPUT_READY.md` (`LangStateUpdateInput` 조립 정책 / `currentState` 의미)
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md` (이동평균 base 계산)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-007_Correction_Flashcard_Dedup_Quality.md` (선행 — 저장 끝단 정리)

---

# 핵심 결정

- 분석 base는 **완료 직전 최신값**으로 잡는다. `launchCompletion()`이 명령 조립 직전 `observeLearningState().first()`로
  최신 `GlobalLangState`를 다시 읽어 `langStates[lang]`을 `command.currentState`에 넣는다.
  `preload`가 끝난 StateFlow라 `.first()`는 캐시를 즉시 반환해 비용이 거의 없다.
- 최신 state 선택과 fallback **결정 로직은 순수 함수로 분리**한다(`resolveCompletionBaseState`). IO(`first()`)만 ViewModel이 하고,
  결정은 helper에 둬 기존 패턴(`computeCompletionLaunch` / `applyGenerationOutcome` 등 `CorrectionUiState` 순수 helper)과 맞춘다.
- **fallback 보장**: 최신 조회가 실패(null)하거나 최신 global에 해당 언어 항목이 없으면 동결 snapshot으로 안전하게 떨어진다. 완료 자체는 막지 않는다.
- **선택 보존**: 선택 카드(`selectedSuggestions`)와 이벤트 fingerprint(`stableEventParts`)는 동결 snapshot 그대로 유지해 사용자 선택을 보존한다.
- **dedup 무변경**: `analysisEventId` 중복 방어(`ApplyLanguageStateUpdateUseCase` / `LearningStateRepoImpl`)는 건드리지 않아 재시도/재진입 idempotency가 유지된다. 이번 변경은 "base를 최신으로" 만드는 것뿐이다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionViewModel | 완료 직전 `observeLearningState().first()`로 최신 GlobalLangState를 읽고(IO), `resolveCompletionBaseState`로 base를 골라 명령에 싣는다. |
| resolveCompletionBaseState (CorrectionUiState 순수 helper) | `freshGlobal.langStates[lang]` 우선, 없으면 동결 snapshot fallback. 순수 결정 로직만 담당(저장/IO 없음). |
| BuildLangStateUpdateInputUseCase | 전달받은 `currentState`로 `LangStateUpdateInput`을 조립한다. **무변경**. |
| DefaultLangStateAnalysisPolicy / LearningStateRepoImpl | 받은 base로 분석·저장한다. **무변경**(dedup 포함). |

---

# 주요 작업

1. **순수 helper 추가** (`CorrectionUiState.kt`)
   - `resolveCompletionBaseState(freshGlobal, lang, frozenSnapshot)` 추가. `freshGlobal?.langStates?.get(lang) ?: frozenSnapshot`.

2. **완료 경로에서 최신 base 사용** (`CorrectionViewModel.launchCompletion()`)
   - 명령 조립 직전 `runCatching { observeLearningState().first() }.getOrNull()`로 최신 global을 읽고
     `resolveCompletionBaseState`로 base를 골라 `command.currentState`에 넣는다.
   - 선택 카드/`stableEventParts`/dedup은 그대로 둔다.

3. **테스트 보강** (`CorrectionUiStateTest`)
   - 최신 우선 / 최신 null → 동결 fallback / 최신 global에 언어 미존재 → 동결 fallback 3건.
   - Android 프레임워크·mock·빌드 설정 변경 없이 순수 함수로 검증한다(기존 helper 테스트 패턴).

---

# 예외 처리

- 최신 조회(`first()`)가 예외/취소로 실패하면 null → 동결 snapshot fallback. 완료 흐름은 막지 않는다.
- 최신 global에 해당 언어 LangState가 없으면(예: 다른 언어만 존재) 동결 snapshot fallback.
- 단말 1대·일반 흐름에서는 동결 snapshot과 최신값이 같아 동작 차이가 없다. lost update는 멀티 디바이스/백그라운드 sync 환경에서만 드러난다.
- 분석 base만 최신으로 바뀌고 저장소의 통째 교체 동작 자체는 변경하지 않으므로, 기존 저장/rollback/sync 계약은 무손상이다.

---

# 검증 기준

- 단위 테스트:
  - 동결 snapshot(낮은 지표) 이후 최신 state(높은 지표)가 들어온 경우 → base로 **최신값** 사용 확인.
  - 최신 조회 null → 동결 snapshot fallback 확인.
  - 최신 global에 해당 언어 미존재 → 동결 snapshot fallback 확인.
- 회귀: `CompleteCorrectionUseCaseTest`(저장→summary→update→record→compress 순서, 실패 비롤백), dedup 무변경 확인.
- `:app:compileDevDebugKotlin` 빌드 통과.

---

# 관련 후속 (범위 밖)

- `LangStateAnalysisPolicy`의 점수 해석 정합성(점수 부적격 신호의 상향 이동 등)과 focus 노출 지연은
  `docs/handover/CHAT-TUNE-005_CORRECTION_SIGNAL_LANGSTATE_FOLLOWUP_HANDOVER.md`로 LearningState/Chat 담당에 인계한다(COR 범위 밖).
