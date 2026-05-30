# [Feature] DASH-006 Dashboard 학습 언어 Selector

## User Story

사용자는 Dashboard에서 현재 학습 언어를 확인하고,
학습 중인 다른 언어로 전환하여 해당 언어의 학습 상태를 볼 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Dashboard 상단에 현재 선택된 학습 언어가 표시된다.
- [ ] 사용자는 학습 중인 언어 목록을 확인할 수 있다.
- [ ] 사용자는 학습 중인 언어 중 하나를 선택할 수 있다.
- [ ] 언어 선택 시 selectedLearningLanguage가 갱신된다.
- [ ] selectedLearningLanguage 변경 후 해당 언어의 Dashboard Summary가 로드된다.
- [ ] Dashboard의 모든 카드가 변경된 언어 기준으로 다시 렌더링된다.
- [ ] 언어 변경 저장 중 중복 요청이 방지된다.
- [ ] 언어 변경 실패 시 이전 selectedLearningLanguage가 유지된다.
- [ ] selectedLearningLanguage가 없는 경우 primaryLearningLanguage로 fallback된다.
- [ ] learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.

---

# Flow (링크)

- FLOW-DASHBOARD
- DASH-006 → Dashboard 학습 언어 selector 및 selectedLearningLanguage 변경

---

# 구현 범위

## 포함 범위

- 학습 언어 Selector UI
- 현재 선택 언어 표시
- learningLanguages 목록 렌더링
- selectedLearningLanguage 변경 처리
- UserLangPref local update
- UserLangPref Firebase sync
- 변경된 언어의 Dashboard Summary fetch
- Dashboard 카드 재렌더링
- Loading/Error 상태 처리

---

## 제외 범위 (Out of Scope)

- 추가 학습 언어 등록
- 학습 언어 삭제
- primaryLearningLanguage 변경
- nativeLanguage 변경
- 언어별 상세 설정 화면
- Dashboard 카드 개별 구현
- AI Chat / Correction / Flashcard / Statistics 기능 자체

> UserLangPref와 Language Scoped Summary 구조는 SYS-LEARNING-STATE-INFRA에서 선행 정의된 상태를 전제로 한다.

---

# Details

## Selector 역할

학습 언어 Selector는:

```text
Dashboard와 이후 이동하는 기능들이 사용할 현재 언어 컨텍스트
```

를 결정한다.

---

## 표시 예시

```text
English
```

또는:

```text
English ▼
```

---

## 사용 데이터

### UserLangPref

```json
{
  "nativeLanguage": "ko",
  "primaryLearningLanguage": "en",
  "selectedLearningLanguage": "en",
  "learningLanguages": ["en", "ja"]
}
```

---

## 사용 필드

- selectedLearningLanguage
- learningLanguages
- primaryLearningLanguage

---

## 핵심 개념

`language`와 `selectedLearningLanguage`는 다르다.

| 구분 | 역할 |
| --- | --- |
| language | Session / Flashcard / Statistics / Language State가 어떤 언어의 데이터인지 나타내는 소속 필드 |
| selectedLearningLanguage | 현재 Dashboard와 기능 이동이 바라보는 앱 전역 언어 컨텍스트 |

---

## 변경 정책

사용자가 Selector에서 언어를 변경하면:

```text
언어 선택
→ selectedLearningLanguage local update
→ DashSummary[selectedLearningLanguage] Local Cache fetch
→ Dashboard 카드 재렌더링
→ UserLangPref Firebase background sync
→ DashSummary[selectedLearningLanguage] Firebase background sync
→ 변경사항 존재 시 UI 갱신
```

---

## 실패 정책

언어 변경 저장 실패 시:

```text
이전 selectedLearningLanguage 유지
→ Snackbar 표시
```

Firebase background sync만 실패한 경우:

```text
Local selectedLearningLanguage 유지
→ 다음 sync 시 재시도
→ Snackbar 또는 non-blocking error 표시
```

---

## fallback 정책

### selectedLearningLanguage 없음

```text
selectedLearningLanguage == null
→ primaryLearningLanguage 사용
→ selectedLearningLanguage를 primaryLearningLanguage로 복구 저장 시도
```

### primaryLearningLanguage도 없음

```text
primaryLearningLanguage == null
→ Initial Setup 필요 상태로 전환
```

---

## Dashboard 카드 반영 정책

selectedLearningLanguage 변경 후 다음 카드들은 모두 변경된 언어 기준으로 렌더링된다.

- 최근 AI 대화 카드
- 교정 대기 카드
- Flashcard 학습 카드
- 언어 성취율 카드

예:

```text
selectedLearningLanguage = "ja"
→ dashboardSummaries["ja"] 렌더링
→ AI Chat 진입 시 "ja" 전달
→ Correction 진입 시 "ja" 전달
→ Flashcard 진입 시 "ja" 전달
→ Statistics 진입 시 "ja" 전달
```

---

## 추가 학습 언어 정책

