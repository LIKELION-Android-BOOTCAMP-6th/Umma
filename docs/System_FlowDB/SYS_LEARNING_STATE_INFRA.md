# SYS-LEARNING-STATE-INFRA

## 목표

- 사용자 학습 상태(Language State)와 통계 데이터를 공통 구조로 관리할 수 있다.
- Dashboard 및 이후 AI Chat/Statistics 기능에서 재사용 가능한 학습 상태 기반을 구축한다.
- 로컬 캐시와 Firebase 동기화 정책을 정의한다.

이 구조는:

- Dashboard
- AI Chat
- 교정
- 플래시카드
- 통계 화면

에서 공통으로 사용된다.

이 구조를 처음 이해할 때는 `SYS_LEARNING_STATE_INFRA/SYS_LEARNING_STATE_INFRA_OVERVIEW.md`부터 보면 흐름이 가장 잘 잡힌다.

---

# 핵심 개념 설명

## Language State

사용자의 현재 언어 능력을 숫자와 상태 형태로 저장한 데이터.

예:

- 문법 정확도
- 어휘 수준
- 유창성
- 자연스러운 표현 사용 정도

AI는 이 데이터를 참고하여 사용자 수준에 맞는 대화를 생성한다.

---

## Dashboard Summary

Dashboard 화면에서 보여주는 언어별 학습 요약 카드 데이터.

예:

- 영어 대화 시간
- 일본어 대화 시간
- 언어별 Flashcard 복습 수

전체 transcript를 매번 다시 계산하지 않고,

언어별 Summary 데이터를 별도로 저장하여 빠르게 출력한다.

목적:

- 다국어 Dashboard 지원
- 언어별 Statistics 분리
- 언어별 Flashcard 집계
- Dashboard preload 성능 최적화

---

## Session Memory

AI Chat의 대화 연속성과 교정 전 full context를 관리하는 언어별 재사용 세션 데이터.

Session Memory는 대화 1회마다 새 문서를 생성하지 않는다.
사용자와 학습 언어 기준으로 하나의 세션 문서를 재사용한다.
MVP에서는 Summary 계열과 초기 저장 계약을 DataStore 기반으로 안정화하고,
실제 원문 turn list는 고빈도 append 데이터이므로 Room 기반 저장으로 분리한다.

예:

```text
users/{uid}/sessions/en
users/{uid}/sessions/ja
```

### 핵심 필드

| 필드 | 역할 |
| --- | --- |
| language | 세션 데이터의 학습 언어 |
| recentFullContext | 교정 및 Flashcard 저장 전까지 유지되는 turn list 원문 buffer |
| recentTopics | 최근 대화 주제 |
| topicSummaries | 교정 이후 남기는 주제별 압축 요약 |
| topicKeySentences | 주제별 핵심 문장 |
| correctionAvailable | 교정 가능한 사용자 발화가 존재하는지 여부 |
| lastCompressedAt | 마지막 압축 시점 |

### recentFullContext 원칙

- 전체 transcript 문자열이 아니라 turn 단위 리스트로 저장한다.
- Firebase Live API streaming chunk는 임시 buffer에서 조립하고, 발화 또는 응답 완료 시 하나의 turn으로 확정한다.
- turn은 최소 `turnId`, `role`, `text`, `createdAt`을 가진다.
- 최대 N턴까지만 유지한다.
- 교정 및 Flashcard 저장 이후 압축되고 초기화된다.

### 비용 정책

- `recentFullContext`는 저장용 원본 buffer이며, AI 교정 요청 시 전체를 그대로 전송하지 않는다.
- 교정 요청 payload는 user turn 중심으로 재구성한다.
- assistant turn은 문맥상 필요한 일부만 포함한다.
- Language State 전체가 아니라 교정에 필요한 snapshot만 포함한다.

---

## Global Learning State

앱 전체에서 공통으로 사용하는 학습 상태 저장 공간.

예:

```text
Dashboard
AI Chat
Statistics
```

가 동일한 학습 상태를 공유한다.

---

## User Learning Preference

