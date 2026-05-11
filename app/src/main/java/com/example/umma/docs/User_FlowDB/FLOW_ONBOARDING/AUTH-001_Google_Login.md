# [Feature] AUTH-001 Google 로그인 구현

## User Story

사용자는 Google 계정으로 로그인하여 Umma 서비스를 사용할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Google 로그인 버튼이 표시된다.
- [ ] Google 계정 선택창이 정상 호출된다.
- [ ] Google 인증 성공 시 Firebase 인증이 완료된다.
- [ ] 로그인 성공 시 Firebase User 정보를 획득할 수 있다.
- [ ] 앱 재실행 시 Firebase 세션이 유지된다.
- [ ] 로그인 성공 후 Dashboard 화면으로 이동한다.
- [ ] 최초 사용자일 경우 Initial Setup Dialog가 표시된다.
- [ ] Initial Setup 완료 후 사용자 프로필이 저장된다.
- [ ] 로그인 진행 중 중복 요청이 방지된다.
- [ ] 인증 실패 시 에러 메시지를 표시한다.

---

# Flow (링크)

- FLOW-ONBOARDING
- AUTH-001 → Google 로그인

---

# 구현 범위

## 포함 범위

- Google 로그인 버튼 UI
- Google 로그인 요청 처리
- Firebase 사용자 인증 처리
- Dashboard 화면 이동 처리
- Initial Setup Dialog 표시
- 사용자 프로필 저장
- Loading/Error 상태 처리

---

## 제외 범위 (Out of Scope)

- Google Auth SDK 설정
- Firebase 프로젝트 설정
- Apple 로그인
- 이메일 로그인
- 회원 탈퇴
- 다중 계정 전환
- 프로필 수정 기능

> Google Auth 및 Firebase 연동 환경은 SYS-COMMON-INFRA에서 선행 구성된 상태를 전제로 한다.

---

# Details

## 인증 방식

- Google Sign-In 기반 로그인 사용
- Google Credential을 Firebase Credential로 변환하여 인증 처리

---

## 세션 정책

- FirebaseAuth.currentUser 기반 자동 로그인 사용
- 앱 재실행 시 로그인 상태 유지
- 로그아웃 전까지 세션 유지

---

## Navigation 정책

### 로그인 성공 시

```text
로그인 성공
→ Dashboard 화면 이동
```

---

## Initial Setup 정책

최초 로그인 사용자일 경우:

Dashboard 진입 직후 Initial Setup Dialog 표시.

### Initial Setup 입력 항목

- 사용자 닉네임
- 모국어 선택
- 주 학습 언어 선택
- 관심 대화 주제 5개 선택

### 저장 데이터

#### User Profile

```json
{
  "nickname": "Wanna",
  "interestTopics": [
    "Travel",
    "Movie",
    "Food",
    "Music",
    "Game"
  ]
}
```

#### UserLangPref

```json
{
  "nativeLanguage": "ko",
  "primaryLearningLanguage": "en",
  "selectedLearningLanguage": "en",
  "learningLanguages": ["en"]
}
```

`selectedLearningLanguage`는 Initial Setup 저장 시 `primaryLearningLanguage`와 같은 값으로 초기화한다.

---

## Loading 정책

로그인 진행 중:

- 버튼 비활성화
- 중복 클릭 방지
- CircularProgressIndicator 표시

---

## Error 정책

### Fatal Error

- Firebase 인증 실패
- Credential 변환 실패

→ Error UI 표시

### Transient Error

- 네트워크 지연
- Google 인증 응답 지연

→ Snackbar 또는 Toast 표시

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/auth/
│   ├── SignInScreen.kt
│   └── AuthViewModel.kt
├── domain/model/
│   └── UserProfile.kt
├── domain/repository/
│   └── AuthRepository.kt
├── domain/usecase/
│   ├── SignInWithGoogleUseCase.kt
│   └── ObserveAuthStateUseCase.kt
├── data/repository/
│   └── AuthRepositoryImpl.kt
└── data/source/remote/
    ├── FirebaseAuthDataSource.kt
    └── GoogleAuthDataSource.kt
```

> 팀 공통 아키텍처는 레이어 중심 Clean Architecture를 따른다.
> 로그인 UI/ViewModel은 `presentation/auth`에 두고,
> 인증 인터페이스와 UseCase는 `domain`, Firebase/Google 연동 구현은 `data`에 둔다.

---

## 권장 상태 구조

```kotlin
sealed interface AuthUiState {
    object Idle
    object Loading
    data class Success(val uid: String)
    data class Error(val message: UiText)
}
```

---

## Initial Setup 상태 구조 예시

```kotlin
data class InitialSetupState(
    val nickname: String = "",
    val nativeLanguage: Language? = null,
    val primaryLearningLanguage: Language? = null,
    val selectedTopics: List<Topic> = emptyList()
)
```

---

# Firebase 저장 데이터

## users/{uid}

최초 로그인 성공 시 생성.

```json
{
  "uid": "...",
  "email": "...",
  "displayName": "...",
  "photoUrl": "...",
  "nickname": "...",
  "interestTopics": [
    "Travel",
    "Food",
    "Movie"
  ],
  "createdAt": "timestamp"
}
```

---

## users/{uid}/user_learning_preference/current

Initial Setup 완료 시 생성.

```json
{
  "nativeLanguage": "ko",
  "primaryLearningLanguage": "en",
  "selectedLearningLanguage": "en",
  "learningLanguages": ["en"]
}
```

---

# Edge Cases

- 사용자가 로그인 도중 인증 취소
- 네트워크 끊김
- FirebaseAuth 응답 실패
- 로그인 버튼 연타
- Initial Setup 도중 앱 종료
- 관심 주제 5개 미선택
- nickname 공백 입력
- 모국어 미선택
- 주 학습 언어 미선택
- selectedLearningLanguage 초기화 실패
- Firebase User null 반환
- 이미 로그인된 상태에서 로그인 화면 진입

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Authentication
- Google Sign-In

---

## Design(Figma)

### 필요 화면

- Intro Screen
- Login Screen
- Dashboard Screen
- Initial Setup Dialog
- Loading State
- Error State

---

## 와이어프레임 체크

- Google 로그인 버튼 위치
- Initial Setup Dialog 레이아웃
- 관심 주제 선택 방식
- 로딩 상태 위치
- Error 표시 방식
- Dialog dismiss 정책 정의

---

# 테스트 시나리오

## 정상 흐름

1. 앱 실행
2. Google 로그인
3. Dashboard 진입
4. Initial Setup Dialog 표시
5. 닉네임/모국어/주 학습 언어/관심주제 입력
6. 저장 완료

---

## 실패 흐름

1. 로그인 취소
2. 네트워크 차단
3. Firebase 인증 실패
4. Initial Setup 저장 실패

---

# Labels

```text
type: feature
domain: auth
priority: high
sprint: week1
```
