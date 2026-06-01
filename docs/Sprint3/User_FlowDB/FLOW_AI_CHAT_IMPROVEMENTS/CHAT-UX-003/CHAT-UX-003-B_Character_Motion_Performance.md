# [UX] CHAT-UX-003-B 캐릭터 모션 및 성능 최적화

## User Story

사용자는 AI Chat 화면의 `Umma` 캐릭터가 단순히 회전하거나 반짝이는 장식이 아니라, 대화 상태에 맞춰 자연스럽게 살아 움직이는 존재처럼 느껴야 한다.

이번 작업은 `CHAT-UX-003-A`의 기본 캐릭터 위에 공처럼 점프하는 오브 모션, 시선 방향 애니메이션, 자연스러운 눈 깜빡임, 점프와 연동되는 그림자를 추가한다.

---

## 완료 기준(AC)

- [ ] Orb bounce는 scale 변화만으로 대체하지 않고, 오브 전체가 공처럼 위아래로 점프하는 모션으로 표현된다.
- [ ] 오브가 바닥에 가까워질 때는 가로로 살짝 squash되고, 위로 튀어오를 때는 세로로 살짝 stretch되어 탄성이 느껴진다.
- [ ] Listening/Speaking은 level meter와 ARC를 주 피드백으로 두고, 오브 점프는 Thinking과 비슷한 차분한 motion으로 제한된다.
- [ ] 눈은 상태에 따라 자연스럽게 다른 방향을 바라보는 느낌을 준다.
- [ ] 눈 깜빡임은 생동감을 주되, Listening/Speaking 중 음성 반응을 방해하지 않을 정도로 절제된다.
- [ ] 그림자는 오브 점프에 맞춰 자연스럽게 변한다.
- [ ] 대화 상태가 바뀌어도 bounce/gaze/blink 루프가 재시작되며 끊기지 않고, 현재 위치에서 다음 상태의 motion으로 자연스럽게 이어진다.
- [ ] 점프, 시선, 깜빡임, 그림자 애니메이션은 자막/마이크 버튼 영역을 침범하지 않는다.
- [ ] 중앙 캐릭터 내부의 중복 안내 텍스트는 제거하고, 정확한 행동 안내는 하단 `micStatusMessage` 한 곳에서 제공한다.
- [ ] Canvas draw에서 매 프레임 반복 생성되는 blur paint/filter/rect/list 객체를 줄이되, 기존 glow/shadow/level meter의 시각 효과는 유지한다.
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
- 눈 깜빡임 타이밍과 상태별 빈도 정책
- animation/draw 비용이 과해지지 않도록 하는 기본 조정 기준
- 중앙 캐릭터 내부 중복 문구 제거와 하단 안내 텍스트의 시각적 정돈
- 기존 디자인을 유지하는 범위의 Canvas draw allocation 방어

## 제외 범위

- 마이크 버튼 동작 변경
- 사용자/AI final 자막 표시 정책 변경
- 복잡한 표정 시스템
- 입 모양, 감정 표정, 눈썹 등 복합 facial animation

---

## 모션 정의

### 1. Orb Jump

`Orb bounce`는 scale만 커지는 효과가 아니다. 오브 본체가 공처럼 위로 떠올랐다가 다시 내려오는 Y축 이동을 기본으로 하고, 착지와 상승 순간에 약한 squash/stretch를 더해 탄성을 표현한다.

- Idle: 거의 점프하지 않는다. 아주 약한 breathing 정도만 유지한다.
- Listening: level meter와 ARC를 주 피드백으로 사용하고, 오브는 Thinking과 비슷한 작은 floating만 유지한다.
- Thinking: 응답 준비 중임을 보여주는 낮은 강도의 점프 또는 미세 부유감을 사용한다.
- Speaking: level meter와 ARC를 주 피드백으로 사용하고, 오브는 Thinking과 비슷한 작은 floating만 유지한다.
- Disabled/Error: 점프하지 않는다.

- 큰 소리 반응은 주로 level meter와 ARC glow에 반영하고, 오브 점프는 자막 영역을 침범하지 않도록 낮은 상한을 둔다.
- 착지 시 갑자기 튀지 않도록 부드럽게 돌아온다.
- 계속 같은 간격으로 반복 점프하지 않고, 짧은 휴지와 약한 높이 변화를 섞어 간헐적으로 튀게 한다.
- 오브가 바닥에 가까울수록 가로로 살짝 넓어지고 세로로 낮아진다.
- 오브가 위로 튀어오를수록 가로는 살짝 좁아지고 세로로 길어진다.
- squash/stretch는 Y축 점프를 보조하는 물리 과장일 뿐, scale 변화만으로 bounce를 대체하지 않는다.

### 2. Eye Gaze

눈 애니메이션은 단순한 미세 좌우 drift가 아니라, 캐릭터가 특정 방향을 바라보는 느낌을 준다.

사용 가능한 시선 방향:

- Center
- Left
- Right
- UpLeft
- UpRight

상태별 기준:

