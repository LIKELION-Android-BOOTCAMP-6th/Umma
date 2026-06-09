package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionContextTurn
import com.app.umma.domain.model.correction.CorrectionSessionContext
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MVP 프롬프트 회귀 테스트.
 *
 * Mapper 와 한 디렉토리에 있는 이유는, 프롬프트 schema 가 [CorrectionAiResponseMapper] 가 받는 DTO 와
 * 한 글자라도 어긋나면 happy path 가 통째로 깨지기 때문이다. 두 파일의 schema 일치를 이 테스트가 못박는다.
 *
 * COR-TUNE-01: 학습자 수준은 raw metric(CEFR/grammarAccuracy/naturalnessScore) 숫자가 아니라
 * 이미 해석된 [LearnerAdaptationProfile.correctionPolicy] 행동 지시로만 반영되어야 한다.
 */
class CorrectionPromptBuilderTest {

    private val builder = CorrectionPromptBuilder()
    private val profileUseCase = BuildLearnerAdaptationProfileUseCase()

    @Test
    fun `prompt includes every candidate id and source text`() {
        val candidates = listOf(
            CorrectionCandidate(
                id = "en-0-abc",
                lang = LangCode.EN,
                sourceTurnIndex = 0,
                sourceText = "i go school"
            ),
            CorrectionCandidate(
                id = "en-2-def",
                lang = LangCode.EN,
                sourceTurnIndex = 2,
                sourceText = "she don't like coffee",
                assistantContext = "We were talking about cafes."
            )
        )
        val input = inputOf(candidates = candidates)

        val prompt = builder.build(input)

        assertTrue("candidateId en-0-abc 누락", prompt.contains("en-0-abc"))
        assertTrue("candidateId en-2-def 누락", prompt.contains("en-2-def"))
        assertTrue("sourceText 누락", prompt.contains("i go school"))
        assertTrue("sourceText 누락", prompt.contains("she don't like coffee"))
        assertTrue("assistantContext 누락", prompt.contains("We were talking about cafes."))
    }

    @Test
    fun `prompt declares the exact JSON schema keys mapper expects`() {
        // 이 키 셋이 깨지면 CorrectionAiResponseMapper 의 @Serializable DTO 와 어긋나 happy path 가 무너진다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            )
        )

        val prompt = builder.build(input)

