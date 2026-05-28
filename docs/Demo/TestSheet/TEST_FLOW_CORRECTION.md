# Test Sheet - FLOW-CORRECTION

> 데모 시나리오: `docs/Demo/DEMO_FLOW_CORRECTION_REVISED.md`, `docs/Demo/DEMO_REAL_INTEGRATED_FLOW.md`
> 사용법: 각 행의 "수행 절차"대로 실행 후 "기대 결과" 만족 여부를 **Pass / Fail** 표기한다. 실패 시 비고에 1~2줄 재현 메모와 GitHub Issue 링크를 남긴다.

**Real / Mock 구분**:

- `devDebug`: 실제 AI Chat 저장, 실제 AI 교정 생성, Flashcard 저장, Dashboard summary 갱신까지 확인한다.
- `mockDebug`: Correction fake preset을 바꿔 Content / Empty / Error / SaveFail / PendingSync / TopicTitle 상태를 재현한다.
- `mockDebug`는 실제 AI 응답 품질 검증용이 아니다. preset은 화면 상태와 상태 전이를 안정적으로 검증하기 위한 도구다.

---

## 사전 준비

### Real 검증

- Android Studio Build Variant: `devDebug`
- Google 로그인 가능한 테스트 계정
- 학습 언어 선택 완료
- AI Chat에서 USER final turn 1개 이상 저장 가능
- 네트워크 연결 정상
- 실제 AI 교정 생성 대기 시간을 감안할 것

### Mock 검증

- Android Studio Build Variant: `mockDebug`
- preset 변경 위치:

```kotlin
// app/src/main/java/com/app/umma/data/repository/fake/demo/correction/CorrectionDemoPreset.kt
object CorrectionDemoPresetConfig {
    val activePreset: CorrectionDemoPreset = CorrectionDemoPreset.Content
}
```

- preset 변경 후 앱 재빌드/재실행
- 필요 시 logcat에서 Correction / CompleteCorrection / Dashboard 관련 태그 확인

### Mock preset 기준

| Preset | 목적 |
| --- | --- |
| `Content` | 교정 결과 카드 Content 상태 |
| `EmptyInitial` | 선택 언어 없음 / 세션 없음 / 교정 불가 |
| `EmptyResult` | 교정 결과 0개 |
| `Error` | AI 요청 실패 / 파싱 실패 / 필드 누락 / candidateId 불일치 |
| `SaveFail` | 저장 요청 또는 완료 파이프라인 실패 |
| `PendingSync` | Firestore sync 또는 compression pending 비차단 |
| `TopicTitleSuccess` | Dashboard 주제 칩 제목 생성 성공 |
| `TopicTitleEmpty` | topic title 없음 또는 요약 실패 fallback |

---

## 커버되는 AC 체크리스트

### COR-000 Mock / Fixture

- [x] AC1: `FakeCorrectionRepository`로 Content / Empty / Error 상태를 검증할 수 있다.
- [x] AC2: `CorrectionSuggestionFixtureBuilder`로 화면과 ViewModel 테스트에서 같은 샘플 데이터를 사용할 수 있다.
- [x] AC3: SaveFail / PendingSync fixture를 검증할 수 있다.
- [x] AC4: ViewModel과 Composable은 fake/real 구현체를 직접 구분하지 않는다.
- [x] AC5: mock/real 교체는 Hilt binding 또는 RepositoryModule 기준으로 관리한다.

### COR-001 진입 및 교정 가능 상태

- [x] AC1: Correction 진입 시 `selectedLearningLanguage`를 확인한다.
- [x] AC2: 현재 선택 언어의 `SessionSummary`를 로드한다.
- [x] AC3: `SessionSummary.correctionAvailable` 기준으로 교정 가능 여부를 판단한다.
- [x] AC4: 현재 선택 언어의 `LangState` snapshot을 로드한다.
- [x] AC5: RT-003 correction context 조회 준비 상태를 확인한다.
- [x] AC6: Ready 이후 사용자 버튼 없이 교정 생성으로 이어진다.
- [x] AC7: 언어 없음, 세션 없음, 교정 불가 상태는 Empty UI로 분기한다.
- [x] AC8: 초기 로딩 중 중복 요청과 중복 초기화가 방지된다.

