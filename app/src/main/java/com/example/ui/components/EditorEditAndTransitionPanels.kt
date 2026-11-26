package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ColorFilterConfig
import com.example.model.StudioCatalog
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.viewmodel.AiTaskState

@Composable
fun EditClipPanel(
    project: VideoProject,
    selectedClipIndex: Int,
    onUpdateTrim: (Int, Long, Long, Boolean) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onSetSpeed: (Int, Double) -> Unit,
    onSetVolume: (Int, Double) -> Unit,
    onDuplicateClip: (Int) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onDeleteClip: (Int) -> Unit
) {
    val clip = project.clips.getOrNull(selectedClipIndex) ?: return
    val maxDur = clip.totalDurationMs.coerceAtLeast(1000L).toFloat()
    var trimRange by remember(clip.clipId, clip.trimStartMs, clip.trimEndMs) {
        mutableStateOf(clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat())
    }
    var speedSlider by remember(clip.clipId, clip.speed) {
        mutableFloatStateOf(clip.speed.toFloat())
    }
    var volumeSlider by remember(clip.clipId, clip.volume) {
        mutableFloatStateOf(clip.volume.toFloat())
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // Quick Action Pill Row (CapCut-style)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ToolActionPill(Icons.Rounded.ContentCut, "Split", ElectricCyan, onSplitAtPlayhead, "split_clip_button")
            }
            item {
                ToolActionPill(Icons.Rounded.ContentCopy, "Duplicate", MaterialTheme.colorScheme.onSurface, { onDuplicateClip(selectedClipIndex) }, "duplicate_clip_button")
            }
            item {
                ToolActionPill(Icons.AutoMirrored.Rounded.ArrowBack, "Move Left", MaterialTheme.colorScheme.onSurface, { onMoveClip(selectedClipIndex, -1) })
            }
            item {
                ToolActionPill(Icons.AutoMirrored.Rounded.ArrowForward, "Move Right", MaterialTheme.colorScheme.onSurface, { onMoveClip(selectedClipIndex, 1) })
            }
            item {
                ToolActionPill(Icons.Rounded.DeleteOutline, "Delete", CoralPulse, { onDeleteClip(selectedClipIndex) }, "delete_clip_button")
            }
        }

        // Trim In / Out Range Slider
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Trim Clip • ${clip.label}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatTimecode(trimRange.start.toLong())} → ${formatTimecode(trimRange.endInclusive.toLong())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                RangeSlider(
                    value = trimRange,
                    onValueChange = { range ->
                        if (range.endInclusive - range.start >= 500f) {
                            trimRange = range
                            onUpdateTrim(selectedClipIndex, range.start.toLong(), range.endInclusive.toLong(), false)
                        }
                    },
                    onValueChangeFinished = {
                        onUpdateTrim(selectedClipIndex, trimRange.start.toLong(), trimRange.endInclusive.toLong(), true)
                    },
                    valueRange = 0f..maxDur,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("clip_trim_slider")
                )
            }
        }

        // Speed & Volume Controls
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clip Speed", style = MaterialTheme.typography.labelLarge)
                    }
                    Text("${"%.2f".format(speedSlider)}x", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                }
                Slider(
                    value = speedSlider,
                    onValueChange = { speedSlider = it },
                    onValueChangeFinished = { onSetSpeed(selectedClipIndex, speedSlider.toDouble()) },
                    valueRange = 0.25f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clip Audio Volume", style = MaterialTheme.typography.labelLarge)
                    Text("${(volumeSlider * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                }
                Slider(
                    value = volumeSlider,
                    onValueChange = { volumeSlider = it },
                    onValueChangeFinished = { onSetVolume(selectedClipIndex, volumeSlider.toDouble()) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
            }
        }
    }
}

@Composable
fun TransitionsPanel(
    project: VideoProject,
    selectedTransitionIndex: Int,
    onSelectTransitionIndex: (Int) -> Unit,
    onApplyTransition: (Int, String, Long, Boolean) -> Unit
) {
    val maxCut = (project.clips.size - 2).coerceAtLeast(0)
    val safeIndex = selectedTransitionIndex.coerceIn(0, maxCut)
    val clip = project.clips.getOrNull(safeIndex)
    var durationMs by remember(clip?.clipId, clip?.transitionDurationMs) {
        mutableFloatStateOf((clip?.transitionDurationMs ?: 700L).toFloat())
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        if (project.clips.size < 2) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ElevatedCard,
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Add or split into 2+ clips on the timeline to place cinematic transitions between cuts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return
        }

        // Cut Selector Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..maxCut).toList()) { cutIdx ->
                val selected = cutIdx == safeIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) CinemaAmber else ElevatedCard,
                    border = BorderStroke(1.dp, if (selected) CinemaAmber else StudioOutline),
                    onClick = { onSelectTransitionIndex(cutIdx) }
                ) {
                    Text(
                        text = "Cut ${cutIdx + 1} → ${cutIdx + 2}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) ObsidianVoid else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(StudioCatalog.transitions) { preset ->
                val active = clip?.transitionOutId == preset.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) CinemaAmber.copy(alpha = 0.2f) else ElevatedCard,
                    border = BorderStroke(1.5.dp, if (active) CinemaAmber else StudioOutline),
                    onClick = { onApplyTransition(safeIndex, preset.id, durationMs.toLong(), false) },
                    modifier = Modifier
                        .width(126.dp)
                        .testTag("transition_${preset.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = if (active) CinemaAmber else ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = preset.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Transition Duration", style = MaterialTheme.typography.labelLarge)
                    Text("${"%.1f".format(durationMs / 1000f)}s", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                }
                Slider(
                    value = durationMs,
                    onValueChange = { durationMs = it },
                    onValueChangeFinished = {
                        clip?.let { onApplyTransition(safeIndex, it.transitionOutId, durationMs.toLong(), false) }
                    },
                    valueRange = 200f..1600f,
                    colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                )
                OutlinedButton(
                    onClick = {
                        clip?.let { onApplyTransition(safeIndex, it.transitionOutId, durationMs.toLong(), true) }
                    },
                    border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply to All Cuts", color = CinemaAmber)
                }
            }
        }
    }
}

