package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatTurnContextSignal
import com.app.umma.domain.model.chat.LatestUserTurnRole
import com.app.umma.domain.model.learningstate.ChatTurnAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.PrimaryBridgeReason
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SentenceDensityPolicy
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import javax.inject.Inject

/**
 * USER final transcript를 이번 AI 응답에만 적용할 임시 보정 정책으로 변환한다.
 *
 * 이 UseCase는 장기 `LangState`를 갱신하지 않는다.
 * 저장된 profile은 세션 기본값이고, 이 정책은 방금 발화가 조각났거나 막힌 경우에만
 * response override와 speed 보정으로 짧게 쓰이는 세션 내 신호다.
 */
class BuildChatTurnAdaptationPolicyUseCase @Inject constructor() {
    operator fun invoke(
        transcript: String,
        contextSignal: ChatTurnContextSignal = ChatTurnContextSignal.Neutral,
        profile: LearnerAdaptationProfile,
        primaryLang: LangCode,
        selectedLang: LangCode
    ): ChatTurnAdaptationPolicy {
        // Realtime transcription 결과는 완벽한 실력 측정값이 아니므로 빈 문자열이면 기본 profile 정책만 사용한다.
        val normalized = transcript.trim()
        if (normalized.isBlank()) return buildBasePolicy(profile)

        // 토큰은 언어별 정확한 형태소 분석이 아니라 "말이 조각났는지"만 보기 위한 가벼운 신호다.
        val tokens = normalized
            .split(WHITESPACE_REGEX)
            .filter { it.isNotBlank() }

        // 첫 세션/저신뢰 profile에서는 현재 발화가 보여준 막힘 신호를 더 적극적으로 반영한다.
        val lowConfidence = profile.core.levelConfidence == ProfileConfidence.Low
        val beginnerAutoSupport = profile.chatPolicy.conversationBand == ConversationAbilityBand.IntentOnly ||
            profile.chatPolicy.conversationBand == ConversationAbilityBand.PhraseEmerging

        // 사용자가 직접 모른다고 말하면 응답 부담은 낮추되, 기준언어 혼합은 별도 조건에서만 연다.
        val explicitBlock = containsBlockingPhrase(normalized)
        // "한국어를 섞어줘", "explain in English" 같은 요청은 실력 평가가 아니라 이번 응답 방식에 대한 직접 요구다.
        val explicitPrimarySupportRequest = containsPrimarySupportRequest(
            text = normalized,
            primaryLang = primaryLang
        )

        // 1~3개 토큰 또는 구두점 없는 짧은 조각은 문장 생성이 아니라 의도 복원이 먼저 필요한 신호다.
        val fragmentLike = tokens.size <= FRAGMENT_TOKEN_LIMIT || normalized.length <= FRAGMENT_CHAR_LIMIT

        // primaryLang 문자가 많이 섞이면 사용자가 target 문장을 만들기 전에 의도 확인이 필요할 가능성이 높다.
        val primaryDominant = isPrimaryDominant(
            text = normalized,
            primaryLang = primaryLang,
            selectedLang = selectedLang
        )

        // Fluent에 가까운 긴 발화는 첫 세션이어도 초저숙련으로 고정하면 안 되므로 과한 보조를 닫는다.
        val fluentLike = tokens.size >= FLUENT_TOKEN_THRESHOLD &&
            !explicitBlock &&
            !primaryDominant

        return when {
            // 명시적 막힘은 가장 강한 부담 완화 신호다. 다만 기준언어 혼합은 초보/명시 요청/기준언어 우세에서만 연다.
            explicitBlock -> ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.OneShortSentence,
                sentenceDensity = SentenceDensityPolicy.OneIdea,
                primaryBridge = if (explicitPrimarySupportRequest || primaryDominant || beginnerAutoSupport) {
                    PrimaryBridgePolicy.Active
                } else {
                    PrimaryBridgePolicy.Brief
                },
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                speechSpeed = SpeechSpeedPolicy.SlowBeginner,
                primaryBridgeReason = when {
                    explicitPrimarySupportRequest -> PrimaryBridgeReason.ExplicitSupportRequest
                    primaryDominant -> PrimaryBridgeReason.PrimaryDominantTurn
                    beginnerAutoSupport -> PrimaryBridgeReason.BeginnerAutoSupport
                    else -> PrimaryBridgeReason.ProfileDefault
                }
            )

            // 사용자가 기준언어 보조를 직접 요청하면 baseline이 이미 Active여도 이번 응답에 그 요청을 반영해야 한다.
            explicitPrimarySupportRequest -> ChatTurnAdaptationPolicy(
                responseLength = if (lowConfidence) {
                    ResponseLengthPolicy.OneShortSentence
                } else {
                    ResponseLengthPolicy.ShortTwoStep
                },
                sentenceDensity = if (lowConfidence) {
                    SentenceDensityPolicy.OneIdea
                } else {
                    SentenceDensityPolicy.SimpleTwoStep
                },
                primaryBridge = PrimaryBridgePolicy.Active,
                questionLoad = if (lowConfidence) {
                    QuestionLoadPolicy.ConcreteChoice
                } else {
                    QuestionLoadPolicy.OneConcreteFollowUp
                },
                speechSpeed = if (lowConfidence) {
                    SpeechSpeedPolicy.SlowBeginner
                } else {
                    SpeechSpeedPolicy.Guided
                },
                primaryBridgeReason = PrimaryBridgeReason.ExplicitSupportRequest
            )

            // 사용자가 기준언어를 많이 섞어 답한 상태는 명시 요청이 없어도 "이해 보조가 필요하다"는 강한 turn 신호다.
            // 직전 질문의 답변이어도 목표언어만 자연스럽게 이어가기보다, 기준언어로 먼저 받아 주고 최소 목표언어 표현만 붙인다.
            primaryDominant -> ChatTurnAdaptationPolicy(
                responseLength = if (lowConfidence) {
                    ResponseLengthPolicy.OneShortSentence
                } else {
                    ResponseLengthPolicy.ShortTwoStep
                },
                sentenceDensity = if (lowConfidence) {
                    SentenceDensityPolicy.OneIdea
                } else {
                    SentenceDensityPolicy.SimpleTwoStep
                },
                primaryBridge = PrimaryBridgePolicy.Active,
                questionLoad = if (lowConfidence) {
                    QuestionLoadPolicy.ConcreteChoice
                } else {
                    QuestionLoadPolicy.OneConcreteFollowUp
                },
                speechSpeed = if (lowConfidence) {
                    SpeechSpeedPolicy.SlowBeginner
                } else {
                    SpeechSpeedPolicy.Guided
                },
                primaryBridgeReason = PrimaryBridgeReason.PrimaryDominantTurn
            )

            // 짧더라도 최근 맥락 안에서 정상 진행 중이면 같은 초보 보정을 반복하지 않는다.
            contextSignal.latestUserTurnRole == LatestUserTurnRole.ProgressingInContext && fragmentLike -> ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.ShortTwoStep,
                sentenceDensity = SentenceDensityPolicy.SimpleTwoStep,
                primaryBridge = PrimaryBridgePolicy.Brief,
                questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
                speechSpeed = SpeechSpeedPolicy.Guided
            )

