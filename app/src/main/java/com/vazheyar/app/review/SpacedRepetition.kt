package com.vazheyar.app.review

import com.vazheyar.app.data.FlashcardEntity
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReviewGrade { AGAIN, GOOD }

object SpacedRepetition {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MINUTE_MS = 60L * 1000L

    fun review(card: FlashcardEntity, grade: ReviewGrade, now: Long): FlashcardEntity {
        return when (grade) {
            ReviewGrade.AGAIN -> {
                val delayMinutes = if (card.lapseCount >= 2) 30L else 10L
                card.copy(
                    repetition = 0,
                    intervalDays = 0,
                    easeFactor = max(1.3, card.easeFactor - 0.18),
                    lapseCount = card.lapseCount + 1,
                    nextReviewAt = now + delayMinutes * MINUTE_MS,
                    lastReviewedAt = now
                )
            }
            ReviewGrade.GOOD -> {
                val nextRepetition = card.repetition + 1
                val nextInterval = when (nextRepetition) {
                    1 -> 1
                    2 -> 3
                    3 -> 7
                    4 -> 14
                    5 -> 30
                    else -> (card.intervalDays.coerceAtLeast(30) * card.easeFactor)
                        .roundToInt()
                        .coerceIn(31, 365)
                }
                card.copy(
                    repetition = nextRepetition,
                    intervalDays = nextInterval,
                    easeFactor = (card.easeFactor + 0.04).coerceAtMost(2.8),
                    nextReviewAt = now + nextInterval * DAY_MS,
                    lastReviewedAt = now
                )
            }
        }
    }
}
