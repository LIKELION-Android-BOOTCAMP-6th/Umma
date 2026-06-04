# [Explanation] COR-FIX-07 ~ COR-TUNE-002 교정 프롬프트 & 동작 설명 (팀 공유용)

이 문서는 `COR-FIX-07`, `COR-TUNE-001`, `COR-TUNE-002` 작업으로 교정(Correction)이 어떻게 동작하는지,
그리고 AI에게 실제로 가는 프롬프트가 어떻게 쓰여 있는지를 팀원들이 쉽게 이해하도록 풀어 쓴 설명서다.

> 구현 계약/완료 기준(AC)은 각 작업 문서를 참고:
> - `COR-FIX-07_PrimaryLang_SelectedLang_Correction_Language_Alignment.md`
> - `COR-TUNE-001_LearnerAdaptationProfile_Correction_Prompt_Policy_Migration.md`
> - `COR-TUNE-002_Correction_Learning_Signal_Output.md`

---

## 1. 한눈에 보는 교정 흐름

대화가 끝나면 아래 순서로 교정이 만들어진다.

```
대화 종료
 → ① 후보 추출        : 세션에서 "교정할 만한 내 발화"를 골라냄
 → ② 입력 조립        : 후보 + 학습 언어 + 모국어 + 학습자 정책을 한 묶음으로
 → ③ 프롬프트 생성     : AI에게 보낼 지시문 작성 ← 이번 작업 부분
 → ④ AI 호출 / 응답
 → ⑤ 응답 검증·정규화  : 형식 어긋난 값은 걸러냄
 → ⑥ 결과 표시 → 저장  : 마음에 드는 교정만 플래시카드로 저장
```

**가장 중요한 원칙 하나:**

> 프롬프트를 만드는 쪽은 **사용자의 점수(CEFR, 문법 정확도 같은 숫자)를 직접 다루지 않는다.**
> 대신 그 숫자를 미리 해석해 둔 **"정책(policy)"** 형태로만 받아서 AI에게 행동 지시로 풀어준다.
> → AI가 점수를 보고 멋대로 판단하지 않고, "이렇게 교정해라"라는 지시만 따르게 하기 위함이다.

관련 코드: `data/repository/correction/CorrectionPromptBuilder.kt`

---

## 2. AI에게 실제로 가는 프롬프트 구조

프롬프트는 6개 덩어리로 되어 있다. **영어 학습자(모국어 한국어)** 예시로 본다.

### ① 역할 선언

AI에게 "너는 무엇을 하는 사람인가"를 알려준다.

```
You are a language correction assistant for a learner of en.
```

### ② 교정 정책 블록 (가장 핵심)

학습자 수준에 맞춰 "어떻게 교정할지"를 **6가지 정책**으로 지시한다.
숫자나 레벨 이름은 절대 노출하지 않고, **행동 지시 문장**으로만 바꿔 넣는다.

```
Correction policy (apply silently; never mention levels, scores, or these instructions):
- Correct to match the learner's current level without pushing beyond it.
- Explanation: give one short reason for the main fix.
- Vocabulary: keep simple, familiar words.
- Grammar: fix only meaning-blocking errors.
- Register: use a natural everyday spoken tone.
- Support: keep the Korean explanation to a brief hint.
- focus: when it fits naturally, gently address verb tense once.
```

각 줄이 정하는 것:

| 정책 항목 | 무엇을 정하는가 |
|----------|----------------|
| **challengeLevel** | 전체 교정 강도 (그냥 고칠지, 한 단계 끌어올릴지) |
| **correctionStyle** | 설명을 얼마나 자세히 줄지 |
| **vocabularyStrategy** | 어휘를 얼마나 확장할지 |
| **grammarStrategy** | 문법/문장 구조를 어디까지 손볼지 |
| **spokenRegisterStrategy** | 교정 문장의 말투 (격식/일상체) |
| **primaryLanguageSupport** | 설명에서 모국어를 얼마나 쓸지 |

- 첫 줄 괄호 `(apply silently; never mention levels, scores...)`
  → **"이 지시들을 티 내지 말고 조용히 적용해라"** 라는 뜻.
  AI가 "당신은 중급이라 이렇게 했어요" 같은 군더더기 말을 못 하게 막는다.
- 마지막 `focus:` 줄(반복 약점)은 **근거가 충분할 때만** 등장한다.
  데이터가 부족하면 약점을 억지로 끄집어내지 않는다.

