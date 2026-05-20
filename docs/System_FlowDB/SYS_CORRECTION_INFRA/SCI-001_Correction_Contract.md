# [Infra] SCI-001 Correction 선행 계약 및 최소 인프라 정리

## User Story

Correction User Flow 작업자는 화면 구현을 시작하기 전에,
교정 기능에서 사용할 모델, Repository 계약, UseCase 경계, mock/real 교체 기준을 명확히 알고 작업할 수 있어야 한다.

---

## 완료 기준(AC)

- [x] 기존 `feedback` 화면/라우트/콜백 명칭을 `correction` 기준으로 정리할 대상이 확인된다.
- [x] `CorrectionCandidate`의 역할이 내부 후보 추출 모델로 정의된다.
- [x] 현재 선택 언어와 Session Memory 언어가 일치할 때만 후보를 추출한다.
- [x] `CorrectionSuggestion`의 역할이 화면 카드 표시 및 Flashcard 저장 선택 모델로 정의된다.
- [x] AI 응답을 `CorrectionSuggestion`으로 변환하는 data 계층 mapper 계약이 준비된다.
- [x] 기존 `CorrectionResult`가 LangState 업데이트 입력용 최소 모델임을 문서와 코드 주석에서 구분한다.
- [x] Correction 화면 진입 기준이 `SessionSummary.correctionAvailable`임을 확정한다.
- [x] `DashSummary.correctionAvailable`은 Dashboard 표시용 파생값임을 확정한다.
- [x] 교정 완료 시 `SessionSummary`와 `DashSummary`를 같은 완료 흐름에서 함께 갱신하는 계약을 마련한다.
- [x] Session Memory의 `recentFullContext`는 화면에서 직접 파싱하지 않고 domain UseCase를 통해 다룬다.
- [x] MVP 후보 추출 범위가 최근 100턴으로 제한된다.
- [x] 필요한 assistant turn은 짧은 문맥으로만 첨부한다.
- [x] AI 교정 요청과 선택 결과 저장 요청을 함께 다루는 `CorrectionRepository` domain repository interface가 준비된다.
- [x] Flashcard 저장은 `CorrectionRepository`의 저장 계약 안에서 local first로 처리한다.
- [x] Flashcard 저장 결과가 Firestore sync pending 상태를 표현할 수 있도록 준비된다.
- [x] 완료 정리를 위한 `CompleteCorrectionUseCase` usecase 경계가 준비된다.
- [x] Flashcard 저장, LangState 업데이트, Summary 갱신이 하나의 로컬 완료 파이프라인으로 묶인다.
- [x] RT-003 `SessionTurn` read model을 Correction 후보 입력으로 변환하는 연결 경계가 준비된다.
- [x] Correction 완료 흐름에서 RT-003 compression에 넘길 최소 압축 payload 생성 책임이 정의된다.
- [x] 압축 저장/초기화 실행은 RT-003 `CompressSessionMemoryUseCase` 계약을 사용하도록 명시된다.
- [x] Flashcard 저장 또는 LangState/Summary 갱신 실패 시 Correction-infra가 직접 수행한 로컬 변경을 rollback하고 Retry 상태로 남긴다.
- [x] mock/real 교체가 Hilt binding 기준으로 가능해야 한다.

---

## 구현 범위

### 포함 범위

- `presentation/feedback` → `presentation/correction` 명칭 정리 기준
- Correction domain 모델 계약
- Correction Repository interface
- 후보 추출 UseCase 계약
- 교정 결과 생성 UseCase 계약
- AI 응답 DTO / mapper 계약
- CorrectionRepository 기반 교정 결과 파생 Flashcard 저장 계약
- Flashcard local 저장과 Firestore sync로 이어질 수 있는 data 저장 경계
- 완료 정리 UseCase 계약
- fake repository / real repository 교체 기준
- User Flow `COR-001 ~ COR-007`이 사용할 공통 인터페이스 정리

### 제외 범위

- 교정 화면 세부 UI 구현
- 교정 결과 카드 디자인
- 실제 AI prompt 품질 고도화
- SRS 복습 스케줄 계산
- Dashboard 카드 UI 구현
- AI Chat turn append 구현

---

## 권장 파일/패키지 방향

최종 파일명은 팀장이 정한 패키지 구조와 충돌하지 않는 범위에서 조정할 수 있다.
다만 책임 위치는 아래 방향을 따른다.

```text
domain/model/correction
→ CorrectionCandidate
→ CorrectionSuggestion

domain/repository
→ CorrectionRepository

domain/usecase/correction
→ ExtractCandidatesUseCase
→ ExtractSessionCandidatesUseCase
→ GenerateSuggestionsUseCase
→ PrepareSaveRequestUseCase
→ CompleteCorrectionUseCase

data/repository
→ CorrectionRepositoryImpl
→ FakeCorrectionRepository
→ CorrectionAiResponseMapper
→ CorrectionFlashcardStore
→ CorrectionFlashcardLocalDataSource
→ CorrectionFlashcardRemoteDataSource

presentation/correction
→ CorrectionScreen
→ CorrectionViewModel
→ CorrectionUiState
```

