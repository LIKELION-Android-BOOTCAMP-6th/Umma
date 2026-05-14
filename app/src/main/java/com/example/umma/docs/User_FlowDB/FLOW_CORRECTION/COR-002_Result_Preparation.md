# [Feature] COR-002 교정 결과 준비

## User Story

사용자는 교정 화면에 진입한 뒤 별도의 후보 선택이나 교정 요청 버튼 없이,
최근 대화 기반 교정 결과 카드가 준비되는 흐름을 경험할 수 있다.

---

## 완료 기준(AC)

- [ ] `recentFullContext` 전체를 화면에 그대로 노출하지 않는다.
- [ ] 현재 선택 언어의 Session Memory만 사용한다.
- [ ] user turn 중심으로 교정 후보를 내부 추출한다.
- [ ] MVP에서는 최근 100턴까지만 후보 추출 대상으로 삼는다.
- [ ] 추출된 후보 목록을 사용자에게 선택 UI로 노출하지 않는다.
- [ ] 내부 후보는 `CorrectionCandidate` domain 계약을 사용한다.
- [ ] 후보가 없으면 AI 요청 없이 Empty 상태를 표시한다.
- [ ] 후보가 있으면 `LangState` snapshot과 함께 교정 결과와 설명을 생성한다.
- [ ] 생성된 교정 결과는 `CorrectionSuggestion` 계약으로 이어지도록 준비한다.
- [ ] mock 결과와 실제 AI 결과가 같은 `CorrectionSuggestion` 계약으로 이어지도록 준비한다.

---

## 구현 범위

### 포함

- `CorrectionCandidate` domain 모델 정의
- 후보 추출 UseCase
- 최근 100턴 제한 정책값
- 교정 결과 생성 Repository interface 확인
- 교정 결과와 설명 생성 규칙
- mock/real 교체 가능한 결과 준비 흐름
- Loading / Empty / Error 상태

### 제외

- 교정 결과 카드 세부 UI
- Flashcard 저장
- Session Memory 원문 수정
- Dashboard Summary 갱신
- 프롬프트 품질 고도화

---

## 구현 가이드

```text
recentFullContext
→ 최근 100턴 제한
→ role = user turn 중심 후보 추출
→ 필요한 assistant turn만 짧은 문맥으로 첨부
→ CorrectionCandidate 내부 목록 생성
→ LangState snapshot 기반 교정 결과와 설명 생성
→ CorrectionSuggestion 생성
```

Composable은 `recentFullContext`를 직접 파싱하지 않는다.
ViewModel은 UseCase 결과만 관찰하고, 후보 추출 규칙은 domain 계층에 둔다.

---

## 사용자 경험 기준

- 화면에는 후보 목록이 아니라 Loading / Empty / Error / 결과 카드 상태만 표시된다.
- 별도의 "교정 요청" 버튼은 두지 않는다.
- 사용자가 선택하는 대상은 후보 문장이 아니라 최종 교정 결과 카드다.
