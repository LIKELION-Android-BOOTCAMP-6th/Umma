# [Improvement] SRS-TUNE-001 SRS 주기 튜닝

## 목적

현재 SRS 반복학습은 `Again / Hard / Good / Easy` 버튼을 제공하지만, 카드 상태를 명시적으로 나누지 않고 `interval`, `easeFactor`, `nextReviewAt` 중심으로만 다음 복습 시점을 계산한다.
이 구조는 MVP 단순화에는 충분했지만, 신규 카드가 `Good` 한 번으로 바로 1일 뒤로 밀리고, 복습 실패 카드가 재학습 상태를 유지하지 못하는 한계가 있다.

이번 작업은 최신 FSRS가 아니라 레거시 Anki 스타일의 `Learning / Review / Relearning` 구조를 도입해, 신규 카드의 짧은 학습 단계와 장기 복습 단계를 분리한다.
이를 통해 사용자는 방금 저장한 교정 문장을 너무 빨리 장기 복습으로 보내지 않고, 짧은 재확인을 거친 뒤 안정적으로 반복학습할 수 있다.

---

# User Story

사용자는 새 Flashcard를 처음 학습할 때 바로 장기 복습으로 넘어가지 않고, 짧은 시간 뒤 한 번 더 확인할 수 있다.
사용자는 이미 복습 중이던 카드를 잊었을 때, 해당 카드가 완전히 새 카드처럼 초기화되지 않고 재학습 단계를 거쳐 다시 복습 흐름으로 돌아가는 경험을 얻는다.
Umma는 SRS 버튼에 표시되는 다음 학습 시간이 실제 저장되는 `nextReviewAt`과 일치하도록 유지한다.

---

# 완료 기준(AC)

- [ ] 신규 카드는 `Learning` 상태로 시작한다.
- [ ] `Learning` 카드의 `Again / Hard / Good / Easy`는 레거시 Anki 스타일 학습 단계에 따라 다음 노출 시간을 계산한다.
- [ ] `Good`을 누른 신규 카드는 바로 1일 뒤로 가지 않고 10분 뒤 한 번 더 노출된다.
- [ ] `Easy`를 누른 신규 카드는 학습 단계를 건너뛰고 `Review` 상태로 전환된다.
- [ ] `Review` 카드의 `Hard / Good / Easy`는 기존 `interval`과 `easeFactor`를 기준으로 장기 복습 간격을 계산한다.
- [ ] `Review` 카드에서 `Again`을 누르면 `Relearning` 상태로 전환된다.
- [ ] `Relearning` 카드는 짧은 재학습 단계를 통과한 뒤 `Review` 상태로 복귀한다.
- [ ] 평가 버튼에 표시되는 다음 학습 시간은 실제 저장되는 schedule 계산 결과와 일치한다.
- [ ] 기존 Flashcard 데이터는 새 schedule 필드가 없어도 안전하게 기본값으로 복원된다.
- [ ] 기존 local-first 저장, pending sync, Dashboard due count 갱신 흐름은 유지된다.

---

# 기준 문서

- [SYS-SRS-INFRA](../../../System_FlowDB/SYS_SRS_INFRA.md)
- [SRI-003 Review 스케줄 정책](../../../System_FlowDB/SYS_SRS_INFRA/SRI-003_Schedule_Policy.md)
- [FLOW-SRS](../../../Sprint2/User_FlowDB/FLOW_SRS.md)
- [SRS-005 복습 평가 및 스케줄 반영](../../../Sprint2/User_FlowDB/FLOW_SRS/SRS-005_Grading_and_Schedule.md)
- [SRS-006 완료, 복귀, 동기화](../../../Sprint2/User_FlowDB/FLOW_SRS/SRS-006_Completion_and_Return.md)

---

# 핵심 결정

- **이번 작업의 목표는 최신 Anki FSRS 구현이 아니다.**
  - FSRS의 retrievability, stability, difficulty 계산은 포함하지 않는다.
  - 레거시 Anki의 상태 구조와 easeFactor 기반 interval 증가만 적용한다.

- **레거시 Anki 스타일을 Umma preset으로 적용한다.**
  - 구조는 Anki의 `Learning / Review / Relearning`을 따른다.
  - 숫자는 교정 문장 학습에 맞게 보수적으로 조정한다.
  - 특히 첫 학습 `Easy`는 Anki 기본 `4일`보다 짧은 `2일`을 권장값으로 둔다.

- **카드 상태를 명시적으로 저장한다.**
  - 현재 `interval < 1일` 여부로 학습 중 카드를 추론하지 않는다.
  - `reviewState`, `learningStep`, `lapseCount`, `previousReviewInterval`을 schedule 필드로 추가한다.

