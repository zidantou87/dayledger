package com.dayledger.app.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dayledger.app.DayApp
import com.dayledger.app.MainActivity
import com.dayledger.app.R
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 计时器前台服务：锁屏、切后台都继续走；通知栏常驻，可一键停止。
 * targetMin > 0 时到点自动结束并推一条完成通知（番茄模式）。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var startAt: Long = 0L
    private var targetMin: Int = 0
    private var activityName: String = "专注"

    private val ticker = object : Runnable {
        override fun run() {
            if (targetMin > 0 && System.currentTimeMillis() - startAt >= targetMin * 60_000L) {
                finishTarget()
                return
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAt = intent.getLongExtra(EXTRA_START_AT, System.currentTimeMillis())
                targetMin = intent.getIntExtra(EXTRA_TARGET_MIN, 0)
                activityName = intent.getStringExtra(EXTRA_ACTIVITY) ?: "专注"
                startForegroundCompat(buildNotification())
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1_000L)
            }

            ACTION_STOP -> {
                scope.launch { (application as DayApp).container.repo.stopRunningTimer() }
                stopTimerService()
            }

            else -> Unit
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun buildNotification(): android.app.Notification {
        val subtitle = if (targetMin > 0) {
            "目标 $targetMin 分钟 · ${Fmt.hm(startAt)} 开始"
        } else {
            "${Fmt.hm(startAt)} 开始"
        }
        return NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("$activityName · 计时中")
            .setContentText(subtitle)
            .setWhen(startAt)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, "停止", stopPendingIntent())
            .build()
    }

    private fun finishTarget() {
        val minutes = targetMin
        scope.launch {
            (application as DayApp).container.repo.stopRunningTimer()
        }
        val done = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("$activityName · $minutes 分钟完成")
            .setContentText("起来动一下，喝口水。")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent())
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID + 1, done) }
        stopTimerService()
    }

    private fun stopTimerService() {
        handler.removeCallbacks(ticker)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        return PendingIntent.getService(
            this, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "计时中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "正在计时时显示，可一键停止"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "计时完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "番茄钟到点时提醒"
            },
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_TIMER = "timer_running"
        private const val CHANNEL_DONE = "timer_done"
        private const val NOTIF_ID = 4201

        const val ACTION_START = "com.dayledger.app.timer.START"
        const val ACTION_STOP = "com.dayledger.app.timer.STOP"
        const val EXTRA_START_AT = "start_at"
        const val EXTRA_ACTIVITY = "activity"
        const val EXTRA_TARGET_MIN = "target_min"

        fun start(context: Context, startAt: Long, activity: String, targetMin: Int) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_AT, startAt)
                putExtra(EXTRA_ACTIVITY, activity)
                putExtra(EXTRA_TARGET_MIN, targetMin)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }
}
