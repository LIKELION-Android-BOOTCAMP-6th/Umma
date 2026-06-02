# [UX] CHAT-UX-003-A 기본 음성 인터랙션 캐릭터

## User Story

사용자는 AI Chat 화면에서 중앙 인터랙션 캐릭터를 통해 지금 앱이 내 말을 듣고 있는지, 내 목소리가 입력되고 있는지, Umma가 생각 중인지, Umma가 말하고 있는지를 즉시 이해할 수 있어야 한다.

이번 작업은 기존 중앙 마이크/스피커 아이콘과 약한 halo 반응을 대체하는 `CHAT-UX-003`의 1차 구현 범위다.

세부 모션 고도화와 성능 최적화는 `CHAT-UX-003-B_Character_Motion_Performance.md`에서 별도로 다룬다.

---

## 완료 기준(AC)

- [ ] 중앙 인터랙션은 단순 아이콘이 아니라 `Umma` 캐릭터형 voice orb로 표시된다.
- [ ] Idle 상태에서도 캐릭터가 멈춘 이미지처럼 보이지 않는다.
- [ ] Listening 상태에서 사용자 `inputLevel`에 따라 좌우 레벨미터가 동시에 waveform처럼 반응한다.
- [ ] Speaking 상태에서 AI `outputLevel`에 따라 좌우 레벨미터가 동시에 waveform처럼 반응한다.
- [ ] Thinking 상태에서 사용자는 Umma가 응답을 준비 중임을 이해할 수 있다.
- [ ] Idle / Listening / Thinking / Speaking 상태가 시각적으로 구분된다.
- [ ] 캐릭터와 ring glow는 자막/말풍선 영역을 읽기 어렵게 침범하지 않는다.

---

## 기준 문서

- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- Fix 기준: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001_AI_Chat_Stability_Fixes.md`
- 마이크 버튼 상태: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-C_Mic_Button_State_UX.md`
- final 자막 표시: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-D_Final_Subtitle_Conversation_UX.md`

---

## 구현 범위

### 포함 범위

- AI Chat 중앙 visual을 Compose Canvas 기반 `VoiceInteractionCharacter`로 교체
- 2D 카툰 스타일의 둥근 orb 캐릭터, 흰색 눈, floating shadow 표현
- 색상별로 분리된 4개의 arc ring과 발광 stroke/glow 표현
- 좌우 레벨미터 표시. 현재 발화가 사용자 음성이든 AI 음성이든 양쪽 레벨미터가 동시에 반응한다.
- `Umma` label과 현재 상태 문구 표시
- `inputLevel` / `outputLevel` 기반 visual level 보정
- Idle / Listening / Thinking / Speaking / Disabled 상태별 visual feedback 및 animation
- 상태별 레벨미터 크기 변화, arc ring 회전 속도 변화, alpha/glow 강도 변화
- 자막 영역과 겹치지 않는 고정 높이/반응형 크기 적용

### 제외 범위

- 공처럼 점프하는 완성형 motion
- 명시적인 시선 방향 animation
- 마이크 버튼 동작 변경
- 사용자/AI final 자막 정책 변경

---

## 디자인 방향

목표는 고급 3D/2.5D 렌더링이 아니라 Compose Canvas 기반 2D 카툰 voice orb다.

- 내부 orb는 teal 계열의 단순한 원형 캐릭터로 표현한다.
- 눈은 흰색 세로 타원 2개로 단순하게 둔다.
- 입, 복잡한 표정, 큰 하이라이트는 사용하지 않는다.
- outer ring은 orange, pink, blue, purple 4개의 arc로 분리한다.
- 4개의 arc는 서로 다른 반지름을 가져 겹치지 않는다.
- 4개의 arc는 교차 방향으로 계속 회전한다.
- ARC는 단일 단색 stroke가 아니라 blur, halo, core, highlight 레이어를 겹쳐 발광선처럼 보이게 한다.
- 상태별 alpha/glow 값은 즉시 튀지 않고 easing으로 서서히 밝아지거나 어두워진다.
- 캐릭터 아래에는 약한 floating shadow를 둔다.
- 좌측 레벨미터는 orange 계열, 우측 레벨미터는 blue 계열로 표시한다.
- 색상은 화면 균형을 위한 구분이며, 특정 상태에서 특정 색만 과하게 강조하지 않는다.
- 움직임과 glow boost는 현재 발화 음성 레벨을 양쪽 레벨미터와 ARC에 동시에 반영한다.
- A 단계는 기본적인 크기, 위치, 눈 반응까지만 다룬다.
- 공처럼 점프하는 오브 모션, 명시적인 시선 방향, 점프와 연동되는 그림자 변화는 B에서 별도 작업한다.

---

## 컬러 기준

기존 앱 테마를 유지하되, 사용자 입력과 AI 출력을 명확히 구분한다.

- 배경: `#F8F2E5`
- Primary Orange: `#F4901E`
- Deep Plum: `#552B5B`
- Title Brown: `#8D4F00`
- Text Brown: `#554335`
- Inner Orb Teal: `#0D7B70`, `#18AE91` 계열
- Input Accent: `#F4901E`, `#FFB14A` 계열
- Output Accent: `#4A7DFF`, `#552B5B`, `#7B4C9E` 계열

