package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val deviceName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoClip(
    val clipId: String = "",
    val label: String = "Clip",
    val sourceType: String = SOURCE_SAMPLE_CYBERCITY,
    val localUri: String = "",
    val totalDurationMs: Long = 5000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 5000L,
    val speed: Double = 1.0,
    val volume: Double = 1.0,
    val reversed: Boolean = false,
    val transitionOutId: String = "dissolve",
    val transitionDurationMs: Long = 700L,
    val orderIndex: Int = 0
) {
    val effectiveDurationMs: Long
        get() = (((trimEndMs - trimStartMs).coerceAtLeast(300L)) / speed.coerceIn(0.25, 4.0)).toLong()

    fun toMap(): Map<String, Any> = mapOf(
        "clipId" to clipId,
        "label" to label.take(60),
        "sourceType" to sourceType,
        "localUri" to localUri,
        "totalDurationMs" to totalDurationMs,
        "trimStartMs" to trimStartMs,
        "trimEndMs" to trimEndMs,
        "speed" to speed,
        "volume" to volume,
        "reversed" to reversed,
        "transitionOutId" to transitionOutId,
        "transitionDurationMs" to transitionDurationMs,
        "orderIndex" to orderIndex
    )

    companion object {
        const val SOURCE_SAMPLE_CYBERCITY = "sample_cybercity"
        const val SOURCE_SAMPLE_ALPINE = "sample_alpine"
        const val SOURCE_DEVICE_MEDIA = "device_media"

        fun fromMap(data: Map<String, Any?>): VideoClip = VideoClip(
            clipId = data["clipId"] as? String ?: "",
            label = data["label"] as? String ?: "Clip",
            sourceType = data["sourceType"] as? String ?: SOURCE_SAMPLE_CYBERCITY,
            localUri = data["localUri"] as? String ?: "",
            totalDurationMs = (data["totalDurationMs"] as? Number)?.toLong() ?: 5000L,
            trimStartMs = (data["trimStartMs"] as? Number)?.toLong() ?: 0L,
            trimEndMs = (data["trimEndMs"] as? Number)?.toLong() ?: 5000L,
            speed = (data["speed"] as? Number)?.toDouble() ?: 1.0,
            volume = (data["volume"] as? Number)?.toDouble() ?: 1.0,
            reversed = data["reversed"] as? Boolean ?: false,
            transitionOutId = data["transitionOutId"] as? String ?: "dissolve",
            transitionDurationMs = (data["transitionDurationMs"] as? Number)?.toLong() ?: 700L,
            orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: 0
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CaptionSegment(
    val captionId: String = "",
    val text: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 2000L,
    val styleId: String = "hormozi_pop",
    val highlightColorHex: String = "#00E5FF"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "captionId" to captionId,
        "text" to text.take(200),
        "startMs" to startMs,
        "endMs" to endMs,
        "styleId" to styleId,
        "highlightColorHex" to highlightColorHex
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): CaptionSegment = CaptionSegment(
            captionId = data["captionId"] as? String ?: "",
            text = data["text"] as? String ?: "",
            startMs = (data["startMs"] as? Number)?.toLong() ?: 0L,
            endMs = (data["endMs"] as? Number)?.toLong() ?: 2000L,
            styleId = data["styleId"] as? String ?: "hormozi_pop",
            highlightColorHex = data["highlightColorHex"] as? String ?: "#00E5FF"
        )
    }
}

@Keep
@IgnoreExtraProperties
data class TextOverlayItem(
    val overlayId: String = "",
    val text: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val styleId: String = "cinema_title",
    val xNorm: Double = 0.5,
    val yNorm: Double = 0.25,
    val scale: Double = 1.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "overlayId" to overlayId,
        "text" to text.take(120),
        "startMs" to startMs,
        "endMs" to endMs,
        "styleId" to styleId,
        "xNorm" to xNorm,
        "yNorm" to yNorm,
        "scale" to scale
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): TextOverlayItem = TextOverlayItem(
            overlayId = data["overlayId"] as? String ?: "",
            text = data["text"] as? String ?: "",
            startMs = (data["startMs"] as? Number)?.toLong() ?: 0L,
            endMs = (data["endMs"] as? Number)?.toLong() ?: 3000L,
            styleId = data["styleId"] as? String ?: "cinema_title",
            xNorm = (data["xNorm"] as? Number)?.toDouble() ?: 0.5,
            yNorm = (data["yNorm"] as? Number)?.toDouble() ?: 0.25,
            scale = (data["scale"] as? Number)?.toDouble() ?: 1.0
        )
    }
}