### COR-002 교정 생성 / Empty / Error / Retry

- [x] AC1: Ready 이후 사용자 추가 입력 없이 교정 결과 생성을 시작한다.
- [x] AC2: 후보 추출 계약과 교정 결과 생성 계약을 호출한다.
- [x] AC3: 실제 AI 교정 API를 호출한다.
- [x] AC4: LangState snapshot과 후보 문맥을 prompt에 반영한다.
- [x] AC5: AI 응답을 `CorrectionSuggestion` 목록으로 변환한다.
- [x] AC6: `candidateId` 불일치 시 Error 상태로 전환한다.
- [x] AC7: 성공 응답은 필수 필드가 채워진 suggestion 1개 이상을 생성한다.
- [x] AC8: 결과가 비어 있으면 Empty 상태를 반환한다.
- [x] AC9: AI 요청 실패, 파싱 실패, 필드 누락 시 Error와 Retry를 제공한다.
- [x] AC10: Retry는 같은 Session Memory와 현재 선택 언어 기준으로 다시 수행한다.

### COR-003 카드 UI

- [x] AC1: 교정 결과는 `CorrectionSuggestion` 계약으로 표시한다.
- [x] AC2: 교정 전 문장과 교정 후 문장을 구분해 보여준다.
- [x] AC3: 생성된 설명을 화면에 표시한다.
- [x] AC4: 후보 목록 선택 UI를 만들지 않는다.
- [x] AC5: Loading / Content / Empty / Error 상태에 맞춰 렌더링한다.

### COR-004 선택 상태

- [x] AC1: 사용자가 교정 결과 카드를 선택할 수 있다.
- [x] AC2: 선택한 카드는 시각적으로 구분된다.
- [x] AC3: 선택한 카드를 다시 누르면 선택 해제할 수 있다.
- [x] AC4: 선택 목록은 ViewModel 상태로 관리한다.
- [x] AC5: 선택 항목이 0개이면 저장 버튼은 비활성화된다.
- [x] AC6: 선택 상태는 `CorrectionSuggestion` 식별자를 기준으로 관리한다.
- [x] AC7: 선택 상태 변경만으로 실제 저장 요청은 실행하지 않는다.

### COR-005 저장 요청 변환 / 방어

- [x] AC1: 선택한 `CorrectionSuggestion`만 저장 요청으로 변환한다.
- [x] AC2: 저장 요청은 현재 선택 언어 기준으로 생성된다.
- [x] AC3: Flashcard 앞면은 모국어 문장으로 구성한다.
- [x] AC4: Flashcard 뒷면은 교정된 외국어 문장과 짧은 설명으로 구성한다.
- [x] AC5: 저장 계약에 맞는 입력으로 구성한다.
- [x] AC6: 저장 대상이 0개이면 완료 파이프라인을 호출하지 않는다.
- [x] AC7: 저장 요청 모델 준비 후 완료 파이프라인으로 넘긴다.
- [x] AC8: 저장 요청 변환 실패 시 Error 상태를 표시한다.
- [x] AC9: 저장 버튼 중복 클릭을 방지한다.

### COR-006 완료 파이프라인

- [x] AC1: 저장할 Flashcard 항목이 준비된 이후에만 완료 처리를 시작한다.
- [x] AC2: `CompleteCorrectionUseCase`를 호출한다.
- [x] AC3: 성공 결과에 저장된 Flashcard ID가 포함된다.
- [x] AC4: 로컬 완료 성공 결과를 받으면 Done 상태로 전환한다.
- [x] AC5: 완료 처리 중 중복 완료 요청이 방지된다.
- [x] AC6: 로컬 완료 실패 결과를 받으면 Retry 상태로 남긴다.

### COR-007 Dashboard 복귀 / pending 비차단

