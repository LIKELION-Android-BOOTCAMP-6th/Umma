# [Fix] COR-FIX-008 교정 AI 응답 파싱/스키마 견고성

## User Story

학습자는 대화 후 `교정` 화면에 진입하면, AI가 만든 교정 카드를 안정적으로 받아야 한다. 그런데 교정 결과는 LLM(Gemini)이 생성한 JSON을 앱이 `kotlinx.serialization`으로 디코딩해 만든다. LLM 응답은 확률적이라 같은 프롬프트에도 **스키마를 어긴 JSON**을 종종 내고, 그때마다 디코딩 단계에서 전체 교정 생성이 실패한다. 사용자는 교정 카드 대신 `다시 시도` 버튼과 내부 파서 예외 문자열만 보게 된다.

이 Flow 문서는 "AI 응답 JSON이 앱이 기대한 계약을 어겨도, 교정의 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`) 흐름과 카드 저장을 가능한 한 살린다"는 **교정 응답 파싱 견고성**을 하나의 Fix 흐름으로 관리한다. 개별 실패 모드는 아래 작업 단위로 작게 나눠 처리한다.

추가 schema drift가 발견되면 `COR-FIX-008-E`, `COR-FIX-008-F`처럼 이 문서에 작업 단위를 확장한다.

---

## 배경 — 공통 근본 원인

`CorrectionAiResponseMapper.map()`은 raw JSON을 곧바로 단일 DTO로 디코딩한다.

```kotlin
val response = json.decodeFromString(CorrectionAiResponseDto.serializer(), rawJson)
```

이 한 줄이 모든 실패의 공통 지점이다. JSON 문법이 깨지거나(B/D 계열), 최상위 형태가 다르거나(A), 필드 타입/존재가 계약과 다르면(B/C) 여기서 예외가 터지고 `runCatching`으로 감싼 `learningSignal` 정규화 방어선보다 **앞단**이라 보호되지 못한다. 즉 방어 책임이 프롬프트에만 있어서는 안 되고, **mapper(파싱 경계)** 에도 있어야 한다.

공통 파이프라인:

```
CorrectionViewModel.triggerGeneration
→ GenerateSuggestionsUseCase
→ CorrectionRepositoryImpl.generateSuggestions
→ CorrectionPromptBuilder.build
→ CorrectionAiClient.generateJson (responseMimeType=application/json)
→ CorrectionAiResponseMapper.map → Json.decodeFromString(...)
```

공통 관련 파일:

- `app/src/main/java/com/app/umma/data/repository/correction/CorrectionAiResponseMapper.kt`
- `app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt`
- `app/src/main/java/com/app/umma/data/repository/CorrectionRepositoryImpl.kt`
- `app/src/test/java/com/app/umma/data/repository/correction/CorrectionAiResponseMapperTest.kt`
- `app/src/test/java/com/app/umma/data/repository/CorrectionRepositoryImplTest.kt`

---

## 핵심 결정 (공통 원칙)

- **mapper 방어 우선, 프롬프트 강화 보조.** `responseMimeType=application/json`은 JSON 형식만 강제할 뿐 앱이 기대한 스키마까지 보장하지 않는다. 프롬프트는 누락/오류 확률을 낮출 뿐 0으로 만들지 못하므로, 파싱 경계에서 한 번 더 방어한다.
- **핵심 4필드를 살린다.** `candidateId`/`nativeText`/`afterText`/`explanation`이 유효하면 교정 카드는 살린다. `learningSignal`·`languageFeatures` 같은 보조 신호는 부분 제외하더라도 전체 교정을 죽이지 않는다.
- **계약 위반(틀린 응답)은 여전히 실패.** unknown candidateId, `nativeText`/`afterText` 공백, candidate 언어 mismatch 등 "JSON은 맞지만 계약이 틀린" 응답은 느슨해지지 않는다(candidateId 계약은 [COR-FIX-009](../COR-FIX-009_Unknown_Candidate_Id_Contract.md)에서 별도 강화).
- **재시도는 "모델 형식 출력 실패"에만, 1회만.** malformed JSON(`SerializationException`, 작업 단위 D)과 explanation 누락/공백(`BlankExplanationException`, 작업 단위 C)이 **하나의 1회 재시도 예산을 공유**한다. 계약 검증 실패(`IllegalArgumentException` — unknown candidateId·필수 텍스트 공백·언어 mismatch)는 재시도하지 않는다(같은 위반 반복 확률이 높아 비용만 증가). 재시도 pass는 explanation이 그래도 비면 drop한다.
- **사용자 에러 메시지는 분리한다.** raw 파서 예외를 화면에 그대로 노출하지 않는 작업은 모든 실패 모드에 공통이므로, 본 문서에서 중복 구현하지 않고 [COR-UX-002](../COR-UX-002_Correction_Failure_Error_Message.md)로 위임한다. 본 문서 자식들은 내부 진단 로그(Logcat)만 남긴다.

