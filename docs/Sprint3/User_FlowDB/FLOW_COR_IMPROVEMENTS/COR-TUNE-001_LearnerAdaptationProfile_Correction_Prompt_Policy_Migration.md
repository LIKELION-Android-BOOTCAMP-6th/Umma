# [Improvement] COR-TUNE-001 LearnerAdaptationProfile 기반 교정 Prompt 연동

## User Story

사용자는 별도 레벨 테스트 없이도, 자신의 현재 언어 실력에 맞는 교정을 받는다.
초급 사용자에게는 의미를 막는 핵심 오류만 최소한으로 고치고 쉬운 설명을 주며, 중급 이상 사용자에게는 더 자연스러운 표현과 문장 구조 확장까지 제안한다.
Umma는 사용자를 과하게 어려운 교정으로 압박하지 않고, 현재 능력보다 약간 더 성장할 수 있는 방향으로 교정을 안내한다.

---

# 배경

현재 교정 프롬프트는 학습자 수준을 "CEFR A1, grammarAccuracy 0.42" 같은 raw metric 숫자로 모델에 직접 전달한다.
`CHAT-TUNE-001` 핸드오버는 이 방식을 금지하고, 이미 해석이 끝난 정책(`LearnerAdaptationProfile.correctionPolicy`)을 사용하도록 요구한다.
Chat은 `BuildPromptUseCase`에서 이미 profile 기반으로 동작하고 있으나, Correction은 아직 raw metric을 쓰고 있어 같은 기준으로 정렬한다.
이 작업은 선행 작업인 `COR-FIX-07`(primaryLang/selectedLang 분리)이 머지된 상태를 전제로 한다.

---

# 완료 기준(AC)

- [ ]  `CorrectionPromptBuilder`가 raw metric 숫자(CEFR 레벨, grammarAccuracy, naturalnessScore)를 직접 프롬프트에 넣지 않는다.
- [ ]  `LearnerAdaptationProfile.correctionPolicy`의 6개 정책을 프롬프트 행동 지시로 변환해 반영한다.
    - `challengeLevel` / `correctionStyle` / `vocabularyStrategy` / `grammarStrategy` / `spokenRegisterStrategy` / `primaryLanguageSupport`
- [ ]  반복 약점(`core.focus`)이 신뢰 가능할 때만 프롬프트에 상위 1개 focus를 한 줄로 가볍게 노출한다.
- [ ]  `profile`은 도메인 경계(UseCase/ViewModel)에서 `BuildLearnerAdaptationProfileUseCase`로 만들어 `GenerateSuggestionsInput`에 주입한다. data 계층은 raw metric을 해석하지 않는다.
- [ ]  응답 schema의 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`)와 `primaryLang`/`selectedLang` 언어 기준은 그대로 유지한다. (learning signal 출력을 위한 `learningSignal` schema 확장은 본 작업 범위가 아니라 후속 **COR-TUNE-002** 소관이다.)
- [ ]  프롬프트는 `difficultyDelta`, 최종 능력 점수, 최종 CEFR 레벨을 만들지 않는다.
- [ ]  분석 근거가 부족한 사용자는 낮은 실력으로 단정하지 않고 보수적인 profile로 처리된다.

---

# 기준 문서

- `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md`
- `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md`
- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md`
- `docs/System_FlowDB/SYS_CORRECTION_INFRA/SCI-001_Correction_Contract.md`

---

# 핵심 결정

- 교정 prompt는 raw `LangState` metric을 직접 해석하지 않고, `LearnerAdaptationProfile.correctionPolicy`를 instruction text로 변환한다.
- 학습자 수준 해석은 `BuildLearnerAdaptationProfileUseCase`(domain)가 전담하고, 프롬프트 빌더는 숫자를 모르게 한다.
- `LearnerAdaptationProfile`은 저장 모델이 아니라 AI 기능이 사용할 교육 전략 read model이다.
- 정책 enum은 prompt에 이름 그대로 노출하지 않고, 실행 가능한 짧은 행동 지시로 압축한다. (Chat `BuildPromptUseCase`와 동일 패턴)
- 반복 약점(focus)은 저장된 전체가 아니라 신뢰 가능한 상위 1~2개만 prompt에 반영한다.
- Correction prompt는 사용자의 최종 점수/레벨/profile을 새로 판정하지 않으며 `difficultyDelta`를 만들지 않는다. 난이도와 성장 판단은 LearningState의 책임이다.
- 분석 근거가 부족하면 사용자를 최저 실력으로 단정하지 않고 보수적인 profile(Support/MinimalFix 등)로 처리한다.
- 응답 schema와 `primaryLang`/`selectedLang` 언어 기준(COR-FIX-07)은 변경하지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| LearningState domain | raw `LangState` metric과 evidence를 관리한다. |
| BuildLearnerAdaptationProfileUseCase | `LangState?`를 교정 정책용 `LearnerAdaptationProfile`로 해석한다. |
| CorrectionViewModel | `langState` snapshot을 profile로 변환해 교정 입력에 주입한다. |
| GenerateSuggestionsInput | `profile`을 교정 생성 입력으로 보관한다. |
| CorrectionPromptBuilder | `correctionPolicy`와 상위 focus를 짧은 행동 지시로 변환한다. raw metric을 해석하지 않는다. |
| CorrectionAiResponseMapper | 기존 응답 schema와 검증 흐름을 유지한다. |

