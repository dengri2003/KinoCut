package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAtMs: Long,
    val createdAtMs: Long,
    val durationMs: Long,
    val clipCount: Int,
    val aspectBadge: String,
    val thumbnailPath: String?,
    val projectJson: String
)
