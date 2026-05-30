# [Improvement] CHAT-TUNE-001 LangState 기반 프롬프트 정책

## User Story

사용자는 자신의 현재 언어능력 상태에 맞춰 너무 쉽거나 어렵지 않은 AI 응답을 받을 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] LangState가 없거나 초기값이면 beginner-safe prompt가 생성된다.
- [ ] Beginner 수준에서는 짧은 응답, 쉬운 어휘, 단순 문장 중심의 지시가 포함된다.
- [ ] Intermediate 이상에서는 follow-up 질문, 맥락 확장, 자연스러운 표현 사용 지시가 포함된다.
- [ ] 최근 대화 context가 있으면 대화 연속성을 유지하는 지시가 포함된다.
- [ ] `StartSessionUseCase`와 `RetryConnectionUseCase`는 같은 prompt policy를 사용한다.
- [ ] Prompt 생성 정책은 UI나 Repository가 아니라 domain usecase에서 관리된다.

---

# Flow (링크)

- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-001_Entry_State.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-005_AI_Response.md`
- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- LearningState 기준: `docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md`

---

# 구현 범위

## 포함 범위

- LangState 기반 prompt policy 정의
- vocabularyLevel, fluencyScore, accuracyScore, expressionRange 등 주요 지표 해석
- beginner-safe fallback
- 최근 대화 context 반영 기준 정리
- StartSession / RetryConnection prompt 생성 일관화

## 제외 범위 (Out of Scope)

- LangState 지표 계산 정책 변경
- AI 응답 품질의 정량 평가 자동화
- Correction prompt 정책 변경
- Statistics 지표 표시 정책 변경

---

# Details

## Prompt 정책

- AI Chat은 LangState를 직접 계산하지 않는다.
- AI Chat은 현재 저장된 LangState snapshot을 읽고, 이를 대화 난이도 지시문으로 해석한다.
- 값이 없거나 초기값이면 쉬운 난이도의 안전한 대화부터 시작한다.
- 난이도는 사용자의 부담을 줄이는 방향으로 적용한다.

## 지표 해석 후보

| 입력 | 사용 목적 |
| --- | --- |
| `external.vocabularyLevel` | 기본 어휘 난이도 |
| `external.expressionRange` | 표현 다양성에 따른 주제 확장 정도 |
| `external.fluencyScore` | 응답 길이와 발화 유도 강도 |
| `external.accuracyScore` | 문법 교정 개입 정도 |
| `internal.sentenceComplexity` | AI 문장 복잡도 |
| `internal.avgUtteranceLength` | 질문 길이와 turn pacing |

## 난이도 방향

```text
Beginner
→ 짧은 문장
→ 쉬운 어휘
→ 한 번에 하나의 질문
→ 필요하면 한국어 보조 설명 최소 사용

Intermediate+
→ 자연스러운 follow-up
→ 짧은 이유 설명
→ 대화 주제 확장
→ 학습 언어 사용 비중 확대
```

---

# 기술 설계 가이드

## 권장 구조

```text
domain/usecase/chat/
→ BuildPromptUseCase
→ ChatPromptPolicy 또는 내부 helper

domain/model/learningstate/
→ LangState
→ ExternalMetrics
→ InternalMetrics
```

- prompt policy는 domain layer에 둔다.
- repository는 완성된 system instruction을 Live API에 전달만 한다.
- UI는 LangState 세부 지표를 직접 해석하지 않는다.

## 현재 코드 확인 지점

- `StartSessionUseCase`는 selected language, LangState, recentFullContext를 `BuildPromptUseCase`에 전달한다.
- `RetryConnectionUseCase`도 재연결 시 `BuildPromptUseCase`를 사용한다.
- 현재 `BuildPromptUseCase`는 `external.vocabularyLevel.name` 중심으로만 난이도를 반영한다.
- `recentTopicSummaries` 파라미터는 있지만 호출처 wiring은 아직 제한적이다.

---

# 검증 기준

- LangState별 prompt 생성 결과가 단위 테스트로 확인된다.
- 초기값/누락값에서도 prompt 생성이 실패하지 않는다.
- StartSession과 RetryConnection에서 prompt 정책이 달라지지 않는다.
- prompt 정책이 data repository나 UI에 흩어지지 않는다.

---

# Edge Cases

- LangState 없음
- ExternalMetrics 초기값
- 일부 지표만 비정상적으로 높거나 낮음
- recentFullContext 없음
- recentFullContext가 너무 김
- selected language 변경 후 재진입
