# USER_FLOW_MOCK_REAL_DATA_GUIDE

## 1. 목적

이 문서는 팀원이 `mock data`와 `real data`를 오가며 개발할 때,
무엇을 고정하고 무엇을 교체해야 하는지 빠르게 확인할 수 있는 작업 가이드다.

적용 대상은 다음과 같다.

- FLOW-ONBOARDING
- FLOW-DASHBOARD
- FLOW-CORRECTION
- FLOW-AI-CHAT
- FLOW-SRS

핵심 원칙은 하나다.

> UI와 ViewModel은 같은 계약을 바라보고, 데이터 공급자만 교체한다.

이 문서는 특히 `Dashboard`를 대표 예시로 설명한다.
Dashboard는 여러 Summary를 한 화면에서 함께 사용하기 때문에,
한 기능 안의 여러 데이터가 어떻게 한 번에 fake로 바뀌는지 이해하기 좋다.

---

## 2. 용어

- `mock`: 실제 API 대신 쓰는 가짜 데이터나 가짜 동작
- `fixture`: 테스트나 목업을 위해 미리 준비한 고정 샘플 데이터
- `fake`: 실제 interface를 구현한 가짜 구현체
- `stub`: 특정 입력에 대해 미리 정해둔 값을 돌려주는 단순 객체
- `real`: Firebase, Local DB, API처럼 실제 데이터를 처리하는 구현체
- `facade`: 여러 데이터나 기능을 하나의 앞단 인터페이스로 묶어 보여주는 경계
- `build variant`: 같은 코드베이스를 `debug`, `release`처럼 서로 다른 실행 구성으로 나누는 방식
  - 예: `debug`에서는 fake를 쓰고, `release`에서는 real 구현체를 쓰게 분리할 수 있다

Dashboard에서의 facade는 예를 들면:

- `DashSummary`
- `UserLangPref`
- `SessionSummary`
- `FlashcardSummary`

이런 값을 한 번에 다루는 `LearningStateRepo` 같은 경계다.
즉, 화면은 하나의 repository만 바라보고, 내부에서는 여러 Summary를 함께 묶어준다.

현재 코드에서는 `presentation/dashboard/DashboardScreen.kt`가 화면 진입점이고,
실제 바인딩 교체는 `di/RepositoryModule.kt`의 `bindLearningStateRepo()`가 담당한다.
나중에 `DashboardViewModel`이 추가되면 그 ViewModel이 이 repository를 바라보는 구조가 된다.

현재 코드에는 `DashboardViewModel`과 `FakeLearningStateRepo`가 아직 없다.
아래 예시는 팀원이 대시보드 데이터 연결을 시작할 때 추가하거나 교체할 구조를 설명한다.

---

## 3. 무엇을 바꾸고, 무엇을 안 바꾸는가

### 바꾸지 않는 것

- Composable 이름
- ViewModel의 public state 이름
- UseCase의 입력 / 출력 계약
- Domain Model 이름
- 화면 전환 규칙

### 바꾸는 것

- Repository 구현체
- Local / Remote DataSource 구현체
- Prompt 생성기
- fixture
- Hilt module binding

즉, `presentation`과 `domain`은 그대로 두고 `data`와 `di`에서만 바꾼다.

---

## 4. 실제 교체 지점

현재 프로젝트는 이미 Hilt로 경계를 나눠두었다.

- [RepositoryModule.kt](../../di/RepositoryModule.kt): Repository interface와 구현체 연결
- [AIModule.kt](../../di/AIModule.kt): Firebase AI Logic / Gemini Live API 제공
- [DataStoreModule.kt](../../di/DataStoreModule.kt): 학습 상태용 DataStore 제공

즉, 실제 전환은 `di`에서 시작한다.

예:

- `LearningStateRepo` -> `FakeLearningStateRepo` / `LearningStateRepoImpl`
- `ChatRepository` -> `FakeChatRepository` / `ChatRepositoryImpl`
- `CorrectionRepository` -> `FakeCorrectionRepository` / `CorrectionRepositoryImpl`

---

## 5. Hilt 전환 방법

### 5.1 현재 real 바인딩 위치

