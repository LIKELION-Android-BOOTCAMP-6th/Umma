# [Infra] STI-002 Correction 완료 후 history 기록 계약

## User Story

Statistics User Flow 작업자는 지표 변화 그래프를 볼 수 있도록,
Correction 완료 이후 언제 어떤 방식으로 `StatisticsHistory`가 기록되는지 신뢰할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] `StatisticsHistory` 기록 시점은 교정 결과 Flashcard local 저장과 `LS-006` local LangState 저장이 모두 성공한 이후로 정의된다.
- [ ] history 기록 입력은 LS-006이 저장 완료한 `language`의 `ExternalMetrics` snapshot을 사용한다.
- [ ] MVP에서는 Flashcard 복습 결과만으로 `StatisticsHistory`를 새로 기록하지 않는다.
- [ ] 화면 진입, Dashboard 진입, 단순 sync 완료는 history 생성 트리거가 아니다.
- [ ] history는 `sourceEventId` 기준으로 중복 기록을 방지한다.
- [ ] history local 저장은 Room local first로 수행한다.
- [ ] Firestore sync 실패는 history 기록 실패로 보지 않고 pending sync로 남긴다.
- [ ] history local 저장 실패 시 재시도 가능한 상태로 남긴다.
- [ ] history 기록 실패가 이미 성공한 Language State 업데이트를 임의로 되돌리지 않는다.

---

## 구현 범위

### 포함 범위

- Correction 완료 파이프라인에서 Language State 업데이트 성공 결과 이후 history 기록 UseCase 계약
- `ExternalMetrics` snapshot → `StatisticsHistory` 변환
- `sourceEventId` 중복 방지
- Room local first 저장
- Firestore pending sync
- history 기록 실패/재시도 상태

### 제외 범위

- Language State 업데이트 공식
- Type A/B/C 분석 로직
- LS-006 payload 생성 및 LangState 저장 파이프라인 구현
- Flashcard 복습 결과를 LangState에 반영하는 SRS 후속 정책
- Dashboard Summary delta 계산
- Statistics 화면 UI
- line chart 표시
- AI 기반 통계 해석

---

## 권장 파일/패키지 방향

```text
domain/usecase/statistics
→ RecordStatisticsHistoryUseCase
→ BuildStatisticsHistoryUseCase

domain/repository
→ StatisticsRepository

data/repository
→ StatisticsRepositoryImpl
```

`RecordStatisticsHistoryUseCase`는 Correction 완료 파이프라인에서 Flashcard local 저장과 `LS-006` local LangState 저장이 모두 성공한 뒤 호출된다.
이 문서는 호출 지점과 입력 계약을 정의하지만, LS-006의 분석 payload 생성, 점수 계산, LangState 저장 파이프라인을 다시 구현하지 않는다.

---

## 핵심 흐름

```text
Correction 선택 결과 Flashcard local 저장 성공
→ LS-006에서 External Metrics 재계산 완료
→ Local LangState 저장 성공 결과 전달
→ 기록 대상 language / sourceEventId / ExternalMetrics snapshot 수신
→ StatisticsHistory snapshot 생성
→ StatisticsHistory Room local 저장
→ Firestore background sync 예약
```

Statistics 화면 진입은 위 기록 결과를 읽는 흐름이다.
화면 진입이 history 생성을 트리거하지 않는다.

---

## 기록 트리거 정책

MVP의 기본 기록 트리거는 Correction 완료 이벤트다.

```text
교정 결과 선택
→ Flashcard local 저장 성공
→ LangState local 저장 성공
→ StatisticsHistory 기록 시도
```

아래 이벤트는 MVP에서 history 생성 트리거로 사용하지 않는다.

- Statistics 화면 진입
- Dashboard 진입 또는 Dashboard Summary observe
- Firestore background sync 완료
- Flashcard 복습 결과 저장만 단독으로 완료된 경우
- 단순 `selectedLearningLanguage` 변경

Flashcard 복습 결과는 SRS / Dashboard 요약 갱신에 우선 사용한다.
추후 복습 결과를 장기 지표에 반영하더라도, 별도 정책이 확정되기 전까지 `StatisticsHistory` 기록 트리거로 직접 연결하지 않는다.

---

## 기록 정책

### 1. 기록 대상

MVP에서는 `ExternalMetrics` 5개만 history로 기록한다.

