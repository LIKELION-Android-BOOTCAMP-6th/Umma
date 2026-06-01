# [Feature] CHAT-003 PTT 음성 입력

## User Story

사용자는 마이크 버튼을 누르는 동안 말하고, 버튼을 놓으면 현재 user turn을 종료할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] PTT 버튼 press 시 음성 입력이 시작된다.
- [ ] PTT 버튼 release 시 현재 user turn 종료 신호를 보낸다.
- [ ] 텍스트 입력 없이 음성 입력만 허용한다.
- [ ] 같은 turn에 대한 중복 입력 시작을 방지한다.
- [ ] 입력 실패 시 Error 또는 Retry 상태를 표시한다.
- [ ] 녹음 시작/중지 상태는 UI state로 관찰 가능해야 한다.
- [ ] 오디오 스트림 전송은 `RT-002` 계약을 따른다.

---

# Flow (링크)

- FLOW-AI-CHAT
- CHAT-002 → 마이크 권한 및 입력 준비
- CHAT-003 → PTT 음성 입력
- RT-002 → 음성 스트림

---

# 구현 범위

## 포함 범위

- PTT press / release 처리
- Recording 상태 표시
- 입력 중복 방지
- 입력 실패 상태
- user turn 종료 신호 전달

## 제외 범위 (Out of Scope)

- 중앙 비주얼 애니메이션 세부 구현
- AI 응답 음성 출력
- 자막 렌더링
- Session Memory append

---

# Details

## 입력 정책

- 사용자는 버튼을 누르는 동안만 말한다.
- 버튼을 놓으면 현재 user turn을 종료한다.
- 텍스트 입력창은 제공하지 않는다.
- press 없이 자동 녹음하지 않는다.

## 구현 가이드

```text
PTT press
→ permission 확인
→ start audio input
→ Recording 상태
→ PTT release
→ stop audio input
→ user turn 종료 신호
```

오디오 frame 전송, partial/final transcript 수신은 `RT-002`의 Realtime 인프라 계약을 사용한다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/chat/
→ ChatViewModel
→ PttInputState

domain/usecase/chat/
→ StartVoiceInputUseCase
→ StopVoiceInputUseCase
```

ViewModel은 press/release 이벤트를 상태로 조립하고, 오디오 캡처 세부 구현은 data/source 또는 realtime repository 뒤에 둔다.

---

## 현재 코드 전환 메모

- 현재 `ChatViewModel.startChatLoop()`는 세션 시작 후 바로 연속 녹음을 시작하므로 PTT press/release 제어로 바꾼다.
- press 시 녹음과 오디오 전송을 시작하고, release 시 현재 user turn 종료 신호를 만든다.
- `AudioRecorder`와 `AudioPlayer`의 16k PCM 기반 처리는 `RT-002`의 기초 구현으로 활용한다.
- `SendTextDataUseCase`와 `sendTextData()`는 AI Chat 사용자 화면에서 사용하지 않는다.
- 텍스트 입력 UI는 만들지 않고, 내부 연결 테스트용 코드가 남아 있더라도 User Flow에는 노출하지 않는다.

---

# 검증 기준

- press 시 Recording 상태가 된다.
- release 시 Recording 상태가 종료된다.
- 권한이 없으면 입력이 시작되지 않는다.
- 빠른 연속 press에도 중복 녹음이 시작되지 않는다.

---

# Edge Cases

- press 후 즉시 release
- 녹음 중 권한 철회
- 녹음 시작 실패
- 녹음 중 화면 이탈
- 같은 turn에서 press 이벤트가 중복 발생
