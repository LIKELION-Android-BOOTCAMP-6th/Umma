# [Feature] COR-004 Flashcard 저장

## User Story

사용자는 교정 결과 카드 중 복습하고 싶은 표현을 선택해 Flashcard로 저장하고,
저장 성공 여부를 화면에서 확인할 수 있다.

---

## 완료 기준(AC)

- [ ] 사용자가 저장할 교정 결과 카드를 선택할 수 있다.
- [ ] 선택한 결과만 Flashcard 저장 요청으로 전달한다.
- [ ] 저장은 현재 선택 언어 기준으로 수행된다.
- [ ] Flashcard 저장은 `SCI-001`에서 정의한 `CorrectionRepository` 저장 계약을 따른다.
- [ ] `CorrectionRepository`는 선택된 교정 결과의 local first 저장과 sync 상태를 함께 다룬다.
- [ ] 저장은 local first 정책을 따른다.
- [ ] Room 저장은 COR-005의 로컬 완료 파이프라인 안에서 확정된다.
- [ ] 로컬 완료 파이프라인이 실패하면 Flashcard 저장을 포함한 로컬 변경을 롤백한다.
- [ ] Firestore sync 실패는 pending sync / dirty flag 개념으로 처리한다.
- [ ] 저장 중 Loading 상태를 표시한다.
- [ ] 로컬 완료 파이프라인 성공 후 Saved 상태를 표시한다.
- [ ] 저장 실패 시 Retry 동작을 제공한다.
- [ ] 저장 대상이 0개이면 완료/압축 단계로 바로 넘기지 않는다.

---

## 구현 범위

### 포함

- 저장 대상 선택 UI
- 저장 요청 모델
- SaveCorrectionFlashcardsUseCase
- `SCI-001`에서 정의한 `CorrectionRepository` 저장 계약 연결
- local first 저장 상태 처리
- pending sync 상태 처리
- 저장 완료 후 COR-005로 이어지는 이벤트

### 제외

- SRS 복습 스케줄 계산
- Flashcard 학습 화면
- Session Memory 압축 실행
- LangState 수치 계산 공식

---

## 저장 정책

```text
선택된 CorrectionSuggestion
→ Flashcard 저장 요청 모델 변환
→ SCI-001의 CorrectionRepository 저장 계약 호출
→ COR-005의 로컬 완료 파이프라인에 포함
→ 로컬 완료 파이프라인 성공 시 Saved 상태 반환
→ Firestore background sync 실패 시 pending sync 상태 기록
```

Flashcard 저장은 사용자가 선택한 교정 결과를 저장 요청으로 넘기는 작업이다.
최종 Saved 상태는 COR-005의 로컬 완료 파이프라인이 성공한 뒤 표시한다.

로컬 완료 파이프라인이 실패하면 Flashcard 저장을 포함한 로컬 변경은 롤백한다.
Firestore sync가 실패해도 로컬 완료는 성공으로 보며, 이 경우 재시도 가능한 pending sync 상태를 남긴다.


---

## 완료 진입 정책

저장 대상이 있고 저장 요청 모델 변환이 성공해야 COR-005 완료 정리로 넘어갈 수 있다.
저장 항목이 0개이면 완료/압축을 수행하지 않는다.
