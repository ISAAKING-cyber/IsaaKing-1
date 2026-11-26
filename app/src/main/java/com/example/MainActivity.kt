package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AiStudioRepository
import com.example.data.ProjectRepository
import com.example.data.authStateFlow
import com.example.data.configureAppCheck
import com.example.media.StudioAudioSynth
import com.example.media.VideoExportManager
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.StudioHubScreen
import com.example.ui.theme.IsaakingTheme
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private object StudioRoutes {
    const val STUDIO_HUB = "studio_hub"
    const val EDITOR = "editor/{projectId}"
    fun editorRoute(projectId: String) = "editor/$projectId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            IsaakingTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStudioNav(
                currentUserId = user.uid,
                onSignedOut = {}
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AuthenticatedStudioNav(
    currentUserId: String,
    onSignedOut: () -> Unit
) {
    val navController = rememberNavController()

    val studioViewModel: StudioViewModel = viewModel(
        key = "studio_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                StudioViewModel(ProjectRepository(db), currentUserId)
            }
        }
    )

    // Ensure user profile doc is synced in Firestore
    LaunchedEffect(currentUserId) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            val context = navController.context.applicationContext
            val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
            ProjectRepository(db).ensureUserProfile(currentUser)
        }
    }

    NavHost(
        navController = navController,
        startDestination = StudioRoutes.STUDIO_HUB
    ) {
        composable(StudioRoutes.STUDIO_HUB) {
            StudioHubScreen(
                viewModel = studioViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(StudioRoutes.editorRoute(projectId))
                },
                onSignedOut = onSignedOut
            )
        }

        composable(
            route = StudioRoutes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val editorViewModel: EditorViewModel = viewModel(
                key = "editor_${currentUserId}_$projectId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                        EditorViewModel(
                            projectRepository = ProjectRepository(db),
                            aiRepository = AiStudioRepository(),
                            audioSynth = StudioAudioSynth(),
                            exportManager = VideoExportManager(app),
                            projectId = projectId,
                            currentUserId = currentUserId
                        )
                    }
                }
            )
            EditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
