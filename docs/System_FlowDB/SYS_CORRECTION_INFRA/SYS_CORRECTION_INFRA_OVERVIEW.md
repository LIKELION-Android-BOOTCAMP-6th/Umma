# SYS-CORRECTION-INFRA 코드 동작 Overview

## 1. 이 문서의 목적

이 문서는 `SYS-CORRECTION-INFRA`에서 준비한 Correction 코드가 실제로 어떤 순서로 동작하는지 설명한다.
`SCI-001_Correction_Contract.md`가 작업 기준과 AC를 다룬다면, 이 문서는 구현자가 코드를 따라가며 책임 경계를 이해하기 위한 안내서다.

Correction의 큰 흐름은 다음과 같다.

```text
RT-003 correction context 조회
→ SessionTurn을 Correction 후보 입력으로 변환
→ Correction 후보 준비
→ 교정 결과 생성
→ 사용자가 저장할 카드 선택
→ Flashcard 저장 요청 생성
→ local-first 저장
→ Firestore sync 시도
→ LangState / SessionSummary / DashSummary 갱신
→ 실패 시 보상 rollback
```

Session Memory 저장/조회/압축 실행은 `SYS-REALTIME-INFRA`의 RT-003 실제 저장소 계약을 사용한다.
Correction 코드는 해당 저장소 구현을 직접 만들지 않고, RT-003 `SessionTurn` read model을 후보 추출 입력으로 변환하는 경계만 담당한다.
Correction 완료 시에는 교정 결과와 분석 대상 turn으로 최소 압축 payload를 만들고, 기존 RT-003 compression 계약에 넘긴다.

---

## 2. 전체 계층 구조

| 계층 | 주요 파일 | 책임 |
| --- | --- | --- |
| `domain/model/correction` | `CorrectionCandidate`, `CorrectionSuggestion`, `CorrectionContracts`, `CorrectionCompletionModels` | Correction에서 주고받는 순수 도메인 계약 |
| `domain/repository` | `CorrectionRepository` | 교정 결과 생성, Flashcard 저장, rollback 계약 |
| `domain/usecase/correction` | `ExtractCandidatesUseCase`, `ExtractSessionCandidatesUseCase`, `GenerateSuggestionsUseCase`, `PrepareSaveRequestUseCase`, `CompleteCorrectionUseCase` | RT-003 turn을 후보 입력으로 변환하고, 후보를 교정 결과/저장/완료 파이프라인으로 연결 |
| `data/repository` | `CorrectionRepositoryImpl`, `FakeCorrectionRepository` | repository 계약의 실제 data 구현과 fake 교체 지점 |
| `data/repository/correction` | `CorrectionAiResponseMapper`, `CorrectionFlashcardStore` | AI 응답 파싱과 local-first 저장 순서 |
| `data/model/correction` | `CorrectionFlashcardDto` | Firestore / local cache가 공유하는 저장 DTO |
| `data/source/local` | `CorrectionFlashcardLocalDataSource` | Room DAO 준비 전까지 local-first 저장 계약을 고정하는 임시 local source |
| `data/source/remote` | `CorrectionFlashcardRemoteDataSource` | Firestore `users/{uid}/flashcards/{flashcardId}` sync |
| `di` | `RepositoryModule` | fake/real repository와 local/remote source 바인딩 |

---

## 3. 후보 준비 흐름

Correction은 Session Memory 저장소를 직접 구현하지 않는다.
`SYS-REALTIME-INFRA`의 RT-003이 제공하는 correction context를 읽고, `ExtractSessionCandidatesUseCase`에서 Correction 후보 추출 입력으로 변환한다.

```text
GetCorrectionContextUseCase(language)
→ Flow<List<SessionTurn>>
→ ExtractSessionCandidatesUseCase(selectedLang, sessionLang, sessionTurns)
→ ExtractCandidatesUseCase
→ List<CorrectionCandidate>
```

역할 분리는 다음과 같다.

| 단계 | 책임 |
| --- | --- |
| RT-003 `SessionMemoryRepository` | 확정 turn 저장, recentFullContext 조회, correction context 제공 |
| `ExtractSessionCandidatesUseCase` | `SessionTurn`을 `ConversationTurn` 입력으로 변환하고 원본 `turnId` 보존 |
| `ExtractCandidatesUseCase` | 언어 일치, 최근 100턴 제한, user turn 필터링, assistant 문맥 첨부 |

