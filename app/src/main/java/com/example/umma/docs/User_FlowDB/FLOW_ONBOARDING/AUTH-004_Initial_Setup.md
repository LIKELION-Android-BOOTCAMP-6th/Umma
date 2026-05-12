# [Feature] AUTH-004 초기 사용자 설정 구현

## User Story

최초 로그인한 사용자는 기본 학습 정보를 설정하여 Umma 서비스를 시작할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 최초 로그인 사용자에게 Initial Setup Dialog가 표시된다.
- [ ] 사용자가 닉네임을 입력할 수 있다.
- [ ] 사용자가 모국어를 선택할 수 있다.
- [ ] 사용자가 주 학습 언어를 선택할 수 있다.
- [ ] 사용자가 관심 대화 주제 5개를 선택할 수 있다.
- [ ] 설정 완료 시 사용자 프로필이 Firebase에 저장된다.
- [ ] 설정 완료 시 UserLangPref가 Firebase에 저장된다.
- [ ] selectedLearningLanguage가 primaryLearningLanguage와 같은 값으로 초기화된다.
- [ ] 주 학습 언어 기준 Language State 기본값이 생성된다.
- [ ] 주 학습 언어 기준 Dashboard Summary 기본값이 생성된다.
- [ ] 주 학습 언어 기준 Session Memory 기본값이 생성된다.
- [ ] Initial Setup 완료 후 Dialog가 닫힌다.
- [ ] 설정 완료 이후 다시 Dialog가 표시되지 않는다.
- [ ] 입력값 검증이 수행된다.
- [ ] 저장 중 중복 요청이 방지된다.
- [ ] 저장 실패 시 재시도 가능하다.

---

# Flow (링크)

- FLOW-ONBOARDING
- AUTH-004 → 초기 사용자 설정

---

# 구현 범위

## 포함 범위

- Initial Setup Dialog UI
- 닉네임 입력
- 모국어 선택
- 주 학습 언어 선택
- 관심 대화 주제 선택
- 사용자 프로필 저장
- UserLangPref 저장
- Language State 기본값 생성
- Dashboard Summary 기본값 생성
- Session Memory 기본값 생성
- 최초 사용자 여부 판별
- Loading/Error 상태 처리

---

## 제외 범위 (Out of Scope)

- 프로필 수정 기능
- 프로필 이미지 설정
- 추가 학습 언어 등록
- 언어 수준 테스트
- AI 기반 관심사 추천

> Firebase Auth 및 사용자 세션은 AUTH-001에서 처리된 상태를 전제로 한다.

---

# Details

## Initial Setup 표시 정책

최초 로그인 사용자일 경우:

```text
Dashboard 진입
→ Initial Setup Dialog 표시
```

---

## 최초 사용자 판별 기준

다음 조건 중 하나 만족 시:

```text
users/{uid} 문서 없음
또는
isSetupCompleted == false
또는
users/{uid}/user_learning_preference/current 문서 없음
```

→ Initial Setup 표시.

---

## 입력 항목

### 1. 닉네임

- 공백 입력 불가
- 최대 글자 수 제한 필요
- 특수문자 제한 여부 검토

---

### 2. 모국어 선택

모국어는 힌트, 교정 카드 앞면, 제한적 모국어 보조에 사용한다.

MVP 지원 모국어 예시:

- Korean

---

### 3. 주 학습 언어 선택

MVP 지원 언어 예시:

- English
- Japanese

---

### 4. 관심 대화 주제 선택

예시:

- Travel
- Food
- Movie
- Music
- Game
- Daily Conversation
- Business
- Study

---

## 선택 정책

- 정확히 5개 선택 필요
- 선택 부족 시 완료 버튼 비활성화
- 모국어와 주 학습 언어를 모두 선택해야 완료 버튼 활성화
- MVP에서는 주 학습 언어 1개만 선택 가능

---

## 저장 정책

설정 완료 시:

```text
사용자 프로필 저장
→ UserLangPref 저장
→ Language State 기본값 생성
→ Session Memory 기본값 생성
→ Dashboard Summary 기본값 생성
→ selectedLearningLanguage = primaryLearningLanguage 설정
→ Dashboard 상태 갱신
→ Dialog 종료
```

