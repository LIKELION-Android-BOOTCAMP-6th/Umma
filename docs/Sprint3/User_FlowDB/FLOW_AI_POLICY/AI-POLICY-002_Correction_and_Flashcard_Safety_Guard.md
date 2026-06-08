# [Improvement] AI-POLICY-002 Correction 후보와 Flashcard 저장 안전 방어

## 목적

AI Chat에서 나온 대화 내용은 Correction 후보 추출, 교정 결과 생성, Flashcard 저장으로 이어질 수 있다.
따라서 Chat에서 AI가 직접 유해 응답을 하지 않도록 막는 것만으로는 충분하지 않다.

이 작업은 유해한 사용자 발화가 “언어 교정”이라는 명목으로 더 자연스럽고 실행 가능한 문장으로 다듬어지거나, Flashcard로 저장되어 반복 학습 콘텐츠가 되는 것을 막는다.

---

# User Story

사용자는 교정 결과와 Flashcard가 안전한 언어 학습 콘텐츠로 유지되기를 기대한다.
Umma는 자해, 범죄, 아동 성착취, 혐오, 괴롭힘, 사기, 성적 콘텐츠, 위험한 전문 조언 같은 문장을 교정하거나 저장 대상으로 만들지 않는다.
정상적인 언어 학습 문장은 과도하게 차단하지 않고 기존 저장 흐름을 유지한다.

---

# 완료 기준(AC)

- [ ] Correction 후보 추출 이후 명백히 유해한 후보는 교정 AI 요청에서 제외된다.
- [ ] Correction prompt는 유해한 문장을 더 자연스럽게 교정하거나 실행 가능하게 만들지 않도록 지시한다.
- [ ] 안전 차단된 후보는 교정 카드로 표시되지 않는다.
- [ ] 안전 차단된 후보는 Flashcard 저장 요청에 포함되지 않는다.
- [ ] 안전 차단된 후보는 LangState learningSignal에 반영되지 않는다.
- [ ] 일부 후보만 차단된 경우, 안전한 후보는 정상적으로 교정/저장될 수 있다.
- [ ] 모든 후보가 차단된 경우, 사용자에게 저장 가능한 학습 문장이 없다는 별도 안내를 제공한다.
- [ ] 기존 과확장 가드, meaningPreserved 가드, learningSignal 정규화 정책은 유지된다.

---

# 기준 문서

- [AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능](./AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)
- [COR-TUNE-004 Correction Candidate Selection Refinement](../FLOW_COR_IMPROVEMENTS/COR-TUNE-004_Correction_Candidate_Selection_Refinement.md)
- [COR-TUNE-006 Correction Overexpansion Runtime Guard](../FLOW_COR_IMPROVEMENTS/COR-TUNE-006_Correction_Overexpansion_Runtime_Guard.md)
- [COR-TUNE-007 Correction Flashcard Dedup Quality](../FLOW_COR_IMPROVEMENTS/COR-TUNE-007_Correction_Flashcard_Dedup_Quality.md)
- [COR-TUNE-009 Empty Save Request Noop Completion](../FLOW_COR_IMPROVEMENTS/COR-TUNE-009_Empty_Save_Request_Noop_Completion.md)
- [CHAT-TUNE-003 Correction Signal 기반 LangState 측정 고도화](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-003_Correction_Signal_LangState_Integration.md)

---

# 핵심 결정

- **교정과 평가는 분리한다.** 유해 후보는 교정 결과를 만들지 않으므로 Flashcard와 LangState 평가에도 들어가지 않는다.
- **안전 필터는 과도하게 넓히지 않는다.** 명백한 유해 후보만 차단하고, 일반적인 언어 학습 문장은 유지한다.
- **저장 전 최종 방어는 domain에서 한다.** Flashcard repository/data source가 안전 판단을 직접 하지 않는다.
- **기존 품질 필터와 안전 차단은 구분한다.** 저장 가능한 카드가 0개가 된 이유가 품질 필터인지 안전 차단인지 사용자 안내와 테스트에서 구분할 수 있어야 한다.
- **Correction 담당 영역과 Flashcard 저장 방어 영역을 분리한다.** 후보 차단과 교정 prompt 안전 지시는 Correction 담당자가 참고하고, 저장 직전 방어는 저장 요청 조립 경계에서 처리한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| `ExtractCandidatesUseCase` | 교정 후보 생성. 안전 판단은 직접 하지 않는다. |
| `FilterCorrectionCandidatesForSafetyUseCase` | 후보 목록에 안전 필터를 적용해 교정 AI 요청 전 차단한다. |
| `CorrectionSafetyPolicy` | 명백히 유해한 후보인지 판단 |
| `CorrectionPromptBuilder` | AI가 유해 후보를 교정/자연화하지 않도록 지시 |
| `CorrectionAiResponseMapper` | AI 응답 schema 검증과 보조 safety flag가 있을 경우 보수 처리 |
| `PrepareSaveRequestUseCase` | Flashcard 저장 요청 직전 최종 안전 방어 |
| `CompleteCorrectionUseCase` | 저장/rollback/LangState/summary 후속 흐름 유지 |
| `FlashcardRepository` / DataSource | 저장만 담당. 안전 판단 직접 수행 금지 |

