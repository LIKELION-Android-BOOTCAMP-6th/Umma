# SYS-LEARNING-STATE-INFRA Overview

## 1. 이 문서의 목적

SYS-LEARNING-STATE-INFRA는 Umma 앱에서 가장 중요한 공통 기반이다.

이 구조는 다음 기능들이 같은 기준을 보게 만든다.

- Dashboard
- AI Chat
- Correction
- Flashcard
- Statistics

핵심은 하나다.

> 사용자의 학습 상태를 어디에 저장하고, 언제 갱신하고, 어떤 화면이 어떤 데이터만 읽을지
> 먼저 정해두는 것

---

## 2. 한 문장 요약

Umma는

**대화 원문은 이후 Session Memory에 두고, 장기 능력은 LangState에 두고, 화면 빠른 출력은 DashSummary / SessionSummary / FlashcardSummary에 두고, 전역 선택 상태는 UserLangPref / GlobalLangState로 묶는다.**

---

## 3. 가장 중요한 데이터 묶음

### 3-1. `UserLangPref`

사용자가 지금 어떤 언어를 배우고 있는지, 어떤 언어를 화면에서 보고 있는지 정한다.

- `nativeLang`
- `primaryLang`
- `selectedLang`
- `learningLangs`

이 값은 "현재 앱이 어떤 언어를 바라보는가"를 결정하는 기준이다.

---

### 3-2. `LangState`

사용자의 **장기 언어 능력 상태**다.

이 값은 쉽게 말하면:

- 문법이 얼마나 안정적인가
- 어휘가 얼마나 넓은가
- 말이 얼마나 자연스러운가
- 복습 효과가 얼마나 쌓였는가

를 담는 본체다.

구조는 두 층으로 나뉜다.

- `InternalMetrics`: AI 적응과 분석용
- `ExternalMetrics`: 사용자 화면과 통계 노출용

즉, 내부 계산값과 외부 표시값을 분리해 둔 것이다.

---

### 3-3. `SessionSummary`

세션 원문 전체를 화면에 직접 들고 다니지 않고, 필요한 판단값만 남긴 요약이다.

- 최근 대화 길이
- 최근 대화 주제
- 교정 가능 여부

이 값은 AI Chat 진입, 교정 진입, 대시보드 안내용으로 쓴다.

---

### 3-4. `DashSummary`

Dashboard가 빠르게 렌더링하기 위한 요약 카드 데이터다.

Dashboard는 여기만 보고 화면을 그린다.

- 최근 대화 시간
- 최근 주제
- 교정 가능 여부
- 복습 카드 수
- 성취 변화량

Dashboard가 `recentFullContext` 전체를 직접 계산하면 느려지고 복잡해지기 때문에, 이 요약이 반드시 따로 있다.

---

### 3-5. `FlashcardSummary`

Dashboard가 복습 카드 상태를 빠르게 보여주기 위한 요약이다.

- 오늘 복습해야 할 카드 수
- 최근 저장한 카드 수

실제 Flashcard 원본과 SRS 계산은 별도 Flashcard Flow에서 다루고,
Dashboard는 이 요약만 보고 Empty 또는 복습 필요 상태를 표시한다.

---

### 3-6. `SessionMemory`

대화 원문 turn list와 `recentFullContext`를 담는 저장 모델이다.

다만 온보딩과 LS-007에서는 실제 Session Memory 원문 문서를 만들지 않는다.
원문 turn 저장 구조는 `SYS-REALTIME-INFRA`의 RT-003에서 확정한다.

---

## 4. 전역 상태가 왜 필요한가

앱 전체가 같은 사용자의 같은 언어 상태를 보도록 하기 위해서다.

예를 들면:

- Dashboard는 현재 선택 언어의 `DashSummary`를 본다.
- AI Chat은 현재 선택 언어의 `LangState`와 `SessionSummary`를 본다.
- Correction은 이후 Session Memory의 `recentFullContext`와 `LangState`를 함께 본다.
- Flashcard는 카드 요약과 복습 상태를 본다.

이걸 화면마다 따로 계산하면 서로 어긋난다.
그래서 `GlobalLangState`가 필요하다.

---

## 5. 전체 흐름

아래 순서로 이해하면 가장 쉽다.

