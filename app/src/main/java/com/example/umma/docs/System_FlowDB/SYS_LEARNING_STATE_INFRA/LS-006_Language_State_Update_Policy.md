# [Infra] LS-006 Language State Update Policy

## User Story

개발자는 AI 대화와 교정 결과가 사용자의 장기 언어 능력 상태에 안정적으로 반영되도록,
`LanguageStateVO`를 언제, 어떤 입력으로, 어떤 계산 방식으로 업데이트할지 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Language State 업데이트 시점이 정의된다.
- [ ] 대화 중 실시간 Language State 업데이트를 하지 않는 원칙이 정의된다.
- [ ] 업데이트 입력 데이터 범위가 정의된다.
- [ ] `recentFullContext`에서 분석 대상 turn을 추출하는 정책이 정의된다.
- [ ] Type A / Type B / Type C 분석 분류가 정의된다.
- [ ] MVP Internal Metrics 12개와 분석 타입 매핑이 정의된다.
- [ ] AI 분석 대상은 MVP 필드 중 필요한 최소 범위로 제한된다.
- [ ] AI 분석 결과가 `null`일 때 기존 값을 유지하는 정책이 정의된다.
- [ ] 이동평균 기반 업데이트 공식이 정의된다.
- [ ] 급격한 점수 변동을 제한하는 안정화 정책이 정의된다.
- [ ] External Metrics 재계산 정책이 정의된다.
- [ ] Dashboard Summary delta 재계산 정책이 정의된다.
- [ ] Local update와 Firebase sync 순서가 정의된다.
- [ ] 중복 분석 또는 중복 업데이트 방지 정책이 정의된다.
- [ ] 실패 시 fallback / retry 정책이 정의된다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- LS-001 → Language State Model Structure
- LS-002 → Dashboard Summary Model
- LS-004 → Global Learning State Store
- LS-005 → Local Cache & Sync Policy
- LS-006 → Language State Update Policy
- DASH-003 → 교정 대기 카드
- DASH-005 → 언어 성취율 카드
- AI Chat → turn 확정 및 Session Memory append
- Correction → recentFullContext 기반 교정 생성
- Flashcard → 저장 카드 및 복습 결과 반영

---

# 구현 범위

## 포함 범위

- Language State batch update 시점 정의
- 업데이트 입력 데이터 정의
- Type A / B / C 분석 전략 정의
- MVP Internal Metrics 12개 업데이트 정책 정의
- External Metrics 재계산 정책 정의
- Dashboard Summary delta 갱신 정책 정의
- 이동평균 및 안정화 정책 정의
- 중복 업데이트 방지 정책 정의
- 실패 및 재시도 정책 정의

---

## 제외 범위 (Out of Scope)

- `LanguageStateVO` 모델 필드 정의 (LS-001)
- Local Cache / Firebase Sync 저장소 구현 (LS-005)
- 실제 AI prompt 세부 튜닝
- STT / audio feature 추출 세부 구현
- Correction UI 구현
- Statistics UI 구현
- Phase 2 확장 지표 저장

> LS-006은 “Language State 값을 어떻게 갱신할 것인가”를 정의한다.
> 모델 구조는 LS-001, 저장 및 동기화는 LS-005, 화면 렌더링은 각 User Flow 문서에서 다룬다.

---

# Details

## 핵심 원칙

### 1. 업데이트는 batch로 수행한다

Language State는 대화 중 매 turn마다 업데이트하지 않는다.

```text
AI Chat 진행 중
→ streaming chunk / 임시 transcript는 memory buffer에만 유지
→ turn 확정 시 Session Memory에 append
→ 교정 또는 학습 분석 시점에 batch update
```

목적:

- Firebase write 비용 절감
- AI 호출 최소화
- 단일 발화에 따른 점수 급변 방지
- Correction / Flashcard / Statistics와 동일한 분석 기준 유지

---

### 2. 현재 선택 언어와 세션 언어가 일치해야 한다

업데이트 대상은 항상 세션의 `language` 기준이다.

```text
session.language = "en"
→ users/{uid}/language_states/en 업데이트
```