이미 `learningstate` 패키지에 존재하는 `CorrectionResult`는 LangState 업데이트 입력용으로 유지한다.
화면 표시용 결과 모델을 여기에 억지로 섞지 않는다.

---

## 핵심 계약

### 1. 진입 상태 계약

```text
GlobalLangState.selectedLang
→ currentSessionSummary()
→ SessionSummary.correctionAvailable
→ currentLangState()
```

- `SessionSummary.correctionAvailable == false`이면 Correction 화면은 Empty 상태를 표시한다.
- `DashSummary.correctionAvailable`은 진입 판단에 사용하지 않는다.

### 2. 후보 추출 계약

```text
RT-003 correction context
→ SessionTurn read model
→ Correction 후보 입력 변환
→ 최근 100턴 제한
→ user turn 중심 후보 추출
→ 필요한 assistant turn만 짧은 문맥으로 첨부
→ CorrectionCandidate 내부 목록 생성
```

- 사용자에게 후보 목록을 보여주지 않는다.
- Composable은 `recentFullContext`를 직접 파싱하지 않는다.
- 후보가 없으면 AI 요청 없이 Empty 상태로 이어진다.
- RT-003이 교정용 read model을 제공하더라도 후보 확정 규칙은 Correction domain UseCase가 담당한다.
- `ExtractSessionCandidatesUseCase`는 `SessionTurn`을 `ConversationTurn` 형태의 후보 추출 입력으로 변환하고, 원본 `turnId`를 `CorrectionCandidate.sourceTurnId`로 보존한다.
- `recentFullContext`는 오래된 turn부터 최신 turn 순서로 전달되는 것을 전제로 한다.

### 3. 교정 결과 계약

```text
CorrectionCandidate + LangState snapshot
→ CorrectionRepository
→ 실제 AI 교정 API
→ AI 응답 파싱 및 필수 필드 검증
→ CorrectionSuggestion list
```

- `CorrectionSuggestion`은 화면 카드와 Flashcard 저장 선택의 기준 모델이다.
- MVP User Flow에서는 실제 AI 교정 API를 호출하고, 응답을 파싱해 `CorrectionSuggestion` 목록으로 변환하는 동작까지 구현한다.
- MVP 최소 프롬프트 계약은 실제 AI 연동에 포함하며, `LangState` snapshot과 후보 문맥을 바탕으로 교정 수준, 의미 보존, 필수 응답 필드, `candidateId` 유지를 요청해야 한다.
- 교정 품질 향상을 위한 세부 prompt tuning, JSON schema 정교화, retry/fallback 고도화는 후속 개선 범위로 둔다.
- 이번 선행 이슈에서는 mock/real이 같은 결과 계약을 사용하도록 경계를 잡고, 실제 AI 응답도 data 계층 mapper를 거쳐 같은 `CorrectionSuggestion` 계약으로 변환되도록 준비한다.
- 실제 AI API client 주입과 호출 구현은 `CorrectionRepository` data 구현체 안에서 처리하며, ViewModel / UseCase는 raw JSON이나 API SDK를 직접 알지 않는다.
- `CorrectionRepository`는 교정 결과 생성과 선택된 교정 결과의 Flashcard 저장 요청만 담당한다.
- 반복학습용 Flashcard 조회와 화면은 별도 학습 계약을 따른다.
- 후보 추출 규칙, 저장 대상 선택, 완료 순서 결정은 Repository가 아니라 UseCase가 담당한다.

### 4. 저장 계약

```text
선택된 CorrectionSuggestion
→ Flashcard 저장 요청 모델 변환
→ CorrectionRepository.saveFlashcards(...)
→ local first 저장(Room DAO 준비 전까지 in-memory local data source)
→ Firestore background sync
```

- Flashcard 앞면은 모국어 문장으로 구성한다.
- Flashcard 뒷면은 교정된 외국어 문장과 짧은 교정 설명으로 구성한다.
- 발음 재생은 뒷면의 교정된 외국어 문장을 대상으로 하며, MVP에서는 Android `TextToSpeech`를 사용한다.
- local 저장 성공 후 Firestore sync 실패는 사용자 저장 실패로 보지 않는다.
- sync 실패 상태는 pending sync / dirty flag 개념으로 관리한다.
- 저장 항목이 0개이면 완료 파이프라인으로 바로 넘기지 않는다.
- 별도 `FlashcardRepository` 생성을 이번 선행 계약의 필수 조건으로 두지 않는다.
- 교정 흐름의 저장 계약은 `CorrectionRepository` 안에 두고, 학습/복습용 Flashcard 흐름은 별도 화면 계약에서 다룬다.
- Firestore에 저장되는 Flashcard 문서 구조는 SRS에서 읽을 수 있는 원본 카드 계약과 충돌하지 않아야 한다.
- 반복학습 조회, due deck 계산, review schedule update는 `SYS-SRS-INFRA`의 `FlashcardRepository` 책임으로 분리한다.
- 현재 단계의 local data source는 Room DAO가 준비되기 전까지 in-memory 구현으로 local-first 순서를 고정한다.
- Firestore remote data source는 `users/{uid}/flashcards/{flashcardId}` 경로에 sync할 수 있어야 하며, 실패 시 저장 결과의 pending sync 목록으로 남긴다.
- MVP 발음 재생은 뒷면의 `backText`를 사용하므로 별도 `pronunciationText` 저장 계약은 필요하지 않다.

