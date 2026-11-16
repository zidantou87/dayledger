package com.dayledger.app.data

import android.content.Context
import com.dayledger.app.domain.DailyReport
import com.dayledger.app.domain.DayFacts
import com.dayledger.app.domain.MonthlyReport
import com.dayledger.app.domain.Reports
import com.dayledger.app.domain.ScoreConfig
import com.dayledger.app.domain.ScoreResult
import com.dayledger.app.domain.Scorer
import com.dayledger.app.domain.WeeklyReport
import com.dayledger.app.domain.buildDayFacts
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth

data class DaySnapshot(
    val date: String,
    val transactions: List<TransactionEntity> = emptyList(),
    val blocks: List<TimeBlockEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val review: DayReviewEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val activities: List<ActivityCategoryEntity> = emptyList(),
)

data class DayAggregate(
    val date: String,
    val facts: DayFacts,
    val score: ScoreResult,
    val hasData: Boolean,
)

@Serializable
data class BackupBundle(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val activities: List<ActivityCategoryEntity> = emptyList(),
    val blocks: List<TimeBlockEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val reviews: List<DayReviewEntity> = emptyList(),
)

private data class CoreDay(
    val transactions: List<TransactionEntity>,
    val blocks: List<TimeBlockEntity>,
    val meals: List<MealEntity>,
    val events: List<EventEntity>,
    val review: DayReviewEntity?,
)

class DayRepository(private val db: DayLedgerDb, val settingsRepo: SettingsRepository) {

    private val seedMutex = Mutex()

    private val txDao = db.transactions()
    private val catDao = db.categories()
    private val blockDao = db.blocks()
    private val eventDao = db.events()
    private val mealDao = db.meals()
    private val reviewDao = db.reviews()
    private val actDao = db.activities()

    // ---------- reads ----------

    fun observeDay(date: String): Flow<DaySnapshot> {        val core = combine(
            txDao.observe(date),
            blockDao.observe(date),
            mealDao.observe(date),
            eventDao.observe(date),
            reviewDao.observe(date),
        ) { t, b, m, e, r -> CoreDay(t, b, m, e, r) }
        return combine(core, catDao.observeAll(), actDao.observeAll()) { c, cats, acts ->
            DaySnapshot(date, c.transactions, c.blocks, c.meals, c.events, c.review, cats, acts)
        }
    }

    suspend fun snapshot(date: String): DaySnapshot = DaySnapshot(
        date = date,
        transactions = txDao.list(date),
        blocks = blockDao.list(date),
        meals = mealDao.list(date),
        events = eventDao.list(date),
        review = reviewDao.get(date),
        categories = catDao.all(),
        activities = actDao.all(),
    )

    fun factsOf(snapshot: DaySnapshot, settings: AppSettings, now: Long = System.currentTimeMillis()): DayFacts =
        buildDayFacts(
            date = snapshot.date,
            transactions = snapshot.transactions,
            blocks = snapshot.blocks,
            meals = snapshot.meals,
            events = snapshot.events,
            review = snapshot.review,
            categories = snapshot.categories,
            activities = snapshot.activities,
            cfg = settings.toConfig(),
            now = now,
        )

    suspend fun dayReport(date: String, settings: AppSettings, now: Long = System.currentTimeMillis()): DailyReport {
        val snap = snapshot(date)
        val facts = factsOf(snap, settings, now)
        val score = Scorer.scoreDay(facts)
        return Reports.daily(facts, score)
    }

    suspend fun aggregates(
        from: String,
        to: String,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
    ): List<DayAggregate> {
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        if (end < start) return emptyList()
        val cats = catDao.all()
        val acts = actDao.all()
        val cfg = settings.toConfig()

        val txs = txDao.between(from, to).groupBy { it.localDate }
        val blocks = blockDao.between(from, to).groupBy { it.localDate }
        val meals = mealDao.between(from, to).groupBy { it.localDate }
        val events = eventDao.between(from, to).groupBy { it.localDate }
        val reviews = reviewDao.between(from, to).associateBy { it.localDate }

        val out = ArrayList<DayAggregate>()
        var d = start
        while (d <= end) {
            val key = d.toString()
            val facts = buildDayFacts(
                date = key,
                transactions = txs[key].orEmpty(),
                blocks = blocks[key].orEmpty(),
                meals = meals[key].orEmpty(),
                events = events[key].orEmpty(),
                review = reviews[key],
                categories = cats,
                activities = acts,
                cfg = cfg,
                now = now,
            )
            out += DayAggregate(key, facts, Scorer.scoreDay(facts), Reports.hasAnyData(facts))
            d = d.plusDays(1)
        }
        return out
    }

