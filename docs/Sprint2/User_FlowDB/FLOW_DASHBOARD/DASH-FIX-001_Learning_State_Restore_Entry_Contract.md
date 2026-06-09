# [Fix] DASH-FIX-001 학습 상태 복구 진입 계약 정리

## User Story

기존 사용자는 앱을 삭제했다가 다시 설치하거나 로컬 캐시가 비어 있는 상태로 접속해도, Dashboard에서 Firestore에 저장된 학습 상태를 다시 불러와 현재 학습 언어와 요약 정보를 확인할 수 있어야 한다.

개발자는 Dashboard, Correction, SRS, Statistics, MyPage가 각자 다른 방식으로 `preload`와 `sync`를 호출하지 않도록, 공통 학습 상태 로드 UseCase를 만들고 화면별로 점진 적용할 수 있어야 한다.

---

# 문제 배경

현재 `LearningStateRepo`는 local-first / pending / retry 정책을 갖고 있다.

```text
preload()
→ DataStore local cache 복원

sync()
→ pending write가 있으면 Firestore write-back 우선
→ pending write가 없으면 Firestore fetch로 local cache 갱신
```

하지만 이 정책을 화면 진입 시점에 어떻게 호출할지는 화면마다 다르다.

```text
Dashboard: preload 후 local userPref가 있을 때만 sync
Chat: preload 후 userPref가 없으면 sync fallback
Correction: preload 중심
Statistics: preload 중심
SRS: preload 중심
MyPage: preload 중심
```

그래서 앱 재설치, 로컬 캐시 삭제, 다른 기기 로그인, DataStore 초기화 같은 상황에서 기존 사용자의 Firestore 데이터가 있어도 일부 화면은 remote restore를 시작하지 못할 수 있다.

이번 작업은 이 문제를 Dashboard에서 먼저 해결하고, 이후 다른 화면이 같은 UseCase를 가져다 쓸 수 있는 공통 진입 계약을 마련한다.

---

# 완료 기준(AC)

- [ ] 학습 상태 로드/복구를 담당하는 공통 UseCase가 `domain/usecase/learningstate`에 추가된다.
- [ ] 공통 UseCase는 `preload()`를 먼저 호출해 local cache를 우선 복원한다.
- [ ] local `UserLangPref`가 있으면 Firestore fetch 없이 성공으로 종료한다.
- [ ] local `UserLangPref`가 없고 로그인 사용자가 있으면 `LearningStateRepo.sync()`를 호출해 Firestore restore를 시도한다.
- [ ] `sync()` 호출은 기존 `LearningStateRepo`의 pending write 우선 정책을 그대로 사용한다.
- [ ] `sync()` 이후에도 `UserLangPref`가 없으면 신규 사용자 또는 초기 설정 미완료 상태로 판단할 수 있게 결과를 돌려준다.
- [ ] Dashboard 진입 시 기존 `preload` 직접 호출 흐름을 공통 UseCase 기반으로 전환한다.
- [ ] Dashboard는 앱 재설치 후 local cache가 비어 있어도 기존 Firestore 학습 상태를 다시 표시할 수 있다.
- [ ] Correction, SRS, Statistics, MyPage 코드는 이번 작업에서 직접 수정하지 않는다.
- [ ] 후속 화면들이 같은 UseCase를 적용할 수 있도록 책임과 사용 시점을 문서에 남긴다.
- [ ] 기존 Chat 세션 시작 흐름은 이번 작업에서 변경하지 않는다.
- [ ] local-first / pending / retry 정책이 우회되거나 중복 구현되지 않는다.

---

# 포함 범위

- `EnsureLearningStateLoadedUseCase` 추가
- Dashboard 진입 시 학습 상태 복구 흐름 정리
- Dashboard의 `preload` / `sync` 호출 책임 재배치
- 로컬 캐시가 비어 있는 기존 사용자 복구 시나리오 처리
- 후속 화면 적용을 위한 UseCase 사용 기준 문서화
- 관련 Dashboard / LearningState 단위 테스트 또는 ViewModel 테스트 보강

---

# 제외 범위

