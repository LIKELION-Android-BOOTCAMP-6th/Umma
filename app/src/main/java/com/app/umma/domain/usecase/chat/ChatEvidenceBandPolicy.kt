package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.ProfileConfidence

/**
 * Chat evidence summary를 ConversationAbilityBand로 해석하는 단일 정책.
 *
 * Firestore snapshot과 LangState summary가 서로 다른 band를 만들지 않도록,
 * 대화 지속 능력 판단은 이 policy를 거치게 한다.
 */
object ChatEvidenceBandPolicy {
    fun chooseBand(summary: ChatEvidenceSummary): ConversationAbilityBand {
        // 학습언어 이해/생산 근거가 없거나 강한 의존이 있으면 전체 대화가 자연스러워도 최하위로 보호한다.
        if (requiresIntentOnly(summary)) {
            return ConversationAbilityBand.IntentOnly
        }

        // Low confidence summary는 첫 세션/초저숙련 단서를 보존하기 위해 저장하지만,
        // 분석 신뢰도가 낮으므로 다음 세션의 난이도를 강하게 올리는 근거로 쓰지 않는다.
        if (summary.confidence == ProfileConfidence.Low) {
            return ConversationAbilityBand.PhraseEmerging
        }

        // 단어 이해/생산 중심이면 자유 문장 대화로 보지 않고 PhraseEmerging에 둔다.
        if (
            summary.targetLanguageComprehension == TargetLanguageComprehensionEvidence.WordLevel ||
            summary.targetLanguageProduction == TargetLanguageProductionEvidence.WordsOrFragments
        ) {
            return ConversationAbilityBand.PhraseEmerging
        }

        // 짧은 구는 이해/지속/일관성 근거가 받쳐줄 때만 SimpleSentence 후보가 된다.
        if (summary.targetLanguageProduction == TargetLanguageProductionEvidence.ShortPhrases) {
            val canHandleSimpleFlow =
                summary.targetLanguageComprehension >= TargetLanguageComprehensionEvidence.SimpleSentence &&
                    summary.supportLanguageDependence.atMost(LanguageDependenceEvidence.Medium) &&
                    summary.aiScaffoldingDependence.atMost(LanguageDependenceEvidence.Medium) &&
                    summary.conversationSustainability >= ConversationSustainabilityEvidence.SustainedSimple &&
                    summary.consistency != ConversationConsistencyEvidence.Low
            return if (canHandleSimpleFlow) {
                ConversationAbilityBand.SimpleSentence
            } else {
                ConversationAbilityBand.PhraseEmerging
            }
        }

        // 단순 문장은 보조/AI 리드 의존이 낮고 세션 전체가 안정적일 때만 기본 대화 band로 올린다.
        if (summary.targetLanguageProduction == TargetLanguageProductionEvidence.SimpleSentences) {
            val canSustainBasicConversation =
                summary.targetLanguageComprehension >= TargetLanguageComprehensionEvidence.SimpleSentence &&
                    summary.supportLanguageDependence == LanguageDependenceEvidence.None &&
                    summary.aiScaffoldingDependence.atMost(LanguageDependenceEvidence.Low) &&
                    summary.conversationSustainability == ConversationSustainabilityEvidence.SustainedNatural &&
                    summary.consistency == ConversationConsistencyEvidence.Stable &&
                    summary.responseDifficultyFit == ResponseDifficultyFitEvidence.Fits
            return if (canSustainBasicConversation) {
                ConversationAbilityBand.BasicConversation
            } else {
                ConversationAbilityBand.SimpleSentence
            }
        }

        // 연결 발화가 가능해도 기준언어/AI 리드 의존이나 일관성 문제가 있으면 BasicConversation 상한을 둔다.
        val canUseConnectedBand =
            summary.targetLanguageComprehension == TargetLanguageComprehensionEvidence.NaturalFlow &&
                summary.supportLanguageDependence == LanguageDependenceEvidence.None &&
                summary.aiScaffoldingDependence.atMost(LanguageDependenceEvidence.Low) &&
                summary.conversationSustainability == ConversationSustainabilityEvidence.SustainedNatural &&
                summary.consistency == ConversationConsistencyEvidence.Stable &&
                summary.confidence == ProfileConfidence.High
        if (!canUseConnectedBand) {
            return ConversationAbilityBand.BasicConversation
        }

        // NuanceControl은 "AI가 쉬웠다"만으로 올리지 않고, 의존이 전혀 없는 natural flow일 때만 허용한다.
        if (
            summary.aiScaffoldingDependence == LanguageDependenceEvidence.None &&
            summary.responseDifficultyFit == ResponseDifficultyFitEvidence.TooEasy
        ) {
            return ConversationAbilityBand.NuanceControl
        }
        return ConversationAbilityBand.ConnectedExpression
    }

    private fun requiresIntentOnly(summary: ChatEvidenceSummary): Boolean {
        return summary.targetLanguageComprehension == TargetLanguageComprehensionEvidence.None ||
            summary.targetLanguageProduction == TargetLanguageProductionEvidence.None ||
            summary.supportLanguageDependence == LanguageDependenceEvidence.High ||
            summary.aiScaffoldingDependence == LanguageDependenceEvidence.High ||
            summary.conversationSustainability == ConversationSustainabilityEvidence.RequiresSupport ||
            summary.responseDifficultyFit == ResponseDifficultyFitEvidence.TooHard
    }

    private fun LanguageDependenceEvidence.atMost(max: LanguageDependenceEvidence): Boolean {
        // enum 순서는 High -> Medium -> Low -> None 이므로 ordinal이 클수록 의존이 낮다.
        return ordinal >= max.ordinal
    }
}
