package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.media.ExportState
import com.example.model.StudioCatalog
import com.example.model.VideoClip
import com.example.ui.components.AiFiltersPanel
import com.example.ui.components.AudioLibraryPanel
import com.example.ui.components.CaptionsAndTextPanel
import com.example.ui.components.CollabAndSyncPanel
import com.example.ui.components.EditClipPanel
import com.example.ui.components.PreviewStage
import com.example.ui.components.TimelinePanel
import com.example.ui.components.TransitionsPanel
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
import com.example.viewmodel.EditorDockTab
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.SyncStatus
import java.util.UUID

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val activityState by viewModel.activityFeed.collectAsStateWithLifecycle()
    val selectedClipIndex by viewModel.selectedClipIndex.collectAsStateWithLifecycle()
    val selectedTransitionIndex by viewModel.selectedTransitionIndex.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val previewTrackId by viewModel.previewTrackId.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRatioMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    val addClipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val baseCount = project?.clips?.size ?: 0
            val clips = uris.mapIndexed { idx, uri ->
                val dur = queryVideoDurationMs(context, uri)
                VideoClip(
                    clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                    label = "Video ${baseCount + idx + 1}",
                    sourceType = VideoClip.SOURCE_DEVICE_MEDIA,
                    localUri = uri.toString(),
                    totalDurationMs = dur,
                    trimStartMs = 0L,
                    trimEndMs = dur,
                    transitionOutId = "dissolve",
                    orderIndex = baseCount + idx
                )
            }
            viewModel.addClips(clips)
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(aiState) {
        when (val s = aiState) {
            is AiTaskState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearAiState()
            }
            is AiTaskState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearAiState()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val currentProject = project
        if (currentProject == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (loadError != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(loadError.orEmpty(), color = CoralPulse)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onBack) { Text("Back to Studio") }
                    }
                } else {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // 1. Clean Top Studio Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurface)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Studio")
                    }
                    Column(
                        modifier = Modifier
                            .clickable {
                                renameInput = currentProject.title
                                showRenameDialog = true
                            }
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = currentProject.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (syncStatus == SyncStatus.SYNCING) Icons.Rounded.CloudSync else Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = when (syncStatus) {
                                    SyncStatus.SYNCED -> EmeraldSync
                                    SyncStatus.SYNCING -> CinemaAmber
                                    SyncStatus.ERROR -> CoralPulse
                                },
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (syncStatus) {
                                    SyncStatus.SYNCED -> "Synced • ${currentProject.memberUids.size} collab"
                                    SyncStatus.SYNCING -> "Syncing..."
                                    SyncStatus.ERROR -> "Sync error"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = EmeraldSync
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Aspect Ratio Selector
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElevatedCard,
                            border = BorderStroke(1.dp, StudioOutline),
                            onClick = { showRatioMenu = true },
                            modifier = Modifier.testTag("aspect_ratio_button")
                        ) {
                            Text(
                                text = currentProject.aspectRatio,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showRatioMenu,
                            onDismissRequest = { showRatioMenu = false }
                        ) {
                            StudioCatalog.aspectRatios.forEach { ratio ->
                                DropdownMenuItem(
                                    text = { Text(ratio) },
                                    onClick = {
                                        showRatioMenu = false
                                        viewModel.setAspectRatio(ratio)
                                    }
                                )
                            }
                        }
                    }

                    // Export CTA
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianVoid
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("export_button")
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 2. Live Preview Stage + Transport Bar
            PreviewStage(
                project = currentProject,
                playheadMs = playheadMs,
                isPlaying = isPlaying,
                canUndo = canUndo,
                canRedo = canRedo,
                onTogglePlay = viewModel::togglePlayback,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onAddClipClick = {
                    addClipLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            )

            // 3. Multi-Track Timeline
            TimelinePanel(
                project = currentProject,
                selectedClipIndex = selectedClipIndex,
                playheadMs = playheadMs,
                onSeekMs = viewModel::seekTo,
                onSelectClip = viewModel::selectClip,
                onTapTransition = viewModel::selectTransitionBetween
            )

            // 4. Contextual Tool Workspace
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (activeTab) {
                    EditorDockTab.EDIT -> EditClipPanel(
                        project = currentProject,
                        selectedClipIndex = selectedClipIndex,
                        onUpdateTrim = viewModel::updateClipTrim,
                        onSplitAtPlayhead = viewModel::splitSelectedClipAtPlayhead,
                        onSetSpeed = viewModel::setClipSpeed,
                        onSetVolume = viewModel::setClipVolume,
                        onDuplicateClip = viewModel::duplicateClip,
                        onMoveClip = viewModel::moveClip,
                        onDeleteClip = viewModel::deleteClip
                    )
                    EditorDockTab.TRANSITIONS -> TransitionsPanel(
                        project = currentProject,
                        selectedTransitionIndex = selectedTransitionIndex,
                        onSelectTransitionIndex = { idx -> viewModel.selectTransitionBetween(idx) },
                        onApplyTransition = viewModel::setTransition
                    )
                    EditorDockTab.FILTERS -> AiFiltersPanel(
                        project = currentProject,
                        aiState = aiState,
                        onGenerateAiFilter = { prompt -> viewModel.generateAiFilter(prompt) },
                        onApplyPresetFilter = viewModel::applyFilter,
                        onAdjustGrading = viewModel::updateFilterAdjustments
                    )
                    EditorDockTab.CAPTIONS -> CaptionsAndTextPanel(
                        project = currentProject,
                        aiState = aiState,
                        onGenerateAutoCaptions = { topic, style -> viewModel.generateAiAutoCaptions(topic, style) },
                        onRemixCaptions = viewModel::remixCaptionsWithAi,
                        onAddManualCaption = viewModel::addManualCaption,
                        onStyleAllCaptions = viewModel::setCaptionStyleForAll,
                        onDeleteCaption = viewModel::deleteCaption,
                        onAddTextOverlay = viewModel::addTextOverlay,
                        onDeleteTextOverlay = viewModel::deleteTextOverlay
                    )
                    EditorDockTab.AUDIO -> AudioLibraryPanel(
                        project = currentProject,
                        previewTrackId = previewTrackId,
                        onTogglePreview = viewModel::togglePreviewMusicTrack,
                        onAttachTrack = viewModel::attachMusicTrack,
                        onUpdateVolume = viewModel::updateMusicVolume,
                        onRemoveTrack = viewModel::removeMusicTrack
                    )
                    EditorDockTab.COLLAB -> CollabAndSyncPanel(
                        project = currentProject,
                        activityState = activityState,
                        onEnsureInviteActive = viewModel::ensureInviteActive,
                        onCopiedCode = { /* handled cleanly */ }
                    )
                }
            }

            // 5. CapCut-Style Clean Bottom Tool Dock
            Surface(
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    EditorDockTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        val icon: ImageVector = when (tab) {
                            EditorDockTab.EDIT -> Icons.Rounded.ContentCut
                            EditorDockTab.AUDIO -> Icons.Rounded.MusicNote
                            EditorDockTab.CAPTIONS -> Icons.Rounded.ClosedCaption
                            EditorDockTab.FILTERS -> Icons.Rounded.AutoAwesome
                            EditorDockTab.TRANSITIONS -> Icons.Rounded.SwapHoriz
                            EditorDockTab.COLLAB -> Icons.Rounded.Group
                        }
                        val tint = when {
                            !selected -> MaterialTheme.colorScheme.onSurfaceVariant
                            tab == EditorDockTab.FILTERS -> CinemaAmber
                            tab == EditorDockTab.CAPTIONS -> NeonViolet
                            tab == EditorDockTab.AUDIO -> CoralPulse
                            tab == EditorDockTab.COLLAB -> EmeraldSync
                            else -> ElectricCyan
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.selectTab(tab) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("dock_tab_${tab.name.lowercase()}")
                        ) {
                            Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = tint
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = StudioSurface,
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProject(renameInput)
                        showRenameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportDialog) {
        val currentProj = project
        AlertDialog(
            onDismissRequest = {
                if (exportState !is ExportState.Exporting) {
                    viewModel.resetExportState()
                    showExportDialog = false
                }
            },
            containerColor = StudioSurface,
            title = { Text("Export Master Video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (currentProj != null && exportState is ExportState.Idle) {
                        Text("Resolution", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioCatalog.resolutions.forEach { res ->
                                val selected = currentProj.resolution == res
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) ElectricCyan else ElevatedCard,
                                    onClick = { viewModel.setExportSpec(res, currentProj.fps) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            res,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) ObsidianVoid else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Text("Frame Rate", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioCatalog.frameRates.forEach { fps ->
                                val selected = currentProj.fps == fps
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) CinemaAmber else ElevatedCard,
                                    onClick = { viewModel.setExportSpec(currentProj.resolution, fps) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            "${fps}fps",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) ObsidianVoid else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    when (val state = exportState) {
                        is ExportState.Idle -> {
                            Text(
                                text = "Media3 Transformer renders trimmed device clips with your color grade and resolution settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is ExportState.Exporting -> {
                            Text(state.stage, style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                color = ElectricCyan,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${state.progressPercent}%", style = MaterialTheme.typography.labelSmall)
                        }
                        is ExportState.Completed -> {
                            Text(state.summary, style = MaterialTheme.typography.bodyMedium, color = EmeraldSync)
                            Button(
                                onClick = {
                                    val shareIntent = viewModel.shareExportIntent(state.outputFile, state.isVideoMp4)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Export"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSync, contentColor = ObsidianVoid),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Export")
                            }
                        }
                        is ExportState.Failed -> {
                            Text(state.message, color = CoralPulse, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (exportState is ExportState.Idle || exportState is ExportState.Failed) {
                    Button(
                        onClick = { viewModel.startExport() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.testTag("start_export_button")
                    ) {
                        Text("Render Video")
                    }
                }
            },
            dismissButton = {
                if (exportState !is ExportState.Exporting) {
                    TextButton(
                        onClick = {
                            viewModel.resetExportState()
                            showExportDialog = false
                        }
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
