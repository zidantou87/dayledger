package com.dayledger.app.domain

import com.dayledger.app.data.DIR_EXPENSE
import com.dayledger.app.data.DayReviewEntity
import com.dayledger.app.data.EVENT_DONE
import com.dayledger.app.data.EVENT_SKIPPED
import com.dayledger.app.data.EventEntity
import com.dayledger.app.data.MEAL_BREAKFAST
import com.dayledger.app.data.MEAL_DINNER
import com.dayledger.app.data.MEAL_LUNCH
import com.dayledger.app.data.MEAL_SNACK
import com.dayledger.app.data.MealTag
import com.dayledger.app.data.MealEntity
import com.dayledger.app.data.ActivityCategoryEntity
import com.dayledger.app.data.CategoryEntity
import com.dayledger.app.data.PLACE_TAKEOUT
import com.dayledger.app.data.TimeBlockEntity
import com.dayledger.app.data.TransactionEntity
import com.dayledger.app.util.Fmt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScoreConfig(
    val dailyBudgetCents: Long = 12000,
    val calorieTarget: Int = 2200,
    val proteinTargetG: Int = 96,
    val focusTargetMin: Int = 180,
    val sportTargetMin: Int = 30,
    val sleepTargetMin: Int = 450,
    val bedTargetMin: Int = 1410,
    val awakeStartMinute: Int = 480,
    val awakeEndMinute: Int = 1440,
    val weightTime: Float = 30f,
    val weightSport: Float = 10f,
    val weightMoney: Float = 20f,
    val weightDiet: Float = 20f,
    val weightExec: Float = 20f,
    val weightState: Float = 10f,
    val strictness: Float = 1f,
)

data class TxFact(
    val amountCents: Long,
    val direction: Int,
    val categoryId: Long,
    val category: String,
    val essential: Boolean,
    val note: String,
    val minute: Int,
)

data class BlockFact(
    val startMinute: Int,
    val endMinute: Int,
    val minutes: Int,
    val activity: String,
    val productive: Boolean,
    val note: String,
    val sport: Boolean = sportIntensity(activity) > 0f,
    val intensity: Float = sportIntensity(activity).takeIf { it > 0f } ?: 1f,
)

/** 不同运动形式的强度系数（近似 MET 比）。不认识的按 1.0 算。 */
val SPORT_INTENSITY: Map<String, Float> = linkedMapOf(
    "跑步" to 1.2f,
    "游泳" to 1.2f,
    "跳绳" to 1.2f,
    "力量" to 1.1f,
    "球类" to 1.1f,
    "骑行" to 1.0f,
    "运动" to 1.0f,
    "瑜伽" to 0.8f,
    "走路" to 0.7f,
)

fun sportIntensity(activity: String): Float =
    SPORT_INTENSITY.entries.firstOrNull { activity.contains(it.key) }?.value ?: 0f

/** 单次不到 10 分钟不算有效训练，严格一点 */
const val SPORT_MIN_SESSION_MIN = 10

data class EventFact(
    val id: Long,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val minutes: Int,
    val done: Boolean,
    val skipped: Boolean,
    val overdue: Boolean,
    val top3: Boolean,
)

data class MealFact(
    val type: Int,
    val minute: Int,
    val kcal: Int?,
    val proteinG: Int?,
    val vegG: Int?,
    val place: Int,
    val note: String,
    val tags: Int = 0,
)

data class DayFacts(
    val date: String,
    val nowMillis: Long,
    val expenses: List<TxFact>,
    val incomes: List<TxFact>,
    val blocks: List<BlockFact>,
    val events: List<EventFact>,
    val meals: List<MealFact>,
    val waterMl: Int,
    val noSpend: Boolean,
    val mood: Int?,
    val energy: Int?,
    val sleepMinutes: Int?,
    val bedMinute: Int?,
    val wakeMinute: Int?,
    val awakeMinutes: Int,
    val cfg: ScoreConfig,
) {
    val expenseCents: Long = expenses.sumOf { it.amountCents }
    val incomeCents: Long = incomes.sumOf { it.amountCents }
    val nonEssentialCents: Long = expenses.filter { !it.essential }.sumOf { it.amountCents }
    val recordedMinutes: Int = blocks.sumOf { it.minutes }
    val sportBlocks: List<BlockFact> = blocks.filter { it.sport }
    /** 有效运动分钟：单次 ≥10 分钟才计入，再乘强度系数 */
    val sportMinutes: Int = sportBlocks.sumOf { b ->
        if (b.minutes >= SPORT_MIN_SESSION_MIN) (b.minutes * b.intensity).roundToInt() else 0
    }
    val sportRawMinutes: Int = sportBlocks.sumOf { it.minutes }
    val sportSessions: Int = sportBlocks.count { it.minutes >= SPORT_MIN_SESSION_MIN }
    val longestSportMinutes: Int = sportBlocks
        .filter { it.minutes >= SPORT_MIN_SESSION_MIN }
        .maxOfOrNull { (it.minutes * it.intensity).roundToInt() } ?: 0

    /** 专注只算工作/学习这类，运动单独进运动维度，避免一件事拿两份分 */
    val productiveMinutes: Int = blocks.filter { it.productive && !it.sport }.sumOf { it.minutes }
    val plannedMinutes: Int = events.sumOf { it.minutes }
    val kcal: Int? = meals.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()
    val proteinG: Int? = meals.mapNotNull { it.proteinG }.takeIf { it.isNotEmpty() }?.sum()
    val vegG: Int? = meals.mapNotNull { it.vegG }.takeIf { it.isNotEmpty() }?.sum()
    val takeoutCount: Int = meals.count { it.place == PLACE_TAKEOUT }
    val mealsWithNote: Int = meals.count { it.note.isNotBlank() }
    val hasAnyTags: Boolean = meals.any { it.tags != 0 }
    val hasVeg: Boolean = meals.any { it.tags and MealTag.VEG != 0 || it.tags and MealTag.FRUIT != 0 }
    val hasProtein: Boolean = meals.any { it.tags and MealTag.PROTEIN != 0 }
    val junkTagCount: Int = listOf(MealTag.FRIED, MealTag.SWEET, MealTag.SUGARY, MealTag.ALCOHOL)
        .count { tag -> meals.any { it.tags and tag != 0 } }
    val lateSnack: Boolean = meals.any { it.type == MEAL_SNACK && (it.minute >= 22 * 60 || it.minute < 4 * 60) }
    val sugary: Boolean = meals.any { m ->
        m.tags and MealTag.SUGARY != 0 ||
            listOf("奶茶", "可乐", "饮料", "拿铁", "星冰乐", "蛋糕").any { m.note.contains(it) }
    }
    val eventsDone: Int = events.count { it.done }
    val eventsSkipped: Int = events.count { it.skipped }
    val eventsOverdue: Int = events.count { it.overdue }
    val top3: List<EventFact> = events.filter { it.top3 }
    val top3Done: Int = top3.count { it.done }
    val hasBreakfast: Boolean = meals.any { it.type == MEAL_BREAKFAST }
    val hasLunch: Boolean = meals.any { it.type == MEAL_LUNCH }
    val hasDinner: Boolean = meals.any { it.type == MEAL_DINNER }
    val spendByCategory: Map<String, Long> =
        expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountCents } }
            .toList().sortedByDescending { it.second }.toMap()
}

