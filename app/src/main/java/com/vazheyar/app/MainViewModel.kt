package com.vazheyar.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vazheyar.app.ai.EnrichmentScheduler
import com.vazheyar.app.ai.GeminiApiKeyStore
import com.vazheyar.app.data.CsvWordParser
import com.vazheyar.app.data.FlashcardDao
import com.vazheyar.app.data.FlashcardEntity
import com.vazheyar.app.review.ReviewGrade
import com.vazheyar.app.review.SpacedRepetition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: FlashcardDao = (application as VazheYarApp).database.flashcards()

    val allCards = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalCount = dao.totalCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val learnedCount = dao.learnedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val pendingCount = dao.pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _dueCards = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    val dueCards: StateFlow<List<FlashcardEntity>> = _dueCards

    private val _dueCount = MutableStateFlow(0)
    val dueCount: StateFlow<Int> = _dueCount

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _apiKeyConfigured = MutableStateFlow(GeminiApiKeyStore.hasKey(application))
    val apiKeyConfigured: StateFlow<Boolean> = _apiKeyConfigured

    init {
        viewModelScope.launch {
            dao.all().collect { cards ->
                val now = System.currentTimeMillis()
                val due = cards.asSequence()
                    .filter { it.enrichmentStatus == "READY" && it.nextReviewAt <= now }
                    .sortedWith(compareBy<FlashcardEntity> { it.nextReviewAt }.thenByDescending { it.lapseCount }.thenBy { it.createdAt })
                    .toList()
                _dueCount.value = due.size
                _dueCards.value = due.take(50)
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun refreshDue() {
        viewModelScope.launch {
            val due = dao.due(System.currentTimeMillis(), limit = 10_000)
            _dueCount.value = due.size
            _dueCards.value = due.take(50)
        }
    }

    fun saveGeminiApiKey(raw: String) {
        val key = raw.trim()
        if (key.isBlank()) {
            _message.value = "کلید API نمی‌تواند خالی باشد."
            return
        }

        runCatching {
            GeminiApiKeyStore.save(getApplication(), key)
        }.onSuccess {
            _apiKeyConfigured.value = true
            _message.value = "کلید Google Gemini روی این دستگاه ذخیره شد."
            viewModelScope.launch {
                dao.retryAllFailed()
                EnrichmentScheduler.enqueue(getApplication())
            }
        }.onFailure {
            _message.value = "ذخیره کلید ناموفق بود: ${it.message ?: "خطای نامشخص"}"
        }
    }

    fun clearGeminiApiKey() {
        GeminiApiKeyStore.clear(getApplication())
        _apiKeyConfigured.value = false
        _message.value = "کلید Google Gemini از این دستگاه حذف شد."
    }

    fun addWord(raw: String) {
        val word = raw.trim()
        if (word.isBlank()) return
        viewModelScope.launch {
            val inserted = dao.insert(newCard(word))
            if (inserted > 0) {
                if (GeminiApiKeyStore.hasKey(getApplication())) {
                    _message.value = "«$word» اضافه شد و در حال تکمیل با Gemini است."
                    EnrichmentScheduler.enqueue(getApplication())
                } else {
                    _message.value = "«$word» اضافه شد. برای ساخت خودکار کارت، کلید Gemini را در تنظیمات ذخیره کنید."
                }
            } else {
                _message.value = "این کلمه قبلاً در مجموعه وجود دارد."
            }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            val words = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input, Charsets.UTF_8).use { CsvWordParser.parse(it) }
                }.orEmpty()
            }

            if (words.isEmpty()) {
                _message.value = "کلمه‌ای در فایل CSV پیدا نشد."
                return@launch
            }

            val ids = dao.insertAll(words.map(::newCard))
            val added = ids.count { it > 0 }
            if (added > 0 && GeminiApiKeyStore.hasKey(getApplication())) {
                _message.value = "$added کلمه جدید از CSV اضافه شد و در حال تکمیل با Gemini است."
                EnrichmentScheduler.enqueue(getApplication())
            } else if (added > 0) {
                _message.value = "$added کلمه اضافه شد. برای تکمیل خودکار، کلید Gemini را در تنظیمات ذخیره کنید."
            } else {
                _message.value = "کلمه جدیدی برای اضافه‌کردن پیدا نشد."
            }
        }
    }

    fun review(card: FlashcardEntity, grade: ReviewGrade) {
        viewModelScope.launch {
            dao.update(SpacedRepetition.review(card, grade, System.currentTimeMillis()))
            refreshDue()
        }
    }

    fun retryFailed(card: FlashcardEntity) {
        viewModelScope.launch {
            dao.update(card.copy(enrichmentStatus = "PENDING", enrichmentError = null))
            if (GeminiApiKeyStore.hasKey(getApplication())) {
                EnrichmentScheduler.enqueue(getApplication())
            } else {
                _message.value = "ابتدا کلید Gemini را در تنظیمات ذخیره کنید."
            }
        }
    }

    fun delete(card: FlashcardEntity) {
        viewModelScope.launch {
            dao.deleteById(card.id)
            refreshDue()
        }
    }

    private fun newCard(word: String): FlashcardEntity = FlashcardEntity(
        word = word,
        normalizedWord = word.trim().lowercase()
    )
}
