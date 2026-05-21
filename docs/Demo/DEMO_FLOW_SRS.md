# Demo Scenario — FLOW-SRS

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름의 TODO 시나리오.
> 기본은 Real 바인딩 (Correction에서 저장된 Flashcard 또는 시드 deck + Android `TextToSpeech`).
> due 없음 / deck 로드 실패 / 저장 실패 / TTS 실패 분기는 Mock fixture로 시연.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: 현재 선택 언어의 due Flashcard 3개 이상 시드된 상태 (Correction 데모 직후 자연스럽게 충족 가능).
> - Mock 토글: `FlashcardRepository` fake 구현 + `FakeFixtures` (Empty / Error / SaveFailure / PendingSync) — 현재 미존재, DASH 스타일 `RepositoryModule` 토글 추가 필요.
> - TTS: 실기기에 한국어/영어/일본어 등 데모 언어 TTS 엔진 설치 사전 확인. 미설치 시 시나리오 8 분기로 시연.
> - Mock fixture가 필요한 시나리오: 5·6·7. 그 외는 Real로 시연.
> - 발표 후 Real 바인딩으로 원복.

---

## 시나리오 1 — SrsStudy 진입 + 카드 학습 (SRS-001 / SRS-002 / SRS-003)

1. Dashboard에서 "Flashcard 학습" 카드 클릭 (due 수치 표시 확인)
2. SrsStudy 화면 진입 → Loading 노출
3. 현재 선택 언어의 due deck 로드 완료 → 첫 카드 앞면 표시 (Content)
4. 앞면: 모국어 문장 표시 확인
5. 카드 탭하여 뒤집기 → 뒷면: 교정된 외국어 문장 + 짧은 설명 + (있으면) hint 표시
6. 뒷면 → 앞면 다시 뒤집기 동작 확인
7. 빠른 연속 탭으로 중복 Navigation/flip이 발생하지 않는지 확인

---

## 시나리오 2 — 발음 재생 (SRS-004)

1. 시나리오 1에서 카드 뒷면 표시 상태
2. speaker 버튼 탭 → 외국어 문장 TTS 재생 (Speaking)
3. 재생 중 speaker 버튼 연속 탭 → 중복 재생 없이 안전 처리
4. 재생 완료 후 버튼 상태 원복 (Ready)
5. 다음 카드로 넘어가도 이전 카드의 재생 상태가 누수되지 않는지 확인

---

## 시나리오 3 — 4단계 평가 + 다음 카드 (SRS-005 / SRS-006)

1. 카드 뒷면에서 `Good` 버튼 탭 (Grading)
2. `ReviewSchedulePolicy`로 interval / easeFactor / nextReviewAt 갱신 → local first 저장 (Saving)
3. 저장 성공 → 다음 카드로 자동 이동, 새 카드는 앞면 + flip state 초기화 상태 (Success)
4. 같은 버튼을 빠르게 두 번 눌렀을 때 1회만 적용 확인 (logcat `duplicate blocked`)
5. 두 번째 카드는 `Again` 탭 → 당일 재노출 큐로 남는지 확인 (interval = 0)
6. 세 번째 카드는 `Easy` 탭 → 더 긴 interval로 갱신되는지 확인 (logcat에서 갱신 값 출력)

---

## 시나리오 4 — 덱 완료 + Dashboard 복귀 (SRS-006)

1. 마지막 카드 평가 후 저장 완료 → 완료 상태 화면 표시 (Done)
2. Dashboard 복귀 CTA 클릭 → Dashboard로 이동
3. Flashcard 학습 카드의 due 수치가 평가 결과에 따라 최신 상태로 갱신됨 확인
4. 학습 카드 색상/Empty 상태 갱신도 함께 반영

---

## 시나리오 5 — due 카드 없음 → Empty (SRS-002 Empty)

> Mock 토글 필요: `FakeFixtures.emptyDeck`

1. Dashboard에서 SrsStudy 진입 시도
2. due Flashcard 0개 → Empty 상태 표시 (Empty)
3. Dashboard 복귀 CTA 또는 자연스러운 안내 노출

---

## 시나리오 6 — 평가 저장 실패 → Retry (SRS-005 Retry)

> Mock 토글 필요: `FakeFixtures.saveReviewFailure`

1. 시나리오 3 흐름 + 평가 버튼 클릭
2. 저장 실패 응답 수신 (Saving → Retry)
3. 화면은 현재 카드 그대로 유지, 다음 카드로 넘어가지 않음
4. 재시도 액션 노출 → 클릭 시 동일 입력으로 재저장, 성공 시 다음 카드 이동

---

## 시나리오 7 — deck 로드 실패 → Retry (SRS-002 Error)

> Mock 토글 필요: `FakeFixtures.deckLoadFailure`

1. SrsStudy 진입 → deck 조회 실패 (Loading → Error)
2. 재시도 액션 노출 (Error + Retry)
3. fixture를 정상으로 토글한 뒤 재시도 → 정상 deck 로드 (Success)

---

## 시나리오 8 — TTS 미지원/실패 (SRS-004 Edge)

> Real 환경: TTS 엔진이 설치되지 않은 디바이스 또는 현재 언어 미지원 상태로 재현. (실기기 준비가 어려우면 Mock에서 TTS 실패 fixture로 대체)

1. 카드 뒷면 표시 → speaker 버튼 탭
2. TTS 호출 실패 → 화면은 깨지지 않고 안전 처리 (Error)
3. 카드 학습/평가 흐름은 정상 진행 가능 (재생 실패가 학습 전체를 막지 않음)

---

## 시나리오 9 — 학습 중 이탈 + 재진입 (SRS-006 복원)

1. 시나리오 3 진행 중 (3번째 카드까지 평가 완료) 뒤로가기로 화면 이탈
2. Dashboard에서 다시 Flashcard 학습 카드 클릭
3. 진입 시 현재 선택 언어 기준 deck 다시 로드 → 이미 평가한 카드는 제외된 상태로 시작 (Success)
4. 직전 진행이 그대로 이어지는지 (또는 정책상 재정렬되어도 모순 없는지) 확인
5. 진입 중 중복 Navigation 발생하지 않음
