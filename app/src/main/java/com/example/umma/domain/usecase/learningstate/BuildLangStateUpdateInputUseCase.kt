package com.example.umma.domain.usecase.learningstate

import com.example.umma.domain.model.learningstate.ConversationTurn
import com.example.umma.domain.model.learningstate.CorrectionResult
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.realtime.SessionTurn
import java.security.MessageDigest
import javax.inject.Inject

/**
 * `LangStateUpdateInput`을 조립하는 LearningState 소유 정책.
 *
 * Correction, SRS, Realtime 같은 caller는 이미 확보한 domain 값만 넘기고,
 * 이 UseCase는 LS-006이 요구하는 입력 형태와 중복 방지 규칙을 고정한다.
 */
class BuildLangStateUpdateInputUseCase @Inject constructor() {

    operator fun invoke(
        command: BuildLangStateUpdateInputCommand
    ): Result<LangStateUpdateInput> = runCatching {
        val uid = command.uid.trim()
        // uid는 동일 사용자 기준의 sessionMemoryKey와 analysisEventId를 묶는 가장 먼저 필요한 키다.
        require(uid.isNotBlank()) {
            "uid must not be blank"
        }
        // 완료 요청은 현재 전역에서 선택된 언어와 같은 스코프에서만 조립해야 한다.
        require(command.selectedLang == command.lang) {
            "selected language mismatch: selected=${command.selectedLang}, request=${command.lang}"
        }

        // currentState는 LS-006 이동 평균 계산의 기준점이다. 없으면 다음 상태를 만들 수 없다.
        val currentState = command.currentState
            ?: throw IllegalStateException("current LangState is not available for ${command.lang}")

        // Correction 입력에는 분석 기준이 되는 USER turn만 남기고, AI turn과 공백 발화는 제거한다.
        val recentUserTurns = command.correctionContextTurns
            .filter { turn -> turn.role == TurnSpeaker.USER && turn.text.isNotBlank() }
            .map { turn -> turn.toConversationTurn() }

        require(recentUserTurns.isNotEmpty()) {
            "correction context must contain at least one meaningful USER turn"
        }

        // caller가 이미 선택한 카드나 식별 단서만 fingerprint 재료로 쓴다.
        val stableEventParts = command.stableEventParts
            .map(String::trim)
            .filter(String::isNotBlank)

        require(stableEventParts.isNotEmpty()) {
            "stable event parts must not be empty"
        }

        // 같은 사용자와 같은 언어는 같은 Session Memory 스코프를 공유해야 한다.
        val sessionMemoryKey = buildSessionMemoryKey(
            uid = uid,
            lang = command.lang
        )

        // 재시도 시에도 동일한 완료 요청이면 같은 analysisEventId가 나오도록 고정한다.
        val analysisEventId = buildAnalysisEventId(
            uid = uid,
            lang = command.lang,
            stableEventParts = stableEventParts,
            sourceTurns = command.correctionContextTurns
        )

        // LS-006 입력은 준비만 하고, 저장과 후속 반영은 ApplyLanguageStateUpdateUseCase와 Repository가 맡는다.
        LangStateUpdateInput(
            uid = uid,
            lang = command.lang,
            sessionMemoryKey = sessionMemoryKey,
            analysisEventId = analysisEventId,
            currentState = currentState,
            preparedState = null,
            recentUserTurns = recentUserTurns,
            correctionResult = command.correctionResult,
            correctionAvailableOverride = command.correctionAvailableOverride,
            // Correction 완료 흐름은 복습 이벤트를 만들지 않는다. SRS 복습 반영은 별도 입력 경로가 책임진다.
            flashcardReviewEvents = emptyList(),
            analyzedAt = command.analyzedAt,
            forceReanalysis = command.forceReanalysis
        )
    }

    private fun SessionTurn.toConversationTurn(): ConversationTurn {
        // LS 모델은 SessionTurn 전체가 아니라 분석에 필요한 발화 본문과 보조 지표만 보존한다.
        return ConversationTurn(
            speaker = role,
            text = text.trim(),
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    private fun buildSessionMemoryKey(
        uid: String,
        lang: LangCode
    ): String {
        // Session Memory는 개별 LiveSession이 아니라 사용자와 학습 언어 단위로 누적된다.
        return "${uid}_${lang.code}"
    }

    private fun buildAnalysisEventId(
        uid: String,
        lang: LangCode,
        stableEventParts: List<String>,
        sourceTurns: List<SessionTurn>
    ): String {
        // requestedAt처럼 매번 바뀌는 값은 제외하고, 같은 완료 요청이면 같은 fingerprint가 나오게 한다.
        val turnFingerprint = sourceTurns
            .filter { turn -> turn.role == TurnSpeaker.USER && turn.text.isNotBlank() }
            .map { turn ->
                // turn 자체를 그대로 쓰지 않고, 중복 방지에 필요한 최소 보조 필드만 문자열화한다.
                listOf(
                    turn.turnId.trim(),
                    turn.text.trim(),
                    turn.createdAt.toString(),
                    turn.tokenCount?.toString().orEmpty(),
                    turn.durationMs?.toString().orEmpty(),
                    turn.confidence?.toString().orEmpty()
                ).joinToString(separator = "|")
            }

        val rawFingerprint = buildString {
            append(uid)
            append("::")
            append(lang.code)
            append("::parts=")
            append(stableEventParts.sorted().joinToString(separator = ","))
            append("::turns=")
            append(turnFingerprint.joinToString(separator = ","))
        }

        return "ls-analysis:${lang.code}:${sha256(rawFingerprint).take(24)}"
    }

    /**
     * 분석용 fingerprint를 짧고 안정적인 식별자로 바꾸기 위한 해시 함수.
     *
     * 이 값은 보안 목적이 아니라, `analysisEventId`를 고정 길이로 만들고
     * 로그/저장소 키에 그대로 넣기 쉬운 형태로 정규화하는 데 사용한다.
     */
    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/**
 * caller가 이미 확보한 값만 담는 조립 명령.
 *
 * 이 모델이 Correction 저장 요청이나 화면 상태를 직접 참조하지 않도록 유지해야
 * LearningState 정책을 다른 완료 흐름에서도 재사용할 수 있다.
 */
data class BuildLangStateUpdateInputCommand(
    val uid: String,
    val lang: LangCode,
    // 현재 전역에서 사용자가 보고 있는 언어. stale completion 차단에 사용한다.
    val selectedLang: LangCode,
    // LS-006 이동 평균 계산에 필요한 기준 상태.
    val currentState: LangState?,
    // RT-003 correction context에서 받아온 원본 turn 목록.
    val correctionContextTurns: List<SessionTurn>,
    // 완료 재시도 시 동일 요청임을 식별할 수 있는 입력 재료.
    val stableEventParts: List<String>,
    // 완료 시각 기준. analysisEventId 자체에는 넣지 않고 result 기록용으로만 사용한다.
    val analyzedAt: Long,
    // Correction 완료에서 얻은 교정 결과 요약. LS는 이 값을 받아 metrics를 계산한다.
    val correctionResult: CorrectionResult? = null,
    // 교정이 끝난 직후에는 correctionAvailable를 false로 내려야 다음 재진입 조건이 맞는다.
    val correctionAvailableOverride: Boolean? = null,
    val forceReanalysis: Boolean = false
)