```text
앱 실행
→ Local preload
→ GlobalLangState 구성
→ Dashboard 먼저 빠르게 렌더링
→ 사용자가 AI Chat 시작
→ Session Memory에 turn 추가
→ 대화 종료 또는 교정 시점 도달
→ LangState 업데이트 계산
→ DashSummary 재계산
→ Local 저장
→ Firebase background sync
```

여기서 중요한 점은:

- 대화 중에는 원칙적으로 Firebase에 매번 쓰지 않는다.
- 먼저 로컬과 메모리에 반영한다.
- 화면은 요약값으로 빠르게 보여준다.

---

## 6. 각 단계에서 무엇을 읽고 무엇을 쓰는가

### 6-1. AI Chat

읽는 것:

- `UserLangPref`
- 현재 선택 언어의 `LangState`
- 현재 선택 언어의 `SessionSummary`

쓰는 것:

- 이후 Session Memory의 `recentFullContext`
- 대화 turn 확정값

AI Chat의 목적은 말하는 흐름을 유지하는 것이고, 장기 상태 자체를 매 턴 갱신하는 것이 아니다.

---

### 6-2. Correction

읽는 것:

- 이후 Session Memory의 `recentFullContext`
- `LangState`
- `SessionSummary`

쓰는 것:

- 교정 결과
- `LangStateUpdateInput`
- 필요 시 `DashSummary`

Correction은 사용자가 말한 내용을 학습 가치가 있는 문장으로 다시 정리하는 단계다.

---

### 6-3. Flashcard

읽는 것:

- 교정 결과
- 복습 요약

쓰는 것:

- 플래시카드 생성
- 복습 이벤트 기록
- `reviewRetention` 반영

---

### 6-4. Dashboard

읽는 것:

- `DashSummary`
- `GlobalLangState.selectedLang`

쓰기보다는 표시가 목적이다.

Dashboard가 원본 대화 전문을 직접 만지지 않도록 설계한 이유가 여기 있다.

---

## 7. 업데이트 원칙

### 7-1. 업데이트는 batch로 한다

`LangState`는 대화 중 즉시 바꾸지 않는다.

대화가 끝난 뒤, 또는 교정이 끝난 뒤, 또는 복습 결과가 모였을 때 한 번에 계산한다.

이유:

- 비용 절감
- 상태 흔들림 방지
- 추적 가능성 확보

---

### 7-2. UseCase가 계산하고 Repository가 저장한다

이 프로젝트의 기준은 다음과 같다.

```text
UseCase
→ 다음 상태를 계산
→ preparedState 생성

Repository
→ preparedState를 원자적으로 저장
```

즉, 저장소가 똑똑해지는 구조가 아니라, 계산 책임은 위에서 끝내고 저장만 아래에서 맡는다.

---

### 7-3. 중복 반영을 막는다

같은 분석 결과를 두 번 반영하면 `LangState`가 왜곡된다.

그래서 다음 값을 남긴다.

- `analysisEventId`
- `lastAnalyzedAt`
- `lastAnalysisEventId`

이 값은 "언제 어떤 분석이 반영됐는지"를 추적하기 위한 안전장치다.

---

## 8. 처음에 기억하면 좋은 것

이 구조를 처음 볼 때는 아래만 먼저 기억하면 된다.

1. **원문은 Session Memory**
2. **장기 능력은 LangState**
3. **빠른 화면은 DashSummary**
4. **현재 언어 선택은 UserLangPref**
5. **전역 스냅샷은 GlobalLangState**
6. **계산은 UseCase, 저장은 Repository**

이 6개만 머리에 잡히면 나머지 세부 문서는 훨씬 읽기 쉬워진다.

---

## 9. 관련 문서 읽는 순서

처음 보면 아래 순서가 가장 편하다.

1. `LS-001 Language State Model Structure`
2. `LS-002 Dashboard Summary Model`
3. `LS-003 User Learning Preference Model`
4. `LS-004 Global Learning State Store`
5. `LS-005 Local Cache & Sync Policy`
6. `LS-006 Language State Update Policy`

이 순서대로 읽으면

- 무엇이 저장되는지
- 무엇이 요약인지
- 무엇이 전역 상태인지
- 언제 동기화되는지
- 언제 갱신되는지

가 자연스럽게 이어진다.
