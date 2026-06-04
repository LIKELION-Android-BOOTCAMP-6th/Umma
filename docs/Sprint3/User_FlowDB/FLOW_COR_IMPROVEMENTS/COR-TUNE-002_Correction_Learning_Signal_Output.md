# [Improvement] COR-TUNE-002 교정 학습 신호(CorrectionLearningSignal) 출력 계약

## User Story

사용자가 교정을 받을수록, 시스템은 그 교정에서 관찰된 오류 범주·개선 유형·말투·심각도 신호를 구조화해 사용자의 장기 학습 상태(LangState)를 더 정확히 갱신한다.
교정 AI는 사용자의 최종 능력 점수나 레벨을 판정하지 않고, 교정하면서 이미 보고 있는 source/corrected 문장과 교정 의도만 신호로 넘긴다.
이를 통해 다음 교정과 복습은 사용자가 실제로 자주 틀리는 지점에 맞춰 더 잘 정렬된다.

---

# 배경

`CHAT-TUNE-001` 핸드오버는 교정 AI가 LearningState에 제공해야 하는 관찰 신호 계약(**Domain 계약 v2**)을 고정했다.
Correction은 “사용자 능력 평가자”가 아니라 “교정 과정에서 관찰한 신호 제공자”다. 교정 결과를 만들면서 이미 판단한
오류 범주, 언어별 세부 feature, 개선 유형, 변경 fragment, 말투(register), 심각도(severity), 의미 보존, confidence를 구조화한다.
LearningState는 이 신호를 받아 장기 metric / evidence / active focus / challenge guard를 계산한다.

이 작업은 선행 작업인 `COR-TUNE-001`(교정 프롬프트의 correctionPolicy 연동)이 머지된 상태를 전제로 한다.
응답 schema의 핵심 4필드와 `primaryLang`/`selectedLang` 언어 기준(COR-FIX-07)은 그대로 두고, 그 위에 신호 출력을 더한다.

---

# 완료 기준(AC)

- [ ]  learning signal 도메인 타입 7종을 신규 추가한다.
    - `CorrectionLearningSignal` / `LanguageFeatureSignal` / `CorrectionEditSpan` / `CorrectionIssueCategory` / `CorrectionImprovementType` / `SpokenRegister` / `CorrectionSeverity`
