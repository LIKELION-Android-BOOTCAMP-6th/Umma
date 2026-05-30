# [Feature] AUTH-003 로그아웃 구현

## User Story

사용자는 현재 로그인된 계정에서 로그아웃할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 설정 화면에서 로그아웃 버튼이 표시된다.
- [ ] 로그아웃 버튼 클릭 시 로그아웃 요청이 수행된다.
- [ ] Firebase 세션이 제거된다.
- [ ] Google 로그인 세션이 함께 해제된다.
- [ ] 로그아웃 완료 후 Onboarding 화면으로 이동한다.
- [ ] 로그아웃 이후 자동 로그인되지 않는다.
- [ ] 로그아웃 진행 중 중복 요청이 방지된다.
- [ ] 로그아웃 실패 시 에러 메시지를 표시한다.
- [ ] 로그아웃 후 전역 사용자 상태(Global State)가 초기화된다.
- [ ] 로그아웃 후 UserLangPref와 selectedLearningLanguage가 현재 앱 세션에서 제거된다.
- [ ] 로그아웃 후 BackStack이 초기화되어 이전 사용자 화면으로 복귀할 수 없다.

---

# Flow (링크)

- FLOW-ONBOARDING
- AUTH-003 → 로그아웃

---

# 구현 범위

## 포함 범위

- 로그아웃 버튼 UI
- Firebase 로그아웃 처리
- Google 세션 해제
- Onboarding 화면 이동 처리
- 전역 사용자 상태 초기화
- Loading/Error 상태 처리

---

## 제외 범위 (Out of Scope)

- 회원 탈퇴
- 계정 전환
- 사용자 프로필 삭제
- Language State 삭제
- Flashcard 데이터 삭제
- UserLangPref 원격 데이터 삭제
- 로컬 영구 학습 데이터 삭제

> Firebase Auth 및 Google 로그인 연동 환경은 SYS-COMMON-INFRA에서 선행 구성된 상태를 전제로 한다.

---

# Details

## 로그아웃 정책

로그아웃 수행 시:

```text
로그아웃 클릭
→ Firebase Session 제거
→ Google Session 해제
→ 현재 사용자 Global State 초기화
→ BackStack 초기화
→ Onboarding 이동
```

---

## 세션 정책

### 로그아웃 완료 시

다음 데이터 초기화:

- 현재 사용자 세션
- GlobalLangState
- UserLangPref
- selectedLearningLanguage
- Dashboard Summary 인메모리 캐시
- 현재 대화 세션 상태
- CurrentUserState

영구 저장된 학습 데이터는 삭제하지 않는다. 단, 현재 앱 세션에서 이전 사용자의 데이터가 화면에 남지 않도록 인메모리 상태와 활성 사용자 컨텍스트는 반드시 비운다.

---

### 유지 데이터

다음 데이터는 Firebase에 유지:

- User Profile
- UserLangPref
- Language State
- Flashcard
- Statistics
- Session Memory

> 로그아웃은 “현재 기기의 로그인 상태 해제”만 수행한다.

---

## Navigation 정책

### 로그아웃 성공 시

```text
로그아웃 완료
→ Onboarding 화면 이동
```

### 뒤로가기 정책

로그아웃 이후:
- 이전 화면으로 복귀 불가
- BackStack 초기화 필요
- Onboarding을 새 root route로 설정

---

## Loading 정책

로그아웃 진행 중:

- 로그아웃 버튼 비활성화
- 중복 클릭 방지
- Progress Indicator 표시

---

## Error 정책

### Fatal Error

- Firebase 세션 제거 실패
- Google Session 해제 실패

→ Error UI 또는 Snackbar 표시

---

### Transient Error

- 네트워크 지연
- Google API 응답 지연

