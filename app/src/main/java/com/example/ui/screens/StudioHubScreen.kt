package com.example.ui.screens

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.signOut
import com.example.model.StudioCatalog
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.viewmodel.StudioActionEvent
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.UUID

@Composable
fun StudioHubScreen(
    viewModel: StudioViewModel,
    onOpenProject: (String) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val user = Firebase.auth.currentUser

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionEvent by viewModel.actionEvent.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showJoinCollabDialog by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var selectedAspectRatio by remember { mutableStateOf("9:16") }
    var newProjectTitle by remember { mutableStateOf("Cyber Reel Edit") }
    var joinCodeInput by remember { mutableStateOf("") }

    // Android Photo Picker (Zero-Permission Google Play Compliant)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val clips = uris.mapIndexed { index, uri ->
                val durationMs = queryVideoDurationMs(context, uri)
                VideoClip(
                    clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                    label = "Clip ${index + 1}",
                    sourceType = VideoClip.SOURCE_DEVICE_MEDIA,
                    localUri = uri.toString(),
                    totalDurationMs = durationMs,
                    trimStartMs = 0L,
                    trimEndMs = durationMs,
                    transitionOutId = if (index < uris.lastIndex) "dissolve" else "none",
                    orderIndex = index
                )
            }
            viewModel.createProject(
                title = newProjectTitle.ifBlank { "My Video Cut" },
                aspectRatio = selectedAspectRatio,
                initialClips = clips
            )
            showNewProjectDialog = false
        }
    }

    LaunchedEffect(actionEvent) {
        when (val evt = actionEvent) {
            is StudioActionEvent.OpenProject -> {
                viewModel.consumeActionEvent()
                onOpenProject(evt.projectId)
            }
            is StudioActionEvent.ShowMessage -> {
                viewModel.consumeActionEvent()
                snackbarHostState.showSnackbar(evt.message)
            }
            null -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            // Top Studio Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = R.drawable.img_app_icon_fg,
                            contentDescription = "Isaaking Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ISAAKING",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = EmeraldSync,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "CLOUD STUDIO SYNCED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSync
                                )
                            }
                        }
                    }

                    Box {
                        Surface(
                            shape = CircleShape,
                            color = ElevatedCard,
                            border = BorderStroke(1.dp, StudioOutline),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { showAccountMenu = true }
                                .testTag("account_menu_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (user?.photoUrl != null) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = "Profile photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(ElectricCyan)
                                    ) {
                                        Text(
                                            text = (user?.displayName?.firstOrNull() ?: 'I').uppercase(),
                                            color = ObsidianVoid,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = user?.displayName?.split(" ")?.firstOrNull() ?: "Creator",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(user?.email ?: "Signed in with Google") },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text("Sign Out") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                },
                                onClick = {
                                    showAccountMenu = false
                                    signOut(credentialManager, scope, onSignedOut)
                                }
                            )
                        }
                    }
                }
            }

            // CapCut-Inspired Clean Hero Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        AsyncImage(
                            model = R.drawable.img_studio_hero,
                            contentDescription = "Studio editing suite",
                            contentScale = ContentScale.Crop,
                            alpha = 0.42f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            ObsidianVoid.copy(alpha = 0.92f),
                                            ObsidianVoid.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(20.dp)
                        ) {
                            Column {
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "PRO TIMELINE ENGINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Create your next cut",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Multi-track trim, AI grades, auto-captions & live collab",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showNewProjectDialog = true },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianVoid
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("new_project_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Project", style = MaterialTheme.typography.labelLarge)
                                }

                                OutlinedButton(
                                    onClick = { showJoinCollabDialog = true },
                                    enabled = !isBusy,
                                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("join_collab_button")
                                ) {
                                    Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Join Collab", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // Quick Tools Strip (CapCut-style shortcuts)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        QuickToolChip(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "AI Filters",
                            subtitle = "Gemini LUTs",
                            accent = CinemaAmber,
                            onClick = { viewModel.createProject("AI Graded Reel", "9:16") }
                        )
                    }
                    item {
                        QuickToolChip(
                            icon = Icons.Rounded.ClosedCaption,
                            title = "Auto Captions",
                            subtitle = "Timed AI text",
                            accent = NeonViolet,
                            onClick = { viewModel.createProject("Captioned Story", "9:16") }
                        )
                    }
                    item {
                        QuickToolChip(
                            icon = Icons.Rounded.MusicNote,
                            title = "Music Studio",
                            subtitle = "Beat-sync audio",
                            accent = CoralPulse,
                            onClick = { viewModel.createProject("Music Beat Cut", "16:9") }
                        )
                    }
                    item {
                        QuickToolChip(
                            icon = Icons.Rounded.MovieCreation,
                            title = "Cinema 16:9",
                            subtitle = "Widescreen",
                            accent = ElectricCyan,
                            onClick = { viewModel.createProject("Widescreen Cinema", "16:9") }
                        )
                    }
                }
            }

            // Projects Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cloud Projects",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            when (val state = projectsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = CoralPulse.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = CoralPulse,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = StudioSurface,
                                border = BorderStroke(1.dp, StudioOutline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No cloud timelines yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap New Project to start from device clips or our 4K studio starter reel.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.data, key = { it.id }) { project ->
                            ProjectRowCard(
                                project = project,
                                isOwner = project.ownerId == user?.uid,
                                onOpen = { onOpenProject(project.id) },
                                onDelete = { viewModel.deleteProject(project) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            containerColor = StudioSurface,
            title = { Text("New Studio Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newProjectTitle,
                        onValueChange = { newProjectTitle = it },
                        label = { Text("Project Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_project_title_input")
                    )
                    Text(
                        text = "Canvas Aspect Ratio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioCatalog.aspectRatios.forEach { ratio ->
                            val selected = selectedAspectRatio == ratio
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ElectricCyan else ElevatedCard,
                                border = BorderStroke(1.dp, if (selected) ElectricCyan else StudioOutline),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAspectRatio = ratio }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = ratio,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) ObsidianVoid else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("pick_videos_button")
                ) {
                    Text("Pick Device Videos")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewProjectDialog = false
                        viewModel.createProject(newProjectTitle, selectedAspectRatio)
                    },
                    modifier = Modifier.testTag("start_studio_template_button")
                ) {
                    Text("Use Studio Sample Clips", color = CinemaAmber)
                }
            }
        )
    }

    if (showJoinCollabDialog) {
        AlertDialog(
            onDismissRequest = { showJoinCollabDialog = false },
            containerColor = StudioSurface,
            title = { Text("Join Collaborative Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the invite code from another device or collaborator to co-edit their timeline in real time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it.uppercase() },
                        label = { Text("Invite Code (e.g. ISK9F2A1C)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinCollabDialog = false
                        viewModel.joinCollaborativeProject(joinCodeInput)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("confirm_join_collab_button")
                ) {
                    Text("Join Timeline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinCollabDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickToolChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ElevatedCard,
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier
            .width(146.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f))
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ProjectRowCard(
    project: VideoProject,
    isOwner: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbRes = if (project.clips.firstOrNull()?.sourceType == VideoClip.SOURCE_SAMPLE_ALPINE) {
        R.drawable.img_sample_alpine
    } else {
        R.drawable.img_sample_cybercity
    }
    val secs = (project.computedDurationMs() / 1000.0)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ElevatedCard,
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 66.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = thumbRes,
                    contentDescription = project.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ObsidianVoid.copy(alpha = 0.72f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = project.aspectRatio,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${"%.1f".format(secs)}s • ${project.clips.size} clips",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.People,
                        contentDescription = null,
                        tint = EmeraldSync,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Code ${project.inviteCode} • ${project.memberUids.size} collaborator(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldSync,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isOwner) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_project_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete project",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun queryVideoDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        raw?.toLongOrNull()?.coerceIn(1000L, 600_000L) ?: 6000L
    } catch (_: Exception) {
        6000L
    } finally {
        runCatching { retriever.release() }
    }
}
