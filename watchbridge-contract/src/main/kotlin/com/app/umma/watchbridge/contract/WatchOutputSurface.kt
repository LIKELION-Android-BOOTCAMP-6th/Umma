package com.app.umma.watchbridge.contract

import kotlinx.serialization.Serializable

@Serializable
enum class WatchOutputSurface {
    NONE,
    PHONE,
    WATCH,
    BOTH
}
