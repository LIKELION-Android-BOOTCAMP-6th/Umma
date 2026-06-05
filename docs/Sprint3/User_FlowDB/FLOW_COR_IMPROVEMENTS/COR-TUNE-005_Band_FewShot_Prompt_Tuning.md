# [Improvement] COR-TUNE-005 Band별 few-shot 예시로 교정 프롬프트 품질 튜닝

## User Story

사용자는 자신의 실력 단계에 맞는 교정을 안정적으로 받기를 기대한다.
초급 단계에서는 짧고 의미를 보존하는 교정을, 고급 단계에서는 자연스러움·뉘앙스를 다듬는 교정을 일관되게 받는다.
Umma는 성장 단계(band)별 짧은 예시를 프롬프트에 넣어, 모델 출력이 band 의도대로 나오도록 정렬한다.
사용자는 내부 단계 이름이나 점수를 보지 않고, 결과(쉬운/자연스러운 교정)만 체감한다.

---

# 배경

`COR-TUNE-003`은 교정 프롬프트를 6단계 `CorrectionGrowthBand` 정책으로 전환하면서 band별 정책 **지시 문장**(`CorrectionPromptBuilder`의 scope/grammar/vocabulary/... 라인)을 넣었다.
그러나 실제 모델 출력이 band 의도대로 나오는지는 검증·정렬되지 않았다. 지시 문장만으로는 모델이 band 기대치를 일관되게 따르지 않을 수 있다.

`CHAT-TUNE-004` 핸드오버는 "prompt 실제 문구 튜닝은 Correction 담당"으로 band별 교정 예시를 남겼다.
이 작업은 band별 **few-shot 예시**(source → corrected + explanation)를 프롬프트에 넣어 모델 출력을 band 기대치에 맞춘다.