---

# 주요 작업

1. `GenerateSuggestionsInput`에 `profile: LearnerAdaptationProfile`을 추가한다. (raw metric 대체)
2. `CorrectionViewModel`에 `BuildLearnerAdaptationProfileUseCase`를 주입하고, `langState` snapshot을 profile로 변환해 입력에 담는다.
3. `CorrectionPromptBuilder`를 재작성한다.
    - 기존 "Learner profile (CEFR/Grammar/Naturalness 숫자)" 블록을 제거한다.
    - `correctionPolicy` 6개 정책 + 상위 focus를 각각 행동 지시 문장으로 변환해 "Correction policy" 블록으로 넣는다. (enum 이름 미노출)
    - 언어 규칙(`nativeText`/`afterText`/`explanation`)과 candidate/schema 블록은 그대로 유지한다.
4. `GenerateSuggestionsInput` 생성처(Fixtures/Mapper/UseCase/RepositoryImpl 테스트)에 `profile` 인자를 반영한다.
5. `CorrectionPromptBuilderTest`에 정책별 회귀를 추가한다. (예: Support는 "최소 수정", Refine은 "뉘앙스·register" 문구 / raw 숫자 미노출)

---

# 예외 처리

- `langState`가 null이거나 분석 근거가 없으면 `BuildLearnerAdaptationProfileUseCase`가 가장 보수적인 profile(Support/MinimalFix/KeepSimpleWords 등)을 돌려주므로, 프롬프트도 안전한 최소 교정으로 떨어진다.
- confidence가 Low면 점수가 높아 보여도 challenge가 올라가지 않는다. (UseCase가 보장, 프롬프트는 결과만 반영)
- focus가 없거나 신뢰도가 낮으면 focus 라인은 생략되어 약점을 억지로 끄집어내지 않는다.
- `correctionPolicy`는 내부 enum이라 unknown 값이 들어오지 않으며, when 분기는 모든 enum을 빠짐없이 처리한다.
- 프롬프트 응답 schema는 변하지 않으므로 기존 mapper / 저장 / 완료 흐름이 깨지지 않는다.

---

# Prompt 비대화 방지 원칙

- 정책 enum을 1:1로 모두 prompt line에 나열하지 않고, 실행 가능한 행동 지시로 압축한다.
- 실패 사례가 나올 때마다 금지 문장을 덧붙이지 않고, 실패를 유도한 기존 지시를 먼저 찾아 더 넓은 행동 원칙으로 바꾼다.
- `하지 마라`보다 `어떻게 교정하라`를 우선한다.
- 내부 enum 이름(`MinimalFix`, `ChallengeLevel.Stretch` 등)과 raw metric(`grammarAccuracy=0.42`)을 prompt에 직접 넣지 않는다.

---

# 검증 기준

- 서로 다른 `correctionPolicy`(예: Support 계열 vs Refine 계열)로 빌드했을 때 해당 정책 행동 문구가 노출되고, raw 숫자("%.2f" 포맷)가 더 이상 등장하지 않는지 확인한다.
- focus가 신뢰 가능할 때 한 줄 노출 / 낮거나 없을 때 생략되는지 확인한다.
- profile 해석이 data 계층이 아니라 domain(UseCase)에서 일어나는지 확인한다.
- 응답 schema와 `primaryLang`/`selectedLang` 언어 기준이 유지되는지 확인한다.
- `profile` 인자 반영 후 correction 단위 테스트가 전체 통과하는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드가 통과하는지 확인한다.

---

# 후속

- learning signal 출력(`CorrectionLearningSignal`)은 본 작업 위에서 **COR-TUNE-002** 로 분리해 진행한다. 응답 schema 의 `learningSignal` 중첩, 신호 도메인 타입, mapper 정규화, `CorrectionResult.learningSignals` 집계는 그 작업 범위이며 본 이슈 머지를 전제로 한다.