- **UI는 계산하지 않고 domain 정책 결과만 표시한다.**
  - 버튼 라벨은 `ReviewSchedulePolicy`가 계산한 결과를 읽어 표시한다.
  - 화면은 `Again / Hard / Good / Easy` 입력만 전달한다.
  - `Again`도 더 이상 `재학습` 고정 문구로 두지 않고 실제 다음 노출 시간인 `1분` 또는 `10분`으로 표시한다.

- **기존 데이터 호환성을 유지한다.**
  - 기존 카드에 새 필드가 없으면 `Learning` 또는 `Review` 기본값을 안전하게 부여한다.
  - Firestore 복원과 Room migration에서 누락 필드를 기본값으로 채운다.
  - 새 필드는 domain/data 모델에서 기본값을 제공해 기존 테스트 fixture와 mapper 호출이 불필요하게 대량으로 깨지지 않게 한다.

- **Cloud Functions는 SRS schedule을 계산하지 않는다.**
  - SRS schedule 계산은 Android domain의 `ReviewSchedulePolicy`가 책임진다.
  - Cloud Functions는 알림/요약 보정에서 due card를 집계할 수 있으므로, 새 `reviewState` 필드를 읽어 알림 대상 여부만 구분한다.
  - Firestore query 조건은 가능한 기존 범위를 유지하고, `reviewState` 필터는 서버 코드에서 적용해 불필요한 composite index 요구를 만들지 않는다.

---

# 사이드 이펙트 방지 핵심 결정

아래 결정은 schedule 정책 변경 자체보다 기존 데이터, 알림, sync, 화면 흐름의 회귀를 막기 위한 기준이다.
구현 중 판단이 갈리면 이 섹션을 우선한다.

## 1. 기존 카드 복원은 보수적으로 처리한다

- 기존 카드에 `reviewState`가 없으면 `interval >= 1440분`인 카드만 `Review`로 복원한다.
- `interval < 1440분`인 카드는 `Learning`으로 복원한다.
- 기존 데이터만으로는 진짜 `Relearning` 상태였는지 확정하기 어렵기 때문에, 기존 카드 migration에서 `Relearning`으로 자동 복원하지 않는다.
- `Relearning`은 새 정책 적용 이후 `Review` 카드에서 `Again`을 누른 경우부터 생성한다.

## 2. 학습 화면 due와 알림 due를 분리한다

- 학습 화면에 보여줄 due card는 기존처럼 `nextReviewAt <= now` 기준을 유지한다.
- 따라서 `Learning`과 `Relearning` 카드도 시간이 지나면 학습 화면에는 다시 나타날 수 있다.
- 알림/요약의 `notifiableDueFlashcards`는 `Review` 상태 카드만 포함한다.
- 이 분리는 1분/10분 단위 학습 카드가 사용자에게 과도한 알림으로 이어지는 것을 막기 위한 방어다.

## 3. 세션 내부 learning queue는 이번 작업에서 만들지 않는다

- 이번 작업은 카드 schedule 저장 정책을 바꾸는 범위다.
- Anki처럼 같은 학습 세션 안에서 1분/10분 뒤 카드를 자동으로 다시 끼워 넣는 queue는 만들지 않는다.
- 짧은 learning step 카드는 `nextReviewAt` 기준으로 다음 진입, 새로고침, 또는 다음 세션에서 due로 잡히게 한다.
- 세션 내부 queue는 진행률, 완료 조건, 뒤로가기, pending sync에 영향을 주므로 별도 작업으로 분리한다.

## 4. Cloud Functions는 기존 query를 유지하고 코드에서 필터링한다

- Cloud Functions의 알림 보정은 기존 `language + nextReviewAt` 조회 범위를 유지한다.
- `reviewState == "Review"` 조건은 Firestore query에 추가하지 않고, 읽어온 문서를 코드에서 필터링한다.
- 이 결정은 composite index 추가와 배포 리스크를 피하기 위한 것이다.
- 카드 수가 충분히 늘어 읽기 비용이 문제가 될 때만 index 기반 query로 전환을 검토한다.

## 5. 새 schedule 필드는 기본값과 fallback을 가진다

- Domain 모델의 새 schedule 필드는 non-null 기본값을 가진다.
- Room 컬럼은 migration에서 기본값을 채운다.
- Firestore DTO와 mapper는 누락 필드를 허용하고 기본값으로 복원한다.
- 이 결정은 기존 문서, 이전 앱 버전, 테스트 fixture가 새 필드 없이 남아 있어도 앱이 깨지지 않게 하기 위한 방어다.

## 6. 이전 앱 버전 또는 이전 테스트 데이터도 계속 방어한다

