# Test Sheet — FLOW-DASHBOARD

> 데모 시나리오: [`docs/Demo/DEMO_FLOW_DASHBOARD.md`](../DEMO_FLOW_DASHBOARD.md)
> 사용법: 각 행의 "수행 절차"대로 실행 후 "기대 결과" 만족 여부를 **Pass / Fail** 표기. 실패 시 비고에 1~2줄 재현 메모 + GitHub Issue 링크.
>
> **Mock 토글 절차**:
> 1. `di/RepositoryModule.kt:64`의 `learningStateRepoImpl: LearningStateRepoImpl` 줄을 주석 처리하고, `:65`의 `fakeLearningStateRepo: FakeLearningStateRepo` 줄의 주석을 해제 (Real → Fake 바인딩 교체).
> 2. `data/repository/fake/FakeLearningStateRepo.kt:46~52` 영역에서 사용할 fixture 라인만 활성화하고 나머지는 주석 처리. (기본 활성 라인은 `:48` activeUser)
> 3. 필요 시 `:68` `syncBehavior`, `:79` `changeBehavior` 값을 변경.
> 4. 발표 후 Real 바인딩으로 원복.
>
> **Real vs Mock 검증 가능 범위**:
> - **Real 바인딩**: Firestore에 데이터가 없으면 카드 4종이 Empty 상태로 렌더됨. DASH-001 진입/렌더/sync 같은 인프라 AC와 throttle 검증만 가능.
> - **Mock activeUser fixture**: 카드별 데이터(시간/주제/due/delta 등) 표시 AC 검증 가능. DASH-002 ~ DASH-005의 데이터 표시 AC는 Mock에서 확인.

---

## 커버되는 AC 체크리스트

> 아래 표의 각 행이 어느 AC를 검증하는지 한눈에 보기 위한 목록.
> `[x]` = 본 테스트 시트에 포함됨 / `[ ]` = 본 시트에 없음(추후 보강 또는 deprecated).

### DASH-001 Dashboard 진입 (12개)
- [x] AC1: Dashboard 진입 시 DashSummary fetch가 수행된다.
- [x] AC2: Dashboard 진입 시 UserLangPref preload가 수행된다.
- [x] AC3: selectedLearningLanguage가 확인된다.
- [x] AC4: Local Cache 기반으로 Dashboard가 빠르게 렌더링된다.
- [x] AC5: 현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다.
- [x] AC6: Firebase background sync가 수행된다.
- [x] AC7: Summary fetch 실패 시 fallback 데이터가 사용된다.
- [x] AC8: 신규 사용자는 Empty Dashboard UI가 출력된다.
- [x] AC9: Loading 상태 중 Skeleton UI가 표시된다.
- [x] AC10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.
- [x] AC11: Summary fetch 중 중복 요청이 방지된다.
- [x] AC12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.

### DASH-002 최근 AI 대화 카드 (8개)
- [x] AC1: 최근 AI 대화 카드가 정상 출력된다.
- [ ] AC2: 현재 선택 언어가 최근 대화 카드에 표시된다. (※ 카드 컴포넌트에 언어 파라미터 없음 — 미구현 또는 AppBar pill로 대체)
- [x] AC3: 최근 대화 주제가 표시된다.
- [x] AC4: 최근 대화 시간이 표시된다.
- [x] AC5: 카드 클릭 시 AI Chat 화면으로 이동한다.
- [ ] AC6: 현재 선택된 언어가 AI Chat 초기 상태에 반영된다. (※ Chat 화면 측 검증 — FLOW-AI-CHAT 스프린트에서 다룸)
- [x] AC7: 최근 대화 데이터가 없을 경우 Empty 상태가 표시된다.
- [x] AC8: 카드 클릭 중 중복 Navigation이 방지된다.

