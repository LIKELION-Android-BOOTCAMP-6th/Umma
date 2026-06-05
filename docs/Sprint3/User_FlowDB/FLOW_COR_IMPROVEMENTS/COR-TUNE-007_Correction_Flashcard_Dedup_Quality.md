# [Improvement] COR-TUNE-007 교정 Flashcard 중복 방지 및 품질 정리

## User Story

사용자는 교정 → flashcard 저장을 반복해도 복습 목록이 유사·중복 카드로 지저분해지지 않기를 기대한다.
교정 전후가 사실상 같은 저품질 카드도 복습에 쌓이지 않기를 기대한다.
Umma는 저장 파이프라인 끝단에서 카드 중복·품질을 정리해 복습 경험을 깨끗하게 유지한다.
중복·제외 처리는 사용자에게 저장 실패처럼 보이지 않게 자연스럽게 처리된다.

---

# 배경

교정 → flashcard 저장은 `PrepareSaveRequestUseCase`가 선택된 suggestion을 `CorrectionSaveRequest`로 정리하고, `CompleteCorrectionUseCase`가 local-first 저장 + LangState/Summary 갱신 + Firestore sync를 묶는다.
현재 `PrepareSaveRequestUseCase`는 `distinctBy { it.id }`로 **같은 suggestion id의 중복 선택**만 제거한다.
앞/뒤 텍스트가 동일하거나 매우 유사한 카드, 교정 전후가 사실상 같은 저품질 카드는 그대로 저장될 수 있다.

저장이 반복되면 유사/중복 카드가 쌓이고 저품질 카드도 섞인다.
이 작업은 파이프라인 끝단에서 카드 중복·품질을 정리한다.

입력(`COR-TUNE-004`)·출력(`COR-TUNE-005`/`006`)이 정리된 뒤라 카드 품질도 함께 올라가지만, 저장 끝단의 dedup·품질 필터는 별도로 필요하다.
기존 저장/rollback/Firestore sync 흐름(`CompleteCorrectionUseCase`)은 무손상으로 유지한다.

---

# 완료 기준(AC)

- [ ] 앞/뒤 텍스트가 동일하거나 매우 유사한 교정 카드의 중복 저장을 방지한다. (병합 또는 skip)
- [ ] 저품질 카드(교정 전후 동일, 지나치게 짧음 등)는 저장에서 제외한다.
- [ ] 기존 저장 / rollback / Firestore sync 흐름은 무손상으로 유지한다.
- [ ] 중복/제외 시 사용자에게 저장 실패처럼 보이지 않게 자연스럽게 처리한다.
- [ ] 유사도 기준(대소문자·문장부호·공백 정규화 후 비교)을 정의한다.

---

# 기준 문서

- `docs/Sprint2/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md` (suggestion → 저장 계약)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-003_Correction_Growth_Policy.md` (교정 강도 정책 / 품질 방향)
- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-004_Correction_Candidate_Selection_Refinement.md` (입력 정제 — 본 끝단 정리의 선행)

---

# 핵심 결정

