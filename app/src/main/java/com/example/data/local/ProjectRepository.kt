package com.example.data.local

import com.example.domain.model.VideoProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ProjectRepository(private val projectDao: ProjectDao) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val projects: Flow<List<VideoProject>> = projectDao.observeAllProjects().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { json.decodeFromString<VideoProject>(entity.projectJson) }.getOrNull()
        }
    }

    fun observeProject(id: String): Flow<VideoProject?> = projectDao.observeProjectById(id).map { entity ->
        entity?.let { runCatching { json.decodeFromString<VideoProject>(it.projectJson) }.getOrNull() }
    }

    suspend fun getProject(id: String): VideoProject? {
        val entity = projectDao.getProjectById(id) ?: return null
        return runCatching { json.decodeFromString<VideoProject>(entity.projectJson) }.getOrNull()
    }

    suspend fun saveProject(project: VideoProject) {
        val updated = project.copy(updatedAtMs = System.currentTimeMillis())
        val encoded = json.encodeToString(updated)
        projectDao.upsertProject(
            ProjectEntity(
                id = updated.id,
                title = updated.title,
                updatedAtMs = updated.updatedAtMs,
                createdAtMs = updated.createdAtMs,
                durationMs = updated.totalDurationMs,
                clipCount = updated.clips.size,
                aspectBadge = updated.aspectRatio.badge,
                thumbnailPath = updated.thumbnailPath,
                projectJson = encoded
            )
        )
    }

    suspend fun duplicateProject(project: VideoProject): VideoProject {
        val now = System.currentTimeMillis()
        val copy = project.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${project.title} (Копия)",
            createdAtMs = now,
            updatedAtMs = now
        )
        saveProject(copy)
        return copy
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }
}
