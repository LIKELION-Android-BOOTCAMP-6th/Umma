> MVP단계에서는 ANKI처럼 플래시카드를 사용자가 스스로 머릿속, 또는 혼잣말로 문장을 되뇌이고 5단계의 버튼을 클릭하는 방식으로 다음 학습 시점을 반영할 예정이야 (AI가 판단하게 하는 것은 추후 확장)전반적으로 대화세션과 사용자의 언어능력상태 데이터, 플래시카드 데이터, 통계 데이터가 공통으로 호출되거나 업데이트되는데, 전역으로 설정해야할지, 로컬과 firebase 어느쪽에 어떤식으로 저장해서 사용해야좋을지 판단해줘AI가 사용자의 언어능력상태를 수치화해서 반영하는 시점과 로직을 구상해줘.

구현 모델 이름은 `LangState`, `DashSummary`, `UserLangPref`, `GlobalLangState`를 기준으로 본다.
>

Firebase Live API 세션 lifecycle, activeSessionId, turn 확정, transcript 이벤트와 자막 표시 같은 대화 실행 경계는 `SYS-REALTIME-INFRA`와 `FLOW-AI-CHAT`에서 별도로 정의한다.

---

# 1. MVP 플래시카드 — Anki식 5단계 버튼

좋은 결정입니다. MVP 단계에서는 Anki의 SM-2 기반 구조를 단순화하여 사용하는 것이 가장 현실적입니다.

사용자는:

- 머릿속으로 문장을 떠올리거나
- 혼잣말로 말해본 뒤

직접 자신의 기억 정도를 5단계 버튼으로 평가합니다.

AI가 발화 품질을 자동 평가하는 방식은 추후 확장 범위로 둡니다.

---

## 5단계 버튼 → 다음 노출 간격 매핑

| 버튼 | 의미 | 다음 노출 |
| --- | --- | --- |
| 완전히 몰랐음 | Blackout | 당일 재노출 |
| 생각났지만 틀림 | Wrong | 1일 후 |
| 힘겹게 맞음 | Hard | 3일 후 |
| 맞음 | Good | 현재 interval × 2.5 |
| 완벽하게 맞음 | Easy | 현재 interval × 3.5 |

---

## Flashcard 핵심 필드

MVP에서는 각 카드가 아래 상태만 가지면 충분합니다.

```json
{
  "interval": 3,
  "ease_factor": 2.5,
  "next_review_at": 1730000000
}
```

---

## Flashcard 저장 구조

Flashcard는 언어별(Language Scoped)로 저장됩니다.

예:

```
English Flashcard
Japanese Flashcard
Spanish Flashcard
```

는 각각 독립적으로 관리됩니다.

---

## Flashcard 전체 구조 예시

```json
{
  "id":"card_001",

  "language":"en",

  "nativeText":"나는 여행을 좋아해",

  "correctedText":"I like traveling",

  "hint":"좋아하다 + 여행",

  "ttsAudioUrl":"...",

  "sourceSessionKey":"en",
  "sourceTurnIds":[12, 14],

  "interval":3,

  "ease_factor":2.5,

  "next_review_at":1730000000
}
```

---

# 2. 데이터 저장 전략

## 핵심 원칙

### 읽기 속도가 중요한 데이터

→ Local Cache 우선

### 정확성과 동기화가 중요한 데이터

→ Firebase 원본 유지

---

# 데이터 구조 개요

Umma의 학습 데이터는 크게:

1. 원본 데이터
2. 언어별 Summary 데이터

두 계층으로 구성됩니다.

---

## 1) 원본 데이터

실제 transcript buffer, Flashcard Memory, Session Memory, Statistics Memory 기록.

예:

- Session Memory의 `recentFullContext`
- Flashcard Memory
- LangState 원본

---

## 2) Summary 데이터

Dashboard와 Statistics에서 빠르게 출력하기 위한 집계 데이터.

예:

```
영어 대화 12분
일본어 Flashcard 14개
문법 정확도 +4
```

---

# 데이터 저장 구조

```
┌────────────────────────────────────────────────────────┐
│                    데이터 저장 구조                     │
├──────────────────┬──────────────────┬─────────────────┤
│   메모리 전용     │ 로컬 + Firebase  │ Firebase 원본   │
├──────────────────┼──────────────────┼─────────────────┤
│ 진행 중 transcript │ LangState        │ Session Memory  │
│ 현재 입력 UI 상태   │ DashSummary      │ recentFullContext │
│ 현재 AI 응답 상태   │ Flashcard Memory  │                 │
│                  │ Statistics Memory │                 │
│                  │ UserLangPref      │                 │
└──────────────────┴──────────────────┴─────────────────┘
```