`selectedLearningLanguage`는 현재 화면 컨텍스트일 뿐이며,
이미 저장된 Session Memory의 `language`를 덮어쓰지 않는다.

---

### 3. MVP 저장 필드는 LS-001의 12개 Internal Metrics로 제한한다

기존 아이디어 문서에는 다음과 같은 AI 진단 지표가 등장할 수 있다.

- `contextualResponseQuality`
- `expressionConfidence`
- `responseLatency`
- `repetitionRate`

MVP에서는 이 값을 `LanguageStateVO`에 저장하지 않는다.
필요하면 분석 과정의 참고값 또는 Phase 2 후보로만 둔다.

LS-006에서 실제 저장 대상으로 삼는 Internal Metrics는 LS-001의 12개이다.

---

# 업데이트 트리거

## 1. Correction 완료 직후

가장 기본 업데이트 시점이다.

```text
Correction 화면 진입
→ selectedLearningLanguage 기준 Session Memory 조회
→ users/{uid}/sessions/{selectedLearningLanguage}
→ recentFullContext에서 분석 대상 추출
→ 교정 결과 생성
→ Language State batch update
→ Dashboard Summary 갱신
→ recentFullContext 압축 또는 초기화
```

---

## 2. 학습 분석 완료 직후

Correction 화면을 거치지 않더라도, 세션 종료 후 별도 학습 분석을 수행하는 경우 업데이트할 수 있다.

```text
AI Chat 종료
→ 학습 분석 실행
→ Language State batch update
→ Dashboard Summary 갱신
```

MVP에서는 Correction 완료 직후를 우선 구현하고,
별도 학습 분석은 후속 단계로 둘 수 있다.

---

## 3. Flashcard 복습 결과 반영

Flashcard 복습은 Language State의 `reviewRetention`에 영향을 줄 수 있다.

```text
Flashcard review 완료
→ 정답/오답/난이도 응답 기록
→ reviewRetention 측정값 생성
→ Language State batch update 또는 별도 lightweight update
```

단, Flashcard review 결과는 중복 반영되면 안 된다.
`reviewEventId` 또는 `lastReviewedAt` 기준 idempotent update를 고려한다.

---

# 업데이트 입력 데이터

## 필수 입력

```kotlin
data class LanguageStateUpdateInput(
    val uid: String,
    val language: String,
    val sessionMemoryKey: String,
    val analysisEventId: String?,
    val currentState: LanguageStateVO,
    val recentUserTurns: List<ConversationTurnVO>,
    val correctionResult: CorrectionResultVO?,
    val flashcardReviewEvents: List<FlashcardReviewEventVO>,
    val analyzedAt: Long
)
```

> VO 이름은 설명용이다.
> 실제 구현 시 팀의 모델 네이밍에 맞춰 조정한다.

---

## recentFullContext 사용 정책

`recentFullContext` 전체를 AI에 그대로 전달하지 않는다.

분석 대상:

- 사용자 발화 turn
- 교정 후보로 선택된 문장
- 최근 세션의 핵심 발화
- 발화 길이, turn 수, pause 등 계산에 필요한 metadata

제외 대상:

- AI 응답 전체 원문
- streaming 중간 chunk
- 빈 발화
- STT confidence가 너무 낮은 발화
- 현재 분석 대상 언어와 다른 언어의 turn

---

## 분석 대상 추출 흐름

```text
selectedLearningLanguage 확인
→ users/{uid}/sessions/{selectedLearningLanguage} 조회
→ session.language 확인
→ recentFullContext에서 user turn 필터링
→ 너무 짧거나 신뢰도 낮은 turn 제외
→ Type A / B 계산용 payload 생성
→ Type C AI 분석용 최소 payload 생성
```

---

# Type A / B / C 분석 정책

## Type A: 코드 계산

규칙이나 AI 없이 앱 로직으로 계산할 수 있는 값이다.

특징:

- 비용 낮음
- 결과 재현성 높음
- 테스트 작성 쉬움

예:

- 평균 발화 길이
- turn 수
- token 수
- 발화 시간
- pause 빈도

---

## Type B: 규칙 기반 분석

사전, 패턴, 간단한 문법 규칙으로 근사 계산하는 값이다.

특징:

