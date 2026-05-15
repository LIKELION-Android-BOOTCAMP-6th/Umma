# [Feature] SRS-004 발음 재생

## User Story

사용자는 Flashcard 뒷면의 교정된 외국어 문장을 들으면서,
표기만 보는 것이 아니라 실제 발음도 함께 확인할 수 있어야 한다.

---

## 완료 기준(AC) (Acceptance Criteria)

- [ ] speaker 버튼이 표시된다.
- [ ] speaker 버튼을 누르면 현재 카드의 외국어 문장이 재생된다.
- [ ] MVP에서는 Android `TextToSpeech`를 사용한다.
- [ ] 발음 재생은 카드 저장이나 평가 로직과 독립적으로 동작한다.
- [ ] 재생 중 중복 탭이 안전하게 처리된다.
- [ ] 발음 재생 실패 시 화면이 깨지지 않는다.

---

## Flow (링크)

- FLOW-SRS
- SRS-004 → 발음 재생

---

## 구현 범위

### 포함 범위

- speaker 버튼 UI
- TextToSpeech 호출
- 현재 카드 텍스트 읽기
- 재생 상태 피드백

### 제외 범위 (Out of Scope)

- 클라우드 TTS 연동
- 카드 deck 로드
- 평가 버튼
- 스케줄 갱신
- 저장소 동기화

---

## Details

## 재생 대상

발음 재생은 뒷면의 교정된 외국어 문장을 대상으로 한다.

```text
backText
→ TextToSpeech
```

---

## 재생 정책

- MVP에서는 Android `TextToSpeech`를 사용한다.
- 발음 재생은 카드 데이터 저장과 별개로 동작한다.
- 재생 완료 후에는 화면 상태만 원복한다.
- 언어별 음성 설정은 현재 선택 언어에 맞춘다.

---

## 권장 구현 경계

- `presentation/srsstudy/components/PronunciationButton.kt`
- `presentation/srsstudy/SrsStudyViewModel.kt`
- `core/tts/` 또는 동등한 TTS 헬퍼 계층

---

## 기술 설계 가이드

## 권장 구조

```text
com.example.umma
├── presentation/srsstudy/components/
│   └── PronunciationButton.kt
└── core/tts/
    └── TextToSpeechController.kt
```

> 발음 재생은 반복학습의 보조 기능이지, 스케줄 정책의 일부가 아니다.
> 그래서 리뷰 저장과 분리해 두면 화면이 더 단순해진다.

---

## Edge Cases

- TTS 엔진이 설치되어 있지 않은 경우
- 현재 선택 언어의 발음을 지원하지 않는 경우
- 재생 중 버튼을 연속으로 누르는 경우
- 카드가 바뀌는 중에 재생이 시작되거나 종료되는 경우
- 앱이 백그라운드로 이동하는 경우
