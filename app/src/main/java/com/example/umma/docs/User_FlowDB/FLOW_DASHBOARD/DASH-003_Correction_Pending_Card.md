# [Feature] DASH-003 교정 대기 카드

## User Story

사용자는 Dashboard에서 최근 대화 기록의 교정 가능 상태를 확인하고,
교정 화면으로 빠르게 이동할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 교정 대기 카드가 정상 출력된다.
- [ ] 최근 대화 기록 존재 여부가 반영된다.
- [ ] 현재 선택 언어가 표시된다.
- [ ] 최근 대화 시간이 표시된다.
- [ ] 현재 선택 언어의 재사용 Session Memory에 교정 가능한 대화가 있는지 표시된다.
- [ ] 카드 클릭 시 Correction 화면으로 이동한다.
- [ ] activeSessionId가 교정 화면으로 전달된다.
- [ ] selectedLearningLanguage가 교정 화면으로 전달된다.
- [ ] recentFullContext가 없을 경우 Empty 상태가 표시된다.
- [ ] 카드 클릭 중 중복 Navigation이 방지된다.

---

# Flow (링크)

- FLOW-DASHBOARD
- DASH-003 → 교정 대기 카드

---

# 구현 범위

## 포함 범위

- 교정 대기 카드 UI
- correctionAvailable 상태 렌더링
- 최근 recentFullContext 상태 표시
- Correction 화면 이동 처리
- activeSessionId 전달
- selectedLearningLanguage 전달
- Empty 상태 처리
- Navigation Loading 처리

---

## 제외 범위 (Out of Scope)

- 문장 교정 기능 자체
- recentFullContext 전체 조회
- AI 교정 요청
- Flashcard 저장 기능
- Correction 상세 화면
- recentFullContext 분석 기능

> Dashboard preload 및 Summary fetch는 DASH-001에서 선행 처리된 상태를 전제로 한다.

---

# Details

## 카드 역할

교정 대기 카드는:

```text
현재 선택 언어의 최근 대화 기록이 교정 가능한 상태인지
```

사용자에게 빠르게 알려준다.

또한:

```text
교정 화면 진입 CTA
```

역할을 수행한다.

---

## 표시 예시

```text
영어 대화 기록이 약 12분 있습니다.
```

---

## CTA

```text
문장 교정받으러 가기
```

---

## 사용 데이터

### LanguageDashboardSummary

`DASH-001`에서 로드된 `DashboardSummary[selectedLearningLanguage]`를 사용한다.

```json
{
  "language":"en",

  "recentConversationMinutes":12,

  "activeSessionId":"session_en",

  "correctionAvailable":true
}
```

---

## 사용 필드

- selectedLearningLanguage
- language
- recentConversationMinutes
- activeSessionId
- correctionAvailable

---

## 카드 표시 정책

### correctionAvailable == true

```text
영어 대화 기록이 약 12분 있습니다.
```

출력 가능.

---

### correctionAvailable == false

```text
아직 교정 가능한 대화 기록이 없습니다.
```

출력 가능.

---

## recentFullContext 표시 정책

Dashboard는 Session Memory의 `recentFullContext` 전체를 렌더링하지 않는다.

대신:

```text
현재 선택 언어의 재사용 Session Memory에 교정 가능한 user turn이 있는지
```

만 표시한다.

---

## recentConversationMinutes 정책

시간은:

```text
약 12분
```

형태로 반올림 출력 가능.

정확한 초 단위 출력은 사용하지 않는다.

---

## 현재 선택 언어 정책

교정 대기 카드는:

```text
DashboardSummary[selectedLearningLanguage]
```

기반으로 렌더링된다.

예:

```text
selectedLearningLanguage = "en"
→ English CorrectionPendingCard 렌더링
```

여러 언어 교정 카드를 동시에 렌더링하지 않는다.
학습 언어 변경은 `DASH-006`에서 처리한다.

---

## Navigation 정책

카드 클릭 시:

```text
Dashboard
→ Correction
```

이동 수행.

---

## Correction 화면 전달 정책

Correction 화면으로:

```kotlin
activeSessionId
selectedLearningLanguage
```

