# [Improvement] COR-TUNE-010 의도 파악 교정 + 세션 기억 연동 + 주제 AI 매핑

## User Story

사용자는 자신이 한 말의 **의도에 맞는** 교정을 받기를 기대한다.
현재 교정은 후보 한 문장과 바로 옆 AI 한마디만 보고 고쳐서 "통째로 번역"하는 느낌이 강하다.
Umma는 이전 대화에서 무엇을 이야기했고(주제) 어떤 핵심 문장을 다뤘는지 기억하고, 현재 세션의 대화 흐름까지 함께 보고서, 학습자의 의도를 파악한 뒤 그 의도에 맞게 자신의 band 수준·길이로 교정해 준다.
사용자는 내부 단계 이름이나 점수를 보지 않고, "내가 하려던 말을 자연스럽게 고쳐줬다"는 결과만 체감한다.

---

# 배경

교정 품질의 핵심은 (1) 대화 맥락에서 학습자의 **의도**를 파악하고 → (2) 그 의도에 맞게 → (3) 학습자 **band 수준/길이**로 고치는 것이다. 현재 파이프라인은 (3)은 잘 되어 있으나 (1)이 약하다.

확인된 현황:

- **세션 기억은 이미 저장되어 있다.** `SessionMemory`([app/src/main/java/com/app/umma/domain/model/realtime/SessionMemory.kt](../../../../app/src/main/java/com/app/umma/domain/model/realtime/SessionMemory.kt))에 `recentTopics`(주제 키워드) / `topicSummaries`(주제별 압축 요약) / `topicKeySentences`(핵심 정답 문장) 필드가 존재하고, 교정 완료 시점에 채워진다.
- **그러나 교정은 이 기억을 전혀 받지 않는다.** `GetCorrectionContextUseCase`([app/src/main/java/com/app/umma/domain/usecase/realtime/GetCorrectionContextUseCase.kt](../../../../app/src/main/java/com/app/umma/domain/usecase/realtime/GetCorrectionContextUseCase.kt))는 raw `List<SessionTurn>`만 반환하고, 프롬프트에는 후보별 `assistantContext`(바로 옆 AI 한마디)만 들어간다([CorrectionPromptBuilder.kt:89-96](../../../../app/src/main/java/com/app/umma/data/repository/correction/CorrectionPromptBuilder.kt)).
- **recentTopics 생성은 COR 소관이다.** `BuildSessionCompressionPayloadUseCase.extractRecentTopics()`([app/src/main/java/com/app/umma/domain/usecase/correction/BuildSessionCompressionPayloadUseCase.kt:91](../../../../app/src/main/java/com/app/umma/domain/usecase/correction/BuildSessionCompressionPayloadUseCase.kt))가 코드 단어빈도(stopword 필터)로 만든다. 팀장 요청은 이를 AI 매핑으로 전환하는 것이다.
- **topicSummaries는 이중 쓰기 상태다.** `CompleteCorrectionUseCase`([app/src/main/java/com/app/umma/domain/usecase/correction/CompleteCorrectionUseCase.kt](../../../../app/src/main/java/com/app/umma/domain/usecase/correction/CompleteCorrectionUseCase.kt)) 2단계(AI `SummarizeRecentTopicsUseCase`)와 5단계(코드 `before→after` 압축)가 같은 컬럼에 쓴다 → SSOT 정리 필요.
- **출력 길이/수준 조절(항목 3)은 이미 구현되어 있다.** band별 정책 라인 + `bandFewShotExample` + `CorrectionOverexpansionGuard`. PM 지침에 따라 단어단위 교정은 도입하지 않는다.

방향은 **"1-pass 기존 자산 재사용"**이다. 별도 의도 추출 AI 호출(2-pass)을 추가하지 않고, 이미 저장된 세션 기억 + 현재 세션 대화 흐름을 한 프롬프트에 실어 AI가 한 번에 의도 파악 후 교정하게 한다. 새 AI 호출을 늘리지 않아 비용·지연을 보존한다.

---

# 완료 기준(AC)

## (A) 의도 파악용 맥락 주입 (읽기 연결)

