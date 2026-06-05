package com.app.umma.watchbridge

import com.app.umma.watchbridge.contract.WatchBridgeEvent

interface WatchBridgeEventSink {
    suspend fun send(nodeId: String, event: WatchBridgeEvent)
}
