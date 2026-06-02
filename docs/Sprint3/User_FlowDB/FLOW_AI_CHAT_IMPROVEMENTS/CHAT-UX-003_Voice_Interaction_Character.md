# [UX] CHAT-UX-003 음성 인터랙션 캐릭터

## User Story

사용자는 AI Chat 화면 중앙의 `Umma` 인터랙션 캐릭터를 통해 현재 대화 상태와 음성 입력/출력 반응을 즉시 이해할 수 있어야 한다.

이 문서는 `CHAT-UX-003`의 상위 문서다. 실제 작업은 하위 문서로 분리한다.

---

## 하위 작업

- `CHAT-UX-003-A`: 기본 인터랙션 캐릭터 구현
  - 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-UX-003/CHAT-UX-003-A_Voice_Interaction_Character_Base.md`
  - 범위: 2D 카툰 orb, 4개 ARC, 발광 stroke/glow, 좌우 레벨미터, 기본 상태별 반응

- `CHAT-UX-003-B`: 캐릭터 모션 및 성능 최적화 보강
  - 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-UX-003/CHAT-UX-003-B_Character_Motion_Performance.md`
  - 범위: 공처럼 점프하는 orb motion, 시선 방향 animation, 자연스러운 눈 깜빡임, 점프와 연동되는 shadow, Canvas 성능 방어

---

## 공통 원칙

- AI Chat transport, prompt, SessionMemory 저장, usage tracking 정책은 변경하지 않는다.
- `ChatScreen`은 상태와 음성 레벨을 전달하고 배치만 담당한다.
- `VoiceInteractionCharacter`는 presentation 전용 컴포넌트로 상태 시각화만 담당한다.
- 정확한 행동 안내 문구는 `ChatUiState.micStatusMessage` 기반 하단 안내를 단일 source로 사용하고, 중앙 캐릭터 내부에는 중복 상태 텍스트를 두지 않는다.
- 사용자 입력/AI 출력의 실제 의미 판단은 ViewModel/Repository가 담당한다.
- 자막과 마이크 버튼의 가독성/조작성이 캐릭터 표현보다 우선이다.
- 디자인 튜닝 수치는 의미 없이 흩어지지 않게 관리한다.