---

## 작업 단위

| 작업 ID | 범위 | 실패 모드 | 출처 BugFix |
| --- | --- | --- | --- |
| `COR-FIX-008-A` | 최상위 JSON 배열 허용 | `[ {...} ]`를 최상위로 반환 → `Expected '{' but had '['` | `CORRECTION_AI_TOP_LEVEL_ARRAY_JSON_PARSE_ERROR.md` |
| `COR-FIX-008-B` | `languageFeatures` 문자열 배열 정규화 | `["EN.Tense"]` 타입 불일치 → `Expected '{' but had '"'` | `CORRECTION_LANGUAGE_FEATURES_JSON_PARSE_ERROR.md` |
| `COR-FIX-008-C` | `explanation` 누락/공백 1회 재시도 후 drop | 필수 필드 누락 → `Field 'explanation' is required` | `COR-FIX-EXPLANATION_MISSING_AI_RESPONSE.md` |
| `COR-FIX-008-D` | malformed JSON 1회 재시도 | 문자열 밖 토큰(`X`) → `Unexpected JSON token` | `CORRECTION_AI_EXPLANATION_TRAILING_TOKEN_JSON_PARSE_ERROR.md` |
| `COR-FIX-008-E+` | 추후 schema drift | AI 응답 계약 위반이 추가로 발견되면 작업 단위 확장 | — |

각 작업 단위의 상세 설계·AC·테스트는 자식 문서를 본다.

- 최상위 배열: `COR-FIX-008/COR-FIX-008-A_TopLevel_Array_Tolerance.md`
- languageFeatures 문자열 배열: `COR-FIX-008/COR-FIX-008-B_LanguageFeatures_String_Array_Normalization.md`
- explanation 누락 1회 재시도 후 drop: `COR-FIX-008/COR-FIX-008-C_Explanation_Missing_Fallback.md` (구현 시 fallback → 재시도 후 drop으로 정책 변경, 문서 본문에 갱신됨)
- malformed JSON 재시도: `COR-FIX-008/COR-FIX-008-D_Malformed_JSON_Single_Retry.md`

---

## 완료 기준(AC) — 부모 레벨

- [ ] 네 가지 실패 모드(A/B/C/D) 각각에서, AI가 스키마를 어겨도 앱이 직렬화 예외로 전체 교정 생성을 실패시키지 않는다(D는 재시도까지 실패한 경우 제외).
- [ ] 정상 응답(`{"suggestions":[...]}`), 빈 응답(`{"suggestions":[]}`)의 기존 동작이 회귀 없이 유지된다.
- [ ] 핵심 4필드가 유효하면 교정 카드 생성·저장 경로(Flashcard, LangState)는 기존대로 동작한다.
- [ ] unknown candidateId / 필수 텍스트 공백 / 언어 mismatch 같은 계약 검증은 느슨해지지 않는다.
- [ ] 모든 실패 모드의 사용자 노출 메시지는 [COR-UX-002](../COR-UX-002_Correction_Failure_Error_Message.md) 정책을 따른다(raw 파서 예외 비노출).
- [ ] `CorrectionAiResponseMapperTest` / `CorrectionRepositoryImplTest`에 각 실패 모드 회귀 테스트가 추가/갱신되어 통과한다.

---

## 기준 문서

- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002_Correction_Learning_Signal_Output.md` (learningSignal 출력 계약)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (AI값 보수적 정규화 — 같은 결)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-011-FIX_AI_Sourced_Utterance_Language_Gating.md` (suggestion 최상위 `sourceLang` 파싱 — 보수적 파싱 선례)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-FIX-009_Unknown_Candidate_Id_Contract.md` (candidateId 계약 강화 — 본 문서가 유지하는 검증을 강화)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-UX-002_Correction_Failure_Error_Message.md` (사용자/개발 에러 메시지 분리 — 공통 위임처)

---

## 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionPromptBuilder | 각 실패 모드별 응답 규칙 명시(최상위 객체, languageFeatures 객체 배열, explanation 필수, 문자열 밖 토큰 금지). 확률 저감만 담당 |
| CorrectionAiResponseMapper | 파싱 경계 방어. 최상위 형태 판별(A), languageFeatures 관대 정규화(B), explanation 1회 재시도 후 drop(C, 1차엔 BlankExplanationException throw·재시도 pass엔 drop). 계약 위반은 그대로 실패 |
| CorrectionRepositoryImpl | malformed JSON(`SerializationException`)에 한정한 1회 재시도 경계(D). 계약 실패는 재시도하지 않음 |
| CorrectionViewModel/UiState/Screen | 실패 시 사용자 메시지 정책 — 본 문서는 다루지 않고 COR-UX-002로 위임 |
| 교정 카드 생성/저장 | 변경 없음 — 핵심 4필드가 살면 기존대로 저장 |

---

## 구현 범위

### 포함 범위

- 최상위 JSON 객체/배열 양쪽 수용 (A)
- `languageFeatures` 객체 배열/문자열 배열 양쪽 수용 + 이상 항목 부분 제외 (B)
- `explanation` 누락/공백 시 1회 재시도 후 drop 정책 고정 (C — fallback 문구 대체안은 채택되지 않음)
- `SerializationException` 한정 1회 재시도 (D)
- 각 실패 모드 프롬프트 규칙 강화
- 각 실패 모드 회귀 테스트

### 제외 범위 (Out of Scope)

- 교정 화면 전체 UX 개편
- Gemini 모델 교체 / AI 응답 schema 전면 재설계
- learningSignal enum allowlist 정책 변경(allowlist 확장 여부는 B에서 국소 결정)
- candidateId 계약 강화·ID 결정성 조사 → COR-FIX-009
- 사용자 에러 메시지 문구·로그 분리 구현 → COR-UX-002
- 문자열 밖 임의 토큰을 앱에서 자동 제거하는 sanitizer (위험 대비 효용 낮음, D에서 비권장)

---

## 검증 기준

```powershell
.\gradlew.bat compileDevDebugKotlin
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionAiResponseMapperTest"
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.CorrectionRepositoryImplTest"
.\gradlew.bat testDevDebugUnitTest
```

- A: 최상위 배열 응답이 suggestion list로 매핑된다. 배열에서도 unknown candidateId/blank 필드는 실패한다.
- B: `["EN.Tense"]`가 객체로 정규화된다. allowlist 밖/이상 타입 항목은 제외하되 suggestion·learningSignal은 유지한다.
- C: `explanation` 누락/공백이 정책(1차 BlankExplanationException → 전체 1회 재시도 → 그래도 비면 해당 suggestion만 drop)대로 처리된다. `nativeText`/`afterText` 누락은 여전히 실패.
- D: 첫 응답 malformed→두 번째 valid면 성공, 둘 다 malformed면 실패, unknown candidateId는 재시도 없이 실패. 두 번째 prompt에 retry 지시 포함.

---

## Edge Cases

- candidate가 1개뿐인데 재시도 후에도 explanation이 비어 그 suggestion이 drop되어 결과가 빈 목록이 됨 (C — `Phase.EmptyResult` 수용 정책)
- 최상위 배열 + 내부 languageFeatures 문자열 배열이 동시에 옴 (A+B 복합)
- malformed JSON 재시도 응답이 또 다른 실패 모드(계약 위반)로 옴 (D→재시도하지 않는 경계)
- 빈 `{"suggestions":[]}` 정상 응답
- learningSignal 일부만 깨지고 핵심 4필드는 정상
