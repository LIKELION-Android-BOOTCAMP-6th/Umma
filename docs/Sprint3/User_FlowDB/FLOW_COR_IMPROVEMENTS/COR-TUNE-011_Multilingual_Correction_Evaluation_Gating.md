# [Improvement] COR-TUNE-011 멀티링궐 교정 평가 게이트 (의도 포착·교정은 하되 평가는 학습언어만)

## User Story

새 언어를 배우는 학습자는 대화 도중 답답하거나 단어가 떠오르지 않으면 모국어나 다른 언어로 말하기도 한다.
Umma는 어떤 언어로 말하든 학습자의 **의도를 포착해 학습 대상 언어로 교정**해 준다(학습자는 원하는 표현을 학습 언어로 얻는다).
다만 **능력 평가(band/LangState 갱신)는 학습 대상 언어(selectedLang)로 말한 발화에 한해서만** 반영한다 — 한국어로 말한 문장을 영어 능력 근거로 카운트하지 않는다.

---

# 배경

현재 발화 turn에는 언어 정보가 없어(`SessionTurn`/`ConversationTurn`에 언어 필드 부재), 교정 파이프라인은 **발화 원문 언어를 식별하지 못한다.** 그 결과:

- 학습 대상 언어가 아닌 발화(모국어·제3언어)도 후보가 되어 교정된다(의도 포착은 우연히 일부 됨).
- 교정 후 생성되는 learningSignal이 **학습 대상 언어 능력 근거로 잘못 집계**되어 LangState/band 산출을 오염시킨다.

확인된 현황 — **평가의 "학습언어만" 게이트는 feature 레벨에는 이미 구현되어 있다**:

- `CorrectionAiResponseMapper.normalizeLanguageFeature`([app/src/main/java/com/app/umma/data/repository/correction/CorrectionAiResponseMapper.kt](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionAiResponseMapper.kt)): `languageFeature.lang != selectedLang` 이면 해당 feature 제외 + featureKey namespace(`{LANG}.`) allowlist 검증.
- `LangStateAnalysisPolicy`([app/src/main/java/com/app/umma/domain/usecase/learningstate/LangStateAnalysisPolicy.kt](../../../../app/src/main/java/com/app/umma/domain/usecase/learningstate/LangStateAnalysisPolicy.kt)): 평가 단계에서 `feature.lang == lang` 으로 재필터.
- `CorrectionAiResponseMapper`는 `require(candidate.lang == selectedLang)` 도 강제하지만, `candidate.lang`은 텍스트에서 감지한 값이 아니라 selectedLang이 그대로 박히는 라벨이라 **발화 원문 언어 게이트로는 동작하지 않는다**.

즉 **부족한 것은 "발화 원문 언어 기준" 평가 게이트**다. 이건 turn에 `detectedLang`이 있어야 가능하다.

---

# 선행 의존

```
[Handover] SessionTurn.detectedLang 캡처 (CHAT/Realtime)  ──선행──>  COR-TUNE-011
```

- 선행: `docs/handover/COR-TUNE-011_TURN_DETECTED_LANG_CAPTURE_HANDOVER.md`.
- `detectedLang`이 없으면 본 이슈의 발화 원문 언어 게이트는 **"언어 불명 → 종전처럼 통과"(no-op fallback)**로 동작한다. 캡처가 머지되면 그때부터 게이트가 실제로 작동한다.
- 따라서 본 이슈는 캡처 없이 **게이트 구조/seam만 먼저 넣어두고**, `detectedLang` 도착 시 자동으로 활성화되도록 설계할 수 있다.

---

# 완료 기준(AC)

- [ ] `CorrectionCandidate`([app/src/main/java/com/app/umma/domain/model/correction/CorrectionCandidate.kt](../../../../app/src/main/java/com/app/umma/domain/model/correction/CorrectionCandidate.kt))에 발화 원문 언어를 담는 `sourceLang: LangCode?` 필드를 추가한다(turn의 `detectedLang`에서 채움, null 허용).
- [ ] 후보 추출 단계(`ExtractSessionCandidatesUseCase` / `ExtractCandidatesUseCase`)에서 turn의 `detectedLang`을 `candidate.sourceLang`으로 전달한다. (감지 없으면 null)
- [ ] 평가 게이트: `sourceLang`이 **학습 대상 언어와 다른 것이 확정된 경우**, 해당 후보의 learningSignal을 **능력 근거(metricEvidence/activeFocus)에서 제외**한다. `sourceLang == selectedLang` 또는 `sourceLang == null`(불명) 이면 종전대로 평가에 반영한다.
- [ ] 평가에서 제외돼도 **교정 카드(앞면 모국어/뒷면 학습언어)는 정상 생성**된다 — 학습자는 원하는 표현을 학습 언어로 얻는다.
- [ ] 게이트 위치는 평가 입력 조립 지점(`CompleteCorrectionUseCase.buildCorrectionResult`의 `learningSignals` 집계 또는 `CorrectionAiResponseMapper`)으로 하되, **단일 지점**에서 결정되게 한다(분산 금지).
- [ ] **제3언어 입력은 교정 쪽에서 특별 거름망 없이 그대로 통과**시킨다(이번 결정). 의도 포착→학습언어 교정 흐름은 동일하게 유지한다.
- [ ] `detectedLang` 부재 시 모든 동작이 종전과 동일함을 보장한다(점진 도입 안전).
- [ ] 기존 feature-레벨 게이트(mapper/분석정책)는 그대로 유지한다.

