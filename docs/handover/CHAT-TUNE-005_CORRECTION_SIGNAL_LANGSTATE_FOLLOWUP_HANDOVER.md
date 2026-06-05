# [Handover] CHAT-TUNE-005 교정 신호 × LangState 정합성 후속 과제

## 목적

COR 담당자가 CHAT-TUNE-004 점검(교정↔LangState 양방향 흐름 정합성 확인) 과정에서 발견한
**LearningState/Chat 영역 후속 과제**를 인계한다.
코드 변경이 필요한 Finding 1과 문서 갱신이 필요한 Finding 2, 그리고 논의가 필요한 Finding 5를 담는다.

---

## 배경

CHAT-TUNE-004 IMPL 인계 이후 COR 담당자가 다음을 점검했다.

- 대화 세션 → 교정 → LangState 전달(방향 A)
- LangState → 교정·대화 프롬프트 반영(방향 B)

점검 결과 두 방향 모두 배선은 정상이었으나, LearningState 영역에서 아래 세 가지 후속 항목이 발견됐다.

---

## Finding 1 — 수정 필요: 점수 부적격 신호가 장기 점수를 *상승*시킨다

### 현상

교정 신호가 있지만 **장기 점수 반영 자격이 없는 경우**(= `meaningPreserved=false`, 또는
`confidence < 0.35`, 또는 과확장 guard 발동)에, 교정 기반 지표 측정이 "교정 0건"으로 계산되어
점수가 만점(1.0) 쪽으로 **끌려 올라간다.**

### 핵심 경로

1. `LangStateAnalysisPolicy.effectiveCorrectionCountForLongTermScore()`
   (`LangStateAnalysisPolicy.kt:319-333`) — 신호가 있으나 score-eligible이 0이면 **0을 반환**.
2. `measureGrammarAccuracy()`(`:184-197`) — `1.0 - count/userTurns` → count=0이면 **측정값 1.0**.
3. `smoothMetric()`(`:733-738`) — `previous*0.8 + 1.0*0.2` → 점수가 위로 이동.
4. 같은 문제가 `measureVocabularyAppropriateness`, `measureNaturalness`, `measureErrorRecurrence`에도 전파.

### 왜 문제인가

- CHAT-TUNE-003 계약: *"meaning이 보존되지 않은 signal은 장기 score를 직접 **움직이지 않는다**"*
  (중립이어야 함). 그런데 실제로는 **위로 움직인다.**
- evidence 레이어는 `Down`(약점)으로 기록되는데(`metricUpdates` `:484-488`),
  내부 점수는 `Up`으로 움직여 **evidence와 score가 서로 모순**된다.
- MVP fallback 비대칭: 신호가 아예 없으면 raw `correctionCount`로 **감점**한다. 즉
  "부적격 신호 하나가 붙는 순간 감점이 가점으로 뒤집힌다."
- 반복 누적 시 의미 깨진 발화 사용자의 점수가 1.0으로 표류 → ProfileConfidence/band 상승 → 역효과.

### 증거 — 테스트가 잘못된 동작을 회귀로 박제하고 있음

- `LearningStateWriteUseCasesTest.kt:382-427`
  `meaning not preserved signal does not directly reduce long term score`:
  grammarAccuracy 0.6 → **0.68 단언**(`:425`). 주석은 "내려가지 않는다"지만 실제로는 **올라간다**.
- `:430-514` `over expanded correction signal...`: 동일하게 0.6 → **0.68**(`:514`).

### 권장 수정안

"점수 부적격 신호만 있을 때"는 교정 기반 측정을 **중립(null)** 으로 만든다.
evidence/focus는 그대로 유지한다(약점 근거는 계속 기록).

1. `effectiveCorrectionCountForLongTermScore()`가 "신호 있음 + score-eligible 0" 상태를 구분하게 한다.
   예: nullable `Int?` 반환 또는 sealed 상태(`NoSignal` / `NoEligible` / `Eligible(count)`).
2. `measureGrammarAccuracy` / `measureVocabularyAppropriateness` / `measureNaturalness` /
   `measureErrorRecurrence`에서 "신호 있음 + eligible 0"이면 **null 반환** → `smoothMetric`이
   기존 값을 그대로 보존.
   - 신호가 전혀 없을 때(MVP fallback)는 기존 raw `correctionCount` 동작 유지 — 회귀 금지.
   - eligible이 1개 이상이면 기존 동작 유지(eligible 개수로만 감점).
3. 테스트 2건(`:425`, `:514`)을 **"점수 불변(0.6) + evidence/focus는 기록됨"** 으로 정정.
   주석도 "내려가지 않는다" → "움직이지 않는다(중립)"로 수정.
4. eligible 부분 적용(3건 중 1건만 eligible) 시 감점이 약해지는 것은 계약상 허용
   (부적격 건은 감점 대상 아님)이므로 그대로 둔다.
   핵심은 **"전부 부적격"일 때의 상향 이동 차단**이다.

### 영향 파일

- `app/src/main/java/com/app/umma/domain/usecase/learningstate/LangStateAnalysisPolicy.kt`
- `app/src/test/java/com/app/umma/domain/usecase/learningstate/LearningStateWriteUseCasesTest.kt`

---

## Finding 2 — 문서 갱신 필요: CHAT-TUNE-004 IMPL 인계 문서가 stale

### 현상

