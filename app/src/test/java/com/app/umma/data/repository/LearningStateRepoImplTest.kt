package com.app.umma.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.app.umma.data.model.learningstate.ExternalMetricsDto
import com.app.umma.data.model.learningstate.InternalMetricsDto
import com.app.umma.data.model.learningstate.LangStateAnalysisMetaDto
import com.app.umma.data.model.learningstate.LangStateDto
import com.app.umma.data.model.learningstate.LearningFocusDto
import com.app.umma.data.model.learningstate.MetricEvidenceDto
import com.app.umma.data.model.learningstate.UserLangPrefDto
import com.app.umma.data.model.learningstate.toDomain
import com.app.umma.data.model.learningstate.toDto
import com.app.umma.data.source.remote.LearningStateRemote
import com.app.umma.data.source.remote.LearningStateRemoteDataSource
import com.app.umma.data.source.remote.LearningStateRemoteUpdate
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LangStateSummaryUpdatePolicy
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class LearningStateRepoImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sync writes pending local snapshot to remote and clears pending marker`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        repo.sync().getOrThrow()

        // 첫 sync는 createInitial에서 남긴 pending key를 Firestore write-back으로 밀어낸다.
        assertEquals(1, remoteDataSource.syncCalls)
        assertEquals("ko", remoteDataSource.lastUpdate?.userPref?.primaryLanguage)
        assertEquals("en", remoteDataSource.lastUpdate?.userPref?.selectedLearningLanguage)
        assertEquals("en", remoteDataSource.lastUpdate?.langStates?.single()?.language)
        // CHAT-TUNE-001-A: 신규 LangState는 schema v2와 빈 analysisMeta를 함께 write-back한다.
        assertEquals(2, remoteDataSource.lastUpdate?.langStates?.single()?.schemaVersion)
        assertEquals(
            LangStateAnalysisMeta.initial(),
            remoteDataSource.lastUpdate?.langStates?.single()?.analysisMeta?.toDomain()
        )
        assertEquals("en", remoteDataSource.lastUpdate?.dashSummaries?.single()?.language)

        repo.sync().getOrThrow()

        // pending marker가 해제됐기 때문에 다음 sync는 remote fetch 경로로 들어간다.
        assertEquals(1, remoteDataSource.syncCalls)
        assertEquals(1, remoteDataSource.fetchCalls)
    }

    @Test
    fun `primary language outside learning languages is preserved`() {
        val legacyDto = UserLangPrefDto(
            // primaryLanguage는 학습 기준 언어라 학습 대상 목록 밖이어도 정상이다.
            primaryLanguage = "ko",
            selectedLearningLanguage = "en",
            learningLanguages = listOf("en", "ja"),
            schemaVersion = 1,
            updatedAt = 1_000L
        )

        val restored = legacyDto.toDomain()

        assertEquals(LangCode.KO, restored.primaryLang)
        assertEquals(LangCode.EN, restored.selectedLang)
        assertEquals(listOf(LangCode.EN, LangCode.JA), restored.learningLangs)
    }

    @Test
    fun `new lang state uses schema v2 and initial analysis meta`() {
        val state = LangState.initial(LangCode.EN, createdAt = 1_000L)

        // schema v2는 저장 구조가 바뀌었다는 신호이고, initial meta는 아직 근거가 없다는 안전 상태다.
        assertEquals(2, state.schema)
        assertEquals(LangStateAnalysisMeta.initial(), state.analysisMeta)
    }

    @Test
    fun `lang state dto preserves valid analysis meta round trip`() {
        val state = LangState.initial(LangCode.EN, createdAt = 1_000L).copy(
            analysisMeta = LangStateAnalysisMeta(
                // metricEvidence는 이후 policy/profile이 "왜 이 metric을 움직일 수 있는지"를 판단하는 근거다.
                metricEvidence = mapOf(
                    LearningMetricKey.GrammarAccuracy to MetricEvidence(
                        observedCount = 3,
                        confidence = 0.82,
                        sourceTypes = setOf(
                            LearningSignalSource.CorrectionSignal,
                            LearningSignalSource.UserTurn,
                            // Chat evidence source도 같은 DTO 경로에서 손실 없이 round-trip 되어야 한다.
                            LearningSignalSource.ChatSession
                        ),
                        direction = EvidenceDirection.Up,
                        directionCount = 2,
                        lastObservedAt = 2_000L
                    )
                ),
                // activeFocus는 다음 Chat/Correction에서 우선 도와줄 반복 약점 후보를 담는다.
                activeFocus = listOf(
                    LearningFocus(
                        type = LearningFocusType.Article,
                        observedCount = 2,
                        confidence = 0.7,
                        firstObservedAt = 1_500L,
                        lastObservedAt = 2_000L
                    )
                ),
                lastSignalAt = 2_000L,
                // Chat source 중복 반영 방어 id도 analysisMeta 안에서 함께 보존한다.
                lastChatAnalysisEventId = "chat-session:session-1",
                // Chat band 공식 source인 summary도 LangState DTO round-trip에서 손실되면 안 된다.
                chatEvidenceSummary = ChatEvidenceSummary(
                    targetLanguageComprehension = TargetLanguageComprehensionEvidence.SimpleSentence,
                    targetLanguageProduction = TargetLanguageProductionEvidence.SimpleSentences,
                    supportLanguageDependence = LanguageDependenceEvidence.Low,
                    aiScaffoldingDependence = LanguageDependenceEvidence.Low,
                    conversationSustainability = ConversationSustainabilityEvidence.SustainedSimple,
                    consistency = ConversationConsistencyEvidence.Mixed,
                    responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
                    confidence = ProfileConfidence.Medium,
                    observedCount = 2,
                    lastObservedAt = 2_000L
                )
            )
        )

        val restored = state.toDto().toDomain()

        // DTO/DataStore/Remote 공통 계약에서 evidence와 focus가 손실되지 않아야 이후 profile이 같은 근거를 읽는다.
        assertEquals(state.analysisMeta, restored.analysisMeta)
    }

    @Test
    fun `schema v1 lang state dto restores initial analysis meta`() {
        val legacyDto = langStateDto(
            schemaVersion = 1,
            analysisMeta = null
        )

        val restored = legacyDto.toDomain()

        // 기존 사용자 데이터에는 analysisMeta가 없으므로 빈 evidence/focus로 복원해야 앱 진입이 깨지지 않는다.
        assertEquals(1, restored.schema)
        assertEquals(LangStateAnalysisMeta.initial(), restored.analysisMeta)
    }

    @Test
    fun `invalid analysis meta enum and confidence are dropped`() {
        val dto = langStateDto(
            schemaVersion = 2,
            analysisMeta = LangStateAnalysisMetaDto(
                metricEvidence = mapOf(
                    // 정상 evidence는 그대로 복원되어야 한다.
                    "GrammarAccuracy" to MetricEvidenceDto(
                        observedCount = 3,
                        confidence = 0.8,
                        sourceTypes = listOf("CorrectionSignal"),
                        direction = "Up",
                        directionCount = 2,
                        lastObservedAt = 2_000L
                    ),
                    // unknown metric key는 어떤 InternalMetrics와도 연결할 수 없어 drop한다.
                    "UnknownMetric" to MetricEvidenceDto(
                        observedCount = 3,
                        confidence = 0.8,
                        sourceTypes = listOf("CorrectionSignal"),
                        direction = "Up",
                        directionCount = 2
                    ),
                    // confidence 범위가 깨진 값은 장기 능력 근거를 오염시킬 수 있어 drop한다.
                    "VocabularyLevel" to MetricEvidenceDto(
                        observedCount = 1,
                        confidence = 1.5,
                        sourceTypes = listOf("CorrectionSignal"),
                        direction = "Up",
                        directionCount = 1
                    ),
                    // direction enum이 unknown이면 score 이동 방향을 알 수 없어 drop한다.
                    "SentenceComplexity" to MetricEvidenceDto(
                        observedCount = 1,
                        confidence = 0.6,
                        sourceTypes = listOf("CorrectionSignal"),
                        direction = "Sideways",
                        directionCount = 1
                    )
                ),
                activeFocus = listOf(
                    // 정상 focus는 prompt/profile 후보로 복원되어야 한다.
                    LearningFocusDto(
                        type = "Article",
                        observedCount = 2,
                        confidence = 0.7,
                        firstObservedAt = 1_000L,
                        lastObservedAt = 2_000L
                    ),
                    // unknown focus type은 Chat/Correction이 행동으로 바꿀 수 없으므로 drop한다.
                    LearningFocusDto(
                        type = "UnknownFocus",
                        observedCount = 2,
                        confidence = 0.7,
                        firstObservedAt = 1_000L,
                        lastObservedAt = 2_000L
                    ),
                    // confidence 범위가 깨진 focus는 prompt에 노출되지 않도록 drop한다.
                    LearningFocusDto(
                        type = "Tense",
                        observedCount = 2,
                        confidence = -0.1,
                        firstObservedAt = 1_000L,
                        lastObservedAt = 2_000L
                    )
                ),
                lastSignalAt = 2_000L
            )
        )

        val restored = dto.toDomain().analysisMeta

        // 오염된 enum/confidence는 domain으로 올리지 않고, 유효한 근거만 유지한다.
        assertEquals(setOf(LearningMetricKey.GrammarAccuracy), restored.metricEvidence.keys)
        assertEquals(EvidenceDirection.Up, restored.metricEvidence[LearningMetricKey.GrammarAccuracy]?.direction)
        assertEquals(listOf(LearningFocusType.Article), restored.activeFocus.map { it.type })
    }

    @Test
    fun `invalid selected language is restored to first learning language`() {
        val legacyDto = UserLangPrefDto(
            primaryLanguage = "ko",
            selectedLearningLanguage = "ko",
            learningLanguages = listOf("en", "ja"),
            schemaVersion = 1,
            updatedAt = 1_000L
        )

        val restored = legacyDto.toDomain()

        // Chat 직접 진입은 Dashboard fallback을 거치지 않을 수 있으므로 selected 학습 언어를 DTO 복원 단계에서 정규화한다.
        assertEquals(LangCode.KO, restored.primaryLang)
        assertEquals(LangCode.EN, restored.selectedLang)
        assertEquals(listOf(LangCode.EN, LangCode.JA), restored.learningLangs)
    }

    @Test
    fun `sync keeps pending marker when remote write fails`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource(failSync = true)
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        val failed = repo.sync()

        // write-back 실패는 local snapshot을 깨지 않고 pending marker만 유지해야 한다.
        assertTrue(failed.isFailure)
        assertEquals(1, remoteDataSource.syncCalls)

        remoteDataSource.failSync = false
        repo.sync().getOrThrow()

        // 실패한 write-back은 pending marker를 지우지 않으므로 다음 sync에서 다시 시도된다.
        assertEquals(2, remoteDataSource.syncCalls)
    }

    @Test
    fun `sync does not overwrite local language change that happens during remote fetch`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        repo.sync().getOrThrow()

        remoteDataSource.onFetch = {
            // Dashboard 진입 sync가 fetch 중일 때 사용자가 selector에서 다른 언어를 고르는 상황을 재현한다.
            repo.changeSelectedLang(LangCode.JA).getOrThrow()
        }

        repo.sync().getOrThrow()

        val selectedLang = repo.observeLearningState().first().userPref?.selectedLang

        assertEquals(LangCode.JA, selectedLang)
        assertEquals("ja", remoteDataSource.lastUpdate?.userPref?.selectedLearningLanguage)
    }

    @Test
    fun `updateCorrectionSignal updates session and dashboard summaries and dedupes duplicate event`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        repo.sync().getOrThrow()

        val input = CorrectionSignalUpdateInput(
            uid = USER_UID,
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            sourceEventId = "turn-1",
            recentMinutes = 9,
            recentTopic = "Travel",
            updatedAt = 2_000L
        )

        val first = repo.updateCorrectionSignal(input).getOrThrow()
        val state = repo.observeLearningState().first()

        // correction signal 은 LangState 를 건드리지 않고 session/dash summary 만 함께 바꿔야 한다.
        // 이 경계가 흔들리면 Chat turn 신호가 분석 상태를 건드리는 부작용이 생긴다.
        assertTrue(first.applied)
        assertTrue(state.sessionSummaries[LangCode.EN]?.correctionAvailable == true)
        assertTrue(state.dashSummaries[LangCode.EN]?.correctionAvailable == true)
        assertEquals(9, state.sessionSummaries[LangCode.EN]?.recentMinutes)
        assertEquals("Travel", state.dashSummaries[LangCode.EN]?.recentTopic)

        repo.sync().getOrThrow()

        // pending marker 가 남아 있던 session/dash summary 가 remote write-back payload 에도 들어가야 한다.
        // local-first 상태가 remote 복구 후에도 동일하게 재현되는지 확인하는 부분이다.
        assertEquals(2, remoteDataSource.syncCalls)
        assertEquals("en", remoteDataSource.lastUpdate?.sessionSummaries?.single()?.language)
        assertEquals("en", remoteDataSource.lastUpdate?.dashSummaries?.single()?.language)

        val duplicate = repo.updateCorrectionSignal(input).getOrThrow()
        assertFalse(duplicate.applied)
    }

    @Test
    fun `updateCorrectionSignal does not re-enable correction after same event was completed`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        val input = CorrectionSignalUpdateInput(
            uid = USER_UID,
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            sourceEventId = "turn-1",
            recentMinutes = 9,
            recentTopic = "Travel",
            updatedAt = 2_000L
        )

        repo.updateCorrectionSignal(input).getOrThrow()
        // Correction 완료 파이프라인이 같은 summary를 false로 내린 상태를 만든다.
        // 이후 같은 turn retry가 오면 repo의 idempotent 기준이 event id뿐인지 검증할 수 있다.
        //
        // 리팩토링 후: Summary 계산은 UseCase 책임이므로, repo 직접 호출 시 prepared Summary 를 함께 넘긴다.
        // correctionAvailable=false 를 담아야 실제 Summary 가 false 로 저장된다.
        val langForTest = LangCode.EN
        val preparedDash = DashSummary.initial(langForTest).copy(
            correctionAvailable = false,
            updatedAt = 3_000L
        )
        val preparedSession = SessionSummary.initial(langForTest).copy(
            correctionAvailable = false,
            updatedAt = 3_000L
        )
        repo.updateLanguageState(
            LangStateUpdateInput(
                uid = USER_UID,
                lang = langForTest,
                sessionMemoryKey = "session-en",
                analysisEventId = "analysis-after-correction",
                currentState = LangState.initial(langForTest, createdAt = 1_000L),
                preparedState = LangState.initial(langForTest, createdAt = 1_000L).copy(
                    updatedAt = 3_000L,
                    lastAnalysisEventId = "analysis-after-correction"
                ),
                preparedDashSummary = preparedDash,
                preparedSessionSummary = preparedSession,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I need book ticket.",
                        tokenCount = 5,
                        durationMs = 2_000L
                    )
                ),
                correctionResult = null,
                correctionAvailableOverride = false,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        ).getOrThrow()

        val retry = repo.updateCorrectionSignal(input.copy(updatedAt = 4_000L)).getOrThrow()
        val state = repo.observeLearningState().first()

        // 같은 sourceEventId 재도착은 현재 correctionAvailable 값과 무관하게 stale retry로 본다.
        // 이 회귀는 조건에 correctionAvailable을 포함했을 때 다시 true로 올라가던 문제를 막는다.
        assertFalse(retry.applied)
        assertFalse(state.sessionSummaries[LangCode.EN]?.correctionAvailable == true)
        assertFalse(state.dashSummaries[LangCode.EN]?.correctionAvailable == true)
    }

    @Test
    fun `updateLanguageState can preserve existing summaries for chat evidence update`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN).copy(
                recentMinutes = 12,
                recentTopic = "Coffee",
                correctionAvailable = true,
                updatedAt = 1_500L
            ),
            sessionSummary = SessionSummary.initial(LangCode.EN).copy(
                recentMinutes = 12,
                recentTopic = "Coffee",
                correctionAvailable = true,
                updatedAt = 1_500L
            ),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()
        // createInitial이 만든 pending key를 먼저 비워야 이번 update의 pending 대상만 검증할 수 있다.
        repo.sync().getOrThrow()

        val preparedState = LangState.initial(LangCode.EN, createdAt = 1_000L).copy(
            updatedAt = 2_000L,
            lastAnalyzedAt = 2_000L,
            lastAnalysisEventId = "chat-session:session-1",
            analysisMeta = LangStateAnalysisMeta.initial().copy(
                lastChatAnalysisEventId = "chat-session:session-1"
            )
        )

        repo.updateLanguageState(
            LangStateUpdateInput(
                uid = USER_UID,
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "chat-session:session-1",
                currentState = LangState.initial(LangCode.EN, createdAt = 1_000L),
                preparedState = preparedState,
                // Chat 분석 turn payload는 summary 계산용이 아니다.
                // duration이 0이어도 기존 recentMinutes가 지워지면 안 된다.
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "hello",
                        tokenCount = 1,
                        durationMs = 0L
                    )
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.PreserveExisting
            )
        ).getOrThrow()

        val localState = repo.observeLearningState().first()
        assertEquals(12, localState.dashSummaries[LangCode.EN]?.recentMinutes)
        assertEquals("Coffee", localState.dashSummaries[LangCode.EN]?.recentTopic)
        assertTrue(localState.dashSummaries[LangCode.EN]?.correctionAvailable == true)
        assertEquals(1_500L, localState.dashSummaries[LangCode.EN]?.updatedAt)
        assertEquals(12, localState.sessionSummaries[LangCode.EN]?.recentMinutes)
        assertEquals("Coffee", localState.sessionSummaries[LangCode.EN]?.recentTopic)
        assertTrue(localState.sessionSummaries[LangCode.EN]?.correctionAvailable == true)
        assertEquals(1_500L, localState.sessionSummaries[LangCode.EN]?.updatedAt)

        repo.sync().getOrThrow()

        // PreserveExisting은 remote write-back도 LangState만 보낸다.
        // summary pending key가 섞이면 Firestore summary가 불필요하게 덮일 수 있다.
        assertEquals("en", remoteDataSource.lastUpdate?.langStates?.single()?.language)
        assertTrue(remoteDataSource.lastUpdate?.dashSummaries.orEmpty().isEmpty())
        assertTrue(remoteDataSource.lastUpdate?.sessionSummaries.orEmpty().isEmpty())
    }

    @Test
    fun `updateCorrectionSignal fails when input language does not match selected language`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        val result = repo.updateCorrectionSignal(
            CorrectionSignalUpdateInput(
                uid = USER_UID,
                lang = LangCode.JA,
                sessionMemoryKey = "session-ja",
                sourceEventId = "turn-ja-1",
                updatedAt = 2_000L
            )
        )

        // Chat 쪽에서 stale language 신호를 보내면 현재 선택 언어의 summary를 오염시키지 않아야 한다.
        assertTrue(result.isFailure)
        assertEquals(
            "correction signal lang must match selected learning language",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `updateCorrectionSignal fails when current summaries are missing`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()
        repo.sync().getOrThrow()

        remoteDataSource.lastUpdate = LearningStateRemoteUpdate(
            userPref = remoteDataSource.lastUpdate?.userPref,
            langStates = remoteDataSource.lastUpdate?.langStates.orEmpty(),
            dashSummaries = emptyList(),
            sessionSummaries = emptyList(),
            flashcardSummaries = remoteDataSource.lastUpdate?.flashcardSummaries.orEmpty()
        )
        repo.sync().getOrThrow()

        val result = repo.updateCorrectionSignal(
            CorrectionSignalUpdateInput(
                uid = USER_UID,
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                sourceEventId = "turn-1",
                updatedAt = 2_000L
            )
        )

        // LS-009는 summary 초기화 흐름이 아니라 기존 summary에 신호를 전파하는 경로다.
        // summary가 빠진 상태를 조용히 복구하면 Initial Setup/Sync 결손이 숨겨진다.
        assertTrue(result.isFailure)
        assertEquals("session summary is missing for lang=en", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync returns success when remote has no user document (new user before setup)`() = runBlocking {
        // createInitial() 없이 sync() 호출 — users/{uid} 문서 미생성 상태 시뮬레이션.
        // RecordingLearningStateRemoteDataSource.fetch() 는 lastUpdate==null → userPref=null 반환.
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        val result = repo.sync()

        // repo 레이어는 userPref=null 인 empty remote 도 성공으로 처리한다.
        // Snackbar 억제 책임은 DashboardViewModel.setupConfirmed 에 있다.
        assertTrue(result.isSuccess)
        assertEquals(0, remoteDataSource.syncCalls)  // pending keys 없음 → write-back 없음
        assertEquals(1, remoteDataSource.fetchCalls) // fetch 경로로 진입
    }

    private fun createRepository(
        remoteDataSource: RecordingLearningStateRemoteDataSource
    ): LearningStateRepoImpl {
        // DataStore는 실제 파일을 쓰되, 각 테스트마다 독립된 임시 파일을 사용한다.
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.newFile("learning_state_${System.nanoTime()}.preferences_pb")
        }
        return LearningStateRepoImpl(
            dataStore = dataStore,
            remoteDataSource = remoteDataSource,
            authRepository = FakeAuthRepository()
        )
    }

    private class RecordingLearningStateRemoteDataSource(
        var failSync: Boolean = false
    ) : LearningStateRemoteDataSource {
        var fetchCalls: Int = 0
        var syncCalls: Int = 0
        var lastUpdate: LearningStateRemoteUpdate? = null
        var onFetch: (suspend () -> Unit)? = null

        override suspend fun fetch(userUid: String): LearningStateRemote {
            fetchCalls += 1
            onFetch?.invoke()
            onFetch = null
            // write-back이 끝난 뒤에는 마지막으로 반영된 snapshot을 다시 읽을 수 있어야 한다.
            val update = lastUpdate
            return LearningStateRemote(
                userPref = update?.userPref,
                langStates = update?.langStates.orEmpty(),
                dashSummaries = update?.dashSummaries.orEmpty(),
                sessionSummaries = update?.sessionSummaries.orEmpty(),
                flashcardSummaries = update?.flashcardSummaries.orEmpty()
            )
        }

        override suspend fun sync(
            userUid: String,
            update: LearningStateRemoteUpdate
        ): Result<Unit> {
            syncCalls += 1
            if (failSync) {
                return Result.failure(IOException("simulated remote sync failure"))
            }
            lastUpdate = update
            return Result.success(Unit)
        }
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUserUid: Flow<String?> = flowOf(USER_UID)

        override suspend fun signInWithGoogle(idToken: String): Result<String> =
            Result.success(USER_UID)

        override fun getCurrentUserUid(): String = USER_UID

        override fun getCurrentUserEmail(): String = "test@example.com"

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

        override suspend fun hasValidSession(): Result<Boolean> = Result.success(true)
    }

    private companion object {
        const val USER_UID = "uid-1"

        fun langStateDto(
            schemaVersion: Int,
            analysisMeta: LangStateAnalysisMetaDto?
        ): LangStateDto {
            // LangStateDto 복원 테스트는 analysisMeta 정책에 집중하므로 나머지 metric은 안정적인 기본값으로 둔다.
            return LangStateDto(
                language = "en",
                internalMetrics = InternalMetricsDto(
                    grammarAccuracy = 0.0,
                    vocabularyAppropriateness = 0.0,
                    lexicalDiversity = 0.0,
                    vocabularyLevel = "A1",
                    sentenceComplexity = 0.0,
                    speechRate = 0.0,
                    pauseFrequency = 0.0,
                    avgUtteranceLength = 0.0,
                    spokenNaturalness = 0.0,
                    naturalExpressionUsage = 0.0,
                    errorRecurrence = 0.0,
                    reviewRetention = 0.0
                ),
                externalMetrics = ExternalMetricsDto(
                    vocabularyLevel = "A1",
                    grammarAccuracy = 0.0,
                    expressionRange = 0,
                    fluencyScore = 0.0,
                    naturalnessScore = 0.0
                ),
                analysisMeta = analysisMeta,
                schemaVersion = schemaVersion
            )
        }
    }
}
