# [Fix] COR-FIX-008-A 교정 응답 최상위 배열 허용

> 부모: [COR-FIX-008 교정 AI 응답 파싱/스키마 견고성](../COR-FIX-008_Correction_AI_Response_Parsing_Robustness.md)
> 출처 BugFix: `CORRECTION_AI_TOP_LEVEL_ARRAY_JSON_PARSE_ERROR.md`

## 증상

교정 결과 생성 중 아래 에러가 사용자 화면에 노출된다.

```text
교정 결과 생성에 실패했어요
사유: Unexpected JSON token at offset 0: Expected start of the object '{', but had '[' instead at path: $
JSON input: [ { "candidateId": "en-1.....
```

앱은 최상위가 `{"suggestions":[...]}` 객체이길 기대하지만, Gemini가 가끔 최상위 배열 `[ {...} ]`을 직접 반환한다. `CorrectionAiResponseMapper.map()`이 객체 DTO로 바로 디코딩하면서 파싱 단계에서 실패한다.

## 직접 원인

`CorrectionAiResponseMapper.kt`:

```kotlin
val response = json.decodeFromString(CorrectionAiResponseDto.serializer(), rawJson)
```

`CorrectionAiResponseDto`는 최상위 `suggestions` 필드를 가진 객체 DTO다. 최상위가 배열이면 디코딩 전에 실패한다. `responseMimeType=application/json`은 JSON 형식만 강제할 뿐 최상위 스키마는 보장하지 않으므로, 방어 책임은 mapper에도 있다.

## 핵심 결정

- **mapper에서 최상위 element를 먼저 판별해 객체/배열 양쪽을 수용한다.** `JsonObject`면 기존대로, `JsonArray`면 `List<CorrectionAiSuggestionDto>`로 디코딩 후 `CorrectionAiResponseDto(suggestions = list)`로 감싼다. 그 외 타입은 명확한 `IllegalArgumentException`.
- **기존 검증은 유지한다.** 배열을 허용해도 그 안의 unknown candidateId, blank 필수 필드, 언어 mismatch는 계속 실패한다.
- **중첩 schema까지 무리하게 복구하지 않는다.** 최상위 형태만 관대화한다.

## 완료 기준(AC)

- [ ] `CorrectionAiResponseMapper`가 최상위 `JsonObject`와 `JsonArray`를 모두 받아 동일한 suggestion list로 매핑한다.
- [ ] 최상위가 객체도 배열도 아니면 `IllegalArgumentException`으로 실패한다.
- [ ] 최상위 배열에서도 unknown candidateId / blank 필수 필드는 실패한다.
- [ ] 기존 정상 객체 응답과 빈 `{"suggestions":[]}` 응답이 회귀 없이 통과한다.
- [ ] 프롬프트에 "최상위 JSON은 `suggestions` 배열을 가진 객체여야 하며 bare array 금지" 규칙이 추가된다.
- [ ] 사용자 노출 메시지는 [COR-UX-002](../COR-UX-002_Correction_Failure_Error_Message.md) 정책을 따른다.

## 주요 작업

1. **mapper 디코딩 분기** — `decodeResponse()` helper 추가:

```kotlin
private fun decodeResponse(rawJson: String): CorrectionAiResponseDto {
    val root = json.parseToJsonElement(rawJson)
    return when (root) {
        is JsonObject -> json.decodeFromJsonElement(CorrectionAiResponseDto.serializer(), root)
        is JsonArray -> CorrectionAiResponseDto(
            suggestions = json.decodeFromJsonElement(
                ListSerializer(CorrectionAiSuggestionDto.serializer()),
                root
            )
        )
        else -> throw IllegalArgumentException("correction AI response must be a JSON object or array")
    }
}
```

필요 imports: `kotlinx.serialization.builtins.ListSerializer`, `kotlinx.serialization.json.{JsonArray, JsonObject, decodeFromJsonElement, parseToJsonElement}`.

2. **프롬프트 강화** — `CorrectionPromptBuilder`에 추가:

```text
The top-level JSON value MUST be an object with a "suggestions" array.
Do NOT return a bare array as the top-level JSON value.
```

(프롬프트만으로는 불충분 — Gemini가 이미 bare array를 낸 적이 있으므로 mapper fallback이 필수.)

3. `CorrectionAiSuggestionDto`/`CorrectionAiResponseDto`가 `private`이므로 같은 파일 안 helper로 추가한다.

## 테스트

`CorrectionAiResponseMapperTest.kt`:

1. top-level array 응답이 suggestion list로 매핑된다(`corr-en-0-a` id, afterText 등 검증).
2. top-level array에서도 unknown candidateId는 실패한다.
3. top-level array에서도 blank 필수 필드는 실패한다.
4. 기존 정상 object / `{"suggestions":[]}` 테스트가 계속 통과한다.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionAiResponseMapperTest"
.\gradlew.bat compileDevDebugKotlin
```

## Out of Scope

- 교정 화면 UX 개편, errorReason 노출 정책(→ COR-UX-002)
- Gemini 모델 교체 / AI 응답 schema 재설계
- learningSignal enum allowlist 정책 변경
- suggestion 내부 nested schema 복구
