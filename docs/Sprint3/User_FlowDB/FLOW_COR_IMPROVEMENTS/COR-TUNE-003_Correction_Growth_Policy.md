# [Improvement] COR-TUNE-003 교정 성장 정책(CorrectionGrowthPolicy) 연동

## User Story

사용자는 교정 결과가 너무 어렵거나 원래 의도와 다르게 바뀌지 않기를 기대한다.
Umma는 사용자의 현재 언어 실력을 기준으로, 의미를 보존하면서 지금보다 조금 더 나은 "다음 단계" 문장으로 교정한다.
초급자는 의미가 통하는 짧고 다시 말할 수 있는 문장을 받고, 고급자는 collocation·register·뉘앙스까지 다듬은 교정을 받는다.
교정 AI는 사용자의 최종 능력 점수나 CEFR 레벨을 판정하지 않고, 이미 해석이 끝난 성장 정책만 받아 교정 강도를 조절한다.

---

# 배경

`CHAT-TUNE-004` 핸드오버는 Correction이 입력으로 받아야 할 교정 성장 정책 계약(`CorrectionGrowthPolicy`)을 고정했다.
현재 Correction은 4단계 `ChallengeLevel` 기반 `CorrectionAdaptationPolicy`로 교정 강도를 받는다.
이 작업은 이를 6단계 `CorrectionGrowthBand` 기반 `CorrectionGrowthPolicy`로 **완전 교체**한다.

방향은 `LearningState/Profile -> Correction`이다. (`COR-TUNE-002`가 정의한 `Correction -> LearningState` 신호 방향의 반대)
전달 경로는 `LangState` → `LearnerAdaptationProfile.correctionPolicy(=CorrectionGrowthPolicy)` → Correction prompt 입력이다.
Correction은 raw `LangState` metric을 직접 해석하지 않고, 이미 해석된 정책만 사용한다.

