# Demo Scenario — FLOW-CORRECTION

> 2차 스프린트 종료 시점에 시연 가능해야 할 흐름. 사용자 여정 관점으로 묶어 애자일 스프린트 작업 배정 단위로도 사용.
> 이슈 단위(COR-001~007) AC 체크리스트는 `docs/User_FlowDB/FLOW_CORRECTION/` 의 이슈 문서를 참조한다.
>
> 각 시나리오 = 한 명(또는 한 페어)이 스프린트 안에 완결할 수 있는 유저 가치 한 덩어리.
> 시나리오는 의존성 순서로 배열되어 있어 위에서 아래로 차곡차곡 쌓아 올릴 수 있다.
>
> **사전 준비 사항 (스프린트 작업 항목)**
> - Real 계정: AI Chat 에서 final turn 1개 이상 저장 완료된 상태 (`SessionSummary.correctionAvailable == true`)
> - Mock 토글: `CorrectionRepository` fake 구현 + `CorrectionSuggestionFixtureBuilder` (Content / Empty / Error 픽스처) — 현재 미존재, DASH 스타일 `RepositoryModule` 토글 추가 필요
> - Mock fixture 가 필요한 분기: 시나리오 4(견고화) 의 모든 분기, 시나리오 1·2·3 의 실패 분기. happy path 는 Real 로 시연
> - 발표 후 Real 바인딩으로 원복

---

## 시나리오 1 — AI 대화 직후 교정 결과 한 번 받기

> **무엇을 하는가** — AI Chat 종료 직후 Correction 화면에 들어가면 자동으로 AI 교정이 돌아 결과 카드 목록이 표시된다.
> **유저 가치** — Umma 의 핵심 차별점("내 발화 → 더 자연스러운 표현")을 사용자가 처음 체감하는 단위. 이게 동작해야 뒤의 저장 / 반복학습 흐름이 의미를 가진다.

**포함 이슈**
- `COR-001` — Correction 초기 상태 로드 (selectedLearningLanguage / SessionSummary / LangState 확인, `correctionAvailable` 판정)
- `COR-002` — 교정 결과 생성 (SYS 후보 추출 계약 + 실제 AI API 호출 + 응답 파싱 + `CorrectionSuggestion` 변환)
- `COR-003` — 교정 결과 카드 표시 (교정 전 / 교정 후 / 짧은 설명)

**의존성**: FLOW-AI-CHAT 시나리오 3 (확정 turn 저장)이 먼저 동작해야 한다. Session Memory 에 final turn 이 있어야 후보 추출이 가능하다.
**예상 규모**: L — 실제 AI 교정 API 호출 + 응답 파싱 + 모델 변환 + 화면 렌더링까지 한 사이클로 묶이는 단위.

**데모 흐름**
1. AI Chat 에서 user / assistant 확정 턴 1~2회 누적 후 화면 종료 (또는 Dashboard 경유)
2. Dashboard 의 "교정 대기" 카드(우측 상단 빨간 점)를 클릭 → Correction 화면 진입 (Loading)
3. `SessionSummary.correctionAvailable == true` 확인 → 사용자 추가 입력 없이 결과 생성 자동 시작 (Loading)
4. 실제 AI 응답 파싱 완료 → `CorrectionSuggestion` 카드 1개 이상 표시 (Content)
5. 각 카드에 교정 전 문장 / 교정 후 문장 / 짧은 설명이 명확히 구분되어 보이는지 확인

**핵심 분기**
- **성공**: Content 상태로 카드가 1개 이상 표시된다.
- **결과 없음 (`COR-002` Empty)**: AI 응답이 빈 목록이면 카드 화면으로 넘어가지 않고 Empty 상태를 보여준다.
- **AI 실패 / 파싱 실패 (`COR-002` Error)**: Error 상태 + Retry. 재시도 시 같은 Session Memory + 같은 선택 언어 기준으로 다시 요청한다.

---

## 시나리오 2 — 마음에 드는 교정을 골라서 Flashcard 로 저장

> **무엇을 하는가** — 카드 목록에서 기억하고 싶은 교정을 골라 선택한 뒤 저장 버튼을 누르면, 새 Flashcard 가 local first 로 저장된다.
> **유저 가치** — 교정 결과가 일회성으로 끝나지 않고 반복학습 흐름으로 이어지는 다리. 사용자가 "내 발화 기반 학습 자산"을 처음 쌓는 순간.

**포함 이슈**
- `COR-004` — 저장 카드 선택 상태 (선택 / 해제 / 0개일 때 저장 버튼 비활성)
- `COR-005` — Flashcard 저장 요청 모델 변환 (현재 선택 언어 + 교정 전/후 문장 + 모국어 앞면 + 설명)
- `COR-006` — 교정 완료 결과 연결 (`CompleteCorrectionUseCase` 호출 + 새 Flashcard local first 저장 + Done / Retry 상태 전환)

**의존성**: 시나리오 1
**예상 규모**: M — UI 선택 상태 관리 + 저장 요청 모델 변환 + 완료 파이프라인 호출 결과 화면 반영.

