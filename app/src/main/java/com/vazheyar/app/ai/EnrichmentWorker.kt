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
            val enriched = AiEnrichmentClient.enrich(applicationContext, pending.map { it.word })
            val byWord = enriched.associateBy { it.word.trim().lowercase() }

            pending.forEach { card ->
                val item = byWord[card.normalizedWord]
                if (item != null) {
                    dao.update(
                        card.copy(
                            ipa = item.ipa,
                            translationFa = item.translationFa,
                            exampleEn = item.exampleEn,
                            exampleFa = item.exampleFa,
                            enrichmentStatus = EnrichmentStatus.READY.name,
                            enrichmentError = null,
                            nextReviewAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.update(
                        card.copy(
                            enrichmentStatus = EnrichmentStatus.FAILED.name,
                            enrichmentError = "No AI result returned"
                        )
                    )
                }
            }

            if (dao.pending(limit = 1).isNotEmpty()) {
                EnrichmentScheduler.enqueue(applicationContext)
            }
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount >= 4) {
                pending.forEach { card ->
                    dao.update(
                        card.copy(
                            enrichmentStatus = EnrichmentStatus.FAILED.name,
                            enrichmentError = t.message?.take(300)
                        )
                    )
                }
                Result.failure()
            } else {
                Result.retry()
            }
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
