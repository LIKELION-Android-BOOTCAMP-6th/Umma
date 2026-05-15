# Umma — 서비스 구조 및 핵심 기능 정리

## 서비스 한 줄 정의

Umma는 AI와 음성 대화를 통해 외국어를 실제로 사용하게 만들고,

교정·플래시카드·반복학습·언어능력 분석을 통해 사용자의 언어 성장을 장기적으로 지원하는 AI 기반 언어 학습 서비스이다.

구현 모델 이름은 `LangState`, `DashSummary`, `UserLangPref`, `GlobalLangState`로 맞춘다.

---

# 1. 핵심 사용자 흐름

```
로그인
→ AI와 음성 대화
→ 교정 확인
→ 플래시카드 저장
→ SRS 반복 학습
→ 언어 성장 통계 확인
→ 다시 AI 대화
```

---

# 2. 핵심 화면 구성

---

# 2-1. Intro / 인증 화면

## 역할

- 서비스 소개
- Google 계정 기반 로그인/회원가입
- 사용자 최초 진입 처리

---

## 핵심 기능

### Intro 화면

- 서비스 소개
- 핵심 가치 설명
- Google 로그인 진입

---

### Google 인증 화면

- Google OAuth 로그인
- 자동 로그인 유지
- 세션 복구

---

## 사용하는 데이터

| 데이터 | 사용 목적 |
| --- | --- |
| User Profile | 로그인 사용자 식별 |
| LangState | 신규 사용자 여부 판단 |

---

# 2-2. Dashboard 화면

## 역할

사용자의 최근 학습 활동과 현재 학습 상태를 한눈에 보여주는 홈 화면.

---

## 핵심 기능

### 최근 대화 요약 카드

예:

- 최근 3분 영어 대화
- 최근 2분 일본어 대화

---

### 학습 진행 카드

예:

- 반복학습 대기 카드 14개
- 최근 학습 streak
- 최근 교정 수

---

### 성장 통계 카드

예:

- 최근 어휘 증가
- 문법 정확도 상승
- 자연스러운 표현 증가

---

### 최근 학습 추천

예:

- 복습 추천 카드
- 최근 실수 표현

---

## 사용하는 데이터

| 데이터 | 사용 방식 |
| --- | --- |
| Session Memory | 최근 대화 정보 |
| Flashcard Memory | 반복학습 필요 카드 수 |
| Statistics Memory | 성장 통계 카드 |
| LangState | 현재 언어 수준 표시 |

---

# 2-3. AI Chat 화면

## 역할

사용자가 AI와 실제 음성 대화를 진행하는 핵심 화면.

---

## 핵심 기능

### 음성 대화

- 버튼 클릭 후 음성 입력
- 중앙 이미지를 중심으로 입력 강도와 AI 음성 출력 상태를 보여주는 비주얼 피드백
- Firebase AI Logic Live API 기반 AI 응답
- Firebase Live 세션 라이프사이클과 turn 확정 경계는 `SYS-REALTIME-INFRA`와 `FLOW-AI-CHAT`에서 별도로 정의한다.

---

### 사용자 수준 적응형 대화

AI는:

- LangState
- 최근 Session Memory

를 참고하여:

- 단어 난이도 조절
- 문장 구조 복잡도 조절
- 대화 리드
- 자연스러운 scaffolding

형태로 대화.

---

### 자막 출력

- 사용자 마지막 발화
- AI 마지막 응답

자막 표시.
자막은 기본적으로 숨기고, On 상태에서만 마지막 확정 턴을 보여준다.
입력 강도와 AI 재생 상태는 자막이 아니라 중앙 비주얼이 담당한다.

---

### 세션 메모리 저장

AI Chat은 대화할 때마다 새로운 세션 문서를 생성하지 않는다.

현재 선택 언어의 Session Memory 문서를 재사용하며,
Firebase Live API에서 확정된 사용자 발화와 AI 응답을 turn 단위로 `recentFullContext`에 추가한다.
실제 세션 복원, activeSessionId 관리, 재연결 정책은 `SYS-REALTIME-INFRA`를 따른다.

대화 중 저장 대상:

- user turn
- assistant turn
- 대화 시간
- 최근 대화 주제 후보

교정 및 Flashcard 저장 이후에는 `recentFullContext`를 압축하여 대화 기억 데이터로 남기고,
원문 full context는 초기화한다.

---

## 사용하는 데이터

| 데이터 | 사용 방식 |
| --- | --- |
| LangState | 사용자 수준 적응 |
| Session Memory | 최근 대화 맥락 유지 |
| Statistics Memory | 대화 통계 반영 |

---

# 2-4. 교정 및 플래시카드 저장 화면

## 역할

대화 내용을 분석하여 교정하고,

사용자가 학습할 표현을 Flashcard로 저장하는 화면.

