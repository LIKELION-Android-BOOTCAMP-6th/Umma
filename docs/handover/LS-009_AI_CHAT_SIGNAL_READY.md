# [LS-009] AI Chat 담당자 전달 사항

> 대상: AI Chat / Realtime 후속 작업자
> 관련 이슈: #150 (`LS-009 AI Chat turn 확정 후 correctionAvailable 시그널 전파`)
> 작성자: `SYS-LEARNING-STATE-INFRA` 작업자

---

## 1. 요약

AI Chat에서 `USER final turn`이 Session Memory에 저장된 뒤 호출할 수 있는 LearningState 진입점이 준비되었다.

AI Chat 쪽은 `LangState`, `SessionSummary`, `DashSummary`를 직접 조립하거나 수정하지 않고, 아래 UseCase에 "교정 가능 신호가 발생했다"는 입력만 넘기면 된다.

- `ApplyCorrectionSignalUpdateUseCase`
- `CorrectionSignalUpdateInput`

---

## 2. 연결 지점

권장 호출 시점은 `USER final turn` append가 성공한 직후다.

```text
USER final turn 확정
→ Session Memory append 성공
→ ApplyCorrectionSignalUpdateUseCase 호출
→ LS가 SessionSummary / DashSummary의 correctionAvailable을 true로 반영
```

AI Chat 화면 이탈 시점이 아니라 append 직후를 기준으로 잡은 이유는, 사용자가 화면을 바로 닫거나 OS가 앱을 중단해도 교정 가능 신호가 누락되지 않게 하기 위해서다.

---

## 3. 호출 입력

```kotlin
CorrectionSignalUpdateInput(
    uid = uid,
    lang = selectedLearningLanguage,
    sessionMemoryKey = sessionMemoryKey,
    sourceEventId = sourceEventId,
    recentMinutes = recentMinutes,
    recentTopic = recentTopic,
    updatedAt = now
)
```

필수 값:

| 값 | 의미 |
| --- | --- |
| `uid` | 현재 로그인 사용자 |
| `lang` | 현재 선택 학습 언어 |
| `sessionMemoryKey` | Session Memory 스코프 식별자 |
| `sourceEventId` | 같은 turn 재시도 중복 방지용 이벤트 식별자 |
| `updatedAt` | 신호 확정 시각 |

`sessionMemoryKey`는 개별 LiveSession id가 아니라 사용자와 언어 기준의 Session Memory 스코프로 맞춘다.

```text
{uid}_{languageCode}
예: uid-1_en
```

선택 값:

| 값 | 의미 |
| --- | --- |
| `recentMinutes` | 최근 대화 시간. 없으면 기존 summary 값을 유지 |
| `recentTopic` | 최근 대화 주제. 없으면 기존 summary 값을 유지 |

`sourceEventId`는 `USER final turn.turnId` 사용을 권장한다.
같은 USER final turn 재시도에서 동일하게 유지되어야 한다.
호출할 때마다 바뀌는 timestamp만으로 만들면 idempotent 처리가 약해진다.

---

## 4. 실패 처리

`ApplyCorrectionSignalUpdateUseCase`는 `Result.failure`를 반환할 수 있다.

대표 실패 조건:

- `uid`, `sessionMemoryKey`, `sourceEventId`가 비어 있음
- `recentMinutes`가 음수
- 입력 언어가 현재 선택 언어와 다름
- 초기 summary가 아직 준비되지 않음

이 실패는 Chat turn 저장 실패로 되돌릴 필요는 없다.
Session Memory append가 이미 성공했다면 대화 저장은 유지하고, LS 신호 갱신 실패만 로그/재시도 대상으로 다루면 된다.

---

## 5. 책임 경계

AI Chat 쪽에서 하지 않아야 할 일:

- `SessionSummary.correctionAvailable` 직접 수정
- `DashSummary.correctionAvailable` 직접 수정
- `LangStateUpdateInput` 조립
- full LangState 분석 실행
- Dashboard / Correction UI 상태 직접 변경

AI Chat 쪽에서 해야 할 일:

- USER final turn append 성공 이후 LS 진입점 호출
- stable한 `sourceEventId` 전달
- 실패 시 Chat 저장 흐름을 막지 않는 non-blocking 처리

---

## 6. 참고 구현

- `ApplyCorrectionSignalUpdateUseCase`: `app/src/main/java/com/app/umma/domain/usecase/learningstate/LearningStateWriteUseCases.kt`
- `CorrectionSignalUpdateInput`: `app/src/main/java/com/app/umma/domain/model/learningstate/LearningUpdateModels.kt`
- `LearningStateRepo.updateCorrectionSignal`: `app/src/main/java/com/app/umma/domain/repository/LearningStateRepo.kt`
