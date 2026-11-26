package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectRepository
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface StudioActionEvent {
    data class OpenProject(val projectId: String) : StudioActionEvent
    data class ShowMessage(val message: String, val isError: Boolean = false) : StudioActionEvent
}

class StudioViewModel(
    private val repository: ProjectRepository,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<UiState<List<VideoProject>>> =
        repository.observeProjects(currentUserId)
            .map<List<VideoProject>, UiState<List<VideoProject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing projects", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load cloud projects"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionEvent = MutableStateFlow<StudioActionEvent?>(null)
    val actionEvent: StateFlow<StudioActionEvent?> = _actionEvent.asStateFlow()

    fun consumeActionEvent() {
        _actionEvent.value = null
    }

    fun createProject(
        title: String,
        aspectRatio: String,
        initialClips: List<VideoClip>? = null
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createProject(title, aspectRatio, initialClips)
            _isBusy.value = false
            result.fold(
                onSuccess = { projectId ->
                    _actionEvent.value = StudioActionEvent.OpenProject(projectId)
                },
                onFailure = { err ->
                    _actionEvent.value = StudioActionEvent.ShowMessage(
                        err.localizedMessage ?: "Failed to create project",
                        isError = true
                    )
                }
            )
        }
    }

    fun joinCollaborativeProject(inviteCode: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.joinProjectByInviteCode(inviteCode)
            _isBusy.value = false
            result.fold(
                onSuccess = { projectId ->
                    _actionEvent.value = StudioActionEvent.OpenProject(projectId)
                },
                onFailure = { err ->
                    _actionEvent.value = StudioActionEvent.ShowMessage(
                        err.localizedMessage ?: "Could not join project",
                        isError = true
                    )
                }
            )
        }
    }

    fun deleteProject(project: VideoProject) {
        viewModelScope.launch {
            val result = repository.deleteProject(project.id, project.inviteCode)
            result.onFailure { err ->
                _actionEvent.value = StudioActionEvent.ShowMessage(
                    err.localizedMessage ?: "Failed to delete project",
                    isError = true
                )
            }
        }
    }

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
