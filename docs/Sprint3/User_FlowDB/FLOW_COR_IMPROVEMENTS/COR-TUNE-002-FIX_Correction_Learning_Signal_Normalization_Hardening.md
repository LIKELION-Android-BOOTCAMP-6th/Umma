# [Improvement] COR-TUNE-002-FIX 교정 학습 신호(CorrectionLearningSignal) 정규화 정책 강화

## User Story

사용자가 교정을 받을수록, 시스템은 그 교정에서 관찰된 신호를 장기 학습 상태(LangState)에 반영한다.
이때 신호가 불확실하면(의미 보존 여부 미확인·문서에 없는 enum) 일부만 살려 반영하기보다 보수적으로 버려야,
검증되지 않은 관찰이 사용자의 장기 실력 추정을 왜곡하지 않는다.
교정 결과 문장(사용자에게 보이는 카드/Flashcard)은 그대로 살리고, 신뢰할 수 없는 learningSignal만 제외한다.

---

# 배경

`COR-TUNE-002`는 교정 AI가 LearningState에 제공하는 관찰 신호 출력 계약을 고정하고, `CorrectionAiResponseMapper`가 이를 정규화하도록 했다.
그러나 구현된 정규화가 **계약(COR-TUNE-002)보다 느슨하게** 동작하는 부분이 있어 이를 보수적 drop 방향으로 정합화한다.

발견된 갭은 두 가지다.

1. **meaningPreserved 누락 처리** — `meaningPreserved`는 "교정 후 문장이 사용자의 원래 의도를 유지했는지"를 나타내는 핵심 방어값이다.
   기존 mapper는 AI 응답에 값이 없으면 `true`로 기본 처리했다. 이 경우 의미 보존 여부가 실제로 확인되지 않았는데도
   LearningState가 장기 실력 계산에 반영할 수 있다.
2. **unknown enum 처리** — `issueCategories`/`improvementTypes`/`register`/`severity`는 문서에 정의된 enum 값만 허용해야 한다.
   기존 mapper는 `register`/`severity` unknown은 signal drop이지만, `issueCategories`/`improvementTypes`는 unknown 원소만 제외하고 signal은 유지했다.
   이는 계약 문서(`COR-TUNE-002` 위반처리·검증 기준)가 이미 명시한 "unknown enum → 해당 signal drop"과 어긋난다.

요약하면 교정 결과 문장은 살리되, learningSignal은 장기 LangState 계산 입력이므로 **불확실하면 보수적으로 signal 전체를 drop**하는 방향으로 맞춘다.
응답 schema·핵심 4필드·도메인 타입·완료 경로(`CorrectionResult.learningSignals`)는 변경하지 않는다.

---

# 완료 기준(AC)

- [x] `meaningPreserved`가 누락(키 없음)된 learningSignal은 signal 전체를 drop한다. `true`/`false`는 모두 유효(특히 `false`는 의미 변형을 알리는 중요 신호). 기존 `?: true` 기본값을 제거한다.
- [x] `issueCategories`/`improvementTypes`에 unknown enum 원소가 하나라도 있으면 그 원소만 제외하지 않고 **signal 전체를 drop**한다. 전부 유효하면 최대 3개로 캡한다.
- [x] `editSpans`의 `issueCategory`/`improvementType`이 unknown이면 그 span만 제외하지 않고 **signal 전체를 drop**한다. `languageFeatureKey`는 형식 위반 시 null로 비우되 span은 유지한다(보조 정보).
- [x] `languageFeatures.featureKey`는 namespace/lang/allowlist 위반 시 **그 feature만 제외**하고 signal은 유지한다. (유일한 부분 제외 예외, 정책 유지)
- [x] 기존 동작은 회귀 없이 유지한다: `register`/`severity` unknown drop, `confidence` 범위 검증(0.0~1.0, 누락 null 허용), 배열 ≤3 캡, `runCatching`으로 핵심 4필드 보호, learningSignal 누락 → suggestion 생존.
- [x] `CorrectionPromptBuilder`가 `meaningPreserved`를 항상 포함하도록 지시하고, 허용 enum 목록 밖 값은 learningSignal 전체가 폐기됨을 명시한다.
- [x] 계약 문서 `COR-TUNE-002`의 위반처리/예외/검증 섹션을 강화된 정책(meaningPreserved 누락 drop, editSpan unknown enum drop, 보수적 drop 원칙)으로 갱신한다.
- [x] 응답 schema·핵심 4필드·도메인 타입·`CorrectionResult.learningSignals` 집계 경로는 변경하지 않는다.

---

# 기준 문서

- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-002_Correction_Learning_Signal_Output.md` (정합화 대상 출력 계약 / 위반처리 / 검증 기준)
- `docs/handover/CHAT-TUNE-001_CORRECTION_LEARNING_SIGNAL_HANDOVER.md` (Domain 계약 v2 / meaningPreserved 의미 / enum 정의)
- `docs/System_FlowDB/SYS_CORRECTION_INFRA/SCI-001_Correction_Contract.md`

---

# 핵심 결정

- learningSignal은 장기 LangState 계산의 **입력**이다. 불확실한 신호를 일부만 살려 넘기면 장기 실력 추정이 오염되므로, "불확실하면 signal 전체를 drop"을 정규화 전 구간에 일관 적용한다.
- 교정 결과 문장(핵심 4필드)은 그대로 살린다. signal drop은 `suggestion.learningSignal`만 null로 비울 뿐, 사용자에게 보이는 교정 카드/Flashcard 저장 흐름은 무손상이다.
- `meaningPreserved`는 `register`/`severity`와 동급의 단일 필수값으로 취급한다. 누락은 "검증되지 않음"이지 "의미 보존됨"이 아니다.
- `languageFeatures.featureKey`만 부분 제외 예외를 유지한다. featureKey는 allowlist를 점진 확장하는 보조 정보라, 미등록 feature 하나로 signal 전체를 버리면 손실이 크다.
- 본 작업은 `COR-TUNE-002`의 정규화 정책 강화(하드닝)이며, 도메인 타입·응답 schema·완료 경로는 변경하지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionAiResponseMapper | AI 응답 DTO를 domain 신호 계약으로 정규화한다. 불확실한 signal은 보수적으로 drop하고, featureKey만 부분 제외한다. 핵심 4필드 검증은 유지한다. |
| CorrectionPromptBuilder | 응답 schema의 신호 규칙에 meaningPreserved 필수·허용 enum 외 값 폐기를 명시한다. schema 키 구성은 유지한다. |
| CorrectionLearningSignal (domain, 범위 밖) | 신호 계약 자체는 변경하지 않는다. |
| LearningState domain (범위 밖) | drop되지 않고 넘어온 신호만 입력으로 evidence/focus/score를 갱신한다. |
| Repository/DataSource (범위 밖) | 변환된 결과를 저장/전달할 뿐, 신호의 교육적 의미를 해석하지 않는다. |

---

# 주요 작업

1. **CorrectionAiResponseMapper.normalizeLearningSignal 강화** (`data/repository/correction/CorrectionAiResponseMapper.kt`)
    - `register`/`severity` 필수값 블록 옆에 `meaningPreserved == null → logDroppedSignal + return null` 추가. 생성자의 `?: true` 기본값 제거.
    - `issueCategories`/`improvementTypes`를 관대 `mapNotNull`에서 엄격 파싱으로 교체. 신규 inline reified 헬퍼 `parseEnumListStrictOrDrop`가 첫 unknown 원소에서 로그 후 null을 돌려 signal 전체를 drop한다. 전부 유효하면 `take(3)` 캡.
    - `editSpans`: `normalizeEditSpan`의 unknown 처리를 `logExcludedItem` → `logDroppedSignal`로 바꾸고, 호출부를 명시적 루프 `?: return null`(signal drop)로 변경. `languageFeatureKey` 형식 위반은 기존대로 null 처리(span 유지).
    - KDoc/라인 주석을 강화된 정책으로 갱신. featureKey 부분 제외 예외는 명시 유지.
2. **CorrectionPromptBuilder 지시 강화** (`data/repository/correction/CorrectionPromptBuilder.kt`)
    - `meaningPreserved`를 "항상 포함 — 누락 시 learningSignal 폐기"로 수정.
    - `issueCategories`/`improvementTypes`에 "허용 목록 밖 값은 learningSignal 전체를 폐기"를 명시.
3. **계약 문서 갱신** (`COR-TUNE-002_Correction_Learning_Signal_Output.md`)
    - 위반처리(AC)·예외 처리·검증 기준 섹션에 meaningPreserved 누락 drop, issue/improvement/editSpan unknown enum 전체 drop, featureKey 부분 제외 예외를 반영.
4. **테스트 반영** (`CorrectionAiResponseMapperTest`)
    - `signalJson` 헬퍼에 `meaningPreserved` 생략 옵션 추가.
    - 기존 "issue category 원소 제외 + signal 유지" 케이스를 "signal drop"으로 변경.
    - improvementType unknown / editSpan unknown enum / meaningPreserved 누락 / meaningPreserved=false 케이스 신규 추가.

---

# 예외 처리

- `meaningPreserved=false` → signal 유지(중요 신호). 누락(키 없음)만 drop.
- `issueCategories`/`improvementTypes`/`editSpans`가 **빈 배열**이면 signal 유지. unknown enum 원소가 하나라도 있을 때만 signal 전체 drop.
- unknown/형식 위반 featureKey → 그 feature만 제외, signal 전체는 유지. (editSpan의 `languageFeatureKey`도 형식 위반 시 null로 비우되 span은 유지)
- learningSignal 누락 / 파싱 실패 → suggestion은 생존(`learningSignal=null`). 핵심 4필드 누락만 candidate 전체 실패.
- `confidence` 범위 밖 → 해당 signal drop. 없으면 null 허용(LearningState가 medium-low로 취급).

---

# 신호 정규화 원칙

- mapper는 "신뢰할 수 없으면 버린다"를 기본값으로 둔다. unknown enum이 섞이거나 핵심 방어값(meaningPreserved)이 누락되면 부분 제외하지 않고 signal 전체를 drop한다.
- 오염된 신호 하나가 교정 결과 저장(핵심 4필드) 전체를 막지 않는다.
- featureKey만 부분 제외 예외를 둔다(allowlist 점진 확장 대상인 보조 정보).

---

# 검증 기준

- `CorrectionAiResponseMapperTest`:
    - `meaningPreserved` 누락 → signal=null·suggestion 생존 / `false` → signal 유지(meaningPreserved=false) 확인.
    - unknown issueCategory/improvementType 원소 → signal=null 확인.
    - unknown editSpan enum → signal=null 확인.
    - 회귀: unknown register/severity → drop, confidence 범위/누락, 배열 ≤3 캡, featureKey/lang 위반 → 그 feature만 제외·signal 유지, learningSignal 누락 → suggestion 생존.
- `CorrectionPromptBuilderTest`: schema 키(meaningPreserved/issueCategories/improvementTypes 등)와 허용 enum 노출 유지 확인.
- 다운스트림 `CompleteCorrectionUseCaseTest`/`LearningStateWriteUseCasesTest` 회귀 없음(도메인 객체 직접 생성이라 무영향).
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- 응답 schema·핵심 4필드·`CorrectionResult.learningSignals` 집계 경로가 변경되지 않았는지 확인.
