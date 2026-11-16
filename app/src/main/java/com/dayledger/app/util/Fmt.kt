package com.dayledger.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

object Fmt {

    val zone: ZoneId get() = ZoneId.systemDefault()
    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): LocalDate = LocalDate.now(zone)
    fun todayKey(): String = today().toString()
    fun key(d: LocalDate): String = d.toString()
    fun parse(key: String): LocalDate = LocalDate.parse(key)

    fun dateKeyOf(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun startOfDay(dateKey: String): Long =
        LocalDate.parse(dateKey).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(dateKey: String): Long =
        LocalDate.parse(dateKey).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun millisAt(dateKey: String, minute: Int): Long =
        LocalDate.parse(dateKey).atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    fun minuteOf(epochMillis: Long): Int {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return t.hour * 60 + t.minute
    }

    fun hm(epochMillis: Long): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun hm(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun dateLabel(key: String): String {
        val d = LocalDate.parse(key)
        return "${d.monthValue}月${d.dayOfMonth}日 ${weekday(d)}"
    }

    fun shortDate(key: String): String {
        val d = LocalDate.parse(key)
        return "${d.monthValue}/${d.dayOfMonth}"
    }

    fun weekday(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]

    fun isToday(key: String): Boolean = key == todayKey()

    fun money(cents: Long): String {
        val absC = abs(cents)
        val yuan = absC / 100
        val fen = absC % 100
        val body = if (fen == 0L) "$yuan" else "$yuan.${fen.toString().padStart(2, '0')}"
        return (if (cents < 0) "-¥" else "¥") + body
    }

    fun moneyPlain(cents: Long): String {
        val yuan = cents / 100
        val fen = abs(cents % 100)
        return if (fen == 0L) "$yuan" else "$yuan.${fen.toString().padStart(2, '0')}"
    }

    fun duration(minutes: Int): String {
        if (minutes <= 0) return "0 分钟"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m 分钟"
            m == 0 -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }

    fun durationShort(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h${m}m"
        }
    }

    fun pct(v: Float): String = "${(v * 100).roundToInt()}%"

    fun score(v: Float?): String = if (v == null) "—" else v.roundToInt().toString()

    fun monthLabel(year: Int, month: Int): String = "${year}年${month}月"
}
