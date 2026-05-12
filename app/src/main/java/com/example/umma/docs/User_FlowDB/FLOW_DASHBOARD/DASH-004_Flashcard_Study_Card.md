# [Feature] DASH-004 Flashcard 학습 카드

## User Story

사용자는 Dashboard에서 현재 복습해야 할 Flashcard 상태를 확인하고,
SRS 학습 화면으로 빠르게 이동할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Flashcard 학습 카드가 정상 출력된다.
- [ ] 복습 예정 Flashcard 수가 표시된다.
- [ ] 최근 저장된 Flashcard 수가 표시된다.
- [ ] 카드 클릭 시 Flashcard 학습 화면으로 이동한다.
- [ ] 현재 선택된 언어가 Flashcard 학습 초기 상태에 반영된다.
- [ ] 복습 카드가 없을 경우 Empty 상태가 표시된다.
- [ ] 카드 클릭 중 중복 Navigation이 방지된다.

---

# Flow (링크)

- FLOW-DASHBOARD
- DASH-004 → Flashcard 학습 카드

---

# 구현 범위

## 포함 범위

- Flashcard 학습 카드 UI
- 현재 선택 언어의 Flashcard Summary 상태 렌더링
- dueFlashcards 표시
- 최근 저장 카드 수 표시
- Flashcard 학습 화면 이동 처리
- Empty 상태 처리
- Navigation Loading 처리

---

## 제외 범위 (Out of Scope)

- Flashcard 학습 기능 자체
- SRS 알고리즘
- 카드 뒤집기 기능
- 발음 재생 기능
- Flashcard 저장 기능
- Flashcard 상세 화면

> Dashboard preload 및 Summary fetch는 DASH-001에서 선행 처리된 상태를 전제로 한다.

---

# Details

## 카드 역할

Flashcard 학습 카드는:

```text
현재 선택 언어에서 복습이 필요한 Flashcard 상태
```

를 빠르게 보여준다.

또한:

```text
SRS 학습 화면 진입 CTA
```

역할을 수행한다.

---

## 표시 예시

```text
복습해야 할 Flashcard가 14장 있습니다.
```

---

## CTA

```text
플래시카드 학습하러 가기
```

---

## 사용 데이터

### DashSummary

`DASH-001`에서 로드된 `DashSummary[selectedLearningLanguage]`를 사용한다.

```json
{
  "language":"en",

  "dueFlashcards":14,

  "recentSavedFlashcards":3
}
```

---

## 사용 필드

- selectedLearningLanguage
- language
- dueFlashcards
- recentSavedFlashcards

---

## 카드 표시 정책

### dueFlashcards > 0

```text
복습해야 할 Flashcard가 14장 있습니다.
```

출력 가능.

---

### recentSavedFlashcards > 0

```text
최근 저장한 표현이 3개 있습니다.
```

보조 문구 출력 가능.

---

### dueFlashcards == 0

```text
현재 복습할 Flashcard가 없습니다.
```

출력 가능.

---

## 현재 선택 언어 정책

Flashcard 학습 카드는:

```text
DashSummary[selectedLearningLanguage]
```

기반으로 렌더링된다.

예:

```text
selectedLearningLanguage = "en"
→ English FlashcardStudyCard 렌더링
```

여러 언어 Flashcard 카드를 동시에 렌더링하지 않는다.
학습 언어 변경은 `DASH-006`에서 처리한다.

---

## Navigation 정책

카드 클릭 시:

```text
Dashboard
→ Flashcard Study
```

이동 수행.

---

## Flashcard 초기 상태 정책

Flashcard 학습 화면 진입 시:

```text
selectedLearningLanguage
```

를 초기 학습 언어로 전달한다.

예:

```text
selectedLearningLanguage = "en"
→ English Flashcard Study 시작
```

---

## Empty 정책

복습 카드가 없는 경우:

```text
현재 복습할 Flashcard가 없습니다.
새로운 표현을 저장해보세요!
```

출력 가능.

---

## Loading 정책

카드 클릭 중:

- 중복 클릭 방지
- Navigation Loading 상태 가능

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/dashboard/
│   ├── components/
│   │   └── FlashcardStudyCard.kt
│   └── DashboardScreen.kt
├── core/navigation/
│   └── Route.kt
└── domain/model/learningstate/
    └── LearningSummaryModels.kt
```

> Dashboard 카드는 `presentation/dashboard/components`에 둔다.
> Flashcard 학습 화면 이동 route는 기존 팀 구조에 맞춰 `core/navigation`에서 관리한다.

---

## 권장 컴포넌트 구조

```text
FlashcardStudyCard
```

---

## 권장 파라미터 예시

```kotlin
@Composable
fun FlashcardStudyCard(

    summary: DashSummary,

    selectedLearningLanguage: String,

    onClick: (String) -> Unit
)
```

---

## 권장 Navigation 전달 값

```kotlin
language: String // selectedLearningLanguage
```

---

# FlashcardStudyCard 예시 데이터

```json
{
  "language":"en",

  "dueFlashcards":14,

  "recentSavedFlashcards":3
}
```

---

# UI 정책

## 카드 우선순위

Dashboard 중단~하단 영역 배치 권장.

이유:

```text
교정 이후 반복 학습 유도
```

역할 수행.

---

## 카드 클릭 영역

카드 전체 clickable 처리 권장.

---

## Skeleton 정책

Dashboard preload 중:

- 카드 placeholder 표시 가능
- 카드 수 placeholder 표시 가능

---

# Error 정책

## Fatal Error

- summary null
- selectedLearningLanguage null
- summary.language와 selectedLearningLanguage 불일치

→ 카드 렌더링 생략 가능.

---

## Transient Error

- Navigation 실패

→ Snackbar 표시 가능.

---

# Edge Cases

- dueFlashcards 0
- recentSavedFlashcards 0
- selectedLearningLanguage null
- summary.language와 selectedLearningLanguage 불일치
- 카드 클릭 연타
- Navigation 실패
- 특정 언어 카드만 존재하지만 현재 선택 언어 카드는 없는 상태
- Flashcard 데이터 일부 손상

---

# Related

## PR

- PR: #

---

## API / SDK

- Navigation Compose

---

## Design(Figma)

### 필요 화면

- Flashcard Study Card
- Card Loading Skeleton
- Empty Flashcard Card

---

## 와이어프레임 체크

- 카드 배치 위치
- 카드 클릭 영역
- Empty 상태 메시지
- Skeleton placeholder 구조
- dueFlashcards 강조 방식

---

# 테스트 시나리오

## 정상 흐름

1. Dashboard 진입
2. Flashcard 카드 출력
3. 카드 클릭
4. Flashcard 학습 화면 이동
5. selectedLearningLanguage 전달 확인

---

## Empty 흐름

1. Flashcard 없음
2. Empty 카드 출력 확인

---

## 실패 흐름

1. Navigation 실패
2. selectedLearningLanguage null
3. summary null
4. 카드 중복 클릭

---

## 검토 후 수정 메모

- `DASH-004`는 여러 언어의 Flashcard 카드를 동시에 렌더링하지 않는다.
- 카드 데이터는 `DASH-001`에서 로드된 `DashSummary[selectedLearningLanguage]`를 사용한다.
- Flashcard 학습 화면에는 현재 앱 컨텍스트인 `selectedLearningLanguage`를 전달한다.
- SRS 알고리즘과 실제 카드 학습 로직은 이 이슈 범위에서 제외한다.
- 학습 언어 변경은 `DASH-006`에서 처리한다.

---

# Labels

```text
type: feature
domain: dashboard
priority: medium
sprint: week1
```
