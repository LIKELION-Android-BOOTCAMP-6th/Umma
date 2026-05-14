# [Feature] COR-003 교정 결과 생성 및 표시

## User Story

사용자는 교정 후보를 선택했을 때,
AI가 교정 전후 문장과 설명을 정리해 주고, 필요하면 Flashcard 저장으로 이어질 수 있기를 기대한다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 교정 요청 payload에는 후보 문장, 필요한 문맥, LangState snapshot만 포함된다.
- [ ] AI 응답은 교정 전 문장, 교정 후 문장, 설명, Flashcard 저장 가능 여부로 변환된다.
- [ ] AI 응답 실패 시 재시도 가능한 Error 상태를 제공한다.
- [ ] 목업 결과와 실제 API 결과가 같은 `CorrectionSuggestion` 계약을 사용한다.
- [ ] 교정 표시용 모델과 LangState 업데이트 입력용 모델이 분리된다.
- [ ] AI 응답 파싱 실패 시 화면이 안전하게 복구된다.
- [ ] prompt engineering / JSON schema 강제 / retry 정책은 MVP 후반부에 고도화한다.

---

# Flow (링크)

- SYS-CORRECTION-INFRA
- COR-003 → 교정 결과 생성 및 표시

---

# 구현 범위

## 포함 범위

- AI 교정 요청
- AI 응답 파싱
- 교정 결과 표시 모델 정의
- `CorrectionSuggestion` 계약 정의
- Loading / Error / Retry 상태 처리
- Flashcard 저장 가능 여부 표시

## 제외 범위 (Out of Scope)

- Flashcard 실제 저장
- Session Memory 압축
- LangState batch update
- Dashboard Summary 갱신
- 후보 추출 로직

> 이 이슈는 “교정 결과를 보여주고 다음 액션으로 이어줄 수 있는지”에 집중한다.
> 실제 저장과 상태 갱신은 COR-004 / COR-005에서 이어진다.

---

# Details

## 결과 모델 정책

교정 화면 표시와 Flashcard 저장 선택에 필요한 결과는 `CorrectionSuggestion`으로 다룬다.

이 모델은 다음을 포함할 수 있다.

- 교정 전 문장
- 교정 후 문장
- 설명
- 저장 가능 여부

반면 `CorrectionResult`는 Language State 업데이트 입력용 최소 표현으로 따로 둔다.

---

## MVP 후반부 고도화

AI 응답 일관성, 설명 난이도, 파싱 복원력은
기본 화면 흐름이 안정화된 뒤 후반부에 고도화한다.

고도화 대상 예시:

- few-shot prompt
- JSON schema 강제
- retry / fallback 정책
- explanation 난이도 조절

---

## 오류 정책

다음 상황에서 Error 상태를 표시할 수 있다.

- AI 요청 실패
- 파싱 실패
- 응답 형식이 계약과 다름
- 일시적 네트워크 오류

Error 상태에서는 사용자가 다시 시도할 수 있어야 한다.

---

## 현재 코드 기준 메모

- `learningstate` 모델의 `CorrectionResult`는 상태 업데이트 입력용 최소 표현이다.
- 이 이슈에서 다루는 화면 결과는 `CorrectionSuggestion` 계약으로 구분한다.
- 목업과 실 API는 같은 domain 계약을 사용해야 한다.