    suspend fun weekReport(weekStart: String, settings: AppSettings, now: Long = System.currentTimeMillis()): WeeklyReport {        val start = LocalDate.parse(weekStart)
        val end = start.plusDays(6)
        val aggs = aggregates(start.toString(), end.toString(), settings, now)
        val prev = aggregates(start.minusDays(7).toString(), start.minusDays(1).toString(), settings, now)
            .filter { it.hasData }
        val prevAvg = prev.map { it.score.total }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val days = aggs.map { it.facts to it.score }
        return Reports.weekly(days, start.toString(), end.toString(), prevAvg)
    }

    suspend fun monthReport(ym: YearMonth, settings: AppSettings, now: Long = System.currentTimeMillis()): MonthlyReport {
        val aggs = aggregates(ym.atDay(1).toString(), ym.atEndOfMonth().toString(), settings, now)
        val prevYm = ym.minusMonths(1)
        val prev = aggregates(prevYm.atDay(1).toString(), prevYm.atEndOfMonth().toString(), settings, now)
            .filter { it.hasData }
        val prevAvg = prev.map { it.score.total }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return Reports.monthly(
            aggs.map { it.facts to it.score },
            "${ym.year}年${ym.monthValue}月",
            prevAvg,
        )
    }

    suspend fun monthScores(year: Int, month: Int, settings: AppSettings, now: Long = System.currentTimeMillis()): Map<String, Float> {
        val ym = YearMonth.of(year, month)
        val aggs = aggregates(ym.atDay(1).toString(), ym.atEndOfMonth().toString(), settings, now)
        return aggs.filter { it.hasData }.associate { it.date to it.score.total }
    }

    suspend fun recentScores(days: Int, settings: AppSettings, now: Long = System.currentTimeMillis()): Map<String, Float> {
        val today = LocalDate.now()
        val aggs = aggregates(today.minusDays(days.toLong() - 1).toString(), today.toString(), settings, now)
        return aggs.filter { it.hasData }.associate { it.date to it.score.total }
    }

    // ---------- mutations ----------

    suspend fun addTransaction(
        amountCents: Long,
        direction: Int,
        categoryId: Long,
        note: String = "",
        at: Long = System.currentTimeMillis(),
    ) {
        txDao.insert(
            TransactionEntity(
                occurredAt = at,
                localDate = Fmt.dateKeyOf(at),
                amountCents = amountCents,
                direction = direction,
                categoryId = categoryId,
                note = note,
            ),
        )
    }

    suspend fun deleteTransaction(id: Long) = txDao.delete(id)

    suspend fun updateTransaction(id: Long, amountCents: Long, categoryId: Long, note: String, at: Long? = null) {
        val t = txDao.byId(id) ?: return
        txDao.update(
            t.copy(
                amountCents = amountCents,
                categoryId = categoryId,
                note = note,
                occurredAt = at ?: t.occurredAt,
                localDate = Fmt.dateKeyOf(at ?: t.occurredAt),
            ),
        )
    }

    suspend fun startTimer(activityId: Long, at: Long = System.currentTimeMillis()) {
        val running = blockDao.running()
        if (running != null) stopTimer(running.id, at)
        blockDao.insert(
            TimeBlockEntity(
                startAt = at,
                endAt = null,
                activityId = activityId,
                source = SRC_TIMER,
                localDate = Fmt.dateKeyOf(at),
            ),
        )
    }

    suspend fun stopTimer(id: Long, at: Long = System.currentTimeMillis()) {
        val block = blockDao.running() ?: return
        if (block.id != id) return
        val end = maxOf(at, block.startAt + 60_000L)
        blockDao.stop(id, end)
    }

    suspend fun runningBlock(): TimeBlockEntity? = blockDao.running()

    suspend fun stopRunningTimer(at: Long = System.currentTimeMillis()) {
        val block = blockDao.running() ?: return
        blockDao.stop(block.id, maxOf(at, block.startAt + 60_000L))
    }

