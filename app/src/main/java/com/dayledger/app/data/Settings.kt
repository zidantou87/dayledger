package com.dayledger.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dayledger.app.domain.ScoreConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("settings")

data class AppSettings(
    val monthlyBudgetCents: Long = 360_000L,
    val calorieTarget: Int = 2200,
    val proteinTargetG: Int = 96,
    val weightKg: Int = 70,
    val focusTargetMin: Int = 240,
    val sportTargetMin: Int = 30,
    val sleepTargetMin: Int = 450,
    val bedTargetMin: Int = 1410,
    val weightTime: Float = 30f,
    val weightSport: Float = 10f,
    val weightMoney: Float = 20f,
    val weightDiet: Float = 20f,
    val weightExec: Float = 20f,
    val weightState: Float = 10f,
    val strictness: Float = 1.15f,
    val showScore: Boolean = true,
    val remindersEnabled: Boolean = true,
    val reviewReminderEnabled: Boolean = true,
) {
    val dailyBudgetCents: Long get() = (monthlyBudgetCents / 30).coerceAtLeast(100L)

    fun toConfig(): ScoreConfig = ScoreConfig(
        dailyBudgetCents = dailyBudgetCents,
        calorieTarget = calorieTarget,
        proteinTargetG = proteinTargetG,
        focusTargetMin = focusTargetMin,
        sportTargetMin = sportTargetMin,
        sleepTargetMin = sleepTargetMin,
        bedTargetMin = bedTargetMin,
        weightTime = weightTime,
        weightSport = weightSport,
        weightMoney = weightMoney,
        weightDiet = weightDiet,
        weightExec = weightExec,
        weightState = weightState,
        strictness = strictness,
    )
}

private object Keys {
    val monthlyBudget = longPreferencesKey("monthly_budget_cents")
    val calorieTarget = intPreferencesKey("calorie_target")
    val proteinTarget = intPreferencesKey("protein_target")
    val weightKg = intPreferencesKey("weight_kg")
    val focusTarget = intPreferencesKey("focus_target_min")
    val sportTarget = intPreferencesKey("sport_target_min")
    val sleepTarget = intPreferencesKey("sleep_target_min")
    val bedTarget = intPreferencesKey("bed_target_min")
    val weightTime = floatPreferencesKey("weight_time")
    val weightSport = floatPreferencesKey("weight_sport")
    val weightMoney = floatPreferencesKey("weight_money")
    val weightDiet = floatPreferencesKey("weight_diet")
    val weightExec = floatPreferencesKey("weight_exec")
    val weightState = floatPreferencesKey("weight_state")
    val strictness = floatPreferencesKey("strictness")
    val showScore = booleanPreferencesKey("show_score")
    val remindersEnabled = booleanPreferencesKey("reminders_enabled")
    val reviewReminder = booleanPreferencesKey("review_reminder")
}

class SettingsRepository(private val context: Context) {

    val flow: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val d = AppSettings()
            AppSettings(
                monthlyBudgetCents = prefs[Keys.monthlyBudget] ?: d.monthlyBudgetCents,
                calorieTarget = prefs[Keys.calorieTarget] ?: d.calorieTarget,
                proteinTargetG = prefs[Keys.proteinTarget] ?: d.proteinTargetG,
                weightKg = prefs[Keys.weightKg] ?: d.weightKg,
                focusTargetMin = prefs[Keys.focusTarget] ?: d.focusTargetMin,
                sportTargetMin = prefs[Keys.sportTarget] ?: d.sportTargetMin,
                sleepTargetMin = prefs[Keys.sleepTarget] ?: d.sleepTargetMin,
                bedTargetMin = prefs[Keys.bedTarget] ?: d.bedTargetMin,
                weightTime = prefs[Keys.weightTime] ?: d.weightTime,
                weightSport = prefs[Keys.weightSport] ?: d.weightSport,
                weightMoney = prefs[Keys.weightMoney] ?: d.weightMoney,
                weightDiet = prefs[Keys.weightDiet] ?: d.weightDiet,
                weightExec = prefs[Keys.weightExec] ?: d.weightExec,
                weightState = prefs[Keys.weightState] ?: d.weightState,
                strictness = prefs[Keys.strictness] ?: d.strictness,
                showScore = prefs[Keys.showScore] ?: d.showScore,
                remindersEnabled = prefs[Keys.remindersEnabled] ?: d.remindersEnabled,
                reviewReminderEnabled = prefs[Keys.reviewReminder] ?: d.reviewReminderEnabled,
            )
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val d = AppSettings()
            val current = AppSettings(
                monthlyBudgetCents = prefs[Keys.monthlyBudget] ?: d.monthlyBudgetCents,
                calorieTarget = prefs[Keys.calorieTarget] ?: d.calorieTarget,
                proteinTargetG = prefs[Keys.proteinTarget] ?: d.proteinTargetG,
                weightKg = prefs[Keys.weightKg] ?: d.weightKg,
                focusTargetMin = prefs[Keys.focusTarget] ?: d.focusTargetMin,
                sportTargetMin = prefs[Keys.sportTarget] ?: d.sportTargetMin,
                sleepTargetMin = prefs[Keys.sleepTarget] ?: d.sleepTargetMin,
                bedTargetMin = prefs[Keys.bedTarget] ?: d.bedTargetMin,
                weightTime = prefs[Keys.weightTime] ?: d.weightTime,
                weightSport = prefs[Keys.weightSport] ?: d.weightSport,
                weightMoney = prefs[Keys.weightMoney] ?: d.weightMoney,
                weightDiet = prefs[Keys.weightDiet] ?: d.weightDiet,
                weightExec = prefs[Keys.weightExec] ?: d.weightExec,
                weightState = prefs[Keys.weightState] ?: d.weightState,
                strictness = prefs[Keys.strictness] ?: d.strictness,
                showScore = prefs[Keys.showScore] ?: d.showScore,
                remindersEnabled = prefs[Keys.remindersEnabled] ?: d.remindersEnabled,
                reviewReminderEnabled = prefs[Keys.reviewReminder] ?: d.reviewReminderEnabled,
            )
            val s = transform(current)
            prefs[Keys.monthlyBudget] = s.monthlyBudgetCents
            prefs[Keys.calorieTarget] = s.calorieTarget
            prefs[Keys.proteinTarget] = s.proteinTargetG
            prefs[Keys.weightKg] = s.weightKg
            prefs[Keys.focusTarget] = s.focusTargetMin
            prefs[Keys.sportTarget] = s.sportTargetMin
            prefs[Keys.sleepTarget] = s.sleepTargetMin
            prefs[Keys.bedTarget] = s.bedTargetMin
            prefs[Keys.weightTime] = s.weightTime
            prefs[Keys.weightSport] = s.weightSport
            prefs[Keys.weightMoney] = s.weightMoney
            prefs[Keys.weightDiet] = s.weightDiet
            prefs[Keys.weightExec] = s.weightExec
            prefs[Keys.weightState] = s.weightState
            prefs[Keys.strictness] = s.strictness
            prefs[Keys.showScore] = s.showScore
            prefs[Keys.remindersEnabled] = s.remindersEnabled
            prefs[Keys.reviewReminder] = s.reviewReminderEnabled
        }
    }
}
