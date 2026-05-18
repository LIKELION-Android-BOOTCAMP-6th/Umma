# SYS-REALTIME-INFRA Overview

## 0. 개요

Realtime 인프라는 Umma에서 사용자가 Firebase AI Logic의 Gemini Live API를 통해 AI와 실제로 말하면서 대화하는 동안 필요한 기술적 기반을 담당한다.

이 문서는 다음 책임을 분리해서 정의한다.

- `SYS-REALTIME-INFRA`: 음성 스트림, 세션 상태, turn 확정, 재연결 정책
- `FLOW-AI-CHAT`: 사용자가 실제로 보는 화면 흐름과 상호작용
- `SYS-LEARNING-STATE-INFRA`: LangState, DashSummary, UserLangPref 같은 학습 상태 데이터

---

## 1. 핵심 원칙

### 1.1 음성 중심

- 텍스트 입력은 기본 제공하지 않는다.
- 사용자는 push-to-talk 방식으로 말한다.
- AI는 음성 응답과 자막을 함께 제공한다.

### 1.2 세션은 언어 단위로 묶는다

- Live 세션은 현재 선택된 학습 언어를 기준으로 관리한다.
- 화면에서 언어가 바뀌면 세션 컨텍스트도 함께 바뀐다.
- 같은 언어로 다시 들어오면 앱 상태는 복원할 수 있지만, Firebase Live API 세션 자체를 여러 연결로 이어 붙이는 방식은 사용하지 않는다.

### 1.3 turn 확정과 스트리밍을 분리한다

- 스트리밍 중의 부분 transcript는 임시 상태다.
- 확정된 turn만 Session Memory에 반영한다.
- 부분 transcript는 저장의 기준이 아니다.
- 화면 자막은 `FLOW-AI-CHAT` 정책에 따라 기본 Off이며, On 상태에서도 마지막 확정 턴만 보여준다.

---

## 2. 세션 라이프사이클

### 2.1 진입

1. 사용자가 AI Chat 화면에 진입한다.
2. selectedLearningLanguage를 읽는다.
3. `activeSessionId`가 있으면 앱 상태 복원을 시도한다.
4. Firebase Live API용 새 LiveSession을 연결한다.

### 2.2 대화 진행

1. 사용자가 마이크 버튼을 누른다.
2. 오디오 프레임이 Realtime 채널로 전송된다.
3. AI가 부분 응답을 스트리밍한다.
4. 부분 transcript는 임시 buffer로만 관리한다.
5. 최종 transcript가 확정되면 화면 자막과 turn 확정 후보로 전달한다.

### 2.3 turn 확정

1. user turn이 확정된다.
2. assistant turn이 확정된다.
3. Session Memory에 append 대상 turn이 정리된다.
4. recentFullContext 또는 동등한 turn buffer가 갱신된다.

### 2.4 종료 / 복구

1. 화면을 벗어나거나 연결이 끊어질 수 있다.
2. 복구 가능하면 앱 상태를 이어간다.
3. 복구 실패 시 새 LiveSession을 생성한다.

---

## 3. 데이터 계약

### 3.1 activeSessionId

`activeSessionId`는 현재 UI가 바라보는 살아 있는 대화 흐름의 앱 레벨 식별자다.

- 한 사용자가 동시에 여러 대화 흐름을 혼동하지 않도록 한다.
- Dashboard의 최근 대화 카드나 AI Chat 재진입 시 기준점이 된다.
- Firebase Live API 세션 식별자와 1:1로 영구 매핑하지 않고, 앱 상태 복원용 키로 쓴다.
- TTL 정책은 MVP에서 우선 제외하고, `updatedAt` 기반 관리만 둔다.

### 3.2 Session Memory

Session Memory는 대화 turn의 연속성을 유지하기 위한 버퍼다.

- 확정된 turn을 보관한다.
- 교정/플래시카드 생성의 입력이 된다.
- 원문 full context는 압축 후 정리될 수 있다.

### 3.3 LangState 연동

Realtime 인프라는 LangState를 직접 계산하지 않는다.

- LangState는 대화 흐름의 난이도 조절에 참고된다.
- 수치 갱신은 learning-state use case에서 수행한다.

---

## 4. 예외 정책