- `vocabularyLevel`
- `grammarAccuracy`
- `expressionRange`
- `fluencyScore`
- `naturalnessScore`

Internal Metrics 전체를 history에 저장하지 않는다.

### 2. 중복 방지

```text
sourceEventId = savedLangState.lastAnalysisEventId
```

- 같은 `language`와 `sourceEventId` 조합이 이미 있으면 새 history를 만들지 않는다.
- 중복 요청이 들어오면 기존 history를 유지하고 성공으로 간주할 수 있다.
- `sourceEventId`가 없으면 `languageState.updatedAt`과 `language`를 조합한 fallback id로 중복 생성을 방지한다.

### 3. LangState 완료 결과 계약

Statistics 쪽 기록 UseCase는 LS-006의 내부 계산 과정을 알지 않는다.
대신 아래 저장 완료 결과만 입력으로 받는다.

```text
language
externalMetrics
savedAt
sourceEventId
```

`sourceEventId`는 `savedLangState.lastAnalysisEventId`를 우선 사용한다.
값이 없을 때만 `language + savedAt` 조합으로 fallback id를 만든다.
LS 선행 계약에서 완료 결과 모델을 아직 제공하지 못한다면, `RecordStatisticsHistoryUseCase` 연결은 보류하고 `LangState` 저장 결과를 명시적으로 받을 수 있는 계약부터 정리한다.

### 4. 실패 정책

- Room local 저장 성공 시 사용 가능한 history로 본다.
- Firestore sync 실패는 pending sync로 남긴다.
- Room local 저장 실패는 history 기록 실패이며 Retry 대상으로 남긴다.
- history 기록 실패가 이미 저장된 LangState를 되돌리지는 않는다.
- 다음 background retry에서 누락 history를 보정할 수 있다.

---

## 검증 기준

- Correction 완료 파이프라인에서 Flashcard 저장과 Language State 업데이트가 모두 성공한 뒤 history 기록이 시도된다.
- Statistics 화면 진입만으로 history가 생성되지 않는다.
- Flashcard 복습 결과 저장만으로 history가 생성되지 않는다.
- 같은 분석 이벤트가 두 번 반영되어도 history가 중복 생성되지 않는다.
- Firestore sync 실패 상태에서도 local history는 Statistics 화면에서 조회 가능하다.
- history local 저장 실패는 Retry 가능한 상태로 남는다.

---

## LangState 선행 계약 확인 항목

Statistics history 기록은 LS-006의 계산 과정을 다시 구현하지 않는다.
연결 전에 아래 선행 계약을 확인한다.

- LS-006 local 저장 성공 이후 저장된 `language`, `ExternalMetrics`, `updatedAt`, `lastAnalysisEventId`를 받을 수 있어야 한다.
- 동일 `analysisEventId` 중복 요청을 실제 저장 단계에서 무시하는 idempotent 처리가 필요하다.
- `ExternalMetrics` MVP 5개 지표 중 `vocabularyLevel`, `expressionRange`까지 포함해 Statistics가 기록할 snapshot이 항상 일관되게 채워져야 한다.
- local 저장 성공과 remote sync 실패를 구분해야 한다. Statistics history 기록은 local LangState 저장 성공을 기준으로 이어지고, remote sync 실패는 pending sync로 분리한다.
- LS 저장소 내부에서 StatisticsRepository를 직접 호출하지 않는다. Correction 완료 파이프라인을 감싸는 별도 조합 UseCase가 LS 저장 완료 결과를 받아 `RecordStatisticsHistoryUseCase`를 호출한다.

---

## Edge Cases

- `ExternalMetrics` 일부 값이 누락됨
- `sourceEventId`가 없음
- 같은 이벤트가 중복 전달됨
- Flashcard 저장은 성공했지만 LangState 저장이 실패함
- Local LangState 저장은 성공했지만 history 저장이 실패함
- history 저장은 성공했지만 Firestore sync가 실패함
- 앱 종료 중 history 기록이 진행됨
- Firebase에는 history가 있으나 local cache가 오래됨

---

## 연결 문서

- [SYS_STATISTICS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA.md)
- [STI-001_StatisticsHistory_Model.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-001_StatisticsHistory_Model.md)
- [LS-006_Language_State_Update_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [FLOW_STATISTICS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS.md)
