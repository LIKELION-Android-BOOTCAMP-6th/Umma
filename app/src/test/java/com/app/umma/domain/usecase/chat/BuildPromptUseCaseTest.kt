package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.CorrectionAdaptationPolicy
import com.app.umma.domain.model.learningstate.CorrectionStylePolicy
import com.app.umma.domain.model.learningstate.GrammarStrategyPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpokenRegisterStrategy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.learningstate.VocabularyStrategyPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPromptUseCaseTest {
    private val useCase = BuildPromptUseCase()

    @Test
    fun `prompt uses profile policy without raw metric names or numeric scores`() {
        // Profile 은 LangState 숫자를 이미 해석한 결과이므로 prompt builder 는 policy 만 읽어야 한다.
        val prompt = useCase(
            profile = profile(
                challengeLevel = ChallengeLevel.Stretch,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                questionStyle = QuestionStylePolicy.OpenFollowUp,
                primarySupport = PrimaryLanguageSupportPolicy.TargetLanguageFirstWithPrimaryFallback,
                primaryFocus = LearningFocusType.WordOrder
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        // selectedLang 은 AI 가 실제로 대화할 언어로 들어간다.
        assertTrue(prompt.contains("Speak primarily in English."))
        // primaryLang 은 설명 보조 언어로만 허용된다.
        assertTrue(prompt.contains("fall back to short Korean support"))
        // focus enum 은 그대로 노출하지 않고 사람이 읽을 수 있는 학습 표현으로 바뀐다.
        assertTrue(prompt.contains("word order"))
        // LangState 내부 필드명과 raw numeric 예시는 prompt 에 들어가면 안 된다.
        assertFalse(prompt.contains("grammarAccuracy"))
        assertFalse(prompt.contains("fluencyScore"))
        assertFalse(prompt.contains("naturalnessScore"))
        assertFalse(prompt.contains("0.42"))
    }

    @Test
    fun `target language only policy keeps primary language out of normal support`() {
        // 고신뢰 profile 은 selectedLang 중심 대화를 유지하고 primaryLang 보조를 기본으로 열지 않는다.
        val prompt = useCase(
            profile = profile(
                challengeLevel = ChallengeLevel.Refine,
                responseLength = ResponseLengthPolicy.Flexible,
                questionStyle = QuestionStylePolicy.NuanceFollowUp,
                primarySupport = PrimaryLanguageSupportPolicy.TargetLanguageOnly
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        // 대화 언어는 selectedLang 인 Japanese 로 고정된다.
        assertTrue(prompt.contains("Speak primarily in Japanese."))
        // primaryLang 은 사용자가 명시적으로 요청할 때만 허용된다는 제한으로 표현된다.
        assertTrue(prompt.contains("Do not use Korean support unless the learner explicitly asks for it."))
    }

    private fun profile(
        challengeLevel: ChallengeLevel,
        responseLength: ResponseLengthPolicy,
        questionStyle: QuestionStylePolicy,
        primarySupport: PrimaryLanguageSupportPolicy,
        primaryFocus: LearningFocusType? = null
    ): LearnerAdaptationProfile {
        // 테스트 profile 은 저장 모델이 아니라 prompt builder 입력 계약만 재현한다.
        return LearnerAdaptationProfile(
            core = LearnerAbilityProfile(
                cefrLevel = VocabLevel.B1,
                levelConfidence = ProfileConfidence.Medium,
                grammarStage = SkillStage.Stable,
                vocabularyStage = SkillStage.Stable,
                fluencyStage = SkillStage.Stable,
                naturalnessStage = SkillStage.Stable,
                focus = LearningFocusSummary(
                    primaryFocus = primaryFocus,
                    secondaryFocus = null,
                    confidence = if (primaryFocus == null) ProfileConfidence.Low else ProfileConfidence.Medium,
                    observedCount = if (primaryFocus == null) 0 else 2
                )
            ),
            chatPolicy = ChatAdaptationPolicy(
                challengeLevel = challengeLevel,
                responseLength = responseLength,
                questionStyle = questionStyle,
                primaryLanguageSupport = primarySupport
            ),
            correctionPolicy = CorrectionAdaptationPolicy(
                challengeLevel = challengeLevel,
                correctionStyle = CorrectionStylePolicy.ExplainOneReason,
                vocabularyStrategy = VocabularyStrategyPolicy.AddOneUsefulExpression,
                grammarStrategy = GrammarStrategyPolicy.FixOneMainPattern,
                spokenRegisterStrategy = SpokenRegisterStrategy.EverydaySpoken,
                primaryLanguageSupport = primarySupport
            )
        )
    }
}