현재 실제 파일은 `app/src/main/java/com/example/umma/di/RepositoryModule.kt`다.
기본 상태에서는 real 구현체인 `LearningStateRepoImpl`이 연결되어 있다.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLearningStateRepo(
        learningStateRepoImpl: LearningStateRepoImpl
    ): LearningStateRepo
}
```

### 5.2 fake로 전환할 때

대시보드 개발 중 고정된 샘플 데이터가 필요하면 `FakeLearningStateRepo`를 만든 뒤,
같은 함수의 파라미터 타입만 fake 구현체로 바꾼다.

```kotlin
// 위치: app/src/main/java/com/example/umma/di/RepositoryModule.kt
@Binds
@Singleton
abstract fun bindLearningStateRepo(
    fakeLearningStateRepo: FakeLearningStateRepo
): LearningStateRepo
```

이렇게 하면 대시보드 관련 화면은 `LearningStateRepo`만 바라보고,
내부의 `DashSummary`, `SessionSummary`, `FlashcardSummary`, `UserLangPref`가
한 번에 fake 데이터로 전환된다.

> `LearningStateRepoImpl` 바인딩과 `FakeLearningStateRepo` 바인딩을 같은 source set에 동시에 두지 않는다.
> 동시에 존재하면 Hilt 중복 바인딩 오류가 발생한다.

### 5.3 build variant로 분리할 때

초기에는 `RepositoryModule.kt` 한 곳에서 바인딩을 바꾸는 방식이 가장 이해하기 쉽다.
반복 전환이 많아지면 build variant로 분리한다.

- `debug` source set: fake module
- `release` source set: real module

즉, `build variant`는 여러 repository를 한 번에 fake 또는 real로 전환하기 위한 통합 스위치에 가깝다.
실제 전환은 Hilt binding에서 일어나고, build variant는 그 전환을 묶어주는 실행 구성이다.
개발 초기에 fake 환경을 맞추거나, 추후 통합 테스트와 release 구성을 분리할 때 유용하다.

이 방식은 현재 소스 구조에는 아직 적용되어 있지 않은 장기 예시다.
아래 코드는 build variant 분리 시 `release` source set에 둘 수 있는 예시다.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLearningStateRepo(
        learningStateRepoImpl: LearningStateRepoImpl
    ): LearningStateRepo
}
```

핵심은 ViewModel 코드는 그대로 두고 `di`만 바꾸는 것이다.

---

## 6. 작업 순서

### 6.1 먼저 고정한다

- UI 계약
- ViewModel state
- UseCase 입출력
- Domain Model

### 6.2 먼저 만든다

- fake repository
- fixture
- empty / error / success 시나리오

### 6.3 마지막에 바꾼다

- fake repository -> real repository
- prompt builder -> 실제 생성기
- Hilt binding

예시:

- 온보딩: `UserLangPref`, `LangState`, `DashSummary`, `SessionSummary`, `FlashcardSummary` 초기 생성 확인
- 대시보드: `DashSummary[selectedLearningLanguage]`, `correctionAvailable`, `dueFlashcards`, 언어 변경 상태 확인
- 교정: `recentFullContext` 내부 후보 추출, `CorrectionSuggestion` 카드 표시, 완료 파이프라인 성공/실패 흐름 확인
- AI Chat: 세션 없음, 새 LiveSession, 재연결 실패 시나리오 확인
- SRS: `Flashcard` due deck 조회, SM-2 기반 4단계 평가, local first 복습 저장, Dashboard summary 반영 확인

---

## 7. 운영 규칙

### 7.1 교체 지점은 한 곳에 모은다

- mock / real 전환은 `presentation`에서 하지 않는다.
- if / else 분기를 여기저기 흩어놓지 않는다.
- 교체는 `di`의 Hilt binding 또는 build variant에서만 한다.

예를 들어 대시보드는 `LearningStateRepo`를 facade처럼 사용한다.
그래서 `DashSummary`, `SessionSummary`, `FlashcardSummary`, `UserLangPref`를
개별적으로 여기저기 바꾸는 대신, `di/RepositoryModule.kt`의
`bindLearningStateRepo()` 한 줄만 바꾸면 대시보드 전체가 fake 데이터로 전환된다.

예시 코드는 5장의 `bindLearningStateRepo()`를 기준으로 한다.
추후 대시보드 ViewModel이 추가되면 그 ViewModel은 항상 `LearningStateRepo`만 바라보고,
내부의 여러 Summary 데이터는 fake에서 real로 한 번에 바뀐다.

### 7.2 fake는 data 경계 안에서 관리한다

- fake 구현체는 현재 프로젝트 구조에 맞춰 `data/repository/fake` 또는 `data/source/fake` 아래에 둔다.
- 팀원이 작업하는 기능과 직접 연결되는 fake만 만든다.
- fixture는 검증용으로만 쓰고 제품 로직에 섞지 않는다.

### 7.3 임시 전환과 커밋 기준

- 개발 중 mock으로 바꿔둔 상태는 로컬 작업으로 둘 수 있다.
- PR / merge 대상 브랜치에는 임시 debug 스위치나 하드코딩 분기가 남지 않아야 한다.
- build variant나 Hilt module로 관리되는 전환은 정상적인 교체로 본다.

### 7.4 리뷰 기준

- “어떤 구현체를 썼는가”보다 “계약이 유지되는가”를 먼저 본다.
- fake와 real이 같은 state와 같은 흐름을 유지하는지 확인한다.

---

## 8. 추천 디렉토리 예시

현재 프로젝트에는 `data/repository/fake/`가 아직 없다.
fake 구현체가 필요해지면 아래 구조로 추가한다.

```text
domain/repository/
data/repository/
data/repository/fake/
data/source/local/
data/source/remote/
di/
presentation/{flow}/
```

예:

```text
data/repository/LearningStateRepoImpl.kt
data/repository/ChatRepositoryImpl.kt
data/repository/fake/FakeLearningStateRepo.kt
data/repository/fake/FakeChatRepository.kt
data/repository/fake/FakeCorrectionRepository.kt
```

---

## 9. 한 줄 요약

> 먼저 fake로 화면과 상태를 완성하고, 나중에 Hilt 바인딩만 바꿔 real 데이터로 전환한다.
