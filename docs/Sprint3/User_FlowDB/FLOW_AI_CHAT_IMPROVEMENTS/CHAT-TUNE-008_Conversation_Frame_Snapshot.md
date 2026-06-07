# [CHAT-TUNE-008] 대화 프레임과 스냅샷 기반 흐름 보조

## 목적

`CHAT-TUNE-008`은 AI Chat 세션 안에서 대화의 관계와 현재 흐름을 더 안정적으로 전달하기 위한 구조 작업이다.

이 문서는 프롬프트 미세 튜닝 기록을 남기는 문서가 아니다.
특정 신고 세션의 실패 원인, revision별 개선 결과, band별 문장 조정 이력은 리뷰 노트와 반복 테스트 문서에서 관리한다.

이번 작업의 핵심은 아래 두 가지다.

- 세션 시작 시 AI가 어떤 관계와 태도로 대화해야 하는지 짧은 `Conversation Frame`으로 전달한다.
- 매 turn 응답 전에는 장기 정책이 아니라 현재 대화 위치만 짧게 요약한 `Conversation Snapshot / Turn Hint`를 전달한다.

---

# User Story

사용자는 학습언어가 부족하거나 기준언어를 섞어 말해도 대화가 끊기지 않기를 기대한다.

Umma는 사용자의 조각난 말, 멈춤, 기준언어 혼합을 평가나 교정 대상이 아니라 자연스러운 소통 과정으로 다룬다.
AI는 사용자의 현재 말과 직전 대화 흐름을 참고해, 사용자가 할 수 있는 만큼만 반응해도 대화를 이어갈 수 있게 한다.

---

# 완료 기준(AC)

- [ ] 세션 시작 prompt에는 roleplay가 아닌 짧은 `Conversation Frame`이 포함된다.
- [ ] `Conversation Frame`은 AI를 교사나 평가자가 아니라 따뜻한 일상 대화 상대로 정의한다.
- [ ] `Conversation Frame`은 조각난 말, 멈춤, 기준언어 혼합을 자연스러운 소통 과정으로 보게 한다.
- [ ] `Conversation Frame`은 특정 장소, 상황극, 오프라인 만남, 바디랭귀지 묘사를 기본값으로 만들지 않는다.
- [ ] Chat 세션 안에서만 쓰는 `Conversation Snapshot` 모델이 정의된다.
- [ ] Snapshot은 현재 주제, 사용자의 최근 의도, 직전 AI 행동, 반복 주의 정보를 짧게 담는다.
- [ ] Snapshot은 `LangState`의 장기 언어능력 source로 저장되지 않는다.
- [ ] Snapshot 갱신은 final transcript 기준으로만 수행된다.
- [ ] Snapshot 갱신에는 추가 AI 요약 호출을 사용하지 않는다.
- [ ] `Turn Hint`는 현재 대화 위치 요약만 전달한다.
- [ ] `Turn Hint`는 세션 prompt의 persona, band 철학, 속도, 언어 비율 같은 장기 정책을 반복하지 않는다.
- [ ] `Turn Hint`에는 raw metric, 내부 enum, 나이 비유, 실패 문구 예시가 노출되지 않는다.
- [ ] `Turn Hint`는 금지어 목록, 단어 감지 조건, 표현별 분기 규칙으로 확장되지 않는다.
- [ ] Realtime transport는 생성된 instruction을 전달만 하고 정책을 해석하지 않는다.
- [ ] 기존 Correction, Flashcard, LangState 저장 구조, usage tracking, mic/transport 흐름은 변경하지 않는다.
- [ ] prompt review 로그와 신고 문서에는 turn hint 적용 여부를 확인할 수 있는 최소 추적 정보가 남는다.

---

# 포함 범위

- `Conversation Frame` 구조 정의
- `Conversation Snapshot` domain model 정의
- Snapshot 갱신 정책 정의
- `Turn Hint` 생성 정책 정의
- Chat 응답 요청 전 hint 전달 경로 연결
- prompt review 도구와 `AiChatPromptTrace`에서 추적할 최소 로그 정의

---

# 제외 범위

- 프롬프트 미세 튜닝 이력 정리
- prompt revision별 결과 기록
- band별 문장 튜닝 전략
- 특정 사례별 응답 규칙
- 상황극 Roleplay 기능
- 특정 장소, 역할, 시나리오 설정
- AI 추가 호출 기반 대화 요약
- LangState schema에 세션 topic/scene 상태 저장
- Correction / Flashcard / Statistics 구조 변경
- Realtime transport / WebSocket / mic / usage tracking 변경
- turn마다 band 정책을 반복 주입하는 구조

---

# 기준 문서