MVP에서는 `learningLanguages`에 이미 존재하는 언어만 선택 가능하다.

추가 학습 언어 등록은 별도 Flow에서 처리한다.

---

## Loading 정책

언어 변경 중:

- Selector interaction 비활성화 가능
- 중복 선택 방지
- 변경 대상 언어의 Summary placeholder 표시 가능

---

## Error 정책

### Fatal Error

- UserLangPref load 실패
- learningLanguages 없음
- primaryLearningLanguage 없음

→ Error UI 또는 Initial Setup 필요 상태 표시

---

### Transient Error

- selectedLearningLanguage Firebase sync 실패
- Dashboard Summary background sync 실패

→ 이전 또는 Local Cache 데이터 유지 후 Snackbar 표시

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/dashboard/
│   ├── components/
│   │   └── LearningLanguageSelector.kt
│   ├── DashboardScreen.kt
│   └── DashboardViewModel.kt
├── domain/model/learningstate/
│   ├── LearningSummaryModels.kt
│   └── LearningProfileModels.kt
├── domain/repository/
│   └── LearningStateRepo.kt
├── domain/usecase/learningstate/
│   ├── LearningStateReadUseCases.kt
│   └── LearningStateWriteUseCases.kt
├── data/repository/
│   └── LearningStateRepoImpl.kt
├── data/source/local/
│   └── UserLangPrefLocalDataSource.kt
└── data/source/remote/
    └── UserLangPrefRemoteDataSource.kt
```

> Selector UI/ViewModel은 `presentation/dashboard`에 둔다.
> 언어 변경 로직은 UseCase를 통해 처리하고, 저장 구현은 `data` 레이어에 둔다.

---

## 권장 컴포넌트 구조

```text
LearningLanguageSelector
```

---

## 권장 파라미터 예시

```kotlin
@Composable
fun LearningLanguageSelector(
    selectedLang: Language,
    learningLangs: List<Language>,
    isLoading: Boolean,
    onLanguageSelected: (Language) -> Unit
)
```

---

## 권장 상태 구조

```kotlin
data class DashboardLanguageSelectorState(
    val selectedLang: Language? = null,
    val primaryLang: Language? = null,
    val learningLangs: List<Language> = emptyList(),
    val isChangingLanguage: Boolean = false,
    val errorMessage: UiText? = null
)
```

---

## 권장 ViewModel 역할

### DashboardViewModel

역할:

- UserLangPref 상태 관리
- selectedLearningLanguage 변경 처리
- 변경된 언어의 Dashboard Summary fetch 요청
- 언어 변경 중복 요청 방지
- 언어 변경 실패 시 rollback 처리
- Dashboard 카드 재렌더링 트리거

---

# Firebase 저장 데이터

## users/{uid}/user_learning_preference/current

```json
{
  "nativeLanguage": "ko",
  "primaryLearningLanguage": "en",
  "selectedLearningLanguage": "ja",
  "learningLanguages": ["en", "ja"],
  "updatedAt": "timestamp"
}
```

---

# Edge Cases

- selectedLearningLanguage null
- primaryLearningLanguage null
- learningLanguages empty
- selectedLearningLanguage가 learningLanguages에 포함되지 않음
- 선택한 언어의 Dashboard Summary 없음
- 선택한 언어의 Local Cache 손상
- Firebase sync 실패
- 언어 변경 중 앱 종료
- 언어 변경 버튼 연타
- 동일 언어 재선택
- 언어 변경 직후 카드 클릭
- 특정 언어 데이터만 존재하는 상태

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- DataStore / Room

---

## Design(Figma)

### 필요 화면

- Dashboard Screen
- Learning Language Selector
- Selector Loading State
- Selector Error State
- Dashboard Card Placeholder

---

## 와이어프레임 체크

- Selector 위치
- 현재 언어 표시 방식
- 언어 목록 표시 방식
- 선택 중 Loading 표시 방식
- 변경 실패 시 Snackbar 표시 방식
- 언어명이 길 때 UI 처리

---

# 테스트 시나리오

## 정상 흐름

1. Dashboard 진입
2. 현재 selectedLearningLanguage 표시
3. 학습 언어 목록 열기
4. 다른 학습 언어 선택
5. selectedLearningLanguage 변경
6. 선택 언어의 Dashboard Summary 로드
7. Dashboard 카드들이 선택 언어 기준으로 재렌더링됨

---

## fallback 흐름

1. selectedLearningLanguage 없음
2. primaryLearningLanguage 확인
3. selectedLearningLanguage를 primaryLearningLanguage로 복구
4. Dashboard Summary 로드

---

## 실패 흐름

1. UserLangPref load 실패
2. learningLanguages empty
3. Firebase sync 실패
4. 선택 언어 Summary fetch 실패
5. 이전 selectedLearningLanguage 유지 또는 fallback 확인

---

# Labels

```text
type: feature
domain: dashboard
priority: high
sprint: week1
```