- 새 필드가 없는 Firestore 문서를 읽는 fallback은 일회성 migration 코드로 취급하지 않는다.
- 개발 중 이전 앱 버전이나 오래된 테스트 기기가 새 필드 없는 문서를 다시 쓸 수 있으므로 fallback은 유지한다.
- fallback은 schedule 계산을 완벽히 복원하기 위한 것이 아니라, 앱 크래시와 잘못된 장기 복습 전환을 막기 위한 최소 방어다.

## 7. 첫 학습 `Easy`는 2일로 유지한다

- 레거시 Anki 기본값처럼 4일로 보내면 교정 문장 학습에서는 간격이 과하게 벌어질 수 있다.
- 1일은 `Good` 졸업 간격과 차이가 약하다.
- 2일은 사용자가 쉽게 기억한다고 표시했을 때 보상은 주되, 초기 교정 문장을 너무 멀리 보내지 않는 Umma preset이다.

---

# 레거시 Anki 스타일 Umma preset

## 기본값

| 항목 | 값 | 의미 |
| --- | ---: | --- |
| Learning steps | `1분`, `10분` | 새 카드가 장기 복습 전 거치는 짧은 확인 단계 |
| Graduating interval | `1일` | Learning 마지막 단계에서 `Good`을 누르면 Review로 졸업하는 첫 간격 |
| Easy interval | `2일` | Learning 카드에서 `Easy`를 누르면 바로 Review로 졸업하는 간격 |
| Relearning step | `10분` | Review 카드에서 `Again`을 누른 뒤 다시 확인하는 간격 |
| Starting ease | `2.5` | 새 카드의 기본 easeFactor |
| Minimum ease | `1.3` | easeFactor 하한 |
| Hard interval multiplier | `1.2` | Review 카드에서 Hard 선택 시 interval 증가 배율 |
| Easy bonus | `1.3` | Review 카드에서 Easy 선택 시 추가 증가 배율 |
| Interval modifier | `1.0` | 전체 interval 보정 배율 |
| Minimum interval | `1일` | Relearning 완료 후 최소 복습 간격 |

## Learning 카드

새로 저장된 카드는 아래 상태로 시작한다.

```text
reviewState = Learning
learningStep = 0
interval = 0
easeFactor = 2.5
lapseCount = 0
previousReviewInterval = null
nextReviewAt = now
```

### Learning step 0

| 버튼 | 다음 상태 | 다음 노출 | 설명 |
| --- | --- | ---: | --- |
| Again | Learning step 0 | 1분 | 첫 학습 단계로 되돌리거나 유지한다. |
| Hard | Learning step 0 | 6분 | 첫 두 step인 1분과 10분의 평균값을 사용한다. |
| Good | Learning step 1 | 10분 | 다음 학습 단계로 이동한다. |
| Easy | Review | 2일 | 학습 단계를 건너뛰고 Review로 졸업한다. |

### Learning step 1

| 버튼 | 다음 상태 | 다음 노출 | 설명 |
| --- | --- | ---: | --- |
| Again | Learning step 0 | 1분 | 첫 학습 단계로 되돌린다. |
| Hard | Learning step 1 | 10분 | 현재 학습 단계를 반복한다. |
| Good | Review | 1일 | 정상 졸업한다. |
| Easy | Review | 2일 | 빠르게 졸업한다. |

## Review 카드

`Review` 상태는 장기 복습 단계다.
이 단계부터 `interval`과 `easeFactor`를 사용해 간격이 점점 늘어난다.

| 버튼 | 다음 상태 | 다음 노출 | easeFactor |
| --- | --- | ---: | --- |
| Again | Relearning | 10분 | `-0.20`, 최소 `1.3` |
| Hard | Review | `max(1일, interval x 1.2)` | `-0.15`, 최소 `1.3` |
| Good | Review | `max(1일, interval x easeFactor)` | 유지 |
| Easy | Review | `max(2일, interval x easeFactor x 1.3)` | `+0.15` |

`Again`을 선택할 때는 기존 장기 interval을 `previousReviewInterval`에 저장한다.
이 값은 Relearning을 통과한 뒤 원래 카드의 복습 이력을 완전히 잃지 않기 위해 필요하다.

## Relearning 카드

`Relearning` 상태는 이미 복습 중이던 카드를 잊었을 때 사용하는 짧은 복구 단계다.
완전히 새 카드로 되돌리지 않고, 짧게 다시 확인한 뒤 Review로 복귀시킨다.

