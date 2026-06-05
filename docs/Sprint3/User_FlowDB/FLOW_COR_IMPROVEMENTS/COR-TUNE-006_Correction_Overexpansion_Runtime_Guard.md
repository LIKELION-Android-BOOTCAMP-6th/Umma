# [Improvement] COR-TUNE-006 교정 결과 과확장 런타임 가드

## User Story

사용자는 교정문이 원문보다 과도하게 길어지거나 의미가 바뀐 결과를 그대로 받지 않기를 기대한다.
Umma는 교정 강도 정책이 허용한 범위를 모델이 어겨 장문으로 교정하거나 의미를 바꾼 경우, 코드가 런타임에서 이를 잡아낸다.
사용자는 정책을 벗어난 과확장 교정 대신, 정한 처리 방식(drop/플래그/최소 교정 fallback)으로 안전하게 정리된 결과를 본다.
정상적으로 더 짧아진 교정은 그대로 통과한다.

---

# 배경

`COR-TUNE-003`은 과확장을 **프롬프트 지시(부탁)**로만 막는다. (`CorrectionPromptBuilder`의 `sentenceExpansionLine`/`newExpressionLimitLine`/`meaningPreservationLine`)
AI가 이 지시를 어겨 장문으로 교정하거나 의미를 바꿔도, 코드가 검증하지 않아 그대로 노출·저장된다.
`CorrectionAiResponseMapper`는 현재 핵심 4필드(공백 검증)와 `learningSignal` 정규화만 하고, 교정문 길이·표현 증가를 정책과 대조하지 않는다.

이 작업은 교정문(`afterText`) vs 원문(`beforeText`=`candidate.sourceText`)을 정책과 대조해 위반을 실제로 잡아내는 **런타임 안전망**을 추가한다.
AC "모든 band에서 의도·길이 과변경 방어"를 프롬프트 수준에서 런타임 수준으로 완성한다.

**전달 경로 확인이 핵심 설계 작업이다.** `CorrectionAiResponseMapper.map(rawJson, input)`은 이미 `GenerateSuggestionsInput`을 받지만, 검사에 필요한 `input.profile.correctionPolicy`(`sentenceExpansion`/`newExpressionLimit`/`meaningPreservation`)가 mapper/후처리 단계까지 실제로 전달·소비되는지 먼저 확인해야 한다. 안 온다면 정책을 검사 단계로 전달하는 설계가 본 작업의 중심이다.

`CorrectionLearningSignal v2` 계약·응답 schema·완료 저장 흐름(`CorrectionResult.learningSignals`)은 변경하지 않는다.

---

# 완료 기준(AC)

- [ ] 교정문(`afterText`)이 원문(`sourceText`/`beforeText`) 대비 정책(`sentenceExpansion` / `newExpressionLimit`)이 허용한 범위를 넘어 과도하게 길거나 표현이 늘어났는지 런타임에서 검사한다.
- [ ] `learningSignal`의 `meaningPreserved=false`인 경우의 처리 정책을 정의한다. (위반으로 간주/플래그)
- [ ] 위반 감지 시 처리 방식을 정한다: 해당 suggestion drop / 플래그 / 최소 교정 fallback 중 택1. (핵심 4필드 저장 흐름은 무손상)
- [ ] 길이/표현 임계값은 상수로 분리하고, 언어 특성(공백 없는 언어 등)을 고려한다.
- [ ] `CorrectionLearningSignal v2` 계약·응답 schema·완료 저장 흐름은 변경하지 않는다.
- [ ] 위반 감지·처리를 Logcat에 기록해 추적 가능하게 한다.

---

# 기준 문서

- `docs/handover/CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_HANDOVER.md` (의도/길이 과변경 방어 / band별 기대 출력)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-003_Correction_Growth_Policy.md` (`sentenceExpansion`/`newExpressionLimit`/`meaningPreservation` 정책 enum / 프롬프트 지시)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (mapper 보수적 drop 패턴 / 회귀 무손상 대상)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-005_Band_FewShot_Prompt_Tuning.md` (임계값 보정 근거가 될 실측 출력 길이)

---

# 핵심 결정

