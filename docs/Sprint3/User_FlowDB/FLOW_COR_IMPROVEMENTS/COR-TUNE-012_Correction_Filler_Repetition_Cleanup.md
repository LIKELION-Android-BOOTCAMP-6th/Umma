# [Improvement-TUNE] COR-TUNE-012 교정 결과 filler/반복 표현 정리

> 출처 BugFix: `CORRECTION_FILLER_REPETITION_CLEANUP.md`

## User Story

학습자는 음성 대화 발화를 교정해 플래시카드로 저장한다. 그런데 STT 발화에는 의미 없는 filler·말버릇·반복이 섞이고, 교정 결과(`afterText`)에 그대로 남으면 학습자는 정리된 표현이 아니라 말버릇까지 포함한 문장을 복습하게 된다. 교정 결과에는 불필요한 filler와 과한 반복이 없어야 한다.

예:

- EN: `you know`, `like`, `um`, `uh`, `I mean`, `basically`
- JA: `なんか`, `えっと`, `あの`, `まあ`, `その`
- KO: `그니까`, `약간`, `음`, `어`, `뭔가`, `아니`, `그래서`

```text
sourceText: I mean, you know, I was like, you know, really tired.
afterText:  I was really tired.
```
```text
sourceText: なんか、今日はなんかちょっと疲れた。
afterText:  今日はちょっと疲れた。
```
```text
sourceText: 약간 오늘 약간 너무 피곤했어.
afterText:  오늘 너무 피곤했어.
```

## 왜 TUNE(품질)인가 — COR-FIX와 분리

이 작업은 버그(파싱 실패)가 아니라 **교정 생성 품질 정책**이다. JSON schema를 바꾸지 않고, mapper가 받는 필드 구조도 그대로다. 변경은 거의 **`CorrectionPromptBuilder`의 `afterText` 생성 정책**에 한정된다. 따라서 응답 파싱 견고성(COR-FIX-008)과 완전히 독립된 TUNE 트랙으로 둔다.

## 핵심 결정

- **프롬프트 정책 강화가 1순위.** rule-based sanitizer를 먼저 만들지 않는다. 모델이 이미 문맥을 보고 자연스러운 문장을 만들 수 있고, EN/JA/KO는 띄어쓰기·조사·담화 기능 때문에 단순 regex 삭제가 어색한 결과를 만든다.
- **원문 보존.** `sourceText`는 수정/삭제하지 않는다. `afterText`만 정리한다. 후보 추출(`ExtractCandidatesUseCase`)에서 filler를 제거하지 않는다 — sourceText 변형은 `candidateId`(source hash) 안정성과 말버릇 학습 신호 관찰을 깬다.
- **의미 있는 표현은 보존.** 실제 의미·대조·강조·뉘앙스를 담은 표현은 지우지 않는다(Edge Cases 참고).
- **규칙은 짧게.** 긴 blacklist는 토큰을 늘리고 문맥 무관 삭제를 유도하며 응답 안정성을 떨어뜨린다.
- **schema/필드 구조 불변.** `nativeText`는 기존대로 primary language 기준 front-face. `learningSignal.editSpans`는 filler 제거가 주 교정일 때만 간단히 반영, 확신 없으면 신호를 무리해서 만들지 않는다.

## 완료 기준(AC)

- [ ] 교정 prompt에 filler/repetition cleanup 정책이 명시된다.
- [ ] prompt에 EN/JA/KO 대표 예시(`you know`/`like`/`なんか`/`약간` 등)가 포함된다.
- [ ] `afterText`는 학습/flashcard용으로 간결한 결과를 생성하도록 지시된다.
- [ ] `sourceText` 원문 보존 정책이 유지된다.
- [ ] `candidateId` exact-copy 규칙이 유지된다.
- [ ] JSON schema와 mapper가 받는 필드 구조는 변경되지 않는다.
- [ ] prompt builder 테스트(또는 snapshot/assertion)가 추가된다.

## 주요 작업

1. **프롬프트 규칙 추가** — `CorrectionPromptBuilder.build()`의 task/rules 근처:

```text
Filler and repetition cleanup:
- In afterText, remove unnecessary filler words, hesitation markers, and repeated discourse markers when they do not change the speaker's meaning.
- Examples include English "um", "uh", "you know", "like", "I mean"; Japanese "なんか", "えっと", "あの", "まあ", "その"; Korean "음", "어", "그니까", "약간", "뭔가".
- Do not remove a word if it carries real meaning, contrast, emphasis, or the speaker's intended nuance.
- Keep the corrected sentence natural and concise for learning/flashcard use.
```

2. **하드 후처리 지양.** 운영에서 모델이 계속 filler를 남긴다는 증거가 쌓이면, 그때 언어별 conservative sanitizer를 별도 usecase로 검토.

## 주요 touchpoints

- `app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt` (가장 유력)
- `app/src/main/java/com/app/umma/data/repository/correction/CorrectionAiResponseMapper.kt` (schema 불변 — 변경 가능성 낮음)
- `app/src/main/java/com/app/umma/domain/usecase/correction/ExtractCandidatesUseCase.kt` (filler 제거 금지 — 변경하지 않음)
- `app/src/main/java/com/app/umma/domain/model/correction/CorrectionSuggestion.kt`

## Edge Cases (보존해야 할 문맥)

- EN `like`가 동사/전치사: `I like coffee.` 보존 / `Like, I was really tired.` 정리
- EN `I mean`이 정정·강조 역할이면 완전 제거하지 않아도 됨
- JA `なんか`가 "something" 의미일 때 주의
- JA `その`가 지시어일 때 제거 금지
- KO `약간`이 실제 정도 표현이면 보존
- KO `아니`가 부정 의미일 때 제거 금지

## 테스트

`CorrectionPromptBuilder` 테스트:

- prompt에 filler cleanup 지시 포함.
- prompt에 `you know`/`like`/`なんか`/`약간` 등 대표 예시 포함.
- prompt의 JSON schema 및 candidateId exact-copy rule 유지.

(응답 mapper 테스트 추가 필요성 낮음 — schema 변경이 아니라 생성 정책 변경.)

수동 QA:

```text
Input:  I mean, you know, I was like really tired today.
Expect: I was really tired today.

Input:  なんか、今日はなんかすごく眠い。
Expect: 今日はすごく眠い。

Input:  약간 오늘 약간 정신이 없었어.
Expect: 오늘 정신이 없었어.
```

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionPromptBuilderTest"
.\gradlew.bat compileDevDebugKotlin
```

## Risks

- 너무 강한 지시 → 회화체의 자연스러운 discourse marker까지 사라짐
- 너무 약한 지시 → 기존과 차이 미미
- filler 제거가 주 교정일 때 learning signal category 분류 애매
- prompt 비대화 → 응답 안정성 저하 (규칙은 짧게)

## 최종 권장 정책

> 교정 결과는 사용자의 의미와 자연스러운 말투를 보존하되, 학습/복습에 불필요한 filler, 머뭇거림, 과한 반복 표현은 제거하거나 줄인다. 단, 해당 표현이 실제 의미나 뉘앙스를 담고 있으면 삭제하지 않는다.

## Out of Scope

- `sourceText` 수정/삭제
- 후보 추출 단계에서 filler 제거
- 모든 filler 기계적 100% 삭제
- candidateId/sourceTurnId/learningSignal 매핑 안정성 변경
- JSON schema 변경
