package com.app.umma.watchbridge

import kotlinx.serialization.json.Json

object WatchBridgeJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
