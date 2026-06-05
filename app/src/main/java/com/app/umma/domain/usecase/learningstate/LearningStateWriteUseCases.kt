package com.app.umma.domain.usecase.learningstate

import com.app.umma.core.logging.LearningSignalFlowLog
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.LearningStateRepo
import javax.inject.Inject

/**
 * 학습 상태를 바꾸는 UseCase 묶음.
 */
class ChangeSelectedLangUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // 선택 언어만 바꾸고 나머지 상태는 유지한다.
    suspend operator fun invoke(lang: LangCode): Result<Unit> = repo.changeSelectedLang(lang)
}

class ApplyLanguageStateUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo,
    // 계산 책임은 repository가 아니라 domain policy에만 둔다.
    // 이렇게 해야 저장 경로와 점수 계산 경로를 분리할 수 있다.
    private val analysisPolicy: LangStateAnalysisPolicy
) {
    suspend operator fun invoke(input: LangStateUpdateInput): Result<LearningStateUpdateResult> {
        // 같은 analysisEventId를 다시 받으면 이동평균을 한 번 더 적용하지 않는다.
        // 이 early return 덕분에 Correction 완료 재시도나 화면 재진입이 점수를 왜곡하지 않는다.
        if (!input.forceReanalysis &&
            input.analysisEventId != null &&
            input.currentState.lastAnalysisEventId == input.analysisEventId
        ) {
            LearningSignalFlowLog.d(
                "update_skipped_duplicate lang=${input.lang.code} eventId=${input.analysisEventId} " +
                    "signals=${input.correctionResult?.learningSignals?.size ?: 0}"
            )
            // 중복 이벤트는 계산도 저장도 하지 않고 현재 상태를 그대로 돌려준다.
            return Result.success(
                LearningStateUpdateResult(
                    lang = input.lang,
                    savedState = input.currentState,
                    sourceEventId = input.analysisEventId,
                    applied = false,
                    updatedAt = input.currentState.updatedAt ?: input.analyzedAt
                )
            )
        }

        LearningSignalFlowLog.d(
            "update_start lang=${input.lang.code} eventId=${input.analysisEventId ?: "none"} " +
                "turns=${input.recentUserTurns.size} corrections=${input.correctionResult?.correctionCount ?: 0} " +
                "signals=${input.correctionResult?.learningSignals?.size ?: 0} " +
                "prepared=${input.preparedState != null}"
        )
        // UseCase는 중복 방어와 저장 흐름만 조율하고, 실제 LangState 계산은 policy에 위임한다.
        // caller가 preparedState를 이미 넘긴 경우에는 기존 계약대로 그 값을 그대로 저장소에 전달한다.
        // preparedState가 없을 때만 policy를 호출해야 불필요한 재계산과 테스트 흔들림을 막을 수 있다.
        val preparedState = input.preparedState ?: analysisPolicy.analyze(input)
        LearningSignalFlowLog.d(
            "update_prepared lang=${input.lang.code} eventId=${input.analysisEventId ?: "none"} " +
                "evidence=${preparedState.analysisMeta.metricEvidence.size} " +
                "focus=${preparedState.analysisMeta.activeFocus.size} " +
                "lastSignalAt=${preparedState.analysisMeta.lastSignalAt ?: "none"}"
        )
        // 저장소는 계산을 모르고, 받아온 preparedState를 snapshot으로만 저장한다.
        return repo.updateLanguageState(input.copy(preparedState = preparedState))
    }
}

class ApplyFlashcardSummaryUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(
        input: FlashcardSummaryUpdateInput
    ): Result<FlashcardSummaryUpdateResult> {
        // due count 계산은 SRS가 끝낸 상태로 넘어온다.
        // LS는 음수처럼 화면을 깨는 값만 막고, 전역 Summary 반영을 담당한다.
        // 저장 전에 음수를 차단해야 repository까지 잘못된 상태가 내려가지 않는다.
        if (input.dueFlashcards < 0 || input.savedFlashcards < 0) {
            return Result.failure(
                IllegalArgumentException("flashcard summary counts must not be negative")
            )
        }
        // 검증을 통과한 입력만 repository로 넘긴다.
        return repo.updateFlashcardSummary(input)
    }
}

class ApplyCorrectionSignalUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    /**
     * Chat final turn 이후 correctionAvailable 만 빠르게 갱신한다.
     *
     * full LangState 분석 batch 를 다시 돌리지 않고, 세션/대시보드 요약만
     * 같은 의미로 맞춰두는 경계다.
     *
     * 이 UseCase는 "신호를 받을 자격이 있는 입력인가"만 먼저 확인하고,
     * 실제 summary 반영은 repository 계약으로 넘긴다.
     */
    suspend operator fun invoke(
        input: CorrectionSignalUpdateInput
    ): Result<CorrectionSignalUpdateResult> {
        // uid / scope / event key 가 비어 있으면 idempotent 판단 자체가 불가능하다.
        // 이 값들은 correctionAvailable를 어디까지 반영할지 판단하는 최소 경계값이다.
        if (input.uid.isBlank()) {
            return Result.failure(IllegalArgumentException("uid must not be blank"))
        }
        if (input.sessionMemoryKey.isBlank()) {
            return Result.failure(IllegalArgumentException("sessionMemoryKey must not be blank"))
        }
        if (input.sourceEventId.isBlank()) {
            return Result.failure(IllegalArgumentException("sourceEventId must not be blank"))
        }
        if (input.recentMinutes != null && input.recentMinutes < 0) {
            return Result.failure(IllegalArgumentException("recentMinutes must not be negative"))
        }

        // 검증이 끝난 신호만 repository에 전달한다.
        return repo.updateCorrectionSignal(input)
    }
}

class InitLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(
        userUid: String,
        userPref: UserLangPref,
        langState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit> {
        // 신규 사용자 최초 진입 시 기본 스냅샷을 만든다.
        // 초기 스냅샷은 이후 sync, chat, statistics가 공유하는 기준점이 된다.
        return repo.createInitial(
            userUid = userUid,
            userPref = userPref,
            langState = langState,
            dashSummary = dashSummary,
            sessionSummary = sessionSummary,
            flashcardSummary = flashcardSummary
        )
    }
}

class ClearLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // 로그아웃 시 전역 상태를 비운다.
    // local cache와 in-memory snapshot이 남아 있으면 다음 사용자에게 상태가 섞일 수 있다.
    suspend operator fun invoke(): Result<Unit> = repo.clear()
}