저장 작업은 가능한 한 batch/transaction 형태로 처리한다.

Firestore 문서 일부만 생성된 경우 재시도 시 같은 uid 기준으로 덮어쓰기 또는 보정 가능해야 한다.

---

## Loading 정책

저장 진행 중:

- 완료 버튼 비활성화
- 중복 클릭 방지
- Progress Indicator 표시

---

## Error 정책

### Fatal Error

- Firebase 저장 실패
- 사용자 문서 생성 실패

→ Error UI 또는 Snackbar 표시

---

### Transient Error

- 네트워크 지연
- Firestore 응답 지연

→ Toast 또는 Snackbar 표시

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/onboarding/
│   ├── InitialSetupDialog.kt
│   └── InitialSetupViewModel.kt
├── domain/model/
│   └── UserProfile.kt
├── domain/model/learningstate/
│   ├── LearningCoreModels.kt
│   ├── LearningStateModels.kt
│   ├── LearningSummaryModels.kt
│   └── LearningProfileModels.kt
├── domain/repository/
│   ├── UserProfileRepository.kt
│   └── LearningStateRepo.kt
├── domain/usecase/
│   └── CompleteInitialSetupUseCase.kt
├── domain/usecase/learningstate/
│   └── LearningStateWriteUseCases.kt
├── data/repository/
│   ├── UserProfileRepositoryImpl.kt
│   └── LearningStateRepoImpl.kt
└── data/source/remote/
    ├── UserProfileRemoteDataSource.kt
    └── LearningStateRemoteDataSource.kt
