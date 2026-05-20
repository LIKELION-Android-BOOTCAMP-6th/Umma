package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.learningstate.ConversationTurn
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.realtime.SessionTurn
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
            // ExtractCandidatesUseCase 는 ConversationTurn 만 알기 때문에 turnId 를 직접 보존할 수 없다.
            // RT-003 원본 index 를 이용해 sourceTurnId 를 다시 붙여 이후 AI 응답/저장 추적에 사용한다.
            candidate.copy(
                sourceTurnId = sessionTurns.getOrNull(candidate.sourceTurnIndex)?.turnId
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
