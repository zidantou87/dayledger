package com.dayledger.app

import android.app.Application
import android.content.Context
import com.dayledger.app.data.AppSettings
import com.dayledger.app.data.DayLedgerDb
import com.dayledger.app.data.DayRepository
import com.dayledger.app.data.EVENT_PLANNED
import com.dayledger.app.data.SettingsRepository
import com.dayledger.app.timer.Reminders
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val db: DayLedgerDb = DayLedgerDb.get(context)
    val settingsRepo = SettingsRepository(context)
    val repo = DayRepository(db, settingsRepo)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            repo.seedIfNeeded()
            repo.ensureSportActivities()
            Reminders.ensureChannel(context)
            val settings = settingsRepo.flow.first()
            if (settings.reviewReminderEnabled) Reminders.scheduleDaily(context)
            if (settings.remindersEnabled) {
                val now = System.currentTimeMillis()
                runCatching {
                    repo.eventsOn(Fmt.todayKey())
                        .filter { it.status == EVENT_PLANNED && it.startAt - 600_000 > now }
                        .forEach { Reminders.scheduleEvent(context, it.id, it.title, it.startAt) }
                }
            }
        }
    }

    fun settingsBlocking(): AppSettings = AppSettings()
}

class DayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
