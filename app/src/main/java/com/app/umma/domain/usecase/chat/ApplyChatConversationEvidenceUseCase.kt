package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SkillStage
import javax.inject.Inject

/**
 * Chat conversation evidence를 세션 시작용 profile에 반영한다.
 *
 * Firestore/Gemini가 band를 직접 결정하지 않도록, 저장된 evidence만 보고
 * domain policy가 [ConversationAbilityBand]를 계산한다. Correction policy와 core profile은
 * 기존 LangState 해석 결과를 유지해 1차 작업이 Correction/Statistics/SRS로 번지지 않게 한다.
 */
class ApplyChatConversationEvidenceUseCase @Inject constructor() {

    operator fun invoke(
        baseProfile: LearnerAdaptationProfile,
        evidence: ChatConversationEvidence?
    ): ChatConversationEvidenceApplication {
        // evidence가 없거나 만료/낮은 confidence면 기존 LangState 기반 profile을 그대로 사용한다.
        if (evidence == null || !evidence.isUsable()) {
            return ChatConversationEvidenceApplication(
                profile = baseProfile,
                applied = false,
                calculatedBand = baseProfile.chatPolicy.conversationBand,
                source = evidence?.source?.name
            )
        }

        // debugRecommendedBand는 분석 품질 확인용일 뿐, 실제 band 계산에는 쓰지 않는다.
        val calculatedBand = chooseBand(evidence)
        val evidenceStage = minimumStageForBand(calculatedBand)
        val profileWithEvidence = baseProfile.copy(
            // 이 profile은 Chat 세션 시작용 read model이다. LangState를 수정하지 않으면서도
            // prompt와 speech speed가 같은 evidence confidence를 보게 한다.
            core = baseProfile.core.copy(
                levelConfidence = evidence.confidence,
                grammarStage = maxStage(baseProfile.core.grammarStage, evidenceStage),
                vocabularyStage = maxStage(baseProfile.core.vocabularyStage, evidenceStage),
                fluencyStage = maxStage(baseProfile.core.fluencyStage, evidenceStage),
                naturalnessStage = maxStage(baseProfile.core.naturalnessStage, evidenceStage)
            ),
            chatPolicy = ChatAdaptationPolicy.defaultsForBand(calculatedBand)
        )
        return ChatConversationEvidenceApplication(
            profile = profileWithEvidence,
            applied = true,
            calculatedBand = calculatedBand,
            source = evidence.source.name
        )
    }

    private fun chooseBand(evidence: ChatConversationEvidence): ConversationAbilityBand {
        // 학습언어 이해/생산 근거가 없거나 강한 의존이 있으면 전체 대화가 자연스러워도 최하위로 보호한다.
        if (requiresIntentOnly(evidence)) {
            return ConversationAbilityBand.IntentOnly
        }

        // 단어 이해/생산 중심이면 자유 문장 대화로 보지 않고 PhraseEmerging에 둔다.
        if (
            evidence.targetLanguageComprehension == TargetLanguageComprehensionEvidence.WordLevel ||
            evidence.targetLanguageProduction == TargetLanguageProductionEvidence.WordsOrFragments
        ) {
            return ConversationAbilityBand.PhraseEmerging
        }

        // 짧은 구는 이해/지속/일관성 근거가 받쳐줄 때만 SimpleSentence 후보가 된다.
        if (evidence.targetLanguageProduction == TargetLanguageProductionEvidence.ShortPhrases) {
            val canHandleSimpleFlow = evidence.targetLanguageComprehension >= TargetLanguageComprehensionEvidence.SimpleSentence &&
                evidence.supportLanguageDependence.atMost(LanguageDependenceEvidence.Medium) &&
                evidence.aiScaffoldingDependence.atMost(LanguageDependenceEvidence.Medium) &&
                evidence.conversationSustainability >= ConversationSustainabilityEvidence.SustainedSimple &&
                evidence.consistency != ConversationConsistencyEvidence.Low
            return if (canHandleSimpleFlow) {
                ConversationAbilityBand.SimpleSentence
            } else {
                ConversationAbilityBand.PhraseEmerging
            }
        }

        // 단순 문장은 보조/AI 리드 의존이 낮고 세션 전체가 안정적일 때만 기본 대화 band로 올린다.
        if (evidence.targetLanguageProduction == TargetLanguageProductionEvidence.SimpleSentences) {
            val canSustainBasicConversation = evidence.targetLanguageComprehension >= TargetLanguageComprehensionEvidence.SimpleSentence &&
                evidence.supportLanguageDependence == LanguageDependenceEvidence.None &&
                evidence.aiScaffoldingDependence.atMost(LanguageDependenceEvidence.Low) &&
                evidence.conversationSustainability == ConversationSustainabilityEvidence.SustainedNatural &&
                evidence.consistency == ConversationConsistencyEvidence.Stable &&
                evidence.responseDifficultyFit == ResponseDifficultyFitEvidence.Fits
            return if (canSustainBasicConversation) {
                ConversationAbilityBand.BasicConversation
            } else {
                ConversationAbilityBand.SimpleSentence
            }
        }

        // 연결 발화가 가능해도 기준언어/AI 리드 의존이나 일관성 문제가 있으면 BasicConversation 상한을 둔다.
        val canUseConnectedBand = evidence.targetLanguageComprehension == TargetLanguageComprehensionEvidence.NaturalFlow &&
            evidence.supportLanguageDependence == LanguageDependenceEvidence.None &&
            evidence.aiScaffoldingDependence.atMost(LanguageDependenceEvidence.Low) &&
            evidence.conversationSustainability == ConversationSustainabilityEvidence.SustainedNatural &&
            evidence.consistency == ConversationConsistencyEvidence.Stable &&
            evidence.confidence == ProfileConfidence.High
        if (!canUseConnectedBand) {
            return ConversationAbilityBand.BasicConversation
        }

        // NuanceControl은 "AI가 쉬웠다"만으로 올리지 않고, 의존이 전혀 없는 natural flow일 때만 허용한다.
        if (
            evidence.aiScaffoldingDependence == LanguageDependenceEvidence.None &&
            evidence.responseDifficultyFit == ResponseDifficultyFitEvidence.TooEasy
        ) {
            return ConversationAbilityBand.NuanceControl
        }
        return ConversationAbilityBand.ConnectedExpression
    }

