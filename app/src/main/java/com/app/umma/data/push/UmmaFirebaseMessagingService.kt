package com.app.umma.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
 * FCM 토큰 갱신과 학습 알림 표시를 담당하는 서비스.
 */
@AndroidEntryPoint
class UmmaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registerNotificationDeviceUseCase: RegisterNotificationDeviceUseCase

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
    }

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

        if (!hasNotificationPermission()) return

        when (message.data[DATA_TYPE]) {
            SRS_NOTIFICATION_TYPE -> showSrsNotification(message)
            MARKETING_NOTIFICATION_TYPE -> showMarketingNotification(message)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showSrsNotification(message: RemoteMessage) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, SRS_NOTIFICATION_TYPE)
            putExtra(EXTRA_NOTIFICATION_ROUTE, SRS_NOTIFICATION_ROUTE)
            putExtra(EXTRA_NOTIFICATION_LANG, message.data[DATA_LANG])
            putExtra(EXTRA_NOTIFICATION_HISTORY_ID, message.data[DATA_HISTORY_ID])
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SRS_REVIEW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.data[DATA_TITLE] ?: "학습 알림")
            .setContentText(
                message.data[DATA_BODY] ?: "복습할 카드가 있습니다. 앱에서 확인해보세요."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                buildPendingIntent(
                    requestCode = REQUEST_CODE_SRS_NOTIFICATION,
                    intent = intent
                )
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(
            REQUEST_CODE_SRS_NOTIFICATION,
            notification
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showMarketingNotification(message: RemoteMessage) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, MARKETING_NOTIFICATION_TYPE)
            putExtra(EXTRA_NOTIFICATION_ROUTE, MARKETING_NOTIFICATION_ROUTE)
            putExtra(EXTRA_NOTIFICATION_HISTORY_ID, message.data[DATA_HISTORY_ID])
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MARKETING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.data[DATA_TITLE] ?: "Umma")
            .setContentText(message.data[DATA_BODY] ?: "앱에서 새로운 소식을 확인해보세요.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                buildPendingIntent(
                    requestCode = REQUEST_CODE_MARKETING_NOTIFICATION,
                    intent = intent
                )
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(
            REQUEST_CODE_MARKETING_NOTIFICATION,
            notification
        )
    }

    private fun buildPendingIntent(
        requestCode: Int,
        intent: Intent
    ): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        private const val DATA_TYPE = "type"
        private const val DATA_TITLE = "title"
        private const val DATA_BODY = "body"
        private const val DATA_LANG = "lang"
        private const val DATA_HISTORY_ID = "historyId"

        private const val SRS_NOTIFICATION_TYPE = "srs_review"
        private const val MARKETING_NOTIFICATION_TYPE = "marketing"
        private const val SRS_NOTIFICATION_ROUTE = "srs_study"
        private const val MARKETING_NOTIFICATION_ROUTE = "home"

        private const val CHANNEL_ID_SRS_REVIEW = "srs_review_notifications"
        private const val CHANNEL_ID_MARKETING = "marketing_notifications"
        private const val REQUEST_CODE_SRS_NOTIFICATION = 1001
        private const val REQUEST_CODE_MARKETING_NOTIFICATION = 1002

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SRS_REVIEW,
                    "학습 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "SRS 학습 리마인드 알림"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_MARKETING,
                    "marketing_notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "마케팅 캠페인 알림"
                }
            )
        }
    }
}
