package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.VideoClip
import com.example.model.VideoProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progressPercent: Int, val stage: String) : ExportState
    data class Completed(val outputFile: File, val isVideoMp4: Boolean, val summary: String) : ExportState
    data class Failed(val message: String) : ExportState
}

@UnstableApi
class VideoExportManager(private val context: Context) {

    fun exportProject(project: VideoProject): Flow<ExportState> = callbackFlow {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeName = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "isaaking_edit" }
        val deviceClips = project.clips.filter {
            it.sourceType == VideoClip.SOURCE_DEVICE_MEDIA && it.localUri.isNotBlank()
        }

        if (deviceClips.isEmpty()) {
            // Studio sample-only timeline: render studio EDL package and simulate smooth frame encoding progress
            trySend(ExportState.Exporting(10, "Compositing ${project.clips.size} timeline clips..."))
            for (step in listOf(28, 52, 76, 94)) {
                delay(280L)
                val label = when {
                    step < 40 -> "Applying ${project.globalFilter?.name ?: "Original"} color LUT..."
                    step < 70 -> "Burning ${project.captions.size} auto-captions & transitions..."
                    else -> "Mastering ${project.resolution} ${project.fps}fps package..."
                }
                trySend(ExportState.Exporting(step, label))
            }
            val outFile = File(exportDir, "${safeName}_project_spec.txt")
            outFile.writeText(buildProjectSummary(project))
            trySend(
                ExportState.Completed(
                    outputFile = outFile,
                    isVideoMp4 = false,
                    summary = "Exported Studio Master Spec (${project.resolution} • ${project.fps}fps). Add a device video clip via + Add Clip to render a shareable MP4."
                )
            )
            close()
            return@callbackFlow
        }

        val outFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.mp4")
        if (outFile.exists()) outFile.delete()

        val mainHandler = Handler(Looper.getMainLooper())
        var transformer: Transformer? = null
        var pollingActive = true

        mainHandler.post {
            try {
                val videoEffects = buildList<Effect> {
                    val targetHeight = when (project.resolution) {
                        "4K" -> 2160
                        "720p" -> 720
                        else -> 1080
                    }
                    add(Presentation.createForHeight(targetHeight))
                    val filter = project.globalFilter
                    if (filter != null && filter.filterId != "original") {
                        val contrastVal = (filter.contrast.toFloat() - 1f).coerceIn(-0.9f, 0.9f)
                        add(Contrast(contrastVal))
                        if (filter.saturation < 0.2) {
                            add(RgbFilter.createGrayscaleFilter())
                        }
                    }
                }

                val editedItems = deviceClips.map { clip ->
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 300L))
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(clip.localUri))
                        .setClippingConfiguration(clipping)
                        .build()
                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveAudio(clip.volume <= 0.05)
                        .setEffects(Effects(emptyList(), videoEffects))
                        .build()
                }

                val sequence = EditedMediaItemSequence.Builder(editedItems).build()
                val composition = Composition.Builder(sequence).build()

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        pollingActive = false
                        trySend(
                            ExportState.Completed(
                                outputFile = outFile,
                                isVideoMp4 = true,
                                summary = "Rendered ${project.resolution} MP4 (${exportResult.fileSizeBytes / 1024} KB)"
                            )
                        )
                        close()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        pollingActive = false
                        trySend(ExportState.Failed(exportException.localizedMessage ?: "Media3 export failed"))
                        close()
                    }
                }

                val builtTransformer = Transformer.Builder(context)
                    .addListener(listener)
                    .build()
                transformer = builtTransformer

                trySend(ExportState.Exporting(5, "Encoding ${deviceClips.size} video clip(s) via Media3..."))
                builtTransformer.start(composition, outFile.absolutePath)

                val progressHolder = ProgressHolder()
                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (!pollingActive) return
                        val state = builtTransformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            trySend(
                                ExportState.Exporting(
                                    progressPercent = progressHolder.progress.coerceIn(5, 99),
                                    stage = "Transcoding ${project.resolution} • ${project.fps}fps MP4..."
                                )
                            )
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED && pollingActive) {
                            mainHandler.postDelayed(this, 350L)
                        }
                    }
                }
                mainHandler.postDelayed(pollRunnable, 350L)
            } catch (e: Exception) {
                pollingActive = false
                trySend(ExportState.Failed(e.localizedMessage ?: "Failed to start Media3 export"))
                close()
            }
        }

        awaitClose {
            pollingActive = false
            mainHandler.post { transformer?.cancel() }
        }
    }

    fun buildShareIntent(file: File, isVideoMp4: Boolean): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = if (isVideoMp4) "video/mp4" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildProjectSummary(project: VideoProject): String = buildString {
        appendLine("ISAAKING VIDEO EDITOR — PROJECT MASTER SPEC")
        appendLine("Title: ${project.title}")
        appendLine("Format: ${project.aspectRatio} | ${project.resolution} @ ${project.fps}fps")
        appendLine("Duration: ${project.computedDurationMs()} ms")
        appendLine("Filter: ${project.globalFilter?.name ?: "Original"}")
        appendLine("Music: ${project.musicTrack?.title ?: "None"}")
        appendLine("Clips (${project.clips.size}):")
        project.clips.forEachIndexed { idx, clip ->
            appendLine("  ${idx + 1}. ${clip.label} [${clip.trimStartMs}ms..${clip.trimEndMs}ms] ${clip.speed}x -> ${clip.transitionOutId}")
        }
        appendLine("Captions (${project.captions.size}):")
        project.captions.forEach { cap ->
            appendLine("  [${cap.startMs}ms..${cap.endMs}ms] ${cap.text}")
        }
    }
}
