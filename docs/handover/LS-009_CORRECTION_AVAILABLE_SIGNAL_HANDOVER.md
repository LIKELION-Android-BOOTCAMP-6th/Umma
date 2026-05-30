# [LS-009 (가칭)] AI Chat turn 확정 → `SessionSummary.correctionAvailable` 시그널 전파 책임 인계

> 대상: `SYS-LEARNING-STATE-INFRA` 소유자(부팀장) 정원화
> 보낸 사람: `feature/correction` 브랜치 작업자 김태환
> 관련 이슈: 미신설 (본 문서에서 신규 이슈 제안). 인접: #138(`LS-008`), #107(`COR-006-A`), `CHAT-007`, `DASH-003`
> 발견 시점: 시나리오 4(`COR-001-B` 초기 Empty / 중복 방어) 진입 직전 실기기 회귀 점검 중

---

## 1. 배경 — 왜 이 문서가 필요한가

시나리오 0~3(`COR-000`~`COR-007-B`)을 모두 머지한 후, 시나리오 4(`COR-001-B`) 진입 직전에 실기기에서 다음 증상을 확인했다.

> **첫 사용자가 AI Chat에서 충분히 대화한 직후에도 Dashboard 교정 대기 카드 빨간 점이 켜지지 않고, Correction 화면 진입 시 Ready 판정이 떨어지지 않는다.**

이 증상은 fixture / fake 기반 회귀에서는 잡히지 않는다. fake 데이터가 `correctionAvailable = true`로 박혀 있어 happy path가 그대로 통과하기 때문이다.

코드 추적 결과, **AI Chat 종료(또는 turn 확정) 시점에 `SessionSummary.correctionAvailable`을 갱신하는 진입점이 main 코드 어디에도 존재하지 않는다**는 정합성 공백이 확인되었다. 화면 레이어가 임의로 LS 진입점을 호출하면 LS-006 batch update 정책을 위반하므로, **LS infra 소유자의 정책 결정 + 진입점 신설이 필요한 사안**으로 판단해 인계한다.

---

## 2. 증상 (재현/근거)

### 2.1. 재현 절차

1. 신규 사용자 또는 교정 이력이 한 번도 없는 사용자로 로그인.
2. Dashboard → AI Chat 진입.
3. `selectedLearningLanguage` 기준으로 PTT 여러 턴 대화 (USER final turn 1개 이상 누적되도록).
4. 뒤로가기로 Dashboard 복귀.

### 2.2. 기대 vs 실제

| 항목 | 기대 | 실제 |
| --- | --- | --- |
| Dashboard 교정 대기 카드 우상단 빨간 점 | 켜짐 | **꺼진 상태 유지** |
| Correction 화면 진입 시 `SessionSummary.correctionAvailable` | `true` | **`false`** |
| `COR-001-A` Ready 판정 | Ready → 자동 교정 생성 진입 | **Empty/NotAvailable 분기** |
| `COR-001-B` Empty UI | 정상 표시 | 정상 표시 (← 이건 의도된 동작) |

> 즉 `COR-001-B`의 Empty 분기는 의도대로 동작하지만, **happy path(`COR-001-A` Ready → `COR-002-A` 자동 생성)가 실기기에서 절대 트리거되지 않는** 상태다.

---

## 3. 원인 — 코드 흐름 추적 결과

### 3.1. `SessionSummary.correctionAvailable`을 갱신하는 유일한 지점

[`LearningStateRepoImpl.updateLanguageState`](../../app/src/main/java/com/app/umma/data/repository/LearningStateRepoImpl.kt) line 130~213.

```kotlin
val hasUserTurns =
    input.recentUserTurns.any { it.speaker == TurnSpeaker.USER }
val correctionAvailable = input.correctionAvailableOverride ?: hasUserTurns
// ...
sessionSummaries = current.sessionSummaries + (
        lang to updatedSession.copy(
            recentMinutes = measuredMinutes,
            correctionAvailable = correctionAvailable,
            updatedAt = input.analyzedAt
        )
),
```

### 3.2. 이 메서드를 호출하는 유일한 도메인 진입점

[`ApplyLanguageStateUpdateUseCase`](../../app/src/main/java/com/app/umma/domain/usecase/learningstate/LearningStateWriteUseCases.kt) line 28~53.

### 3.3. main 코드에서 `ApplyLanguageStateUpdateUseCase`를 호출하는 caller

`Grep("ApplyLanguageStateUpdateUseCase", glob="*.kt", path="app/src/main")` 결과 — **단 한 곳**:

- [`CompleteCorrectionUseCase`](../../app/src/main/java/com/app/umma/domain/usecase/correction/CompleteCorrectionUseCase.kt) (=교정 완료 시점)

### 3.4. 따라서 발생하는 chicken-and-egg

```text
신규 사용자
→ 교정 한 번도 안 함
→ ApplyLanguageStateUpdateUseCase 호출 0회
→ SessionSummary.correctionAvailable 영원히 false
→ 교정 진입 게이트 닫힘
→ 교정 평생 불가
```

### 3.5. `SessionMemory.meta.correctionAvailable`은 이미 켜짐 (별개 채널)

