# User Flow - SRS

## 1. 목표

- 사용자는 Dashboard에서 복습해야 할 Flashcard 상태를 확인하고 SrsStudy 학습 화면으로 이동할 수 있다.
- 사용자는 Flashcard의 앞면과 뒷면을 확인하며 반복학습할 수 있다.
- 사용자는 뒷면의 발음 재생을 들을 수 있고, MVP에서는 Android `TextToSpeech`를 사용한다.
- 사용자는 SM-2 기반 4단계 평가 버튼으로 현재 기억 정도를 평가하고, 시스템은 다음 복습 시점을 갱신한다.
- 학습 결과는 local first로 반영되고, Dashboard의 복습 요약은 최신 상태로 갱신된다.

---

## 2. 시작 조건

- 사용자가 로그인된 상태
- Initial Setup 완료 상태
- `selectedLearningLanguage` 존재
- Global Learning State preload 완료 상태
- 현재 선택 언어의 `FlashcardSummary` 조회 가능 상태
- `SYS-SRS-INFRA`의 Flashcard 조회/저장 계약 참조 가능 상태
- Dashboard의 Flashcard 학습 카드가 표시 가능한 상태

---

## 3. 성공 조건

- 사용자가 Dashboard 카드 또는 직접 경로로 SrsStudy 화면에 진입할 수 있다.
- 현재 선택 언어의 due Flashcard만 학습 대상으로 노출된다.
- Flashcard 앞면에는 모국어 문장이, 뒷면에는 교정된 외국어 문장과 짧은 설명이 표시된다.
- 사용자는 카드 뒤집기로 앞/뒤를 전환할 수 있다.
- 사용자는 뒷면의 발음 재생을 들을 수 있다.
- 사용자는 SM-2 기반 4단계 평가 버튼으로 복습 결과를 기록할 수 있다.
- 평가는 local first로 저장되고, 다음 복습 시점이 갱신된다.
- 카드 저장 후 `FlashcardSummary`와 `DashSummary`의 due 수치가 최신 상태로 반영된다.
- 복습 카드가 없으면 Empty 상태가 표시된다.

---

## 4. 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 상세 이슈 |
| --- | --- | --- | --- | --- | --- | --- |
| 반복학습 진입 | Dashboard 카드 또는 직접 경로로 진입 | selectedLearningLanguage와 학습 컨텍스트 확인 | SrsStudy 화면 진입 | 라우트/컨텍스트 없음 | Loading / Ready / Error | SRS-001 |
| 복습 덱 로드 | 화면 진입 후 대기 | 현재 언어의 due Flashcard 조회 | 복습 카드 목록 준비 | due deck 없음 / 로드 실패 | Loading / Empty / Error | SRS-002 |
| 카드 앞/뒤 표시 | 카드 확인 | 앞면/뒷면 문장과 설명 렌더링 | 학습 가능한 카드 출력 | 렌더링 실패 | Content / Error | SRS-003 |
| 발음 재생 | speaker 버튼 탭 | 뒷면의 외국어 문장을 발음 재생 | 발음 재생 완료 | TTS 실패 | Speaking / Error | SRS-004 |
| 복습 평가 | 4단계 버튼 선택 | 기억 정도를 SM-2 기반 스케줄 정책에 반영하고 현재 카드 결과를 저장 | 다음 복습 시점 갱신 | 입력 실패 / 저장 실패 | Grading / Saving / Retry | SRS-005 |
| 완료 및 복귀 | 카드 저장 완료 또는 뒤로가기 | 다음 카드 이동, 덱 완료, Summary 최신 상태 관찰 | Dashboard 복귀 또는 완료 상태 | 동기화 지연 / 복귀 실패 | Completing / Done / PendingSync | SRS-006 |

---

## 5. GitHub Issue (실행 기준 / SSOT)

### User Flow Issues

