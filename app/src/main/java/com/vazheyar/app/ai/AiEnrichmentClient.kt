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
    private const val MODEL = "gemini-3.5-flash"
    private val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun enrich(context: Context, words: List<String>): List<EnrichedCard> {
        if (words.isEmpty()) return emptyList()

        val apiKey = GeminiApiKeyStore.load(context)
            ?: error("کلید Google Gemini تنظیم نشده است. آن را در بخش تنظیمات ذخیره کنید.")

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        val body = buildRequest(words).toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
        }.orEmpty()

        if (code !in 200..299) {
            val message = runCatching {
                JSONObject(responseText)
                    .optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: responseText.take(500)
            error("Gemini API HTTP $code: $message")
        }

        val responseRoot = JSONObject(responseText)
        val candidates = responseRoot.optJSONArray("candidates")
            ?: error("Gemini returned no candidates")
        if (candidates.length() == 0) error("Gemini returned an empty candidate list")

        val parts = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")

        var jsonText: String? = null
        for (i in 0 until parts.length()) {
            val text = parts.getJSONObject(i).optString("text")
            if (text.isNotBlank()) {
                jsonText = text
                break
            }
        }

        val parsed = JSONObject(jsonText ?: error("Gemini returned no text output"))
        val cards = parsed.getJSONArray("cards")
        if (cards.length() != words.size) {
            error("Gemini returned ${cards.length()} cards for ${words.size} words")
        }

        return buildList {
            for (i in 0 until cards.length()) {
                val item = cards.getJSONObject(i)
                add(
                    EnrichedCard(
                        word = item.getString("word"),
                        ipa = item.getString("ipa"),
                        translationFa = item.getString("translationFa"),
                        exampleEn = item.getString("exampleEn"),
                        exampleFa = item.getString("exampleFa")
                    )
                )
            }
        }
    }

    private fun buildRequest(words: List<String>): JSONObject {
        val cardSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("word", JSONObject().put("type", "string"))
                    .put("ipa", JSONObject().put("type", "string"))
                    .put("translationFa", JSONObject().put("type", "string"))
                    .put("exampleEn", JSONObject().put("type", "string"))
                    .put("exampleFa", JSONObject().put("type", "string"))
            )
            .put(
                "required",
                JSONArray(listOf("word", "ipa", "translationFa", "exampleEn", "exampleFa"))
            )

        val schema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "cards",
                    JSONObject()
                        .put("type", "array")
                        .put("items", cardSchema)
                )
            )
            .put("required", JSONArray(listOf("cards")))

        val prompt = """
            You create high-quality English-to-Persian vocabulary flashcards for Persian-speaking learners.
            Return exactly one card for every input word and keep exactly the same order.

            Rules:
            - word: preserve the exact input spelling except surrounding whitespace.
            - ipa: standard General American IPA wrapped in /slashes/.
            - translationFa: concise, natural Persian meaning; include at most two common senses separated by «؛» when useful.
            - exampleEn: one short, natural English sentence that clearly demonstrates the target word.
            - exampleFa: fluent Persian translation of exampleEn.
            - Do not omit words, add words, or add explanations.

            Input words: ${JSONArray(words)}
        """.trimIndent()

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject().put(
                    "responseFormat",
                    JSONObject().put(
                        "text",
                        JSONObject()
                            .put("mimeType", "application/json")
                            .put("schema", schema)
                    )
                )
            )
    }
}