---

# Firebase 구조

```
users/{uid}
├── language_states/
│    ├── en
│    ├── ja
│    └── es
│
├── dashboard_summaries/
│    ├── en
│    ├── ja
│    └── es
│
├── sessions/
│    ├── en
│    └── ja
│
└── flashcards/
     ├── card_001
     └── card_002
```

---

# 구체적인 저장 전략

---

## ① LangState

### 저장 방식

```
Local persist + Firebase sync
```

---

### 역할

사용자의 장기 언어 능력 상태 저장.

예:

- 문법 정확도
- 유창성
- 어휘 수준

---

### 동작 방식

- 앱 시작 시 preload
- Local Cache 우선 조회
- Firebase background sync
- 교정 또는 학습 분석 완료 시 batch update

---

### 이유

대화 중 매 턴마다 Firebase read/write를 수행하면:

- 비용 증가
- 레이턴시 증가
- UX 저하

문제가 발생함.

---

## ② DashSummary

### 저장 방식

```
Local cache + Firebase sync
```

---

### 역할

Dashboard 빠른 렌더링용 요약 데이터.

예:

```
영어 대화 12분
일본어 Flashcard 14개
```

---

### Dashboard preload 흐름

```
앱 실행
→ DashSummary Local preload
→ 즉시 Dashboard 렌더링
→ Firebase background sync
→ 변경사항 존재 시 UI 갱신
```

---

### 핵심 원칙

Dashboard는:

- recentFullContext 전체
- LangState 전체

를 직접 계산하지 않는다.

대신:

```
DashSummary
```

만 사용하여 빠르게 렌더링한다.

---

## ③ Flashcard Memory

### 저장 방식

```
Local persist + Firebase sync
```

---

### 역할

SRS 반복 학습 데이터 저장.

---

### 동작 방식

- 로컬에 전체 카드 유지
- 학습 결과 즉시 로컬 반영
- Firebase는 비동기 sync

---

### 이유

Flashcard 학습은 반응 속도가 매우 중요함.

Firebase 응답을 기다리면 UX가 끊김.

---

## ④ Session Memory

### 저장 방식

```
대화 중 메모리/로컬 buffer 우선
→ 확정 turn 단위로 Session Memory 반영
→ Firebase batch sync
```

---

### 역할

현재 선택 언어의 대화 기억과 교정 전 full context를 관리한다.

Session Memory는 대화 1회마다 새 문서를 생성하지 않고,
사용자와 학습 언어 기준으로 재사용한다.

예:

```text
users/{uid}/sessions/en
users/{uid}/sessions/ja
```

MVP에서는 persona를 고려하지 않는다.

---

### 동작 방식

대화 중:

```
Firebase Live API streaming chunk 수신
→ 발화 또는 응답 완료 시 turn 확정
→ recentFullContext에 append
→ 최대 N턴 초과 시 오래된 turn 제거
```

Dashboard 반영 시:

```
recentConversationMinutes 업데이트
→ recentConversationTopic 업데이트
→ correctionAvailable 업데이트
→ Dashboard Summary 업데이트
```

교정 및 Flashcard 저장 완료 시:

```
recentFullContext 분석
→ topicSummary / topicKeySentences로 압축
→ recentFullContext 초기화
→ correctionAvailable = false
→ Language State 업데이트
→ Dashboard Summary 업데이트
```

---

### recentFullContext 저장 정책

`recentFullContext`는 전체 transcript 문자열이 아니라 turn 리스트로 저장한다.

최소 turn 구조:

```json
{
  "turnId": 12,
  "role": "user",
  "text": "I went to the museum yesterday.",
  "createdAt": "timestamp"
}
```

권장 필드:

```json
{
  "turnId": 12,
  "role": "user",
  "text": "I went to the museum yesterday.",
  "createdAt": "timestamp",
  "durationMs": 3200
}
```

저장 원칙:

- `role`은 `user` 또는 `assistant`를 사용한다.
- streaming 중간 조각은 영구 저장하지 않는다.
- 하나의 발화 또는 응답이 완료된 뒤 turn으로 확정한다.
- 교정 후보는 기본적으로 `role = user` turn에서 추출한다.
- 최대 N턴까지만 유지한다. MVP 문서에서는 예시로 100턴을 사용할 수 있으나, 실제 값은 구현 중 조정 가능하다.

---

### 교정 요청 비용 최적화 정책

`recentFullContext`는 저장용 원본 buffer이며,
AI 교정 요청 시 전체를 그대로 전송하지 않는다.

교정 요청 시에는 다음 순서로 payload를 재구성한다.

