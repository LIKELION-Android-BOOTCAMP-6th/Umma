package com.example.umma.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.umma.data.model.learningstate.DashSummaryDto
import com.example.umma.data.model.learningstate.FlashcardSummaryDto
import com.example.umma.data.model.learningstate.LangStateDto
import com.example.umma.data.model.learningstate.SessionSummaryDto
import com.example.umma.data.model.learningstate.UserLangPrefDto
import com.example.umma.data.model.learningstate.toDomain
import com.example.umma.data.model.learningstate.toDto
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore + in-memory snapshot을 함께 쓰는 학습 상태 저장소 구현체.
 *
 * LS-007은 Initial Setup의 초기 저장 계약이 실제로 동작해야 하므로,
 * 우선 앱 세션 내에서 일관되게 읽고 쓸 수 있는 저장 계층부터 만든다.
 * 원문 Session Memory와 더 큰 turn history는 이후 Room 저장소로 옮길 수 있도록 분리해 둔다.
 */
@Singleton
class LearningStateRepoImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : LearningStateRepo {

    // 저장/복원 시점에는 문자열 형태로만 다루고, Domain 모델은 바깥에서 유지한다.
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    // 앱 세션 동안만 유지되는 즉시 반영용 메모리 스냅샷.
    private val _state = MutableStateFlow(GlobalLangState.initial())

    override fun observeLearningState(): Flow<GlobalLangState> = _state.asStateFlow()

    override fun observeUserPref(): Flow<UserLangPref?> = _state.map { it.userPref }

    override fun observeLangState(lang: LangCode): Flow<LangState?> =
        _state.map { it.langStates[lang] }

    override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> =
        _state.map { it.dashSummaries[lang] }

    override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> =
        _state.map { it.sessionSummaries[lang] }

    override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> =
        _state.map { it.flashcardSummaries[lang] }

