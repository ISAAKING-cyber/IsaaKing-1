package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiStudioRepository
import com.example.data.ProjectRepository
import com.example.media.ExportState
import com.example.media.StudioAudioSynth
import com.example.media.VideoExportManager
import com.example.model.CaptionSegment
import com.example.model.ColorFilterConfig
import com.example.model.MusicTrackConfig
import com.example.model.ProjectActivity
import com.example.model.StudioCatalog
import com.example.model.TextOverlayItem
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class EditorDockTab(val label: String) {
    EDIT("Edit"),
    AUDIO("Audio"),
    CAPTIONS("Captions"),
    FILTERS("AI Filters"),
    TRANSITIONS("Transitions"),
    COLLAB("Collab")
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Running(val message: String) : AiTaskState
    data class Success(val message: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

enum class SyncStatus {
    SYNCED, SYNCING, ERROR
}

class EditorViewModel(
    private val projectRepository: ProjectRepository,
    private val aiRepository: AiStudioRepository,
    private val audioSynth: StudioAudioSynth,
    private val exportManager: VideoExportManager,
    private val projectId: String,
    private val currentUserId: String
) : ViewModel() {

    private val _project = MutableStateFlow<VideoProject?>(null)
    val project: StateFlow<VideoProject?> = _project.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    val activityFeed: StateFlow<UiState<List<ProjectActivity>>> =
        projectRepository.observeProjectActivity(projectId, currentUserId)
            .map<List<ProjectActivity>, UiState<List<ProjectActivity>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing project activity", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load activity"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _selectedClipIndex = MutableStateFlow(0)
    val selectedClipIndex: StateFlow<Int> = _selectedClipIndex.asStateFlow()

    private val _selectedTransitionIndex = MutableStateFlow(0)
    val selectedTransitionIndex: StateFlow<Int> = _selectedTransitionIndex.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorDockTab.EDIT)
    val activeTab: StateFlow<EditorDockTab> = _activeTab.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _previewTrackId = MutableStateFlow<String?>(null)
    val previewTrackId: StateFlow<String?> = _previewTrackId.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _aiState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiState: StateFlow<AiTaskState> = _aiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val undoStack = ArrayDeque<VideoProject>()
    private val redoStack = ArrayDeque<VideoProject>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var playTickerJob: Job? = null
    private var syncDebounceJob: Job? = null
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            projectRepository.observeProject(projectId, currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    _loadError.value = (firestoreError ?: error).localizedMessage ?: "Failed to sync project"
                }
                .collectLatest { incoming ->
                    if (incoming == null) {
                        _loadError.value = "Project not found or access removed."
                    } else {
                        _loadError.value = null
                        _project.value = incoming
                        val maxIdx = (incoming.clips.size - 1).coerceAtLeast(0)
                        if (_selectedClipIndex.value > maxIdx) {
                            _selectedClipIndex.value = maxIdx
                        }
                        val dur = incoming.computedDurationMs()
                        if (_playheadMs.value > dur) {
                            _playheadMs.value = dur
                        }
                    }
                }
        }
    }

    fun selectTab(tab: EditorDockTab) {
        _activeTab.value = tab
    }

    fun selectClip(index: Int) {
        val size = _project.value?.clips?.size ?: 0
        if (size > 0) {
            _selectedClipIndex.value = index.coerceIn(0, size - 1)
        }
    }

    fun selectTransitionBetween(clipIndex: Int) {
        _selectedTransitionIndex.value = clipIndex
        _activeTab.value = EditorDockTab.TRANSITIONS
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearAiState() {
        _aiState.value = AiTaskState.Idle
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val proj = _project.value ?: return
        val total = proj.computedDurationMs()
        if (total <= 0L) return
        if (_playheadMs.value >= total - 50L) {
            _playheadMs.value = 0L
        }
        _previewTrackId.value = null
        _isPlaying.value = true
        proj.musicTrack?.let { track ->
            audioSynth.start(track, _playheadMs.value + track.startOffsetMs)
        }

        playTickerJob?.cancel()
        playTickerJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                delay(24L)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick).coerceIn(1L, 120L)
                lastTick = now
                val currentTotal = _project.value?.computedDurationMs() ?: total
                val next = _playheadMs.value + delta
                if (next >= currentTotal) {
                    _playheadMs.value = 0L
                    _isPlaying.value = false
                    audioSynth.stop()
                    break
                } else {
                    _playheadMs.value = next
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playTickerJob?.cancel()
        playTickerJob = null
        audioSynth.stop()
    }

    fun seekTo(ms: Long) {
        val total = _project.value?.computedDurationMs() ?: 0L
        _playheadMs.value = ms.coerceIn(0L, total)
        if (_isPlaying.value) {
            _project.value?.musicTrack?.let { track ->
                audioSynth.start(track, _playheadMs.value + track.startOffsetMs)
            }
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _project.value?.let { redoStack.addLast(it) }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _project.value = prev
        syncProjectToCloud(prev, "Undid previous edit", immediate = true)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        _project.value?.let { undoStack.addLast(it) }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _project.value = next
        syncProjectToCloud(next, "Redid edit", immediate = true)
    }

    fun renameProject(newTitle: String) {
        val clean = newTitle.trim().take(80)
        if (clean.isEmpty()) return
        mutateProject("Renamed project to \"$clean\"") { it.copy(title = clean) }
    }

    fun setAspectRatio(ratio: String) {
        mutateProject("Changed canvas ratio to $ratio") { it.copy(aspectRatio = ratio) }
    }

    fun setExportSpec(resolution: String, fps: Int) {
        mutateProject("Set export format $resolution • ${fps}fps") {
            it.copy(resolution = resolution, fps = fps)
        }
    }

    fun addClips(newClips: List<VideoClip>) {
        if (newClips.isEmpty()) return
        mutateProject("Added ${newClips.size} clip(s) to timeline") { proj ->
            val combined = (proj.clips + newClips).take(20).mapIndexed { idx, clip ->
                clip.copy(orderIndex = idx)
            }
            proj.copy(clips = combined, totalDurationMs = combined.sumOf { c -> c.effectiveDurationMs })
        }
    }

    fun updateClipTrim(index: Int, trimStartMs: Long, trimEndMs: Long, commitSync: Boolean = true) {
        val current = _project.value ?: return
        val clip = current.clips.getOrNull(index) ?: return
        val minSpan = 500L
        val safeStart = trimStartMs.coerceIn(0L, (clip.totalDurationMs - minSpan).coerceAtLeast(0L))
        val safeEnd = trimEndMs.coerceIn(safeStart + minSpan, clip.totalDurationMs.coerceAtLeast(minSpan))
        val updatedClips = current.clips.toMutableList().apply {
            this[index] = clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
        }
        val updated = current.copy(
            clips = updatedClips,
            totalDurationMs = updatedClips.sumOf { it.effectiveDurationMs }
        )
        if (commitSync) {
            pushUndoAndApply(current, updated, "Trimmed ${clip.label}", immediate = false)
        } else {
            _project.value = updated
        }
    }

    fun splitSelectedClipAtPlayhead() {
        val current = _project.value ?: return
        val idx = _selectedClipIndex.value
        val clip = current.clips.getOrNull(idx) ?: return
        if (current.clips.size >= 20) {
            _statusBanner.value = "Timeline clip limit (20) reached."
            return
        }
        val clipStartOnTimeline = current.clips.take(idx).sumOf { it.effectiveDurationMs }
        val localOffset = ((_playheadMs.value - clipStartOnTimeline) * clip.speed).toLong()
        val splitPoint = clip.trimStartMs + localOffset
        val minPart = 450L
        val actualSplit = if (splitPoint > clip.trimStartMs + minPart && splitPoint < clip.trimEndMs - minPart) {
            splitPoint
        } else {
            (clip.trimStartMs + clip.trimEndMs) / 2L
        }
        if (actualSplit - clip.trimStartMs < minPart || clip.trimEndMs - actualSplit < minPart) {
            _statusBanner.value = "Clip is too short to split further."
            return
        }
        val left = clip.copy(trimEndMs = actualSplit, transitionOutId = "dissolve")
        val right = clip.copy(
            clipId = "clip_${UUID.randomUUID().toString().take(8)}",
            label = "${clip.label} Pt.2".take(40),
            trimStartMs = actualSplit
        )
        val nextClips = current.clips.toMutableList().apply {
            this[idx] = left
            add(idx + 1, right)
        }.mapIndexed { order, c -> c.copy(orderIndex = order) }

        mutateProject("Split ${clip.label}") {
            it.copy(clips = nextClips, totalDurationMs = nextClips.sumOf { c -> c.effectiveDurationMs })
        }
    }

    fun setClipSpeed(index: Int, speed: Double) {
        val safeSpeed = speed.coerceIn(0.25, 3.0)
        mutateProject("Set clip speed ${"%.2f".format(safeSpeed)}x") { proj ->
            val next = proj.clips.mapIndexed { i, clip ->
                if (i == index) clip.copy(speed = safeSpeed) else clip
            }
            proj.copy(clips = next, totalDurationMs = next.sumOf { it.effectiveDurationMs })
        }
    }

    fun setClipVolume(index: Int, volume: Double) {
        val safeVol = volume.coerceIn(0.0, 1.0)
        mutateProject("Adjusted clip audio ${(safeVol * 100).toInt()}%") { proj ->
            val next = proj.clips.mapIndexed { i, clip ->
                if (i == index) clip.copy(volume = safeVol) else clip
            }
            proj.copy(clips = next)
        }
    }

    fun duplicateClip(index: Int) {
        val current = _project.value ?: return
        val clip = current.clips.getOrNull(index) ?: return
        if (current.clips.size >= 20) {
            _statusBanner.value = "Timeline clip limit (20) reached."
            return
        }
        val copyClip = clip.copy(
            clipId = "clip_${UUID.randomUUID().toString().take(8)}",
            label = "${clip.label} Copy".take(40)
        )
        val next = current.clips.toMutableList().apply { add(index + 1, copyClip) }
            .mapIndexed { i, c -> c.copy(orderIndex = i) }
        mutateProject("Duplicated ${clip.label}") {
            it.copy(clips = next, totalDurationMs = next.sumOf { c -> c.effectiveDurationMs })
        }
    }

    fun moveClip(index: Int, delta: Int) {
        val current = _project.value ?: return
        val target = index + delta
        if (index !in current.clips.indices || target !in current.clips.indices) return
        val next = current.clips.toMutableList()
        val moved = next.removeAt(index)
        next.add(target, moved)
        val ordered = next.mapIndexed { i, c -> c.copy(orderIndex = i) }
        _selectedClipIndex.value = target
        mutateProject("Reordered ${moved.label}") {
            it.copy(clips = ordered)
        }
    }

    fun deleteClip(index: Int) {
        val current = _project.value ?: return
        if (current.clips.size <= 1) {
            _statusBanner.value = "Keep at least one clip on the timeline."
            return
        }
        val removed = current.clips.getOrNull(index) ?: return
        val next = current.clips.toMutableList().apply { removeAt(index) }
            .mapIndexed { i, c -> c.copy(orderIndex = i) }
        _selectedClipIndex.value = index.coerceAtMost(next.lastIndex)
        mutateProject("Removed ${removed.label}") {
            it.copy(clips = next, totalDurationMs = next.sumOf { c -> c.effectiveDurationMs })
        }
    }

    fun setTransition(clipIndex: Int, transitionId: String, durationMs: Long, applyToAll: Boolean = false) {
        val presetName = StudioCatalog.transitions.firstOrNull { it.id == transitionId }?.name ?: transitionId
        mutateProject(if (applyToAll) "Applied $presetName transition to all cuts" else "Added $presetName transition") { proj ->
            val next = proj.clips.mapIndexed { i, clip ->
                if (applyToAll || i == clipIndex) {
                    clip.copy(transitionOutId = transitionId, transitionDurationMs = durationMs.coerceIn(200L, 1800L))
                } else {
                    clip
                }
            }
            proj.copy(clips = next)
        }
    }

    fun applyFilter(filter: ColorFilterConfig) {
        mutateProject("Applied filter: ${filter.name}") { it.copy(globalFilter = filter) }
    }

    fun updateFilterAdjustments(
        brightness: Double,
        contrast: Double,
        saturation: Double,
        temperature: Double,
        vignette: Double
    ) {
        val current = _project.value ?: return
        val base = current.globalFilter ?: StudioCatalog.presetFilters.first()
        val updatedFilter = base.copy(
            filterId = if (base.filterId == "original") "custom_grade" else base.filterId,
            name = if (base.filterId == "original") "Custom Grade" else base.name,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            temperature = temperature,
            vignette = vignette
        )
        mutateProject("Fine-tuned color grading") { it.copy(globalFilter = updatedFilter) }
    }

    fun generateAiFilter(prompt: String, sampleBitmap: Bitmap? = null) {
        viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Gemini AI is grading your scene...")
            val result = aiRepository.generateAiFilter(prompt, sampleBitmap)
            result.fold(
                onSuccess = { filter ->
                    applyFilter(filter)
                    _aiState.value = AiTaskState.Success("Applied AI LUT: ${filter.name}")
                },
                onFailure = { err ->
                    _aiState.value = AiTaskState.Error(err.localizedMessage ?: "AI Filter failed")
                }
            )
        }
    }

    fun generateAiAutoCaptions(topicOrScript: String, styleId: String, sampleBitmap: Bitmap? = null) {
        val proj = _project.value ?: return
        viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Gemini AI is generating timed captions...")
            val result = aiRepository.generateAutoCaptions(
                topicOrScript = topicOrScript,
                clipLabels = proj.clips.map { it.label },
                totalDurationMs = proj.computedDurationMs().coerceAtLeast(4000L),
                styleId = styleId,
                frameBitmap = sampleBitmap
            )
            result.fold(
                onSuccess = { segments ->
                    mutateProject("Generated ${segments.size} AI auto-captions") {
                        it.copy(captions = segments)
                    }
                    _aiState.value = AiTaskState.Success("Generated ${segments.size} timed captions")
                },
                onFailure = { err ->
                    _aiState.value = AiTaskState.Error(err.localizedMessage ?: "Auto-captions failed")
                }
            )
        }
    }

    fun remixCaptionsWithAi(instruction: String) {
        val currentCaptions = _project.value?.captions.orEmpty()
        viewModelScope.launch {
            _aiState.value = AiTaskState.Running("AI is transforming captions...")
            val result = aiRepository.remixCaptions(currentCaptions, instruction)
            result.fold(
                onSuccess = { updated ->
                    mutateProject("AI updated captions ($instruction)") { it.copy(captions = updated) }
                    _aiState.value = AiTaskState.Success("Updated ${updated.size} captions")
                },
                onFailure = { err ->
                    _aiState.value = AiTaskState.Error(err.localizedMessage ?: "Caption update failed")
                }
            )
        }
    }

    fun addManualCaption(text: String, styleId: String) {
        val clean = text.trim().take(180)
        if (clean.isEmpty()) return
        val proj = _project.value ?: return
        val total = proj.computedDurationMs().coerceAtLeast(3000L)
        val start = _playheadMs.value.coerceIn(0L, (total - 1000L).coerceAtLeast(0L))
        val end = (start + 2200L).coerceAtMost(total)
        val accent = StudioCatalog.captionStyles.firstOrNull { it.id == styleId }?.accentHex ?: "#00E5FF"
        val seg = CaptionSegment(
            captionId = "cap_${UUID.randomUUID().toString().take(8)}",
            text = clean,
            startMs = start,
            endMs = end,
            styleId = styleId,
            highlightColorHex = accent
        )
        mutateProject("Added caption \"$clean\"") {
            it.copy(captions = (it.captions + seg).sortedBy { c -> c.startMs }.take(40))
        }
    }

    fun setCaptionStyleForAll(styleId: String) {
        val accent = StudioCatalog.captionStyles.firstOrNull { it.id == styleId }?.accentHex ?: "#00E5FF"
        mutateProject("Styled captions ($styleId)") { proj ->
            proj.copy(
                captions = proj.captions.map {
                    it.copy(styleId = styleId, highlightColorHex = accent)
                }
            )
        }
    }

    fun deleteCaption(captionId: String) {
        mutateProject("Deleted caption") { proj ->
            proj.copy(captions = proj.captions.filterNot { it.captionId == captionId })
        }
    }

    fun addTextOverlay(text: String) {
        val clean = text.trim().take(100)
        if (clean.isEmpty()) return
        val proj = _project.value ?: return
        val total = proj.computedDurationMs().coerceAtLeast(3000L)
        val start = _playheadMs.value.coerceIn(0L, (total - 800L).coerceAtLeast(0L))
        val end = (start + 3000L).coerceAtMost(total)
        val overlay = TextOverlayItem(
            overlayId = "txt_${UUID.randomUUID().toString().take(8)}",
            text = clean,
            startMs = start,
            endMs = end,
            styleId = "cinema_title"
        )
        mutateProject("Added title overlay \"$clean\"") {
            it.copy(textOverlays = (it.textOverlays + overlay).take(20))
        }
    }

    fun deleteTextOverlay(overlayId: String) {
        mutateProject("Removed title overlay") { proj ->
            proj.copy(textOverlays = proj.textOverlays.filterNot { it.overlayId == overlayId })
        }
    }

    fun togglePreviewMusicTrack(track: MusicTrackConfig) {
        if (_previewTrackId.value == track.trackId && audioSynth.isPlaying) {
            audioSynth.stop()
            _previewTrackId.value = null
        } else {
            pausePlayback()
            _previewTrackId.value = track.trackId
            audioSynth.start(track, 0L)
        }
    }

    fun attachMusicTrack(track: MusicTrackConfig) {
        if (_previewTrackId.value != null) {
            audioSynth.stop()
            _previewTrackId.value = null
        }
        mutateProject("Added soundtrack: ${track.title}") { it.copy(musicTrack = track) }
    }

    fun updateMusicVolume(volume: Double) {
        val safeVol = volume.coerceIn(0.0, 1.0)
        audioSynth.updateVolume(safeVol.toFloat())
        mutateProject("Adjusted music volume ${(safeVol * 100).toInt()}%") { proj ->
            proj.copy(musicTrack = proj.musicTrack?.copy(volume = safeVol))
        }
    }

    fun removeMusicTrack() {
        audioSynth.stop()
        _previewTrackId.value = null
        mutateProject("Removed soundtrack") { it.copy(musicTrack = null) }
    }

    fun ensureInviteActive() {
        val proj = _project.value ?: return
        viewModelScope.launch {
            projectRepository.ensureInviteActive(proj)
        }
    }

    fun startExport() {
        val proj = _project.value ?: return
        pausePlayback()
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            exportManager.exportProject(proj).collect { state ->
                _exportState.value = state
            }
        }
    }

    fun resetExportState() {
        exportJob?.cancel()
        _exportState.value = ExportState.Idle
    }

    fun shareExportIntent(file: java.io.File, isVideoMp4: Boolean) =
        exportManager.buildShareIntent(file, isVideoMp4)

    private fun mutateProject(actionSummary: String, transform: (VideoProject) -> VideoProject) {
        val current = _project.value ?: return
        val updated = transform(current)
        pushUndoAndApply(current, updated, actionSummary, immediate = true)
    }

    private fun pushUndoAndApply(
        previous: VideoProject,
        updated: VideoProject,
        actionSummary: String,
        immediate: Boolean
    ) {
        if (undoStack.size >= 25) undoStack.removeFirst()
        undoStack.addLast(previous)
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
        _project.value = updated
        syncProjectToCloud(updated, actionSummary, immediate)
    }

    private fun syncProjectToCloud(
        updated: VideoProject,
        actionSummary: String,
        immediate: Boolean
    ) {
        syncDebounceJob?.cancel()
        syncDebounceJob = viewModelScope.launch {
            if (!immediate) delay(450L)
            _syncStatus.value = SyncStatus.SYNCING
            val result = projectRepository.saveProjectEdits(updated, actionSummary)
            result.fold(
                onSuccess = { _syncStatus.value = SyncStatus.SYNCED },
                onFailure = { err ->
                    _syncStatus.value = SyncStatus.ERROR
                    _statusBanner.value = err.localizedMessage ?: "Cloud sync failed"
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        playTickerJob?.cancel()
        exportJob?.cancel()
        audioSynth.stop()
    }

    private companion object {
        const val TAG = "EditorViewModel"
    }
}