```text
recentFullContext 조회
→ user turn 추출
→ 짧은 발화 / 중복 발화 / 감탄사성 응답 제외
→ 필요한 주변 assistant turn 일부만 포함
→ LangState snapshot 생성
→ AI Correction 요청
```

원칙:

- 대화 중에는 교정 AI 호출을 수행하지 않는다.
- 교정 화면 진입 또는 사용자의 명시적 요청 시에만 AI 교정 요청을 수행한다.
- LangState 전체가 아니라 교정에 필요한 요약 snapshot만 전달한다.
- 후보 문장 수와 turn 수에 상한을 둔다.

---

### 이유

실시간 Firebase write는 비용과 레이턴시가 매우 큼.

---

## ⑤ Statistics

### 저장 방식

```
Firebase fetch + Local cache
```

---

### 역할

통계 그래프 및 성장 데이터 출력.

---

### 특징

- 읽기 빈도 낮음
- Local TTL 캐시 사용 가능
- Dashboard보다 preload 우선순위 낮음

---

# 전역 상태 관리 구조

```
GlobalLangState
├── langStates
│    ├── en
│    ├── ja
│    └── es
│
├── dashSummaries
│    ├── en
│    ├── ja
│    └── es
│
├── statisticsSummaries
│    ├── en
│    ├── ja
│    └── es
│
└── flashcardSummaries
     ├── en
     ├── ja
     └── es
```

---

# 3. LangState 업데이트 — 시점과 로직

## 업데이트 시점

업데이트는 단 한 곳에서만 수행합니다.

```text
교정 또는 학습 분석 완료 직후
```

---

## 핵심 이유

대화 중 실시간 분석보다:

- 정확도 높음
- 비용 절감
- 구현 안정성 높음

---

# 업데이트 흐름

```
사용자가 대화 종료
        ↓
transcript + 현재 LangState 전달
        ↓
AI가 transcript 분석
        ↓
delta 값 반환
        ↓
가중 이동평균 적용
        ↓
LangState 갱신
        ↓
DashSummary 재계산
        ↓
Firebase sync + Local cache 갱신
```

---

# 핵심 로직 — 이동 평균

한 번의 세션으로 수치가 급변하지 않도록 이동 평균 방식 사용.

```
새 값 = 기존 값 × 0.8 + 새 측정값 × 0.2
```

---

# AI 분석 전략

모든 분석을 AI로 수행하지 않습니다.

비용 효율을 위해:

- 코드 계산
- 규칙 기반
- AI 분석

을 분리합니다.

---

## Type A — 코드 계산

비용 거의 없음.

예:

- avg_sentence_length
- duration_minutes
- turn_count

---

## Type B — 규칙 기반 분석

비용 낮음.

예:

- repetition_rate
- article_accuracy
- lexical_diversity

---

## Type C — AI 분석

비용 높음.

정말 AI 판단이 필요한 항목만 수행.

예:

- spoken_naturalness
- contextual_response_quality
- expression_confidence

---

# AI 분석 프롬프트 구조

```
[시스템]

다음은 사용자의 현재 Language State와 transcript입니다.

각 지표를 분석하고,
변화가 없는 값은 null 반환하세요.

JSON만 반환하세요.
```

---

# AI 반환 예시

```json
{
  "grammar": {
    "article_accuracy": 0.65,
    "tense_consistency": null
  },

  "fluency": {
    "avg_sentence_length": 0.58,
    "pause_frequency": 0.42
  }
}
```

---

# MVP 우선 구현 추천 지표

처음부터 모든 지표를 구현하지 않습니다.

실제로 transcript 기반으로 안정적으로 측정 가능한 지표부터 우선 구현합니다.

| 우선순위 | 지표 | 측정 방식 |
| --- | --- | --- |
| 1순위 | avg_sentence_length | 토큰 수 계산 |
| 1순위 | vocabulary_level | CEFR 매핑 |
| 1순위 | repetition_rate | 단어 빈도 분석 |
| 2순위 | sentence_complexity | 문장 패턴 분석 |
| 2순위 | response_latency | 발화 간 시간 측정 |
| 3순위 | natural_expression_usage | AI 분석 |
| 3순위 | expression_confidence | AI 분석 |

---

# 최종 요약

- Flashcard:
    
    SM-2 기반 5단계 버튼 사용
    
- 저장 전략:
    
    Local Cache 우선 + Firebase sync
    
- Dashboard:
    
    언어별 Summary 기반 preload 렌더링
    
- Language State:
    
    교정 또는 학습 분석 완료 후 batch 업데이트
    
- 분석 전략:
    
    코드 계산 / 규칙 기반 / AI 분석 분리
    
- 핵심 구조:
    
    모든 학습 데이터는 Language Scoped 구조로 저장
