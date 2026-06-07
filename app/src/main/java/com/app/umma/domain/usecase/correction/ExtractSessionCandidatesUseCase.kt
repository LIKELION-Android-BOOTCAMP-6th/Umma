package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.SessionTurn
import javax.inject.Inject

/**
 * RT-003 Session Memory read model 을 Correction 후보 추출 입력으로 변환하는 UseCase 입니다.
 *
 * Session Memory 의 저장/조회 책임은 Realtime-infra 에 있고,
 * 어떤 user turn 을 교정 후보로 확정할지는 Correction domain 의 [ExtractCandidatesUseCase]가 담당합니다.
 * 이 UseCase 는 두 시스템 경계 사이에서 모델 변환과 원본 turnId 보존만 수행합니다.
 */
class ExtractSessionCandidatesUseCase @Inject constructor(
    private val extractCandidatesUseCase: ExtractCandidatesUseCase
) {

    operator fun invoke(
        selectedLang: LangCode,
        sessionLang: LangCode,
        sessionTurns: List<SessionTurn>
    ): List<CorrectionCandidate> {
        // RT-003 read model 은 오래된 turn -> 최신 turn 순서를 보장해야 한다.
        // Correction 은 전달받은 순서를 기준으로 sourceTurnIndex 를 계산하므로 여기서 재정렬하지 않는다.
        val conversationTurns = sessionTurns.map { turn ->
            turn.toConversationTurn()
        }

        val candidates = extractCandidatesUseCase(
            ExtractCandidatesInput(
                selectedLang = selectedLang,
                sessionLang = sessionLang,
                recentFullContext = conversationTurns
            )
        )

        return candidates.map { candidate ->
            // ExtractCandidatesUseCase 는 ConversationTurn 만 알기 때문에 turnId/언어 정보를 직접 보존할 수 없다.
            // RT-003 원본 index 를 이용해 sourceTurnId 와 sourceLang 을 다시 붙인다.
            // sourceLang 은 turn.detectedLang 을 그대로 옮긴 값이다(COR-TUNE-011) — 캡처가 아직 머지되지
            // 않은 구간에서는 detectedLang 이 항상 null 이므로 sourceLang 도 null 로 전달되어 평가 게이트가
            // no-op 로 동작한다(점진 도입 안전).
            val sourceTurn = sessionTurns.getOrNull(candidate.sourceTurnIndex)
            candidate.copy(
                sourceTurnId = sourceTurn?.turnId,
                sourceLang = sourceTurn?.detectedLang
            )
        }
    }

    private fun SessionTurn.toConversationTurn(): ConversationTurn {
        // ExtractCandidatesUseCase 는 learningstate 의 ConversationTurn 계약을 재사용한다.
        // RT-003 모델을 그대로 넘기지 않아 Correction domain 이 realtime 저장 모델에 과하게 묶이지 않게 한다.
        return ConversationTurn(
            speaker = role,
            text = text,
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }
}
