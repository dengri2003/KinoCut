package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.navigation.EditorRoute
import com.example.ui.navigation.ProjectsRoute
import com.example.ui.projects.ProjectsScreen
import com.example.ui.projects.ProjectsViewModel
import com.example.ui.theme.KinoCutTheme
import com.example.ui.theme.ObsidianBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            KinoCutTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    KinoCutApp()
                }
            }
        }
    }
}

@Composable
fun KinoCutApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ProjectsRoute
    ) {
        composable<ProjectsRoute> {
            val projectsViewModel: ProjectsViewModel = viewModel()
            ProjectsScreen(
                viewModel = projectsViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(EditorRoute(projectId))
                }
            )
        }

        composable<EditorRoute> { backStackEntry ->
            val route: EditorRoute = backStackEntry.toRoute()
            val editorViewModel: EditorViewModel = viewModel()
            EditorScreen(
                projectId = route.projectId,
                viewModel = editorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