| 버튼 | 다음 상태 | 다음 노출 | 설명 |
| --- | --- | ---: | --- |
| Again | Relearning | 10분 | 재학습 단계를 유지한다. |
| Hard | Relearning | 10분 | 아직 불안정하므로 재학습 단계를 유지한다. |
| Good | Review | `max(1일, previousReviewInterval x 0.5)` | 기존 간격의 일부를 반영해 복귀한다. |
| Easy | Review | `max(1일, previousReviewInterval)` | 기존 간격을 최대한 보존해 복귀한다. |

`previousReviewInterval`이 없으면 `1일`을 기본값으로 사용한다.
이 fallback은 기존 데이터 복원, migration 누락, 예외 상황에서 schedule 계산이 실패하지 않게 하기 위한 방어다.

---

# 현재 방식과의 차이

## 신규 카드

| 버튼 | 현재 방식 | 변경 후 |
| --- | ---: | ---: |
| Again | 1분 | 1분 |
| Hard | 10분 | 6분 |
| Good | 1일 | 10분 |
| Easy | 4일 | 2일 |

가장 큰 차이는 `Good`이다.
현재는 새 카드가 `Good` 한 번으로 1일 뒤로 이동하지만, 변경 후에는 10분 뒤 한 번 더 확인한 뒤 1일 졸업 여부를 판단한다.

## 복습 실패 카드

현재 방식은 `Review` 카드에서 `Again`을 누르면 interval이 1분으로 내려가고, 다음 `Good`에서 새 카드처럼 1일로 이동한다.
변경 후에는 `Relearning` 상태로 들어가며, 기존 interval을 `previousReviewInterval`로 보존한 뒤 재학습 성공 시 일부 복구한다.

```text
현재:
15일 카드 -> Again -> 1분 -> Good -> 1일

변경 후:
15일 카드 -> Again -> 10분 -> Good -> 7일 이상 복귀
```

---

# 필요한 모델 변경

## Domain

```kotlin
enum class ReviewCardState {
    Learning,
    Review,
    Relearning
}

data class FlashcardSchedule(
    val interval: Int,
    val easeFactor: Double,
    val nextReviewAt: Long,
    val reviewState: ReviewCardState = ReviewCardState.Learning,
    val learningStep: Int = 0,
    val lapseCount: Int = 0,
    val previousReviewInterval: Int? = null
)
```

`ReviewScheduleResult`에도 같은 schedule 필드를 포함한다.
Repository는 domain 정책이 계산한 결과를 저장만 하고, schedule 정책을 다시 계산하지 않는다.

기본값을 두는 이유:

- 기존 코드와 테스트가 `interval`, `easeFactor`, `nextReviewAt`만으로 schedule fixture를 만드는 곳이 많다.
- 새 필드 추가가 실제 정책 변경보다 더 큰 컴파일 파급으로 번지지 않게 한다.
- mapper fallback과 테스트 fixture가 같은 기본 의미를 공유하게 한다.

## Data

Room Entity, DTO, Firestore 문서에 아래 필드를 추가한다.

```text
reviewState
learningStep
lapseCount
previousReviewInterval
```

기존 Firestore 문서나 Room row에 값이 없을 수 있으므로 mapper는 기본값을 제공한다.

권장 기본값:

```text
reviewState:
  interval >= 1440분이면 Review
  그 외에는 Learning

learningStep:
  0

lapseCount:
  0

previousReviewInterval:
  null
```

DTO와 Entity도 domain과 같은 의미의 기본값을 제공한다.
Firestore에서 필드가 누락된 문서를 읽거나, 개발 중 이전 앱 버전이 새 필드 없이 문서를 쓴 경우에도 앱이 크래시 나지 않아야 한다.

주의:

- 기존 `interval` 단위는 분이다. 새 정책도 분 단위를 유지한다.
- `Graduating interval = 1일`은 `1440분`으로 저장한다.
- Firestore에는 문자열 enum 값을 저장해 운영 콘솔에서 읽기 쉽게 한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| `SrsStudyScreen` | 버튼과 라벨을 표시하고 사용자 입력을 ViewModel에 전달한다. |
| `SrsStudyViewModel` | 현재 카드 상태를 UI에 전달하고 평가 요청을 UseCase에 넘긴다. 스케줄 계산을 직접 하지 않는다. |
| `ReviewSchedulePolicy` | 레거시 Anki 스타일 schedule 계산을 책임진다. |
| `ApplyReviewDecisionUseCase` | 계산된 schedule 저장, summary 갱신, 실패 시 보상 갱신을 조율한다. |
| `FlashcardRepository` | local-first schedule 저장과 remote pending sync를 담당한다. |
| `CorrectionFlashcardLocalDataSource` | Room 원본 조회와 schedule 필드 갱신을 담당한다. |
| `CorrectionFlashcardRemoteDataSource` | Firestore schedule 필드 동기화를 담당한다. |
| `LearningState` | due/saved count summary 반영만 담당한다. SRS schedule을 계산하지 않는다. |