`ExtractSessionCandidatesUseCase`는 전달받은 turn 순서를 재정렬하지 않는다.
RT-003 read model은 오래된 turn부터 최신 turn 순서로 제공되어야 하며,
Correction은 그 순서를 기준으로 `sourceTurnIndex`를 계산한다.

## 4. 교정 결과 생성 흐름

### 4.1 입력 모델

교정 결과 생성의 입력은 `GenerateSuggestionsInput`이다.

```text
GenerateSuggestionsInput
→ candidates: List<CorrectionCandidate>
→ langState: LangState
```

- `CorrectionCandidate`는 화면에 노출하지 않는 내부 후보 모델이다.
- 후보는 최근 대화에서 교정할 만한 user turn을 추출한 결과다.
- `assistantContext`는 발화 의미를 보조하는 문맥일 뿐, 화면 카드의 원본이 아니다.
- `LangState`는 현재 선택 언어, 난이도, 학습 상태를 반영하기 위한 snapshot이다.

### 4.2 UseCase 호출

```text
Correction 화면 / ViewModel
→ GenerateSuggestionsUseCase(input)
→ CorrectionRepository.generateSuggestions(input)
```

`GenerateSuggestionsUseCase`는 복잡한 로직을 직접 갖지 않는다.
UseCase의 역할은 presentation이 repository interface만 바라보도록 연결하는 것이다.

### 4.3 현재 data 구현

현재 `CorrectionRepositoryImpl.generateSuggestions()`는 실제 AI API 연결 전까지 `CorrectionSuggestionFixtureBuilder.buildSuggestions(input)`을 사용한다.

```text
GenerateSuggestionsInput
→ CorrectionRepositoryImpl.generateSuggestions()
→ CorrectionSuggestionFixtureBuilder.buildSuggestions()
→ List<CorrectionSuggestion>
```

이 deterministic fixture builder는 실제 AI를 대체하는 최종 구현이 아니다.
목적은 다음과 같다.

- AI 없이도 화면 상태, 저장 요청, 완료 파이프라인을 테스트할 수 있게 한다.
- fake/real이 같은 `CorrectionSuggestion` 계약을 사용하도록 고정한다.
- User Flow에서 실제 AI 연결이 들어와도 domain/presentation 계약이 흔들리지 않게 한다.

### 4.4 실제 AI 연결 시 들어갈 위치

실제 AI API 연결은 `CorrectionRepositoryImpl.generateSuggestions()` 내부 data 계층에 들어간다.

```text
GenerateSuggestionsInput
→ AI request DTO 생성
→ AI API 호출
→ raw JSON 응답 수신
→ CorrectionAiResponseMapper.map(rawJson, input)
→ List<CorrectionSuggestion>
```

중요한 점은 ViewModel, UseCase, domain model이 raw JSON이나 AI SDK를 직접 알면 안 된다는 것이다.
AI 응답 원문과 파싱 구조는 data 계층 내부에 머물러야 한다.

---

## 5. AI 응답 파싱 흐름

`CorrectionAiResponseMapper`는 실제 AI 응답을 `CorrectionSuggestion`으로 변환하는 mapper다.

기대하는 최소 JSON 구조는 다음과 같다.

```json
{
  "suggestions": [
    {
      "candidateId": "en-0-a",
      "nativeText": "나는 학교에 간다",
      "afterText": "I go to school.",
      "explanation": "Use 'go to school' instead of 'go school'."
    }
  ]
}
```

mapper는 다음 순서로 동작한다.

```text
raw JSON
→ CorrectionAiResponseDto 디코딩
→ candidateId를 GenerateSuggestionsInput.candidates와 매칭
→ 후보 언어와 LangState 언어 일치 검증
→ nativeText / afterText / explanation 필수값 검증
→ CorrectionSuggestion 생성
```

필드별 의미는 다음과 같다.

| AI 응답 필드 | 사용 위치 | 설명 |
| --- | --- | --- |
| `candidateId` | `sourceCandidateIds`, `sourceTurnIndex` 추적 | 어떤 내부 후보에서 나온 교정인지 확인한다. |
| `nativeText` | Flashcard 앞면 | 사용자의 모국어 문장이다. |
| `afterText` | Correction 카드 / Flashcard 뒷면 | 교정된 외국어 문장이다. |
| `explanation` | Correction 카드 / Flashcard 뒷면 | 짧은 교정 설명이다. |