    private fun requiresIntentOnly(evidence: ChatConversationEvidence): Boolean {
        return evidence.targetLanguageComprehension == TargetLanguageComprehensionEvidence.None ||
            evidence.targetLanguageProduction == TargetLanguageProductionEvidence.None ||
            evidence.supportLanguageDependence == LanguageDependenceEvidence.High ||
            evidence.aiScaffoldingDependence == LanguageDependenceEvidence.High ||
            evidence.conversationSustainability == ConversationSustainabilityEvidence.RequiresSupport ||
            evidence.responseDifficultyFit == ResponseDifficultyFitEvidence.TooHard
    }

    private fun LanguageDependenceEvidence.atMost(max: LanguageDependenceEvidence): Boolean {
        // enum 순서는 High -> Medium -> Low -> None 이므로 ordinal이 클수록 의존이 낮다.
        return ordinal >= max.ordinal
    }

    private fun minimumStageForBand(band: ConversationAbilityBand): SkillStage {
        // Chat evidence가 세션 시작 profile을 대체할 때 audio speed가 초기 LangState의 Foundation에 묶이지 않도록
        // band별 최소 처리 단위를 보수적으로 맞춘다. 이 값은 저장되지 않는다.
        return when (band) {
            ConversationAbilityBand.IntentOnly,
            ConversationAbilityBand.PhraseEmerging -> SkillStage.Foundation
            ConversationAbilityBand.SimpleSentence -> SkillStage.Developing
            ConversationAbilityBand.BasicConversation -> SkillStage.Stable
            ConversationAbilityBand.ConnectedExpression -> SkillStage.Expanding
            ConversationAbilityBand.NuanceControl -> SkillStage.Refined
        }
    }

    private fun maxStage(
        current: SkillStage,
        evidenceMinimum: SkillStage
    ): SkillStage {
        // 기존 LangState가 더 높은 근거를 가진 경우에는 낮추지 않고, evidence가 더 강한 경우에만 세션용 profile을 올린다.
        return if (current.ordinal >= evidenceMinimum.ordinal) current else evidenceMinimum
    }
}

/**
 * evidence 적용 결과와 trace에 남길 최소 정보를 함께 반환한다.
 */
data class ChatConversationEvidenceApplication(
    // 실제 세션 시작에 사용할 profile.
    val profile: LearnerAdaptationProfile,
    // evidence가 band 계산에 반영됐는지.
    val applied: Boolean,
    // 최종 Chat band. applied=false면 기존 LangState 기반 band다.
    val calculatedBand: ConversationAbilityBand,
    // review/debug용 evidence 출처.
    val source: String?
)
