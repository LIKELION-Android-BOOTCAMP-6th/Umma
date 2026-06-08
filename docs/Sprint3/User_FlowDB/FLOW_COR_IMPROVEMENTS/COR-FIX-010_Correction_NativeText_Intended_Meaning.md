# [FIX] COR-FIX-010 교정 헤더(nativeText) 의미 문장 생성 교정

> 발견 경위: 실기기 교정 화면에서 카드 헤더가 "하려던 말"이 아니라 'i' 설명과 **거의 같은 교정 설명 문구**로 나오는 현상 확인.
> 관련: [COR-UX-005](COR-UX-005_Correction_Result_Card_Readability_Polish.md)(설명 아이콘 시각 정리 — 본 건과 분리)

## User Story

사용자는 교정 카드 헤더에서 **"내가 원래 하려던 말"** 을 모국어(한국어)로 본다. 지금은 헤더에 교정 설명("주어와 동사를 추가했어요")이 들어와, 아래 'i' 설명과 거의 같은 문장이 두 번 보인다.

---

# 배경

`CorrectionSuggestion.nativeText`는 **플래시카드 앞면 = 교정문(afterText)의 모국어 뜻(= 학습자가 하려던 말)** 이어야 한다([CorrectionSuggestion.kt](../../../../app/src/main/java/com/app/umma/domain/model/correction/CorrectionSuggestion.kt): `Flashcard 앞면에 표시할 primaryLang 기준 문장`).

확인된 현황(실기기):

```text
헤더(nativeText):  "문장을 완성하기 위해 주어와 동사를 추가했어요."
i 설명(explanation): 문장을 완성하기 위해 주어와 동사를 추가했어요.
```

```text
헤더(nativeText):  "'like' 없이 'completely'를 더 자연스러운 위치에 두어 간결하게 표현했어요."
```

→ 헤더에 **교정문의 한국어 뜻**이 아니라 **교정을 어떻게 했는지에 대한 설명**이 들어가 있다. 그래서 헤더와 'i' 설명이 사실상 같은 문장이 되어 중복돼 보인다.

원인은 프롬프트의 `nativeText` 지시가 모호하기 때문이다([CorrectionPromptBuilder.kt:115](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt)):

```
- nativeText: the front-face sentence in 한국어 (ko).
```

"한국어로 된 앞면 문장"이라고만 해서, AI가 "교정문(afterText)의 한국어 번역/의도"가 아니라 "교정 설명"을 채워 넣는다. 같은 빌더의 언어 기준 주석도 `앞면(nativeText)과 설명(explanation)의 기준 언어`([:38](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt))까지만 규정하고 **내용(설명이 아니라 의미)** 은 구분하지 않는다.

방향은 **"`nativeText`는 afterText의 모국어 의미(하려던 말)이고, 교정에 대한 설명은 explanation에만 둔다"는 점을 프롬프트에서 명확히 못 박는 것**이다. 응답 schema 핵심 4필드와 learningSignal 규칙은 바꾸지 않는다(프롬프트 문구만 명료화).

---

# 완료 기준(AC)

- [ ] `nativeText`는 **afterText(교정문)의 자연스러운 모국어(primaryLang) 번역 = 학습자가 하려던 말**이 되도록 프롬프트 지시를 명확히 한다.
- [ ] `nativeText`에 **교정을 설명하는 문구**(무엇을 고쳤는지)가 들어가지 않도록 명시한다. 교정 설명은 `explanation`에만 둔다.
- [ ] 결과적으로 한 카드에서 헤더(`nativeText`)와 'i'(`explanation`)가 **서로 다른 역할의 문장**으로 보인다(의도 문장 vs 교정 이유).
- [ ] 응답 schema 핵심 4필드(candidateId/nativeText/afterText/explanation)와 learningSignal 규칙(COR-TUNE-002-FIX 강화판)은 변경하지 않는다.
- [ ] `afterText`(대상 언어), `explanation`(언어/길이 정책)의 기존 동작은 회귀 없이 유지된다.
- [ ] AI 호출은 교정 1회당 1회로 유지한다(2-pass 도입 금지).

---

# 기준 문서

