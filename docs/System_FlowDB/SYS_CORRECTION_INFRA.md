# SYS-CORRECTION-INFRA

## 1. 목표 및 범위

Correction 기능이 User Flow에서 구현되기 전에 필요한 공통 계약과 최소 인프라 경계를 선행 정리한다.

Correction은 `SYS-LEARNING-STATE-INFRA`에서 이미 정의된 `GlobalLangState`, `SessionSummary`, `DashSummary`, `LangState`, `CorrectionResult`를 재사용한다.
따라서 별도 대형 System Flow로 쪼개기보다, User Flow 작업자가 바로 구현에 들어갈 수 있도록 다음 공통 경계를 먼저 확정하는 것을 목표로 한다.

- 기존 `feedback` 명칭을 `correction` 기준으로 정리할 기준
- Correction 화면 진입 시 사용할 상태 source of truth
- 내부 후보 추출과 교정 결과 모델의 책임 경계
- Flashcard 저장과 완료 정리의 통합 완료 파이프라인 계약
- mock/real 교체 가능 구조
- `SessionSummary`와 `DashSummary` 동기화 원칙

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. Correction 선행 계약 정리 | 화면/라우트 명칭, domain 모델, repository interface, usecase 경계, DI 방향 정의 | User Flow 작업자가 공통 기준에 맞춰 구현 가능 | 성공: User Flow 구현 가능 상태 확보 / 실패: 명칭, 모델, 저장 경계 불명확 | SCI-001 |

`SCI-001`은 System Flow의 선행 작업이다.
User Flow의 실제 기능 구현은 `FLOW-CORRECTION`에서 진행한다.

---

## 3. GitHub Issue (실행 기준 / SSOT)

### System Flow 선행 이슈

- [SCI-001 Correction 선행 계약 및 최소 인프라 정리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_CORRECTION_INFRA/SCI-001_Correction_Contract.md)

### User Flow 구현 이슈

- [COR-001 Correction 초기 상태 로드](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md)
- [COR-002 교정 결과 생성](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md)
- [COR-003 교정 결과 카드 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-003_Result_Cards.md)
- [COR-004 저장 카드 선택 상태](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-004_Card_Selection.md)
- [COR-005 Flashcard 저장 요청 준비](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-005_Save_Request.md)
- [COR-006 교정 완료 결과 연결](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md)
- [COR-007 Dashboard 복귀 및 후처리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-007_Return_and_Sync.md)

---

## 4. 책임 경계

### System Flow에서 선행 정리할 것

- `CorrectionCandidate`, `CorrectionSuggestion` 등 필요한 domain 계약
- `CorrectionRepository` 안에서 교정 결과 생성과 선택 결과 저장 요청을 함께 다루는 통합 repository 계약
- 실제 AI API 호출을 붙일 수 있는 data 계층 adapter / mapper 경계
- 교정 결과에서 파생된 Flashcard를 local first로 저장하고 Firestore sync 대상으로 넘기는 저장 경계
- `CompleteCorrectionUseCase` 완료 usecase의 책임
- fake/real 구현체를 Hilt binding으로 교체할 수 있는 DI 기준
- 기존 `feedback` 명칭을 `correction`으로 정리하는 기준

### User Flow에서 구현할 것

- Correction 화면 상태와 UI
- Loading / Empty / Error / Retry / Saved / Completing 상태
- `CorrectionRepository`를 호출해 실제 AI 교정 결과를 화면 상태로 연결
- 교정 결과 카드 렌더링
- 저장할 카드 선택
- Flashcard 저장 호출과 완료/실패 상태 표시
- 완료 후 Dashboard 복귀

실제 AI API 호출과 Firestore 문서 저장 자체는 data 계층 작업이므로 System Flow에서 연결 가능한 구조를 준비한다.
다만 사용자가 화면에서 진입해 AI 결과를 보고 저장하는 end-to-end 흐름 검증은 `FLOW-CORRECTION`의 User Flow 이슈에서 수행한다.

---

## 5. 클린 아키텍처 경계

| 계층 | 책임 |
| --- | --- |
| `presentation` | `CorrectionScreen`, `CorrectionViewModel`, UI 상태, 사용자 선택, 로딩/에러/저장 이벤트 처리 |
| `domain` | 후보 추출 규칙, 교정 결과 모델, UseCase, Repository interface |
| `data` | AI 교정 요청 구현, AI 응답 파싱/mapper, Flashcard local first 저장, 보상 rollback 저장소 계약, DTO/Entity 변환 |
| `di` | fake/real 구현체 바인딩, 테스트용 repository 교체 |