- **정책 전달 경로 먼저 확인**: `CorrectionAiResponseMapper`(또는 별도 후처리)가 `input.profile.correctionPolicy`를 검사에 쓸 수 있는지 확인한다. mapper는 이미 `GenerateSuggestionsInput`을 받으므로 정책 접근은 가능하나, 검사 로직이 실제로 소비하는 형태로 연결한다.
- **검사 기준**: `sentenceExpansion`(NoExpansion ~ FlexibleNaturalDetail)별 길이비 상한, `newExpressionLimit`별 새 표현 수 상한, `learningSignal.meaningPreserved`를 검사한다. 길이비·새 표현 수 임계값은 **상수로 분리**한다.
- **`meaningPreserved=false` 처리**: 위반으로 간주(또는 플래그)한다. 의미가 바뀐 교정은 정책 위반이므로 처리 방식에 포함한다. 단 `meaningPreserved`는 누락 시 이미 mapper가 signal을 drop하므로(COR-TUNE-002-FIX), 여기서는 `false`로 확정된 경우를 다룬다.
- **위반 처리 방식 택1**: drop / 플래그 / 최소 교정 fallback 중 구현 시 하나로 확정한다. 어느 방식이든 **핵심 4필드 저장 흐름은 무손상**이어야 하고, fallback 선택 시 무한 재교정을 막기 위해 **재시도 1회 제한**을 둔다.
- **언어 특성 고려**: 길이 측정은 공백 기준 단어 수가 아니라 언어 특성을 반영한다. 일본어/중국어 등 공백 없는 언어는 글자 수 기준 등 별도 측정을 적용한다.
- **무변경 보장**: `CorrectionLearningSignal v2` 계약·응답 schema·완료 저장 흐름은 그대로다. 본 작업은 후처리 가드 추가까지다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionAiResponseMapper (또는 별도 후처리) | 교정문 vs 원문을 정책과 대조해 과확장·의미 위반을 검사하고, 정한 방식(drop/flag/fallback)으로 처리한다. Logcat 기록. |
| CorrectionGrowthPolicy (domain) | `sentenceExpansion`/`newExpressionLimit`/`meaningPreservation` 정책 enum 제공. **무변경**. |
| GenerateSuggestionsInput / Repository | `profile.correctionPolicy`를 검사 단계까지 전달한다. (전달 경로가 본 작업 핵심) |
| CompleteCorrectionUseCase (범위 밖) | 완료 저장/rollback/sync. **무손상** 유지. drop된 suggestion은 애초에 화면 목록에서 빠지므로 저장 흐름은 영향받지 않는다. |
| LearningState domain (범위 밖) | `learningSignal` 소비. 본 작업에서 변경하지 않는다. |

---

# 주요 작업

1. **정책 전달 경로 확인·연결** (`CorrectionAiResponseMapper` / Repository)
   - `input.profile.correctionPolicy`가 검사 로직까지 도달하는지 확인. 안 오면 전달 경로를 추가한다.

2. **과확장 검사 로직** (mapper 또는 별도 후처리 클래스)
   - `afterText` vs `beforeText` 길이비, 새 표현 수, `meaningPreserved`를 정책 임계값과 대조한다.
   - 길이비·새 표현 수 임계값과 언어별 측정 방식을 companion 상수로 분리한다.

3. **위반 처리 구현** (동 위치)
   - drop/flag/최소 교정 fallback 중 택1. fallback 시 재시도 1회 제한. 위반 감지·처리를 Logcat에 남긴다.
   - 핵심 4필드 저장 흐름이 깨지지 않도록 `runCatching` 등으로 검사 실패가 교정 결과를 죽이지 않게 한다.

4. **테스트 보강**
   - `CorrectionAiResponseMapperTest`(또는 신규 가드 테스트): 정책 대비 과도하게 긴 교정문 → 위반 감지·처리 / 정상(더 짧아진) 교정 → 통과 / `meaningPreserved=false` → 정의한 처리 / 공백 없는 언어 길이 측정.
   - 기존 mapper 회귀(COR-TUNE-002-FIX 케이스) 무손상 확인.

---

# 예외 처리

- 정상적으로 더 짧아진 교정은 위반이 아니다.
- 언어별 길이 측정 차이(일본어/중국어 등 공백 없는 언어)를 별도 기준으로 측정한다.
- fallback 선택 시 무한 재교정 방지(재시도 1회 제한).
- `meaningPreserved` 누락은 이미 mapper가 signal을 drop한다(COR-TUNE-002-FIX). 본 작업은 `false`로 확정된 경우의 교정문 처리 정책을 다룬다.
- 검사 로직 자체의 실패가 핵심 4필드 저장을 막지 않도록 방어한다.
- 정책이 검사 단계에 도달하지 않는 경로(예: 정책 없는 fallback 프로필)에서는 보수적으로 통과시키되 Logcat에 남긴다.

---

# 검증 기준

- 단위 테스트:
  - 정책 대비 과도하게 긴 교정문 → 위반 감지·처리 확인.
  - 정상(동일/더 짧은) 교정 → 통과 확인.
  - `meaningPreserved=false` → 정의한 처리 동작 확인.
  - 공백 없는 언어(JA 등) 길이 측정이 단어 수가 아닌 적절한 기준으로 동작하는지 확인.
- 기존 `CorrectionAiResponseMapperTest`(COR-TUNE-02-FIX 회귀) 무손상 확인.
- `CorrectionLearningSignal v2` 계약·응답 schema·핵심 4필드·`CorrectionResult.learningSignals` 집계 경로가 변경되지 않았는지 확인.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