- [SRS-001 반복학습 진입 및 언어 컨텍스트](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-001_Entry_Context.md)
- [SRS-002 복습 카드 덱 로드](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-002_Deck_Load.md)
- [SRS-003 카드 앞/뒤 표시 및 뒤집기](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-003_Card_Flip.md)
- [SRS-004 발음 재생](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-004_Pronunciation_Playback.md)
- [SRS-005 복습 평가 및 스케줄 반영](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-005_Grading_and_Schedule.md)
- [SRS-006 완료, 복귀, 동기화](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_SRS/SRS-006_Completion_and_Return.md)

`SRS-001 ~ SRS-006`는 팀원이 작은 PR 단위로 완료할 수 있도록 쪼갠다.  
각 이슈는 화면, 카드 표시, 상호작용, 스케줄, 완료 정리 중 하나의 책임을 중심으로 잡는다.

---

## 6. 이슈 분할 기준

- 하나의 이슈는 하나의 사용자 상호작용 또는 하나의 데이터 정책만 중심으로 잡는다.
- 화면 진입, 카드 로드, 카드 표시, 음성 재생, 평가, 완료 후 정리는 분리한다.
- 뒤 이슈의 스케줄 계산을 앞 이슈에서 미리 완성하지 않는다.
- `SRS-005`는 현재 카드의 평가 저장까지 책임지고, `SRS-006`은 저장 이후 다음 카드/완료/복귀 상태를 책임진다.
- mock 데이터로 확인 가능한 이슈는 실제 Firestore 동기화를 기다리지 않고 먼저 완료할 수 있다.

---

## 7. 데모 시나리오

1. Dashboard에서 Flashcard 학습 카드를 클릭한다.
2. SrsStudy 화면에 들어와 현재 선택 언어가 반영되는지 확인한다.
3. due Flashcard가 로드되는지 확인한다.
4. 카드 앞면과 뒷면을 번갈아 본다.
5. speaker 버튼으로 발음을 들어본다.
6. 4단계 평가 버튼 중 하나를 선택한다.
7. 마지막 카드까지 학습한 뒤 완료 상태와 Dashboard 요약 갱신을 확인한다.

---

## 8. 핵심 정책

### 8.1 현재 언어 정책

```text
GlobalLangState
→ UserLangPref.selectedLang
→ currentFlashcardSummary()
→ current Flashcard deck
```

현재 선택 언어의 deck만 복습 대상으로 사용한다.  
`dueFlashcards`는 표시용 숫자이며, deck의 source of truth는 Flashcard 원본이다.
세부 모델/조회/스케줄 계약은 `SYS-SRS-INFRA`의 `SRI-001 ~ SRI-003`을 따른다.

### 8.2 카드 표시 정책

```text
frontText
→ 모국어 문장

backText
→ 교정된 외국어 문장

explanation
→ 짧은 교정 설명

hint
→ 회상 보조 문구
```

카드 뒷면에는 발음 재생 버튼이 함께 노출될 수 있다.  
뒷면의 긴 문장과 설명은 줄바꿈 가능한 레이아웃으로 처리한다.

### 8.3 발음 재생 정책

MVP에서는 Android `TextToSpeech`를 사용한다.  
추후 더 자연스러운 음성이 필요해지면 클라우드 TTS로 교체할 수 있도록 Flashcard 데이터 계약만 유지한다.

### 8.4 복습 평가 정책

4단계 평가 버튼은 `Again / Hard / Good / Easy`로 고정한다.  
평가 결과에 따라 `interval`, `easeFactor`, `nextReviewAt`이 갱신된다.
세부 계산은 `SRI-003`의 SM-2 기반 `ReviewSchedulePolicy`를 따른다.

### 8.5 저장 정책

복습 결과는 local first로 반영한다.  
Firestore 동기화 실패는 로컬 완료 실패로 보지 않고 pending sync로 다룬다.

---

## 9. 디자인 (필요 시)

- Flashcard Study Screen
- Flashcard Front View
- Flashcard Back View
- Pronunciation Button
- 4-Step Review Buttons
- Empty / Error / Completing State
