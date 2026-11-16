package com.dayledger.app.domain

import com.dayledger.app.util.Fmt
import kotlin.math.abs
import kotlin.math.max

data class DimScore(
    val key: String,
    val label: String,
    val weight: Float,
    val score: Float,
    val available: Boolean,
    val evidence: List<String>,
    /** 是否计入总分：财务只记录、不计分 */
    val counted: Boolean = true,
)

data class ScoreResult(
    val total: Float,
    val grade: String,
    val confidence: Float,
    val dims: List<DimScore>,
    val raw: Float,
) {
    fun dim(key: String): DimScore? = dims.firstOrNull { it.key == key }
    val confidenceLabel: String
        get() = when {
            confidence >= 0.85f -> "可信"
            confidence >= 0.6f -> "参考"
            else -> "数据不足"
        }
    val availableDims: List<DimScore> get() = dims.filter { it.available }
    val bestDim: DimScore? get() = availableDims.maxByOrNull { it.score }
    val worstDim: DimScore? get() = availableDims.minByOrNull { it.score }
}

object Scorer {

    fun scoreDay(f: DayFacts): ScoreResult {
        val dims = listOf(timeDim(f), sportDim(f), moneyDim(f), dietDim(f), execDim(f), stateDim(f))
        val present = dims.filter { it.counted && it.available && it.weight > 0f }
        if (present.isEmpty()) {
            return ScoreResult(0f, "—", 0f, dims, 0f)
        }
        val weightSum = present.sumOf { it.weight.toDouble() }.toFloat()
        val raw = if (weightSum <= 0f) 0f
        else present.sumOf { (it.score * it.weight).toDouble() }.toFloat() / weightSum
        val conf = confidence(f)
        val total = (60f + (raw - 60f) * conf).coerceIn(0f, 100f)
        return ScoreResult(total, grade(total), conf, dims, raw)
    }

    fun grade(score: Float): String = when {
        score >= 90f -> "S"
        score >= 80f -> "A"
        score >= 70f -> "B"
        score >= 60f -> "C"
        else -> "D"
    }

    fun applyStrictness(score: Float, strict: Float): Float =
        (100f - (100f - score) * strict).coerceIn(0f, 100f)

    private fun timeDim(f: DayFacts): DimScore {
        val cfg = f.cfg
        if (f.blocks.isEmpty() && f.sleepMinutes == null) {
            return DimScore("time", "时间", cfg.weightTime, 0f, false, listOf("今天没有记录时间"))
        }
        val coverageRatio = clamp01(f.recordedMinutes.toFloat() / f.awakeMinutes.coerceAtLeast(1))
        val coveragePts = 20f * clamp01(coverageRatio / 0.7f)
        val focusPts = 30f * clamp01(f.productiveMinutes.toFloat() / cfg.focusTargetMin.coerceAtLeast(1))

        var sleepPts = 0f
        var sleepMax = 0f
        val sleepText: String
        if (f.sleepMinutes != null && f.bedMinute != null) {
            sleepMax = 25f
            // 7–8 小时算达标区间，超出区间每半小时扣 3，最多扣完 15
            val lower = cfg.sleepTargetMin - 30
            val upper = cfg.sleepTargetMin + 30
            val dev = when {
                f.sleepMinutes < lower -> (lower - f.sleepMinutes).toFloat()
                f.sleepMinutes > upper -> (f.sleepMinutes - upper).toFloat()
                else -> 0f
            }
            val durPts = (15f - 3f * (dev / 30f)).coerceIn(0f, 15f)
            val late = lateMinutes(f.bedMinute, cfg.bedTargetMin)
            val latePts = (10f - 2f * (late / 30f)).coerceIn(0f, 10f)
            sleepPts = durPts + latePts
            sleepText = "睡眠 ${Fmt.duration(f.sleepMinutes)} · ${Fmt.hm(f.bedMinute)} 入睡" +
                if (late > 0) "（晚 $late 分钟）" else "（准时）"
        } else {
            sleepText = "睡眠未记录（不计入本项）"
        }

        // 不用日历来安排事情的人，不该在"时间"这一维被扣一次；有安排才算结构分
        var structMax = 25f
        var structPts = 0f
        val structText: String
        if (f.events.isEmpty()) {
            structMax = 0f
            structText = "没有安排事项（结构分不计）"
        } else {
            val structRatio = f.plannedMinutes.toFloat() / f.awakeMinutes.coerceAtLeast(1)
            structPts = when {
                structRatio <= 0.4f -> 25f * (structRatio / 0.4f)
                structRatio <= 0.8f -> 25f
                else -> 25f - ((structRatio - 0.8f) / 0.1f) * 2.5f
            }.coerceIn(0f, 25f)
            structText = "计划 ${Fmt.duration(f.plannedMinutes)}（占清醒 ${Fmt.pct(structRatio)}）"
        }

        val maxPoints = 20f + 30f + sleepMax + structMax
        val points = coveragePts + focusPts + structPts + sleepPts
        var score = points / maxPoints * 100f
        score = applyStrictness(score, cfg.strictness)

        return DimScore(
            key = "time", label = "时间", weight = cfg.weightTime, score = score, available = true,
            evidence = listOf(
                "记录覆盖 ${Fmt.pct(coverageRatio)}（${Fmt.durationShort(f.recordedMinutes)} / ${Fmt.durationShort(f.awakeMinutes)}）",
                "专注 ${Fmt.duration(f.productiveMinutes)} · 目标 ${Fmt.duration(cfg.focusTargetMin)}",
                sleepText,
                structText,
            ),
        )
    }

