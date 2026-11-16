package com.dayledger.app.domain

import com.dayledger.app.util.Fmt
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DailyReport(
    val date: String,
    val score: ScoreResult,
    val headline: String,
    val good: List<String>,
    val improve: List<String>,
    val suggestions: List<String>,
)

data class DayPoint(val date: String, val score: Float?, val confidence: Float)

data class WeeklyReport(
    val weekStart: String,
    val weekEnd: String,
    val points: List<DayPoint>,
    val avg: Float?,
    val stability: Float?,
    val trend: Float?,
    val totalSpendCents: Long,
    val spendByCategory: List<Pair<String, Long>>,
    val productiveMinutes: Int,
    val minutesByActivity: List<Pair<String, Int>>,
    val completionRate: Float?,
    val avgKcal: Int?,
    val takeoutCount: Int,
    val lateSnackCount: Int,
    val dimAverages: List<Pair<String, Float>>,
    val insights: List<String>,
    val suggestions: List<String>,
    val best: DayPoint?,
    val worst: DayPoint?,
)

data class MonthlyReport(
    val monthLabel: String,
    val scoredDays: Int,
    val totalDays: Int,
    val avg: Float?,
    val prevAvg: Float?,
    val best: DayPoint?,
    val worst: DayPoint?,
    val dimAverages: List<Pair<String, Float>>,
    val weekAverages: List<Float?>,
    val totalSpendCents: Long,
    val spendByCategory: List<Pair<String, Long>>,
    val productiveMinutes: Int,
    val sportMinutes: Int,
    val sportDays: Int,
    val topActivities: List<Pair<String, Int>>,
    val avgKcal: Int?,
    val takeoutCount: Int,
    val lateSnackDays: Int,
    val completionRate: Float?,
    val insights: List<String>,
    val suggestions: List<String>,
) {
    val trend: Float? get() = if (avg != null && prevAvg != null) avg - prevAvg else null
}

object Reports {

    fun hasAnyData(f: DayFacts): Boolean =
        f.expenses.isNotEmpty() || f.blocks.isNotEmpty() || f.meals.isNotEmpty() ||
            f.events.isNotEmpty() || f.mood != null || f.energy != null || f.noSpend

    fun daily(f: DayFacts, s: ScoreResult): DailyReport {
        val cfg = f.cfg
        val best = s.bestDim
        val worst = s.worstDim

        val headline = when {
            s.total >= 85f -> "今天几乎没什么可挑的，${best?.label ?: "节奏"}撑起了整天的分。"
            s.total >= 70f -> "整体不错，${worst?.label ?: "细节"}拖了一点后腿。"
            s.total >= 60f -> "${worst?.label ?: "状态"}是今天的主要问题，其余还算稳。"
            else -> "今天有点散。先看${worst?.label ?: "状态"}，别急着补记录。"
        }

        val good = buildList {
            if (f.productiveMinutes >= cfg.focusTargetMin) {
                add("专注 ${Fmt.duration(f.productiveMinutes)}，超过目标 ${Fmt.duration(cfg.focusTargetMin)}")
            }
            if (f.expenses.isNotEmpty() && f.expenseCents <= cfg.dailyBudgetCents) {
                add("预算内，还剩 ${Fmt.money(cfg.dailyBudgetCents - f.expenseCents)}")
            }
            if (f.hasBreakfast && f.hasLunch && f.hasDinner && f.takeoutCount == 0) {
                add("三餐规律，一顿外卖都没点")
            }
            if (f.top3.isNotEmpty() && f.top3Done == f.top3.size) {
                add("重要三件事全部做完")
            }
            if (f.sleepMinutes != null && f.sleepMinutes >= cfg.sleepTargetMin) {
                add("睡了 ${Fmt.duration(f.sleepMinutes)}")
            }
            if (f.waterMl >= 1500) add("喝水 ${f.waterMl}ml，达标")
            if (f.events.isNotEmpty() && f.eventsSkipped == 0 && f.eventsOverdue == 0) {
                add("没有爽约，也没有超时")
            }
        }.take(3)

        val over = f.expenseCents - cfg.dailyBudgetCents
        val improve = buildList {
            if (f.sleepMinutes != null && f.sleepMinutes < cfg.sleepTargetMin - 45) {
                val bed = f.bedMinute
                add("只睡了 ${Fmt.duration(f.sleepMinutes)}" + if (bed != null) "，${Fmt.hm(bed)} 才睡" else "")
            }
            if (over > 0) {
                val top = f.spendByCategory.entries.firstOrNull()
                add("超支 ${Fmt.money(over)}" + if (top != null) "，主要在${top.key}" else "")
            }
            val missed = f.eventsSkipped + f.eventsOverdue
            if (missed > 0) add("$missed 项安排没有做成")
            if (f.takeoutCount >= 2) add("点了 ${f.takeoutCount} 次外卖")
            if (f.lateSnack) add("吃了夜宵")
            val protein = f.proteinG
            if (protein != null && protein < cfg.proteinTargetG * 0.8f) {
                add("蛋白 ${protein}g，离目标还差 ${cfg.proteinTargetG - protein}g")
            }
            if (f.sugary) add("有含糖饮料")
        }.distinct().take(3)

        val suggestions = buildList {
            val undone = f.top3.firstOrNull { !it.done && !it.skipped }
            if (undone != null) add("把「${undone.title}」放到明天上午第一件")
            val remain = cfg.dailyBudgetCents - f.expenseCents
            add(
                if (remain > 0) "明天可用预算 ${Fmt.money(remain)}"
                else "明天先别再超，把 ${Fmt.money(-remain)} 找回来",
            )
            if (f.sleepMinutes == null) {
                add("今晚顺手记一下几点睡，睡眠占总分 1/4")
            } else if (f.sleepMinutes < cfg.sleepTargetMin - 45 || (f.bedMinute ?: 0) > cfg.bedTargetMin) {
                add("今晚 23:30 前躺下，这是明天分数最便宜的一笔")
            } else if (f.productiveMinutes < cfg.focusTargetMin) {
                add("明天上午留 90 分钟给最难的那件事")
            }
            if (f.events.isEmpty()) add("给明天排三件要事，别超过三件")
        }.take(3)

        return DailyReport(f.date, s, headline, good, improve, suggestions)
    }

