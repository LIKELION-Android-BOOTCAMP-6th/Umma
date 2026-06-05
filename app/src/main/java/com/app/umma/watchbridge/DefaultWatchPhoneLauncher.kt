package com.app.umma.watchbridge

import android.content.Context
import android.content.Intent
import com.app.umma.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DefaultWatchPhoneLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : WatchPhoneLauncher {
    override fun openChatOnPhone() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, MainActivity.OPEN_ROUTE_CHAT)
        }
        context.startActivity(intent)
    }
}
