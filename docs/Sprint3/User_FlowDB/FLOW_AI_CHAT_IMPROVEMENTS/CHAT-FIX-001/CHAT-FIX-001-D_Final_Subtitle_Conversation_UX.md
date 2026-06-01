# [Fix] CHAT-FIX-001-D final 자막 대화형 표시

## User Story

사용자는 AI Chat에서 자신의 최신 발화와 Umma의 최신 응답 자막을 메신저 대화처럼 역할별로 구분해 확인할 수 있다.

---

## 완료 기준(AC)

- [ ] 사용자 final 자막은 USER final transcript 수신 후 사용자 말풍선으로 추가된다.
- [ ] AI final 자막은 AI final transcript 수신 후 AI 말풍선으로 추가된다.
- [ ] 화면에는 사용자 최신 final 자막 1개와 AI 최신 final 자막 1개만 유지된다.
- [ ] 사용자와 AI 자막은 역할이 시각적으로 구분된다.
- [ ] 사용자/AI 자막은 실시간 타이핑이 아니라 final transcript 기준으로 표시된다.
- [ ] 같은 final 자막이 화면에 중복 표시되지 않는다.
- [ ] 긴 자막은 중앙 음성 visual을 덮지 않고 자막 영역 안에서 스크롤로 확인할 수 있다.

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
- 사용자/AI 최신 final 자막의 순차 표시
- 사용자/AI 역할 구분 UI
- 긴 자막의 전체 표시
- 긴 자막 확인을 위한 자막 영역 내부 스크롤과 상단 진행 힌트
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
→ 이전 사용자 말풍선 교체
```

- USER final transcript는 사용자 말풍선으로 표시한다.
- AI final transcript는 AI 말풍선으로 표시한다.
- 새 자막은 같은 역할의 이전 자막을 교체한다.
- 화면에는 사용자 최신 자막과 AI 최신 자막만 남긴다.
- 사용자와 AI 말풍선은 정렬, 색상, 라벨 중 하나 이상으로 구분한다.
- 긴 말풍선은 말줄임 없이 표시하되, 중앙 음성 visual을 덮지 않도록 자막 영역 안에서 스크롤한다.
- 자막 영역이 스크롤 가능할 때는 상단 fade와 화살표로 위쪽 내용을 더 볼 수 있음을 알려준다.
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
- 여러 턴을 진행해도 화면에는 사용자/AI 최신 자막 1개씩만 표시된다.
- 사용자/AI 말풍선이 시각적으로 구분된다.
- partial/delta 이벤트가 화면에 실시간 타이핑 자막처럼 표시되지 않는다.
- 같은 final 자막이 화면에 중복 표시되지 않는다.
- 화면 회전 후 현재 표시 중인 자막 리스트가 유지된다.
- 긴 자막은 말줄임 없이 자막 영역 안에서 스크롤로 확인된다.
- 스크롤 가능한 자막 영역 상단에 fade와 화살표 힌트가 표시된다.

---

## Edge Cases

- USER final transcript가 늦게 도착함
- USER final transcript가 비어 있음
- AI final transcript가 비어 있음
- 동일 final turn 이벤트가 중복 수신됨
- 긴 사용자/AI 자막이 여러 줄로 전체 표시됨
- 긴 자막이 중앙 음성 visual과 겹치지 않도록 자막 영역 내부에서 스크롤됨
- 여러 턴 진행 후 화면 회전
- 자막 Off 상태에서 final transcript 이벤트가 도착함
