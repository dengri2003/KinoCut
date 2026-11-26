package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object ProjectsRoute

@Serializable
data class EditorRoute(val projectId: String)
