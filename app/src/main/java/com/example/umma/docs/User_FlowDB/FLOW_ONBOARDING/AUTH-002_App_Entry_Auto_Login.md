# [Feature] AUTH-002 앱 진입 및 자동 로그인 처리

## User Story

사용자는 앱 실행 시 로그인 상태에 따라 적절한 화면으로 자동 진입할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 앱 실행 시 Splash 화면이 표시된다.
- [ ] Firebase 현재 로그인 상태를 확인할 수 있다.
- [ ] 로그인된 사용자일 경우 Dashboard 화면으로 자동 이동한다.
- [ ] 로그인되지 않은 사용자일 경우 Onboarding 화면으로 이동한다.
- [ ] 세션 확인 중 Loading 상태가 표시된다.
- [ ] 세션 확인 완료 전까지 화면 전환이 발생하지 않는다.
- [ ] 자동 로그인 상태에서 앱 재실행 시 세션이 유지된다.
- [ ] 유효하지 않은 세션은 자동 제거된다.
- [ ] 세션 확인 실패 시 재시도 가능한 에러 상태를 표시한다.
- [ ] 세션 확인 로직은 Dashboard 데이터 preload 또는 학습 언어 preload를 수행하지 않는다.

---

# Flow (링크)

- FLOW-ONBOARDING
- AUTH-002 → 앱 진입 및 자동 로그인 처리

---

# 구현 범위

## 포함 범위

- Splash 화면 구성
- FirebaseAuth.currentUser 확인
- 자동 로그인 처리
- 로그인 여부에 따른 Navigation 처리
- 세션 Loading 상태 처리
- 세션 Error 상태 처리

---

## 제외 범위 (Out of Scope)

- 로그아웃 기능 (AUTH-003에서 구현)
- 사용자 프로필 수정
- Initial Setup 입력 및 저장 (AUTH-001 / AUTH-004에서 구현)
- UserLearningPreference preload
- selectedLearningLanguage 결정
- Language State preload
- Dashboard 데이터 preload

> Firebase Auth 연동 환경은 SYS-COMMON-INFRA에서 선행 구성된 상태를 전제로 한다.

---

# Details

## 앱 진입 정책

앱 실행 시:

```text
앱 실행
→ Splash 표시
→ Firebase 세션 확인
→ 로그인 상태 분기
```

---

## Navigation 정책

### 로그인 상태

```text
FirebaseAuth.currentUser != null
→ Dashboard route 이동
```

Dashboard route 진입 이후의 UserLearningPreference preload, selectedLearningLanguage 확인, Dashboard Summary preload는 Dashboard Flow의 책임이다.

세션은 존재하지만 Initial Setup이 완료되지 않은 사용자의 Dialog 표시와 저장 처리는 AUTH-001 / AUTH-004의 책임이다.

### 비로그인 상태

```text
FirebaseAuth.currentUser == null
→ Onboarding 화면 이동
```

---

## Splash 정책

- Splash 화면은 세션 확인 완료 전까지 유지
- 세션 확인 완료 후 화면 전환
- 불필요한 화면 깜빡임(flicker) 방지

---

## Loading 정책

세션 확인 중:

- Splash 화면 유지
- 사용자 Interaction 차단
- Navigation 지연

---

## Error 정책

### Fatal Error

- Firebase 세션 확인 실패
- 세션 데이터 손상

→ Error UI 표시 후 재시도 버튼 제공

### Transient Error

- 네트워크 지연
- Firebase 응답 지연

→ Snackbar 또는 Toast 표시

---

# 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/auth/
│   ├── AppEntryScreen.kt
│   └── AppEntryViewModel.kt
├── domain/repository/
│   └── AuthRepository.kt
├── domain/usecase/
│   └── CheckAuthSessionUseCase.kt
├── data/repository/
│   └── AuthRepositoryImpl.kt
└── data/source/remote/
    └── FirebaseAuthDataSource.kt
```

> 자동 로그인은 인증 세션 존재 여부만 판단한다.
> UI/ViewModel은 `presentation/auth`, 세션 확인 UseCase와 Repository interface는 `domain`,
> FirebaseAuth 구현은 `data`에 둔다.

---

## 권장 상태 구조

```kotlin
sealed interface AppEntryState {
    object Loading
    object Authenticated
    object Unauthenticated
    data class Error(val message: UiText)
}
```

---

## 권장 ViewModel 역할

### AppEntryViewModel

역할:

- 앱 시작 시 세션 확인
- 로그인 상태 분기
- Navigation 이벤트 처리
- 세션 Error 처리

---

# 세션 정책

## 자동 로그인 유지

- FirebaseAuth.currentUser 기반 유지
- 앱 종료 후 재실행 시 자동 로그인
- AUTH-002는 인증 세션 존재 여부만 판단한다.
- 사용자 학습 언어 설정과 Dashboard 데이터 로드는 Dashboard 진입 이후 별도 Flow에서 처리한다.

---

## 세션 무효 처리

다음 상황 시 세션 제거:

- Firebase User null
- 인증 토큰 만료
- 인증 데이터 손상
- Firebase User reload 실패로 계정이 유효하지 않다고 판단되는 경우

---

# Edge Cases

- 앱 실행 중 네트워크 끊김
- FirebaseAuth 응답 지연
- Splash 화면 중 앱 백그라운드 이동
- Firebase User null 반환
- 토큰 만료 상태
- 로그인 세션은 있으나 Initial Setup 미완료
- 로그인 세션은 있으나 UserLearningPreference 없음
- 세션 확인 중 Activity recreate
- Splash 화면 무한 유지
- 이미 로그인 상태인데 Onboarding 진입
- 세션 확인 중 중복 Navigation 발생

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Authentication

---

## Design(Figma)

### 필요 화면

- Splash Screen
- Onboarding Screen
- Dashboard Screen
- Error State

---

## 와이어프레임 체크

- Splash 유지 시간 정의
- 화면 전환 타이밍 정의
- Error 상태 표시 방식 정의
- 자동 로그인 실패 시 UX 정의
- Navigation 중복 방지 정책 정의

---

# 테스트 시나리오

## 정상 흐름

1. 앱 실행
2. Splash 표시
3. Firebase 세션 확인
4. Dashboard route 자동 이동

---

## 비로그인 흐름

1. 앱 실행
2. Splash 표시
3. 세션 없음 확인
4. Onboarding 이동

---

## 실패 흐름

1. Firebase 응답 실패
2. 네트워크 차단
3. 세션 데이터 손상
4. Error 상태 표시 확인

---

## 책임 경계 확인

1. AUTH-002는 Firebase 인증 세션만 확인한다.
2. AUTH-002는 `UserLearningPreference`, `selectedLearningLanguage`, `Language State`, `Dashboard Summary`를 preload하지 않는다.
3. 로그인된 사용자는 Dashboard route로 이동한다.
4. Dashboard route 이후 현재 선택 언어와 Summary 로딩은 FLOW-DASHBOARD / SYS-LEARNING-STATE-INFRA 기준을 따른다.
5. Initial Setup 미완료 사용자의 Dialog 표시와 저장은 AUTH-001 / AUTH-004 기준을 따른다.

---

# Labels

```text
type: feature
domain: auth
priority: high
sprint: week1
```