    suspend fun addBlock(activityId: Long, startAt: Long, endAt: Long, note: String = "") {
        blockDao.insert(
            TimeBlockEntity(
                startAt = startAt,
                endAt = endAt,
                activityId = activityId,
                note = note,
                source = SRC_MANUAL,
                localDate = Fmt.dateKeyOf(startAt),
            ),
        )
    }

    suspend fun deleteBlock(id: Long) = blockDao.delete(id)

    suspend fun updateBlock(id: Long, activityId: Long, startAt: Long, endAt: Long) {
        val b = blockDao.byId(id) ?: return
        blockDao.update(
            b.copy(
                activityId = activityId,
                startAt = startAt,
                endAt = endAt,
                localDate = Fmt.dateKeyOf(startAt),
            ),
        )
    }

    suspend fun addEvent(title: String, startAt: Long, endAt: Long, top3: Boolean, note: String = ""): Long =
        eventDao.insert(
            EventEntity(
                title = title,
                startAt = startAt,
                endAt = endAt,
                top3 = top3,
                note = note,
                localDate = Fmt.dateKeyOf(startAt),
            ),
        )

    suspend fun setEventStatus(id: Long, status: Int) = eventDao.setStatus(id, status)

    suspend fun eventsOn(date: String): List<EventEntity> = eventDao.list(date)

    suspend fun deleteEvent(id: Long) = eventDao.delete(id)

    suspend fun updateEvent(id: Long, title: String, startAt: Long, endAt: Long, top3: Boolean) {
        val e = eventDao.byId(id) ?: return
        eventDao.update(e.copy(title = title, startAt = startAt, endAt = endAt, top3 = top3, localDate = Fmt.dateKeyOf(startAt)))
    }

    suspend fun addMeal(
        type: Int,
        place: Int,
        kcal: Int?,
        note: String,
        tags: Int = 0,
        at: Long = System.currentTimeMillis(),
    ) {
        mealDao.insert(
            MealEntity(
                occurredAt = at,
                localDate = Fmt.dateKeyOf(at),
                type = type,
                place = place,
                note = note,
                kcal = kcal,
                tags = tags,
            ),
        )
    }

    suspend fun deleteMeal(id: Long) = mealDao.delete(id)

    suspend fun updateMeal(
        id: Long,
        type: Int,
        place: Int,
        kcal: Int?,
        note: String,
        tags: Int,
        at: Long? = null,
    ) {
        val m = mealDao.byId(id) ?: return
        mealDao.update(
            m.copy(
                type = type,
                place = place,
                kcal = kcal,
                note = note,
                tags = tags,
                occurredAt = at ?: m.occurredAt,
                localDate = Fmt.dateKeyOf(at ?: m.occurredAt),
            ),
        )
    }

