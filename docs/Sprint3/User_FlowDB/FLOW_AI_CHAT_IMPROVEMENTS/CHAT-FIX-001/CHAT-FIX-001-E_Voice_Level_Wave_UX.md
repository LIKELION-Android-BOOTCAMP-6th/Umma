# [Fix] CHAT-FIX-001-E 음성 레벨 반응형 웨이브

## User Story

사용자는 AI Chat에서 자신이 말하고 있는 상태와 AI가 말하고 있는 상태를 음성 레벨에 반응하는 더 큰 원형 wave 애니메이션으로 직관적으로 인지할 수 있다.

---

## 완료 기준(AC)

- [ ] 사용자가 말하는 동안 중앙 애니메이션이 입력 음성 크기에 맞춰 눈에 띄게 반응한다.
- [ ] AI가 말하는 동안 중앙 애니메이션이 출력 음성 크기에 맞춰 눈에 띄게 반응한다.
- [ ] 애니메이션은 기존 원형 계열을 유지하되, 완전한 동심원 반복이 아니라 가장자리가 유동적인 wave 형태로 보인다.
- [ ] 크게 말하거나 AI 음성이 커질 때 wave의 크기, 흔들림, 투명도 또는 선 강도 중 하나 이상이 함께 커진다.
- [ ] 조용하거나 음성이 없을 때 wave가 과하게 흔들리지 않고 안정적인 idle 상태로 돌아온다.
- [ ] wave 변경 후에도 마이크 버튼 상태, final 자막 표시, 대화 저장 흐름은 기존처럼 동작한다.

---

## 기준 문서

- 부모 Fix 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001_AI_Chat_Stability_Fixes.md`
- Engine 전환: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md`
- Sprint2 AI Chat 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`

---

## 구현 범위

### 포함 범위

- 기존 `ChatUiState.inputLevel` 기반 사용자 입력 wave 표시
- 기존 `ChatUiState.outputLevel` 기반 AI 출력 wave 표시
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
- wave 움직임은 매 프레임 무작위로 바뀌는 random 값이 아니라, 시간과 음성 레벨을 조합한 deterministic animation으로 만든다.
- 애니메이션은 현재 상태를 보여주는 feedback이며, AI 응답 생성이나 SessionMemory 저장 정책에 관여하지 않는다.

---

## 책임 경계

- `ChatScreen`: wave animation composable을 렌더링한다.
- `ChatViewModel`: 사용자 입력/AI 출력 상태와 화면에 필요한 level 값을 제공한다.
- `AudioRecorder` / `AudioPlayer`: 기존처럼 입력/출력 audio level을 계산해 전달한다.
- `ChatRepository`: transport와 transcript/audio 이벤트를 담당한다. 이번 작업에서 wave UI 정책을 판단하지 않는다.

현재 코드에는 `inputLevel` / `outputLevel` 전달 경로가 이미 있으므로, 별도 문제가 발견되지 않는 한 이번 구현은 `ChatScreen`의 중앙 visual 개선에 집중한다.

---

## 검증 기준

- 사용자 발화 중 wave가 입력 음성 레벨 변화에 따라 더 크게 또는 더 강하게 보인다.
- AI 응답 중 wave가 출력 음성 레벨 변화에 따라 더 크게 또는 더 강하게 보인다.
- wave가 완전한 동심원 확대/축소만 반복하지 않고, 원형 가장자리의 유동적인 움직임을 보여준다.
- 음성이 작거나 없으면 wave가 idle 상태로 돌아온다.
- 사용자 입력 wave와 AI 출력 wave가 상태에 맞게 전환된다.
- 화면 회전 후에도 현재 입력/출력 상태에 맞는 wave 상태가 이어진다.
- AI 응답 중 마이크 버튼 비활성화 상태가 유지된다.
- final 자막 영역과 중앙 wave가 서로 읽기 어렵게 겹치지 않는다.
- 애니메이션 변경 후에도 녹음, 재생, final transcript 저장 흐름이 유지된다.

---

## Edge Cases

- 입력 음성 레벨이 거의 0에 가까움
- 출력 음성 레벨이 거의 0에 가까움
- 음성 레벨이 급격하게 커졌다가 작아짐
- 녹음 시작 직후 즉시 정지
- AI 응답 중 화면 회전
- 오디오 출력이 끝났지만 final transcript가 늦게 도착함
