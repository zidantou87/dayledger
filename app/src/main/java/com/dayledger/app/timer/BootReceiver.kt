package com.dayledger.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dayledger.app.DayApp
import com.dayledger.app.data.EVENT_PLANNED
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 重启后闹钟会丢，开机时补排一次 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? DayApp ?: return@launch
                val settings = app.container.settingsRepo.flow.first()
                Reminders.ensureChannel(context)
                if (settings.reviewReminderEnabled) Reminders.scheduleDaily(context)
                if (settings.remindersEnabled) {
                    val now = System.currentTimeMillis()
                    app.container.repo.eventsOn(Fmt.todayKey())
                        .filter { it.status == EVENT_PLANNED && it.startAt - 600_000 > now }
                        .forEach { Reminders.scheduleEvent(context, it.id, it.title, it.startAt) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
