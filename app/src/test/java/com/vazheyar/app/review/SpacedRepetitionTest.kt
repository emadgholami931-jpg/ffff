package com.vazheyar.app.review

import com.vazheyar.app.data.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionTest {
    private val base = FlashcardEntity(word = "test", normalizedWord = "test")

    @Test
    fun goodReviewExpandsIntervals() {
        val now = 1_000_000L
        val first = SpacedRepetition.review(base, ReviewGrade.GOOD, now)
        assertEquals(1, first.intervalDays)
        val second = SpacedRepetition.review(first, ReviewGrade.GOOD, first.nextReviewAt)
        assertEquals(3, second.intervalDays)
    }

    @Test
    fun againReturnsSoonAndIncreasesLapses() {
        val now = 1_000_000L
        val result = SpacedRepetition.review(base.copy(repetition = 4, intervalDays = 14), ReviewGrade.AGAIN, now)
        assertEquals(0, result.repetition)
        assertEquals(1, result.lapseCount)
        assertTrue(result.nextReviewAt - now <= 10 * 60 * 1000L)
    }
}
