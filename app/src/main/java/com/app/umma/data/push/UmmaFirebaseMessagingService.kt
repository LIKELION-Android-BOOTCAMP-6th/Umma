package com.app.umma.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.umma.MainActivity
import com.app.umma.R
import com.app.umma.domain.usecase.notification.RegisterNotificationDeviceUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * FCM 토큰 갱신과 앱 내 알림 표시를 담당하는 서비스.
 */
@AndroidEntryPoint
class UmmaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registerNotificationDeviceUseCase: RegisterNotificationDeviceUseCase

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        CoroutineScope(Dispatchers.IO).launch {
            registerNotificationDeviceUseCase(
                token = token,
                permissionGranted = hasNotificationPermission(),
                timezone = TimeZone.getDefault().id
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (message.data["type"] != "srs_review") return
        if (!hasNotificationPermission()) return

        createSrsNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, "srs_review")
            putExtra(EXTRA_NOTIFICATION_ROUTE, "srs_study")
            putExtra(EXTRA_NOTIFICATION_LANG, message.data["lang"])
            putExtra(EXTRA_NOTIFICATION_HISTORY_ID, message.data["historyId"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_SRS_NOTIFICATION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SRS_REVIEW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.data["title"] ?: "학습 알림")
            .setContentText(message.data["body"] ?: "복습할 카드가 있습니다. 앱에서 확인해보세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(
            REQUEST_CODE_SRS_NOTIFICATION,
            notification
        )
    }

    private fun createSrsNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID_SRS_REVIEW,
            "학습 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "SRS 학습 리마인드 알림"
        }
        manager.createNotificationChannel(channel)
    }

    private fun createMarketingNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID_SRS_REVIEW,
            "학습 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "SRS 학습 리마인드 알림"
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_NOTIFICATION_ROUTE = "notification_route"
        const val EXTRA_NOTIFICATION_LANG = "notification_lang"
        const val EXTRA_NOTIFICATION_HISTORY_ID = "notification_history_id"

        private const val CHANNEL_ID_SRS_REVIEW = "srs_review_notifications"
        private const val REQUEST_CODE_SRS_NOTIFICATION = 1001

        private const val CHANNEL_ID_MARKETING = "marketing_notifications"
        private const val REQUEST_CODE_MARKETING_NOTIFICATION = 1002

    }
}
