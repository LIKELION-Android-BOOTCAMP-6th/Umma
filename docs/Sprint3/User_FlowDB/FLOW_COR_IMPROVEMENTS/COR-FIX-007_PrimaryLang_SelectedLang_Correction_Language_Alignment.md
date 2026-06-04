# [Refactor] COR-FIX-07 primaryLang/selectedLang 교정 언어 기준 정합

## User Story

사용자는 교정 카드를 볼 때, 카드 앞면 문장과 설명을 자신이 이해하는 학습 기준 언어(primaryLang)로 받는다.
교정된 문장은 자신이 배우는 언어(selectedLang) 그대로 유지된다.
한국어를 기준 언어로 쓰지 않는 사용자도 자신이 이해하는 언어로 교정 설명을 받을 수 있다.

---

# 배경

`#234`(LANG-FIX)는 `primaryLang`(학습 기준·설명 언어)과 `selectedLang`(학습 대상·데이터 소속 언어)의 의미를 분리하고 Chat / LearningState / Statistics 흐름을 정리했다.
다만 Correction 생산 로직은 의도적으로 손대지 않아, 교정 프롬프트가 카드 앞면(nativeText)과 설명(explanation)을 여전히 한국어로 하드코딩하고 있었다.
이 문서는 Correction을 같은 언어 기준으로 맞추는 정합 작업을 정의한다.

---

# 완료 기준(AC)

- [x]  교정 생성 입력(`GenerateSuggestionsInput`)에 `primaryLang`이 포함되어 교정 프롬프트까지 전달된다.
- [x]  교정 카드 앞면 문장(`nativeText`)과 설명(`explanation`)이 한국어 고정이 아니라 사용자의 `primaryLang`으로 생성된다.
- [x]  교정된 문장(`afterText`)과 카드 저장 언어는 기존대로 `selectedLang`(`langState.lang`)을 따른다.
- [x]  화면 상태(`CorrectionUiState`)에 `primaryLanguage`가 노출되고, 사용자 설정(`userPref.primaryLang`)에서 채워진다.
- [x]  `primaryLang`은 교정 시작 조건(Ready)에 영향을 주지 않고, 값이 없으면 한국어(KO)로 대체해 흐름이 멈추지 않는다.
- [x]  응답 필드명 `nativeText`는 기존 호환을 위해 유지하되, 의미는 "`primaryLang` 기준 앞면 문장"으로 정리한다.
- [x]  기존 후보 추출 / 생성 / 저장 / 완료 흐름과 응답 schema는 변경되지 않는다.

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION.md`
- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md`
- `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md`
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-001_Language_State_Model_Structure.md`

---

# 핵심 결정

- `primaryLang`은 사용자가 학습을 이해하고 설명을 받을 기준 언어다. 모국어로 단정하지 않는다.
- `selectedLang`은 현재 교정 대상이자 데이터 소속의 기준 언어이며, `langState.lang`이 단일 출처다.
- 교정 결과의 앞면/설명은 `primaryLang` 기준으로, 교정 후 문장은 `selectedLang` 기준으로 생성한다.
- 응답 필드명 `nativeText`는 기존 저장/화면 계약과의 호환명이며, 의미는 "모국어 문장"이 아니라 "`primaryLang` 기준 앞면 문장"이다. 필드명을 바꾸지 않는다.
- `primaryLang`은 교정 생성의 Ready 게이트 조건이 아니다. 값이 없어도 안전한 fallback(KO)으로 생성을 진행한다.
- 언어명 변환은 특정 언어에 고정하지 않고 `LangCode` 기준 매핑으로 처리하며, Chat의 `BuildPromptUseCase`와 동일한 매핑을 따른다.
- 프롬프트 빌더(data 계층)는 언어 정책을 새로 판단하지 않고, 입력으로 받은 `primaryLang` / `selectedLang`만 사용한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| GlobalLangState | `userPref.primaryLang` / `selectedLang`을 확장으로 노출 |
| CorrectionViewModel | `GlobalLangState`에서 `primaryLang`을 읽어 교정 입력에 주입 |
| GenerateSuggestionsInput | `primaryLang`(앞면/설명 기준)과 `langState.lang`(교정 대상)을 함께 보관 |
| CorrectionPromptBuilder | 입력의 `primaryLang` / `selectedLang`을 프롬프트 언어 규칙으로 변환 |
| CorrectionAiResponseMapper | 응답 schema(`nativeText` 등) 유지, `selectedLang` 검증 유지 |

---

# 주요 작업

1. `GenerateSuggestionsInput`에 `primaryLang` 필드를 추가한다. `langState.lang`은 `selectedLang`의 단일 출처로 유지한다.
2. `GlobalLangState.primaryLang` 확장을 기존 `selectedLang` 확장과 대칭으로 추가한다.
3. `CorrectionUiState`에 `primaryLanguage`를 추가하고 `toCorrectionUiState()`에서 `userPref.primaryLang`으로 채운다. Ready 게이트 조건에는 포함하지 않는다.
4. `CorrectionViewModel`이 교정 입력 조립 시 `ready.primaryLanguage ?: LangCode.KO`로 `primaryLang`을 전달한다.
5. `CorrectionPromptBuilder`에서 하드코딩된 "Korean (ko)"를 제거하고, `primaryLang` 기준 언어명으로 앞면/설명 언어를 동적 지정한다. `afterText`는 `selectedLang`을 유지한다.
6. `CorrectionAiResponseMapper` / `CorrectionSuggestion`의 `nativeText` 관련 KDoc을 "`primaryLang` 기준 앞면 문장(필드명 호환 유지)"으로 정정한다.

---

# 예외 처리

- `userPref`가 없으면 `selectedLearningLanguage`와 `primaryLanguage`가 모두 null이며, `primaryLang`은 KO로 대체되어 교정이 멈추지 않는다.
- `primaryLang == selectedLang`인 경우(앞면과 교정문이 같은 언어), 프롬프트가 "translation"을 강제하지 않고 "앞면 문장"으로 자연스럽게 동작한다.
- 응답 필드명 `nativeText`를 유지했으므로 기존 mapper의 candidateId 매칭과 `selectedLang` 검증이 깨지지 않는다.
- 화면 회전 / recomposition으로 `primaryLanguage` 노출이 흔들리지 않는다.

---

# 검증 기준

- 교정 카드 앞면/설명이 `primaryLang`을, 교정 문장이 `selectedLang`을 따르는지 확인한다.
- `userPref` 부재 시 `primaryLanguage`가 null이고 KO fallback으로 생성이 진행되는지 확인한다.
- `nativeText` 응답 필드명과 `selectedLang` 기준 데이터 소속 / 검증 흐름이 유지되는지 확인한다.
- `GenerateSuggestionsInput` 생성처(테스트/픽스처)가 `primaryLang`을 반영하고 correction 단위 테스트가 통과하는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드가 통과하는지 확인한다.