---

# 주요 작업

## 1. Correction 후보 안전 필터

후보 추출 후 `FilterCorrectionCandidatesForSafetyUseCase`에서 명백한 유해 후보를 제외한다.
이 단계의 목적은 AI 비용 절감이 아니라, 유해 문장을 교정 대상으로 만들지 않는 것이다.

차단 예시:

- 자해 방법 또는 위험 행동을 조장하는 문장
- 범죄 실행 방법 또는 사기/기만 행위를 돕는 문장
- 아동 성착취 또는 아동 성적 콘텐츠
- 특정 집단에 대한 혐오, 괴롭힘, 폭력 선동
- 성적으로 노골적인 문장
- 위험한 의료/법률/금융 조언을 단정적으로 요구하는 문장

주의:

- 욕설이나 부정적 감정이 있다는 이유만으로 무조건 차단하지 않는다.
- 언어 학습 예시로 안전하게 다룰 수 있는 일반 문장까지 막지 않는다.
- 판단이 애매한 경우에는 교정 prompt 안전 지시와 후처리 가드에 맡긴다.
- `ExtractCandidatesUseCase`는 기존처럼 후보 추출 책임만 유지하고, safety 판단은 별도 UseCase/Policy로 분리한다.

## 2. Correction prompt 안전 지시

후보 필터가 놓친 위험 후보를 AI가 더 자연스럽게 만들지 않도록 짧은 안전 지시를 추가한다.

예시:

```text
safety:
- Do not correct, naturalize, translate, or make harmful content more actionable.
- Skip candidates involving self-harm instructions, child sexual content, hate or harassment, crime, fraud, explicit sexual content, or dangerous professional advice.
- Safe language-learning help is allowed only when it does not preserve or strengthen harmful intent.
```

주의:

- 기존 교정 성장 정책과 schema 지시를 반복하지 않는다.
- 유해 후보를 skip할 수 있다는 지시가 기존 “candidate마다 suggestion 생성” 지시와 충돌하지 않게 정리한다.

## 3. Flashcard 저장 전 최종 방어

`PrepareSaveRequestUseCase`에서 저장 요청을 만들기 직전에 안전 차단을 한 번 더 적용한다.
Correction 후보 단계에서 이미 차단됐더라도, 저장 직전 방어는 최종 안전망으로 유지한다.

권장 흐름:

```text
selectedSuggestions
→ 빈 값/중복/품질 필터
→ safety blocked suggestion 제외
→ CorrectionFlashcardSaveItem 생성
```

정책:

- 일부 suggestion만 차단되면 나머지는 저장한다.
- 전부 차단되면 저장 요청은 비워지되, “품질 필터로 0개”와 “안전 차단으로 0개”를 구분할 수 있어야 한다.
- 안전 차단된 suggestion의 learningSignal은 LangState에 들어가지 않아야 한다.
- `PrepareSaveRequestUseCase`는 safety 차단 결과를 outcome에 남겨 UI가 품질 필터와 안전 차단을 다르게 안내할 수 있게 한다.

## 4. UI 안내

저장 가능한 카드가 안전상 제외되면 사용자에게 간단히 안내한다.
문구는 위협적이거나 과하게 상세하지 않게 한다.

예시:

```text
일부 문장은 학습 카드로 저장할 수 없어 제외했어요.
```

또는 전부 차단된 경우:

```text
이번 교정 결과에는 저장 가능한 학습 문장이 없어요.
```

---

# 예외 처리

- 모든 후보가 차단되어도 Correction 화면이 크래시나 무한 로딩에 빠지지 않는다.
- 저장 가능한 카드가 0개인 경우에도 `correctionAvailable=false` 처리와 세션 완료 흐름은 기존 정책과 맞춘다.
- safety filter 실패는 유해 콘텐츠를 통과시키는 방향보다 보수적으로 처리하되, 정상 후보 전체를 불필요하게 죽이지 않는다.
- AI가 schema를 어겨 safety flag를 누락해도 앱 정책 필터가 최소 방어를 수행한다.

---

# 검증 기준

- 명백한 유해 후보가 교정 AI 요청 후보에서 제외되는지 확인한다.
- 안전한 일반 학습 문장이 과도하게 제외되지 않는지 확인한다.
- Correction prompt에 safety 지시가 한 번만 들어가고 기존 schema 지시와 충돌하지 않는지 확인한다.
- 안전 차단된 suggestion이 Flashcard 저장 요청에 포함되지 않는지 확인한다.
- 일부 차단/전체 차단/차단 없음 케이스를 테스트한다.
- 안전 차단된 suggestion의 learningSignal이 LangState에 반영되지 않는지 확인한다.
- 기존 `CorrectionOverexpansionGuard`, `meaningPreserved`, `PrepareSaveRequestUseCase` 품질 필터가 회귀하지 않는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드가 통과한다.
