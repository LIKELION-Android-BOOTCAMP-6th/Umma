# SYS-REALTIME-INFRA

## 1. 목표 및 범위

- 목표: Firebase AI Logic의 Gemini Live API를 기반으로 사용자가 AI와 음성으로 대화할 수 있는 세션 인프라를 구축한다.
- 성공 조건: 마이크 입력, 실시간 스트리밍 응답, turn 확정, 세션 재진입, 오류 복구가 하나의 표준 흐름으로 동작한다.

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. 세션 부트스트랩 | activeSessionId 생성 및 복원, 언어 컨텍스트 초기화 | 현재 선택 언어 기준 Live 세션 준비 | 성공: 앱 상태 복원 / 실패: 새 LiveSession 생성 | RT-001 |
| 2. 음성 스트림 | 마이크 입력 수집, 오디오 스트림 전송, transcript 이벤트 수신 | user turn / assistant turn 스트리밍 | 성공: turn 스트림 유지 / 실패: 마이크 또는 네트워크 오류 | RT-002 |
| 3. 턴 확정 | 확정된 turn을 Session Memory에 append | recentFullContext 및 메타데이터 갱신 | 성공: 세션 기록 누적 / 실패: turn 누락 또는 중복 반영 | RT-003 |
| 4. 재연결/복구 | 세션 중단 후 재연결, fallback 정책 적용 | 마지막 선택 언어와 세션 컨텍스트 복구 | 성공: 마지막 확정 turn과 앱 상태 기준으로 재개 / 실패: 세션 재생성 | RT-004 |

---

## 3. 책임 경계

### 포함 범위

- 음성 입력 처리
- Firebase AI Logic Live API 연결 및 재연결
- 부분 transcript / 최종 transcript 스트림
- turn 확정 이벤트 생성
- session scope 관리
- activeSessionId 관리

### 제외 범위

- LangState 수치 계산
- Dashboard Summary 집계
- Flashcard 생성 정책
- 교정 문장 선정 정책
- Session Memory Room Entity / DAO 상세 구현

> Session Memory 원문 turn list는 고빈도 append 데이터이므로 LS-005 정책에 따라 Room에 먼저 기록하고, Firestore에는 batch sync한다.
> Firebase Live API 세션은 장기 재개 대상으로 보지 않고, 앱 상태 복원 뒤 새 LiveSession을 연결하는 방식으로 관리한다.

---

## 4. GitHub Issue (실행 기준 / SSOT)

- RT-001 세션 부트스트랩
- RT-002 음성 스트림
- RT-003 turn 저장
- RT-004 재연결

---

## 5. 연결 문서

- [SYS_REALTIME_INFRA_OVERVIEW.md](./SYS_REALTIME_INFRA/SYS_REALTIME_INFRA_OVERVIEW.md)
- [FLOW_AI_CHAT.md](../User_FlowDB/FLOW_AI_CHAT.md)
- [Umma_Service_Structure.md](../Umma_Service_Structure.md)
- [Umma_Data_Strategy_and_Language_State.md](../Umma_Data_Strategy_and_Language_State.md)
