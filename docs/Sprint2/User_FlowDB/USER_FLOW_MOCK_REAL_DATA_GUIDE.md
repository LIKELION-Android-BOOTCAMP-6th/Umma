# USER_FLOW_MOCK_REAL_DATA_GUIDE

## 1. 목적

이 문서는 팀원이 real data와 mock data를 오가며 화면을 검증할 때 사용하는 기준이다.

핵심 원칙은 하나다.

> UI / ViewModel / UseCase는 항상 같은 domain interface를 바라보고, 데이터 공급 구현체만 build variant와 Hilt module로 교체한다.

## 2. 실행 기준

현재 프로젝트는 `dev`와 `mock` product flavor를 사용한다.

| Build Variant | 목적 | Repository 기준 |
| --- | --- | --- |
| `devDebug` | 일반 개발/real 흐름 확인 | real repository |
| `mockDebug` | 데모/QA용 화면 상태 재현 | fake repository |
| `devRelease` | 배포 후보 확인 | real repository |

일반 실행은 `devDebug`, fake 데이터 기반 화면 검증은 `mockDebug`를 사용한다.
`mockRelease`는 별도 필요가 생기기 전까지 일반 검증 흐름에서 사용하지 않는다.

## 3. Repository 바인딩 구조

variant별 repository 교체는 source set의 Hilt module로 처리한다.

- `app/src/dev/.../di/*RepositoryModule.kt`
  - real repository를 주입한다.
- `app/src/mock/.../di/*RepositoryModule.kt`
  - fake repository를 주입한다.
- `app/src/main/.../di/RepositoryModule.kt`
  - variant로 교체하지 않는 공통 binding만 유지한다.

같은 interface를 `main`과 `dev/mock`에서 동시에 바인딩하면 Hilt duplicate binding 오류가 난다.
따라서 variant에서 교체하는 repository는 `main`의 `RepositoryModule`에 함께 두지 않는다.

현재 기준:

| Interface | `devDebug` | `mockDebug` |
| --- | --- | --- |
| `LearningStateRepo` | `LearningStateRepoImpl` | `FakeLearningStateRepo` |
| `StatisticsRepository` | `StatisticsRepositoryImpl` | `FakeStatisticsRepository` |
| `FlashcardRepository` | `FlashcardRepositoryImpl` | `FakeFlashcardRepository` |

## 4. Fake preset 기준

fake repository는 “화면 상태를 반복 재현하기 위한 입력”만 담당한다.
제품 로직을 fake 안에 새로 만들지 않는다.

도메인별 데모 상태가 필요하면 다음 구조를 사용한다.

```text
app/src/main/java/com/app/umma/data/repository/fake/demo/{domain}/
```

예시:

```text
fake/demo/statistics/
├── StatisticsDemoPreset.kt
├── StatisticsHistoryFixtures.kt
└── StatisticsLearningStateFixtures.kt
```

수동 테스트에서 preset을 바꾸는 지점은 각 도메인의 `*DemoPresetConfig.activePreset`이다.
예를 들어 통계 화면은 `StatisticsDemoPresetConfig.activePreset`만 바꾼 뒤 `mockDebug`로 다시 실행한다.

## 5. Android Studio 실행 방법

1. `View > Tool Windows > Build Variants`를 연다.
2. `:app`의 Active Build Variant를 선택한다.
3. 실제 저장 흐름을 보려면 `devDebug`를 선택한다.
4. fake 데이터로 데모/QA 상태를 보려면 `mockDebug`를 선택한다.
5. 필요한 경우 도메인별 `*DemoPresetConfig.activePreset`을 변경한다.
6. 앱을 다시 빌드/실행한다.
7. 확인 후 preset은 기본값으로 되돌린다.

Run/Debug 버튼 자체가 mock/real을 바꾸는 것이 아니다.
현재 선택된 build variant가 어떤 source set을 포함하는지가 mock/real을 결정한다.

## 6. 터미널 실행 예시

```bash
./gradlew :app:installDevDebug
./gradlew :app:installMockDebug
```

컴파일 확인:

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:compileMockDebugKotlin
```

mock variant 테스트 확인:

```bash
./gradlew :app:testMockDebugUnitTest
```

## 7. mockDebug와 unit test의 차이

`mockDebug`는 앱을 실행해 화면을 눈으로 확인하기 위한 구성이다.
Hilt가 `mock` source set의 fake repository를 주입하고, 화면은 준비된 fixture/preset으로 렌더링된다.

unit test는 fake 구현과 preset이 기대한 계약을 지키는지 빠르게 검증한다.

- repository 계약 테스트: fake repository가 real repository와 같은 interface 계약을 지키는지 확인한다.
- demo preset 테스트: 문서에 적힌 데모 시나리오 입력이 실제로 준비되어 있는지 확인한다.

예시:

- `FakeStatisticsRepositoryTest`: repository 계약 회귀 테스트
- `StatisticsDemoPresetTest`: `DEMO_FLOW_STATISTICS.md` preset 준비 상태 검증

## 8. 작업 규칙

- Composable, ViewModel, UseCase 안에서 mock/real 분기를 만들지 않는다.
- repository 교체는 Hilt binding과 build variant로만 처리한다.
- 수동 주석 토글 방식으로 real/fake를 바꾸지 않는다.
- fake repository는 real repository와 같은 domain interface를 구현한다.
- demo preset은 도메인별 package에 분리한다.
- 다른 도메인 테스트를 위해 공통 fake를 직접 오염시키지 않는다.
- 테스트 대상이 아닌 도메인은 기본 preset 또는 정상 fake 상태로 둔다.
- PR에는 임시 preset, 임시 delay, 임시 실패 hook 활성 상태를 남기지 않는다.
