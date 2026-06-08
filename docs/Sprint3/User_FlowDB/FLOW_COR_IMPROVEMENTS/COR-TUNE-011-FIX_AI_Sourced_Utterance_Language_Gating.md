# [Improvement-FIX] COR-TUNE-011-FIX 발화 원문 언어 게이트 입력을 AI 보고로 전환 (detectedLang 폐기)

## User Story

학습자는 대화 중 모국어·제3언어로도 말한다. Umma는 어떤 언어로 말하든 **의도를 포착해 학습 대상 언어로 교정**하되, **능력 평가는 학습 대상 언어로 말한 발화에 한해서만** 반영한다(COR-TUNE-011). 이 평가 게이트가 실제로 작동하려면 "발화 원문 언어(`sourceLang`)"를 알아야 한다.

---

# 배경 — 왜 다시 손대는가

COR-TUNE-011은 `sourceLang`의 출처를 **CHAT/Realtime(RT-003)이 STT 시점에 태깅하는 `SessionTurn.detectedLang`**(handover)으로 가정하고, 게이트 구조 + seam을 먼저 넣어 두었다.

그러나 CHAT/Realtime 측 회신:

- **STT 실시간 언어 판별이 어렵다.** 발화별 `detectedLang`을 안정적으로 실어주기 곤란하다.
- 자막/세션 저장 시점에 "약간 더 명확히 기록"하는 정도는 가능하나, COR-TUNE-011이 기대한 turn 단위 언어 태그는 **사실상 제공되지 않는다(필드 폐기 예정).**

결과: `detectedLang`이 영원히 비어 있으면 COR-TUNE-011 게이트의 입력 `sourceLang`도 항상 null → **게이트가 무한 no-op**가 되어 평가 오염 방지가 작동하지 않는다.

확인된 사실: **교정 파이프라인은 이미 발화 원문 텍스트를 AI(Gemini)에 보낸다.** AI는 교정하려고 원문을 읽으므로, "이 발화 원문이 무슨 언어였나"를 함께 보고하는 것은 거의 비용이 들지 않는다.

---

# 핵심 결정

- **게이트 입력 출처를 `detectedLang` → AI 보고(`sourceLang`)로 전환한다(Method B).** 향후 같은 문자체계 언어(FR/ES/IT 등)가 늘어도 AI는 추가 엔지니어링 없이 판별 가능 → 확장성 우위.
- **`detectedLang` seam은 제거한다(AI 단독).** RT가 turn 단위 언어를 안 주기로 했으므로, `SessionTurn.detectedLang` / 추출 단계 전달 / `CorrectionCandidate.sourceLang`을 모두 걷어낸다(dead seam 제거).
- **게이트 자체는 COR-TUNE-011 그대로 유지한다.** 평가 제외 판정은 여전히 `CompleteCorrectionUseCase.buildCorrectionResult` **단일 지점**, `sourceLang == null || sourceLang == selectedLang`이면 종전대로 평가 반영.
- **AI 값은 보수적으로 받는다.** `sourceLang`은 신뢰 데이터가 아니라 AI 분석값이므로, "불명/미지원/누락"은 모두 null(=통과)로 떨어뜨린다. **다른 언어로 "확정"된 경우에만** 제외한다(과제외로 정상 학습 신호를 잃지 않게 — null=통과 원칙 유지).
- **출력 위치는 suggestion 최상위.** `learningSignal` 안이 아니라 candidateId/afterText와 동급. 신호가 정규화 단계에서 drop돼도 게이트가 읽을 수 있어야 하고, 원문 언어는 신호가 아니라 발화 속성이기 때문.

---

# 완료 기준(AC)

- [ ] AI 교정 응답 schema(suggestion 최상위)에 `sourceLang` 필드를 추가하고, 프롬프트 Rules에 "원문(sourceText) 기준 학습자가 실제로 사용한 언어 코드, afterText 기준 아님, 확신 없으면 `unknown`"을 명시한다.
- [ ] `CorrectionAiResponseMapper`가 AI의 `sourceLang`을 보수적으로 파싱해 `CorrectionSuggestion.sourceLang`을 채운다: `LangCode.UNKNOWN`/미지원/누락/공백 → null.
- [ ] `SessionTurn.detectedLang`, `ExtractSessionCandidatesUseCase`의 `sourceLang` 전달, `CorrectionCandidate.sourceLang`을 제거한다(detectedLang seam 폐기).
- [ ] `CorrectionSuggestion.sourceLang` 필드와 `CompleteCorrectionUseCase.buildCorrectionResult`의 단일 평가 게이트는 그대로 유지한다(COR-TUNE-011 산출물).
- [ ] 교정 카드(앞면 모국어/뒷면 학습 언어) 생성·저장 경로는 변경하지 않는다 — 평가만 거른다.
- [ ] AI가 `sourceLang`을 누락/오염해도 핵심 4필드 교정 흐름과 카드 저장은 막히지 않는다(null → 통과).
- [ ] 기존 feature-레벨 게이트(`normalizeLanguageFeature`)·learningSignal 정규화는 그대로 유지한다.

