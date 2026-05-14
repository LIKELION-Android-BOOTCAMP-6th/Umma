# [Feature] COR-003 교정 결과 카드

## User Story

사용자는 교정 화면에 진입한 뒤 준비된 교정 결과 카드에서
교정 전 문장, 교정 후 문장, 쉬운 설명을 확인할 수 있다.

---

## 완료 기준(AC)

- [ ] 교정 결과는 `CorrectionSuggestion` 계약으로 화면에 표시한다.
- [ ] 교정 전 문장과 교정 후 문장을 구분해 보여준다.
- [ ] 생성된 설명을 화면에 표시한다.
- [ ] 사용자가 Flashcard로 저장할 카드를 선택할 수 있다.
- [ ] AI 요청 실패, 파싱 실패 시 Error 상태와 Retry 동작을 제공한다.
- [ ] Retry는 같은 세션 메모리와 현재 선택 언어 기준으로 다시 수행한다.
- [ ] 목업 결과와 실제 API 결과는 같은 화면 모델을 사용한다.
- [ ] prompt engineering / JSON schema / retry 고도화는 MVP 후반부 작업으로 남긴다.

---

## 구현 범위

### 포함

- `CorrectionSuggestion` domain/UI 표시 모델 정의
- 교정 결과 카드 UI
- 카드 선택 상태
- 교정 전/후 문장 표시
- 설명 표시
- Loading / Error / Retry 상태
- mock 결과 기반 화면 흐름

### 제외

- 후보 목록 선택 UI
- Flashcard 실제 저장
- Session Memory 압축
- LangState 수치 계산
- Dashboard Summary 갱신

---

## 카드 표시 기준

예시:

```text
Before: I went museum yesterday.
After: I went to the museum yesterday.
Why: 장소 앞에는 보통 전치사 to를 사용합니다.
```

실제 UI 문구는 디자인 톤에 맞게 조정할 수 있다.
다만 카드가 담아야 할 정보는 교정 전 문장, 교정 후 문장, 설명, 저장 선택 상태다.

---

## 모델 분리 기준

- `CorrectionCandidate`: 내부 후보 추출 결과
- `CorrectionSuggestion`: 화면 표시와 Flashcard 저장 선택에 사용하는 결과
- `CorrectionResult`: LangState 업데이트 입력용 최소 결과

화면은 `CorrectionCandidate` 목록을 직접 렌더링하지 않는다.
`CorrectionSuggestion`과 `CorrectionResult`를 섞지 않는다.
