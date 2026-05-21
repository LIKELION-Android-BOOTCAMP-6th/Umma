# [Feature] STAT-004 통계 데이터 동기화 및 재진입 처리

## User Story

사용자는 Statistics 화면에 다시 들어왔을 때 빠르게 기존 통계를 확인하고,
동기화 상태와 무관하게 저장된 local history를 안정적으로 볼 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] Statistics 화면 재진입 시 local cache history를 먼저 렌더링한다.
- [ ] repository가 제공하는 background refresh 결과를 관찰해 stale cache 보정 상태를 화면에 반영한다.
- [ ] Firestore sync 실패는 화면 실패로 처리하지 않는다.
- [ ] sync pending 상태가 있어도 local history는 표시된다.
- [ ] background sync로 새 history가 들어오면 현재 선택 언어 기준으로 chart가 갱신된다.
- [ ] 다른 언어의 sync 결과는 현재 화면에 섞이지 않는다.
- [ ] 네트워크 실패 시 기존 local history를 유지한다.
- [ ] 화면 이탈 중 진행 중인 fetch가 UI를 깨뜨리지 않는다.

---

## Flow (링크)

- [FLOW-STATISTICS](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS.md)
- [STAT-003 → 지표 카드 클릭 및 line chart 표시](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS/STAT-003_Metric_Line_Chart.md)
- [STAT-004 → 통계 데이터 동기화 및 재진입 처리](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS/STAT-004_Sync_and_Reentry.md)
- [STI-001 → StatisticsHistory 모델 및 Repository 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-001_StatisticsHistory_Model.md)
- [STI-002 → Correction 완료 후 history 기록 계약](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-002_History_Record_Policy.md)

---

## 구현 범위

### 포함 범위

- local cache 우선 렌더링
- background sync 결과 observe 및 화면 반영
- pending sync 상태 표시
- 재진입 시 최신 local state 관찰
- sync 실패 시 non-blocking 상태 처리

### 제외 범위

- StatisticsHistory 생성
- Language State 업데이트
- line chart 컴포넌트 자체 구현
- Dashboard 카드 UI
- AI 기반 통계 해석

---

## Details

## sync 흐름

```text
Statistics 화면 진입 또는 재진입
→ StatisticsHistory local cache 조회
→ 즉시 Content 또는 Empty 렌더링
→ Repository background refresh 결과 observe
→ 변경사항 local 반영 결과 observe
→ 현재 선택 언어 화면 갱신
```

화면은 remote fetch 완료를 기다린 뒤 처음 렌더링하지 않는다.
Statistics는 Dashboard보다 preload 우선순위가 낮으므로 화면 진입 시 local first 정책을 유지한다.

---

## pending sync 정책

- history local 저장 후 Firestore sync가 실패하면 repository/local sync metadata에 pending sync로 남는다.
- pending sync는 사용자에게 chart 실패로 표시하지 않는다.
- 사용자에게 노출해야 할 때는 작은 sync 상태 표시로만 알린다.
- pending 상태인 history도 local에 있으면 chart source로 사용할 수 있다.

---

## 재진입 정책

- 화면 재진입 시 `selectedLearningLanguage`를 다시 확인한다.
- 이전 언어의 selected metric이나 chart point가 현재 언어 화면에 남지 않게 한다.
- 현재 선택 언어가 바뀌면 history observe 대상도 바뀐다.
- 화면 이탈 중 완료된 fetch 결과가 이미 사라진 화면 state를 덮어쓰지 않게 한다.

---

## 작업 지시

- local cache 결과를 먼저 화면에 올린다.
- repository background refresh 실패를 Fatal Error로 올리지 않는다.
- 현재 선택 언어와 fetch 결과의 `language`가 다르면 반영하지 않는다.
- background sync 결과가 들어오면 선택 지표 chart를 다시 계산한다.
- sync 재시도 정책은 repository/local sync metadata 기준으로 관리하며, 이 이슈는 해당 상태를 관찰해 화면에 반영한다.

---

## 기술 설계 가이드

## 권장 구조

```text
domain/usecase/statistics/
→ ObserveStatisticsHistoryUseCase
→ RefreshStatisticsHistoryUseCase

presentation/statistics/
→ StatisticsViewModel
→ StatisticsSyncState
```

## 상태 예시

```text
Content
ContentWithPendingSync
Refreshing
NonBlockingSyncError
FatalError
```

FatalError는 local cache도 없고 기본 화면을 구성할 수 없을 때만 사용한다.

---

## 검증 기준

- offline 상태에서도 local history가 있으면 chart를 볼 수 있다.
- repository background refresh 실패 시 기존 chart가 사라지지 않는다.
- background sync로 새 point가 추가되면 chart가 갱신된다.
- 언어 변경 후 이전 언어의 chart point가 남지 않는다.
- pending sync 상태인 history도 chart에 반영된다.

---

## Edge Cases

- local history 없음 + remote fetch 실패
- local history 있음 + remote fetch 실패
- pending sync history만 존재
- 언어 변경 직후 이전 fetch 결과 도착
- 화면 이탈 직후 sync 완료
- remote에는 삭제된 history가 local에 남아 있음
- 같은 recordedAt의 history가 중복으로 내려옴

---

## 연결 문서

- [FLOW_STATISTICS.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS.md)
- [STAT-003_Metric_Line_Chart.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/User_FlowDB/FLOW_STATISTICS/STAT-003_Metric_Line_Chart.md)
- [SYS_STATISTICS_INFRA.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA.md)
- [STI-001_StatisticsHistory_Model.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-001_StatisticsHistory_Model.md)
- [STI-002_History_Record_Policy.md](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/develop/docs/System_FlowDB/SYS_STATISTICS_INFRA/STI-002_History_Record_Policy.md)
