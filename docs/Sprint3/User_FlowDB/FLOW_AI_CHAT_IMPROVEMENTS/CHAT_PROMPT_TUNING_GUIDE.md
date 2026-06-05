# AI Chat Prompt Tuning Guide

AI Chat 프롬프트는 실제 대화 세션을 보고 작게 반복 개선한다. 이 문서는 프롬프트를 수정할 때 따라야 하는 공통 지침이다.

## 목표

- AI가 단순 답변기가 아니라 회화 연습 파트너처럼 대화를 이어가게 한다.
- 사용자를 저장된 profile만으로 고정 판단하지 않고, 현재 발화와 최근 맥락을 함께 본다.
- prompt를 비대하게 만들지 않고 기존 구조의 책임을 조정해 문제를 해결한다.
- 실제 대화 export와 prompt trace로 원인을 확인한 뒤, 가장 작은 단위로 수정한다.

## 작업 원칙

- 새 구조를 만들기보다 기존 `persona`, `languages`, `response_flow`, `branches`, `learner_policy`, `learner_profile`, `context`, `current_turn_override` 안에서 튜닝한다.
- 실패 문구를 금지 목록처럼 계속 추가하지 않는다. 먼저 기존 지시 중 어떤 책임이 실패를 유도했는지 확인한다.
- prompt 문장을 추가하기 전에 같은 의미가 `support_rule`, `fragment`, `primaryBridge`, `priority_rule`, `turnPrimaryBridgeLine`에 이미 있는지 확인한다.
- 같은 의미의 요구사항은 여러 블록에 반복하지 않고 하나의 짧은 행동 원칙으로 압축한다.
- 실패 사례는 리뷰 노트나 테스트에 남기고, 실제 prompt에는 일반화된 행동 원칙만 반영한다.

## 책임 경계

- 세션 prompt는 모든 turn에 공통인 persona, 언어 설정, 자연 대화 원칙, context 사용 규칙을 담당한다.
- `learner_policy`와 `branches`는 profile별 말투, 길이, 보조 강도, recast 방식을 담당한다.
- `current_turn_override`는 이번 USER final transcript 때문에 세션 기본값과 달라지는 예외만 짧게 전달한다.
- turn override에 세션 prompt, persona, recent context 원문을 반복 주입하지 않는다.
- `recentFullContext` 전체를 매 turn prompt에 다시 넣지 않는다. 최근 3~5개 확정 turn에서 계산한 domain signal만 사용한다.
- `ChatRepositoryImpl`과 transport는 prompt 정책을 해석하지 않고, 이미 계산된 instruction과 speed만 전달한다.

## 맥락 해석

- 짧은 발화를 무조건 실패나 조각 발화로 보지 않는다.
- 직전 AI 질문에 대한 짧은 답변은 먼저 최근 맥락 안에서 정상 진행인지 확인한다.
- `ProgressingInContext`는 보정 강화 신호가 아니라 과보정 방지 신호다.
- 자연스러운 대화 흐름 자체를 `BeginnerAutoSupport` 같은 강한 보정 트리거로 반복 사용하지 않는다.
- `StuckOrFragment`, `PrimaryDominantTurn`, `ExplicitSupportRequest`, 명시적 막힘 표현처럼 실제 단절이나 도움 요청이 있을 때만 보정을 강화한다.
- 첫 인사나 “let's talk” 같은 대화 시작 표현은 짧아도 막힘으로 단정하지 않는다.

## 언어 보조

- 기본은 목표 언어 중심이다.
- `FallbackOnly`에서는 명시적 도움 요청이나 큰 오해가 있을 때만 기준언어를 짧게 사용한다.
- 칭찬, 요약, 공감만을 위해 기준언어를 덧붙이지 않는다.
- `BeginnerAutoSupport`가 세션 기본 정책과 같으면 같은 보조 지시를 turn override에 반복하지 않는다.
- 사용자가 기준언어를 주로 사용한 경우에는 이해 보조 신호로 볼 수 있지만, 목표 언어 대화 경험을 대체하지 않는다.

## 대화 주도권

- AI는 사용자가 모든 주제를 정하도록 압박하지 않는다.
- 대화 시작이나 재개 때는 최근 주제나 가벼운 일상 주제를 먼저 제안한다.
- “What do you want to talk about?” 같은 넓은 질문을 반복하지 않는다.
- 사용자가 짧게 답할 수 있도록 음식, 장소, 감정, 행동처럼 실제 내용이 있는 선택지를 제공한다.
- 최근 맥락은 이전 AI의 응답 습관을 모방하는 데 쓰지 않고, 주제 이해와 가벼운 대화 제안에만 사용한다.

## Prompt 금지 사항

- raw metric을 넣지 않는다.
- 내부 enum이나 단계 라벨을 그대로 노출하지 않는다.
- 나이 비유를 쓰지 않는다.
- 실패했던 문구 예시를 prompt에 직접 넣지 않는다.
- 이모지 생성 지시를 넣지 않는다.
- 같은 의미를 여러 위치에 반복해 모델이 특정 행동을 과하게 해석하게 만들지 않는다.

## 검증 기준

- 관련 prompt/chat unit test를 갱신한다.
- prompt 문자열 테스트는 문구 자체보다 책임 경계, 중복 제거, 회귀 방지를 검증한다.
- 가능하면 `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`까지 확인한다.
- 실제 앱 대화 세션을 만든 뒤 `AiChatPromptReview` export로 결과를 다시 분석한다.
- `AiChatPromptTrace` 또는 export에서 `contextSignal`, `basePolicy`, `turnPolicy`, `changed`, `hasInstructions`, `outputAudioSpeed`를 확인한다.
- 수정 후 유사 세션 2개 이상에서 재현되지 않으면 리뷰 노트의 상세 증거를 해결 기록으로 축약한다.

## 관련 문서

- [CHAT-TUNE-002 Conversation Ability Prompt Strategy](./CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)
- [RULES.md - Prompt Tuning Rules](../../../../RULES.md#prompt-tuning-rules)
