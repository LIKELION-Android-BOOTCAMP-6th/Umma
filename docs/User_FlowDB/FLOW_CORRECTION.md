# User Flow - CORRECTION

## 1. 목표

- 사용자가 AI Chat 이후 저장된 대화에서 교정된 문장 카드를 바로 확인할 수 있다.
- 사용자는 교정 결과 카드 중 복습하고 싶은 항목을 Flashcard로 저장할 수 있다.
- 저장 완료 결과를 받으면 Dashboard 복귀와 다음 AI Chat 준비 흐름으로 이어진다.
- MVP에서는 실제 AI 교정 API를 연결해 교정 결과 생성이 동작해야 하며, fake/mock 구현은 AI 없이 화면과 저장 파이프라인을 검증하기 위한 보조 수단으로 사용한다.

---

## 2. 시작 조건

- 사용자가 로그인된 상태
- Initial Setup 완료 상태
- `selectedLearningLanguage` 존재
- Global Learning State preload 완료 상태
- 현재 선택 언어의 `SessionSummary` 조회 가능 상태
- 현재 선택 언어의 `LangState` snapshot 조회 가능 상태
- AI Chat에서 확정된 turn이 RT-003 Session Memory에 저장되고 correction context로 조회 가능한 상태

---

## 3. 성공 조건

- Correction 화면 진입 시 현재 선택 언어 기준 상태를 읽는다.
- 교정 가능 여부는 `SessionSummary.correctionAvailable`을 기준으로 판단한다.
- `DashSummary.correctionAvailable`은 Dashboard 표시용 파생값으로만 본다.
- 후보 추출은 `SYS-CORRECTION-INFRA` 계약에 따른 내부 처리이며, 사용자는 후보 목록을 선택하지 않는다.
- 교정 결과 생성은 최소 프롬프트 계약을 통해 교정 수준과 응답 형식을 고정하고, 실제 AI 응답을 파싱해 필수 필드가 채워진 `CorrectionSuggestion` 목록으로 변환하는 흐름을 포함한다.
- 사용자는 진입 후 Loading을 거쳐 교정 결과 카드 목록을 확인한다.
- 사용자는 저장할 교정 결과 카드를 선택하여 Flashcard로 저장한다.
- 선택된 교정 결과의 새 Flashcard 최초 생성과 local first 저장은 Correction 완료 흐름에서 수행한다.
- Flashcard 저장과 완료 처리는 `SYS-CORRECTION-INFRA`의 저장/완료 계약을 따른다.
- 완료 성공 결과를 받으면 Dashboard 복귀 흐름으로 이어진다.
- 완료 실패 결과를 받으면 Retry 상태를 제공한다.

---

## 4. 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 상세 이슈 |
| --- | --- | --- | --- | --- | --- | --- |
| 초기 상태 로드 | Correction 화면 진입 | Global Learning State, SessionSummary, LangState snapshot 로드 | 교정 준비 상태 진입 | 언어 없음 / 세션 없음 / 교정 불가 | Loading / Empty / Error | COR-001 |
| 교정 결과 생성 | 교정 준비 상태 진입 | SYS 후보 추출 계약, LangState snapshot, 실제 AI 응답으로 교정 결과 생성 | CorrectionSuggestion 준비 | 후보 없음 / AI 실패 / 파싱 실패 | Loading / Empty / Error / Retry | COR-002 |
| 결과 카드 표시 | 교정 결과 카드 확인 | 교정 전/후 문장과 설명을 표시 | 저장 가능한 카드 출력 | 렌더링 실패 | Content / Error | COR-003 |
| 저장 카드 선택 | 저장할 카드 선택/해제 | 선택 상태를 화면 상태로 관리 | 저장 대상 준비 | 선택 항목 없음 | Content / Disabled | COR-004 |
| Flashcard 저장 요청 준비 | 저장 버튼 클릭 | 선택 항목을 저장 요청 모델로 변환 | 완료 파이프라인 호출 가능 | 저장 요청 변환 실패 | Preparing / Error | COR-005 |
| 완료 결과 연결 | 저장 요청 후 완료 처리 | `CompleteCorrectionUseCase` 호출, 새 Flashcard local first 저장 결과 반영 | 로컬 완료 성공 | 로컬 완료 실패 | Completing / Retry | COR-006 |
| 복귀 및 후처리 | 완료 후 Dashboard 복귀 | Dashboard 복귀 이벤트 처리, sync pending 상태 유지 | Dashboard 복귀 완료 | 복귀 실패 / sync pending | Done / PendingSync | COR-007 |

