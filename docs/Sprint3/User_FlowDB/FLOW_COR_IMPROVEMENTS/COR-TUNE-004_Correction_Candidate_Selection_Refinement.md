# [Improvement] COR-TUNE-004 교정 후보 선정 고도화 (사소한·과도하게 긴 발화 처리)

## User Story

사용자는 교정 화면에서 "Hi", "ok" 같은 인사말이나 한두 단어짜리 사소한 발화까지 굳이 교정 카드로 받지 않기를 기대한다.
또 5문장을 한 번에 말한 긴 발화가 통째로 길고 불친절하게 고쳐지는 대신, 따라갈 수 있는 핵심 단위로 정리되기를 기대한다.
Umma는 교정 결과를 만들기 전에 "무엇을 교정 대상으로 고를지"를 먼저 깔끔하게 거른다. 교정할 게 없으면 화면이 깨지지 않고 빈 상태로 안전하게 처리한다.

---

# 배경

현재 후보 추출은 `ExtractCandidatesUseCase`가 담당한다.
이 UseCase는 최근 문맥(`MAX_SOURCE_TURNS=100`) 안에서 USER turn을 가져와 공백만 있는 발화만 제외하고 거의 그대로 교정 후보로 만든다.
그 결과 "Hi" 같은 사소한 발화도 후보가 되고, 여러 문장으로 된 긴 발화도 통째로 한 후보가 되어 교정 결과가 너무 길고 불친절해진다.

교정 결과 품질·길이 문제의 상당 부분은 출력 단계가 아니라 "무엇을 교정 대상으로 고르냐"는 **입력 단계**에서 발생한다.
이 작업은 입력 후보를 먼저 정제하는 것이며, 출력 과확장 방어(`COR-TUNE-006`)와는 별개인 **선행 입력 정제 단계**다.

후보 추출 도메인 로직은 `ExtractCandidatesUseCase`에 있고, RT-003 Session Memory read model → 후보 변환·원본 `turnId` 보존은 경계 어댑터인 `ExtractSessionCandidatesUseCase`가 이미 처리한다.
이 작업은 도메인 필터(`ExtractCandidatesUseCase`)에 필터를 추가하되, 경계 어댑터가 채워 주는 `sourceTurnId`·`sourceTurnIndex` 추적이 깨지지 않게 유지하는 것이 핵심이다.

응답 schema의 핵심 4필드(`candidateId`/`nativeText`/`afterText`/`explanation`)와 `CorrectionLearningSignal v2` 출력 계약, 완료 저장 흐름은 변경하지 않는다.

---

# 완료 기준(AC)