---

# 기준 문서

- `docs/handover/COR-TUNE-011_TURN_DETECTED_LANG_CAPTURE_HANDOVER.md` (선행 — detectedLang 캡처)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002_Correction_Learning_Signal_Output.md` (learningSignal 출력 계약)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (신호 정규화 — 보존 대상)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-004_Correction_Candidate_Selection_Refinement.md` (후보 추출 — sourceLang 전달 지점)
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md` (평가/지표 갱신 정책)

---

# 핵심 결정

- **교정 vs 평가 분리.** 교정은 언어 불문 의도 포착해 학습 언어로 만든다. 평가만 학습 대상 언어 발화로 제한한다.
- **게이트 기준은 발화 원문 언어(`sourceLang`).** feature.lang(AI가 주장한 feature 언어)이 아니라 "사용자가 실제로 무슨 언어로 말했나"가 기준. feature-레벨 게이트는 보완재로 유지.
- **null(불명)은 통과.** 감지 실패/미도입 구간에서 종전 동작을 보존(과도한 제외로 정상 학습 신호를 잃지 않게).
- **제3언어 무필터.** 교정 쪽에 별도 거름망을 두지 않는다. CHAT이 캡처 단계를 처리하면 제3언어 유입 빈도 자체가 줄어든다.
- **단일 게이트 지점.** 평가 제외 판정을 한 곳에서만 내려 분기 분산을 막는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CHAT/Realtime (범위 밖, 선행) | `SessionTurn.detectedLang` 캡처 (handover) |
| ExtractCandidates / ExtractSessionCandidates | turn의 `detectedLang` → `candidate.sourceLang` 전달 |
| CorrectionCandidate (domain 계약) | `sourceLang` 필드 추가 |
| 평가 입력 조립(CompleteCorrectionUseCase/Mapper) | `sourceLang != selectedLang` 확정 시 learningSignal 평가 제외 |
| 교정 생성/카드 저장 | 변경 없음 — 언어 불문 교정·저장 유지 |
| CorrectionOverexpansionGuard | 변경 없음 — 단일 suggestion 길이/토큰만 보므로 평가 제외와 무관 |

---

# 주요 작업

1. **`CorrectionCandidate.sourceLang` 추가** + KDoc(“발화 원문 언어, 감지 실패 시 null”).
2. **추출 단계 전달**: `ExtractSessionCandidatesUseCase`가 `SessionTurn.detectedLang`을 candidate로 넘김(현재 turnId 보존하듯 동일 패턴).
3. **평가 게이트 단일 지점 구현**: `CompleteCorrectionUseCase.buildCorrectionResult`([app/src/main/java/com/app/umma/domain/usecase/correction/CompleteCorrectionUseCase.kt:322](../../../../app/src/main/java/com/app/umma/domain/usecase/correction/CompleteCorrectionUseCase.kt))의 `learningSignals = selectedSuggestions.mapNotNull { it.learningSignal }` 에 `sourceLang` 게이트 추가(또는 mapper에서 signal=null 처리). 카드 저장 경로는 손대지 않음.
4. **null 폴백 보장**: `sourceLang == null || sourceLang == selectedLang` → 평가 반영.
5. **테스트**: (a) selectedLang 발화 → 평가 반영, (b) 다른 언어 발화 → 카드 생성되지만 평가 제외, (c) detectedLang 없음 → 종전과 동일, (d) 제3언어 → 교정 통과·평가 제외.

---

# 예외 처리

- `detectedLang` 미도입 구간: 모든 후보 `sourceLang = null` → 게이트 no-op → 종전 동작.
- 한 세션에 여러 언어가 섞임: 후보별로 독립 판정(세션 전체 게이트 아님).
- `sourceLang`이 selectedLang과 다른데 AI가 feature를 selectedLang으로 채운 경우: 발화 원문 게이트가 우선해 평가에서 제외(이 케이스가 본 이슈의 핵심 오염 방지 대상).
- 교정 자체는 어떤 경우에도 막지 않는다 — 평가만 거른다.

---

# 검증 기준

- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- selectedLang 발화의 learningSignal이 평가에 반영되는지.
- 다른 언어(sourceLang≠selectedLang) 발화: 교정 카드는 생성되나 metricEvidence/activeFocus에 반영 안 되는지.
- `detectedLang == null` 일 때 기존 회귀 테스트가 모두 통과하는지(no-op 보장).
- 제3언어 입력이 교정 단계에서 거름망 없이 통과하는지.
- 기존 feature-레벨 게이트(mapper/분석정책)와 `CorrectionOverexpansionGuard`가 회귀 없이 유지되는지.
- 평가 제외 판정이 단일 지점에서만 일어나는지(코드 grep으로 분산 없음 확인).