    private fun sportDim(f: DayFacts): DimScore {
        val cfg = f.cfg
        if (f.blocks.isEmpty()) {
            return DimScore("sport", "运动", cfg.weightSport, 0f, false, listOf("今天没有记录时间"))
        }
        // 时长 70（有上限，练两小时不会比半小时多拿分）+ 连续 30（单次满 20 分钟才算完整一次）
        val volume = clamp01(f.sportMinutes.toFloat() / cfg.sportTargetMin.coerceAtLeast(1)) * 70f
        val continuity = clamp01(f.longestSportMinutes / 20f) * 30f
        val score = applyStrictness(volume + continuity, cfg.strictness)

        val ev = buildList {
            if (f.sportBlocks.isEmpty()) {
                add("今天没有运动记录")
            } else {
                val forms = f.sportBlocks.groupBy { it.activity }
                    .map { (name, list) -> "$name ${list.sumOf { b -> b.minutes }} 分钟" }
                add("形式：" + forms.joinToString(" · "))
                add("有效 ${Fmt.duration(f.sportMinutes)}（强度折算后，单次 ≥10 分钟才计入）")
                if (f.sportSessions == 0) add("每次都不足 10 分钟，按严格规则不计入")
                add("目标 ${Fmt.duration(cfg.sportTargetMin)}，单次 ≥20 分钟算完整一次")
            }
        }
        return DimScore("sport", "运动", cfg.weightSport, score, true, ev)
    }

    private fun moneyDim(f: DayFacts): DimScore {
        val cfg = f.cfg
        if (f.expenses.isEmpty() && !f.noSpend) {
            return DimScore("money", "财务", cfg.weightMoney, 0f, false, listOf("今天没有记账"), counted = false)
        }
        val budget = cfg.dailyBudgetCents.coerceAtLeast(1)
        val expense = f.expenseCents
        val over = max(0L, expense - budget)
        val budgetPts = if (expense == 0L) 50f else 50f * clamp01(1f - (over.toFloat() / budget) * 1.5f)
        val recordPts = 25f
        val p = if (expense == 0L) 0f else f.nonEssentialCents.toFloat() / expense
        val structurePts = 25f * clamp01(1f - max(0f, p - 0.2f) / 0.5f)
        var score = applyStrictness(budgetPts + recordPts + structurePts, cfg.strictness)

        val ev = buildList {
            add("支出 ${Fmt.money(expense)} / 日预算 ${Fmt.money(budget)}" + if (over > 0) "（超 ${Fmt.money(over)}）" else "（未超支）")
            add("非必需 ${Fmt.money(f.nonEssentialCents)}（${Fmt.pct(p)}）")
            add("共 ${f.expenses.size} 笔")
        }
        return DimScore("money", "财务", cfg.weightMoney, score, true, ev, counted = false)
    }