**데모 흐름**
1. 시나리오 1 의 카드 목록 상태에서 카드 1개 탭 → 선택 강조 표시, 저장 버튼 활성화
2. 같은 카드를 다시 탭 → 선택 해제, 저장 버튼이 다시 비활성화 (선택 0개 가드)
3. 카드 2개를 다시 선택 → 저장 버튼 클릭 (Preparing → Completing)
4. `CompleteCorrectionUseCase` 가 새 Flashcard 를 local first 로 저장 → Done 상태
5. 저장 버튼을 빠르게 연타해도 in-flight 중복 차단되는지 확인 (`duplicate blocked` 로그)

**핵심 분기**
- **성공**: Done 상태 진입, local Flashcard 저장 완료.
- **저장 실패 (`COR-006` Retry)**: Retry 상태로 남고, 선택 상태와 카드 목록은 유지된다. 재시도 시 동일 입력으로 완료 파이프라인을 다시 호출한다.
- **Firestore sync 만 실패 (`COR-006` PendingSync)**: 로컬 완료는 성공으로 유지된다. 사용자에게 실패로 노출하지 않고, pending 상태는 내부 로그로만 다룬다.

---

## 시나리오 3 — 저장 후 Dashboard 복귀 + 상태 갱신

> **무엇을 하는가** — 완료 직후 사용자가 자연스럽게 Dashboard 로 돌아오고, 학습 카드 due 수치 / 교정 대기 신호 등 Summary 가 최신 상태로 보인다.
> **유저 가치** — "한 사이클이 닫혔다"는 인지를 만들어 준다. Dashboard 가 다시 출발점이 되어 다음 학습 흐름(SRS / Statistics)으로 연결된다.

**포함 이슈**
- `COR-007` — Dashboard 복귀 + completion success event 1회 소비 + Firestore sync pending 비차단

**의존성**: 시나리오 2
**예상 규모**: S — Dashboard 복귀 자체는 작은 작업. Summary 갱신은 Dashboard Flow 의 책임이라 관찰만 한다.

**데모 흐름**
1. 시나리오 2 Done 직후 Dashboard 로 자동 복귀
2. Flashcard 학습 카드 due 수치가 저장한 카드 수만큼 증가했는지 확인
3. 교정 대기 카드의 빨간 점이 사라지거나 갱신되는지 확인
4. Compose recomposition / 재진입 상황에서도 completion 이벤트가 중복으로 소비되지 않는지 확인

**핵심 분기**
- **성공**: Dashboard 복귀 + Summary 즉시 갱신 + 이벤트 1회만 소비.
- **pending sync (`COR-007`)**: Firestore sync 가 아직 끝나지 않았어도 사용자에게 완료로 보인다. pending 상태는 디버그 영역에서만 확인된다.
- **navigation 실패 (`COR-007` Edge)**: 완료 결과를 잃지 않고 Retry 또는 Dashboard 재이동이 가능하다.

---

## 시나리오 4 — 교정할 게 없거나 실패한 상황도 자연스럽게 (견고화 패스)

> **무엇을 하는가** — 교정 불가 / 결과 없음 / AI 실패 / 저장 실패 / sync 실패 같은 다양한 분기에서 명확한 안내와 다음 행동(Retry / AI Chat 이동 / 보호된 완료)을 제공한다.
> **유저 가치** — happy path 만 있는 데모를 넘어 실제 사용 시 자주 발생하는 "비어 있음 / 실패함" 상황에서도 사용자가 길을 잃지 않게 한다.

**포함 이슈**
- `COR-001` Empty — `SessionSummary.correctionAvailable == false` 일 때 Empty + AI Chat 이동 CTA
- `COR-002` Empty / Error / Retry — 결과 없음 / AI 실패 / 파싱 실패 분기 모두
- `COR-006` Retry — 로컬 완료 실패 시 Retry 보호
- `COR-007` PendingSync — Firestore sync 실패만 발생했을 때 사용자에게 차단되지 않음

**의존성**: 시나리오 1~3 의 happy path 가 먼저 깔려 있어야 의미를 가진다.
**예상 규모**: S~M — 이슈별로 분산 작업 가능. fixture 와 토글이 같이 준비되면 한 명이 분기 하나씩 잡기 좋다.

**데모 흐름**
1. `SessionSummary.correctionAvailable == false` 상태로 진입 → Empty + AI Chat 이동 CTA 노출
2. AI 응답은 정상이나 `CorrectionSuggestion` 0개로 반환되는 상황 → 카드 화면으로 넘어가지 않고 Empty 상태
3. AI 호출 실패 / 파싱 실패 / 필수 필드 누락 → Error + Retry. fixture 를 정상으로 토글 후 재시도 시 정상 결과로 복구
4. 저장 실패 → Retry 상태로 남고 선택 / 카드 목록 유지. 재시도 성공 시 Dashboard 복귀
5. Firestore sync 만 실패 → 사용자에겐 Done 으로 보이고 Dashboard 복귀 정상 동작. pending 은 내부 로그에서만 확인

**핵심 분기**
- **1차 합격선**: 모든 Empty / Error / Pending 경로가 크래시 없이 동작한다.
- **2차 합격선**: 각 분기에서 사용자가 다음에 무엇을 해야 하는지(Retry / AI Chat 이동 / 닫기)가 화면에서 명확히 보인다.
