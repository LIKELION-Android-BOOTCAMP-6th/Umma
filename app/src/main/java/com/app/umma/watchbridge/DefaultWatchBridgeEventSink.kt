package com.app.umma.watchbridge

import android.content.Context
import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.watchbridge.contract.WatchBridgePath
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DefaultWatchBridgeEventSink @Inject constructor(
    @ApplicationContext private val context: Context
) : WatchBridgeEventSink {
    override suspend fun send(nodeId: String, event: WatchBridgeEvent) {
        Log.d(TAG, "send event to nodeId=$nodeId type=${event::class.simpleName}")
        Wearable.getMessageClient(context).sendMessage(
            nodeId,
            WatchBridgePath.EVENT,
            WatchBridgeJson.instance.encodeToString(
                WatchBridgeEvent.serializer(),
                event
            ).encodeToByteArray()
        )
    }

    private companion object {
        const val TAG = "WatchBridgeEvent"
    }
}