- [ ] 사소한 발화(인사말, 한두 단어, 이미 정확한 짧은 표현 등)는 교정 후보에서 제외한다. (예: "Hi", "ok")
- [ ] 과도하게 긴 발화(여러 문장)는 통째로 교정하지 않는다. 핵심 문장 단위로 자르거나 후보당 길이 상한을 적용한다.
- [ ] 이미 교정한 turn / 중복되는 후보는 제외한다. (가능 범위 내)
- [ ] 후보가 0개여도 교정 화면이 깨지지 않고 "교정할 내용 없음" 빈 상태로 안전하게 처리된다.
- [ ] 후보 선정은 도메인(`ExtractCandidatesUseCase`)에서 처리하고, 잘린/필터된 후보도 `candidateId`·`sourceTurnIndex` 추적이 유지된다.
- [ ] 사소한 발화 제외 기준(최소 길이/단어 수, 인사말 패턴)과 후보당 길이 상한은 상수로 분리한다.
- [ ] 장문 분할 시 같은 원본 turn에서 나온 여러 후보의 `candidateId`가 충돌하지 않고 `sourceTurnIndex` 추적도 누락되지 않는다.

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md` (교정 결과 생성 흐름 / 후보→suggestion 계약)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-003_Correction_Growth_Policy.md` (성장 정책 / 의미·길이 과변경 방어 방향)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-006_Correction_Overexpansion_Runtime_Guard.md` (후속 출력 길이 단속 — 본 입력 정제와 별개)

---

# 핵심 결정

- 후보 정제는 **도메인 필터**(`ExtractCandidatesUseCase`)에서 처리한다. 화면이나 repository가 아니라 후보 추출 UseCase가 "무엇이 교정 대상인지"의 단일 출처를 갖는다.
- 사소한 발화 기준(최소 길이/단어 수·인사말 패턴)과 후보당 길이 상한은 **상수로 분리**해, 휴리스틱 임계값을 한곳에서 조정한다. (`MAX_SOURCE_TURNS`와 동일 패턴)
- 장문 처리는 **핵심 문장 단위 분할 또는 후보당 길이 상한**으로 한다. 분할 시 같은 원본 turn에서 나온 후보들의 `candidateId`는 충돌하지 않도록 분할 인덱스를 식별자에 반영하고, 모든 분할 후보의 `sourceTurnIndex`는 원본 turn 순서를 그대로 유지한다.
- "이미 교정한 turn / 중복 후보 제외"는 **가능 범위 내**에서 처리한다. 정규화(공백·대소문자) 후 같은 `sourceText`는 한 후보로 본다. local-first 추적 한계를 넘는 과설계는 하지 않는다.
- 본 작업은 **입력 정제 전용**이다. 교정문 출력 길이 단속은 `COR-TUNE-006`에서 별도로 다룬다. 두 작업을 한 PR에 섞지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| ExtractCandidatesUseCase | 사소한 발화 제외·장문 분할/상한·중복 제외 필터를 추가한다. 분할/필터 후에도 `candidateId`·`sourceTurnIndex` 추적을 유지한다. |
| ExtractSessionCandidatesUseCase (경계) | RT-003 read model → 후보 변환과 원본 `turnId` 보존을 이미 담당. 본 작업에서 구조 변경하지 않는다. (분할 후보에도 `sourceTurnId` 보존이 깨지지 않는지만 회귀 확인) |
| CorrectionCandidate (domain) | `id`/`sourceTurnIndex`/`sourceTurnId` 추적 필드 계약. 분할 후보 식별에 필요하면 최소 보강만 검토한다. |
| Correction 화면/ViewModel | 후보 0개 → 빈 상태("교정할 내용 없음")로 안전 처리. (기존 빈 상태 흐름 재사용) |
| CorrectionAiResponseMapper (범위 밖) | 핵심 4필드·learningSignal 정규화. 본 작업에서 변경하지 않는다. |
| COR-TUNE-006 (범위 밖) | 교정문 출력 과확장 런타임 가드. 본 입력 정제의 후속이다. |

---

# 주요 작업

1. **사소한 발화 제외 필터 추가** (`ExtractCandidatesUseCase`)
   - 최소 길이/단어 수, 인사말 패턴 등 제외 기준을 companion 상수로 분리한다. (`MAX_SOURCE_TURNS`와 같은 위치·패턴)
   - 공백 trim 후 사소한 발화로 판정된 turn은 후보에서 제외한다. 라인 주석으로 "왜 제외하는지(교정 가치 없는 입력)"를 남긴다.

2. **장문 발화 처리** (동 UseCase)
   - 핵심 문장 단위 분할 또는 후보당 길이 상한 적용. 둘 중 택1 또는 병행을 구현 시 결정하고 KDoc에 명시한다.
   - 분할 시 `buildCandidateId`가 같은 원본 turn에서 여러 후보를 만들어도 충돌하지 않도록 분할 인덱스를 식별자 구성에 반영한다. 모든 분할 후보의 `sourceTurnIndex`는 원본 turn 순서를 유지한다.

3. **이미 교정한 turn / 중복 후보 제외** (동 UseCase)
   - 정규화(공백·대소문자) 후 동일 `sourceText`는 한 후보로 본다. 가능 범위 내에서 직전 교정 turn을 제외한다.

4. **빈 상태 회귀 확인** (Correction 화면/ViewModel)
   - 모든 발화가 필터되어 후보 0개여도 기존 빈 상태("교정할 내용 없음")로 안전하게 떨어지는지 확인한다. 새 흐름 추가가 아니라 기존 빈 상태 재사용을 우선한다.

5. **테스트 보강**
   - `ExtractCandidatesUseCaseTest` / `ExtractSessionCandidatesUseCaseTest`: "Hi"·한 단어 → 후보 제외 / 5문장 발화 → 분할 또는 상한 적용 / 정상 발화 → 후보 유지 / 분할 후보 `candidateId` 충돌 없음·`sourceTurnIndex` 유지 / 후보 0개 → 빈 결과.

---

# 예외 처리

- 모든 발화가 필터되면 후보 0개 → 빈 상태로 정상 처리(에러 아님).
- 장문 분할 시 같은 원본 turn에서 나온 후보들의 `candidateId` 충돌/추적 누락을 방지한다.
- 기준언어 혼합 발화나 아주 짧지만 교정 가치가 있는 발화의 경계는 보수적으로 처리한다. (애매하면 후보 유지 쪽으로 — 교정 가치 있는 발화를 잘못 버리지 않는다)
- `selectedLang != sessionLang`이거나 최근 문맥이 비면 기존대로 빈 리스트를 반환한다. (현 동작 유지)
- 분할 후보에 `sourceTurnId`를 다시 붙이는 경계 어댑터(`ExtractSessionCandidatesUseCase`) 로직이 분할로 인해 어긋나지 않는지 확인한다. (현재 `sourceTurnIndex` 기반 매칭)

---

# 검증 기준

- `ExtractCandidatesUseCaseTest`:
  - "Hi"·"ok"·한 단어 발화 → 후보 제외 확인.
  - 5문장 발화 → 분할되거나 후보당 길이 상한이 적용되는지 확인.
  - 정상 발화 → 후보 유지 확인.
  - 분할 후보들의 `candidateId`가 서로 다르고 `sourceTurnIndex`가 원본 순서를 유지하는지 확인.
  - 모든 발화 필터 시 빈 리스트 반환 확인.
- `ExtractSessionCandidatesUseCaseTest`: 분할/필터 후에도 `sourceTurnId` 보존이 깨지지 않는지 회귀 확인.
- 후보 0개 시 Correction 화면이 빈 상태로 떨어지는지 확인.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
- 응답 schema 핵심 4필드·`CorrectionLearningSignal v2`·완료 저장 흐름이 변경되지 않았는지 확인.
