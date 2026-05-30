# [Feature] SRS-003 카드 앞/뒤 표시 및 뒤집기

## User Story

사용자는 Flashcard의 앞면과 뒷면을 넘겨 보면서,
모국어 문장과 교정된 외국어 문장을 번갈아 확인할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] 앞면에는 모국어 문장이 표시된다.
- [ ] 뒷면에는 교정된 외국어 문장과 짧은 설명이 표시된다.
- [ ] hint가 있으면 보조 문구로 표시된다.
- [ ] 카드 뒤집기 동작이 정상 동작한다.
- [ ] 카드가 넘어갈 때 이전 카드의 뒤집기 상태는 남지 않는다.
- [ ] 긴 문장과 설명이 레이아웃을 깨지 않는다.

---

## Flow (링크)

- FLOW-SRS
- SRS-003 → 카드 앞/뒤 표시 및 뒤집기

---

## 구현 범위

### 포함 범위

- 카드 front / back 렌더링
- flip interaction
- explanation / hint 표시
- 현재 카드의 내용 표시 상태 관리

### 제외 범위 (Out of Scope)

- 발음 재생
- 평가 버튼
- 스케줄 갱신
- deck 로드
- local 저장

---

## Details

## 카드 구성

```text
frontText
→ 모국어 문장

backText
→ 교정된 외국어 문장

explanation
→ 짧은 교정 설명

hint
→ 회상 보조 문구
```

카드 앞/뒤의 표시 목적은 학습자의 회상을 돕는 것이다.  
여기서는 아직 정답 평가나 다음 카드 이동을 결정하지 않는다.

---

## 표시 정책

- 앞면은 한 번에 읽기 쉬운 길이로 배치한다.
- 뒷면은 문장, 설명, hint를 시각적으로 구분한다.
- 카드 뒤집기 애니메이션은 있어도 되고 없어도 되지만, 상태는 반드시 분리한다.
- 다음 카드로 넘어가면 flip state는 초기화한다.

---

## 권장 구현 경계

- `presentation/srsstudy/components/SrsStudyCard.kt`
- `presentation/srsstudy/components/SrsStudyCardFront.kt`
- `presentation/srsstudy/components/SrsStudyCardBack.kt`
- `presentation/srsstudy/SrsStudyViewModel.kt`

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
└── presentation/srsstudy/components/
    ├── SrsStudyCard.kt
    ├── SrsStudyCardFront.kt
    └── SrsStudyCardBack.kt
```

> flip state는 카드 자체의 표시 상태이므로 score state와 섞지 않는다.
> 이렇게 분리해야 발음 재생이나 평가 버튼과 서로 엉키지 않는다.

---

## Edge Cases

- 모국어 문장이나 외국어 문장이 너무 긴 경우
- explanation 또는 hint가 비어 있는 경우
- 카드 뒤집기 중 다음 카드가 로드되는 경우
- 한 카드의 flip 상태가 다음 카드에 누수되는 경우