---

# 주요 작업

## 1. 정책 확정

- 레거시 Anki 스타일의 상태와 숫자를 문서 기준으로 확정한다.
- 첫 학습 `Easy interval`은 Umma preset 기준 `2일`로 둔다.
- `Again` 버튼 라벨은 `재학습` 고정 문구가 아니라 실제 다음 노출 시간으로 표시한다.

## 2. Domain 모델 확장

- `ReviewCardState` enum을 추가한다.
- `FlashcardSchedule`과 `ReviewScheduleResult`에 상태 필드를 추가한다.
- 기존 `ReviewRating` 버튼 의미는 유지한다.

## 3. Schedule policy 재작성

- `Learning / Review / Relearning` 분기를 구현한다.
- 각 상태별 `Again / Hard / Good / Easy` 결과를 고정한다.
- `easeFactor` 하한과 증가/감소를 적용한다.
- `previousReviewInterval` fallback을 포함한다.
- 잘못 저장된 값은 계산 전에 안전한 값으로 보정한다.
  - `learningStep`이 범위를 벗어나면 `0` 또는 마지막 유효 step으로 보정한다.
  - `Review` 카드의 `interval`이 0 이하이면 `1일`로 보정한다.
  - `easeFactor`가 `1.3`보다 작으면 `1.3`으로 보정한다.
  - `previousReviewInterval`이 없거나 0 이하이면 `1일`로 보정한다.

## 4. Local/Remote 저장 확장

- Room Entity와 DAO update query에 새 schedule 필드를 추가한다.
- Firestore DTO와 map 변환에 새 필드를 추가한다.
- remote sync update에 새 필드를 포함한다.
- 기존 문서 복원 시 누락 필드는 기본값으로 처리한다.

## 5. Migration과 호환 처리

- Room migration으로 기존 row에 새 컬럼 기본값을 채운다.
- 기존 Firestore 문서는 mapper fallback으로 읽을 수 있게 한다.
- 기존 dirty/pending sync 카드가 새 필드 없이도 sync 실패하지 않게 한다.

## 6. UI 라벨 정리

- 버튼 라벨은 `ReviewSchedulePolicy` 계산 결과를 계속 사용한다.
- `Again`도 실제 next interval을 표시한다.
  - Learning step 0의 `Again`은 `1분`으로 표시한다.
  - Review 카드의 `Again`은 `10분`으로 표시한다.
  - Relearning 카드의 `Again`은 `10분`으로 표시한다.
- 라벨과 저장 결과가 다른 값이 되지 않게 한다.

## 7. Cloud Functions와 알림 집계 보강

Cloud Functions는 Flashcard 원본을 직접 생성하거나 SRS schedule을 계산하지 않는다.
다만 `functions/index.js`의 due flashcard 알림 보정 흐름은 Firestore `flashcards` 문서를 읽어 `notifiableDueFlashcards`를 다시 계산한다.

현재 서버 보정 로직은 `nextReviewAt <= now`와 `lastReviewRating in HARD/GOOD/EASY`를 기준으로 알림 대상 수를 센다.
레거시 Anki 스타일에서는 `Learning`과 `Relearning`의 분 단위 due가 늘어나므로, 이 카드들을 알림 대상으로 세면 사용자가 짧은 재학습 카드 때문에 과도한 알림을 받을 수 있다.

정책:

- Android는 모든 due card를 `nextReviewAt <= now` 기준으로 학습 화면에 노출한다.
- 알림/요약의 `notifiableDueFlashcards`는 `Review` 상태 카드만 대상으로 한다.
- Cloud Functions의 보정 로직은 기존 query 결과를 읽은 뒤 `reviewState == "Review"` 조건을 코드에서 필터링한다.
- 기존 Firestore 문서에 `reviewState`가 없으면 `interval >= 1440`인 문서만 알림 대상 후보로 본다.
- Cloud Functions는 새 schedule 필드를 쓰지 않고 읽기 기준만 보강한다.
- Firestore query에 `reviewState == "Review"` 조건을 바로 추가하면 composite index가 새로 필요할 수 있으므로, 이번 작업에서는 기존 `language + nextReviewAt` 조회를 유지한다.

Correction 완료 직후 summary 갱신도 같은 기준을 사용한다.
교정으로 새로 저장된 카드는 `Learning` 상태이므로 `dueFlashcards`에는 포함될 수 있지만, `notifiableDueFlashcards`에는 포함되지 않아야 한다.

