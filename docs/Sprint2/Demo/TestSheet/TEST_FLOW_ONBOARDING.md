# Test Sheet — FLOW-ONBOARDING

> 데모 시나리오: [`docs/Sprint2/Demo/DEMO_FLOW_ONBOARDING.md`](../DEMO_FLOW_ONBOARDING.md)
> 사용법: 각 행의 "수행 절차"대로 실행 후 "기대 결과" 만족 여부를 **Pass / Fail** 표기. 실패 시 비고에 1~2줄 재현 메모 + GitHub Issue 링크.
>
> **사전 준비 (Real Firebase 환경)**:
> - 계정 A (신규): Firestore `users/{uid}` 미생성 상태인 Google 테스트 계정. Initial Setup 트리거 검증용.
> - 계정 B (기존): Firestore 시드 완료 (`users/{uid}` + `user_learning_preference/current` 존재). 자동 로그인 / 로그아웃 후 잔존 확인용.
> - 시나리오 §5(에러)는 네트워크 차단(기내 모드 등) 필요.

---

## 커버되는 AC 체크리스트

> 아래 표의 각 행이 어느 AC를 검증하는지 한눈에 보기 위한 목록.
> `[x]` = 본 테스트 시트에 포함됨 / `[ ]` = 본 시트에 없음(스코프 밖 또는 추후 보강).

### AUTH-001 Google 로그인 (10개)
- [x] AC1: Google 로그인 버튼이 표시된다.
- [x] AC2: Google 계정 선택창이 정상 호출된다.
- [x] AC3: Google 인증 성공 시 Firebase 인증이 완료된다.
- [x] AC4: 로그인 성공 시 Firebase User 정보를 획득할 수 있다.
- [ ] AC5: 앱 재실행 시 Firebase 세션이 유지된다. (※ AUTH-002 AC7로 동등 검증 — TC-ON-19에서 확인)
- [x] AC6: 로그인 성공 후 Dashboard 화면으로 이동한다.
- [x] AC7: 최초 사용자일 경우 Initial Setup Dialog가 표시된다.
- [ ] AC8: Initial Setup 완료 후 사용자 프로필과 학습 상태 초기값 저장은 AUTH-004 기준으로 위임된다. (※ AUTH-004 AC6~12로 위임)
- [x] AC9: 로그인 진행 중 중복 요청이 방지된다.
- [x] AC10: 인증 실패 시 에러 메시지를 표시한다.

### AUTH-002 자동 로그인 (10개)
- [x] AC1: 앱 실행 시 Splash 화면이 표시된다.
- [x] AC2: Firebase 현재 로그인 상태를 확인할 수 있다.
- [x] AC3: 로그인된 사용자일 경우 Dashboard 화면으로 자동 이동한다.
- [x] AC4: 로그인되지 않은 사용자일 경우 Onboarding 화면으로 이동한다.
- [x] AC5: 세션 확인 중 Loading 상태가 표시된다.
- [x] AC6: 세션 확인 완료 전까지 화면 전환이 발생하지 않는다.
- [x] AC7: 자동 로그인 상태에서 앱 재실행 시 세션이 유지된다.
- [ ] AC8: 유효하지 않은 세션은 자동 제거된다. (※ 토큰 만료/손상 fixture 필요 — 추후 보강)
- [ ] AC9: 세션 확인 실패 시 재시도 가능한 에러 상태를 표시한다. (※ 추후 보강)
- [ ] AC10: 세션 확인 로직은 Dashboard 데이터 preload 또는 학습 언어 preload를 수행하지 않는다. (※ logcat 검증 별도 필요 — 추후 보강)

### AUTH-003 로그아웃 (11개)
- [x] AC1: 설정 화면에서 로그아웃 버튼이 표시된다.
- [x] AC2: 로그아웃 버튼 클릭 시 로그아웃 요청이 수행된다.
- [x] AC3: Firebase 세션이 제거된다.
- [x] AC4: Google 로그인 세션이 함께 해제된다.
- [x] AC5: 로그아웃 완료 후 Onboarding 화면으로 이동한다.
- [x] AC6: 로그아웃 이후 자동 로그인되지 않는다.
- [x] AC7: 로그아웃 진행 중 중복 요청이 방지된다.
- [ ] AC8: 로그아웃 실패 시 에러 메시지를 표시한다. (※ 추후 보강)
- [x] AC9: 로그아웃 후 전역 사용자 상태(Global State)가 초기화된다.
- [x] AC10: 로그아웃 후 UserLangPref와 selectedLearningLanguage가 현재 앱 세션에서 제거된다.
- [x] AC11: 로그아웃 후 BackStack이 초기화되어 이전 사용자 화면으로 복귀할 수 없다.

