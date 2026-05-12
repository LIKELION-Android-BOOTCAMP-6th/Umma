# [Feature] CHAT-003 자막 표시

## User Story

사용자는 AI가 말하는 동안 마지막 턴의 자막을 보거나 숨길 수 있으며, 자막 기본값은 Off다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 자막 기본값이 Off다.
- [ ] 자막이 On일 때 마지막 턴의 자막만 표시된다.
- [ ] 사용자 발화와 AI 발화가 구분되어 표시된다.
- [ ] 자막 On/Off 상태가 정상 반영된다.
- [ ] 자막 지연 시 마지막 상태가 유지된다.
- [ ] 화면이 과도하게 흔들리거나 재구성되지 않는다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-003 → 자막 표시

---

# 구현 범위

## 포함 범위

- subtitle rendering
- transcript display
- subtitle visibility toggle
- loading / streaming / complete state

## 제외 범위 (Out of Scope)

- 전체 대화 전문 누적 표시
- 자막 On 상태에서 과거 턴 전체 표시
- 세션 생성
- turn commit
- correction / flashcard 저장

---

# Details

## 자막 정책

- 자막 기본값은 Off다.
- On 상태일 때는 마지막 턴의 자막만 보여준다.
- 전체 대화 전문은 누적 표시하지 않는다.

## 표시 정책

- AI 발화와 사용자 발화를 구분해서 보여준다.
- 자막 지연 시 마지막 상태를 유지한다.
- 토글은 표시 상태만 바꾸고 저장 상태는 바꾸지 않는다.

## 예외 처리

- 렌더링 실패 시 화면 전체를 깨지 말고 마지막 상태를 유지한다.
- 자막 상태는 세션 저장과 독립적으로 관리한다.

## 데모 확인 포인트

1. 자막 기본값이 Off인지 확인
2. On 상태에서 마지막 턴만 보이는지 확인
3. Off 상태에서 자막이 숨겨지는지 확인

## 권장 구현 경계

- `presentation/chat/ChatScreen.kt`
- `presentation/chat/ChatViewModel.kt`
- `domain/model/AIEvent.kt`
- `data/repository/ChatRepositoryImpl.kt`

## 현재 테스트 코드 전환 메모

- 현재 `AIEvent.TextResponse(text)`만으로는 user/assistant, 부분/최종 transcript를 구분하기 어렵다.
- 자막 UI는 최종 transcript 기준의 마지막 턴만 표시해야 하므로, 이벤트 모델에 role과 final 여부를 표현할 수 있어야 한다.
- 자막 토글 기본값은 Off로 두고, On/Off는 저장 여부나 turn commit에 영향을 주지 않는다.

## 한 줄 가이드

- 자막은 저장 데이터가 아니라 UI 상태로 다루고, 마지막 확정 턴만 보여준다.
