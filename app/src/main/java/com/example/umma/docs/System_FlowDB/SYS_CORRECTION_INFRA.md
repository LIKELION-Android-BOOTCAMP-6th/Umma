# SYS-CORRECTION-INFRA

## 1. 목표 및 범위

- 목표: 사용자가 AI Chat 이후 교정 화면에 진입했을 때, 현재 선택 언어의 Session Memory와 LangState snapshot을 바탕으로 교정 후보를 추출하고, 선택한 교정 결과를 Flashcard로 저장할 수 있는 교정 인프라를 구축한다.
- 성공 조건: 교정 진입, 후보 추출, 교정 결과 생성, Flashcard 저장, 교정 완료 정리가 하나의 표준 흐름으로 동작한다.

교정은 대화 중 실시간으로 수행하지 않는다.
AI Chat에서 확정된 turn이 Session Memory에 저장된 뒤, 사용자가 교정 화면에 진입하거나 명시적으로 교정을 요청할 때만 수행한다.

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. 교정 진입 준비 | `selectedLearningLanguage`, `DashSummary`, `SessionSummary`, LangState snapshot 확인 | 현재 선택 언어 기준 교정 가능 상태 준비 | 성공: 교정 가능 상태 진입 / 실패: 언어 미선택 또는 세션 없음 | COR-001 |
| 2. 교정 후보 추출 | Session Memory의 `recentFullContext`에서 user turn 중심 후보 추출 | 교정 요청용 후보 목록 생성 | 성공: 교정 후보 생성 / 실패: 후보 없음 또는 원문 부족 | COR-002 |
| 3. 교정 제안 생성 | 후보와 LangState snapshot을 기반으로 AI 교정 제안 생성 | 교정 문장, 설명, 저장 후보 출력 | 성공: 교정 결과 표시 / 실패: AI 응답 실패 또는 파싱 실패 | COR-003 |
| 4. Flashcard 저장 | 사용자가 선택한 교정 결과를 Flashcard로 저장 | Flashcard 저장 및 요약 갱신 요청 | 성공: 선택 문장 저장 / 실패: 저장 또는 동기화 실패 | COR-004 |
| 5. 교정 완료 정리 | 세션 압축, 원문 buffer 정리, `correctionAvailable` 갱신 요청 | 다음 대화와 Dashboard 상태 준비 | 성공: 압축 및 요약 반영 / 실패: 정리 지연 또는 재시도 필요 | COR-005 |

---

## 3. 책임 경계

### 포함 범위

- 현재 선택 언어 기준 교정 세션 조회
- Session Memory의 `recentFullContext` 접근
- user turn 중심 교정 후보 추출
- LangState snapshot 기반 교정 난이도와 설명 수준 결정
- 교정 요청용 payload 구성
- 교정 결과 표시용 모델 정의
- Flashcard 저장 요청 모델 정의
- Flashcard 저장을 위한 Repository 계약 및 local first 저장 흐름 준비
- 교정 완료 후 Session Memory 압축 요청
- `correctionAvailable`, `recentSavedFlashcards`, `dueFlashcards` 갱신 요청

### 제외 범위

- Firebase Live API 세션 관리
- Dashboard 카드 UI 렌더링
- Flashcard 반복학습 UI
- LangState 수치 계산 정책
- Room Entity / DAO 세부 최적화
- 교정 품질 고도화용 장기 오류 패턴 분석

> Correction flow는 `recentFullContext`를 읽고 후보를 만들지만, 저장소 내부 구조를 직접 수정하지 않는다.
> 원문 append와 압축은 `SYS-REALTIME-INFRA`의 Session Memory 경계를 따르고, 상태 수치 계산은 `SYS-LEARNING-STATE-INFRA`의 LS-006 정책을 따른다.

---

## 4. 클린 아키텍처 경계

| 계층 | 책임 | 예시 |
| --- | --- | --- |
| `presentation` | 화면 상태 표시, 사용자 선택, 로딩/에러/저장 이벤트 전달 | `CorrectionScreen`, `CorrectionViewModel`, `CorrectionUiState` |
| `domain` | 후보 추출 규칙, 교정 요청/결과 모델, UseCase, Repository interface | `CorrectionCandidate`, `CorrectionSuggestion`, `ExtractCorrectionCandidatesUseCase` |
| `data` | AI 교정 요청 구현, Session Memory/Flashcard 저장소 접근, DTO 변환 | `CorrectionRepositoryImpl`, `SessionMemoryRepositoryImpl`, `FlashcardRepositoryImpl` |
| `di` | fake/real 구현체 주입, API 비용 절감을 위한 구현체 교체 | `RepositoryModule`, feature별 fake module |

권장 UseCase 단위:

- `PrepareCorrectionEntryUseCase`
- `ExtractCorrectionCandidatesUseCase`
- `GenerateCorrectionSuggestionsUseCase`
- `SaveCorrectionFlashcardsUseCase`
- `CompleteCorrectionSessionUseCase`

