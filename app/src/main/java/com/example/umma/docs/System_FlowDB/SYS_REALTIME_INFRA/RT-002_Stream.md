# [Feature] RT-002 음성 스트림

## User Story

사용자는 마이크 버튼을 누르고 말한 뒤 AI의 음성 응답을 들으며 자연스럽게 대화를 이어갈 수 있고,
자막을 켠 경우에는 확정된 마지막 턴 자막만 확인할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 마이크 버튼을 누르는 동안 음성 입력이 수집된다.
- [ ] 사용자가 버튼을 놓으면 turn이 종료된다.
- [ ] AI 응답 음성과 transcript 이벤트가 스트리밍 방식으로 수신된다.
- [ ] 부분 transcript와 최종 transcript가 구분된다.
- [ ] UI 자막은 최종 transcript 기준의 마지막 턴만 표시하도록 전달된다.
- [ ] 대화 중 네트워크 지연이 발생해도 UI가 즉시 깨지지 않는다.
- [ ] 음성 입력 실패 시 사용자에게 상태가 안내된다.
- [ ] 텍스트 입력 UI 없이도 대화 흐름이 완결된다.

---

# Flow (링크)

- SYS-REALTIME-INFRA
- RT-002 → 음성 스트림

---

# 구현 범위

## 포함 범위

- 마이크 입력 수집
- 오디오 frame 전송
- 부분 transcript 임시 수신
- 최종 transcript 확정 이벤트 전달
- 스트림 중단 / 재개 처리

## 제외 범위 (Out of Scope)

- Session Memory 저장
- 교정 문장 선정
- 플래시카드 생성
- LangState 업데이트

---

# Details

## transcript 정책

- Realtime 계층은 부분 transcript를 받을 수 있지만, 저장 기준으로 사용하지 않는다.
- 최종 transcript가 도착하면 해당 user turn 또는 assistant turn을 확정 후보로 본다.
- UI 자막은 `CHAT-003` 정책을 따른다. 즉, 자막 On 상태에서도 마지막 확정 턴만 보여준다.

---

## 중복 입력 방지

- 마이크 버튼을 누르는 동안만 입력 허용
- 동일 turn에 대해 중복 송신 금지

## 데모 확인 포인트

1. 마이크 press 시 입력이 시작되는지 확인
2. release 시 turn 종료가 되는지 확인
3. AI 음성이 수신되는지 확인
4. 자막 On 상태에서 마지막 확정 턴만 표시되는지 확인

## 권장 구현 경계

- `data/source/local/AudioRecorder.kt`
- `data/source/local/AudioPlayer.kt`
- `presentation/chat/ChatViewModel.kt`
- `domain/usecase/chat/SendAudioDataUseCase.kt`
- `domain/model/AIEvent.kt`
- `data/repository/ChatRepositoryImpl.kt`

## 현재 테스트 코드 전환 메모

- `AudioRecorder.kt`와 `AudioPlayer.kt`는 16k PCM 음성 입출력 기반으로 활용한다.
- 현재 `ChatViewModel.startChatLoop()`는 세션 시작 후 바로 연속 녹음을 시작하므로, push-to-talk press/release 제어로 바꾼다.
- `sendTextData()`는 사용자 플로우에서 제외한다. 내부 연결 테스트용으로 남기더라도 AI Chat 화면에는 텍스트 입력을 두지 않는다.
- `AIEvent.TextResponse`는 부분/최종 transcript와 user/assistant 구분을 표현할 수 있도록 확장한다.

## 한 줄 가이드

- 오디오는 스트림 단위로 보내고, 부분 transcript는 임시 이벤트로만 다루며, 화면에는 마지막 확정 턴만 넘긴다.