### DASH-003 교정 대기 카드 (9개)
- [x] AC1: 교정 대기 카드가 정상 출력된다.
- [ ] AC2: 최근 대화 기록 존재 여부가 반영된다.
- [ ] AC3: 현재 선택 언어가 표시된다.
- [ ] AC4: 최근 대화 시간이 표시된다. (※ 4c6ea7b 커밋으로 시간 칩 제거됨 — AC 자체 deprecated)
- [x] AC5: 현재 선택 언어의 재사용 Session Memory에 교정 가능한 대화가 있는지 표시된다.
- [x] AC6: 카드 클릭 시 Correction 화면으로 이동한다.
- [ ] AC7: selectedLearningLanguage가 교정 화면으로 전달된다.
- [x] AC8: recentFullContext가 없을 경우 Empty 상태가 표시된다.
- [x] AC9: 카드 클릭 중 중복 Navigation이 방지된다.

### DASH-004 Flashcard 학습 카드 (7개)
- [x] AC1: Flashcard 학습 카드가 정상 출력된다.
- [x] AC2: 복습 예정 Flashcard 수가 표시된다.
- [x] AC3: 최근 저장된 Flashcard 수가 표시된다.
- [x] AC4: 카드 클릭 시 Flashcard 학습 화면으로 이동한다.
- [ ] AC5: 현재 선택된 언어가 Flashcard 학습 초기 상태에 반영된다.
- [x] AC6: 복습 카드가 없을 경우 Empty 상태가 표시된다.
- [x] AC7: 카드 클릭 중 중복 Navigation이 방지된다.

### DASH-005 언어 성취율 카드 (7개)
- [x] AC1: 언어 성취율 카드가 정상 출력된다.
- [x] AC2: 대표 학습 성장 지표(delta)가 표시된다.
- [x] AC3: 현재 선택 언어 기준 성취율 데이터가 정상 렌더링된다.
- [x] AC4: 카드 클릭 시 Statistics 화면으로 이동한다.
- [ ] AC5: 현재 선택된 언어가 Statistics 초기 상태에 반영된다.
- [x] AC6: 통계 데이터가 부족할 경우 Empty 상태가 표시된다.
- [x] AC7: 카드 클릭 중 중복 Navigation이 방지된다.

### DASH-006 학습 언어 Selector (10개)
- [x] AC1: Dashboard 상단에 현재 선택된 학습 언어가 표시된다.
- [x] AC2: 사용자는 학습 중인 언어 목록을 확인할 수 있다.
- [x] AC3: 사용자는 학습 중인 언어 중 하나를 선택할 수 있다.
- [x] AC4: 언어 선택 시 selectedLearningLanguage가 갱신된다.
- [x] AC5: selectedLearningLanguage 변경 후 해당 언어의 Dashboard Summary가 로드된다.
- [x] AC6: Dashboard의 모든 카드가 변경된 언어 기준으로 다시 렌더링된다.
- [x] AC7: 언어 변경 저장 중 중복 요청이 방지된다.
- [x] AC8: 언어 변경 실패 시 이전 selectedLearningLanguage가 유지된다.
- [x] AC9: selectedLearningLanguage가 없는 경우 primaryLearningLanguage로 fallback된다.
- [x] AC10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.

---

## 테스트 시트

> 사전 조건이 같은 행끼리 인접하게 묶고, 위에서 아래로 갈수록 setup이 복잡해지는 순서로 배치.