Composable은 `recentFullContext`를 직접 파싱하지 않는다.
ViewModel은 UseCase를 호출하고, 비즈니스 규칙은 domain 계층에서 처리한다.
Repository는 저장과 외부 통신을 담당하며, 화면 정책이나 점수 계산을 직접 결정하지 않는다.
`CorrectionRepository`는 교정 결과 생성과 선택된 교정 결과의 Flashcard 저장 요청만 담당한다.
단, 후보 추출 규칙과 완료 순서 결정은 UseCase에 둔다.
실제 AI 응답 원문과 JSON 파싱은 data 계층 내부에서 처리하고, presentation/domain은 `CorrectionSuggestion` 계약만 바라본다.

---

## 6. 핵심 데이터 흐름

```text
Correction 화면 진입
→ GlobalLangState에서 selectedLearningLanguage 확인
→ SessionSummary.correctionAvailable 확인
→ Session Memory의 recentFullContext 조회
→ user turn 중심 후보를 내부 추출
→ LangState snapshot과 후보를 입력으로 실제 AI 교정 API 호출
→ AI 응답 파싱 및 필수 필드 검증
→ CorrectionSuggestion 생성
→ 사용자가 저장할 교정 결과 카드 선택
→ CompleteCorrectionUseCase 호출
→ Flashcard local first 저장
→ LangState 업데이트 입력 생성 및 적용
→ SessionSummary.correctionAvailable false 갱신
→ DashSummary.correctionAvailable 동시 반영
→ Firestore background sync 예약
```

Session Memory 원문 buffer 압축과 정리는 `SYS-REALTIME-INFRA`의 `RT-003` 실제 저장소 계약이 머지된 뒤 후속 연결 작업에서 붙인다.
Correction-infra는 `SessionMemoryRepository` 구현체를 직접 제공하지 않고, 후속 작업에서 RT-003 계약을 소비할 연결 지점만 남긴다.
RT-003이 교정용 read model을 제공하더라도, 어떤 user turn을 `CorrectionCandidate`로 확정할지는 Correction domain UseCase가 최종 결정한다.
`recentFullContext`는 오래된 turn부터 최신 turn 순서로 전달되는 것을 전제로 한다.

사용자에게는 `CorrectionCandidate` 목록을 보여주지 않는다.
후보 추출은 교정 결과 카드를 만들기 위한 내부 준비 단계다.

---

## 7. 상태 기준

- 교정 진입 가능 여부의 기준은 `SessionSummary.correctionAvailable`이다.
- `DashSummary.correctionAvailable`은 Dashboard 카드 표시용 파생값이다.
- 교정 완료 시 두 값은 같은 완료 흐름 안에서 함께 갱신한다.
- 둘 중 하나만 갱신하는 구현은 허용하지 않는다.
- Flashcard 저장, LangState 업데이트, Summary 갱신은 하나의 로컬 완료 파이프라인으로 묶는다.
- Session Memory 압축은 RT-003 계약이 확정된 뒤 같은 완료 흐름의 후속 연결 지점으로 붙인다.
- 로컬 완료 파이프라인 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태로 둔다.
- 저장소가 하나의 transaction으로 묶이지 않는 경우 보상 rollback 또는 commit marker 방식으로 부분 완료 상태를 남기지 않는다.
- Firestore background sync 실패는 로컬 완료 실패로 보지 않고 pending sync로 관리한다.
- `recentFullContext`는 화면에 원문 그대로 노출하지 않는다.
- MVP 후보 추출 범위는 최근 100턴까지로 제한한다.

---

## 8. Flashcard 저장 책임

Correction에서 사용자가 선택한 `CorrectionSuggestion`을 새 Flashcard로 최초 생성하고 저장하는 책임은 `SYS-CORRECTION-INFRA`의 저장 계약에 둔다.
이 저장은 local first로 수행하며, Firestore 동기화는 로컬 완료 이후 background sync / pending sync로 관리한다.

SRS는 Correction에서 이미 저장된 Flashcard 원본을 조회하고 복습 결과에 따라 schedule/review 상태를 갱신한다.
따라서 새 Flashcard 생성 저장을 `SYS-SRS-INFRA`나 SRS User Flow로 넘기지 않는다.

```text
CorrectionSuggestion 선택
→ CorrectionSaveRequest 생성
→ CorrectionRepository.saveFlashcards(...)
→ Flashcard local first 최초 저장
→ Firestore background sync 예약
→ 이후 SRS에서 due deck 조회 대상으로 사용
```

Flashcard 문서/Entity는 SRS에서 읽을 수 있는 원본 카드 계약과 호환되어야 한다.
다만 due deck 조회, SM-2 schedule 계산, review 결과 갱신은 `SYS-SRS-INFRA`의 `FlashcardRepository` 책임이다.
Room DAO가 아직 준비되지 않은 경우에는 in-memory local data source로 local-first 순서와 중복 저장 방지 계약을 먼저 고정한다.
Firestore sync는 `users/{uid}/flashcards/{flashcardId}` 문서 저장으로 연결하고, 실패 시 pending sync로 남긴다.
MVP 발음 재생은 뒷면의 `backText`를 그대로 읽는 방식이므로 별도 `pronunciationText` 저장 필드는 두지 않는다.