`beforeText`는 AI 응답을 믿지 않고, 원본 후보의 `sourceText`를 사용한다.
이렇게 해야 AI가 원문을 임의로 바꾸더라도 교정 전 문장이 흔들리지 않는다.

---

## 6. CorrectionSuggestion의 역할

`CorrectionSuggestion`은 화면 표시와 저장 선택을 동시에 연결하는 중심 모델이다.

```text
CorrectionSuggestion
→ id
→ lang
→ sourceCandidateIds
→ sourceTurnIndex
→ beforeText
→ nativeText
→ afterText
→ explanation
```

- `beforeText`: 교정 전 외국어 문장
- `nativeText`: Flashcard 앞면이 될 모국어 문장
- `afterText`: Flashcard 뒷면에 표시할 교정된 외국어 문장
- `explanation`: 뒷면에 함께 표시할 설명

`CorrectionSuggestion`은 `CorrectionResult`와 다르다.
`CorrectionSuggestion`은 화면 카드와 Flashcard 저장 선택을 위한 모델이고,
`CorrectionResult`는 `LangState` 업데이트에 필요한 최소 요약 모델이다.

---

## 7. 저장 요청 준비 흐름

사용자가 저장할 교정 결과를 선택하면 `PrepareSaveRequestUseCase`가 `CorrectionSaveRequest`를 만든다.

```text
selectedSuggestions
→ distinctBy(id)로 중복 제거
→ 언어가 모두 같은지 검증
→ nativeText / afterText 필수값 검증
→ CorrectionFlashcardSaveItem 생성
→ CorrectionSaveRequest 생성
```

저장 요청의 카드 구성은 다음 기준을 따른다.

| Flashcard 저장 항목 | 값 |
| --- | --- |
| `suggestionId` | `CorrectionSuggestion.id` |
| `frontText` | `CorrectionSuggestion.nativeText` |
| `backText` | `CorrectionSuggestion.afterText` |
| `explanation` | `CorrectionSuggestion.explanation` |

MVP 발음 재생은 별도 `pronunciationText` 필드를 저장하지 않는다.
SRS 화면에서 뒷면의 `backText`를 Android `TextToSpeech` 입력으로 사용한다.

---

## 8. Flashcard 저장 흐름

`CorrectionRepository.saveFlashcards()`는 `CorrectionFlashcardStore`에 저장 순서를 위임한다.

```text
CorrectionSaveRequest
→ CorrectionRepositoryImpl.saveFlashcards()
→ CorrectionFlashcardStore.save()
→ CorrectionFlashcardDto 변환
→ localDataSource.saveFlashcards()
→ remoteDataSource.syncFlashcards()
→ CorrectionSaveResult
```

### 8.1 DTO 변환

`CorrectionFlashcardSaveItem.toCorrectionFlashcardDto()`는 domain 저장 요청을 data 저장 DTO로 바꾼다.

```text
CorrectionFlashcardDto
→ id
→ language
→ sourceSuggestionId
→ frontText
→ backText
→ explanation
→ source
→ createdAt
→ updatedAt
→ nextReviewAt
→ interval
→ easeFactor
→ dirty
```

Firestore 필드명은 DTO의 camelCase 이름을 그대로 사용한다.

| 필드 | 기준 |
| --- | --- |
| `language` | LS-001 표준 언어 필드명 |
| `frontText` | 모국어 문장 |
| `backText` | 교정된 외국어 문장 |
| `nextReviewAt` | SRS due 판정 기준 |
| `interval` | SRS 정책이 정하는 복습 간격 값 |
| `easeFactor` | SM-2 기반 반복학습 난이도 계수 |
| `dirty` | Firestore sync가 아직 끝나지 않은 local 상태 표시 |

`id`는 현재 `suggestionId`와 동일하게 둔다.
이렇게 하면 같은 교정 결과를 중복 저장해도 같은 Flashcard로 합쳐진다.

`sourceSuggestionId`도 현재는 `id`와 같지만, 나중에 카드 ID 정책이 바뀌어도 어떤 교정 결과에서 만들어진 카드인지 추적하기 위해 별도 필드로 유지한다.

### 8.2 local-first 저장

현재는 Flashcard Room DAO가 준비되기 전이므로 `InMemoryCorrectionFlashcardLocalDataSource`가 local 저장 계약을 임시로 담당한다.

