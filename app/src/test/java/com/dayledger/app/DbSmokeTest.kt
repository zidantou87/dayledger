package com.dayledger.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dayledger.app.data.AppSettings
import com.dayledger.app.data.DayLedgerDb
import com.dayledger.app.data.DayRepository
import com.dayledger.app.data.SettingsRepository
import com.dayledger.app.domain.Scorer
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DbSmokeTest {

    private fun newRepo(context: Context): Pair<DayLedgerDb, DayRepository> {
        val db = Room.inMemoryDatabaseBuilder(context, DayLedgerDb::class.java)
            .allowMainThreadQueries()
            .build()
        return db to DayRepository(db, SettingsRepository(context))
    }

    @Test
    fun `end to end write, score, export and import`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = newRepo(ctx)
        repo.seedIfNeeded()

        val cats = db.categories().all()
        assertTrue("categories should be seeded", cats.size >= 9)
        val food = cats.first { it.name == "餐饮" }
        val activity = db.activities().all().first()

        val now = System.currentTimeMillis()
        repo.addTransaction(1200, 0, food.id, "午饭", now)
        repo.addMeal(1, 1, 700, 30, 100, "外卖", now)
        repo.addEvent("写周报", now, now + 3_600_000, top3 = true)
        repo.startTimer(activity.id, now - 1_800_000)
        repo.updateReview(Fmt.todayKey()) { it.copy(mood = 4, energy = 3, bedMinute = 70, wakeMinute = 450) }

        val snap = repo.snapshot(Fmt.todayKey())
        assertEquals(1, snap.transactions.size)
        assertEquals(1, snap.meals.size)
        assertEquals(1, snap.events.size)
        assertEquals(1, snap.blocks.size)

        val facts = repo.factsOf(snap, AppSettings())
        assertEquals(1200L, facts.expenseCents)
        assertEquals(30, facts.proteinG ?: -1)
        assertEquals(380, facts.sleepMinutes ?: -1)

        val score = Scorer.scoreDay(facts)
        assertTrue("score should be positive, was ${score.total}", score.total > 0f)
        assertTrue(score.availableDims.size >= 4)

        val report = repo.dayReport(Fmt.todayKey(), AppSettings())
        assertEquals(Fmt.todayKey(), report.date)
        assertTrue(report.suggestions.isNotEmpty())

        val week = repo.weekReport(Fmt.weekStart(Fmt.today()).toString(), AppSettings())
        assertTrue(week.avg != null)

        val json = repo.exportJson()
        assertTrue(json.contains("\"transactions\""))
        assertTrue(json.contains("\"categories\""))

        val ok = repo.importJson(json)
        assertTrue("import should succeed: ${ok.exceptionOrNull()}", ok.isSuccess)
        assertEquals(1, db.transactions().all().size)
        assertEquals(1, db.meals().all().size)

        db.close()
    }

    @Test
    fun `timer can be stopped`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = newRepo(ctx)
        repo.seedIfNeeded()
        val activity = db.activities().all().first()

        repo.startTimer(activity.id, System.currentTimeMillis() - 600_000)
        val running = db.blocks().running()
        assertTrue(running != null)
        repo.stopTimer(running!!.id)
        assertTrue(db.blocks().running() == null)
        assertEquals(1, db.blocks().all().size)
        db.close()
    }
}