- 비용 낮음
- AI보다 일관성 높음
- 언어별 규칙 품질에 따라 정확도 차이 발생

예:

- 어휘 다양성
- CEFR vocabulary level 매핑
- 문장 복잡도 근사
- 반복 오류율

---

## Type C: AI 분석

문맥과 자연스러움 판단이 필요한 값이다.

특징:

- 비용 높음
- 결과 변동 가능성 있음
- 최소 필드만 요청해야 함

MVP에서 Type C persisted field는 다음 2개로 제한한다.

- `spokenNaturalness`
- `naturalExpressionUsage`

---

# Internal Metrics 업데이트 매핑

| Internal Metric | 타입 | 업데이트 입력 | 정책 |
| --- | --- | --- | --- |
| `grammarAccuracy` | Type B + Correction | correctionResult, 오류 수 | 문법 오류율을 점수화 |
| `vocabularyAppropriateness` | Type C 또는 Correction | 교정 결과, AI 분석 | 문맥상 어휘 선택 적절성 |
| `lexicalDiversity` | Type A/B | user turn token 집계 | 고유 어휘 비율 기반 |
| `vocabularyLevel` | Type B | CEFR 사전 또는 난이도 매핑 | 가장 안정적인 등급으로 반영 |
| `sentenceComplexity` | Type B | 문장 구조 패턴 | 접속사, 절, 문장 길이 근사 |
| `speechRate` | Type A | 발화 시간, token 수 | 분당 발화량 또는 상대 점수 |
| `pauseFrequency` | Type A | pause metadata | pause 횟수/길이 기반 |
| `avgUtteranceLength` | Type A | user turn token 수 | 평균 발화 길이 |
| `spokenNaturalness` | Type C | AI 분석 | 구어체 자연스러움 |
| `naturalExpressionUsage` | Type C | AI 분석 | 자연스러운 표현 사용 |
| `errorRecurrence` | Type B + Correction | 이전 교정 오류, 현재 오류 | 반복 오류율 |
| `reviewRetention` | Type A | Flashcard review events | 복습 유지율 |

---

# Type C AI 분석 정책

## 호출 조건

Type C는 매번 호출하지 않을 수 있다.

호출 조건 예:

- 최근 분석 이후 누적 user turn이 충분한 경우
- Correction 완료 시점
- 최근 Type C 분석이 오래된 경우
- 자연스러움 관련 교정 후보가 충분한 경우

MVP에서는 Correction 완료 시점에 Type C를 호출하는 방식을 우선한다.

---

## AI 입력 최소화

AI에는 필요한 정보만 전달한다.

```text
- selected language
- current Language State summary
- filtered user turns
- correction candidates
- 요청 지표: spokenNaturalness, naturalExpressionUsage
```

전달하지 않는 것:

- Language State 전체 raw history
- AI 응답 전체
- Flashcard 전체 목록
- Statistics 전체 히스토리

---

## AI 반환 형식

AI는 JSON만 반환해야 한다.

```json
{
  "spokenNaturalness": 0.68,
  "naturalExpressionUsage": 0.72,
  "vocabularyAppropriateness": null
}
```

정책:

- 값 범위는 `0.0 ~ 1.0`으로 제한한다.
- 판단 불가 값은 `null`로 반환한다.
- `null` 값은 기존 Language State 값을 유지한다.
- 정의되지 않은 필드는 무시한다.
- MVP 모델에 없는 필드는 저장하지 않는다.

---

# 업데이트 공식

## 기본 이동평균

단일 세션으로 점수가 급변하지 않도록 이동평균을 사용한다.

```text
updated = previous * 0.8 + measured * 0.2
```

Kotlin 예시:

```kotlin
fun smoothMetric(previous: Double, measured: Double): Double {
    return (previous * 0.8) + (measured * 0.2)
}
```

---

## 값 범위 제한

모든 Double metric은 저장 전 `0.0 ~ 1.0` 범위로 clamp한다.

```kotlin
fun clampScore(value: Double): Double {
    return value.coerceIn(0.0, 1.0)
}
```

---

## 급격한 변동 제한

한 번의 업데이트에서 너무 큰 폭으로 변하지 않도록 제한할 수 있다.

