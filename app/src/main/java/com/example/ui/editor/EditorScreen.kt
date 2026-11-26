package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.example.domain.model.AspectRatioPreset
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedConsole
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    BackHandler { onNavigateBack() }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycle()
    val selectedTextId by viewModel.selectedTextId.collectAsStateWithLifecycle()
    val selectedAudioId by viewModel.selectedAudioId.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val compareBeforeAfter by viewModel.compareBeforeAfter.collectAsStateWithLifecycle()
    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()
    val snapEnabled by viewModel.snapEnabled.collectAsStateWithLifecycle()
    val filmstrips by viewModel.filmstrips.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val settings by viewModel.studioSettings.collectAsStateWithLifecycle()
    val playerError by viewModel.playerError.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playerError) {
        playerError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearPlayerError()
        }
    }

    val addClipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addMediaClips(uris)
    }

    val addAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.addAudioTrack(it) }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val currentProject = project
        if (currentProject == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricCyan)
            }
            return@Scaffold
        }

        val selectedClipIndex = currentProject.clips.indexOfFirst { it.id == selectedClipId }
        val selectedClip = currentProject.clips.getOrNull(selectedClipIndex)
        val selectedClipStartMs = if (selectedClipIndex >= 0) {
            viewModel.clipStartTimeMs(currentProject.clips, selectedClipIndex)
        } else 0L
        val selectedText = currentProject.textOverlays.find { it.id == selectedTextId }
        val selectedAudio = currentProject.audioTracks.find { it.id == selectedAudioId }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            EditorTopHeader(
                title = currentProject.title,
                aspectRatio = currentProject.aspectRatio,
                targetFps = currentProject.targetFps,
                canUndo = canUndo,
                canRedo = canRedo,
                onBack = onNavigateBack,
                onAspectChange = viewModel::setAspectRatio,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onExportClick = {
                    viewModel.resetExportState()
                    showExportSheet = true
                }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWide = maxWidth >= 720.dp

                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                        ) {
                            PreviewViewport(
                                project = currentProject,
                                player = viewModel.compositionPlayer,
                                playheadMs = playheadMs,
                                isPlaying = isPlaying,
                                selectedTextId = selectedTextId,
                                compareBeforeAfter = compareBeforeAfter,
                                showGrid = showGrid,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onToggleCompare = viewModel::toggleCompareBeforeAfter,
                                onToggleGrid = viewModel::toggleGridOverlay,
                                onSelectText = viewModel::selectTextOverlay,
                                onMoveText = { item, nx, ny ->
                                    viewModel.updateTextOverlay(item.copy(xFraction = nx, yFraction = ny))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TimelineView(
                                project = currentProject,
                                selectedClipId = selectedClipId,
                                selectedTextId = selectedTextId,
                                selectedAudioId = selectedAudioId,
                                playheadMs = playheadMs,
                                isPlaying = isPlaying,
                                snapEnabled = snapEnabled,
                                filmstrips = filmstrips,
                                onScrubTo = viewModel::seekTo,
                                onSelectClip = viewModel::selectClip,
                                onSelectTransition = { clipId ->
                                    viewModel.selectClip(clipId)
                                    viewModel.selectTab(com.example.domain.model.EditorToolTab.TRANSITION)
                                },
                                onSelectText = viewModel::selectTextOverlay,
                                onSelectAudio = viewModel::selectAudioTrack,
                                onSplitAtPlayhead = viewModel::splitSelectedClipAtPlayhead,
                                onToggleSnap = viewModel::toggleSnap,
                                onAddClipClick = {
                                    addClipLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onAddAudioClick = { addAudioLauncher.launch(arrayOf("audio/*")) },
                                onTrimClipEdge = { clip, s, e ->
                                    viewModel.selectClip(clip.id)
                                    viewModel.trimSelectedClip(s, e)
                                }
                            )
                        }
                        InspectorDock(
                            activeTab = activeTab,
                            project = currentProject,
                            selectedClip = selectedClip,
                            selectedText = selectedText,
                            selectedAudio = selectedAudio,
                            playheadMs = playheadMs,
                            clipStartMs = selectedClipStartMs,
                            onSelectTab = viewModel::selectTab,
                            onSplit = viewModel::splitSelectedClipAtPlayhead,
                            onRotate = {
                                selectedClip?.let {
                                    viewModel.updateClipGeometry(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
                                }
                            },
                            onFlip = {
                                selectedClip?.let {
                                    viewModel.updateClipGeometry(flipHorizontal = !it.flipHorizontal)
                                }
                            },
                            onDuplicate = viewModel::duplicateSelectedClip,
                            onDeleteClip = viewModel::deleteSelectedClip,
                            onMoveClip = viewModel::moveSelectedClip,
                            onScaleClip = { viewModel.updateClipGeometry(scale = it) },
                            onSpeedChange = viewModel::updateClipSpeed,
                            onColorGradeChange = viewModel::updateClipColorGrade,
                            onAddKeyframe = viewModel::addOrUpdateKeyframe,
                            onRemoveKeyframe = viewModel::removeKeyframe,
                            onSeekTo = viewModel::seekTo,
                            onAddText = { viewModel.addTextOverlay() },
                            onUpdateText = viewModel::updateTextOverlay,
                            onDeleteText = viewModel::deleteTextOverlay,
                            onAddAudioClick = { addAudioLauncher.launch(arrayOf("audio/*")) },
                            onUpdateClipAudio = viewModel::updateClipAudio,
                            onUpdateAudioTrack = viewModel::updateAudioTrack,
                            onDeleteAudioTrack = viewModel::deleteAudioTrack,
                            onUpdateTransition = viewModel::updateClipTransition,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PreviewViewport(
                            project = currentProject,
                            player = viewModel.compositionPlayer,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            selectedTextId = selectedTextId,
                            compareBeforeAfter = compareBeforeAfter,
                            showGrid = showGrid,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onToggleCompare = viewModel::toggleCompareBeforeAfter,
                            onToggleGrid = viewModel::toggleGridOverlay,
                            onSelectText = viewModel::selectTextOverlay,
                            onMoveText = { item, nx, ny ->
                                viewModel.updateTextOverlay(item.copy(xFraction = nx, yFraction = ny))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimelineView(
                            project = currentProject,
                            selectedClipId = selectedClipId,
                            selectedTextId = selectedTextId,
                            selectedAudioId = selectedAudioId,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            snapEnabled = snapEnabled,
                            filmstrips = filmstrips,
                            onScrubTo = viewModel::seekTo,
                            onSelectClip = viewModel::selectClip,
                            onSelectTransition = { clipId ->
                                viewModel.selectClip(clipId)
                                viewModel.selectTab(com.example.domain.model.EditorToolTab.TRANSITION)
                            },
                            onSelectText = viewModel::selectTextOverlay,
                            onSelectAudio = viewModel::selectAudioTrack,
                            onSplitAtPlayhead = viewModel::splitSelectedClipAtPlayhead,
                            onToggleSnap = viewModel::toggleSnap,
                            onAddClipClick = {
                                addClipLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            onAddAudioClick = { addAudioLauncher.launch(arrayOf("audio/*")) },
                            onTrimClipEdge = { clip, s, e ->
                                viewModel.selectClip(clip.id)
                                viewModel.trimSelectedClip(s, e)
                            }
                        )
                        InspectorDock(
                            activeTab = activeTab,
                            project = currentProject,
                            selectedClip = selectedClip,
                            selectedText = selectedText,
                            selectedAudio = selectedAudio,
                            playheadMs = playheadMs,
                            clipStartMs = selectedClipStartMs,
                            onSelectTab = viewModel::selectTab,
                            onSplit = viewModel::splitSelectedClipAtPlayhead,
                            onRotate = {
                                selectedClip?.let {
                                    viewModel.updateClipGeometry(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
                                }
                            },
                            onFlip = {
                                selectedClip?.let {
                                    viewModel.updateClipGeometry(flipHorizontal = !it.flipHorizontal)
                                }
                            },
                            onDuplicate = viewModel::duplicateSelectedClip,
                            onDeleteClip = viewModel::deleteSelectedClip,
                            onMoveClip = viewModel::moveSelectedClip,
                            onScaleClip = { viewModel.updateClipGeometry(scale = it) },
                            onSpeedChange = viewModel::updateClipSpeed,
                            onColorGradeChange = viewModel::updateClipColorGrade,
                            onAddKeyframe = viewModel::addOrUpdateKeyframe,
                            onRemoveKeyframe = viewModel::removeKeyframe,
                            onSeekTo = viewModel::seekTo,
                            onAddText = { viewModel.addTextOverlay() },
                            onUpdateText = viewModel::updateTextOverlay,
                            onDeleteText = viewModel::deleteTextOverlay,
                            onAddAudioClick = { addAudioLauncher.launch(arrayOf("audio/*")) },
                            onUpdateClipAudio = viewModel::updateClipAudio,
                            onUpdateAudioTrack = viewModel::updateAudioTrack,
                            onDeleteAudioTrack = viewModel::deleteAudioTrack,
                            onUpdateTransition = viewModel::updateClipTransition
                        )
                    }
                }
            }
        }

        if (showExportSheet) {
            ExportSheet(
                project = currentProject,
                settings = settings,
                exportState = exportState,
                onDismiss = {
                    viewModel.resetExportState()
                    showExportSheet = false
                },
                onStartExport = viewModel::startExport,
                onCancelExport = viewModel::cancelExport
            )
        }
    }
}

@Composable
private fun EditorTopHeader(
    title: String,
    aspectRatio: AspectRatioPreset,
    targetFps: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onAspectChange: (AspectRatioPreset) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExportClick: () -> Unit
) {
    var ratioMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = ConsoleSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "К проектам",
                        tint = TextSecondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { ratioMenuOpen = true }
                        ) {
                            Text(
                                text = "${aspectRatio.badge} • ${targetFps}fps ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold
                            )
                        }
                        DropdownMenu(
                            expanded = ratioMenuOpen,
                            onDismissRequest = { ratioMenuOpen = false },
                            containerColor = ElevatedConsole
                        ) {
                            AspectRatioPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text("${preset.badge} — ${preset.label}") },
                                    onClick = {
                                        ratioMenuOpen = false
                                        onAspectChange(preset)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Отменить",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else TextMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Повторить",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else TextMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("export_button")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Экспорт",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