→ Toast 또는 Snackbar 표시

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/auth/
│   └── AuthViewModel.kt
├── domain/repository/
│   ├── AuthRepository.kt
│   └── LearningStateRepo.kt
├── domain/usecase/
│   └── LogoutUseCase.kt
├── domain/usecase/learningstate/
│   └── LearningStateWriteUseCases.kt
├── data/repository/
│   ├── AuthRepositoryImpl.kt
│   └── LearningStateRepoImpl.kt
└── data/source/remote/
    ├── FirebaseAuthDataSource.kt
    └── GoogleAuthDataSource.kt
```

> 로그아웃 UI 이벤트는 `presentation/auth`에서 처리한다.
> 세션 제거와 현재 사용자 학습 상태 초기화는 UseCase로 분리하고, 실제 Firebase/Google 세션 해제는 `data`에 둔다.

---

## 권장 상태 구조

```kotlin
sealed interface LogoutUiState {
    object Idle
    object Loading
    object Success
    data class Error(val message: UiText)
}
```

---

## 권장 ViewModel 역할

### AuthViewModel

역할:

- 로그아웃 요청 처리
- Firebase 세션 제거
- Google 세션 해제
- Global State 초기화
- Navigation Event 처리

---

# Global State 초기화 정책

## 초기화 대상 예시

```text
GlobalLangState
UserLangPref
selectedLearningLanguage
DashSummary in-memory cache
CurrentSessionMemory
CurrentUserState
```

---

## 목적

다른 사용자가 동일 기기에서 로그인할 경우,
이전 사용자 데이터가 UI에 남지 않도록 방지.

## 초기화 범위

| 구분 | 처리 |
| --- | --- |
| Firebase 원격 데이터 | 유지 |
| 로컬 영구 학습 데이터 | 유지 또는 uid 기반 격리 |
| 인메모리 Global State | 초기화 |
| 현재 사용자 컨텍스트 | 초기화 |
| selectedLearningLanguage | 초기화 |
| 진행 중 대화 세션 | 폐기 |

로컬 영구 데이터는 삭제하지 않는 것을 기본으로 하되, 반드시 uid 기준으로 격리되어야 한다.

---

# Edge Cases

- 로그아웃 중 네트워크 끊김
- 로그아웃 버튼 연타
- FirebaseAuth null 상태
- Google Session 해제 실패
- 로그아웃 중 앱 종료
- 로그아웃 후 뒤로가기 시 이전 화면 노출
- 로그아웃 직후 자동 로그인 발생
- Global State 일부 초기화 실패
- UserLangPref가 앱 세션에 남아 있음
- selectedLearningLanguage가 이전 사용자 값으로 남아 있음
- uid 기반 로컬 캐시 격리 실패
- 로그아웃 중 Activity recreate

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

- Settings Screen
- Logout Dialog (선택)
- Loading State
- Error State
- Onboarding Screen

---

## 와이어프레임 체크

- 로그아웃 버튼 위치
- 로그아웃 확인 UX 정의
- Loading 상태 표시 방식
- 뒤로가기 차단 정책 정의
- Error 표시 방식 정의

---

# 테스트 시나리오

## 정상 흐름

1. 로그인 상태 진입
2. 설정 화면 이동
3. 로그아웃 클릭
4. Firebase 세션 제거
5. Google 세션 해제
6. Global State 초기화
7. Onboarding 이동 확인
8. 뒤로가기 시 이전 화면으로 복귀되지 않는지 확인

---

## 실패 흐름

1. 네트워크 차단
2. Firebase 로그아웃 실패
3. Google Session 해제 실패
4. Error 상태 표시 확인

---

## 검토 후 수정 메모

- 로그아웃은 계정 삭제가 아니므로 Firebase 원격 학습 데이터는 유지한다.
- `UserLangPref`와 `selectedLearningLanguage`는 원격 데이터 삭제 대상이 아니라 현재 앱 세션 초기화 대상이다.
- Dashboard Summary, Language State, Flashcard 등 로컬 영구 데이터는 삭제하지 않는 것을 기본으로 하되 uid 기준으로 격리한다.
- Onboarding 이동 시 BackStack을 초기화하여 이전 사용자 화면이 노출되지 않도록 한다.

---

# Labels

```text
type: feature
domain: auth
priority: medium
sprint: week1
```
