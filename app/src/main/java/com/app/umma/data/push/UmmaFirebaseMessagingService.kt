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
import com.app.umma.domain.repository.SessionRepository
import com.app.umma.domain.usecase.auth.LogoutUseCase
import com.app.umma.domain.usecase.notification.RegisterNotificationDeviceUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles FCM token refresh and phone-owned notification display.
 */
@AndroidEntryPoint
class UmmaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registerNotificationDeviceUseCase: RegisterNotificationDeviceUseCase

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var logoutUseCase: LogoutUseCase

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

        when (message.data[DATA_TYPE]) {
            // 강제 로그아웃은 알림 권한과 무관하게 항상 처리해야 한다.
            // 시스템 알림 표시만 권한이 있을 때 수행한다.
            FORCE_LOGOUT_TYPE -> handleForceLogout(message)
            SRS_NOTIFICATION_TYPE -> if (hasNotificationPermission()) showSrsNotification(message)
            MARKETING_NOTIFICATION_TYPE -> if (hasNotificationPermission()) showMarketingNotification(message)
        }
    }

    /**
     * 다른 기기에서 로그인되어 이 기기의 세션이 무효화되었을 때 처리.
     * 서버에서 이미 요청 기기를 broadcast 대상에서 제외하지만, 방어적으로 한 번 더 sessionId를 비교한다.
     */
    private fun handleForceLogout(message: RemoteMessage) {
        val incomingSessionId = message.data[DATA_SESSION_ID]
        val localSessionId = sessionRepository.getLocalSessionId()
        if (incomingSessionId != null && incomingSessionId == localSessionId) return

        CoroutineScope(Dispatchers.IO).launch {
            logoutUseCase()
            sessionRepository.markPendingForceLogoutNotice()

            if (hasNotificationPermission()) {
                showForceLogoutNotification()
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showForceLogoutNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ACCOUNT_SECURITY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_force_logout_title))
            .setContentText(getString(R.string.notification_force_logout_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                buildPendingIntent(
                    requestCode = REQUEST_CODE_FORCE_LOGOUT_NOTIFICATION,
                    intent = intent
                )
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(
            REQUEST_CODE_FORCE_LOGOUT_NOTIFICATION,
            notification
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showSrsNotification(message: RemoteMessage) {
        val content = SrsNotificationContentPolicy.build(
            title = message.data[DATA_TITLE],
            body = message.data[DATA_BODY],
            fallbackTitle = getString(R.string.notification_srs_fallback_title),
            fallbackBody = getString(R.string.notification_srs_fallback_body)
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, SRS_NOTIFICATION_TYPE)
            putExtra(EXTRA_NOTIFICATION_ROUTE, SRS_NOTIFICATION_ROUTE)
            putExtra(EXTRA_NOTIFICATION_LANG, message.data[DATA_LANG])
            putExtra(EXTRA_NOTIFICATION_HISTORY_ID, message.data[DATA_HISTORY_ID])
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SRS_REVIEW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(content.title)
            .setContentText(content.body)
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
            .setContentTitle(
                message.data[DATA_TITLE] ?: getString(R.string.notification_marketing_fallback_title)
            )
            .setContentText(
                message.data[DATA_BODY] ?: getString(R.string.notification_marketing_fallback_body)
            )
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
        private const val DATA_SESSION_ID = "sessionId"

        private const val SRS_NOTIFICATION_TYPE = "srs_review"
        private const val MARKETING_NOTIFICATION_TYPE = "marketing"
        private const val FORCE_LOGOUT_TYPE = "force_logout"
        private const val SRS_NOTIFICATION_ROUTE = "srs_study"
        private const val MARKETING_NOTIFICATION_ROUTE = "home"

        private const val CHANNEL_ID_SRS_REVIEW = "srs_review_notifications"
        private const val CHANNEL_ID_MARKETING = "marketing_notifications"
        private const val CHANNEL_ID_ACCOUNT_SECURITY = "account_security"
        private const val REQUEST_CODE_SRS_NOTIFICATION = 1001
        private const val REQUEST_CODE_MARKETING_NOTIFICATION = 1002
        private const val REQUEST_CODE_FORCE_LOGOUT_NOTIFICATION = 1003

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SRS_REVIEW,
                    context.getString(R.string.notification_channel_srs_review_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(
                        R.string.notification_channel_srs_review_description
                    )
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_MARKETING,
                    context.getString(R.string.notification_channel_marketing_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(
                        R.string.notification_channel_marketing_description
                    )
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ACCOUNT_SECURITY,
                    context.getString(R.string.notification_channel_account_security_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(
                        R.string.notification_channel_account_security_description
                    )
                }
            )
        }
    }
}
