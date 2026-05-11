# User Flow — DASHBOARD

## 목표

- 사용자가 현재 학습 상태와 최근 학습 활동을 한눈에 확인할 수 있다.
- 사용자는 Dashboard를 통해 다음 학습 행동(AI Chat, 교정, Flashcard 학습, 통계 확인)으로 이동할 수 있다.

---

## 시작 조건

- 사용자가 로그인된 상태
- Initial Setup 완료 상태
- Global Learning State preload 완료 상태
- UserLearningPreference preload 완료 상태
- selectedLearningLanguage가 존재하는 상태
- 인터넷 연결 가능 상태 (오프라인 시 Local Cache 사용 가능)

---

## 성공 조건

- Dashboard 진입 시 학습 요약 카드가 정상 출력된다.
- 현재 선택된 언어 기준으로 Dashboard 카드가 출력된다.
- 사용자가 Dashboard에서 학습 언어를 변경하면 Dashboard 카드와 이후 이동하는 기능의 언어 컨텍스트가 함께 변경된다.
- 사용자가 현재 학습 상태를 빠르게 확인할 수 있다.
- 최근 학습 데이터가 Local Cache 기반으로 빠르게 출력된다.
- 사용자가 다음 학습 행동으로 자연스럽게 이동할 수 있다.

---

# 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 예외 처리 | Flow 상세 페이지 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Dashboard 진입 | Dashboard 화면 진입 | UserLearningPreference와 selectedLearningLanguage preload 후 해당 언어의 Dashboard Summary fetch | 현재 선택 언어 기준 Dashboard 카드 출력 | Preference 또는 Summary fetch 실패 | Loading / Content | Preference 없음 시 primaryLearningLanguage fallback / Firebase 실패 시 Local Cache fallback | DASH-001 |
| 학습 언어 변경 | 언어 selector에서 학습 언어 선택 | selectedLearningLanguage 갱신 및 해당 언어 Summary 로드 | 선택 언어 기준 카드 재렌더링 | 언어 변경 저장 실패 | Content / Saving | 저장 실패 시 이전 선택 언어 유지 및 Snackbar 표시 | DASH-006 |
| 최근 AI 대화 카드 확인 | 최근 대화 카드 확인 및 클릭 | 현재 선택 언어의 최근 대화 정보 렌더링 및 AI Chat 이동 | AI Chat 화면 진입 | Navigation 실패 | Content / Navigation Loading | 현재 언어의 최근 대화 데이터 없음 처리 | DASH-002 |
| 교정 대기 카드 확인 | 최근 대화 기록 카드 확인 및 클릭 | 현재 선택 언어의 교정 가능 상태 렌더링 및 교정 화면 이동 | Correction 화면 진입 | Navigation 실패 | Content / Navigation Loading | 현재 언어의 recentFullContext 없음 처리 | DASH-003 |
| Flashcard 학습 카드 확인 | Flashcard 카드 확인 및 클릭 | 현재 선택 언어의 복습 카드 수 렌더링 및 SRS 화면 이동 | Flashcard 학습 화면 진입 | Navigation 실패 | Content / Navigation Loading | 현재 언어의 복습 카드 없음 처리 | DASH-004 |
| 언어 성취율 카드 확인 | 성취율 카드 확인 및 클릭 | 현재 선택 언어의 대표 학습 지표 렌더링 및 통계 화면 이동 | Statistics 화면 진입 | Navigation 실패 | Content / Navigation Loading | 현재 언어의 통계 데이터 부족 처리 | DASH-005 |

---

# GitHub Issue (실행 기준 / SSOT)

예:

- DASH-001 Dashboard preload 및 Summary fetch
- DASH-002 최근 AI 대화 카드
- DASH-003 문장 교정 유도 카드
- DASH-004 Flashcard 학습 카드
- DASH-005 언어 성취율 카드
- DASH-006 Dashboard 학습 언어 selector 및 selectedLearningLanguage 변경

---

# 데모 시나리오 (Demo Scenario)

1. 로그인 완료 후 Dashboard 진입
2. 현재 선택된 언어 확인
3. 최근 AI 대화 카드 확인
4. 교정 대기 카드 클릭
5. Flashcard 학습 카드 확인
6. 언어 성취율 카드 확인
7. 학습 언어 변경 후 카드 데이터 변경 확인
8. AI Chat 화면 이동 후 Dashboard 재진입
9. 최신 학습 상태 반영 확인

---

# 디자인 (Figma, 필요 시)

- Dashboard Screen
- Summary Card Component
- Empty Dashboard State
- Loading Skeleton UI
- Error State UI

