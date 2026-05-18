# [Feature] RT-003 turn 저장

## User Story

사용자는 AI와 나눈 대화가 turn 단위로 저장되고,
교정과 플래시카드 생성이 같은 Session Memory를 기준으로 동작하기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 확정된 user turn과 assistant turn이 Session Memory에 append 된다.
- [ ] `recentFullContext`가 turn 확정 이후에만 갱신된다.
- [ ] 부분 스트림 데이터는 저장 경계에 포함되지 않는다.
- [ ] 교정/플래시카드 흐름이 Session Memory를 함께 참조할 수 있다.
- [ ] turn 확정 시 중복 반영이 방지된다.
- [ ] Session Memory 압축 이후 원문 buffer 정리 정책이 존재한다.

---

# Flow (링크)

- SYS-REALTIME-INFRA
- RT-003 → turn 저장

---

# 구현 범위

## 포함 범위

- Session Memory 원문 turn 모델 정의
- turn append 정책
- recentFullContext 갱신
- 교정 입력용 context 준비
- 플래시카드 입력용 context 준비
- buffer 압축 정책
- Room 기반 local append와 Firestore batch sync 경계

## 제외 범위 (Out of Scope)

- turn 생성 자체
- 오디오 캡처
- LangState 계산
- Dashboard Summary 집계
- Room Entity / DAO의 세부 SQL 최적화

---

# Details

## 저장 경계

저장 대상은 다음 순서로 정리한다.

1. 확정된 user turn
2. 확정된 assistant turn
3. 대화 요약 메타데이터
4. 압축된 session context

---

## 분리 원칙

- 스트리밍 중간 결과와 확정 결과를 섞지 않는다.
- `recentFullContext`는 full context의 원문 버퍼이자 교정 기준이다.
- 교정/플래시카드는 이 버퍼를 읽기만 하고, 수정 책임은 갖지 않는다.

## 권장 구현 경계

- `domain/model/realtime/SessionMemoryModels.kt`
- `domain/repository/SessionMemoryRepository.kt`
- `domain/usecase/realtime/AppendTurnUseCase.kt`
- `domain/usecase/realtime/CompressSessionMemoryUseCase.kt`
- `data/repository/SessionMemoryRepositoryImpl.kt`
- `data/source/local/SessionMemoryLocalDataSource.kt`
- `data/source/remote/SessionMemoryRemoteDataSource.kt`

## 현재 테스트 코드 전환 메모

- 현재 AI 대화 테스트 코드는 음성 송수신 중심이므로, Session Memory append 계층은 새로 추가해야 한다.
- `AIEvent.TextResponse`에서 최종 user/assistant transcript가 구분된 뒤에만 append한다.
- `ChatRepositoryImpl`은 Firebase Live API 연결과 이벤트 수신을 담당하고, Session Memory 저장 책임은 별도 Repository/UseCase로 분리한다.
- `recentFullContext`는 전체 문자열이 아니라 turn list로 저장한다.

## 한 줄 가이드

- 확정된 turn만 저장하고, 분석 전 중간 chunk는 버린다.
