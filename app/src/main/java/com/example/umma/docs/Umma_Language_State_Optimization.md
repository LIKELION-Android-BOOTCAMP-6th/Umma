> Language State가 너무 추상적이어서 AI가 모든 지표를 판단하고 반영하는데에 너무 많은 비용이 들거나 결과가 일관적이지 않게 될 문제는 없을까?? 만약 그렇다면 해결방법은?
> 

# 문제 진단

Language State를 단순히:

```
AI가 모든 지표를 매번 분석하는 구조
```

로 설계하면,

비용 / 일관성 / 확장성 문제가 매우 커집니다.

따라서 Umma에서는:

```
모든 지표를 AI가 판단하지 않는다.
```

를 핵심 원칙으로 둡니다.

---

# 1. 비용 문제

교정 또는 학습 분석 시점마다:

- recentFullContext 전체
- Language State 전체
- 30개 이상의 지표

를 AI에 전송하면 토큰 비용이 급격히 증가합니다.

---

## 기존 구조의 문제

```
transcript 3분 대화 ≈ 800~1,200 토큰
Language State 전체 ≈ 400 토큰
프롬프트 ≈ 200 토큰

────────────────────────

1회 분석 ≈ 1,500~1,800 토큰
```

---

## 예상 문제

```
하루 3세션
× 30일
= 약 90회 분석
```

유저가 증가하면:

- 월 수십억 토큰
- API 비용 급증
- 응답 속도 저하

문제가 발생 가능.

---

# 2. 일관성 문제

AI는 본질적으로 확률적 모델입니다.

따라서:

```
natural_expression_usage = 0.72
```

같은 수치를 매번 동일하게 반환하지 않습니다.

즉:

- 같은 transcript
- 같은 프롬프트

라도:

```
0.68
0.71
0.74
```

처럼 조금씩 흔들릴 수 있습니다.

---

# 3. 구조적 문제

초기 Language State 설계에서는:

```
거의 모든 지표를 AI가 추론
```

하는 구조에 가까웠습니다.

하지만 실제로는:

- 계산 가능한 값
- 규칙 기반 판단 가능한 값

도 많습니다.

AI를 사용하지 않아도 되는 영역까지 AI에 맡기면:

- 비용 증가
- 일관성 감소
- 디버깅 어려움

문제가 발생합니다.

---

# 해결 방향

Umma에서는 Language State 지표를 아래 3가지 유형으로 분리합니다.

```
┌──────────────────────────────────────────────┐
│          Language State 처리 전략            │
├──────────────┬──────────────┬───────────────┤
│ Type A       │ Type B       │ Type C        │
│ 코드 계산     │ 규칙 기반     │ AI 분석        │
│ 비용 거의 0   │ 비용 거의 0   │ 비용 발생      │
└──────────────┴──────────────┴───────────────┘
```

---

# Type A — 코드 계산

AI 없이 코드만으로 계산 가능한 지표.

---

## 특징

- 비용 거의 없음
- 일관성 매우 높음
- 실시간 계산 가능

---

## 예시

| 지표 | 계산 방식 |
| --- | --- |
| avg_sentence_length | 평균 토큰 수 |
| repetition_rate | 단어 반복률 계산 |
| lexical_diversity | TTR 계산 |
| response_latency | 발화 시간 측정 |
| pause_frequency | 침묵 구간 감지 |
| review_retention | Flashcard 정답률 |
| error_recurrence | 교정 패턴 재등장 여부 |

---

# Type B — 규칙 기반 분석

사전/문법 규칙/NLP 패턴 기반으로 분석 가능한 지표.

---

## 특징

- AI 불필요
- 비용 거의 없음
- 안정적
- explainable 가능

---

## 예시

| 지표 | 분석 방식 |
| --- | --- |
| vocabulary_level | CEFR 단어 사전 매핑 |
| sentence_complexity | 접속사/관계절 패턴 |
| article_accuracy | 관사 문법 규칙 |
| tense_consistency | 동사 시제 분석 |
| plural_accuracy | 복수형 규칙 검사 |
| word_order_stability | SVO 패턴 감지 |

---

## 사용 가능 기술

### 경량

```
compromise.js
```

---

### 서버 기반 고급 분석

```
spaCy
```

---

## CEFR 사전

오픈소스 CEFR vocabulary 데이터 사용 가능.

예:

- EVP
- COCA 기반 데이터셋

---

# Type C — AI 분석

정말 AI의 맥락 이해가 필요한 항목만 AI 분석 수행.

---

## 특징

- 비용 발생
- 확률적 결과
- 맥락 이해 가능

---

## Type C 최소화 전략

Umma MVP에서는 Type C를 최소 4개만 사용.

---

## Type C 지표

