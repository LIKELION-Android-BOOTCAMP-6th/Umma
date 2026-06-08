# [Fix] COR-FIX-008-C explanation 누락/공백 1회 재시도 후 drop

> 부모: [COR-FIX-008 교정 AI 응답 파싱/스키마 견고성](../COR-FIX-008_Correction_AI_Response_Parsing_Robustness.md)
> 출처 BugFix: `COR-FIX-EXPLANATION_MISSING_AI_RESPONSE.md`
>
> **정책 변경 안내**: 이 문서는 처음에 fallback(고정 문구로 대체)을 권장안으로 제시했다. 구현 단계에서
> 팀 결정으로 **1회 재시도 후 drop**으로 확정되어, 아래 "핵심 결정"/"주요 작업"/"테스트"는 최종 정책
> 기준으로 갱신했다(문서-코드 정합성). fallback 권장안은 "변경 전 권장안" 표시로만 남겨 둔다.

## 증상

```text
교정 결과 생성에 실패했어요
사유: Field 'explanation' is required for type with serial name
'...CorrectionAiSuggestionDto', but it was missing at path: $.suggestions[0]
```

AI가 반환한 첫 suggestion에 필수 필드 `explanation`이 없어 `kotlinx.serialization` 디코딩이 실패한다.

## 직접 원인

현재 `CorrectionAiResponseMapper.kt`는 `explanation`을 필수로 받고, 매핑 시 `requireFilled`로 공백까지 막는다.

```kotlin
explanation = item.explanation.requireFilled("explanation"),
```

DTO에서도 `explanation: String`(non-null)이라, 누락 시 매핑 이전 디코딩 단계에서 실패한다. 프롬프트는 이미 explanation을 요구하지만 LLM 응답은 확률적이라 프롬프트만으로 누락을 막을 수 없다.

## 핵심 결정

