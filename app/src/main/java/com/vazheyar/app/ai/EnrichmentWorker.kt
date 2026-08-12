package com.vazheyar.app.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vazheyar.app.data.AppDatabase
import com.vazheyar.app.data.EnrichmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EnrichmentWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(applicationContext).flashcards()
        val pending = dao.pending(limit = 20)

        if (pending.isEmpty()) return@withContext Result.success()
        if (!GeminiApiKeyStore.hasKey(applicationContext)) return@withContext Result.success()

        try {
            val enriched = AiEnrichmentClient.enrich(
                applicationContext,
                pending.map { it.word }
            )
            val byWord = enriched.associateBy { it.word.trim().lowercase() }

            pending.forEach { card ->
                val item = byWord[card.normalizedWord]
                if (item != null) {
                    dao.update(
                        card.copy(
                            ipa = item.ipa,
                            translationFa = item.meaningsFa.joinToString("\n") { "• $it" },
                            exampleEn = item.exampleEn,
                            exampleFa = "",
                            enrichmentStatus = EnrichmentStatus.READY.name,
                            enrichmentError = null,
                            nextReviewAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.update(
                        card.copy(
                            enrichmentStatus = EnrichmentStatus.FAILED.name,
                            enrichmentError = "No Gemini result was returned for this word."
                        )
                    )
                }
            }

            if (dao.pending(limit = 1).isNotEmpty()) {
                EnrichmentScheduler.enqueue(applicationContext)
            }

            Result.success()
        } catch (e: GeminiApiException) {
            val retryable = e.statusCode == 408 ||
                e.statusCode == 409 ||
                e.statusCode == 429 ||
                e.statusCode >= 500

            if (retryable && runAttemptCount < 2) {
                Result.retry()
            } else {
                markFailed(pending, e.message ?: "Gemini request failed.")
                Result.failure()
            }
        } catch (t: Throwable) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                markFailed(
                    pending,
                    t.message?.take(500) ?: "Unexpected enrichment error."
                )
                Result.failure()
            }
        }
    }

    private suspend fun markFailed(cards: List<com.vazheyar.app.data.FlashcardEntity>, message: String) {
        val dao = AppDatabase.get(applicationContext).flashcards()
        cards.forEach { card ->
            dao.update(
                card.copy(
                    enrichmentStatus = EnrichmentStatus.FAILED.name,
                    enrichmentError = message.take(500)
                )
            )
        }
    }
}

object EnrichmentScheduler {
    private const val UNIQUE_WORK = "flashcard-enrichment"

    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<EnrichmentWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
