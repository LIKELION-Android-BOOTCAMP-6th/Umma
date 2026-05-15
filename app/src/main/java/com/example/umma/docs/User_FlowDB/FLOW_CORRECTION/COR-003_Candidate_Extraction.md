# [Feature] COR-003 교정 후보 내부 추출

## User Story

사용자는 별도의 후보 선택 없이, 최근 대화에서 교정할 만한 문장이 내부적으로 준비되는 흐름을 경험한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `recentFullContext` 전체를 화면에 그대로 노출하지 않는다.
- [ ] 현재 선택 언어의 Session Memory만 사용한다.
- [ ] MVP에서는 최근 100턴까지만 후보 추출 대상으로 삼는다.
- [ ] user turn 중심으로 교정 후보를 내부 추출한다.
- [ ] 필요한 assistant turn은 짧은 문맥으로만 첨부한다.
- [ ] 내부 후보는 `CorrectionCandidate` domain 계약을 사용한다.
- [ ] 추출된 후보 목록을 사용자에게 선택 UI로 노출하지 않는다.
- [ ] 후보가 없으면 AI 요청 없이 Empty 상태를 표시한다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-002 → Correction 초기 상태 로드
- COR-003 → 교정 후보 내부 추출
- SYS-REALTIME-INFRA → Session Memory / recentFullContext

---

# 구현 범위

## 포함 범위

- `CorrectionCandidate` domain 모델 정의 또는 연결
- 후보 추출 UseCase
- 최근 100턴 제한 정책값
- 후보 없음 Empty 분기

## 제외 범위 (Out of Scope)

- AI 교정 결과 생성
- 교정 결과 카드 UI
- Flashcard 저장
- Session Memory 원문 수정

---

# Details

## 후보 추출 역할

후보 추출은 교정 결과 생성을 위한 내부 준비 단계다.
사용자는 후보 목록을 직접 보거나 선택하지 않는다.

## 사용 데이터

- 현재 선택 언어의 Session Memory
- `recentFullContext`
- user turn
- 필요한 경우 짧은 assistant turn 문맥

## 추출 정책

- MVP에서는 최근 100턴까지만 사용한다.
- user turn을 중심으로 후보를 만든다.
- assistant turn은 사용자의 발화 의미를 보조하는 문맥으로만 사용한다.
- 후보가 없으면 AI 요청을 하지 않는다.

## 구현 가이드

```text
recentFullContext
→ 최근 100턴 제한
→ role = user turn 중심 후보 추출
→ 필요한 assistant turn만 짧은 문맥으로 첨부
→ CorrectionCandidate 내부 목록 생성
```

Composable은 `recentFullContext`를 직접 파싱하지 않는다.
ViewModel은 UseCase 결과만 관찰하고, 후보 추출 규칙은 domain 계층에 둔다.

---

## 작업 지시

- 후보 추출 로직은 domain UseCase에 둔다.
- 후보는 화면 표시 모델이 아니라 `CorrectionCandidate` 내부 모델로 다룬다.
- 최근 100턴 제한은 상수나 정책값으로 분리해 추후 조정 가능하게 둔다.
- user turn을 우선 대상으로 삼고, assistant turn은 의미 파악에 필요한 짧은 문맥으로만 붙인다.
- 후보가 0개인 경우 `CorrectionRepository`를 호출하지 않는다.

---

# 기술 설계 가이드

## 권장 구조

```text
domain/model/correction/
→ CorrectionCandidate

domain/usecase/correction/
→ ExtractCandidatesUseCase
```

## 정책값

```text
maxCandidateSourceTurns = 100
primaryTargetRole = user
```

정책값은 추후 조정 가능하도록 UseCase 내부 상수 또는 별도 policy로 분리한다.

---

## 검증 기준

- 100턴을 초과하는 context가 들어와도 최근 100턴만 대상으로 삼는다.
- user turn이 없으면 후보 없음으로 처리한다.
- 후보 목록은 UI에 직접 노출되지 않는다.

---

# Edge Cases

- `recentFullContext`가 비어 있음
- 최근 100턴 안에 user turn이 없음
- assistant turn만 있어 후보를 만들 수 없음
- 후보 문장이 너무 짧거나 의미가 불명확함
- Session Memory 언어와 현재 선택 언어가 일치하지 않음
