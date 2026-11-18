package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.VideoItem
import com.example.utils.FormatUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "AiVideoRepository"

class AiVideoRepository {

    private val textModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    fun streamVideoSummary(video: VideoItem, frameBitmap: Bitmap?, extraNotes: String): Flow<String> = flow {
        val prompt = buildString {
            appendLine("You are an expert cinema & media analyst inside LT PLAYER.")
            appendLine("Provide a crisp, structured summary, likely content overview, viewing takeaways, and recommended playback settings for this video:")
            appendLine("- Title: ${video.title}")
            appendLine("- File: ${video.displayName}")
            appendLine("- Duration: ${FormatUtils.formatDuration(video.durationMs)}")
            appendLine("- Resolution: ${video.width}x${video.height} (${video.resolutionLabel})")
            appendLine("- Container/MIME: ${video.containerExtension.uppercase()} (${video.mimeType})")
            if (extraNotes.isNotBlank()) {
                appendLine("- User context/transcript: $extraNotes")
            }
        }
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            val stream = if (frameBitmap != null) {
                textModel.generateContentStream(
                    content {
                        image(frameBitmap)
                        text(prompt)
                    }
                )
            } else {
                textModel.generateContentStream(prompt)
            }
            stream.collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    fun streamSceneDescription(video: VideoItem, frameBitmap: Bitmap?): Flow<String> = flow {
        val prompt = if (frameBitmap != null) {
            "Analyze this real video frame from '${video.title}' (${video.resolutionLabel}). Describe the visual scene, lighting, subjects, mood, color palette, and suggest ideal aspect ratio / subtitle placement."
        } else {
            "Describe expected scene composition and visual framing considerations for '${video.title}' (${video.resolutionLabel}, ${FormatUtils.formatDuration(video.durationMs)})."
        }
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            val stream = if (frameBitmap != null) {
                textModel.generateContentStream(
                    content {
                        image(frameBitmap)
                        text(prompt)
                    }
                )
            } else {
                textModel.generateContentStream(prompt)
            }
            stream.collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    fun streamSubtitleGeneration(video: VideoItem, transcriptOrContext: String): Flow<String> = flow {
        val prompt = buildString {
            appendLine("Generate a valid SRT subtitle draft for the video '${video.title}' (duration ${FormatUtils.formatDuration(video.durationMs)}).")
            appendLine("Output clean numbered SRT blocks with timestamps like 00:00:01,000 --> 00:00:04,500.")
            if (transcriptOrContext.isNotBlank()) {
                appendLine("Base the subtitles on this dialogue/script:")
                appendLine(transcriptOrContext)
            } else {
                appendLine("No transcript was provided, so generate a well-timed starter template SRT with chapter intro cues matching the video duration.")
            }
        }
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            textModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    fun streamSubtitleTranslation(sourceSubtitles: String, targetLanguage: String): Flow<String> = flow {
        val prompt = """
            Translate the following subtitles into $targetLanguage.
            Preserve all SRT/VTT sequence numbers and timestamps verbatim. Only translate the spoken lines naturally.
            
            $sourceSubtitles
        """.trimIndent()
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            textModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    fun streamMetadataExplanation(video: VideoItem, codecReportMessage: String): Flow<String> = flow {
        val prompt = """
            Explain the technical video metadata below in plain, helpful language for a mobile viewer:
            - File: ${video.displayName}
            - Size: ${FormatUtils.formatFileSize(video.sizeBytes)}
            - Duration: ${FormatUtils.formatDuration(video.durationMs)}
            - Resolution: ${video.width}x${video.height}
            - MIME / Container: ${video.mimeType} / .${video.containerExtension}
            - Device Hardware Decoder Report: $codecReportMessage
            Cover playback smoothness, battery impact, and compatibility notes.
        """.trimIndent()
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            textModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    fun streamEditingAssistance(video: VideoItem, goal: String): Flow<String> = flow {
        val prompt = """
            Act as a mobile video editing assistant for '${video.title}' (${FormatUtils.formatDuration(video.durationMs)}, ${video.resolutionLabel}).
            User editing goal: ${goal.ifBlank { "Create engaging social highlight cuts and chapter markers" }}
            Provide timestamped cut suggestions, pacing tips, audio leveling guidance, and export settings.
        """.trimIndent()
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            textModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    suspend fun generateAiThumbnail(promptText: String): Result<Bitmap> {
        return try {
            val bitmap = withTimeout(60_000L) {
                val response = imageModel.generateContent(promptText)
                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
            }
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("No thumbnail image was returned by Gemini."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Thumbnail generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail generation failed", e)
            Result.failure(e)
        }
    }
}