[`SessionMemoryLocalDataSource`](../../app/src/main/java/com/app/umma/data/source/local/SessionMemoryLocalDataSource.kt) line 30 주석:

> user turn 이 하나라도 존재하면 correctionAvailable = true

→ Session Memory 메타 테이블은 turn append 시점에 정확히 갱신되고 있다. **이 시그널이 `SessionSummary`로 전파되는 다리만 비어 있는 상태.**

---

## 4. 책임 판정 — 설계 문서 3종 교차 확인

### 4.1. `CHAT-007` (AI Chat) — 명시적 Out of Scope

[`CHAT-007_Turn_Commit.md`](../Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-007_Turn_Commit.md) line 39~45 "제외 범위(Out of Scope)":

> - Session Memory Repository 상세 구현
> - Room Entity / DAO 구현
> - Session Memory 압축
> - **LangState 업데이트**

→ AI Chat은 `final turn → Session Memory append` 까지만 책임지고, LangState / Summary 갱신은 명시적으로 배제됨. ChatViewModel이 LS 진입점을 호출하면 CHAT-007 OoS 라인을 깬다.

### 4.2. `LS-006` (LearningState) — 트리거 정의 권한 + MVP 누락 자인

[`LS-006_Language_State_Update_Policy.md`](../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md)가 "LangState / Summary를 언제·어떤 입력으로 갱신할지" 정의하는 단일 권위 문서다.

LS-006이 정의한 트리거:

| # | 트리거 | LS-006 위치 | MVP 구현 |
| --- | --- | --- | --- |
| ① | Correction 완료 직후 | line 133~146 | ✅ `CompleteCorrectionUseCase`에 연결됨 |
| ② | **AI Chat 종료 후 학습 분석** | line 150~162 | ❌ **"후속 단계로 둘 수 있다"로 보류** |
| ③ | Flashcard 복습 결과 반영 | line 166~178 | (별도) |

그리고 LS-006 line 81~90:

> Language State는 **대화 중 매 turn마다 업데이트하지 않는다** → 교정 또는 학습 분석 시점에 batch update

→ LS-006이 MVP 트리거를 ①로 의도적으로 좁히면서 **첫 Correction 진입 게이트(correctionAvailable=true)를 채우는 경로가 정책상 비어 있는 상태**다. 이 공백은 LS-006의 후속 결정 사항.

### 4.3. `DASH-003` (Dashboard) — 데이터 소비자일 뿐

[`DASH-003_Correction_Pending_Card.md`](../Sprint2/User_FlowDB/FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md) line 96~119는 `DashSummary[selectedLearningLanguage].correctionAvailable`을 **읽기**만 한다. 채우는 책임은 DashSummary 공급자(=LS infra)에 위임.

### 4.4. 결론

코드 차원 유일 갱신 지점 + 설계 문서 책임 분배 → **본 사안은 `SYS-LEARNING-STATE-INFRA` 소유자 결정 영역**이다.

---

## 5. 신규 이슈 제안 — `LS-009 (가칭)`

### 5.1. 이슈명

> **LS-009 AI Chat turn 확정 → `SessionSummary.correctionAvailable` 시그널 전파**

### 5.2. AC 초안

- [ ] AI Chat에서 USER final turn이 Session Memory에 append된 이후 LS infra가 `SessionSummary.correctionAvailable` 시그널을 갱신할 수 있는 진입점을 제공한다.
- [ ] 이 진입점은 LS-006의 batch update 원칙을 깨지 않는다. (=풀 Type A/B/C 분석을 돌리지 않고, `correctionAvailable` / `recentMinutes` 시그널만 갱신하는 lightweight 경로)
- [ ] 같은 turn 또는 같은 세션 스코프의 중복 호출은 idempotent하게 처리된다. (`analysisEventId` 또는 turn 단위 dedupe)
- [ ] 트리거 시점이 정책으로 명시된다. (옵션 5.3 참조)
- [ ] AI Chat / Realtime 측은 LS 진입점만 호출할 뿐 `LangStateUpdateInput`을 직접 조립하지 않는다. (LS-008 `BuildLangStateUpdateInputUseCase` 패턴 유지)
- [ ] 실기기 회귀: 신규 사용자가 PTT 1턴 이상 발화 후 Dashboard 복귀 시 교정 대기 카드 빨간 점이 켜지고, Correction 화면 진입 시 `COR-001-A` Ready 판정이 통과한다.

### 5.3. 트리거 시점 옵션 (정책 결정 필요)

#### 옵션 (a) — `AppendTurnUseCase` 후처리에서 LS infra 위임 호출 (권장)

- 동작: 매 USER final turn append 성공 직후 LS infra가 노출한 lightweight update를 호출.
- 장점: 사용자가 Chat을 명시적으로 종료하지 않고 Dashboard로 바로 이동해도 시그널이 살아 있음. 가장 견고함.
- 단점: 호출 빈도가 높음 → idempotent 처리 + `recentMinutes`만 재계산하는 가벼운 경로가 필수.

#### 옵션 (b) — Chat 종료(`CHAT-008` cleanup) 시점에 1회 호출