- dedup·품질 필터는 **저장 요청 정리 단계**(`PrepareSaveRequestUseCase`)에 두는 것을 우선 검토한다. 이미 `distinctBy { it.id }`로 중복 제거 책임을 가진 위치라 자연스럽다. 단 카드 저장소 관점의 기존 카드와의 중복까지 보려면 `CorrectionFlashcardStore`/`CompleteCorrectionUseCase` 단계가 필요할 수 있어 구현 시 위치를 확정한다.
- **유사도 기준**: 대소문자·문장부호·공백을 정규화한 뒤 `frontText`/`backText`를 비교한다. 정규화 후 동일하면 중복으로 본다. 정규화 규칙을 상수/유틸로 분리한다.
- **저품질 기준**: 교정 전후(`beforeText` ≈ `afterText`)가 정규화 후 동일하거나, 지나치게 짧은 카드는 제외한다. 임계값은 상수로 분리한다.
- **자연스러운 처리**: 중복/제외로 저장 카드 수가 줄어도 사용자에게 저장 실패로 보이지 않게 한다. 모든 카드가 제외되는 경우의 흐름도 정의한다(저장 성공으로 보되 새로 저장된 카드 0개).
- **무손상 보장**: `CompleteCorrectionUseCase`의 local-first 저장·rollback(이번 요청에서 새로 저장된 카드만 대상)·Firestore sync 흐름은 그대로 둔다. local-first와 sync 양쪽이 같은 dedup 결과를 보도록 한쪽만 중복 제거되지 않게 한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| PrepareSaveRequestUseCase | 선택 suggestion → 저장 요청 정리. 텍스트 기준 dedup·품질 필터 추가(위치 후보 1순위). 현재 `distinctBy { it.id }` 확장. |
| CorrectionFlashcardStore / CompleteCorrectionUseCase | 기존 저장 카드와의 중복까지 봐야 하면 이 단계에서 dedup 보강. 저장/rollback/sync 흐름 무손상. |
| CorrectionSuggestion (domain) | `beforeText`/`afterText`/`nativeText` 비교 대상 필드 제공. **무변경**. |
| Firestore sync (범위 밖 구조) | local-first 결과와 동일한 dedup 결과를 sync한다. 한쪽만 중복 제거되지 않게 한다. |

---

# 주요 작업

1. **유사도 정규화 유틸 정의**
   - 대소문자·문장부호·공백을 정규화하는 비교 함수를 추가한다. 정규화 규칙을 한곳에 모은다.

2. **dedup·품질 필터 추가** (`PrepareSaveRequestUseCase` 우선, 필요 시 store/complete 단계)
   - 정규화 후 `frontText`/`backText`가 같은 카드는 병합 또는 skip한다.
   - 교정 전후 동일·과도하게 짧은 저품질 카드는 제외한다.
   - 필터 후 저장 요청을 만든다. 기존 `distinctBy { it.id }` 뒤에 텍스트 기준 dedup을 잇는다.

3. **빈 결과/부분 제외 흐름 정의** (`CompleteCorrectionUseCase` 회귀 확인)
   - 일부/전부 제외돼도 사용자에게 저장 실패로 보이지 않게 한다. rollback이 이번 요청에서 새로 저장된 카드만 대상으로 하는 현 동작과 충돌하지 않는지 확인한다.

4. **테스트 보강**
   - `PrepareSaveRequestUseCaseTest`(및 필요 시 `CompleteCorrectionUseCaseTest`): 동일/유사 카드 입력 → 중복 제거 / 교정 전후 동일 카드 → 제외 / 정상 카드 → 저장 / 전부 제외 → 자연스러운 처리.
   - 저장·rollback·sync 회귀 확인.

---

# 예외 처리

- 대소문자/문장부호만 다른 사실상 중복은 정규화 후 동일로 본다.
- local-first 저장과 Firestore sync 간 정합: 한쪽만 중복 제거되지 않게 동일 dedup 결과를 양쪽이 본다.
- 의도적으로 비슷하지만 다른 학습 포인트의 카드는 구분한다. (애매하면 보존 쪽 — 정당한 카드를 잘못 버리지 않는다)
- 전부 제외되어 저장 카드 0개여도 사용자 흐름은 저장 실패가 아니다. 새로 저장된 카드만 rollback 대상으로 두는 현 정책과 충돌하지 않게 한다.

---

# 검증 기준

- 단위 테스트:
  - 동일/유사 카드 입력 → 중복 제거(병합 또는 skip) 확인.
  - 교정 전후 동일·과도하게 짧은 카드 → 제외 확인.
  - 정상 카드 → 저장 확인.
  - 전부 제외 → 저장 실패로 보이지 않는 자연스러운 처리 확인.
- 기존 저장·rollback·Firestore sync 회귀 확인. (이번 요청에서 새로 저장된 카드만 rollback 대상)
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드 통과.