    suspend fun updateReview(date: String, transform: (DayReviewEntity) -> DayReviewEntity) {
        val current = reviewDao.get(date) ?: DayReviewEntity(localDate = date)
        reviewDao.upsert(transform(current).copy(localDate = date, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addWater(date: String, deltaMl: Int) = updateReview(date) { it.copy(waterMl = (it.waterMl + deltaMl).coerceAtLeast(0)) }

    /** 清空所有记录，保留分类、活动与设置。 */
    suspend fun wipeRecords() = withContext(Dispatchers.IO) {
        txDao.clearAll()
        blockDao.clearAll()
        eventDao.clearAll()
        mealDao.clearAll()
        reviewDao.clearAll()
    }

    // ---------- export / import ----------

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun exportJson(): String = json.encodeToString(
        BackupBundle(
            categories = catDao.all(),
            transactions = txDao.all(),
            activities = actDao.all(),
            blocks = blockDao.all(),
            events = eventDao.all(),
            meals = mealDao.all(),
            reviews = reviewDao.all(),
        ),
    )

    suspend fun importJson(text: String): Result<Unit> = runCatching {
        val bundle = json.decodeFromString<BackupBundle>(text)
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            if (bundle.categories.isNotEmpty()) catDao.insertAll(bundle.categories)
            if (bundle.activities.isNotEmpty()) actDao.insertAll(bundle.activities)
            bundle.transactions.forEach { txDao.insert(it) }
            bundle.blocks.forEach { blockDao.insert(it) }
            bundle.events.forEach { eventDao.insert(it) }
            bundle.meals.forEach { mealDao.insert(it) }
            bundle.reviews.forEach { reviewDao.upsert(it) }
        }
        seedIfNeeded()
    }

    suspend fun seedIfNeeded() = seedMutex.withLock {
        if (catDao.count() > 0) return@withLock
        catDao.insertAll(defaultCategories())
        actDao.insertAll(defaultActivities())
    }

    /**
     * 彩蛋式示例数据：只有通过 intent extra `demo=true` 触发才会写入，
     * 用于空机上快速看效果，不会出现在正常使用流程里。
     */
    suspend fun seedDemoData() {
        seedIfNeeded()
        if (txDao.all().isNotEmpty() || mealDao.all().isNotEmpty() || blockDao.all().isNotEmpty()) return

        val cats = catDao.all().filter { it.kind == 0 }
        fun cat(name: String) = cats.firstOrNull { it.name == name } ?: cats.first()
        val food = cat("餐饮")
        val traffic = cat("交通")
        val home = cat("居家")
        val shop = cat("购物")
        val funCat = cat("娱乐")
        val study = cat("学习")

        val acts = actDao.all()
        fun act(name: String) = acts.firstOrNull { it.name == name } ?: acts.first()
        val work = act("工作")
        val learn = act("学习")
        val sport = act("运动")
        val rest = act("娱乐")
        val commute = act("通勤")

        val today = LocalDate.now()

        for (i in 13 downTo 0) {
            val day = today.minusDays(i.toLong())
            val key = day.toString()
            val rnd = kotlin.random.Random(i * 977 + 13)
            fun at(minute: Int) = Fmt.startOfDay(key) + minute * 60_000L

            // 花钱
            txDao.insert(
                TransactionEntity(
                    occurredAt = at(8 * 60 + rnd.nextInt(0, 30)),
                    localDate = key,
                    amountCents = (rnd.nextInt(8, 22) * 100).toLong(),
                    direction = DIR_EXPENSE,
                    categoryId = food.id,
                    note = "早餐",
                ),
            )
            txDao.insert(
                TransactionEntity(
                    occurredAt = at(12 * 60 + rnd.nextInt(0, 40)),
                    localDate = key,
                    amountCents = (rnd.nextInt(22, 46) * 100).toLong(),
                    direction = DIR_EXPENSE,
                    categoryId = food.id,
                    note = if (rnd.nextBoolean()) "外卖" else "楼下随便吃",
                ),
            )
            txDao.insert(
                TransactionEntity(
                    occurredAt = at(19 * 60 + rnd.nextInt(0, 45)),
                    localDate = key,
                    amountCents = (rnd.nextInt(30, 80) * 100).toLong(),
                    direction = DIR_EXPENSE,
                    categoryId = if (rnd.nextInt(3) == 0) funCat.id else home.id,
                    note = if (rnd.nextBoolean()) "晚饭" else "日用品",
                ),
            )
            if (rnd.nextInt(3) == 0) {
                txDao.insert(
                    TransactionEntity(
                        occurredAt = at(15 * 60),
                        localDate = key,
                        amountCents = (rnd.nextInt(15, 30) * 100).toLong(),
                        direction = DIR_EXPENSE,
                        categoryId = food.id,
                        note = "咖啡",
                    ),
                )
            }
            if (rnd.nextInt(4) == 0) {
                txDao.insert(
                    TransactionEntity(
                        occurredAt = at(9 * 60),
                        localDate = key,
                        amountCents = (rnd.nextInt(3, 8) * 100).toLong(),
                        direction = DIR_EXPENSE,
                        categoryId = traffic.id,
                        note = "地铁",
                    ),
                )
            }
            if (rnd.nextInt(5) == 0) {
                txDao.insert(
                    TransactionEntity(
                        occurredAt = at(20 * 60),
                        localDate = key,
                        amountCents = (rnd.nextInt(40, 200) * 100).toLong(),
                        direction = DIR_EXPENSE,
                        categoryId = study.id,
                        note = "买书",
                    ),
                )
            }

            // 时间块
            blockDao.insert(
                TimeBlockEntity(
                    startAt = at(9 * 60 + 20 + rnd.nextInt(0, 30)),
                    endAt = at(11 * 60 + 50),
                    activityId = work.id,
                    source = SRC_MANUAL,
                    localDate = key,
                ),
            )
            blockDao.insert(
                TimeBlockEntity(
                    startAt = at(14 * 60 + rnd.nextInt(0, 40)),
                    endAt = at(17 * 60 + 20),
                    activityId = work.id,
                    source = SRC_MANUAL,
                    localDate = key,
                ),
            )
            blockDao.insert(
                TimeBlockEntity(
                    startAt = at(21 * 60),
                    endAt = at(22 * 60 + 30),
                    activityId = if (rnd.nextBoolean()) learn.id else rest.id,
                    source = SRC_MANUAL,
                    localDate = key,
                ),
            )
            blockDao.insert(
                TimeBlockEntity(
                    startAt = at(8 * 60 + 20),
                    endAt = at(9 * 60 + 5),
                    activityId = commute.id,
                    source = SRC_MANUAL,
                    localDate = key,
                ),
            )
            if (rnd.nextInt(3) == 0) {
                blockDao.insert(
                    TimeBlockEntity(
                        startAt = at(19 * 60 + 30),
                        endAt = at(20 * 60 + 15),
                        activityId = sport.id,
                        source = SRC_MANUAL,
                        localDate = key,
                    ),
                )
            }

            // 三餐
            mealDao.insert(
                MealEntity(
                    occurredAt = at(8 * 60 + 10), localDate = key, type = MEAL_BREAKFAST,
                    place = PLACE_HOME, kcal = 380 + rnd.nextInt(-40, 60), proteinG = 14, vegG = 30,
                ),
            )
            mealDao.insert(
                MealEntity(
                    occurredAt = at(12 * 60 + 20), localDate = key, type = MEAL_LUNCH,
                    place = if (rnd.nextBoolean()) PLACE_TAKEOUT else PLACE_DINEOUT,
                    kcal = 760 + rnd.nextInt(-80, 120), proteinG = 28, vegG = 90,
                    note = if (rnd.nextInt(4) == 0) "奶茶" else "",
                ),
            )
            mealDao.insert(
                MealEntity(
                    occurredAt = at(19 * 60 + 10), localDate = key, type = MEAL_DINNER,
                    place = PLACE_HOME, kcal = 620 + rnd.nextInt(-60, 100), proteinG = 32, vegG = 150,
                ),
            )

            // 日程
            eventDao.insert(
                EventEntity(
                    title = listOf("写方案", "改需求", "写周报", "对齐进度", "整理资料")[rnd.nextInt(5)],
                    startAt = at(10 * 60),
                    endAt = at(11 * 60 + 30),
                    top3 = true,
                    status = if (rnd.nextInt(5) == 0) EVENT_PLANNED else EVENT_DONE,
                    localDate = key,
                ),
            )
            eventDao.insert(
                EventEntity(
                    title = "健身",
                    startAt = at(19 * 60 + 30),
                    endAt = at(20 * 60 + 15),
                    top3 = true,
                    status = if (rnd.nextInt(3) == 0) EVENT_SKIPPED else EVENT_DONE,
                    localDate = key,
                ),
            )
            eventDao.insert(
                EventEntity(
                    title = "回邮件",
                    startAt = at(16 * 60),
                    endAt = at(16 * 60 + 40),
                    top3 = false,
                    status = if (rnd.nextBoolean()) EVENT_DONE else EVENT_PLANNED,
                    localDate = key,
                ),
            )

            // 复盘
            reviewDao.upsert(
                DayReviewEntity(
                    localDate = key,
                    mood = 3 + rnd.nextInt(0, 3),
                    energy = 2 + rnd.nextInt(0, 3),
                    waterMl = 900 + rnd.nextInt(0, 900),
                    bedMinute = 23 * 60 + 30 + rnd.nextInt(-60, 90),
                    wakeMinute = 7 * 60 + rnd.nextInt(-20, 40),
                    note = if (i == 0) "今天状态还行，晚上把周报写了。" else "",
                ),
            )
        }
    }

    private fun defaultCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(name = "餐饮", kind = 0, color = 0xFFE07A3F.toInt(), essential = true, sortOrder = 0),
        CategoryEntity(name = "交通", kind = 0, color = 0xFF4A7FB5.toInt(), essential = true, sortOrder = 1),
        CategoryEntity(name = "居家", kind = 0, color = 0xFF5A9E6F.toInt(), essential = true, sortOrder = 2),
        CategoryEntity(name = "购物", kind = 0, color = 0xFFB07CC6.toInt(), essential = false, sortOrder = 3),
        CategoryEntity(name = "娱乐", kind = 0, color = 0xFFD96C8A.toInt(), essential = false, sortOrder = 4),
        CategoryEntity(name = "健康", kind = 0, color = 0xFFC0554A.toInt(), essential = true, sortOrder = 5),
        CategoryEntity(name = "学习", kind = 0, color = 0xFF3D9E9E.toInt(), essential = true, sortOrder = 6),
        CategoryEntity(name = "人情", kind = 0, color = 0xFFC9A227.toInt(), essential = false, sortOrder = 7),
        CategoryEntity(name = "其他", kind = 0, color = 0xFF8D8579.toInt(), essential = false, sortOrder = 8),
        CategoryEntity(name = "工资", kind = 1, color = 0xFF2F8F6B.toInt(), essential = true, sortOrder = 0),
        CategoryEntity(name = "兼职", kind = 1, color = 0xFF3D9E9E.toInt(), essential = true, sortOrder = 1),
        CategoryEntity(name = "其他收入", kind = 1, color = 0xFF8D8579.toInt(), essential = true, sortOrder = 2),
    )

    private fun defaultActivities(): List<ActivityCategoryEntity> = listOf(
        ActivityCategoryEntity(name = "工作", color = 0xFF4A7FB5.toInt(), productive = true, sortOrder = 0),
        ActivityCategoryEntity(name = "学习", color = 0xFF3D9E9E.toInt(), productive = true, sortOrder = 1),
        ActivityCategoryEntity(name = "运动", color = 0xFF5A9E6F.toInt(), productive = true, sortOrder = 2),
        ActivityCategoryEntity(name = "跑步", color = 0xFFD97742.toInt(), productive = true, sortOrder = 3),
        ActivityCategoryEntity(name = "力量", color = 0xFF8A6FB8.toInt(), productive = true, sortOrder = 4),
        ActivityCategoryEntity(name = "骑行", color = 0xFF4A7FB5.toInt(), productive = true, sortOrder = 5),
        ActivityCategoryEntity(name = "游泳", color = 0xFF3D9E9E.toInt(), productive = true, sortOrder = 6),
        ActivityCategoryEntity(name = "球类", color = 0xFF5A9E6F.toInt(), productive = true, sortOrder = 7),
        ActivityCategoryEntity(name = "瑜伽", color = 0xFFB07CC6.toInt(), productive = true, sortOrder = 8),
        ActivityCategoryEntity(name = "走路", color = 0xFF8D8579.toInt(), productive = true, sortOrder = 9),
        ActivityCategoryEntity(name = "通勤", color = 0xFF8D8579.toInt(), productive = false, sortOrder = 10),
        ActivityCategoryEntity(name = "家务", color = 0xFFC9A227.toInt(), productive = false, sortOrder = 11),
        ActivityCategoryEntity(name = "社交", color = 0xFFD96C8A.toInt(), productive = false, sortOrder = 12),
        ActivityCategoryEntity(name = "娱乐", color = 0xFFB07CC6.toInt(), productive = false, sortOrder = 13),
        ActivityCategoryEntity(name = "休息", color = 0xFF9C8F7A.toInt(), productive = false, sortOrder = 14),
    )

    /** 老版本装过的用户没有运动细分，补一次（按名字幂等），同时去重、理顺顺序 */
    suspend fun ensureSportActivities() = seedMutex.withLock {
        // 旧版本并发初始化可能插重复，这里按名字去重（保留 id 最小的）
        actDao.all().groupBy { it.name }.values.forEach { group ->
            group.sortedBy { it.id }.drop(1).forEach { actDao.delete(it.id) }
        }
        catDao.all().groupBy { it.name to it.kind }.values.forEach { group ->
            group.sortedBy { it.id }.drop(1).forEach { catDao.delete(it.id) }
        }

        val defaults = defaultActivities().associateBy { it.name }
        val existing = actDao.all()
        existing.forEach { row ->
            val d = defaults[row.name] ?: return@forEach
            if (row.sortOrder != d.sortOrder || row.productive != d.productive) {
                actDao.update(row.copy(sortOrder = d.sortOrder, productive = d.productive))
            }
        }
        val names = existing.map { it.name }.toSet()
        val missing = defaults.values.filter { it.name !in names }
        if (missing.isNotEmpty()) actDao.insertAll(missing)
    }
}
