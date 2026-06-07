package com.app.umma.domain.model.correction

import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Correction 화면에서 직접 보여주고 선택할 수 있는 교정 결과 카드 모델입니다.
 *
 * 이 모델은 화면 표시와 Flashcard 저장 선택의 공통 계약으로 사용된다.
 * Flashcard 저장 시에는 nativeText가 앞면, afterText와 explanation이 뒷면의 원본이 된다.
 */
data class CorrectionSuggestion(
    // 카드 추적용 고유 식별자.
    val id: String,
    // 현재 선택된 학습 언어.
    val lang: LangCode,
    // 이 제안의 근거가 된 후보 ID 목록.
    val sourceCandidateIds: List<String>,
    // 원본 user turn 순서.
    val sourceTurnIndex: Int,
    // 교정 전 원문.
    val beforeText: String,
    // Flashcard 앞면에 표시할 primaryLang 기준 문장. 필드명은 호환용으로 유지.
    val nativeText: String,
    // 교정 후 문장.
    val afterText: String,
    // 간단한 교정 설명.
    val explanation: String,
    // 교정 과정에서 관찰된 학습 신호(COR-TUNE-02). LearningState 갱신 입력으로 흐른다.
    // 신호 파싱 실패/누락은 suggestion 생성을 막지 않으므로 nullable 기본값으로 둔다.
    val learningSignal: CorrectionLearningSignal? = null,
    // COR-TUNE-011: 발화 원문 언어(candidate.sourceLang 을 그대로 옮긴 운반용 필드).
    // 이 모델 자체는 판정을 내리지 않는다 — CompleteCorrectionUseCase.buildCorrectionResult 의
    // 단일 평가 게이트가 이 값과 selectedLang 을 비교해 learningSignal 의 평가 반영 여부만 결정한다.
    val sourceLang: LangCode? = null
)