---

## 5. GitHub Issue (실행 기준 / SSOT)

### User Flow Issues

- [COR-001 Correction 초기 상태 로드](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md)
- [COR-002 교정 결과 생성](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-002_Suggestion_Generation.md)
- [COR-003 교정 결과 카드 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-003_Result_Cards.md)
- [COR-004 저장 카드 선택 상태](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-004_Card_Selection.md)
- [COR-005 Flashcard 저장 요청 준비](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-005_Save_Request.md)
- [COR-006 교정 완료 결과 연결](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-006_Completion_Pipeline.md)
- [COR-007 Dashboard 복귀 및 후처리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-007_Return_and_Sync.md)

`COR-001 ~ COR-007`는 팀원이 완료 여부를 빠르게 확인할 수 있도록 작은 단위로 분리한다.
각 이슈는 담당 범위를 넘는 구현을 끌어오지 않고, 필요한 선행 결과는 이전 이슈의 완료 결과를 사용한다.

### 이슈 분할 기준

- 하나의 이슈는 화면, 상태, domain 처리, 저장, 후처리 중 하나의 책임만 중심으로 잡는다.
- 팀원이 PR을 올렸을 때 1차 리뷰에서 완료 여부를 판단할 수 있을 정도로 작업 단위를 작게 유지한다.
- 뒤 이슈의 구현을 앞 이슈에서 미리 완성하지 않는다.
- mock 데이터로 확인 가능한 화면/상태 이슈는 실제 API 연결을 기다리지 않고 먼저 검증할 수 있다.
- 단, `COR-002` 완료 기준에는 실제 AI API 호출, 응답 파싱, `CorrectionSuggestion` 변환, 실패/Error/Retry 검증이 포함된다.

---

## 6. 데모 시나리오

