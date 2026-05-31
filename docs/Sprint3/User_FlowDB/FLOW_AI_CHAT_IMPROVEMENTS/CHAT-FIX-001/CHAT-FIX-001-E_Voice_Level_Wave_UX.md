# [Fix] CHAT-FIX-001-E 음성 레벨 반응형 웨이브

## User Story

사용자는 AI Chat에서 자신이 말하고 있는 상태와 AI가 말하고 있는 상태를 음성 레벨에 반응하는 더 큰 원형 wave 애니메이션으로 직관적으로 인지할 수 있다.

---

## 완료 기준(AC)

- [ ] 사용자 발화 중 애니메이션은 입력 음성 레벨 변화에 따라 반응한다.
- [ ] AI 응답 중 애니메이션은 출력 음성 레벨 변화에 따라 반응한다.
- [ ] 애니메이션은 기존 원형 계열을 유지하되 물결처럼 유동적인 wave 형태로 표시된다.
- [ ] 음성이 커질수록 wave의 크기, 변형, alpha 또는 stroke 강도 중 하나 이상이 커진다.
- [ ] 음성 레벨이 작거나 없을 때는 과한 움직임 없이 idle 상태로 돌아온다.
- [ ] 음성 반응 애니메이션은 입력/출력 상태 표시만 담당하고 저장/응답 생성 정책을 바꾸지 않는다.

---

## 기준 문서

- 부모 Fix 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001_AI_Chat_Stability_Fixes.md`
- Engine 전환: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md`
- Sprint2 AI Chat 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`

---

## 구현 범위

### 포함 범위

- 사용자 입력 음성 레벨 기반 animation state
- AI 출력 음성 레벨 기반 animation state
- 기존 원형 계열을 유지한 wave 형태의 시각 개선
- 음성 레벨에 따른 크기/변형/투명도/선 강도 반응
- idle 상태 복귀

### 제외 범위

- 음성 인식 모델 변경
- 음성 합성 모델 변경
- OpenAI Realtime transport 설정 변경
- 사용자/AI 자막 UI 변경
- 마이크 버튼 상태 UX 변경
- SessionMemory 저장 정책 변경

---

## UX 정책

기존 동심원 반응은 현재 입력/출력 상태를 드러내기에는 약하다. 이번 작업에서는 원형 계열의 방향은 유지하되, 음성 레벨에 따라 가장자리가 유동적으로 흔들리는 wave 형태로 개선한다.

- 사용자 발화 중에는 microphone input level을 반영한다.
- AI 응답 중에는 output audio level을 반영한다.
- 음성이 커질수록 wave radius, deformation, alpha, stroke 강도 중 하나 이상이 커진다.
- 음성이 작거나 없으면 잔잔한 idle 상태로 돌아온다.
- 애니메이션은 현재 상태를 보여주는 feedback이며, AI 응답 생성이나 SessionMemory 저장 정책에 관여하지 않는다.

---

## 책임 경계

- `ChatScreen`: wave animation composable을 렌더링한다.
- `ChatViewModel`: 사용자 입력/AI 출력 상태와 화면에 필요한 level 값을 제공한다.
- `ChatRepository`: level 계산에 필요한 audio frame 또는 output audio 이벤트를 전달한다.
- `AudioRecorder` / `AudioPlayer`: 실제 입력/출력 audio stream을 다루되 UI 정책을 판단하지 않는다.

---

## 검증 기준

- 사용자 발화 중 wave가 입력 음성 레벨 변화에 반응한다.
- AI 응답 중 wave가 출력 음성 레벨 변화에 반응한다.
- 음성이 커질수록 wave 반응이 더 크게 보인다.
- 음성이 작거나 없으면 idle 상태로 돌아온다.
- 사용자 입력 wave와 AI 출력 wave가 상태에 맞게 전환된다.
- 애니메이션 변경 후에도 녹음, 재생, final transcript 저장 흐름이 유지된다.

---

## Edge Cases

- 입력 음성 레벨이 거의 0에 가까움
- 출력 음성 레벨이 거의 0에 가까움
- 음성 레벨이 급격하게 커졌다가 작아짐
- 녹음 시작 직후 즉시 정지
- AI 응답 중 화면 회전
- 오디오 출력이 끝났지만 final transcript가 늦게 도착함