- GitHub Issue: [#303](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/issues/303)
- [CHAT-TUNE-005 대화 능력 단계 정의 재조정](./CHAT-TUNE-005_Conversation_Band_Definition_Recalibration.md)
- [CHAT-TUNE-006 Chat 대화 근거 LangState 연동](./CHAT-TUNE-006_Chat_Evidence_LangState_Integration.md)
- [CHAT-TUNE-007 Chat band source를 LangState summary로 일원화](./CHAT-TUNE-007_Chat_Evidence_Summary_Band_Source.md)
- [CHAT-FIX-002 AI Chat 프롬프트 반복 개선 운영 계획](./CHAT-FIX-002_AI_Chat_Prompt_Iteration_Workflow.md)
- [CHAT_PROMPT_TUNING_GUIDE](./CHAT_PROMPT_TUNING_GUIDE.md)

---

# 설계 원칙

## Conversation Frame은 관계와 태도만 다룬다

`Conversation Frame`은 구체적인 상황극 설정이 아니다.
AI가 사용자를 어떤 관계와 태도로 대해야 하는지 알려주는 짧은 기준이다.

권장 방향:

```text
conversation_frame:
role: 교사나 평가자가 아니라 따뜻한 일상 대화 상대다.
situation: 사용자의 조각난 말, 멈춤, 기준언어 혼합을 자연스러운 소통 과정으로 보고 의도 이해를 우선한다.
goal: 사용자가 할 수 있는 만큼만 반응해도 대화가 이어지게 한다.
```

제외 방향:

```text
You are in a cafe with the user. You use gestures and talk like close friends.
```

이유:

- 특정 roleplay로 흐를 수 있다.
- 자막/음성에 바디랭귀지 지문이 섞일 수 있다.
- 자유로운 일상 대화보다 특정 장면에 갇힐 수 있다.

## Conversation Snapshot은 현재 대화 위치만 다룬다

Snapshot은 장기 능력이나 band 정책이 아니다.
세션 안에서 AI가 현재 대화 위치를 참고할 수 있게 하는 임시 상태다.

Snapshot이 다루는 정보:

- 현재 주제
- 사용자의 최근 의도
- 직전 AI 행동
- 반복 주의가 필요한 질문 또는 표현
- 현재 사용자가 대화를 따라오고 있는지에 대한 짧은 신호

Snapshot이 다루지 않는 정보:

- 장기 언어능력
- LangState metric
- 교정 정책
- prompt revision별 튜닝 결과
- 특정 실패 문구별 응답 규칙

## Turn Hint는 현재 위치 요약이다

`Turn Hint`는 이번 응답 직전에 필요한 현재 대화 위치만 전달한다.
세션 prompt의 장기 정책을 반복하지 않고, 모델에게 특정 문장 출력을 강제하지 않는다.

좋은 방향:

```text
현재 대화:
사용자는 방금 말한 주제에 짧게 반응한 것으로 보인다.
직전 흐름을 유지하고, 이미 물어본 질문은 반복하지 않는다.
```

나쁜 방향:

```text
사용자는 초급자다. 천천히 말하고, 짧게 말하고, 한국어를 섞고, 질문은 하나만 한다.
```

이유:

- 세션 prompt의 band 정책을 반복한다.
- Turn Hint가 대화 위치가 아니라 행동 제한 목록이 된다.
- 사례별 응답 규칙이 누적되기 쉽다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Domain | `ConversationSnapshot` 모델, snapshot 갱신 정책, turn hint 생성 정책 |
| ViewModel | final transcript와 현재 snapshot 전달만 담당 |
| Repository/Transport | 생성된 instruction 전달만 담당. 정책 해석 금지 |
| SessionMemory | 기존 final transcript 저장 흐름 유지 |
| LangState | 장기 언어능력 source. 세션 topic/scene/repetition 상태 저장 금지 |
| BuildPromptUseCase | 세션 시작 prompt의 frame, 언어 관계, band style을 짧게 압축 |
| Prompt Review Tool | 신고 세션과 로그로 snapshot/hint 적용 여부를 수동 검증 |

---

# 모델 초안

MVP에서는 필드를 과하게 늘리지 않는다.
초기 목적은 현재 대화 위치를 전달하는 최소 구조를 만드는 것이다.

```kotlin
data class ChatConversationSnapshot(
    val currentTopic: String?,
    val inferredUserIntent: String?,
    val lastAiMoveSummary: String?,
    val avoidRepeating: List<String>,
    val userSignal: ChatComprehensionSignal
)
```

```kotlin
enum class ChatComprehensionSignal {
    Unknown,
    Following,
    Struggling,
    MixedPrimaryLang,
    FragmentOnly
}
```

주의:

- enum 이름은 prompt에 노출하지 않는다.
- `avoidRepeating`은 실패 문구 금지 목록이 아니라, 직전 대화에서 이미 반복된 질문/표현을 피하기 위한 세션 상태다.
- 필요성이 확인되기 전까지 snapshot 필드를 추가하지 않는다.

---

# UseCase 계획

```kotlin
class BuildChatConversationSnapshotUseCase
class BuildChatTurnHintUseCase
```

## BuildChatConversationSnapshotUseCase

책임:

- 기존 snapshot과 최근 USER/AI final transcript를 보고 다음 snapshot을 만든다.
- partial transcript는 사용하지 않는다.
- AI 호출 없이 로컬 계산만 사용한다.
- 단어별 조건 목록을 과하게 늘리지 않는다.
- 저장 모델이 아니라 현재 세션용 read model로만 다룬다.

## BuildChatTurnHintUseCase

책임:

- snapshot을 conversation `system` item으로 전달할 수 있는 짧은 hint로 압축한다.
- 세션 prompt의 persona, band별 철학, speed, 기준언어 정책을 반복하지 않는다.
- 2~4줄 이내로 유지한다.
- 내부 enum, raw metric, 나이 비유, 실패 사례 문구를 노출하지 않는다.

---

# Conversation Frame과 Band Style 경계

공통 frame은 짧게 유지한다.
band별 차이는 긴 상황 설명이 아니라 `persona`, `language_use`, `conversation_principles`, `current_style`, `style_reference`의 책임 안에서 짧게 둔다.

이 문서에서는 band별 프롬프트 문장 튜닝을 다루지 않는다.
band별 세부 문장 조정과 반복 테스트 결과는 별도의 prompt review 기록에서 관리한다.

---

# 구현 계획

1. `BuildPromptUseCase`의 세션 시작 prompt에 `Conversation Frame`을 반영한다.
   - roleplay, 장소 설정, 바디랭귀지 묘사는 넣지 않는다.
   - teacher/examiner/lesson/drill/repeat-after-me 뉘앙스가 frame에 들어가지 않게 한다.
2. domain layer에 `ChatConversationSnapshot`과 `ChatComprehensionSignal`을 추가한다.
   - model은 세션 내 흐름 보조용이며 Firestore/LangState 저장 모델로 확장하지 않는다.
3. `BuildChatConversationSnapshotUseCase`를 추가한다.
   - 최근 final turn과 기존 snapshot을 입력으로 받는다.
   - 현재 topic, inferred intent, last AI move, avoidRepeating, userSignal을 갱신한다.
   - 표현별 금지 목록이 아니라 직전 대화에서 이미 반복된 질문/표현만 좁게 추적한다.
4. `BuildChatTurnHintUseCase`를 추가한다.
   - snapshot을 짧은 hint로 만든다.
   - band 정책 반복을 방지하는 테스트를 추가한다.
   - hint 원문에는 내부 enum, raw metric, band명, age analogy를 넣지 않는다.
5. Chat 흐름에 연결한다.
   - USER final transcript 확정 후 AI 응답 요청 전에 snapshot을 갱신한다.
   - turn hint는 conversation `system` item으로 전달한다.
   - transport는 instruction 전달만 담당한다.
6. prompt review 로그에서 turn hint 적용 여부를 확인할 수 있게 한다.
   - 원문 turn hint 전문을 과도하게 로그에 남기지 않는다.
   - 적용 여부와 세션 식별에 필요한 최소 정보만 남긴다.

---

# 테스트 방법

- `git diff --check`
- `BuildPromptUseCaseTest`
- `ChatPromptIntegrationUseCaseTest`
- Snapshot 갱신 UseCase test
- Turn Hint 생성 UseCase test
- `:app:compileDevDebugKotlin`
- `:app:compileMockDebugKotlin`
- `AiChatPromptTrace`에서 session prompt, turn hint 적용 여부, 신고 세션 저장 로그 확인

검증할 것:

- Frame이 roleplay나 바디랭귀지 묘사를 만들지 않는다.
- Snapshot이 LangState나 장기 능력 source로 저장되지 않는다.
- Turn Hint가 세션 prompt의 장기 정책을 반복하지 않는다.
- Turn Hint가 내부 enum이나 raw metric을 노출하지 않는다.
- Transport가 instruction을 전달만 하고 정책을 해석하지 않는다.

---

# 후속 판단

`CHAT-TUNE-008`은 대규모 dialogue manager를 만드는 작업이 아니다.
세션 prompt와 turn hint 사이의 책임 경계를 작게 정의하는 작업이다.

추가 확장이 필요하면 아래 순서로 판단한다.

1. Frame이 관계/태도 기준을 넘어 상황극으로 커졌는지 확인한다.
2. Turn Hint가 현재 위치 요약을 넘어 행동 제한 목록으로 커졌는지 확인한다.
3. Snapshot이 장기 상태나 평가 지표를 담기 시작했는지 확인한다.
4. 그래도 구조 확장이 필요할 때만 별도 이슈로 분리한다.
