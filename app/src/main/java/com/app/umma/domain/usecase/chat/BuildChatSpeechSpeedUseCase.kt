package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import javax.inject.Inject

/**
 * 학습자 능력 profile을 Realtime 음성 출력 속도로 변환합니다.
 *
 * 속도는 저장 모델이 아니라 세션 출력 정책이므로 Chat domain에서 계산하고,
 * transport repository는 전달받은 숫자를 provider 설정에 반영하는 역할만 맡습니다.
 */
class BuildChatSpeechSpeedUseCase @Inject constructor() {
    operator fun invoke(
        profile: LearnerAdaptationProfile
    ): Double {
        // 신뢰도가 낮으면 실제 실력을 단정할 수 없지만, 첫 응답이 빠르면 초저숙련 사용자는 바로 이탈한다.
        // 그래서 첫 selectedLang 세션 fallback 은 "혹시 fluent일 수도 있음"보다 "못 알아들어도 대화가 끊기지 않음"을 우선한다.
        if (profile.core.levelConfidence == ProfileConfidence.Low) {
            // CHAT-TUNE-007부터 Chat band는 chatEvidenceSummary로도 산출될 수 있다.
            // 이 경우 correction/core metric은 아직 비어 있어도 Chat 대화 근거는 존재하므로,
            // IntentOnly가 아닌 band에서는 Chat policy 속도를 우선해 prompt 난이도와 실제 음성 속도를 맞춘다.
            if (profile.chatPolicy.conversationBand != ConversationAbilityBand.IntentOnly) {
                return speedForPolicy(profile.chatPolicy.speechSpeed)
            }
            // 현재 구조에서는 USER transcript를 앱이 해석해 turn별 speed를 바꾸지 않는다.
            // 근거 부족 상태의 첫 세션은 안전 속도로 시작하고, 이후 적응은 모델의 대화 내용에 맡긴다.
            return FIRST_SESSION_SAFE_SPEED
        }

        return profileBasedSpeed(profile)
    }

    private fun profileBasedSpeed(profile: LearnerAdaptationProfile): Double {
        // 대화 이해 속도는 네 영역 중 가장 약한 영역에 맞춰야 끊기지 않는다.
        val weakestStage = listOf(
            profile.core.grammarStage,
            profile.core.vocabularyStage,
            profile.core.fluencyStage,
            profile.core.naturalnessStage
        ).minByOrNull { it.ordinal } ?: SkillStage.Foundation

        // stage는 사용자의 처리 부담을, speechSpeed는 Chat profile이 허용한 대화 속도를 나타낸다.
        val stageSpeed = speedForStage(weakestStage)
        val policySpeed = speedForPolicy(profile.chatPolicy.speechSpeed)

        // 두 기준 중 더 보수적인 값을 사용해야 고급 한 영역 때문에 전체 음성이 빨라지지 않는다.
        return minOf(stageSpeed, policySpeed).coerceIn(MIN_SAFE_SPEED, MAX_LEARNING_SPEED)
    }

    private fun speedForStage(stage: SkillStage): Double {
        // Foundation/Developing은 이해 가능한 속도가 우선이므로 실제 음성 속도를 낮춘다.
        return when (stage) {
            SkillStage.Foundation -> SLOW_BEGINNER_SPEED
            SkillStage.Developing -> GUIDED_SPEED
            SkillStage.Stable -> NORMAL_LEARNING_SPEED
            SkillStage.Expanding -> SLIGHTLY_FAST_SPEED
            SkillStage.Refined -> ADVANCED_SPEED
        }
    }

    private fun speedForPolicy(policy: SpeechSpeedPolicy): Double {
        // Chat 전용 band가 산출한 속도 정책을 provider가 받는 0.x~1.x 숫자로만 변환한다.
        return when (policy) {
            SpeechSpeedPolicy.SlowBeginner -> SLOW_BEGINNER_SPEED
            SpeechSpeedPolicy.Guided -> GUIDED_SPEED
            SpeechSpeedPolicy.NormalLearning -> NORMAL_LEARNING_SPEED
            SpeechSpeedPolicy.SlightlyFast -> SLIGHTLY_FAST_SPEED
            SpeechSpeedPolicy.Advanced -> ADVANCED_SPEED
        }
    }

    companion object {
        // Realtime speed는 후처리 속도라 너무 낮추면 부자연스럽고, 너무 높이면 학습자가 따라가기 어렵다.
        private const val MIN_SAFE_SPEED = 0.8
        private const val MAX_LEARNING_SPEED = 1.1
        // 첫 세션/근거 부족 상태는 사용자의 첫 발화가 아주 낮은 수준일 수 있어 provider 허용 하한을 사용한다.
        private const val FIRST_SESSION_SAFE_SPEED = 0.8
        // 저장 근거가 있는 초급자는 너무 느린 음성의 부자연스러움을 피하면서도 듣기 부담을 낮춘다.
        private const val SLOW_BEGINNER_SPEED = 0.85
        private const val GUIDED_SPEED = 0.92
        private const val NORMAL_LEARNING_SPEED = 1.0
        private const val SLIGHTLY_FAST_SPEED = 1.05
        private const val ADVANCED_SPEED = 1.1
    }
}