---

## 핵심 기능

### Full Context 교정

- Session Memory의 `recentFullContext` 기반 교정
- 사용자 발화 turn을 중심으로 교정 후보 추출
- 사용자 수준(LangState)을 고려한 교정 제공
- 교정 요청 시 full context 전체를 그대로 AI에 전달하지 않고, 비용 최적화된 correction payload로 재구성한다.
- 교정 및 Flashcard 저장 이후에는 `recentFullContext`를 압축하고 원문 buffer는 비워진다.
세부 교정 입력 경계는 `SYS-REALTIME-INFRA`와 `FLOW-AI-CHAT`의 turn 확정 규칙을 함께 따른다.

---

### 교정 포인트 강조

예:

- 문법
- 자연스러운 표현
- 더 좋은 어휘

---

### Flashcard 저장

사용자가:

- 교정 문장 선택
- 플래시카드 저장

가능.

---

## 사용하는 데이터

| 데이터 | 사용 방식 |
| --- | --- |
| Session Memory | recentFullContext 조회 및 교정 후보 추출 |
| LangState | 사용자 수준 기반 교정 |
| Flashcard Memory | 선택 문장 저장 |

---

# 2-5. Flashcard 반복학습 화면

## 역할

SRS 기반 반복 학습을 통해 사용자의 표현 기억을 장기 강화하는 화면.

---

## 핵심 기능

### 카드 뒤집기 학습

앞면:

- 모국어 문장

뒷면:

- 교정된 외국어 문장
- 짧은 교정 설명
- 발음 재생

## 발음 재생 정책

MVP에서는 Android `TextToSpeech`로 발음 재생을 구현한다.
추후에는 더 자연스러운 음성 품질이 필요해질 때 클라우드 TTS로 교체할 수 있도록 상위 구조만 열어둔다.

---

### Hint 제공

- 일부 표현 힌트
- 핵심 단어 힌트

---

### SRS 반복 노출

학습 결과에 따라:

- 다음 복습 시간
- 난이도

조정.

---

## 사용하는 데이터

| 데이터 | 사용 방식 |
| --- | --- |
| Flashcard Memory | 카드 조회 및 학습 |
| Statistics Memory | 학습률 통계 반영 |
| LangState | 학습 성과 반영 |

---

# 2-6. 통계 화면

## 역할

사용자의 언어 성장 과정을 시각적으로 보여주는 화면.

---

## 핵심 기능

### 언어 성장 그래프

예:

- 어휘 수준 변화
- 문법 정확도 변화
- 회화 자연스러움 변화

---

### 학습 통계 카드

예:

- 최근 학습 시간
- 반복학습 완료 수
- 실수 감소율

---

### 영역별 능력 분석

예:

- Grammar
- Vocabulary
- Fluency
- Conversation

---

## 사용하는 데이터

| 데이터 | 사용 방식 |
| --- | --- |
| LangState | 능력 변화 분석 |
| Statistics Memory | 그래프 및 카드 표시 |
| Flashcard Memory | 학습 통계 계산 |

---

# 3. 핵심 저장 데이터 구조

---

# 3-1. 대화 세션 메모리 (Session Memory)

## 역할

최근 대화 맥락 유지 및 대화 연속성 제공.

Session Memory는 대화 1회마다 새로 생성되는 문서가 아니라,
사용자와 학습 언어 기준으로 재사용되는 대화 기억 문서이다.

예:

```text
users/{uid}/sessions/en
users/{uid}/sessions/ja
```

MVP에서는 persona를 고려하지 않는다.

---

## 저장 데이터

### recentFullContext

- 교정 및 Flashcard 저장 전까지 유지되는 원문 대화 buffer
- 전체 transcript 문자열이 아니라 turn 단위 리스트로 저장
- Firebase Live API에서 streaming 조각을 그대로 저장하지 않고, 하나의 사용자 발화 또는 AI 응답이 완료되었을 때 turn으로 확정 저장
- 최대 N턴까지만 유지한다. MVP 문서에서는 예시로 100턴을 사용하되, 실제 값은 구현 시 조정 가능하다.

예:

```json
[
  {
    "turnId": 1,
    "role": "user",
    "text": "I want to travel to Japan next month.",
    "createdAt": "timestamp"
  },
  {
    "turnId": 2,
    "role": "assistant",
    "text": "That sounds exciting. Which city do you want to visit?",
    "createdAt": "timestamp"
  }
]
```

`recentFullContext`는 저장용 원본 buffer이며,
AI 교정 요청 시 전체를 그대로 전송하지 않는다.

---

### recent_topics

- 최근 5개 대화 주제

예:

- coffee
- travel
- movie

---

### topic_summary