## 8. 테스트 보강

- 신규 카드 step 0 테스트
- 신규 카드 step 1 테스트
- Review 카드 Hard/Good/Easy 테스트
- Review 카드 Again 후 Relearning 진입 테스트
- Relearning Good/Easy 복귀 테스트
- 기존 데이터 fallback 테스트
- `ApplyReviewDecisionUseCase`가 새 schedule 필드를 repository로 전달하는지 검증
- Cloud Functions 알림 보정이 `Learning / Relearning` due card를 알림 수에 포함하지 않는지 검증
- Correction 완료 후 summary 갱신이 `Learning` 신규 카드를 알림 수에 포함하지 않는지 검증

---

# 실제 수정 대상

## Android Domain

- `app/src/main/java/com/app/umma/domain/model/flashcard/Flashcard.kt`
  - `ReviewCardState` enum을 추가한다.
  - `FlashcardSchedule`에 새 schedule 상태 필드를 추가한다.
  - 기존 fixture 파급을 줄이기 위해 새 필드에 안전한 기본값을 둔다.
- `app/src/main/java/com/app/umma/domain/model/flashcard/ReviewScheduleResult.kt`
  - repository 저장에 필요한 새 schedule 상태 필드를 추가한다.
  - rollback과 label 계산이 같은 결과를 쓰도록 기본값과 필드 의미를 `FlashcardSchedule`과 맞춘다.
- `app/src/main/java/com/app/umma/domain/usecase/flashcardreview/ReviewSchedulePolicy.kt`
  - 기존 `interval >= 1440` 추론을 제거하고 `reviewState` 기준 분기로 바꾼다.
- `app/src/main/java/com/app/umma/domain/usecase/flashcardreview/ApplyReviewDecisionUseCase.kt`
  - rollback 시 새 schedule 상태 필드까지 이전 값으로 되돌린다.
- `app/src/main/java/com/app/umma/domain/repository/FlashcardRepository.kt`
  - `ReviewScheduleResult` 확장으로 repository 계약이 자동 확장되는지 확인한다.

## Android Data

- `app/src/main/java/com/app/umma/data/model/correction/CorrectionFlashcardDto.kt`
  - Firestore 저장/복원 DTO에 새 schedule 필드를 추가한다.
  - 새 카드 생성 기본값을 `Learning / step0 / lapse0 / previous=null`로 둔다.
  - Firestore 누락 필드 fallback과 테스트 fixture 기본값을 같은 의미로 맞춘다.
- `app/src/main/java/com/app/umma/data/source/local/CorrectionFlashcardLocalDataSource.kt`
  - `CorrectionFlashcardEntity`에 새 컬럼을 추가한다.
  - `updateReviewSchedule()` query와 mapper에 새 필드를 포함한다.
  - `countNotifiableDueFlashcards()`는 `reviewState = Review`인 due card만 세도록 수정한다.
  - `CorrectionFlashcardDatabase` version을 `2 -> 3`으로 올린다.
- `app/src/main/java/com/app/umma/di/DatabaseModule.kt`
  - `MIGRATION_2_3_CORRECTION_FLASHCARD`를 추가한다.
  - 기존 row의 `reviewState`는 `interval >= 1440`이면 `Review`, 아니면 `Learning`으로 채운다.
- `app/src/main/java/com/app/umma/data/source/remote/CorrectionFlashcardRemoteDataSource.kt`
  - `syncReviewSchedule()`와 `syncFlashcards()`가 새 필드를 Firestore에 함께 저장하게 한다.
- `app/src/main/java/com/app/umma/data/repository/FlashcardRepositoryImpl.kt`
  - DTO와 domain schedule 사이의 mapper에 새 필드를 반영한다.
  - `getReviewSummary()`가 `dueFlashcards`와 `notifiableDueFlashcards`를 서로 다른 기준으로 계산하는지 확인한다.
- `app/src/main/java/com/app/umma/data/repository/fake/FakeFlashcardRepository.kt`
  - fake 카드 초기 schedule과 update payload를 새 모델에 맞춘다.
- `app/src/test/java/com/app/umma/data/source/local/TestCorrectionFlashcardLocalDataSource.kt`
  - local fake/test datasource가 새 schedule 필드를 보존하도록 수정한다.
- `app/src/main/java/com/app/umma/domain/usecase/correction/CompleteCorrectionUseCase.kt`
  - 교정 완료 후 Flashcard summary 갱신이 새 `notifiableDueFlashcards` 기준을 그대로 사용하도록 확인한다.

## Android Presentation

