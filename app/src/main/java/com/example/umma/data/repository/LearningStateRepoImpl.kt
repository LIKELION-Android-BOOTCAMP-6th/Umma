package com.example.umma.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.umma.data.model.learningstate.DashSummaryDto
import com.example.umma.data.model.learningstate.FlashcardSummaryDto
import com.example.umma.data.model.learningstate.LangStateDto
import com.example.umma.data.model.learningstate.SessionSummaryDto
import com.example.umma.data.model.learningstate.UserLangPrefDto
import com.example.umma.data.model.learningstate.toDomain
import com.example.umma.data.model.learningstate.toDto
import com.example.umma.data.source.remote.LearningStateRemote
import com.example.umma.data.source.remote.LearningStateRemoteDataSource
import com.example.umma.data.source.remote.LearningStateRemoteUpdate
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.LearningStateUpdateResult
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.repository.AuthRepository
import com.example.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val dataStore: DataStore<Preferences>,
    private val remoteDataSource: LearningStateRemoteDataSource,
    private val authRepository: AuthRepository
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
        // 언어 선택은 UX상 즉시 반영하고, Firestore 반영은 sync가 처리하게 둔다.
        return persistStateSafely(
            addPendingSyncKeys = setOf(
                PendingSyncKey.userPref(),
                PendingSyncKey.langState(lang),
                PendingSyncKey.dashSummary(lang),
                PendingSyncKey.sessionSummary(lang),
                PendingSyncKey.flashcardSummary(lang)
            )
        ) { current ->
            // 현재 선택 언어만 바꾸고, 언어별 요약은 없으면 기본값으로 채운다.
            //   기존 userPref 가 있으면 selectedLang 갱신 + learningLangs 자동 확장.
            //   userPref 가 아직 없는 신규 사용자/mock 진입 케이스(=Dashboard 진입 시
            //   닉네임/언어 다이얼로그를 거치지 않은 상태) 에서도 selector 로 첫 언어 설정이
            //   동작하도록 UserLangPref.initial 로 최소 정보 userPref 를 생성한다. nativeLang
            //   기본값은 KO. 이후 sync / Initial Setup 흐름에서 더 정확한 값으로 덮어쓰임.
            val existingPref = current.userPref
            val normalizedPref = if (existingPref != null) {
                existingPref.copy(
                    selectedLang = lang,
                    learningLangs = existingPref.learningLangs.toMutableSet().apply { add(lang) }.toList()
                )
            } else {
                UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = lang)
            }

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

    override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<LearningStateUpdateResult> {
        val preparedState = input.preparedState ?: return Result.failure(
            IllegalArgumentException("preparedState is required for updateLanguageState")
        )

        return try {
            val current = _state.value
            val lang = input.lang

            // 저장소에서도 한 번 더 중복 분석을 막는다.
            // stale currentState가 UseCase로 들어와도 실제 cache 기준으로 idempotent 하게 동작한다.
            val cachedState = current.langStates[lang]
            if (!input.forceReanalysis &&
                input.analysisEventId != null &&
                cachedState?.lastAnalysisEventId == input.analysisEventId
            ) {
                return Result.success(
                    LearningStateUpdateResult(
                        lang = lang,
                        savedState = cachedState,
                        sourceEventId = input.analysisEventId,
                        applied = false,
                        updatedAt = cachedState.updatedAt ?: input.analyzedAt
                    )
                )
            }

            // LS-006에서 계산된 결과를 그대로 반영하고, 화면용 요약은 함께 갱신한다.
            val measuredMinutes = calculateRecentMinutes(input)
            val hasUserTurns =
                input.recentUserTurns.any { it.speaker == com.example.umma.domain.model.learningstate.TurnSpeaker.USER }
            val correctionAvailable = input.correctionAvailableOverride ?: hasUserTurns

            val updatedDash = current.dashSummaries[lang]
                ?: DashSummary.initial(lang)
            val updatedSession = current.sessionSummaries[lang]
                ?: SessionSummary.initial(lang)

            val nextState = current.copy(
                langStates = current.langStates + (lang to preparedState),
                dashSummaries = current.dashSummaries + (
                        lang to updatedDash.copy(
                            recentMinutes = measuredMinutes,
                            correctionAvailable = correctionAvailable,
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
                            correctionAvailable = correctionAvailable,
                            updatedAt = input.analyzedAt
                        )
                ),
                isPreloaded = true
            )
            // 이 배치로 바뀐 항목만 pending sync 대상으로 기록한다.
            persistSnapshot(
                state = nextState,
                addPendingSyncKeys = setOf(
                    PendingSyncKey.langState(lang),
                    PendingSyncKey.dashSummary(lang),
                    PendingSyncKey.sessionSummary(lang)
                )
            )
            _state.value = nextState

            Result.success(
                LearningStateUpdateResult(
                    lang = lang,
                    savedState = preparedState,
                    sourceEventId = input.analysisEventId ?: "${lang.code}:${input.analyzedAt}",
                    applied = true,
                    updatedAt = input.analyzedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateFlashcardSummary(
        input: FlashcardSummaryUpdateInput
    ): Result<FlashcardSummaryUpdateResult> {
        if (input.uid.isBlank()) {
            return Result.failure(IllegalArgumentException("uid must not be blank"))
        }

        return try {
            val current = _state.value
            val lang = input.lang
            val previousFlashcard = current.flashcardSummaries[lang] ?: FlashcardSummary.initial(lang)
            val previousDash = current.dashSummaries[lang] ?: DashSummary.initial(lang)

            // SRS가 계산한 원본 수치를 FlashcardSummary와 Dashboard Summary에 같은 값으로 반영한다.
            // 이렇게 해야 Dashboard 카드와 SRS 진입 화면이 서로 다른 due count를 보지 않는다.
            val nextFlashcard = previousFlashcard.copy(
                dueFlashcards = input.dueFlashcards,
                savedFlashcards = input.savedFlashcards,
                updatedAt = input.updatedAt
            )
            val nextDash = previousDash.copy(
                dueFlashcards = input.dueFlashcards,
                savedFlashcards = input.savedFlashcards,
                updatedAt = input.updatedAt
            )

            val applied = nextFlashcard != previousFlashcard || nextDash != previousDash
            val nextState = current.copy(
                flashcardSummaries = current.flashcardSummaries + (lang to nextFlashcard),
                dashSummaries = current.dashSummaries + (lang to nextDash),
                isPreloaded = true
            )
            // SRS due count는 카드 요약과 대시보드 요약이 동시에 같아야 하므로 같은 pending 키를 남긴다.
            persistSnapshot(
                state = nextState,
                addPendingSyncKeys = setOf(
                    PendingSyncKey.flashcardSummary(lang),
                    PendingSyncKey.dashSummary(lang)
                )
            )
            _state.value = nextState

            Result.success(
                FlashcardSummaryUpdateResult(
                    lang = lang,
                    flashcardSummary = nextFlashcard,
                    dashSummary = nextDash,
                    applied = applied,
                    sourceEventId = input.sourceEventId,
                    updatedAt = input.updatedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
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

        return persistStateSafely(
            addPendingSyncKeys = setOf(
                PendingSyncKey.userPref(),
                PendingSyncKey.langState(userPref.primaryLang),
                PendingSyncKey.dashSummary(userPref.primaryLang),
                PendingSyncKey.sessionSummary(userPref.primaryLang),
                PendingSyncKey.flashcardSummary(userPref.primaryLang)
            )
        ) {
            // Initial Setup은 primaryLang을 기준으로 하나의 시작 스냅샷을 만든다.
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
        addPendingSyncKeys: Set<String> = emptySet(),
        transform: (GlobalLangState) -> GlobalLangState
    ): Result<Unit> {
        return try {
            // 먼저 다음 상태를 계산하고, 저장이 성공해야만 메모리 스냅샷도 바꾼다.
            val nextState = transform(_state.value)
            persistSnapshot(
                state = nextState,
                addPendingSyncKeys = addPendingSyncKeys
            )
            _state.value = nextState
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun persistSnapshot(
        state: GlobalLangState,
        addPendingSyncKeys: Set<String> = emptySet(),
        pendingSyncKeysOverride: Set<String>? = null
    ) {
        dataStore.edit { prefs ->
            // 기존 pending 키를 유지하면서, 이번 저장에서 추가된 dirty 항목을 합친다.
            val previousPendingKeys = prefs[PENDING_SYNC_KEYS].orEmpty()
            val pendingKeys = pendingSyncKeysOverride ?: (previousPendingKeys + addPendingSyncKeys)
            writeSnapshot(
                prefs = prefs,
                state = state,
                pendingSyncKeys = pendingKeys
            )
        }
    }

    private fun writeSnapshot(
        prefs: MutablePreferences,
        state: GlobalLangState,
        pendingSyncKeys: Set<String>
    ) {
        // MVP는 단일 스냅샷 전략을 택해, 저장 전 기존 키를 비우고 다시 쓴다.
        // pending sync key는 같은 DataStore에 보존해야 앱 재시작 후에도 remote write-back을 재시도할 수 있다.
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

        // pending key가 남아 있으면 아직 Firestore write-back이 완료되지 않았다는 뜻이다.
        if (pendingSyncKeys.isNotEmpty()) {
            prefs[PENDING_SYNC_KEYS] = pendingSyncKeys
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
        val PENDING_SYNC_KEYS = stringSetPreferencesKey("learning_pending_sync_keys")

        fun langStateKey(lang: LangCode) = stringPreferencesKey("$LANG_STATE_PREFIX${lang.code}")
        fun dashSummaryKey(lang: LangCode) =
            stringPreferencesKey("$DASH_SUMMARY_PREFIX${lang.code}")

        fun sessionSummaryKey(lang: LangCode) =
            stringPreferencesKey("$SESSION_SUMMARY_PREFIX${lang.code}")

        fun flashcardSummaryKey(lang: LangCode) =
            stringPreferencesKey("$FLASHCARD_SUMMARY_PREFIX${lang.code}")
    }

    /**
     * LearningState의 background sync 진입점.
     *
     * pending write가 있으면 local snapshot을 Firestore에 먼저 반영하고,
     * pending write가 없으면 Firestore 최신 snapshot을 받아 local cache를 갱신한다.
     *
     * 왜 write-back을 먼저 하나:
     *  - Correction/SRS는 local-first로 사용자 완료를 먼저 만든다.
     *  - 이 상태에서 remote fetch를 먼저 하면 아직 Firestore에 없는 local 완료 상태가
     *    오래된 remote snapshot으로 덮일 수 있다.
     *  - 따라서 pending marker가 남아 있으면 Firestore commit 성공 후에만 marker를 지운다.
     *
     * 에러 정책:
     *  - write-back 실패: local snapshot과 pending marker 유지, 다음 sync에서 재시도
     *  - fetch 실패: local cache 유지, 호출자가 fallback UI 처리
     */
    override suspend fun sync(): Result<Unit> {
        return try {
            // 1, 2.
            val userUid = authRepository.currentUserUid.first()
            if (userUid.isNullOrBlank()) {
                return Result.success(Unit)
            }

            val prefs = dataStore.data.first()
            val pendingSyncKeys = prefs[PENDING_SYNC_KEYS].orEmpty()

            if (pendingSyncKeys.isNotEmpty()) {
                // local-first 변경이 남아 있으면 remote fetch보다 write-back을 먼저 수행한다.
                // 그렇지 않으면 아직 Firestore에 없는 local 완료 상태를 오래된 remote 값으로 덮을 수 있다.
                val localState = readSnapshot(prefs)
                val update = localState.toRemoteUpdate(pendingSyncKeys)
                remoteDataSource.sync(userUid, update).getOrThrow()

                // Firestore commit이 성공한 뒤에만 pending key를 지운다.
                // 실패 시에는 catch로 빠져 DataStore의 pending key가 그대로 남는다.
                persistSnapshot(
                    state = localState,
                    pendingSyncKeysOverride = emptySet()
                )
                _state.value = localState
            } else {
                // pending write가 없을 때만 remote snapshot을 받아 local cache를 최신화한다.
                val remote = remoteDataSource.fetch(userUid)

                val latestPrefs = dataStore.data.first()
                val latestPendingKeys = latestPrefs[PENDING_SYNC_KEYS].orEmpty()
                if (latestPendingKeys.isNotEmpty()) {
                    // fetch 대기 중 화면에서 local-first 변경이 생겼다면 remote snapshot을 저장하지 않는다.
                    // Dashboard 언어 변경 같은 최신 local intent가 오래된 remote 값으로 되돌아가는 것을 막는다.
                    val localState = readSnapshot(latestPrefs)
                    val update = localState.toRemoteUpdate(latestPendingKeys)
                    remoteDataSource.sync(userUid, update).getOrThrow()
                    persistSnapshot(
                        state = localState,
                        pendingSyncKeysOverride = emptySet()
                    )
                    _state.value = localState
                    return Result.success(Unit)
                }

                val next = remote.toGlobalLangState()
                // fetch는 restore 경로라 pending key를 새로 만들지 않는다.
                persistSnapshot(next)
                _state.value = next
            }

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Remote DTO 묶음 → GlobalLangState 변환.
     *
     * preload() 의 readSnapshot() 과 변환 결과는 같지만 입력 형태가 달라서 분리:
     *  - readSnapshot: Preferences 의 prefix key 기반
     *  - 여기: List 기반 (Firestore 컬렉션 결과)
     */
    private fun LearningStateRemote.toGlobalLangState(): GlobalLangState {
        return GlobalLangState(
            userPref = userPref?.toDomain(),
            langStates = langStates.associate { dto ->
                dto.toDomain().let { it.lang to it }
            },
            dashSummaries = dashSummaries.associate { dto ->
                dto.toDomain().let { it.lang to it }
            },
            sessionSummaries = sessionSummaries.associate { dto ->
                dto.toDomain().let { it.lang to it }
            },
            flashcardSummaries = flashcardSummaries.associate { dto ->
                dto.toDomain().let { it.lang to it }
            },
            isPreloaded = true,
            schema = GlobalLangState.SCHEMA
        )
    }

    private fun GlobalLangState.toRemoteUpdate(pendingSyncKeys: Set<String>): LearningStateRemoteUpdate {
        // 전체 snapshot을 덮지 않고 dirty로 표시된 항목만 Firestore write-back payload로 만든다.
        return LearningStateRemoteUpdate(
            userPref = userPref
                ?.takeIf { PendingSyncKey.userPref() in pendingSyncKeys }
                ?.toDto(),
            langStates = langStates
                .filterKeys { lang -> PendingSyncKey.langState(lang) in pendingSyncKeys }
                .values
                .map { it.toDto() },
            dashSummaries = dashSummaries
                .filterKeys { lang -> PendingSyncKey.dashSummary(lang) in pendingSyncKeys }
                .values
                .map { it.toDto() },
            sessionSummaries = sessionSummaries
                .filterKeys { lang -> PendingSyncKey.sessionSummary(lang) in pendingSyncKeys }
                .values
                .map { it.toDto() },
            flashcardSummaries = flashcardSummaries
                .filterKeys { lang -> PendingSyncKey.flashcardSummary(lang) in pendingSyncKeys }
                .values
                .map { it.toDto() }
        )
    }

    private object PendingSyncKey {
        // 사람이 읽어도 어떤 항목이 dirty인지 바로 알 수 있도록 문자열을 고정한다.
        fun userPref(): String = "user_pref"
        fun langState(lang: LangCode): String = "lang_state:${lang.code}"
        fun dashSummary(lang: LangCode): String = "dash_summary:${lang.code}"
        fun sessionSummary(lang: LangCode): String = "session_summary:${lang.code}"
        fun flashcardSummary(lang: LangCode): String = "flashcard_summary:${lang.code}"
    }
}
