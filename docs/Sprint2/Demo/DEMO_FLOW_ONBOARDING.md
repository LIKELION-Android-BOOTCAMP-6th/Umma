# Demo Scenario — FLOW-ONBOARDING

> Auth는 Real Firebase 환경. 데모용 Google 테스트 계정 2개 준비.
> - 계정 A (신규): Firestore `users/{uid}` 없음 → Initial Setup 트리거
> - 계정 B (기존): Firestore 시드 완료 → 자동 로그인 직행

---

## 시나리오 1 — 신규 로그인 + Initial Setup (AUTH-001 / AUTH-004)

1. 앱 실행 → Splash → Onboarding 화면 노출
2. Google 로그인 버튼 클릭 → 계정 A 선택 (Loading)
3. Firebase 인증 완료 → Dashboard 진입
4. Initial Setup Dialog 자동 표시
5. 닉네임 "데모유저" 입력
6. 주 학습 언어 = English 선택
7. 완료 클릭
8. Dialog 닫힘 + Dashboard Empty 상태 진입 (Success)

> 이번 스프린트 범위 밖: 모국어 선택(AC3), 관심 주제 5개 선택(AC5), 저장 중 Progress Indicator, 5개 미만 비활성화 — 다음 스프린트에서 보강 예정.

---

## 시나리오 2 — 자동 로그인 (AUTH-002)

1. 계정 B로 로그인된 상태에서 앱을 한번 종료 (swipe-out)
2. 앱 재실행 → Splash 노출 (Loading)
3. Firebase `currentUser != null` 확인 → Dashboard 자동 이동 (Success)

---

## 시나리오 3 — 비로그인 진입 (AUTH-002)

1. 로그아웃 또는 신규 디바이스 상태에서 앱 실행
2. Splash 노출 (Loading)
3. Firebase 세션 없음 확인 → Onboarding 화면 이동 (Success)

---

## 시나리오 4 — 로그아웃 (AUTH-003)

1. Dashboard에서 설정 화면 진입
2. 로그아웃 버튼 클릭 (Loading)
3. Onboarding 화면 자동 이동 (Success)
4. 디바이스 뒤로가기 → 이전 Dashboard로 복귀되지 않음 (BackStack 초기화 확인)

---

## 시나리오 5 — 로그인 실패 (AUTH-001 Error)

1. 네트워크 끊기 (또는 Google 계정 선택 취소)
2. Google 로그인 버튼 클릭
3. 에러 메시지 + 재시도 가능 확인 (Error + Retry)
