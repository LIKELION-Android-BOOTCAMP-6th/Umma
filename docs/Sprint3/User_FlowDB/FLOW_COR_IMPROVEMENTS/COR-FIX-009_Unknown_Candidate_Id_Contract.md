# [Fix] COR-FIX-009 교정 candidateId 계약 강화 및 진단

> 출처 BugFix: `CORRECTION_UNKNOWN_CANDIDATE_ID_JA.md`
> 관련: [COR-FIX-008 교정 AI 응답 파싱/스키마 견고성](COR-FIX-008_Correction_AI_Response_Parsing_Robustness.md) — 본 문서는 COR-FIX-008이 "유지한다"고 한 candidateId 계약 검증을 강화/진단하는 별도 트랙

## User Story

학습자가 일본어로 대화한 뒤 `교정` 화면에 진입했을 때, 교정 결과가 생성되어야 한다. 그런데 아래 에러로 실패한다.

```text
교정 결과 생성에 실패했어요
사유: unknown correction candidate id: ja-6-0-79967d5
```

이는 일본어 문장의 문법 문제가 아니라, **AI 응답의 `candidateId`가 앱이 실제로 넘긴 후보 목록에 없어** 매칭에 실패한 것이다.

## 왜 COR-FIX-008과 분리하는가

COR-FIX-008(A~D)은 "JSON 문법/타입/스키마가 어긋난" 응답을 mapper 파싱 경계에서 관대하게 받는 문제다. 반면 본 건은 **"JSON은 맞지만 계약(candidateId)이 틀린" 응답**이다. 성격이 다르다:

- COR-FIX-008-D는 `SerializationException`을 1회 재시도하지만, candidateId 불일치는 `IllegalArgumentException`이라 **재시도하지 않는다**(같은 hallucination 반복 위험).
- 원인 조사 대상이 mapper가 아니라 **후보 ID 생성 결정성(`ExtractCandidatesUseCase`)** 과 **상태 재사용 흐름**까지 걸친다.
- "unknown이면 무시"는 절대 안 됨 — candidateId 매칭은 `beforeText`/`sourceTurnIndex`/`sourceText`/`lang` 등을 안전히 보존하는 방어선이라, 잘못 매칭하면 원문과 교정문이 틀리게 붙어 Flashcard·LangState까지 오염된다.

## 직접 원인

`CorrectionAiResponseMapper.map()`:

```kotlin
val candidatesById = input.candidates.associateBy { it.id }
val candidate = requireNotNull(candidatesById[item.candidateId]) {
    "unknown correction candidate id: ${item.candidateId}"
}
```

`ja-6-0-79967d5`와 정확히 같은 ID가 `input.candidates`에 없었다는 뜻이다.

## 추정 원인

1. AI가 prompt의 candidateId를 복사하지 않고 새 ID를 생성(hallucinate).
2. 응답 전후 처리에서 ID 변형.
3. 후보 생성 시점과 응답 매핑 시점의 후보 목록 불일치.
4. 일본어 후보 ID 생성 규칙(`ja-6-0-...`)과 실제 input의 suffix/hash 불일치.
5. 이전 요청 candidateId 혼입.
6. 후보 일부가 필터링됐는데 응답에 필터 전 ID 잔존.

가장 먼저 확인: prompt의 `Candidates:` 블록에 실제로 `ja-6-0-79967d5`가 있었는가.

## 핵심 결정 (우선순위)

- **A. 프롬프트 강화(최우선·최소 변경).** "candidateId는 Candidates 섹션 값에서 정확히 복사, 생성/추론/축약/해시/번역/재포맷 금지, 정확한 ID를 못 쓰면 그 후보는 skip" 규칙 추가. mapper의 strict validation은 유지.
- **B. mapper 진단 로그.** unknown 예외에 known IDs 일부 + count를 포함해 Logcat에 남긴다(사용자 화면 비노출은 COR-UX-002).
- **C. safe fallback은 매우 제한적으로만.** suggestion 수==candidate 수 && 순서 명확 대응 && 텍스트 non-blank && fallback marker/metric 기록일 때만 검토. 원문-교정문 오결합 위험이 커서 **기본은 채택하지 않음.** 먼저 A/B로 원인부터 잡는다.
- **D. UI 에러 메시지 분리** → [COR-UX-002](COR-UX-002_Correction_Failure_Error_Message.md)로 위임.

