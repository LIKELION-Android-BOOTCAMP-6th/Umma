# [UX] COR-UX-001 교정 로딩 화면 단계 동기화 + 플래시카드 복습

## User Story

사용자는 교정 탭에 진입해 AI 교정 결과를 기다리는 동안, **지금 무슨 작업이 진행 중인지**를 실제 흐름에 맞게 안내받고, 비어 있는 대기 시간에는 **자신이 저장해 둔 학습 카드를 자동으로 복습**할 수 있다.
사용자는 내부 단계 이름이나 점수를 보지 않고, "최근 대화를 불러오고 → 교정할 내용을 고르고 → 내 수준을 반영해 → 표현을 다듬고 → 정리한다"는 자연스러운 진행과, 기다리는 동안 앞면(모국어)→뒷면(교정문)으로 뒤집히는 카드 복습만 체감한다.

---

# 배경

교정 품질이 아니라 **대기 경험(UX)** 을 다루는 카드다. 현재 로딩 화면([CorrectionScreen.kt](../../../../app/src/main/java/com/app/umma/presentation/correction/CorrectionScreen.kt)의 `CorrectionLoading`)에는 두 가지 문제가 있다.

확인된 현황:

- **단계 안내가 실제 작업과 무관한 순수 연출이다.** `CorrectionLoading`이 자체 `LaunchedEffect`로 4초 간격(`LOADING_GUIDE_STEP_INTERVAL_MS`)으로 5개 문구를 순차 노출하고, ViewModel은 별도로 일괄 최소 20초(`MIN_LOADING_GUIDE_DURATION_MS`)만 강제한다([CorrectionViewModel.kt:702](../../../../app/src/main/java/com/app/umma/presentation/correction/CorrectionViewModel.kt)). 즉 "표현을 다듬는 중" 문구가 떠 있을 때 실제 AI 호출은 이미 끝났거나 아직 시작도 안 했을 수 있어, 안내와 실제가 어긋난다.
- **화면 중앙에만 바 인디케이터 + 문구가 있고 상·하단이 비어 있다.** 사용자는 평균 15~20초를 빈 화면으로 기다린다.
- **단계 문구가 실제 작업 경계와 다르게 쪼개져 있다.** 실제 파이프라인은 맥락 조회 → 후보 추출 → 적응 프로파일 → **AI 호출(시간의 95%)** → 결과 매핑인데, 현재 문구는 입력 수집을 한 덩어리로 뭉치고 단일 AI 호출을 "표현 생성/설명 정리/카드 준비" 3개로 쪼갰다.

방향은 **"실제 작업에 단계를 동기화 + 대기 시간을 복습으로 채움"** 이다. ViewModel이 실제 파이프라인 진행에 맞춰 단계를 구동하되 4단계가 실제 AI 호출 시간을 반영하고, 비는 영역에는 학습자의 기존 플래시카드를 오래된 순으로 자동 재생한다. 새 AI 호출이나 새 저장소 메서드는 추가하지 않는다(기존 `GetFlashcardsUseCase` 재사용).

---

# 완료 기준(AC)

## (A) 단계 문구 + 실제 작업 동기화

- [ ] 5단계 문구를 아래로 교체한다(번호 제외, 한글 문구만 적용).
  1. `최근 대화를 불러오고 있어요`
  2. `교정할 내용을 고르고 있어요`
  3. `이용자의 학습 수준을 반영하고 있어요`
  4. `자연스러운 표현으로 다듬고 있어요`
  5. `교정된 내용을 정리하고 있어요`
- [ ] 단계 진행을 화면 자체 타이머가 아니라 **ViewModel이 구동**한다. `CorrectionUiState`에 현재 단계(`loadingStep`)를 두고 화면은 이를 렌더링만 한다.
- [ ] **AI 호출은 진입 즉시 백그라운드로 시작**하고(1단계부터 선행), 1~3단계는 각 최소 3.5초 노출하며 AI 호출과 겹쳐 돌린다.
- [ ] **4단계는 실제 AI 호출이 끝날 때까지 유지**한다(최소 3.5초 보장). AI가 1~3단계 중 이미 끝났으면 4단계는 최소 3.5초만 노출한다.
- [ ] **5단계는 실제로는 즉시 끝나는 결과 매핑이지만 최소 3.5초 노출**한 뒤 결과(`Content`/`EmptyResult`/`Error`)로 전환한다.
- [ ] 기존 일괄 최소 20초 강제(`MIN_LOADING_GUIDE_DURATION_MS`)는 제거하고, 단계별 최소 시간 가드로 대체한다.
- [ ] 말줄임표(`.`) 증감 속도를 더 빠르게 한다(`LOADING_ELLIPSIS_INTERVAL_MS` 하향).

