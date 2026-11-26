package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.TimelineSurface
import com.example.ui.theme.TimelineTrackBg
import kotlin.math.abs
import kotlin.math.sin

private const val PX_PER_SECOND_DP = 56f

@Composable
fun TimelinePanel(
    project: VideoProject,
    selectedClipIndex: Int,
    playheadMs: Long,
    onSeekMs: (Long) -> Unit,
    onSelectClip: (Int) -> Unit,
    onTapTransition: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val totalMs = project.computedDurationMs().coerceAtLeast(3000L)
    val totalSeconds = (totalMs / 1000f).coerceAtLeast(3f)
    val trackWidthDp: Dp = (totalSeconds * PX_PER_SECOND_DP + 48f).dp
    val playheadX: Dp = ((playheadMs / 1000f) * PX_PER_SECOND_DP).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TimelineSurface)
            .border(1.dp, StudioOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Timecode Ruler (tap anywhere to scrub playhead)
            Box(
                modifier = Modifier
                    .width(trackWidthDp)
                    .height(24.dp)
                    .pointerInput(totalMs) {
                        detectTapGestures { offset ->
                            val dpPos = with(density) { offset.x.toDp().value }
                            val tappedMs = ((dpPos / PX_PER_SECOND_DP) * 1000f).toLong().coerceIn(0L, totalMs)
                            onSeekMs(tappedMs)
                        }
                    }
                    .testTag("timeline_ruler")
            ) {
                val secCount = totalSeconds.toInt() + 1
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxSize()
                ) {
                    for (s in 0..secCount) {
                        Box(modifier = Modifier.width(PX_PER_SECOND_DP.dp)) {
                            Text(
                                text = "${s}s",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Main Video Clips Track + Transition Nodes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(trackWidthDp)
                    .height(64.dp)
            ) {
                project.clips.forEachIndexed { index, clip ->
                    val clipWidthDp = ((clip.effectiveDurationMs / 1000f) * PX_PER_SECOND_DP)
                        .coerceAtLeast(62f).dp
                    val isSelected = index == selectedClipIndex
                    val thumbRes = if (clip.sourceType == VideoClip.SOURCE_SAMPLE_ALPINE) {
                        R.drawable.img_sample_alpine
                    } else {
                        R.drawable.img_sample_cybercity
                    }

                    Box(
                        modifier = Modifier
                            .width(clipWidthDp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TimelineTrackBg)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ElectricCyan else StudioOutline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectClip(index) }
                            .testTag("timeline_clip_$index")
                    ) {
                        coil.compose.AsyncImage(
                            model = thumbRes,
                            contentDescription = clip.label,
                            contentScale = ContentScale.Crop,
                            alpha = 0.72f,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianVoid.copy(alpha = 0.35f))
                        )
                        // Trim handle accents when selected
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(6.dp)
                                    .fillMaxHeight()
                                    .background(ElectricCyan)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(6.dp)
                                    .fillMaxHeight()
                                    .background(ElectricCyan)
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = clip.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${"%.1f".format(clip.effectiveDurationMs / 1000.0)}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = ElectricCyan
                                )
                                if (abs(clip.speed - 1.0) > 0.05) {
                                    Text(
                                        text = "${"%.1f".format(clip.speed)}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = CinemaAmber
                                    )
                                }
                            }
                        }
                    }

                    // Transition node between adjacent clips
                    if (index < project.clips.lastIndex) {
                        val hasTrans = clip.transitionOutId != "none"
                        Surface(
                            shape = CircleShape,
                            color = if (hasTrans) CinemaAmber else TimelineTrackBg,
                            border = BorderStroke(1.dp, if (hasTrans) CinemaAmber else StudioOutline),
                            onClick = { onTapTransition(index) },
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(26.dp)
                                .testTag("transition_node_$index")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = "Edit transition",
                                    tint = if (hasTrans) ObsidianVoid else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Audio / Music Waveform Track
            val music = project.musicTrack
            Box(
                modifier = Modifier
                    .width((totalSeconds * PX_PER_SECOND_DP).coerceAtLeast(180f).dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (music != null) CoralPulse.copy(alpha = 0.22f) else TimelineTrackBg.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        if (music != null) CoralPulse.copy(alpha = 0.65f) else StudioOutline,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (music != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bars = (size.width / 6f).toInt().coerceIn(12, 120)
                        val midY = size.height / 2f
                        for (i in 0 until bars) {
                            val amp = (0.25f + 0.65f * abs(sin(i * 0.65f))) * (size.height * 0.38f)
                            val x = i * (size.width / bars)
                            drawLine(
                                color = CoralPulse.copy(alpha = 0.55f),
                                start = Offset(x, midY - amp),
                                end = Offset(x, midY + amp),
                                strokeWidth = 2.5f
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = CoralPulse,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${music.title} • ${music.bpm} BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = "+ Add Music from Audio tab",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 4. Captions & AI Filter Track Summary
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NeonViolet.copy(alpha = if (project.captions.isNotEmpty()) 0.24f else 0.08f),
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClosedCaption,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (project.captions.isNotEmpty()) "${project.captions.size} Captions" else "No Captions",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonViolet
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CinemaAmber.copy(alpha = if (project.globalFilter != null) 0.22f else 0.08f),
                    border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = CinemaAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LUT: ${project.globalFilter?.name ?: "Original"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CinemaAmber
                        )
                    }
                }
            }

            // Scrolling Playhead Needle Indicator (visual bar aligned inside scroll container)
            Box(
                modifier = Modifier
                    .offset(x = playheadX)
                    .padding(top = 4.dp)
                    .width(2.5.dp)
                    .height(10.dp)
                    .background(ElectricCyan, CircleShape)
            )
        }
    }
}
