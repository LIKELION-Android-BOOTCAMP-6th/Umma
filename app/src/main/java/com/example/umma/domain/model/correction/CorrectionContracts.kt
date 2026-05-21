package com.example.umma.domain.model.correction

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState

/**
 * 후보 추출 결과와 LangState snapshot 을 함께 넘겨 교정 결과를 생성하기 위한 입력 모델입니다.
 */
data class GenerateSuggestionsInput(
    // 내부 후보 목록.
    val candidates: List<CorrectionCandidate>,
    // 현재 선택 언어의 LangState snapshot.
    val langState: LangState
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