- **explanation을 nullable/default로 받아 핵심 4필드 흐름을 살린다.** `learningSignal`이 이미 nullable로 본체를 살리는 정책과 같은 결.
- **최종 정책(팀 결정): fallback이 아니라 1회 재시도 후 drop.** explanation 누락/공백은 "모델의 형식
  출력 실패"에 가까워, 고정 문구로 메우기보다 모델에게 다시 기회를 주는 편이 학습 콘텐츠 품질에
  낫다고 판단했다.
  - 1차 응답에서 explanation이 비어 있으면 mapper가 `BlankExplanationException`을 던지고,
    repository가 **전체 교정을 1회 재시도**한다 — malformed JSON 재시도와 **같은 1회 예산을 공유**한다
    (자세한 경계는 [COR-FIX-008-D](COR-FIX-008-D_Malformed_JSON_Single_Retry.md) 참조).
  - 재시도 응답에서도 explanation이 비어 있으면 **그 suggestion만 drop**한다(전체 실패 아님).
    candidate가 1개뿐이어서 결과가 빈 목록이 되면 화면이 `Phase.EmptyResult`("교정할 부분을
    찾지 못했어요")로 보일 수 있음을 인지하고 수용한다(과도한 fallback 문구 노출보다 낫다는 판단).
  - (아래 "변경 전 권장안"은 최초 설계 당시의 fallback 코드 스케치다 — 채택되지 않았다)
- **`nativeText`/`afterText`는 완화하지 않는다.** 카드 핵심 텍스트라 누락/공백 시 계속 실패한다. explanation만 "필수 hard fail"에서 "복구 가능 필드"로 낮춘다.

최종 구현 형태(요지 — 전체 코드는 `CorrectionAiResponseMapper.map()` / `CorrectionRepositoryImpl.generateAndMapWithRetry()` 참조):

```kotlin
// mapper: dropBlankExplanation 모드에 따라 1차에서는 throw, 재시도 pass에서는 drop
val explanation = item.explanation?.trim().orEmpty()
if (explanation.isEmpty()) {
    if (dropBlankExplanation) {
        logWarn("dropped — blank explanation, candidateId=${item.candidateId}")
        return@mapNotNull null
    }
    throw BlankExplanationException(item.candidateId)
}
```

```kotlin
// repository: malformed JSON(SerializationException)과 같은 1회 재시도 예산을 공유한다.
// 재시도 pass는 dropBlankExplanation=true로 호출해 그래도 비면 drop한다.
catch (e: Exception) {
    if (e !is SerializationException && e !is BlankExplanationException) throw e
    val retryRawJson = aiClient.generateJson(prompt + retryInstruction(e))
    responseMapper.map(retryRawJson, input, dropBlankExplanation = true)
}
```

변경 전 권장안(채택되지 않음 — fallback 문구 스케치):

```kotlin
private fun fallbackExplanation(primaryLang: LangCode): String =
    when (primaryLang) {
        LangCode.KO, LangCode.UNKNOWN -> "교정 표현을 자연스럽게 다듬었어요."
        LangCode.EN -> "The expression was corrected to sound natural."
        LangCode.JA -> "自然な表現に直しました。"
        LangCode.DE -> "Der Ausdruck wurde natürlicher korrigiert."
    }
```

## 완료 기준(AC)

- [ ] DTO `explanation`을 `String? = null`로 받는다.
- [ ] explanation 누락/공백이 정책(1회 재시도 후 drop)대로 처리되어 전체 교정이 실패하지 않는다.
- [ ] `nativeText`/`afterText` 누락/공백은 기존대로 `IllegalArgumentException`으로 실패한다 — 완화하지 않음(정책 고정).
- [ ] candidateId mismatch는 기존대로 실패한다.
- [ ] 정상/빈 응답의 기존 동작이 유지된다.
- [ ] 프롬프트에 "every suggestion MUST include explanation; never omit" 명시가 추가된다.
- [ ] 사용자 노출 메시지는 [COR-UX-002](../COR-UX-002_Correction_Failure_Error_Message.md) 정책을 따른다.

## 주요 작업

1. **DTO 완화** — `CorrectionAiSuggestionDto.explanation`을 nullable/default로:

```kotlin
@SerialName("explanation")
val explanation: String? = null,
```

2. **매핑부 정책 — 1회 재시도 후 drop(최종 결정)**:
   - `map()`이 `dropBlankExplanation: Boolean = false` 매개변수를 받는다.
   - 1차 시도(`dropBlankExplanation = false`): explanation이 비면 `BlankExplanationException`을
     던져 repository가 전체를 1회 재시도하게 한다(핵심 4필드 보존 우선, 즉시 실패하지 않음).
   - 재시도 pass(`dropBlankExplanation = true`): 그래도 비면 그 suggestion만 drop한다(전체 실패 아님).
   - (당초 권장이던 fallback 코드는 채택되지 않았다 — 위 "변경 전 권장안" 참조)

```kotlin
val explanation = item.explanation?.trim().orEmpty()
if (explanation.isEmpty()) {
    if (dropBlankExplanation) {
        logWarn("correction suggestion dropped - blank explanation, candidateId=${item.candidateId}")
        return@mapNotNull null
    }
    throw BlankExplanationException(item.candidateId)
}
```

3. **재시도 경계** — `CorrectionRepositoryImpl.generateAndMapWithRetry()`가 malformed JSON과 같은 1회
   예산을 공유한다. 상세는 [COR-FIX-008-D](COR-FIX-008-D_Malformed_JSON_Single_Retry.md) 참조.
4. **프롬프트 강화** — `CorrectionPromptBuilder`:

```text
- Every emitted suggestion MUST include candidateId, nativeText, afterText, and explanation.
- Never omit explanation. If the reason is simple, still provide a short tip.
```

## 테스트

`CorrectionAiResponseMapperTest.kt`:

1. explanation 있는 정상 응답은 성공.
2. explanation 누락(1차, `dropBlankExplanation = false`) → `BlankExplanationException`.
3. explanation blank(1차) → 동일하게 `BlankExplanationException`.
4. explanation 누락(재시도 pass, `dropBlankExplanation = true`) → 해당 suggestion만 drop(빈 목록).
5. `nativeText`/`afterText` 누락/blank → 여전히 `IllegalArgumentException`(완화하지 않음, 정책 고정).
6. candidateId mismatch → 기존대로 실패.

throw assert 예: `assertThrowsType<BlankExplanationException> { mapper.map(rawJson, baseInput()) }`
drop assert 예: `assertTrue(suggestions.isEmpty())`

`CorrectionRepositoryImplTest.kt`(재시도 경계, `FakeCorrectionAiClient` 순차 응답 큐 사용):

7. 1차 explanation 누락 → 재시도 valid → success(정상 explanation 매핑, `generateJson` 2회).
8. 1차·재시도 모두 explanation 누락 → success(drop, 빈 목록), 전체 실패 아님.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionAiResponseMapperTest"
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.CorrectionRepositoryImplTest"
```

## Out of Scope

- 사용자 에러 메시지 정리(→ COR-UX-002)
- `nativeText`/`afterText`까지 fallback로 완화(카드 핵심 텍스트라 제외)
- learningSignal 정책 변경
