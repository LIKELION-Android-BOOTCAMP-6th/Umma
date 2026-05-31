package com.app.umma.presentation.chat

import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.user.Topic

/**
 * AI Chat 화면의 UI 상태를 정의하는 데이터 클래스입니다.
 *
 * @property sessionState 전체 세션 연결 상태
 * @property aiState AI 동작 상태
 * @property activeSessionId 현재 활성 세션 ID
 * @property isRecording 현재 녹음 중 여부
 * @property isAwaitingUserTranscript 정지 버튼 이후 USER final transcript 확정을 기다리는 중인지 여부
 * @property userPartialTranscript 현재 사용자 partial transcript
 * @property aiPartialTranscript 현재 AI partial transcript
 * @property lastFinalUserTranscript 가장 최근 사용자 final transcript
 * @property lastFinalAITranscript 가장 최근 AI final transcript
 * @property lastHandledFinalTurnId 중복 append 방지를 위한 마지막 turnId
 * @property showSubtitle 자막 on/off 토글용 상태
 * @property inputLevel 입력 오디오 레벨
 * @property outputLevel 출력 오디오 레벨
 * @property isSavingTurn 현재 확정 turn 저장 중 여부
 * @property saveErrorMessage turn 저장 오류 메시지
 * @property reconnectAttempt 현재 자동 재연결 시도 횟수
 * @property maxReconnectAttempts 최대 자동 재연결 시도 횟수
 * @property isRecoverableError 사용자 재시도 가능 오류 여부
 * @property microphonePermissionDenied 마이크 권한 거부 여부
 * @property microphonePermissionPermanentlyDenied 마이크 권한 영구 거부 여부
 * @property fallbackMessage 재연결 폴백 시 메시지
 * @property didFallbackToNewSession 재연결 폴백 후 새 세션 실행 여부
 * @property errorMessage 세션 오류 메시지
 */
