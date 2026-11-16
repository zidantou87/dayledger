package com.dayledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        ActivityCategoryEntity::class,
        TimeBlockEntity::class,
        EventEntity::class,
        MealEntity::class,
        DayReviewEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DayLedgerDb : RoomDatabase() {

    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun activities(): ActivityCategoryDao
    abstract fun blocks(): TimeBlockDao
    abstract fun events(): EventDao
    abstract fun meals(): MealDao
    abstract fun reviews(): ReviewDao

    companion object {
        @Volatile
        private var instance: DayLedgerDb? = null

        fun get(context: Context): DayLedgerDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DayLedgerDb::class.java,
                    "dayledger.db",
                ).build().also { instance = it }
            }
    }
}