를 전달한다.

---

## Correction 초기 상태 정책

Correction 화면은:

```text
activeSessionId 기반 Session Memory 조회
→ recentFullContext에서 user turn 중심 교정 후보 추출
```

전략 사용.

Dashboard에서는 `recentFullContext` preload를 수행하지 않는다.

---

## Empty 정책

교정 가능한 대화 기록이 없는 경우:

```text
아직 교정 가능한 대화 기록이 없습니다.
AI와 먼저 대화를 시작해보세요!
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
│   │   └── CorrectionPendingCard.kt
│   └── DashboardScreen.kt
├── core/navigation/
│   └── Route.kt
└── domain/model/
    └── LanguageDashboardSummaryVO.kt
```

> Dashboard 카드는 `presentation/dashboard/components`에 둔다.
> Correction 이동에 필요한 route/argument는 기존 팀 구조에 맞춰 `core/navigation`에서 관리한다.

---

## 권장 컴포넌트 구조

```text
CorrectionPendingCard
```

---

## 권장 파라미터 예시

```kotlin
@Composable
fun CorrectionPendingCard(

    summary: LanguageDashboardSummaryVO,

    selectedLearningLanguage: String,

    onClick: (String, String) -> Unit
)
```

---

## 권장 Navigation 전달 값

```kotlin
activeSessionId: String
language: String // selectedLearningLanguage
```

---

# CorrectionPendingCard 예시 데이터

```json
{
  "language":"en",

  "recentConversationMinutes":12,

  "activeSessionId":"session_en",

  "correctionAvailable":true
}
```

---

# UI 정책

## 카드 우선순위

Dashboard 중단 영역 배치 권장.

이유:

```text
AI Chat 이후 다음 학습 행동 유도
```

역할 수행.

---

## 카드 클릭 영역

카드 전체 clickable 처리 권장.

---

## Skeleton 정책

Dashboard preload 중:

- 카드 placeholder 표시 가능
- 시간 placeholder 표시 가능

---

# Error 정책

## Fatal Error

- activeSessionId null 상태에서 correctionAvailable == true
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

- correctionAvailable false
- activeSessionId null
- recentConversationMinutes 0
- selectedLearningLanguage null
- summary.language와 selectedLearningLanguage 불일치
- 카드 클릭 연타
- Navigation 실패
- recentFullContext 초기화 상태
- 특정 언어 recentFullContext만 존재하지만 현재 선택 언어 recentFullContext는 없는 상태

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

- Correction Pending Card
- Card Loading Skeleton
- Empty Correction Card

---

## 와이어프레임 체크

- 카드 배치 위치
- 카드 클릭 영역
- Empty 상태 메시지
- Skeleton placeholder 구조
- 교정 가능 상태 표시 방식

---

# 테스트 시나리오

## 정상 흐름

1. Dashboard 진입
2. 교정 대기 카드 출력
3. 카드 클릭
4. Correction 화면 이동
5. activeSessionId 전달 확인
6. selectedLearningLanguage 전달 확인

---

## Empty 흐름

1. 신규 사용자 로그인
2. 교정 가능한 recentFullContext 없음
3. Empty 카드 출력 확인

---

## 실패 흐름

1. Navigation 실패
2. activeSessionId null
3. summary null
4. 카드 중복 클릭

---

## 검토 후 수정 메모

- `DASH-003`은 여러 언어의 교정 대기 카드를 동시에 렌더링하지 않는다.
- 카드 데이터는 `DASH-001`에서 로드된 `DashboardSummary[selectedLearningLanguage]`를 사용한다.
- Correction 화면에는 `activeSessionId`와 `selectedLearningLanguage`를 전달한다.
- `activeSessionId`는 대화 1회마다 생성되는 세션이 아니라 현재 선택 언어의 재사용 Session Memory를 가리킨다.
- Dashboard에서는 `recentFullContext` 전체를 preload하지 않고, Correction 화면이 `activeSessionId` 기준으로 Session Memory를 조회한다.
- 학습 언어 변경은 `DASH-006`에서 처리한다.

---

# Labels

```text
type: feature
domain: dashboard
priority: medium
sprint: week1
```
