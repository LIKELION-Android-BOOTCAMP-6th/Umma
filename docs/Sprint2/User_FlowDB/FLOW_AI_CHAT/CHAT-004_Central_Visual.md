# [Feature] CHAT-004 중앙 비주얼 피드백

## User Story

사용자는 AI Chat 화면 중앙의 이미지 또는 아바타를 통해 현재 자신의 음성이 입력 중인지, AI가 말하는 중인지 즉시 알 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Loading 상태에서 세션 준비 중 비주얼을 표시한다.
- [ ] Recording 상태에서 사용자 음성 입력 강도 피드백을 표시한다.
- [ ] 입력 강도는 0.0~1.0 범위로 정규화된 `inputLevel` 기준으로 다룬다.
- [ ] AI Speaking 상태에서 AI 음성 출력 중임을 별도 시각 상태로 보여준다.
- [ ] Idle 상태와 Recording / Speaking 상태가 명확히 구분된다.
- [ ] 애니메이션이 화면 레이아웃을 밀거나 흔들지 않는다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-003 → PTT 음성 입력
- CHAT-004 → 중앙 비주얼 피드백
- RT-002 → 입력 강도와 AI 재생 상태 전달

---

# 구현 범위

## 포함 범위

- 중앙 이미지 또는 아바타 상태 표시
- 입력 강도 기반 볼륨/파형/확장 피드백
- AI Speaking 상태 표시
- Loading / Idle / Recording / Speaking 상태 구분

## 제외 범위 (Out of Scope)

- 오디오 캡처 구현
- AI 응답 생성
- 자막 표시
- turn 저장

---

# Details

## 비주얼 역할

중앙 비주얼은 장식이 아니라 사용자가 말하고 있는지, AI가 응답 중인지 알려주는 피드백이다.
자막이 꺼져 있어도 입력/출력 상태는 중앙 비주얼로 이해할 수 있어야 한다.
중앙 비주얼은 자막 On/Off와 독립적으로 동작하며, 입력/출력 상태만 반영한다.

## 상태 정책

- `Loading`: 세션 준비 중
- `Idle`: 입력 대기
- `Recording`: 사용자 음성 입력 중
- `Speaking`: AI 음성 출력 중
- `Error`: 입력 또는 연결 실패

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/components/
→ ChatCenterVisual

presentation/chat/
→ VoiceVisualState
```

## 상태 예시

```text
VoiceVisualState(
    mode = Idle | Loading | Recording | Speaking | Error,
    inputLevel = 0.0f
)
```

`Recording`은 사용자 입력 UI 상태이고, AI 응답 상태와 같은 의미로 사용하지 않는다.

---

## 현재 코드 전환 메모

- 입력 강도 피드백은 `AudioRecorder`에서 산출한 값을 0.0~1.0 범위로 정규화해 presentation state로 전달한다.
- `AIEvent.StateChanged`는 AI 응답 상태를 표현하고, 사용자 입력 중 상태인 `Recording`은 별도 UI 상태로 관리한다.
- AI 응답 재생 중에는 중앙 비주얼이 `Speaking` 상태로 전환되어야 한다.

---

# 검증 기준

- 입력 중 `inputLevel` 변화가 비주얼에 반영된다.
- AI 응답 재생 중 Recording과 다른 상태로 표시된다.
- 자막 Off 상태에서도 입력/출력 피드백이 보인다.
- 비주얼 상태 변화로 레이아웃이 흔들리지 않는다.

---

# Edge Cases

- 입력 레벨이 전달되지 않음
- 입력 레벨이 0.0 또는 1.0에 고정됨
- AI Speaking과 Recording 상태가 동시에 들어옴
- Error 이후 상태가 원복되지 않음
- 작은 화면에서 중앙 비주얼과 PTT 버튼이 겹침