---

## 상태 정책

### Idle

- 캐릭터 본체는 과하게 움직이지 않는다.
- 4개의 arc ring은 느리게 계속 회전한다.
- 레벨미터는 매우 약하게 표시한다.
- 상태 문구는 대화 가능 상태를 짧게 표현한다.

### Listening

- 사용자가 마이크 버튼을 누르고 발화 중인 상태다.
- `inputLevel`에 따라 좌우 레벨미터가 빠르게 동시에 반응한다.
- 사용자 음성 입력이 감지되면 양쪽 레벨미터가 waveform처럼 격동적으로 움직인다.
- ARC는 기본적으로 활성화된 상태를 유지하고, 입력 강도가 커질수록 발광 stroke와 glow boost가 더 눈에 띄게 커진다.
- arc ring은 Idle보다 빠르게 회전해 "듣는 중" 상태가 명확히 보이게 한다.
- 작은 목소리에도 최소 반응이 보여야 한다.
- 큰 목소리에는 좌우 레벨미터 높이, 움직임 폭, ARC glow 변화가 확실히 커져야 한다.

### Thinking

- 사용자가 정지 버튼을 누른 뒤 Umma가 응답을 준비하는 상태다.
- arc ring은 Idle보다 활성화되어 있지만 Listening/Speaking보다는 차분하게 유지해 "처리 중"임을 보여준다.
- 입력/출력 레벨미터는 낮은 강도로 대기한다.
- ARC alpha/glow는 Idle보다 높고 Listening/Speaking보다 낮게 유지한다.
- 화면이 멈춘 것처럼 보이면 안 된다.

### Speaking

- AI 음성이 출력 중인 상태다.
- `outputLevel`에 따라 좌우 레벨미터가 빠르게 동시에 반응한다.
- AI 음성 출력이 감지되면 양쪽 레벨미터가 waveform처럼 격동적으로 움직인다.
- ARC는 기본적으로 활성화된 상태를 유지하고, 출력 강도가 커질수록 발광 stroke와 glow boost가 더 눈에 띄게 커진다.
- arc ring은 Listening과 같은 수준으로 활성화되어 보이되, 출력 레벨에 따른 glow로 AI 발화 상태를 보조한다.
- AI가 말하고 있다는 사실이 자막 없이도 드러나야 한다.

### Disabled / Error

- 채도와 glow를 낮춘다.
- 레벨미터는 숨기거나 약하게 둔다.
- ring 회전은 멈추거나 매우 느리게 낮춰 상호작용 불가 상태를 보여준다.
- 기존 에러/재시도 UI를 가리지 않는다.

---

## 음성 레벨 처리 기준

`inputLevel`과 `outputLevel`은 0f~1f로 들어오지만, 시각 반응에는 그대로 사용하지 않는다.

