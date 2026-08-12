package com.vazheyar.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal data class EnrichedCard(
    val word: String,
    val ipa: String,
    val translationFa: String,
    val exampleEn: String,
    val exampleFa: String
)

internal object AiEnrichmentClient {

    private const val MODEL = "gemini-2.5-flash"

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 45_000

    private val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun enrich(
        context: Context,
        words: List<String>
    ): List<EnrichedCard> {

        if (words.isEmpty()) return emptyList()

        val cleanWords = words
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(20)

        if (cleanWords.isEmpty()) return emptyList()

        val apiKey = GeminiApiKeyStore.load(context)
            ?: error(
                "کلید Google Gemini تنظیم نشده است. " +
                    "آن را در بخش تنظیمات ذخیره کنید."
            )

        val connection =
            (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )

                setRequestProperty(
                    "x-goog-api-key",
                    apiKey
                )
            }

        try {
            val requestBody = buildRequest(cleanWords).toString()

            connection.outputStream.use { output ->
                output.write(
                    requestBody.toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode = connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.let {
                    BufferedReader(
                        InputStreamReader(it, Charsets.UTF_8)
                    ).use { reader ->
                        reader.readText()
                    }
                }.orEmpty()

            if (responseCode !in 200..299) {

                val apiMessage =
                    runCatching {
                        JSONObject(responseText)
                            .optJSONObject("error")
                            ?.optString("message")
                            ?.takeIf { it.isNotBlank() }
                    }.getOrNull()

                val friendlyMessage =
                    when (responseCode) {

                        400 ->
                            "درخواست Gemini نامعتبر بود."

                        401, 403 ->
                            "کلید Gemini معتبر نیست یا اجازه دسترسی ندارد."

                        429 ->
                            "سهمیه رایگان Gemini فعلاً تمام شده یا محدودیت درخواست فعال شده است."

                        else ->
                            "خطا در ارتباط با Gemini."
                    }

                error(
                    "$friendlyMessage\n" +
                        (apiMessage ?: "HTTP $responseCode")
                )
            }

            return parseResponse(
                responseText = responseText,
                expectedWords = cleanWords
            )

        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequest(
        words: List<String>
    ): JSONObject {

        val cardSchema =
            JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "word",
                            JSONObject()
                                .put("type", "string")
                        )
                        .put(
                            "ipa",
                            JSONObject()
                                .put("type", "string")
                        )
                        .put(
                            "translationFa",
                            JSONObject()
                                .put("type", "string")
                        )
                        .put(
                            "exampleEn",
                            JSONObject()
                                .put("type", "string")
                        )
                        .put(
                            "exampleFa",
                            JSONObject()
                                .put("type", "string")
                        )
                )
                .put(
                    "required",
                    JSONArray(
                        listOf(
                            "word",
                            "ipa",
                            "translationFa",
                            "exampleEn",
                            "exampleFa"
                        )
                    )
                )
                .put(
                    "additionalProperties",
                    false
                )

        val responseSchema =
            JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "cards",
                            JSONObject()
                                .put("type", "array")
                                .put("items", cardSchema)
                                .put(
                                    "minItems",
                                    words.size
                                )
                                .put(
                                    "maxItems",
                                    words.size
                                )
                        )
                )
                .put(
                    "required",
                    JSONArray(
                        listOf("cards")
                    )
                )
                .put(
                    "additionalProperties",
                    false
                )

        val prompt = """
You are creating English-to-Persian vocabulary flashcards.

For every input word, create exactly one flashcard.

Important rules:

1. Keep the cards in exactly the same order as the input words.

2. "word":
Return the original English word.

3. "ipa":
Return standard General American English IPA.
Put the IPA between /slashes/.

4. "translationFa":
Return the most useful and natural Persian meaning.
Keep it concise.
If necessary, include at most two common meanings separated by «؛».

5. "exampleEn":
Write one short, natural English example sentence.
The sentence must clearly demonstrate the target word.

6. "exampleFa":
Write a fluent Persian translation of exampleEn.

7. Do not omit any input word.

8. Do not add extra words.

9. Do not add explanations outside the required JSON structure.

Input words:
${JSONArray(words)}
        """.trimIndent()

        val contents =
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            prompt
                        )
                    )
                )
            )

        val generationConfig =
            JSONObject()
                .put(
                    "temperature",
                    0.2
                )
                .put(
                    "candidateCount",
                    1
                )
                .put(
                    "maxOutputTokens",
                    4096
                )
                .put(
                    "responseMimeType",
                    "application/json"
                )
                .put(
                    "responseJsonSchema",
                    responseSchema
                )
                .put(
                    "thinkingConfig",
                    JSONObject()
                        .put(
                            "thinkingBudget",
                            0
                        )
                )

        return JSONObject()
            .put(
                "contents",
                contents
            )
            .put(
                "generationConfig",
                generationConfig
            )
    }

    private fun parseResponse(
        responseText: String,
        expectedWords: List<String>
    ): List<EnrichedCard> {

        val root =
            JSONObject(responseText)

        val candidates =
            root.optJSONArray("candidates")
                ?: error(
                    "Gemini پاسخی برای این درخواست تولید نکرد."
                )

        if (candidates.length() == 0) {
            error(
                "Gemini پاسخ خالی برگرداند."
            )
        }

        val candidate =
            candidates.getJSONObject(0)

        val content =
            candidate.optJSONObject("content")
                ?: error(
                    "پاسخ Gemini فاقد content است."
                )

        val parts =
            content.optJSONArray("parts")
                ?: error(
                    "پاسخ Gemini فاقد parts است."
                )

        var jsonText: String? = null

        for (i in 0 until parts.length()) {

            val part =
                parts.optJSONObject(i)
                    ?: continue

            val text =
                part.optString("text")

            if (text.isNotBlank()) {
                jsonText = text
                break
            }
        }

        val outputText =
            jsonText
                ?: error(
                    "Gemini متن قابل پردازشی برنگرداند."
                )

        val parsed =
            JSONObject(outputText)

        val cards =
            parsed.optJSONArray("cards")
                ?: error(
                    "Gemini لیست cards را برنگرداند."
                )

        if (cards.length() != expectedWords.size) {
            error(
                "Gemini ${cards.length()} کارت برای " +
                    "${expectedWords.size} کلمه تولید کرد."
            )
        }

        return buildList {

            for (i in 0 until cards.length()) {

                val item =
                    cards.getJSONObject(i)

                add(
                    EnrichedCard(
                        word =
                            expectedWords[i],

                        ipa =
                            item.optString("ipa")
                                .trim(),

                        translationFa =
                            item.optString(
                                "translationFa"
                            ).trim(),

                        exampleEn =
                            item.optString(
                                "exampleEn"
                            ).trim(),

                        exampleFa =
                            item.optString(
                                "exampleFa"
                            ).trim()
                    )
                )
            }
        }
    }
}