사용자가 어떤 언어를 학습하고 있으며, 현재 앱이 어떤 언어 컨텍스트를 바라보는지 저장하는 전역 설정 데이터.

예:

```json
{
  "nativeLanguage":"ko",
  "primaryLearningLanguage":"en",
  "selectedLearningLanguage":"en",
  "learningLanguages":["en"]
}
```

### 필드 역할

| 필드 | 역할 |
| --- | --- |
| nativeLanguage | 힌트, 교정 앞면, 제한적 모국어 보조에 사용 |
| primaryLearningLanguage | 최초 온보딩에서 선택한 주 학습 언어 |
| selectedLearningLanguage | Dashboard / AI Chat / Flashcard / Statistics가 현재 바라보는 언어 |
| learningLanguages | 사용자가 학습 중인 언어 목록 |

### 핵심 원칙

- `language`는 각 학습 데이터의 소속 필드이다.
- `selectedLearningLanguage`는 현재 화면과 기능 이동에 적용되는 전역 필터/컨텍스트이다.
- 온보딩 완료 시 `selectedLearningLanguage = primaryLearningLanguage`로 초기화한다.
- Dashboard에서 언어 selector를 통해 `selectedLearningLanguage`를 변경할 수 있다.

---

## observe 정책

어떤 화면이 어떤 상태 데이터를 “구독(observe)”하고 자동 갱신되는지 정의하는 정책.

예:

- Flashcard 학습 완료
→ Dashboard의 복습 카드 수 자동 갱신

---

## persist 정책

어떤 데이터를:

- 어디에 저장할지
- 얼마나 오래 유지할지

정의하는 정책.

예:

- Language State:
앱 종료 후에도 유지 필요 → 로컬 + Firebase 저장

- 현재 입력 UI 상태:
앱 종료 시 유지 불필요 → 메모리에서만 유지

---

## Local Cache

Firebase를 매번 조회하지 않고,

앱 내부(기기)에 임시 저장하는 데이터.

목적:

- 빠른 로딩
- Firebase 비용 절감
- 오프라인 대응

---

## Batch Update

대화 중 실시간 저장하지 않고,

교정 또는 학습 분석 시점에 `recentFullContext`에서 필요한 turn만 추출하여 상태를 한 번에 업데이트하는 방식.

목적:

- Firebase write 비용 감소
- AI 호출 최소화
- 상태 안정성 유지

---

# 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. Language State 모델 정의 | Internal / External Metrics 구조 설계 | 사용자 언어 상태 데이터 생성 가능 | 성공: 모델 생성 완료 / 실패: 모델 구조 충돌 | LS-001 |
| 2. Dashboard Summary 구조 정의 | Dashboard 카드 출력용 통계 모델 설계 | 최근 학습 요약 데이터 생성 가능 | 성공: Dashboard 데이터 매핑 완료 / 실패: 통계 데이터 누락 | LS-002 |
| 3. User Learning Preference 정의 | nativeLanguage / primaryLearningLanguage / selectedLearningLanguage / learningLanguages 구조 설계 | 앱 전역 현재 학습 언어 지정 가능 | 성공: 현재 언어 컨텍스트 생성 완료 / 실패: 언어 미선택 상태 | LS-003 |
| 4. Global Learning State 구성 | 전역 학습 상태 Store 구성 | 여러 화면에서 상태 공유 가능 | 성공: 상태 공유 완료 / 실패: 상태 동기화 오류 | LS-004 |
| 5. 로컬 캐시 및 Firebase 동기화 정책 정의 | persist / fetch / sync 구조 정의 | 로컬 우선 읽기 및 batch 저장 가능 | 성공: 상태 동기화 완료 / 실패: 데이터 충돌 | LS-005 |
| 6. Language State 업데이트 정책 정의 | 교정 또는 학습 분석 시점의 batch 업데이트 로직 정의 | 안정적인 상태 갱신 가능 | 성공: 이동평균 반영 완료 / 실패: 급격한 상태 변동 | LS-006 |
| 7. Initial Learning State 초기 저장 계약 정의 | Initial Setup 완료 시 학습 상태 초기값 저장 계약 정의 | 온보딩 완료 후 Dashboard 진입 가능 | 성공: 초기 학습 상태 생성 완료 / 실패: 부분 생성 또는 저장 실패 | LS-007 |