    override suspend fun preload(): Result<Unit> {
        return try {
            // 앱 시작 시 DataStore의 마지막 저장값을 올려서 화면이 바로 읽게 한다.
            val prefs = dataStore.data.first()
            _state.value = readSnapshot(prefs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> {
        return persistStateSafely { current ->
            // 현재 선택 언어만 바꾸고, 언어별 요약은 없으면 기본값으로 채운다.
            val userPref = current.userPref ?: return@persistStateSafely current
            val normalizedPref = userPref.copy(
                selectedLang = lang,
                learningLangs = userPref.learningLangs.toMutableSet().apply { add(lang) }.toList()
            )

            current.copy(
                userPref = normalizedPref,
                langStates = current.langStates.ensureLangState(lang),
                dashSummaries = current.dashSummaries.ensureDashSummary(lang),
                sessionSummaries = current.sessionSummaries.ensureSessionSummary(lang),
                flashcardSummaries = current.flashcardSummaries.ensureFlashcardSummary(lang),
                isPreloaded = true
            )
        }
    }

    override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<Unit> {
        val preparedState = input.preparedState ?: return Result.failure(
            IllegalArgumentException("preparedState is required for updateLanguageState")
        )

        return persistStateSafely { current ->
            // LS-006에서 계산된 결과를 그대로 반영하고, 화면용 요약은 함께 갱신한다.
            val lang = input.lang
            val measuredMinutes = calculateRecentMinutes(input)
            val hasUserTurns =
                input.recentUserTurns.any { it.speaker == com.example.umma.domain.model.learningstate.TurnSpeaker.USER }

            val updatedDash = current.dashSummaries[lang]
                ?: DashSummary.initial(lang)
            val updatedSession = current.sessionSummaries[lang]
                ?: SessionSummary.initial(lang)

            current.copy(
                langStates = current.langStates + (lang to preparedState),
                dashSummaries = current.dashSummaries + (
                        lang to updatedDash.copy(
                            recentMinutes = measuredMinutes,
                            correctionAvailable = hasUserTurns,
                            grammarDelta = deltaFromInternal(preparedState.external.grammarAccuracy),
                            fluencyDelta = deltaFromInternal(preparedState.external.fluencyScore),
                            vocabDelta = deltaFromInternal(preparedState.external.vocabularyLevel.ordinal.toDouble() / 5.0),
                            naturalnessDelta = deltaFromInternal(preparedState.external.naturalnessScore),
                            updatedAt = input.analyzedAt
                        )
                        ),
                sessionSummaries = current.sessionSummaries + (
                        lang to updatedSession.copy(
                            recentMinutes = measuredMinutes,
                            correctionAvailable = hasUserTurns || updatedSession.correctionAvailable,
                            updatedAt = input.analyzedAt
                        )
                        ),
                isPreloaded = true
            )
        }
    }

    override suspend fun createInitial(
        userUid: String,
        userPref: UserLangPref,
        langState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit> {
        if (userUid.isBlank()) {
            return Result.failure(IllegalArgumentException("userUid must not be blank"))
        }

        return persistStateSafely {
            // Initial Setup에서 만든 시작값을 현재 선택 언어 기준으로 정규화한다.
            val normalizedPref = userPref.copy(
                selectedLang = userPref.primaryLang,
                learningLangs = userPref.learningLangs.toMutableSet()
                    .apply { add(userPref.primaryLang) }.toList()
            )

            val normalizedLang = langState.copy(lang = userPref.primaryLang)
            val normalizedDash = dashSummary.copy(lang = userPref.primaryLang)
            val normalizedSession = sessionSummary.copy(lang = userPref.primaryLang)
            val normalizedFlashcard = flashcardSummary.copy(lang = userPref.primaryLang)

            GlobalLangState(
                userPref = normalizedPref,
                langStates = mapOf(normalizedLang.lang to normalizedLang),
                dashSummaries = mapOf(normalizedDash.lang to normalizedDash),
                sessionSummaries = mapOf(normalizedSession.lang to normalizedSession),
                flashcardSummaries = mapOf(normalizedFlashcard.lang to normalizedFlashcard),
                isPreloaded = true,
                schema = GlobalLangState.SCHEMA
            )
        }
    }

    override suspend fun clear(): Result<Unit> {
        return try {
            dataStore.edit { it.clear() }
            _state.value = GlobalLangState.initial()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun persistStateSafely(
        transform: (GlobalLangState) -> GlobalLangState
    ): Result<Unit> {
        return try {
            // 먼저 다음 상태를 계산하고, 저장이 성공해야만 메모리 스냅샷도 바꾼다.
            val nextState = transform(_state.value)
            persistSnapshot(nextState)
            _state.value = nextState
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun persistSnapshot(state: GlobalLangState) {
        dataStore.edit { prefs ->
            // MVP는 단일 스냅샷 전략을 택해, 저장 전 기존 키를 비우고 다시 쓴다.
            prefs.clear()

            state.userPref?.let { userPref ->
                prefs[USER_PREF_KEY] = json.encodeToString(userPref.toDto())
            }

            state.langStates.forEach { (lang, value) ->
                prefs[langStateKey(lang)] = json.encodeToString(value.toDto())
            }

            state.dashSummaries.forEach { (lang, value) ->
                prefs[dashSummaryKey(lang)] = json.encodeToString(value.toDto())
            }

            state.sessionSummaries.forEach { (lang, value) ->
                prefs[sessionSummaryKey(lang)] = json.encodeToString(value.toDto())
            }

            state.flashcardSummaries.forEach { (lang, value) ->
                prefs[flashcardSummaryKey(lang)] = json.encodeToString(value.toDto())
            }
        }
    }

    private fun readSnapshot(prefs: Preferences): GlobalLangState {
        // 저장된 문자열을 Domain 객체로 다시 복원한다.
        val userPref =
            prefs[USER_PREF_KEY]?.let { json.decodeFromString<UserLangPrefDto>(it).toDomain() }
        val langStates = mutableMapOf<LangCode, LangState>()
        val dashSummaries = mutableMapOf<LangCode, DashSummary>()
        val sessionSummaries = mutableMapOf<LangCode, SessionSummary>()
        val flashcardSummaries = mutableMapOf<LangCode, FlashcardSummary>()

        prefs.asMap().forEach { (key, value) ->
            val rawValue = value as? String ?: return@forEach
            val keyName = key.name
            when {
                keyName.startsWith(LANG_STATE_PREFIX) -> {
                    val state = json.decodeFromString<LangStateDto>(rawValue).toDomain()
                    langStates[state.lang] = state
                }

                keyName.startsWith(DASH_SUMMARY_PREFIX) -> {
                    val summary = json.decodeFromString<DashSummaryDto>(rawValue).toDomain()
                    dashSummaries[summary.lang] = summary
                }

                keyName.startsWith(SESSION_SUMMARY_PREFIX) -> {
                    val summary = json.decodeFromString<SessionSummaryDto>(rawValue).toDomain()
                    sessionSummaries[summary.lang] = summary
                }

                keyName.startsWith(FLASHCARD_SUMMARY_PREFIX) -> {
                    val summary = json.decodeFromString<FlashcardSummaryDto>(rawValue).toDomain()
                    flashcardSummaries[summary.lang] = summary
                }
            }
        }

        return GlobalLangState(
            userPref = userPref,
            langStates = langStates,
            dashSummaries = dashSummaries,
            sessionSummaries = sessionSummaries,
            flashcardSummaries = flashcardSummaries,
            isPreloaded = userPref != null,
            schema = GlobalLangState.SCHEMA
        )
    }

    private fun calculateRecentMinutes(input: LangStateUpdateInput): Int {
        // 분석 입력의 발화 길이를 대시보드/세션 요약용 분 단위 근사치로 바꾼다.
        val totalDurationMs = input.recentUserTurns.sumOf { it.durationMs ?: 0L }
        if (totalDurationMs <= 0L) return 0
        return (totalDurationMs / 60_000L).toInt()
    }

    private fun deltaFromInternal(value: Double): Int {
        // 성취 변화량은 현재 MVP에서 외부 표시용 정수 스냅샷으로만 남긴다.
        return (value * 100).toInt().coerceIn(0, 100)
    }

    private fun Map<LangCode, LangState>.ensureLangState(lang: LangCode): Map<LangCode, LangState> {
        return if (containsKey(lang)) this else this + (lang to LangState.initial(lang))
    }

    private fun Map<LangCode, DashSummary>.ensureDashSummary(lang: LangCode): Map<LangCode, DashSummary> {
        return if (containsKey(lang)) this else this + (lang to DashSummary.initial(lang))
    }

    private fun Map<LangCode, SessionSummary>.ensureSessionSummary(lang: LangCode): Map<LangCode, SessionSummary> {
        return if (containsKey(lang)) this else this + (lang to SessionSummary.initial(lang))
    }

    private fun Map<LangCode, FlashcardSummary>.ensureFlashcardSummary(lang: LangCode): Map<LangCode, FlashcardSummary> {
        return if (containsKey(lang)) this else this + (lang to FlashcardSummary.initial(lang))
    }

    private companion object {
        val USER_PREF_KEY = stringPreferencesKey("learning_user_pref")
        const val LANG_STATE_PREFIX = "learning_lang_state_"
        const val DASH_SUMMARY_PREFIX = "learning_dash_summary_"
        const val SESSION_SUMMARY_PREFIX = "learning_session_summary_"
        const val FLASHCARD_SUMMARY_PREFIX = "learning_flashcard_summary_"

        fun langStateKey(lang: LangCode) = stringPreferencesKey("$LANG_STATE_PREFIX${lang.code}")
        fun dashSummaryKey(lang: LangCode) =
            stringPreferencesKey("$DASH_SUMMARY_PREFIX${lang.code}")

        fun sessionSummaryKey(lang: LangCode) =
            stringPreferencesKey("$SESSION_SUMMARY_PREFIX${lang.code}")

        fun flashcardSummaryKey(lang: LangCode) =
            stringPreferencesKey("$FLASHCARD_SUMMARY_PREFIX${lang.code}")
    }
}
