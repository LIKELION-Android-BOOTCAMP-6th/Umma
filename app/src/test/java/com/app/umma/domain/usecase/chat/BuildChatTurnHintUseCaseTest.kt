package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationSnapshot
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildChatTurnHintUseCaseTest {
    private val snapshotUseCase = BuildChatConversationSnapshotUseCase()
    private val hintUseCase = BuildChatTurnHintUseCase()

    @Test
    fun `turn hint summarizes current position without repeating band policy`() {
        // USER가 의미를 확인하는 상황을 재현한다.
        // hint는 "초급자니까 쉽게 말하라"가 아니라, 현재 위치를 작게 이어가라는 정보만 담아야 한다.
        val snapshot = snapshotUseCase(
            previous = ChatConversationSnapshot(),
            turn = finalTurn(
                role = TurnSpeaker.USER,
                text = "방금 무슨 뜻이야?"
            )
        )

        val hint = hintUseCase(snapshot)

        assertNotNull(hint)
        assertTrue(hint!!.contains("현재 대화 위치:"))
        assertTrue(hint.contains("세션 prompt의 current_style과 style_reference 리듬은 유지한다."))
        assertTrue(hint.contains("확인"))
        assertTrue(hint.contains("흐름을 더 작게 이어간다"))
        assertFalse(hint.contains("IntentOnly"))
        assertFalse(hint.contains("ChatComprehensionSignal"))
        assertFalse(hint.contains("천천히"))
        assertFalse(hint.contains("짧게 말"))
        assertFalse(hint.contains("기준언어"))
    }

    @Test
    fun `snapshot tracks previous ai move only as narrow repetition context`() {
        // AI final을 먼저 넣어 직전 AI 움직임이 avoidRepeating에만 좁게 들어가는지 확인한다.
        // 이 값은 금지어 목록이 아니라 같은 질문을 바로 반복하지 않기 위한 세션 상태다.
        val afterAi = snapshotUseCase(
            previous = ChatConversationSnapshot(),
            turn = finalTurn(
                role = TurnSpeaker.AI,
                text = "지금 뭐 하고 싶어? 산책? 밖에?"
            )
        )

        val afterUser = snapshotUseCase(
            previous = afterAi,
            turn = finalTurn(
                role = TurnSpeaker.USER,
                text = "잘 모르겠어"
            )
        )
        val hint = hintUseCase(afterUser)

        assertNotNull(hint)
        assertTrue(afterUser.avoidRepeating.isNotEmpty())
        assertTrue(hint!!.contains("직전 AI 흐름을 그대로 반복하지 말고 이어받는다."))
        assertTrue(hint.contains("직전 AI 말 일부"))
        assertFalse(hint.contains("ChatComprehensionSignal"))
    }

    @Test
    fun `turn hint uses previous ai utterance when user checks a target expression`() {
        // 이번 신고 회귀는 사용자가 직전 AI의 "今日は" 뜻을 물었는데 날짜 질문처럼 오해된 케이스다.
        // snapshot은 full context를 다시 넣지 않고도 직전 AI 말 일부와 흐름 메모를 함께 전달해야 한다.
        val afterAi = snapshotUseCase(
            previous = ChatConversationSnapshot(),
            turn = finalTurn(
                role = TurnSpeaker.AI,
                text = "おかえりなさい！お疲れさまでした。今日はどんな一日でしたか？"
            )
        )
        val afterUser = snapshotUseCase(
            previous = afterAi,
            turn = finalTurn(
                role = TurnSpeaker.USER,
                text = "今日は는 오늘이지?"
            )
        )

        val hint = hintUseCase(afterUser)

        assertNotNull(hint)
        assertTrue(hint!!.contains("직전 AI 말에 나온 표현"))
        assertTrue(hint.contains("표현은 짧게 확인하고"))
        assertTrue(hint.contains("세션의 주제 후보로 작게 이어간다"))
        assertTrue(hint.contains("직전 AI 말 일부"))
        assertFalse(hint.contains("ChatComprehensionSignal"))
    }

    @Test
    fun `support language after target heavy ai turn is treated as possible comprehension break`() {
        // 질문표가 없어도 직전 AI가 학습언어를 길게 쓴 뒤 사용자가 한국어로 막힘을 표현하면,
        // "대화를 잘 이어가는 중"이 아니라 의미를 짧게 받쳐야 하는 흐름으로 요약해야 한다.
        val afterAi = snapshotUseCase(
            previous = ChatConversationSnapshot(),
            turn = finalTurn(
                role = TurnSpeaker.AI,
                text = "そうですね、今日は6月7日ですね。何か今日のことについて確認したいことがあれば教えてください。"
            )
        )
        val afterUser = snapshotUseCase(
            previous = afterAi,
            turn = finalTurn(
                role = TurnSpeaker.USER,
                text = "어 무슨 말인지 하나도 모르겠어."
            )
        )

        val hint = hintUseCase(afterUser)

        assertNotNull(hint)
        assertTrue(hint!!.contains("어렵거나 선택하기 어려워"))
        assertTrue(hint.contains("의미를 짧게 받치고 대화 흐름을 작게 되돌린다"))
        assertFalse(hint.contains("대화를 이어가려는 것으로 보인다"))
    }

    @Test
    fun `support language content reply is treated as normal conversation response`() {
        // 이번 신고 회귀는 "어 힙합, 힙합 좋아해"처럼 정상 답변한 한국어 반응까지
        // 직전 AI의 학습언어 때문에 이해 실패로 오판한 케이스다.
        val afterAi = snapshotUseCase(
            previous = ChatConversationSnapshot(),
            turn = finalTurn(
                role = TurnSpeaker.AI,
                text = "좋아, 그럼 어떤 음악 장르 좋아해? 예를 들어 팝とか, 발라드とか?"
            )
        )
        val afterUser = snapshotUseCase(
            previous = afterAi,
            turn = finalTurn(
                role = TurnSpeaker.USER,
                text = "어 힙합, 힙합 좋아해."
            )
        )

        val hint = hintUseCase(afterUser)

        assertNotNull(hint)
        assertTrue(hint!!.contains("대화를 이어가려는 것으로 보인다"))
        assertFalse(hint.contains("충분히 따라오지 못해"))
        assertFalse(hint.contains("의미를 짧게 받치고 대화 흐름을 작게 되돌린다"))
    }

    @Test
    fun `known expression response is treated as acceptance instead of comprehension break`() {
        // 사용자가 "이미 알아"라고 수용한 경우는 다시 설명하거나 단어 수업으로 되돌릴 근거가 아니다.
        // 직전 AI에 일본어가 있어도 다음 hint는 생활 대화로 진행 가능한 상태를 전달해야 한다.
        val afterAi = snapshotUseCase(
            previous = ChatConversationSnapshot(),
            turn = finalTurn(
                role = TurnSpeaker.AI,
                text = "좋아, 그럼 설명해 줄게. ‘ありがとう’는 일본어로 고마워라는 뜻이야."
            )
        )
        val afterUser = snapshotUseCase(
            previous = afterAi,
            turn = finalTurn(
                role = TurnSpeaker.USER,
                text = "음, 아리가토는 알아."
            )
        )

        val hint = hintUseCase(afterUser)

        assertNotNull(hint)
        assertTrue(hint!!.contains("받아들이고 대화를 계속할 준비"))
        assertTrue(hint.contains("이미 받아들인 표현을 다시 설명하지 말고"))
        assertTrue(hint.contains("세션의 주제 후보로 작게 이어간다"))
        assertFalse(hint.contains("충분히 따라오지 못해"))
        assertFalse(hint.contains("의미를 짧게 받치고 대화 흐름을 작게 되돌린다"))
    }

    private fun finalTurn(
        role: TurnSpeaker,
        text: String
    ): SessionTurn {
        // Snapshot UseCase는 LangState나 저장소가 아니라 final transcript domain model만 입력으로 받는다.
        return SessionTurn(
            turnId = "turn-${role.name}-$text",
            sessionId = "session-1",
            text = text,
            role = role,
            createdAt = 1_000L
        )
    }
}
