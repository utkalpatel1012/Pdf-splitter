package com.example.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.ChapterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * High-Thinking Deep Reasoning with gemini-3.1-pro-preview.
     * Sets thinkingLevel to "HIGH" and does NOT set maxOutputTokens, as mandated by the instructions.
     */
    suspend fun analyzeIndexWithHighThinking(
        rawIndexText: String,
        totalPages: Int,
        docTitle: String
    ): Result<List<ChapterItem>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel.")
            )
        }

        try {
            val systemPrompt = """
                You are a world-class document intelligence and scientific indexing system.
                The user has a PDF document titled "$docTitle" with a total of $totalPages pages.
                Analyze the provided Index / Table of Contents / Outline text.
                CRITICAL REASONING REQUIREMENTS:
                1. Differentiate between Front Matter (Preface, Introduction, TOC itself), core Chapters, and Back Matter (Appendices, Glossary, Bibliography, Index).
                2. Real-world PDFs often have an offset between the printed page numbers in the index and actual PDF page numbers (e.g., Roman numerals in preface cause Chapter 1 starting at printed page 1 to actually be PDF page 12). If there's an obvious offset or page range discrepancy, reason carefully to adjust each chapter's startPage and endPage to match the real PDF page range (from page 1 to $totalPages).
                3. Ensure startPage and endPage form contiguous, sensible boundaries covering the document without exceeding $totalPages.
                4. Output STRICT JSON array format only:
                [
                  {
                    "title": "Chapter 1: Title Here",
                    "startPage": 1,
                    "endPage": 25,
                    "topics": ["Key Topic 1", "Key Topic 2"]
                  }
                ]
            """.trimIndent()

            val prompt = """
                Total PDF Pages: $totalPages
                Document Title: $docTitle
                Index / TOC Content:
                $rawIndexText
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contents)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })
                // Thinking configuration with HIGH thinkingLevel, without setting maxOutputTokens
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "HIGH")
                    })
                })
            }

            val url = "$BASE_URL/gemini-3.1-pro-preview:generateContent?key=$apiKey"
            val response = executeRequest(url, requestJson.toString())

            val chapters = parseChaptersFromJson(response, totalPages, "Gemini 3.1 Pro (High Thinking)")
            Result.success(chapters)
        } catch (e: Exception) {
            Log.e(TAG, "High thinking analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Multimodal Image Understanding with gemini-3.1-pro-preview.
     * Analyzes photo of a physical book index or document TOC page.
     */
    suspend fun analyzeIndexImage(
        bitmap: Bitmap,
        totalPages: Int,
        docTitle: String
    ): Result<List<ChapterItem>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel.")
            )
        }

        try {
            val base64Image = bitmapToBase64(bitmap)
            val prompt = """
                You are an expert document index reader. Look at this photo/scan of a Table of Contents / Index for "$docTitle" (Total PDF Pages: $totalPages).
                Extract each chapter title, its starting page, and ending page.
                Account for chapter sequences. If end pages are not printed, infer that each chapter ends right before the next begins, and the last chapter ends at page $totalPages.
                Return ONLY a JSON array:
                [
                  {
                    "title": "Chapter 1: Title",
                    "startPage": 1,
                    "endPage": 20,
                    "topics": ["Overview"]
                  }
                ]
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val url = "$BASE_URL/gemini-3.1-pro-preview:generateContent?key=$apiKey"
            val response = executeRequest(url, requestJson.toString())
            val chapters = parseChaptersFromJson(response, totalPages, "Gemini 3.1 Pro (Visual Scan)")
            Result.success(chapters)
        } catch (e: Exception) {
            Log.e(TAG, "Image index analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fast Auto-Segregation heuristic with gemini-3.1-flash-lite.
     */
    suspend fun quickAutoSegregate(
        totalPages: Int,
        docTitle: String,
        samplePageHeaders: List<String>
    ): Result<List<ChapterItem>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured.")
            )
        }

        try {
            val prompt = """
                Document Title: $docTitle
                Total Pages: $totalPages
                Sample detected page headings:
                ${samplePageHeaders.joinToString("\n")}

                Divide this document into logical chapters/sections based on the headings and total pages.
                Return ONLY a JSON array:
                [
                  {
                    "title": "Section Title",
                    "startPage": 1,
                    "endPage": 10
                  }
                ]
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "$BASE_URL/gemini-3.1-flash-lite-preview:generateContent?key=$apiKey"
            val response = executeRequest(url, requestJson.toString())
            val chapters = parseChaptersFromJson(response, totalPages, "Gemini 3.1 Flash Lite (Fast)")
            Result.success(chapters)
        } catch (e: Exception) {
            Log.e(TAG, "Fast auto segregation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Chapter Summarization with gemini-3.5-flash.
     */
    suspend fun summarizeChapter(
        chapterTitle: String,
        pageRange: String,
        extractedTextSample: String
    ): Result<Pair<String, List<String>>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val prompt = """
                Summarize this chapter: "$chapterTitle" ($pageRange).
                Content excerpt:
                $extractedTextSample

                Respond in JSON:
                {
                  "summary": "Concise 2-3 sentence overview of this chapter.",
                  "keyTopics": ["Topic 1", "Topic 2", "Topic 3"]
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                })
            }

            val url = "$BASE_URL/gemini-3.5-flash:generateContent?key=$apiKey"
            val response = executeRequest(url, requestJson.toString())
            val text = extractContentText(response)

            val obj = JSONObject(text)
            val summary = obj.optString("summary", "Summary unavailable.")
            val topicsArray = obj.optJSONArray("keyTopics") ?: JSONArray()
            val topics = mutableListOf<String>()
            for (i in 0 until topicsArray.length()) {
                topics.add(topicsArray.getString(i))
            }
            Result.success(Pair(summary, topics))
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed", e)
            Result.failure(e)
        }
    }

    private fun executeRequest(url: String, jsonBody: String): String {
        val body = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw IllegalStateException("Gemini API Error: $err")
            }
            return response.body?.string() ?: throw IllegalStateException("Empty response body")
        }
    }

    private fun extractContentText(rawResponse: String): String {
        val root = JSONObject(rawResponse)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("No response candidates returned by Gemini model.")
        if (candidates.length() == 0) {
            val promptFeedback = root.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            throw IllegalStateException(
                if (!blockReason.isNullOrBlank()) "Gemini request blocked: $blockReason"
                else "Empty candidates list from Gemini model."
            )
        }
        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw IllegalStateException("No content object in candidate.")
        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("No parts found in model content.")
        if (parts.length() == 0) {
            throw IllegalStateException("Empty parts list in model content.")
        }
        val rawText = parts.getJSONObject(0).optString("text", "")
        return cleanJsonText(rawText)
    }

    private fun cleanJsonText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```json", ignoreCase = true)) {
            cleaned = cleaned.substring(7)
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3)
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3)
        }
        return cleaned.trim()
    }

    private fun parseChaptersFromJson(
        rawResponse: String,
        totalPages: Int,
        detectorName: String
    ): List<ChapterItem> {
        val text = extractContentText(rawResponse)

        // Parse JSON array
        val array = JSONArray(text)
        val chapters = mutableListOf<ChapterItem>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val title = item.optString("title", "Chapter ${i + 1}")
            var start = item.optInt("startPage", 1).coerceIn(1, totalPages)
            var end = item.optInt("endPage", totalPages).coerceIn(start, totalPages)

            val topics = mutableListOf<String>()
            val topicsJson = item.optJSONArray("topics")
            if (topicsJson != null) {
                for (j in 0 until topicsJson.length()) {
                    topics.add(topicsJson.getString(j))
                }
            }

            chapters.add(
                ChapterItem(
                    id = UUID.randomUUID().toString(),
                    index = i + 1,
                    title = title,
                    startPage = start,
                    endPage = end,
                    detectedBy = detectorName,
                    summary = null,
                    keyTopics = topics
                )
            )
        }

        return chapters
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