- [CorrectionPromptBuilder.kt](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt) (대상 — nativeText 지시 명료화)
- [CorrectionSuggestion.kt](../../../../app/src/main/java/com/app/umma/domain/model/correction/CorrectionSuggestion.kt) (nativeText = 앞면 모국어 문장 계약)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-FIX-007_PrimaryLang_SelectedLang_Correction_Language_Alignment.md` (nativeText/afterText 언어 기준 정렬)
- [COR-TUNE-002-FIX](COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md) (learningSignal 규칙 — 불변 확인)

---

# 핵심 결정

- **프롬프트 문구만 명료화한다.** schema/모델/매퍼는 그대로 두고, `nativeText` 규칙 한 줄을 "afterText의 모국어 의미, 교정 설명 금지"로 구체화한다. 가장 작은 변경으로 근본 원인을 제거한다.
- **역할 분리를 프롬프트가 명시.** `nativeText` = "하려던 말(의미)", `explanation` = "왜/어떻게 고쳤는지". 두 필드의 책임을 규칙에서 대비시켜 AI가 헷갈리지 않게 한다.
- **(선택) 짧은 대비 예시 1줄.** 필요하면 "nativeText는 의미, explanation은 교정 이유"를 보여주는 illustrative 예시를 추가하되, 특정 언어/문장을 강제하지 않는다(기존 few-shot 원칙 유지).

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionPromptBuilder | `nativeText` 규칙을 "afterText의 모국어 의미, 교정 설명 금지"로 명료화. (선택) 역할 대비 예시 1줄 |
| CorrectionAiResponseMapper / CorrectionSuggestion (범위 밖) | schema·필드·파싱 불변 |
| CorrectionResultCard (범위 밖) | 헤더/설명 렌더링 구조 불변(내용이 바로잡히면 자연 구분) |
| explanation 언어/길이 정책 (범위 밖) | COR-TUNE-003 계열 정책 유지 |

---

# 주요 작업

1. **nativeText 규칙 명료화** (`CorrectionPromptBuilder.build`, line 115 인근):
   ```
   - nativeText: a natural 한국어 translation of the corrected sentence (afterText) —
     i.e. what the learner meant to say. Do NOT describe the correction here
     (what was changed belongs ONLY in explanation).
   ```
2. **(선택) 역할 대비 보강**: `explanation` 규칙 줄에 "nativeText와 달리 explanation은 교정 이유"라는 대비를 한 구절 추가.
3. **테스트 보강** (`CorrectionPromptBuilderTest`): nativeText 지시 문구에 "translation of afterText / what the learner meant" 취지와 "do not describe the correction" 가드가 포함되는지 회귀.

---

# 예외 처리

- afterText가 매우 짧거나 단편적 → nativeText도 그에 상응하는 짧은 모국어 의미로(여전히 설명이 아니라 의미).
- primaryLang == selectedLang(모국어=대상 언어) 등 경계 → COR-FIX-007 정렬 정책을 따르며, 본 건은 "설명이 아니라 의미"라는 내용 규칙만 추가한다.
- AI가 여전히 설명성 문장을 넣는 회귀 → 프롬프트 예시/문구를 한 번 더 강화(2-pass 미도입 유지).

---

# 검증 기준

- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- 실기기 교정 카드에서 헤더(`nativeText`)가 "하려던 말(한국어 의미)"로, 'i' 설명과 **다른 문장**으로 나오는지(수동).
- 헤더에 "~를 추가했어요/표현했어요" 같은 교정 설명 문구가 들어가지 않는지(수동).
- 단위 테스트: `CorrectionPromptBuilderTest`에서 nativeText 지시 명료화 회귀, 핵심 4필드/learningSignal 문구 불변.
- AI 호출이 교정 1회당 1회인지(2-pass 미도입) 확인.

```powershell
.\gradlew.bat testDevDebugUnitTest --tests "com.app.umma.data.repository.correction.CorrectionPromptBuilderTest"
```

---

# Out of Scope

- 설명 아이콘 크기/위치 등 카드 시각 정리(→ [COR-UX-005](COR-UX-005_Correction_Result_Card_Readability_Polish.md)).
- explanation 언어/길이 band 정책 변경.
- nativeText 외 필드(afterText/learningSignal) schema·생성 정책 변경.
- 2-pass 호출 도입.