@Keep
@IgnoreExtraProperties
data class MusicTrackConfig(
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val genre: String = "",
    val bpm: Int = 120,
    val durationMs: Long = 30000L,
    val rootMidiNote: Int = 60,
    val volume: Double = 0.75,
    val fadeInMs: Long = 500L,
    val fadeOutMs: Long = 800L,
    val startOffsetMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "trackId" to trackId,
        "title" to title,
        "artist" to artist,
        "genre" to genre,
        "bpm" to bpm,
        "durationMs" to durationMs,
        "rootMidiNote" to rootMidiNote,
        "volume" to volume,
        "fadeInMs" to fadeInMs,
        "fadeOutMs" to fadeOutMs,
        "startOffsetMs" to startOffsetMs
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): MusicTrackConfig = MusicTrackConfig(
            trackId = data["trackId"] as? String ?: "",
            title = data["title"] as? String ?: "",
            artist = data["artist"] as? String ?: "",
            genre = data["genre"] as? String ?: "",
            bpm = (data["bpm"] as? Number)?.toInt() ?: 120,
            durationMs = (data["durationMs"] as? Number)?.toLong() ?: 30000L,
            rootMidiNote = (data["rootMidiNote"] as? Number)?.toInt() ?: 60,
            volume = (data["volume"] as? Number)?.toDouble() ?: 0.75,
            fadeInMs = (data["fadeInMs"] as? Number)?.toLong() ?: 500L,
            fadeOutMs = (data["fadeOutMs"] as? Number)?.toLong() ?: 800L,
            startOffsetMs = (data["startOffsetMs"] as? Number)?.toLong() ?: 0L
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ColorFilterConfig(
    val filterId: String = "original",
    val name: String = "Original",
    val prompt: String = "",
    val brightness: Double = 0.0,
    val contrast: Double = 1.0,
    val saturation: Double = 1.0,
    val temperature: Double = 0.0,
    val tintHex: String = "#000000",
    val tintAlpha: Double = 0.0,
    val vignette: Double = 0.0,
    val aiGenerated: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "filterId" to filterId,
        "name" to name.take(40),
        "prompt" to prompt.take(120),
        "brightness" to brightness,
        "contrast" to contrast,
        "saturation" to saturation,
        "temperature" to temperature,
        "tintHex" to tintHex,
        "tintAlpha" to tintAlpha,
        "vignette" to vignette,
        "aiGenerated" to aiGenerated
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): ColorFilterConfig = ColorFilterConfig(
            filterId = data["filterId"] as? String ?: "original",
            name = data["name"] as? String ?: "Original",
            prompt = data["prompt"] as? String ?: "",
            brightness = (data["brightness"] as? Number)?.toDouble() ?: 0.0,
            contrast = (data["contrast"] as? Number)?.toDouble() ?: 1.0,
            saturation = (data["saturation"] as? Number)?.toDouble() ?: 1.0,
            temperature = (data["temperature"] as? Number)?.toDouble() ?: 0.0,
            tintHex = data["tintHex"] as? String ?: "#000000",
            tintAlpha = (data["tintAlpha"] as? Number)?.toDouble() ?: 0.0,
            vignette = (data["vignette"] as? Number)?.toDouble() ?: 0.0,
            aiGenerated = data["aiGenerated"] as? Boolean ?: false
        )
    }
}

@Keep
@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val title: String = "Untitled Edit",
    val aspectRatio: String = "9:16",
    val resolution: String = "1080p",
    val fps: Int = 60,
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    val activeEditors: List<String> = emptyList(),
    val clips: List<VideoClip> = emptyList(),
    val captions: List<CaptionSegment> = emptyList(),
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val musicTrack: MusicTrackConfig? = null,
    val globalFilter: ColorFilterConfig? = null,
    val totalDurationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun computedDurationMs(): Long = clips.sumOf { it.effectiveDurationMs }.coerceAtLeast(0L)
}

