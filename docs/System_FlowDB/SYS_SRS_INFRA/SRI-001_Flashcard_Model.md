# [Infra] SRI-001 Flashcard 반복학습 모델 계약

## User Story

SRS User Flow 작업자는 반복학습 화면을 구현하기 전에,
Flashcard 원본 카드가 어떤 필드를 갖고 각 필드가 어떤 의미인지 명확히 알고 작업할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] `Flashcard`가 반복학습 원본 카드 계약으로 정의된다.
- [ ] Flashcard의 front/back, explanation, hint, schedule 필드와 pronunciation 재생 대상의 역할이 구분된다.
- [ ] 카드 앞면은 모국어 문장, 카드 뒷면은 교정된 외국어 문장과 짧은 설명을 기준으로 한다.
- [ ] 발음 재생 대상은 카드 뒷면의 교정된 외국어 문장임을 명시한다.
- [ ] `ReviewDecision`이 SM-2 기반 4단계 복습 평가 결과 계약으로 정의된다.
- [ ] `ReviewScheduleResult` 또는 동등한 스케줄 계산 결과 모델이 정의된다.
- [ ] Flashcard가 언어 식별자를 가져 언어별 deck 분리가 가능하도록 정의된다.
- [ ] 기존 공통 작업으로 생성된 `study` 화면/폴더/파일 명칭을 `SrsStudy` 기준으로 재정의한다.

---

## 구현 범위

### 포함 범위

- `domain/model/flashcard` 계약
- `Flashcard` 원본 모델
- `ReviewDecision` 모델
- `ReviewScheduleResult` 모델
- front/back/explanation/hint/schedule 필드 의미와 pronunciation 재생 대상
- Flashcard 언어 식별자 필드 의미
- 기존 `presentation/study` 계열 화면/폴더/파일의 `SrsStudy` 명칭 재정의 기준

### 제외 범위

- FlashcardRepository 조회/복습 결과 갱신 구현
- SRS 스케줄 계산 세부 정책
- Flashcard 화면 세부 UI 구현
- 카드 뒤집기 애니메이션
- 발음 재생 버튼 동작
- Dashboard 카드 렌더링

---

## 권장 파일/패키지 방향

```text
domain/model/flashcard
→ Flashcard
→ ReviewDecision
→ ReviewScheduleResult

presentation/study
→ presentation/srsstudy

StudyListScreen
→ SrsStudyScreen
```

---

## 선행 명칭 재정의

팀장이 공통 작업으로 미리 생성한 `study` 계열 화면/폴더/파일은 SRS 반복학습 화면의 구현 시작 전에 `SrsStudy` 기준으로 정리한다.

```text
presentation/study
→ presentation/srsstudy

StudyListScreen
→ SrsStudyScreen
```

- `study`는 의미가 넓어 Dashboard, AI Chat, Correction 이후의 학습 화면들과 혼동될 수 있다.
- 이 Flow에서 다루는 화면은 Flashcard 기반 SRS 반복학습이므로 구현 명칭은 `SrsStudy`로 고정한다.
- Flow / Issue 식별자인 `SYS-SRS-INFRA`, `FLOW-SRS`, `SRS-001` 같은 문서 ID는 변경하지 않는다.
- 기존 `study` 파일을 재사용할 수 있더라도, 작업 시작 시 파일명과 package 경로를 `SrsStudy` 기준으로 맞춘 뒤 이어서 구현한다.

---

## 핵심 계약

### 1. Flashcard 카드 구조

```text
Flashcard
→ id
→ language
→ frontText
→ backText
→ explanation
→ hint
→ schedule
```

- `frontText`: 카드 앞면에 표시할 모국어 문장
- `backText`: 카드 뒷면에 표시할 교정된 외국어 문장
- `explanation`: 뒷면에 표시할 짧은 교정 설명
- `hint`: 앞면 또는 학습 중 회상 보조에 사용할 선택 필드이며, Correction에서 생성한 카드는 생략할 수 있다.
- pronunciation 재생 대상: MVP에서는 별도 저장 필드를 두지 않고 `backText`를 Android `TextToSpeech`의 입력으로 사용한다.
- `schedule`: `interval`, `easeFactor`, `nextReviewAt` 등 복습 스케줄 상태

### 2. ReviewDecision 계약

```text
ReviewDecision
→ flashcardId
→ rating
→ reviewedAt
```

- `rating`은 `Again / Hard / Good / Easy` 중 하나로 고정한다.
- 이 rating은 SM-2 기반 스케줄 정책에 입력되는 사용자 자기평가 값이다.
- `ReviewDecision`은 UI 버튼 의미를 domain 정책으로 연결하는 얇은 계약이다.
- 실제 스케줄 계산은 `SRI-003`의 `ReviewSchedulePolicy`가 책임진다.

### 3. ReviewScheduleResult 계약

```text
ReviewScheduleResult
→ interval
→ easeFactor
→ nextReviewAt
```

- `ReviewScheduleResult`는 평가 후 Flashcard schedule을 갱신하기 위한 결과 모델이다.
- 계산 공식은 이 문서에서 결정하지 않고 `SRI-003`에서 확정한다.

---

## 상태 기준

- Flashcard는 어떤 학습 언어에 속하는지 식별할 수 있어야 한다.
- Flashcard 원본 목록은 Global Learning State가 아니라 별도 repository에서 읽는다.
- `FlashcardSummary.dueFlashcards` 계산 기준은 `SRI-002`의 조회 계약에서 다룬다.
- `nextReviewAt` 기반 due 판정은 `SRI-002`의 조회 계약에서 다룬다.
- Review session의 현재 위치는 User Flow의 UI state에서 관리한다.

---

## 예외 처리

- 필수 표시 필드가 없는 Flashcard는 due deck에서 제외하거나 Error 상태로 분리한다.
- 삭제되었거나 동기화 충돌이 있는 카드는 최신 저장값을 우선한다.
- pronunciation 정보가 없어도 카드 표시 자체는 실패로 보지 않는다.

---

## 연결 문서

- [SYS_SRS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA.md)
- [SRI-002_Repository_Contract.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-002_Repository_Contract.md)
- [SRI-003_Schedule_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_SRS_INFRA/SRI-003_Schedule_Policy.md)
- [FLOW_SRS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS.md)
- [SYS_LEARNING_STATE_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [Umma_Data_Strategy_and_Language_State.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/Umma_Data_Strategy_and_Language_State.md)
