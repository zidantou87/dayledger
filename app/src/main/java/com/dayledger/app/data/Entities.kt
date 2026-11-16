package com.dayledger.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

const val DIR_EXPENSE = 0
const val DIR_INCOME = 1

const val SRC_MANUAL = 0
const val SRC_TIMER = 1
const val SRC_EVENT = 2

const val EVENT_PLANNED = 0
const val EVENT_DONE = 1
const val EVENT_SKIPPED = 2

const val MEAL_BREAKFAST = 0
const val MEAL_LUNCH = 1
const val MEAL_DINNER = 2
const val MEAL_SNACK = 3

const val PLACE_HOME = 0
const val PLACE_TAKEOUT = 1
const val PLACE_DINEOUT = 2

@Serializable
@Entity(tableName = "categories", indices = [Index("kind")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: Int,
    val color: Int,
    val icon: String = "other",
    val essential: Boolean = false,
    val monthlyBudgetCents: Long = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "transactions", indices = [Index("localDate"), Index("categoryId")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAt: Long,
    val localDate: String,
    val amountCents: Long,
    val direction: Int,
    val categoryId: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "activity_categories")
data class ActivityCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val productive: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "time_blocks", indices = [Index("localDate"), Index("startAt")])
data class TimeBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,
    val endAt: Long?,
    val activityId: Long,
    val note: String = "",
    val source: Int = SRC_MANUAL,
    val eventId: Long? = null,
    val localDate: String,
)

@Serializable
@Entity(tableName = "events", indices = [Index("localDate")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val top3: Boolean = false,
    val status: Int = EVENT_PLANNED,
    val skipReason: String = "",
    val note: String = "",
    val localDate: String,
    val activityId: Long? = null,
)

@Serializable
@Entity(tableName = "meals", indices = [Index("localDate")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAt: Long,
    val localDate: String,
    val type: Int,
    val place: Int = PLACE_HOME,
    val note: String = "",
    val kcal: Int? = null,
    val proteinG: Int? = null,
    val vegG: Int? = null,
    /** 健康标签位掩码：蔬菜/水果/蛋白/主食/油炸/甜食/含糖饮料/酒 */
    val tags: Int = 0,
)

object MealTag {
    const val VEG = 1
    const val FRUIT = 2
    const val PROTEIN = 4
    const val STAPLE = 8
    const val FRIED = 16
    const val SWEET = 32
    const val SUGARY = 64
    const val ALCOHOL = 128
}

@Serializable
@Entity(tableName = "day_reviews")
data class DayReviewEntity(
    @PrimaryKey val localDate: String,
    val mood: Int? = null,
    val energy: Int? = null,
    val highlight: String = "",
    val note: String = "",
    val waterMl: Int = 0,
    val bedMinute: Int? = null,
    val wakeMinute: Int? = null,
    val noSpend: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
