package com.dayledger.app

import com.dayledger.app.domain.BlockFact
import com.dayledger.app.domain.DayFacts
import com.dayledger.app.domain.EventFact
import com.dayledger.app.domain.MealFact
import com.dayledger.app.domain.ScoreConfig
import com.dayledger.app.domain.Scorer
import com.dayledger.app.domain.TxFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    private val cfg = ScoreConfig()

    private fun baseFacts(
        expenses: List<TxFact> = emptyList(),
        blocks: List<BlockFact> = emptyList(),
        events: List<EventFact> = emptyList(),
        meals: List<MealFact> = emptyList(),
        water: Int = 0,
        mood: Int? = null,
        energy: Int? = null,
        sleep: Int? = null,
        bed: Int? = null,
        wake: Int? = null,
        awake: Int = 960,
    ) = DayFacts(
        date = "2026-11-12",
        nowMillis = 0L,
        expenses = expenses,
        incomes = emptyList(),
        blocks = blocks,
        events = events,
        meals = meals,
        waterMl = water,
        noSpend = false,
        mood = mood,
        energy = energy,
        sleepMinutes = sleep,
        bedMinute = bed,
        wakeMinute = wake,
        awakeMinutes = awake,
        cfg = cfg,
    )

    @Test
    fun `full example day lands around 64 without exercise`() {
        val tx = listOf(
            TxFact(800, 0, 1, "餐饮", true, "", 8 * 60),
            TxFact(2800, 0, 1, "餐饮", true, "午饭外卖", 12 * 60),
            TxFact(2200, 0, 1, "餐饮", false, "咖啡", 15 * 60),
            TxFact(4500, 0, 1, "餐饮", true, "", 19 * 60),
            TxFact(3600, 0, 2, "交通", true, "", 21 * 60),
        )
        val blocks = listOf(
            BlockFact(570, 720, 150, "工作", true, ""),
            BlockFact(840, 1050, 210, "工作", true, ""),
            BlockFact(480, 525, 45, "通勤", false, ""),
            BlockFact(1080, 1280, 200, "娱乐", false, ""),
            BlockFact(1280, 1461, 181, "学习", false, ""),
        )
        val events = listOf(
            EventFact(1, "写方案", 570, 690, 120, true, false, false, true),
            EventFact(2, "回邮件", 720, 780, 60, true, false, false, true),
            EventFact(3, "评审", 840, 900, 60, true, false, false, false),
            EventFact(4, "健身", 1140, 1200, 60, false, true, false, true),
            EventFact(5, "读书", 1260, 1320, 60, false, false, true, false),
        )
        val meals = listOf(
            MealFact(0, 8 * 60, 400, 15, 0, 0, ""),
            MealFact(1, 12 * 60, 900, 30, 100, 1, "外卖"),
            MealFact(2, 19 * 60, 1050, 40, 50, 0, "奶茶"),
        )

        val f = baseFacts(tx, blocks, events, meals, water = 1200, mood = 4, energy = 3, sleep = 380, bed = 70, wake = 450, awake = 1060)
        val s = Scorer.scoreDay(f)

        assertEquals(54f, s.dim("exec")!!.score, 0.5f)
        assertEquals(70f, s.dim("state")!!.score, 0.5f)
        assertEquals(82.1f, s.dim("diet")!!.score, 1.5f)
        assertEquals(0f, s.dim("sport")!!.score, 0.01f)
        assertTrue("total should be in 60..70, was ${s.total}", s.total in 60f..70f)
        assertEquals("C", s.grade)
        assertTrue(s.confidence in 0.8f..0.95f)
    }

    @Test
    fun `sport scores by form and continuity`() {
        val single = Scorer.scoreDay(
            baseFacts(blocks = listOf(BlockFact(540, 720, 180, "工作", true, ""), BlockFact(1080, 1110, 30, "跑步", true, ""))),
        )
        val split = Scorer.scoreDay(
            baseFacts(
                blocks = listOf(
                    BlockFact(540, 720, 180, "工作", true, ""),
                    BlockFact(1080, 1090, 10, "跑步", true, ""),
                    BlockFact(1100, 1110, 10, "跑步", true, ""),
                    BlockFact(1120, 1130, 10, "跑步", true, ""),
                ),
            ),
        )
        val tiny = Scorer.scoreDay(
            baseFacts(blocks = listOf(BlockFact(540, 720, 180, "工作", true, ""), BlockFact(1080, 1085, 5, "跑步", true, ""))),
        )

        // 一次 30 分钟跑步：有效 36 分钟，时长与连续都拿满
        assertEquals(100f, single.dim("sport")!!.score, 0.5f)
        // 三个 10 分钟：有效分钟够，但没有一次完整，连续分打折
        assertTrue(split.dim("sport")!!.score in 80f..90f)
        // 单次 5 分钟：严格规则下不计入
        assertEquals(0f, tiny.dim("sport")!!.score, 0.01f)
        assertTrue(tiny.dim("sport")!!.evidence.any { it.contains("不足 10 分钟") })
    }

    @Test
    fun `empty day has no score`() {
        val s = Scorer.scoreDay(baseFacts())
        assertEquals(0f, s.total, 0.001f)
        assertTrue(s.availableDims.isEmpty())
    }

    @Test
    fun `missing sleep is excluded instead of scored zero`() {
        val withoutSleep = Scorer.scoreDay(
            baseFacts(
                blocks = listOf(BlockFact(540, 720, 180, "工作", true, "")),
                meals = listOf(MealFact(0, 480, 500, 20, 100, 0, "")),
            ),
        )
        val withBadSleep = Scorer.scoreDay(
            baseFacts(
                blocks = listOf(BlockFact(540, 720, 180, "工作", true, "")),
                meals = listOf(MealFact(0, 480, 500, 20, 100, 0, "")),
                sleep = 240, bed = 180, wake = 540, awake = 1200,
            ),
        )
        val a = withoutSleep.dim("time")!!
        val b = withBadSleep.dim("time")!!
        assertTrue("no-sleep time score ${a.score} should beat ${b.score}", a.score > b.score)
        assertTrue(a.evidence.any { it.contains("未记") })
    }

    @Test
    fun `overspending lowers money score`() {
        val spend = Scorer.scoreDay(
            baseFacts(
                expenses = listOf(TxFact(30000, 0, 1, "餐饮", true, "", 600)),
                meals = listOf(MealFact(1, 720, 800, 30, 100, 0, "")),
            ),
        )
        val tight = Scorer.scoreDay(
            baseFacts(
                expenses = listOf(TxFact(5000, 0, 1, "餐饮", true, "", 600)),
                meals = listOf(MealFact(1, 720, 800, 30, 100, 0, "")),
            ),
        )
        assertTrue(spend.dim("money")!!.score < tight.dim("money")!!.score)
    }

    @Test
    fun `skipping every event tanks execution`() {
        val events = (1..3).map {
            EventFact(it.toLong(), "事项$it", 540, 600, 60, false, true, false, it == 1)
        }
        val s = Scorer.scoreDay(baseFacts(events = events, blocks = listOf(BlockFact(540, 720, 180, "工作", true, ""))))
        assertEquals(0f, s.dim("exec")!!.score, 0.01f)
    }
}