- Idle: 일정 간격으로 자연스럽게 방향을 바꾼다.
- Listening: 사용자의 말을 듣는 느낌이 우선이므로 Center로 안정적으로 둔다.
- Thinking: UpLeft/UpRight처럼 생각하는 방향을 주로 사용한다.
- Speaking: AI가 정면으로 말하는 인상을 유지하기 위해 Center로 안정적으로 둔다.
- Disabled/Error: Center에 가깝게 고정한다.

- 시선 방향은 eye offset으로 표현한다.
- 눈 깜빡임은 별도 표정 시스템이 아니라 눈 height scale을 아주 짧게 줄였다가 복귀시키는 생동감 보조 모션으로 제한한다.
- 방향 전환은 즉시 튀지 않고 부드럽게 이동한다.
- 너무 자주 움직이면 산만하므로 상태별 최소 유지 시간을 둔다.

### 3. Blink

눈 깜빡임은 캐릭터가 살아 있는 느낌을 주는 보조 모션이다. 상태 전달보다 앞서면 안 되며, 음성 레벨 반응이나 시선 방향을 방해하지 않아야 한다.

- Idle: 자연스러운 주기로 간헐적으로 깜빡인다.
- Listening: 사용자의 말을 듣는 느낌을 유지하기 위해 깜빡임 빈도를 낮추고, 음성 입력이 활발한 순간에는 과한 깜빡임을 피한다.
- Thinking: 생각하는 듯한 시선 전환과 함께 드물게 깜빡인다.
- Speaking: 말하는 리듬이 주 피드백이므로 깜빡임은 짧고 낮은 빈도로만 둔다.
- Disabled/Error: 거의 깜빡이지 않거나 매우 느리게 둔다.

- blink는 눈을 완전히 사라지게 하는 연출이 아니라, eye height를 짧게 줄였다가 복귀시키는 방식으로 표현한다.
- 깜빡임은 1회당 짧게 끝나야 하고, 연속 깜빡임은 기본 정책으로 사용하지 않는다.
- 상태 전환 직후 즉시 blink가 발생하면 시선 변화와 겹쳐 산만하므로 최소 지연 시간을 둔다.

### 4. Shadow Response

그림자는 오브 점프와 연결된다.

- 오브가 위로 떠오르면 그림자는 작아지고 alpha가 낮아진다.
- 오브가 내려오면 그림자는 넓어지고 alpha가 높아진다.
- shadow는 오브의 부유감을 보조하되, 너무 진해서 UI를 더럽게 보이게 하면 안 된다.

- shadow blur는 화면 반응성을 해치지 않는 범위에서 제한적으로 사용한다.

### 5. 상태별 모션 우선순위

상태마다 모든 motion을 같은 강도로 쓰면 캐릭터가 산만해지고 상태 전달력이 떨어진다. 각 상태의 주 피드백과 보조 피드백을 분리한다.

| 상태 | 주 피드백 | 보조 피드백 | 제한 |
| --- | --- | --- | --- |
| Idle | 느린 arc 회전, 낮은 breathing | 자연스러운 시선 전환, 간헐적 blink | 점프와 glow를 과하게 키우지 않는다 |
| Listening | inputLevel 기반 level meter, ARC glow | Center 고정 시선, 낮은 빈도 blink, Thinking 수준 floating | 사용자가 말하는 동안 시선과 jump가 너무 바쁘면 안 된다 |
| Thinking | 차분한 floating, UpLeft/UpRight 시선 | 낮은 glow, 드문 blink | Listening/Speaking과 비슷한 낮은 점프 강도를 유지한다 |
| Speaking | outputLevel 기반 level meter, ARC glow | Center 고정 시선, 짧은 blink, Thinking 수준 floating | AI 음성 반응보다 blink와 jump가 눈에 띄면 안 된다 |
| Disabled/Error | 낮은 alpha, 약한 또는 정지 motion | Center 고정, 거의 없는 blink | 재시도/오류 UI를 가리면 안 된다 |

---

## 구현 주의

인터랙션 캐릭터는 AI Chat 화면에 계속 표시되므로, 애니메이션 추가 시 성능 방어가 필요하다.

- 전체 화면의 불필요한 갱신을 만들지 않고 캐릭터 컴포넌트 내부 상태로 처리한다.
- Canvas draw layer 수를 과하게 늘리지 않는다.
- blur/glow는 필요한 곳에만 제한적으로 사용한다.
- 중앙 캐릭터는 motion과 visual feedback만 담당하고, 사용자 행동 안내 문구는 `ChatUiState.micStatusMessage` 기반 하단 텍스트만 사용한다.
- squash/stretch는 기존 orb body와 eye draw 크기만 조정하고, 추가 이미지나 새 draw layer를 만들지 않는다.
- blink는 기존 눈 draw 로직의 height scale만 조정해 표현하고, 별도 이미지/복잡한 path/추가 레이어를 만들지 않는다.
- glow/shadow 디자인을 보존하기 위해 blur 자체는 제거하지 않는다. 대신 `Paint`, `RectF`, `BlurMaskFilter`, 반복 배열처럼 draw마다 새로 만들 필요가 없는 객체를 컴포넌트 단위로 재사용한다.
- bounce/gaze/blink는 상태 변경을 key로 coroutine을 재시작하지 않는다. 장기 실행 루프가 최신 상태를 읽어 현재 위치와 타이밍을 최대한 유지해야 전환이 끊겨 보이지 않는다.
- 상태별 수치는 의미 없는 소수점 튜닝을 피하고 `0.05` 또는 `0.1` 단위 중심으로 관리한다.

