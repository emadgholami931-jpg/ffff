package com.vazheyar.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    indices = [Index(value = ["normalizedWord"], unique = true)]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val normalizedWord: String,
    val ipa: String = "",
    val translationFa: String = "",
    val exampleEn: String = "",
    val exampleFa: String = "",
    val enrichmentStatus: String = EnrichmentStatus.PENDING.name,
    val enrichmentError: String? = null,
    val repetition: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.35,
    val lapseCount: Int = 0,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class EnrichmentStatus { PENDING, READY, FAILED }