### 5. 완료 계약

```text
사용자가 저장할 CorrectionSuggestion 선택
→ CompleteCorrectionUseCase 호출
→ Flashcard local first 저장
→ LangState 업데이트 입력 생성 및 적용
→ SessionSummary.correctionAvailable false
→ DashSummary.correctionAvailable 동시 반영
→ Firestore background sync 예약
```

Session Memory 저장/조회/압축 구현은 `SYS-REALTIME-INFRA`의 `RT-003`이 제공하는 실제 `SessionMemory` 저장소 계약을 따른다.
Correction-infra는 `SessionMemoryRepository` / `SessionMemoryRepositoryImpl`을 직접 만들지 않는다.
Correction-infra는 RT-003의 correction context를 읽은 뒤 `SessionTurn`을 후보 추출 입력으로 변환하는 경계까지만 담당한다.
Correction-infra는 선택된 `CorrectionSuggestion`과 `LangStateUpdateInput.recentUserTurns`를 바탕으로 RT-003에 넘길 최소 압축 payload를 생성한다.
압축 payload는 `recentTopics`, `topicSummaries`, `topicKeySentences`로 구성한다.
RT-003은 전달받은 `CompressSessionMemoryCommand`를 저장하고 `recentFullContext`를 정리하는 실행 책임을 가진다.
압축 payload가 비어 있으면 빈 요약으로 Session Memory를 정리하지 않도록 RT-003 compression을 호출하지 않는다.

- 교정 화면을 단순히 이탈했다고 완료 처리하지 않는다.
- `SessionSummary`와 `DashSummary` 중 하나만 갱신하는 구현은 허용하지 않는다.
- LangState 업데이트가 필요한 경우 `LS-006` 정책과 `CorrectionResult` 입력을 따른다.
- Flashcard 저장, LangState 업데이트, Summary 갱신은 하나의 로컬 완료 파이프라인으로 묶는다.
- compression 실패는 Flashcard 저장과 LangState/Summary 갱신을 롤백하지 않고 후속 재시도 대상으로 남긴다.
- Flashcard 저장 또는 LangState/Summary 갱신이 실패하면 Correction-infra가 직접 수행한 로컬 변경을 rollback하고 Retry 상태로 남긴다.
- 저장소가 하나의 transaction으로 묶이지 않는 경우 보상 rollback 또는 commit marker 방식으로 부분 완료 상태를 남기지 않는다.
- Firestore background sync는 로컬 완료 이후 비동기로 수행하며, sync 실패는 pending sync / dirty flag로 관리한다.

---

## mock/real 교체 기준

- UI 개발자는 `FakeCorrectionRepository`로 결과 카드, 저장 상태, 완료 파이프라인 성공/실패 상태를 먼저 구현할 수 있다.
- 실제 AI API 연결은 `CorrectionRepository` interface 뒤에 둔다.
- fake/mock 또는 `CorrectionSuggestionFixtureBuilder`는 AI 없이도 화면과 저장 파이프라인을 테스트하기 위한 보조 수단이며, 실제 AI 연동 완료 기준을 대체하지 않는다.
- ViewModel과 Composable은 fake/real 구현체를 구분하지 않는다.
- 여러 repository를 함께 fake로 바꿔야 하는 경우 `USER_FLOW_MOCK_REAL_DATA_GUIDE.md`의 build variant와 Hilt binding 기준을 따른다.
- 발음 재생은 MVP에서 Android `TextToSpeech`를 사용하고, 추후 클라우드 TTS로 교체 가능한 구조를 유지한다.

---

## User Flow 인계 기준

`SCI-001`이 완료되면 다음 작업이 가능해야 한다.

- `COR-001`: Correction 초기 상태 로드
- `COR-002`: 교정 결과 생성
- `COR-003`: 교정 결과 카드 표시
- `COR-004`: 저장 카드 선택 상태 구현
- `COR-005`: Flashcard 저장 요청 준비
- `COR-006`: 교정 완료 결과 연결
- `COR-007`: Dashboard 복귀 및 후처리 구현

User Flow 작업자는 이 문서의 공통 계약을 변경하지 않고, 각 이슈의 AC 범위 안에서 구현한다.
공통 계약 변경이 필요하면 `SCI-001` 문서를 먼저 수정하고 팀장/부팀장 리뷰를 거친다.
