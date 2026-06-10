package com.app.umma.data.model.learningstate

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.UserLangPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UserLangPrefDto] ↔ [UserLangPref] 직렬화/복원 round-trip 테스트.
 *
 * onboardingGuideStages 필드의 정상 복원, 미인식 키/값 drop, 누락 시 빈 맵 처리를 검증한다.
 */
class UserLangPrefDtoTest {

    // ── toDto → toDomain round-trip ─────────────────────────────────────────────

    @Test
    fun `round-trip preserves onboardingGuideStages`() {
        val stages = mapOf(
            LangCode.EN to OnboardingGuideStage.CORRECTION,
            LangCode.JA to OnboardingGuideStage.DONE,
        )
        val original = UserLangPref(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN,
            learningLangs = listOf(LangCode.EN, LangCode.JA),
            onboardingGuideStages = stages,
            schema = UserLangPref.SCHEMA,
        )

        val restored = original.toDto().toDomain()

        assertEquals(stages, restored.onboardingGuideStages)
    }

    @Test
    fun `round-trip with empty onboardingGuideStages returns empty map`() {
        val original = UserLangPref(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN,
            learningLangs = listOf(LangCode.EN),
            onboardingGuideStages = emptyMap(),
            schema = UserLangPref.SCHEMA,
        )

        val restored = original.toDto().toDomain()

        assertTrue(restored.onboardingGuideStages.isEmpty())
    }

    // ── toDomain 복원 방어 케이스 ────────────────────────────────────────────────

    @Test
    fun `unknown stage name is dropped during toDomain`() {
        val dto = UserLangPrefDto(
            primaryLanguage = "ko",
            selectedLearningLanguage = "en",
            learningLanguages = listOf("en"),
            // "UNKNOWN_STAGE" 는 OnboardingGuideStage 에 없으므로 drop.
            onboardingGuideStages = mapOf("en" to "UNKNOWN_STAGE"),
            schemaVersion = 2,
        )

        val domain = dto.toDomain()

        assertTrue(domain.onboardingGuideStages.isEmpty())
    }

    @Test
    fun `unknown lang code is dropped during toDomain`() {
        val dto = UserLangPrefDto(
            primaryLanguage = "ko",
            selectedLearningLanguage = "en",
            learningLanguages = listOf("en"),
            // "xx" 는 LangCode 에 없으므로 drop.
            onboardingGuideStages = mapOf("xx" to OnboardingGuideStage.STUDY.name),
            schemaVersion = 2,
        )

        val domain = dto.toDomain()

        assertTrue(domain.onboardingGuideStages.isEmpty())
    }

    @Test
    fun `missing onboardingGuideStages field defaults to empty map (구버전 저장값 호환)`() {
        // 기존 schema v1 DTO 에는 onboardingGuideStages 가 없으므로 기본값(emptyMap()) 으로 초기화.
        val dto = UserLangPrefDto(
            primaryLanguage = "ko",
            selectedLearningLanguage = "en",
            learningLanguages = listOf("en"),
            onboardingGuideStages = emptyMap(), // 직렬화에서 누락된 경우와 동치
            schemaVersion = 1,
        )

        val domain = dto.toDomain()

        assertTrue(domain.onboardingGuideStages.isEmpty())
    }

    @Test
    fun `multiple stages are all restored correctly`() {
        val dto = UserLangPrefDto(
            primaryLanguage = "ko",
            selectedLearningLanguage = "en",
            learningLanguages = listOf("en", "ja", "de"),
            onboardingGuideStages = mapOf(
                "en" to OnboardingGuideStage.DONE.name,
                "ja" to OnboardingGuideStage.STUDY.name,
                "de" to OnboardingGuideStage.CONVERSATION.name,
            ),
            schemaVersion = 2,
        )

        val domain = dto.toDomain()

        assertEquals(OnboardingGuideStage.DONE, domain.onboardingGuideStages[LangCode.EN])
        assertEquals(OnboardingGuideStage.STUDY, domain.onboardingGuideStages[LangCode.JA])
        assertEquals(OnboardingGuideStage.CONVERSATION, domain.onboardingGuideStages[LangCode.DE])
    }
}
