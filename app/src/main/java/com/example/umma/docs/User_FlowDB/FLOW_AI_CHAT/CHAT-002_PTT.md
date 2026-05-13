# [Feature] CHAT-002 음성 전송

## User Story

사용자는 마이크 버튼을 누르고 말하면서 Firebase Live API 기반 AI와 대화를 이어갈 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 마이크 버튼을 누르면 음성 입력이 시작된다.
- [ ] 버튼을 놓으면 현재 turn이 종료된다.
- [ ] 텍스트 입력 없이 대화가 진행된다.
- [ ] 음성 입력 실패 시 사용자에게 상태가 안내된다.
- [ ] 중복 송신이 방지된다.
- [ ] 입력 중 중앙 비주얼이 음성 강도에 따라 반응한다.
- [ ] AI 음성 출력 중 중앙 비주얼이 재생 상태를 보여준다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-002 → 음성 전송

---

# 구현 범위

## 포함 범위

- 음성 입력 UI
- press / release 처리
- turn 종료 신호
- 입력 중 상태 표시
- 중앙 비주얼 상태 피드백

## 제외 범위 (Out of Scope)

- 자막 렌더링
- turn 저장
- Session Memory 압축

---

# Details

## 입력 방식

- 사용자는 마이크 버튼을 누르는 동안 말한다.
- 버튼을 놓으면 현재 turn이 종료된다.
- 텍스트 입력창은 제공하지 않는다.

## 상태 정책

- 입력 중에는 Recording 상태를 보여준다.
- 전송 중에는 Uploading 또는 Streaming 상태를 보여준다.
- 실패 시 Error 상태를 보여준다.
- Recording 상태에서는 사용자의 입력 강도에 맞춰 파형 또는 볼륨 미터를 반응시킨다.
- AI 음성 출력 상태에서는 중앙 비주얼이 재생 중임을 보여준다.
- `Recording`은 입력 UI 상태이며 `AIState`와 같은 의미가 아니다.

## 예외 처리

- 마이크 권한이 없으면 권한 요청 또는 안내를 보여준다.
- 음성 전송 실패 시 재시도가 가능해야 한다.
- 같은 turn에 대한 중복 송신을 막아야 한다.

## 데모 확인 포인트

1. 마이크 버튼 press 시 입력이 시작되는지 확인
2. release 시 turn이 종료되는지 확인
3. 음성이 Realtime 연결로 전달되는지 확인
4. 입력 강도에 따라 시각 피드백이 반응하는지 확인
5. AI 음성 출력 시 중앙 비주얼이 재생 상태를 보여주는지 확인

## 권장 구현 경계

- `data/source/local/AudioRecorder.kt`
- `data/source/local/AudioPlayer.kt`
- `presentation/chat/ChatViewModel.kt`
- `domain/usecase/chat/SendAudioDataUseCase.kt`
- `data/repository/ChatRepositoryImpl.kt`

## 현재 테스트 코드 전환 메모

- 현재 `ChatViewModel`의 자동 연속 녹음 흐름은 press/release 기반으로 분리한다.
- press 시 녹음과 오디오 전송을 시작하고, release 시 현재 user turn 종료 신호를 만든다.
- `SendTextDataUseCase`는 AI Chat 사용자 화면에서 사용하지 않는다.
- `AudioRecorder.kt`에서 권한 실패와 녹음 시작 실패를 UI 상태로 전달할 수 있어야 한다.
- 입력 중 실시간 레벨 값 또는 파형 값을 presentation으로 전달할 수 있어야 한다.
- AI 응답 재생 중에는 중앙 비주얼이 별도 speaking 상태로 전환되어야 한다.

## 한 줄 가이드

- 음성 캡처와 전송은 분리하고, ViewModel은 시작/중지 상태만 조립한다.
