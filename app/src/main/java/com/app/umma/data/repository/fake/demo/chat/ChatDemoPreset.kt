package com.app.umma.data.repository.fake.demo.chat

/**
 * Chat mockDebug preset list.
 */
enum class ChatDemoPreset {
    HandoffSuccess,
    SaveSignalOnly,
    HandoffDuplicateFinal,
    RecordingInterrupted,
    SilentInputNoFinal,
    ReconnectSuccess,
    ReconnectFailed,
    FatalError
}

/**
 * Active Chat preset for mockDebug.
 */
object ChatDemoPresetConfig {
    val activePreset: ChatDemoPreset = ChatDemoPreset.HandoffSuccess
}