```

> Initial Setup은 사용자 입력 UI만 `presentation/onboarding`에 둔다.
> UserProfile, UserLangPref, Language State, Session Memory, Dashboard Summary 생성 로직은 UseCase에서 묶고,
> Firestore 저장 구현은 `data` 레이어에 둔다.

---

## 권장 상태 구조

```kotlin
data class InitialSetupState(
    val nickname: String = "",
    val nativeLanguage: Language? = null,
    val primaryLearningLanguage: Language? = null,
    val selectedTopics: List<Topic> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null
)
```

---

## 권장 ViewModel 역할

### InitialSetupViewModel

역할:

- 입력 상태 관리
- 입력값 검증
- 사용자 프로필 저장
- UserLangPref 저장
- Language State 초기 생성
- Session Memory 초기 생성
- Dashboard Summary 초기 생성
- Dialog 종료 이벤트 처리

---

# Firebase 저장 데이터

## users/{uid}

```json
{
  "uid": "...",
  "email": "...",
  "displayName": "...",
  "nickname": "Wanna",
  "interestTopics": [
    "Travel",
    "Movie",
    "Food",
    "Music",
    "Game"
  ],
  "isSetupCompleted": true,
  "createdAt": "timestamp"
}
```

---

## users/{uid}/user_learning_preference/current

```json
{
  "nativeLanguage": "ko",
  "primaryLearningLanguage": "en",
  "selectedLearningLanguage": "en",
  "learningLanguages": ["en"],
  "updatedAt": "timestamp"
}
```

`selectedLearningLanguage`는 Initial Setup 완료 시 `primaryLearningLanguage`와 같은 값으로 저장한다.

---

# Language State 초기화 정책

Initial Setup 완료 시:

주 학습 언어 기준 기본 Language State 생성.

예시:

```json
{
  "language": "en",
  "internalMetrics": {
    "grammarAccuracy": 0.0,
    "vocabularyAppropriateness": 0.0,
    "lexicalDiversity": 0.0,
    "vocabularyLevel": "A1",
    "sentenceComplexity": 0.0,
    "speechRate": 0.0,
    "pauseFrequency": 0.0,
    "avgUtteranceLength": 0.0,
    "spokenNaturalness": 0.0,
    "naturalExpressionUsage": 0.0,
    "errorRecurrence": 0.0,
    "reviewRetention": 0.0
  },
  "externalMetrics": {
    "vocabularyLevel": "A1",
    "grammarAccuracy": 0.0,
    "expressionRange": 0,
    "fluencyScore": 0.0,
    "naturalnessScore": 0.0
  },
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

> 초기값은 이후 SYS-LEARNING-STATE-INFRA 정책에 따라 확장 가능.

---

# Session Memory 초기화 정책

Initial Setup 완료 시:

주 학습 언어 기준 재사용 Session Memory 기본값을 생성한다.

예시:

```json
{
  "id": "en",
  "language": "en",
  "recentFullContext": [],
  "recentTopics": [],
  "topicSummaries": [],
  "topicKeySentences": [],
  "correctionAvailable": false,
  "recentConversationMinutes": 0,
  "lastCompressedAt": null,
  "updatedAt": "timestamp"
}
```

Session Memory는 대화 1회마다 새로 생성되는 문서가 아니라,
현재 선택 언어의 AI Chat / Correction / Dashboard가 함께 참조하는 언어별 재사용 세션 문서이다.

---

# Dashboard Summary 초기화 정책

Initial Setup 완료 시:

주 학습 언어 기준 기본 Dashboard Summary를 생성한다.

예시:

```json
{
  "language": "en",
  "recentConversationMinutes": 0,
  "recentConversationTopic": null,
  "correctionAvailable": false,
  "dueFlashcards": 0,
  "recentSavedFlashcards": 0,
  "grammarScoreDelta": 0,
  "fluencyScoreDelta": 0,
  "vocabularyScoreDelta": 0,
  "naturalnessScoreDelta": 0,
  "updatedAt": "timestamp"
}
```

Dashboard는 이 Summary를 기반으로 Empty 상태를 렌더링한다.

---

# Edge Cases

- 닉네임 공백 입력
- 관심 주제 5개 미선택
- 모국어 미선택
- 주 학습 언어 미선택
- 저장 버튼 연타
- 저장 중 앱 종료
- Firebase write 실패
- Dashboard 진입 직후 Dialog 중복 표시
- UserLangPref 생성 실패
- selectedLearningLanguage 초기화 실패
- Language State 생성 실패
- Dashboard Summary 생성 실패
- 주 학습 언어 null 상태 저장 시도
- Firestore 문서 일부만 생성된 상태

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore

---

## Design(Figma)

### 필요 화면

- Initial Setup Dialog
- Topic Select UI
- Loading State
- Error State
- Dashboard Screen

---

## 와이어프레임 체크

- Dialog 크기 및 dismiss 정책 정의
- Topic 선택 UI 방식 정의
- 완료 버튼 활성화 조건 정의
- 저장 중 Loading 표시 위치 정의
- Error 메시지 표시 방식 정의

---

# 테스트 시나리오

## 정상 흐름

1. 최초 로그인
2. Dashboard 진입
3. Initial Setup Dialog 표시
4. 닉네임 입력
5. 모국어 선택
6. 주 학습 언어 선택
7. 관심 주제 5개 선택
8. 저장 완료
9. UserLangPref / Language State / Session Memory / Dashboard Summary 생성 확인
10. Dialog 종료 확인

---

## 실패 흐름

1. 네트워크 차단
2. Firestore 저장 실패
3. Topic 미선택 상태 저장 시도
4. Error 상태 표시 확인

---

## 검토 후 수정 메모

- 기존 `targetLanguage`, `selectedLanguage` 표현을 제거하고 `nativeLanguage`, `primaryLearningLanguage`, `selectedLearningLanguage`, `learningLanguages` 구조로 수정한다.
- Initial Setup은 사용자 프로필뿐 아니라 UserLangPref, Language State, Session Memory, Dashboard Summary 초기값까지 생성한다.
- MVP에서는 추가 학습 언어 등록은 제외하고, 주 학습 언어 1개만 선택한다.
- `selectedLearningLanguage`는 데이터의 소속 필드가 아니라 현재 앱 언어 컨텍스트이므로 `primaryLearningLanguage`와 같은 값으로 초기화한다.
- Firestore 일부 저장 실패에 대비해 batch/transaction 또는 재시도 가능한 보정 로직이 필요하다.

---

# Labels

```text
type: feature
domain: onboarding
priority: high
sprint: week1
```