| 지표 | AI가 필요한 이유 |
| --- | --- |
| natural_expression_usage | 자연스러운 표현 판단 |
| spoken_naturalness | 교과서체 vs 구어체 |
| contextual_response_quality | 대화 흐름 적절성 |
| expression_confidence | 위축/적극성 판단 |

---

# 핵심 전략

```
Type A/B는 코드 기반으로 처리
Type C만 AI 호출
```

---

# 비용 최적화 구조

## 기존 구조 (문제 구조)

```
교정 또는 학습 분석 시점
→ recentFullContext 전체 전송
→ AI가 30개 지표 전부 판단
→ 고비용 구조
```

---

## 개선 구조

```
교정 또는 학습 분석 시점
→ Type A/B 즉시 계산
→ Type C만 AI 호출
→ user turn 중심 correction/analysis payload만 전송
```

---

# Type C AI 호출 정책

## 전송 데이터 최소화

AI에는:

```
마지막 20턴
```

정도만 전달.

---

## 이유

언어 능력 판단에는:

- 최근 표현 패턴
- 최근 대화 흐름

이 더 중요함.

전체 transcript는 불필요.

---

# Type C 전용 프롬프트 구조

```
다음 transcript를 기반으로 아래 항목만 평가하세요.

- natural_expression_usage
- spoken_naturalness
- contextual_response_quality
- expression_confidence

0.0~1.0으로 반환하고,
판단 근거가 부족하면 null 반환.

JSON만 반환하세요.
```

---

# AI 반환 예시

```json
{
  "natural_expression_usage": 0.72,

  "spoken_naturalness": 0.68,

  "contextual_response_quality": 0.81,

  "expression_confidence": null
}
```

---

# null 정책

null은:

```
이번 세션에서 판단 근거 부족
```

의 의미.

즉:

```
기존 값 유지
```

처리.

---

# 업데이트 빈도 최적화

Type C조차 매 세션마다 호출하지 않습니다.

---

## 최적화 정책

| 유형 | 업데이트 빈도 |
| --- | --- |
| Type A | 매 세션 |
| Type B | 매 세션 |
| Type C | 3세션마다 또는 누적 발화량 기준 |

---

# 이유

다음 요소들은:

- 자연스러움
- 자신감
- 대화 맥락 적응

이 단기간에 크게 변하지 않기 때문.

---

# 결과

AI 비용이:

```
기존 대비 약 70~90% 절감 가능
```

---

# Language State 업데이트 구조

## 최종 흐름

```
교정 또는 학습 분석 시점
        ↓
Session Memory recentFullContext 조회
        ↓
Type A 계산
        ↓
Type B 분석
        ↓
(필요 시) Type C AI 분석
        ↓
Language State 업데이트
        ↓
Dashboard Summary 재계산
        ↓
Firebase sync
        ↓
Local cache 갱신
```

---

# 이동 평균 정책

수치 급변 방지를 위해 이동 평균 사용.

```
새 값 = 기존 값 × 0.8 + 새 측정값 × 0.2
```

---

# 왜 이동 평균을 사용하는가?

단일 세션으로:

- 갑자기 유창성이 폭등하거나
- 갑자기 문법 점수가 폭락하는

현상을 방지하기 위함.

---

# MVP 우선 구현 추천 지표

처음부터 모든 지표를 구현하지 않습니다.

실제로 transcript 기반으로 안정적으로 측정 가능한 것부터 우선 구현.

---

## 1순위

| 지표 | 방식 |
| --- | --- |
| avg_sentence_length | 코드 계산 |
| vocabulary_level | CEFR 매핑 |
| repetition_rate | 빈도 분석 |

---

## 2순위

| 지표 | 방식 |
| --- | --- |
| sentence_complexity | 규칙 기반 |
| response_latency | 시간 측정 |

---

## 3순위

| 지표 | 방식 |
| --- | --- |
| natural_expression_usage | AI 분석 |
| expression_confidence | AI 분석 |

---

# 최종 요약

- 모든 지표를 AI가 분석하지 않는다.
- Language State를:
    
    Type A / B / C
    
    로 분리한다.
    
- 대부분의 지표는:
    
    코드 계산 + 규칙 기반
    
    으로 처리한다.
    
- AI는:
    
    자연스러움 / 맥락 / 자신감
    
    같은 고차원 판단만 수행한다.
    
- Type C는:
    
    마지막 20턴만 사용
    
    하여 비용 최소화.
    
- AI 호출 빈도도:
    
    3세션마다 1회
    
    수준으로 제한 가능.
    
- Dashboard와 Statistics는:
    
    Language State 전체를 매번 계산하지 않고
    
    Summary 기반으로 preload 렌더링한다.