- [ ] 교정 입력에 이전 세션 기억(`topicSummaries` / `topicKeySentences` / `recentTopics`)을 함께 확보한다. `SessionMemoryRepository.getSessionMemory(lang)`([app/src/main/java/com/app/umma/domain/repository/SessionMemoryRepository.kt:39](../../../../app/src/main/java/com/app/umma/domain/repository/SessionMemoryRepository.kt))를 재사용한다(새 저장소 메서드 신설 금지).
- [ ] 현재 세션 대화 흐름을 의도 파악에 충분한 범위로 제공한다. (후보 1문장 + 옆 AI 한마디보다 넓은 맥락)
- [ ] `GenerateSuggestionsInput`([app/src/main/java/com/app/umma/domain/model/correction/CorrectionContracts.kt](../../../../app/src/main/java/com/app/umma/domain/model/correction/CorrectionContracts.kt))에 세션 맥락 필드를 추가한다. (candidates/langState/primaryLang/profile 외)
- [ ] `CorrectionPromptBuilder`가 Candidates 블록 **위**에 "Conversation context / intent" 블록을 추가해, AI가 후보 교정 전에 의도를 먼저 파악하도록 지시한다.
- [ ] 토큰 비대 방지: 주입량 상한을 명시한다(예: 요약 최대 N개, 핵심문장 최대 N개, 현재 세션 turn 최대 N개). 상한은 상수로 두고 KDoc에 근거를 남긴다.
- [ ] 응답 schema 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`)와 learningSignal 규칙(COR-TUNE-002-FIX 강화판)은 변경하지 않는다.

## (B) recentTopics/topicSummaries AI 매핑 (쓰기)

- [ ] `BuildSessionCompressionPayloadUseCase`의 코드 단어빈도 `recentTopics` 생성을 AI 매핑 기반으로 전환한다. 기존 `SummarizeRecentTopicsUseCase`([app/src/main/java/com/app/umma/domain/usecase/realtime/](../../../../app/src/main/java/com/app/umma/domain/usecase/realtime/)) AI 경로 재사용을 우선 검토한다(중복 AI 호출 신설 지양).
- [ ] `topicSummaries` 이중 쓰기를 정리한다. `CompleteCorrectionUseCase` 2단계(AI)와 5단계(코드) 중 **AI 결과를 SSOT로** 두고, 코드 `before→after`는 보조/폴백으로만 남기거나 제거한다.
- [ ] 정리 후에도 교정 완료 흐름(저장→요약→LangState→통계→압축)의 단계 순서·실패 폴백(pending only)이 깨지지 않는다.

## (C) 출력 길이/수준 — 현행 유지 확인 (항목 3)

- [ ] band별 길이/수준 조절이 이미 동작함을 문서로 확인한다(`sentenceExpansionLine`/`grammarCorrectionLine`/`vocabularyGrowthLine` + `bandFewShotExample` + `CorrectionOverexpansionGuard`). **재구현하지 않는다.**
- [ ] **단어단위(word-to-word) 교정은 도입하지 않는다**(PM 지침). 교정 출력은 항상 band에 맞는 문장 형태/길이로 만든다.
- [ ] (선택) 의도 맥락 주입 후 교정문이 과도하게 길어지지 않는지 `CorrectionOverexpansionGuard` 임계값 재점검 포인트만 기록한다.

---

# 기준 문서

- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-REF-001_Correction_Band_Derivation_Reference.md` (band 산출·교정 강도 기준)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-005_Band_FewShot_Prompt_Tuning.md` (band few-shot — 출력 형태 정렬, 유지 대상)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-006_Correction_Overexpansion_Runtime_Guard.md` (길이/의미 과확장 가드, 유지 대상)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002-FIX_Correction_Learning_Signal_Normalization_Hardening.md` (learningSignal 규칙 — 보존 대상)
- `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-003_Turn_Commit.md` (Session Memory turn/압축 소유 경계)

---

# 핵심 결정

- **의도 파악은 1-pass.** 별도 의도 추출 AI 호출을 추가하지 않는다. 이미 저장된 세션 기억 + 현재 세션 흐름을 한 프롬프트에 실어 AI가 한 번에 의도 파악→교정. (2-pass는 1-pass로 의도 파악이 부족하다고 판명될 때의 후속 카드)
- **기억의 출처는 SessionMemory 재사용.** `getSessionMemory(lang)`로 읽기만 한다. 저장/생성 시점·소유는 RT-003/완료 파이프라인 그대로.
- **읽기(주입)와 쓰기(AI 매핑)를 한 이슈에서 다룬다.** 주제 데이터의 "생성 품질(쓰기)"과 "활용(읽기)"이 같은 흐름이라 분리하면 정합성 검증이 어렵다.
- **topicSummaries SSOT는 AI 결과.** 코드 `before→after` 요약은 AI 실패 시 폴백 또는 제거 대상.
- **항목 3은 신규 구현 아님.** 길이/수준 band 정렬은 이미 존재 → 확인·튜닝만. 단어단위 교정은 명시적으로 제외(PM 지침).
- 내부 band 이름·점수·레벨·raw metric은 프롬프트 표면에 노출하지 않는다(기존 원칙 유지).

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| GetCorrectionContext / CorrectionViewModel | 세션 기억(`getSessionMemory`) + 현재 세션 흐름을 확보해 교정 입력으로 전달 |
| GenerateSuggestionsInput (domain 계약) | 세션 맥락 필드 추가. 의미/구조 변경은 이 계약에서 한 번만 |
| CorrectionPromptBuilder | 맥락 블록을 행동 지시로 변환. 핵심 4필드·learningSignal 규칙·few-shot은 유지 |
| BuildSessionCompressionPayloadUseCase | recentTopics/topicSummaries 생성을 AI 매핑으로 전환 |
| CompleteCorrectionUseCase | topicSummaries 이중 쓰기 정리, 단계 순서·폴백 유지 |
| CorrectionAiResponseMapper (범위 밖) | 응답 파싱/learningSignal 정규화 — 본 작업에서 변경하지 않음 |
| SessionMemory 저장 (RT-003, 범위 밖) | 저장/압축 실행 — 읽기만 재사용, 쓰기 구조는 건드리지 않음 |

---

# 주요 작업

1. **세션 맥락 확보** (`CorrectionViewModel.triggerGeneration`): 기존 `getCorrectionContext(lang)`에 더해 `getSessionMemory(lang)`로 topicSummaries/topicKeySentences/recentTopics 확보. 실패 시 빈 맥락으로 폴백(교정은 계속).
2. **입력 계약 확장** (`GenerateSuggestionsInput`): 세션 맥락 필드 추가 + KDoc.
3. **프롬프트 맥락 블록** (`CorrectionPromptBuilder`): Candidates 위에 "이전 대화 주제/핵심문장 + 현재 대화 흐름 → 의도 파악 후 교정" 지시 추가. 주입 상한 상수화.
4. **recentTopics/topicSummaries AI 매핑** (`BuildSessionCompressionPayloadUseCase` + `SummarizeRecentTopicsUseCase` 재사용 검토).
5. **이중 쓰기 정리** (`CompleteCorrectionUseCase`): topicSummaries SSOT를 AI로 확정.
6. **항목 3 확인 노트**: 길이/수준 현행 동작 + 단어교정 제외를 문서/주석으로 못 박음.
7. **테스트 보강**: 프롬프트에 맥락 블록이 들어가는지, 주입 상한이 지켜지는지, topicSummaries SSOT가 AI인지, 핵심 4필드/learningSignal 회귀.

---

# 예외 처리

- 세션 기억이 비어 있거나 조회 실패 시 → 빈 맥락으로 폴백, 교정은 현재 세션 turn 기반으로 계속한다(완료 막지 않음).
- 주입 맥락이 토큰 상한을 넘으면 → 최신/고빈도 우선으로 잘라낸다.
- recentTopics AI 매핑 실패 시 → 기존 코드 단어빈도로 폴백하거나 빈 목록으로 두되, 압축 흐름은 진행한다(pending only).
- 제3언어가 섞인 발화의 의도/주제도 맥락에 포함될 수 있으나, **평가 제외는 본 이슈 범위 아님**(COR-TUNE-011 담당).

---

# 검증 기준

- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- 프롬프트에 "이전 대화 주제/핵심문장 + 현재 흐름" 맥락 블록이 Candidates 위에 노출되는지(`CorrectionPromptBuilderTest`).
- 주입량이 상한을 넘지 않는지.
- AI 호출 횟수가 교정 1회당 1회로 유지되는지(2-pass 도입 안 됨 확인).
- topicSummaries가 AI 결과로 저장되는지(코드 before→after가 AI를 덮어쓰지 않는지).
- band별 길이/수준 조절·과확장 가드가 회귀 없이 유지되는지.
- prompt에 raw metric(`\d\.\d{2}`)·band 이름·점수/레벨이 노출되지 않는지.
- 핵심 4필드·learningSignal 규칙(COR-TUNE-002-FIX 강화 문구)이 그대로 유지되는지.
