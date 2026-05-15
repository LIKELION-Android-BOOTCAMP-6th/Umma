# User Flow - CORRECTION

## 1. 목표

- 사용자가 AI Chat 이후 저장된 대화에서 교정된 문장 카드를 바로 확인할 수 있다.
- 사용자는 교정 결과 카드 중 복습하고 싶은 항목을 Flashcard로 저장할 수 있다.
- 저장 완료 파이프라인이 성공하면 Session Memory 압축과 Summary 갱신이 수행되어 Dashboard와 다음 AI Chat 상태가 정리된다.

---

## 2. 시작 조건

- 사용자가 로그인된 상태
- Initial Setup 완료 상태
- `selectedLearningLanguage` 존재
- Global Learning State preload 완료 상태
- 현재 선택 언어의 `SessionSummary` 조회 가능 상태
- 현재 선택 언어의 `LangState` snapshot 조회 가능 상태
- AI Chat에서 확정된 turn이 Session Memory에 저장된 상태

---

## 3. 성공 조건

- Correction 화면 진입 시 현재 선택 언어 기준 상태를 읽는다.
- 교정 가능 여부는 `SessionSummary.correctionAvailable`을 기준으로 판단한다.
- `DashSummary.correctionAvailable`은 Dashboard 표시용 파생값으로만 본다.
- 후보 추출은 내부 처리이며, 사용자는 후보 목록을 선택하지 않는다.
- 사용자는 진입 후 Loading을 거쳐 교정 결과 카드 목록을 확인한다.
- 사용자는 저장할 교정 결과 카드를 선택하여 Flashcard로 저장한다.
- Flashcard 저장은 local first 정책을 따른다.
- 교정 완료 시 Flashcard 저장, LangState 업데이트, Session Memory 압축, `SessionSummary`/`DashSummary` 갱신을 하나의 로컬 완료 파이프라인으로 처리한다.
- Flashcard 저장 계약은 `SCI-001`을 따른다.
- 로컬 완료 파이프라인 중 하나라도 실패하면 전체 로컬 변경을 롤백하고 Retry 상태를 제공한다.

---

## 4. 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 상세 이슈 |
| --- | --- | --- | --- | --- | --- | --- |
| Correction 진입 | 진입 경로: Dashboard 교정 대기 카드, 하단 탭 | Global Learning State, SessionSummary, LangState snapshot 로드 | 교정 준비 상태 진입 | 언어 없음 / 세션 없음 / 교정 불가 | Loading / Empty / Error | COR-001 |
| 교정 결과 준비 | 화면 진입 후 대기 | recentFullContext에서 user turn 중심 후보를 내부 추출하고 교정 결과와 설명을 생성 | 교정 결과 카드 준비 | 후보 없음 / AI 실패 / 파싱 실패 | Loading / Empty / Error | COR-002 |
| 결과 카드 확인 | 교정 결과 카드 확인 | 교정 전/후 문장과 설명을 표시 | 저장 가능한 카드 출력 | 렌더링 실패 | Content / Error | COR-003 |
| Flashcard 저장 | 저장할 카드 선택 후 저장 | 선택 항목을 local first 저장 요청으로 전달 | 저장 요청 성공 | 저장 요청 실패 | Saving / Error | COR-004 |
| 교정 완료 | 저장 요청 후 완료 처리 | 로컬 완료 파이프라인 실행 후 Firestore sync 예약 | Dashboard 복귀 | 로컬 파이프라인 실패 / sync pending | Completing / Done / Retry | COR-005 |

---

## 5. GitHub Issue (실행 기준 / SSOT)

### User Flow Issues

- [COR-001 Correction 진입 및 초기 상태](./FLOW_CORRECTION/COR-001_Entry.md)
- [COR-002 교정 결과 준비](./FLOW_CORRECTION/COR-002_Result_Preparation.md)
- [COR-003 교정 결과 카드](./FLOW_CORRECTION/COR-003_Result_Cards.md)
- [COR-004 Flashcard 저장](./FLOW_CORRECTION/COR-004_Flashcard_Save.md)
- [COR-005 교정 완료 정리](./FLOW_CORRECTION/COR-005_Completion.md)

`COR-001 ~ COR-005`는 화면 작업만이 아니라 필요한 ViewModel, UseCase 연결, Repository 사용, fake/real 연결까지 포함하는 vertical slice 단위로 작성한다.

