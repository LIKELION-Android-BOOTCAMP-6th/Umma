package com.app.umma.domain.repository

import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Local persistence contract for unsaved Correction suggestions.
 *
 * The repository only stores and retrieves raw cache payloads. Fingerprint matching
 * remains a domain use case concern so staleness policy stays outside the data layer.
 */
interface CorrectionSuggestionCacheRepository {
    suspend fun getCachedCorrection(uid: String, language: LangCode): CachedCorrectionResult?

    suspend fun saveCachedCorrection(uid: String, cache: CachedCorrectionResult)

    suspend fun clearCachedCorrection(uid: String, language: LangCode)

    /**
     * 회원탈퇴 시 저장 전 교정 제안 캐시를 모두 비운다.
     *
     * 이 캐시는 uid별 교정 후보 원문을 담으므로 계정 삭제 뒤 복원되면 안 된다.
     */
    suspend fun clearLocal(): Result<Unit> = Result.success(Unit)
}
