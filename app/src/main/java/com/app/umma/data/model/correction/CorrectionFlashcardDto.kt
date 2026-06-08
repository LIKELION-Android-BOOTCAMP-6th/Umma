package com.app.umma.data.model.correction

import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.learningstate.LangCode
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Correction에서 최초 생성한 Flashcard를 저장 계층으로 넘기기 위한 DTO입니다.
 *
 * Domain의 [CorrectionFlashcardSaveItem]은 화면/UseCase가 공유하는 저장 요청 모델이고,
 * 이 DTO는 Firestore 문서와 local cache가 함께 이해할 수 있는 저장 구조를 담당한다.
 */
data class CorrectionFlashcardDto(
    val id: String,
    val language: String,
    val sourceSuggestionId: String,
    val frontText: String,
    val backText: String,
    val explanation: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val nextReviewAt: Long,
    val interval: Int,
    val easeFactor: Double,
    val dirty: Boolean,
    val lastReviewRating: String?,
    val lastReviewedAt: Long?
) {
    /**
     * Firestore 저장 필드는 DTO의 camelCase 이름을 그대로 사용한다.
     *
     * `language`는 LS-001에서 정한 표준 언어 필드명이고,
     * `interval` / `easeFactor` / `nextReviewAt`은 SRS가 바로 읽을 수 있는 초기 스케줄 값이다.
     * interval은 날짜 단위로 고정하지 않고, SRS 정책이 정하는 간격 값으로 해석한다.
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "language" to language,
            "sourceSuggestionId" to sourceSuggestionId,
            "frontText" to frontText,
            "backText" to backText,
            "explanation" to explanation,
            "source" to source,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "nextReviewAt" to nextReviewAt,
            "interval" to interval,
            "easeFactor" to easeFactor,
            "dirty" to dirty,
            "lastReviewRating" to lastReviewRating,
            "lastReviewedAt" to lastReviewedAt
        )
    }


}

/**
 * Correction 저장 요청을 SRS가 읽을 수 있는 Flashcard 원본 구조로 변환한다.
 */
fun CorrectionFlashcardSaveItem.toCorrectionFlashcardDto(
    lang: LangCode,
    requestedAt: Long
): CorrectionFlashcardDto {
    // suggestionId를 문서 id로 고정하면 같은 교정 결과의 중복 저장을 같은 카드로 합칠 수 있다.
    return CorrectionFlashcardDto(
        id = suggestionId,
        // Firestore 표준 필드값은 enum name(EN)이 아니라 LS-001 언어 코드(en)를 사용한다.
        language = lang.code,
        // 현재는 id와 같지만, 카드가 어떤 교정 결과에서 만들어졌는지 추적하기 위해 별도 필드로 남긴다.
        sourceSuggestionId = suggestionId,
        frontText = frontText.trim(),
        backText = backText.trim(),
        explanation = explanation.trim(),
        source = "correction",
        createdAt = requestedAt,
        updatedAt = requestedAt,
        // 새 카드 저장 직후에는 SRS에서 즉시 due card로 잡을 수 있도록 현재 시각을 기본값으로 둔다.
        nextReviewAt = requestedAt,
        interval = 0,
        easeFactor = 2.5,
        // Firestore sync가 끝나기 전까지는 local 원본이 dirty 상태다.
        dirty = true,
        lastReviewRating = null,
        lastReviewedAt = null
    )
}

/**
 * Firestore 문서를 local 원복 복원용 DTO로 되돌린다
 *
 * 재설치 등으로 Room이 비었을 때 [CorrectionFlashcardDto.toFirestoreMap]으로 저장한 문서를
 * 다시 읽어 들이는 역방향 매핑
 * id/language가 없는 손상 문서는 null로 걸러냄
 */
fun DocumentSnapshot.toCorrectionFlashcardDto(): CorrectionFlashcardDto? {
    val id = getString("id") ?: return null
    val language = getString("language") ?: return null
    return CorrectionFlashcardDto(
        id = id,
        language = language,
        sourceSuggestionId = getString("sourceSuggestionId") ?: id,
        frontText = getString("frontText") ?: "",
        backText = getString("backText") ?: "",
        explanation = getString("explanation") ?: "",
        source = getString("source") ?: "correction",
        createdAt = getLong("createdAt") ?: 0L,
        updatedAt = getLong("updatedAt") ?: 0L,
        nextReviewAt = getLong("nextReviewAt") ?: 0L,
        interval = getLong("interval")?.toInt() ?: 0,
        easeFactor = getDouble("easeFactor") ?: 2.5,
        // 원격 문서는 sync 완료 상태이므로 복원된 카드는 dirty가 아니다.
        dirty = getBoolean("dirty") ?: false,
        lastReviewRating = getString("lastReviewRating"),
        lastReviewedAt = getLong("lastReviewedAt")
    )
}