## (B) 레이아웃 재배치

- [ ] 5칸 바 인디케이터를 화면 중앙 → **화면 하단**으로 옮긴다.
- [ ] 진행 문구를 바 인디케이터 **바로 위**에 둔다.
- [ ] 비는 상단/중앙 영역에 플래시카드 복습 영역을 배치한다(세로 Column: 카드 영역 weight → 문구 → 바).

## (C) 플래시카드 자동 복습

- [ ] 현재 학습 언어의 로컬 플래시카드를 **오래된 순(createdAt 오름차순)** 으로 불러온다. `GetFlashcardsUseCase`([app/src/main/java/com/app/umma/domain/usecase/flashcardreview/GetFlashcardsUseCase.kt](../../../../app/src/main/java/com/app/umma/domain/usecase/flashcardreview/GetFlashcardsUseCase.kt))를 재사용한다(새 저장소 메서드 신설 금지). DAO 기본 정렬이 newest-first이므로 메모리에서 정렬한다.
- [ ] 카드 1장당: 앞면 3.5초 → **뒤집기 애니메이션** → 뒷면 4.5초 → 다음 카드(총 8초, 면별 비대칭 — 앞면은 짧게 훑고 뒷면/정답에 더 오래 머문다). 카드 소진 시 처음부터 반복한다. 전환은 카드가 옆모습이 되어 보이지 않는 90°/270° 모서리에서만 일어나도록 표시 콘텐츠를 회전각의 순수 함수로 파생해, 다음 카드의 뒷면이 잠깐 비치는 잔상(글리치)이 생기지 않게 한다.
- [ ] 앞면은 `Flashcard.frontText`(primary language 문장)만, 뒷면은 `Flashcard.backText`(교정된 문장)만 노출한다.
- [ ] SRS 학습 카드의 부가 요소(앞면 "눌러서 교정 확인" 탭 힌트, 뒷면 스피커 아이콘·GrammarNote·"CORRECT ANSWER" 라벨, 하단 Again/Hard 등 평가 버튼)는 모두 제외한다.
- [ ] 부가 요소 제거에 맞춰 카드 크기를 SRS 카드(280dp)보다 약간 작게 한다(높이 270dp — 가독성 보정으로 커진 글자가 스크롤 없이 들어가도록 1차 구현의 230dp에서 확대했다가, 너무 커 보인다는 피드백에 맞춰 300dp에서 다시 살짝 줄였다).
- [ ] 해당 학습 언어 카드가 0개이면, 앞·뒤 모두 `추후 학습 카드 저장 시 교정 로딩 창에 표시됩니다!` 문구를 담은 단일 카드를 같은 형식으로 반복한다.
- [ ] 플래시카드 조회는 교정 파이프라인을 막지 않는다(병렬 로드, 실패 시 빈 목록 → 안내 카드 폴백).

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md` (교정 화면 phase 분기 — Loading/Ready/Generating)
- `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-UX-003_Voice_Interaction_Character.md` (UX 카드 형식·presentation 책임 경계 참고)
- `app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyScreen.kt` (플래시카드 앞/뒷면 시각 원본 — 부가 요소 제외 대상)

---

# 핵심 결정

- **단계 구동 주체는 ViewModel.** 화면 자체 타이머 대신 `loadingStep`을 상태로 노출해 "안내 문구"와 "실제 작업"을 일치시킨다. 화면은 렌더링만 담당한다.
- **AI 호출은 백그라운드 선행(1단계부터 시작).** 1~3단계 연출(각 3.5초)과 겹쳐 돌려 전체 대기를 단축한다. 4단계가 AI 완료 시점을 반영한다(최소 3.5초). PM/사용자 합의 사항.
- **최소 시간 가드는 단계별 3.5초**(1차 구현 3초 → 에뮬레이터 확인 후 사용자 요청으로 조정). 기존 일괄 20초를 대체. 전체 최소 ≈17.5초, AI가 길면 4단계가 그만큼 늘어난다.
- **플래시카드는 읽기 전용 재사용.** `GetFlashcardsUseCase`로 조회만 하고, SRS 스케줄/평가/TTS/문법노트는 일절 건드리지 않는다. 본 화면 카드는 복습 표시 전용이다.
- **3D 뒤집기는 신규 구현.** SRS 카드는 조건부 스왑이라 회전 애니메이션이 없으므로, 본 화면용 `rotationY` 뒤집기를 별도로 만든다.
- 내부 band 이름·점수·단계 코드명은 화면 표면에 노출하지 않는다(기존 원칙 유지).

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionViewModel.triggerGeneration | 실제 파이프라인을 백그라운드로 시작, 단계 타임라인(`loadingStep`)을 구동, 4단계를 AI 완료에 동기화 |
| CorrectionViewModel (플래시카드 로드) | `GetFlashcardsUseCase`로 현재 언어 카드 조회 → 오래된 순 정렬 → `loadingFlashcards`로 노출. 파이프라인 비차단 |
| CorrectionUiState | `loadingStep` / `loadingFlashcards` 필드 + 경량 카드 모델 추가. 기존 phase 분기 helper는 유지 |
| CorrectionScreen.CorrectionLoading | 레이아웃 재배치(카드/문구/바), `loadingStep`·`loadingFlashcards` 렌더링. 자체 단계 타이머 제거 |
| CorrectionScreen.CorrectionLoadingFlashcards (신규) | 카드 순환·뒤집기 애니메이션·앞/뒷면 표시·빈 목록 폴백 |
| GetFlashcardsUseCase / FlashcardRepository (범위 밖) | 조회만 재사용. 쿼리/스케줄/저장 구조는 건드리지 않음 |
| 교정 도메인 파이프라인(완료/저장, 범위 밖) | `CompleteCorrectionUseCase` 등 결과 처리 로직은 변경하지 않음 |

---

# 주요 작업

1. **상태 확장** (`CorrectionUiState`): `loadingStep: Int`, `loadingFlashcards: List<CorrectionLoadingCard>` 필드와 경량 모델(`front`/`back`) 추가 + KDoc.
2. **단계 타임라인 구동** (`CorrectionViewModel.triggerGeneration`): 파이프라인을 `async`로 백그라운드 시작 → 1~3단계 각 3초 → 4단계에서 `await`(최소 3초) → 5단계 최소 3초 → `applyGenerationOutcome` 적용. `MIN_LOADING_GUIDE_DURATION_MS` 제거, `STEP_MIN_DURATION_MS` 추가.
3. **플래시카드 로드** (`CorrectionViewModel`): `GetFlashcardsUseCase(uid, lang)` 별도 `launch` 조회 → `sortedBy { createdAt }` → `CorrectionLoadingCard` 매핑. 실패 무시.
4. **문구/속도** (`CorrectionScreen`): `CorrectionLoadingGuideStep` 라벨 5개 교체, `LOADING_ELLIPSIS_INTERVAL_MS` 하향, 자체 단계 타이머(`LOADING_GUIDE_STEP_INTERVAL_MS`) 제거.
5. **레이아웃** (`CorrectionLoading`): 중앙 Box → 전체 Column(카드 weight 1f → 문구 → 하단 바). `loadingStep`/`loadingFlashcards` 수신.
6. **카드 컴포저블** (`CorrectionLoadingFlashcards` 신규): 순환 로직 + `rotationY` 뒤집기(90도 기준 앞/뒷면 스왑, 뒷면 반전 보정) + 축소 카드 면 + 빈 목록 안내 카드.
7. **테스트 보강**: `loadingStep` 전이/필드 추가 회귀(`CorrectionUiStateTest`), 20초 상수 제거 영향 확인.

---

# 예외 처리

- 해당 언어 플래시카드가 0개거나 조회 실패 → 안내 문구 카드(`추후 학습 카드 저장 시 교정 로딩 창에 표시됩니다!`)를 같은 형식으로 반복(교정 흐름 비차단).
- 카드가 1장뿐 → 그 카드를 반복 재생한다.
- AI 호출 실패 → 단계 타임라인을 거친 뒤 `Error` phase로 전환(기존 Retry 흐름 유지).
- AI가 1~3단계 연출보다 빨리 끝남 → 4단계는 최소 3초만 노출 후 5단계로 진행.
- `Phase.Loading`/`Phase.Ready`의 짧은 프레임 동안 카드/단계 미로딩 → 1단계 문구 + 빈 폴백 카드 노출(곧 Generating으로 전이).

---

# 검증 기준

- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- 단계 1~3이 각 ~3.5초, 4단계가 AI 응답까지 유지, 5단계 ~3.5초 후 결과 카드로 전환되는지(수동).
- 바 인디케이터/문구가 하단에 위치하고, 상단 카드가 앞→뒤 8초 주기(앞 ~3.5초 → 뒤 ~4.5초, 비대칭)로 뒤집히며 소진 시 반복되고, 전환 순간 다음 카드의 뒷면이 비치는 잔상이 없는지(수동).
- 해당 언어 카드 0개일 때 안내 문구 카드가 반복 노출되는지(수동).
- 앞면=primary 문장, 뒷면=교정문만 노출되고 스피커/문법노트/평가버튼/탭힌트가 없는지(수동).
- AI 호출이 교정 1회당 1회로 유지되는지(2-pass 미도입 확인).
- 단위 테스트: `CorrectionUiStateTest` 회귀 통과, 20초 상수 제거가 깨는 테스트 없음.
