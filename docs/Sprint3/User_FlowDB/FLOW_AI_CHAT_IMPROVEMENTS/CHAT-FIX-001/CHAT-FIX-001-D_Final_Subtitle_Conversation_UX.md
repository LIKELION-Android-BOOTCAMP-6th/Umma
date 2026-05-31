# [Fix] CHAT-FIX-001-D final 자막 대화형 표시

## User Story

사용자는 AI Chat에서 자신의 발화와 AI 응답 자막을 메신저 대화처럼 순서대로 확인할 수 있다.

---

## 완료 기준(AC)

- [ ] 사용자 final 자막은 USER final transcript 수신 후 사용자 말풍선으로 추가된다.
- [ ] AI final 자막은 AI final transcript 수신 후 AI 말풍선으로 추가된다.
- [ ] 사용자와 AI 자막은 발화 순서대로 아래에 누적된다.
- [ ] 사용자와 AI 자막은 역할이 시각적으로 구분된다.
- [ ] 사용자/AI 자막은 실시간 타이핑이 아니라 final transcript 기준으로 표시된다.
- [ ] 같은 final 자막이 화면에 중복 표시되지 않는다.

---

## 기준 문서

- 부모 Fix 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001_AI_Chat_Stability_Fixes.md`
- Engine 전환: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md`
- Sprint2 Subtitle 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-006_Subtitle.md`
- Sprint2 Turn Commit 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-007_Turn_Commit.md`

---

## 구현 범위

### 포함 범위

- 현재 Chat 화면 표시용 subtitle item 리스트
- 사용자/AI final 자막의 순차 누적 표시
- 사용자/AI 역할 구분 UI
- 긴 자막의 줄바꿈 및 스크롤 대응
- 화면 표시용 자막 상태와 SessionMemory 저장 트리거 분리

### 제외 범위

- 사용자 발화 중 실시간 타이핑 자막
- AI 응답 중 실시간 타이핑 자막
- 전체 대화 로그 화면 구현
- SessionMemory 저장 정책 변경
- correctionAvailable 신호 정책 변경
- OpenAI Realtime transport 설정 변경

---

## UX 정책

이번 범위의 자막은 실시간 타이핑 자막이 아니라 final transcript 표시다. OpenAI Realtime에서 partial/delta 이벤트가 도착하더라도 MVP 화면에서는 final 자막만 대화형으로 누적한다.

```text
사용자 final transcript
→ 사용자 말풍선 추가
→ AI final transcript
→ AI 말풍선 추가
→ 다음 사용자 final transcript
→ 사용자 말풍선 추가
```

- USER final transcript는 사용자 말풍선으로 표시한다.
- AI final transcript는 AI 말풍선으로 표시한다.
- 새 자막은 메신저 대화처럼 아래쪽에 추가한다.
- 사용자와 AI 말풍선은 정렬, 색상, 라벨 중 하나 이상으로 구분한다.
- 화면 표시용 자막 리스트는 저장 데이터가 아니며, 장기 저장의 source of truth는 기존 SessionMemory final turn이다.

---

## 책임 경계

- `ChatScreen`: subtitle 리스트를 렌더링하고 새 항목이 아래에 추가되는 UI를 담당한다.
- `ChatViewModel`: final transcript event를 화면 표시용 subtitle item으로 변환한다.
- `SessionMemoryRepository`: final turn 저장만 담당하며 화면 표시용 subtitle 리스트를 저장하지 않는다.
- `ChatRepository`: USER/AI final transcript 이벤트를 역할별로 전달한다.

---

## 검증 기준

- 사용자 final 자막이 사용자 말풍선으로 추가된다.
- AI final 자막이 AI 말풍선으로 추가된다.
- 여러 턴을 진행하면 자막이 발화 순서대로 아래에 누적된다.
- 사용자/AI 말풍선이 시각적으로 구분된다.
- partial/delta 이벤트가 화면에 실시간 타이핑 자막처럼 표시되지 않는다.
- 같은 final 자막이 화면에 중복 표시되지 않는다.
- 화면 회전 후 현재 표시 중인 자막 리스트가 유지된다.

---

## Edge Cases

- USER final transcript가 늦게 도착함
- USER final transcript가 비어 있음
- AI final transcript가 비어 있음
- 동일 final turn 이벤트가 중복 수신됨
- 긴 사용자/AI 자막이 여러 줄로 표시됨
- 여러 턴 진행 후 화면 회전
- 자막 Off 상태에서 final transcript 이벤트가 도착함
