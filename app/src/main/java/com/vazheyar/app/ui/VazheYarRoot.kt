package com.vazheyar.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vazheyar.app.MainViewModel
import com.vazheyar.app.data.EnrichmentStatus
import com.vazheyar.app.data.FlashcardEntity
import com.vazheyar.app.review.ReviewGrade
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tab(val title: String) {
    HOME("Home"),
    REVIEW("Review"),
    ADD("Add"),
    LIBRARY("Words"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VazheYarRoot(vm: MainViewModel) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val snackbars = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateCompat()

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            vm.clearMessage()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("flashcard", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        val icon = when (item) {
                            Tab.HOME -> Icons.Default.Home
                            Tab.REVIEW -> Icons.Default.School
                            Tab.ADD -> Icons.Default.Add
                            Tab.LIBRARY -> Icons.Default.LibraryBooks
                            Tab.SETTINGS -> Icons.Default.Settings
                        }

                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                tab = item
                                if (item == Tab.REVIEW) vm.refreshDue()
                            },
                            icon = { Icon(icon, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        vm,
                        onReview = {
                            tab = Tab.REVIEW
                            vm.refreshDue()
                        }
                    )

                    Tab.REVIEW -> ReviewScreen(vm)
                    Tab.ADD -> AddScreen(vm)
                    Tab.LIBRARY -> LibraryScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    onReview: () -> Unit
) {
    val total by vm.totalCount.collectAsStateCompat()
    val learned by vm.learnedCount.collectAsStateCompat()
    val pending by vm.pendingCount.collectAsStateCompat()
    val dueCount by vm.dueCount.collectAsStateCompat()
    val apiKeyConfigured by vm.apiKeyConfigured.collectAsStateCompat()

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Today's review",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("Hard words return sooner. Learned words are reviewed at longer intervals.")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Total words", total.toString(), Modifier.weight(1f))
            StatCard("Learned", learned.toString(), Modifier.weight(1f))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Due now", dueCount.toString(), Modifier.weight(1f))
            StatCard("Processing", pending.toString(), Modifier.weight(1f))
        }

        Button(
            onClick = onReview,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Default.School, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (dueCount == 0) {
                    "Check reviews"
                } else {
                    "Start review ($dueCount)"
                }
            )
        }

        if (pending > 0 && apiKeyConfigured) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemini is creating flashcards", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Common Persian meanings, IPA, and one English example will be added automatically.")
                }
            }
        } else if (pending > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Flashcards are waiting for a Gemini API key", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Open Settings and save your Google Gemini API key to complete pending cards.")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReviewScreen(vm: MainViewModel) {
    val cards by vm.dueCards.collectAsStateCompat()
    val speaker = rememberTtsSpeaker()
    val card = cards.firstOrNull()

    if (card == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Review complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "The next card will return when it is due.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = vm::refreshDue) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check again")
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("1 of ${cards.size}", style = MaterialTheme.typography.labelLarge)
        Text("Tap to flip  •  Swipe left: Again  •  Swipe right: Known")

        SwipeFlashcard(
            card = card,
            onSpeak = { speaker.speak(card.word) },
            onAgain = { vm.review(card, ReviewGrade.AGAIN) },
            onGood = { vm.review(card, ReviewGrade.GOOD) }
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { vm.review(card, ReviewGrade.AGAIN) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Again")
            }

            Button(
                onClick = { vm.review(card, ReviewGrade.GOOD) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Known")
            }
        }
    }
}

@Composable
private fun SwipeFlashcard(
    card: FlashcardEntity,
    onSpeak: () -> Unit,
    onAgain: () -> Unit,
    onGood: () -> Unit
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    var offsetX by remember(card.id) { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 110.dp.toPx() }
    val rotation by animateFloatAsState(
        targetValue = (offsetX / 35f).coerceIn(-8f, 8f),
        label = "card-rotation"
    )

    val feedbackAlpha = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
    val feedback = if (offsetX >= 0) "Known" else "Again"

    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            feedback,
            modifier = Modifier.alpha(feedbackAlpha),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .rotate(rotation)
                .pointerInput(card.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX > thresholdPx -> onGood()
                                offsetX < -thresholdPx -> onAgain()
                                else -> offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    )
                }
                .clickable { flipped = !flipped },
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            AnimatedContent(
                targetState = flipped,
                label = "flip-content"
            ) { isBack ->
                if (isBack) {
                    CardBack(card)
                } else {
                    CardFront(card, onSpeak)
                }
            }
        }
    }
}

