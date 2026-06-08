# [Fix] COR-FIX-008-D malformed JSON 한정 1회 재시도

> 부모: [COR-FIX-008 교정 AI 응답 파싱/스키마 견고성](../COR-FIX-008_Correction_AI_Response_Parsing_Robustness.md)
> 출처 BugFix: `CORRECTION_AI_EXPLANATION_TRAILING_TOKEN_JSON_PARSE_ERROR.md`

## 증상

```text
교정 결과 생성에 실패했어요
사유: Unexpected JSON token at offset 285:
Expected quotation mark '"', but had 'X' instead at path:
$.suggestions[0].explanation
JSON input: ..... "is"를 사용하는 것이 자연스러워요." X}, { "candidateId": ...
```

`explanation` 문자열이 끝난 뒤, 다음 필드/객체로 넘어가기 전에 JSON 문자열 **밖**에 `X` 같은 마커가 끼어 JSON 자체가 깨진다. `kotlinx.serialization`이 DTO 디코딩 전에 실패한다.

## 직접 원인

A/B/C는 "구조/타입/필드"가 어긋난 경우라 mapper에서 형태 판별로 복구할 수 있지만, 이번은 **JSON 문법 자체가 깨진** 경우다. 문자열 밖 임의 토큰을 앱이 안전하게 잘라낼 보장이 없어, mapper에서의 복구 대신 **재생성(재시도)** 이 더 안전하다.

```kotlin
val response = json.decodeFromString(CorrectionAiResponseDto.serializer(), rawJson)
```

## 핵심 결정

- **`SerializationException` 계열에만 1회 재시도한다.** JSON 문법/타입 디코딩 실패는 모델의 형식 출력 실패일 가능성이 커 1회 재호출 가치가 있다.
- **`IllegalArgumentException`은 재시도하지 않는다.** unknown candidateId, 필수 텍스트 공백, 언어 mismatch 등 계약 검증 실패는 재호출해도 같은 계약 위반일 확률이 높아 비용만 는다.
- **재시도는 1회로 제한.** 재시도 prompt에 "이전 응답이 유효한 JSON이 아니었다, 따옴표 밖 문자 금지" 지시를 덧붙인다.
- **파싱 전 sanitizer는 비권장.** 문자열 밖 토큰 자동 제거는 어디까지가 안전한 수리인지 판별이 어렵고, 잘못 수리하면 잘못된 교정 결과를 정상처럼 저장할 위험이 있다. 우선순위: 프롬프트 강화 → 1회 재시도 → 에러 메시지 정리 → (정말 필요하면) 제한적 sanitizer.

우선순위가 A/B/C와 다른 점: 본 작업은 **mapper가 아니라 repository(재시도 경계)** 에 변경이 들어간다. mapper는 malformed JSON을 그대로 실패시키는 책임을 유지한다.

## 완료 기준(AC)

- [ ] 첫 응답 malformed JSON, 두 번째 valid JSON이면 교정 카드가 생성된다(success).
- [ ] 첫 응답 malformed, 두 번째도 malformed면 실패한다(재시도 1회 후 종료).
- [ ] unknown candidateId(계약 위반)는 재시도 없이 즉시 실패한다.
- [ ] 재시도 두 번째 prompt에 retry 지시가 포함된다.
- [ ] 프롬프트에 "문자열 값 밖 마커/문자 금지, strict JSON 파서로 파싱 가능" 규칙이 추가된다.
- [ ] 사용자 노출 메시지는 [COR-UX-002](../COR-UX-002_Correction_Failure_Error_Message.md) 정책을 따른다.

## 주요 작업

1. **repository 재시도 경계** — `CorrectionRepositoryImpl`에 private helper:

```kotlin
private suspend fun generateAndMapWithMalformedJsonRetry(
    prompt: String,
    input: GenerateSuggestionsInput
): List<CorrectionSuggestion> {
    val rawJson = aiClient.generateJson(prompt)
    return try {
        responseMapper.map(rawJson, input)
    } catch (e: SerializationException) {
        val retryPrompt = prompt +
            "\n\nYour previous response was not valid JSON. Retry once and return ONLY a syntactically valid JSON object. " +
            "Do not put any characters outside quoted JSON string values."
        val retryRawJson = aiClient.generateJson(retryPrompt)
        try {
            responseMapper.map(retryRawJson, input)
        } catch (retryFailure: SerializationException) {
            retryFailure.addSuppressed(e)
            throw retryFailure
        }
    }
}
```

`SerializationException` import 필요. `IllegalArgumentException`은 catch하지 않으므로 자연히 재시도에서 제외된다.

2. **프롬프트 강화** — `CorrectionPromptBuilder`:

```text
- Do not put any marker, grade, label, letter, or extra character outside JSON string values.
- Every string value must be valid JSON-escaped text. Escape quotation marks inside explanation.
- The response must parse with a strict JSON parser.
```

## 테스트

`CorrectionRepositoryImplTest.kt` — `FakeCorrectionAiClient`가 응답을 순서대로 반환하도록 확장:

1. 첫 malformed → 두 번째 valid → success. `generateJson()` 2회 호출, 두 번째 prompt에 retry 지시 포함, 최종 정상 매핑.
2. 첫 malformed → 두 번째 malformed → failure.
3. unknown candidateId → `generateJson()` 1회만, 재시도 없이 failure.

malformed 예시:

```json
{ "suggestions": [ { "candidateId": "en-0-a", "nativeText": "나는 학생이에요.", "afterText": "I am a student.", "explanation": "\"am\"을 사용하는 것이 자연스러워요." X} ] }
```

`CorrectionAiResponseMapperTest.kt` — mapper는 malformed JSON을 `SerializationException`으로 실패시키는 것만 확인(재시도 경계는 repository 책임).

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.CorrectionRepositoryImplTest"
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionAiResponseMapperTest"
```

## Out of Scope

- 사용자 에러 메시지 정리(→ COR-UX-002)
- 문자열 밖 토큰 자동 제거 sanitizer(위험 대비 효용 낮음)
- 2회 이상 재시도 / 모든 예외 재시도
- Gemini 모델 교체
