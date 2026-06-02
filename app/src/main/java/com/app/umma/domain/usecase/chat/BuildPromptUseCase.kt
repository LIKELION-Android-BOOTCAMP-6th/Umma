package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.QuestionStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.realtime.SessionTurn
import javax.inject.Inject

/**
 * LearnerAdaptationProfile 과 언어 설정을 기반으로 AI 튜터의 시스템 프롬프트를 생성하는 유스케이스입니다.
 *
 * 이 UseCase 는 raw LangState metric 을 직접 해석하지 않는다.
 * LearningState domain 이 만든 profile policy 만 사람이 읽을 수 있는 instruction 으로 바꾼다.
 */
class BuildPromptUseCase @Inject constructor() {
    /**
     * 프롬프트를 생성합니다.
     *
     * @param profile LearningState domain 이 계산한 학습자 적응 정책
     * @param primaryLang 설명/힌트 보조에 사용할 학습 기준 언어
     * @param selectedLang AI 가 대화해야 하는 학습 대상 언어
     * @param recentFullContext 저장 완료된 최근 대화 context
     * @param recentTopicSummaries 최근 5개 세션 주제 요약 목록. 존재 시 AI 에 전달해 대화 연속성을 높인다. (#162-C)
     * @return OpenAI Realtime 세션에 전달할 시스템 지침 문자열
     */
    operator fun invoke(
        profile: LearnerAdaptationProfile,
        primaryLang: LangCode,
        selectedLang: LangCode,
        recentFullContext: List<SessionTurn> = emptyList(),
        recentTopicSummaries: List<String> = emptyList()
    ): String {
        // selectedLang 은 대화 언어이고, primaryLang 은 설명 보조 언어다.
        // 두 값을 분리해 prompt 에 써야 primaryLang 이 대화 언어를 덮어쓰지 않는다.
        val selectedLanguageName = languageName(selectedLang)
        val primaryLanguageName = languageName(primaryLang)
        // Chat 정책 enum 은 prompt 에 그대로 노출하지 않고 행동 지시 문장으로 바꾼다.
        val responseInstruction = responseInstruction(profile.chatPolicy.responseLength)
        val questionInstruction = questionInstruction(profile.chatPolicy.questionStyle)
        val challengeInstruction = challengeInstruction(profile.chatPolicy.challengeLevel)
        val primarySupportInstruction = primarySupportInstruction(
            support = profile.chatPolicy.primaryLanguageSupport,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        // 반복 약점은 전체 목록을 넣지 않고 profile 이 요약한 1~2개만 짧게 전달한다.
        val focusInstruction = focusInstruction(profile.core.focus)
        val recentContext = recentFullContext
            .takeLast(12)
            .joinToString(separator = "\n") { turn ->
                "${turn.role.name}: ${turn.text}"
            }

        val contextInstruction = if (recentContext.isBlank()) {
            "There is no confirmed conversation context yet."
        } else {
            """
            Use this confirmed recent conversation context when continuing:
            $recentContext
            """.trimIndent()
        }

        // 최근 세션 주제 요약이 있으면 AI 가 대화 방향을 자연스럽게 이어갈 수 있도록 주입한다.
        // 호출처가 emptyList() 를 전달할 경우 이 블록은 프롬프트에 포함되지 않는다. (#162-C)
        val topicsInstruction = if (recentTopicSummaries.isNotEmpty()) {
            val topicsList = recentTopicSummaries.joinToString(separator = "\n") { "- $it" }
            "\nRecent topics the student has discussed:\n$topicsList"
        } else {
            ""
        }

        return """
            You are Umma, a warm $selectedLanguageName conversation tutor.
            Speak primarily in $selectedLanguageName.
            $primarySupportInstruction
            $challengeInstruction
            $responseInstruction
            $questionInstruction
            $focusInstruction
            $contextInstruction$topicsInstruction
            Lead a natural conversation and keep the learner speaking.
            Do not mention internal scores, numeric metrics, or stored learning state fields.
        """.trimIndent()
    }

    private fun languageName(langCode: LangCode): String {
        return when (langCode) {
            LangCode.EN -> "English"
            LangCode.JA -> "Japanese"
            LangCode.KO -> "Korean"
            LangCode.DE -> "German"
            // UNKNOWN 은 정상 selectedLang 으로 오면 안 되지만, prompt 생성 실패가 session start 를 막지 않도록 안전값을 둔다.
            LangCode.UNKNOWN -> "English"
        }
    }

    private fun responseInstruction(policy: ResponseLengthPolicy): String {
        // 응답 길이는 대화 피로도를 좌우하므로 profile policy 를 직접적인 행동 지시로 변환한다.
        return when (policy) {
            ResponseLengthPolicy.OneShortSentence -> "Reply with one short sentence at a time."
            ResponseLengthPolicy.ShortTwoStep -> "Reply briefly, then add one easy follow-up question."
            ResponseLengthPolicy.NaturalBrief -> "Keep replies natural and brief without over-explaining."
            ResponseLengthPolicy.Flexible -> "Use flexible response length only when nuance or examples help the learner."
        }
    }

    private fun questionInstruction(policy: QuestionStylePolicy): String {
        // 질문 방식은 사용자가 다음 발화를 만들 수 있는지에 직접 영향을 준다.
        return when (policy) {
            QuestionStylePolicy.OneConcreteQuestion -> "Ask one concrete question that can be answered simply."
            QuestionStylePolicy.GuidedChoiceQuestion -> "Offer simple choices when the learner may need help continuing."
            QuestionStylePolicy.OpenFollowUp -> "Ask natural follow-up questions about reasons, experiences, or preferences."
            QuestionStylePolicy.NuanceFollowUp -> "Invite nuance, register, and more natural expression choices when appropriate."
        }
    }

    private fun challengeInstruction(policy: ChallengeLevel): String {
        // Challenge 는 "현재 능력보다 얼마나 밀어도 되는지"를 정하는 가장 상위의 대화 난이도 지시다.
        return when (policy) {
            ChallengeLevel.Support -> "Use very familiar patterns and avoid introducing new expressions unless necessary."
            ChallengeLevel.Match -> "Stay close to the learner's current ability and introduce only small extensions."
            ChallengeLevel.Stretch -> "Add at most one useful new spoken expression when it clearly helps the conversation."
            ChallengeLevel.Refine -> "Refine nuance, register, and natural phrasing while keeping the conversation fluid."
        }
    }

    private fun primarySupportInstruction(
        support: PrimaryLanguageSupportPolicy,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        // primaryLang 은 보조 설명 언어일 뿐이므로, selectedLang 중심 대화 지시와 항상 함께 둔다.
        return when (support) {
            PrimaryLanguageSupportPolicy.PrimaryLanguageFirst ->
                "You may use $primaryLanguageName for short explanations or hints, but keep the conversation in $selectedLanguageName."
            PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint ->
                "Use brief $primaryLanguageName hints only when they help the learner continue in $selectedLanguageName."
            PrimaryLanguageSupportPolicy.TargetLanguageFirstWithPrimaryFallback ->
                "Use $selectedLanguageName first, and fall back to short $primaryLanguageName support only if the learner seems stuck."
            PrimaryLanguageSupportPolicy.TargetLanguageOnly ->
                "Do not use $primaryLanguageName support unless the learner explicitly asks for it."
        }
    }

    private fun focusInstruction(focus: LearningFocusSummary): String {
        // focus 가 없거나 신뢰도가 낮으면 특정 약점을 강제로 끼워 넣지 않는다.
        val primary = focus.primaryFocus ?: return "Do not force a correction focus unless the learner's current utterance needs it."
        val secondary = focus.secondaryFocus
        val focusText = listOfNotNull(
            focusLabel(primary),
            secondary?.let(::focusLabel)
        ).joinToString(separator = ", ")
        return "When a natural opportunity appears, gently support these recurring weak points: $focusText."
    }

    private fun focusLabel(type: LearningFocusType): String {
        // enum 이름을 prompt 에 그대로 넣지 않고, 모델이 바로 이해할 수 있는 학습 표현으로 바꾼다.
        return when (type) {
            LearningFocusType.Article -> "articles"
            LearningFocusType.Tense -> "tense choices"
            LearningFocusType.Preposition -> "prepositions"
            LearningFocusType.WordOrder -> "word order"
            LearningFocusType.SentenceFragment -> "complete sentences"
            LearningFocusType.VocabularyChoice -> "word choice"
            LearningFocusType.LimitedVerbRange -> "a wider verb range"
            LearningFocusType.UnnaturalCollocation -> "natural collocations"
            LearningFocusType.TooFormal -> "more natural spoken register"
            LearningFocusType.MissingContext -> "missing context"
        }
    }
}
