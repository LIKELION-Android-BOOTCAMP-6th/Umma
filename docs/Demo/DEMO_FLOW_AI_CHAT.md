# Demo Scenario — FLOW-AI-CHAT

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름의 TODO 시나리오.
> 기본은 Real 바인딩 (Firebase Live API + 실제 마이크). 권한/네트워크 실패 분기는 Mock 또는 OS 토글로 시연.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: AI Chat 카드까지 진입 가능한 시드 계정 (Initial Setup 완료 + `selectedLearningLanguage` 존재).
> - Mock 토글: `ChatRepository` / `RealtimeRepository` fake 구현 — fixture가 partial/final transcript와 `AIEvent.StateChanged`를 시뮬레이션할 수 있어야 함. (현재 미존재 — DASH 스타일 `RepositoryModule` 토글 추가 필요)
> - Mock fixture가 필요한 시나리오: 3·5·9·10. 그 외는 Real로 시연.
> - 발표 후 Real 바인딩으로 원복.

---

## 시나리오 1 — AI Chat 진입 (CHAT-001)

1. Dashboard에서 "최근 AI 대화" 카드 클릭
2. AI Chat 화면 진입 → Loading 노출 (세션 준비 / 앱 상태 복원 시도)
3. `selectedLearningLanguage` + `LangState` snapshot 로드 완료 → Ready 상태
4. 자막은 Off, 중앙 비주얼은 Idle, PTT 버튼은 마이크 권한 확인 대기 상태로 표시 (Success)
5. 진입 직후 자동 녹음이 시작되지 않는 것 확인

---

## 시나리오 2 — 마이크 권한 허용 (CHAT-002)

1. AI Chat 진입 → PTT 버튼 첫 탭
2. OS 마이크 권한 다이얼로그 노출 (Loading)
3. "허용" 선택 → PTT 버튼 활성화 (Success)
4. 권한 상태 변경 후 화면 재진입 시 권한 재요청 없이 곧바로 활성화 확인

---

## 시나리오 3 — PTT 음성 입력 + AI 응답 (CHAT-003 / CHAT-004 / CHAT-005)

> Real Firebase Live API 기반. 음성/응답 흐름이 흔들리면 Mock fixture로 partial → final 이벤트 재현.

1. PTT 버튼 press → 중앙 비주얼이 `Recording` 상태로 전환 (Recording)
2. 발화하면서 입력 강도(0.0~1.0)가 비주얼에 반영되는지 확인
3. PTT release → `Recording` 종료, AI 응답 대기 (`Thinking`)
4. AI 음성 출력 시작 → 중앙 비주얼이 `Speaking` 상태로 전환 (Speaking)
5. AI 음성 출력 종료 → `Ready` 복귀 (Success)
6. 같은 흐름을 2~3 턴 반복하며 turn 중첩 없이 깔끔하게 처리되는지 확인
7. 빠른 연속 press → 중복 녹음 시작되지 않음 (logcat에서 중복 차단 확인)

---

## 시나리오 4 — 자막 On/Off (CHAT-006)

1. 시나리오 3 종료 직후 자막 토글 Off 상태 → 자막 영역 비어있음 확인
2. 토글 On → 마지막 확정 턴 (user 또는 assistant) 1개만 표시 (Success)
3. 한 턴 더 진행 후 자막이 새로운 마지막 턴으로 교체되는지 확인 (과거 턴 누적되지 않음)
4. 토글 Off → 자막 숨김, 다시 On → 직전 마지막 자막 복원

---

## 시나리오 5 — 확정 turn 저장 (CHAT-007)

> Mock 토글 필요: `AppendTurnUseCase` 호출 여부를 logcat로 노출하거나, fake repo에서 `recentFullContext` snapshot을 화면 디버그 영역에 띄울 수 있어야 시연 가능.

1. PTT 입력으로 user turn 한 번 종료 → partial transcript는 저장되지 않고 final user turn만 append (logcat `append user`)
2. AI 응답 final transcript 도착 → assistant turn append (logcat `append assistant`)
3. 같은 세션 내 partial 이벤트가 turn 저장으로 흘러가지 않는 것 확인
4. 시나리오 종료 후 Correction 카드에서 `recentFullContext` 참조 가능한 상태인지 한 번 더 확인

---

## 시나리오 6 — 종료 및 재진입 복구 (CHAT-008)

1. AI Chat 화면에서 뒤로가기 → 녹음/AI 재생 즉시 중지 (logcat `cleanup`)
2. Dashboard로 복귀 후 다시 AI Chat 카드 클릭
3. 같은 선택 언어 기준으로 앱 상태 복원 시도 → 성공 시 직전 자막/turn 컨텍스트 유지 (Success)
4. 복원 실패 시뮬레이션 (네트워크 끊고 진입) → 새 LiveSession으로 자연스럽게 전환 (Fallback)

---

## 시나리오 7 — 마이크 권한 거부 (CHAT-002 Error)

1. AI Chat 진입 → PTT 버튼 첫 탭
2. OS 권한 다이얼로그에서 "거부" 선택
3. PTT 버튼 비활성화 + 권한 안내 UI 노출 (PermissionRequired)
4. 안내에서 재요청 경로 클릭 → 권한 팝업 또는 시스템 설정으로 이동 가능 확인
5. 권한 거부 상태에서도 앱이 크래시하지 않고 화면 자체는 유지됨

---

## 시나리오 8 — selectedLearningLanguage 없음 (CHAT-001 Edge)

> Mock 토글 필요: `FakeFixtures.noSelectedLang` (DASH와 동일한 명명 규칙으로 추가)

1. Dashboard에서 AI Chat 진입 시도
2. 선택 언어 없음 → Error 또는 안내 상태 표시 (Error)
3. 재시도 액션 노출 + Dashboard 복귀 경로 확인

---

## 시나리오 9 — AI 응답 실패 (CHAT-005 Error)

> Mock 토글 필요: fake realtime repo에서 `AIEvent.Error` 또는 timeout 강제 발화.

1. PTT 입력 → user turn 종료
2. AI 응답 대기 중 네트워크 끊김 또는 강제 실패 이벤트
3. `Error` 상태 표시 + 재시도 액션 노출 (Error + Retry)
4. 재시도 클릭 → 같은 turn 기준으로 응답 재요청, 정상 응답으로 복구

---

## 시나리오 10 — turn 저장 실패 (CHAT-007 Pending)

> Mock 토글 필요: `AppendTurnUseCase` 실패 모드.

1. PTT 입력 + AI 응답 정상 진행
2. final assistant turn 도착 시점에 저장 실패 발생
3. 화면은 크래시 없이 유지되고 turn은 local pending 상태로 남음 (logcat `pending`)
4. 자막과 중앙 비주얼은 정상 동작 유지
5. 화면 재진입 시 pending turn 재시도 또는 유지 확인 (CHAT-008과 연결)