이 작업은 선행 작업인 `COR-TUNE-001`(교정 프롬프트의 correctionPolicy 연동), `COR-TUNE-002`(learning signal 출력 계약), `COR-TUNE-002-FIX`(learning signal 정규화 보수적 drop 강화)가 머지된 상태를 전제로 한다.
응답 schema의 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`)와 `primaryLang`/`selectedLang` 언어 기준(COR-FIX-07), 그리고 `CorrectionLearningSignal v2` 출력 계약(`COR-TUNE-002` + `COR-TUNE-002-FIX` 강화판)은 그대로 유지한다.

---

# 완료 기준(AC)

- [ ] 교정 성장 정책 도메인 타입을 신규 추가한다.
    - `CorrectionGrowthBand`(MeaningFirst/PatternFix/SentenceShape/EverydayNatural/ConnectedExpression/NuanceRefine)
    - `CorrectionGrowthPolicy`(band, scope, grammar, vocabulary, sentenceExpansion, register, explanation, newExpressionLimit, primaryLanguageSupport, meaningPreservation)
    - 세부 enum: `CorrectionScopePolicy` / `GrammarCorrectionPolicy` / `VocabularyGrowthPolicy` / `SentenceExpansionPolicy` / `RegisterCorrectionPolicy` / `CorrectionExplanationPolicy` / `NewExpressionLimitPolicy` / `MeaningPreservationPolicy`
- [ ] `primaryLanguageSupport`는 기존 `LearnerAdaptationProfile`의 `PrimaryLanguageSupportPolicy`를 재사용한다. (Correction 전용 중복 enum을 만들지 않는다)
- [ ] `LearnerAdaptationProfile.correctionPolicy` 타입을 `CorrectionAdaptationPolicy` → `CorrectionGrowthPolicy`로 전환한다.
- [ ] 기존 4단계 `ChallengeLevel` 기반 `CorrectionAdaptationPolicy`와 Correction 전용 enum(`CorrectionStylePolicy`/`VocabularyStrategyPolicy`/`GrammarStrategyPolicy`/`SpokenRegisterStrategy`)을 제거한다. (`ChallengeLevel`은 chat 경로 미사용)
- [ ] `BuildLearnerAdaptationProfileUseCase`가 LangState/profile에서 교정용 band를 산출하고, band별 기본 정책 + confidence/focus 보수 조정으로 `CorrectionGrowthPolicy`를 만든다.
- [ ] band 산출은 Chat band(`ConversationAbilityBand`)를 그대로 복사하지 않고 Correction 전용으로 계산한다.
- [ ] 초급 band(`MeaningFirst`/`PatternFix`)는 의미 보존과 핵심 패턴 수정 중심, 새 표현·문장 확장이 제한된다.
- [ ] 중급 band(`SentenceShape`/`EverydayNatural`)는 짧은 문장 구조와 일상 표현을 조금씩 확장한다.
- [ ] 고급 band(`ConnectedExpression`/`NuanceRefine`)는 register/collocation/뉘앙스 중심 교정이 가능하다.
- [ ] 모든 band에서 사용자의 원래 의도와 문장 길이가 과하게 바뀌지 않도록 prompt 지시로 방어한다.
- [ ] 하나의 높은 지표만으로 상위 band로 올라가지 않고, low confidence면 한 단계 낮춘다.
- [ ] `CorrectionPromptBuilder`가 `CorrectionGrowthPolicy`를 행동 지시 문장으로 변환한다. (enum 이름·점수·레벨 비노출)
- [ ] Correction prompt 경로에 4단계 정책과 6단계 성장 정책이 동시에 들어가지 않는다.
- [ ] `CorrectionGrowthPolicy`에는 raw numeric metric이 들어가지 않는다. (difficultyDelta·최종 CEFR·음성 지표 비포함)
- [ ] 기존 `CorrectionLearningSignal v2` 출력 계약과 응답 schema 핵심 4필드는 변경 없이 유지된다.

---

# 기준 문서

- `docs/handover/CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER.md` (교정 성장 정책 계약 / band / 세부 정책 / band별 기본 매핑 / 책임 경계)
- `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-004_Correction_Growth_Policy.md` (개선 스펙 / band 산출 우선순위)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-001_LearnerAdaptationProfile_Correction_Prompt_Policy_Migration.md` (선행 작업 / correctionPolicy 연동)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002_Correction_Learning_Signal_Output.md` (반대 방향 신호 계약 / 유지 대상)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (강화된 신호 정규화 / 프롬프트 learningSignal 규칙 보존 대상)
- `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-001/CHAT-TUNE-001-C_LearnerAdaptationProfile.md`

---

# 핵심 결정

- Correction은 성장 정책의 **소비자**다. 정책을 받아 prompt 문장으로 바꾸고 교정 결과를 생성할 뿐, 능력 점수/레벨/band를 저장하지 않는다.
- `CorrectionGrowthPolicy`는 저장 모델이 아니라 AI 기능을 위한 domain read model이다. `domain/model/learningstate` 패키지에 둔다. (`LearnerAdaptationProfile`이 참조)
- `LangState → band` 산출은 **이번 작업에서 인터림 휴리스틱으로 구현**하되, evidence/activeFocus 기반 정밀 산출은 **LearningState 담당자**가 `CHAT-TUNE-004` 기준으로 정교화한다. 해당 함수에 KDoc/라인 주석으로 정교화 지점을 명시한다.
- band별 기본 정책 매핑 표(`CHAT-TUNE-004` "Band별 기본 정책")는 계약의 일부이므로 모델 파일(`defaultsForBand`)에 둔다.
- Correction AI 응답 **DTO/mapper는 변경하지 않는다.** AC의 "의도/길이 과변경 방어"는 prompt 지시(NoExpansion·Strict 계열 문구)로만 보장한다.
- 나이 비유("아기 수준" 등)는 팀 내부 이해용이며 코드·prompt·저장 모델·사용자 화면에 넣지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| LearningState domain | `LangState`와 evidence를 기반으로 교정 성장 정책을 계산한다. (band 산출 정밀화 담당) |
| BuildLearnerAdaptationProfileUseCase | `LearnerAdaptationProfile.correctionPolicy`(=`CorrectionGrowthPolicy`)를 생성한다. 이번 작업은 인터림 band 산출까지. |
| CorrectionGrowthPolicy (domain) | Correction prompt가 받는 교정 강도 정책 계약. presentation 전용 모델로 두지 않는다. |
| Correction 담당 영역 | 전달받은 policy를 prompt 문장으로 바꾸고 교정 결과를 생성한다. |
| CorrectionPromptBuilder | `CorrectionGrowthPolicy`를 사람이 읽는 행동 지시로 변환한다. learningSignal schema/규칙(v2 + COR-TUNE-002-FIX 강화판)은 그대로 유지한다. |
| Correction mapper (범위 밖) | 교정 후 learning signal을 기존 v2 계약으로 정규화한다. 이번 작업에서 변경하지 않는다. |
| Chat (범위 밖) | Correction growth policy를 직접 사용하지 않는다. |

---

# 주요 작업

1. **성장 정책 도메인 타입 신규 파일 추가** (`domain/model/learningstate/CorrectionGrowthPolicyModels.kt`)
    - `CorrectionGrowthBand` + `CorrectionGrowthPolicy` + 세부 enum 8종. `primaryLanguageSupport`는 기존 `PrimaryLanguageSupportPolicy` 재사용.
    - 동반 객체에 `defaultsForBand(band)` 추가 — `CHAT-TUNE-004` "Band별 기본 정책" 표 1:1 매핑.
2. **기존 4단계 정책 제거 + 필드 교체** (`LearnerAdaptationModels.kt`)
    - `correctionPolicy` 타입 전환. `CorrectionAdaptationPolicy`/`ChallengeLevel`/`CorrectionStylePolicy`/`VocabularyStrategyPolicy`/`GrammarStrategyPolicy`/`SpokenRegisterStrategy` 삭제.
3. **band 산출 브리지 구현** (`BuildLearnerAdaptationProfileUseCase`)
    - `buildCorrectionPolicy` 재작성: `chooseCorrectionGrowthBand(...)` → `defaultsForBand(band)` + confidence/focus 보수 조정.
    - `chooseCorrectionGrowthBand`: `CHAT-TUNE-004` "산출 우선순위" 6단계의 인터림 구현. 입력은 grammar/vocabulary/fluency/naturalness stage, focus, confidence, `sentenceComplexity`. KDoc에 LearningState 정교화 TODO 명시.
    - `chooseChallengeLevel` 및 4단계 `*For()` 매핑 함수 제거. `conservativeProfile()`는 `defaultsForBand(MeaningFirst)`로 교체.
4. **프롬프트 빌더 소비부 교체** (`CorrectionPromptBuilder`)
    - 정책 라인 블록을 scope/grammar/vocabulary/sentenceExpansion/register/explanation/newExpressionLimit/meaningPreservation + 기존 `primarySupportLine`·`focusLine`으로 교체. candidate·JSON 스키마는 무변경.
    - **`COR-TUNE-002-FIX`로 강화된 learningSignal 규칙 블록(`meaningPreserved` ALWAYS include, "허용 목록 밖 값은 learningSignal 전체 폐기" 문구)은 그대로 보존**한다. 편집 전 현재 파일을 재확인하고 강화 문구를 되돌리지 않는다.
    - 주의: 신규 `RegisterCorrectionPolicy`와 learningSignal용 `SpokenRegister`는 값 이름이 일부 겹치지만 별개 enum이다.
5. **인계용 핸드오버 문서 작성** (`docs/handover/CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_IMPL_HANDOVER.md`)
    - 구현된 계약 위치, 기본 매핑 표 위치, LearningState 담당자 정교화 지점(band 산출), 변경하지 않은 것(mapper/v2 계약), 검증 방법.
6. **테스트/픽스처 반영**
    - `CorrectionPromptBuilderTest`: 정책 fixture를 `CorrectionGrowthPolicy`로 교체, 단언 문구 갱신.
    - `BuildLearnerAdaptationProfileUseCaseTest`: 초기/저신뢰 → 낮은 band, 고급 → `NuanceRefine`, 단일 고점 미상승, 의미차단 focus 우선 케이스 추가.

---

# 예외 처리

- meaningful `LangState`가 없거나 `ProfileConfidence.Low`면 `MeaningFirst`/`PatternFix` 같은 낮은 band로 보수 조정한다.
- 지표가 서로 크게 충돌(high spread)하면 일부 고점이 있어도 band를 올리지 않는다.
- active focus가 의미 전달 오류나 핵심 문법에 쏠리면 자연스러움 개선보다 패턴 안정화 band를 우선한다.
- `external` metric은 band 산출에 직접 쓰지 않는다. (`expressionRange`만 vocabulary 보조 신호로 예외 사용)
- `CorrectionGrowthPolicy`는 내부 enum이라 unknown 값이 들어올 일이 없다. prompt 빌더의 when 분기는 모든 enum을 빠짐없이 처리해야 한다.
- 응답 schema(핵심 4필드)와 learningSignal v2가 변하지 않으므로 기존 mapper/저장/완료 흐름이 깨지지 않아야 한다.

---

# 검증 기준

- `git diff --check`
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- 초기/저신뢰 사용자 → 낮은 교정 band(의미 보존·최소 수정·확장 제한) 정책을 받는지 확인.
- 고급 사용자(High + refined naturalness) → 자연스러움/뉘앙스 교정 band를 받는지 확인.
- active focus가 있으면 교정 정책이 해당 약점을 우선 반영하는지 확인.
- 하나의 고점 metric만으로 고급 band로 올라가지 않는지 확인.
- prompt에 raw metric 숫자(`\d\.\d{2}`)·CEFR·`ChallengeLevel` 흔적이 없는지 확인.
- Correction prompt 경로에 4단계 정책과 6단계 성장 정책이 동시에 들어가지 않는지 확인. (main 코드에서 `ChallengeLevel`/`CorrectionAdaptationPolicy` grep 0건)
- 기존 `CorrectionLearningSignal v2` 출력 계약과 핵심 4필드가 유지되는지 확인.
- `CorrectionAiResponseMapperTest`가 회귀 없이 통과하는지 확인. (COR-TUNE-002-FIX 강화 정규화가 깨지지 않았는지 회귀 가드)
- 발음/pause/hesitation 같은 음성 지표가 성장 정책에 포함되지 않는지 확인.
