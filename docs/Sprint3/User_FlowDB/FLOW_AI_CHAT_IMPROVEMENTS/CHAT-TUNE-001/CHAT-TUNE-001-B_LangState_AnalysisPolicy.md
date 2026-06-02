# [Implementation] CHAT-TUNE-001-B LangState AnalysisPolicy

## 목적

현재 `LearningStateWriteUseCases.kt`의 `prepareNextState()`를 명시적인 `LangStateAnalysisPolicy`로 분리하고, 이후 Correction signal/evidence/focus/difficulty guard를 단계적으로 반영한다.

초기 구현은 기존 동작을 보존해야 한다.
정책 분리 자체가 점수 변화를 만들면 회귀를 추적하기 어렵기 때문이다.

---

# 포함 범위

- `LangStateAnalysisPolicy` interface 추가
- 기존 `prepareNextState()` 로직 보존 이동
- `analysisMeta.metricEvidence` 갱신
- `analysisMeta.activeFocus` 갱신
- source/corrected 난이도 guard 계산
- confidence/evidence/smoothing/max delta 적용

# 제외 범위

- Repository 저장 책임 변경
- Correction prompt 실제 튜닝
- Chat prompt 실제 적용
- 별도 AI 레벨 테스트

---

# 권장 구조

```kotlin
interface LangStateAnalysisPolicy {
    fun analyze(input: LangStateUpdateInput): LangState
}
```

`ApplyLanguageStateUpdateUseCase`는 중복 분석 방어와 저장 orchestration을 유지하고, 실제 계산만 `LangStateAnalysisPolicy`에 위임한다.

---

# 계산 원칙

- 결측값은 기존 값을 유지한다.
- 모든 double metric은 `0.0..1.0` 범위로 clamp한다.
- 낮은 confidence signal은 반영하지 않거나 낮은 weight로 반영한다.
- `maxDeltaPerUpdate`로 단일 update의 급등락을 막는다.
- `vocabularyLevel`은 한 번에 한 단계까지만 움직인다.
- `vocabularyLevel`은 `directionCount`가 충분히 누적될 때만 이동한다.
- idempotency는 기존 `analysisEventId` 정책을 유지한다.

초기 구현 상수 후보:

| 항목 | 기준 |
| --- | --- |
| low confidence | `< 0.5` |
| medium confidence | `0.5 <= confidence < 0.75` |
| high confidence | `>= 0.75` |
| double metric max delta | update 1회당 `0.03` 기본 상한 |
| low confidence metric delta | 장기 score에는 직접 반영하지 않거나 `0.01` 이하 |
| medium confidence metric delta | 기본 max delta의 절반 이하 |
| focus max count | 5개 |
| prompt focus count | 상위 1~2개 |
| level movement evidence | 같은 방향 high-confidence evidence 3회 이상 |

상수는 구현 중 실제 테스트 결과에 따라 조정할 수 있지만, 조정하더라도 원칙은 유지한다.
단일 교정 결과는 active focus에 빠르게 보일 수 있어도 장기 score와 CEFR level을 크게 움직이면 안 된다.

---

# Focus / Score / Level 속도

```text
Focus fast
→ 최근 교정에서 드러난 약점이나 다음 대화에서 도울 주제는 빠르게 반영한다.

Score slow
→ grammarAccuracy, naturalnessScore 같은 장기 score는 smoothing과 max delta를 거쳐 천천히 움직인다.

Level slowest
→ CEFR vocabularyLevel 같은 단계 값은 반복 관측이 누적될 때만 한 단계씩 이동한다.
```

예:

- Article 오류 1회는 active focus 후보가 될 수 있다.
- grammarAccuracy는 조금만 움직인다.
- vocabularyLevel은 움직이지 않는다.

---

# Difficulty Guard

`difficultyDelta`는 Correction AI에게 받지 않는다.
LearningState가 source/corrected 문장을 비교해 correctedText가 현재 사용자에게 과하게 어려운지 판단한다.

계산 후보:

- source/corrected token 수 차이
- 평균 문장 길이 차이
- 절/구문 확장 여부
- 새 표현 추가 수
- `StructureExpanded`, `SpokenExpressionAdded`, `BetterCollocation` 여부
- correctedText register
- 기존 grammar/vocabulary/fluency/naturalness stage

이 계산은 정밀 CEFR 판정이 아니라, 과한 교정을 막는 challenge guard다.

초기 guard 방향:

- correctedText가 sourceText보다 짧거나 비슷하고 `GrammarFixed` 중심이면 current ability 범위 안의 수정으로 본다.
- correctedText가 길어지고 `StructureExpanded`, `SpokenExpressionAdded`, `BetterCollocation`이 함께 있으면 stretch 후보로 본다.
- 사용자의 `grammarStage`나 `fluencyStage`가 `Foundation`인데 여러 개의 새 구조와 표현이 동시에 추가되면 과한 challenge로 본다.
- 과한 challenge로 판단된 correction signal은 focus에는 반영할 수 있지만 장기 score 상승 근거로는 낮은 weight를 사용한다.
- meaningPreserved가 false인 signal은 difficulty guard 계산에는 참고할 수 있으나 장기 능력 score를 올리는 근거로 쓰지 않는다.

---

# Evidence 갱신 정책

- 새 관측이 기존 evidence와 같은 방향이면 `directionCount`를 올린다.
- 방향이 바뀌면 `Mixed`로 두고 confidence를 낮춘다.
- `confidence < 0.5` signal은 장기 metric에는 반영하지 않고 focus 후보로만 사용할 수 있다.
- `0.5 <= confidence < 0.75` signal은 낮은 weight로 반영한다.
- `confidence >= 0.75`이고 enum 검증을 통과한 signal만 일반 weight로 반영한다.
- unknown enum이나 confidence 범위 오류로 drop된 signal은 evidence에 반영하지 않는다.
- repeated high-confidence signal만 enum level 이동의 근거가 될 수 있다.

---

# Focus 갱신 정책

- active focus는 최대 5개 유지
- 반복 관측되면 observedCount/confidence 증가
- 오래 관측되지 않으면 confidence decay
- 기준 이하 confidence는 제거
- profile/prompt에는 상위 1~2개만 사용

초기 focus decay 방향:

- 같은 focus가 다시 관측되면 최근성과 confidence를 올린다.
- 일정 기간 또는 일정 분석 횟수 동안 다시 관측되지 않으면 confidence를 낮춘다.
- confidence가 `0.35` 미만으로 내려가면 active focus에서 제거한다.
- active focus가 5개를 넘으면 confidence가 낮고 오래된 항목부터 제거한다.
- low confidence signal도 반복되면 focus 후보가 될 수 있지만, score 상승/하락 근거로 바로 쓰지는 않는다.

---

# 검증 기준

- `LangStateAnalysisPolicy` 도입 직후 기존 `prepareNextState()` 결과가 보존된다.
- `analysisEventId` 중복 방어가 유지된다.
- null/empty signal에서 기존 metric이 보존된다.
- single correction outlier가 score/level을 크게 흔들지 않는다.
- repeated focus는 activeFocus에 누적된다.
- 오래된 focus는 제거 대상이 된다.
- `vocabularyLevel`은 충분한 같은 방향 evidence 없이 이동하지 않는다.
- confidence 범위 오류와 unknown enum signal은 evidence/focus에 잘못 저장되지 않는다.
- 과한 challenge correction은 focus에는 남을 수 있지만 장기 score를 크게 올리지 않는다.
