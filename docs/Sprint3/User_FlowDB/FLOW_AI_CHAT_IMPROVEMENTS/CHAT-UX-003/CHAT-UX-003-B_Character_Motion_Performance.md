# [UX] CHAT-UX-003-B 캐릭터 모션 및 성능 최적화

## User Story

사용자는 AI Chat 화면의 `Umma` 캐릭터가 단순히 회전하거나 반짝이는 장식이 아니라, 대화 상태에 맞춰 자연스럽게 살아 움직이는 존재처럼 느껴야 한다.

이번 작업은 `CHAT-UX-003-A`의 기본 캐릭터 위에 공처럼 점프하는 오브 모션, 시선 방향 애니메이션, 점프와 연동되는 그림자를 추가한다.

---

## 완료 기준(AC)

- [ ] Orb bounce는 scale 변화가 아니라 오브 전체가 공처럼 위아래로 점프하는 모션으로 표현된다.
- [ ] 점프 높이는 상태별로 다르며, Listening/Speaking에서 가장 활발하고 Thinking은 그보다 차분하다.
- [ ] 눈은 상태에 따라 자연스럽게 다른 방향을 바라보는 느낌을 준다.
- [ ] 그림자는 오브 점프에 맞춰 자연스럽게 변한다.
- [ ] 점프, 눈, 그림자 애니메이션은 자막/마이크 버튼 영역을 침범하지 않는다.
- [ ] 애니메이션 추가 후에도 기존 대화 조작과 자막 표시 흐름은 유지된다.

---

## 기준 문서

- 상위 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-UX-003_Voice_Interaction_Character.md`
- 기본 구현: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-UX-003/CHAT-UX-003-A_Voice_Interaction_Character_Base.md`
- 데모 시나리오: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`

---

## 포함 범위

- 오브 전체의 Y축 점프 모션
- 점프 높이와 상태별 활성도 정책
- 오브 점프와 연동되는 shadow scale/alpha
- 눈의 시선 방향 전환 모션
- animation/draw 비용이 과해지지 않도록 하는 기본 조정 기준

## 제외 범위

- 마이크 버튼 동작 변경
- 사용자/AI final 자막 표시 정책 변경
- 복잡한 표정 시스템

---

## 모션 정의

### 1. Orb Jump

`Orb bounce`는 scale만 커지는 효과가 아니다. 오브 본체가 공처럼 위로 떠올랐다가 다시 내려오는 Y축 이동을 의미한다.

- Idle: 거의 점프하지 않는다. 아주 약한 breathing 정도만 유지한다.
- Listening: 사용자가 말하는 동안 입력 레벨에 따라 작게 점프한다.
- Thinking: 응답 준비 중임을 보여주는 낮은 강도의 점프 또는 미세 부유감을 사용한다.
- Speaking: AI 음성 출력 레벨에 따라 Listening과 비슷하거나 조금 더 리듬감 있게 점프한다.
- Disabled/Error: 점프하지 않는다.

- 큰 소리일수록 점프 높이가 커지지만, 자막 영역을 침범하지 않도록 상한을 둔다.
- 착지 시 갑자기 튀지 않도록 부드럽게 돌아온다.

### 2. Eye Gaze

눈 애니메이션은 단순한 미세 좌우 drift가 아니라, 캐릭터가 특정 방향을 바라보는 느낌을 준다.

사용 가능한 시선 방향:

- Center
- Left
- Right
- UpLeft
- UpRight
- DownLeft
- DownRight

상태별 기준:

- Idle: 일정 간격으로 자연스럽게 방향을 바꾼다.
- Listening: 사용자의 말을 듣는 느낌이 우선이므로 Center 또는 살짝 Left/Right 정도로 안정적으로 둔다.
- Thinking: UpLeft/UpRight처럼 생각하는 방향을 주로 사용한다.
- Speaking: Center 또는 사용자를 바라보는 듯한 Left/Right를 사용한다.
- Disabled/Error: Center에 가깝게 고정한다.

- 눈 모양 자체를 바꾸지 않고 eye offset만 변경한다.
- 방향 전환은 즉시 튀지 않고 부드럽게 이동한다.
- 너무 자주 움직이면 산만하므로 상태별 최소 유지 시간을 둔다.

### 3. Shadow Response

그림자는 오브 점프와 연결된다.

- 오브가 위로 떠오르면 그림자는 작아지고 alpha가 낮아진다.
- 오브가 내려오면 그림자는 넓어지고 alpha가 높아진다.
- shadow는 오브의 부유감을 보조하되, 너무 진해서 UI를 더럽게 보이게 하면 안 된다.

- shadow blur는 화면 반응성을 해치지 않는 범위에서 제한적으로 사용한다.

---

## 구현 주의

인터랙션 캐릭터는 AI Chat 화면에 계속 표시되므로, 애니메이션 추가 시 성능 방어가 필요하다.

- 전체 화면의 불필요한 갱신을 만들지 않고 캐릭터 컴포넌트 내부 상태로 처리한다.
- Canvas draw layer 수를 과하게 늘리지 않는다.
- blur/glow는 필요한 곳에만 제한적으로 사용한다.
- 상태별 수치는 의미 없는 소수점 튜닝을 피하고 `0.05` 또는 `0.1` 단위 중심으로 관리한다.

---

## 책임 경계

- `ChatScreen`: 캐릭터 컴포넌트를 배치하고 자막/마이크 버튼 영역과 충돌하지 않게 한다.
- `VoiceInteractionCharacter`: 점프, 시선, 그림자, animation 강도 조정을 담당한다.
- `ChatViewModel`: 기존처럼 대화 상태와 음성 레벨만 제공한다.

---

## 작업 방향

1. 오브 전체가 위아래로 움직이는 점프 모션을 추가한다.
2. Listening/Speaking은 음성 레벨에 따라 점프가 더 활발해지도록 한다.
3. Thinking은 Idle보다 살아 있지만 Listening/Speaking보다 차분한 모션으로 둔다.
4. 오브 점프에 맞춰 그림자의 크기와 투명도가 함께 변하도록 한다.
5. 눈은 Center, Left, Right, 대각 방향을 자연스럽게 바라볼 수 있게 한다.
6. 상태별 시선 방향은 과하게 산만하지 않도록 제한한다.
7. 점프/눈/그림자 관련 수치는 코드에서 조정 지점을 쉽게 찾을 수 있게 주석을 남긴다.
8. `devDebug`에서 Idle, Listening, Thinking, Speaking, Disabled 상태를 수동 확인한다.
9. `:app:compileDevDebugKotlin`과 `:app:compileMockDebugKotlin`으로 컴파일을 확인한다.

---

## 검증 기준

- Idle 상태에서 캐릭터가 정지 이미지처럼 보이지 않는다.
- Listening 상태에서 사용자가 말하면 오브가 공처럼 작게 점프한다.
- Speaking 상태에서 AI 음성 출력에 맞춰 오브가 공처럼 작게 점프한다.
- Thinking 상태는 Idle보다 살아 있지만 Listening/Speaking보다 차분하다.
- 눈이 Center/Left/Right/대각 방향을 바라보는 느낌을 준다.
- 오브가 떠오를 때 그림자가 작아지고 옅어진다.
- 오브가 내려올 때 그림자가 넓어지고 진해진다.
- 긴 자막이 표시되어도 캐릭터 모션이 자막 가독성을 해치지 않는다.
- 기존 대화 조작과 자막 표시 흐름에 회귀가 없다.
