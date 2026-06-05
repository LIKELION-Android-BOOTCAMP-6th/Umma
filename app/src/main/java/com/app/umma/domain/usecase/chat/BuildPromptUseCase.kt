package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatTurnContextSignal
import com.app.umma.domain.model.chat.LatestUserTurnRole
import com.app.umma.domain.model.learningstate.ChatTurnAdaptationPolicy
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.PrimaryBridgeReason
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SentenceDensityPolicy
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.realtime.SessionTurn
import javax.inject.Inject

/**
 * LearningState domain 이 만든 profile 을 Chat Realtime system instruction 으로 변환한다.
 *
 * 이 UseCase 는 LangState 숫자와 저장 스키마를 직접 해석하지 않는다.
 * LangState 해석은 BuildLearnerAdaptationProfileUseCase 의 책임이고,
 * 여기서는 이미 정규화된 대화 정책만 짧은 prompt 데이터로 바꾼다.
 */
class BuildPromptUseCase @Inject constructor() {
    /**
     * OpenAI Realtime 세션에 전달할 system instruction 을 만든다.
     *
     * @param profile LangState 를 해석해 만든 공통 AI 적응 profile
     * @param primaryLang 사용자가 학습 기준으로 삼는 언어
     * @param selectedLang 사용자가 현재 배우며 대화하려는 언어
     * @param recentFullContext 저장이 확정된 최근 대화 turn
     * @param recentTopicSummaries 최근 세션의 주제 요약
     */
    operator fun invoke(
        profile: LearnerAdaptationProfile,
        primaryLang: LangCode,
        selectedLang: LangCode,
        recentFullContext: List<SessionTurn> = emptyList(),
        recentTopicSummaries: List<String> = emptyList()
    ): String {
        // 언어명은 prompt 가 읽기 쉬운 자연어로만 주입한다.
        val primaryLanguageName = languageName(primaryLang)
        val selectedLanguageName = languageName(selectedLang)
        // profile 은 원점수가 아니라 대화 난이도와 보조 강도만 전달해야 한다.
        val profileBlock = profileBlock(profile)
        // 언어 보조 정책은 낮은 단계에서 대화 이해를 먼저 보장하도록 짧게 축약한다.
        val languageBlock = languageBlock(
            primaryBridge = profile.chatPolicy.primaryBridge,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        // response_flow에는 모든 모드에 공통인 대화 원칙만 둔다. 난이도별 의도 추론/recast는 learner_policy가 담당한다.
        val responseFlowBlock = responseFlowBlock()
        // 발화 수준별 분기는 prompt 의 핵심이다. 장문 설명보다 짧은 분기 데이터가 더 안정적이다.
        val branchBlock = branchBlock(
            selectedLanguageName = selectedLanguageName,
            primaryLanguageName = primaryLanguageName,
            primaryBridge = profile.chatPolicy.primaryBridge
        )
        // Chat 세부 정책은 내부 enum 이름을 숨기고 4~6줄의 행동 지시로 압축한다.
        val policyBlock = policyBlock(
            profile = profile,
            selectedLanguageName = selectedLanguageName
        )
        // 응답 길이와 질문 방식은 profile 의 chatPolicy 를 그대로 행동 문장으로 낮춘다.
        val responseBlock = responseBlock(
            responseLength = profile.chatPolicy.responseLength,
            questionLoad = profile.chatPolicy.questionLoad,
            speechSpeed = profile.chatPolicy.speechSpeed,
            confidence = profile.core.levelConfidence
        )
        // focus 는 신뢰 가능한 상위 약점만 대화 안에서 가볍게 다루도록 한다.
        val focusBlock = focusBlock(profile.core.focus)
        // 최근 맥락은 topic continuity 용도이며, 이전 AI 응답 스타일을 모방하라는 지시가 아니다.
        val contextBlock = contextBlock(recentFullContext, recentTopicSummaries)

        return """
            persona:
            - name: Umma
            - role: 모국어는 ${selectedLanguageName}이고 ${primaryLanguageName}도 잘 구사하는, 눈치 빠른 원어민 친구
            - goal: 모든 응답의 첫 원칙은 실제 일상 대화처럼 자연스럽게 반응하는 것이다.
            - style: 학습 보조는 대화를 깨지 않는 범위에서만 조용히 섞고, 원어민이 자주 쓰는 자연스러운 구어체를 우선한다.

            languages:
            - target: $selectedLanguageName
            - support: $primaryLanguageName
            $languageBlock

            response_flow:
            $responseFlowBlock

            branches:
            $branchBlock

            learner_policy:
            $policyBlock
            $responseBlock
            $focusBlock

            learner_profile:
            $profileBlock

            context:
            $contextBlock
        """.trimIndent()
    }

    /**
     * Logcat에서 세션 prompt에 어떤 정책이 반영됐는지 확인하기 위한 요약.
     *
     * prompt 본문, 최근 대화 원문, 사용자 발화 원문은 담지 않는다.
     */
    fun buildSessionPromptTrace(
        profile: LearnerAdaptationProfile,
        primaryLang: LangCode,
        selectedLang: LangCode,
        recentFullContext: List<SessionTurn> = emptyList(),
        recentTopicSummaries: List<String> = emptyList()
    ): String {
        val policy = profile.chatPolicy
        val focus = profile.core.focus
        return "prompt=system " +
            "sections=persona,languages,response_flow,branches,learner_policy,learner_profile,context " +
            "langs=${primaryLang.code}->${selectedLang.code} " +
            "chatPolicy={band=${policy.conversationBand},intent=${policy.intentSupport},primaryBridge=${policy.primaryBridge}," +
            "recast=${policy.recastStyle},growth=${policy.expressionGrowth},questionLoad=${policy.questionLoad}," +
            "responseLength=${policy.responseLength},speechSpeed=${policy.speechSpeed}} " +
            "profile={confidence=${profile.core.levelConfidence},grammar=${profile.core.grammarStage}," +
            "vocabulary=${profile.core.vocabularyStage},fluency=${profile.core.fluencyStage}," +
            "naturalness=${profile.core.naturalnessStage}} " +
            "focus={primary=${focus.primaryFocus},secondary=${focus.secondaryFocus},confidence=${focus.confidence}," +
            "observed=${focus.observedCount}} " +
            "context={turns=${recentFullContext.size},topics=${recentTopicSummaries.size}}"
    }

    /**
     * Logcat에서 이번 response.create override가 왜 들어갔는지 확인하기 위한 요약.
     *
     * response.create.instructions 본문과 사용자 발화 원문은 담지 않는다.
     */
    fun buildTurnOverrideTrace(
        basePolicy: ChatTurnAdaptationPolicy,
        turnPolicy: ChatTurnAdaptationPolicy,
        contextSignal: ChatTurnContextSignal,
        hasResponseInstructions: Boolean,
        outputAudioSpeed: Double?
    ): String {
        val changed = listOfNotNull(
            "responseLength".takeIf { turnPolicy.responseLength != basePolicy.responseLength },
            "sentenceDensity".takeIf { turnPolicy.sentenceDensity != basePolicy.sentenceDensity },
            "primaryBridge".takeIf {
                turnPolicy.primaryBridge != basePolicy.primaryBridge ||
                    turnPolicy.primaryBridgeReason != PrimaryBridgeReason.ProfileDefault
            },
            "questionLoad".takeIf { turnPolicy.questionLoad != basePolicy.questionLoad },
            "speechSpeed".takeIf { turnPolicy.speechSpeed != basePolicy.speechSpeed },
            "contextProgress".takeIf {
                contextSignal.latestUserTurnRole == LatestUserTurnRole.ProgressingInContext
            }
        )
        return "prompt=response.create.override " +
            "contextSignal={role=${contextSignal.latestUserTurnRole},followsQuestion=${contextSignal.followsAssistantQuestion}} " +
            "base=${turnPolicyTrace(basePolicy)} " +
            "turn=${turnPolicyTrace(turnPolicy)} " +
            "changed=${changed.ifEmpty { listOf("none") }.joinToString(separator = ",")} " +
            "hasInstructions=$hasResponseInstructions " +
            "outputAudioSpeed=$outputAudioSpeed"
    }

    /**
     * 이번 AI 응답에만 적용할 `response.create.instructions` override를 만든다.
     *
     * 세션 시작 prompt 전체를 다시 보내지 않고, 방금 USER final transcript가 보여준
     * 막힘/회복 신호에 따라 이번 응답에서 바뀌는 값만 2~4줄로 압축한다.
     */
    fun buildTurnOverrideInstruction(
        basePolicy: ChatTurnAdaptationPolicy,
        turnPolicy: ChatTurnAdaptationPolicy,
        primaryLang: LangCode,
        selectedLang: LangCode
    ): String? {
        // turn override도 내부 enum 이름을 노출하지 않기 위해 언어명을 자연어로 변환한다.
        val primaryLanguageName = languageName(primaryLang)
        val selectedLanguageName = languageName(selectedLang)
        // 같은 초급 지시가 매 turn 반복되면 모델이 "더 느리게/더 쉽게"를 누적 강화할 수 있다.
        // 그래서 baseline과 달라진 항목만 response.create.instructions에 넣는다.
        val overrideLines = buildList {
            if (turnPolicy.responseLength != basePolicy.responseLength) {
                add(turnResponseLengthLine(turnPolicy.responseLength))
            }
            if (turnPolicy.sentenceDensity != basePolicy.sentenceDensity) {
                add(sentenceDensityLine(turnPolicy.sentenceDensity))
            }
            if (
                turnPolicy.primaryBridge != basePolicy.primaryBridge ||
                turnPolicy.primaryBridgeReason == PrimaryBridgeReason.ExplicitSupportRequest ||
                turnPolicy.primaryBridgeReason == PrimaryBridgeReason.PrimaryDominantTurn
            ) {
                add(
                    turnPrimaryBridgeLine(
                        policy = turnPolicy.primaryBridge,
                        reason = turnPolicy.primaryBridgeReason,
                        primaryLanguageName = primaryLanguageName,
                        selectedLanguageName = selectedLanguageName
                    )
                )
            }
            if (turnPolicy.questionLoad != basePolicy.questionLoad) {
                add(turnQuestionLoadLine(turnPolicy.questionLoad))
            }
            if (turnPolicy.speechSpeed != basePolicy.speechSpeed) {
                add(turnSpeechDeliveryLine(turnPolicy.speechSpeed))
            }
        }

        // 바뀐 항목이 없으면 이번 응답은 세션 시작 prompt만 따르게 해서 중복 instruction을 없앤다.
        if (overrideLines.isEmpty()) return null

        return buildString {
            appendLine("current_turn_override:")
            overrideLines.forEach { line ->
                appendLine("- $line")
            }
        }.trimEnd()
    }

    private fun turnPolicyTrace(policy: ChatTurnAdaptationPolicy): String {
        return "{responseLength=${policy.responseLength},sentenceDensity=${policy.sentenceDensity}," +
            "primaryBridge=${policy.primaryBridge},primaryBridgeReason=${policy.primaryBridgeReason}," +
            "questionLoad=${policy.questionLoad},speechSpeed=${policy.speechSpeed}}"
    }

    private fun languageName(langCode: LangCode): String {
        // UNKNOWN 은 정상 사용자 설정이 아니지만 session start 실패보다 안전한 기본값이 낫다.
        return when (langCode) {
            LangCode.EN -> "영어"
            LangCode.JA -> "일본어"
            LangCode.KO -> "한국어"
            LangCode.DE -> "독일어"
            LangCode.UNKNOWN -> "영어"
        }
    }

    private fun languageBlock(
        primaryBridge: PrimaryBridgePolicy,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        // primaryLang 은 낮은 단계에서 이해를 보장하는 대화 지속 장치이고, selectedLang 은 실제 대화 목표 언어다.
        val supportRule = when (primaryBridge) {
            PrimaryBridgePolicy.Active ->
                "support_rule: 사용자가 학습언어($selectedLanguageName)를 거의 못 쓰거나 기준언어($primaryLanguageName)를 섞으면 ${primaryLanguageName}로 의미를 먼저 받아 주고, ${selectedLanguageName}는 1~3단어 조합이나 아주 짧은 표현만 붙인다."
            PrimaryBridgePolicy.Brief ->
                "support_rule: 사용자가 막히면 $primaryLanguageName 힌트로 이해를 돕고 $selectedLanguageName 표현은 짧게 둔다."
            PrimaryBridgePolicy.FallbackOnly ->
                "support_rule: 먼저 학습언어($selectedLanguageName)로 답하고, 명시적 도움 요청이나 의도 복원이 어려운 경우에만 기준언어($primaryLanguageName)를 짧게 섞는다. 칭찬, 요약, 공감만을 위해 기준언어를 덧붙이지 않는다."
            PrimaryBridgePolicy.None ->
                "support_rule: 사용자가 요청한 경우를 제외하고 ${selectedLanguageName}만 사용한다."
        }
        return """
            - $supportRule
            - third_language_rule: ${primaryLanguageName}와 $selectedLanguageName 외 언어를 섞지 않는다.
        """.trimIndent()
    }

    private fun responseFlowBlock(): String {
        // 모든 모드에 공통인 대화 원칙만 남기고, 난이도별 보정은 learner_policy/branches로 분리한다.
        return """
            - natural_reaction: 교정 설명보다 일상 대화 반응을 먼저 한다.
            - flow: 대화가 끊기지 않게 짧게 이어가되, 매번 같은 질문 형식으로 끝내지 않는다.
            - conversation_lead: 대화 시작이나 재개 때는 최근 주제나 가벼운 일상 주제를 먼저 제안하고, 사용자가 모든 주제를 정하게 하지 않는다.
        """.trimIndent()
    }

    private fun branchBlock(
        selectedLanguageName: String,
        primaryLanguageName: String,
        primaryBridge: PrimaryBridgePolicy
    ): String {
        // 분기는 band 이름 없이 현재 발화 모양에 따라 모델이 즉시 적용할 행동만 남긴다.
        val fragmentRule = when (primaryBridge) {
            PrimaryBridgePolicy.Active ->
                "fragment: 뜻만 있는 단어 조각이면 기준언어($primaryLanguageName)로 의미를 먼저 받아 주고, ${selectedLanguageName}는 완성 문장보다 1~3단어 조합이나 아주 짧은 고정 표현 하나만 붙인다."
            PrimaryBridgePolicy.Brief ->
                "fragment: 뜻만 있는 단어 조각이면 먼저 쉬운 ${selectedLanguageName} 표현으로 이어가되, 사용자가 막힌 신호를 보일 때만 기준언어($primaryLanguageName) 힌트를 짧게 붙인다."
            PrimaryBridgePolicy.FallbackOnly ->
                "fragment: 뜻만 있는 단어 조각이어도 기본은 쉬운 ${selectedLanguageName}로 이어가고, 의도 복원이 어려울 때만 기준언어($primaryLanguageName)를 한 줄 보조로 쓴다."
            PrimaryBridgePolicy.None ->
                "fragment: 뜻만 있는 단어 조각이어도 사용자가 요청하지 않으면 ${selectedLanguageName}만 사용하고, 짧고 쉬운 표현으로 이어간다."
        }
        return """
            - $fragmentRule
            - simple: 뜻이 대략 통하면 자연 반응 안에 $selectedLanguageName 재표현을 한 번만 섞고, 필요할 때만 짧게 이어 묻는다.
            - fluent: 말이 충분히 자연스러우면 교정 없이 대화를 이어가고, 필요할 때만 더 구어체다운 표현을 대화 문장 안에 자연스럽게 섞는다.
        """.trimIndent()
    }

    private fun policyBlock(
        profile: LearnerAdaptationProfile,
        selectedLanguageName: String
    ): String {
        // 여러 Chat 정책 enum을 1:1로 모두 노출하면 prompt가 길어지므로 실행 가능한 문장만 고른다.
        val policy = profile.chatPolicy
        return listOfNotNull(
            intentSupportLine(policy.intentSupport),
            primaryBridgeLine(policy.primaryBridge),
            primaryBridgePriorityLine(
                policy = policy.primaryBridge,
                selectedLanguageName = selectedLanguageName
            ),
            recastLine(
                policy = policy.recastStyle,
                selectedLanguageName = selectedLanguageName
            ),
            growthLine(policy.expressionGrowth),
            confidenceLine(profile.core.levelConfidence)
        ).joinToString(separator = "\n") { "- $it" }
    }

    private fun intentSupportLine(policy: IntentSupportPolicy): String {
        // 의도 추론 강도는 초저숙련 사용자가 대화를 끊기지 않게 만드는 핵심 정책이다.
        return when (policy) {
            IntentSupportPolicy.InferActively -> "불완전한 말에서도 사용자의 의도를 먼저 추론하고 대화를 이어간다."
            IntentSupportPolicy.ConfirmBriefly -> "오해 가능성이 있으면 아주 짧게 의도만 확인한 뒤 대화를 이어간다."
            IntentSupportPolicy.TrustMeaning -> "뜻이 보이면 확인 질문보다 자연스러운 대화 반응을 우선한다."
            IntentSupportPolicy.FollowUserLead -> "사용자가 이끄는 주제와 말투를 따라가며 일반 대화처럼 답한다."
        }
    }

    private fun primaryBridgeLine(policy: PrimaryBridgePolicy): String {
        // primaryLang은 학습 보조 언어일 뿐이며 selectedLang 대화 경험을 대체하면 안 된다.
        return when (policy) {
            PrimaryBridgePolicy.Active -> "이해가 끊길 것 같으면 보조 언어로 짧게 확인하고 바로 목표 언어로 돌아온다."
            PrimaryBridgePolicy.Brief -> "사용자가 막힐 때만 보조 언어 힌트를 짧게 사용한다."
            PrimaryBridgePolicy.FallbackOnly -> "기본은 목표 언어이고, 명시적 도움 요청이나 큰 오해가 있을 때만 보조 언어를 한 줄 쓴다."
            PrimaryBridgePolicy.None -> "사용자가 요청하지 않으면 목표 언어만 사용한다."
        }
    }

    private fun primaryBridgePriorityLine(
        policy: PrimaryBridgePolicy,
        selectedLanguageName: String
    ): String? {
        // 이해 우선 원칙은 보조 언어가 실제로 열리는 낮은 모드에서만 강하게 적용한다.
        return when (policy) {
            PrimaryBridgePolicy.Active,
            PrimaryBridgePolicy.Brief -> "priority_rule: $selectedLanguageName 노출보다 사용자가 이해하고 다음 말을 할 수 있게 하는 것이 먼저다."
            PrimaryBridgePolicy.FallbackOnly,
            PrimaryBridgePolicy.None -> null
        }
    }

    private fun recastLine(
        policy: RecastStylePolicy,
        selectedLanguageName: String
    ): String {
        // recast는 별도 수업 예문이 아니라 응답 문장 안에 자연스럽게 들어가야 한다.
        return when (policy) {
            RecastStylePolicy.TinyInline -> "사용자가 말하려던 뜻을 아주 쉬운 표현으로 응답 안에 한 번만 보여준다."
            RecastStylePolicy.SimpleInline -> "사용자의 뜻을 쉬운 자연 문장으로 한 번만 다시 보여준다."
            RecastStylePolicy.NaturalInline -> "필요할 때만 사용자가 말하려던 뜻을 원어민이 실제 자주 쓰는 $selectedLanguageName 문장 안에 자연스럽게 한 번 녹인다."
            RecastStylePolicy.NuanceOnly -> "필요할 때만 더 원어민다운 뉘앙스를 자연스럽게 섞는다."
        }
    }

    private fun growthLine(policy: ExpressionGrowthPolicy): String {
        // 10% 성장 목적은 한 번에 많은 표현을 주입하는 것이 아니라 작은 확장을 꾸준히 보여주는 것이다.
        return when (policy) {
            ExpressionGrowthPolicy.OneTinyPhrase -> "새 표현은 아주 작은 표현 하나만 둔다."
            ExpressionGrowthPolicy.OneSimplePattern -> "쉬운 일상 패턴 하나만 자연스럽게 더한다."
            ExpressionGrowthPolicy.OneEverydayExpression -> "상황에 맞는 일상 구어 표현 하나만 더한다."
            ExpressionGrowthPolicy.OneNativeLikeChoice -> "필요할 때만 원어민다운 표현 선택지를 미세하게 보여준다."
        }
    }

    private fun confidenceLine(confidence: ProfileConfidence): String {
        // confidence는 실력 라벨이 아니라 저장된 판단 근거의 신뢰도라 현재 발화 우선 여부를 정한다.
        return when (confidence) {
            ProfileConfidence.Low -> "저장된 근거가 적어도 현재 발화가 이어질 수 있게 이해 가능한 반응을 우선한다."
            ProfileConfidence.Medium -> "저장된 profile을 참고하되 현재 발화가 더 쉽거나 어렵다면 즉시 맞춘다."
            ProfileConfidence.High -> "저장된 profile을 적극 참고하되 대화 흐름을 우선한다."
        }
    }

    private fun profileBlock(profile: LearnerAdaptationProfile): String {
        // profile block은 내부 enum/점수를 설명하지 않고 영역별 대화 지원 방향만 담는다.
        return """
            - grammar: ${stageLabel(profile.core.grammarStage)}
            - vocabulary: ${stageLabel(profile.core.vocabularyStage)}
            - fluency: ${stageLabel(profile.core.fluencyStage)}
            - naturalness: ${stageLabel(profile.core.naturalnessStage)}
        """.trimIndent()
    }

    private fun stageLabel(stage: SkillStage): String {
        // stage 는 raw 점수보다 prompt 에 안정적으로 들어가는 능력 단위다.
        return when (stage) {
            SkillStage.Foundation -> "기초 표현을 안전하게 만든다"
            SkillStage.Developing -> "짧은 문장을 조금씩 넓힌다"
            SkillStage.Stable -> "기본 대화를 안정적으로 이어간다"
            SkillStage.Expanding -> "표현 폭을 자연스럽게 넓힌다"
            SkillStage.Refined -> "뉘앙스와 말투를 다듬는다"
        }
    }

    private fun responseBlock(
        responseLength: ResponseLengthPolicy,
        questionLoad: QuestionLoadPolicy,
        speechSpeed: SpeechSpeedPolicy,
        confidence: ProfileConfidence
    ): String {
        // 응답 길이와 질문 방식은 사용자가 다음 turn 을 만들 수 있는지에 직접 영향을 준다.
        val lengthRule = when (responseLength) {
            ResponseLengthPolicy.OneShortSentence -> "response_length: 짧은 반응으로 한 가지 의미만 전달한다."
            ResponseLengthPolicy.ShortTwoStep -> "response_length: 짧은 반응에 필요한 경우 가벼운 후속 여지를 둔다."
            ResponseLengthPolicy.NaturalBrief -> "response_length: 자연스럽고 간결하게 말한다."
            ResponseLengthPolicy.Flexible -> "response_length: 필요할 때만 조금 길게 설명한다."
        }
        val questionRule = when (questionLoad) {
            QuestionLoadPolicy.ConcreteChoice -> "turn_space: 필요할 때 음식, 장소, 감정, 행동처럼 실제 내용으로 짧게 답할 여지를 주고, 넓은 주제 선택을 사용자에게 떠넘기지 않는다."
            QuestionLoadPolicy.OneConcreteFollowUp -> "turn_space: 사용자의 말이나 최근 주제에서 자연스럽게 이어지는 짧은 여지를 둔다."
            QuestionLoadPolicy.OpenShort -> "turn_space: 너무 넓지 않은 짧은 open-ended 여지를 두되, 시작할 때는 AI가 가벼운 주제나 선택지를 먼저 제안한다."
            QuestionLoadPolicy.NuanceFollowUp -> "turn_space: 대화가 안정될 때만 이유나 뉘앙스로 가볍게 넓힌다."
        }
        // Realtime speed 값만 낮추면 문장 자체가 길 때 초저숙련 사용자는 여전히 이해하기 어렵다.
        // 그래서 근거 부족/초급 속도 정책에서는 같은 turn 안에서 말의 밀도와 pause 느낌까지 함께 지시한다.
        val deliveryRule = when {
            confidence == ProfileConfidence.Low ->
                "speech_delivery: 첫 발화가 조각나도 천천히 말하며 한 가지 의미씩 이해하게 한다."
            speechSpeed == SpeechSpeedPolicy.SlowBeginner ->
                "speech_delivery: 쉬운 단어를 또박또박 쓰고 문장 사이를 쉬어 의미를 놓치지 않게 한다."
            speechSpeed == SpeechSpeedPolicy.Guided ->
                "speech_delivery: 자연스럽되 빠르게 몰아 말하지 않는다."
            speechSpeed == SpeechSpeedPolicy.NormalLearning ->
                "speech_delivery: 일반 학습자가 듣기 좋은 자연스러운 속도로 말한다."
            speechSpeed == SpeechSpeedPolicy.SlightlyFast ->
                "speech_delivery: 자연스러운 속도를 유지하되 핵심 의미는 분명하게 말한다."
            speechSpeed == SpeechSpeedPolicy.Advanced ->
                "speech_delivery: 원어민 대화에 가까운 자연스러운 속도와 리듬으로 말한다."
            else ->
                "speech_delivery: 사용자가 이해할 수 있는 속도와 문장 밀도를 우선한다."
        }
        return """
            - $lengthRule
            - $questionRule
            - $deliveryRule
        """.trimIndent()
    }

    private fun turnResponseLengthLine(policy: ResponseLengthPolicy): String {
        // 이번 응답 길이는 세션 기본값보다 더 보수적으로 낮아질 수 있다.
        return when (policy) {
            ResponseLengthPolicy.OneShortSentence -> "이번 응답은 짧게 반응하고 한 가지 의미만 전달한다."
            ResponseLengthPolicy.ShortTwoStep -> "이번 응답은 짧은 반응에 필요한 후속 여지만 둔다."
            ResponseLengthPolicy.NaturalBrief -> "이번 응답은 자연스럽지만 간결하게 유지한다."
            ResponseLengthPolicy.Flexible -> "이번 응답은 필요할 때만 조금 더 자세히 말한다."
        }
    }

    private fun sentenceDensityLine(policy: SentenceDensityPolicy): String {
        // 정보 밀도는 실제 속도와 별개다. 초저숙련 사용자는 한 의미씩 들어야 다음 말을 만들 수 있다.
        return when (policy) {
            SentenceDensityPolicy.OneIdea -> "한 번에 하나의 의미만 담아 사용자가 놓치지 않게 한다."
            SentenceDensityPolicy.SimpleTwoStep -> "정보는 두 단계 이하로 나누고, 필요할 때만 구체 상황으로 이어 간다."
            SentenceDensityPolicy.NaturalBrief -> "일상 대화처럼 짧고 자연스러운 정보량을 유지한다."
            SentenceDensityPolicy.Flexible -> "대화가 안정적이면 필요한 맥락을 조금 더 담아도 된다."
        }
    }

    private fun turnPrimaryBridgeLine(
        policy: PrimaryBridgePolicy,
        reason: PrimaryBridgeReason,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        // turn 단위 primary bridge는 이번 응답에서만 열리며, selectedLang 대화를 대체하면 안 된다.
        if (reason == PrimaryBridgeReason.ExplicitSupportRequest) {
            return "${primaryLanguageName} 보조 요청을 반영해 ${primaryLanguageName}로 이해를 먼저 보장하고, ${selectedLanguageName}는 1~3단어 조합이나 아주 짧은 표현만 붙인다."
        }
        if (reason == PrimaryBridgeReason.BeginnerAutoSupport) {
            return "아직 대화 부담이 큰 상태이므로 ${primaryLanguageName}로 의미를 먼저 짧게 받아 주고, ${selectedLanguageName}는 완성 문장보다 1~3단어 조합이나 아주 짧은 표현 하나만 붙인다."
        }
        if (reason == PrimaryBridgeReason.PrimaryDominantTurn) {
            return "사용자가 ${primaryLanguageName}를 섞어 답했으므로 ${primaryLanguageName}로 의미를 먼저 짧게 받아 주고, ${selectedLanguageName}는 완성 문장보다 1~3단어 조합이나 아주 짧은 표현 하나만 붙인다."
        }
        return when (policy) {
            PrimaryBridgePolicy.Active ->
                "${primaryLanguageName}로 의미를 먼저 받아 주고 ${selectedLanguageName}는 1~3단어 조합이나 아주 짧은 표현만 붙인다."
            PrimaryBridgePolicy.Brief ->
                "막힌 부분만 ${primaryLanguageName} 힌트로 짧게 돕고 ${selectedLanguageName} 대화로 자연스럽게 돌아온다."
            PrimaryBridgePolicy.FallbackOnly ->
                "기본은 ${selectedLanguageName}이고 명시적 도움 요청이나 큰 오해가 있을 때만 ${primaryLanguageName} 한 줄을 보조로 쓴다. 칭찬, 요약, 공감만을 위해 ${primaryLanguageName}를 덧붙이지 않는다."
            PrimaryBridgePolicy.None ->
                "사용자가 요청하지 않으면 ${selectedLanguageName}만 사용한다."
        }
    }

    private fun turnQuestionLoadLine(policy: QuestionLoadPolicy): String {
        // 질문은 학습 설명보다 다음 발화를 가능하게 하는 장치라 turn별로 가장 체감이 크다.
        return when (policy) {
            QuestionLoadPolicy.ConcreteChoice -> "필요할 때 음식, 장소, 감정, 행동 중 하나로 짧게 답할 여지를 둔다."
            QuestionLoadPolicy.OneConcreteFollowUp -> "필요할 때 사용자의 말에서 이어지는 실제 상황 여지만 둔다."
            QuestionLoadPolicy.OpenShort -> "필요할 때 너무 넓지 않은 짧은 open-ended 여지를 둔다."
            QuestionLoadPolicy.NuanceFollowUp -> "대화가 안정적일 때만 이유나 뉘앙스로 가볍게 넓힌다."
        }
    }

    private fun turnSpeechDeliveryLine(policy: SpeechSpeedPolicy): String {
        // Realtime speed가 적용되더라도 모델이 빠른 리듬의 긴 문장을 만들지 않도록 전달 방식을 같이 제어한다.
        return when (policy) {
            SpeechSpeedPolicy.SlowBeginner -> "천천히 또박또박 말하되, 한 번에 한 가지 의미만 전달한다."
            SpeechSpeedPolicy.Guided -> "속도를 낮춰 사용자가 따라올 수 있는 리듬으로 말한다."
            SpeechSpeedPolicy.NormalLearning -> "일반 학습 대화에 맞는 자연스러운 속도감으로 말한다."
            SpeechSpeedPolicy.SlightlyFast -> "자연스러운 리듬을 유지하되 핵심 의미는 분명하게 말한다."
            SpeechSpeedPolicy.Advanced -> "원어민 대화에 가까운 자연스러운 리듬으로 말한다."
        }
    }

    private fun focusBlock(focus: LearningFocusSummary): String {
        // focus 는 confidence 가 낮으면 장기 약점으로 단정하지 않는다.
        val primaryFocus = focus.primaryFocus
        if (primaryFocus == null || focus.confidence == ProfileConfidence.Low || focus.observedCount <= 0) {
            return "- focus: 현재 발화에 꼭 필요할 때만 가볍게 돕고, 특정 약점을 억지로 꺼내지 않는다."
        }
        val focusText = listOfNotNull(
            focusLabel(primaryFocus),
            focus.secondaryFocus?.let(::focusLabel)
        ).joinToString(separator = ", ")
        return "- focus: 자연스러운 기회가 있을 때 $focusText 를 한 번만 가볍게 돕는다."
    }

    private fun focusLabel(type: LearningFocusType): String {
        // enum 이름을 그대로 노출하지 않고, 모델이 바로 이해할 수 있는 표현으로 바꾼다.
        return when (type) {
            LearningFocusType.Article -> "관사"
            LearningFocusType.Tense -> "시제"
            LearningFocusType.Preposition -> "전치사"
            LearningFocusType.WordOrder -> "어순"
            LearningFocusType.SentenceFragment -> "완전한 문장"
            LearningFocusType.VocabularyChoice -> "단어 선택"
            LearningFocusType.LimitedVerbRange -> "동사 표현"
            LearningFocusType.UnnaturalCollocation -> "자연스러운 단어 조합"
            LearningFocusType.TooFormal -> "구어체 말투"
            LearningFocusType.MissingContext -> "맥락 보완"
        }
    }

    private fun contextBlock(
        recentFullContext: List<SessionTurn>,
        recentTopicSummaries: List<String>
    ): String {
        // 최근 turn 은 대화 주제 연속성을 위한 자료이며 이전 답변 패턴을 복제하라는 의미가 아니다.
        val recentTurns = recentFullContext
            .takeLast(MAX_CONTEXT_TURN_COUNT)
            .joinToString(separator = "\n") { turn ->
                "- ${turn.role.name}: ${turn.text}"
            }
            .ifBlank { "- none" }
        // topic summary 도 너무 많이 넣으면 prompt 가 다시 장황해져 상위 몇 개만 보낸다.
        val topicLines = recentTopicSummaries
            .take(MAX_TOPIC_COUNT)
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- none" }
        return """
            - recent_turns:
            $recentTurns
            - recent_topics:
            $topicLines
            - rule: 최근 맥락은 주제 이해와 가벼운 대화 제안에만 쓰고, 이전 AI의 응답 습관은 모방하지 않는다.
        """.trimIndent()
    }

    private companion object {
        // 최근 맥락은 많을수록 좋은 것이 아니라 핵심 주제를 유지할 만큼만 필요하다.
        private const val MAX_CONTEXT_TURN_COUNT = 8
        // 주제 요약도 모델 지시보다 길어지지 않도록 작게 제한한다.
        private const val MAX_TOPIC_COUNT = 3
    }
}
