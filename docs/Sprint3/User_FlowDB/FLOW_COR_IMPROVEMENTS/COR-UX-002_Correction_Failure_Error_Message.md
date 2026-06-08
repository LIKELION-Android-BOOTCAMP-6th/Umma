# [UX] COR-UX-002 교정 실패 에러 메시지 사용자/개발 로그 분리

> 출처 BugFix: `COR-FIX-EXPLANATION_MISSING_AI_RESPONSE.md`, `CORRECTION_AI_EXPLANATION_TRAILING_TOKEN_JSON_PARSE_ERROR.md`, `CORRECTION_LANGUAGE_FEATURES_JSON_PARSE_ERROR.md`, `CORRECTION_UNKNOWN_CANDIDATE_ID_JA.md` (공통 횡단 관심사)
> 관련: [COR-FIX-008](COR-FIX-008_Correction_AI_Response_Parsing_Robustness.md), [COR-FIX-009](COR-FIX-009_Unknown_Candidate_Id_Contract.md)

## User Story

교정 생성이 실패했을 때, 학습자는 직렬화 라이브러리의 내부 예외 문자열이 아니라 **이해 가능한 안내 문구**를 봐야 한다. 현재는 실패 시 raw parser exception이 그대로 노출된다.

```text
교정 결과 생성에 실패했어요
사유: Unexpected JSON token at offset 285: Expected quotation mark '"', but had 'X' ...
```
```text
사유: Field 'explanation' is required for type with serial name '...CorrectionAiSuggestionDto' ...
```
```text
사유: unknown correction candidate id: ja-6-0-79967d5
```

이 메시지들은 일반 사용자에게 의미가 없고, 내부 ID·구현 세부를 노출해 UX가 거칠다.

## 왜 독립 문서인가

이 "사용자 노출 메시지 vs 개발 진단 로그 분리"는 COR-FIX-008(A/B/C/D)과 COR-FIX-009 **여러 실패 모드에 공통으로 반복**된다. 각 Fix 문서에서 따로 구현하면 4~5곳에 메시지/로그 정책이 분산되고 충돌한다. 따라서 **한 곳에서 일관 정책으로** 처리하고, 각 Fix 문서는 본 문서를 참조만 한다.

## 현재 흐름

`CorrectionUiState.applyGenerationOutcome()`이 실패 시 `e.message ?: e.javaClass.simpleName`을 `errorReason`에 저장하고, `CorrectionScreen`의 `CorrectionError()`가 이를 화면에 그대로 표시한다. 그래서 내부 예외 메시지가 노출된다. (현재 화면 주석에도 "운영에서는 errorReason이 기술 메시지일 수 있으므로 별도 매핑 검토" 취지가 있음.)

관련 파일:

- `app/src/main/java/com/app/umma/presentation/correction/CorrectionUiState.kt`
- `app/src/main/java/com/app/umma/presentation/correction/CorrectionViewModel.kt`
- `app/src/main/java/com/app/umma/presentation/correction/CorrectionScreen.kt`

## 핵심 결정

- **사용자 화면은 고정 안내 문구.** raw 예외를 표시하지 않는다. 실패 유형별로 짧은 한국어 문구를 둔다(아래 매핑).
- **내부 사유는 Logcat/디버그 경로에 유지.** `errorReason`은 디버깅용으로 state에 남겨도 되지만, `CorrectionError()`는 그것을 원문 그대로 렌더링하지 않는다.
- **유형 매핑은 최소·안정적으로.** 과도한 분기 대신, 직렬화/형식 오류 vs 일반 실패 정도로 구분한다. 다른 flow에 이미 통일된 에러 메시지 패턴이 있으면 그 패턴을 우선 따른다.

권장 사용자 문구 매핑:

| 실패 유형 | 사용자 문구(예) |
| --- | --- |
| JSON 형식/직렬화 오류 (008-A/B/D) | `교정 결과 형식이 올바르지 않아 다시 시도해 주세요.` |
| explanation 등 필드 해석 실패 (008-C) | `교정 결과를 해석하지 못했어요. 다시 시도해 주세요.` |
| candidateId 등 계약 실패 (009) | `교정 결과를 불러오지 못했어요. 다시 시도해 주세요.` |
| 기타/네트워크 | `잠시 후 다시 시도해 주세요.` |

(문구는 디자인/카피 합의로 통일 가능. 핵심은 "raw 예외 비노출".)

## 완료 기준(AC)

- [ ] `CorrectionError()`가 `errorReason` 원문을 그대로 표시하지 않는다.
- [ ] 사용자 화면에 `Unexpected JSON token...` / `Field '...' is required` / `unknown correction candidate id: ...` 같은 내부 문자열이 노출되지 않는다.
- [ ] 내부 실패 사유는 Logcat(또는 디버그 경로)에 보존되어 개발자가 원인을 추적할 수 있다.
- [ ] 정상 교정 생성/저장 흐름과 기존 Retry 동작은 회귀 없이 유지된다.
- [ ] 다른 화면과 통일된 에러 메시지 패턴이 있으면 그 패턴을 따른다.

## 주요 작업

1. **표시 계층 분리** — `CorrectionScreen.CorrectionError()`는 고정 안내 문구를 표시:

```kotlin
Text(
    text = "잠시 후 다시 시도해 주세요.",  // 또는 유형별 문구
    textAlign = TextAlign.Center,
)
```

2. **사유 보존·로깅** — `CorrectionViewModel`/mapper/repository 레벨에서 원본 예외 메시지를 Logcat에 남긴다(`errorReason`은 진단용으로 state에 둘 수 있음).
3. (선택) 실패 유형 → 사용자 문구 매핑 함수 도입. 과분기 지양.

## 테스트

- `CorrectionUiStateTest`: `errorReason` 저장 정책과 "사용자 표시 메시지 ≠ 내부 reason" 분리 검증. Compose UI 테스트가 없으면 pure state 수준에서 errorReason 저장 정책만 확인하고, 화면은 수동/스냅샷 확인으로 대체 가능.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.presentation.correction.CorrectionUiStateTest"
```

## Out of Scope

- 교정 화면 전체 UX 개편
- mapper/repository의 파싱·재시도·계약 방어 자체(→ COR-FIX-008 / COR-FIX-009)
- 에러 상태에서의 자동 재시도 정책 변경