- raw level은 0f~1f로 clamp한다.
- active 상태에서는 너무 작은 값도 완전히 죽이지 않고 최소 visual level을 둔다.
- 작은 소리도 보이도록 sqrt 계열 보정 또는 유사 curve를 적용할 수 있다.
- 레벨미터는 중앙 막대가 가장 길고 양끝 막대가 가장 짧은 고정 실루엣을 유지한다.
- ARC glow level은 레벨미터와 별도 curve를 사용할 수 있다. 작은 소리는 낮게, 큰 소리는 더 크게 반응하도록 대비를 둔다.

---

## 애니메이션 물리감

애니메이션은 느리거나 둔하게 보이면 안 된다. 시작 반응은 빠르고, 복귀는 짧은 잔향이 남는 정도로 부드럽게 둔다.

- 모든 상태에서 모든 애니메이션을 동시에 강하게 쓰지 않는다. 상태를 구분하는 주 피드백을 먼저 정하고, 나머지는 보조 피드백으로만 사용한다.
- `inputLevel` 증가: 좌우 레벨미터 높이와 막대별 변화 폭이 빠르게 커진다.
- `outputLevel` 증가: 좌우 레벨미터 높이와 막대별 변화 폭이 빠르게 커진다.
- arc ring: 상태와 무관하게 계속 회전하되, 색상별로 반지름과 방향을 다르게 둔다.
- arc ring speed: Idle은 느리게, Listening/Speaking은 가장 활성화된 상태로, Thinking은 Idle보다 빠르지만 발화 상태보다는 차분하게 조정한다.
- glow: 상태별 기본 glow는 `Listening/Speaking > Thinking > Idle > Disabled` 위계를 유지하고, 실제 음성 강도에 따른 boost를 별도로 더한다.
- alpha/glow 전환: 상태가 바뀔 때 즉시 튀지 않고 easing으로 천천히 밝아지거나 어두워진다.
- level meter glow: 음성 입력/출력 레벨이 커질수록 양쪽 레벨미터의 밝기와 높이가 함께 커진다.
- Disabled/Error: 전체 alpha를 낮춰 상호작용 불가 상태를 표현한다.

### 상태별 애니메이션 요약

| 상태 | 레벨미터 | Arc ring | Glow | 상태 문구 |
| --- | --- | --- | --- | --- |
| Idle | 낮은 강도 | 느린 지속 회전 | 낮은 강도 | 대화 가능 |
| Listening | 좌우 동시 waveform 반응 | 활성화된 빠른 회전 | 높은 기본 glow + 입력 boost | 듣는 중 |
| Thinking | 낮은 강도 | Idle보다 활성화된 회전 | 중간 glow | 답변 준비 중 |
| Speaking | 좌우 동시 waveform 반응 | 활성화된 빠른 회전 | 높은 기본 glow + 출력 boost | 말하는 중 |
| Disabled/Error | 숨김 또는 약화 | 정지 또는 매우 느림 | 낮은 채도/낮은 alpha | 대화 불가/오류 |

### 상태별 주 피드백

상태별로 가장 먼저 눈에 들어와야 하는 피드백은 다음과 같다.

- Idle: 느리게 회전하는 arc ring과 약한 레벨미터
- Listening: inputLevel 기반 좌우 레벨미터 waveform 반응
- Thinking: 멈추지 않는 arc ring 회전과 상태 문구
- Speaking: outputLevel 기반 좌우 레벨미터 waveform 반응과 ARC glow boost
- Disabled/Error: 낮은 채도와 약화된 움직임

---

## 책임 경계

- `ChatScreen`: 화면 전체 배치와 상태 전달만 담당한다.
- `VoiceInteractionCharacter`: 캐릭터 시각화, 상태 문구, visual level 보정, animation을 담당한다.
- `ChatViewModel`: 음성 입력/출력 레벨과 대화 상태를 제공한다.

---

## 작업 방향