---

## 6. 데모 시나리오

1. AI Chat에서 한두 턴 이상 대화한다.
2. Dashboard로 돌아와 교정 대기 카드를 확인한다.
3. Dashboard 교정 대기 카드나 하단 탭에서 Correction 화면에 진입한다.
4. Loading 후 교정 결과 카드 목록이 표시된다.
5. 교정 전/후 문장과 설명을 확인한다.
6. 저장할 교정 결과 카드를 선택해 Flashcard로 저장한다.
7. 로컬 완료 파이프라인 성공 후 교정 완료 처리를 수행한다.
8. Dashboard로 돌아왔을 때 교정 대기 상태가 최신 Summary 기준으로 갱신된다.

---

## 7. 핵심 정책

### 7.1 진입 기준

```text
GlobalLangState
→ UserLangPref.selectedLang
→ currentSessionSummary()
→ currentLangState()
```

`SessionSummary.correctionAvailable`이 교정 진입 판단의 기준이다.
`DashSummary.correctionAvailable`은 Dashboard 카드 표시용 값이다.

### 7.2 교정 결과 준비

사용자는 후보 목록을 보거나 후보를 선택하지 않는다.

```text
recentFullContext
→ user turn 중심 후보 내부 추출
→ 필요한 assistant turn만 짧은 문맥으로 사용
→ CorrectionCandidate 내부 구성
→ LangState snapshot 기반 교정 결과와 설명 생성
→ CorrectionSuggestion 생성
→ CorrectionSuggestion 카드 표시
```

MVP에서는 최근 100턴까지만 후보 추출 대상으로 삼는다.

### 7.3 모델 분리

- `CorrectionCandidate`: 내부 후보 추출 결과
- `CorrectionSuggestion`: 화면 카드 표시와 Flashcard 저장 선택에 사용하는 결과 모델
- `CorrectionResult`: LangState 업데이트 입력용 최소 모델

화면은 `CorrectionCandidate` 목록을 직접 렌더링하지 않는다.
`CorrectionSuggestion`과 `CorrectionResult`를 섞지 않는다.

### 7.4 완료 기준

교정 화면을 단순히 이탈했다고 완료로 보지 않는다.

```text
교정 결과 카드 확인
→ 사용자가 Flashcard 저장 항목 선택
→ CompleteCorrectionSessionUseCase 호출
→ LangState 업데이트 입력 생성 및 적용
→ Flashcard local first 저장
→ Session Memory 압축 요청
→ SessionSummary.correctionAvailable false 갱신
→ DashSummary.correctionAvailable 동시 반영
→ Firestore background sync 예약
```

저장 항목이 0개이면 완료/압축을 수행하지 않는다.
로컬 완료 파이프라인 중 하나라도 실패하면 Flashcard 저장을 포함한 로컬 변경을 롤백하고 Retry 상태로 남긴다.
저장소가 하나의 transaction으로 묶이지 않는 경우 보상 rollback 또는 commit marker 방식으로 부분 완료 상태를 남기지 않는다.
Firestore background sync 실패는 로컬 완료 실패로 보지 않고 pending sync 상태로 관리한다.

---

## 8. 현재 코드 기준 메모

- 현재 코드에는 `presentation/feedback/FeedbackListScreen.kt`와 `Route.FeedbackList` 계열 명칭이 남아 있다.
- Correction 작업이 시작되는 시점부터 화면 파일명, 폴더, 라우트, 콜백 이름은 `Correction` 기준으로 정리한다.
- 이전 대시보드/온보딩 문서는 별도 수정하지 않는다.
- `SYS-CORRECTION-INFRA`는 Correction 기능의 공통 계약을 담는 참고 문서다.

---

## 9. 연결 문서

- [SYS_CORRECTION_INFRA.md](../System_FlowDB/SYS_CORRECTION_INFRA.md)
- [SYS_LEARNING_STATE_INFRA.md](../System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [SYS_REALTIME_INFRA.md](../System_FlowDB/SYS_REALTIME_INFRA.md)
- [FLOW_AI_CHAT.md](./FLOW_AI_CHAT.md)
- [FLOW_DASHBOARD.md](./FLOW_DASHBOARD.md)
- [DASH-003_Correction_Pending_Card.md](./FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md)
- [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](./USER_FLOW_MOCK_REAL_DATA_GUIDE.md)