1. AI Chat에서 한두 턴 이상 대화한다.
2. Correction 화면에 진입한다.
3. 초기 상태 로드 후 교정 가능 상태를 확인한다.
4. SYS 후보 추출 계약을 거쳐 교정 결과 생성이 진행된다.
5. 교정 결과 카드 목록이 표시된다.
6. 저장할 교정 결과 카드를 선택해 Flashcard 저장을 요청한다.
7. 완료 성공 후 Dashboard로 복귀한다.

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
SYS-CORRECTION-INFRA 후보 추출 계약
→ 내부 후보 준비
→ 실제 AI 교정 API 호출
→ AI 응답 파싱 및 필수 필드 검증
→ CorrectionSuggestion 생성
→ CorrectionSuggestion 카드 표시
```

후보 추출 세부 정책과 내부 후보 모델은 `SYS-CORRECTION-INFRA`의 후보 추출 계약을 따른다.
AI 응답 원문이나 JSON 파싱은 화면에서 직접 처리하지 않고, `CorrectionRepository`의 data 계층 구현과 mapper를 통해 `CorrectionSuggestion`으로 변환한다.

MVP에서의 프롬프트 엔지니어링은 선택 사항이 아니라 실제 AI 교정이 정상 동작하기 위한 최소 구현에 포함한다.
프롬프트는 `LangState` snapshot, 현재 선택 언어, `CorrectionCandidate` 원문, 필요한 assistant 문맥을 입력으로 사용해 다음 기준을 지켜야 한다.

- 학습자의 현재 수준을 벗어나 지나치게 어려운 문장으로 바꾸지 않는다.
- 의미를 바꾸지 않고 자연스러운 외국어 문장으로 교정한다.
- `nativeText`, `afterText`, `explanation`이 항상 채워진 응답 구조를 요청한다.
- `candidateId`를 유지해 AI 응답과 원본 후보를 매칭할 수 있게 한다.
- 설명은 Flashcard 뒷면에 표시 가능한 짧은 학습 설명으로 제한한다.

다만 교정 품질을 더 높이기 위한 세부 prompt tuning, JSON schema 정교화, fallback 고도화는 후속 개선 범위로 둔다.

### 7.3 모델 분리

- `CorrectionSuggestion`: 화면 카드 표시와 Flashcard 저장 선택에 사용하는 결과 모델
- `CorrectionResult`: LangState 업데이트 입력용 최소 모델

화면은 SYS 후보 목록을 직접 렌더링하지 않는다.
`CorrectionSuggestion`과 `CorrectionResult`를 섞지 않는다.

### 7.4 완료 기준

교정 화면을 단순히 이탈했다고 완료로 보지 않는다.
Correction에서 선택한 교정 결과는 이 완료 흐름 안에서 새 Flashcard로 최초 저장된다.
SRS는 이 저장을 대신 수행하지 않고, 저장된 Flashcard를 이후 복습 대상으로 조회한다.

```text
교정 결과 카드 확인
→ 사용자가 Flashcard 저장 항목 선택
→ CompleteCorrectionUseCase 호출
→ 새 Flashcard local first 저장
→ Firestore background sync 예약
→ 완료 성공/실패 결과 수신
→ Done 또는 Retry 상태 전환
```

저장 항목이 0개이면 완료 파이프라인을 수행하지 않는다.
Session Memory 저장/조회/압축 실행은 RT-003 계약을 따른다.
Correction 화면은 RT-003 correction context를 읽어 후보 추출 입력으로 변환하지만, Session Memory 저장소 구현을 직접 만들지 않는다.
Correction 완료 흐름은 선택된 교정 결과와 분석 대상 turn으로 최소 압축 payload를 만든 뒤 RT-003 compression 계약을 호출한다.
압축 payload가 비어 있으면 원문 buffer만 비우지 않도록 compression을 호출하지 않는다.
로컬 완료 파이프라인 내부 순서, rollback, pending sync 정책은 `SYS-CORRECTION-INFRA` 계약을 따른다.
Firestore sync 실패만 발생한 경우에는 로컬 저장 성공을 유지하고 pending sync 상태로 다룬다.
compression 실패는 저장 완료 자체를 되돌리지 않고 후속 재시도 대상으로 남긴다.

---

## 8. 현재 코드 기준 메모

- 현재 코드는 `presentation/correction/CorrectionScreen.kt`와 `Route.CorrectionList` 계열 명칭을 사용한다.
- Correction 작업은 화면 파일명, 폴더, 라우트, 콜백 이름을 `Correction` 기준으로 유지한다.
- 이전 대시보드/온보딩 문서는 별도 수정하지 않는다.
- `SYS-CORRECTION-INFRA`는 Correction 기능의 공통 계약을 담는 참고 문서다.

---

## 9. 연결 문서

- [SYS_CORRECTION_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_CORRECTION_INFRA.md)
- [SYS_LEARNING_STATE_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)
- [SYS_REALTIME_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_REALTIME_INFRA.md)
- [FLOW_AI_CHAT.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_AI_CHAT.md)
- [FLOW_DASHBOARD.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_DASHBOARD.md)
- [DASH-003_Correction_Pending_Card.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md)
- [USER_FLOW_MOCK_REAL_DATA_GUIDE.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/USER_FLOW_MOCK_REAL_DATA_GUIDE.md)
