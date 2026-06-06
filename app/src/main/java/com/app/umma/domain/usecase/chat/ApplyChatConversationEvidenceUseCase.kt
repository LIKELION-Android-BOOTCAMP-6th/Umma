package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.SupportRequiredEvidence
import com.app.umma.domain.model.chat.UserContributionEvidence
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
        // 기준언어 보조 없이는 대화가 거의 이어지지 않았으면 최하위 band로 보호한다.
        if (
            evidence.conversationSustainability == ConversationSustainabilityEvidence.RequiresSupport ||
            evidence.supportRequiredToContinue == SupportRequiredEvidence.High ||
            evidence.responseDifficultyFit == ResponseDifficultyFitEvidence.TooHard
        ) {
            return ConversationAbilityBand.IntentOnly
        }

        // 사용자가 의미 있는 학습언어 기여를 거의 못 했다면 보조 강도와 무관하게 IntentOnly에 가깝다.
        if (evidence.userContributionLevel == UserContributionEvidence.Minimal) {
            return ConversationAbilityBand.IntentOnly
        }

        // 단어/조각 중심 참여는 자유 문장 근거가 아니므로 PhraseEmerging을 넘기지 않는다.
        if (evidence.userContributionLevel == UserContributionEvidence.WordsOrFragments) {
            return ConversationAbilityBand.PhraseEmerging
        }

        // 짧은 구 단계는 보조가 적고 쉬운 흐름이 유지될 때만 SimpleSentence 후보가 된다.
        if (evidence.userContributionLevel == UserContributionEvidence.ShortPhrases) {
            val canHandleSimpleFlow =
                evidence.supportRequiredToContinue.ordinal >= SupportRequiredEvidence.Low.ordinal &&
                    evidence.conversationSustainability.ordinal >= ConversationSustainabilityEvidence.SustainedSimple.ordinal &&
                    evidence.responseDifficultyFit != ResponseDifficultyFitEvidence.SlightlyHard
            return if (canHandleSimpleFlow) {
                ConversationAbilityBand.SimpleSentence
            } else {
                ConversationAbilityBand.PhraseEmerging
            }
        }

        // 짧은 자유 문장이 가능해도 자연 대화가 안정적이라는 근거가 없으면 SimpleSentence에 둔다.
        if (evidence.userContributionLevel == UserContributionEvidence.SimpleSentences) {
            val canSustainBasicConversation =
                evidence.supportRequiredToContinue == SupportRequiredEvidence.None &&
                    evidence.conversationSustainability == ConversationSustainabilityEvidence.SustainedNatural &&
                    evidence.responseDifficultyFit == ResponseDifficultyFitEvidence.Fits
            return if (canSustainBasicConversation) {
                ConversationAbilityBand.BasicConversation
            } else {
                ConversationAbilityBand.SimpleSentence
            }
        }

        // 연결 발화를 만들 수 있으면 최소 BasicConversation이며, 높은 confidence일 때만 상위 band로 올린다.
        val hasNaturalSustainability =
            evidence.conversationSustainability == ConversationSustainabilityEvidence.SustainedNatural
        val hasNoSupportNeed = evidence.supportRequiredToContinue == SupportRequiredEvidence.None
        if (hasNaturalSustainability && hasNoSupportNeed && evidence.confidence == ProfileConfidence.High) {
            return if (evidence.responseDifficultyFit == ResponseDifficultyFitEvidence.TooEasy) {
                ConversationAbilityBand.NuanceControl
            } else {
                ConversationAbilityBand.ConnectedExpression
            }
        }
        return ConversationAbilityBand.BasicConversation
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
