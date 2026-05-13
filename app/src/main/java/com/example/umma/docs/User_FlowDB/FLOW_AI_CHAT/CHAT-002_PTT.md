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

## 예외 처리

- 마이크 권한이 없으면 권한 요청 또는 안내를 보여준다.
- 음성 전송 실패 시 재시도가 가능해야 한다.
- 같은 turn에 대한 중복 송신을 막아야 한다.

## 데모 확인 포인트

1. 마이크 버튼 press 시 입력이 시작되는지 확인
2. release 시 turn이 종료되는지 확인
3. 음성이 Realtime 연결로 전달되는지 확인

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

## 한 줄 가이드

- 음성 캡처와 전송은 분리하고, ViewModel은 시작/중지 상태만 조립한다.
