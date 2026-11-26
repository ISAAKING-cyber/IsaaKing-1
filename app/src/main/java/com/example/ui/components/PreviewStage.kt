package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.model.CaptionSegment
import com.example.model.ColorFilterConfig
import com.example.model.TextOverlayItem
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

data class ActiveClipFrame(
    val clipIndex: Int,
    val clip: VideoClip,
    val localTimeMs: Long,
    val clipProgress: Float,
    val transitionAlpha: Float,
    val activeTransitionId: String?
)

@OptIn(UnstableApi::class)
@Composable
fun PreviewStage(
    project: VideoProject,
    playheadMs: Long,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onTogglePlay: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddClipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalDuration = project.computedDurationMs().coerceAtLeast(1000L)
    val frameInfo = remember(project.clips, playheadMs) {
        resolveActiveClipFrame(project.clips, playheadMs)
    }

    val aspect = when (project.aspectRatio) {
        "16:9" -> 16f / 9f
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        else -> 9f / 16f
    }

    val filterMatrix = remember(project.globalFilter) {
        buildColorMatrix(project.globalFilter)
    }

    val activeCaption = remember(project.captions, playheadMs) {
        project.captions.firstOrNull { playheadMs in it.startMs..it.endMs }
    }

    val activeOverlay = remember(project.textOverlays, playheadMs) {
        project.textOverlays.firstOrNull { playheadMs in it.startMs..it.endMs }
    }

    val hasDeviceVideo = remember(project.clips) {
        project.clips.any { it.sourceType == VideoClip.SOURCE_DEVICE_MEDIA && it.localUri.isNotBlank() }
    }

    // ExoPlayer for real device video clips (constructed only when needed)
    val exoPlayer = remember(context, hasDeviceVideo) {
        if (hasDeviceVideo) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
        } else null
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    val currentClip = frameInfo?.clip
    val isDeviceVideo = currentClip?.sourceType == VideoClip.SOURCE_DEVICE_MEDIA && currentClip.localUri.isNotBlank()

    LaunchedEffect(exoPlayer, currentClip?.clipId, currentClip?.localUri, currentClip?.trimStartMs, currentClip?.trimEndMs) {
        if (currentClip != null && isDeviceVideo && exoPlayer != null) {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(currentClip.localUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(currentClip.trimStartMs)
                        .setEndPositionMs(currentClip.trimEndMs)
                        .build()
                )
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, isDeviceVideo, currentClip?.volume) {
        if (exoPlayer == null) return@LaunchedEffect
        if (currentClip != null && isDeviceVideo) {
            exoPlayer.volume = currentClip.volume.toFloat()
            if (isPlaying) exoPlayer.play() else exoPlayer.pause()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(exoPlayer, playheadMs, isPlaying, isDeviceVideo) {
        if (exoPlayer != null && isDeviceVideo && !isPlaying && frameInfo != null) {
            val targetPos = (frameInfo.localTimeMs * frameInfo.clip.speed).toLong()
            if (abs(exoPlayer.currentPosition - targetPos) > 120L) {
                exoPlayer.seekTo(targetPos)
            }
        }
    }

    // Fallback frame thumbnail if device video player hasn't painted first frame
    var deviceFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentClip?.localUri) {
        deviceFrameBitmap = if (currentClip != null && isDeviceVideo) {
            withContext(Dispatchers.IO) {
                extractVideoFrame(context, Uri.parse(currentClip.localUri), currentClip.trimStartMs)
            }
        } else null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Video Stage Canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(ObsidianVoid)
                .padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, StudioOutline, RoundedCornerShape(14.dp))
            ) {
                if (frameInfo != null) {
                    val transModifier = transitionModifier(
                        transitionId = frameInfo.activeTransitionId,
                        progress = frameInfo.transitionAlpha,
                        clipProgress = frameInfo.clipProgress
                    )

                    if (isDeviceVideo) {
                        deviceFrameBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = frameInfo.clip.label,
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(filterMatrix),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(transModifier)
                            )
                        }
                        if (exoPlayer != null) {
                            AndroidView(
                                factory = { ctx ->
                                    (android.view.LayoutInflater.from(ctx)
                                        .inflate(R.layout.player_texture_view, null, false) as PlayerView)
                                        .apply {
                                            player = exoPlayer
                                            useController = false
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            layoutParams = FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                },
                                update = { view ->
                                    if (view.player !== exoPlayer) {
                                        view.player = exoPlayer
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(transModifier)
                            )
                        }
                    } else {
                        val drawableRes = if (frameInfo.clip.sourceType == VideoClip.SOURCE_SAMPLE_ALPINE) {
                            R.drawable.img_sample_alpine
                        } else {
                            R.drawable.img_sample_cybercity
                        }
                        Image(
                            painter = painterResource(id = drawableRes),
                            contentDescription = frameInfo.clip.label,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(filterMatrix),
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.04f + frameInfo.clipProgress * 0.08f)
                                .then(transModifier)
                        )
                    }

                    // Color Tint + Vignette Grade Layer
                    project.globalFilter?.let { filter ->
                        if (filter.tintAlpha > 0.01) {
                            val tintColor = parseHexSafe(filter.tintHex)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(tintColor.copy(alpha = filter.tintAlpha.toFloat().coerceIn(0f, 0.3f)))
                            )
                        }
                        if (filter.vignette > 0.02) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = filter.vignette.toFloat().coerceIn(0f, 0.75f))
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Active Transition Overlay Flare (Film Burn / Light Leak / Glitch)
                    if (frameInfo.transitionAlpha > 0.05f) {
                        TransitionOverlayFlare(
                            transitionId = frameInfo.activeTransitionId,
                            intensity = frameInfo.transitionAlpha
                        )
                    }

                    // Floating Text Overlay
                    activeOverlay?.let { overlay ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 20.dp, start = 14.dp, end = 14.dp)
                        ) {
                            Surface(
                                color = ObsidianVoid.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = overlay.text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Live Auto-Caption Subtitle Render
                    activeCaption?.let { caption ->
                        CaptionStageOverlay(
                            caption = caption,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 18.dp)
                        )
                    }

                    // Filter pill indicator
                    project.globalFilter?.takeIf { it.filterId != "original" }?.let { filter ->
                        Surface(
                            color = ObsidianVoid.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (filter.aiGenerated) "AI • ${filter.name}" else filter.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = CinemaAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Transport Control Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimecode(playheadMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = CircleShape,
                    color = ElectricCyan,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("play_pause_button"),
                    onClick = onTogglePlay
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ObsidianVoid
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatTimecode(totalDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElectricCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                onClick = onAddClipClick,
                modifier = Modifier.testTag("add_clip_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add clip",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clip",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionStageOverlay(
    caption: CaptionSegment,
    modifier: Modifier = Modifier
) {
    val accent = parseHexSafe(caption.highlightColorHex)
    when (caption.styleId) {
        "karaoke_gold" -> {
            Surface(
                color = ObsidianVoid.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CinemaAmber),
                modifier = modifier
            ) {
                Text(
                    text = caption.text.uppercase(),
                    color = CinemaAmber,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        "neon_cyber" -> {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, CoralPulse),
                modifier = modifier
            ) {
                Text(
                    text = caption.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        "cinema_serif" -> {
            Text(
                text = caption.text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        else -> {
            Surface(
                color = accent,
                shape = RoundedCornerShape(8.dp),
                modifier = modifier
            ) {
                Text(
                    text = caption.text.uppercase(),
                    color = ObsidianVoid,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun TransitionOverlayFlare(
    transitionId: String?,
    intensity: Float
) {
    when (transitionId) {
        "film_burn" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                CinemaAmber.copy(alpha = intensity * 0.7f),
                                CoralPulse.copy(alpha = intensity * 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        "light_leak" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElectricCyan.copy(alpha = intensity * 0.55f))
            )
        }
        "glitch_warp" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = intensity * 0.35f),
                                Color.Transparent,
                                CoralPulse.copy(alpha = intensity * 0.45f)
                            )
                        )
                    )
            )
        }
    }
}

private fun transitionModifier(
    transitionId: String?,
    progress: Float,
    clipProgress: Float
): Modifier {
    if (transitionId == null || progress <= 0.01f) return Modifier
    return when (transitionId) {
        "dissolve" -> Modifier.alpha((1f - progress * 0.85f).coerceIn(0.15f, 1f))
        "whip_pan" -> Modifier.graphicsLayer {
            translationX = -progress * 180f
            alpha = (1f - progress * 0.5f).coerceIn(0.2f, 1f)
        }
        "zoom_blur" -> Modifier.scale(1f + progress * 0.35f)
        "radial_spin" -> Modifier
            .rotate(progress * 90f)
            .scale(1f + progress * 0.2f)
        "glitch_warp" -> Modifier.graphicsLayer {
            translationX = sin(clipProgress * 45f) * progress * 24f
        }
        else -> Modifier
    }
}

fun resolveActiveClipFrame(clips: List<VideoClip>, playheadMs: Long): ActiveClipFrame? {
    if (clips.isEmpty()) return null
    var cursor = 0L
    clips.forEachIndexed { index, clip ->
        val dur = clip.effectiveDurationMs
        val end = cursor + dur
        if (playheadMs <= end || index == clips.lastIndex) {
            val localMs = (playheadMs - cursor).coerceIn(0L, dur)
            val progress = (localMs.toFloat() / dur.coerceAtLeast(1L)).coerceIn(0f, 1f)
            val hasNext = index < clips.lastIndex && clip.transitionOutId != "none"
            val transWindow = clip.transitionDurationMs.coerceAtMost(dur / 2L).coerceAtLeast(200L)
            val distToEnd = dur - localMs
            val transAlpha = if (hasNext && distToEnd < transWindow) {
                (1f - distToEnd.toFloat() / transWindow.toFloat()).coerceIn(0f, 1f)
            } else 0f
            return ActiveClipFrame(
                clipIndex = index,
                clip = clip,
                localTimeMs = localMs,
                clipProgress = progress,
                transitionAlpha = transAlpha,
                activeTransitionId = if (transAlpha > 0f) clip.transitionOutId else null
            )
        }
        cursor = end
    }
    return null
}

fun buildColorMatrix(filter: ColorFilterConfig?): ColorMatrix {
    if (filter == null) return ColorMatrix()
    val matrix = ColorMatrix()
    matrix.setToSaturation(filter.saturation.toFloat().coerceIn(0f, 2f))

    val c = filter.contrast.toFloat().coerceIn(0.5f, 2f)
    val b = (filter.brightness.toFloat() * 255f) + ((1f - c) * 128f)
    val temp = filter.temperature.toFloat().coerceIn(-1f, 1f) * 28f

    val contrastAndTemp = ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, b + temp,
            0f, c, 0f, 0f, b,
            0f, 0f, c, 0f, b - temp,
            0f, 0f, 0f, 1f, 0f
        )
    )
    matrix.timesAssign(contrastAndTemp)
    return matrix
}

fun parseHexSafe(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        ElectricCyan
    }
}

fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val mins = (safe / 60_000L)
    val secs = (safe % 60_000L) / 1000L
    val tenths = (safe % 1000L) / 100L
    return "%02d:%02d.%d".format(mins, secs, tenths)
}

private fun extractVideoFrame(context: android.content.Context, uri: Uri, positionMs: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
