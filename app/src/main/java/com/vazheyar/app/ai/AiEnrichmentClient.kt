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
    val meaningsFa: List<String>,
    val exampleEn: String
)

internal class GeminiApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

internal object AiEnrichmentClient {

    private const val MODEL = "gemini-3.6-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1/interactions"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 45_000

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
            ?: error("Google Gemini API key is not configured. Add it in Settings.")

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            val requestBody = buildRequest(cleanWords).toString()
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            if (responseCode !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(responseText)
                        .optJSONObject("error")
                        ?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()

                val friendlyMessage = when (responseCode) {
                    400 -> "Gemini rejected the request as invalid."
                    401, 403 -> "The Gemini API key is invalid or does not have access."
                    404 -> "The selected Gemini model or API endpoint is unavailable."
                    408 -> "The Gemini request timed out."
                    429 -> "Gemini rate limit or quota was reached."
                    in 500..599 -> "Gemini is temporarily unavailable."
                    else -> "Gemini request failed with HTTP $responseCode."
                }

                throw GeminiApiException(
                    statusCode = responseCode,
                    message = buildString {
                        append(friendlyMessage)
                        if (!apiMessage.isNullOrBlank()) {
                            append(" ")
                            append(apiMessage.take(500))
                        }
                    }
                )
            }

            return parseResponse(responseText, cleanWords)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequest(words: List<String>): JSONObject {
        val cardSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("ipa", JSONObject().put("type", "string"))
                    .put(
                        "meaningsFa",
                        JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject().put("type", "string"))
                            .put("minItems", 1)
                            .put("maxItems", 4)
                    )
                    .put("exampleEn", JSONObject().put("type", "string"))
            )
            .put("required", JSONArray(listOf("ipa", "meaningsFa", "exampleEn")))
            .put("additionalProperties", false)

        val responseSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "cards",
                    JSONObject()
                        .put("type", "array")
                        .put("items", cardSchema)
                        .put("minItems", words.size)
                        .put("maxItems", words.size)
                )
            )
            .put("required", JSONArray(listOf("cards")))
            .put("additionalProperties", false)

        val systemInstruction = """
You create accurate English-to-Persian vocabulary flashcards for language learners.
Follow the requested JSON schema exactly. Keep answers concise and practical.
Do not add explanations, markdown, notes, or fields that are not requested.
        """.trimIndent()

        val input = """
Create exactly one flashcard record for each English word below, in the exact same order.

For each word:
- ipa: Give standard General American IPA between /slashes/.
- meaningsFa: Give 1 to 4 genuinely common Persian meanings. Prefer everyday meanings, avoid rare senses, avoid duplicates, and do not invent extra meanings just to reach a number.
- exampleEn: Give one short, natural English example sentence that clearly demonstrates the word. Prefer a practical B1-B2 level sentence and keep it concise.
- Do not translate the example sentence into Persian.

Input words:
${JSONArray(words)}
        """.trimIndent()

        val responseFormat = JSONObject()
            .put("type", "text")
            .put("mime_type", "application/json")
            .put("schema", responseSchema)

        return JSONObject()
            .put("model", MODEL)
            .put("store", false)
            .put("system_instruction", systemInstruction)
            .put("input", input)
            .put(
                "generation_config",
                JSONObject().put("thinking_level", "low")
            )
            .put("response_format", responseFormat)
    }

    private fun parseResponse(
        responseText: String,
        expectedWords: List<String>
    ): List<EnrichedCard> {
        val root = JSONObject(responseText)
        val steps = root.optJSONArray("steps")
            ?: error("Gemini returned no response steps.")

        var outputText: String? = null

        for (i in steps.length() - 1 downTo 0) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("type") != "model_output") continue

            val content = step.optJSONArray("content") ?: continue
            val text = buildString {
                for (j in 0 until content.length()) {
                    val block = content.optJSONObject(j) ?: continue
                    if (block.optString("type") == "text") {
                        append(block.optString("text"))
                    }
                }
            }.trim()

            if (text.isNotBlank()) {
                outputText = text
                break
            }
        }

        val jsonText = outputText
            ?: error("Gemini returned no usable text output.")

        val parsed = JSONObject(jsonText)
        val cards = parsed.optJSONArray("cards")
            ?: error("Gemini response did not contain cards.")

        if (cards.length() != expectedWords.size) {
            error(
                "Gemini returned ${cards.length()} cards for ${expectedWords.size} words."
            )
        }

        return buildList {
            for (i in 0 until cards.length()) {
                val item = cards.getJSONObject(i)
                val meaningsJson = item.optJSONArray("meaningsFa")
                    ?: error("Gemini returned a card without meanings.")

                val meanings = buildList {
                    for (j in 0 until meaningsJson.length()) {
                        val meaning = meaningsJson.optString(j).trim()
                        if (meaning.isNotBlank() && meaning !in this) add(meaning)
                    }
                }.take(4)

                if (meanings.isEmpty()) {
                    error("Gemini returned an empty meanings list.")
                }

                val ipa = item.optString("ipa").trim()
                val exampleEn = item.optString("exampleEn").trim()

                if (ipa.isBlank() || exampleEn.isBlank()) {
                    error("Gemini returned an incomplete flashcard.")
                }

                add(
                    EnrichedCard(
                        word = expectedWords[i],
                        ipa = ipa,
                        meaningsFa = meanings,
                        exampleEn = exampleEn
                    )
                )
            }
        }
    }
}