- `app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyViewModel.kt`
  - `againLabel`도 `ReviewSchedulePolicy` 계산 결과를 사용하게 한다.
  - 라벨 변환은 계속 분 단위 interval을 사람이 읽는 문자열로 바꾸는 역할만 한다.
- `app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyUiState.kt`
  - `againLabel` 기본값을 실제 계산 흐름과 맞춘다.

## Cloud Functions

- `functions/index.js`
  - `recalculateNotifiableDueFlashcardsCount()`에서 `reviewState == "Review"` 또는 기존 문서 fallback 조건을 반영한다.
  - 기존 query에 새 `where`를 추가하지 않고, 읽어온 문서를 코드에서 필터링해 index 추가 없이 보강한다.
  - SRS schedule 계산은 추가하지 않는다.

## Firebase / Firestore

- 현재 Firestore Rules가 collection 소유권 중심으로 동작한다면 새 필드 추가만으로 Rules 수정은 필요하지 않다.
- 이 작업과 별도로 field schema 검증을 강화한 상태라면 `reviewState`, `learningStep`, `lapseCount`, `previousReviewInterval`을 허용 필드에 추가한다.
- 기존 Firestore 문서에 새 필드가 없을 수 있으므로 Android mapper와 Cloud Functions 읽기 로직은 모두 fallback을 가져야 한다.

---

# 사이드 이펙트와 방어

## 1. 기존 카드의 상태 추론

기존 카드에는 `reviewState`가 없으므로 migration/fallback이 필요하다.
단순히 모든 카드를 `Learning`으로 두면 이미 장기 복습 중이던 카드가 다시 초기 학습처럼 보일 수 있다.

방어:

- `interval >= 1440분`이면 `Review`로 복원한다.
- `interval < 1440분`이면 `Learning`으로 복원한다.
- `lastReviewRating == AGAIN`이고 `interval <= 10분`이면 `Relearning` 후보로 볼 수 있지만, 기존 데이터에서 정확도가 낮으면 `Learning` fallback을 우선한다.

## 2. Due count 변화

신규 카드 `Good`이 1일 뒤가 아니라 10분 뒤가 되므로, 학습 직후 due count가 더 빨리 다시 증가할 수 있다.
이는 의도된 변화지만 Dashboard 숫자와 SRS 진입 UX가 달라질 수 있다.

방어:

- `nextReviewAt <= now` 기준은 유지한다.
- Summary는 기존처럼 schedule 저장 후 local 원본에서 재계산한다.
- 학습 완료 화면에서 "오늘 복습 완료"처럼 단정하는 문구가 있다면 후속 검토한다.

## 3. 세션 중 재노출 여부

현재 SRS ViewModel은 `Again`을 눌러도 현재 세션에 카드를 다시 추가하지 않는다.
레거시 Anki는 작은 learning step이 지나면 같은 세션 안에서 다시 보여줄 수 있다.

이번 작업에서는 우선 `nextReviewAt` 기준으로 다음 세션/재진입 때 due로 잡히는 구조를 유지한다.
세션 내부에서 1분/10분 뒤 자동 재노출하는 기능은 별도 후속 작업으로 둔다.

주의:

- 이 결정 때문에 Anki와 완전히 같은 "세션 내 대기열"은 아니다.
- 하지만 local-first 저장, 화면 단순성, 모바일 사용 패턴을 고려하면 우선 due deck 재진입 기준이 안전하다.

## 4. 알림과 due 정렬

분 단위 due가 늘어나면 알림 대상 카드 수가 늘어날 수 있다.
현재 `notifiableDueFlashcards`는 `lastReviewRating IN ('HARD', 'GOOD', 'EASY')` 조건을 사용한다.

방어:

- 사용자를 과도하게 알리지 않기 위해 `Learning`과 `Relearning`의 분 단위 due는 알림 대상에서 제외한다.
- Android local count와 Cloud Functions 보정 count는 모두 `Review` 상태만 `notifiableDueFlashcards`에 포함한다.
- 기존 문서에 `reviewState`가 없으면 `interval >= 1440` 기준으로만 알림 후보로 본다.
- due deck 자체에는 포함하되, 알림 summary는 별도 기준을 유지한다.

## 5. Sync와 rollback

새 schedule 필드가 local에는 저장됐지만 remote sync에 실패할 수 있다.
기존 pending sync 구조를 유지하되, 새 필드도 dirty sync 대상에 포함되어야 한다.

방어:

- `updateReviewSchedule()` rollback 시 새 schedule 필드도 이전 값으로 되돌린다.
- `syncDirtyFlashcards()`가 새 필드를 Firestore에 함께 업로드하는지 확인한다.
- remote 문서가 새 필드 없이 내려와도 mapper fallback으로 앱이 깨지지 않게 한다.
- 이전 앱 버전이나 이전 테스트 데이터가 새 필드 없이 문서를 남겨도 fallback은 영구 방어로 유지한다.