    fun monthly(
        days: List<Pair<DayFacts, ScoreResult>>,
        monthLabel: String,
        prevAvg: Float?,
    ): MonthlyReport {
        val points = days.map { (f, s) ->
            if (hasAnyData(f)) DayPoint(f.date, s.total, s.confidence) else DayPoint(f.date, null, 0f)
        }
        val scored = points.filter { it.score != null }
        val weightSum = scored.sumOf { it.confidence.toDouble() }.toFloat()
        val avg = if (scored.isEmpty() || weightSum <= 0f) null
        else scored.sumOf { (it.score!! * it.confidence).toDouble() }.toFloat() / weightSum

        val dims = listOf(
            "time" to "时间",
            "sport" to "运动",
            "money" to "财务",
            "diet" to "饮食",
            "exec" to "执行",
            "state" to "状态",
        )
        val dimAverages = dims.mapNotNull { (key, label) ->
            val vals = days.mapNotNull { (_, s) -> s.dim(key)?.takeIf { it.available }?.score }
            if (vals.isEmpty()) null else label to vals.average().toFloat()
        }

        val weeks = days.groupBy { Fmt.parse(it.first.date).dayOfMonth.let { d -> (d - 1) / 7 } }
            .toSortedMap()
            .values.map { group ->
                val sub = group.map { it.second.total }.filter { it > 0f }
                if (sub.isEmpty()) null else sub.average().toFloat()
            }

        val allExpenses = days.flatMap { it.first.expenses }
        val totalSpend = allExpenses.sumOf { it.amountCents }
        val spendByCategory = allExpenses.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amountCents } }
            .toList().sortedByDescending { it.second }.take(6)

        val allBlocks = days.flatMap { it.first.blocks }
        val topActivities = allBlocks.groupBy { it.activity }
            .mapValues { e -> e.value.sumOf { it.minutes } }
            .toList().sortedByDescending { it.second }.take(6)
        val productive = days.sumOf { it.first.productiveMinutes }
        val sportMinutes = days.sumOf { it.first.sportMinutes }
        val sportDays = days.count { it.first.sportMinutes > 0 }

        val allEvents = days.flatMap { it.first.events }
        val completionRate = if (allEvents.isEmpty()) null
        else allEvents.count { it.done }.toFloat() / allEvents.size

        val kcals = days.mapNotNull { it.first.kcal }
        val avgKcal = kcals.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val takeout = days.sumOf { it.first.takeoutCount }
        val lateSnack = days.count { it.first.lateSnack }

        val cfg = days.firstOrNull()?.first?.cfg
        val zeroDays = days.size - scored.size
        val best = scored.maxByOrNull { it.score!! }
        val worst = scored.minByOrNull { it.score!! }

        val insights = buildList {
            if (zeroDays > 0) add("这个月有 $zeroDays 天没有记录，报告只基于剩下的 ${scored.size} 天")
            if (cfg != null) {
                val monthBudget = cfg.dailyBudgetCents * 30
                if (totalSpend > monthBudget * 1.05) {
                    add("月支出 ${Fmt.money(totalSpend)}，超出月预算 ${Fmt.money(totalSpend - monthBudget)}")
                } else if (totalSpend > 0) {
                    add("月支出 ${Fmt.money(totalSpend)}，在预算内，日均 ${Fmt.money(totalSpend / 30)}")
                }
                spendByCategory.firstOrNull()?.let { (cat, cents) ->
                    if (totalSpend > 0) add("$cat 花了 ${Fmt.money(cents)}，占 ${Fmt.pct(cents.toFloat() / totalSpend)}")
                }
                add("运动 $sportDays 天，共 ${Fmt.duration(sportMinutes)}")
                if (avgKcal != null) add("平均每天 $avgKcal kcal，外卖 $takeout 次")
            }
            best?.let { add("最好的一天是 ${Fmt.shortDate(it.date)}（${it.score!!.roundToInt()} 分）") }
        }.take(5)

        val suggestions = buildList {
            val weakest = dimAverages.minByOrNull { it.second }
            when (weakest?.first) {
                "时间" -> add("下个月先做到「每天记满 8 小时」——不用记准，记全就有用")
                "运动" -> add("运动目标是每月 12 天，先从每周 3 次、每次 20 分钟开始")
                "饮食" -> add("把早餐固定下来，三餐齐了饮食分就稳了")
                "执行" -> add("每天只排 3 件要事，宁可少排也别爽约")
                "状态" -> add("睡前点一下心情和精力，一个月后就能看到规律")
                else -> add("先把目标填进设置，分数才有参照")
            }
            if (avg != null && avg < 70f) add("下个月目标定 75：每天多睡半小时、每周运动 3 次")
            if (trendOf(avg, prevAvg) != null && trendOf(avg, prevAvg)!! < 0f) {
                add("比上个月低了 ${String.format("%.1f", -trendOf(avg, prevAvg)!!)} 分，看看是哪一维掉了")
            }
        }.distinct().take(3)

        return MonthlyReport(
            monthLabel = monthLabel,
            scoredDays = scored.size,
            totalDays = days.size,
            avg = avg,
            prevAvg = prevAvg,
            best = best,
            worst = worst,
            dimAverages = dimAverages,
            weekAverages = weeks,
            totalSpendCents = totalSpend,
            spendByCategory = spendByCategory,
            productiveMinutes = productive,
            sportMinutes = sportMinutes,
            sportDays = sportDays,
            topActivities = topActivities,
            avgKcal = avgKcal,
            takeoutCount = takeout,
            lateSnackDays = lateSnack,
            completionRate = completionRate,
            insights = insights,
            suggestions = suggestions,
        )
    }

    private fun trendOf(avg: Float?, prev: Float?): Float? =
        if (avg != null && prev != null) avg - prev else null

    fun weekly(
        days: List<Pair<DayFacts, ScoreResult>>,
        weekStart: String,
        weekEnd: String,
        prevAvg: Float?,
    ): WeeklyReport {
        val points = days.map { (f, s) ->
            if (hasAnyData(f)) DayPoint(f.date, s.total, s.confidence)
            else DayPoint(f.date, null, 0f)
        }
        val scored = points.filter { it.score != null }
        val weightSum = scored.sumOf { it.confidence.toDouble() }.toFloat()
        val avg = if (scored.isEmpty() || weightSum <= 0f) null
        else scored.sumOf { (it.score!! * it.confidence).toDouble() }.toFloat() / weightSum

        val mean = scored.map { it.score!! }.average().takeIf { scored.isNotEmpty() }
        val variance = if (mean != null && scored.size > 1)
            scored.sumOf { (it.score!! - mean) * (it.score!! - mean) } / scored.size else 0.0
        val std = sqrt(variance.toDouble()).toFloat()
        val stability = if (scored.size < 2) null else (100f - 2.5f * std).coerceIn(0f, 100f)

        val allExpenses = days.flatMap { it.first.expenses }
        val totalSpend = allExpenses.sumOf { it.amountCents }
        val spendByCategory = allExpenses.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amountCents } }
            .toList().sortedByDescending { it.second }.take(6)

        val allBlocks = days.flatMap { it.first.blocks }
        val minutesByActivity = allBlocks.groupBy { it.activity }
            .mapValues { e -> e.value.sumOf { it.minutes } }
            .toList().sortedByDescending { it.second }.take(6)
        val productive = days.sumOf { it.first.productiveMinutes }

        val allEvents = days.flatMap { it.first.events }
        val completionRate = if (allEvents.isEmpty()) null
        else allEvents.count { it.done }.toFloat() / allEvents.size

        val kcals = days.mapNotNull { it.first.kcal }
        val avgKcal = kcals.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val takeout = days.sumOf { it.first.takeoutCount }
        val lateSnack = days.count { it.first.lateSnack }

        val dims = listOf(
            "time" to "时间",
            "sport" to "运动",
            "money" to "财务",
            "diet" to "饮食",
            "exec" to "执行",
            "state" to "状态",
        )
        val dimAverages = dims.mapNotNull { (key, label) ->
            val vals = days.mapNotNull { (_, s) -> s.dim(key)?.takeIf { it.available }?.score }
            if (vals.isEmpty()) null else label to vals.average().toFloat()
        }

        val cfg = days.firstOrNull()?.first?.cfg
        val zeroDays = points.count { it.score == null }
        val best = scored.maxByOrNull { it.score!! }
        val worst = scored.minByOrNull { it.score!! }

        val insights = buildList {
            if (zeroDays >= 2) add("本周有 $zeroDays 天没有记录，报告只基于剩下的 ${scored.size} 天")
            if (cfg != null) {
                val weekBudget = cfg.dailyBudgetCents * 7
                if (totalSpend > weekBudget * 1.05) {
                    add("本周支出 ${Fmt.money(totalSpend)}，超出周预算 ${Fmt.money(totalSpend - weekBudget)}")
                }
                val top = spendByCategory.firstOrNull()
                if (top != null && totalSpend > 0 && top.second.toFloat() / totalSpend > 0.35f) {
                    add("${top.first}占支出的 ${Fmt.pct(top.second.toFloat() / totalSpend)}，是本周最大的开销方向")
                }
                val target = cfg.focusTargetMin * 7
                if (productive < target * 0.7f) {
                    add("专注 ${Fmt.duration(productive)}，只有目标的 ${Fmt.pct(productive.toFloat() / target)}")
                }
                if (takeout >= 4) add("外卖 $takeout 次，比在家吃多花了一笔")
                if (lateSnack >= 2) add("有 $lateSnack 天吃了夜宵")
            }
            if (completionRate != null && completionRate < 0.6f) {
                add("完成率 ${Fmt.pct(completionRate)}，计划排得偏满——少排两件会做得更多")
            }
            dimAverages.minByOrNull { it.second }?.let {
                add("${it.first}是本周最弱的一维，平均 ${it.second.roundToInt()} 分")
            }
        }.take(4)

        val suggestions = buildList {
            val weakest = dimAverages.minByOrNull { it.second }
            when (weakest?.first) {
                "时间" -> add("下周挑一天完整记录时间（从起床到睡前），你会看到最多的东西")
                "财务" -> add("下周把日预算写在记账页顶部，超了就当天停")
                "饮食" -> add("下周先把三餐记全，热量可以慢慢补")
                "执行" -> add("每天只标 3 件要事，其余都算加分项")
                "状态" -> add("每天睡前点一下心情和精力，两周后能看出规律")
                else -> add("下周一先把目标填进设置，分数才有参照")
            }
            if (avg != null && avg < 70f) add("下周目标定在 75：只需要每天多睡半小时、少点一次外卖")
            if (scored.size < 5) add("先连续记满 5 天，报告会比现在有用得多")
        }.distinct().take(3)

        return WeeklyReport(
            weekStart = weekStart,
            weekEnd = weekEnd,
            points = points,
            avg = avg,
            stability = stability,
            trend = if (avg != null && prevAvg != null) avg - prevAvg else null,
            totalSpendCents = totalSpend,
            spendByCategory = spendByCategory,
            productiveMinutes = productive,
            minutesByActivity = minutesByActivity,
            completionRate = completionRate,
            avgKcal = avgKcal,
            takeoutCount = takeout,
            lateSnackCount = lateSnack,
            dimAverages = dimAverages,
            insights = insights,
            suggestions = suggestions,
            best = best,
            worst = worst,
        )
    }
}
