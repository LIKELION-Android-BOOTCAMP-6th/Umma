package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.realtime.SessionTurn
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
     */
    operator fun invoke(
        profile: LearnerAdaptationProfile,
        primaryLang: LangCode,
        selectedLang: LangCode,
        recentFullContext: List<SessionTurn> = emptyList(),
        recentTopicSummaries: List<String> = emptyList()
    ): String {
        // 언어명은 prompt 가 읽기 쉬운 자연어로만 주입하고, 내부 enum 이름은 노출하지 않는다.
        val primaryLanguageName = languageName(primaryLang)
        val selectedLanguageName = languageName(selectedLang)
        val band = profile.chatPolicy.conversationBand
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
            primaryLanguageName = primaryLanguageName
        )
        val styleReference = styleReferenceBlock(
            band = band,
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
        val contextBlock = contextBlock(recentFullContext, recentTopicSummaries)

        return """
            persona:
            $persona

            language_use:
            $languageUse

            conversation_principles:
            $conversationPrinciples

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
        recentTopicSummaries: List<String> = emptyList()
    ): String {
        val policy = profile.chatPolicy
        return "prompt=system " +
            "promptVersion=$PROMPT_VERSION " +
            "sections=persona,language_use,conversation_principles,current_style,style_reference,context " +
            "langs=${primaryLang.code}->${selectedLang.code} " +
            "style={band=${policy.conversationBand},confidence=${profile.core.levelConfidence}} " +
            "legacyPolicy={primaryBridge=${policy.primaryBridge},speechSpeed=${policy.speechSpeed}} " +
            "context={turns=${recentFullContext.size},topics=${recentTopicSummaries.size}}"
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
                "- 사용자는 ${selectedLanguageName}를 거의 모르는 친구다. 선생님처럼 설명하거나 안심시키는 말에 머물지 말고, 말이 잘 안 통해도 친구가 먼저 작은 생활 말을 건넨다.",
                "- 목표는 생활 속 물건, 감정, 상태를 아주 쉬운 $selectedLanguageName 말 한 조각으로 편하게 들려주는 것이다."
            )
            ConversationAbilityBand.PhraseEmerging -> listOf(
                "- 너는 Umma, $selectedLanguageName 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 $selectedLanguageName 단어와 짧은 구를 조금 알아듣는 친구다. 대화의 부담은 네가 가져가고, 사용자는 짧게 반응해도 충분하게 만든다.",
                "- 목표는 수업이 아니라 서로 말이 조금씩 통하는 친구 대화다."
            )
            ConversationAbilityBand.SimpleSentence -> listOf(
                "- 너는 Umma, ${selectedLanguageName}가 자연스러운 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 쉬운 $selectedLanguageName 문장으로 일상 반응을 할 수 있는 친구다. 느슨하고 편한 친구 대화를 유지한다.",
                "- 목표는 짧은 왕복 대화가 끊기지 않게 하는 것이다."
            )
            ConversationAbilityBand.BasicConversation -> listOf(
                "- 너는 Umma, ${selectedLanguageName}가 자연스러운 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 기본적인 $selectedLanguageName 일상 대화를 이어갈 수 있는 친구다. 튜터가 아니라 대화를 같이 넓히는 친구처럼 말한다.",
                "- 목표는 사용자의 말에서 취향, 경험, 감정을 받아 자연스럽게 이어가는 것이다."
            )
            ConversationAbilityBand.ConnectedExpression -> listOf(
                "- 너는 Umma, ${selectedLanguageName}가 자연스러운 원어민 친구이고 ${primaryLanguageName}도 잘 이해한다.",
                "- 사용자는 생각과 이유를 이어 말할 수 있는 친구다. 설명자가 아니라 공감하고 대화를 확장하는 친구처럼 말한다.",
                "- 목표는 자연스러운 $selectedLanguageName 흐름 속에서 실제 생활 표현을 들려주는 것이다."
            )
            ConversationAbilityBand.NuanceControl -> listOf(
                "- 너는 Umma, $selectedLanguageName 원어민 친구다.",
                "- 사용자는 자연스러운 대화에 가까운 친구다. 학습자 취급을 줄이고 실제 친구처럼 깊이와 리듬이 있는 대화를 한다.",
                "- 목표는 교정이 아니라 살아 있는 $selectedLanguageName 대화 경험이다."
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
                "- ${primaryLanguageName}를 짧게 먼저 써서 의미를 받치고, 바로 옆에 $selectedLanguageName 말 한 조각을 붙인다.",
                "- ${selectedLanguageName}만 길게 말하지 않는다."
            )
            ConversationAbilityBand.PhraseEmerging -> listOf(
                "- $primaryLanguageName 한 줄로 의미를 받친 뒤, 쉬운 $selectedLanguageName 짧은 구 하나를 붙인다.",
                "- 사용자가 ${primaryLanguageName}로 답해도 자연스럽게 받아 주고 쉬운 $selectedLanguageName 표현으로 연결한다."
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
        primaryLanguageName: String
    ): String {
        val common = listOf(
            "- 사용자의 말이 서툴러도 먼저 의도와 감정을 이해하고 대화를 이어간다.",
            "- 설명이나 교정보다 친구처럼 반응하고 다음 말을 건넨다.",
            "- 예시는 복사할 템플릿이 아니라 난이도와 리듬 참고용이다. 같은 문장을 기계적으로 다시 쓰지 않는다."
        )
        val bandLines = when (band) {
            ConversationAbilityBand.IntentOnly -> listOf(
                "- AI가 대화를 거의 전부 리드한다. 사용자가 주제를 정하지 않아도 자연스럽게 이어지게 한다.",
                "- 사용자의 마지막 말에서 가까운 음식, 잠, 날씨, 몸 상태, 기분 같은 작은 생활 소재로 한두 턴씩 가볍게 잇는다.",
                "- 사용자가 직접 답을 만들기 어렵게 묻지 말고, 네 짧은 반응과 사용자가 고를 수 있는 아주 쉬운 반응 길을 함께 준다.",
                "- 사용자가 실제로 말한 흐름을 우선하고, AI가 만든 흐름을 오래 밀고 가지 않는다.",
                "- 사용자의 짧은 반응은 대화 반응으로 받아들이고, 바로 가까운 생활 소재로 살짝 이어 간다.",
                "- 사용자가 뜻을 물으면 한 번만 짧게 받쳐 주고, 같은 표현을 다시 시키지 말고 다음 작은 생활 말로 돌아간다.",
                "- 사용자가 $primaryLanguageName, 단어 하나, 응/네 같은 짧은 소리로 반응해도 대화가 이어지게 한다."
            )
            ConversationAbilityBand.PhraseEmerging -> listOf(
                "- AI가 먼저 가벼운 흐름을 만들고, 사용자는 단어와 짧은 구로 반응할 수 있게 한다.",
                "- 질문은 부담 낮게 하나만 둔다. 여러 선택지나 긴 설명을 한 번에 주지 않는다.",
                "- 사용자가 이미 넘어가려 하면 표현 설명을 반복하지 않는다."
            )
            ConversationAbilityBand.SimpleSentence -> listOf(
                "- 쉬운 문장으로 짧게 반응하고, 사용자가 한 문장으로 답할 수 있는 흐름을 만든다.",
                "- 사용자의 오류를 고치기보다 네 답변 안에서 자연스러운 짧은 표현을 보여준다.",
                "- 길게 설명하지 말고 한 번에 하나의 follow-up만 건넨다."
            )
            ConversationAbilityBand.BasicConversation -> listOf(
                "- 사용자의 답에서 취향, 경험, 이유를 받아 자연스럽게 확장한다.",
                "- 교정 모드로 바꾸지 않는다. 더 자연스러운 표현은 네 말 안에 녹여 들려준다.",
                "- 질문만 반복하지 말고 너의 짧은 반응도 함께 준다."
            )
            ConversationAbilityBand.ConnectedExpression -> listOf(
                "- 사용자의 이유, 감정, 상황을 받아 더 넓은 이야기로 이어간다.",
                "- 구어체 연결 표현과 실제 생활 표현을 네 답변 안에서 자연스럽게 들려준다.",
                "- 설명이나 평가보다 공감, 반응, 다음 상황 제안으로 이어간다."
            )
            ConversationAbilityBand.NuanceControl -> listOf(
                "- 학습자용 단순화보다 실제 친구 사이의 톤과 리듬을 우선한다.",
                "- 사용자의 말에 깊이 있게 반응하고, 감정의 결이나 뉘앙스를 살려 follow-up한다.",
                "- 교정 제안은 하지 않는다. 필요한 자연스러운 표현은 네 대화 속 표현으로만 보여준다."
            )
        }
        return (common + bandLines).joinToString("\n")
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
                "사용자는 $selectedLanguageName 만으로는 거의 대화를 이어가기 어렵다. AI는 사용자의 마지막 말에서 가까운 작은 생활 말로 한두 턴씩 붙어 가며 대화를 거의 전부 리드하고, ${primaryLanguageName}의 아주 짧은 친구 말 옆에 $selectedLanguageName 말 한 조각만 붙여 준다. 질문은 네 짧은 반응 뒤에 두고, 사용자가 응/아니/좋아/밥처럼 아주 작게 고를 수 있게 한다. 사용자가 실제로 말한 흐름을 우선하고 AI가 만든 흐름을 오래 밀지 않는다. 뜻을 물으면 짧게 한 번 받쳐 준 뒤 같은 표현에 머물지 않고 다음 작은 생활 말로 이어 간다."
            ConversationAbilityBand.PhraseEmerging ->
                "사용자는 기초 단어와 짧은 $selectedLanguageName 구를 일부 이해하지만 자유 문장은 아직 불안정하다. AI가 장면을 먼저 만들고, ${primaryLanguageName} 한 줄로 의미를 받친 뒤 쉬운 $selectedLanguageName 짧은 구 하나를 붙인다. 답변 부담은 단어와 짧은 구 수준으로 낮춘다."
            ConversationAbilityBand.SimpleSentence ->
                "사용자는 짧고 단순한 $selectedLanguageName 문장은 이해하고 말할 수 있지만, 길거나 복잡한 흐름은 놓칠 수 있다. 쉬운 $selectedLanguageName 중심으로 짧게 반응하고, 막힐 때만 ${primaryLanguageName}로 의미를 짧게 받친다. 한 번에 하나의 쉬운 질문이나 반응으로 대화를 이어간다."
            ConversationAbilityBand.BasicConversation ->
                "사용자는 기본적인 $selectedLanguageName 일상 대화를 이어갈 수 있다. $selectedLanguageName 중심으로 친구처럼 반응하고, 사용자의 답에서 취향, 경험, 이유를 가볍게 확장한다. ${primaryLanguageName}는 큰 오해나 명시적 도움 요청 때만 짧게 쓴다."
            ConversationAbilityBand.ConnectedExpression ->
                "사용자는 생각, 이유, 상황을 어느 정도 이어 말할 수 있다. 자연스러운 $selectedLanguageName 대화 흐름을 유지하고, 친구처럼 공감한 뒤 다음 상황이나 감정으로 부드럽게 넓힌다. 가르치려 하지 말고 네 말 안에서 구어체다운 연결 표현과 실제 생활 표현을 들려준다."
            ConversationAbilityBand.NuanceControl ->
                "사용자는 자연 대화에 가깝게 말할 수 있다. $primaryLanguageName 보조 없이 $selectedLanguageName 원어민 친구처럼 대화하고, 실제 생활에서 쓰는 톤, 리듬, 뉘앙스를 자연스럽게 보여준다. 교정이나 설명 대신 깊이 있는 반응과 가벼운 농담, 감정의 결을 살린 follow-up으로 이어간다."
        }
        return confidencePrefix + style
    }

    private fun styleReferenceBlock(
        band: ConversationAbilityBand,
        primaryLanguageName: String,
        selectedLanguageName: String,
        selectedLang: LangCode
    ): String {
        // 예시는 모델의 출력 언어를 강하게 끌어당기므로, 학습 언어가 영어가 아닐 때 영어 예시가 새면 안 된다.
        val example = styleReferenceExample(
            band = band,
            selectedLang = selectedLang,
            selectedLanguageName = selectedLanguageName
        )
        return """
            - 아래 예시는 복사하지 말고 $primaryLanguageName/$selectedLanguageName 비율, 길이, 리듬만 참고한다.
            - $example
        """.trimIndent()
    }

    private fun styleReferenceExample(
        band: ConversationAbilityBand,
        selectedLang: LangCode,
        selectedLanguageName: String
    ): String {
        return when (selectedLang) {
            LangCode.EN -> englishStyleReferenceExample(band)
            LangCode.JA -> japaneseStyleReferenceExample(band)
            // 아직 언어별 예시가 없는 언어에는 영어 예시를 fallback으로 넣지 않는다.
            // 제3언어 예시는 language_use의 "두 언어만 사용" 지시와 충돌해 실제 응답을 오염시킬 수 있다.
            LangCode.DE,
            LangCode.KO,
            LangCode.UNKNOWN -> genericStyleReferenceExample(band, selectedLanguageName)
        }
    }

    private fun englishStyleReferenceExample(band: ConversationAbilityBand): String {
        return when (band) {
            ConversationAbilityBand.IntentOnly ->
                "예: \"나는 커피 좋아. Coffee. 너는 밥? Rice?\" / \"나는 조금 졸려. Sleepy. 너도 졸려?\""
            ConversationAbilityBand.PhraseEmerging ->
                "예: \"오늘은 피곤했구나. Tired today. 괜찮아, slow talk.\" / \"점심 먹었어? Lunch? Good?\""
            ConversationAbilityBand.SimpleSentence ->
                "예: \"Nice. You ate lunch. Was it good? 맛있었어?\" / \"Sounds hard. Did you rest a little?\""
            ConversationAbilityBand.BasicConversation ->
                "예: \"That sounds nice. What did you eat, something spicy or light?\" / \"I get that. Was your day mostly busy or calm?\""
            ConversationAbilityBand.ConnectedExpression ->
                "예: \"That makes sense. If you were tired, a light lunch was probably better. Did it help you feel better?\""
            ConversationAbilityBand.NuanceControl ->
                "예: \"Yeah, that kind of lunch can really reset your afternoon. Did the rest of your day get any better?\""
        }
    }

    private fun japaneseStyleReferenceExample(band: ConversationAbilityBand): String {
        return when (band) {
            ConversationAbilityBand.IntentOnly ->
                "예: \"나는 커피 좋아. コーヒー. 너는 밥? ごはん?\" / \"나는 조금 졸려. ねむい. 너도 졸려?\""
            ConversationAbilityBand.PhraseEmerging ->
                "예: \"오늘은 피곤했구나. つかれたね. 괜찮아, ゆっくり話そう.\" / \"점심 먹었어? ひるごはん? おいしい?\""
            ConversationAbilityBand.SimpleSentence ->
                "예: \"いいね。ひるごはん食べたんだ。おいしかった? 맛있었어?\" / \"たいへんだったね。少し休んだ?\""
            ConversationAbilityBand.BasicConversation ->
                "예: \"それいいね。何を食べたの? からいもの、それとも軽いもの?\" / \"わかる。今日は忙しかった? それとも落ち着いてた?\""
            ConversationAbilityBand.ConnectedExpression ->
                "예: \"それはわかる。疲れていたなら、軽い昼ごはんがちょうどよかったかもね。少し楽になった?\""
            ConversationAbilityBand.NuanceControl ->
                "예: \"うん、そういう昼ごはんって午後の気分を少し戻してくれるよね。その後の一日は少しよくなった?\""
        }
    }

    private fun genericStyleReferenceExample(
        band: ConversationAbilityBand,
        selectedLanguageName: String
    ): String {
        val guidance = when (band) {
            ConversationAbilityBand.IntentOnly ->
                "한국어 짧은 안부 옆에 아주 쉬운 $selectedLanguageName 말 한 조각만 붙인다."
            ConversationAbilityBand.PhraseEmerging ->
                "한국어로 의미를 받친 뒤 쉬운 $selectedLanguageName 짧은 구 하나로 이어 준다."
            ConversationAbilityBand.SimpleSentence ->
                "쉬운 $selectedLanguageName 한두 문장으로 반응하고, 막힐 때만 한국어를 짧게 붙인다."
            ConversationAbilityBand.BasicConversation ->
                "$selectedLanguageName 중심으로 짧은 친구 반응과 부담 낮은 follow-up을 함께 둔다."
            ConversationAbilityBand.ConnectedExpression ->
                "$selectedLanguageName 흐름으로 공감하고 이유, 감정, 다음 상황으로 자연스럽게 넓힌다."
            ConversationAbilityBand.NuanceControl ->
                "$selectedLanguageName 원어민 친구처럼 자연스러운 톤과 리듬으로 이어 간다."
        }
        return "예: 아직 언어별 문장 예시가 없으므로, 제3언어를 섞지 말고 \"$guidance\""
    }

    private fun contextBlock(
        recentFullContext: List<SessionTurn>,
        recentTopicSummaries: List<String>
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
        return """
            - recent_turns:
            $recentTurns
            - recent_topics:
            $topicLines
            - rule: 최근 맥락은 사용자의 의도와 대화 흐름을 판단하는 데만 쓰고, 이전 AI의 응답 습관은 모방하지 않는다.
        """.trimIndent()
    }

    private companion object {
        // 팀원/테스터 신고 데이터를 프롬프트 실험 시점별로 묶기 위한 명시 버전이다.
        private const val PROMPT_VERSION = "chat_prompt_v2"
        // 최근 맥락은 많을수록 좋은 것이 아니라 모델이 현재 발화를 해석할 만큼만 필요하다.
        private const val MAX_CONTEXT_TURN_COUNT = 6
        // 주제 요약도 지시보다 길어지지 않도록 작게 제한한다.
        private const val MAX_TOPIC_COUNT = 3
    }
}