        listOf("suggestions", "candidateId", "nativeText", "afterText", "explanation").forEach { key ->
            assertTrue("schema key '$key' 누락", prompt.contains(key))
        }
    }

    @Test
    fun `prompt asks AI to keep at most 10 most impactful suggestions`() {
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue("최대 10개 지시 누락", prompt.contains("Return at most 10 suggestions"))
        assertTrue("영향도 우선 지시 누락", prompt.contains("keep the 10 most impactful"))
    }

    @Test
    fun `prompt enforces candidateId exact-copy contract with copy, no-invent, and skip rules`() {
        // COR-FIX-009: "unknown correction candidate id: ja-6-0-79967d5" 실패의 직접 원인은
        // AI 가 candidateId 를 새로 만들어(hallucinate) 돌려준 것으로 추정된다. 한 줄짜리 지시("COPY
        // EXACTLY ... Do not invent new ids.")만으로는 모델이 "비슷하게 변형해도 된다"고 오해할 여지가
        // 있어, 복사 의무 / 생성·추론·축약·해시·번역·재포맷 금지 / 불가능하면 skip 세 규칙으로 못 박는다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "ja-6-0-79967d5",
                    lang = LangCode.JA,
                    sourceTurnIndex = 6,
                    sourceText = "わたしは学校に行きました"
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue(
            "exact-copy 지시 누락",
            prompt.contains("COPY EXACTLY one of the candidateId values from the Candidates section above")
        )
        assertTrue(
            "생성/추론/축약/해시/번역/재포맷 금지 지시 누락",
            prompt.contains("Never create, infer, shorten, hash, translate, or reformat a candidateId")
        )
        assertTrue(
            "정확한 id 를 못 쓰면 skip 하라는 지시 누락",
            prompt.contains("If you cannot use an exact candidateId from the Candidates section, skip that candidate")
        )
    }

    @Test
    fun `prompt asks AI to report sourceLang at suggestion top level with unknown escape hatch`() {
        // COR-TUNE-011-FIX (Method B): detectedLang seam 폐기로 발화 원문 언어의 유일한 출처가
        // AI 교정 응답이 되었다. schema 의 sourceLang 키와 "원문 기준/확신 없으면 unknown" Rule 이
        // 둘 다 노출돼야 mapper 의 DTO·보수적 정규화 계약과 어긋나지 않는다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue("schema 에 sourceLang 키 누락", prompt.contains("\"sourceLang\""))
        assertTrue("sourceLang 이 원문(sourceText) 기준임을 알리는 Rule 누락", prompt.contains("sourceText"))
        assertTrue("sourceLang 이 afterText 기준이 아님을 알리는 Rule 누락", prompt.contains("not the corrected afterText"))
        assertTrue("확신 없으면 unknown 을 쓰라는 escape hatch 지시 누락", prompt.contains("\"unknown\""))
    }

    @Test
    fun `prompt adds filler cleanup guidance without changing source preservation contract`() {
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "I mean, you know, I was like really tired."
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue("filler cleanup 吏???꾨씫", prompt.contains("Filler and repetition cleanup"))
        assertTrue("afterText 媛꾧껐??/ flashcard 吏???꾨씫", prompt.contains("natural and concise for learning and flashcard use"))
        assertTrue("sourceText 蹂댁〈 怨꾩빟 ?꾨씫", prompt.contains("Do not rewrite, trim, sanitize, or remove filler from sourceText itself"))
        assertTrue("meaning-bearing expression 蹂댁〈 吏???꾨씫", prompt.contains("Do NOT remove an expression if it carries real meaning"))
    }

    @Test
    fun `prompt includes representative filler examples and edge-case preservation guidance`() {
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "Like, I was tired."
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue("English filler example ?꾨씫", prompt.contains("\"you know\""))
        assertTrue("English discourse-marker like example ?꾨씫", prompt.contains("discourse-marker \"like\""))
        assertTrue("Japanese filler example ?꾨씫", prompt.contains("\"なんか\""))
        assertTrue("Korean filler example ?꾨씫", prompt.contains("\"약간\""))
        assertTrue("I like coffee edge case ?꾨씫", prompt.contains("\"I like coffee.\""))
        assertTrue("Japanese referential その edge case ?꾨씫", prompt.contains("referential \"その\""))
        assertTrue("Korean negative 아니 edge case ?꾨씫", prompt.contains("negative \"아니\""))
    }

    @Test
    fun `prompt declares learningSignal schema keys and allowed enums`() {
        // COR-TUNE-02: 응답 schema 에 suggestion 당 learningSignal 중첩과 허용 enum/규칙이 노출되어야
        // mapper 가 받는 DTO/정규화 계약과 어긋나지 않는다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            lang = LangCode.EN
        )

        val prompt = builder.build(input)

        // learningSignal 중첩 키 노출.
        listOf(
            "learningSignal", "issueCategories", "languageFeatures", "featureKey",
            "improvementTypes", "editSpans", "register", "severity", "meaningPreserved", "confidence"
        ).forEach { key ->
            assertTrue("learningSignal schema key '$key' 누락", prompt.contains(key))
        }
        // 허용 enum 대표값 노출 (issue/improvement/register/severity).
        assertTrue("issueCategory enum 누락", prompt.contains("GrammarForm"))
        assertTrue("improvementType enum 누락", prompt.contains("GrammarFixed"))
        assertTrue("register enum 누락", prompt.contains("EverydaySpoken"))
        assertTrue("severity enum 누락", prompt.contains("MajorPattern"))
        // featureKey namespace 는 selectedLang(EN) 기준 대문자 prefix 예시를 보여줘야 한다.
        assertTrue("featureKey namespace 예시 누락", prompt.contains("EN.Tense"))
        // 배열 캡 ≤3 지시 노출.
        assertTrue("배열 캡(at most 3) 지시 누락", prompt.contains("at most 3"))
    }

    @Test
    fun `prompt does not leak raw learner metrics`() {
        // COR-TUNE-01: CEFR 라벨이나 grammarAccuracy/naturalnessScore 같은 "%.2f" 숫자가 프롬프트에 노출되면 안 된다.
        // LangState.initial 의 기본 vocabularyLevel(A1)도 더 이상 프롬프트에 들어가지 않는다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hello"
                )
            )
        )

        val prompt = builder.build(input)

        assertFalse("CEFR level(A1) 이 프롬프트에 노출됨", prompt.contains("A1"))
        assertFalse("CEFR 라벨이 프롬프트에 노출됨", prompt.contains("CEFR"))
        assertFalse("grammar accuracy raw 라벨 노출", prompt.contains("Grammar accuracy"))
        // COR-TUNE-02 이후 CorrectionSeverity.NaturalnessOnly enum 토큰이 schema 규칙에 등장하므로,
        // raw 메트릭 누수 검증은 실제 metric 라벨/필드명으로 특정한다("Naturalness" 단독 검사는 enum 과 충돌).
        assertFalse("naturalness raw 점수 라벨 노출", prompt.contains("Naturalness score"))
        assertFalse("naturalness raw 필드명 노출", prompt.contains("naturalnessScore"))
        assertFalse(
            "raw metric 숫자(%.2f 포맷)가 프롬프트에 노출됨",
            Regex("""\d\.\d{2}""").containsMatchIn(prompt)
        )
    }

    @Test
    fun `prompt omits assistantContext line when null or blank`() {
        // 빈 context 가 그대로 들어가면 토큰 낭비 + 모델 혼란.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hi",
                    assistantContext = null
                )
            )
        )

        val prompt = builder.build(input)

        assertFalse("null context 가 라벨로 노출됨", prompt.contains("assistantContext:"))
    }

    @Test
    fun `prompt forbids markdown fences so mapper json decode survives`() {
        // mapper 는 markdown fence 가 섞이면 json decode 단계에서 즉시 깨진다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hi"
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue("markdown 금지 지시 누락", prompt.contains("no markdown"))
    }

    @Test
    fun `nativeText rule uses primaryLang language name not hardcoded Korean`() {
        // primaryLang=KO 이면 "Korean"이 앞면 언어로 지정되어야 한다.
        val inputKo = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "hi")
            ),
            lang = LangCode.EN,
            primaryLang = LangCode.KO
        )
        val promptKo = builder.build(inputKo)
        assertTrue("nativeText 규칙에 Korean 누락", promptKo.contains("Korean"))
        assertTrue("nativeText 규칙에 ko 코드 누락", promptKo.contains("(ko)"))

        // primaryLang=EN, selectedLang=JA 이면 앞면은 English, 교정문은 ja 로 지정되어야 한다.
        val inputEnJa = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "ja-0-a", lang = LangCode.JA, sourceTurnIndex = 0, sourceText = "わたしが学校")
            ),
            lang = LangCode.JA,
            primaryLang = LangCode.EN
        )
        val promptEnJa = builder.build(inputEnJa)
        assertTrue("nativeText 앞면 언어가 English 여야 함", promptEnJa.contains("English"))
        assertTrue("afterText 교정문 언어가 ja 여야 함", promptEnJa.contains("in ja"))
    }

    @Test
    fun `explanation rule delegates to Explanation policy not fixed primaryLang`() {
        // COR-TUNE-003-FIX: explanation 응답 규칙은 더 이상 primaryLang 을 고정하지 않는다.
        // 언어 결정은 Explanation 정책(explanationLine)에 위임되므로, 응답 규칙 줄에 "Explanation policy" 위임 문구가 있어야 한다.
        // primaryLang=KO 이어도 "Korean" 고정 문구가 응답 규칙 라인에 나타나지 않아야 한다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "hi")
            ),
            lang = LangCode.EN,
            primaryLang = LangCode.KO
        )
        val prompt = builder.build(input)
        assertTrue("Explanation policy 위임 문구 누락", prompt.contains("Explanation policy above"))
        assertFalse("explanation 응답 규칙에 primaryLang 고정 문구가 남아 있음", prompt.contains("a short correction tip in Korean"))
    }

    @Test
    fun `advanced band explanation does not conflict with response rule`() {
        // COR-TUNE-003-FIX: 고급 band 에서 explanationLine 은 target-language 설명을 지시하는데,
        // 응답 규칙이 primaryLang 을 강제하면 두 지시가 충돌한다. 수정 후에는 응답 규칙이 primaryLang 을 고정하지 않아야 한다.
        val connectedInput = inputOf(
            candidates = listOf(CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.ConnectedExpression))
        )
        val connectedPrompt = builder.build(connectedInput)
        // 정책 블록: target language 설명을 지시해야 한다.
        assertTrue("ConnectedExpression explanationLine target-language 문구 누락", connectedPrompt.contains("target language"))
        // 응답 규칙: primaryLang 고정 문구가 없어야 한다.
        assertFalse("ConnectedExpression 응답 규칙에 primaryLang 고정이 남아 있음", connectedPrompt.contains("a short correction tip in Korean"))

        val nuanceInput = inputOf(
            candidates = listOf(CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.NuanceRefine))
        )
        val nuancePrompt = builder.build(nuanceInput)
        assertTrue("NuanceRefine explanationLine target-language 문구 누락", nuancePrompt.contains("target language"))
        assertFalse("NuanceRefine 응답 규칙에 primaryLang 고정이 남아 있음", nuancePrompt.contains("a short correction tip in Korean"))
    }

    @Test
    fun `editSpans rule exposes enum constraints and drop warning`() {
        // COR-TUNE-003-FIX: editSpans 규칙에 issueCategory/improvementType enum 제약과
        // "discards the whole learningSignal" 폐기 경고가 노출되어야 한다.
        // 매퍼 normalizeEditSpan 이 이 enum 들을 unknown 으로 받으면 signal 전체를 drop 하므로, 프롬프트가 동일하게 안내해야 한다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            lang = LangCode.EN
        )

        val prompt = builder.build(input)

        // editSpans 규칙에 issueCategory/improvementType enum 목록이 노출되어야 한다(대표값 확인).
        assertTrue("editSpans issueCategory enum 제약 누락", prompt.contains("GrammarForm"))
        assertTrue("editSpans improvementType enum 제약 누락", prompt.contains("GrammarFixed"))
        // 폐기 경고가 editSpans 규칙에 노출되어야 한다.
        assertTrue("editSpans drop 경고 누락", prompt.contains("discards the whole learningSignal"))
    }

    @Test
    fun `MeaningFirst policy yields meaning-preservation and no-expansion wording`() {
        // COR-TUNE-003: MeaningFirst band는 의미 보존·최소 수정·확장 금지가 핵심이다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        )

        val prompt = builder.build(input)

        assertTrue("Correction policy 블록 누락", prompt.contains("Correction policy"))
        // PreserveIntentOnly scope 문구 확인.
        assertTrue("MeaningFirst 의 의미 보존 scope 지시 누락", prompt.contains("do not restructure or expand"))
        // NoExpansion 문구 확인.
        assertTrue("MeaningFirst 의 문장 확장 금지 지시 누락", prompt.contains("do not expand the sentence"))
        // NuanceRefine 전용 문구가 새어 나오지 않는지 확인.
        assertFalse("MeaningFirst 에 뉘앙스/register 문구가 새어 나옴", prompt.contains("handle register, tone, and nuance"))
    }

    @Test
    fun `NuanceRefine policy surfaces nuance and register behaviour`() {
        // COR-TUNE-003: NuanceRefine band는 뉘앙스·register·원어민식 선택이 핵심이다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.NuanceRefine))
        )

        val prompt = builder.build(input)

        assertTrue("NuanceRefine 의 뉘앙스 문구 누락", prompt.contains("nuance"))
        assertTrue("NuanceRefine 의 register 문구 누락", prompt.contains("register"))
        // NuanceRewriteWithinSameMeaning scope 문구 확인.
        assertTrue("NuanceRefine 의 scope 지시 누락", prompt.contains("refine tone and nuance"))
        // MeaningFirst 전용 확장 금지 문구가 새어 나오지 않는지 확인.
        assertFalse("NuanceRefine 에 do-not-expand 문구가 새어 나옴", prompt.contains("do not expand the sentence"))
    }

    @Test
    fun `each band exposes its own few-shot anchor wording`() {
        // COR-TUNE-005: band별 빌드 시 해당 band의 고유 anchor 문구가 노출되고, 다른 band 문구가 새어 나오지 않는지 확인.
        val candidate = CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")

        val meaningFirstPrompt = builder.build(inputOf(
            candidates = listOf(candidate),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        ))
        assertTrue("MeaningFirst few-shot anchor 누락", meaningFirstPrompt.contains("disconnected words or fragments"))
        assertFalse("MeaningFirst에 NuanceRefine anchor가 새어 나옴", meaningFirstPrompt.contains("refined for tone and register"))

        val patternFixPrompt = builder.build(inputOf(
            candidates = listOf(candidate),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.PatternFix))
        ))
        assertTrue("PatternFix few-shot anchor 누락", patternFixPrompt.contains("single pattern fixed"))

        val sentenceShapePrompt = builder.build(inputOf(
            candidates = listOf(candidate),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.SentenceShape))
        ))
        assertTrue("SentenceShape few-shot anchor 누락", sentenceShapePrompt.contains("shaky word order or grammar"))

        val everydayNaturalPrompt = builder.build(inputOf(
            candidates = listOf(candidate),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.EverydayNatural))
        ))
        assertTrue("EverydayNatural few-shot anchor 누락", everydayNaturalPrompt.contains("more natural everyday phrase"))

        val connectedPrompt = builder.build(inputOf(
            candidates = listOf(candidate),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.ConnectedExpression))
        ))
        assertTrue("ConnectedExpression few-shot anchor 누락", connectedPrompt.contains("natural connective"))

        val nuancePrompt = builder.build(inputOf(
            candidates = listOf(candidate),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.NuanceRefine))
        ))
        assertTrue("NuanceRefine few-shot anchor 누락", nuancePrompt.contains("refined for tone and register"))
        assertFalse("NuanceRefine에 MeaningFirst anchor가 새어 나옴", nuancePrompt.contains("disconnected words or fragments"))
    }

    @Test
    fun `few-shot anchor does not leak band names or raw metrics`() {
        // COR-TUNE-005: 예시 삽입 후에도 band 이름·점수·레벨이 프롬프트에 노출되지 않아야 한다.
        val bands = CorrectionGrowthBand.entries
        val candidate = CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "hi")

        bands.forEach { band ->
            val prompt = builder.build(inputOf(
                candidates = listOf(candidate),
                profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(band))
            ))
            assertFalse("band 이름 '${band.name}'이 프롬프트에 노출됨", prompt.contains(band.name))
            assertFalse("CEFR 레벨이 노출됨 (band=$band)", prompt.contains("A1"))
            assertFalse("raw metric 숫자(%.2f)가 노출됨 (band=$band)", Regex("""\d\.\d{2}""").containsMatchIn(prompt))
        }
    }

    @Test
    fun `few-shot anchor language follows selectedLang dynamically`() {
        // COR-TUNE-005: 예시 문구에 selectedLang.code가 동적으로 반영되어야 한다. 하드코딩된 언어가 없어야 한다.
        val candidateJa = CorrectionCandidate(id = "ja-0-a", lang = LangCode.JA, sourceTurnIndex = 0, sourceText = "わたし学校行く")
        val promptJa = builder.build(inputOf(
            candidates = listOf(candidateJa),
            lang = LangCode.JA,
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        ))
        // selectedLang=JA이면 예시에 "in ja"가 포함되어야 한다.
        assertTrue("JA 빌드에 'in ja' 누락", promptJa.contains("in ja"))
    }

    @Test
    fun `few-shot anchor does not break existing schema regression`() {
        // COR-TUNE-005: few-shot 삽입 후에도 핵심 4필드·learningSignal 키·위임 문구 회귀가 없어야 한다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.EverydayNatural))
        )
        val prompt = builder.build(input)

        // 핵심 4필드 유지.
        listOf("suggestions", "candidateId", "nativeText", "afterText", "explanation").forEach { key ->
            assertTrue("few-shot 삽입 후 schema key '$key' 누락", prompt.contains(key))
        }
        // learningSignal 강화 문구 유지(COR-TUNE-002-FIX).
        assertTrue("meaningPreserved ALWAYS 지시 누락", prompt.contains("ALWAYS include"))
        assertTrue("drop 경고 누락", prompt.contains("discards the whole learningSignal"))
        // Explanation policy 위임 유지(COR-TUNE-003-FIX).
        assertTrue("Explanation policy 위임 문구 누락", prompt.contains("Explanation policy above"))
    }

    @Test
    fun `focus line appears only when focus is trustworthy`() {
        val candidate = CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")

        // 신뢰 가능한 focus(Tense, Medium, 관측 3회) → 한 줄 노출.
        val trustworthy = inputOf(
            candidates = listOf(candidate),
            profile = profileWith(
                correctionPolicy = meaningFirstPolicy(),
                focus = LearningFocusSummary(
                    primaryFocus = LearningFocusType.Tense,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Medium,
                    observedCount = 3
                )
            )
        )
        val withFocus = builder.build(trustworthy)
        assertTrue("신뢰 가능한 focus 라인 누락", withFocus.contains("focus:"))
        assertTrue("focus 라벨(verb tense) 누락", withFocus.contains("verb tense"))

        // primaryFocus 가 없으면(=신뢰 불가) focus 라인은 생략된다.
        val noFocusInput = inputOf(
            candidates = listOf(candidate),
            profile = profileWith(correctionPolicy = meaningFirstPolicy(), focus = noFocus())
        )
        val withoutFocus = builder.build(noFocusInput)
        assertFalse("focus 가 없는데 focus 라인이 노출됨", withoutFocus.contains("focus:"))

        // confidence Low 면 focus 가 있어도 생략된다.
        val lowConfidence = inputOf(
            candidates = listOf(candidate),
            profile = profileWith(
                correctionPolicy = meaningFirstPolicy(),
                focus = LearningFocusSummary(
                    primaryFocus = LearningFocusType.Tense,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 5
                )
            )
        )
        assertFalse("저신뢰 focus 가 노출됨", builder.build(lowConfidence).contains("focus:"))
    }

    @Test
    fun `intent context block appears above Candidates when sessionContext is not empty`() {
        // COR-TUNE-010: AI 가 후보를 보기 전에 의도 맥락을 먼저 읽어야 하므로 블록은 Candidates 보다 위에 있어야 한다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            sessionContext = CorrectionSessionContext(
                recentTopics = listOf("school routine"),
                topicSummaries = listOf("i go school -> I go to school (article)"),
                topicKeySentences = listOf("I go to school every day."),
                currentSessionTurns = listOf(
                    CorrectionContextTurn(speaker = "user", text = "I go school yesterday"),
                    CorrectionContextTurn(speaker = "assistant", text = "Oh, where do you study?")
                )
            )
        )

        val prompt = builder.build(input)

        val contextIndex = prompt.indexOf("Conversation context / intent")
        val candidatesIndex = prompt.indexOf("Candidates:")
        assertTrue("의도 맥락 블록 누락", contextIndex >= 0)
        assertTrue("Candidates 블록 누락", candidatesIndex >= 0)
        assertTrue("의도 맥락 블록이 Candidates 보다 아래에 있음", contextIndex < candidatesIndex)
        assertTrue("recentTopics 누락", prompt.contains("school routine"))
        assertTrue("topicSummaries 누락", prompt.contains("i go school -> I go to school (article)"))
        assertTrue("topicKeySentences 누락", prompt.contains("I go to school every day."))
        assertTrue("현재 세션 turn 누락", prompt.contains("user: I go school yesterday"))
        assertTrue("현재 세션 turn 누락", prompt.contains("assistant: Oh, where do you study?"))
    }

    @Test
    fun `intent context block omitted when sessionContext is empty`() {
        // 세션 기억 조회 실패 등으로 맥락이 전부 비면 블록 자체를 생략해 토큰을 아끼고 기존 동작을 보존한다(폴백).
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            sessionContext = CorrectionSessionContext()
        )

        val prompt = builder.build(input)

        assertFalse("빈 세션 맥락인데 의도 맥락 블록이 노출됨", prompt.contains("Conversation context / intent"))
    }

    @Test
    fun `intent context block enforces injection caps`() {
        // COR-TUNE-010: 토큰 비대 방지를 위해 요약/핵심문장은 최대 5개, 현재 세션 turn 은 최신 20개만 남는다.
        val recentTopics = (1..6).map { "topic-$it" }
        val topicSummaries = (1..6).map { "summary-$it" }
        val topicKeySentences = (1..6).map { "sentence-$it" }
        // 두 자리로 zero-pad 해 "turn-01"이 "turn-010"/"turn-11" 같은 다른 항목의 substring 이 되지 않게 한다.
        val currentSessionTurns = (1..21).map { index ->
            CorrectionContextTurn(speaker = "user", text = "turn-${index.toString().padStart(2, '0')}")
        }

        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            sessionContext = CorrectionSessionContext(
                recentTopics = recentTopics,
                topicSummaries = topicSummaries,
                topicKeySentences = topicKeySentences,
                currentSessionTurns = currentSessionTurns
            )
        )

        val prompt = builder.build(input)

        // recentTopics/topicSummaries: 앞에서부터 5개만 노출(최신/고빈도 우선 순서를 SessionMemory 가 이미 보장).
        (1..5).forEach { index -> assertTrue("topic-$index 누락", prompt.contains("topic-$index")) }
        assertFalse("주입 상한(5)을 넘는 topic-6 이 노출됨", prompt.contains("topic-6"))
        (1..5).forEach { index -> assertTrue("summary-$index 누락", prompt.contains("summary-$index")) }
        assertFalse("주입 상한(5)을 넘는 summary-6 이 노출됨", prompt.contains("summary-6"))
        (1..5).forEach { index -> assertTrue("sentence-$index 누락", prompt.contains("sentence-$index")) }
        assertFalse("주입 상한(5)을 넘는 sentence-6 이 노출됨", prompt.contains("sentence-6"))

        // currentSessionTurns: 최신 20개만 남아야 하므로 가장 오래된 turn-01 은 잘려나가야 한다.
        assertFalse("주입 상한(20)을 넘는 가장 오래된 turn-01 이 노출됨", prompt.contains("turn-01"))
        (2..21).forEach { index ->
            val text = "turn-${index.toString().padStart(2, '0')}"
            assertTrue("$text 누락", prompt.contains(text))
        }
    }

    // --- helpers ---

    /**
     * 테스트용 입력 생성. profile 을 명시하지 않으면 LangState.initial 에서 해석한 보수적 profile 을 쓴다.
     */
    private fun inputOf(
        candidates: List<CorrectionCandidate>,
        lang: LangCode = LangCode.EN,
        primaryLang: LangCode = LangCode.KO,
        profile: LearnerAdaptationProfile = profileUseCase(LangState.initial(lang)),
        sessionContext: CorrectionSessionContext = CorrectionSessionContext()
    ): GenerateSuggestionsInput = GenerateSuggestionsInput(
        candidates = candidates,
        langState = LangState.initial(lang),
        primaryLang = primaryLang,
        profile = profile,
        sessionContext = sessionContext
    )

    /** MeaningFirst 기본 정책. focusLine 게이트 테스트 등 "policy 무관" 케이스에서 사용한다. */
    private fun meaningFirstPolicy(): CorrectionGrowthPolicy =
        CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst)

    @Test
    fun `includes safety guard instructions exactly once`() {
        val prompt = builder.build(
            inputOf(
                candidates = listOf(
                    CorrectionCandidate(
                        id = "en-0-safe",
                        lang = LangCode.EN,
                        sourceTurnIndex = 0,
                        sourceText = "i goed home"
                    )
                )
            )
        )

        assertEquals(1, Regex("Safety:").findAll(prompt).count())
        assertTrue(prompt.contains("Do not correct, naturalize, translate, or make harmful content more actionable."))
        assertTrue(prompt.contains("Skip candidates involving self-harm instructions"))
        assertTrue(prompt.contains("Emit one suggestion per safe candidate."))
    }

    private fun noFocus(): LearningFocusSummary = LearningFocusSummary(
        primaryFocus = null,
        secondaryFocus = null,
        confidence = ProfileConfidence.Low,
        observedCount = 0
    )

    /**
     * correctionPolicy 만 다르게 한 LearnerAdaptationProfile 직접 조립.
     * 프롬프트 빌더 단위 테스트라 BuildLearnerAdaptationProfileUseCase 의 휴리스틱과 분리해 정책→문구만 검증한다.
     */
    private fun profileWith(
        correctionPolicy: CorrectionGrowthPolicy,
        focus: LearningFocusSummary = noFocus()
    ): LearnerAdaptationProfile = LearnerAdaptationProfile(
        core = LearnerAbilityProfile(
            cefrLevel = VocabLevel.A1,
            levelConfidence = ProfileConfidence.Medium,
            grammarStage = SkillStage.Foundation,
            vocabularyStage = SkillStage.Foundation,
            fluencyStage = SkillStage.Foundation,
            naturalnessStage = SkillStage.Foundation,
            focus = focus
        ),
        chatPolicy = anyChatPolicy(),
        correctionPolicy = correctionPolicy
    )

    private fun anyChatPolicy(): ChatAdaptationPolicy = ChatAdaptationPolicy(
        conversationBand = ConversationAbilityBand.SimpleSentence,
        intentSupport = IntentSupportPolicy.TrustMeaning,
        primaryBridge = PrimaryBridgePolicy.FallbackOnly,
        recastStyle = RecastStylePolicy.SimpleInline,
        expressionGrowth = ExpressionGrowthPolicy.OneSimplePattern,
        questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
        responseLength = ResponseLengthPolicy.NaturalBrief,
        speechSpeed = SpeechSpeedPolicy.NormalLearning
    )
}