### AUTH-004 Initial Setup (16개)
- [x] AC1: 최초 로그인 사용자에게 Initial Setup Dialog가 표시된다. (※ AUTH-001 AC7와 동일 — TC-ON-07에서 검증)
- [x] AC2: 사용자가 닉네임을 입력할 수 있다.
- [ ] AC3: 사용자가 모국어를 선택할 수 있다. (※ 이번 스프린트 스코프 밖 — DEMO_FLOW_ONBOARDING.md 시나리오 1 주석 참조)
- [x] AC4: 사용자가 주 학습 언어를 선택할 수 있다.
- [ ] AC5: 사용자가 관심 대화 주제 5개를 선택할 수 있다. (※ 이번 스프린트 스코프 밖)
- [x] AC6: 설정 완료 시 사용자 프로필이 Firebase에 저장된다.
- [x] AC7: 설정 완료 시 UserLangPref가 Firebase에 저장된다.
- [x] AC8: selectedLearningLanguage가 primaryLearningLanguage와 같은 값으로 초기화된다.
- [x] AC9: 주 학습 언어 기준 Language State 기본값이 생성된다.
- [x] AC10: 주 학습 언어 기준 Dashboard Summary 기본값이 생성된다.
- [x] AC11: 주 학습 언어 기준 Session Summary 기본값이 생성된다.
- [x] AC12: 주 학습 언어 기준 Flashcard Summary 기본값이 생성된다.
- [x] AC13: Initial Setup 완료 후 Dialog가 닫힌다.
- [x] AC14: 설정 완료 이후 다시 Dialog가 표시되지 않는다.
- [ ] AC15: 입력값 검증이 수행된다. (※ 추후 보강 — 닉네임 공백 등)
- [ ] AC16: 저장 실패 시 재시도 가능하다. (※ 추후 보강)

### DASH-001 신규 사용자 Empty (참고)
- [x] AC8: 신규 사용자는 Empty Dashboard UI가 출력된다. (※ TC-ON-16으로 진입만 확인 — 상세 동작은 TEST_FLOW_DASHBOARD §3 참조)

---

## 테스트 시트

> 사전 조건이 같은 행끼리 인접하게 묶고, 위에서 아래로 갈수록 setup이 복잡해지는 순서로 배치.
> 진행 순서: ①클린 상태 → ②Onboarding 진입 → ③인증 진행 중 → ④신규 Dialog 입력/저장 → ⑤Firestore 저장 검증 → ⑥완료 직후 후속 동작 → ⑦계정 B 자동 로그인 → ⑧로그아웃 → ⑨에러/재시도

