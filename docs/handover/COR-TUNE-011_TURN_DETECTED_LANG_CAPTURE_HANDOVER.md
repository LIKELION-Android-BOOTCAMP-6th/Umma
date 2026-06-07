# [Handover] 발화 언어 태깅(detectedLang) 캡처 요청 — CHAT/Realtime 소관

## 목적

교정(Correction) 쪽에서 "다른 언어로 말해도 의도는 포착·교정하되, 능력 평가는 학습 대상 언어(selectedLang)에 한해서만 한다"(`COR-TUNE-011`)를 구현하려면, **각 발화 turn이 어떤 언어로 말해졌는지**를 알아야 한다.

그러나 발화를 확정·저장하는 단계는 교정 범위 밖(CHAT/Realtime-infra, RT-003)이다. 이 문서는 교정이 필요로 하는 **발화별 언어 태그(`detectedLang`)** 추가를 CHAT/Realtime 담당자에게 인계한다.

이 문서는 교정·평가 로직을 정의하지 않는다. 그건 `COR-TUNE-011`이 담당한다. 여기서는 "교정이 평가 게이트를 걸 수 있도록, turn에 언어 정보를 실어 달라"는 입력 계약만 요청한다.

---

# 배경 — 왜 필요한가

현재 발화 turn 모델에는 언어 정보가 전혀 없다.

- `SessionTurn`([app/src/main/java/com/app/umma/domain/model/realtime/SessionTurn.kt](../../app/src/main/java/com/app/umma/domain/model/realtime/SessionTurn.kt)): `turnId, sessionId, text, role, createdAt, durationMs, tokenCount, confidence` — 언어 필드 없음.
- `ConversationTurn`([app/src/main/java/com/app/umma/domain/model/learningstate/LearningUpdateModels.kt](../../app/src/main/java/com/app/umma/domain/model/learningstate/LearningUpdateModels.kt)): `speaker, text, tokenCount, durationMs, confidence` — 언어 필드 없음.

그 결과, 학습자가 학습 대상 언어(예: 영어)가 아닌 모국어(한국어)나 제3언어(예: 프랑스어)로 말한 발화도 교정 후보가 되고, 교정 후 생성되는 학습 신호(learningSignal)가 **학습 대상 언어 능력 근거로 잘못 집계**된다. 즉 한국어로 말한 발화가 영어 문법 evidence로 카운트되어 LangState/band 산출을 오염시킨다.

평가의 "학습 언어만" 게이트는 **feature 레벨에는 이미 구현**돼 있으나(`CorrectionAiResponseMapper.normalizeLanguageFeature`, `LangStateAnalysisPolicy` 재필터), **발화 원문 언어 기준 게이트는 불가능**하다 — turn에 언어 정보가 없기 때문이다.

---

# 요청 사항 (CHAT/Realtime)

## 1. `SessionTurn`에 `detectedLang` 추가

```kotlin
data class SessionTurn(
    val turnId: String,
    val sessionId: String,
    val text: String,
    val role: TurnSpeaker,
    val createdAt: Long,
    val durationMs: Long? = null,
    val tokenCount: Int? = null,
    val confidence: Double? = null,
    val detectedLang: LangCode? = null // ← 추가 요청. 감지 실패 시 null 허용
)
```

- nullable 로 두어 감지 실패/미지원 시에도 기존 흐름이 깨지지 않게 한다.
- 기본값 `null` 이면 교정 쪽은 "언어 불명 → 종전과 동일하게 통과"로 처리하므로 점진 도입이 안전하다.

## 2. Room 엔터티 컬럼 추가

- `SessionMemoryDatabase`([app/src/main/java/com/app/umma/data/source/local/SessionMemoryDatabase.kt](../../app/src/main/java/com/app/umma/data/source/local/SessionMemoryDatabase.kt))의 turn 엔터티에 `detectedLang` 컬럼 추가 + 마이그레이션.
- nullable 컬럼이라 기존 row 는 null 로 채워지면 된다(파괴적 마이그레이션 불필요).

## 3. 언어 감지 출처 (권장 우선순위)

1. **STT/Realtime 엔진 메타데이터 우선** — OpenAI Realtime transport(CHAT-ENGINE-001)가 발화별 언어 정보를 제공하면 그대로 사용.
2. **없으면 문자체계 휴리스틱** — MVP 지원 언어는 문자체계가 서로 달라 코드로 충분히 판별 가능:
   - 한글(U+AC00~U+D7A3, U+1100~) → KO
   - 가나(U+3040~U+30FF) / 한자(U+4E00~) → JA
   - 라틴 위주 → EN/DE (둘 구분이 필요하면 보조 사전, MVP는 selectedLang 우선 추정 허용)
   - AI 호출 없이 가능 → 비용 0, 지연 0.

---

# 비범위 (이 인계의 책임 밖)

- 교정 후보 추출/교정 생성 로직 — 교정(COR) 담당.
- 발화 원문 언어 기준 **평가 게이트** 로직 — `COR-TUNE-011`(COR)에서 구현.
- `detectedLang` 을 어떻게 활용해 평가에서 제외할지의 정책 — `COR-TUNE-011`.

이 인계는 **"turn에 언어 정보를 실어 달라"**까지만 요청한다.

---

# 의존 관계

```
[본 인계] SessionTurn.detectedLang 캡처  ──선행──>  COR-TUNE-011 (발화 원문 언어 평가 게이트)
```

- `detectedLang` 이 들어오기 전까지 `COR-TUNE-011`의 평가 게이트는 "언어 불명 → 종전처럼 통과"로 동작한다(no-op fallback). 즉 본 인계가 머지되면 그때부터 게이트가 실제로 작동한다.
- 본 인계 없이도 교정 자체(의도 포착·학습언어 교정)는 동작한다. 영향받는 건 "평가 정확도"뿐이다.

---

# 검증 기준 (인계 완료 확인)

- `SessionTurn`/turn 엔터티에 `detectedLang` 추가 + 마이그레이션 통과.
- 한글/가나/라틴 발화 각각이 KO/JA/EN 으로 태깅되는지(또는 STT 메타데이터가 채워지는지) 확인.
- 기존 turn(언어 미상)은 `null`로 남고, append/조회/압축 흐름이 회귀 없이 동작하는지 확인.
- `:app:compileDevDebugKotlin` / `:app:compileMockDebugKotlin` 빌드 통과.

---

# 관련 문서

- `docs/Sprint3/User_FlowDB/FLOW_COR_IMPROVEMENTS/COR-TUNE-011_Multilingual_Correction_Evaluation_Gating.md` (이 인계를 소비하는 교정 평가 게이트)
- `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-003_Turn_Commit.md` (turn 확정/저장 — 캡처 단계 소유)
