package com.app.umma.domain.repository

import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.flow.Flow

/**
 * AI Chat의 실시간 세션 transport를 추상화한 repository 계약입니다.
 *
 * 이 repository는 OpenAI Realtime 같은 실시간 세션 기반 백엔드와의 연결,
 * 오디오/텍스트 전송, 서버 이벤트 수신을 담당합니다.
 *
 * 책임 범위:
 * - 실시간 세션 시작/종료
 * - 현재 활성 세션 ID 조회
 * - 사용자 오디오 chunk 전송
 * - 테스트용 텍스트 payload 전송
 * - 서버에서 들어오는 실시간 이벤트 스트림 노출
 *
 */
interface ChatRepository {
    /**
     * 지정한 언어와 system instruction을 기준으로 실시간 AI 세션을 시작합니다.
     *
     * - 이미 동일 조건의 유효 세션이 있으면 재사용
     * - 그렇지 않으면 새 realtime transport session 생성
     *
     * @param langCode 현재 대화에 사용할 학습 언어
     * @param systemInstruction 세션 시작 시 모델에 주입할 system prompt
     * @param outputAudioSpeed 학습자 수준에 맞춘 AI 음성 출력 속도. `1.0`이 기본 속도입니다.
     * @param systemInstructionDebugTrace 세션 prompt에 반영된 정책 추적 정보. prompt 본문은 포함하지 않습니다.
     * @return 성공 시 활성 세션 ID를 담은 [Result], 실패 시 예외를 담은 [Result]
     */
    suspend fun startSession(
        langCode: LangCode,
        systemInstruction: String,
        outputAudioSpeed: Double,
        systemInstructionDebugTrace: String? = null
    ): Result<String>

    /**
     * 기존 앱 레벨 세션 ID를 유지한 채 realtime transport만 다시 연결합니다.
     *
     * 자동 재연결 실패 후 사용자가 명시적으로 재시도할 때 사용하며,
     * 호출자는 최신 context를 반영한 [systemInstruction]을 전달해야 합니다.
     *
     * @param systemInstruction 새 realtime session에 주입할 최신 system prompt
     * @param outputAudioSpeed 최신 학습자 profile 기준으로 다시 계산한 AI 음성 출력 속도
     * @param systemInstructionDebugTrace 재연결 prompt에 반영된 정책 추적 정보. prompt 본문은 포함하지 않습니다.
     * @return 성공 시 유지된 활성 세션 ID를 담은 [Result], 실패 시 예외를 담은 [Result]
     */
    suspend fun reconnectSession(
        systemInstruction: String,
        outputAudioSpeed: Double,
        systemInstructionDebugTrace: String? = null
    ): Result<String>

    /**
     * 현재 활성화된 앱 레벨 세션 ID를 반환합니다.
     *
     * 이 값은 transport session 객체 자체의 식별자가 아니라,
     * 앱이 현재 대화 흐름을 추적하기 위해 관리하는 식별자입니다.
     *
     * @return 활성 세션 ID, 없으면 `null`
     */
    fun getActiveSessionId(): String?

    fun getCurrentSessionLang(): LangCode?

    /**
     * 사용자의 오디오 chunk를 실시간 세션으로 전송합니다.
     *
     * 오디오는 구현체가 요구하는 포맷으로 전달되어야 하며,
     * PCM chunk를 반복적으로 보내는 용도로 사용합니다.
     *
     * @param audio 전송할 오디오 바이트 배열
     */
    suspend fun sendAudioData(audio: ByteArray)

    /**
     * 사용자가 정지 버튼을 눌러 현재 user turn 을 끝냈음을 transport 에 알립니다.
     *
     * OpenAI Realtime 구현은 이 시점에 input audio commit 을 수행하고 USER transcript 확정 이후
     * AI response 를 시작합니다.
     *
     * @param durationMs 현재 user turn 의 발화 길이. final USER transcript metadata 로 사용합니다.
     */
    fun endUserTurn(durationMs: Long?)

    /**
     * 아직 commit 되지 않은 user turn metadata 와 commit 대기 상태를 취소합니다.
     *
     * 화면 이탈, 새 발화 시작 전 초기화, 녹음 중단 같은 cleanup 경로에서 사용합니다.
     */
    fun cancelPendingUserTurn()

    /**
     * 텍스트 payload를 실시간 세션으로 전송합니다.
     *
     * 사용자 AI Chat 메인 플로우에서는 사용하지 않고,
     * 내부 테스트나 연결 검증 용도로만 유지하는 보조 API입니다.
     *
     * @param text 전송할 텍스트
     */
    suspend fun sendTextData(text: String)

    /**
     * 실시간 AI 이벤트 스트림을 관찰합니다.
     *
     * 이 스트림에는 세션 초기화, partial/final transcript,
     * 오디오 응답, 상태 변화, 오류 같은 이벤트가 포함됩니다.
     *
     * @return [AIEvent] 흐름
     */
    fun observeAIEvent(): Flow<AIEvent>

    /**
     * 현재 활성 세션을 종료하고 관련 리소스를 정리합니다.
     *
     * 구현체는 세션 close, 내부 coroutine 정리, 버퍼 초기화 등을 수행해야 합니다.
     */
    suspend fun stopSession(clearAppSession: Boolean = true)
}