---

# 필수 상태 관리 (State Detail)

## Loading

- Language State preload 중 로딩 표시
- Dashboard Summary fetch 중 Skeleton UI 사용 가능

---

## Error

### Fatal

- Language State fetch 실패 시 기본 상태로 fallback

### Transient

- Statistics fetch 실패 시 Snackbar 표시

---

## Empty

- 신규 사용자:
    - User Learning Preference 없음
    - Language State 없음
    - Dashboard Summary 없음
    - Flashcard 없음

초기 상태 UI 필요.

---

# GitHub Issues (실행 기준 / SSOT)

## [LS-001] Language State Model Structure

### 포함 내용

- Internal Metrics 구조 정의
- External Metrics 구조 정의
- Kotlin Model 설계
- Firestore Schema 정의

---

### Internal Metrics (MVP)

#### Accuracy

- grammar_accuracy
- vocabulary_appropriateness

#### Range

- lexical_diversity
- vocabulary_level
- sentence_complexity

#### Fluency

- speech_rate
- pause_frequency
- avg_utterance_length

#### Naturalness

- spoken_naturalness
- natural_expression_usage

#### Learning

- error_recurrence
- review_retention

---

### External Metrics

사용자에게 통계 화면 형태로 노출되는 요약 지표.

- vocabulary_level
- grammar_accuracy
- expression_range
- fluency_score
- naturalness_score

---

### language 필드 정책

Language State / Session / Flashcard / Statistics 데이터는 모두 language 필드를 포함한다.

예:

```json
{
  "language":"en"
}
```

목적:

- 다국어 학습 지원
- 언어별 Summary 집계
- 언어별 Dashboard 출력
- 언어별 Flashcard 학습 지원

---

## [LS-002] Dashboard Summary Model

### 포함 내용

- 최근 대화 시간
- 최근 학습량
- 복습 카드 수
- 최근 성장 수치

---

### Dashboard Summary 예시

Dashboard는 단일 Summary가 아니라,

언어별(Language Scoped) Summary Collection 구조를 사용한다.

```json
{
  "summaries":[
    {
      "language":"en",

      "recentConversationMinutes":5,

      "recentConversationTopic":"Travel",

      "correctionAvailable":true,

      "dueFlashcards":8,

      "recentSavedFlashcards":3,

      "grammarScoreDelta":4,

      "fluencyScoreDelta":2,

      "vocabularyScoreDelta":5,

      "naturalnessScoreDelta":1
    },

    {
      "language":"ja",

      "recentConversationMinutes":3,

      "recentConversationTopic":"Anime",

      "correctionAvailable":false,

      "dueFlashcards":14,

      "recentSavedFlashcards":5,

      "grammarScoreDelta":2,

      "fluencyScoreDelta":1,

      "vocabularyScoreDelta":4,

      "naturalnessScoreDelta":3
    }
  ]
}
```

목적:

- 다국어 Dashboard 지원
- 언어별 Flashcard 집계
- 언어별 Statistics 분리
- Dashboard preload 성능 최적화

---

## [LS-003] User Learning Preference Model

### 포함 내용

- nativeLanguage 정의
- primaryLearningLanguage 정의
- selectedLearningLanguage 정의
- learningLanguages 정의
- 온보딩 초기화 정책
- Dashboard 언어 변경 정책
- Firestore / Local Cache 저장 구조

---

### 저장 예시

```json
{
  "nativeLanguage":"ko",
  "primaryLearningLanguage":"en",
  "selectedLearningLanguage":"en",
  "learningLanguages":["en"]
}
```

---

### 초기화 정책

- 온보딩에서 모국어와 주 학습 언어를 선택한다.
- 저장 완료 시 `selectedLearningLanguage`는 `primaryLearningLanguage`와 같은 값으로 설정한다.
- 주 학습 언어의 Language State, Dashboard Summary, Session Summary, Flashcard Summary 초기값을 생성한다.
- 실제 원문 Session Memory 모델은 `SYS-REALTIME-INFRA`의 RT-003에서 turn 저장 구조로 다룬다.

