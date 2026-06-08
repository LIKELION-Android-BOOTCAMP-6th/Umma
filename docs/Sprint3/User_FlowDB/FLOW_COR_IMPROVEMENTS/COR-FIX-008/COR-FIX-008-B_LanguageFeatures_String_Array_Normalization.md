# [Fix] COR-FIX-008-B languageFeatures 문자열 배열 정규화

> 부모: [COR-FIX-008 교정 AI 응답 파싱/스키마 견고성](../COR-FIX-008_Correction_AI_Response_Parsing_Robustness.md)
> 출처 BugFix: `CORRECTION_LANGUAGE_FEATURES_JSON_PARSE_ERROR.md`

## 증상

```text
교정 결과 생성에 실패했어요
사유: Unexpected JSON token at offset 1588:
Expected start of the object '{', but had '"' instead at path:
$.suggestions[1].learningSignal.languageFeatures[0]
JSON input: ... "languageFeatures": [ "EN.NounPhrase" ] ...
```

앱은 `languageFeatures`를 객체 배열 `[{"lang":"EN","featureKey":"EN.Tense"}]`로 기대하는데, AI가 문자열 배열 `["EN.NounPhrase"]`로 내려 디코더가 `languageFeatures[0]`에서 객체 대신 문자열을 만나 전체 파싱이 실패한다.

## 직접 원인

타입 불일치가 `decodeFromString()` 단계에서 터지기 때문에, `normalizeLearningSignal()`의 `runCatching` 방어가 보호하지 못한다(파싱이 정규화보다 앞단). 현재 DTO:

```kotlin
@SerialName("languageFeatures")
val languageFeatures: List<LanguageFeatureDto>? = null,
```

추가로, 스크린샷의 `"EN.NounPhrase"`는 현재 `ALLOWED_FEATURE_KEYS`(EN.Article/Preposition/Tense, JA.Particle/Honorific/VerbConjugation)에 없으므로, 문자열을 파싱 가능하게 해도 `normalizeLanguageFeature()`에서 제외될 가능성이 크다.

## 핵심 결정

- **languageFeatures는 학습 분석 보조 신호다.** allowlist 밖/타입 이상 항목 때문에 **전체 교정을 실패시키면 안 된다.** 해당 feature만 제외하고 suggestion·learningSignal은 살린다(현재 주석상 "languageFeatures는 유일한 부분 제외 예외"와 일치).
- **객체 배열/문자열 배열 양쪽을 수용한다.** 문자열 `"EN.Tense"`는 `lang="EN", featureKey="EN.Tense"`로 정규화. namespace 없거나 미지의 prefix면 `lang=null, featureKey=raw`.
- **구현 방식**은 둘 중 선택:
  - **방식 A (custom serializer)** — `LanguageFeatureDto`/리스트에 객체·문자열 양쪽 받는 serializer. 영향 범위 작고 테스트 쉬움. DTO 구조 유지. 이번 버그만 보면 가장 깔끔.
  - **방식 B (`JsonElement` 수동 정규화)** — `languageFeatures`를 `JsonArray?`로 받고 normalize에서 처리. 숫자/null/이상값까지 강하게 방어, 향후 drift 대응 유리. 타입 안정성은 약간 감소.
  - 권장: 영향 최소화 → custom serializer. 숫자 혼합 같은 이상 타입까지 방어 필요 시 → JsonElement.
- **allowlist 확장 여부는 국소 결정.** `EN.NounPhrase` 등을 허용 feature로 추가할지는 제품 판단. 단, 추가하든 안 하든 "전체 파싱 실패 금지" 원칙이 우선.

## 완료 기준(AC)

- [ ] `languageFeatures`가 객체 배열이면 기존대로 처리된다.
- [ ] `languageFeatures`가 문자열 배열이면 `{LANG}.{Feature}` 정규화 후 처리된다.
- [ ] allowlist 밖 feature는 제외되더라도 suggestion·learningSignal은 유지된다.
- [ ] (방식 B 채택 시) 숫자 등 이상 타입이 섞여도 유효 항목만 유지하고 전체 파싱은 실패하지 않는다.
- [ ] learningSignal 일부가 깨져도 핵심 4필드 suggestion은 유지된다.
- [ ] 프롬프트에 "languageFeatures는 객체 배열만, 문자열 배열 금지" 규칙이 추가된다.
- [ ] 사용자 노출 메시지는 [COR-UX-002](../COR-UX-002_Correction_Failure_Error_Message.md) 정책을 따른다.

## 주요 작업

1. **mapper 관대 정규화** — 문자열→객체 변환 규칙:
   - `"EN.Tense"` → `lang="EN", featureKey="EN.Tense"`
   - `"JA.Particle"` → `lang="JA", featureKey="JA.Particle"`
   - namespace 없음/미지 → `lang=null, featureKey=raw` → 이후 allowlist/lang 게이트에서 제외 가능
2. **프롬프트 강화** — `CorrectionPromptBuilder`의 languageFeatures 규칙에 추가:

```text
- languageFeatures MUST be an array of objects, never strings.
- Correct: [{"lang":"EN","featureKey":"EN.Tense"}]
- Incorrect: ["EN.Tense"]
```

3. allowlist 확장 여부 결정(`ALLOWED_FEATURE_KEYS`).

## 테스트

`CorrectionAiResponseMapperTest.kt`:

1. `languageFeatures: ["EN.Tense"]` → throw 없음, `featureKey=="EN.Tense"`, `lang==LangCode.EN`.
2. `languageFeatures: ["EN.NounPhrase"]`(allowlist 밖) → throw 없음, suggestion·learningSignal 유지, feature는 정책(제외 또는 allowlist 추가 시 유지).
3. (방식 B) `["EN.Tense", 123, {"lang":"EN","featureKey":"EN.Article"}]` → 유효 항목만 유지, 숫자 제외, suggestion 유지.
4. learningSignal 일부 손상 시 핵심 4필드 suggestion 유지.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionAiResponseMapperTest"
.\gradlew.bat compileDevDebugKotlin
```

## Out of Scope

- 사용자 에러 메시지 정리(→ COR-UX-002)
- learningSignal 출력 계약 전면 변경(COR-TUNE-002 영역)
- Gemini 모델 교체
