# USER_FLOW_MOCK_REAL_DATA_GUIDE

## 1. 목적

이 문서는 팀원이 real data와 mock data를 오가며 화면을 검증할 때,
어떤 계층은 고정하고 어떤 계층만 교체해야 하는지 정리한 가이드다.

핵심 원칙은 다음과 같다.

> UI / ViewModel / UseCase는 같은 domain 계약을 바라보고, 데이터 공급 구현체만 Hilt와 build variant로 교체한다.

## 2. 용어

- `real`: Firebase, Room, DataStore처럼 실제 저장소와 연결되는 구현체
- `fake`: 같은 interface를 구현하지만 고정 fixture를 반환하는 검증용 구현체
- `fixture`: 화면 또는 테스트 확인을 위해 준비한 샘플 데이터
- `Hilt binding`: interface에 어떤 구현체를 주입할지 정하는 DI 연결
- `build variant`: `devDebug`, `mockDebug`, `devRelease`처럼 source set과 build type을 조합한 실행 구성
- `source set`: variant별로 함께 컴파일되는 코드 위치

## 3. 현재 전략

현재 프로젝트는 `dev`와 `mock` product flavor를 사용한다.

- `devDebug`: 일반 개발/디버그 실행. real repository를 사용한다.
- `mockDebug`: 화면 검증 실행. 필요한 fake repository들을 사용한다.
- `devRelease`: 배포 후보 빌드. real repository를 사용한다.
- `mockRelease`: 목업 전용 실행 구성이 필요할 때만 별도로 두고, 일반적으로는 만들지 않는다.


## 4. Hilt와 Variant의 역할

Hilt는 interface에 어떤 구현체를 주입할지 담당한다.
Variant/source set은 어떤 Hilt module이 컴파일에 포함될지 담당한다.

즉, 전환의 편리성은 variant/source set이 제공하고,
주입의 일관성은 Hilt가 제공한다.

실무에서는 어떤 repository를 `main`에 둘지, 어떤 repository를 `dev/mock` source set으로 분리할지
기능 책임에 따라 결정한다.

같은 variant 안에서 동일 interface를 두 번 바인딩하면 Hilt 중복 바인딩 오류가 발생한다.
따라서 variant에서 교체하는 repository는 `main`의 `RepositoryModule`에 동시에 바인딩하지 않는다.

## 5. Android Studio에서 실행하는 방법

1. Android Studio에서 `View > Tool Windows > Build Variants`를 연다.
2. `:app` 모듈의 Active Build Variant를 선택한다.
3. 실제 데이터로 일반 실행하려면 `devDebug`를 선택한다.
4. 목업 데이터로 화면과 chart를 확인하려면 `mockDebug`를 선택한다.
5. variant를 선택한 뒤 상단의 Run 또는 Debug 버튼을 평소처럼 누른다.

상단의 Run 버튼과 Debug 버튼 자체는 특별한 동작을 하지 않는다.
현재 선택된 build variant가 무엇인지에 따라 포함되는 Hilt module이 달라진다.

배포 후보를 확인할 때는 `devRelease`를 사용한다.
`mockRelease`는 목업 확인용으로만 둘 수 있고, 일반 배포 흐름에서는 제외한다.

## 6. 터미널 실행 예시

```bash
./gradlew :app:installDevDebug
./gradlew :app:installMockDebug
./gradlew :app:assembleDevRelease
```

컴파일만 확인할 때는 다음처럼 실행한다.

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:compileMockDebugKotlin
```

## 7. mockDebug와 unit test의 차이

`mockDebug`는 앱을 실행해 화면을 눈으로 확인하기 위한 구성이다.
이때 Hilt는 mock source set에서 제공되는 fake repository를 주입하고, 화면은 준비된 fixture로 렌더링된다.
화면이 여러 저장소를 함께 읽는 경우에는 그에 맞는 fake를 함께 제공할 수 있다.

`FakeStatisticsRepositoryTest` 같은 테스트 파일은 앱 실행 구성이 아니다.
테스트는 fake repository가 userId/language 필터링, Retry, pending sync, 중복 기록 방지 같은 계약을 지키는지 빠르게 검증한다.

즉, `mockDebug`는 화면 확인용이고 `testMockDebugUnitTest`는 fake 구현의 계약 검증용이다.

## 8. 작업 규칙

- mock/real 전환을 Composable, ViewModel, UseCase 내부의 if 분기로 만들지 않는다.
- variant에서 교체하는 repository만 variant source set module로 이동한다.
- 책임 영역이 아닌 전역 repository를 목업 확인 목적으로 임의 교체하지 않는다.
- fake repository는 real repository와 같은 domain interface를 구현해야 한다.
- fixture는 화면 확인과 테스트를 위한 데이터이며 제품 로직에 섞지 않는다.
- PR에는 임시 하드코딩이나 수동 토글 상태가 남지 않아야 한다.