---

### 변경 정책

- Dashboard 언어 selector에서 학습 언어를 변경하면 `selectedLearningLanguage`를 갱신한다.
- 기존 학습 데이터의 `language` 필드는 변경하지 않는다.
- 선택 언어가 `learningLanguages`에 없으면 추가 학습 언어 등록 플로우로 연결한다.
- MVP에서는 `learningLanguages`에 이미 존재하는 언어만 선택 가능하도록 제한할 수 있다.

---

## [LS-004] Global Learning State Store

### 포함 내용

- App 전역 학습 상태 구조
- 상태 observe 정책
- persist 정책

---

### observe 정책

어떤 화면이 어떤 상태를 observe(구독)할 것인지 정의.

예:

- Dashboard → 언어별 dueFlashcards observe
- Statistics → 현재 선택 언어의 languageState observe
- AI Chat → 현재 선택 언어의 languageState / recentSessionSummary observe
- Flashcard → 현재 선택 언어의 dueFlashcards observe

상태가 변경되면 UI 자동 갱신.

---

### persist 정책

어떤 데이터를:

- 메모리에만 유지할지
- 로컬 저장할지
- Firebase에 저장할지

정의.

---

### 전역 상태 예시

모든 학습 상태는 언어별(Language Scoped)로 관리한다.