fun clamp01(v: Float): Float = max(0f, min(1f, v))

fun sleepDurationMinutes(bed: Int, wake: Int): Int {
    val awake = if (bed < wake) bed + 1440 - wake else bed - wake
    return (1440 - awake).coerceIn(0, 1440)
}

fun lateMinutes(bed: Int, target: Int): Int {
    val bedAbs = if (bed < 720) bed + 1440 else bed
    return max(0, bedAbs - target)
}

fun buildDayFacts(
    date: String,
    transactions: List<TransactionEntity>,
    blocks: List<TimeBlockEntity>,
    meals: List<MealEntity>,
    events: List<EventEntity>,
    review: DayReviewEntity?,
    categories: List<CategoryEntity>,
    activities: List<ActivityCategoryEntity>,
    cfg: ScoreConfig,
    now: Long = System.currentTimeMillis(),
): DayFacts {
    val catById = categories.associateBy { it.id }
    val actById = activities.associateBy { it.id }

    val txFacts = transactions.map { t ->
        val c = catById[t.categoryId]
        TxFact(
            amountCents = t.amountCents,
            direction = t.direction,
            categoryId = t.categoryId,
            category = c?.name ?: "未分类",
            essential = c?.essential ?: false,
            note = t.note,
            minute = Fmt.minuteOf(t.occurredAt),
        )
    }

    val blockFacts = blocks.map { b ->
        val end = b.endAt ?: now
        val minutes = ((end - b.startAt) / 60_000L).toInt().coerceAtLeast(0)
        val a = b.activityId.let { actById[it] }
        val name = a?.name ?: "未分类"
        BlockFact(
            startMinute = Fmt.minuteOf(b.startAt),
            endMinute = Fmt.minuteOf(end),
            minutes = minutes,
            activity = name,
            productive = a?.productive ?: false,
            note = b.note,
        )
    }

    val eventFacts = events.map { e ->
        EventFact(
            id = e.id,
            title = e.title,
            startMinute = Fmt.minuteOf(e.startAt),
            endMinute = Fmt.minuteOf(e.endAt),
            minutes = ((e.endAt - e.startAt) / 60_000L).toInt().coerceAtLeast(0),
            done = e.status == EVENT_DONE,
            skipped = e.status == EVENT_SKIPPED,
            overdue = e.status == 0 && e.endAt < now,
            top3 = e.top3,
        )
    }

    val mealFacts = meals.map { m ->
        MealFact(
            type = m.type,
            minute = Fmt.minuteOf(m.occurredAt),
            kcal = m.kcal,
            proteinG = m.proteinG,
            vegG = m.vegG,
            place = m.place,
            note = m.note,
            tags = m.tags,
        )
    }

    val bed = review?.bedMinute
    val wake = review?.wakeMinute
    val sleep = if (bed != null && wake != null) sleepDurationMinutes(bed, wake) else null

    val awake = when {
        bed != null && wake != null -> {
            val a = if (bed < wake) bed + 1440 - wake else bed - wake
            a.coerceIn(240, 1200)
        }
        else -> (cfg.awakeEndMinute - cfg.awakeStartMinute).coerceIn(240, 1440)
    }

    return DayFacts(
        date = date,
        nowMillis = now,
        expenses = txFacts.filter { it.direction == DIR_EXPENSE },
        incomes = txFacts.filter { it.direction != DIR_EXPENSE },
        blocks = blockFacts,
        events = eventFacts,
        meals = mealFacts,
        waterMl = review?.waterMl ?: 0,
        noSpend = review?.noSpend ?: false,
        mood = review?.mood,
        energy = review?.energy,
        sleepMinutes = sleep,
        bedMinute = bed,
        wakeMinute = wake,
        awakeMinutes = awake,
        cfg = cfg,
    )
}
