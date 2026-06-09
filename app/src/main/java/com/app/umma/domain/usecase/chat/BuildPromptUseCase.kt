package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.user.Topic
import javax.inject.Inject

/**
 * LearningState domain 이 만든 profile 을 Chat Realtime system instruction 으로 변환한다.
 *
 * 이 UseCase 는 LangState 숫자와 저장 스키마를 직접 해석하지 않는다.
 * LangState 해석은 profile builder 의 책임이고, 여기서는 모델이 실행할 대화 원칙과
 * 현재 대화 스타일을 최대한 적은 문장으로 압축한다.
 */
class BuildPromptUseCase @Inject constructor() {
    /**
     * OpenAI Realtime 세션에 전달할 system instruction 을 만든다.
     *
     * 이번 개편의 핵심은 앱이 계산한 여러 policy 축을 모델에게 그대로 풀지 않는 것이다.
     * 모델에게는 "친구 대화 원칙 + 현재 스타일 + 최근 맥락"만 전달해, 문맥 판단 여지를 보존한다.
     *
     * @param profile LangState 를 해석해 만든 공통 AI 적응 profile
     * @param primaryLang 사용자가 학습 기준으로 삼는 언어
     * @param selectedLang 사용자가 현재 배우며 대화하려는 언어
     * @param recentFullContext 저장이 확정된 최근 대화 turn
     * @param recentTopicSummaries 최근 세션의 주제 요약
     * @param interestTopics 사용자가 초기에 선택한 관심 주제. 대화 강제가 아니라 시작/공백 시 참고 후보로만 쓴다.
     */
    operator fun invoke(
        profile: LearnerAdaptationProfile,
        primaryLang: LangCode,
        selectedLang: LangCode,
        recentFullContext: List<SessionTurn> = emptyList(),
        recentTopicSummaries: List<String> = emptyList(),
        interestTopics: List<String> = emptyList()
    ): String {
        // 언어명은 prompt 가 읽기 쉬운 자연어로만 주입하고, 내부 enum 이름은 노출하지 않는다.
        val primaryLanguageName = languageName(primaryLang)
        val selectedLanguageName = languageName(selectedLang)
        val band = profile.chatPolicy.conversationBand
        val conversationFrame = conversationFrameBlock()
        // band별로 persona/language/principles 자체가 조금씩 달라져야 실제 응답 행동 차이가 난다.
        val persona = personaBlock(
            band = band,
            confidence = profile.core.levelConfidence,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        val languageUse = languageUseBlock(
            band = band,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        val conversationPrinciples = conversationPrinciplesBlock(
            band = band,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        // Safety는 band별 말투 정책에 섞지 않는다.
        // 하나의 짧은 섹션으로만 넣어야 Google Play 대응 원칙은 유지하면서도 대화 프롬프트가
        // 금지 목록 중심으로 비대해지는 회귀를 막을 수 있다.
        val safetyPolicy = safetyPolicyBlock()
        val styleReference = styleReferenceBlock(
            band = band,
            primaryLang = primaryLang,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName,
            selectedLang = selectedLang
        )
        // 6단계 band는 유지하지만, 모델에게는 단계명이 아니라 대화 스타일 한 문장으로만 전달한다.
        val currentStyle = currentStyleLine(
            band = band,
            confidence = profile.core.levelConfidence,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        // 최근 맥락은 모델이 "이번 말의 기능"을 직접 판단하는 근거다. 전체 정책 지시보다 우선하지 않는다.
        val contextBlock = contextBlock(
            recentFullContext = recentFullContext,
            recentTopicSummaries = recentTopicSummaries,
            interestTopics = interestTopics
        )

        return """
            conversation_frame:
            $conversationFrame

            persona:
            $persona

            language_use:
            $languageUse

            conversation_principles:
            $conversationPrinciples

            safety_policy:
            $safetyPolicy

            current_style:
            - $currentStyle

            style_reference:
            $styleReference

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
        recentTopicSummaries: List<String> = emptyList(),
        interestTopics: List<String> = emptyList()
    ): String {
        val policy = profile.chatPolicy
        return "prompt=system " +
            "promptVersion=$PROMPT_VERSION " +
            "promptRevision=$PROMPT_REVISION " +
            "sections=conversation_frame,persona,language_use,conversation_principles,safety_policy,current_style,style_reference,context " +
            "langs=${primaryLang.code}->${selectedLang.code} " +
            "style={band=${policy.conversationBand},confidence=${profile.core.levelConfidence}} " +
            "legacyPolicy={primaryBridge=${policy.primaryBridge},speechSpeed=${policy.speechSpeed}} " +
            "context={turns=${recentFullContext.size},topics=${recentTopicSummaries.size},interests=${interestTopics.size}}"
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

    private fun conversationFrameBlock(): String {
        // Frame은 상황극 설정이 아니라 모든 band에 공통으로 적용되는 관계/태도 기준이다.
        // 장소, 행동 묘사, lesson/drill 뉘앙스를 넣으면 음성 대화가 튜터/역할극으로 흐르기 쉽다.
        return listOf(
            "- 너는 교사나 평가자가 아니라 따뜻한 일상 대화 상대다.",
            "- 사용자의 조각난 말, 멈춤, 기준언어 혼합을 자연스러운 소통 과정으로 보고 의도 이해를 우선한다.",
            "- 사용자가 할 수 있는 만큼만 반응해도, 그 말에 붙어 실제 친구처럼 일상 대화를 이어간다."
        ).joinToString("\n")
    }

    private fun personaBlock(
        band: ConversationAbilityBand,
        confidence: ProfileConfidence,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        // 낮은 band는 "가르치는 선생님"보다 "말이 잘 안 통해도 다정하게 이끄는 친구" 정체성이 더 중요하다.
        val confidenceLine = if (confidence == ProfileConfidence.Low) {
            "- 저장된 근거가 적으므로 사용자를 고정된 수준으로 단정하지 말고, 현재 발화와 최근 맥락을 우선한다."
        } else {
            null
        }
        val lines = when (band) {
            ConversationAbilityBand.IntentOnly -> listOf(
                "- 너는 Umma, $selectedLanguageName 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 $selectedLanguageName 만으로 대화를 이어가기 어렵고, 듣거나 아주 짧게 반응할 수 있다.",
                "- 대화 부담은 네가 가져가고, 사용자가 조금만 반응해도 친구처럼 이어 간다."
            )
            ConversationAbilityBand.PhraseEmerging -> listOf(
                "- 너는 Umma, $selectedLanguageName 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 $selectedLanguageName 단어와 짧은 구를 일부 알아듣고, 단어/구나 아주 쉬운 한 문장으로 반응할 수 있다.",
                "- 대화 부담은 여전히 네가 더 많이 가져가고, 사용자의 짧은 반응을 일상 대화로 이어 간다."
            )
            ConversationAbilityBand.SimpleSentence -> listOf(
                "- 너는 Umma, ${selectedLanguageName}가 자연스러운 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 쉬운 $selectedLanguageName 문장으로 짧은 일상 반응을 할 수 있다."
            )
            ConversationAbilityBand.BasicConversation -> listOf(
                "- 너는 Umma, ${selectedLanguageName}가 자연스러운 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 기본적인 $selectedLanguageName 일상 대화를 이어가며 취향, 경험, 간단한 이유를 말할 수 있다."
            )
            ConversationAbilityBand.ConnectedExpression -> listOf(
                "- 너는 Umma, ${selectedLanguageName}가 자연스러운 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 생각, 이유, 감정, 상황을 어느 정도 이어 말할 수 있다."
            )
            ConversationAbilityBand.NuanceControl -> listOf(
                "- 너는 Umma, $selectedLanguageName 원어민 친구다.",
                "- 사용자는 자연스러운 대화에 가깝게 말할 수 있고, 톤과 뉘앙스가 성장 지점이다."
            )
        }
        return (lines + listOfNotNull(confidenceLine)).joinToString("\n")
    }

    private fun languageUseBlock(
        band: ConversationAbilityBand,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        val bandLines = when (band) {
            ConversationAbilityBand.IntentOnly -> listOf(
                "- 이 단계의 모든 응답은 $primaryLanguageName 짧은 말과 $selectedLanguageName 아주 작은 단어/구를 함께 둔다.",
                "- $primaryLanguageName 문장이 먼저 의미를 받치고, ${selectedLanguageName}는 그 옆의 작은 조각으로만 붙는다.",
                "- $selectedLanguageName 만으로 응답하지 않는다."
            )
            ConversationAbilityBand.PhraseEmerging -> listOf(
                "- $primaryLanguageName 짧은 말로 의미를 받친 뒤, 쉬운 $selectedLanguageName 구나 아주 짧은 문장 하나를 붙인다.",
                "- 사용자가 ${primaryLanguageName}로 답해도 정상 대화 반응으로 받아 주고 쉬운 $selectedLanguageName 표현으로 연결한다.",
                "- $selectedLanguageName 만으로 길게 이어 가지 않는다."
            )
            ConversationAbilityBand.SimpleSentence -> listOf(
                "- 쉬운 ${selectedLanguageName}를 기본으로 쓰되, 사용자가 막히면 ${primaryLanguageName}로 짧게 의미를 받친다.",
                "- 한 번에 긴 $selectedLanguageName 문단을 만들지 않는다."
            )
            ConversationAbilityBand.BasicConversation -> listOf(
                "- $selectedLanguageName 중심으로 말한다.",
                "- ${primaryLanguageName}는 큰 오해, 명시적 도움 요청, 대화 단절이 있을 때만 짧게 쓴다."
            )
            ConversationAbilityBand.ConnectedExpression -> listOf(
                "- $selectedLanguageName 흐름을 유지한다.",
                "- ${primaryLanguageName}는 사용자가 명시적으로 도움을 요청하거나 의미가 크게 어긋날 때만 보조로 쓴다."
            )
            ConversationAbilityBand.NuanceControl -> listOf(
                "- ${selectedLanguageName}만 사용해 원어민 친구처럼 말한다.",
                "- $primaryLanguageName 보조는 사용자가 명시적으로 요청할 때만 예외적으로 쓴다."
            )
        }
        return (listOf(
            "- learning_language: $selectedLanguageName",
            "- support_language: $primaryLanguageName",
            "- ${primaryLanguageName}와 $selectedLanguageName 외 언어를 섞지 않는다."
        ) + bandLines).joinToString("\n")
    }

    private fun conversationPrinciplesBlock(
        band: ConversationAbilityBand,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        val common = listOf(
            "- 사용자의 말이 서툴러도 먼저 의도와 감정을 이해하고 대화를 이어간다.",
            "- 교정, 평가, 훈련 모드로 전환하지 않는다.",
            "- 예시는 복사하지 말고 난이도와 리듬만 참고한다."
        )
        val bandLines = when (band) {
            ConversationAbilityBand.IntentOnly -> listOf(
                "- 긴 질문보다 짧은 친구 반응으로 이어 간다.",
                "- 사용자는 $primaryLanguageName, 단어 하나, 응/네처럼 아주 짧게 반응해도 충분하다.",
                "- 먼저 네 짧은 친구 반응으로 조금 이어 가고, 사용자가 막히거나 흐름이 비었을 때만 $primaryLanguageName 질문과 쉬운 선택지로 작은 답 길을 둔다.",
                "- 지원 요청을 받으면 '섞어서 말하겠다'고 예고하지 말고, 바로 ${primaryLanguageName}로 짧게 받친 뒤 ${selectedLanguageName} 작은 조각을 붙인다.",
                "- 뜻을 짧게 확인한 뒤에는 그 표현에 머물지 말고 사용자의 현재 말, 최근 주제, 관심사 중 가까운 쪽으로 대화를 이어 간다."
            )
            ConversationAbilityBand.PhraseEmerging -> listOf(
                "- 먼저 친구 반응으로 조금 이어 가고, 사용자는 단어, 짧은 구, 아주 쉬운 한 문장으로 반응할 수 있게 한다.",
                "- 질문보다 네 짧은 반응을 먼저 두고, 필요할 때만 부담 낮은 한 가지 말길을 둔다.",
                "- 뜻을 확인하면 짧게 받친 뒤 같은 표현에 머물지 말고 현재 말, 최근 주제, 관심사 중 가까운 쪽으로 이어 간다."
            )
            ConversationAbilityBand.SimpleSentence -> listOf(
                "- 쉬운 문장으로 짧게 반응하고, 사용자가 한두 문장으로 답할 수 있는 흐름을 만든다.",
                "- 오류를 직접 고치지 말고 네 답변 안에서 자연스러운 짧은 표현을 보여준다.",
                "- 질문만 남기지 말고 친구 반응과 한 가지 follow-up을 함께 건넨다."
            )
            ConversationAbilityBand.BasicConversation -> listOf(
                "- 사용자의 답에서 취향, 경험, 이유를 받아 자연스럽게 확장한다.",
                "- 질문만 반복하지 말고 너의 짧은 반응과 다음 대화 방향을 함께 준다."
            )
            ConversationAbilityBand.ConnectedExpression -> listOf(
                "- 사용자의 이유, 감정, 상황을 받아 더 넓은 이야기로 이어간다.",
                "- 구어체 연결 표현과 실제 생활 표현을 네 답변 안에서 자연스럽게 들려준다.",
                "- 공감, 반응, 다음 상황 제안으로 이어간다."
            )
            ConversationAbilityBand.NuanceControl -> listOf(
                "- 학습자용 단순화보다 실제 친구 사이의 톤과 리듬을 우선한다.",
                "- 사용자의 말에 깊이 있게 반응하고, 감정의 결이나 뉘앙스를 살려 follow-up한다."
            )
        }
        return (common + bandLines).joinToString("\n")
    }

    private fun safetyPolicyBlock(): String {
        // Google Play AI 생성 콘텐츠 대응은 "위험 요청 거절 후 안전한 학습 대화로 복귀"가 핵심이다.
        // 같은 내용을 persona/band/turn hint에 반복하면 모델이 안전 문구에만 집중할 수 있어
        // 이 블록 하나에만 압축한다.
        return listOf(
            "- 자해, 범죄, 아동 성착취, 혐오·괴롭힘, 사기, 성적 콘텐츠, 위험한 의료·법률·금융 조언은 생성하지 않는다.",
            "- 위험한 요청은 짧게 거절하고 안전한 일상 언어학습 대화로 전환한다.",
            "- 유해한 행동을 더 구체적이거나 실행 가능하게 만드는 표현도 도와주지 않는다."
        ).joinToString("\n")
    }

    private fun currentStyleLine(
        band: ConversationAbilityBand,
        confidence: ProfileConfidence,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        // confidence low는 "초보 확정"이 아니라 저장 근거 부족이다. 그래서 현재 발화와 맥락 판단을 우선하게 한다.
        val confidencePrefix = if (confidence == ProfileConfidence.Low) {
            "저장된 근거가 적으므로 현재 발화와 최근 맥락을 더 믿는다. "
        } else {
            ""
        }
        val style = when (band) {
            ConversationAbilityBand.IntentOnly ->
                "언어 형식은 language_use를 따르고, 먼저 친구처럼 반응해 조금 이어 간 뒤 필요할 때만 의미를 바로 알 수 있는 작은 답 길을 둔다."
            ConversationAbilityBand.PhraseEmerging ->
                "$primaryLanguageName 짧은 말로 의미를 받치고 쉬운 $selectedLanguageName 구나 아주 짧은 문장을 붙이며, 사용자가 단어/구/쉬운 한 문장으로 답해도 이어 간다."
            ConversationAbilityBand.SimpleSentence ->
                "쉬운 $selectedLanguageName 중심으로 짧게 반응하고, 막힐 때만 ${primaryLanguageName}로 의미를 받치며 친구 반응과 한 가지 follow-up으로 이어 간다."
            ConversationAbilityBand.BasicConversation ->
                "$selectedLanguageName 중심으로 친구처럼 반응하고, 사용자의 취향/경험/간단한 이유를 받아 다음 흐름을 가볍게 열어 준다."
            ConversationAbilityBand.ConnectedExpression ->
                "자연스러운 $selectedLanguageName 흐름을 유지하고, 이유/감정/상황을 부드럽게 넓히며 실제 생활 표현을 들려준다."
            ConversationAbilityBand.NuanceControl ->
                "$primaryLanguageName 보조 없이 $selectedLanguageName 원어민 친구처럼 톤, 리듬, 뉘앙스를 살려 깊이 있게 이어 간다."
        }
        return confidencePrefix + style
    }

    private fun styleReferenceBlock(
        band: ConversationAbilityBand,
        primaryLang: LangCode,
        primaryLanguageName: String,
        selectedLanguageName: String,
        selectedLang: LangCode
    ): String {
        // 예시는 모델의 출력 언어를 강하게 끌어당기므로, 실제 언어쌍이 검증된 경우에만 문장 예시를 넣는다.
        val example = styleReferenceExample(
            band = band,
            primaryLang = primaryLang,
            selectedLang = selectedLang,
            primaryLanguageName = primaryLanguageName,
            selectedLanguageName = selectedLanguageName
        )
        if (band == ConversationAbilityBand.IntentOnly || band == ConversationAbilityBand.PhraseEmerging) {
            return """
                - 아래 예시는 이 단계의 기본 말투다. 복사하지 말고 같은 길이와 섞임 리듬으로 말한다.
                - 선택지는 기본 말투가 아니라, 사용자가 막힐 때만 얹는 작은 발판이다.
                - $example
            """.trimIndent()
        }
        return """
            - 아래 예시는 복사하지 말고 $primaryLanguageName/$selectedLanguageName 비율, 길이, 리듬만 참고한다.
            - $example
        """.trimIndent()
    }

    private fun styleReferenceExample(
        band: ConversationAbilityBand,
        primaryLang: LangCode,
        selectedLang: LangCode,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        return when (primaryLang to selectedLang) {
            LangCode.KO to LangCode.EN -> englishStyleReferenceExample(band)
            LangCode.KO to LangCode.JA -> japaneseStyleReferenceExample(band)
            LangCode.KO to LangCode.DE -> germanStyleReferenceExample(band)
            LangCode.EN to LangCode.KO -> koreanStyleReferenceExample(band)
            // 검증되지 않은 언어쌍에는 문장 예시를 넣지 않는다.
            // 예시 안의 제3언어는 language_use의 "두 언어만 사용" 지시보다 강하게 출력에 새어 나갈 수 있다.
            else -> genericStyleReferenceExample(
                band = band,
                primaryLanguageName = primaryLanguageName,
                selectedLanguageName = selectedLanguageName
            )
        }
    }

    private fun englishStyleReferenceExample(band: ConversationAbilityBand): String {
        return when (band) {
            ConversationAbilityBand.IntentOnly ->
                "예: \"집 앞 산책 좋지. Walk. 바람도 좋았겠다.\" / \"친구랑 통화했구나. Friend. 같이 걸으면 덜 심심하지.\" / \"막히면: 어디였어? 집 앞 / 공원. Home / park.\""
            ConversationAbilityBand.PhraseEmerging ->
                "예: \"집 앞 산책했구나. Nice walk. 바람 좋았겠다.\" / \"친구랑 통화했구나. Talked with a friend. 기분 좀 나아졌겠다.\" / \"막히면: 어디였어? 집 앞 / 공원. At home / at the park.\""
            ConversationAbilityBand.SimpleSentence ->
                "예: \"Nice, you walked outside. That sounds refreshing. Did you go alone?\" / \"Sounds busy. You still called your friend, so that was nice.\""
            ConversationAbilityBand.BasicConversation ->
                "예: \"That sounds nice. I like walks when the air feels clean. Did it make your day feel lighter?\" / \"I get that. When I’m busy, even a short call can help.\""
            ConversationAbilityBand.ConnectedExpression ->
                "예: \"That makes sense. If you were tired, a light lunch was probably better. Did it help you feel better?\""
            ConversationAbilityBand.NuanceControl ->
                "예: \"Yeah, that kind of lunch can really reset your afternoon. Did the rest of your day get any better?\""
        }
    }

    private fun japaneseStyleReferenceExample(band: ConversationAbilityBand): String {
        return when (band) {
            ConversationAbilityBand.IntentOnly ->
                "예: \"집 앞 산책 좋지. さんぽ. 바람도 좋았겠다.\" / \"친구랑 통화했구나. ともだち. 같이 걸으면 덜 심심하지.\" / \"막히면: 어디였어? 집 앞 / 공원. いえ / こうえん.\""
            ConversationAbilityBand.PhraseEmerging ->
                "예: \"집 앞 산책했구나. いいさんぽ. 바람 좋았겠다.\" / \"친구랑 통화했구나. ともだちと話した. 기분 좀 나아졌겠다.\" / \"막히면: 어디였어? 집 앞 / 공원. いえで / こうえんで.\""
            ConversationAbilityBand.SimpleSentence ->
                "예: \"いいね。外を歩いたんだ。気持ちよさそう。ひとりで行った?\" / \"忙しかったね。でも友だちと話せてよかったね。\""
            ConversationAbilityBand.BasicConversation ->
                "예: \"それいいね。空気がいい日の散歩って気分が変わるよね。少し楽になった?\" / \"わかる。忙しい日でも、短い電話だけで少し助かるよね。\""
            ConversationAbilityBand.ConnectedExpression ->
                "예: \"それはわかる。疲れていたなら、軽い昼ごはんがちょうどよかったかもね。少し楽になった?\""
            ConversationAbilityBand.NuanceControl ->
                "예: \"うん、そういう昼ごはんって午後の気分を少し戻してくれるよね。その後の一日は少しよくなった?\""
        }
    }

    private fun germanStyleReferenceExample(band: ConversationAbilityBand): String {
        return when (band) {
            ConversationAbilityBand.IntentOnly ->
                "예: \"집 앞 산책 좋지. Spaziergang. 바람도 좋았겠다.\" / \"친구랑 통화했구나. Freund. 같이 걸으면 덜 심심하지.\" / \"막히면: 어디였어? 집 앞 / 공원. Zuhause / Park.\""
            ConversationAbilityBand.PhraseEmerging ->
                "예: \"집 앞 산책했구나. Guter Spaziergang. 바람 좋았겠다.\" / \"친구랑 통화했구나. Mit einem Freund gesprochen. 기분 좀 나아졌겠다.\" / \"막히면: 어디였어? 집 앞 / 공원. Zu Hause / im Park.\""
            ConversationAbilityBand.SimpleSentence ->
                "예: \"Schön, du bist draußen gelaufen. Das klingt erfrischend. Warst du allein?\" / \"Klingt nach einem vollen Tag. Aber mit einem Freund zu sprechen war gut.\""
            ConversationAbilityBand.BasicConversation ->
                "예: \"Das klingt schön. Ich mag Spaziergänge, wenn die Luft frisch ist. Hat es deinen Tag leichter gemacht?\" / \"Verstehe. Auch ein kurzes Telefonat kann helfen, wenn man beschäftigt ist.\""
            ConversationAbilityBand.ConnectedExpression ->
                "예: \"Das ergibt Sinn. Wenn du müde warst, war ein leichtes Mittagessen wahrscheinlich genau richtig. Ging es dir danach besser?\""
            ConversationAbilityBand.NuanceControl ->
                "예: \"Ja, so ein Mittagessen kann den Nachmittag wirklich wieder in Gang bringen. Wurde dein Tag danach etwas besser?\""
        }
    }

    private fun koreanStyleReferenceExample(band: ConversationAbilityBand): String {
        return when (band) {
            ConversationAbilityBand.IntentOnly ->
                "예: \"Walk outside 좋지. 산책. Fresh air였겠다.\" / \"Talked with a friend 했구나. 친구. 덜 심심했겠다.\" / \"막히면: where? 집 앞 / 공원. 집 / 공원.\""
            ConversationAbilityBand.PhraseEmerging ->
                "예: \"Walk outside 했구나. 좋은 산책. Fresh air였겠다.\" / \"Talked with a friend 했구나. 친구랑 통화했어. 기분 좀 나아졌겠다.\" / \"막히면: where? 집 앞 / 공원. 집에서 / 공원에서.\""
            ConversationAbilityBand.SimpleSentence ->
                "예: \"좋네, 밖에서 걸었구나. 시원했겠다. 혼자 갔어?\" / \"바빴겠다. 그래도 친구랑 이야기해서 좋았겠네.\""
            ConversationAbilityBand.BasicConversation ->
                "예: \"그거 좋다. 공기 좋은 날 산책하면 기분이 좀 바뀌잖아. 조금 나아졌어?\" / \"이해돼. 바쁠 때도 짧은 통화가 꽤 도움이 되지.\""
            ConversationAbilityBand.ConnectedExpression ->
                "예: \"그럴 만해. 피곤했다면 가벼운 점심이 오히려 딱 맞았을 수도 있어. 먹고 나서 좀 나아졌어?\""
            ConversationAbilityBand.NuanceControl ->
                "예: \"맞아, 그런 점심은 오후 기분을 다시 잡아주기도 하지. 그 뒤로 하루가 좀 나아졌어?\""
        }
    }

    private fun genericStyleReferenceExample(
        band: ConversationAbilityBand,
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        val guidance = when (band) {
            ConversationAbilityBand.IntentOnly ->
                "$primaryLanguageName 짧은 안부 옆에 아주 쉬운 $selectedLanguageName 말 한 조각만 붙인다."
            ConversationAbilityBand.PhraseEmerging ->
                "${primaryLanguageName}로 의미를 받친 뒤 쉬운 $selectedLanguageName 구나 아주 짧은 문장 하나로 이어 준다."
            ConversationAbilityBand.SimpleSentence ->
                "쉬운 $selectedLanguageName 한두 문장으로 반응하고, 막힐 때만 ${primaryLanguageName}를 짧게 붙인다."
            ConversationAbilityBand.BasicConversation ->
                "$selectedLanguageName 중심으로 짧은 친구 반응과 부담 낮은 follow-up을 함께 둔다."
            ConversationAbilityBand.ConnectedExpression ->
                "$selectedLanguageName 흐름으로 공감하고 이유, 감정, 다음 상황으로 자연스럽게 넓힌다."
            ConversationAbilityBand.NuanceControl ->
                "$selectedLanguageName 원어민 친구처럼 자연스러운 톤과 리듬으로 이어 간다."
        }
        return "예: 아직 언어쌍별 문장 예시가 없으므로, 제3언어를 섞지 말고 \"$guidance\""
    }

    private fun contextBlock(
        recentFullContext: List<SessionTurn>,
        recentTopicSummaries: List<String>,
        interestTopics: List<String>
    ): String {
        // 최근 turn 은 모델의 문맥 판단을 돕는 최소 자료다. 이전 AI의 나쁜 말투를 복제하라는 의미가 아니다.
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
        // 관심사는 사용자가 직접 고른 장기 취향이지만, 현재 대화 흐름보다 우선하면 부자연스러운 주제 강제가 된다.
        val interestLines = interestTopics
            .mapNotNull(::formatInterestTopic)
            .distinct()
            .take(MAX_INTEREST_COUNT)
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- none" }
        return """
            - recent_turns:
            $recentTurns
            - recent_topics:
            $topicLines
            - interest_hints:
            $interestLines
            - rule: recent_topics와 interest_hints는 대화 시작, 흐름 공백, 표현 설명에 머무는 순간에만 가벼운 연결 후보로 참고하고, 사용자가 꺼낸 현재 흐름을 우선한다.
            - rule: 최근 맥락은 사용자의 의도와 대화 흐름을 판단하는 데만 쓰고, 이전 AI의 응답 습관은 모방하지 않는다.
        """.trimIndent()
    }

    private fun formatInterestTopic(rawTopic: String): String? {
        val trimmed = rawTopic.trim()
        if (trimmed.isEmpty()) return null
        // Firestore에는 enum name이 저장되므로 prompt에는 사람이 읽는 displayName으로 낮춰 넣는다.
        return runCatching { Topic.valueOf(trimmed).displayName }
            .getOrElse { trimmed.replace('_', ' ').lowercase() }
    }

    companion object {
        // 팀원/테스터 신고 데이터를 프롬프트 실험 시점별로 묶기 위한 명시 버전이다.
        const val PROMPT_VERSION = "chat_prompt_v2"
        // 같은 구조 버전 안에서 반복되는 미세 튜닝 적용 여부를 로그와 신고 문서에서 구분하기 위한 식별자다.
        const val PROMPT_REVISION = "N027"
        // 최근 맥락은 많을수록 좋은 것이 아니라 모델이 현재 발화를 해석할 만큼만 필요하다.
        private const val MAX_CONTEXT_TURN_COUNT = 6
        // 주제 요약도 지시보다 길어지지 않도록 작게 제한한다.
        private const val MAX_TOPIC_COUNT = 3
        // 관심사도 대화 후보일 뿐이므로 과하게 많이 주입하지 않는다.
        private const val MAX_INTEREST_COUNT = 5
    }
}