            // 1~2단계 초보 profile에서도 맥락상 정상 진행이 아니면 짧은 조각 발화에 기준언어 보조를 강하게 적용한다.
            beginnerAutoSupport &&
                (fragmentLike || contextSignal.latestUserTurnRole == LatestUserTurnRole.StuckOrFragment) -> ChatTurnAdaptationPolicy(
                    responseLength = ResponseLengthPolicy.OneShortSentence,
                    sentenceDensity = SentenceDensityPolicy.OneIdea,
                    primaryBridge = PrimaryBridgePolicy.Active,
                    questionLoad = QuestionLoadPolicy.ConcreteChoice,
                    speechSpeed = SpeechSpeedPolicy.SlowBeginner,
                    primaryBridgeReason = PrimaryBridgeReason.BeginnerAutoSupport
                )

            // 저신뢰 profile에서 조각난 발화는 첫 세션 fallback의 핵심 케이스다.
            lowConfidence && fragmentLike -> ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.OneShortSentence,
                sentenceDensity = SentenceDensityPolicy.OneIdea,
                primaryBridge = PrimaryBridgePolicy.Active,
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                speechSpeed = SpeechSpeedPolicy.SlowBeginner
            )

            // 저장된 profile이 있어도 현재 발화가 짧게 무너진 경우에는 이번 응답만 한 단계 쉽게 낮춘다.
            fragmentLike -> ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.ShortTwoStep,
                sentenceDensity = SentenceDensityPolicy.SimpleTwoStep,
                primaryBridge = PrimaryBridgePolicy.Brief,
                questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
                speechSpeed = SpeechSpeedPolicy.Guided
            )

            // 첫 세션이어도 충분히 말하는 사용자는 초저숙련 fallback에 갇히지 않게 자연 대화에 가깝게 연다.
            fluentLike && lowConfidence -> ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.NaturalBrief,
                sentenceDensity = SentenceDensityPolicy.NaturalBrief,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                questionLoad = QuestionLoadPolicy.OpenShort,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            )

            // 저장 근거가 있고 충분히 말하는 사용자는 장기 profile의 기본 대화 정책을 유지한다.
            fluentLike -> buildBasePolicy(profile)

            // 그 외에는 장기 profile을 기본으로 두되, low confidence면 질문과 속도를 과하게 올리지 않는다.
            lowConfidence -> ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.ShortTwoStep,
                sentenceDensity = SentenceDensityPolicy.SimpleTwoStep,
                primaryBridge = PrimaryBridgePolicy.Brief,
                questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
                speechSpeed = SpeechSpeedPolicy.Guided
            )

            // 고신뢰/중신뢰 상태에서 특별한 막힘이 없으면 세션 시작 profile의 정책을 유지한다.
            else -> buildBasePolicy(profile)
        }
    }

    fun buildBasePolicy(profile: LearnerAdaptationProfile): ChatTurnAdaptationPolicy {
        // 장기 profile 정책을 turn 정책 형태로 옮겨, override가 없어도 같은 계산 경로를 쓸 수 있게 한다.
        // Start/Retry 경로도 이 값을 baseline으로 사용해 같은 지시가 매 turn 반복 주입되지 않게 한다.
        return ChatTurnAdaptationPolicy(
            responseLength = profile.chatPolicy.responseLength,
            sentenceDensity = densityFor(profile.chatPolicy.responseLength),
            primaryBridge = profile.chatPolicy.primaryBridge,
            questionLoad = profile.chatPolicy.questionLoad,
            speechSpeed = profile.chatPolicy.speechSpeed
        )
    }

    private fun densityFor(responseLength: ResponseLengthPolicy): SentenceDensityPolicy {
        // 응답 길이와 정보 밀도는 완전히 같지 않지만, 기본값은 길이 정책에서 안전하게 유도한다.
        return when (responseLength) {
            ResponseLengthPolicy.OneShortSentence -> SentenceDensityPolicy.OneIdea
            ResponseLengthPolicy.ShortTwoStep -> SentenceDensityPolicy.SimpleTwoStep
            ResponseLengthPolicy.NaturalBrief -> SentenceDensityPolicy.NaturalBrief
            ResponseLengthPolicy.Flexible -> SentenceDensityPolicy.Flexible
        }
    }

    private fun isPrimaryDominant(
        text: String,
        primaryLang: LangCode,
        selectedLang: LangCode
    ): Boolean {
        // 같은 언어 조합이면 primary/selected 혼합을 판단할 수 없으므로 기준언어 우세로 보지 않는다.
        if (primaryLang == selectedLang) return false

        // MVP 대상인 KO/EN/JA는 문자권 차이가 커서 script 비율만으로도 가벼운 막힘 신호를 얻을 수 있다.
        val primaryCount = scriptCount(text, primaryLang)
        val selectedCount = scriptCount(text, selectedLang)

        // primary 문자가 충분히 많고 selected 문자가 적으면 target 문장을 만들기 전 의도 확인이 필요하다.
        return primaryCount >= PRIMARY_DOMINANT_MIN_COUNT &&
            primaryCount > selectedCount * PRIMARY_DOMINANT_RATIO
    }

    private fun scriptCount(text: String, langCode: LangCode): Int {
        // 정교한 언어 판별은 AI/ML 영역이지만 turn override는 지연을 줄여야 하므로 유니코드 범위만 사용한다.
        return text.count { char ->
            when (langCode) {
                LangCode.KO -> char in '\uAC00'..'\uD7A3' || char in '\u3131'..'\u318E'
                LangCode.JA -> char in '\u3040'..'\u30FF' || char in '\u4E00'..'\u9FFF'
                LangCode.EN -> char in 'A'..'Z' || char in 'a'..'z'
                LangCode.DE -> char in 'A'..'Z' || char in 'a'..'z' || char in GERMAN_EXTRA_CHARS
                LangCode.UNKNOWN -> false
            }
        }
    }

    private fun containsBlockingPhrase(text: String): Boolean {
        // 막힘 표현은 대소문자 차이로 놓치면 안 되므로 소문자 비교로 통일한다.
        val lowerText = text.lowercase()
        return BLOCKING_PHRASES.any { lowerText.contains(it) }
    }

    private fun containsPrimarySupportRequest(
        text: String,
        primaryLang: LangCode
    ): Boolean {
        // "한국어"만 말했다고 항상 보조 요청은 아니므로 현재 primaryLang 단서와 요청 동사를 함께 본다.
        val lowerText = text.lowercase()
        val hasSupportLanguageWord = primarySupportLanguageWords(primaryLang).any { lowerText.contains(it) }
        val hasSupportActionWord = PRIMARY_SUPPORT_ACTION_WORDS.any { lowerText.contains(it) }
        return hasSupportLanguageWord && hasSupportActionWord
    }

    private fun primarySupportLanguageWords(primaryLang: LangCode): List<String> {
        // primaryLang은 고정 한국어가 아니라 사용자의 기준 언어이므로, 명시 요청 감지도 설정값을 따라야 한다.
        return when (primaryLang) {
            LangCode.KO -> listOf("한글", "한국어", "우리말", "기준언어", "korean")
            LangCode.EN -> listOf("영어", "english", "기준언어")
            LangCode.JA -> listOf("일본어", "日本語", "にほんご", "japanese", "nihongo", "기준언어")
            LangCode.DE -> listOf("독일어", "deutsch", "german", "기준언어")
            LangCode.UNKNOWN -> listOf("기준언어")
        }
    }

    private companion object {
        // 공백 분리만으로도 "단어 조각"과 "문장 발화"의 대략적인 차이는 충분히 구분된다.
        private val WHITESPACE_REGEX = Regex("\\s+")
        // 짧은 단어 나열은 문법 교정보다 의도 복원과 쉬운 질문이 먼저 필요하다.
        private const val FRAGMENT_TOKEN_LIMIT = 3
        // 일본어/한국어처럼 공백이 적은 언어도 짧은 조각을 잡기 위한 문자 길이 기준이다.
        private const val FRAGMENT_CHAR_LIMIT = 12
        // 이 정도 길이의 발화는 첫 세션이어도 초저숙련으로 고정하지 않는다.
        private const val FLUENT_TOKEN_THRESHOLD = 8
        // primary script가 한두 글자 섞인 정도는 우세 판단에서 제외한다.
        private const val PRIMARY_DOMINANT_MIN_COUNT = 3
        // selected script보다 primary script가 확실히 많을 때만 기준언어 우세로 본다.
        private const val PRIMARY_DOMINANT_RATIO = 2
        // 독일어의 추가 라틴 문자는 영어와 구분하기 어렵지만 최소한 독일어 글자는 누락하지 않는다.
        private const val GERMAN_EXTRA_CHARS = "ÄÖÜäöüß"
        // 사용자가 대화 단절을 직접 표현하는 대표 문구들이다. prompt가 아니라 로컬 분류에만 사용한다.
        private val BLOCKING_PHRASES = listOf(
            "모르",
            "못해",
            "못 하",
            "이해 안",
            "몰라",
            "don't know",
            "dont know",
            "no understand",
            "not understand",
            "can't speak",
            "cant speak"
        )
        // 언어 단서와 함께 나와야 실제 보조 요청으로 본다. 단어 하나만으로 과잉 반응하지 않기 위함이다.
        private val PRIMARY_SUPPORT_ACTION_WORDS = listOf(
            "섞",
            "같이",
            "함께",
            "설명",
            "말해",
            "써",
            "해줘",
            "mix",
            "explain",
            "speak",
            "use",
            "in "
        )
    }
}