    private fun dietDim(f: DayFacts): DimScore {
        val cfg = f.cfg
        if (f.meals.isEmpty()) {
            return DimScore("diet", "饮食", cfg.weightDiet, 0f, false, listOf("今天没有记录饮食"))
        }

        // 三餐 35
        val mealPts = (if (f.hasBreakfast) 12f else 0f) + (if (f.hasLunch) 12f else 0f) + (if (f.hasDinner) 11f else 0f)
        var points = mealPts
        var maxPoints = 35f

        // 标注「吃了什么」25：写了内容的餐占比
        val noteRatio = f.mealsWithNote.toFloat() / f.meals.size
        points += 25f * noteRatio
        maxPoints += 25f

        // 健康标签 20：有标签才算；蔬果/蛋白加分，油炸/甜/含糖饮料/酒扣分
        if (f.hasAnyTags) {
            val bonus = (if (f.hasVeg) 5f else 0f) + (if (f.hasProtein) 5f else 0f)
            val penalty = f.junkTagCount * 3f
            points += (10f + bonus - penalty).coerceIn(0f, 20f)
            maxPoints += 20f
        }

        // 热量 10：填了才算，没填不计（不惩罚）
        val kcal = f.kcal
        var kcalDev: Float? = null
        if (kcal != null) {
            val target = cfg.calorieTarget.coerceAtLeast(1)
            val dev = abs(kcal - target).toFloat() / target
            kcalDev = dev
            val pts = if (dev <= 0.1f) 1f else clamp01(1f - (dev - 0.1f) / 0.4f)
            points += 10f * pts
            maxPoints += 10f
        }

        // 饮水 10：喝了才算；0 视为没记，不罚
        if (f.waterMl > 0) {
            points += 10f * clamp01(f.waterMl / 1500f)
            maxPoints += 10f
        }

        var penalty = 0f
        if (f.lateSnack) penalty += 10f
        if (f.sugary) penalty += 5f
        if (f.takeoutCount >= 2) penalty += 3f

        val base = (points / maxPoints * 100f) - penalty
        val score = applyStrictness(base, cfg.strictness)

        val ev = buildList {
            add(
                "三餐 " + listOfNotNull(
                    if (f.hasBreakfast) "早" else null,
                    if (f.hasLunch) "午" else null,
                    if (f.hasDinner) "晚" else null,
                ).joinToString("·").ifEmpty { "未记" },
            )
            add("标注 ${f.mealsWithNote}/${f.meals.size} 餐（写了吃了什么）")
            add(
                if (!f.hasAnyTags) "没有健康标签（健康度未计）"
                else "标签：${if (f.hasVeg) "有蔬果 " else ""}${if (f.hasProtein) "有蛋白 " else ""}".trim() +
                    if (f.junkTagCount > 0) " · 油炸/甜/饮料/酒 ${f.junkTagCount} 类" else "",
            )
            add(if (kcal != null) "热量 $kcal kcal（目标 ${cfg.calorieTarget}，偏差 ${Fmt.pct(kcalDev ?: 0f)}）" else "热量未填（不计）")
            add("饮水 ${f.waterMl}ml" + if (f.waterMl == 0) "（未记，不计）" else "")
            if (penalty > 0f) add("扣分 −${penalty.toInt()}（" + listOfNotNull(
                if (f.lateSnack) "夜宵" else null,
                if (f.sugary) "含糖饮料" else null,
                if (f.takeoutCount >= 2) "外卖" else null,
            ).joinToString("、") + "）")
        }
        return DimScore("diet", "饮食", cfg.weightDiet, score, true, ev)
    }

    private fun execDim(f: DayFacts): DimScore {
        val cfg = f.cfg
        if (f.events.isEmpty()) {
            return DimScore("exec", "执行", cfg.weightExec, 0f, false, listOf("今天没有安排事项"))
        }
        val total = f.events.size
        val completionPts = 50f * f.eventsDone / total

        var points = completionPts
        var maxPoints = 50f
        if (f.top3.isNotEmpty()) {
            points += 30f * f.top3Done / f.top3.size
            maxPoints += 30f
        }
        val punctual = (20f - 8f * (f.eventsSkipped + f.eventsOverdue)).coerceIn(0f, 20f)
        points += punctual
        maxPoints += 20f

        val score = applyStrictness(points / maxPoints * 100f, cfg.strictness)
        val ev = buildList {
            add("完成 $f.eventsDone/$total 件")
            if (f.top3.isNotEmpty()) add("重要三件事 $f.top3Done/${f.top3.size}")
            if (f.eventsSkipped > 0) add("${f.eventsSkipped} 件放弃")
            if (f.eventsOverdue > 0) add("${f.eventsOverdue} 件超时未做")
        }
        return DimScore("exec", "执行", cfg.weightExec, score, true, ev)
    }

    private fun stateDim(f: DayFacts): DimScore {
        val cfg = f.cfg
        val mood = f.mood
        val energy = f.energy
        if (mood == null && energy == null) {
            return DimScore("state", "状态", cfg.weightState, 0f, false, listOf("今天还没有自评"))
        }
        var points = 0f
        var maxPoints = 0f
        if (mood != null) {
            points += 50f * mood / 5f
            maxPoints += 50f
        }
        if (energy != null) {
            points += 50f * energy / 5f
            maxPoints += 50f
        }
        val score = applyStrictness(points / maxPoints * 100f, cfg.strictness)
        return DimScore(
            "state", "状态", cfg.weightState, score, true,
            listOf("心情 ${mood ?: "—"} · 精力 ${energy ?: "—"}"),
        )
    }

    fun confidence(f: DayFacts): Float {
        val coverage = clamp01(f.recordedMinutes.toFloat() / f.awakeMinutes.coerceAtLeast(1))
        // 财务不算分，也就不参与"数据完整度"
        val types = listOf(
            f.blocks.isNotEmpty(),
            f.meals.isNotEmpty(),
            f.events.isNotEmpty(),
            f.mood != null || f.energy != null,
        )
        val completeness = types.count { it } / 4f
        return (0.5f * coverage + 0.5f * completeness).coerceIn(0.3f, 1f)
    }
}
