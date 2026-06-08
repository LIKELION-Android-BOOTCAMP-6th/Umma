# [UX] COR-UX-003 미저장 교정 결과 로컬 캐시·복원

## User Story

사용자는 교정 결과를 **저장하지 않고** 화면을 떠나거나(다른 탭 이동·뒤로가기) 앱을 종료한 뒤, 다시 교정 탭에 들어오면 **AI를 다시 호출하지 않고** 직전에 생성된 교정 결과를 **그대로** 본다. 같은 대화 세션이라면 매번 같은 교정을 다시 만들 이유가 없다. 캐시에서 복원하는 짧은 순간에는 긴 5단계 로딩 대신 **chat 진입 화면처럼 원형 인디케이터**만 잠깐 본다.

---

# 배경

현재 교정 화면은 **재진입할 때마다 AI를 다시 호출**한다.

확인된 현황([CorrectionViewModel.kt](../../../../app/src/main/java/com/app/umma/presentation/correction/CorrectionViewModel.kt)):

- `onEnter()` → `ensureObservation()`가 `GlobalLangState`를 구독하고, `Ready` 게이트(선택언어 + SessionSummary + LangState + `correctionAvailable==true`)를 통과하면 `shouldTriggerGeneration`을 거쳐 곧바로 `triggerGeneration()`으로 **새 AI 호출**을 시작한다.
- 생성된 교정 결과(`CorrectionUiState.suggestions`)는 **메모리에만** 존재한다. 화면을 떠나 ViewModel이 정리되거나 앱이 종료되면 사라진다.
- 따라서 저장하지 않고 나갔다가 돌아오면, 같은 세션인데도 5단계(~17.5초) 로딩과 함께 교정이 **처음부터 다시** 만들어진다(비용·대기·결과 변동 낭비).

방향은 **"생성됐지만 저장되지 않은 교정 결과를 로컬에 보관 → 같은 세션 재진입 시 복원"** 이다. 같은 대화 세션인지 판정할 지문이 필요하고, 저장(완료)되면 캐시는 더 이상 유효하지 않으므로 비운다.

> 참고: 저장(완료) 흐름은 `correctionAvailableOverride=false`로 세션을 더 이상 교정 대상이 아니게 만든다(`launchCompletion`). 즉 본 캐시는 **"생성 O / 저장 X"** 상태만 보관한다.

---

# 캐시 키 · 세션 지문 · 무효화

- **키**: `(uid, language)` — 사용자·학습 언어당 한 행.
- **세션 지문(fingerprint)**: `SessionSummary.updatedAt`([LearningSummaryModels.kt:47](../../../../app/src/main/java/com/app/umma/domain/model/learningstate/LearningSummaryModels.kt)). 새 대화 세션이 끝나면 SessionSummary가 새 `updatedAt`으로 갱신되므로, **캐시 지문 == 현재 `sessionSummary.updatedAt`** 이면 같은 세션(복원), 다르면 stale(재생성)로 본다.
- **무효화**: 완료(저장) 성공 시 해당 `(uid, lang)` 캐시 삭제. (저장 후엔 `correctionAvailable=false`라 재진입이 Empty가 되지만, stale 행이 남지 않도록 명시적으로 비운다.)

---

# 완료 기준(AC)

## (A) 캐시 저장

- [ ] 교정 생성이 `Content`로 성공하면, 그 시점의 `suggestions` 전체를 `(uid, lang)` 키로 로컬에 저장한다. 세션 지문(`sessionSummary.updatedAt`)과 `primaryLanguage`를 함께 보관한다.
- [ ] 저장은 **전 필드 손실 없이**(중첩 `learningSignal`, `sourceLang`, `lang`, `sourceCandidateIds`, `sourceTurnIndex` 등) 이뤄져, 복원된 결과로도 저장/완료 파이프라인이 동일하게 동작한다.
- [ ] 캐시 저장 실패는 교정 흐름을 막지 않는다(로그만 남기고 진행).

## (B) 복원 + 로딩 처리

- [ ] 교정 탭 재진입 시, `Ready` 게이트 통과 후 **AI를 호출하기 전에 먼저 캐시를 조회**한다.
- [ ] 캐시 히트(지문 일치) → AI 호출 없이 캐시의 `suggestions`로 `Content`를 채운다. 복원하는 짧은 구간에는 **중앙 원형 인디케이터**(`Phase.Done`이 이미 쓰는 `CircularProgressIndicator(color = ThemePrimary)` 패턴)를 노출한다. 긴 5단계 로딩(`CorrectionLoading`)은 보이지 않는다.
- [ ] 캐시 미스(지문 불일치/없음) → 기존 5단계 로딩 + AI 생성 흐름 그대로.
- [ ] 복원 경로에서는 logcat에 generate(AI 호출) 로그가 남지 않는다.

