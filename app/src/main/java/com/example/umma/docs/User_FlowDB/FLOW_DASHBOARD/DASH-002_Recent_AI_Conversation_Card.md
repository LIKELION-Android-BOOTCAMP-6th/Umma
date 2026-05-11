# [Feature] DASH-002 최근 AI 대화 카드

## User Story

사용자는 Dashboard에서 최근 AI 대화 정보를 확인하고,
AI Chat 화면으로 빠르게 이동할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 최근 AI 대화 카드가 정상 출력된다.
- [ ] 현재 선택 언어가 최근 대화 카드에 표시된다.
- [ ] 최근 대화 주제가 표시된다.
- [ ] 최근 대화 시간이 표시된다.
- [ ] 카드 클릭 시 AI Chat 화면으로 이동한다.
- [ ] 현재 선택된 언어가 AI Chat 초기 상태에 반영된다.
- [ ] 최근 대화 데이터가 없을 경우 Empty 상태가 표시된다.
- [ ] 카드 클릭 중 중복 Navigation이 방지된다.

---

# Flow (링크)

- FLOW-DASHBOARD
- DASH-002 → 최근 AI 대화 카드

---

# 구현 범위

## 포함 범위

- 최근 AI 대화 카드 UI
- 현재 선택 언어의 LanguageDashboardSummary 데이터 렌더링
- 최근 대화 정보 출력
- AI Chat 화면 이동 처리
- Empty 상태 처리
- Navigation Loading 처리

---

## 제외 범위 (Out of Scope)

- AI Chat 기능 자체
- Realtime API 연결
- recentFullContext 전체 조회
- Session Memory turn list 렌더링
- AI 응답 생성
- 언어 변경 기능

> Dashboard preload 및 Summary fetch는 DASH-001에서 선행 처리된 상태를 전제로 한다.

---

# Details

## 카드 역할

최근 AI 대화 카드는:

```text
현재 선택된 언어에서
가장 최근에 어떤 주제로 대화했는지
```

빠르게 보여준다.

또한:

```text
AI Chat 재진입 CTA
```

역할을 수행한다.

---

## 표시 예시

```text
오늘 영어로 여행 주제로 대화하셨습니다.
```

---

## CTA

```text
AI 대화하러 가기
```

---

## 사용 데이터

### LanguageDashboardSummary

`DASH-001`에서 로드된 `DashboardSummary[selectedLearningLanguage]`를 사용한다.

```json
{
  "language":"en",

  "recentConversationMinutes":12,

  "recentConversationTopic":"Travel"
}
```

---

## 사용 필드

- selectedLearningLanguage
- language
- recentConversationTopic
- recentConversationMinutes

---

## 카드 표시 정책

### recentConversationTopic 존재 시

```text
오늘 영어로 여행 주제로 대화하셨습니다.
```

---

### recentConversationTopic null 시

```text
최근 영어로 대화한 기록이 있습니다.
```

---

### recentConversationMinutes 표시 정책

시간은:

```text
약 12분
```

형태로 반올림 출력 가능.

정확한 초 단위 출력은 사용하지 않는다.

---

## 현재 선택 언어 정책

최근 AI 대화 카드는:

```text
DashboardSummary[selectedLearningLanguage]
```

기반으로 렌더링된다.

예:

```text
selectedLearningLanguage = "en"
→ English RecentConversationCard 렌더링
```

여러 언어 카드를 동시에 렌더링하지 않는다.
학습 언어 변경은 `DASH-006`에서 처리한다.

---

## Navigation 정책

카드 클릭 시:

```text
Dashboard
→ AI Chat
```

이동 수행.

---

## AI Chat 초기 상태 정책

AI Chat 진입 시:

```text
selectedLearningLanguage
```

를 초기 대화 언어로 전달한다.

예:

```text
selectedLearningLanguage = "en"
→ AI Chat 진입
→ English AI Chat 시작
```

---

## Empty 정책

최근 대화 데이터가 없는 경우:

```text
아직 최근 대화 기록이 없습니다.
AI와 첫 대화를 시작해보세요!
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
│   │   └── RecentConversationCard.kt
│   └── DashboardScreen.kt
├── core/navigation/
│   └── Route.kt
└── domain/model/
    └── LanguageDashboardSummaryVO.kt
```

> Dashboard 카드는 `presentation/dashboard/components`에 둔다.
> 화면 이동 route 정의는 기존 팀 구조에 맞춰 `core/navigation`에서 관리한다.

---

## 권장 컴포넌트 구조

```text
RecentConversationCard
```

---

## 권장 파라미터 예시

```kotlin
@Composable
fun RecentConversationCard(

    summary: LanguageDashboardSummaryVO,

    selectedLearningLanguage: String,

    onClick: (String) -> Unit
)
```

---

## 권장 Navigation 전달 값

```kotlin
language: String
```

---

# RecentConversationCard 예시 데이터

```json
{
  "language":"en",

  "recentConversationMinutes":12,

  "recentConversationTopic":"Travel"
}
```

---

# UI 정책

## 카드 우선순위

Dashboard 상단 영역에 우선 배치 가능.

이유:

```text
AI Chat 재진입 유도
```

가 Dashboard 핵심 행동 중 하나이기 때문.

---

## 카드 클릭 영역

카드 전체 clickable 처리 권장.

---

## Skeleton 정책

Dashboard preload 중:

- 카드 placeholder 표시 가능
- topic placeholder 표시 가능

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

- recentConversationTopic null
- recentConversationMinutes 0
- selectedLearningLanguage null
- summary.language와 selectedLearningLanguage 불일치
- 카드 클릭 연타
- Navigation 실패
- Summary 일부만 존재
- Dashboard preload 중 카드 클릭
- 특정 언어 Summary만 존재하지만 현재 선택 언어 Summary는 없는 상태

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

- Recent Conversation Card
- Card Loading Skeleton
- Empty Conversation Card

---

## 와이어프레임 체크

- 카드 배치 위치
- 카드 클릭 영역
- topic 길이 제한
- Empty 상태 메시지
- Skeleton placeholder 구조

---

# 테스트 시나리오

## 정상 흐름

1. Dashboard 진입
2. 최근 AI 대화 카드 출력
3. 카드 클릭
4. AI Chat 이동
5. selectedLearningLanguage 전달 확인

---

## Empty 흐름

1. 신규 사용자 로그인
2. 최근 대화 없음
3. Empty 카드 출력 확인

---

## 실패 흐름

1. Navigation 실패
2. selectedLearningLanguage null
3. summary null
4. 카드 중복 클릭

---

## 검토 후 수정 메모

- `DASH-002`는 여러 언어의 최근 대화 카드를 동시에 렌더링하지 않는다.
- 카드 데이터는 `DASH-001`에서 로드된 `DashboardSummary[selectedLearningLanguage]`를 사용한다.
- AI Chat으로 전달하는 값은 카드 목록의 개별 `language`가 아니라 현재 앱 컨텍스트인 `selectedLearningLanguage`이다.
- 학습 언어 변경은 `DASH-006`에서 처리한다.

---

# Labels

```text
type: feature
domain: dashboard
priority: medium
sprint: week1
```
