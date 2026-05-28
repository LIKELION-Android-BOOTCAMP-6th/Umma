package com.app.umma.data.repository.fake.demo.chat

import com.app.umma.domain.model.realtime.SessionInterruptedReason

/**
 * Static fixtures used by [com.app.umma.data.repository.fake.FakeChatRepository].
 */
object ChatDemoFixtures {

    val handoffSuccess = HandoffFixture(
        partialUserText = "I went to the market",
        finalUserText = "I went to the market yesterday and bought some fruit."
    )

    val saveSignalOnly = HandoffFixture(
        partialUserText = null,
        finalUserText = "I need feedback on this sentence."
    )

    val handoffDuplicateFinal = HandoffFixture(
        partialUserText = "Yesterday I go to school",
        finalUserText = "Yesterday I go to school with my friend."
    )

    const val silentInputPartialText: String = "..."

    val recordingInterrupted = ReconnectFixture(
        message = "Mock interruption while recording",
        reason = SessionInterruptedReason.NETWORK_ERROR,
        attempt = 1,
        maxAttempts = 3
    )

    val reconnectSuccess = ReconnectFixture(
        message = "Mock network interruption",
        reason = SessionInterruptedReason.NETWORK_ERROR,
        attempt = 1,
        maxAttempts = 3
    )

    val reconnectFailed = ReconnectFixture(
        message = "Mock reconnect failed",
        reason = SessionInterruptedReason.NETWORK_ERROR,
        attempt = 1,
        maxAttempts = 3
    )

    const val fatalErrorMessage: String = "Mock fatal chat error"
}

/**
 * Fixture for the handoff success path.
 */
data class HandoffFixture(
    val partialUserText: String?,
    val finalUserText: String
)

/**
 * Fixture for reconnect interruption flows.
 */
data class ReconnectFixture(
    val message: String,
    val reason: SessionInterruptedReason,
    val attempt: Int,
    val maxAttempts: Int
)