## (C) 무효화

- [ ] 완료(저장) 성공 시 `(uid, lang)` 캐시를 삭제한다.
- [ ] 새 대화 세션으로 지문이 바뀌면 옛 캐시는 복원에 쓰이지 않는다(재생성).

## (D) 영속성

- [ ] 앱을 완전히 종료한 뒤 다시 켜서 같은 세션으로 교정 탭에 들어와도 복원된다(메모리/SavedState 아님 — 로컬 DB 영속).

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md` (Ready/Empty 게이트, 화면 phase)
- [COR-UX-001 교정 로딩 화면 단계 동기화](COR-UX-001_Correction_Loading_Flashcard_Review.md) (5단계 로딩 — 캐시 미스 경로에서만 유지)
- `docs/System_FlowDB/SYS_CORRECTION_INFRA/SCI-001_Correction_Contract.md` (교정 저장 계약 — local-first 패턴 참고)
- [CorrectionFlashcardLocalDataSource.kt](../../../../app/src/main/java/com/app/umma/data/source/local/CorrectionFlashcardLocalDataSource.kt) (Room Entity/DAO/Database/DI 미러 원본)

---

# 핵심 결정

- **저장 매체는 Room 신규 테이블.** 앱 종료 후에도 복원해야 하므로 메모리/`SavedStateHandle`로는 부족하다. 기존 `correction_flashcards` 테이블에 마이그레이션을 얹는 대신, 독립 `CorrectionSuggestionCacheDatabase`(버전 1)를 신설해 마이그레이션 부담을 없애고 캐시 책임을 격리한다.
- **suggestions는 JSON 블록으로 직렬화.** `CorrectionSuggestion`은 중첩 `learningSignal`을 포함해 컬럼 매핑이 번거롭다. 프로젝트가 이미 쓰는 `kotlinx.serialization`으로 전 필드를 직렬화해 한 컬럼(`suggestionsJson`)에 보관하고, **별도 `@Serializable` 캐시 DTO + Mapper**로 도메인 모델과 경계를 둔다(레이어 이동은 Mapper 통과).
- **세션 지문은 `SessionSummary.updatedAt`.** 별도 sessionId 모델이 없으므로 갱신 타임스탬프를 지문으로 쓴다. 일치 여부 판정 정책은 **domain UseCase**(`GetCachedCorrectionUseCase`)가 갖는다.
- **Repository는 저장/통신만.** 캐시 조회/저장/삭제는 `CorrectionSuggestionCacheRepository`(domain 인터페이스) + Impl(data)로 두고, "지문 일치 시에만 반환" 같은 판단은 UseCase에 둔다.
- **복원 로딩은 기존 패턴 재사용.** 새 인디케이터 컴포넌트를 만들지 않고 `Phase.Done`이 쓰는 중앙 `CircularProgressIndicator`를 새 `Phase.Restoring`에 재사용한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionSuggestionCacheDatabase / Entity / DAO (data·신규) | `(userId, language)` PK 행에 `suggestionsJson`·`sessionFingerprint`·`primaryLang`·`cachedAt` 보관. upsert/get/delete |
| CorrectionSuggestionCacheDto + Mapper (data·신규) | `CorrectionSuggestion ↔ DTO` 직렬화/역직렬화(전 필드). 레이어 경계 |
| CorrectionSuggestionCacheRepository(Impl) (신규) | LocalDataSource + Mapper 호출만(저장/통신). 판단 로직 없음 |
| Get/Save/ClearCorrectionCacheUseCase (domain·신규) | 지문 일치 판정·저장·삭제 정책 |
| CorrectionViewModel | Ready 시 캐시 우선 조회 → 히트면 `Restoring`→`Content`, 미스면 기존 generate. 생성 성공 시 저장, 완료 성공 시 삭제 |
| CorrectionUiState | `Phase.Restoring` 추가. 기존 분기 helper 유지 |
| CorrectionScreen | `Restoring` 분기 = 중앙 `CircularProgressIndicator`(Done 패턴 재사용) |
| DatabaseModule (di) | 신규 DB·DAO provider 추가(기존 `provideCorrectionFlashcardDatabase` 미러) |
| 교정 생성·완료 파이프라인(범위 밖) | `triggerGeneration`/`CompleteCorrectionUseCase` 본 로직 불변. 캐시 read/write hook만 추가 |

---

# 주요 작업

1. **로컬 소스 신설** (`data/source/local/CorrectionSuggestionCacheLocalDataSource.kt`): `@Entity("correction_suggestion_cache", primaryKeys=["userId","language"])`, `@Dao`(upsert REPLACE / get / delete), `@Database(version=1) CorrectionSuggestionCacheDatabase`, Room impl. 기존 `CorrectionFlashcard*` 패턴 미러.
2. **직렬화 DTO + Mapper** (`data/model/correction/CorrectionSuggestionCacheDto.kt`): `@Serializable`로 `CorrectionSuggestion` 전 필드 표현, `Json.encodeToString`/`decodeFromString`. `CorrectionSuggestion ↔ DTO` Mapper.
3. **DI** (`di/DatabaseModule.kt`): `provideCorrectionSuggestionCacheDatabase`(DB명 `"umma_correction_suggestion_cache_db"`) + `...Dao` 추가.
4. **도메인 계약·UseCase** (`domain/`): `CorrectionSuggestionCacheRepository` 인터페이스 + `CorrectionSuggestionCacheRepositoryImpl`(data, Hilt 바인딩). `GetCachedCorrectionUseCase`(지문 비교) / `SaveCorrectionCacheUseCase` / `ClearCorrectionCacheUseCase`.
5. **상태 확장** (`CorrectionUiState`): `Phase.Restoring` enum 추가 + KDoc.
6. **ViewModel 배선** (`CorrectionViewModel`): UseCase 3종 주입. `ensureObservation` Ready 분기에서 캐시 우선 조회(히트 → `Restoring`→`Content`, `generationLaunched=true` 유지). `triggerGeneration` 성공 적용부에서 `SaveCorrectionCacheUseCase`. `launchCompletion` 완료 성공 분기에서 `ClearCorrectionCacheUseCase`.
7. **화면 분기** (`CorrectionScreen`): `when(uiState.phase)`에 `Restoring` → 중앙 `CircularProgressIndicator(color = ThemePrimary)`(Done 패턴 재사용).
8. **테스트 보강** (`CorrectionUiStateTest` 등): 지문 일치/불일치 분기, 완료 시 clear 호출, 복원 시 generate 미호출 회귀.

---

# 예외 처리

- 캐시 조회 실패/역직렬화 실패 → 캐시 미스로 간주하고 정상 생성 흐름으로 폴백(손상 행은 무시/삭제). 흐름 비차단.
- 캐시 저장 실패 → 로그만 남기고 진행(다음 진입 시 재생성).
- uid 미확보 → 캐시 조회·저장 생략(기존 생성 흐름).
- 지문(`updatedAt`)이 null이거나 불일치 → 재생성.
- 완료 성공 후 캐시 삭제 실패 → 다음 진입에서 지문 불일치/Empty로 자연 무효화(치명적이지 않음).

---

# 검증 기준

- `:app:compileDevDebugKotlin` 빌드 통과.
- 교정 결과가 뜬 뒤 **저장하지 않고** 뒤로가기 → 재진입 시 원형 인디케이터가 잠깐 뜨고 **동일 카드**가 복원되며, logcat `CorrectionViewModel`에 generate 로그가 없음(수동).
- 앱 강제 종료 후 재실행 → 같은 세션 재진입 시 복원되는지(수동).
- 새 대화 세션을 만든 뒤 교정 진입 → 옛 캐시가 아닌 **새 교정**이 5단계 로딩과 함께 생성되는지(수동).
- 저장(완료) 후 재진입 → 캐시가 비워져 Empty 또는 신규 생성으로 가는지(수동).
- 단위 테스트: 지문 일치/불일치, 완료 시 clear, 복원 시 generate 미호출.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.presentation.correction.CorrectionUiStateTest"
```

---

# Out of Scope

- 캐시의 원격(Firestore) 동기화 — 본 캐시는 **로컬 전용 미저장 임시본**이다.
- 여러 과거 세션의 교정 히스토리 보관/탐색(현재 세션 1건만).
- 캐시 만료(TTL)·용량 관리 정책(세션 지문 불일치 시 자연 폐기로 충분).
- 교정 생성/완료 파이프라인 자체 로직 변경.