---

# Dashboard 카드 구성

---

## 0. 학습 언어 Selector

### 역할

Dashboard와 이후 이동하는 기능들이 사용할 현재 학습 언어를 결정한다.

### 표시 예시

```text
English
```

### 사용 데이터

- selectedLearningLanguage
- learningLanguages
- 언어별 DashboardSummary 존재 여부

### 변경 정책

- 사용자가 언어를 변경하면 `selectedLearningLanguage`를 갱신한다.
- Dashboard의 모든 카드는 변경된 언어의 Summary만 사용한다.
- AI Chat, Correction, Flashcard, Statistics로 이동할 때 현재 선택 언어를 route argument 또는 Global Learning State를 통해 전달한다.

---

## 1. 최근 AI 대화 카드

### 표시 예시

```text
오늘 영어로 여행 주제로 대화하셨습니다.
```

### CTA

```text
AI 대화하러 가기
```

### 사용 데이터

- selectedLearningLanguage
- 최근 대화 주제
- 최근 대화 시간

---

## 2. 교정 대기 카드

### 표시 예시

```text
영어 대화 기록이 약 12분 있습니다.
```

### CTA

```text
문장 교정받으러 가기
```

### 사용 데이터

- selectedLearningLanguage
- recentFullContext 존재 여부
- 최근 대화 길이

---

## 3. Flashcard 학습 카드

### 표시 예시

```text
복습해야 할 Flashcard가 14장 있습니다.
```

### CTA

```text
플래시카드 학습하러 가기
```

### 사용 데이터

- selectedLearningLanguage
- dueFlashcards
- 최근 저장 카드 수

---

## 4. 대표 언어 성취율 카드

### 표시 예시

```text
문법 정확도 +8
어휘 다양성 +5
유창성 +3
자연스러움 +4
```

### CTA

```text
자세한 통계 보러 가기
```

### 사용 데이터

- selectedLearningLanguage
- grammar_accuracy
- lexical_diversity
- fluency_score
- spoken_naturalness

---

# 상태 정책

## Loading

- Dashboard preload 중 Skeleton UI 표시
- Summary fetch 중 카드 placeholder 표시

---

## Error

### Fatal

- Dashboard Summary fetch 실패
- Local Cache load 실패

→ Error UI 표시 및 재시도 버튼 제공

---

### Transient

- 일부 Summary fetch 실패
- Background sync 실패

→ Snackbar 또는 fallback 데이터 사용

---

## Empty

신규 사용자:

- 최근 대화 없음
- Flashcard 없음
- 통계 없음

→ Empty Dashboard UI 표시

예:

```text
아직 학습 데이터가 없습니다.
AI와 첫 대화를 시작해보세요!
```

---

# 데이터 정책

## Dashboard는 Summary 기반으로만 렌더링

Dashboard는:

- recentFullContext 전체
- Language State 전체

를 직접 계산하지 않는다.

대신:

```text
DashboardSummary[selectedLearningLanguage]
```

만 사용하여 빠르게 출력한다.

---

## 현재 선택 언어 정책

`selectedLearningLanguage`는 데이터의 소속을 나타내는 `language` 필드와 다르다.

| 구분 | 역할 |
| --- | --- |
| language | Session / Flashcard / Statistics / Language State가 어떤 언어의 데이터인지 나타내는 소속 필드 |
| selectedLearningLanguage | 현재 Dashboard와 기능 이동이 바라보는 앱 전역 언어 컨텍스트 |

Dashboard는 `selectedLearningLanguage`를 기준으로 언어별 Summary를 선택한다.

```text
selectedLearningLanguage = "en"
→ dashboardSummaries["en"] 렌더링
→ AI Chat 진입 시 "en"으로 대화 시작
→ Flashcard 진입 시 "en" 카드만 조회
→ Statistics 진입 시 "en" 통계 표시
```

---

## 데이터 조회 우선순위

```text
Local Cache
→ 즉시 렌더링
→ Firebase background sync
```

---

## observe 정책

다음 상태 변화 이후 Dashboard 재진입 시 최신 상태 반영 가능:

- selectedLearningLanguage 변경
- AI Chat 세션 종료
- Session Memory recentFullContext 변경
- Flashcard 학습 완료
- Language State 업데이트
- Correction 완료

---

# 범위 제외 (Out of Scope)

- 상세 통계 그래프
- recentFullContext 전체 조회
- Flashcard 학습 기능 자체
- AI Chat 기능 자체
- 교정 기능 자체
- 실시간 통계 분석

---

# 향후 확장 예정

- 추천 학습 루틴
- 주간 성장 리포트