---

## 9. 모델 기준

| 모델 | 역할 |
| --- | --- |
| `CorrectionCandidate` | 내부 후보 추출 결과. 화면에 직접 목록으로 노출하지 않는다. |
| `CorrectionSuggestion` | 교정 화면 카드 표시 및 Flashcard 저장 선택에 사용하는 결과 모델. |
| `CorrectionResult` | LangState 업데이트 입력용 최소 교정 결과 모델. |
| `SessionSummary` | Correction 진입 가능 여부의 기준. |
| `DashSummary` | Dashboard 표시용 요약. |

`CorrectionSuggestion`은 아직 구현되지 않은 경우 `SCI-001`에서 domain 모델로 정의한다.
기존 `CorrectionResult`와 화면 표시 모델을 섞지 않는다.

### AI 응답 계약

MVP User Flow는 실제 AI 교정 API를 호출해 교정 결과를 생성해야 한다.
AI 응답은 data 계층에서 정해진 응답 계약으로 파싱하고, 필수 필드를 검증한 뒤 `CorrectionSuggestion` 목록으로 변환한다.
AI 요청 실패, 응답 파싱 실패, 필수 필드 누락은 화면에서 Error / Retry 상태로 연결할 수 있어야 한다.

System Flow에서는 실제 AI 연결이 들어갈 data adapter와 응답 mapper의 경계를 준비한다.
User Flow에서는 그 repository 계약을 호출해 화면 상태와 실제 최소 성공 흐름을 검증한다.

다만 교정 품질을 높이기 위한 prompt tuning, JSON schema 정교화, fallback 고도화는 후속 개선 범위로 둔다.
fake/mock 또는 `CorrectionSuggestionFixtureBuilder`는 실제 AI 없이도 화면 상태, 저장 요청, 완료 파이프라인, rollback을 테스트하기 위한 보조 수단이며 실제 AI 연동을 대체하지 않는다.

---

## 10. 현재 코드 정리 기준

현재 코드에는 `presentation/feedback/FeedbackListScreen.kt`, `Route.FeedbackList`, `Route.FeedbackGraph`, `onNavigateToFeedbackList`처럼 `Feedback` 명칭이 남아 있다.

Correction 작업이 시작되는 시점부터 다음 명칭으로 정리한다.

| 현재 이름 | 목표 이름 |
| --- | --- |
| `presentation/feedback` | `presentation/correction` |
| `FeedbackListScreen` | `CorrectionScreen` |
| `Route.FeedbackList` / `Route.FeedbackGraph` | `Route.CorrectionList` / `Route.CorrectionGraph` |
| `onNavigateToFeedbackList` | `onNavigateToCorrection` |
| `Feedback` 탭/문구 | `Correction` 기준 문구 |

이미 완료된 시스템 문서나 현재 작업 중인 온보딩/대시보드 문서는 별도 수정하지 않는다.
명칭 정리는 Correction 작업 범위 안에서만 진행한다.

---

## 11. mock/real 정책

Correction은 AI 응답과 저장 흐름이 포함되므로 mock/real 교체 가능 구조가 필요하다.

- UI 작업자는 mock repository로 카드 표시와 저장 상태를 먼저 구현할 수 있다.
- 실제 AI API 연결은 같은 domain 계약을 사용해야 하며, fake/mock 구현만으로 `COR-002` 최종 완료를 대체하지 않는다.
- ViewModel과 Composable은 fake인지 real인지 알지 못해야 한다.
- 교체 방식은 [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)를 따른다.

---

## 12. 연결 문서

- [SYS_CORRECTION_INFRA_OVERVIEW.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_CORRECTION_INFRA/SYS_CORRECTION_INFRA_OVERVIEW.md)
- [FLOW_CORRECTION.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION.md)
- [SYS_LEARNING_STATE_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [SYS_LEARNING_STATE_INFRA_OVERVIEW.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/SYS_LEARNING_STATE_INFRA_OVERVIEW.md)
- [SYS_REALTIME_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_REALTIME_INFRA.md)
- [FLOW_AI_CHAT.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_AI_CHAT.md)
- [FLOW_DASHBOARD.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-003_Correction_Pending_Card.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md)

---

## 13. 한 줄 요약

> `SYS-CORRECTION-INFRA`는 User Flow 구현 전에 필요한 Correction 공통 계약을 `SCI-001` 한 단위로 선행 정리하고, 실제 화면 기능은 `FLOW-CORRECTION`의 `COR-001 ~ COR-007`에서 구현한다.
