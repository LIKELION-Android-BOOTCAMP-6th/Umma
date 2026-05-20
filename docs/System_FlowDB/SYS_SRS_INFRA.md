# SYS-SRS-INFRA

## 1. 목표 및 범위

SRS 반복학습 User Flow가 구현되기 전에, Flashcard 반복학습에 필요한 공통 계약과 최소 인프라 경계를 선행 정리한다.

SRS는 `SYS-LEARNING-STATE-INFRA`에서 이미 정의된 `GlobalLangState`, `FlashcardSummary`, `DashSummary`, `UserLangPref`, `LangState`를 재사용한다.
따라서 이 문서는 Flashcard 원본 구조, repository 계약, 복습 스케줄 정책, mock/real 교체 기준만 확정한다.
Correction 흐름에서 생성된 Flashcard의 최초 저장은 `SYS-CORRECTION-INFRA`의 저장 계약을 따른다.
SRS는 이미 저장된 Flashcard 원본을 조회하고, 복습 평가 이후 schedule을 갱신하는 책임을 가진다.
현재 `SYS-CORRECTION-INFRA`는 Room DAO가 준비되기 전까지 in-memory local data source로 Flashcard 최초 저장 계약을 고정한다.
`SYS-SRS-INFRA`의 Flashcard 저장소 작업에서는 이 임시 local data source가 보장한 필드/중복 방지/rollback 계약을 Room Entity / DAO로 이어받아야 한다.

- Flashcard 복습 대상 조회 기준
- SM-2 기반 4단계 평가 버튼과 SRS 스케줄 갱신 정책
- FlashcardRepository / UseCase 경계
- pronunciation asset 저장 계약
- `dueFlashcards` observe 원칙

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. Flashcard 모델 계약 | Flashcard 원본 모델, 카드 앞/뒤, 설명, 힌트, 발음, 스케줄 필드 정의, 기존 `study` 명칭의 `SrsStudy` 재정의 | User Flow 작업자가 동일한 카드 구조와 화면 명칭을 기준으로 구현 가능 | 성공: 카드 표시/저장 모델 및 화면 명칭 기준 확정 / 실패: 카드 필드 또는 화면 명칭 의미 불명확 | SRI-001 |
| 2. Repository 조회/저장 계약 | FlashcardRepository, due deck 조회, review 결과 저장 요청, fake/real 교체 기준 정의 | 화면과 data 구현이 같은 repository 계약을 바라봄 | 성공: mock/real 전환 가능 / 실패: 조회와 저장 책임 경계 불명확 | SRI-002 |
| 3. Review 스케줄 정책 | SM-2 기반 4단계 평가, ReviewSchedulePolicy, local first 완료 파이프라인, summary 갱신 기준 정의 | 평가 결과가 다음 복습 시점과 요약값에 일관되게 반영됨 | 성공: 스케줄/요약 갱신 가능 / 실패: nextReviewAt, pending sync 기준 불명확 | SRI-003 |

`SRI-001 ~ SRI-003`은 System Flow의 선행 작업이다.  
User Flow의 실제 학습 화면 구현은 `FLOW-SRS`에서 진행한다.

---

## 3. GitHub Issue (실행 기준 / SSOT)

### System Flow 선행 이슈

- [SRI-001 Flashcard 반복학습 모델 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-001_Flashcard_Model.md)
- [SRI-002 Flashcard Repository 조회/저장 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-002_Repository_Contract.md)
- [SRI-003 Review 스케줄 정책 및 완료 파이프라인](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-003_Schedule_Policy.md)

### User Flow 구현 이슈

- [SRS-001 반복학습 진입 및 언어 컨텍스트](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-001_Entry_Context.md)
- [SRS-002 복습 카드 덱 로드](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-002_Deck_Load.md)
- [SRS-003 카드 앞/뒤 표시 및 뒤집기](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-003_Card_Flip.md)
- [SRS-004 발음 재생](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-004_Pronunciation_Playback.md)
- [SRS-005 복습 평가 및 스케줄 반영](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-005_Grading_and_Schedule.md)
- [SRS-006 완료, 복귀, 동기화](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-006_Completion_and_Return.md)

---

## 4. 책임 경계

### System Flow에서 선행 정리할 것

- `Flashcard` 원본 모델의 front/back/schedule/pronunciation 계약
- 기존 `presentation/study` 계열 화면/폴더/파일의 `SrsStudy` 명칭 재정의
- `FlashcardRepository` interface와 review query / update 경계
- `ReviewDecision`과 `ReviewSchedulePolicy`의 역할
- fake/real 구현체를 Hilt binding으로 교체할 수 있는 DI 기준
- `FlashcardSummary.dueFlashcards`가 어디서 계산되는지에 대한 observe 기준

`SYS-SRS-INFRA`의 저장 계약은 Flashcard 최초 생성 저장이 아니라, 복습 평가 이후 기존 Flashcard의 schedule/review 상태를 갱신하는 저장 계약이다.
Correction에서 선택한 교정 결과를 새 Flashcard로 만드는 local first 저장은 `SYS-CORRECTION-INFRA`에서 다룬다.
다만 SRS의 Room Entity / DAO는 Correction에서 최초 저장한 Flashcard 원본을 그대로 조회할 수 있어야 하므로, Correction의 임시 in-memory local 저장 구조와 필드 의미를 실제 영속 저장 구조로 승계한다.