교정 ViewModel은 위 UseCase만 호출한다.
Composable 내부에서 `recentFullContext`를 직접 파싱하거나, AI 요청을 직접 호출하지 않는다.

---

## 5. GitHub Issue (실행 기준 / SSOT)

- [COR-001_Correction_Entry.md](./SYS_CORRECTION_INFRA/COR-001_Correction_Entry.md)
- [COR-002_Correction_Candidate_Extraction.md](./SYS_CORRECTION_INFRA/COR-002_Correction_Candidate_Extraction.md)
- [COR-003_Correction_Suggestion_Generation.md](./SYS_CORRECTION_INFRA/COR-003_Correction_Suggestion_Generation.md)
- [COR-004_Correction_Flashcard_Save.md](./SYS_CORRECTION_INFRA/COR-004_Correction_Flashcard_Save.md)
- [COR-005_Correction_Completion.md](./SYS_CORRECTION_INFRA/COR-005_Correction_Completion.md)

세부 AC와 구현 범위는 각 하위 issue 문서를 기준으로 확인한다.

---

## 6. 연결 문서

- [SYS_LEARNING_STATE_INFRA.md](./SYS_LEARNING_STATE_INFRA.md)
- [SYS_LEARNING_STATE_INFRA_OVERVIEW.md](./SYS_LEARNING_STATE_INFRA/SYS_LEARNING_STATE_INFRA_OVERVIEW.md)
- [SYS_REALTIME_INFRA.md](./SYS_REALTIME_INFRA.md)
- [RT-003_Turn_Commit.md](./SYS_REALTIME_INFRA/RT-003_Turn_Commit.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](./SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [LS-006_Language_State_Update_Policy.md](./SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [FLOW_DASHBOARD.md](../User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-003_Correction_Pending_Card.md](../User_FlowDB/FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md)
- [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](../User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)
- [Umma_Service_Structure.md](../Umma_Service_Structure.md)
- [Umma_Data_Strategy_and_Language_State.md](../Umma_Data_Strategy_and_Language_State.md)
- [Umma_Planning_Notes.md](../Umma_Planning_Notes.md)

---

## 7. 교정 흐름 핵심 원칙

### 7.1 교정은 후보 중심으로 본다

- `recentFullContext` 전체 turn list를 그대로 AI에 보내지 않는다.
- 교정 후보는 기본적으로 `role = user` turn에서 추출한다.
- assistant turn은 사용자의 의도 파악에 필요한 짧은 문맥으로만 붙인다.
- MVP에서는 누적 오류 패턴보다 이번 대화의 user turn과 현재 LangState를 우선한다.

### 7.2 교정은 선택 언어 기준으로 동작한다

- `selectedLearningLanguage`는 교정 화면의 기준 언어다.
- 교정 화면은 route 인자에 의존하지 않고, Global Learning State의 `UserLangPref.selectedLang`를 기준으로 현재 선택 언어를 확인한다.
- Session Memory의 `language`를 화면에서 임의로 덮어쓰지 않는다.
- 교정 후보 추출, AI 요청, Flashcard 저장은 같은 언어 컨텍스트를 사용한다.

### 7.3 교정 후 정리는 별도 경계에 위임한다

- Correction flow는 교정 완료 이벤트를 만들고, Session Memory 압축은 별도 UseCase에 위임한다.
- 압축 완료 후 `recentFullContext`는 비워지고, 압축 요약은 다음 AI Chat 맥락에 사용된다.
- `correctionAvailable`은 교정 완료 상태에 맞게 갱신한다.

### 7.4 LangState와 교정 결과는 분리한다

- 교정 결과는 LangState 업데이트의 입력이 될 수 있다.
- 단, 교정 화면이 LangState 점수를 직접 계산하지 않는다.
- LangState 계산과 중복 반영 방지는 `LS-006` 정책을 따른다.
- 현재 `learningstate` 모델의 `CorrectionResult`는 Language State 업데이트 입력으로 넘기기 위한 최소 표현이다.
- 교정 화면 표시와 Flashcard 저장 선택에 필요한 결과 모델은 `CorrectionSuggestion` 또는 동등한 별도 domain 모델로 구분한다.

---

## 8. 데이터 흐름

```text
Dashboard
→ Correction 화면 진입
→ Global Learning State에서 selectedLearningLanguage 확인
→ DashSummary / SessionSummary / LangState snapshot 로드
→ Session Memory recentFullContext 조회
→ user turn 중심 CorrectionCandidate 추출
→ Correction payload 구성
→ AI 교정 제안 생성
→ 사용자가 Flashcard 저장 항목 선택
→ Flashcard local first 저장
→ Session Memory 압축 요청
→ DashSummary / SessionSummary / FlashcardSummary / LangState 갱신 요청
```

---

## 9. 저장 및 동기화 정책

| 데이터 | 저장/조회 기준 | 정책 |
| --- | --- | --- |
| `recentFullContext` | Session Memory | Room local append + Firestore batch sync |
| `CorrectionCandidate` | 화면 세션 내 임시 모델 | 영구 저장하지 않고 교정 요청/표시용으로 사용 |
| `CorrectionSuggestion` | 화면 세션 내 결과 모델 | 교정 표시, 사용자 선택, Flashcard 저장 요청에 사용 |
| `CorrectionResult` | Language State 업데이트 입력 모델 | 학습 상태 갱신에 필요한 최소 교정 결과만 전달 |
| `Flashcard` | Flashcard Repository | Correction infra에서 저장 계약과 local first 저장 흐름을 준비하고, SRS flow는 복습 정책을 구체화 |
| `DashSummary` | LearningStateRepo | 교정 가능 여부와 최근 저장 카드 수 요약 |
| `SessionSummary` | LearningStateRepo | 교정 진입 판단용 요약 |
| `LangState` | LearningStateRepo | 교정 결과 기반 batch update 입력 |

---

## 10. 목업 / 실제 데이터 전환

공통 mock/real 전환 규칙은 `USER_FLOW_MOCK_REAL_DATA_GUIDE.md`를 따른다.
이 문서에서는 교정 기능에 필요한 fixture 시나리오만 정의한다.

교정 fixture는 다음 시나리오를 우선 준비한다.

- 빈 `recentFullContext`
- user turn이 1개뿐이라 교정 후보가 부족한 상태
- 교정 후보가 여러 개인 대화
- LangState가 낮아서 쉬운 설명이 필요한 상태
- AI 응답 실패 또는 파싱 실패 상태
- Flashcard 저장 직전 상태
- Flashcard 저장 성공 후 요약 갱신 대기 상태

목업과 실제 API는 같은 domain 계약을 사용한다.
ViewModel과 Composable은 fake인지 real인지 알지 못해야 한다.

---

## 11. 현재 코드 기준 메모

- 현재 코드는 `presentation/feedback/FeedbackListScreen.kt`, `Route.FeedbackGraph`, `Route.FeedbackList`, `onNavigateToFeedbackList`처럼 `Feedback` 명칭으로 작성되어 있다.
- 이 문서가 가리키는 교정 작업 범위에는 위 기존 `Feedback` 기반 화면 파일명, 폴더, 라우트, 관련 클래스명을 `Correction` 기준으로 정리하는 작업도 포함한다.
- 즉, `SYS-CORRECTION-INFRA` 작업이 시작되는 시점부터는 인프라 담당자와 user-flow-correction 담당자가 기존 `Feedback` 명칭을 교정 기능 기준으로 정리하는 작업까지 함께 본다.
- 이미 완료된 다른 시스템 문서나 현재 진행 중인 온보딩/대시보드 문서는 혼란을 줄이기 위해 별도 수정 대상으로 삼지 않는다.
- 현재 코드에는 `CorrectionRepository`, `CorrectionViewModel`, `CorrectionScreen`이 아직 없고, 해당 역할은 추후 `Correction` 명칭으로 새로 정리해야 한다.
- COR 하위 issue 문서는 `COR-001`부터 `COR-005`까지 별도 파일로 분리되어 있으며, 각 문서의 AC를 기준으로 작업한다.
- `LearningStateRepo`와 `LearningStateRepoImpl`은 이미 존재하며, `DashSummary`, `SessionSummary`, `FlashcardSummary`, `LangState` 관찰 경계로 사용할 수 있다.
- `ChatRepositoryImpl`은 AI 대화 연결을 담당하며, 교정 저장 책임을 직접 맡지 않는다.
- Session Memory 원문 turn 모델과 repository는 `RT-003`에서 확정되는 구조를 따른다.
- Flashcard 저장은 교정 화면의 핵심 액션이므로, `SYS-CORRECTION-INFRA` 범위에서 저장 요청 모델, Repository 계약, local first 저장 흐름을 준비한다.
- SRS/Flashcard system flow는 저장된 Flashcard의 복습 스케줄, 난이도 반영, 복습 결과 갱신 정책을 구체화한다.

| 현재 이름 | 교정 작업에서 정리할 목표 이름 |
| --- | --- |
| `FeedbackListScreen` | `CorrectionScreen` |
| `Route.FeedbackList` / `Route.FeedbackGraph` | `Route.Correction` / `Route.CorrectionGraph` 또는 팀 구조에 맞는 교정 라우트명 |
| `onNavigateToFeedbackList` | `onNavigateToCorrection` |
| `presentation/feedback` | `presentation/correction` |
| `Feedback` 탭/문구 | `Correction` 기준 문구 |

---

## 12. 한 줄 요약

> Correction infra는 AI Chat이 저장한 Session Memory를 읽어 교정 후보를 만들고, 선택된 교정 결과를 Flashcard와 학습 상태 갱신 흐름으로 안전하게 넘기는 시스템 경계다.