```text
maxDeltaPerUpdate = 0.15
```

예:

```kotlin
fun limitDelta(previous: Double, updated: Double): Double {
    val maxDelta = 0.15
    return updated.coerceIn(previous - maxDelta, previous + maxDelta)
}
```

---

## null 처리

측정값이 없거나 신뢰할 수 없는 경우 기존 값을 유지한다.

```text
measured == null
→ previous 유지
```

예:

```kotlin
fun updateNullableMetric(previous: Double, measured: Double?): Double {
    return measured?.let { smoothMetric(previous, clampScore(it)) } ?: previous
}
```

---

# Vocabulary Level 업데이트

`vocabularyLevel`은 Double이 아니라 CEFR enum이다.
한 세션 결과만으로 등급을 급격하게 바꾸지 않는다.

정책:

- CEFR 사전 또는 난이도 매핑으로 session-level 후보 등급을 계산한다.
- 기존 등급보다 2단계 이상 상승/하락시키지 않는다.
- 같은 방향의 관측이 여러 번 누적될 때 등급을 변경한다.
- MVP에서는 안정성을 위해 “한 번에 최대 1단계 변경”을 기본으로 한다.

예:

```text
previous = A2
measured = B2
→ updated candidate = B1
```

---

# External Metrics 재계산 정책

Internal Metrics 업데이트 후 External Metrics를 재계산한다.

```text
LanguageInternalMetricsVO 업데이트
→ LanguageExternalMetricsVO 재계산
→ LanguageStateVO updatedAt 갱신
```

권장 매핑:

| External Metric | 계산 기준 |
| --- | --- |
| `vocabularyLevel` | internal `vocabularyLevel` |
| `grammarAccuracy` | internal `grammarAccuracy` |
| `expressionRange` | lexical diversity + 누적 표현/어휘 집계 |
| `fluencyScore` | speechRate, pauseFrequency, avgUtteranceLength |
| `naturalnessScore` | spokenNaturalness, naturalExpressionUsage |

---

## 예시 계산

```kotlin
fun calculateFluencyScore(metrics: LanguageInternalMetricsVO): Double {
    val pauseScore = 1.0 - metrics.pauseFrequency
    return ((metrics.speechRate + pauseScore + metrics.avgUtteranceLength) / 3.0)
        .coerceIn(0.0, 1.0)
}

fun calculateNaturalnessScore(metrics: LanguageInternalMetricsVO): Double {
    return ((metrics.spokenNaturalness + metrics.naturalExpressionUsage) / 2.0)
        .coerceIn(0.0, 1.0)
}
```

---

# Dashboard Summary 갱신 정책

Language State 업데이트 후 Dashboard Summary의 성취율 delta를 갱신한다.

대상 필드:

- `grammarScoreDelta`
- `fluencyScoreDelta`
- `vocabularyScoreDelta`
- `naturalnessScoreDelta`

정책:

- 직전 Dashboard Summary 또는 직전 External Metrics와 비교한다.
- delta는 사용자에게 보여줄 수 있는 작은 정수 값으로 환산한다.
- Dashboard는 Language State 전체를 직접 계산하지 않는다.
- Dashboard Summary 갱신도 LS-005 정책에 따라 Local first + Firebase sync를 따른다.

---

# 저장 및 동기화 순서

```text
분석 payload 생성
→ Type A 계산
→ Type B 분석
→ 필요한 경우 Type C AI 분석
→ measured metrics 생성
→ 이동평균 적용
→ External Metrics 재계산
→ Local Language State 저장
→ GlobalLearningState 갱신
→ Dashboard Summary 갱신
→ Firebase sync 예약
```

Firebase sync 실패 시 Local 상태는 유지하고 pending sync로 재시도한다.
Local 저장 실패 시 Firebase write만 단독 수행하지 않는다.

---

# 중복 업데이트 방지

같은 세션 분석 결과가 여러 번 반영되면 Language State가 왜곡될 수 있다.

권장 필드:

```text
lastAnalyzedLanguage
lastAnalyzedAt
analysisEventId
lastCompressedAt
```

정책:

