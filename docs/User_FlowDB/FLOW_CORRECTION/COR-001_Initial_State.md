# [Feature] COR-001 Correction 초기 상태 로드

## User Story

사용자는 Correction 화면에 진입했을 때 현재 선택 언어 기준으로 교정 가능한 대화가 있는지 확인할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] Correction 화면 진입 시 Global Learning State에서 `selectedLearningLanguage`를 확인한다.
- [ ] 현재 선택 언어의 `SessionSummary`를 로드한다.
- [ ] `SessionSummary.correctionAvailable`을 기준으로 교정 가능 여부를 판단한다.
- [ ] 현재 선택 언어의 `LangState` snapshot을 로드한다.
- [ ] 현재 선택 언어의 RT-003 correction context 조회 준비 상태를 확인한다.
- [ ] 언어 없음, 세션 없음, 교정 불가 상태는 Empty UI로 분기한다.
- [ ] 초기 로딩 중 중복 요청과 중복 초기화가 방지된다.
- [ ] Ready 상태가 되면 사용자 버튼 없이 `COR-002` 교정 결과 생성 흐름으로 이어질 수 있다.

---

# Flow (링크)

- [FLOW-CORRECTION](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION.md)
- [COR-001 → Correction 초기 상태 로드](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md)
- [SYS-LEARNING-STATE-INFRA → GlobalLangState / SessionSummary / LangState](https://github.com/LIKELION-Android-BOOTCAMP-6th/Umma/blob/docs/flow/docs/System_FlowDB/SYS_LEARNING_STATE_INFRA.md)

---

# 구현 범위

## 포함 범위

- `CorrectionViewModel` 초기 상태 구성
- selected language / SessionSummary / LangState snapshot 로드
- Loading / Empty / Error 상태

## 제외 범위 (Out of Scope)

- 교정 결과 생성
- 교정 결과 카드 UI
- Flashcard 저장
- Session Memory 압축 또는 저장소 구현
- RT-003 correction context 내부 필터링 구현

---

# Details

## 상태 로드 역할

초기 상태 로드는 Correction 화면이 실제 교정 결과를 만들기 전에,
현재 선택 언어 기준으로 교정 가능한 세션이 있는지 확인하는 단계다.

## 사용 데이터

- `GlobalLangState`
- `UserLangPref.selectedLang`
- `SessionSummary`
- `LangState`

## 판단 기준

`SessionSummary.correctionAvailable`이 Correction 진입 판단의 기준이다.
`DashSummary.correctionAvailable`은 Dashboard 카드 표시용 파생값이므로 진입 판단에는 사용하지 않는다.

## 구현 가이드

```text
Correction 화면 진입
→ GlobalLangState 관찰
→ selectedLearningLanguage 확인
→ currentSessionSummary() 확인
→ currentLangState() 확인
→ 조건 미충족 시 Empty 표시
```

Empty 상태는 짧은 안내와 AI Chat 이동 CTA를 제공할 수 있다.

---

## 작업 지시

- `CorrectionViewModel`이 화면의 초기 상태 owner가 되도록 구성한다.
- 현재 선택 언어는 route 인자가 아니라 Global Learning State에서 읽는다.
- 교정 가능 여부는 `SessionSummary.correctionAvailable`만 기준으로 삼는다.
- `DashSummary.correctionAvailable`은 Dashboard 표시용 값이므로 진입 판단에는 사용하지 않는다.
- Session Memory 저장소를 직접 구현하지 않고 RT-003의 correction context 조회 UseCase를 사용할 준비 상태만 확인한다.
- 상태가 부족한 경우 Error보다 Empty로 우선 분기하되, 로드 자체가 실패한 경우만 Error로 둔다.

---

# 기술 설계 가이드

## 권장 구조

```text
presentation/correction/
→ CorrectionViewModel
→ CorrectionUiState
```

## 상태 구조 예시

```text
Loading
Empty(reason)
Ready(lang, sessionSummary, langState)
Error(message)
```

Ready 상태는 교정 결과 생성을 시작할 수 있는 상태다.
Correction 화면에는 별도의 "교정 요청" 버튼을 두지 않으므로,
Ready 상태가 되면 ViewModel 흐름에서 `COR-002` 교정 결과 생성으로 이어진다.

---

## 검증 기준

- 선택 언어가 없으면 Empty 상태가 표시된다.
- `SessionSummary.correctionAvailable == false`이면 Empty 상태가 표시된다.
- 조건이 충족되면 `COR-002` 교정 결과 생성을 시작할 수 있는 Ready 상태가 된다.
- Ready 상태 이후 사용자의 추가 버튼 클릭 없이 교정 결과 생성 흐름으로 이어진다.

---

# Edge Cases

- `selectedLearningLanguage`가 없음
- 현재 선택 언어의 `SessionSummary`가 없음
- `SessionSummary.correctionAvailable == false`
- 현재 선택 언어의 `LangState` snapshot이 없음
- 로딩 중 화면 재진입으로 초기화 요청이 중복 실행됨