이번 작업은 레퍼런스 이미지를 완전하게 복제하는 것이 아니라, 사용자에게 상태와 음성 반응을 명확히 전달하는 기본 캐릭터를 목표로 한다.

- 기존 중앙 아이콘을 캐릭터형 orb로 교체한다.
- `Umma` label을 표시한다.
- Listening / Thinking / Speaking 상태 문구를 표시한다.
- `inputLevel`에 따라 좌우 레벨미터가 동시에 waveform처럼 반응한다.
- `outputLevel`에 따라 좌우 레벨미터가 동시에 waveform처럼 반응한다.
- 음성 level이 커질수록 좌우 레벨미터 높이와 움직임 폭이 확실히 커진다.
- 4개의 arc ring은 서로 다른 반지름과 방향으로 계속 회전한다.
- 상태에 따라 ring 회전 속도와 alpha/glow 강도를 다르게 적용한다.
- 상태별 alpha/glow 변화는 천천히 보간하고, 음성 강도 boost는 빠르게 반응한다.
- 자막 영역과 겹치지 않게 상단/중단 영역 안에 배치한다.
- 기존 마이크 버튼 동작과 final 자막 정책은 변경하지 않는다.
- A 단계 애니메이션은 ring 회전, ARC glow, 레벨미터 반응에 집중한다.
- 공처럼 점프하는 오브 모션, 명시적인 시선 방향, 점프와 연동되는 그림자는 B 단계에서 다룬다.

---

## 레이아웃 기준

- 캐릭터 영역은 화면 중앙보다 약간 위쪽에 배치한다.
- 자막 버블 영역을 침범하지 않는다.
- orb 본체는 화면 너비의 약 25~35%를 기준으로 한다.
- 레벨미터/ring glow 포함 전체 폭은 화면 너비의 50~70% 안에 들어오게 한다.
- 작은 화면에서는 캐릭터 표현보다 자막 가독성을 우선한다.
- 가로 화면에서 무리하게 크게 보이지 않도록 최대 크기를 제한한다.

---

## 구현 주의

기본 캐릭터는 Compose Canvas에서 비용이 낮은 표현을 조합한다.

- blur-heavy 구현은 피한다.
- 단순 도형과 제한된 blur/glow를 우선한다.
- 레벨미터는 rounded rect 기반으로 그린다.
- outer ring arc는 색상별 4개로 제한한다.
- 전체 화면의 불필요한 갱신을 만들지 않도록 캐릭터 표현 범위 안에서 animation을 처리한다.

---

## 검증 기준

- Idle 상태에서 캐릭터가 정적인 아이콘처럼 멈춰 보이지 않는다.
- Listening 상태에서 크게 말하면 좌우 레벨미터가 동시에 확실히 커진다.
- Thinking 상태에서 응답 준비 중임을 알 수 있다.
- Speaking 상태에서 좌우 레벨미터가 AI 음성 크기에 따라 동시에 반응한다.
- 긴 자막이 표시되어도 캐릭터와 ring glow가 자막 가독성을 해치지 않는다.
- 마이크 버튼과 final 자막 흐름이 기존처럼 동작한다.

---

## 최종 의도

이 캐릭터는 장식이 아니라 AI Chat의 상태 피드백 장치다.

사용자는 다음을 즉시 알아야 한다.

- 지금 내 말을 듣고 있는가?
- 내 목소리가 입력되고 있는가?
- Umma가 생각 중인가?
- Umma가 말하고 있는가?

따라서 디자인은 예쁘게 보이는 것보다 상태 구분과 반응성이 우선이다. 다만 상태 피드백이 명확하다는 이유로 표현력을 지나치게 낮추면 안 된다.

---

## Edge Cases

- 입력 음성 레벨이 거의 0에 가까움
- 출력 음성 레벨이 거의 0에 가까움
- 음성 레벨이 급격하게 커졌다가 작아짐
- 사용자 발화 확정 대기 상태가 길어짐
- 긴 사용자/AI 자막이 표시됨
- 오류 또는 대화 불가 상태에서 진입함
