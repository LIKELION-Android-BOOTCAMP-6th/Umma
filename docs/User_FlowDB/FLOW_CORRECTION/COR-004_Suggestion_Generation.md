# [Feature] COR-004 교정 결과 생성

## User Story

사용자는 내부 후보 추출이 끝난 뒤 현재 언어 상태에 맞는 교정 결과와 설명을 받을 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] `CorrectionCandidate`와 `LangState` snapshot을 입력으로 사용한다.
- [ ] 교정 전 문장, 교정 후 문장, 설명을 포함한 `CorrectionSuggestion`을 생성한다.
- [ ] Flashcard 앞면에 사용할 모국어 문장을 함께 생성한다.
- [ ] Flashcard 발음 재생 대상은 교정된 외국어 문장으로 고정한다.
- [ ] MVP 기본 교정 결과 수는 최대 10개를 기준으로 한다.
- [ ] 교정 결과는 실제 대화에서 쓸 수 있는 구어체 문장을 우선한다.
- [ ] 교정 결과 난이도는 현재 `LangState`보다 약간 높은 수준을 목표로 한다.
- [ ] mock 결과와 실제 AI 결과가 같은 `CorrectionSuggestion` 계약으로 이어진다.
- [ ] AI 요청 실패 시 Error 상태를 반환한다.
- [ ] 파싱 실패 시 Error 상태를 반환한다.
- [ ] Retry는 같은 Session Memory와 현재 선택 언어 기준으로 다시 수행한다.
- [ ] prompt engineering / JSON schema / retry 고도화는 MVP 후반부 작업으로 남긴다.

---

# Flow (링크)

- FLOW-CORRECTION
- COR-003 → 교정 후보 내부 추출
- COR-004 → 교정 결과 생성
- SYS-CORRECTION-INFRA → CorrectionRepository 계약

---

# 구현 범위

## 포함 범위

- 교정 결과 생성 UseCase 연결
- `CorrectionRepository` 결과 생성 계약 연결
- mock/real 교체 가능한 결과 생성 흐름
- Loading / Error / Retry 상태

## 제외 범위 (Out of Scope)

- 후보 추출 규칙
- 결과 카드 세부 UI
- Flashcard 저장
- LangState 수치 계산 공식

---

# Details

## 교정 결과 생성 역할

내부 후보와 현재 `LangState` snapshot을 사용해 사용자가 볼 수 있는 교정 결과를 생성한다.
결과 모델은 화면 표시와 저장 선택에 사용할 `CorrectionSuggestion`으로 통일한다.

## 사용 데이터

- `CorrectionCandidate`
- `LangState` snapshot
- 현재 선택 언어
- `CorrectionRepository`

## 생성 정책

- 기본적으로 최대 10개의 `CorrectionSuggestion`을 생성한다.
- 모든 교정 결과는 실제 사용자의 발화에서 파생되어야 한다.
- 문법만 고치기보다 사용자가 말하려던 의미를 자연스러운 구어체 문장으로 복원한다.
- 사용자의 현재 `LangState`를 크게 벗어나지 않고, 약 10% 정도 성장 가능한 난이도를 목표로 한다.
- Flashcard 앞면에 사용할 모국어 문장을 함께 생성한다.
- Flashcard 뒷면에는 교정된 외국어 문장과 짧은 교정 설명을 표시할 수 있어야 한다.
- 교정된 외국어 문장은 이후 발음 재생 대상이 되므로 텍스트가 명확하게 분리되어야 한다.

## 실패 정책

- AI 요청 실패는 Error 상태로 분기한다.
- 응답 파싱 실패도 Error 상태로 분기한다.
- Retry는 같은 Session Memory와 현재 선택 언어 기준으로 다시 수행한다.

## 구현 가이드

```text
CorrectionCandidate list
→ LangState snapshot
→ CorrectionRepository
→ CorrectionSuggestion list
```

이 이슈는 화면 카드 배치를 완성하지 않는다.
화면 표시는 `COR-005`에서 다룬다.

---

## 작업 지시

- `CorrectionRepository`는 같은 입력에 대해 mock/real 모두 `CorrectionSuggestion` 목록을 반환하도록 맞춘다.
- prompt 품질 고도화는 이번 이슈의 목표가 아니므로, 우선 구조와 실패 처리를 안정화한다.
- AI 응답 파싱 실패와 네트워크 실패는 모두 Error 상태로 올리고 Retry가 가능해야 한다.
- Retry는 새 세션을 만들지 않고 현재 선택 언어와 같은 Session Memory 기준으로 다시 수행한다.
- `CorrectionResult`는 LangState 업데이트 입력용이므로 화면 표시 모델과 섞지 않는다.

---

# 기술 설계 가이드

## 권장 구조

```text
domain/usecase/correction/
→ GenerateSuggestionsUseCase

domain/repository/
→ CorrectionRepository

data/repository/
→ FakeCorrectionRepository
→ CorrectionRepositoryImpl
```

## 결과 계약

```text
CorrectionCandidate + LangState snapshot
→ CorrectionSuggestion list
```

mock과 real 구현체는 같은 `CorrectionSuggestion` 계약을 반환한다.

## CorrectionSuggestion 최소 필드

```kotlin
data class CorrectionSuggestion(
    val id: String,
    val lang: LangCode,
    val sourceTurnId: String?,
    val beforeText: String,
    val afterText: String,
    val nativeText: String,
    val explanation: String
)
```

- `id`: 카드 선택과 저장 요청 식별자
- `lang`: 현재 선택 언어
- `sourceTurnId`: 원본 user turn 추적용. MVP에서 원본 turn id가 없으면 null 허용
- `beforeText`: 교정 전 문장
- `afterText`: 교정 후 문장
- `nativeText`: Flashcard 앞면에 표시할 모국어 문장
- `explanation`: 사용자에게 보여줄 쉬운 설명

Flashcard 저장 시 `nativeText`는 앞면, `afterText`와 `explanation`은 뒷면에 사용한다.
발음 재생은 `afterText`를 대상으로 하며, MVP에서는 Android `TextToSpeech`를 사용한다.

---

## 검증 기준

- mock repository만으로 `CorrectionSuggestion` 목록을 받을 수 있다.
- mock과 real 결과가 같은 최소 필드를 채운다.
- 결과 수가 10개를 초과하지 않는다.
- AI 실패 또는 파싱 실패 시 카드 화면으로 넘어가지 않고 Error 상태가 된다.
- Retry 시 같은 언어와 같은 세션 기준으로 다시 요청한다.

---

# Edge Cases

- 후보는 있으나 AI 응답이 비어 있음
- AI 응답 JSON 구조가 깨짐
- 일부 후보만 교정 결과로 변환됨
- 결과가 10개를 초과함
- `nativeText` 생성이 실패함
- 네트워크 실패로 교정 결과 생성이 중단됨
- Retry 중 선택 언어가 변경됨
