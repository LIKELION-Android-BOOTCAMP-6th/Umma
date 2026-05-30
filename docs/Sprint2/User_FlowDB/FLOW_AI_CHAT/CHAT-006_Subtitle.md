# [Feature] CHAT-006 마지막 턴 자막

## User Story

사용자는 자막을 켰을 때 마지막으로 확정된 사용자 발화 또는 AI 응답만 확인할 수 있고, 필요하지 않으면 숨길 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 자막 기본값은 Off다.
- [ ] 자막 On 상태에서는 마지막 확정 턴만 표시한다.
- [ ] 사용자 발화와 AI 발화를 구분해 표시한다.
- [ ] partial transcript를 전체 대화처럼 누적 표시하지 않는다.
- [ ] 자막 지연 시 마지막 확정 자막을 유지한다.
- [ ] 화면이 과도하게 흔들리거나 재구성되지 않는다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-005 → AI 응답 출력
- CHAT-006 → 마지막 턴 자막
- RT-002 → partial/final transcript 구분

---

# 구현 범위

## 포함 범위

- subtitle toggle
- 마지막 확정 턴 자막 표시
- user / assistant role 표시
- Loading / Streaming / Complete 상태에 따른 자막 유지

## 제외 범위 (Out of Scope)

- 전체 대화 전문 표시
- Session Memory 저장
- turn commit
- correction / flashcard 저장

---

# Details

## 자막 정책

- 기본값은 Off다.
- On 상태에서만 마지막 확정 턴을 보여준다.
- 전체 대화 로그를 화면에 누적하지 않는다.
- 자막은 표시 상태이며 저장 정책이 아니다.

## 표시 기준

```text
subtitleVisible = false → 아무 자막도 표시하지 않음
subtitleVisible = true → lastFinalTurnSubtitle만 표시
```

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/components/
→ ChatSubtitle

presentation/chat/
→ SubtitleUiState
```

`SubtitleUiState`는 role, text, final 여부, visible 상태를 구분할 수 있어야 한다.

---

# 검증 기준

- 첫 진입 시 자막은 Off다.
- On 상태에서 마지막 확정 턴만 표시된다.
- Off로 바꾸면 자막이 숨겨진다.
- partial transcript가 누적 대화처럼 보이지 않는다.

---

# Edge Cases

- final transcript가 늦게 도착함
- 자막 On/Off를 빠르게 반복함
- role 정보가 없는 transcript가 들어옴
- 긴 자막이 화면을 가림
- Error 상태에서 이전 자막이 남아 있음
