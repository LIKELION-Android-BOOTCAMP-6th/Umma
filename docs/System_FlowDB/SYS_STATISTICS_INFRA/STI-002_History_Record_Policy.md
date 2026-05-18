# [Infra] STI-002 Language State 업데이트 후 history 기록 계약

## User Story

Statistics User Flow 작업자는 지표 변화 그래프를 볼 수 있도록,
Language State 업데이트 이후 언제 어떤 방식으로 `StatisticsHistory`가 기록되는지 신뢰할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] `StatisticsHistory` 기록 시점은 `LS-006`의 External Metrics 재계산과 local LangState 저장 성공 이후로 정의된다.
- [ ] history 기록 입력은 현재 선택 언어의 `ExternalMetrics` snapshot을 사용한다.
- [ ] history는 `sourceEventId` 기준으로 중복 기록을 방지한다.
- [ ] history local 저장은 Room local first로 수행한다.
- [ ] Firestore sync 실패는 history 기록 실패로 보지 않고 pending sync로 남긴다.
- [ ] history local 저장 실패 시 재시도 가능한 상태로 남긴다.
- [ ] history 기록 실패가 이미 성공한 Language State 업데이트를 임의로 되돌리지 않는다.

---

## 구현 범위

### 포함 범위

- Language State 업데이트 이후 history 기록 UseCase 계약
- `ExternalMetrics` snapshot → `StatisticsHistory` 변환
- `sourceEventId` 중복 방지
- Room local first 저장
- Firestore pending sync
- history 기록 실패/재시도 상태

### 제외 범위

- Language State 업데이트 공식
- Type A/B/C 분석 로직
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

`RecordStatisticsHistoryUseCase`는 `LS-006`의 Language State 업데이트 흐름에서 local LangState 저장이 성공한 뒤 호출된다.

---

## 핵심 흐름

```text
LS-006 분석 payload 생성
→ measured metrics 생성
→ 이동평균 적용
→ External Metrics 재계산
→ Local LangState 저장 성공
→ GlobalLangState 갱신
→ Dashboard Summary 갱신
→ StatisticsHistory snapshot 생성
→ StatisticsHistory Room local 저장
→ Firestore background sync 예약
```

Statistics 화면 진입은 위 기록 결과를 읽는 흐름이다.
화면 진입이 history 생성을 트리거하지 않는다.

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
sourceEventId = languageState.lastAnalysisEventId
```

- 같은 `language`와 `sourceEventId` 조합이 이미 있으면 새 history를 만들지 않는다.
- 중복 요청이 들어오면 기존 history를 유지하고 성공으로 간주할 수 있다.
- `sourceEventId`가 없으면 `languageState.updatedAt`과 `language`를 조합한 fallback id로 중복 생성을 방지한다.

### 3. 실패 정책

- Room local 저장 성공 시 사용 가능한 history로 본다.
- Firestore sync 실패는 pending sync로 남긴다.
- Room local 저장 실패는 history 기록 실패이며 Retry 대상으로 남긴다.
- history 기록 실패가 이미 저장된 LangState를 되돌리지는 않는다.
- 다음 background retry에서 누락 history를 보정할 수 있다.

---

## 검증 기준

- Language State 업데이트가 성공한 뒤 history 기록이 시도된다.
- 같은 분석 이벤트가 두 번 반영되어도 history가 중복 생성되지 않는다.
- Firestore sync 실패 상태에서도 local history는 Statistics 화면에서 조회 가능하다.
- history local 저장 실패는 Retry 가능한 상태로 남는다.

---

## Edge Cases

- `ExternalMetrics` 일부 값이 누락됨
- `sourceEventId`가 없음
- 같은 이벤트가 중복 전달됨
- Local LangState 저장은 성공했지만 history 저장이 실패함
- history 저장은 성공했지만 Firestore sync가 실패함
- 앱 종료 중 history 기록이 진행됨
- Firebase에는 history가 있으나 local cache가 오래됨

---

## 연결 문서

- [SYS_STATISTICS_INFRA.md](../SYS_STATISTICS_INFRA.md)
- [STI-001_StatisticsHistory_Model.md](./STI-001_StatisticsHistory_Model.md)
- [LS-006_Language_State_Update_Policy.md](../SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)
- [LS-005_Local_Cache_and_Sync_Policy.md](../SYS_LEARNING_STATE_INFRA/LS-005_Local_Cache_and_Sync_Policy.md)
- [FLOW_STATISTICS.md](../../User_FlowDB/FLOW_STATISTICS.md)
