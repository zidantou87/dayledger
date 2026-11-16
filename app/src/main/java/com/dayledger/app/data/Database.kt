package com.dayledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meals ADD COLUMN tags INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): DayLedgerDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DayLedgerDb::class.java,
                    "dayledger.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
