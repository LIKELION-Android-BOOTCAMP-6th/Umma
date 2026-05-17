package com.example.umma.data.repository

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.model.realtime.AIState
import com.example.umma.domain.repository.ChatRepository
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerMessage
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

/**
 * Gemini Live API를 사용하여 실시간 음성 대화 인프라를 구현하는 리포지토리입니다.
 *
 * 세션의 생명주기 관리, 오디오 데이터 스트리밍, 서버 메시지 파싱 및
 * 대화 상태 관리를 담당합니다. 모든 세션 조작은 [sessionMutex]를 통해 스레드 안전하게 보호됩니다.
 *
 * @property firebaseAI Gemini Live API 연결을 위한 Firebase AI 인스턴스
 */
class ChatRepositoryImpl @OptIn(PublicPreviewAPI::class)
@Inject constructor(
    private val firebaseAI: FirebaseAI
) : ChatRepository {

    /** 세션 중복 생성 및 동시 접근을 방지하기 위한 뮤텍스 */
    private val sessionMutex = Mutex()

    /** 현재 활성화된 Gemini Live 세션 */
    @OptIn(PublicPreviewAPI::class)
    private var session: LiveSession? = null

    /** 현재 세션의 학습 언어 코드 */
    private var currentLang: LangCode? = null

    /** 현재 활성화된 앱 레벨 세션 고유 식별자 (UUID) */
    private var activeSessionId: String? = null

    /** 세션 메시지 수신 및 비동기 작업을 관리하는 백그라운드 코루틴 스코프 */
    private var scope: CoroutineScope? = null

    /** 실시간 이벤트를 전파하는 SharedFlow */
    private val _events = MutableSharedFlow<AIEvent>(extraBufferCapacity = 64)

    /** 현재 세션에 적용된 시스템 지침 캐시 */
    private var currentSystemInstruction: String? = null

    /** 사용자 발화 자막 임시 버퍼 */
    private var userTranscriptBuffer: String = ""

    /** AI 응답 자막 임시 버퍼 */
    private var aiTranscriptionBuffer: String = ""

    /**
     * 새로운 대화 세션을 시작하거나 기존 유효 세션을 반환합니다.
     *
     * @param langCode 학습 대상 언어
     * @param systemInstruction AI에게 전달할 페르소나 및 학습 지침
     * @return 성공 시 세션 ID, 실패 시 예외를 포함한 [Result]
     */
    @OptIn(PublicPreviewAPI::class)
    override suspend fun startSession(
        langCode: LangCode,
        systemInstruction: String
    ): Result<String> {
        return sessionMutex.withLock {
            // 기존 세션이 동일한 조건(언어, 지침)으로 이미 존재하면 재사용합니다.
            if (session != null && activeSessionId != null && currentLang == langCode && currentSystemInstruction == systemInstruction) {
                return Result.success(activeSessionId!!)
            }
            try {
                // 이전 세션 및 리소스를 깨끗하게 정리합니다.
                stopInternal()

                _events.emit(AIEvent.Initializing)

                // Gemini Live 모델 설정 (음성/자막 활성화)
                val liveModel = firebaseAI.liveModel(
                    modelName = "gemini-3.1-flash-live-preview",
                    systemInstruction = content {
                        text(systemInstruction)
                    },
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                    }
                )

                // 서버에 연결하여 세션을 생성합니다.
                val newSession = liveModel.connect()
                session = newSession

                /** 앱 레벨에서 관리할 고유 세션 ID 생성 및 상태 캐싱 */
                val newId = UUID.randomUUID().toString()
                activeSessionId = newId
                currentSystemInstruction = systemInstruction
                currentLang = langCode

                /** 서버 메시지 수신을 위한 IO 디스패처 기반의 백그라운드 루프 시작 */
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                scope?.launch {
                    newSession.receive().collect { message ->
                        handleServerMessage(message)
                    }
                }

                /** 세션 준비 완료 이벤트 발행 */
                _events.emit(AIEvent.Initialized(newId))
                Result.success(newId)

            } catch (e: Exception) {
                // 연결 실패 시 리소스 정리 및 에러 전파
                stopInternal()
                _events.emit(AIEvent.Error(e.message ?: "Connection Failed"))
                Result.failure(e)
            }
        }
    }

    /**
     * 서버 메시지를 해석해 domain 이벤트로 변환합니다.
     *
     * [LiveServerContent]
     * - input/output transcription 처리
     * - inline audio 처리
     * - turnComplete 시 final transcript 발행
     *
     * [LiveServerSetupComplete]
     * - 세션 준비 완료 상태 반영
     *
     * [LiveServerGoAway]
     * - 즉시 세션을 닫지 않고 interruption 이벤트로 surface
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun handleServerMessage(message: LiveServerMessage) {
        when (message) {
            is LiveServerContent -> handleServerContent(message)
            is LiveServerSetupComplete -> {
                _events.emit(AIEvent.StateChanged(AIState.IDLE))
            }
            is LiveServerGoAway -> {
                _events.emit(AIEvent.StateChanged(AIState.RECONNECTING))
                _events.emit(
                    AIEvent.SessionInterrupted(
                        message = "Live Session interrupted by server"
                    )
                )
            }
        }
    }

    /**
     * 실제 transcript/audio/turnComplete를 포함하는 content 메시지를 처리합니다.
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun handleServerContent(message: LiveServerContent) {
        message.inputTranscription?.text // 유저 발화
            ?.takeIf { it.isNotBlank() }
            ?.let { text ->
                userTranscriptBuffer = text
                _events.emit(
                    AIEvent.PartialTranscription(
                        text = text,
                        role = TurnSpeaker.USER
                    )
                )
                _events.emit(AIEvent.StateChanged(AIState.LISTENING))
            }

        message.outputTranscription?.text // AI 발화
            ?.takeIf { it.isNotBlank() }
            ?.let { text ->
                aiTranscriptionBuffer = text
                _events.emit(
                    AIEvent.PartialTranscription(
                        text = text,
                        role = TurnSpeaker.AI
                    )
                )
                _events.emit(AIEvent.StateChanged(AIState.SPEAKING))
            }

        message.content // 오디오 데이터 스트림
            ?.parts
            ?.filterIsInstance<InlineDataPart>()
            ?.forEach { part ->
                _events.emit(AIEvent.AudioResponse(part.inlineData))
            }

        if (message.turnComplete) {
            emitFinalTranscripts()
            _events.emit(AIEvent.StateChanged(AIState.IDLE))
        }

        if (message.interrupted) {
            aiTranscriptionBuffer = ""
            _events.emit(AIEvent.StateChanged(AIState.IDLE))
        }
    }

    /**
     * 버퍼에 저장된 임시 자막들을 최종 확정([AIEvent.FinalTranscription]) 이벤트로 발행합니다.
     */
    private suspend fun emitFinalTranscripts() {
        if (userTranscriptBuffer.isNotBlank()) {
            _events.emit(AIEvent.FinalTranscription(userTranscriptBuffer, TurnSpeaker.USER))
            userTranscriptBuffer = ""
        }
        if (aiTranscriptionBuffer.isNotBlank()) {
            _events.emit(AIEvent.FinalTranscription(aiTranscriptionBuffer, TurnSpeaker.AI))
            aiTranscriptionBuffer = ""
        }
    }

    /**
     * 외부 노출 없이 내부 리소스(세션, 코루틴, 버퍼)만 즉시 정리합니다.
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun stopInternal() {
        scope?.cancel()
        scope = null

        session?.let { currentSession ->
            if (!currentSession.isClosed()) {
                currentSession.close()
            }
        }

        session = null
        activeSessionId = null
        currentSystemInstruction = null
        currentLang = null
        userTranscriptBuffer = ""
        aiTranscriptionBuffer = ""
    }

    /**
     * 사용자 오디오 데이터를 서버로 전송합니다. (16k PCM)
     */
    @OptIn(PublicPreviewAPI::class)
    override suspend fun sendAudioData(audio: ByteArray) {
        session?.sendAudioRealtime(
            InlineData(
                data = audio,
                mimeType = "audio/pcm;rate=16000"
            )
        )
    }

    /**
     * 텍스트 데이터를 서버로 전송합니다. (테스트용)
     */
    @OptIn(PublicPreviewAPI::class)
    override suspend fun sendTextData(text: String) {
        session?.sendTextRealtime(text)
    }

    /**
     * AI 이벤트 스트림을 구독합니다.
     */
    override fun observeAIEvent(): Flow<AIEvent> = _events.asSharedFlow()

    /**
     * 현재 활성화된 세션 ID를 조회합니다.
     */
    override fun getActiveSessionId(): String? = activeSessionId

    /**
     * 활성 세션을 종료하고 리소스를 정리합니다. 뮤텍스를 통해 안전하게 처리됩니다.
     */
    @OptIn(PublicPreviewAPI::class)
    override suspend fun stopSession() = sessionMutex.withLock {
        stopInternal()
    }
}