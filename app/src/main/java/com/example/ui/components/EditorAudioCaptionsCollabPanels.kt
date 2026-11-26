package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.MusicTrackConfig
import com.example.model.ProjectActivity
import com.example.model.StudioCatalog
import com.example.model.VideoProject
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.viewmodel.AiTaskState
import com.example.viewmodel.UiState

@Composable
fun AudioLibraryPanel(
    project: VideoProject,
    previewTrackId: String?,
    onTogglePreview: (MusicTrackConfig) -> Unit,
    onAttachTrack: (MusicTrackConfig) -> Unit,
    onUpdateVolume: (Double) -> Unit,
    onRemoveTrack: () -> Unit
) {
    val genres = remember { listOf("All") + StudioCatalog.musicLibrary.map { it.genre }.distinct() }
    var selectedGenre by remember { mutableStateOf("All") }
    val filteredTracks = remember(selectedGenre) {
        if (selectedGenre == "All") StudioCatalog.musicLibrary
        else StudioCatalog.musicLibrary.filter { it.genre == selectedGenre }
    }
    val attached = project.musicTrack
    var musicVol by remember(attached?.trackId, attached?.volume) {
        mutableFloatStateOf((attached?.volume ?: 0.75).toFloat())
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        if (attached != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CoralPulse.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, CoralPulse.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Active Soundtrack", style = MaterialTheme.typography.labelSmall, color = CoralPulse)
                            Text("${attached.title} • ${attached.artist}", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = onRemoveTrack) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove music", tint = CoralPulse)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Music Volume", style = MaterialTheme.typography.labelMedium)
                        Text("${(musicVol * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = CoralPulse)
                    }
                    Slider(
                        value = musicVol,
                        onValueChange = { musicVol = it },
                        onValueChangeFinished = { onUpdateVolume(musicVol.toDouble()) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = CoralPulse, activeTrackColor = CoralPulse)
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(genres) { genre ->
                val selected = selectedGenre == genre
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) CoralPulse else ElevatedCard,
                    border = BorderStroke(1.dp, if (selected) CoralPulse else StudioOutline),
                    onClick = { selectedGenre = genre }
                ) {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) ObsidianVoid else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        filteredTracks.forEach { track ->
            val isAttached = attached?.trackId == track.trackId
            val isPreviewing = previewTrackId == track.trackId
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ElevatedCard,
                border = BorderStroke(1.dp, if (isAttached) CoralPulse else StudioOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_track_${track.trackId}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPreviewing) CoralPulse else StudioSurface,
                        onClick = { onTogglePreview(track) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Preview track",
                                tint = if (isPreviewing) ObsidianVoid else ElectricCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${track.artist} • ${track.genre} • ${track.bpm} BPM",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onAttachTrack(track) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAttached) EmeraldSync else ElectricCyan,
                            contentColor = ObsidianVoid
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isAttached) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isAttached) "Used" else "Use", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CaptionsAndTextPanel(
    project: VideoProject,
    aiState: AiTaskState,
    onGenerateAutoCaptions: (String, String) -> Unit,
    onRemixCaptions: (String) -> Unit,
    onAddManualCaption: (String, String) -> Unit,
    onStyleAllCaptions: (String) -> Unit,
    onDeleteCaption: (String) -> Unit,
    onAddTextOverlay: (String) -> Unit,
    onDeleteTextOverlay: (String) -> Unit
) {
    var topicInput by remember { mutableStateOf("Neon city nights meet alpine horizons — built in Isaaking Studio") }
    var manualCaption by remember { mutableStateOf("") }
    var titleOverlay by remember { mutableStateOf("") }
    var selectedStyleId by remember {
        mutableStateOf(project.captions.firstOrNull()?.styleId ?: "hormozi_pop")
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // AI Auto-Captions Generator
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Auto-Captions", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = topicInput,
                    onValueChange = { topicInput = it },
                    label = { Text("Spoken dialogue or reel topic") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_caption_topic_input")
                )

                // Style Selector
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StudioCatalog.captionStyles) { style ->
                        val active = selectedStyleId == style.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) NeonViolet else StudioSurface,
                            border = BorderStroke(1.dp, if (active) NeonViolet else StudioOutline),
                            onClick = {
                                selectedStyleId = style.id
                                if (project.captions.isNotEmpty()) onStyleAllCaptions(style.id)
                            }
                        ) {
                            Text(
                                text = style.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) ObsidianVoid else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = { onGenerateAutoCaptions(topicInput, selectedStyleId) },
                    enabled = aiState !is AiTaskState.Running,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet, contentColor = ObsidianVoid),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_auto_captions_button")
                ) {
                    if (aiState is AiTaskState.Running) {
                        CircularProgressIndicator(color = ObsidianVoid, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(aiState.message)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-Generate Captions", fontWeight = FontWeight.Bold)
                    }
                }

                if (project.captions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onRemixCaptions("Translate naturally into Spanish") },
                            enabled = aiState !is AiTaskState.Running,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Spanish")
                        }
                        OutlinedButton(
                            onClick = { onRemixCaptions("Make punchier viral short-form hooks") },
                            enabled = aiState !is AiTaskState.Running,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Viral Punch")
                        }
                    }
                }
            }
        }

        // Manual Caption + Floating Title Overlay
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualCaption,
                        onValueChange = { manualCaption = it },
                        label = { Text("Add caption at playhead") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddManualCaption(manualCaption, selectedStyleId)
                            manualCaption = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Add")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleOverlay,
                        onValueChange = { titleOverlay = it },
                        label = { Text("Add floating title overlay") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddTextOverlay(titleOverlay)
                            titleOverlay = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = ObsidianVoid),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Title")
                    }
                }
            }
        }

        // Existing Captions & Overlays
        project.captions.forEach { cap ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cap.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${formatTimecode(cap.startMs)} → ${formatTimecode(cap.endMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonViolet
                        )
                    }
                    IconButton(onClick = { onDeleteCaption(cap.captionId) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete caption", tint = CoralPulse)
                    }
                }
            }
        }

        project.textOverlays.forEach { overlay ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TITLE: ${overlay.text}", style = MaterialTheme.typography.bodyMedium, color = CinemaAmber)
                        Text(
                            text = "${formatTimecode(overlay.startMs)} → ${formatTimecode(overlay.endMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDeleteTextOverlay(overlay.overlayId) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete title", tint = CoralPulse)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollabAndSyncPanel(
    project: VideoProject,
    activityState: UiState<List<ProjectActivity>>,
    onEnsureInviteActive: () -> Unit,
    onCopiedCode: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElevatedCard,
            border = BorderStroke(1.dp, EmeraldSync.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Multi-Device Live Sync", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Share this code to co-edit from any phone or tablet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.Share, contentDescription = null, tint = EmeraldSync)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = project.inviteCode,
                            style = MaterialTheme.typography.titleLarge,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                onEnsureInviteActive()
                                clipboard.setText(AnnotatedString(project.inviteCode))
                                onCopiedCode()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSync, contentColor = ObsidianVoid),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("copy_invite_code_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Code")
                        }
                    }
                }

                Text("Active Editors & Devices", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    project.activeEditors.forEach { editor ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, EmeraldSync.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldSync)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Rounded.Devices, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(editor, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        Text("Collaborative Edit Feed", style = MaterialTheme.typography.titleMedium)

        when (activityState) {
            is UiState.Loading -> {
                CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(22.dp))
            }
            is UiState.Error -> {
                Text(activityState.message, color = CoralPulse, style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                activityState.data.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(item.actionSummary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${item.actorName} • ${item.deviceName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