`docs/handover/CHAT-TUNE-004_CORRECTION_GROWTH_POLICY_IMPL_HANDOVER.md`의
"## LearningState 담당자 정교화 지점" 섹션은 두 항목을 여전히
"현재 인터림 로직 / 정교화가 필요한 부분"으로 적고 있다.
그러나 **둘 다 `BuildLearnerAdaptationProfileUseCase`에 구현 완료**되었다.

### 구현 완료 확인 위치

| 인계 문서 항목 | 실제 구현 위치 |
|---|---|
| metricEvidence 기반 band 보수 조정 | `BuildLearnerAdaptationProfileUseCase.kt:532-565` `correctionGrowthEvidenceProfile()` — Mixed/Down 방향·반복 약점·상승 근거 개수(Connected≥2, Nuance≥3) 게이트 구현 |
| sentenceComplexity 독립 방어 | `BuildLearnerAdaptationProfileUseCase.kt:469-481` — grammar와 별도로 `sentenceComplexity` stage를 계산해 상위 band 차단 |

### 권장 조치

해당 섹션을 **"구현 완료"** 로 갱신하고, 위 구현 위치와 관련 상수
(Connected≥2: `:679`, Nuance≥3: `:680`)를 명시한다.
다음 작업자가 이미 끝난 일을 다시 하거나 "미구현"으로 오해하지 않도록 한다.
(프로젝트 안전 규칙 "문서-코드 정합성" 준수)

---

## Finding 5 — 논의 필요: 단일 교정 focus의 prompt 노출 지연

### 현재 동작

- 교정 1회로 `analysisMeta.activeFocus`에는 focus가 **즉시 저장**된다(`mergeFocuses` observedCount=1).
- 그러나 prompt **노출**은 `LearningFocusSummary.confidence`가 게이트한다.
  `confidenceFromScore()` (`BuildLearnerAdaptationProfileUseCase:580-590`)는
  `observedCount ≥ 2`(Medium 이상)부터만 Low를 벗어난다.
  → 1회 관측 focus는 summary.confidence = **Low**.
- 소비처가 Low를 차단:
  - 대화: `BuildPromptUseCase.focusBlock()` (`BuildPromptUseCase.kt:526-537`)이
    `confidence == Low`면 "특정 약점 억지로 꺼내지 않는다"로 빠짐.
  - 교정: `hasMeaningBlockingFocus()` (`BuildLearnerAdaptationProfileUseCase.kt:517-524`)도
    Low면 false.
- 즉 같은 focus가 **2회 이상 반복 관측**돼야 다음 대화/교정 프롬프트에 명시 노출된다.
  (단, 내부 metric stage는 1회에도 `smoothMetric`으로 조금 움직여 난이도는 미세하게 반영됨)

### 문서와의 긴장

CHAT-TUNE-003은 *"단일 signal은 focus에는 **빠르게 반영**"* 이라고 적어,
"저장은 빠르되 노출은 2회부터"인 현 구현과 표현상 어긋난다.
"교정 1회 → 바로 다음 대화에서 약점이 보여야 한다"는 기대였다면 체감상 덜 반영된 것처럼 보인다.

### 현재 설계의 근거 (버그 아님)

`focusBlock` 주석("confidence 낮으면 장기 약점으로 단정하지 않는다")과
"반복 학습 초점" 정책에 부합한다. 게이트를 1회로 낮추면 noise 1건이 장기 약점으로 과대 노출되어
보수 설계가 무너진다.

### 논의 포인트

- "교정 1회 → 바로 다음 대화에 약점 노출"이 목표 UX인가?
- 현재 "저장은 빠르게, 노출은 2회부터"가 사용자 체감에 문제인가?

### 제안 옵션 (합의 후 구현)

"focus는 빠르게, score는 천천히" 원칙 안에서:

- 최근 1회 **strong severity**(BlockingMeaning / MajorPattern) 신호에 한해,
  장기 focus 승격(2회 기준)과 **분리된 one-turn 약한 hint** 채널을 추가한다.
  예: `LearningFocusSummary`에 `recentHint: LearningFocusType?` 필드 또는 별도 transient 신호.
- 장기 `activeFocus`/band 게이트(2회 기준)는 그대로 두어 noise 내성 보존.

### 변경 시 영향 파일 후보

| 파일 | 역할 |
|---|---|
| `LangStateAnalysisPolicy.kt` | focus 산출(`mergeFocuses` → one-turn hint 분리) |
| `LearnerAdaptationProfile.kt` / `LearningFocusSummary` 모델 | `recentHint` 필드 추가 후보 |
| `BuildPromptUseCase.kt` | `focusBlock` — recentHint가 있으면 soft hint 추가 |
| `CorrectionPromptBuilder.kt` | 교정 side에서 recentHint 반영 여부 |

---

## 책임 경계 명시

위 세 Finding은 모두 **LearningState/Chat 담당 영역**이다.

- Finding 1: `DefaultLangStateAnalysisPolicy`의 점수 계산 정책 (CHAT-TUNE-003 책임경계 "LearningState domain: signal을 evidence/focus/score로 해석")
- Finding 2: LearningState 담당자가 구현한 코드에 대한 문서 상태 갱신
- Finding 5: `BuildLearnerAdaptationProfileUseCase`의 focus 노출 정책 + `BuildPromptUseCase`의 소비 정책

COR 담당자는 점검 및 인계만 수행하였으며, 위 파일에 대한 코드/문서 변경은 LearningState/Chat 담당자가 결정한다.