| Test ID | 시나리오 | 연결 AC | 사전 조건 | 수행 절차 | 기대 결과 | Pass/Fail | 비고 |
|---|---|---|---|---|---|---|---|
| TC-DB-01 | §1 | DASH-001 AC1·2·3<br/>AC1: Dashboard 진입 시 DashSummary fetch가 수행된다.<br/>AC2: Dashboard 진입 시 UserLangPref preload가 수행된다.<br/>AC3: selectedLearningLanguage가 확인된다. | Real 바인딩, 시드된 계정으로 로그인 완료 | Dashboard 진입 | DashSummary fetch + UserLangPref preload + selectedLang 확인 로그 (logcat `DashboardViewModel`) | | |
| TC-DB-02 | §1 | DASH-001 AC9<br/>AC9: Loading 상태 중 Skeleton UI가 표시된다. | Real 바인딩, 시드된 계정으로 로그인 완료 | Dashboard 진입 직후 화면 관찰 | 2x2 Skeleton 노출 (Loading) | | |
| TC-DB-03 | §1 | DASH-001 AC4·5<br/>AC4: Local Cache 기반으로 Dashboard가 빠르게 렌더링된다.<br/>AC5: 현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다. | Real 바인딩, 시드된 계정으로 로그인 완료 | Skeleton 종료 후 카드 4종 확인 | 2x2 그리드로 대화 / 학습 / 교정 / 통계 카드 4종 렌더링 (Success — 카드별 데이터 표시는 TC-DB-14 ~ TC-DB-17에서 별도 검증) | | |
| TC-DB-04 | §1 | DASH-001 AC6·12<br/>AC6: Firebase background sync가 수행된다.<br/>AC12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다. | Real 바인딩, 시드된 계정으로 로그인 완료, logcat 필터 `DashboardViewModel` | 진입 후 ~1초 logcat 관찰 | `state emit` 로그 cache + sync 2회 기록 | | |
| TC-DB-05 | §1 | DASH-001 AC10<br/>AC10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다. | Real 바인딩, Dashboard 진입 완료 상태 | 설정/마이페이지 화면 이동 후 Dashboard 재진입 | 최신 데이터로 즉시 표시 | | |
| TC-DB-06 | §2 | DASH-006 AC1<br/>AC1: Dashboard 상단에 현재 선택된 학습 언어가 표시된다. | Real 바인딩, activeUser 시드 (또는 동급 Real 데이터), 로그인 완료 | Dashboard AppBar 우측 확인 | ThemePrimary 알약 버튼에 현재 선택 언어 코드(예: `EN`) 노출 | | |
| TC-DB-07 | §2 | DASH-006 AC2·3<br/>AC2: 사용자는 학습 중인 언어 목록을 확인할 수 있다.<br/>AC3: 사용자는 학습 중인 언어 중 하나를 선택할 수 있다. | Real 바인딩, activeUser 시드, 로그인 완료 | AppBar 알약 버튼 클릭 | "학습 언어 선택" 다이얼로그 노출 + 4개 한국어 라벨 (`한국어`/`영어`/`일본어`/`스페인어`) | | |
| TC-DB-08 | §2 | DASH-006 AC3<br/>AC3: 사용자는 학습 중인 언어 중 하나를 선택할 수 있다. | Real 바인딩, activeUser 시드, 다이얼로그가 열린 상태 | 일본어 항목 탭 | 일본어 항목에 ThemePrimary 보더 강조 (임시 선택 상태) | | |
| TC-DB-09 | §1 | DASH-002 AC5<br/>AC5: 카드 클릭 시 AI Chat 화면으로 이동한다. | Real 바인딩, activeUser 시드, 로그인 완료 | 최근 AI 대화 카드 1회 클릭 | Chat route로 화면 전환 — 백스택에 Chat 화면 push (도착지가 stub이어도 navigation은 동작해야 함) | | |
| TC-DB-10 | §5 | DASH-002 AC8<br/>AC8: 카드 클릭 중 중복 Navigation이 방지된다. | Real 바인딩, activeUser 시드, 로그인 완료. (선택) `DashboardCardCommon.kt:36-43`의 else 분기에 `Log.d("DashCardThrottle", "drop — throttle window")` 임시 추가하면 logcat 검증 가능 (`NAV_THROTTLE_MS = 500L`) | 최근 AI 대화 카드를 ~500ms 이내 빠르게 2회 연속 클릭 | Navigation 1회만 발생 — Chat 화면 1회 push. 뒤로가기 1회로 Dashboard 복귀. 임시 로그 추가 시 logcat에 `drop — throttle window` 1회 기록 | | |
| TC-DB-11 | §5 | DASH-003 AC9 (마지막)<br/>AC9: 카드 클릭 중 중복 Navigation이 방지된다. | Real 바인딩, activeUser 시드, 로그인 완료. (선택) `DashboardCardCommon.kt:36-43`의 else 분기에 `Log.d("DashCardThrottle", "drop — throttle window")` 임시 추가하면 logcat 검증 가능 | 교정 대기 카드를 ~500ms 이내 빠르게 2회 연속 클릭 | Navigation 1회만 발생 — Correction 화면 1회 push. 뒤로가기 1회로 Dashboard 복귀 | | |
| TC-DB-12 | §5 | DASH-004 AC7 (마지막)<br/>AC7: 카드 클릭 중 중복 Navigation이 방지된다. | Real 바인딩, activeUser 시드, 로그인 완료. (선택) `DashboardCardCommon.kt`에 임시 로그 추가 시 logcat 검증 가능 | Flashcard 학습 카드를 ~500ms 이내 빠르게 2회 연속 클릭 | Navigation 1회만 발생 — Study 화면 1회 push. 뒤로가기 1회로 Dashboard 복귀 | | |
| TC-DB-13 | §5 | DASH-005 AC7 (마지막)<br/>AC7: 카드 클릭 중 중복 Navigation이 방지된다. | Real 바인딩, activeUser 시드, 로그인 완료. (선택) `DashboardCardCommon.kt`에 임시 로그 추가 시 logcat 검증 가능 | 언어 성취율 카드를 ~500ms 이내 빠르게 2회 연속 클릭 | Navigation 1회만 발생 — Analytics 화면 1회 push. 뒤로가기 1회로 Dashboard 복귀 | | |
| TC-DB-14 | §1 | DASH-002 AC1·3·4<br/>AC1: 최근 AI 대화 카드가 정상 출력된다.<br/>AC3: 최근 대화 주제가 표시된다.<br/>AC4: 최근 대화 시간이 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.SUCCESS` | Dashboard 진입 후 최근 AI 대화 카드 확인 | "최근 대화 시간" 라벨 + 30분 + "Travel" 주제 표시 (Success) | | |
| TC-DB-15 | §1 | DASH-003 AC1·5<br/>AC1: 교정 대기 카드가 정상 출력된다.<br/>AC5: 현재 선택 언어의 재사용 Session Memory에 교정 가능한 대화가 있는지 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.SUCCESS` | Dashboard 진입 후 교정 대기 카드 확인 | correctionAvailable=true 시그널 (우측 상단 빨간 점) 표시, 하단 시간 칩 없음 (Success) | | |
| TC-DB-16 | §1 | DASH-004 AC1·2·3<br/>AC1: Flashcard 학습 카드가 정상 출력된다.<br/>AC2: 복습 예정 Flashcard 수가 표시된다.<br/>AC3: 최근 저장된 Flashcard 수가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.SUCCESS` | Dashboard 진입 후 Flashcard 학습 카드 확인 | due 12장 + 저장 84장 수 표시 (Success) | | |
| TC-DB-17 | §1 | DASH-005 AC1·2·3<br/>AC1: 언어 성취율 카드가 정상 출력된다.<br/>AC2: 대표 학습 성장 지표(delta)가 표시된다.<br/>AC3: 현재 선택 언어 기준 성취율 데이터가 정상 렌더링된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.SUCCESS` | Dashboard 진입 후 언어 성취율 카드 확인 | grammar +8 / fluency +12 / vocab +5 / naturalness +15 delta 칩 표시 (Success) | | |
| TC-DB-18 | §2 | DASH-006 AC4·5·6<br/>AC4: 언어 선택 시 selectedLearningLanguage가 갱신된다.<br/>AC5: selectedLearningLanguage 변경 후 해당 언어의 Dashboard Summary가 로드된다.<br/>AC6: Dashboard의 모든 카드가 변경된 언어 기준으로 다시 렌더링된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.SUCCESS`, 일본어 임시 선택된 상태 | "선택" 버튼 클릭 | 카드 4종이 JA 기준 데이터로 즉시 재렌더링(JA의 15분·"日常会話"·due 5·저장 22·delta 4종), 변경이 되돌아가지 않음 (race 가드) | | |
| TC-DB-19 | §2 | DASH-006 AC2 (체크 아이콘 정정)<br/>AC2: 사용자는 학습 중인 언어 목록을 확인할 수 있다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`(learningLangs=`[EN, JA]`, dashSummaries=`{EN, JA}` 시드), `:68` = `SyncBehavior.SUCCESS`, 사전에 알약 selector → 스페인어 한 번 선택해 learningLangs에 ES 추가된 상태 | AppBar 알약 클릭 → 다이얼로그 항목 확인 | EN/JA 옆에는 체크 아이콘, ES 옆에는 체크 아이콘 없음 (activeLearningLanguages 기준 — ES는 dashSummary 없어서 active 아님) | | |
| TC-DB-20 | §1 | DASH-001 AC11<br/>AC11: Summary fetch 중 중복 요청이 방지된다. | Mock 바인딩 (`RepositoryModule.kt:64-65` 토글), `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.SUCCESS`. **`:182` `SYNC_DELAY_MS = 4000L`로 임시 연장** (기본 800L은 회전 타이밍 잡기 빡빡함, 검증 후 800L로 원복). logcat 필터 `tag:DashboardViewModel` | Dashboard 진입 직후 sync 진행 중(SYNC_DELAY_MS 윈도우 안)에 디바이스 회전 → `LaunchedEffect`의 `onEnter()` 재트리거 | logcat에 `triggerSync() skipped — AC 11 dedup, in-flight job exists` 1회 기록 (※ `MainActivity`가 `configChanges`로 회전을 직접 처리한다면 onEnter 재트리거 안 됨 → 단위 테스트로 대체) | | |
| TC-DB-21 | §2 | DASH-006 AC7<br/>AC7: 언어 변경 저장 중 중복 요청이 방지된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`. **`changeSelectedLang` 본문에 `delay(3000L)` 임시 추가** (Mock의 즉시 success 반환으로 `isChangingLanguage = true` 윈도우가 너무 짧아서 차단 발화 못 보는 문제 해결, 검증 후 delay 줄 제거) | 다이얼로그에서 일본어 선택 → "선택" 클릭 → 변경 진행 중(~3초 안)에 알약 버튼 재클릭 | 알약 버튼 클릭 무시(`LearningLanguageSelector`의 `clickable(enabled = !isLoading)` 가드), 다이얼로그 재진입 안 됨 | | |
| TC-DB-22 | §3 | DASH-001 AC8<br/>AC8: 신규 사용자는 Empty Dashboard UI가 출력된다. | Mock 바인딩 (`RepositoryModule.kt:64-65` 토글), `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)` 활성, `:68` = `SyncBehavior.SUCCESS` | Dashboard 진입 | 별도 Empty 화면 없이 본 레이아웃 유지 + 알약 selector(fallback `KO`) 노출 | | |
| TC-DB-23 | §3 | DASH-001 AC8 + DASH-002 AC7<br/>AC8(DASH-001): 신규 사용자는 Empty Dashboard UI가 출력된다.<br/>DASH-002 AC7: 최근 대화 데이터가 없을 경우 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)`, `:68` = `SyncBehavior.SUCCESS` | 최근 AI 대화 카드 확인 | 본 색(ThemePrimary) + 우측 상단 점, 시간/주제 칩 숨김 (Empty) | | |
| TC-DB-24 | §3 | DASH-001 AC8 + DASH-003 AC8 + DASH-004 AC6 + DASH-005 AC6<br/>AC8(DASH-001): 신규 사용자는 Empty Dashboard UI가 출력된다.<br/>DASH-003 AC8: recentFullContext가 없을 경우 Empty 상태가 표시된다.<br/>DASH-004 AC6: 복습 카드가 없을 경우 Empty 상태가 표시된다.<br/>DASH-005 AC6: 통계 데이터가 부족할 경우 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)`, `:68` = `SyncBehavior.SUCCESS` | 학습 / 교정 / 통계 카드 확인 | 3개 카드 모두 회색(`TextWrong`) 처리 (Empty) | | |
| TC-DB-25 | §3 | DASH-004 AC4·6<br/>AC4: 카드 클릭 시 Flashcard 학습 화면으로 이동한다.<br/>AC6: 복습 카드가 없을 경우 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)`, `:68` = `SyncBehavior.SUCCESS` | 학습 카드 클릭 | "저장된 카드 없음" 토스트 + Study 화면 이동 | | |
| TC-DB-26 | §3 | DASH-003 AC6·8<br/>AC6: 카드 클릭 시 Correction 화면으로 이동한다.<br/>AC8: recentFullContext가 없을 경우 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)`, `:68` = `SyncBehavior.SUCCESS` | 교정 카드 클릭 | "교정 가능 데이터 없음" 토스트 + Correction 화면 이동 | | |
| TC-DB-27 | §3 | DASH-005 AC4·6<br/>AC4: 카드 클릭 시 Statistics 화면으로 이동한다.<br/>AC6: 통계 데이터가 부족할 경우 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)`, `:68` = `SyncBehavior.SUCCESS` | 통계 카드 클릭 | "데이터 부족" 토스트 + Analytics 화면 이동 | | |
| TC-DB-28 | §3 | DASH-006 AC4 (신규 사용자 동작 검증)<br/>AC4: 언어 선택 시 selectedLearningLanguage가 갱신된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.onboardingDone)`, `:68` = `SyncBehavior.SUCCESS` | 알약 selector 클릭 → 다이얼로그에서 영어 선택 → "선택" | 다이얼로그가 닫히고 selector 라벨이 `EN`으로 갱신 (no-op 버그 미발생) | | |
| TC-DB-29 | §4 | DASH-001 AC7<br/>AC7: Summary fetch 실패 시 fallback 데이터가 사용된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.FAILURE`, logcat 필터 `tag:DashboardViewModel` | Dashboard 진입 → 카드 4종 확인 → sync 완료(~800ms) 후 logcat 관찰 | activeUser cache 데이터가 카드 4종에 즉시 표시 — 최근 AI 대화(30분·"Travel") / 교정 대기(빨간 점) / Flashcard(due 12·저장 84) / 언어 성취율(grammar +8 등). sync 완료 후 logcat에 `sync failed — keeping cache (AC 7 fallback)` 1회 기록 (Fallback) | | |
| TC-DB-30 | §4 | DASH-001 AC7<br/>AC7: Summary fetch 실패 시 fallback 데이터가 사용된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.FAILURE` | 진입 후 ~800ms 대기 | Snackbar 노출 ("최신 데이터 동기화 실패" 등) (Error) | | |
| TC-DB-31 | §4 | DASH-001 AC7<br/>AC7: Summary fetch 실패 시 fallback 데이터가 사용된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:68` = `SyncBehavior.FAILURE`, Snackbar 노출 후 | 카드 데이터 재확인 | cache 값 그대로 유지, 전체 화면 Error UI 없음 | | |
| TC-DB-32 | §6 | DASH-006 AC8<br/>AC8: 언어 변경 실패 시 이전 selectedLearningLanguage가 유지된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:79` = `ChangeBehavior.FAILURE` | 알약 selector → 다이얼로그 → 일본어 선택 → "선택" 클릭 | Snackbar 실패 안내 (Error) | | |
| TC-DB-33 | §6 | DASH-006 AC8<br/>AC8: 언어 변경 실패 시 이전 selectedLearningLanguage가 유지된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.activeUser)`, `:79` = `ChangeBehavior.FAILURE`, 실패 직후 | 카드 4종 및 알약 selector 라벨 확인 | 카드 4종 EN 데이터 유지 + 알약 라벨 = `EN` (Fallback) | | |
| TC-DB-34 | §7 | DASH-006 AC9<br/>AC9: selectedLearningLanguage가 없는 경우 primaryLearningLanguage로 fallback된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.corruptedSelectedLang)` (selectedLang=`UNKNOWN`, learningLangs=`[KO, EN, JA, ES]`, primaryLang=`EN`) | Dashboard 진입 | EN(primaryLang) 기준 카드 4종 표시 (Fallback) | | |
| TC-DB-35 | §7 | DASH-006 AC9<br/>AC9: selectedLearningLanguage가 없는 경우 primaryLearningLanguage로 fallback된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.corruptedSelectedLang)` | 진입 직후 logcat 관찰 | `AC 9 fallback` 로그 + `changeSelectedLang(EN)` 복구 호출 1회 | | |
| TC-DB-36 | §8 | DASH-006 AC10<br/>AC10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.emptyLearningLangs)` (userPref 있고 learningLangs=`[]`) | Dashboard 진입 | Fatal Error 화면 노출 (Error) | | |
| TC-DB-37 | §8 | DASH-006 AC10<br/>AC10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다. | Mock 바인딩, `FakeLearningStateRepo.kt:48` = `MutableStateFlow(FakeFixtures.emptyLearningLangs)`, Fatal Error 화면 노출 후 | 화면 버튼 확인 | 재시도 버튼 노출 (Retry) | | |