---

# 기준 문서

- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-011_Multilingual_Correction_Evaluation_Gating.md` (본 FIX가 입력 출처를 교체하는 원 설계)
- `docs/handover/COR-TUNE-011_TURN_DETECTED_LANG_CAPTURE_HANDOVER.md` (폐기되는 선행 의존 — detectedLang 캡처)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002_Correction_Learning_Signal_Output.md` (learningSignal 출력 계약)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (AI값 보수적 정규화 — 동일 결)
- `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md` (평가/지표 갱신 정책)

---

# 데이터 흐름 (전환 후)

```
AI 교정 응답(suggestion.sourceLang)
   → CorrectionAiResponseMapper (보수적 파싱: unknown/미지원/누락 → null)
   → CorrectionSuggestion.sourceLang
   → CompleteCorrectionUseCase.buildCorrectionResult 단일 게이트 (sourceLang != selectedLang 확정 시 learningSignal 평가 제외)
```

(전환 전: `SessionTurn.detectedLang → CorrectionCandidate.sourceLang → CorrectionSuggestion.sourceLang → 게이트` — detectedLang 미공급으로 폐기)

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CHAT/Realtime | turn 단위 언어 태깅 **하지 않음**(STT 판별 불가). detectedLang handover 폐기 |
| CorrectionPromptBuilder | AI에 "원문 언어(sourceLang) 보고" 지시 + schema 노출 |
| CorrectionAiResponseMapper | AI sourceLang을 보수적으로 파싱해 `CorrectionSuggestion.sourceLang` 채움(불명→null) |
| CompleteCorrectionUseCase.buildCorrectionResult | `sourceLang != selectedLang` 확정 시 learningSignal 평가 제외(단일 지점, COR-TUNE-011 그대로) |
| 교정 생성/카드 저장 | 변경 없음 — 언어 불문 교정·저장 유지 |

---

# 주요 작업

1. **프롬프트**: `CorrectionPromptBuilder` 응답 schema의 suggestion 최상위에 `"sourceLang":"..."` 추가 + Rule("sourceText 기준 실제 사용 언어 코드; afterText 아님; 불확실하면 `unknown`").
2. **DTO**: `CorrectionAiSuggestionDto`에 `@SerialName("sourceLang") val sourceLang: String? = null` 추가.
3. **mapper 파싱**: `map()`에서 `sourceLang = LangCode.fromCode(item.sourceLang.orEmpty())?.takeIf { it != LangCode.UNKNOWN }` 로 보수적 정규화.
4. **seam 제거**: `SessionTurn.detectedLang`, `ExtractSessionCandidatesUseCase`의 sourceLang 전달, `CorrectionCandidate.sourceLang` 삭제.
5. **유지**: `CorrectionSuggestion.sourceLang` + `buildCorrectionResult` 게이트(COR-TUNE-011).

---

# 예외 처리

- AI가 `sourceLang` 누락/오타/미지원 코드/`unknown` → null → **평가 반영(통과)**. 과제외 방지.
- AI가 원문이 모국어인데 feature를 학습 언어로 채운 경우: 발화 원문 게이트(suggestion.sourceLang)가 우선해 평가에서 제외(핵심 오염 방지 대상, COR-TUNE-011과 동일).
- 혼합 발화("I go 학교"): AI가 우세/의도 언어로 보고. 거친 게이트가 통과시키면 feature-레벨 게이트가 학습 언어 신호만 추리는 2중 방어는 그대로.
- 교정 자체는 어떤 경우에도 막지 않는다 — 평가만 거른다.

---

# 검증 기준

- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- `CorrectionAiResponseMapperTest`: `sourceLang` = ko/en → 해당 LangCode, 누락/`unknown`/미지원 → null.
- `CorrectionPromptBuilderTest`: 프롬프트에 `sourceLang` schema 키 + Rule 문구 포함.
- `CompleteCorrectionUseCaseTest`: 게이트 회귀(일치 반영/불일치 제외+카드 생성/null no-op/혼재 독립판정) 통과.
- 코드(non-docs)에서 `detectedLang` 제거 확인. `sourceLang` 제외 판정이 `buildCorrectionResult` 단일 지점인지 grep 확인.
- 기존 feature-레벨 게이트·learningSignal 정규화·카드 저장 회귀 없음.
