package com.example.data

import android.content.Context
import android.os.Build
import com.example.R
import com.example.model.ColorFilterConfig
import com.example.model.ProjectActivity
import com.example.model.VideoClip
import com.example.model.VideoProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ProjectRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun currentDeviceLabel(): String {
        val name = auth.currentUser?.displayName?.split(" ")?.firstOrNull()?.take(18)
            ?.ifBlank { "Creator" } ?: "Creator"
        val model = (Build.MODEL ?: "Android").replace(Regex("[^a-zA-Z0-9 _\\-]"), "").take(20)
        return "$name ($model)".take(48)
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Authenticated user mismatch." }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val displayName = (user.displayName ?: "Isaaking Creator").ifBlank { "Isaaking Creator" }.take(60)
            val deviceName = (Build.MODEL ?: "Android Studio Device").take(60)
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to (user.email ?: "").take(120),
                    "photoUrl" to (user.photoUrl?.toString() ?: "").take(500),
                    "deviceName" to deviceName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to displayName,
                        "photoUrl" to (user.photoUrl?.toString() ?: "").take(500),
                        "deviceName" to deviceName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeProjects(userId: String): Flow<List<VideoProject>> = flow {
        require(userId.isNotBlank()) { "User ID required to observe projects." }
        val path = "projects"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc -> parseProjectSnapshot(doc) }
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProject(projectId: String, userId: String): Flow<VideoProject?> = flow {
        require(projectId.isNotBlank() && userId.isNotBlank()) { "Project ID and User ID required." }
        val path = "projects/$projectId"
        emitAll(
            db.collection("projects").document(projectId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null else parseProjectSnapshot(snapshot)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjectActivity(projectId: String, userId: String): Flow<List<ProjectActivity>> = flow {
        require(projectId.isNotBlank() && userId.isNotBlank()) { "Project ID and User ID required." }
        val path = "projects/$projectId/activity"
        emitAll(
            db.collection("projects").document(projectId).collection("activity")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProjectActivity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                        .take(25)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(
        title: String,
        aspectRatio: String = "9:16",
        initialClips: List<VideoClip>? = null
    ): Result<String> {
        val projectId = "proj_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "projects/$projectId"
        return try {
            val uid = requireUserId()
            val ownerName = (auth.currentUser?.displayName ?: "Isaaking Creator").ifBlank { "Isaaking Creator" }.take(60)
            val safeTitle = title.trim().ifBlank { "New Studio Reel" }.take(80)
            val inviteCode = "ISK${UUID.randomUUID().toString().replace("-", "").uppercase().take(6)}"

            val clips = initialClips?.takeIf { it.isNotEmpty() } ?: listOf(
                VideoClip(
                    clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                    label = "Neon Cybercity",
                    sourceType = VideoClip.SOURCE_SAMPLE_CYBERCITY,
                    totalDurationMs = 5000L,
                    trimStartMs = 0L,
                    trimEndMs = 4500L,
                    speed = 1.0,
                    volume = 1.0,
                    transitionOutId = "dissolve",
                    transitionDurationMs = 700L,
                    orderIndex = 0
                ),
                VideoClip(
                    clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                    label = "Alpine Sunrise",
                    sourceType = VideoClip.SOURCE_SAMPLE_ALPINE,
                    totalDurationMs = 5000L,
                    trimStartMs = 0L,
                    trimEndMs = 4500L,
                    speed = 1.0,
                    volume = 1.0,
                    transitionOutId = "none",
                    transitionDurationMs = 500L,
                    orderIndex = 1
                )
            )
            val totalDurationMs = clips.sumOf { it.effectiveDurationMs }
            val editorTag = currentDeviceLabel()

            val payload = mapOf(
                "ownerId" to uid,
                "ownerName" to ownerName,
                "title" to safeTitle,
                "aspectRatio" to aspectRatio,
                "resolution" to "1080p",
                "fps" to 60,
                "inviteCode" to inviteCode,
                "memberUids" to listOf(uid),
                "activeEditors" to listOf(editorTag),
                "clips" to clips.map { it.toMap() },
                "captions" to emptyList<Map<String, Any>>(),
                "textOverlays" to emptyList<Map<String, Any>>(),
                "totalDurationMs" to totalDurationMs,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("projects").document(projectId).set(payload).await()

            val invitePayload = mapOf(
                "projectId" to projectId,
                "ownerId" to uid,
                "projectTitle" to safeTitle,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("project_invites").document(inviteCode).set(invitePayload).await()

            logActivity(
                projectId = projectId,
                memberUids = listOf(uid),
                actionSummary = "Created project \"$safeTitle\""
            )

            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveProjectEdits(
        project: VideoProject,
        actionSummary: String
    ): Result<Unit> {
        val path = "projects/${project.id}"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Project ID cannot be blank." }
            val safeTitle = project.title.trim().ifBlank { "Untitled Edit" }.take(80)
            val editorTag = currentDeviceLabel()
            val updatedEditors = (listOf(editorTag) + project.activeEditors.filter { it != editorTag }).take(10)
            val computedDuration = project.computedDurationMs().coerceIn(0L, 3_600_000L)

            val updates = mutableMapOf<String, Any>(
                "title" to safeTitle,
                "aspectRatio" to project.aspectRatio,
                "resolution" to project.resolution,
                "fps" to project.fps,
                "activeEditors" to updatedEditors,
                "clips" to project.clips.take(20).mapIndexed { index, clip ->
                    clip.copy(orderIndex = index).toMap()
                },
                "captions" to project.captions.take(40).map { it.toMap() },
                "textOverlays" to project.textOverlays.take(20).map { it.toMap() },
                "musicTrack" to (project.musicTrack?.toMap() ?: FieldValue.delete()),
                "globalFilter" to (project.globalFilter?.toMap() ?: FieldValue.delete()),
                "totalDurationMs" to computedDuration,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("projects").document(project.id).update(updates).await()

            val members = if (uid in project.memberUids) project.memberUids else project.memberUids + uid
            if (actionSummary.isNotBlank()) {
                logActivity(project.id, members, actionSummary)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun ensureInviteActive(project: VideoProject): Result<String> {
        val code = project.inviteCode.trim().uppercase()
        val path = "project_invites/$code"
        return try {
            val uid = requireUserId()
            require(code.length >= 6) { "Invalid invite code." }
            val ref = db.collection("project_invites").document(code)
            val snap = ref.get().await()
            if (!snap.exists() && project.ownerId == uid) {
                ref.set(
                    mapOf(
                        "projectId" to project.id,
                        "ownerId" to uid,
                        "projectTitle" to project.title.take(80),
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun joinProjectByInviteCode(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")
        if (code.length < 6) {
            return Result.failure(IllegalArgumentException("Enter a valid 6+ character collab code."))
        }
        val invitePath = "project_invites/$code"
        return try {
            val uid = requireUserId()
            val inviteSnap = db.collection("project_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invite code \"$code\" was not found."))
            }
            val active = inviteSnap.getBoolean("active") ?: false
            val projectId = inviteSnap.getString("projectId") ?: ""
            if (!active || projectId.isBlank()) {
                return Result.failure(IllegalStateException("This collaborative invite is no longer active."))
            }

            val editorTag = currentDeviceLabel()
            db.collection("projects").document(projectId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "activeEditors" to FieldValue.arrayUnion(editorTag),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val updatedSnap = db.collection("projects").document(projectId).get().await()
            val parsed = parseProjectSnapshot(updatedSnap)
            val memberUids = parsed?.memberUids ?: listOf(uid)
            logActivity(projectId, memberUids, "Joined collaborative session from ${Build.MODEL}")

            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invitePath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String, inviteCode: String): Result<Unit> {
        val path = "projects/$projectId"
        return try {
            requireUserId()
            db.collection("projects").document(projectId).delete().await()
            if (inviteCode.isNotBlank()) {
                runCatching {
                    db.collection("project_invites").document(inviteCode).delete().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun logActivity(
        projectId: String,
        memberUids: List<String>,
        actionSummary: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val activityId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val actorName = (auth.currentUser?.displayName ?: "Creator").ifBlank { "Creator" }.take(60)
        val deviceName = (Build.MODEL ?: "Android").take(60)
        val payload = mapOf(
            "projectId" to projectId,
            "actorUid" to uid,
            "actorName" to actorName,
            "deviceName" to deviceName,
            "actionSummary" to actionSummary.take(160),
            "memberUids" to memberUids.take(10),
            "createdAt" to FieldValue.serverTimestamp()
        )
        runCatching {
            db.collection("projects").document(projectId)
                .collection("activity").document(activityId)
                .set(payload)
                .await()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseProjectSnapshot(doc: DocumentSnapshot): VideoProject? {
        val data = doc.data ?: return null
        val clipsRaw = (data["clips"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val captionsRaw = (data["captions"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val overlaysRaw = (data["textOverlays"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val musicMap = data["musicTrack"] as? Map<String, Any?>
        val filterMap = data["globalFilter"] as? Map<String, Any?>
        val memberUids = (data["memberUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val activeEditors = (data["activeEditors"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        return VideoProject(
            id = doc.id,
            ownerId = data["ownerId"] as? String ?: "",
            ownerName = data["ownerName"] as? String ?: "Creator",
            title = data["title"] as? String ?: "Untitled Edit",
            aspectRatio = data["aspectRatio"] as? String ?: "9:16",
            resolution = data["resolution"] as? String ?: "1080p",
            fps = (data["fps"] as? Number)?.toInt() ?: 60,
            inviteCode = data["inviteCode"] as? String ?: "",
            memberUids = memberUids,
            activeEditors = activeEditors,
            clips = clipsRaw.map { VideoClip.fromMap(it) }.sortedBy { it.orderIndex },
            captions = captionsRaw.map { com.example.model.CaptionSegment.fromMap(it) },
            textOverlays = overlaysRaw.map { com.example.model.TextOverlayItem.fromMap(it) },
            musicTrack = musicMap?.let { com.example.model.MusicTrackConfig.fromMap(it) },
            globalFilter = filterMap?.let { ColorFilterConfig.fromMap(it) },
            totalDurationMs = (data["totalDurationMs"] as? Number)?.toLong() ?: 0L,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }
}
