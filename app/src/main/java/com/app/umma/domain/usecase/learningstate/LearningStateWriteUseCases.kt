package com.app.umma.domain.usecase.learningstate

import com.app.umma.core.logging.ChatPromptTraceLog
import com.app.umma.core.logging.LearningSignalFlowLog
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.ChatSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateSummaryUpdatePolicy
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.firstOrNull
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

class ChangePrimaryLangUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(lang: LangCode): Result<Unit> = repo.changePrimaryLang(lang)
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

/**
 * Chat 대화 분석 결과를 LangState에 연결하는 얇은 adapter.
 *
 * Correction처럼 점수 계산을 직접 실행하지 않고, Chat evidence를 "근거 방향"으로만 누적한다.
 * 이렇게 해야 Chat 실험 과정에서 잘못 측정된 한 세션이 내부 숙련도 점수를 즉시 왜곡하지 않는다.
 */
class ApplyChatSignalUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    /**
     * Chat 세션 분석 evidence를 공식 LangState의 analysisMeta 근거로만 누적한다.
     *
     * Chat source는 대화 지속 능력 근거이지 문법/통계 점수 source가 아니므로,
     * InternalMetrics와 ExternalMetrics는 그대로 두고 source가 분리된 MetricEvidence만 갱신한다.
     */
    suspend operator fun invoke(input: ChatSignalUpdateInput): Result<LearningStateUpdateResult> {
        val validationFailure = validateInput(input)
        if (validationFailure != null) return Result.failure(validationFailure)

        // Chat source는 correction analysis event와 별도 idempotency key를 사용한다.
        // 같은 sourceSessionId가 다시 들어와도 evidence observedCount가 중복 증가하지 않게 한다.
        val analysisEventId = buildChatAnalysisEventId(input.sourceSessionId)
        repo.preload()
        val currentState = repo.observeLangState(input.lang).firstOrNull()
            ?: LangState.initial(
                lang = input.lang,
                createdAt = input.analyzedAt,
                updatedAt = input.analyzedAt
            )

        if (!input.forceReanalysis &&
            currentState.analysisMeta.lastChatAnalysisEventId == analysisEventId
        ) {
            ChatPromptTraceLog.d(
                "chat_ability langstate_skipped lang=${input.lang.code} " +
                        "session=${input.sourceSessionId} reason=duplicate"
            )
            return Result.success(
                LearningStateUpdateResult(
                    lang = input.lang,
                    savedState = currentState,
                    sourceEventId = analysisEventId,
                    applied = false,
                    updatedAt = currentState.updatedAt ?: input.analyzedAt
                )
            )
        }

        // Gemini가 만든 label을 LangState가 이해하는 metric 방향으로 낮춰서 저장한다.
        // 이 단계는 "점수 반영"이 아니라 "나중에 profile 계산이 참고할 관찰 근거"를 만드는 단계다.
        val metricUpdates = input.evidence.toMetricDirectionUpdates()
        val nextEvidence =
            metricUpdates.fold(currentState.analysisMeta.metricEvidence) { evidence, update ->
                evidence + (update.key to MetricEvidenceMergePolicy.merge(
                    previous = evidence[update.key],
                    direction = update.direction,
                    confidence = input.evidence.toMetricConfidence(),
                    source = LearningSignalSource.ChatSession,
                    observedAt = input.analyzedAt
                ))
            }
        val nextChatEvidenceSummary = input.evidence.toUpdatedChatEvidenceSummary(
            previous = currentState.analysisMeta.chatEvidenceSummary,
            observedAt = input.analyzedAt
        )
        val nextMeta = currentState.analysisMeta.copy(
            metricEvidence = nextEvidence,
            // summary나 metric이 실제로 반영된 경우에만 signal 시각을 옮겨 Low confidence 분석을 과대 해석하지 않는다.
            lastSignalAt = if (metricUpdates.isNotEmpty() || nextChatEvidenceSummary != currentState.analysisMeta.chatEvidenceSummary) {
                input.analyzedAt
            } else {
                currentState.analysisMeta.lastSignalAt
            },
            lastChatAnalysisEventId = analysisEventId,
            chatEvidenceSummary = nextChatEvidenceSummary
        )
        val preparedState = currentState.copy(
            internal = currentState.internal,
            external = currentState.external,
            analysisMeta = nextMeta,
            updatedAt = input.analyzedAt,
            lastAnalyzedAt = input.analyzedAt,
            // Chat source 중복 방어는 analysisMeta.lastChatAnalysisEventId가 전담한다.
            // 공용 lastAnalysisEventId를 chat-session 값으로 덮으면 Correction 재시도 중복 방어가 약해질 수 있다.
            lastAnalysisEventId = currentState.lastAnalysisEventId
        )

        ChatPromptTraceLog.d(
            "chat_ability langstate_prepared lang=${input.lang.code} " +
                    "session=${input.sourceSessionId} metrics=${metricUpdates.size} " +
                    "summary=${nextChatEvidenceSummary != currentState.analysisMeta.chatEvidenceSummary} " +
                    "confidence=${input.evidence.confidence}"
        )

        val updateResult = repo.updateLanguageState(
            LangStateUpdateInput(
                uid = input.uid,
                lang = input.lang,
                sessionMemoryKey = input.sessionMemoryKey,
                analysisEventId = analysisEventId,
                currentState = currentState,
                preparedState = preparedState,
                recentUserTurns = input.recentUserTurns,
                correctionResult = null,
                correctionAvailableOverride = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = input.analyzedAt,
                // Chat evidence는 공식 LangState 근거만 누적하고,
                // recentMinutes/correctionAvailable 같은 Summary는 기존 값을 보존한다.
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.PreserveExisting,
                forceReanalysis = input.forceReanalysis
            )
        )
        updateResult
            .onSuccess { result ->
                ChatPromptTraceLog.i(
                    "chat_ability langstate_saved lang=${input.lang.code} " +
                            "session=${input.sourceSessionId} applied=${result.applied} " +
                            "metrics=${metricUpdates.size} " +
                            "summary=${nextChatEvidenceSummary != currentState.analysisMeta.chatEvidenceSummary}"
                )
            }
            .onFailure { error ->
                ChatPromptTraceLog.w(
                    "chat_ability langstate_failed lang=${input.lang.code} " +
                            "session=${input.sourceSessionId} reason=${error::class.simpleName}",
                    error
                )
            }
        return updateResult
    }

    private fun validateInput(input: ChatSignalUpdateInput): IllegalArgumentException? {
        // LangState에 들어가는 Chat evidence는 사용자, 언어, 세션 범위가 모두 특정되어야 한다.
        // 범위가 모호한 입력은 debug snapshot에는 남을 수 있어도 공식 상태에는 올리지 않는다.
        return when {
            input.uid.isBlank() -> IllegalArgumentException("uid must not be blank")
            input.sessionMemoryKey.isBlank() -> IllegalArgumentException("sessionMemoryKey must not be blank")
            input.sourceSessionId.isBlank() -> IllegalArgumentException("sourceSessionId must not be blank")
            input.evidence.selectedLang != input.lang -> IllegalArgumentException(
                "chat evidence language mismatch: evidence=${input.evidence.selectedLang}, input=${input.lang}"
            )

            input.recentUserTurns.isEmpty() -> IllegalArgumentException("recentUserTurns must not be empty")
            else -> null
        }
    }

    private fun ChatConversationEvidence.toMetricDirectionUpdates(): List<MetricDirectionUpdate> {
        // Low confidence와 강한 보조 의존 evidence는 공식 LangState 근거로 누적하지 않는다.
        if (!isEligibleForLangStateEvidence()) return emptyList()

        val rawUpdates = buildList {
            when (targetLanguageProduction) {
                TargetLanguageProductionEvidence.ConnectedTurns,
                TargetLanguageProductionEvidence.SimpleSentences -> {
                    // 사용자가 학습언어로 문장을 직접 만들어 이어간 경우에만 길이/복잡도 근거를 올린다.
                    add(
                        MetricDirectionUpdate(
                            LearningMetricKey.AvgUtteranceLength,
                            EvidenceDirection.Up
                        )
                    )
                    add(
                        MetricDirectionUpdate(
                            LearningMetricKey.SentenceComplexity,
                            EvidenceDirection.Up
                        )
                    )
                }

                TargetLanguageProductionEvidence.ShortPhrases -> {
                    // 짧은 구 단위 발화는 성장 신호라기보다 현재 수준 유지 근거에 가깝다.
                    add(
                        MetricDirectionUpdate(
                            LearningMetricKey.AvgUtteranceLength,
                            EvidenceDirection.Stable
                        )
                    )
                }

                TargetLanguageProductionEvidence.WordsOrFragments,
                TargetLanguageProductionEvidence.None -> Unit
            }

            when (conversationSustainability) {
                ConversationSustainabilityEvidence.SustainedNatural,
                ConversationSustainabilityEvidence.SustainedSimple -> {
                    // 대화가 AI 보조 없이 이어진 경우는 발화 길이 metric의 긍정 근거로만 반영한다.
                    add(
                        MetricDirectionUpdate(
                            LearningMetricKey.AvgUtteranceLength,
                            EvidenceDirection.Up
                        )
                    )
                }

                ConversationSustainabilityEvidence.SupportedShort -> {
                    // 보조가 있는 짧은 지속은 과평가를 막기 위해 stable로 제한한다.
                    add(
                        MetricDirectionUpdate(
                            LearningMetricKey.AvgUtteranceLength,
                            EvidenceDirection.Stable
                        )
                    )
                }

                ConversationSustainabilityEvidence.RequiresSupport -> Unit
            }

            if (targetLanguageComprehension == TargetLanguageComprehensionEvidence.NaturalFlow ||
                targetLanguageComprehension == TargetLanguageComprehensionEvidence.SimpleSentence
            ) {
                // 이해 근거는 사용자가 직접 말한 문장보다 약한 신호라 SentenceComplexity 근거만 보조한다.
                add(
                    MetricDirectionUpdate(
                        LearningMetricKey.SentenceComplexity,
                        EvidenceDirection.Up
                    )
                )
            }

            if (responseDifficultyFit == ResponseDifficultyFitEvidence.Fits &&
                conversationSustainability != ConversationSustainabilityEvidence.SupportedShort
            ) {
                // 난이도가 맞고 대화가 스스로 유지된 경우에만 자연스러움 근거로 인정한다.
                add(
                    MetricDirectionUpdate(
                        LearningMetricKey.SpokenNaturalness,
                        EvidenceDirection.Up
                    )
                )
            }
        }

        // 같은 Chat evidence 안에서 같은 metric이 두 번 관측돼도 observedCount는 한 번만 증가해야 한다.
        return rawUpdates
            .groupBy { update -> update.key }
            .map { (key, updates) ->
                MetricDirectionUpdate(
                    key = key,
                    direction = mergeDirectionsWithinChatEvidence(updates.map { update -> update.direction })
                )
            }
    }

    private fun ChatConversationEvidence.toUpdatedChatEvidenceSummary(
        previous: ChatEvidenceSummary?,
        observedAt: Long
    ): ChatEvidenceSummary {
        // Low confidence는 "능력이 낮다"가 아니라 "이번 분석을 강하게 믿기 어렵다"는 뜻이다.
        // 하지만 첫 세션이나 기존 Low 상태에서는 이 단서를 버리면 초저숙련 사용자의 공식 Chat 상태가 계속 비게 된다.
        if (confidence == ProfileConfidence.Low && previous?.confidence != null && previous.confidence != ProfileConfidence.Low) {
            // 이미 Medium/High summary가 있으면 짧거나 애매한 Low 세션 하나로 기존 근거를 덮지 않는다.
            return previous
        }

        // summary 본문은 최신 유효 세션으로 교체한다.
        // 여러 세션을 평균내면 초저숙련/중급 테스트 전환 시 원인을 추적하기 어려워지므로 count만 누적한다.
        return ChatEvidenceSummary(
            targetLanguageComprehension = targetLanguageComprehension,
            targetLanguageProduction = targetLanguageProduction,
            supportLanguageDependence = supportLanguageDependence,
            aiScaffoldingDependence = aiScaffoldingDependence,
            conversationSustainability = conversationSustainability,
            consistency = consistency,
            responseDifficultyFit = responseDifficultyFit,
            confidence = confidence,
            observedCount = (previous?.observedCount ?: 0) + 1,
            lastObservedAt = observedAt
        )
    }

    private fun ChatConversationEvidence.isEligibleForLangStateEvidence(): Boolean {
        // LangState는 누적 상태이므로 보조 의존/난이도 과다/낮은 신뢰도 세션을 보수적으로 제외한다.
        // 이런 세션은 debug snapshot에는 남지만 공식 profile 근거로는 쓰지 않는다.
        return confidence != ProfileConfidence.Low &&
                supportLanguageDependence != LanguageDependenceEvidence.High &&
                aiScaffoldingDependence != LanguageDependenceEvidence.High &&
                consistency != ConversationConsistencyEvidence.Low &&
                responseDifficultyFit != ResponseDifficultyFitEvidence.TooHard &&
                targetLanguageProduction != TargetLanguageProductionEvidence.None &&
                targetLanguageComprehension != TargetLanguageComprehensionEvidence.None
    }

    private fun ChatConversationEvidence.toMetricConfidence(): Double {
        // Chat evidence는 Correction보다 간접 근거라 High도 1.0으로 두지 않는다.
        // 이후 merge policy가 source별 관찰을 완만하게 누적하도록 confidence를 낮춰 전달한다.
        return when (confidence) {
            ProfileConfidence.Low -> 0.0
            ProfileConfidence.Medium -> 0.55
            ProfileConfidence.High -> 0.75
        }
    }

    private fun mergeDirectionsWithinChatEvidence(
        directions: List<EvidenceDirection>
    ): EvidenceDirection {
        val meaningfulDirections =
            directions.filter { direction -> direction != EvidenceDirection.Stable }.distinct()
        return when {
            meaningfulDirections.isEmpty() -> EvidenceDirection.Stable
            meaningfulDirections.size == 1 -> meaningfulDirections.first()
            else -> EvidenceDirection.Mixed
        }
    }

    private fun buildChatAnalysisEventId(sourceSessionId: String): String {
        return "chat-session:${sourceSessionId.trim()}"
    }

    private data class MetricDirectionUpdate(
        val key: LearningMetricKey,
        val direction: EvidenceDirection
    )
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
