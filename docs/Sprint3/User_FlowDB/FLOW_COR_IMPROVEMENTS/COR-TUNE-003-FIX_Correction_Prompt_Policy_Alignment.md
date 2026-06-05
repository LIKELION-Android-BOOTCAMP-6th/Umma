# [Improvement] COR-TUNE-003-FIX 교정 프롬프트 정합화 (editSpans enum 안내 + explanation 정책 위임)

## User Story

사용자가 교정을 받을수록, 시스템은 그 교정에서 관찰된 신호를 장기 학습 상태(LangState)에 반영한다.
AI가 editSpans에 허용 목록 밖의 값을 넣거나, explanation 언어 지시가 서로 충돌할 경우, 교정 결과 문장은 정상 표시되지만
학습 신호(learningSignal)가 조용히 누락되거나 고급 사용자에게 일관되지 않은 설명이 제공될 수 있다.
프롬프트가 매퍼의 실제 drop 규칙을 정확히 안내하고, explanation 언어 지시의 단일 출처를 확보한다.

---

# 배경

`COR-TUNE-003`은 교정 프롬프트를 4단계 ChallengeLevel에서 6단계 CorrectionGrowthBand 정책으로 완전 교체했다.
`COR-TUNE-002-FIX`는 매퍼(`CorrectionAiResponseMapper`)의 보수적 drop 정책을 강화했다.
이 두 작업 이후 프롬프트 측에 정합화되지 않은 갭 두 가지가 남았다.

발견된 갭은 두 가지다.

1. **editSpans enum 안내 누락** — 매퍼 `normalizeEditSpan`은 editSpan의 `issueCategory`/`improvementType`이 허용 목록 밖이면
   learningSignal 전체를 drop 한다(COR-TUNE-002-FIX AC). 그런데 프롬프트의 editSpans 규칙은 이 enum 제약·폐기 경고를 안내하지 않는다.
   top-level `issueCategories`/`improvementTypes` 규칙에만 "Use ONLY these values — any value outside the list discards the whole learningSignal"
   안내가 있고, editSpans에는 없다. AI가 `grammar mistake`같은 자연어 값을 editSpan에 넣으면 교정 문장은 정상 표시되지만 분석 데이터가 통째로 누락된다.

2. **explanation 언어 규칙 충돌** — `explanationLine()`은 고급 band(`ConnectedExpression`→`TargetLanguageWithPrimaryFallback`,
   `NuanceRefine`→`TargetLanguageNuance`)에서 target-language 설명을 지시한다. 그러나 응답 규칙은 항상 `primaryLang`으로 설명하라고 고정해
   두 지시가 충돌한다. AI가 어느 쪽을 따를지 불안정해져, 고급 사용자에게 의도한 target-language 설명이 안정적으로 제공되지 않을 수 있다.

요약하면 프롬프트가 매퍼의 실제 동작과 6단계 성장 정책의 의도를 정확히 안내하도록 정합화한다.
매퍼·도메인 타입·응답 schema·완료 경로는 변경하지 않는다.

---

# 완료 기준(AC)

- [x] **editSpans enum 안내 추가**: `editSpans` 규칙에 `issueCategory`/`improvementType`은 허용 enum 목록 값만 사용해야 하고, 목록 밖 값이면 **learningSignal 전체가 폐기**됨을 명시한다. 이미 만들어 둔 `issueCategoryValues`/`improvementTypeValues`를 재사용하고(drift 방지), top-level 규칙의 "Use ONLY these values — any value outside the list discards the whole learningSignal" 어휘와 일관되게 맞춘다.
- [x] **explanation 언어 정책 위임**: 응답 규칙의 `explanation` 라인에서 `primaryLang` 고정을 제거하고 형식 제약(60자 이내)만 남긴다. 언어 결정은 **위 Explanation 정책(`explanationLine()`)에 위임**해 단일 출처를 확보한다.
- [x] **고급 band 충돌 제거**: `ConnectedExpression`/`NuanceRefine` 등 target-language 설명 정책을 가진 band에서 응답 규칙이 더 이상 primaryLang을 강제하지 않는다. (낮은 band는 정책이 primaryLang 계열이라 위임 후에도 동작 변화 없음)
- [x] **무변경 보장**: 매퍼(`CorrectionAiResponseMapper`)·도메인 타입·응답 schema·핵심 4필드·완료 경로(`CorrectionResult.learningSignals`)는 변경하지 않는다. 본 이슈는 프롬프트 문자열 정합화까지다.
- [x] **테스트 보강**: `CorrectionPromptBuilderTest`에 editSpans enum 제약·폐기 경고 노출, explanation 정책 위임, 고급 band 충돌 회귀 테스트를 추가한다.
- [x] **문서 정합성**: 본 정합화를 FLOW 문서(COR-TUNE-003-FIX)로 남기고, 데모 문서(`DEMO_FLOW_COR_IMPROVEMENTS.md`) COR-TUNE-003 시나리오에 고급 단계 설명 언어 이동을 한 줄 보완해 QA 오탐을 방지한다.

---

# 기준 문서

- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-003_Correction_Growth_Policy.md` (정합화 대상 6단계 성장 정책 / explanationLine 고급 band target-language 설명 의도)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (editSpan unknown enum → learningSignal 전체 drop 규칙을 매퍼에 넣은 선행 하드닝)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002_Correction_Learning_Signal_Output.md` (learningSignal 출력 계약 / top-level enum 안내 선례)