```text
flashcardsById
→ flashcard.id 기준 중복 확인
→ 새 카드만 저장
→ 저장된 card id 목록 반환
```

이 구현은 영구 저장소가 아니라 계약 고정용이다.
SRS 인프라에서 Room Entity / DAO가 준비되면 `CorrectionFlashcardLocalDataSource`의 구현만 Room 기반으로 교체한다.
domain UseCase와 `CorrectionRepository` 계약은 바뀌지 않아야 한다.

### 8.3 Firestore sync

local 저장이 성공한 새 카드만 Firestore sync 대상으로 보낸다.

```text
newlySavedFlashcards
→ FirestoreCorrectionFlashcardRemoteDataSource.syncFlashcards()
→ users/{uid}/flashcards/{flashcardId}
→ batch.set(...)
```

Firestore에는 `dirty = false`로 저장한다.
local 원본은 sync 결과를 보고 pending 여부를 판단한다.

Firestore sync가 실패해도 local 저장 성공을 사용자 저장 실패로 바꾸지 않는다.
대신 `CorrectionSaveResult.pendingSyncFlashcardIds`에 남겨 후속 sync 대상으로 다룬다.

---

## 9. 완료 파이프라인 흐름

`CompleteCorrectionUseCase`는 사용자가 선택한 교정 결과를 저장하고, 교정 완료 후 필요한 로컬 상태 정리를 한 번에 묶는다.

```text
CompleteCorrectionInput
→ PrepareSaveRequestUseCase
→ CorrectionRepository.saveFlashcards()
→ ApplyLanguageStateUpdateUseCase
→ CompleteCorrectionResult
```

각 단계의 책임은 다음과 같다.

| 단계 | 책임 |
| --- | --- |
| `PrepareSaveRequestUseCase` | 선택 결과를 Flashcard 저장 요청으로 정규화 |
| `CorrectionRepository.saveFlashcards` | 새 Flashcard local-first 저장과 Firestore sync 시도 |
| `ApplyLanguageStateUpdateUseCase` | `LangState`, `SessionSummary`, `DashSummary` 갱신 |

`ApplyLanguageStateUpdateUseCase` 호출 시에는 `correctionAvailableOverride = false`가 들어간다.
교정이 완료된 세션은 다시 교정 대기 상태로 남아 있으면 안 되기 때문이다.

Session Memory compression의 저장/초기화 실행은 Realtime-infra가 제공하는 RT-003 계약이다.
Correction 완료 파이프라인은 선택된 교정 결과와 분석 대상 user turn으로 `recentTopics`, `topicSummaries`, `topicKeySentences`를 만든다.
그 결과가 비어 있지 않을 때만 RT-003 `CompressSessionMemoryUseCase`를 호출한다.
빈 payload로 원문 buffer를 지우는 구현은 허용하지 않는다.
compression 실패는 Flashcard 저장과 학습 상태 갱신을 롤백하지 않고 `sessionCompressionPending`으로 남긴다.

완료 결과는 다음 값을 화면으로 돌려준다.

```text
CompleteCorrectionResult
→ savedFlashcardIds
→ pendingSyncFlashcardIds
→ sessionMemoryKey
→ sessionCompressionApplied / sessionCompressionPending
→ completedAt
```

---

## 10. 실패와 rollback 흐름

`CompleteCorrectionUseCase`는 저장 또는 상태 갱신 중 하나라도 실패하면 catch 블록으로 들어간다.

```text
try
→ saveFlashcards 성공
→ applyLanguageStateUpdate 성공
→ 완료

catch
→ Correction-infra가 성공시킨 local 저장만 rollback
→ Result.failure(error)
```

rollback 순서는 다음 기준을 따른다.

1. Flashcard 저장이 성공했다면 `CorrectionRepository.rollbackFlashcards()`를 호출한다.
2. 아직 성공하지 않은 단계는 rollback하지 않는다.
3. Session Memory compression 실패는 완료 실패로 키우지 않고 pending 결과로 남긴다.

Flashcard rollback은 local 저장을 반드시 되돌린다.
remote delete는 best-effort다.
이미 Firestore sync가 성공했을 수 있으므로 삭제를 시도하지만, remote delete 실패를 다시 사용자 흐름의 fatal error로 키우지는 않는다.

Firestore sync만 실패한 경우는 local completion 실패가 아니다.
이 경우 rollback하지 않고 pending sync 상태로 남긴다.