- 동작: AI Chat 화면 이탈 시 누적된 turn 기준으로 단 1회 시그널 update.
- 장점: 호출 빈도 최소.
- 단점: 사용자가 강제 종료 / OS kill / 백그라운드 격리로 cleanup을 못 거치면 시그널 미스. 신뢰성 떨어짐.

> 권장: **(a)**. LS-008의 `BuildLangStateUpdateInputUseCase` Command에 이미 `correctionAvailableOverride` 필드가 있어 lightweight 경로 표현이 가능하다.

### 5.4. 핵심 정책 결정 포인트

LS 소유자가 결정해 주셔야 할 항목:

1. **lightweight update의 범위**: `correctionAvailable` + `recentMinutes`만 갱신할지, `LangState.internal`까지 부분 갱신할지.
2. **caller 위치**: `AppendTurnUseCase` 내부에 LS 의존성을 주입할지, 별도 facade/observer 패턴으로 뺄지. (현재 `SessionMemoryRepositoryImpl`은 LS 의존성이 0건이라 도메인 경계 결정 필요)
3. **idempotency 키**: turn 단위(`turnId`)인지 세션 단위(`sessionMemoryKey`)인지.
4. **LS-006 문서 업데이트**: 이 lightweight 경로가 LS-006 트리거 ①/②와 별개로 추가되는 ④인지, 트리거 ②의 MVP 구현인지.

---

## 6. Correction 측이 한 일 / 안 한 일

### 6.1. 한 일

- 시나리오 0~3 (`COR-000`~`COR-007-B`) 머지 완료.
- 실기기 회귀에서 본 chicken-and-egg 발견.
- 코드 추적으로 갱신 경로 단절 지점 특정.
- 설계 문서 3종 교차로 책임자 식별.
- 본 인계 문서 작성.

### 6.2. 안 한 일 (의도적 보류)

- `ChatViewModel` / `AppendTurnUseCase` / `SessionMemoryRepositoryImpl` 어느 쪽에도 LS 진입점 호출을 추가하지 않았다. (CHAT-007 OoS 라인 + LS-006 batch update 원칙을 화면 레이어가 임의 위반하면 안 됨)
- `LearningStateRepoImpl.updateLanguageState` 시그니처 변경 없음.
- `BuildLangStateUpdateInputUseCase` (LS-008) 호출부 추가 없음.

→ **이번 인계는 코드 변경 0줄**. 정책 결정 후 LS infra 소유자가 진입점을 신설하면 Correction 측은 추가 작업 없이 happy path가 자동 복원된다.

---

## 7. 영향 받는 백로그

| 백로그 | 영향 |
| --- | --- |
| **시나리오 4 (`COR-001-B`/`COR-002-B`/`COR-003-B`/`COR-006-B`)** | fixture 기반 분기 검증은 영향 없음 → 본 인계와 **병렬 진행 가능**. 단, 시연/QA 시점 전에 LS-009가 머지되어야 실기기 happy path 검증 가능. |
| **`COR-001-A` Ready 판정 happy path** | LS-009 머지 전까지 **실기기에서 절대 통과 불가**. |
| **`COR-002-A` 자동 교정 생성** | Ready 게이트 통과가 전제이므로 같이 막힘. |
| **`DASH-003` 빨간 점 시연** | 실기기 시연 시 LS-009 머지 필요. |
| **LS-006 문서** | 트리거 정의에 lightweight 경로 추가 또는 트리거 ② MVP 명세 보강 필요. |

---

## 8. 참고 링크

- 본 인계 코드 추적 기반: [`LearningStateRepoImpl.kt:130-213`](../../app/src/main/java/com/app/umma/data/repository/LearningStateRepoImpl.kt), [`LearningStateWriteUseCases.kt:28-53`](../../app/src/main/java/com/app/umma/domain/usecase/learningstate/LearningStateWriteUseCases.kt), [`SessionMemoryLocalDataSource.kt:30`](../../app/src/main/java/com/app/umma/data/source/local/SessionMemoryLocalDataSource.kt)
- 책임 판정 근거 문서: [`CHAT-007`](../Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-007_Turn_Commit.md), [`LS-006`](../System_FlowDB/SYS_LEARNING_STATE_INFRA/LS-006_Language_State_Update_Policy.md), [`DASH-003`](../Sprint2/User_FlowDB/FLOW_DASHBOARD/DASH-003_Correction_Pending_Card.md)
- Correction 측 진입점: [`COR-001_Initial_State.md`](../Sprint2/User_FlowDB/FLOW_CORRECTION/COR-001_Initial_State.md)
- 선행 LS 인계: [`LS-008_LANGSTATE_INPUT_READY.md`](LS-008_LANGSTATE_INPUT_READY.md) — `BuildLangStateUpdateInputUseCase` 계약. 본 이슈에서 lightweight 경로 진입점을 만들 때 재사용 가능.
- "충돌 지점을 먼저 정리하여 정합성을 맞춘 뒤 작업 진행" 원칙에 따라 본 인계는 **코드 변경 없이 문서로만** 마무리. 정책 결정 후 회신 부탁드립니다.