@Keep
@IgnoreExtraProperties
data class ProjectActivity(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val deviceName: String = "",
    val actionSummary: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProjectInvite(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val ownerId: String = "",
    val projectTitle: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class TransitionPreset(
    val id: String,
    val name: String,
    val category: String,
    val description: String
)

data class CaptionStylePreset(
    val id: String,
    val name: String,
    val accentHex: String
)

object StudioCatalog {
    val aspectRatios = listOf("9:16", "16:9", "1:1", "4:5")
    val resolutions = listOf("720p", "1080p", "4K")
    val frameRates = listOf(24, 30, 60)

    val transitions = listOf(
        TransitionPreset("none", "Cut", "Basic", "Hard frame cut"),
        TransitionPreset("dissolve", "Cross Dissolve", "Cinema", "Smooth optical crossfade"),
        TransitionPreset("whip_pan", "Whip Pan", "Camera", "High-velocity horizontal motion swipe"),
        TransitionPreset("glitch_warp", "Glitch Warp", "FX", "Chromatic RGB split pulse"),
        TransitionPreset("zoom_blur", "Zoom Punch", "Camera", "Dynamic scale-in focal burst"),
        TransitionPreset("film_burn", "Film Burn", "Cinema", "Warm amber analog light flare"),
        TransitionPreset("light_leak", "Cyan Flash", "FX", "Anamorphic cyan exposure flash"),
        TransitionPreset("radial_spin", "Radial Spin", "Camera", "360-degree kinetic twist")
    )

    val presetFilters = listOf(
        ColorFilterConfig("original", "Original", "Neutral untouched footage", 0.0, 1.0, 1.0, 0.0, "#000000", 0.0, 0.0, false),
        ColorFilterConfig("teal_orange", "Blockbuster", "Teal shadows & golden highlights", 0.02, 1.24, 1.18, 0.22, "#00B8D4", 0.08, 0.28, false),
        ColorFilterConfig("cyber_neon", "Neon Rain", "Vibrant cyan & magenta night glow", -0.04, 1.32, 1.35, -0.35, "#00E5FF", 0.12, 0.35, false),
        ColorFilterConfig("kodak_gold", "Analog 70s", "Warm vintage film grain & fade", 0.05, 1.08, 0.92, 0.42, "#FFB300", 0.11, 0.22, false),
        ColorFilterConfig("noir_silver", "Noir 35mm", "High-contrast monochrome silver", -0.05, 1.48, 0.05, -0.1, "#FFFFFF", 0.0, 0.45, false),
        ColorFilterConfig("arctic_clean", "Arctic Crisp", "Cool editorial clarity & soft lift", 0.06, 1.12, 0.88, -0.32, "#7AE6FF", 0.07, 0.10, false)
    )

    val captionStyles = listOf(
        CaptionStylePreset("hormozi_pop", "Bold Pop", "#00E5FF"),
        CaptionStylePreset("karaoke_gold", "Karaoke Gold", "#FFB300"),
        CaptionStylePreset("neon_cyber", "Neon Outline", "#FF4D6D"),
        CaptionStylePreset("cinema_serif", "Cinema Clean", "#FFFFFF")
    )

    val musicLibrary = listOf(
        MusicTrackConfig("trk_neon_pulse", "Midnight Telemetry", "KAIRO", "Cyberpunk Synth", 124, 32000L, 57),
        MusicTrackConfig("trk_lofi_rain", "Velvet Espresso", "Milo Beats", "Lo-Fi Chill", 84, 36000L, 60),
        MusicTrackConfig("trk_epic_rise", "Horizon Protocol", "Astra Score", "Cinematic", 96, 40000L, 50),
        MusicTrackConfig("trk_trap_808", "Vortex 808", "Zay Prod", "Trap / Hip-Hop", 140, 28000L, 53),
        MusicTrackConfig("trk_afro_sun", "Lagos Sunset", "Temson", "Afrobeats", 108, 34000L, 62),
        MusicTrackConfig("trk_future_bass", "Prism Sky", "NovaWave", "Future Bass", 128, 30000L, 64),
        MusicTrackConfig("trk_ambient_drift", "Alpine Mist", "Soren", "Ambient", 74, 42000L, 55),
        MusicTrackConfig("trk_indie_drive", "Coastline Tape", "The Pines", "Indie Pop", 116, 32000L, 65)
    )
}
