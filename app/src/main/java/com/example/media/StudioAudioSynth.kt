package com.example.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.MusicTrackConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Procedural real-time PCM synthesizer for the built-in royalty-free Studio Music Library.
 * Generates genre-authentic rhythmic bass, chords, and hi-hat pulses without external URLs.
 */
class StudioAudioSynth {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var currentVolume: Float = 0.75f

    @Volatile
    var isPlaying: Boolean = false
        private set

    fun start(track: MusicTrackConfig, startOffsetMs: Long = 0L) {
        stop()
        currentVolume = track.volume.toFloat().coerceIn(0f, 1f)
        isPlaying = true

        playbackJob = scope.launch {
            val sampleRate = 22050
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val audio = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = audio
            runCatching { audio.play() }

            val chunkFrames = 1024
            val pcm = ShortArray(chunkFrames)
            val beatSec = 60.0 / track.bpm.coerceIn(60, 180)
            val rootFreq = midiToFreq(track.rootMidiNote)
            val fifthFreq = midiToFreq(track.rootMidiNote + 7)
            val minorThirdFreq = midiToFreq(track.rootMidiNote + 3)
            val octaveDownFreq = rootFreq * 0.5

            var sampleIndex = ((startOffsetMs.coerceAtLeast(0L) / 1000.0) * sampleRate).toLong()
            var noiseSeed = 1337

            try {
                while (isActive && isPlaying) {
                    for (i in 0 until chunkFrames) {
                        val t = sampleIndex.toDouble() / sampleRate
                        val beatPos = t / beatSec
                        val beatFrac = beatPos - kotlin.math.floor(beatPos)
                        val barStep = (beatPos.toInt() % 4)

                        // Warm sub-bass & synth pad
                        val kickEnv = exp(-beatFrac * 9.0)
                        val kickFreq = 50.0 + 110.0 * exp(-beatFrac * 26.0)
                        val kick = sin(2.0 * PI * kickFreq * beatFrac * beatSec) * kickEnv * 0.45

                        val chordMul = when (barStep) {
                            0 -> 1.0
                            1 -> 1.1892 // minor 3rd
                            2 -> 1.4983 // 5th
                            else -> 1.3348 // 4th
                        }
                        val padEnv = 0.55 + 0.45 * sin(2.0 * PI * (beatFrac * 0.5))
                        val bass = sin(2.0 * PI * octaveDownFreq * chordMul * t) * 0.22
                        val pad = (
                            sin(2.0 * PI * rootFreq * chordMul * t) +
                                0.6 * sin(2.0 * PI * minorThirdFreq * chordMul * t) +
                                0.5 * sin(2.0 * PI * fifthFreq * chordMul * t)
                            ) * 0.14 * padEnv

                        // Subtle hi-hat / texture on off-beats
                        noiseSeed = (noiseSeed * 1103515245 + 12345) and 0x7fffffff
                        val white = (noiseSeed.toDouble() / 0x7fffffff) * 2.0 - 1.0
                        val offBeatFrac = (beatPos * 2.0) - kotlin.math.floor(beatPos * 2.0)
                        val hatEnv = if (track.genre.contains("Ambient", ignoreCase = true)) 0.0 else exp(-offBeatFrac * 22.0) * 0.08

                        val mixed = ((kick + bass + pad + white * hatEnv) * currentVolume)
                            .coerceIn(-0.95, 0.95)
                        pcm[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
                        sampleIndex++
                    }
                    audio.write(pcm, 0, chunkFrames)
                }
            } catch (_: Exception) {
                // AudioTrack released during stop
            }
        }
    }

    fun updateVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.let { track ->
            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
        }
        audioTrack = null
    }

    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
}
