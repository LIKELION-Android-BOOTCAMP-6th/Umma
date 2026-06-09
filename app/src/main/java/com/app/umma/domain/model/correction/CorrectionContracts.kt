package com.app.umma.domain.model.correction

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile

/**
 * 후보 추출 결과와 LangState snapshot 을 함께 넘겨 교정 결과를 생성하기 위한 입력 모델입니다.
 *
 * 언어 기준:
 *  - [primaryLang]: 사용자가 학습 기준으로 삼는 언어. 앞면(nativeText)과 설명(explanation)을 이 언어로 생성한다.
 *  - selectedLang ([langState].lang): 학습 대상 언어. 교정 후 문장(afterText)과 데이터 소속의 기준이 된다.
 */
data class GenerateSuggestionsInput(
    // 내부 후보 목록.
    val candidates: List<CorrectionCandidate>,
    // 현재 선택 언어의 LangState snapshot. langState.lang 이 selectedLang(교정 대상 언어)의 단일 출처다.
    val langState: LangState,
    // 사용자의 학습 기준 언어. 앞면 문장(nativeText)과 교정 설명(explanation)을 이 언어로 생성한다.
    // selectedLang 과 같을 수 있으며, 그 경우 앞면과 교정문이 동일 언어가 된다.
    val primaryLang: LangCode,
    // 이미 해석이 끝난 교정 적응 정책 read model (COR-TUNE-01).
    // BuildLearnerAdaptationProfileUseCase 가 raw LangState metric 을 정책으로 변환한 결과이며,
    // CorrectionPromptBuilder 는 이 profile 의 correctionPolicy/focus 만 행동 지시로 쓰고 raw metric 은 해석하지 않는다.
    val profile: LearnerAdaptationProfile,
    // COR-TUNE-010: 의도 파악용 세션 맥락. 후보 1문장 + assistantContext 한마디보다 넓은 범위로
    // "이 학습자가 무엇을 말하려 했는지"를 AI가 먼저 추론하도록 돕는다.
    // 비어 있으면(SessionMemory 조회 실패 등) CorrectionPromptBuilder 가 맥락 블록을 생략하고
    // 기존 candidate 기반 교정으로 폴백한다 — 완료 흐름을 막지 않는다.
    val sessionContext: CorrectionSessionContext = CorrectionSessionContext()
)

/**
 * Correction 결과가 비었을 때 화면이 안내 문구를 구분할 수 있게 하는 사유 모델이다.
 */
enum class CorrectionEmptyResultReason {
    // 안전 후보는 있었지만 교정 결과가 0건인 일반 빈 결과.
    NO_CORRECTION_NEEDED,
    // 후보 단계 안전 필터가 모든 후보를 차단한 결과.
    SAFETY_BLOCKED
}

/**
 * 교정 의도 파악을 돕는 세션 맥락 read model 입니다 (COR-TUNE-010).
 *
 * 출처:
 *  - [recentTopics]/[topicSummaries]/[topicKeySentences]: 이전 세션에서 이미 압축·저장된
 *    [com.app.umma.domain.model.realtime.SessionMemory] 필드를 `getSessionMemory(lang)` 로 그대로 읽어온다
 *    (새 저장소 메서드 신설 금지 — AC 준수).
 *  - [currentSessionTurns]: 같은 트리거에서 이미 확보한 RT-003 `getCorrectionContext(lang)` 결과를 재사용한다.
 *
 * 모든 필드는 빈 목록이 기본값이다. SessionMemory 조회가 실패하거나 비어 있으면 빈 맥락으로 폴백하고
 * 교정은 현재 세션 turn 기반으로 계속 진행한다(예외 처리 정책).
 */
data class CorrectionSessionContext(
    // 이전 세션 주제 키워드/라벨. AI 매핑 결과(B 트랙)이며, 코드 단어빈도가 아니다.
    val recentTopics: List<String> = emptyList(),
    // 주제별 압축 요약("무엇을 교정했는지"). AI 결과가 SSOT다.
    val topicSummaries: List<String> = emptyList(),
    // 이후 학습에 재사용 가능한 핵심 정답 문장.
    val topicKeySentences: List<String> = emptyList(),
    // 현재 세션의 대화 흐름. candidate 1문장 + assistantContext 한마디보다 넓은 범위를 제공해
    // AI 가 의도를 먼저 파악한 뒤 band 에 맞게 교정하도록 돕는다.
    val currentSessionTurns: List<CorrectionContextTurn> = emptyList()
)

/**
 * 의도 파악 맥락에 실리는 한 turn 의 최소 표현입니다.
 *
 * @property speaker "user" 또는 "assistant" 같은 화자 라벨. 내부 enum 이름을 그대로 노출하지 않고
 *                   프롬프트에 자연스럽게 들어갈 수 있는 문자열로만 전달한다.
 * @property text 발화 원문.
 */
data class CorrectionContextTurn(
    val speaker: String,
    val text: String
)

/**
 * 선택된 교정 결과를 Flashcard 저장 계약으로 넘기기 위한 입력 모델입니다.
 */
data class CorrectionSaveRequest(
    // 저장 대상 사용자. Room local 저장은 사용자별로 분리되어야 계정 전환 시 카드가 섞이지 않는다.
    val uid: String,
    // 저장 대상 언어.
    val lang: LangCode,
    // 선택된 교정 결과에서 파생된 실제 Flashcard 저장 항목.
    val flashcards: List<CorrectionFlashcardSaveItem>,
    // 요청 시각.
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * 저장 요청 조립 단계가 품질 필터와 안전 차단 결과를 함께 보존하는 결과 모델이다.
 */
data class PrepareCorrectionSaveRequestResult(
    val request: CorrectionSaveRequest,
    val saveableSuggestionIds: List<String>,
    val qualityFilteredSuggestionIds: List<String>,
    val safetyBlockedSuggestionIds: List<String>,
    val zeroReason: CorrectionSaveZeroReason? = null
)

/**
 * 저장 가능한 카드가 0개가 된 사유.
 */
enum class CorrectionSaveZeroReason {
    QUALITY_FILTERED,
    SAFETY_BLOCKED
}

/**
 * Correction 결과를 Flashcard 저장소에 넘길 때 사용하는 카드 단위 계약입니다.
 */
data class CorrectionFlashcardSaveItem(
    // 원본 CorrectionSuggestion 식별자. 저장 후 중복 방지와 추적에 사용한다.
    val suggestionId: String,
    // Flashcard 앞면: 사용자의 모국어 문장.
    val frontText: String,
    // Flashcard 뒷면: 교정된 외국어 문장.
    val backText: String,
    // 뒷면에 함께 표시할 짧은 교정 설명.
    val explanation: String
)

/**
 * Correction Flashcard 저장 결과를 나타내는 최소 결과 모델입니다.
 */
data class CorrectionSaveResult(
    // 로컬 저장에 반영된 카드 ID.
    val localSavedFlashcardIds: List<String>,
    // 원격 pending sync 로 남은 카드 ID.
    val pendingSyncFlashcardIds: List<String>,
    // 저장 반영 시각.
    val savedAt: Long
)