### 4.1 마이크 권한 없음

- 음성 입력을 시작하지 못한다.
- 권한 안내 UI를 보여준다.

### 4.2 네트워크 끊김

- 현재 turn을 안전하게 마무리하지 못하면 재연결을 시도한다.
- 재연결 실패 시 새 LiveSession을 시작한다.

### 4.3 Realtime 응답 지연

- 화면 자막은 마지막 확정 턴 상태를 유지한다.
- 사용자에게는 로딩 또는 재시도 상태를 표시한다.

---

## 5. 구현 메모

- raw audio blob은 장기 저장의 대상이 아니다.
- 저장 단위는 turn과 summary 이벤트가 기본이다.
- Session Memory 원문 turn list는 LS-005 정책에 따라 Room에 먼저 append하고, Firestore에는 batch sync한다.
- Firebase Live API의 세션 자체는 짧은 연결 단위로 보고, 장기 기억은 우리 앱의 Session Memory가 맡는다.
- `recentFullContext`는 AI Chat과 correction flow가 함께 참고하는 세션 맥락이다.

---

## 6. 기존 AI 대화 테스트 코드 전환 방향

팀장이 미리 준비한 AI 대화 테스트 코드는 Firebase Live API 연동의 출발점으로 활용한다. 다만 현재 코드는 연결 테스트 성격이 강하므로, `SYS-REALTIME-INFRA`와 `FLOW-AI-CHAT` 작업에서는 아래 방향으로 정리한다.

### 6.1 유지할 부분

- `di/AIModule.kt`: Firebase AI Logic / Gemini Live API 연결 설정은 유지한다.
- `data/repository/ChatRepositoryImpl.kt`: `LiveSession` 연결, 오디오 전송, 서버 이벤트 수신 구조는 유지한다.
- `data/source/local/AudioRecorder.kt`, `AudioPlayer.kt`: 16k PCM 기반 녹음/재생 처리는 RT-002의 기초 구현으로 활용한다.
- `domain/usecase/chat/*`: ViewModel이 Repository를 직접 호출하지 않도록 하는 유스케이스 경계는 유지한다.

### 6.2 수정할 부분

- `ChatViewModel.startChatLoop()`의 자동 연속 녹음은 push-to-talk 방식으로 바꾼다. 화면 진입은 세션 준비만 하고, 실제 녹음은 마이크 press/release에 맞춰 시작/종료한다.
- `ChatRepositoryImpl.stopSession()`은 `LiveSession.close()` 이후 `session = null`까지 정리해 중복 연결과 재사용 오해를 막는다.
- `LiveServerGoAway`는 단순 종료가 아니라 재연결/복구 상태 이벤트로 전달한다. 복구 실패 시 새 `LiveSession`으로 전환한다.
- `AIEvent.TextResponse`는 부분 transcript와 최종 transcript, user/assistant 구분을 표현할 수 있도록 확장한다. UI는 최종 transcript 기준의 마지막 턴만 표시한다.
- `sendTextData()`와 텍스트 입력 유스케이스는 사용자 플로우에서는 제외한다. 내부 테스트용으로 남길 수는 있지만, AI Chat 화면에는 텍스트 입력 UI를 만들지 않는다.
- `AIModule.kt`의 하드코딩된 영어 튜터 프롬프트는 selectedLearningLanguage, LangState, Session Memory를 조합하는 prompt builder 또는 use case로 옮긴다.
- `ChatScreen.kt`의 placeholder는 실제 AI Chat 화면으로 교체한다. 최소 구성은 push-to-talk 버튼, 자막 On/Off 토글, 마지막 턴 자막 영역, 연결/권한/오류 상태다.

### 6.3 클린 아키텍처 주의점

- Firebase Live API 의존성은 data/di 경계에 둔다.
- Presentation은 `LiveSession`을 직접 알지 않고, ViewModel state와 use case만 바라본다.
- ViewModel이 audio source를 직접 다루는 현재 구조는 테스트 구현으로 보고, 최종 구조에서는 음성 입력/출력 제어를 use case 또는 presentation-safe controller로 감싼다.
- Realtime 인프라는 LangState 수치를 직접 계산하지 않는다. 대화 완료 후 확정 turn만 learning-state use case에 넘긴다.
