package com.example.ui.projects

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EditorPreferences
import com.example.data.local.KinoCutDatabase
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioSettings
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportCodec
import com.example.domain.model.ExportResolution
import com.example.domain.model.VideoProject
import com.example.media.MediaInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KinoCutDatabase.getInstance(application)
    private val repository = ProjectRepository(db.projectDao())
    private val preferences = EditorPreferences(application)
    private val inspector = MediaInspector(application)

    val projects: StateFlow<List<VideoProject>> = repository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studioSettings: StateFlow<StudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettings())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun createProjectFromMedia(
        uris: List<Uri>,
        aspectRatio: AspectRatioPreset,
        onCreated: (String) -> Unit
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val clips = uris.mapNotNull { inspector.inspectVisualUri(it) }
                if (clips.isNotEmpty()) {
                    val thumbPath = inspector.saveProjectThumbnail(clips.first())
                    val firstWidth = clips.first().width
                    val firstHeight = clips.first().height
                    val inferredRatio = if (uris.size == 1) {
                        if (firstHeight > firstWidth) AspectRatioPreset.RATIO_9_16
                        else if (firstWidth > firstHeight) AspectRatioPreset.RATIO_16_9
                        else AspectRatioPreset.RATIO_1_1
                    } else {
                        aspectRatio
                    }
                    val project = VideoProject(
                        title = clips.first().title.take(24).ifBlank { "Монтаж" },
                        aspectRatio = inferredRatio,
                        targetFps = studioSettings.value.defaultFps,
                        clips = clips,
                        thumbnailPath = thumbPath
                    )
                    repository.saveProject(project)
                    onCreated(project.id)
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun createTitleCardProject(
        title: String,
        subtitle: String,
        aspectRatio: AspectRatioPreset,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val cleanTitle = title.ifBlank { "KinoCut Studio" }
                val cleanSub = subtitle.ifBlank { "PRO TIMELINE • 60 FPS" }
                val clip = inspector.createTitleCardAsset(cleanTitle, cleanSub, aspectRatio)
                val thumbPath = inspector.saveProjectThumbnail(clip)
                val project = VideoProject(
                    title = cleanTitle.take(24),
                    aspectRatio = aspectRatio,
                    targetFps = studioSettings.value.defaultFps,
                    clips = listOf(clip),
                    thumbnailPath = thumbPath
                )
                repository.saveProject(project)
                onCreated(project.id)
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun renameProject(project: VideoProject, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.saveProject(project.copy(title = newTitle.trim()))
        }
    }

    fun duplicateProject(project: VideoProject) {
        viewModelScope.launch {
            repository.duplicateProject(project)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun updateSnapToGrid(enabled: Boolean) {
        viewModelScope.launch { preferences.setSnapToGrid(enabled) }
    }

    fun updateRuleOfThirds(enabled: Boolean) {
        viewModelScope.launch { preferences.setRuleOfThirds(enabled) }
    }

    fun updateExportDefaults(res: ExportResolution, fps: Int, codec: ExportCodec) {
        viewModelScope.launch { preferences.setExportDefaults(res, fps, codec) }
    }
}