## 완료 기준(AC)

- [ ] 프롬프트에 candidateId exact-copy 규칙(복사/생성금지/skip)이 명시된다.
- [ ] mapper의 unknown candidateId 검증은 그대로 유지(느슨해지지 않음)되며, 예외에 knownCandidateIds 일부+count 진단 정보가 포함되어 Logcat에 남는다.
- [ ] `ExtractCandidatesUseCase.buildCandidateId()`가 같은 입력에 대해 deterministic함을 테스트로 확인한다(일본어 포함).
- [ ] 잘못된 원문과 교정 결과가 임의로 연결되지 않는다(fallback 미채택 또는 제한 조건 충족 시에만).
- [ ] 사용자 노출 메시지는 [COR-UX-002](COR-UX-002_Correction_Failure_Error_Message.md) 정책을 따른다(내부 ID 비노출).

## 조사 단계

1. `CorrectionRepositoryImpl.generateSuggestions()` AI 호출 직전 로깅: selected language, `input.candidates.map { it.id }`, count, sourceTurnIndex/Id, sourceText 앞부분.
2. `CorrectionPromptBuilder.build()`의 `Candidates:` 블록에 실패 ID가 있었는지, 줄바꿈/공백/특수문자로 모델이 오독할 형태였는지.
3. `CorrectionAiClient.generateJson()` raw JSON: AI가 prompt에 없던 ID를 만들었는지, suggestion 수와 candidate 수가 맞는지, candidateId만 틀리고 문장은 특정 후보와 매칭 가능한지.
4. `ExtractCandidatesUseCase.buildCandidateId()`: deterministic 여부, 호출 시점마다 suffix/hash 변동 여부, 일본어 문장 분리/trim이 hash를 바꾸는지.
5. `CorrectionViewModel.triggerGeneration()`: 재시도 시 후보를 새로 추출하는지, 이전 상태/응답 혼입 지점, `generationLaunched`/terminal phase reset 흐름.

## 주요 작업

1. **프롬프트** — `CorrectionPromptBuilder` response rules:

```text
- candidateId must be copied exactly from one of the candidateId values in the Candidates section.
- Never create, infer, shorten, hash, translate, or reformat candidateId.
- If you cannot use an exact candidateId from the Candidates section, skip that candidate.
```

2. **mapper 진단** — unknown 예외 메시지에 `knownCandidateIds=[...], count=N` 포함(Logcat용).
3. (필요 시) `ExtractCandidatesUseCase` 후보 ID 결정성 보강.

## 테스트

- `CorrectionAiResponseMapperTest`: unknown 일본어 candidateId는 계속 `IllegalArgumentException`으로 실패(엄격 검증 회귀 방지).
- `CorrectionPromptBuilderTest`: prompt에 `COPY EXACTLY`/`Never create`/`candidateId` 규칙 문구 포함.
- `ExtractCandidatesUseCaseTest`: 같은 입력 2회 → 같은 candidateId, 후보 순서·sourceTurnIndex 안정, trim 외 변형으로 hash 불변(일본어 포함).

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionAiResponseMapperTest"
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionPromptBuilderTest"
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.domain.usecase.correction.ExtractCandidatesUseCaseTest"
.\gradlew.bat compileDevDebugKotlin
```

## Out of Scope

- Flashcard 저장 로직 변경 / LangState 업데이트 정책 변경
- learningSignal enum allowlist 변경
- Gemini 모델 교체 / 교정 화면 전체 UX 개편 / 일본어 교정 품질 튜닝

## Handoff Note

본질은 `ja-6-0-79967d5` 한 ID가 이상한 게 아니라 "AI 응답 candidateId ↔ 앱 내부 후보 ID 계약이 깨진 것"이다. 먼저 실제 prompt와 raw response를 확보한다. prompt에 ID가 있었다면 후보 생성/상태 재사용을, 없었다면 AI hallucination이므로 프롬프트 강화 + mapper 진단 로그를 우선한다.
