# Demo Scenario — FLOW-DASHBOARD

> 기본은 Real 바인딩 (`RepositoryModule.kt:64` = `LearningStateRepoImpl`).
> 상태 분기(시나리오 3·4·6·7·8)는 `FakeLearningStateRepo` 토글로 시연.
>
> **Mock 전환**: `RepositoryModule.kt:64-65`의 주석을 토글해 `FakeLearningStateRepo`로 바꾼 뒤, `FakeLearningStateRepo.kt`의 활성 fixture(`:48`) / `syncBehavior`(`:68`) / `changeBehavior`(`:79`) 값을 시나리오에 맞게 변경.
> 발표 후 Real 바인딩으로 원복.

---

## 시나리오 1 — Dashboard 진입 (DASH-001 ~ DASH-005)

> 카드 **렌더**와 **인프라 흐름**(Skeleton/cache/sync)은 Real로 시연 가능.
> 카드별 **데이터 표시**(시간/주제/due/delta 등 DASH-002~005 AC)는 Firestore에 데이터가 시드된 Real 계정이거나 Mock `activeUser` fixture가 필요. (Real 신규 계정은 빈 카드만 보임)

1. 로그인 완료 후 Dashboard 진입
2. 2x2 Skeleton 노출 (Loading)
3. 카드 4종 표시 (Success)
   - 활성 사용자 데이터 기준 예시: 최근 AI 대화 (라벨 **"최근 대화 시간"**, 30분, "Travel") / 교정 대기 (우측 상단 빨간 점만, 하단 시간 칩 없음) / Flashcard (due 12, 저장 84) / 언어 성취율 (grammar +8 / fluency +12 / vocab +5 / naturalness +15)
4. ~800ms 후 sync 완료 → updatedAt 갱신 (logcat `state emit` 2회)

---

## 시나리오 2 — 학습 언어 변경 (DASH-006)

> selector는 **알약 버튼 + 다이얼로그** 형태. 다이얼로그에서 **임시 선택** 후 **"선택" 버튼**을 눌러야 반영됨.

1. AppBar 우측 알약 버튼(현재 선택 언어 코드 표시, 예: `EN`) 클릭
2. "학습 언어 선택" 다이얼로그 노출 → 4개 항목 한국어 라벨 (`한국어` / `영어` / `일본어` / `스페인어`)
3. 일본어 임시 선택 (ThemePrimary 보더로 강조됨)
4. "선택" 버튼 클릭
5. 카드 4종이 즉시 JA 기준 데이터로 재렌더링되고, 변경이 되돌아가지 않음 (Success)
6. 다이얼로그 내 체크 아이콘은 실제 학습 데이터가 쌓인 언어(`activeLearningLanguages`)에만 표시 — "선택만 한 언어"와 "실제 학습한 언어"가 시각적으로 분리됨

---

## 시나리오 3 — 신규 사용자 Empty (DASH-001 AC8)

> Mock 토글: `FakeFixtures.onboardingDone` / `SyncBehavior.SUCCESS`

1. Dashboard 진입 → Skeleton 후 본 레이아웃 유지 (별도 Empty 화면 없음)
2. AppBar 알약 selector는 fallback(`KO`)으로라도 노출됨
3. 카드 4종 Empty 시각화 확인 (Empty)
   - 최근 AI 대화 카드: 우측 상단 점 부착, 시간/주제 칩은 모두 숨김(빈 DashSummary 가드)
   - 학습 / 교정 / 통계 카드: 회색(`TextWrong`)
4. 회색 카드 클릭 → 토스트 + 본 화면 이동
   - 학습 카드 → "저장된 카드 없음"
   - 교정 카드 → "교정 가능 데이터 없음"
   - 통계 카드 → "데이터 부족"

---

## 시나리오 4 — Sync 실패 → Fallback (DASH-001 AC7)

> Mock 토글: `FakeFixtures.activeUser` / `SyncBehavior.FAILURE`

1. Dashboard 진입
2. cache 데이터로 카드 4종 표시
3. ~800ms 후 sync 실패 → Snackbar 노출 (Error)
4. 카드 데이터는 cache 값 그대로 유지 (Fallback)

---

## 시나리오 5 — 카드 클릭 throttle (DASH-003·004·005 마지막 AC)

1. 카드 하나를 빠르게 2회 연속 클릭
2. Navigation은 1회만 발생 (logcat에서 중복 차단 확인)

---

## 시나리오 6 — 언어 변경 실패 (DASH-006 AC8)

> Mock 토글: `FakeFixtures.activeUser` / `ChangeBehavior.FAILURE`

1. AppBar 알약 selector 클릭 → 다이얼로그에서 일본어 임시 선택 → "선택" 클릭
2. 저장 실패 → Snackbar 노출 (Error)
3. 카드 4종과 알약 selector 라벨 모두 영어 상태로 유지

---

## 시나리오 7 — selectedLang fallback (DASH-006 AC9)

> Mock 토글: `FakeFixtures.corruptedSelectedLang`
> fixture 값: `selectedLang = UNKNOWN`(미지원 코드 표현용), `learningLangs = [KO, EN, JA, ES]`(전체 지원), `primaryLang = EN`
> 앱 다운그레이드 / DB 마이그레이션 실패 / 외부 소스 오염 시 발생 가능한 진짜 오염 상태 시뮬레이션.

1. Dashboard 진입
2. selectedLang = `UNKNOWN`, learningLangs = `[KO, EN, JA, ES]` — selectedLang이 `learningLangs`에 없는 오염 상태
3. 자동으로 `primaryLearningLanguage = EN` 기준 카드로 표시 (Fallback)
4. 내부적으로 복구 저장 `changeSelectedLang(EN)` 1회 발화 (logcat `AC 9 fallback`)

---

## 시나리오 8 — learningLangs 비어있음 (DASH-006 AC10)

> Mock 토글: `FakeFixtures.emptyLearningLangs`

1. Dashboard 진입
2. `learningLangs == []` 감지 → Fatal Error 화면 + 재시도 버튼 (Error + Retry)
