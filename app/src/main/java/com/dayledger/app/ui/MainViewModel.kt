package com.dayledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dayledger.app.DayApp
import com.dayledger.app.data.AppSettings
import com.dayledger.app.data.DIR_EXPENSE
import com.dayledger.app.data.DaySnapshot
import com.dayledger.app.data.EVENT_DONE
import com.dayledger.app.data.EVENT_PLANNED
import com.dayledger.app.data.EVENT_SKIPPED
import com.dayledger.app.data.TimeBlockEntity
import com.dayledger.app.domain.DayFacts
import com.dayledger.app.domain.MonthlyReport
import com.dayledger.app.domain.ScoreResult
import com.dayledger.app.domain.Scorer
import com.dayledger.app.domain.WeeklyReport
import com.dayledger.app.domain.buildDayFacts
import com.dayledger.app.timer.Reminders
import com.dayledger.app.timer.TimerService
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class DayUi(
    val date: String,
    val snapshot: DaySnapshot,
    val facts: DayFacts,
    val score: ScoreResult,
    val settings: AppSettings,
    val now: Long,
) {
    val running: TimeBlockEntity? get() = snapshot.blocks.firstOrNull { it.endAt == null }
    val hasData: Boolean get() = score.availableDims.isNotEmpty()

    /** 至少两个计分维度有数据、且可信度过半才出分，避免记一条就显示"D 等"。 */
    val scoreReady: Boolean
        get() = hasData &&
            score.dims.count { it.counted && it.available } >= 2 &&
            score.confidence >= 0.5f

    companion object {
        fun loading(date: String, settings: AppSettings = AppSettings()): DayUi {
            val empty = buildDayFacts(
                date = date,
                transactions = emptyList(),
                blocks = emptyList(),
                meals = emptyList(),
                events = emptyList(),
                review = null,
                categories = emptyList(),
                activities = emptyList(),
                cfg = settings.toConfig(),
            )
            return DayUi(date, DaySnapshot(date), empty, Scorer.scoreDay(empty), settings, System.currentTimeMillis())
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DayApp).container.repo

    private val _date = MutableStateFlow(Fmt.todayKey())
    val date: StateFlow<String> = _date.asStateFlow()

    private val _refresh = MutableStateFlow(0)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(20_000)
        }
    }

    val settings: StateFlow<AppSettings> =
        repo.settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val snapshotFlow = _date.flatMapLatest { repo.observeDay(it) }

    val day: StateFlow<DayUi> = combine(_date, snapshotFlow, settings, ticker) { d, snap, s, now ->
        val facts = repo.factsOf(snap, s, now)
        DayUi(d, snap, facts, Scorer.scoreDay(facts), s, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUi.loading(Fmt.todayKey()))

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val monthScores: StateFlow<Map<String, Float>> =
        combine(_month, settings, _refresh) { m, s, _ -> m to s }
            .flatMapLatest { (m, s) -> flow { emit(repo.monthScores(m.year, m.monthValue, s)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _weekStart = MutableStateFlow(Fmt.weekStart(Fmt.today()).toString())
    val weekStart: StateFlow<String> = _weekStart.asStateFlow()

    private val _reportMonth = MutableStateFlow(YearMonth.now())
    val reportMonth: StateFlow<YearMonth> = _reportMonth.asStateFlow()

    val monthReport: StateFlow<MonthlyReport?> =
        combine(_reportMonth, settings, _refresh) { m, s, _ -> m to s }
            .flatMapLatest { (m, s) -> flow { emit(repo.monthReport(m, s)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weekReport: StateFlow<WeeklyReport?> =
        combine(_weekStart, settings, _refresh) { w, s, _ -> w to s }
            .flatMapLatest { (w, s) -> flow { emit(repo.weekReport(w, s)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------- navigation ----------

    fun selectDate(date: String) {
        _date.value = date
    }

    fun goToday() {
        _date.value = Fmt.todayKey()
    }

    fun shiftMonth(delta: Long) {
        _month.value = _month.value.plusMonths(delta)
    }

    fun goCurrentMonth() {
        _month.value = YearMonth.now()
    }

    fun shiftWeek(delta: Long) {
        _weekStart.value = Fmt.parse(_weekStart.value).plusWeeks(delta).toString()
    }

    fun goCurrentWeek() {
        _weekStart.value = Fmt.weekStart(Fmt.today()).toString()
    }

    fun shiftReportMonth(delta: Long) {
        _reportMonth.value = _reportMonth.value.plusMonths(delta)
    }

    fun goCurrentReportMonth() {
        _reportMonth.value = YearMonth.now()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.settingsRepo.update(transform) }
    }

    // ---------- mutations ----------

    private fun bump() {
        _refresh.value++
    }

    fun addExpense(amountCents: Long, categoryId: Long, note: String, at: Long) {
        viewModelScope.launch {
            repo.addTransaction(amountCents, DIR_EXPENSE, categoryId, note, at)
            bump()
        }
    }

    fun addMeal(type: Int, place: Int, kcal: Int?, protein: Int?, veg: Int?, note: String, at: Long) {
        viewModelScope.launch {
            repo.addMeal(type, place, kcal, protein, veg, note, at)
            bump()
        }
    }

    fun addBlock(activityId: Long, startAt: Long, endAt: Long, note: String = "") {
        viewModelScope.launch {
            repo.addBlock(activityId, startAt, endAt, note)
            bump()
        }
    }

    fun startTimer(activityId: Long, activityName: String, targetMinutes: Int = 0) {
        viewModelScope.launch {
            repo.startTimer(activityId)
            val running = repo.runningBlock()
            if (running != null) {
                TimerService.start(getApplication<Application>(), running.startAt, activityName, targetMinutes)
            }
            bump()
        }
    }

    fun stopTimer() {
        val running = day.value.running ?: return
        viewModelScope.launch {
            repo.stopTimer(running.id)
            TimerService.stop(getApplication<Application>())
            bump()
        }
    }

    fun addEvent(title: String, startAt: Long, endAt: Long, top3: Boolean) {
        viewModelScope.launch {
            val id = repo.addEvent(title, startAt, endAt, top3)
            if (settings.value.remindersEnabled && id > 0) {
                Reminders.scheduleEvent(getApplication<Application>(), id, title, startAt)
            }
            bump()
        }
    }

    fun completeEvent(id: Long) {
        viewModelScope.launch {
            repo.setEventStatus(id, EVENT_DONE)
            bump()
        }
    }

    fun skipEvent(id: Long) {
        viewModelScope.launch {
            repo.setEventStatus(id, EVENT_SKIPPED)
            bump()
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            repo.deleteEvent(id)
            Reminders.cancelEvent(getApplication<Application>(), id)
            bump()
        }
    }

    fun updateEvent(id: Long, title: String, startAt: Long, endAt: Long, top3: Boolean) {
        viewModelScope.launch {
            repo.updateEvent(id, title, startAt, endAt, top3)
            val app = getApplication<Application>()
            Reminders.cancelEvent(app, id)
            if (settings.value.remindersEnabled) Reminders.scheduleEvent(app, id, title, startAt)
            bump()
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.settingsRepo.update { it.copy(remindersEnabled = enabled) }
            val app = getApplication<Application>()
            if (enabled) {
                val now = System.currentTimeMillis()
                repo.eventsOn(Fmt.todayKey())
                    .filter { it.status == EVENT_PLANNED && it.startAt - 600_000 > now }
                    .forEach { Reminders.scheduleEvent(app, it.id, it.title, it.startAt) }
            }
        }
    }

    fun setReviewReminder(enabled: Boolean) {
        viewModelScope.launch {
            repo.settingsRepo.update { it.copy(reviewReminderEnabled = enabled) }
            val app = getApplication<Application>()
            if (enabled) Reminders.scheduleDaily(app) else Reminders.cancelDaily(app)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
            bump()
        }
    }

    fun updateTransaction(id: Long, cents: Long, categoryId: Long, note: String) {
        viewModelScope.launch {
            repo.updateTransaction(id, cents, categoryId, note)
            bump()
        }
    }

    fun updateBlock(id: Long, activityId: Long, startAt: Long, endAt: Long) {
        viewModelScope.launch {
            repo.updateBlock(id, activityId, startAt, endAt)
            bump()
        }
    }

    fun updateMeal(id: Long, type: Int, place: Int, kcal: Int?, protein: Int?, veg: Int?, note: String) {
        viewModelScope.launch {
            repo.updateMeal(id, type, place, kcal, protein, veg, note)
            bump()
        }
    }

    fun deleteBlock(id: Long) {
        viewModelScope.launch {
            repo.deleteBlock(id)
            bump()
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            repo.deleteMeal(id)
            bump()
        }
    }

    fun saveReview(mood: Int?, energy: Int?, bedMinute: Int?, wakeMinute: Int?, noSpend: Boolean, note: String) {
        viewModelScope.launch {
            repo.updateReview(_date.value) {
                it.copy(
                    mood = mood,
                    energy = energy,
                    bedMinute = bedMinute,
                    wakeMinute = wakeMinute,
                    noSpend = noSpend,
                    note = note,
                )
            }
            bump()
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch {
            repo.addWater(_date.value, ml)
            bump()
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            repo.wipeRecords()
            bump()
        }
    }

    suspend fun exportJson(): String = repo.exportJson()

    fun importJson(text: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.importJson(text)
            if (result.isSuccess) bump()
            onDone(result.isSuccess)
        }
    }
}
