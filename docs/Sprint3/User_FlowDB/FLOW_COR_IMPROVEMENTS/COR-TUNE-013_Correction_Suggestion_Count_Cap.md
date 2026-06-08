# [TUNE] COR-TUNE-013 교정 결과 개수 상한(최대 10개)

## User Story

사용자는 한 번의 교정에서 카드가 끝없이 쏟아지지 않고, **가장 중요한 교정 최대 10개**만 본다. 카드가 너무 많으면 무엇부터 봐야 할지 막막하고, 저장 선택도 부담스럽다. 학습자는 한 세션에서 다룰 만한 양으로 정돈된 교정 목록을 받는다.

---

# 배경

현재 교정 결과 개수에는 **상한이 없다**. 확인된 현황:

- `CorrectionPromptBuilder`가 `Task: For each candidate sentence ... Emit one suggestion per candidate.`로 지시하므로([CorrectionPromptBuilder.kt:81](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt), [:125](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt)), 결과 개수 = (교정이 필요한) candidate 수다.
- candidate 수의 간접 상한은 후보 추출 단계의 `MAX_SOURCE_TURNS=100`뿐이다. 대화가 길면 카드가 수십 개까지 나올 수 있다.
- `CorrectionAiResponseMapper`/`GenerateSuggestionsUseCase`/`CorrectionResultList` 어디에도 `take(N)` 같은 개수 컷이 없다 — AI가 돌려준 만큼 전부 표시된다.

방향은 **"표시·저장 대상 교정을 최대 10개로 고정"** 이다. 개수 제한은 표시 정책이 아니라 도메인 정책이므로 **UseCase**에서 보증하고(Repository/Mapper에 계산·정책을 넣지 않는다 — Clean Architecture 원칙), 프롬프트에는 토큰 절약·중요도 우선 선택을 위한 best-effort 안내만 둔다.

---

# 완료 기준(AC)

- [ ] 교정 결과는 어떤 입력에서도 **최대 10개**를 넘지 않는다.
- [ ] 개수 컷의 단일 보증 지점은 **`GenerateSuggestionsUseCase`**(domain)다. AI가 10개를 초과해 돌려줘도 UseCase가 잘라 10개만 흘려보낸다.
- [ ] 상한 값은 매직넘버가 아니라 명명 상수(`MAX_SUGGESTIONS = 10`)로 두고 KDoc에 정책 근거를 남긴다.
- [ ] `CorrectionPromptBuilder`에 "최대 10개, 초과 시 가장 영향이 큰 10개를 우선" 취지의 안내 1줄을 추가한다(best-effort — 최종 보증은 UseCase).
- [ ] Repository(`CorrectionRepositoryImpl`)·Mapper(`CorrectionAiResponseMapper`)에는 개수 컷을 두지 않는다(저장/통신·파싱 책임만 유지).
- [ ] 10개 이하 입력에서는 기존 동작과 동일(회귀 없음). 0건은 기존 `EmptyResult` 흐름 그대로.

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md` (교정 생성 흐름)
- [COR-TUNE-004 Correction Candidate Selection Refinement](COR-TUNE-004_Correction_Candidate_Selection_Refinement.md) (후보 선별 정책 — 본 컷은 그 다음 단계의 출력 상한)
- `C:/Users/pc/.claude/projects/C--Kotlin-Umma/memory/project_architecture_rule.md` (정책은 domain UseCase, Repository는 저장/통신만)

---

# 핵심 결정

- **개수 상한은 domain 정책.** `GenerateSuggestionsUseCase`의 성공 결과에 `.take(MAX_SUGGESTIONS)`를 적용한다. 표시 계층(`CorrectionResultList`)에서 자르면 저장 대상 집합과 화면이 어긋날 수 있으므로, 도메인에서 한 번 잘라 상태 전체가 10개로 수렴하게 한다.
- **프롬프트 안내는 보조 수단.** 모델이 중요도 순으로 10개를 고르면 토큰·품질에 이득이지만, 모델 준수를 신뢰하지 않고 UseCase 컷으로 못 박는다.
- **"가장 중요한 10개" 기준은 AI 우선순위에 위임.** 별도 도메인 정렬/스코어링을 새로 만들지 않는다(범위 최소화). AI가 돌려준 순서를 신뢰해 앞에서 10개를 취한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| GenerateSuggestionsUseCase (domain) | `MAX_SUGGESTIONS` 상수 보유, 성공 결과를 `take(10)`으로 컷 — **개수 보증의 단일 지점** |
| CorrectionPromptBuilder (data) | "최대 10개·중요도 우선" best-effort 안내 1줄 추가. 개수 보증 책임은 없음 |
| CorrectionRepositoryImpl / CorrectionAiResponseMapper (data, 범위 밖) | 저장/통신·파싱만. 개수 컷을 넣지 않음 |
| CorrectionResultList / CorrectionUiState (presentation, 범위 밖) | 받은 목록을 그대로 렌더링·선택. 자체 컷 없음 |

---

# 주요 작업

1. **UseCase 컷** (`GenerateSuggestionsUseCase`): `private const val MAX_SUGGESTIONS = 10` 추가, `repository.generateSuggestions(input).map { it.take(MAX_SUGGESTIONS) }`로 변경 + 정책 KDoc.
2. **프롬프트 안내** (`CorrectionPromptBuilder.build`): `Task:` 줄 인근에 `Return at most 10 suggestions; if more candidates need correcting, keep the 10 most impactful.` 추가.
3. **테스트 보강**: 11개 이상 입력 → 정확히 10개로 잘리는지, 10개 이하/0건은 그대로인지 단위 테스트(`GenerateSuggestionsUseCase` 또는 동등 위치).

---

# 예외 처리

- AI가 10개 미만 반환 → 그대로 통과(컷 무영향).
- AI가 0건 반환 → 기존 `EmptyResult` 분기 유지(컷과 무관).
- 호출/파싱 실패 → 기존 `Error` 분기 유지.

---

# 검증 기준

- `:app:compileDevDebugKotlin` 빌드 통과.
- 후보가 11개 이상 생성되는 대화로 교정 진입 → 카드가 정확히 10개인지(수동).
- 단위 테스트: 11개 입력 → 10개, 10개/5개/0개 입력은 동일 개수 통과.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.domain.usecase.correction.*"
```

---

# Out of Scope

- "가장 중요한 10개"를 위한 도메인 스코어링/재정렬 도입(AI 순서 신뢰).
- 후보 추출 단계(`ExtractSessionCandidates`)의 상한 변경.
- 10개 초과분의 "더 보기" 페이징 UI.
