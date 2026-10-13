package com.aniciel.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive (and shows a progress notification)
 * while downloads run. The downloads themselves are run by [Downloader]; this service
 * only holds foreground priority and stops itself when nothing is active any more.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "aniciel_downloads"
        private const val NOTIFICATION_ID = 1001

        fun track(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DownloadsStore.init(applicationContext)
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            var idleTicks = 0
            while (isActive) {
                if (DownloadsStore.activeCount() == 0) {
                    idleTicks++
                    if (idleTicks >= 3) {
                        stopSelf()
                        return@launch
                    }
                } else {
                    idleTicks = 0
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                delay(1000)
            }
        }
    }

    private fun buildNotification(): Notification {
        val active = DownloadsStore.activeItems()
        val text: String
        var percent = 0
        var known = false
        when {
            active.isEmpty() -> text = "No active downloads"
            active.size == 1 -> {
                val item = active[0]
                if (item.total > 0) {
                    percent = (item.downloaded * 100 / item.total).toInt().coerceIn(0, 100)
                    known = true
                }
                val what = "${item.animeTitle} · ${item.displayName()}"
                text = if (item.state == "processing") "$what - finishing up" else if (known) "$what - $percent%" else what
            }
            else -> {
                val total = active.sumOf { it.total }
                val done = active.sumOf { it.downloaded }
                if (total > 0) {
                    percent = (done * 100 / total).toInt().coerceIn(0, 100)
                    known = true
                }
                text = "${active.size} downloads in progress" + if (known) " - $percent%" else ""
            }
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AniCiel")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setOngoing(active.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, active.isNotEmpty() && !known)
            .build()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