- [x] AC1: 로컬 완료 성공 후 Dashboard로 복귀한다.
- [x] AC2: 완료 성공 이벤트는 한 번만 소비된다.
- [x] AC3: sync pending 상태가 있어도 Dashboard 복귀를 막지 않는다.
- [x] AC4: compression pending 상태가 있어도 저장 완료와 Dashboard 복귀를 막지 않는다.

### COR-FIX / DASH-FIX 보강

- [x] AC1: 교정 완료 시 선택된 모든 suggestion의 `afterText`가 LS 분석 입력에 반영된다.
- [x] AC2: 최근 대화 세션 5개를 turnId 누적 순서 기준으로 추출하고 주제를 요약한다.
- [x] AC3: 저장 직후 `DashSummary.savedFlashcards` / `dueFlashcards`가 즉시 반영된다.
- [x] AC4: summary count 반영 실패는 pending sync로만 남고 Done 흐름은 계속된다.
- [x] AC5: Dashboard 최근 대화 카드의 주제 칩은 짧은 topic title을 표시한다.
- [x] AC6: AI 요약 실패 시 이상한 단어로 기존 recentTopic을 덮어쓰지 않는다.
- [x] AC7: Correction 로딩은 중앙 인디케이터와 5단계 안내 문구로 표시된다.
- [x] AC8: 안내 문구는 3초 간격으로 전환되고 최소 15초 로딩이 보장된다.
- [x] AC9: 교정 완료 성공 후 Phase.Done 완료 안내 화면을 노출하지 않는다.
- [x] AC10: 완료 안내는 Dashboard 커스텀 토스트로 표시된다.
- [x] AC11: 토스트는 저장 카드 수를 포함하고 1.5초 뒤 사라진다.
- [x] AC12: 저장 실패 또는 Retry phase에서는 Dashboard 이동과 완료 토스트가 발생하지 않는다.

---

## 테스트 시트