- 대화 주제별 핵심요약 문장 5개씩
- 교정 및 Flashcard 저장 이후 `recentFullContext`를 압축하여 갱신
- 다음 AI Chat에서 장기 대화 기억으로 사용

---

### topic_key_sentences

- 주제별로 사용자가 연습한 핵심 문장 또는 표현
- Flashcard로 저장된 문장과 별도로, 다음 대화 맥락을 돕기 위한 압축 기억으로 사용

---

### correction_available

- `recentFullContext`에 교정 가능한 사용자 발화가 존재하는지 나타내는 상태
- 교정 및 Flashcard 저장 이후 `false`로 갱신

---

## 사용 화면

| 화면 | 사용 목적 |
| --- | --- |
| AI Chat | 압축 기억과 최근 원문 맥락을 참고하여 대화 연속성 유지 |
| 교정 화면 | `recentFullContext`에서 교정 후보 추출 |
| Dashboard | 교정받을 대화가 어느정도 있음을 안내 |

---

## 생명주기

```text
AI Chat 진입
→ selectedLearningLanguage 기준 Session Memory 조회
→ topic_summary / topic_key_sentences / recentFullContext를 참고하여 대화
→ Firebase Live API에서 확정된 발화와 응답을 turn으로 저장
→ Dashboard Summary 업데이트
→ 사용자가 교정 및 Flashcard 저장
→ recentFullContext를 topic_summary / topic_key_sentences로 압축
→ recentFullContext 초기화
→ correction_available = false
```

---

## 비용 최적화 원칙

- 대화 중에는 교정 AI 분석을 수행하지 않는다.
- 교정 화면 진입 또는 사용자의 명시적 요청 시에만 correction payload를 생성한다.
- correction payload는 사용자 발화 turn 중심으로 구성한다.
- assistant turn은 문맥상 필요한 일부만 포함한다.
- LangState 전체가 아니라 교정에 필요한 snapshot만 포함한다.
- 의미 없는 짧은 발화, 중복 발화, 감탄사성 응답은 AI 요청 전에 제외한다.

---

# 3-2. 사용자 언어능력 상태 메모리 (LangState)

## 역할

사용자의 장기 언어 능력 상태를 압축 저장하는 핵심 메모리.

---

## 주요 레이어

### Grammar Layer - 문법 정확도 및 문장 구조 능력

1. article_accuracy
: 관사 사용 정확도 (a, an, the 등)
2. preposition_accuracy
: 전치사 사용 정확도 (in, on, at, to 등)
3. tense_consistency
: 시제 사용 안정성 (과거/현재/미래 시제를 자연스럽게 유지하는 능력)
4. plural_accuracy
: 복수형 처리 정확도 (book/books, child/children 등)
5. word_order_stability
: 영어 어순 안정성 (자연스러운 단어 순서 구성 능력)
6. sentence_complexity
: 복합문 구성 능력 (관계절, 접속절, 긴 문장 연결 등)

---

### Vocabulary Layer - 어휘 수준 및 표현 다양성

1. lexical_diversity
: 어휘 다양성 (같은 단어 반복 없이 다양한 표현을 사용하는 정도)
2. vocabulary_level
: 사용 가능한 어휘 수준 (CEFR 기반 A1~C2 등급 추정)
3. advanced_expression_ratio
: 고급 표현 사용 비율 (단순 단어 대신 자연스럽고 고급 표현을 사용하는 정도)
4. repetition_rate
: 반복 표현 사용률 (같은 단어나 표현을 반복하는 정도)
5. natural_expression_usage
: 자연스러운 표현 사용 능력 (관용 표현, 원어민다운 표현 활용 정도)

---

### Fluency Layer - 유창성 및 응답 속도

1. avg_sentence_length
: 평균 문장 길이 (짧은 단문 위주인지, 긴 문장을 구성하는지)
2. response_latency
: 응답 속도 (질문 후 답변까지 걸리는 시간)
3. self_correction_rate
: 자기 수정 빈도 (말하다가 스스로 문장을 수정하는 빈도)
4. pause_frequency
: 머뭇거림 빈도 (um, uh, 긴 침묵 등)
5. response_completeness
: 응답 완성도 (맥락에 맞게 충분히 설명하고 표현하는 정도)

---

### Conversation Layer - 질문 생성 및 대화 유지 능력

1. question_generation
: 질문 생성 능력 (상대에게 자연스럽게 질문하는 능력)
2. topic_expansion
: 주제 확장 능력 (대화를 짧게 끝내지 않고 발전시키는 능력)
3. interaction_balance
: 상호작용 균형 (일방적이지 않고 자연스럽게 대화를 주고받는 정도)
4. contextual_response_quality
: 맥락 반응 품질 (상대의 말에 자연스럽고 적절하게 반응하는 능력)
5. conversation_initiation
: 대화 시작 능력 (먼저 화제를 꺼내고 대화를 시작하는 능력)

