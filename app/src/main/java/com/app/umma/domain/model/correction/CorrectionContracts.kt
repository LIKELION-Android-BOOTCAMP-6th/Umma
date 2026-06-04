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
    val profile: LearnerAdaptationProfile
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
