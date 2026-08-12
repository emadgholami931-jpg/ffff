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
                    .sortedWith(
                        compareBy<FlashcardEntity> { it.nextReviewAt }
                            .thenByDescending { it.lapseCount }
                            .thenBy { it.createdAt }
                    )
                    .toList()

                _dueCount.value = due.size
                _dueCards.value = due.take(50)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

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
            _message.value = "API key cannot be empty."
            return
        }

        runCatching {
            GeminiApiKeyStore.save(getApplication(), key)
        }.onSuccess {
            _apiKeyConfigured.value = true
            _message.value = "Google Gemini API key was saved securely on this device."
            viewModelScope.launch {
                dao.retryAllFailed()
                EnrichmentScheduler.enqueue(getApplication())
            }
        }.onFailure {
            _message.value = "Could not save the API key: ${it.message ?: "Unknown error"}"
        }
    }

    fun clearGeminiApiKey() {
        GeminiApiKeyStore.clear(getApplication())
        _apiKeyConfigured.value = false
        _message.value = "Google Gemini API key was removed from this device."
    }

    fun addWord(raw: String) {
        val word = raw.trim()
        if (word.isBlank()) return

        viewModelScope.launch {
            val inserted = dao.insert(newCard(word))

            if (inserted > 0) {
                if (GeminiApiKeyStore.hasKey(getApplication())) {
                    _message.value = "\"$word\" was added. Gemini is creating the flashcard."
                    EnrichmentScheduler.enqueue(getApplication())
                } else {
                    _message.value = "\"$word\" was added. Add your Gemini API key in Settings to complete it."
                }
            } else {
                _message.value = "This word is already in your library."
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
                _message.value = "No words were found in the CSV file."
                return@launch
            }

            val ids = dao.insertAll(words.map(::newCard))
            val added = ids.count { it > 0 }

            if (added > 0 && GeminiApiKeyStore.hasKey(getApplication())) {
                _message.value = "$added new words were imported. Gemini is creating the flashcards."
                EnrichmentScheduler.enqueue(getApplication())
            } else if (added > 0) {
                _message.value = "$added words were imported. Add your Gemini API key in Settings to complete them."
            } else {
                _message.value = "No new words were found to add."
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
            dao.update(
                card.copy(
                    enrichmentStatus = "PENDING",
                    enrichmentError = null
                )
            )

            if (GeminiApiKeyStore.hasKey(getApplication())) {
                EnrichmentScheduler.enqueue(getApplication())
            } else {
                _message.value = "Add your Gemini API key in Settings first."
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
