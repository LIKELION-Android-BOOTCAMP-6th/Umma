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
- Firebase Live API 연결 설정 가능 상태
- 마이크 권한 확인 대상 상태

---

## 성공 조건

- 사용자가 push-to-talk 방식으로 AI와 대화할 수 있다.
- AI 응답 음성이 출력되고, 자막 On 상태에서는 확정된 마지막 턴 자막이 표시된다.
- 중앙 비주얼 피드백이 사용자 입력 중과 AI 음성 출력 중 상태를 구분해서 보여준다.
- 사용자가 자막을 On/Off 할 수 있고, 기본값은 Off다.
- 자막이 켜져 있을 때는 마지막 턴의 자막만 표시된다.
- 확정된 turn이 Session Memory에 반영된다.
- 사용자가 화면을 떠났다가 다시 들어와도 같은 언어의 앱 상태를 자연스럽게 이어갈 수 있다.
- 이후 correction / flashcard 흐름으로 넘어갈 준비가 된다.

---

# 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 예외 처리 | Flow 상세 페이지 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AI Chat 진입 | AI Chat 화면 진입 | selectedLearningLanguage와 LangState snapshot 확인 후 초기 상태 구성 | 대화 준비 상태 진입 | 상태 로드 실패 | Loading / Ready / Error | 재시도 가능한 오류 상태 표시 | CHAT-001 |
| 마이크 권한 확인 | 마이크 버튼 사용 준비 | 권한 상태 확인 및 PTT 버튼 활성/비활성 결정 | PTT 입력 가능 | 권한 거부 | Ready / PermissionRequired | 권한 안내 및 재요청 경로 제공 | CHAT-002 |
| PTT 음성 입력 | 마이크 버튼 press / release | press 중 음성 입력, release 시 user turn 종료 신호 전달 | user turn 입력 완료 | 입력 실패 | Recording / Error | 중복 입력 방지 및 재시도 | CHAT-003 |
| 중앙 비주얼 피드백 | 입력 또는 AI 응답 상태 관찰 | 중앙 이미지/아바타가 입력 강도와 AI Speaking 상태를 표시 | 현재 상태 파악 가능 | 상태 불일치 | Idle / Recording / Speaking | 상태 fallback 표시 | CHAT-004 |
| AI 응답 출력 | AI 응답 대기 | AI 음성 출력 및 응답 상태 반영 | AI 응답 재생 | 응답 실패 | Thinking / Speaking / Error | 재시도 가능한 상태 표시 | CHAT-005 |
| 마지막 턴 자막 | 자막 토글 조작 | On 상태에서 마지막 확정 턴 자막 표시 | 최신 턴 확인 | 자막 지연 | Subtitle Off / On | 마지막 확정 자막 유지 | CHAT-006 |
| 확정 turn 저장 | final transcript 수신 | final user/assistant turn을 Session Memory 저장 입력으로 전달 | recentFullContext 갱신 가능 | 저장 실패 | Saving / Ready | 재시도 가능한 저장 상태 | CHAT-007 |
| 종료 및 재진입 | 화면 이탈 또는 재진입 | 녹음/재생 정리 후 앱 상태 복원 또는 새 LiveSession 연결 | 자연스러운 재개 | 복구 실패 | Cleaning / Restoring / Ready | 새 LiveSession 전환 | CHAT-008 |

---

# GitHub Issue (실행 기준 / SSOT)

- [CHAT-001 AI Chat 진입 및 초기 상태](./FLOW_AI_CHAT/CHAT-001_Entry_State.md)
- [CHAT-002 마이크 권한 및 입력 준비](./FLOW_AI_CHAT/CHAT-002_Mic_Permission.md)
- [CHAT-003 PTT 음성 입력](./FLOW_AI_CHAT/CHAT-003_PTT_Input.md)
- [CHAT-004 중앙 비주얼 피드백](./FLOW_AI_CHAT/CHAT-004_Central_Visual.md)
- [CHAT-005 AI 응답 출력](./FLOW_AI_CHAT/CHAT-005_AI_Response.md)
- [CHAT-006 마지막 턴 자막](./FLOW_AI_CHAT/CHAT-006_Subtitle.md)
- [CHAT-007 확정 turn 저장 연동](./FLOW_AI_CHAT/CHAT-007_Turn_Commit.md)
- [CHAT-008 종료 및 재진입 복구](./FLOW_AI_CHAT/CHAT-008_Exit_Reentry.md)

---

# 이슈 분할 기준

- 각 이슈는 한 명의 작업자가 작은 PR로 완료할 수 있는 단위를 기준으로 나눈다.
- Realtime 연결, 스트림, turn append의 핵심 인프라는 `SYS-REALTIME-INFRA`의 `RT-001 ~ RT-004`를 따른다.
- User Flow 이슈는 화면 상태, 사용자 입력, UI 피드백, 인프라 계약 연결에 집중한다.
- mock 또는 fake 이벤트로 확인 가능한 화면 작업은 실제 Firebase Live API 완성을 기다리지 않고 진행할 수 있다.

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
- Central Voice State Visual
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
- 중앙 비주얼은 AI 음성 출력 상태에 맞춰 반응한다.

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

## 2-1. 중앙 상태 비주얼

### 역할

화면 중앙의 이미지 또는 아바타를 통해 현재 대화 상태를 즉시 보여준다.

### 정책

- `Recording` 상태에서는 사용자의 음성 입력 강도에 맞춰 파형, 볼륨 미터, 미세한 확장 애니메이션을 보여준다.
- 입력 강도는 `inputLevel: Float` 같은 presentation state로 전달하며, 값 범위는 0.0~1.0 기준으로 정규화한다.
- `Streaming` 또는 `Speaking` 상태에서는 AI 음성 출력 중임을 나타내는 별도 반응을 보여준다.
- `Loading` 상태에서는 세션 준비 중임을 보여준다.
- 이 비주얼은 장식이 아니라 입력/출력 상태 피드백이다.
- 자막 On/Off와는 독립적으로 동작한다.
- `Recording`은 AI 상태가 아니라 입력 UI 상태이며, `AIState`는 AI 응답 상태를 표현한다.

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

## 5. 저장 / 종료 / 복구 영역

### 역할

대화를 마치고 다음 학습 플로우로 이동할 수 있게 한다.

### 정책

- 확정 turn 저장은 `CHAT-007`과 `RT-003` 기준으로 처리한다.
- 종료 후 correction / flashcard flow로 이어질 수 있다.
- 화면 이탈 시에는 녹음/재생 정리와 저장 중 상태 보호를 우선한다.
- 복구 실패 시 새 LiveSession으로 전환한다.
