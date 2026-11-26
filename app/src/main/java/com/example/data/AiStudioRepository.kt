package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CaptionSegment
import com.example.model.ColorFilterConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "AiStudioRepository"
private const val AI_TIMEOUT_MS = 60_000L

class AiStudioRepository {

    private val filterSchema = Schema.obj(
        properties = mapOf(
            "filterName" to Schema.string(description = "Short creative LUT filter name, 1 to 3 words"),
            "moodDescription" to Schema.string(description = "Concise description of the color grade look"),
            "brightness" to Schema.double(description = "Brightness offset between -0.35 and 0.35"),
            "contrast" to Schema.double(description = "Contrast multiplier between 0.7 and 1.7"),
            "saturation" to Schema.double(description = "Saturation multiplier between 0.0 and 1.8"),
            "temperature" to Schema.double(description = "Color temperature from -1.0 (cool) to 1.0 (warm)"),
            "tintHex" to Schema.string(description = "Hex color tint like #00E5FF or #FFB300"),
            "tintAlpha" to Schema.double(description = "Subtle color tint overlay alpha between 0.0 and 0.22"),
            "vignette" to Schema.double(description = "Edge vignette intensity between 0.0 and 0.7")
        )
    )

    private val captionsSchema = Schema.obj(
        properties = mapOf(
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "text" to Schema.string(description = "Punchy short video caption line (3-8 words)"),
                        "startMs" to Schema.integer(description = "Start timestamp in milliseconds"),
                        "endMs" to Schema.integer(description = "End timestamp in milliseconds"),
                        "highlightColorHex" to Schema.string(description = "Accent hex color like #00E5FF or #FFB300")
                    )
                ),
                description = "Timed caption segments spanning the timeline duration"
            )
        )
    )

    private val filterModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = filterSchema
            }
        )
    }

    private val captionsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = captionsSchema
            }
        )
    }

    suspend fun generateAiFilter(
        prompt: String,
        frameBitmap: Bitmap? = null
    ): Result<ColorFilterConfig> {
        return try {
            val userBrief = prompt.trim().ifBlank { "Cinematic anamorphic cyberpunk film grade" }
            val response = withTimeout(AI_TIMEOUT_MS) {
                if (frameBitmap != null) {
                    filterModel.generateContent(
                        content {
                            image(frameBitmap)
                            text(
                                "You are a senior Hollywood film colorist. Analyze this video frame and create a bespoke color grading LUT matching the request: \"$userBrief\". Return calibrated color values."
                            )
                        }
                    )
                } else {
                    filterModel.generateContent(
                        "You are a senior Hollywood film colorist. Design a cinematic color grading LUT preset for the mood: \"$userBrief\"."
                    )
                }
            }

            val rawJson = response.text ?: throw IllegalStateException("AI returned an empty response.")
            val json = JSONObject(rawJson)
            val tintHex = json.optString("tintHex", "#00E5FF").let {
                if (Regex("^#[0-9A-Fa-f]{6}$").matches(it)) it else "#00E5FF"
            }
            val filter = ColorFilterConfig(
                filterId = "ai_${UUID.randomUUID().toString().take(8)}",
                name = json.optString("filterName", "AI Grade").take(32),
                prompt = json.optString("moodDescription", userBrief).take(110),
                brightness = json.optDouble("brightness", 0.0).coerceIn(-0.4, 0.4),
                contrast = json.optDouble("contrast", 1.15).coerceIn(0.6, 1.8),
                saturation = json.optDouble("saturation", 1.1).coerceIn(0.0, 2.0),
                temperature = json.optDouble("temperature", 0.0).coerceIn(-1.0, 1.0),
                tintHex = tintHex,
                tintAlpha = json.optDouble("tintAlpha", 0.08).coerceIn(0.0, 0.25),
                vignette = json.optDouble("vignette", 0.2).coerceIn(0.0, 0.8),
                aiGenerated = true
            )
            Result.success(filter)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI filter generation timed out", e)
            Result.failure(Exception("AI Filter generation timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI filter generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Filter generation failed."))
        }
    }

    suspend fun generateAutoCaptions(
        topicOrScript: String,
        clipLabels: List<String>,
        totalDurationMs: Long,
        styleId: String,
        frameBitmap: Bitmap? = null
    ): Result<List<CaptionSegment>> {
        return try {
            val safeDuration = totalDurationMs.coerceAtLeast(3000L)
            val clipSummary = clipLabels.joinToString(", ").ifBlank { "Cinematic montage" }
            val userContext = topicOrScript.trim().ifBlank {
                "Auto-caption this dynamic short-form reel featuring scenes: $clipSummary"
            }

            val instruction = """
                Generate tight, engaging CapCut-style timed auto-captions for a video of total duration ${safeDuration}ms.
                Video scenes: $clipSummary.
                Creator context / spoken audio summary: "$userContext".
                Create 3 to 8 sequential caption segments that fit strictly inside 0 to $safeDuration ms without overlapping, with realistic reading durations (each 1200ms to 3000ms).
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                if (frameBitmap != null) {
                    captionsModel.generateContent(
                        content {
                            image(frameBitmap)
                            text(instruction)
                        }
                    )
                } else {
                    captionsModel.generateContent(instruction)
                }
            }

            val rawJson = response.text ?: throw IllegalStateException("AI returned empty captions.")
            val json = JSONObject(rawJson)
            val array = json.getJSONArray("segments")
            val parsed = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val start = item.optLong("startMs", (i * 1500L)).coerceIn(0L, safeDuration - 500L)
                    val end = item.optLong("endMs", start + 1500L).coerceIn(start + 500L, safeDuration)
                    val hex = item.optString("highlightColorHex", "#00E5FF").let {
                        if (Regex("^#[0-9A-Fa-f]{6}$").matches(it)) it else "#00E5FF"
                    }
                    val text = item.optString("text", "").trim().take(180)
                    if (text.isNotEmpty()) {
                        add(
                            CaptionSegment(
                                captionId = "cap_${UUID.randomUUID().toString().take(8)}",
                                text = text,
                                startMs = start,
                                endMs = end,
                                styleId = styleId,
                                highlightColorHex = hex
                            )
                        )
                    }
                }
            }.sortedBy { it.startMs }

            if (parsed.isEmpty()) {
                Result.failure(IllegalStateException("AI did not return any caption segments."))
            } else {
                Result.success(parsed)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Auto-captions timed out", e)
            Result.failure(Exception("Auto-captions timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Auto-captions failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Auto-captions generation failed."))
        }
    }

    suspend fun remixCaptions(
        existing: List<CaptionSegment>,
        instruction: String
    ): Result<List<CaptionSegment>> {
        if (existing.isEmpty()) {
            return Result.failure(IllegalArgumentException("Add or generate captions first."))
        }
        return try {
            val serialized = existing.joinToString("\n") {
                "[${it.startMs}ms - ${it.endMs}ms]: ${it.text}"
            }
            val prompt = """
                Transform the following video captions according to this instruction: "$instruction".
                Preserve the exact startMs and endMs timing of each segment.
                Captions:
                $serialized
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                captionsModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned empty captions.")
            val json = JSONObject(rawJson)
            val array = json.getJSONArray("segments")
            val style = existing.first().styleId
            val parsed = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val fallback = existing.getOrNull(i)
                    val start = item.optLong("startMs", fallback?.startMs ?: 0L)
                    val end = item.optLong("endMs", fallback?.endMs ?: (start + 1500L)).coerceAtLeast(start + 400L)
                    val hex = item.optString("highlightColorHex", fallback?.highlightColorHex ?: "#00E5FF")
                    val text = item.optString("text", fallback?.text ?: "").trim().take(180)
                    if (text.isNotEmpty()) {
                        add(
                            CaptionSegment(
                                captionId = fallback?.captionId ?: "cap_${UUID.randomUUID().toString().take(8)}",
                                text = text,
                                startMs = start,
                                endMs = end,
                                styleId = style,
                                highlightColorHex = hex
                            )
                        )
                    }
                }
            }
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Caption remix timed out", e)
            Result.failure(Exception("Caption remix timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Caption remix failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to update captions."))
        }
    }
}