- Correction 화면 코드 변경
- SRS 화면 코드 변경
- Statistics 화면 코드 변경
- MyPage 화면 코드 변경
- Chat 세션 시작 정책 변경
- `LearningStateRepo.sync()` 내부 pending/write-back 정책 변경
- Firestore schema 변경
- 회원탈퇴 cleanup 변경
- Android backup 정책 변경
- Dashboard UI 디자인 변경

---

# 기준 문서

- [DASH-001 Dashboard 진입](./DASH-001_Dashboard_Entry.md)
- [User Flow - Dashboard](../FLOW_DASHBOARD.md)
- [LS-004 Global Learning State Store](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-004_Global_Learning_State_Store.md)
- [LS-005 Local Cache and Sync Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [LS-006 Language State Update Policy](../../../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)

---

# 설계 방향

## 공통 UseCase

새 UseCase의 역할은 화면별로 흩어진 학습 상태 로드 조건을 하나로 모으는 것이다.

예상 이름:

```text
EnsureLearningStateLoadedUseCase
```

예상 위치:

```text
domain/usecase/learningstate
```

예상 흐름:

```text
preload()
→ observeUserPref().firstOrNull()
→ userPref 있으면 LoadedFromLocal
→ userPref 없으면 sync()
→ observeUserPref().firstOrNull()
→ userPref 있으면 RestoredFromRemote
→ 그래도 없으면 MissingSetup
```

`sync()` 내부에는 이미 pending write 우선 정책이 있으므로, UseCase가 pending key를 직접 해석하지 않는다.

---

## 결과 모델

화면이 신규/복구/실패 상태를 구분할 수 있도록 결과는 단순 `Result<Unit>`보다 의미 있는 값으로 둔다.

예상 모델:

```kotlin
sealed interface LearningStateLoadResult {
    data object LoadedFromLocal : LearningStateLoadResult
    data object RestoredFromRemote : LearningStateLoadResult
    data object MissingSetup : LearningStateLoadResult
}
```

실패는 기존 패턴처럼 `Result.failure`로 돌려준다.

의도:

- `LoadedFromLocal`: 기존 local cache가 있어 즉시 사용 가능
- `RestoredFromRemote`: 재설치/캐시 삭제 후 Firestore에서 복구됨
- `MissingSetup`: local/remote 모두 설정 없음. Initial Setup 또는 Empty 흐름으로 처리 가능

---

# Dashboard 적용 정책

Dashboard 진입 시 기존 흐름은 다음과 같이 바꾼다.

```text
현재:
preloadLearningState()
→ observeLearningState()
→ userPref가 확인될 때만 sync

변경:
ensureLearningStateLoaded()
→ observeLearningState()
→ local 또는 remote에서 복구된 상태를 UI에 반영
→ 이후 재진입/언어 변경 시 기존 triggerSync 정책 유지
```

Dashboard는 여전히 local cache 우선 렌더링을 유지한다.
다만 local cache가 완전히 비어 있는 기존 사용자에게는 remote restore를 한 번 시도한다.

`LoadedFromLocal`이 반환되어도 Dashboard의 기존 background sync 흐름은 유지한다.
local cache는 즉시 렌더링용이고, pending retry나 최신 remote 반영은 별도 sync 책임이기 때문이다.

장기적으로는 로그인과 초기 설정 확인이 끝난 뒤 AppEntry 또는 Splash 단계에서 학습 상태를 한 번 보장하는 구조가 더 자연스럽다.
하지만 이번 작업에서 AppEntry, Auth, Onboarding, Navigation까지 함께 바꾸면 영향 범위가 커지므로, 우선 Dashboard에서 공통 UseCase를 적용하고 후속으로 진입점 이전을 검토한다.

---

# 후속 전파 기준

이번 작업에서는 다른 화면을 직접 수정하지 않는다.
다만 후속 작업에서 아래 화면은 기존 `preload()` 직접 호출을 공통 UseCase로 교체하는 후보가 된다.

| 화면/흐름 | 현재 위험 | 후속 적용 방향 |
| --- | --- | --- |
| Correction | local cache가 비면 Empty/NotAvailable로 떨어질 수 있음 | 진입 시 `EnsureLearningStateLoadedUseCase` 호출 후 observe |
| SRS | selectedLang이 null이면 학습 언어를 못 고정할 수 있음 | review session 시작 전에 공통 UseCase 호출 |
| Statistics | LangState가 없으면 overview 조립 실패 가능 | overview 생성 전 공통 UseCase 호출 |
| MyPage | primaryLang 초기 표시가 local cache에 의존 | 화면 시작 시 공통 UseCase 호출 |
| Chat | 이미 유사 fallback 보유 | 후속으로 중복 로직을 공통 UseCase로 교체 검토 |

---

# 예외 처리

## 기존 사용자 + local cache 없음 + remote 있음

```text
ensureLearningStateLoaded()
→ preload: userPref 없음
→ sync: Firestore fetch 성공
→ RestoredFromRemote
→ Dashboard 카드 출력
```

## 신규 사용자 + local cache 없음 + remote 없음

```text
ensureLearningStateLoaded()
→ preload: userPref 없음
→ sync: remote userPref 없음
→ MissingSetup
→ Initial Setup / Empty 흐름 유지
```

## pending write가 남아 있는 경우

```text
ensureLearningStateLoaded()
→ preload
→ userPref 없음 또는 복구 필요
→ sync()
→ LearningStateRepo가 pending write-back 우선 처리
```

UseCase는 pending key를 직접 지우거나 Firestore fetch를 강제로 앞세우지 않는다.

pending write가 남아 있는데 local `UserLangPref`가 없는 상태는 일반적인 재설치 흐름보다는 DataStore 부분 복구나 비정상 종료 같은 예외에 가깝다.
이번 작업에서는 `sync()` 1회 호출로 기존 repository 정책에 위임하는 최소 방어만 적용한다.
`sync()`가 pending write-back만 처리하고 remote fetch까지 이어지지 않는 특수 케이스는 후속 hardening 범위로 남긴다.

## sync 실패

```text
ensureLearningStateLoaded()
→ sync 실패
→ Result.failure
→ Dashboard는 기존 fallback/error 정책으로 처리
```

네트워크 실패나 권한 실패를 신규 사용자로 오판하지 않는다.
즉 `sync()`가 실패하면 `MissingSetup`이 아니라 `Result.failure`로 전달한다.
`MissingSetup`은 sync 자체가 성공했지만 local/remote 어디에서도 `UserLangPref`를 확인하지 못한 경우에만 사용한다.

---

# 테스트 계획

- local cache에 `UserLangPref`가 있으면 `sync()`를 호출하지 않고 `LoadedFromLocal`을 반환한다.
- local cache가 비어 있고 remote fetch 후 `UserLangPref`가 생기면 `RestoredFromRemote`를 반환한다.
- local cache와 remote 모두 `UserLangPref`가 없으면 `MissingSetup`을 반환한다.
- `sync()` 실패 시 `Result.failure`를 반환하고 신규 사용자로 오판하지 않는다.
- Dashboard 진입 시 local cache가 비어 있는 기존 사용자도 sync 후 summary를 표시한다.
- pending key가 있는 경우 UseCase가 pending 정책을 직접 해석하지 않고 `repo.sync()`에 위임한다.
- `LoadedFromLocal`이어도 Dashboard의 기존 background sync 흐름은 끊기지 않는다.
- pending write는 있지만 local `UserLangPref`가 없는 비정상 상태는 MVP에서 repository 정책 위임까지만 검증하고, 강제 fetch 정책은 후속으로 분리한다.

검증 명령:

```bash
./gradlew :app:testDevDebugUnitTest --tests "*LearningState*"
./gradlew :app:testDevDebugUnitTest --tests "*Dashboard*"
./gradlew :app:compileDevDebugKotlin
./gradlew :app:compileMockDebugKotlin
```

---

# 구현 메모

- 공통 UseCase는 `DashboardViewModel` 전용 모델을 반환하지 않는다.
- UI 문구나 화면 상태는 Dashboard가 해석한다.
- Repository 내부의 pending/write-back/fetch 순서는 수정하지 않는다.
- 후속 화면은 `preload()` 직접 호출을 제거하기 전에 해당 화면의 Empty/Error 정책과 함께 검토한다.
- 이 작업은 앱 전반의 완전한 일관성 적용이 아니라, 공통 진입 계약 생성과 Dashboard 우선 적용이다.
