package com.dayledger.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE localDate = :date ORDER BY occurredAt DESC")
    fun observe(date: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE localDate = :date ORDER BY occurredAt DESC")
    suspend fun list(date: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE localDate BETWEEN :from AND :to ORDER BY occurredAt DESC")
    suspend fun between(from: String, to: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun all(): List<TransactionEntity>

    @Insert
    suspend fun insert(item: TransactionEntity): Long

    @Update
    suspend fun update(item: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY kind, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Update
    suspend fun update(item: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ActivityCategoryDao {
    @Query("SELECT * FROM activity_categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ActivityCategoryEntity>>

    @Query("SELECT * FROM activity_categories ORDER BY sortOrder, id")
    suspend fun all(): List<ActivityCategoryEntity>

    @Insert
    suspend fun insertAll(items: List<ActivityCategoryEntity>)

    @Update
    suspend fun update(item: ActivityCategoryEntity)

    @Query("UPDATE activity_categories SET productive = :productive WHERE id = :id")
    suspend fun setProductive(id: Long, productive: Boolean)

    @Query("DELETE FROM activity_categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TimeBlockDao {
    @Query("SELECT * FROM time_blocks WHERE localDate = :date ORDER BY startAt ASC")
    fun observe(date: String): Flow<List<TimeBlockEntity>>

    @Query("SELECT * FROM time_blocks WHERE localDate = :date ORDER BY startAt ASC")
    suspend fun list(date: String): List<TimeBlockEntity>

    @Query("SELECT * FROM time_blocks WHERE localDate BETWEEN :from AND :to ORDER BY startAt ASC")
    suspend fun between(from: String, to: String): List<TimeBlockEntity>

    @Query("SELECT * FROM time_blocks WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeRunning(): Flow<TimeBlockEntity?>

    @Query("SELECT * FROM time_blocks WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun running(): TimeBlockEntity?

    @Query("SELECT * FROM time_blocks ORDER BY startAt ASC")
    suspend fun all(): List<TimeBlockEntity>

    @Insert
    suspend fun insert(item: TimeBlockEntity): Long

    @Update
    suspend fun update(item: TimeBlockEntity)

    @Query("UPDATE time_blocks SET endAt = :endAt WHERE id = :id")
    suspend fun stop(id: Long, endAt: Long)

    @Query("DELETE FROM time_blocks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM time_blocks WHERE id = :id")
    suspend fun byId(id: Long): TimeBlockEntity?

    @Query("DELETE FROM time_blocks")
    suspend fun clearAll()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE localDate = :date ORDER BY startAt ASC")
    fun observe(date: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE localDate = :date ORDER BY startAt ASC")
    suspend fun list(date: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE localDate BETWEEN :from AND :to ORDER BY startAt ASC")
    suspend fun between(from: String, to: String): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY startAt ASC")
    suspend fun all(): List<EventEntity>

    @Insert
    suspend fun insert(item: EventEntity): Long

    @Update
    suspend fun update(item: EventEntity)

    @Query("UPDATE events SET status = :status, skipReason = :reason WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int, reason: String = "")

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): EventEntity?

    @Query("DELETE FROM events")
    suspend fun clearAll()
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE localDate = :date ORDER BY occurredAt ASC")
    fun observe(date: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE localDate = :date ORDER BY occurredAt ASC")
    suspend fun list(date: String): List<MealEntity>

    @Query("SELECT * FROM meals WHERE localDate BETWEEN :from AND :to ORDER BY occurredAt ASC")
    suspend fun between(from: String, to: String): List<MealEntity>

    @Query("SELECT * FROM meals ORDER BY occurredAt ASC")
    suspend fun all(): List<MealEntity>

    @Insert
    suspend fun insert(item: MealEntity): Long

    @Update
    suspend fun update(item: MealEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun byId(id: Long): MealEntity?

    @Query("DELETE FROM meals")
    suspend fun clearAll()
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM day_reviews WHERE localDate = :date")
    fun observe(date: String): Flow<DayReviewEntity?>

    @Query("SELECT * FROM day_reviews WHERE localDate = :date")
    suspend fun get(date: String): DayReviewEntity?

    @Query("SELECT * FROM day_reviews WHERE localDate BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<DayReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DayReviewEntity)

    @Query("SELECT * FROM day_reviews ORDER BY localDate")
    suspend fun all(): List<DayReviewEntity>

    @Query("DELETE FROM day_reviews")
    suspend fun clearAll()
}
