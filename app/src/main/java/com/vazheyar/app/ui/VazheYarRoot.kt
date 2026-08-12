package com.vazheyar.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

private enum class Tab(val title: String) { HOME("خانه"), REVIEW("مرور"), ADD("افزودن"), LIBRARY("واژه‌ها"), SETTINGS("تنظیمات") }

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

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                            onClick = { tab = item; if (item == Tab.REVIEW) vm.refreshDue() },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm, onReview = { tab = Tab.REVIEW; vm.refreshDue() })
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
private fun HomeScreen(vm: MainViewModel, onReview: () -> Unit) {
    val total by vm.totalCount.collectAsStateCompat()
    val learned by vm.learnedCount.collectAsStateCompat()
    val pending by vm.pendingCount.collectAsStateCompat()
    val dueCount by vm.dueCount.collectAsStateCompat()
    val apiKeyConfigured by vm.apiKeyConfigured.collectAsStateCompat()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("مرور امروز", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("کلمات سخت زودتر برمی‌گردند و کلمات یادگرفته‌شده با فاصله‌ی بیشتر مرور می‌شوند.")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("کل واژه‌ها", total.toString(), Modifier.weight(1f))
            StatCard("یادگرفته", learned.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("آماده مرور", dueCount.toString(), Modifier.weight(1f))
            StatCard("در حال تکمیل", pending.toString(), Modifier.weight(1f))
        }

        Button(onClick = onReview, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Icon(Icons.Default.School, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (dueCount == 0) "بررسی مرورها" else "شروع مرور $dueCount کارت")
        }

        if (pending > 0 && apiKeyConfigured) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemini در حال تکمیل کارت‌هاست", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("معنی فارسی، IPA و جمله مثال به‌صورت خودکار اضافه می‌شوند.")
                }
            }
        } else if (pending > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("کارت‌ها در انتظار کلید Gemini هستند", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("از بخش تنظیمات، Google Gemini API Key را ذخیره کنید تا کارت‌های در انتظار تکمیل شوند.")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("مرور فعلی تمام شد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("کارت بعدی دقیقاً در زمان مناسب دوباره ظاهر می‌شود.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = vm::refreshDue) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("بررسی دوباره")
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("کارت ${1} از ${cards.size}", style = MaterialTheme.typography.labelLarge)
        Text("← بلد نیستم    |    بلد هستم →", style = MaterialTheme.typography.bodySmall)

        SwipeFlashcard(
            card = card,
            onSpeak = { speaker.speak(card.word) },
            onAgain = { vm.review(card, ReviewGrade.AGAIN) },
            onGood = { vm.review(card, ReviewGrade.GOOD) }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.review(card, ReviewGrade.AGAIN) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Close, null)
                Spacer(Modifier.width(6.dp))
                Text("یاد نگرفتم")
            }
            Button(onClick = { vm.review(card, ReviewGrade.GOOD) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("یاد گرفتم")
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
    val rotation by animateFloatAsState(targetValue = (offsetX / 35f).coerceIn(-8f, 8f), label = "card-rotation")

    val feedbackAlpha = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
    val feedback = if (offsetX >= 0) "یاد گرفتم" else "یاد نگرفتم"

    Box(
        Modifier
            .fillMaxWidth()
            .height(440.dp),
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
            AnimatedContent(targetState = flipped, label = "flip-content") { isBack ->
                if (!isBack) CardFront(card, onSpeak) else CardBack(card)
            }
        }
    }
}

@Composable
private fun CardFront(card: FlashcardEntity, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(card.word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(card.ipa, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(onClick = onSpeak) {
            Icon(Icons.Default.VolumeUp, null)
            Spacer(Modifier.width(8.dp))
            Text("پخش تلفظ")
        }
        Spacer(Modifier.height(28.dp))
        Text("برای دیدن معنی و مثال، روی کارت بزنید", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CardBack(card: FlashcardEntity) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(card.translationFa, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(card.exampleEn, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        if (card.exampleFa.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(card.exampleFa, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Text("برای برگشت به روی کارت، دوباره لمس کنید", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddScreen(vm: MainViewModel) {
    var word by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::importCsv)
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("افزودن واژه", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("فقط کلمه انگلیسی را وارد کنید؛ معنی، تلفظ و مثال خودکار ساخته می‌شود.")

        OutlinedTextField(
            value = word,
            onValueChange = { word = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("کلمه انگلیسی") },
            placeholder = { Text("مثلاً: remarkable") }
        )
        Button(
            onClick = { vm.addWord(word); word = "" },
            enabled = word.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("افزودن و ساخت فلش‌کارت")
        }

        HorizontalDivider()

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ورود گروهی با CSV", fontWeight = FontWeight.Bold)
                Text("فایل می‌تواند فقط یک ستون word داشته باشد. اگر هدر نداشته باشد، ستون اول به‌عنوان کلمه خوانده می‌شود.")
                OutlinedButton(
                    onClick = { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("انتخاب فایل CSV")
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
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("تنظیمات Gemini", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("کلید API هر کاربر فقط روی همان دستگاه ذخیره می‌شود و برای ساخت معنی، تلفظ و مثال استفاده خواهد شد.")

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (configured) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (configured) "کلید Gemini ذخیره شده است" else "کلید Gemini تنظیم نشده است",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (configured) "فلش‌کارت‌های جدید مستقیماً با Gemini تکمیل می‌شوند."
                        else "برای تکمیل خودکار فلش‌کارت‌ها یک API Key از Google AI Studio وارد کنید.",
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
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            label = { Text(if (configured) "کلید جدید برای جایگزینی" else "Google Gemini API Key") },
            placeholder = { Text("API Key را اینجا وارد کنید") }
        )

        Button(
            onClick = { vm.saveGeminiApiKey(apiKey); apiKey = "" },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (configured) "جایگزینی کلید" else "ذخیره کلید")
        }

        if (configured) {
            OutlinedButton(
                onClick = vm::clearGeminiApiKey,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حذف کلید از دستگاه")
            }
        }

        HorizontalDivider()

        Text("حریم و امنیت", fontWeight = FontWeight.Bold)
        Text(
            "کلید با Android Keystore رمزگذاری می‌شود، داخل APK یا GitHub قرار نمی‌گیرد و با حذف برنامه از این دستگاه پاک می‌شود. درخواست‌های ساخت فلش‌کارت مستقیماً از گوشی به Google Gemini ارسال می‌شوند.",
            style = MaterialTheme.typography.bodyMedium
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
            title = { Text("حذف واژه") },
            text = { Text("«${pendingDelete?.word}» حذف شود؟") },
            confirmButton = {
                Button(onClick = { pendingDelete?.let(vm::delete); pendingDelete = null }) { Text("حذف") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingDelete = null }) { Text("انصراف") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("همه واژه‌ها", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        items(cards, key = { it.id }) { card ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(card.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        when (card.enrichmentStatus) {
                            EnrichmentStatus.READY.name -> Text(card.translationFa)
                            EnrichmentStatus.PENDING.name -> Text("در حال تکمیل…", color = MaterialTheme.colorScheme.primary)
                            else -> Text(card.enrichmentError ?: "خطا در تکمیل", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (card.enrichmentStatus == EnrichmentStatus.FAILED.name) {
                        IconButton(onClick = { vm.retryFailed(card) }) { Icon(Icons.Default.Refresh, "تلاش دوباره") }
                    }
                    IconButton(onClick = { pendingDelete = card }) { Icon(Icons.Default.Delete, "حذف") }
                }
            }
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = this.collectAsState()
