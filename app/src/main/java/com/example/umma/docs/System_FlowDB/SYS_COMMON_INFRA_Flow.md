# SYS-COMMON_INFRA 시스템 인프라 구축 플로우

## 1. 목표 및 범위

- 목표: 팀원들이 기능 개발에만 집중할 수 있도록 공통 아키텍처, UI 상태, 데이터 통신 기반을 완성한다.
- 성공 조건 공통 테마가 적용된 앱에서 네비게이션 이동이 가능하고, API 에러/로딩 상태가 표준에 맞게 출력된다.

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. 환경/테마 | 디자인 시스템(Figma) 및 테마 설정 | 전역 스타일(Color, Typo) 적용 | 성공: 테마 적용 완료 / 실패: 리소스 참조 에러 | SYS-001 |
| 2. 네비게이션 | NavHost 및 Screen 라우팅 정의 | 화면 전환 및 데이터 전달 | 성공: 화면 간 이동 성공 / 실패: 라우팅 경로 유실 | SYS-002 |
| 3. 상태 표준화 | Loading, Error, Empty 표준 UI 구현 | LCE(Loading/Content/Error) 노출 | 성공: 공통 UI 렌더링 / 실패: 상태 미반영 | SYS-003 |
| 4. 통신/문자열 | API interceptor 설정 및 UiText 인터페이스 구현 | 텍스트 리소스 및 타임아웃 처리 | 성공: 다국어/에러 대응 완료 / 실패: 런타임 크래시 | SYS-004 |
| 5. 외부 서비스 | Firestore/Firebase.Auth 연동 인프라 | 서버 세션 및 DB 커넥션 유지 | 성공: 원격 데이터 로드 / 실패: 인증/연결 실패 | SYS-005 |

---

## 3. 필수 상태 관리 (State Detail)

### Loading

- `CircularProgressIndicator` 기반
- ViewModel의 `isLoading` 상태와 연동
- 사용자 터치 차단(Interaction Guard)

### Error

#### Fatal

- 화면 중앙에 `ErrorView` 노출
- 다시 시도 버튼 포함

#### Transient

- `Snackbar`를 통해 작업 중단 없이 알림

### Empty

- 데이터 리스트가 0개일 때 표시
- "콘텐츠가 없습니다" 문구 출력
- 액션 버튼(예: 작성하기) 제공

---

## 4. GitHub Issues (실행 기준 / SSOT)

### [INFRA-001] Design System & Theme Setup

- `core/theme` 패키지 구성 (`Color`, `Type`, `Theme.kt`)
- Figma 변수와 1:1 매핑되는 상수 정의

---

### [INFRA-002] Navigation Structure & Route Guard

- `Screen` sealed interface 정의 및 Type-safe Navigation 적용
- 단일 액티비티(`MainActivity`) 구조의 `AppNavigation` 컴포저블 구현

---

### [INFRA-003] Global UI Components (LCE)

- `core/ui` 내 로딩, 에러, 빈 화면 UI 표준화
- 모든 스크린에서 재사용 가능한 Scaffold 템플릿 제작

---

### [INFRA-004] String Management & UiText Utility

- `res/values/strings.xml` 도메인별 구조화
- `core/util/UiText.kt` 선언 및 ViewModel 연동 가이드 작성

---

### [INFRA-005] Firebase/API Base Configuration

- Firestore, Auth 초기화 및 Dependency Injection(Hilt/Koin) 설정
- 10초 타임아웃 및 네트워크 상태 모니터링 모듈 구성

---

## 5. 예외 상황 및 대응 (Edge Case)

### 1. 네트워크 단절

`INFRA-004`에서 정의한:

```kotlin
UiText.StringResource(R.string.error_network)
```

를 통해 공통 에러 뷰 노출

---

### 2. 권한 거부

- 마이크/카메라 권한 거부 시
- `INFRA-003`의 공통 다이얼로그 사용
- 설정 화면 이동 유도

---

### 3. 데이터 저장 실패

- Firestore 쓰기 실패 시 Snackbar 노출
- 기존 데이터 롤백 처리

---

## 6. 디자인(Figma) 및 명명 규칙 연결

### Package 규칙

```text
core/navigation
core/theme
core/ui
domain/model
domain/repository
domain/usecase
data/repository
data/source/local
data/source/remote
presentation/{screen_or_flow}
di
```

### 아키텍처 배치 원칙

```text
presentation
→ 화면, Composable, ViewModel, UI State

domain
→ VO/Entity, Repository interface, UseCase

data
→ Repository 구현체, Local/Remote DataSource, DTO/Mapper

core
→ navigation, theme, 공통 UI, UiText 등 앱 공통 유틸

di
→ Hilt Module
```

User Flow 문서에서 특정 기능 폴더가 필요할 경우 `feature/*`를 새로 만들지 않고,
기존 팀 구조에 맞춰 `presentation/{flow}`, `domain`, `data`, `core`에 역할별로 배치한다.

---

### Naming 규칙

#### Composable

```text
UpperCamelCase
예: HomeContent
```

#### Resource

```text
lower_snake_case
예: login_btn_submit
```

---

### SSOT 원칙

- 모든 이슈의 상세 내용은 GitHub Issue Description에 기록
- 디자인 수정 시 Figma Link를 이슈에 업데이트
