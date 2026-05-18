# [Feature] RT-004 재연결

## User Story

사용자는 네트워크 지연이나 Firebase Live API 중단이 있어도 대화 흐름이 크게 깨지지 않도록 보호받아야 한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 네트워크 단절 시 재연결이 시도된다.
- [ ] 재연결 실패 시 새 LiveSession 생성 정책으로 전환된다.
- [ ] 마이크 권한 거부 시 안내 UI가 제공된다.
- [ ] 응답 지연 시 마지막 자막 상태가 유지된다.
- [ ] 복구 불가능한 오류는 사용자에게 명확하게 안내된다.

---

# Flow (링크)

- SYS-REALTIME-INFRA
- RT-004 → 재연결

---

# 구현 범위

## 포함 범위

- reconnect 정책
- timeout handling
- microphone permission fallback
- latest confirmed subtitle fallback

## 제외 범위 (Out of Scope)

- 세션 데이터 영속 저장
- 교정/플래시카드 생성 로직
- Dashboard Summary 업데이트

---

# Details

## 재연결 정책

- 네트워크가 끊기면 재연결을 시도한다.
- 재연결에 실패하면 새 LiveSession을 시작한다.
- 응답 지연 중에는 마지막 자막 상태를 유지한다.

## 예외 처리

- 마이크 권한 거부는 별도 안내 UI로 처리한다.
- 연결 오류는 복구 가능한 상태로 보여준다.
- 재연결이 반복 실패해도 앱이 강제 종료되면 안 된다.

## 데모 확인 포인트

1. 네트워크 중단 상황에서 재연결이 시도되는지 확인
2. 재연결 실패 시 새 LiveSession으로 넘어가는지 확인
3. 지연 시 마지막 자막이 유지되는지 확인

## 권장 구현 경계

- `presentation/chat/ChatViewModel.kt`
- `data/repository/ChatRepositoryImpl.kt`
- `domain/usecase/chat/StartSessionUseCase.kt`
- `domain/usecase/chat/StopSessionUseCase.kt`

## 현재 테스트 코드 전환 메모

- `ChatRepositoryImpl.stopSession()`은 `LiveSession.close()` 이후 `session = null`까지 수행한다.
- `LiveServerGoAway`는 단순 종료로 숨기지 말고, ViewModel이 재연결/새 세션 전환 상태를 알 수 있는 이벤트로 전달한다.
- 재연결 중에도 UI는 마지막 확정 자막과 현재 오류/재시도 상태를 분리해서 보여준다.

## 한 줄 가이드

- 연결 실패는 앱 종료가 아니라 재시도/재연결 상태로 돌려보낸다.
