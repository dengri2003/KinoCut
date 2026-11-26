package com.example.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.example.data.local.EditorPreferences
import com.example.data.local.KinoCutDatabase
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioSettings
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioTrackItem
import com.example.domain.model.ColorGradeSettings
import com.example.domain.model.EditorToolTab
import com.example.domain.model.ExportCodec
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportState
import com.example.domain.model.Keyframe
import com.example.domain.model.SpeedCurvePreset
import com.example.domain.model.TextOverlayItem
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoProject
import com.example.media.CompositionEngine
import com.example.media.ExportManager
import com.example.media.MediaInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(UnstableApi::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = KinoCutDatabase.getInstance(application)
    private val repository = ProjectRepository(db.projectDao())
    private val preferences = EditorPreferences(application)
    private val inspector = MediaInspector(application)
    private val exportManager = ExportManager(application)

    val studioSettings: StateFlow<StudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettings())

    val exportState: StateFlow<ExportState> = exportManager.exportState

    private val _project = MutableStateFlow<VideoProject?>(null)
    val project: StateFlow<VideoProject?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()

    private val _selectedAudioId = MutableStateFlow<String?>(null)
    val selectedAudioId: StateFlow<String?> = _selectedAudioId.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorToolTab.CUT)
    val activeTab: StateFlow<EditorToolTab> = _activeTab.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _compareBeforeAfter = MutableStateFlow(false)
    val compareBeforeAfter: StateFlow<Boolean> = _compareBeforeAfter.asStateFlow()

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    private val _snapEnabled = MutableStateFlow(true)
    val snapEnabled: StateFlow<Boolean> = _snapEnabled.asStateFlow()

    private val _filmstrips = MutableStateFlow<Map<String, List<Bitmap>>>(emptyMap())
    val filmstrips: StateFlow<Map<String, List<Bitmap>>> = _filmstrips.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val undoStack = ArrayDeque<VideoProject>()
    private val redoStack = ArrayDeque<VideoProject>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    var compositionPlayer: CompositionPlayer? = null
        private set

    private var playheadJob: Job? = null
    private var rebuildJob: Job? = null
    private var saveJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startPlayheadPolling() else stopPlayheadPolling()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                stopPlayheadPolling()
                compositionPlayer?.pause()
                compositionPlayer?.seekTo(0L)
                _playheadMs.value = 0L
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerError.value = error.localizedMessage ?: "Ошибка предпросмотра"
            _isPlaying.value = false
            stopPlayheadPolling()
        }
    }

    fun initPlayerIfNeeded() {
        if (compositionPlayer == null) {
            compositionPlayer = CompositionPlayer.Builder(getApplication()).build().apply {
                addListener(playerListener)
            }
            _project.value?.let { scheduleCompositionRebuild(it, immediate = true) }
        }
    }

    fun loadProject(projectId: String) {
        if (_project.value?.id == projectId) return
        viewModelScope.launch {
            val loaded = repository.getProject(projectId) ?: return@launch
            _project.value = loaded
            _selectedClipId.value = loaded.clips.firstOrNull()?.id
            _selectedAudioId.value = loaded.audioTracks.firstOrNull()?.id
            _showGrid.value = studioSettings.value.showRuleOfThirds
            _snapEnabled.value = studioSettings.value.snapToGrid
            undoStack.clear()
            redoStack.clear()
            updateHistoryFlags()
            initPlayerIfNeeded()
            scheduleCompositionRebuild(loaded, immediate = true)
            loadMissingFilmstrips(loaded.clips)
        }
    }

    fun selectTab(tab: EditorToolTab) {
        _activeTab.value = tab
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
        _selectedTextId.value = null
    }

    fun selectTextOverlay(textId: String?) {
        _selectedTextId.value = textId
        if (textId != null) _activeTab.value = EditorToolTab.TEXT
    }

    fun selectAudioTrack(audioId: String?) {
        _selectedAudioId.value = audioId
        if (audioId != null) _activeTab.value = EditorToolTab.AUDIO
    }

    fun togglePlayPause() {
        val player = compositionPlayer ?: return
        if (_isPlaying.value) {
            player.pause()
        } else {
            val total = _project.value?.totalDurationMs ?: 0L
            if (_playheadMs.value >= total - 50L && total > 100L) {
                player.seekTo(0L)
                _playheadMs.value = 0L
            }
            player.play()
        }
    }

    fun seekTo(ms: Long) {
        val total = _project.value?.totalDurationMs?.coerceAtLeast(1L) ?: 1L
        val clamped = ms.coerceIn(0L, total)
        _playheadMs.value = clamped
        compositionPlayer?.seekTo(clamped)
        syncSelectedClipToPlayhead(clamped)
    }

    fun toggleCompareBeforeAfter() {
        _compareBeforeAfter.value = !_compareBeforeAfter.value
        _project.value?.let { scheduleCompositionRebuild(it, immediate = true) }
    }

    fun toggleGridOverlay() {
        _showGrid.value = !_showGrid.value
    }

    fun toggleSnap() {
        _snapEnabled.value = !_snapEnabled.value
    }

    fun clearPlayerError() {
        _playerError.value = null
    }

    // --- Project Mutations ---

    fun addMediaClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newClips = uris.mapNotNull { inspector.inspectVisualUri(it) }
            if (newClips.isEmpty()) return@launch
            mutateProject { current ->
                current.copy(clips = current.clips + newClips)
            }
            _selectedClipId.value = newClips.first().id
            loadMissingFilmstrips(newClips)
        }
    }

    fun addTitleCardClip(title: String, subtitle: String) {
        val current = _project.value ?: return
        viewModelScope.launch {
            val clip = inspector.createTitleCardAsset(title, subtitle, current.aspectRatio)
            mutateProject { proj -> proj.copy(clips = proj.clips + clip) }
            _selectedClipId.value = clip.id
            loadMissingFilmstrips(listOf(clip))
        }
    }

    fun splitSelectedClipAtPlayhead() {
        val proj = _project.value ?: return
        val targetId = _selectedClipId.value ?: findClipAtTime(proj, _playheadMs.value)?.id ?: return
        val index = proj.clips.indexOfFirst { it.id == targetId }
        if (index < 0) return

        val clip = proj.clips[index]
        val clipStartMs = clipStartTimeMs(proj.clips, index)
        val clipEndMs = clipStartMs + clip.timelineDurationMs
        val splitTimelineMs = _playheadMs.value

        if (splitTimelineMs <= clipStartMs + 250L || splitTimelineMs >= clipEndMs - 250L) return

        val progress = (splitTimelineMs - clipStartMs).toDouble() / clip.timelineDurationMs.toDouble()
        val splitSourceMs = clip.trimStartMs + (clip.trimmedSourceDurationMs * progress).toLong()

        val leftClip = clip.copy(
            id = UUID.randomUUID().toString(),
            trimEndMs = splitSourceMs,
            keyframes = clip.keyframes.filter { it.offsetMs <= (splitTimelineMs - clipStartMs) }
        )
        val rightClip = clip.copy(
            id = UUID.randomUUID().toString(),
            trimStartMs = splitSourceMs,
            keyframes = emptyList()
        )

        val updatedClips = proj.clips.toMutableList().apply {
            removeAt(index)
            add(index, leftClip)
            add(index + 1, rightClip)
        }
        mutateProject { it.copy(clips = updatedClips) }
        _selectedClipId.value = rightClip.id
    }

    fun trimSelectedClip(newTrimStartMs: Long, newTrimEndMs: Long) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = true) { clip ->
            val safeStart = newTrimStartMs.coerceIn(0L, (clip.sourceDurationMs - 300L).coerceAtLeast(0L))
            val safeEnd = newTrimEndMs.coerceIn(safeStart + 300L, clip.sourceDurationMs)
            clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
        }
    }

    fun duplicateSelectedClip() {
        val proj = _project.value ?: return
        val targetId = _selectedClipId.value ?: return
        val idx = proj.clips.indexOfFirst { it.id == targetId }
        if (idx < 0) return
        val clone = proj.clips[idx].copy(id = UUID.randomUUID().toString())
        val list = proj.clips.toMutableList().apply { add(idx + 1, clone) }
        mutateProject { it.copy(clips = list) }
        _selectedClipId.value = clone.id
        loadMissingFilmstrips(listOf(clone))
    }

    fun deleteSelectedClip() {
        val proj = _project.value ?: return
        if (proj.clips.size <= 1) return
        val targetId = _selectedClipId.value ?: return
        val updated = proj.clips.filterNot { it.id == targetId }
        mutateProject { it.copy(clips = updated) }
        _selectedClipId.value = updated.firstOrNull()?.id
    }

    fun moveSelectedClip(direction: Int) {
        val proj = _project.value ?: return
        val targetId = _selectedClipId.value ?: return
        val idx = proj.clips.indexOfFirst { it.id == targetId }
        val newIdx = (idx + direction).coerceIn(0, proj.clips.lastIndex)
        if (idx < 0 || idx == newIdx) return
        val list = proj.clips.toMutableList()
        val item = list.removeAt(idx)
        list.add(newIdx, item)
        mutateProject { it.copy(clips = list) }
    }

    fun updateClipSpeed(speed: Float, curve: SpeedCurvePreset) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = true) { clip ->
            clip.copy(speed = speed.coerceIn(0.2f, 6f), speedCurve = curve)
        }
    }

    fun updateClipGeometry(
        rotationDegrees: Float? = null,
        flipHorizontal: Boolean? = null,
        scale: Float? = null,
        panX: Float? = null,
        panY: Float? = null
    ) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = true) { clip ->
            clip.copy(
                rotationDegrees = rotationDegrees ?: clip.rotationDegrees,
                flipHorizontal = flipHorizontal ?: clip.flipHorizontal,
                scale = (scale ?: clip.scale).coerceIn(0.5f, 3f),
                panX = (panX ?: clip.panX).coerceIn(-0.8f, 0.8f),
                panY = (panY ?: clip.panY).coerceIn(-0.8f, 0.8f)
            )
        }
    }

    fun updateClipColorGrade(grade: ColorGradeSettings) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = true) { clip ->
            clip.copy(colorGrade = grade)
        }
    }

    fun updateClipAudio(volume: Float, muted: Boolean, fadeInMs: Long, fadeOutMs: Long) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = true) { clip ->
            clip.copy(volume = volume.coerceIn(0f, 2f), muted = muted, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs)
        }
    }

    fun updateClipTransition(transition: TransitionType, durationMs: Long) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = false) { clip ->
            clip.copy(transitionOut = transition, transitionDurationMs = durationMs.coerceIn(200L, 1500L))
        }
    }

    // --- Keyframes ---

    fun addOrUpdateKeyframe(scale: Float, offsetX: Float, offsetY: Float, rotationDeg: Float) {
        val proj = _project.value ?: return
        val targetId = _selectedClipId.value ?: return
        val idx = proj.clips.indexOfFirst { it.id == targetId }
        if (idx < 0) return
        val clip = proj.clips[idx]
        val clipStart = clipStartTimeMs(proj.clips, idx)
        val localMs = (_playheadMs.value - clipStart).coerceIn(0L, clip.timelineDurationMs)

        val existing = clip.keyframes.indexOfFirst { kotlin.math.abs(it.offsetMs - localMs) < 120L }
        val newKf = Keyframe(
            id = if (existing >= 0) clip.keyframes[existing].id else UUID.randomUUID().toString(),
            offsetMs = localMs,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotationDeg = rotationDeg
        )
        val updatedKfs = clip.keyframes.toMutableList().apply {
            if (existing >= 0) set(existing, newKf) else add(newKf)
            sortBy { it.offsetMs }
        }
        mutateClip(targetId, debounceRebuild = true) { it.copy(keyframes = updatedKfs) }
    }

    fun removeKeyframe(keyframeId: String) {
        val targetId = _selectedClipId.value ?: return
        mutateClip(targetId, debounceRebuild = false) { clip ->
            clip.copy(keyframes = clip.keyframes.filterNot { it.id == keyframeId })
        }
    }

    // --- Text Overlays ---

    fun addTextOverlay(text: String = "Новый титр") {
        val proj = _project.value ?: return
        val start = _playheadMs.value.coerceAtMost((proj.totalDurationMs - 1000L).coerceAtLeast(0L))
        val end = (start + 3000L).coerceAtMost(maxOf(proj.totalDurationMs, start + 3000L))
        val item = TextOverlayItem(text = text, startMs = start, endMs = end)
        mutateProject(rebuildPlayer = false) { it.copy(textOverlays = it.textOverlays + item) }
        _selectedTextId.value = item.id
    }

    fun updateTextOverlay(updated: TextOverlayItem) {
        mutateProject(rebuildPlayer = false) { proj ->
            proj.copy(textOverlays = proj.textOverlays.map { if (it.id == updated.id) updated else it })
        }
    }

    fun deleteTextOverlay(id: String) {
        mutateProject(rebuildPlayer = false) { proj ->
            proj.copy(textOverlays = proj.textOverlays.filterNot { it.id == id })
        }
        if (_selectedTextId.value == id) _selectedTextId.value = null
    }

    // --- Audio Tracks ---

    fun addAudioTrack(uri: Uri) {
        viewModelScope.launch {
            val track = inspector.inspectAudioUri(uri) ?: return@launch
            mutateProject { proj -> proj.copy(audioTracks = proj.audioTracks + track) }
            _selectedAudioId.value = track.id
            _activeTab.value = EditorToolTab.AUDIO
        }
    }

    fun updateAudioTrack(updated: AudioTrackItem) {
        mutateProject(debounceRebuild = true) { proj ->
            proj.copy(audioTracks = proj.audioTracks.map { if (it.id == updated.id) updated else it })
        }
    }

    fun deleteAudioTrack(id: String) {
        mutateProject { proj ->
            proj.copy(audioTracks = proj.audioTracks.filterNot { it.id == id })
        }
        if (_selectedAudioId.value == id) _selectedAudioId.value = null
    }

    // --- Canvas & Project Meta ---

    fun setAspectRatio(ratio: AspectRatioPreset) {
        mutateProject { it.copy(aspectRatio = ratio) }
    }

    fun setTargetFps(fps: Int) {
        mutateProject(rebuildPlayer = false) { it.copy(targetFps = fps) }
    }

    // --- Undo / Redo ---

    fun undo() {
        val current = _project.value ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _project.value = prev
        updateHistoryFlags()
        scheduleCompositionRebuild(prev, immediate = true)
        scheduleSave(prev)
    }

    fun redo() {
        val current = _project.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _project.value = next
        updateHistoryFlags()
        scheduleCompositionRebuild(next, immediate = true)
        scheduleSave(next)
    }

    // --- Export ---

    fun startExport(resolution: ExportResolution, fps: Int, codec: ExportCodec) {
        val proj = _project.value ?: return
        compositionPlayer?.pause()
        exportManager.startExport(proj, resolution, fps, codec) { savedUri ->
            viewModelScope.launch {
                val updated = proj.copy(lastExportPath = savedUri)
                _project.value = updated
                repository.saveProject(updated)
            }
        }
    }

    fun cancelExport() = exportManager.cancelExport()
    fun resetExportState() = exportManager.resetState()

    // --- Helpers ---

    private fun mutateClip(
        clipId: String,
        debounceRebuild: Boolean = false,
        transform: (VideoClip) -> VideoClip
    ) {
        mutateProject(debounceRebuild = debounceRebuild) { proj ->
            proj.copy(clips = proj.clips.map { if (it.id == clipId) transform(it) else it })
        }
    }

    private fun mutateProject(
        rebuildPlayer: Boolean = true,
        debounceRebuild: Boolean = false,
        transform: (VideoProject) -> VideoProject
    ) {
        val current = _project.value ?: return
        val next = transform(current)
        if (next == current) return

        undoStack.addLast(current)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()

        _project.value = next
        if (rebuildPlayer) {
            scheduleCompositionRebuild(next, immediate = !debounceRebuild)
        }
        scheduleSave(next)
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleCompositionRebuild(project: VideoProject, immediate: Boolean) {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            if (!immediate) delay(130L)
            val player = compositionPlayer ?: return@launch
            val wasPlaying = _isPlaying.value
            val pos = _playheadMs.value
            val comp = CompositionEngine.buildComposition(
                context = getApplication(),
                project = project,
                includeBakedTextOverlays = false,
                applyColorGrading = !_compareBeforeAfter.value,
                targetHeight = null
            )
            if (comp != null) {
                player.setComposition(comp)
                player.prepare()
                player.seekTo(pos.coerceIn(0L, project.totalDurationMs.coerceAtLeast(1L)))
                if (wasPlaying) player.play()
            }
        }
    }

    private fun scheduleSave(project: VideoProject) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350L)
            repository.saveProject(project)
        }
    }

    private fun loadMissingFilmstrips(clips: List<VideoClip>) {
        viewModelScope.launch {
            clips.forEach { clip ->
                if (!_filmstrips.value.containsKey(clip.id)) {
                    val frames = inspector.extractFilmstrip(
                        uriString = clip.uriString,
                        isImage = clip.isImage,
                        startMs = clip.trimStartMs,
                        endMs = clip.trimEndMs,
                        frameCount = 8
                    )
                    _filmstrips.value = _filmstrips.value + (clip.id to frames)
                }
            }
        }
    }

    private fun startPlayheadPolling() {
        playheadJob?.cancel()
        playheadJob = viewModelScope.launch {
            while (true) {
                compositionPlayer?.let { p ->
                    _playheadMs.value = p.currentPosition.coerceAtLeast(0L)
                }
                delay(33L)
            }
        }
    }

    private fun stopPlayheadPolling() {
        playheadJob?.cancel()
        playheadJob = null
    }

    private fun syncSelectedClipToPlayhead(ms: Long) {
        val proj = _project.value ?: return
        findClipAtTime(proj, ms)?.let { clip ->
            _selectedClipId.value = clip.id
        }
    }

    fun clipStartTimeMs(clips: List<VideoClip>, targetIndex: Int): Long {
        var acc = 0L
        for (i in 0 until targetIndex) {
            acc += clips[i].timelineDurationMs
        }
        return acc
    }

    fun findClipAtTime(project: VideoProject, timeMs: Long): VideoClip? {
        var cursor = 0L
        for (clip in project.clips) {
            val end = cursor + clip.timelineDurationMs
            if (timeMs in cursor..end) return clip
            cursor = end
        }
        return project.clips.lastOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayheadPolling()
        compositionPlayer?.removeListener(playerListener)
        compositionPlayer?.release()
        compositionPlayer = null
    }
}
