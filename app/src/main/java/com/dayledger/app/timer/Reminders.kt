package com.dayledger.app.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dayledger.app.MainActivity
import com.dayledger.app.R
import java.util.Calendar

/**
 * 提醒：日程开始前 10 分钟 + 每晚 22:00 复盘。
 * 只用不精确闹钟（setAndAllowWhileIdle），不需要"精确闹钟"特殊权限。
 */
object Reminders {

    const val CHANNEL_REMIND = "reminders"
    const val ACTION_EVENT = "com.dayledger.app.reminder.EVENT"
    const val ACTION_DAILY = "com.dayledger.app.reminder.DAILY"

    private const val REQ_DAILY = 9001
    private const val NOTIF_DAILY = 9100
    private const val LEAD_MINUTES = 10

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMIND, "提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "日程到点和每晚复盘"
            },
        )
    }

    fun scheduleEvent(context: Context, id: Long, title: String, startAt: Long) {
        val at = startAt - LEAD_MINUTES * 60_000L
        if (at <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, eventPending(context, id, title)) }
    }

    fun cancelEvent(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(eventPending(context, id, null))
    }

    fun scheduleDaily(context: Context, hour: Int = 22) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, dailyPending(context))
        }
    }

    fun cancelDaily(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(dailyPending(context))
    }

    private fun eventPending(context: Context, id: Long, title: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_EVENT
            putExtra("title", title.orEmpty())
            putExtra("event_id", id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dailyPending(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_DAILY }
        return PendingIntent.getBroadcast(
            context, REQ_DAILY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.ensureChannel(context)
        val daily = intent.action == Reminders.ACTION_DAILY
        val eventTitle = intent.getStringExtra("title").orEmpty()
        val title = if (daily) "该复盘了" else eventTitle.ifBlank { "日程提醒" }
        val text = if (daily) "今天过得怎么样？花 30 秒记一下心情和睡眠。" else "10 分钟后开始"
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp(context))
            .build()
        val id = if (daily) 9100 else 9200 + intent.getLongExtra("event_id", 0L).toInt()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