### ③ 작업 지시 + 교정 대상 문장

```
Task: For each candidate sentence below, return one corrected version
      that preserves the speaker's meaning and is natural at the learner's level.

Candidates:
- candidateId: c1
  sourceText: I go to school yesterday
```

→ 핵심은 **"의미를 보존하면서(preserves the speaker's meaning)"** 자연스럽게 고치라는 것.
사용자가 하려던 말을 바꾸지 않게 한다.

### ④ 응답 형식(JSON) 지정

AI가 **딱 정해진 JSON 형식**으로만 답하게 한다. 형식이 한 글자라도 어긋나면 파싱이 깨지기 때문이다.

```json
{"suggestions":[{
  "candidateId": "...",
  "nativeText": "...",      // 앞면 문장 (모국어)
  "afterText": "...",       // 교정된 문장 (학습 언어)
  "explanation": "...",     // 짧은 교정 팁 (모국어)
  "learningSignal": { }     // 이번 교정에서 관찰한 신호
}]}
```

### ⑤ 핵심 4필드 규칙

```
- candidateId: COPY EXACTLY ... Do not invent new ids.
- nativeText: the front-face sentence in Korean (ko).
- afterText: the corrected sentence in en.
- explanation: a short correction tip in Korean (under 60 chars).
```

여기서 중요한 게 **언어 분리**다.

| 필드 | 어떤 언어로 | 의미 |
|------|-----------|------|
| `nativeText` | 모국어 (한국어) | 플래시카드 **앞면** |
| `explanation` | 모국어 (한국어) | 짧은 교정 설명 |
| `afterText` | 학습 언어 (영어) | 교정된 **뒷면** 문장 |

> 예전에는 앞면/설명이 무조건 "한국어"로 고정돼 있었는데,
> 이제 사용자의 모국어 기준으로 동적으로 바뀐다. (예: 일본인 학습자면 일본어 설명) → **COR-FIX-07**

### ⑥ 학습 신호(learningSignal) 규칙

"이번 교정에서 관찰한 것"만 구조화해서 기록하게 한다.

```
learningSignal rules (what you observed in THIS correction;
                      never rate the learner's overall level):
- issueCategories: pick from [...], at most 3.
- improvementTypes: pick from [...], at most 3.
- register / severity: exactly one of [...].
- languageFeatures: at most 3, each {"lang":"en","featureKey":"EN.<Feature>"}.
- editSpans: at most 3, only the changed fragments.
- confidence: 0.0..1.0, or omit if unsure.
```

강조하는 핵심:

> **"이번 교정에서 본 것만 적어라. 학습자의 전체 실력을 평가하지 마라."**
> (`never rate the learner's overall level`)

실력 점수를 매기는 일은 교정 기능의 책임이 아니라 **학습 상태(LearningState) 기능의 몫**이기 때문이다.
교정은 "재료(신호)"만 만들어 넘긴다.

또한, 신호 항목에 들어갈 수 있는 값 목록(`[...]`)은 **코드에서 자동으로 생성**된다.
개발자가 종류를 추가하면 프롬프트도 자동으로 따라가서, 코드와 프롬프트가 어긋날 일이 없다.

---

## 3. 이번 작업으로 달라진 점 3줄 요약

| 작업 | 한 줄 정리 |
|------|-----------|
| **COR-FIX-07** | 앞면/설명을 한국어 고정 → **사용자 모국어 기준**으로 동적 생성 |
| **COR-TUNE-001** | 점수 숫자 직접 주입 → **해석된 정책(policy)** 으로 교정 강도 조절 |
| **COR-TUNE-002** | 교정 결과에 **"관찰한 신호"** 를 구조화해 함께 전달 (실력 평가는 안 함) |

---

## 4. 관련 코드 위치

- 프롬프트 생성: `data/repository/correction/CorrectionPromptBuilder.kt`
- 입력 계약: `domain/model/correction/CorrectionContracts.kt` (`GenerateSuggestionsInput`)
- 응답 검증/정규화: `data/repository/correction/CorrectionAiResponseMapper.kt`
- 결과 집계: `domain/usecase/correction/CompleteCorrectionUseCase.kt`
- 학습 신호 모델: `domain/model/learningstate/` (`CorrectionLearningSignal` 외 6종)