- [ ]  `CorrectionSuggestion`에 `learningSignal: CorrectionLearningSignal?`(nullable 기본값), `CorrectionResult`에 `learningSignals: List<CorrectionLearningSignal>`(기본 emptyList)을 추가한다.
- [ ]  응답 schema가 suggestion당 `learningSignal` 객체를 중첩한다. 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`)와 `primaryLang`/`selectedLang` 기준은 그대로 유지한다.
- [ ]  `CorrectionAiResponseMapper`가 learningSignal을 정규화한다.
    - enum은 allowlist로 관대 파싱하고, `issueCategories`/`languageFeatures`/`improvementTypes`/`editSpans`는 각각 최대 3개로 캡한다.
    - `confidence`는 `0.0..1.0`만 허용하고, 없으면 null을 허용한다.
    - `languageFeatures.featureKey`는 `{LANG}.{FeatureName}` namespace를 지키고 `lang`은 `selectedLang`과 일치해야 한다.
- [ ]  위반 처리: unknown enum 또는 confidence 범위 밖이면 **해당 signal만 drop**하고 suggestion·핵심 4필드 흐름은 유지한다. unknown/형식 위반 featureKey는 **그 feature만 제외**한다. (drop/제외 시 Logcat에 candidateId + 위반 값 기록)
- [ ]  learningSignal 누락이나 파싱 실패가 suggestion 생성을 막지 않는다. (핵심 4필드 누락만 기존대로 실패 처리)
- [ ]  learning signal은 correction candidate당 1개만 반환한다. `sourceTurnId`는 원본 turnId가 있을 때만 채우고, 없어도 `sourceTurnIndex`는 후보 추적 fallback으로 반드시 채운다.
- [ ]  `CompleteCorrectionUseCase`가 선택된 suggestion의 신호를 `CorrectionResult.learningSignals`로 집계해 completion pipeline으로 넘긴다.
- [ ]  Correction은 최종 `grammarAccuracy`/`vocabularyLevel`/`fluencyScore`/최종 CEFR level/`LearnerAdaptationProfile`/`difficultyDelta`, 그리고 음성 유창성(pause/hesitation/pronunciation)을 만들지 않는다. (핸드오버 금지 사항)

---

# 기준 문서

- `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md` (Domain 계약 v2 / Issue Category / Language Feature / Improvement Type / Register / Severity / Edit Span / JSON 응답 예시)
- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md`
- `docs/System_FlowDB/SYS_CORRECTION_INFRA/SCI-001_Correction_Contract.md`
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-001_LearnerAdaptationProfile_Correction_Prompt_Policy_Migration.md` (선행 작업)

---

# 핵심 결정

- Correction은 신호를 **생산·정규화**해 completion pipeline으로 넘기는 역할만 한다. 신호를 근거로 최종 점수/레벨을 계산하지 않는다.
- LearningState가 `CorrectionResult.learningSignals`를 받아 evidence/focus/score를 갱신하는 로직은 **별도 작업(다른 담당)** 이며 본 작업 범위가 아니다. 본 작업은 계약 생산까지다.
- 신호 도메인 타입은 `domain/model/learningstate` 패키지에 둔다. `CorrectionResult`(learningstate 패키지)가 참조해야 하므로 learningstate→correction 역의존을 피하기 위함이다.
- 핵심 4필드는 기존대로 실패 처리(Error phase)하되, **learningSignal 파싱 실패는 절대 suggestion을 죽이지 않는다**(신호=null, suggestion 유지).
- AI 응답의 enum은 allowlist 기반으로 관대 파싱하고, 허용되지 않은 값/범위 밖 값은 해당 signal만 drop하거나 해당 feature만 제외한다.
- 응답 schema와 `primaryLang`/`selectedLang` 언어 기준(COR-FIX-07)은 변경하지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionPromptBuilder | 응답 schema에 suggestion당 `learningSignal` 중첩과 신호 규칙(허용 enum/캡/featureKey/register·severity/confidence)을 명시한다. |
| CorrectionAiResponseMapper | AI 응답 DTO를 domain 신호 계약으로 정규화한다. 핵심 4필드 검증은 유지하고, 신호는 관대하게 drop/cap/filter한다. |
| CorrectionLearningSignal (domain) | LearningState 갱신 입력으로 재사용되는 관찰 신호 계약. presentation 전용 모델로 두지 않는다. |
| CompleteCorrectionUseCase | 선택된 suggestion의 신호를 `CorrectionResult.learningSignals`로 집계한다. |
| LearningState domain (범위 밖) | 신호를 입력으로 evidence/focus/score 갱신 여부를 판단한다. |
| Repository/DataSource | 변환된 결과를 저장/전달할 뿐, 신호의 교육적 의미를 해석하지 않는다. |

---

# 주요 작업

1. **신호 도메인 타입 신규 파일 추가** (`domain/model/learningstate/CorrectionLearningSignalModels.kt`)
    - data class: `CorrectionLearningSignal` / `LanguageFeatureSignal` / `CorrectionEditSpan`
    - enum: `CorrectionIssueCategory`(GrammarForm/WordOrder/SentenceCompleteness/VocabularyChoice/Collocation/Register/MissingContext/MeaningMismatch) / `CorrectionImprovementType`(GrammarFixed/StructureExpanded/MoreNaturalVerb/BetterCollocation/SpokenExpressionAdded/ShortenedForClarity/MadeMoreCasual/MadeMorePolite) / `SpokenRegister`(Simple/EverydaySpoken/NativeLikeCasual/Formal) / `CorrectionSeverity`(BlockingMeaning/MajorPattern/MinorForm/NaturalnessOnly)
2. **도메인 모델 확장**: `CorrectionSuggestion.learningSignal` / `CorrectionResult.learningSignals` 추가(둘 다 nullable/기본값 → 기존 생성처 무변경).
3. **CorrectionPromptBuilder에 신호 schema 지시 추가**: schema 줄에 `learningSignal` 중첩 + 허용 enum·배열 캡(≤3)·featureKey 규칙·register/severity 단일·confidence 0~1·meaningPreserved·edit는 변경 fragment만·애매하면 빈 배열/낮은 confidence 허용을 compact 규칙으로 명시. (핸드오버 JSON 예시와 정확히 일치)
4. **CorrectionAiResponseMapper 확장**: DTO에 nested learningSignal(전 필드 nullable) 추가, `runCatching`으로 신호 파싱을 감싸 suggestion 보호. enum allowlist 파싱 / `take(3)` 캡 / confidence 범위 / featureKey namespace·lang 검증 / drop·feature 제외 + Logcat. 초기 featureKey allowlist는 mapper 내 상수(EN.Article/Preposition/Tense, JA.Particle/Honorific/VerbConjugation 등).
5. **완료 경로 합류**: `CompleteCorrectionUseCase.buildCorrectionResult`에서 `learningSignals = selectedSuggestions.mapNotNull { it.learningSignal }` 집계. (ViewModel/저장 경로 무변경)
6. **테스트/픽스처 반영**: mapper 테스트 중심으로 신규 케이스 추가. 신규 필드는 nullable/기본값이라 기존 생성처는 무변경.

---

# 예외 처리

- learningSignal 파싱 실패 / unknown enum → 그 signal만 drop(또는 feature만 제외), correction 저장과 핵심 4필드 흐름은 무손상.
- `confidence` 범위 밖 → 해당 signal drop. 없으면 null 허용(LearningState가 medium-low로 취급).
- unknown / 형식 위반 featureKey → 그 feature만 제외, signal 전체는 유지.
- `editSpans`가 비어 있어도 signal은 유지한다. (LearningState difficulty guard 정밀도만 낮아짐)
- `sourceTurnId`가 없어도 `sourceTurnIndex`로 후보를 추적할 수 있어야 한다.
- 핵심 교정 문장(핵심 4필드) 자체가 파싱 불가일 때만 해당 candidate 전체 실패로 처리한다.

---

# 신호 정규화 원칙

- 신호 출력은 응답 schema를 확장하지만, 빌더의 규칙 블록은 enum을 1:1로 장황하게 나열하지 않고 실행 가능한 짧은 지시로 압축한다.
- mapper는 “신뢰할 수 없으면 버린다”를 기본값으로 둔다. 오염된 신호 하나가 교정 결과 저장 전체를 막지 않는다.
- 신호는 LearningState 갱신을 돕는 보조 입력이므로, 판단이 애매하면 빈 배열 / 낮은 confidence / signal 생략을 허용한다.

---

# 검증 기준

- 핸드오버 예시 JSON → learningSignal이 정상 파싱되는지 확인한다.
- unknown issueCategory/improvementType/register/severity → 해당 signal=null, suggestion은 생존하는지 확인한다.
- `confidence` 범위 밖 → signal drop / 누락 → null 허용되는지 확인한다.
- 배열 4개 입력 → 3개로 캡되는지 확인한다.
- unknown featureKey / `lang≠selectedLang` → 그 feature만 제외되고 signal은 유지되는지 확인한다.
- learningSignal 누락 → suggestion.learningSignal=null, 실패가 아닌지 / 핵심 4필드 누락 → 기존대로 실패하는지 확인한다.
- `CompleteCorrectionUseCase`가 선택 suggestion의 learningSignals를 집계하는지 확인한다.
- 응답 schema와 `primaryLang`/`selectedLang` 언어 기준이 유지되는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드가 통과하는지 확인한다.