@Composable
fun AiFiltersPanel(
    project: VideoProject,
    aiState: AiTaskState,
    onGenerateAiFilter: (String) -> Unit,
    onApplyPresetFilter: (ColorFilterConfig) -> Unit,
    onAdjustGrading: (Double, Double, Double, Double, Double) -> Unit
) {
    var aiPrompt by remember { mutableStateOf("Moody cyberpunk neon rain, rich teal shadows") }
    val current = project.globalFilter ?: StudioCatalog.presetFilters.first()

    var brightness by remember(current.filterId, current.brightness) { mutableFloatStateOf(current.brightness.toFloat()) }
    var contrast by remember(current.filterId, current.contrast) { mutableFloatStateOf(current.contrast.toFloat()) }
    var saturation by remember(current.filterId, current.saturation) { mutableFloatStateOf(current.saturation.toFloat()) }
    var temperature by remember(current.filterId, current.temperature) { mutableFloatStateOf(current.temperature.toFloat()) }
    var vignette by remember(current.filterId, current.vignette) { mutableFloatStateOf(current.vignette.toFloat()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // AI Prompt-to-Filter Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Color Grade Studio", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    label = { Text("Describe the look or film stock") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_filter_prompt_input")
                )
                Button(
                    onClick = { onGenerateAiFilter(aiPrompt) },
                    enabled = aiState !is AiTaskState.Running,
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = ObsidianVoid),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_ai_filter_button")
                ) {
                    if (aiState is AiTaskState.Running) {
                        CircularProgressIndicator(color = ObsidianVoid, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(aiState.message)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate AI Filter", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Preset LUT Strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StudioCatalog.presetFilters) { preset ->
                val selected = current.filterId == preset.filterId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) ElectricCyan.copy(alpha = 0.2f) else ElevatedCard,
                    border = BorderStroke(1.5.dp, if (selected) ElectricCyan else StudioOutline),
                    onClick = { onApplyPresetFilter(preset) },
                    modifier = Modifier
                        .width(114.dp)
                        .testTag("preset_filter_${preset.filterId}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(preset.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(preset.prompt, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }

        // Manual Pro Sliders
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual Grade Adjustments", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(6.dp))
                GradeSliderRow("Brightness", brightness, -0.35f..0.35f, { brightness = it }) {
                    onAdjustGrading(brightness.toDouble(), contrast.toDouble(), saturation.toDouble(), temperature.toDouble(), vignette.toDouble())
                }
                GradeSliderRow("Contrast", contrast, 0.6f..1.8f, { contrast = it }) {
                    onAdjustGrading(brightness.toDouble(), contrast.toDouble(), saturation.toDouble(), temperature.toDouble(), vignette.toDouble())
                }
                GradeSliderRow("Saturation", saturation, 0f..2f, { saturation = it }) {
                    onAdjustGrading(brightness.toDouble(), contrast.toDouble(), saturation.toDouble(), temperature.toDouble(), vignette.toDouble())
                }
                GradeSliderRow("Warmth / Temp", temperature, -1f..1f, { temperature = it }) {
                    onAdjustGrading(brightness.toDouble(), contrast.toDouble(), saturation.toDouble(), temperature.toDouble(), vignette.toDouble())
                }
                GradeSliderRow("Vignette", vignette, 0f..0.8f, { vignette = it }) {
                    onAdjustGrading(brightness.toDouble(), contrast.toDouble(), saturation.toDouble(), temperature.toDouble(), vignette.toDouble())
                }
            }
        }
    }
}

@Composable
private fun GradeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${"%.2f".format(value)}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        onValueChangeFinished = onFinish,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
    )
}

@Composable
private fun ToolActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    tag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ElevatedCard,
        border = BorderStroke(1.dp, StudioOutline),
        onClick = onClick,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
