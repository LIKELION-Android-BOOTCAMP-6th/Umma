package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.selectedLang
import com.app.umma.domain.usecase.learningstate.EnsureLearningStateLoadedUseCase
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * SRS 화면 진입 -> "현재 학습 언어" 가져오는 UseCase
 *
 * 화면이 열리는 순간 언어를 고정
 * 그 이후 카드 목록을 고정된 언어로 불러올 수 있음
 */
class StartReviewSessionUseCase @Inject constructor(
    private val observeLearningState: ObserveLearningStateUseCase,
    private val ensureLearningStateLoadedUseCase: EnsureLearningStateLoadedUseCase
) {
    // 성공 -> Result.success(LangCode) or Result.success(null)
    // 실패 -> Result.failure() -> 다시시도 표시 필요
    suspend operator fun invoke(): Result<LangCode?> {
        return try {
            // local preload -> 로컬이 비었으면 remote restore까지 한 번에 보장
            // (앱 데이터 삭제/콜드스타트 후에도 selectedLang을 Firestore에서 복구)
            ensureLearningStateLoadedUseCase().getOrThrow()
            // Flow에서 현재 시점 스냅샷 딱 한번 읽어 selectedLang 값 가져옴
            val lang = observeLearningState().firstOrNull()?.selectedLang
            Result.success(lang)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}