### User Flow에서 구현할 것

- Flashcard 학습 화면 상태와 UI
- 카드 앞/뒤 표시와 flip interaction
- pronunciation playback interaction
- SM-2 기반 4단계 복습 평가 버튼
- 현재 카드 완료 후 다음 카드로 이동
- Deck 완료 후 Dashboard 복귀

---

## 5. 클린 아키텍처 경계

| 계층 | 책임 |
| --- | --- |
| `presentation` | `SrsStudyScreen`, `SrsStudyViewModel`, 카드 표시, flip, pronunciation 버튼, 평가 버튼, 로딩/에러/완료 상태 |
| `domain` | 복습 대상 선택 규칙, SRS 스케줄 정책, review decision 모델, Repository interface, UseCase |
| `data` | Flashcard local first 조회/저장, Firestore sync, DTO/Entity 변환, due 카드 조회 |
| `di` | fake/real 구현체 바인딩, 테스트용 repository 교체 |

Composable은 Flashcard 원본 목록을 직접 계산하지 않는다.  
ViewModel은 UseCase를 호출하고, 복습 규칙은 domain 계층에서 처리한다.  
Repository는 저장과 외부 통신을 담당하며, 화면 정책이나 스케줄 계산을 직접 결정하지 않는다.  
`FlashcardRepository`는 복습 대상 조회와 평가 결과 저장 요청을 함께 담당한다.
Room DAO가 준비되면 Correction의 in-memory local data source는 같은 Flashcard 원본 계약을 사용하는 Room 기반 구현으로 교체한다.

---

## 6. 핵심 데이터 흐름

```text
Dashboard Flashcard 카드
→ selectedLearningLanguage 확인
→ SrsStudy 화면 진입
→ FlashcardRepository에서 저장된 Flashcard due deck 조회
→ 카드 앞면 / 뒷면 표시
→ 사용자가 평가 버튼 선택
→ ReviewSchedulePolicy로 nextReviewAt 계산
→ Flashcard schedule/review 상태 local first 갱신
→ FlashcardSummary / DashSummary 갱신
→ Firestore background sync 예약
```

사용자에게는 Dashboard의 `dueFlashcards` 숫자를 보여주지만,
실제 복습 카드 순서는 Flashcard 원본의 `nextReviewAt` 기준으로 계산한다.

---

## 7. 상태 기준

- 복습 진입 가능 여부의 기준은 `selectedLearningLanguage`와 해당 언어의 Flashcard deck 존재 여부다.
- `DashSummary.dueFlashcards`는 Dashboard 표시용 요약값이다.
- 복습 덱의 source of truth는 Flashcard 원본 목록이다.
- `nextReviewAt <= now`인 카드가 복습 대상이다.
- `FlashcardSummary.dueFlashcards`와 `DashSummary.dueFlashcards`는 같은 완료 흐름 안에서 함께 갱신한다.
- Flashcard local 저장과 스케줄 업데이트는 하나의 로컬 완료 파이프라인으로 묶는다.
- 로컬 완료 파이프라인 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태로 남긴다.
- Firestore background sync 실패는 로컬 완료 실패로 보지 않고 pending sync로 관리한다.
- Flashcard deck은 언어별로 분리되며, 현재 선택 언어 외의 카드는 섞지 않는다.

---

## 8. 모델 기준

| 모델 | 역할 |
| --- | --- |
| `Flashcard` | 반복학습 대상 원본 카드 |
| `ReviewDecision` | SM-2 기반 4단계 평가 결과와 스케줄 입력 |
| `ReviewScheduleResult` | interval, easeFactor, nextReviewAt 계산 결과 |
| `FlashcardSummary` | Dashboard와 학습 진입용 복습 요약 |
| `ReviewSessionState` | 현재 학습 세션의 카드 위치와 진행 상태 |

`Flashcard`는 최소한 front/back/schedule/pronunciation 계약을 유지한다.  
`ReviewDecision`은 UI 버튼 의미를 domain 정책으로 연결하는 얇은 계약이다.

---

## 9. mock/real 정책

SRS는 카드 조회와 복습 결과 저장이 모두 포함되므로 mock/real 교체 가능 구조가 필요하다.

- UI 작업자는 fake repository로 카드 표시와 복습 평가 흐름을 먼저 구현할 수 있다.
- 실제 API 연결은 같은 domain 계약을 사용해야 한다.
- ViewModel과 Composable은 fake인지 real인지 알지 못해야 한다.
- 교체 방식은 [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)를 따른다.

---

## 10. 연결 문서

- [FLOW_SRS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS.md)
- [SYS_LEARNING_STATE_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [SYS_LEARNING_STATE_INFRA_OVERVIEW.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/SYS_LEARNING_STATE_INFRA_OVERVIEW.md)
- [Umma_Service_Structure.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Umma_Service_Structure.md)
- [Umma_Data_Strategy_and_Language_State.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Umma_Data_Strategy_and_Language_State.md)
- [Umma_Planning_Notes.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Umma_Planning_Notes.md)

---

## 11. 한 줄 요약

> `SYS-SRS-INFRA`는 Flashcard 반복학습 User Flow가 필요한 공통 계약을 `SRI-001 ~ SRI-003`으로 선행 정리하고, 실제 복습 화면 기능은 `FLOW-SRS`의 `SRS-001 ~ SRS-006`에서 구현한다.