data class ChatUiState(
    val entryStage: ChatEntryStage = ChatEntryStage.IDLE,
    val blockedReason: ChatBlockedReason? = null,
    val sessionState: SessionState = SessionState.IDLE,
    val aiState: AIState = AIState.IDLE,
    val activeSessionId: String? = null,
    val isRecording: Boolean = false,
    val isAwaitingUserTranscript: Boolean = false,
    val userPartialTranscript: String = "",
    val aiPartialTranscript: String = "",
    val lastFinalUserTranscript: String = "",
    val lastFinalAITranscript: String = "",
    val userNickname: String = "",
    val lastHandledFinalTurnId: String? = null,
    val showSubtitle: Boolean = false,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val entryMessageOverride: String? = null,
    val errorMessage: String? = null,
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = 0,
    val isRecoverableError: Boolean = false,
    val microphonePermissionDenied: Boolean = false,
    val microphonePermissionPermanentlyDenied: Boolean = false,
    val didFallbackToNewSession: Boolean = false, // 재연결 시 fallback에 빠졌는 지 여부
    val fallbackMessage: String? = null, // 해당 fallback의 message
    val selectedTopic: List<Topic> = emptyList(), // 사용자가 선택한 관심 주제 목록, 5개여야 저장 가능
    /**
     * ChatScreen 진입 시 사용자의 interestTopics 가 비어있으면 true 로 설정.
     * Topic 5개 선택 후 저장 완료 시 false 로 변경.
     */
    val showTopicDialog: Boolean = false,
    // 저장 중 중복 클릭 방지용 true 일 때 버튼 비활성화
    val isTopicSaving: Boolean = false,
    val topicError: String? = null,
    val isSavingTurn: Boolean = false,
    val saveErrorMessage: String? = null,
) {
    /**
     * 유저가 발화를 시작할 수 있는 경우 ->
     * 현재 세션 준비가 되었고 녹음중이 아니고 AI가 말, 생각, 재연결 상태가 아닐 때
     * */
    val canStartUserTurn: Boolean
        get() = sessionState == SessionState.READY && !isRecording
                // AI가 생각하거나 말하는 동안에는 사용자가 새 turn을 시작할 수 없다.
                // 이 조건이 버튼 비활성화와 ViewModel 중복 녹음 방어의 공통 기준이 된다.
                && aiState != AIState.SPEAKING
                && aiState != AIState.THINKING
                && aiState != AIState.RECONNECTING

    /**
     * 유저가 발화를 끝낼 수 있는 경우 ->
     * 현재 녹음 중인 turn 이 있어 정지 버튼 입력을 받을 수 있는 경우
     * */
    val canEndUserTurn: Boolean
        get() = isRecording

    /**
     * 마이크 버튼이 화면에서 표현해야 하는 동작 상태입니다.
     *
     * ChatScreen 이 AIState / SessionState 조합을 직접 해석하면 같은 정책이 여러 곳으로
     * 퍼지므로, 버튼의 시작/정지/비활성 판단은 UiState 의 파생 상태로 고정합니다.
     */
    val micControlState: ChatMicControlState
        get() = when {
            // 녹음 중에는 사용자가 같은 버튼으로 turn을 끝낼 수 있어야 하므로 STOP이 최우선이다.
            canEndUserTurn -> ChatMicControlState.STOP
            // 세션이 준비되고 AI가 응답 중이 아니면 새 user turn을 시작할 수 있다.
            canStartUserTurn -> ChatMicControlState.START
            // 그 외 상태는 버튼 맥락은 유지하되 입력을 막는 disabled 표현으로 통일한다.
            else -> ChatMicControlState.DISABLED
        }

    /**
     * 마이크 버튼 주변에 표시할 짧은 상태 문구입니다.
     *
     * 정지 버튼 직후에는 aiState 가 아직 IDLE 일 수 있으므로 별도 flag 로
     * "발화 확정 대기" 상태를 보여준다.
     */
    val micStatusMessage: String?
        get() = when {
            // 사용자가 지금 해야 할 행동은 "말하기를 끝내려면 정지 버튼을 누르는 것"이다.
            isRecording -> "듣고 있어요. 정지 버튼을 누르면 Umma가 답변합니다."
            // 정지 버튼 직후 USER final transcript가 아직 도착하지 않은 짧은 구간이다.
            isAwaitingUserTranscript -> "당신의 말을 인식하고 있어요."
            // USER transcript 확정 이후 AI response 생성이 진행 중인 구간이다.
            aiState == AIState.THINKING -> "Umma가 답변을 준비하고 있어요."
            // AI 오디오가 재생되는 동안에는 새 입력을 받을 수 없다는 점을 알려준다.
            aiState == AIState.SPEAKING -> "Umma가 말하는 중입니다."
            // transport 재연결 중에는 입력 가능/불가능보다 연결 복구 상태가 더 중요하다.
            aiState == AIState.RECONNECTING || sessionState == SessionState.RECONNECTING ->
                "연결을 복구하고 있어요."
            else -> null
        }
}

/**
 * 하단 마이크 버튼이 사용자에게 보여줘야 하는 세 가지 상태입니다.
 *
 * START/STOP/DISABLED를 명시 enum으로 둔 이유는 ChatScreen이 `isRecording`, `aiState`,
 * `sessionState` 조합을 직접 해석하지 않게 하여 버튼 모양과 클릭 가능 조건을 한 곳에서 맞추기 위해서다.
 */
enum class ChatMicControlState {
    /** 사용자가 새 발화를 시작할 수 있는 상태 */
    START,
    /** 사용자가 현재 발화를 명시적으로 종료할 수 있는 상태 */
    STOP,
    /** 마이크 버튼 맥락은 유지하지만 지금은 입력을 받을 수 없는 상태 */
    DISABLED
}

enum class ChatEntryStage {
    IDLE,
    GUARDING,
    RESTORING,
    STARTING_NEW,
    BLOCKED_NETWORK,
    READY,
    ERROR
}

enum class ChatBlockedReason {
    MISSING_LANG,
    OFFLINE,
    UNRECOVERABLE
}

/**
 * 앱 레벨 세션 연결 상태입니다.
 */
enum class SessionState {
    /**
     * 세션 시작 전 초기 상태입니다.
     */
    IDLE,

    /**
     * 세션 연결 및 초기화 중 상태입니다.
     */
    LOADING,

    /**
     * 세션 준비 완료 상태입니다.
     */
    READY,

    /**
     * realtime transport 재연결 중 상태입니다.
     */
    RECONNECTING,

    /**
     * 세션 오류 상태입니다.
     */
    ERROR
}