```text
GlobalLangState
├── userPref
│    ├── nativeLang
│    ├── primaryLang
│    ├── selectedLang
│    └── learningLangs
│
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

예:

- English Summary
- Japanese Summary
- Spanish Summary
- selectedLearningLanguage가 `en`이면 각 화면은 기본적으로 `en` 하위 데이터를 사용한다.

---

## [LS-005] Local Cache & Sync Policy

### 저장 전략

| 데이터 | 저장 방식 | 설명 |
| --- | --- | --- |
| User Learning Preference | Local persist + Firebase sync | 현재 학습 언어 및 사용자 언어 설정 |
| Language State | Local persist + Firebase sync | 사용자 장기 언어 상태 |
| Dashboard Summary | Local cache + updatedAt | 빠른 Dashboard 출력 |
| Session Summary | Local cache + updatedAt | Dashboard / Correction 진입 판단용 세션 요약 |
| Flashcard Summary | Local cache + updatedAt | Dashboard 복습 카드 상태 요약 |
| Statistics | Firebase fetch + cache | 통계 그래프 데이터 |
| Session Memory | turn 확정 후 로컬 반영 + Firebase batch sync | 언어별 재사용 세션, recentFullContext 및 압축 기억 저장, Room 기반 원문 저장 |

> MVP에서는 `updatedAt` 기준으로 Local Summary를 우선 렌더링하고 Firebase background sync로 최신화한다.
> TTL 기반 캐시 만료 정책은 Phase 2에서 검토한다.

---

### 언어별 저장 정책

Umma의 학습 데이터는 모두 언어별(language scoped)로 저장한다.

예:

- 영어 Session
- 일본어 Session
- 영어 Flashcard
- 일본어 Flashcard

는 각각 분리 저장 및 집계된다.

목적:

- 다국어 학습 지원
- 언어별 Dashboard 출력
- 언어별 Statistics 분리
- 언어별 Flashcard 학습 지원

---

### 저장 위치 권장

| 데이터 | 언어별 저장 | 메모리 | 로컬 저장 | Firebase | 설명 |
| --- | --- | --- | --- | --- | --- |
| User Learning Preference | X | O | DataStore | Firestore | 현재 선택 언어 및 언어 설정 |
| Language State | O | O | DataStore | Firestore | 장기 언어 능력 상태 |
| Dashboard Summary | O | O | DataStore | Firestore | Dashboard preload용 요약 데이터 |
| Session Summary | O | O | DataStore | Firestore | Dashboard / Correction 진입 판단용 세션 요약 |
| Flashcard Summary | O | O | DataStore | Firestore | Dashboard 복습 카드 상태 요약 |
| Session Memory | O | O | Room | Firestore | turn list 기반 full context 및 압축 대화 기억 |
| Flashcard | O | O | Room | Firestore | SRS 반복 학습 카드 |
| Statistics | O | O | Room/DataStore | Firestore | 통계 그래프 및 성장 데이터 |

---

### 동기화 정책

- 앱 시작 시 preload
- turn 확정 후 로컬 반영
- Firebase batch sync
- 실시간 Firebase write 금지
- 로컬 데이터 우선 조회 후 Firebase 동기화

```text
앱 실행
→ UserLangPref Local preload
→ selectedLearningLanguage 확인
→ DashSummary[selectedLearningLanguage] Local preload
→ 현재 선택 언어 기준 Dashboard 렌더링
→ Firebase background sync
→ 변경사항 존재 시 UI 갱신
```

---

## [LS-006] Language State Update Policy

### 업데이트 시점

- 교정 또는 학습 분석 완료 직후

이때 업데이트 대상은 해당 Session Memory의 `language`와 일치하는 Language State이다.

예:

```text
Session.language = "en"
→ languageStates["en"] 업데이트
→ dashboardSummaries["en"] 재계산
```

---

### 업데이트 방식

```text
새 값 = 기존 값 × 0.8 + 새 측정값 × 0.2
```

최근 한 번의 대화 결과로 상태가 급격히 변하지 않도록 이동 평균 방식 사용.

---

### 분석 전략

| 유형 | 처리 방식 | 설명 |
| --- | --- | --- |
| Type A | 코드 계산 | 발화 길이, 발화 속도, pause 등 단순 계산 |
| Type B | 규칙 기반 | 어휘 다양성, CEFR 매핑, 문장 복잡도, 반복 오류 분석 |
| Type C | AI 분석 | 자연스러움, 자연 표현 사용, 문맥상 어휘 적절성 판단 |

---

### AI 분석 대상 최소화

비용 절감을 위해,

정말 AI 판단이 필요한 지표만 AI 분석 수행.

---

### Type C만 AI 호출

- spoken_naturalness
- natural_expression_usage
- vocabulary_appropriateness

MVP에서 `contextual_response_quality`, `expression_confidence` 등은 `LangState`에 저장하지 않고,
필요 시 분석 과정의 참고값 또는 Phase 2 확장 후보로만 둔다.

---

## [LS-007] Initial Learning State Persistence Contract

### 포함 내용

- Initial Setup 완료 시 생성할 학습 상태 초기 데이터 묶음
- `UserLangPref`, `LangState`, `DashSummary`, `SessionSummary`, `FlashcardSummary` 초기값 생성 계약
- `LearningStateRepoImpl`의 초기 저장 책임
- Kotlin Domain 필드와 Firestore 필드 간 mapper 규칙
- 저장 실패 / 재시도 / 부분 생성 복구 정책
- Session Memory 원문 모델의 보류 범위
- 현재 MVP는 학습 상태 스냅샷은 DataStore 기반 저장부터 시작하고, Session Memory 원문과 고빈도 변경 데이터는 Room을 사용한다.

---

### Initial Setup 초기 저장 흐름

```text
Initial Setup 완료
→ UserLangPref.initial(nativeLang, primaryLang)
→ LangState.initial(primaryLang)
→ DashSummary.initial(primaryLang)
→ SessionSummary.initial(primaryLang)
→ FlashcardSummary.initial(primaryLang)
→ LearningStateRepo.createInitial(...)
→ Local / Remote 저장
→ GlobalLangState 갱신
→ Dashboard 진입
```

---

### 책임 경계

- `UserProfile` 생성은 AUTH / UserProfile 영역에서 다룬다.
- 학습 상태 초기값 생성은 LS-007 계약을 따른다.
- 실제 원문 turn list를 담는 Session Memory 모델은 `SYS-REALTIME-INFRA`의 RT-003에서 구현한다.
- 온보딩은 우선 `SessionSummary.initial(...)`만 생성해 Dashboard 진입 가능 상태를 만든다.

---

# 예외 상황 및 대응 (Edge Case)

1. 신규 사용자로 Language State 없음
2. User Learning Preference 없음
3. selectedLearningLanguage 없음
4. selectedLearningLanguage에 해당하는 Dashboard Summary 생성 전 상태
5. selectedLearningLanguage가 learningLanguages에 포함되지 않음
6. Firebase write 실패
7. 로컬 캐시 손상
8. Language State 버전 불일치
9. 동기화 중 앱 종료
10. 통계 데이터 일부 누락
11. AI 분석 결과 null 반환

---

# 디자인 및 구조 연결

## Package

```text
domain/model/learningstate
├── LearningCoreModels.kt
├── LearningStateModels.kt
├── LearningSummaryModels.kt
├── LearningProfileModels.kt
└── LearningUpdateModels.kt