- 동일 `analysisEventId`는 한 번만 반영한다.
- 동일 언어의 같은 `recentFullContext` 분석 결과가 중복 반영되지 않도록 `analysisEventId` 또는 `lastCompressedAt`을 기록한다.
- MVP에서는 동일 `analysisEventId`의 중복 batch update를 무시하는 방식을 우선한다.
- Correction 재생성 기능이 생기면 명시적인 `forceReanalysis` 정책을 별도 정의한다.

---

# 상태 정책

## Loading

- Type A / B 계산은 짧은 Loading 또는 background 처리 가능하다.
- Type C AI 분석은 네트워크 지연이 있으므로 Correction 완료 UI와 분리할 수 있다.
- Dashboard는 기존 Summary를 유지하다가 업데이트 완료 후 갱신한다.

## Error

### Fatal

- `currentState`가 없고 초기 상태 생성도 실패
- `language`가 비어 있음
- Session language와 업데이트 대상 language가 불일치
- Local Language State 저장 실패
- schemaVersion 미지원

→ 업데이트 실패 처리 후 Error UI 또는 Snackbar를 표시한다.

### Transient

- Type C AI 분석 실패
- Firebase sync 실패
- 일부 metric 분석 실패
- Dashboard Summary 갱신 실패

→ 가능한 값만 반영하고, 실패한 값은 기존 값을 유지한다.

## Empty

- 분석 가능한 user turn이 충분하지 않으면 Language State를 업데이트하지 않는다.
- 신규 사용자라면 LS-001 초기값을 먼저 생성한다.

---

# Edge Cases

- `recentFullContext`가 비어 있음
- user turn이 너무 짧아 분석 불가
- STT confidence가 낮음
- 세션 언어와 선택 언어가 다름
- AI 분석 결과가 JSON 형식이 아님
- AI 분석 결과 값이 `0.0 ~ 1.0` 범위를 벗어남
- 일부 metric만 분석 성공
- 같은 recentFullContext가 중복 분석됨
- Flashcard review result가 중복 반영됨
- Local 저장 성공 후 Firebase sync 실패
- Firebase에는 최신 Language State가 있으나 Local은 오래됨
- schemaVersion 불일치
- 앱 종료 중 업데이트 미완료

---

# 테스트 시나리오

## Correction 완료 후 정상 업데이트

1. 사용자가 AI Chat을 종료한다.
2. Session Memory에 `recentFullContext`가 저장된다.
3. Correction 화면에서 교정이 완료된다.
4. Type A / B / C 분석이 수행된다.
5. 이동평균이 적용된다.
6. Language State가 Local에 저장된다.
7. Dashboard Summary가 갱신된다.
8. Firebase sync가 예약된다.

---

## 분석 가능한 turn 부족

1. `recentFullContext`에 user turn이 없거나 너무 짧다.
2. 분석 payload 생성에 실패한다.
3. Language State는 기존 값을 유지한다.
4. UI에는 non-blocking 안내를 표시한다.

---

## Type C AI 실패

1. Type A / B 분석은 성공한다.
2. Type C AI 호출이 실패한다.
3. `spokenNaturalness`, `naturalExpressionUsage`는 기존 값을 유지한다.
4. 나머지 계산 가능한 metric만 업데이트한다.
5. retry 가능 상태를 기록한다.

---

## 중복 업데이트 방지

1. 동일 `analysisEventId`에 대한 분석이 이미 반영되어 있다.
2. 같은 `analysisEventId`로 업데이트가 다시 요청된다.
3. Language State를 다시 변경하지 않는다.
4. 성공으로 간주하거나 중복 이벤트로 무시한다.

---

## 언어 불일치

1. Session Memory의 `language`는 `"en"`이다.
2. 현재 `selectedLearningLanguage`는 `"ja"`이다.
3. 업데이트 대상은 `"en"` Language State로 고정한다.
4. `"ja"` Language State는 변경하지 않는다.

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- Firebase AI / Generative AI
- DataStore
- Room
- Kotlin Flow / StateFlow
- Hilt

---

## Design(Figma)

해당 없음.

LS-006은 화면 UI가 아니라 Language State 업데이트 정책 정의 이슈이다.

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
