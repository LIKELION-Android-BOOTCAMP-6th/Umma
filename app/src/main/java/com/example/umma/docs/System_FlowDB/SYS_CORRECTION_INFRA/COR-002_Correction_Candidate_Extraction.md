# [Feature] COR-002 교정 후보 추출 및 payload 구성

## User Story

사용자는 교정 화면에서 Session Memory 전체를 직접 읽지 않아도,
대화에서 바로 교정할 수 있는 후보만 추려진 상태로 교정을 시작할 수 있기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `recentFullContext` 전체를 그대로 AI에 전달하지 않는다.
- [ ] user turn 중심으로 교정 후보를 추출한다.
- [ ] assistant turn은 문맥 보조가 필요한 경우에만 제한적으로 포함한다.
- [ ] 후보가 없으면 AI 요청 없이 Empty 상태를 반환한다.
- [ ] 추출 결과는 `CorrectionCandidate` 같은 domain 모델로 고정한다.
- [ ] 후보 추출 결과는 화면 세션 안에서만 사용되고, 저장소 원문을 직접 수정하지 않는다.

---

# Flow (링크)

- SYS-CORRECTION-INFRA
- COR-002 → 교정 후보 추출 및 payload 구성

---

# 구현 범위

## 포함 범위

- `recentFullContext` 조회
- user turn 중심 후보 추출
- assistant turn 최소 보조 문맥 선택
- `CorrectionCandidate` 모델 정의
- 교정 요청용 payload 구성
- 후보 없음 상태 처리

## 제외 범위 (Out of Scope)

- AI 교정 제안 생성
- Flashcard 저장
- Session Memory 압축
- LangState 업데이트
- Dashboard 카드 렌더링

> 이 이슈는 후보를 고르고 payload를 만드는 일만 책임진다.
> 실제 AI 결과 생성과 저장 반영은 COR-003 / COR-004 / COR-005에서 처리한다.

---

# Details

## 후보 추출 정책

교정 후보는 기본적으로 `role = user` turn에서 추출한다.

```text
recentFullContext
→ user turn 추출
→ 짧은 발화 / 중복 발화 / 감탄사성 응답 제외
→ 필요한 assistant turn 일부만 추가
→ CorrectionCandidate 생성
```

전체 turn list를 그대로 보내지 않는다.
MVP에서는 이번 세션의 user turn과 현재 LangState를 우선한다.

---

## Payload 정책

교정 요청 payload에는 다음만 포함한다.

- 후보 문장
- 필요한 주변 문맥
- LangState snapshot

payload는 AI가 문맥을 이해하는 데 필요한 최소 범위만 담는다.

---

## 현재 코드 기준 메모

- `recentFullContext`는 Session Memory에 저장된 원문 버퍼이며, 이 이슈에서는 읽기만 한다.
- 교정 후보는 저장소 자체가 아니라 도메인 모델로 분리해서 다룬다.
- 현재 코드에 `CorrectionCandidate` 모델이 아직 없다면, 이 이슈에서 먼저 계약을 고정해야 한다.