| Test ID | 시나리오 | 연결 AC | 사전 조건 | 수행 절차 | 기대 결과 | Pass/Fail | 비고 |
|---|---|---|---|---|---|---|---|
| TC-ON-01 | §1 | AUTH-001 AC1<br/>AC1: Google 로그인 버튼이 표시된다. | 계정 A, 로그아웃 상태 | 앱 실행 | Onboarding 화면 + Google 로그인 버튼 노출 | | |
| TC-ON-02 | §3 | AUTH-002 AC1·2<br/>AC1: 앱 실행 시 Splash 화면이 표시된다.<br/>AC2: Firebase 현재 로그인 상태를 확인할 수 있다. | 로그아웃 상태 또는 신규 디바이스/앱 데이터 초기화 상태 | 앱 실행 | Splash 노출 (Loading) | | |
| TC-ON-03 | §3 | AUTH-002 AC4<br/>AC4: 로그인되지 않은 사용자일 경우 Onboarding 화면으로 이동한다. | 로그아웃 상태, Splash 노출 중 | 세션 없음 확인 완료 대기 | Onboarding 화면 이동 (Success) | | |
| TC-ON-04 | §1 | AUTH-001 AC2<br/>AC2: Google 계정 선택창이 정상 호출된다. | 계정 A, 로그아웃 상태, Onboarding 화면 진입 | Google 로그인 버튼 클릭 | Google 계정 선택창 노출 | | |
| TC-ON-05 | §1 | AUTH-001 AC9<br/>AC9: 로그인 진행 중 중복 요청이 방지된다. | 계정 A 선택 후 인증 진행 중 | 로그인 버튼 재클릭 | 중복 인증 시도 차단 (Loading) | | |
| TC-ON-06 | §1 | AUTH-001 AC3·4<br/>AC3: Google 인증 성공 시 Firebase 인증이 완료된다.<br/>AC4: 로그인 성공 시 Firebase User 정보를 획득할 수 있다. | 계정 A 선택 후 인증 진행 중 | 인증 완료 대기 | Firebase User 정보 획득 | | |
| TC-ON-07 | §1 | AUTH-001 AC6·7<br/>AC6: 로그인 성공 후 Dashboard 화면으로 이동한다.<br/>AC7: 최초 사용자일 경우 Initial Setup Dialog가 표시된다. | 계정 A, 인증 완료, Firestore `users/{uid}` 미생성 상태 | Dashboard 진입 직후 화면 관찰 | Initial Setup Dialog 자동 표시 | | |
| TC-ON-08 | §1 | AUTH-004 AC14<br/>AC14: 설정 완료 이후 다시 Dialog가 표시되지 않는다. (dismiss 정책) | Initial Setup Dialog 표시 상태 | Dialog 외부 영역 탭 | dismiss되지 않음 | | |
| TC-ON-09 | §1 | AUTH-004 AC2<br/>AC2: 사용자가 닉네임을 입력할 수 있다. | Initial Setup Dialog 표시 상태 | 닉네임 입력란에 "데모유저" 입력 | 입력값이 그대로 반영 | | |
| TC-ON-10 | §1 | AUTH-004 AC4<br/>AC4: 사용자가 주 학습 언어를 선택할 수 있다. | Initial Setup Dialog 표시, 닉네임 입력 완료 | 주 학습 언어 = English 선택 | 선택값 반영 | | |
| TC-ON-11 | §1 | AUTH-004 AC13<br/>AC13: Initial Setup 완료 후 Dialog가 닫힌다. | Initial Setup Dialog 표시, 닉네임/학습 언어 입력 완료 | 완료 버튼 클릭 | Dialog 닫힘 (Success) | | |
| TC-ON-12 | §1 | AUTH-004 AC6<br/>AC6: 설정 완료 시 사용자 프로필이 Firebase에 저장된다. | 완료 버튼 클릭 후 | Firestore `users/{uid}` 확인 | nickname / displayName / isSetupCompleted=true 저장 | | |
| TC-ON-13 | §1 | AUTH-004 AC7·8<br/>AC7: 설정 완료 시 UserLangPref가 Firebase에 저장된다.<br/>AC8: selectedLearningLanguage가 primaryLearningLanguage와 같은 값으로 초기화된다. | 완료 버튼 클릭 후 | Firestore `users/{uid}/user_learning_preference/current` 확인 | UserLangPref 저장 + selectedLearningLanguage = primaryLearningLanguage = `"en"` | | |
| TC-ON-14 | §1 | AUTH-004 AC9·10·11·12<br/>AC9: 주 학습 언어 기준 Language State 기본값이 생성된다.<br/>AC10: 주 학습 언어 기준 Dashboard Summary 기본값이 생성된다.<br/>AC11: 주 학습 언어 기준 Session Summary 기본값이 생성된다.<br/>AC12: 주 학습 언어 기준 Flashcard Summary 기본값이 생성된다. | 완료 버튼 클릭 후 | Firestore subcollection 확인 | LangState / DashSummary / SessionSummary / FlashcardSummary 4종 모두 생성 | | |
| TC-ON-15 | §1 | AUTH-004 AC14<br/>AC14: 설정 완료 이후 다시 Dialog가 표시되지 않는다. | Initial Setup 완료 직후, 같은 세션 유지 | Dashboard에서 다른 화면 이동 후 재진입 | Initial Setup Dialog 재표시되지 않음 | | |
| TC-ON-16 | §1 | DASH-001 AC8<br/>AC8: 신규 사용자는 Empty Dashboard UI가 출력된다. | Initial Setup 완료 직후 | Dashboard 화면 확인 | 카드 4종 Empty 시각화 (Empty) — 자세한 동작은 TEST_FLOW_DASHBOARD §3 참조 | | |
| TC-ON-17 | §2 | AUTH-002 AC1<br/>AC1: 앱 실행 시 Splash 화면이 표시된다. | 계정 B 로그인 상태에서 앱 swipe-out | 앱 재실행 | Splash 화면 노출 (Loading) | | |
| TC-ON-18 | §2 | AUTH-002 AC5·6<br/>AC5: 세션 확인 중 Loading 상태가 표시된다.<br/>AC6: 세션 확인 완료 전까지 화면 전환이 발생하지 않는다. | 계정 B, 앱 재실행 후 Splash 노출 중 | 화면 탭/스와이프 시도 | 사용자 입력 차단, 깜빡임 없음 | | |
| TC-ON-19 | §2 | AUTH-002 AC2·3·7<br/>AC2: Firebase 현재 로그인 상태를 확인할 수 있다.<br/>AC3: 로그인된 사용자일 경우 Dashboard 화면으로 자동 이동한다.<br/>AC7: 자동 로그인 상태에서 앱 재실행 시 세션이 유지된다. | 계정 B, 앱 재실행 후 Splash 노출 중 | 세션 확인 완료 대기 | Dashboard 자동 이동 (Success) | | |
| TC-ON-20 | §4 | AUTH-003 AC1·2<br/>AC1: 설정 화면에서 로그아웃 버튼이 표시된다.<br/>AC2: 로그아웃 버튼 클릭 시 로그아웃 요청이 수행된다. | Dashboard 진입 상태, 설정 화면으로 이동 가능 | 설정 → 로그아웃 클릭 | Progress Indicator 노출, 버튼 비활성화 (Loading) | | |
| TC-ON-21 | §4 | AUTH-003 AC7<br/>AC7: 로그아웃 진행 중 중복 요청이 방지된다. | 로그아웃 진행 중 (Progress Indicator 노출 중) | 로그아웃 버튼 재클릭 | 중복 요청 차단 | | |
| TC-ON-22 | §4 | AUTH-003 AC3·4·5<br/>AC3: Firebase 세션이 제거된다.<br/>AC4: Google 로그인 세션이 함께 해제된다.<br/>AC5: 로그아웃 완료 후 Onboarding 화면으로 이동한다. | 로그아웃 진행 중 | 로그아웃 완료 대기 | Firebase + Google 세션 해제 → Onboarding 이동 (Success) | | |
| TC-ON-23 | §4 | AUTH-003 AC11<br/>AC11: 로그아웃 후 BackStack이 초기화되어 이전 사용자 화면으로 복귀할 수 없다. | 로그아웃 완료, Onboarding 화면 진입 | 디바이스 뒤로가기 | Dashboard로 복귀하지 않음 (앱 종료 또는 Onboarding 유지) | | |
| TC-ON-24 | §4 | AUTH-003 AC9·10<br/>AC9: 로그아웃 후 전역 사용자 상태(Global State)가 초기화된다.<br/>AC10: 로그아웃 후 UserLangPref와 selectedLearningLanguage가 현재 앱 세션에서 제거된다. | 로그아웃 완료, Onboarding 화면 진입 | 즉시 계정 B로 재로그인 | 이전 사용자(A) 카드 데이터 잠깐도 노출되지 않음 | | |
| TC-ON-25 | §4 | AUTH-003 AC6<br/>AC6: 로그아웃 이후 자동 로그인되지 않는다. | 로그아웃 직후, 앱이 종료된 상태 | 앱 재실행 (자동 로그인 시도) | Onboarding으로 이동 (자동 로그인 안 됨) | | |
| TC-ON-26 | §5 | AUTH-001 AC10<br/>AC10: 인증 실패 시 에러 메시지를 표시한다. | 계정 A, 네트워크 끊긴 상태, Onboarding 화면 진입 | Google 로그인 시도 | 에러 메시지 노출 (Error) | | |
| TC-ON-27 | §5 | AUTH-001 AC10<br/>AC10: 인증 실패 시 에러 메시지를 표시한다. (재시도 검증) | 직전 로그인 실패 직후, 네트워크 복구 | 재시도 (다시 로그인 버튼 클릭) | 정상 로그인 진행 (Retry 성공) | | |