domain/repository
└── LearningStateRepo.kt

domain/usecase/learningstate
├── LearningStateReadUseCases.kt
└── LearningStateWriteUseCases.kt

data/repository
└── LearningStateRepoImpl

data/source/local
├── LearningStateLocalDataSource
├── UserLangPrefLocalDataSource
└── SessionMemoryLocalDataSource

data/source/remote
├── LearningStateRemoteDataSource
├── UserLangPrefRemoteDataSource
└── SessionMemoryRemoteDataSource

presentation/dashboard
presentation/chat
presentation/study
presentation/feed_back
presentation/analytics
```

학습 상태 구조는 별도의 `core.learning`, `core.statistics`, `core.memory` 패키지를 새로 만들지 않고,
팀장이 구성한 Clean Architecture 레이어에 맞춰 역할별로 배치한다.

---

## Naming

### Kotlin Model

- `LangState`
- `DashSummary`
- `UserLangPref`
- `GlobalLangState`
- `SessionSummary`
- `FlashcardSummary`

### Repository / UseCase

- `LearningStateRepo`
- `ObserveLearningStateUseCase`
- `ObserveDashSummaryUseCase`
- `PreloadLearningStateUseCase`
- `ChangeSelectedLangUseCase`
- `ApplyLanguageStateUpdateUseCase`
- `InitLearningStateUseCase`
- `ClearLearningStateUseCase`

### Kotlin 필드명과 Firestore 필드명

Kotlin Domain 모델은 팀 내 사용성을 위해 짧은 이름을 사용한다.

- `lang`, `selectedLang`, `learningLangs`
- `recentMinutes`, `recentTopic`, `savedFlashcards`
- `grammarDelta`, `vocabDelta`, `schema`

Firestore 문서는 데이터 의미를 외부에서 읽기 쉽도록 설명적인 lowerCamelCase 필드명을 유지한다.

- `language`, `selectedLearningLanguage`, `learningLanguages`
- `recentConversationMinutes`, `recentConversationTopic`, `recentSavedFlashcards`
- `grammarScoreDelta`, `vocabularyScoreDelta`, `schemaVersion`

Data Layer mapper는 두 명명 체계 사이를 변환한다.

---

## 구조 변경 메모

초기 구조에서는 Dashboard Summary를 단일 사용자 기준으로 설계했으나,

Umma의 장기 목표인:

- 다국어 학습
- 언어별 Flashcard
- 언어별 Statistics
- 언어별 Dashboard

구조를 고려하여,

모든 학습 데이터는 Language Scoped 구조로 수정되었다.

또한 앱이 현재 바라보는 학습 언어를 명확히 하기 위해 `selectedLearningLanguage`를 User Learning Preference에 추가한다.

`selectedLearningLanguage`는 학습 데이터의 소속을 바꾸지 않고, Dashboard와 기능 이동에서 사용할 현재 언어 컨텍스트만 결정한다.

---

### Firebase 저장 구조 예시

```text
users/{uid}
├── user_learning_preference
│    └── current
│         ├── nativeLanguage
│         ├── primaryLearningLanguage
│         ├── selectedLearningLanguage
│         └── learningLanguages
│
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