| Test ID | 구분 | 시나리오 | 연결 AC | 사전 조건 | 수행 절차 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-COR-01 | Real | Correction 진입 | COR-001 AC1·2·3·4·5·6 | `devDebug`, AI Chat final turn 1개 이상 저장 | Dashboard -> 교정 대기 카드 클릭 | 현재 선택 언어 기준으로 SessionSummary/LangState 로드 후 자동 교정 생성 시작 |  |  |
| TC-COR-02 | Real | 단계형 로딩 | COR-FIX-04 AC1·2·3·4 | TC-COR-01 진행 중 | Correction 진입 후 로딩 화면 관찰 | 중앙 CircularProgressIndicator 표시, 5단계 안내 문구가 3초 간격으로 전환, 말줄임표 애니메이션 표시 |  |  |
| TC-COR-03 | Real | AI 교정 생성 | COR-002 AC1·2·3·4·5·7 | `devDebug`, 교정 가능한 세션 존재 | 로딩 완료까지 대기 | 실제 AI 응답이 `CorrectionSuggestion` 1개 이상으로 변환되고 Content 상태 표시 |  |  |
| TC-COR-04 | Mock | candidateId 불일치 | COR-002 AC6 / COR-002-B | `mockDebug`, `activePreset = Error` 또는 candidate mismatch fixture | Correction 진입 | `candidateId` 불일치 또는 필수 필드 누락 시 Error 상태와 Retry 표시 |  |  |
| TC-COR-05 | Mock | 초기 Empty | COR-001-B AC1·2 | `mockDebug`, `activePreset = EmptyInitial` | Correction 진입 | 언어 없음/세션 없음/교정 불가 조건에서 Empty UI와 AI Chat 이동 CTA 표시, 초기화 중복 없음 |  |  |
| TC-COR-06 | Mock | 결과 Empty | COR-002-B AC1 / COR-003-B | `mockDebug`, `activePreset = EmptyResult` | Correction 진입 | 교정 결과 0개이면 카드 화면이 아니라 결과 없음 Empty 상태 표시 |  |  |
| TC-COR-07 | Mock | Error Retry | COR-002-B AC2·3·4 | `mockDebug`, `activePreset = Error` | Error 상태에서 Retry 클릭 | 같은 Session Memory와 현재 선택 언어 기준으로 재요청. 성공 fixture 전환 시 Content 복구 가능 |  |  |
| TC-COR-08 | Mock | 카드 Content UI | COR-000 / COR-003 AC1·2·3·4 | `mockDebug`, `activePreset = Content` | Correction 진입 후 카드 확인 | fixture 기반 카드 목록 표시, 교정 전/후/설명 구분, 후보 목록 선택 UI 미노출 |  |  |
| TC-COR-09 | Mock | 카드 목록 상태 렌더링 | COR-003-B | `mockDebug`, Content/Empty/Error preset 순차 적용 | 각 preset으로 앱 실행 | Loading / Content / Empty / Error 상태가 각각 맞는 UI로 표시 |  |  |
| TC-COR-10 | Mock | 카드 선택/해제 | COR-004 AC1·2·3·4·5·6·7 | `mockDebug`, `activePreset = Content` | 카드 선택 -> 같은 카드 재클릭 -> 여러 카드 선택 | 선택 상태 시각 구분, 재클릭 시 해제, 0개면 저장 버튼 비활성, id 기준 상태 유지 |  |  |
| TC-COR-11 | Real | 저장 요청 변환 | COR-005 AC1·2·3·4·5·7 | `devDebug`, 교정 카드 2개 이상 표시 | 카드 2개 선택 후 저장 | 선택한 suggestion만 저장 요청에 포함, 언어/사용자/앞면/뒷면/설명 필드가 계약에 맞게 구성 |  |  |
| TC-COR-12 | Mock | 선택 0개 저장 방어 | COR-005 AC6 / COR-004 AC5 | `mockDebug`, `activePreset = Content` | 아무 카드도 선택하지 않음 | 저장 버튼 비활성, 완료 파이프라인 호출 없음 |  |  |
| TC-COR-13 | Mock | 저장 버튼 중복 클릭 방어 | COR-005-B AC2 / COR-006 AC5 | `mockDebug`, `activePreset = Content` | 카드 선택 후 저장 버튼 빠르게 여러 번 클릭 | 저장/완료 요청은 1회만 발생, 중복 완료 요청 없음 |  |  |
| TC-COR-14 | Mock | 저장 실패 Retry | COR-005-B AC1 / COR-006-B | `mockDebug`, `activePreset = SaveFail` | 카드 선택 후 저장 | 저장 진행 상태 후 Dashboard 이동 없이 Retry 상태 유지, 카드 목록과 선택 재시도 가능 |  |  |
| TC-COR-15 | Real | 저장 진행 상태 | COR-FIX-03 AC1 | `devDebug`, 카드 1개 이상 선택 | 저장 버튼 클릭 | 버튼 또는 화면에 저장 진행 상태가 표시되고 중복 입력이 차단됨 |  |  |
| TC-COR-16 | Real | 완료 후 Dashboard 복귀 | COR-006 AC2·3·4 / COR-007 AC1·2 / COR-FIX-04 AC5·6 | `devDebug`, 카드 저장 성공 | 저장 성공까지 진행 | Phase.Done 완료 화면 없이 Dashboard로 즉시 복귀, navigation 이벤트 1회만 소비 |  |  |
| TC-COR-17 | Mock | Pending sync 비차단 | COR-007-B AC1·2 / COR-FIX-01 AC4 | `mockDebug`, `activePreset = PendingSync` | 카드 선택 후 저장 | Firestore sync 또는 compression pending이어도 Dashboard 복귀, 사용자 Error UI 노출 없음 |  |  |
| TC-COR-18 | Real | 완료 토스트 | COR-DASH-FIX-01 AC1·2·3·5 | `devDebug`, 카드 N개 저장 성공 | 저장 후 Dashboard 복귀 화면 확인 | Dashboard 위 커스텀 토스트에 실제 저장 카드 수가 포함되어 표시되고 1.5초 뒤 사라짐 |  |  |
| TC-COR-19 | Mock | 토스트 0개 fallback | COR-DASH-FIX-01 AC4 | `mockDebug`, 저장 카드 수 0 성공 fixture | 저장 성공 이벤트 발생 | 기본 완료 메시지로 fallback 표시 |  |  |
| TC-COR-20 | Mock | 토스트 1회 소비 | COR-DASH-FIX-01 AC6 | `mockDebug`, `activePreset = Content` | 저장 성공 후 화면 회전 또는 recomposition | 같은 완료 토스트가 다시 표시되지 않음 |  |  |
| TC-COR-21 | Mock | 저장 실패 시 토스트 미표시 | COR-DASH-FIX-01 AC7 | `mockDebug`, `activePreset = SaveFail` | 저장 실패 유도 | Dashboard 이동과 완료 토스트가 발생하지 않고 Retry 상태 유지 |  |  |
| TC-COR-22 | Real | Dashboard Flashcard summary 갱신 | COR-FIX-01 AC3·6 | `devDebug`, 카드 N개 저장 | Dashboard Flashcard 카드 확인 | `savedFlashcards`와 `dueFlashcards`가 저장 직후 즉시 재계산되어 표시 |  |  |
| TC-COR-23 | Unit/Mock | 모든 suggestion LS 반영 | COR-FIX-01 AC2 | 선택 suggestion N개 fixture | CompleteCorrectionUseCase 또는 fake 검증 | 선택된 모든 suggestion의 `afterText`가 LS 분석 입력에 반영됨 |  |  |
| TC-COR-24 | Real/Mock | 최근 세션 요약 저장 | COR-FIX-01 AC3 / COR-FIX-02 | 최근 세션 1~5개 존재 | 저장 완료 후 Dashboard 및 저장 데이터 확인 | 최근 세션이 turnId 누적 순서 기준으로 요약되고 DashSummary/SessionSummary topic에 반영 |  |  |
| TC-COR-25 | Mock | topic title 성공 | DASH-FIX-01 AC1·2·3 | `mockDebug`, `activePreset = TopicTitleSuccess` | 저장 성공 후 Dashboard 확인 | 최근 대화 카드 주제 칩이 `여행 계획` 같은 2~5어절 제목으로 표시 |  |  |
| TC-COR-26 | Mock | topic title 실패 fallback | DASH-FIX-01 AC4·5 | `mockDebug`, `activePreset = TopicTitleEmpty` | 저장 성공 후 Dashboard 확인 | AI 요약 실패/빈 title이어도 이상한 단어로 덮어쓰지 않고 기존 topic 보존 또는 fallback 표시 |  |  |
| TC-COR-27 | Mock | Snackbar와 완료 토스트 충돌 | COR-DASH-FIX-01 Edge | `mockDebug`, 완료 토스트 + Dashboard sync snackbar 동시 발생 fixture | 저장 후 Dashboard 확인 | 완료 토스트와 기존 Snackbar가 주요 UI를 가리지 않고 겹치지 않음 |  |  |
| TC-COR-28 | Build | mock/real 바인딩 | COR-000 AC4·5 | `devDebug`, `mockDebug` | 각 variant 빌드/실행 | ViewModel/Composable 분기 없이 Hilt module 기준으로 real/fake repository가 교체됨 |  |  |

---

## 판정 기준

- Real happy path는 `devDebug`에서만 Pass 처리한다. `mockDebug`에서 실제 AI 교정 품질, 응답 정확도, 네트워크 latency는 판정하지 않는다.
- Mock preset은 상태 전이와 예외 재현용이다. preset 변경 후에는 앱을 재빌드/재실행한다.
- Empty / Error / Retry / PendingSync는 사용자가 다음 행동을 이해할 수 있으면 Pass로 본다.
- 저장 성공 후 완료 안내는 Correction 화면이 아니라 Dashboard 커스텀 토스트에서 확인한다.
- 저장 실패 또는 Retry phase에서 Dashboard 이동이 발생하면 Fail이다.
- pending sync/compression은 사용자에게 실패로 보이면 Fail이다.
- Dashboard 주제 칩은 단어 하나가 아닌 짧은 제목이어야 하며, 요약 실패 시 이상한 fallback 단어로 덮어쓰면 Fail이다.
