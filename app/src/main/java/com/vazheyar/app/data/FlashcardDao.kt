package com.vazheyar.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<FlashcardEntity>): List<Long>

    @Update
    suspend fun update(card: FlashcardEntity)

    @Query("SELECT * FROM flashcards WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): FlashcardEntity?

    @Query("SELECT * FROM flashcards WHERE enrichmentStatus = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE enrichmentStatus = 'READY' AND nextReviewAt <= :now ORDER BY nextReviewAt ASC, lapseCount DESC, createdAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int = 50): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun all(): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards")
    fun totalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE enrichmentStatus = 'READY' AND nextReviewAt <= :now")
    fun dueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE repetition > 0")
    fun learnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE enrichmentStatus = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query("UPDATE flashcards SET enrichmentStatus = 'PENDING', enrichmentError = NULL WHERE enrichmentStatus = 'FAILED'")
    suspend fun retryAllFailed(): Int

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteById(id: Long)
}