---

## 11. DI와 fake/real 교체

`RepositoryModule`은 다음 바인딩을 제공한다.

```text
CorrectionRepository
→ CorrectionRepositoryImpl

CorrectionFlashcardLocalDataSource
→ InMemoryCorrectionFlashcardLocalDataSource

CorrectionFlashcardRemoteDataSource
→ FirestoreCorrectionFlashcardRemoteDataSource
```

`FakeCorrectionRepository`는 `CorrectionRepositoryImpl`을 상속한다.
현재 fake는 별도 저장 로직을 다시 만들지 않고 같은 `CorrectionFlashcardStore`를 사용한다.
따라서 fake 환경에서도 저장 요청, 중복 방지, pending sync, rollback 계약을 같은 방식으로 검증할 수 있다.

실제 AI 응답 품질이나 prompt는 fake가 보장하지 않는다.
fake는 화면 상태와 저장 파이프라인을 AI 없이 테스트하기 위한 보조 수단이다.

---

## 12. 테스트로 확인되는 계약

| 테스트 | 확인하는 내용 |
| --- | --- |
| `ExtractSessionCandidatesUseCaseTest` | RT-003 `SessionTurn` 입력을 Correction 후보 입력으로 변환하고 `sourceTurnId`를 보존하는지 확인 |
| `BuildSessionCompressionPayloadUseCaseTest` | 교정 결과와 분석 대상 turn으로 RT-003 compression payload를 만들고, 빈 payload 호출을 막는지 확인 |
| `GenerateSuggestionsUseCaseTest` | 후보 입력이 교정 결과 계약으로 연결되는지 확인 |
| `PrepareSaveRequestUseCaseTest` | 선택 결과 중복 제거, 언어 검증, 필수 텍스트 검증 |
| `CompleteCorrectionUseCaseTest` | 저장, 상태 갱신, compression 호출 순서와 실패 시 rollback/pending 정책 |
| `CorrectionRepositoryImplTest` | local-first 저장, Firestore pending sync, 중복 저장 방지, rollback |
| `CorrectionAiResponseMapperTest` | AI JSON 파싱, candidateId 매칭, 필수 필드 검증 |
| `FakeCorrectionRepositoryTest` | fake 구현도 같은 저장 계약을 사용하는지 확인 |

---

## 13. 구현자가 주의할 점

- `CorrectionCandidate`는 내부 후보이며 화면에 후보 목록으로 노출하지 않는다.
- RT-003의 `SessionTurn` 저장/조회 구현은 Realtime-infra 책임이며, Correction은 `ExtractSessionCandidatesUseCase`로 후보 입력 변환만 담당한다.
- `CorrectionSuggestion`은 화면 카드와 저장 선택의 기준 모델이다.
- 실제 AI 연결은 data 계층에서 처리하고, ViewModel / UseCase가 raw JSON이나 SDK에 직접 의존하지 않는다.
- AI 응답의 `candidateId`는 반드시 기존 후보와 매칭되어야 한다.
- `beforeText`는 AI 응답이 아니라 후보의 `sourceText`를 사용한다.
- Flashcard 앞면은 `nativeText`, 뒷면은 `afterText + explanation`이다.
- 발음 재생용 별도 필드는 저장하지 않고, SRS 화면에서 `backText`를 TTS 입력으로 사용한다.
- Flashcard 저장 결과 필드는 `savedFlashcardIds`, `pendingSyncFlashcardIds` 기준으로 이해한다.
- Firestore field는 `language`, `frontText`, `backText`, `nextReviewAt`, `interval`, `easeFactor`처럼 camelCase 기준을 사용한다.
- `lang` 필드명으로 Firestore에 저장하지 않는다.
- `interval`은 날짜 단위로 고정하지 않고 SRS 정책이 정하는 간격 값으로 해석한다.
- SRS는 이미 저장된 Flashcard를 조회하고 review schedule을 갱신한다. 새 Flashcard 최초 생성 저장은 Correction 책임이다.

---

## 14. 한 줄 요약

> Correction 코드는 RT-003 Session Memory read model을 후보 입력으로 변환하고, AI 교정 결과를 `CorrectionSuggestion`으로 표준화한 뒤, 사용자가 선택한 결과를 Flashcard로 local-first 저장하고 학습 상태를 정리하도록 설계되어 있다.
