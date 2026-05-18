# [Feature] CHAT-007 확정 turn 저장 연동

## User Story

사용자는 AI와 나눈 확정 대화가 Session Memory에 반영되어 이후 Correction 흐름에서 다시 활용되기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] final user turn만 저장 대상으로 전달한다.
- [ ] final assistant turn만 저장 대상으로 전달한다.
- [ ] partial transcript는 저장 대상으로 전달하지 않는다.
- [ ] turn 저장은 `RT-003`의 Session Memory append 계약을 따른다.
- [ ] 저장 실패 시 사용자 입력 흐름이 즉시 크래시되지 않는다.
- [ ] 저장 성공 후 Correction에서 `recentFullContext`를 참조할 수 있는 상태가 된다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-005 → AI 응답 출력
- CHAT-006 → 마지막 턴 자막
- CHAT-007 → 확정 turn 저장 연동
- RT-003 → turn 저장

---

# 구현 범위

## 포함 범위

- final transcript 이벤트를 저장 입력으로 전달
- user / assistant role 구분
- 저장 성공/실패 상태 처리
- 후속 Correction 입력 준비 상태

## 제외 범위 (Out of Scope)

- Session Memory Repository 상세 구현
- Room Entity / DAO 구현
- Session Memory 압축
- LangState 업데이트

---

# Details

## 저장 연동 역할

User Flow에서는 final transcript 이벤트를 저장 usecase로 넘기는 연결 지점만 다룬다.
실제 Session Memory append 정책, Room local first, Firestore batch sync는 `RT-003`과 `LS-005` 기준을 따른다.

## 저장 기준

- user final turn
- assistant final turn
- timestamp 또는 turn order
- 현재 선택 언어

## 실패 정책

- 저장 실패는 화면 크래시로 이어지지 않아야 한다.
- 저장 실패 시 재시도 가능한 상태나 local pending 상태를 남긴다.
- 실패한 turn을 partial transcript처럼 버리지 않는다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatViewModel

domain/usecase/realtime/
→ AppendTurnUseCase
```

ViewModel은 final transcript를 감지해 저장 usecase를 호출한다.
저장 대상 판정은 partial/final 구분이 가능한 이벤트 모델을 사용한다.

---

## 현재 코드 전환 메모

- 현재 `AIEvent.TextResponse`를 그대로 저장하지 않는다.
- role과 final 여부가 구분된 이벤트만 `AppendTurnUseCase` 입력으로 전달한다.
- `ChatRepositoryImpl`은 Firebase Live API 연결과 이벤트 수신을 담당하고, Session Memory 저장 책임은 별도 repository/usecase로 분리한다.
- `recentFullContext`는 교정 입력으로 재사용되어야 하므로 확정 turn list 기준으로 append한다.

---

# 검증 기준

- partial transcript는 저장되지 않는다.
- final user / assistant turn만 저장 입력으로 전달된다.
- 저장 실패가 화면 크래시로 이어지지 않는다.
- 저장 실패 시 재시도 가능한 상태나 local pending 상태가 남는다.
- 저장된 turn은 이후 Correction 후보 추출의 입력이 될 수 있다.

---

# Edge Cases

- final user turn만 있고 assistant turn이 아직 없음
- assistant final turn이 중복 도착함
- 저장 중 화면 이탈
- 저장 실패 후 재시도 필요
- 언어가 바뀐 직후 turn 이벤트가 도착함