@Composable
private fun CardFront(
    card: FlashcardEntity,
    onSpeak: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            card.word,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Text(
            card.ipa,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        FilledTonalButton(onClick = onSpeak) {
            Icon(Icons.Default.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pronounce")
        }
    }
}

@Composable
private fun CardBack(card: FlashcardEntity) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            Text(
                card.translationFa,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr
        ) {
            Text(
                card.exampleEn,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddScreen(vm: MainViewModel) {
    var word by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(vm::importCsv)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            "Add a word",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("Enter only the English word. Gemini will add common Persian meanings, IPA, and one English example.")

        OutlinedTextField(
            value = word,
            onValueChange = { word = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("English word") },
            placeholder = { Text("e.g. remarkable") }
        )

        Button(
            onClick = {
                vm.addWord(word)
                word = ""
            },
            enabled = word.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add flashcard")
        }

        HorizontalDivider()

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Import CSV", fontWeight = FontWeight.Bold)
                Text("The file can contain a single \"word\" column. Without a header, the first column is used.")

                OutlinedButton(
                    onClick = {
                        launcher.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/octet-stream"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose CSV file")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val configured by vm.apiKeyConfigured.collectAsStateCompat()
    var apiKey by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Gemini settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("Your API key is stored only on this device and is used to create your flashcards.")

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (configured) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null)

                Column(Modifier.weight(1f)) {
                    Text(
                        if (configured) {
                            "Gemini API key saved"
                        } else {
                            "Gemini API key not configured"
                        },
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        if (configured) {
                            "New flashcards will be completed directly with Gemini."
                        } else {
                            "Enter an API key from Google AI Studio to enable automatic flashcards."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null)
            },
            label = {
                Text(
                    if (configured) {
                        "New API key"
                    } else {
                        "Google Gemini API key"
                    }
                )
            },
            placeholder = { Text("Paste API key") }
        )

        Button(
            onClick = {
                vm.saveGeminiApiKey(apiKey)
                apiKey = ""
            },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (configured) "Replace API key" else "Save API key")
        }

        if (configured) {
            OutlinedButton(
                onClick = vm::clearGeminiApiKey,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove API key from device")
            }
        }

        HorizontalDivider()

        Text("Privacy & security", fontWeight = FontWeight.Bold)
        Text(
            "The key is encrypted with Android Keystore, is never bundled into the APK or GitHub repository, and is removed when the app is uninstalled. Flashcard requests are sent directly from this device to Google Gemini."
        )
    }
}

@Composable
private fun LibraryScreen(vm: MainViewModel) {
    val cards by vm.allCards.collectAsStateCompat()
    var pendingDelete by remember { mutableStateOf<FlashcardEntity?>(null) }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete word") },
            text = { Text("Delete \"${pendingDelete?.word}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete?.let(vm::delete)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "All words",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        items(cards, key = { it.id }) { card ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.word,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        when (card.enrichmentStatus) {
                            EnrichmentStatus.READY.name -> {
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides LayoutDirection.Rtl
                                ) {
                                    Text(card.translationFa)
                                }
                            }

                            EnrichmentStatus.PENDING.name -> {
                                Text(
                                    "Creating flashcard...",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            else -> {
                                Text(
                                    card.enrichmentError ?: "Flashcard creation failed.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (card.enrichmentStatus == EnrichmentStatus.FAILED.name) {
                        IconButton(
                            onClick = { vm.retryFailed(card) }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry"
                            )
                        }
                    }

                    IconButton(
                        onClick = { pendingDelete = card }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = this.collectAsState()