## 6. Room migration 실패

`CorrectionFlashcardDatabase`는 현재 version 2이며 `fallbackToDestructiveMigration(false)`를 사용한다.
따라서 migration이 빠지면 앱 업데이트 후 기존 사용자 기기에서 DB open이 실패할 수 있다.

방어:

- 반드시 `Migration(2, 3)`을 추가한다.
- nullable 가능 필드는 nullable column으로 추가한다.
- non-null 필드는 SQL default를 함께 지정한다.
- migration 후 기존 row가 due deck 조회와 mapper 변환을 통과하는지 테스트한다.

## 7. 기존 테스트와 문서 충돌

현재 `ReviewSchedulePolicyTest`와 `ApplyReviewDecisionUseCaseTest`는 기존 단순 정책의 기대값을 일부 유지하고 있다.
새 정책을 구현하면 기존 기대값은 회귀가 아니라 정책 변경으로 갱신되어야 한다.

방어:

- 테스트 이름과 fixture 주석을 레거시 Anki 스타일 정책 기준으로 다시 작성한다.
- 기존 `Good -> 1일` 신규 카드 기대값은 `Good -> 10분`으로 바꾼다.
- 기존 복습 카드 `Again -> 1분` 기대값은 `Again -> Relearning 10분`으로 바꾼다.

## 8. 모델 확장으로 인한 컴파일 파급

`FlashcardSchedule`과 `CorrectionFlashcardDto`는 테스트와 fake에서 직접 생성되는 곳이 많다.
새 필드를 non-default로 추가하면 정책 변경과 무관한 컴파일 수정이 과도하게 늘어난다.

방어:

- domain schedule 새 필드에는 기본값을 둔다.
- DTO 새 필드에도 기존 문서 fallback과 같은 기본값을 둔다.
- 테스트는 정책을 검증해야 하는 곳에서만 새 필드를 명시한다.
- 단순 fixture는 기본값을 사용해 테스트 의도가 schedule 세부값에 묶이지 않게 한다.

---

# 검증 기준

- `ReviewSchedulePolicyTest`가 Learning, Review, Relearning 케이스를 모두 검증한다.
- `ApplyReviewDecisionUseCaseTest`가 새 schedule 필드를 repository에 전달하고 rollback도 원복하는지 확인한다.
- Room `2 -> 3` migration 테스트가 기존 row를 보존하고 새 schedule 필드를 채우는지 확인한다.
- `CorrectionFlashcardDto`가 새 필드 없는 Firestore 문서도 안전하게 복원하는지 확인한다.
- `FlashcardRepositoryImpl`과 `FakeFlashcardRepository`가 같은 schedule 의미를 사용한다.
- Cloud Functions 알림 보정은 `Learning / Relearning` due card를 `notifiableDueFlashcards`에 포함하지 않는다.
- 기존 카드가 새 필드 없이 복원되어도 앱이 크래시 나지 않는다.
- 신규 카드에서 `Good`을 누르면 버튼 라벨과 저장 결과가 모두 10분 뒤로 계산된다.
- Learning step 1에서 `Good`을 누르면 `Review` 상태와 1일 interval로 졸업한다.
- Review 카드에서 `Again`을 누르면 `Relearning` 상태와 10분 interval로 저장된다.
- Relearning 카드에서 `Good`을 누르면 `Review` 상태로 복귀한다.
- Dashboard/SRS due count는 `nextReviewAt <= now` 기준을 계속 따른다.
- Firestore sync 실패는 local completion 실패로 바뀌지 않고 pending sync로 남는다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`을 통과한다.

---

# 제외 범위

- FSRS 구현
- AI가 카드 난이도를 자동 평가하는 기능
- 카드별 개인화 preset 자동 조정
- 세션 내부 learning queue 재삽입
- 알림 UX 전면 개편
- Dashboard 디자인 변경
- Correction의 Flashcard 생성 정책 변경

---

# 작업 규모

예상 작업 규모는 중간 이상이다.

| 작업 | 규모 |
| --- | --- |
| 문서/정책 확정 | 작음 |
| Domain 모델과 schedule policy 수정 | 중간 |
| Room/DTO/Firestore sync 확장 | 중간 이상 |
| migration/fallback/dirty sync 검증 | 중간 |
| UI 라벨과 테스트 보강 | 중간 |

전체적으로는 2.5~4일 규모로 본다.
기존 카드 호환성과 pending sync까지 안전하게 검증해야 하므로, 단순 계산식 변경 작업으로 취급하지 않는다.