---

## 책임 경계

- `ChatScreen`: 캐릭터 컴포넌트를 배치하고 자막/마이크 버튼 영역과 충돌하지 않게 한다.
- `VoiceInteractionCharacter`: 점프, 시선, 깜빡임, 그림자, animation 강도 조정을 담당한다.
- `ChatViewModel`: 기존처럼 대화 상태와 음성 레벨만 제공한다.

---

## 작업 방향

1. 오브 전체가 위아래로 움직이는 점프 모션을 추가한다.
2. Listening/Speaking은 음성 레벨 반응을 level meter와 ARC에 집중시키고, 오브 점프는 Thinking 수준으로 낮춘다.
3. Thinking은 Idle보다 살아 있지만 대화 중 상태와 비슷하게 차분한 floating으로 둔다.
4. 착지에 가까운 구간은 가로 squash, 상승 구간은 세로 stretch를 약하게 적용한다.
5. 점프 간격은 일정한 sine 반복처럼 보이지 않도록 contact, airborne, rest 구간을 분리하고 약한 간격 변화를 둔다.
6. 오브 점프에 맞춰 그림자의 크기와 투명도가 함께 변하도록 한다.
7. 눈은 Center, Left, Right, 대각 방향을 자연스럽게 바라볼 수 있게 한다.
8. 상태별 시선 방향은 과하게 산만하지 않도록 제한한다.
9. 눈 깜빡임은 상태별 최소 간격과 짧은 duration을 둬 생동감만 보조하게 한다.
10. 상태 변경 때 bounce/gaze/blink coroutine이 취소되어 snap되지 않도록, 장기 루프와 최신 상태 참조 방식으로 전환한다.
11. 중앙 캐릭터 내부 중복 상태 문구를 제거하고, 하단 안내 텍스트를 화면 바닥에서 적절히 띄워 읽기 쉽게 정돈한다.
12. Canvas draw에서 blur용 paint/filter/rect와 레벨미터 고정 배열을 재사용해 allocation 비용을 줄인다.
13. 점프/squash/stretch/시선/깜빡임/그림자 관련 수치는 코드에서 조정 지점을 쉽게 찾을 수 있게 주석을 남긴다.
14. `devDebug`에서 Idle, Listening, Thinking, Speaking, Disabled 상태를 수동 확인한다.
15. `:app:compileDevDebugKotlin`과 `:app:compileMockDebugKotlin`으로 컴파일을 확인한다.

---

## 검증 기준

- Idle 상태에서 캐릭터가 정지 이미지처럼 보이지 않는다.
- Listening 상태에서 사용자가 말하면 level meter와 ARC가 주로 반응하고, 오브는 Thinking 수준의 작은 floating만 유지한다.
- Speaking 상태에서 AI 음성 출력 중에도 level meter와 ARC가 주로 반응하고, 오브는 Thinking 수준의 작은 floating만 유지한다.
- 오브가 낮게 내려왔을 때 가로로 살짝 눌리고, 위로 튀어오를 때 세로로 살짝 길어진다.
- 점프가 너무 규칙적으로 반복되지 않고, 짧은 휴지와 높이 변화가 있어 자연스럽게 보인다.
- Thinking 상태는 Idle보다 살아 있고 Listening/Speaking과 비슷한 낮은 강도의 floating을 유지한다.
- Listening/Speaking 상태에서는 눈이 Center를 유지하고, Idle/Thinking에서는 Center/Left/Right/대각 방향을 바라보는 느낌을 준다.
- 눈 깜빡임이 자연스럽게 보이되, 상태 피드백과 음성 반응을 방해하지 않는다.
- 상태가 Idle/Listening/Thinking/Speaking 사이에서 바뀌어도 bounce/gaze/blink가 끊겨 재시작되는 느낌 없이 이어진다.
- 중앙 캐릭터 안에는 중복 상태 텍스트가 표시되지 않고, 하단 안내 텍스트만 현재 행동 안내를 담당한다.
- 하단 안내 텍스트가 화면 바닥에 붙지 않고 마이크 버튼 아래에서 읽기 쉬운 여백을 가진다.
- glow/shadow/level meter의 시각 효과는 유지되면서 draw 중 반복 객체 생성이 줄어든다.
- 오브가 떠오를 때 그림자가 작아지고 옅어진다.
- 오브가 내려올 때 그림자가 넓어지고 진해진다.
- 긴 자막이 표시되어도 캐릭터 모션이 자막 가독성을 해치지 않는다.
- 기존 대화 조작과 자막 표시 흐름에 회귀가 없다.
