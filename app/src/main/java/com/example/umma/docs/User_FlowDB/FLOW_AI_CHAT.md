# FLOW-AI-CHAT

## 목표

- 사용자가 AI와 실제로 한 번 이상 말하며 대화를 이어갈 수 있다.
- 사용자는 음성 기반 대화 중 자막을 확인하고, 필요 시 앱 상태를 복원해 대화를 다시 이어갈 수 있다.
- 사용자는 자막을 On/Off 할 수 있고, 기본값은 Off이며, 켰을 때는 마지막 턴 자막만 표시된다.
- 대화 내용은 세션 메모리로 저장된다.

---

## 시작 조건

- 사용자가 로그인된 상태
- Initial Setup 완료 상태
- selectedLearningLanguage 존재
- LangState preload 완료 상태
- Firebase Live API 세션 준비 완료 상태
- 마이크 권한 허용 상태

---

## 성공 조건

- 사용자가 push-to-talk 방식으로 AI와 대화할 수 있다.
- AI 응답 음성이 출력되고, 자막 On 상태에서는 확정된 마지막 턴 자막이 표시된다.
- 사용자가 자막을 On/Off 할 수 있고, 기본값은 Off다.
- 자막이 켜져 있을 때는 마지막 턴의 자막만 표시된다.
- 확정된 turn이 Session Memory에 반영된다.
- 사용자가 화면을 떠났다가 다시 들어와도 같은 언어의 앱 상태를 자연스럽게 이어갈 수 있다.
- 이후 correction / flashcard 흐름으로 넘어갈 준비가 된다.

---

# 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 예외 처리 | Flow 상세 페이지 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AI Chat 진입 | AI Chat 화면 진입 | selectedLearningLanguage와 activeSessionId 확인 후 앱 상태 복원 및 새 LiveSession 연결 | 이전 상태 또는 새 LiveSession 준비 | 세션 준비 실패 | Loading / Content | 세션 없음 시 새 LiveSession 연결 | CHAT-001 |
| 음성 대화 시작 | 마이크 버튼 누르기 | 오디오 스트림 전송 시작 | user turn 수집 | 마이크 권한 실패 | Recording / Streaming | 권한 없음 안내 | CHAT-002 |
| 자막 확인 | AI 응답을 보며 대화 | 확정된 최신 턴 자막 및 자막 표시 상태 반영 | 대화 맥락 파악 가능 | 자막 지연 | Streaming / Content | 마지막 자막 유지 | CHAT-003 |
| 대화 종료 | 버튼 해제 또는 화면 이탈 | 확정 turn commit 및 Session Memory 반영 | recentFullContext 갱신 | 저장 실패 | Saving / Content | 재시도 또는 복구 | CHAT-004 |

---

# GitHub Issue (실행 기준 / SSOT)

- CHAT-001 AI Chat 진입
- CHAT-002 음성 전송
- CHAT-003 자막 표시
- CHAT-004 종료 및 복구

---

# 데모 시나리오

1. Dashboard에서 AI 대화 카드 클릭
2. AI Chat 화면 진입
3. 마이크 버튼을 누르고 음성 입력
4. 자막을 켰을 때 확정된 마지막 턴 자막만 표시되는지 확인
5. 버튼을 놓아 turn 종료
6. 대화를 몇 차례 이어가기
7. 화면을 나갔다가 다시 진입
8. 같은 언어의 앱 상태가 자연스럽게 복원되는지 확인

---

# 디자인 (필요 시)

- AI Chat Screen
- Subtitle Overlay
- Push-to-Talk Button
- Session Restore State
- Error / Permission State

---

# 핵심 상태

## Loading

- 앱 상태 복원 또는 LiveSession 생성 중
- 마이크 권한 확인 중

## Recording

- 사용자가 말하는 동안
- 오디오 입력이 전송되는 상태

## Streaming

- AI가 응답을 생성 중
- Realtime 레이어에서는 부분 응답을 받을 수 있다.
- 화면 자막은 확정된 마지막 턴 기준으로 유지하거나 갱신한다.

## Saving

- 확정 turn을 Session Memory에 반영 중

---

# AI Chat 카드 구성

## 1. 진입 영역

### 역할

Dashboard 또는 다른 경로에서 들어온 사용자를 현재 세션으로 자연스럽게 연결한다.

### 사용 데이터

- selectedLearningLanguage
- activeSessionId
- LangState
- Session Memory

---

## 2. 음성 입력 영역

### 역할

사용자의 발화를 수집한다.

### 정책

- 텍스트 입력창 없음
- push-to-talk만 허용

---

## 3. 자막 영역

### 역할

AI 응답과 사용자의 마지막 발화를 보여준다.

### 정책

- 기본값은 Off다.
- 표시 상태가 On일 때는 마지막 턴만 보여준다.
- 부분 응답을 계속 누적 표시하지 않고, 확정된 마지막 턴 자막만 보여준다.
- 최종 응답 확정 후 turn commit

## 4. 자막 제어 영역

### 역할

사용자가 자막을 보고 싶을 때만 켜고, 필요하지 않으면 숨길 수 있다.

### 정책

- 자막 On/Off는 표시 상태만 바꾼다.
- 세션 저장이나 turn 확정 여부는 바꾸지 않는다.
- 마지막 자막 상태는 다시 켰을 때 복원될 수 있다.
- On 상태일 때 보여주는 자막은 항상 최신 마지막 턴이다.

---

## 5. 종료 / 저장 / 복구 영역

### 역할

대화를 마치고 다음 학습 플로우로 이동할 수 있게 한다.

### 정책

- 종료 후 correction / flashcard flow로 이어질 수 있다.
- 종료 시 Session Memory 저장을 시도한다.
- 복구 실패 시 새 LiveSession으로 전환한다.