---

# 핵심 결정

- **editSpans enum 제약**: editSpans도 top-level `issueCategories`/`improvementTypes`와 동일하게 enum 목록·폐기 경고를 안내한다. 매퍼가 이미 동일한 drop 규칙을 갖고 있으므로, 프롬프트가 안내하지 않으면 AI가 자연어 값을 넣을 수 있고 분석 데이터가 통째로 누락된다.
- **explanation 정책 위임**: explanation 언어를 응답 규칙에서 다시 고정하지 않고 `explanationLine()`에 위임한다. 분기 로직 중복 없이 언어 결정의 단일 출처를 확보해 drift를 방지한다. 낮은 band는 기존대로 primaryLang, 고급 band는 target-language로 안정적으로 동작한다.
- **무변경 보장**: 프롬프트 문자열과 테스트·문서만 조인다. 매퍼의 drop 동작·도메인 계약·스키마는 그대로이며 본 작업은 "프롬프트가 실제 동작을 정확히 안내하는" 정합화다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionPromptBuilder | editSpans enum 제약·폐기 경고를 추가하고 explanation 언어를 Explanation 정책에 위임한다. |
| CorrectionAiResponseMapper | **무변경**. editSpan unknown enum → signal drop 동작은 COR-TUNE-002-FIX에서 이미 완료. |
| CorrectionGrowthPolicy (domain) | **무변경**. band별 explanation 정책 매핑은 그대로 유지한다. |
| LearningState domain | drop되지 않고 넘어온 신호만 입력으로 evidence/focus/score를 갱신한다. 본 이슈 범위 밖. |

---

# 주요 작업

1. **CorrectionPromptBuilder editSpans 규칙 강화** (`data/repository/correction/CorrectionPromptBuilder.kt`)
   - editSpans 규칙 라인에 `issueCategoryValues`/`improvementTypeValues`를 재사용해 각 span의 `issueCategory`/`improvementType` enum 제약과 폐기 경고를 추가한다. 매퍼 `normalizeEditSpan` 동작과 1:1 정합.
   - 인라인 주석으로 "왜 추가했는지(COR-TUNE-002-FIX 매퍼 규칙을 프롬프트에 반영)"를 한 줄 남긴다.

2. **CorrectionPromptBuilder explanation 규칙 위임** (동 파일)
   - 응답 규칙의 explanation 라인에서 `$primaryLangName` 고정을 제거하고, "in the language set by the Explanation policy above"로 위임한다.
   - 인라인 주석으로 "왜 primaryLang 고정에서 정책 위임으로 바꿨는지(고급 band 충돌 제거)"를 한 줄 남긴다.

3. **CorrectionPromptBuilderTest 보강** (`data/repository/correction/CorrectionPromptBuilderTest.kt`)
   - 기존 `explanation rule uses primaryLang for tip language` 테스트를 "응답 규칙이 Explanation 정책에 위임"하는 검증으로 갱신.
   - 신규: `advanced band explanation does not conflict with response rule` — `ConnectedExpression`/`NuanceRefine` 빌드 시 target-language 문구 존재 + primaryLang 고정 부재.
   - 신규: `editSpans rule exposes enum constraints and drop warning` — editSpans 규칙에 enum 대표값과 "discards the whole learningSignal" 경고 노출 확인.

4. **데모 문서 한 줄 보완** (`docs/Sprint3/Demo/DEMO_FLOW_COR_IMPROVEMENTS.md`)
   - COR-TUNE-003 시나리오 UX 포인트에 고급 단계 설명 언어 이동을 명시해 `COR-FIX-07`("설명=항상 primaryLang")과의 미세 충돌을 해소.

---

# 예외 처리

- `editSpans`가 **빈 배열**이면 signal 유지. enum 안내는 "값을 넣을 때 허용 목록만"이라는 제약이며 editSpan 자체를 강제하지 않는다.
- `editSpan.languageFeatureKey` — 형식 위반 시 null로 비우되 span은 유지(보조 정보). 본 이슈 범위 밖.
- 낮은 band(`MeaningFirst`~`SentenceShape`) — Explanation 정책이 primaryLang 계열이므로 위임 후에도 동작 변화 없음.
- 고급 band(`ConnectedExpression`/`NuanceRefine`) — explanation 언어가 target-language로 안정적으로 이동. 이는 COR-TUNE-003의 의도이며 본 FIX는 그 의도가 안정적으로 적용되도록 한다.

---

# 검증 기준

- `CorrectionPromptBuilderTest`:
  - explanation 응답 규칙에 "Explanation policy above" 위임 문구 존재 / primaryLang 고정 부재 확인.
  - `ConnectedExpression`/`NuanceRefine` 빌드 시 정책 블록에 target-language 문구 존재 + 응답 규칙에 primaryLang 고정 부재 확인.
  - editSpans 규칙에 issueCategory/improvementType enum 대표값(`GrammarForm`/`GrammarFixed`)과 "discards the whole learningSignal" 경고 노출 확인.
  - 회귀: schema 키(`learningSignal`/`editSpans`/`issueCategories` 등) 노출, 허용 enum 대표값 노출, raw metric 미노출.
- `CorrectionAiResponseMapperTest`: 동작 변경 없음 → 그대로 green.
- `:app:compileDevDebugKotlin` 빌드 통과.
- 응답 schema·핵심 4필드·`CorrectionResult.learningSignals` 집계 경로가 변경되지 않았는지 확인.