`CorrectionPromptBuilder`에는 이미 `policy.band`(`CorrectionGrowthBand`)가 들어와 있어 band별 분기가 가능하다.
예시는 정책 지시 블록 아래, Candidates 블록 위에 삽입한다.
응답 schema의 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`)와 `learningSignal` schema/규칙(`COR-TUNE-002` + `COR-TUNE-002-FIX` 강화판)은 변경하지 않는다.

---

# 완료 기준(AC)

- [ ] `CorrectionGrowthBand`(또는 대표 band 그룹)별 few-shot 예시를 1개 이상 프롬프트에 포함한다.
- [ ] 예시는 핸드오버 예시(MeaningFirst / EverydayNatural 등) 또는 실측 결과 기반이며, `primaryLang`(앞면/설명)·`selectedLang`(교정문) 기준을 따른다.
- [ ] 예시는 토큰 비대를 막기 위해 최소화한다. (band별 1개 수준, 필요 시 band 그룹화)
- [ ] `learningSignal` schema/규칙(COR-TUNE-002-FIX 강화판)과 응답 schema 핵심 4필드는 변경하지 않는다.
- [ ] 프롬프트에 raw metric 숫자·band 이름·점수/레벨은 노출되지 않는다.
- [ ] 예시 언어는 하드코딩하지 않고 `selectedLang`/`primaryLang` 규칙을 그대로 따르게 동적 처리한다.

---

# 기준 문서

- `docs/handover/CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER.md` (band별 교정 예시 / 책임 경계 "prompt 실제 문구 튜닝은 Correction 담당")
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-003_Correction_Growth_Policy.md` (6단계 성장 정책 / band 정의 / 정책 지시 블록)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-003-FIX_Correction_Prompt_Policy_Alignment.md` (explanation 언어 정책 위임 / editSpans enum 안내 — 보존 대상)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (learningSignal 규칙 — 보존 대상)

---

# 핵심 결정

- few-shot 예시는 `CorrectionPromptBuilder.build()`의 **정책 지시 블록 아래, Candidates 블록 위**에 삽입한다. band별 분기는 이미 들어와 있는 `policy.band`로 한다.
- 예시는 **band별 1개 수준**으로 최소화한다. 토큰 비대를 막기 위해 의미가 비슷한 band는 그룹화(예: 초급 MeaningFirst/PatternFix, 고급 ConnectedExpression/NuanceRefine)를 검토한다.
- 예시 언어는 **하드코딩하지 않는다.** `afterText`는 `selectedLang`, `nativeText`/`explanation`은 band별 Explanation 정책(`explanationLine()`)이 정한 언어를 따른다. 예시는 anchor일 뿐 candidate 언어 조합을 강제하지 않는다.
- 프롬프트 표면에는 band 이름·점수·레벨·raw metric을 **노출하지 않는다.** 예시는 "이런 식으로 고친다"는 형태만 보여 준다.
- `learningSignal` 규칙 블록과 응답 schema 핵심 4필드는 **무변경**이다. `COR-TUNE-002-FIX`로 강화된 문구(`meaningPreserved` ALWAYS include, "허용 목록 밖 값은 learningSignal 전체 폐기")를 되돌리지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionPromptBuilder | band→few-shot 매핑을 추가하고 정책 블록 아래에 예시를 삽입한다. 예시 언어를 `selectedLang`/`primaryLang` 규칙으로 동적 처리한다. |
| CorrectionGrowthPolicy (domain) | **무변경**. band·정책 enum은 그대로 사용한다. |
| CorrectionAiResponseMapper (범위 밖) | 응답 파싱·learningSignal 정규화. 본 작업에서 변경하지 않는다. (few-shot은 프롬프트 문자열 변경까지) |
| LearningState domain (범위 밖) | band 산출. 본 작업에서 다루지 않는다. |

---

# 주요 작업

1. **band→few-shot 매핑 추가** (`CorrectionPromptBuilder`)
   - `policy.band` 기준 few-shot 예시(source → corrected + 짧은 explanation)를 만드는 private 함수 추가. 정책 지시 블록 아래·Candidates 위에 `appendLine`으로 삽입한다.
   - 토큰 절약을 위해 band 그룹화 여부를 결정하고 KDoc에 명시한다.

2. **예시 언어 동적 처리** (동 파일)
   - 예시의 교정문은 `selectedLang.code`, 설명은 band별 Explanation 정책 언어를 따르게 한다. 언어를 하드코딩하지 않는다(`languageName()` 재사용).
   - 라인 주석으로 "예시는 anchor일 뿐 candidate 언어를 강제하지 않음"을 남긴다.

3. **누수 방지 확인** (동 파일)
   - 예시 어디에도 band 이름·점수·레벨·raw metric이 들어가지 않는지 확인한다. (기존 `Correction policy (apply silently...)` 원칙과 일관)

4. **테스트 보강** (`CorrectionPromptBuilderTest`)
   - band별 빌드 시 해당 few-shot 문구가 노출되는지.
   - raw metric(`\d\.\d{2}`)·band 이름(`MeaningFirst` 등)·점수/레벨이 미노출인지.
   - 핵심 4필드·`learningSignal` 키(`issueCategories`/`editSpans`/`meaningPreserved` 등)와 강화 문구가 그대로 유지되는지 회귀.

---

# 예외 처리

- 예시 언어와 실제 candidate 언어 조합이 다를 수 있다. 예시는 anchor일 뿐이며 강제가 아니다.
- 토큰 한도: 예시가 많아지지 않도록 band 그룹화를 고려한다. 모든 band에 개별 예시를 강제하지 않는다.
- 낮은 band와 고급 band의 Explanation 정책 언어가 다르므로(초급=primaryLang, 고급=target language), 예시 설명 언어도 band 정책을 그대로 따라야 `COR-TUNE-003-FIX`의 위임 결정과 충돌하지 않는다.
- `CorrectionGrowthBand`는 내부 enum이라 unknown 값이 없다. few-shot 분기 `when`은 모든 band(또는 그룹)를 빠짐없이 처리해야 한다.

---

# 검증 기준

- `CorrectionPromptBuilderTest`:
  - 각 band(또는 그룹) 빌드 시 해당 few-shot 예시 문구가 프롬프트에 노출되는지 확인.
  - raw metric 숫자·band 이름·점수/레벨이 노출되지 않는지 확인.
  - 응답 schema 핵심 4필드와 `learningSignal` 규칙(COR-TUNE-002-FIX 강화 문구 포함)이 유지되는지 회귀 확인.
  - explanation 언어 정책 위임(COR-TUNE-003-FIX)이 예시 삽입 후에도 깨지지 않는지 확인.
- (선택) 실제 모델 응답 샘플로 band별 교정 결과를 수동 확인하고, 관찰한 출력 길이를 `COR-TUNE-006` 임계값 설정 근거로 기록한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