---

### Comprehension Layer - 듣기 및 이해 능력

1. listening_comprehension
: 듣기 이해력 (상대의 음성/문장을 이해하는 능력)
2. input_complexity_tolerance
: 처리 가능한 입력 난이도 (어느 수준의 영어까지 이해 가능한지)

---

### Confidence Layer - 표현 자신감 및 표현 시도 성향

1. expression_confidence
: 표현 자신감 (실수를 두려워하지 않고 표현하려는 정도)
2. complexity_hesitation
: 복잡한 문장에서의 위축 정도 (어려운 문장이 나오면 표현이 급격히 줄어드는 정도)
3. expression_risk_taking
: 새로운 표현 시도 성향 (익숙하지 않은 표현도 적극적으로 사용하려는 정도)

---

### Register Layer - 상황별 말투 적응 능력

1. casual_adaptability
: 구어체 / 친근한 말투 적응 능력
2. formal_adaptability
: 격식체 / 공식적인 말투 적응 능력
3. business_adaptability
: 비즈니스 상황에서의 어투 적응 능력
4. written_communication
: 문어체 / 글쓰기 표현 능력
5. spoken_naturalness
: 자연스러운 회화 말투 수준 (번역투·교과서체가 아닌 정도)
6. tone_adaptability
: 상황에 따라 적절한 어투로 전환하는 능력

---

### Learning Layer - 복습 유지율 및 교정 반영 속도

1. review_retention
: 복습 기억 유지율 (학습한 표현을 장기적으로 기억하는 정도)
2. error_recurrence
: 실수 재발률 (교정받은 실수를 반복하는 정도)
3. correction_adoption_speed
: 교정 반영 속도 (교정된 표현을 실제 대화에 빠르게 적용하는 정도)

---

## 사용 화면

| 화면 | 사용 목적 |
| --- | --- |
| AI Chat | 사용자 수준 적응 |
| 교정 화면 | 수준 기반 교정 |
| 통계 화면 | 성장 시각화 |
| Dashboard | 성장 요약 카드 |

---

# 3-3. 플래시카드 메모리 (Flashcard Memory)

## 역할

반복 학습을 위한 표현 저장.

---

## 저장 데이터

### front_text

모국어 문장

---

### back_text

교정된 외국어 문장

---

### pronunciation_uri

발음 음성 URI

---

### hint

문장 회상 힌트

---

### next_review_at

다음 반복 학습 시간

---

## 사용 화면

| 화면 | 사용 목적 |
| --- | --- |
| Flashcard 학습 | 반복 학습 |
| Dashboard | 복습 카드 수 표시 |
| 통계 화면 | 학습량 분석 |

---

# 3-4. 통계 메모리 (Statistics Memory)

## 역할

사용자 성장 데이터를 통계화하여 저장.

---

## 저장 데이터 예시

- vocabulary_growth
- grammar_score_change
- fluency_change
- review_completion_rate
- weekly_learning_time

---

## 사용 화면

| 화면 | 사용 목적 |
| --- | --- |
| Dashboard | 요약 카드 |
| 통계 화면 | 그래프 및 성장 분석 |

---

# 4. 핵심 시스템 구조

## 실시간 대화 Layer

역할:

- Realtime 음성 대화
- transcript 생성
- 자막 출력
- 중앙 비주얼 상태 피드백

---

## Learning Memory Layer

역할:

- Session Memory
- LangState
- Flashcard Memory
- Statistics Memory

관리.

---

## Batch Analysis Layer

역할:

- 대화 종료 후 분석
- Flashcard 생성 후보 추출
- LangState 업데이트
- 통계 데이터 업데이트

---

# 5. 서비스 핵심 가치

## 1. 사용자 수준 적응형 대화

AI가:

- 사용자의 언어 수준
- 최근 대화 맥락

을 기반으로 대화를 조절.

---

## 2. 실제 대화를 통한 언어 습득

단순 문제풀이가 아니라,

실제 음성 대화를 중심으로 학습.

---

## 3. 학습 데이터의 장기 축적

대화·교정·반복학습·통계가 연결되어

사용자의 언어 성장을 장기적으로 관리.

---

## 4. 개인화 언어 성장 시스템

AI는:

- 사용자의 실수
- 성장 속도
- 표현 습관

을 기반으로 점점 더 개인화된 학습 경험 제공.

MVP에서는 사용자의 자주 하는 실수나 반복 오류 패턴을 별도 데이터로 저장하지 않는다.
다만 추후 기능 고도화 단계에서 반복 실수, 교정 반영 여부, 자주 막히는 표현 패턴을 별도 참조 데이터로 축적하여
AI Chat, 교정, Flashcard 추천에 반영할 수 있도록 확장한다.
