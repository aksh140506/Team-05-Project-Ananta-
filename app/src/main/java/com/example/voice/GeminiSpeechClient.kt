package com.example.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Log
import com.example.data.model.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

private data class CachedSpeechClip(
    val pcmBytes: ByteArray,
    val sampleRate: Int
)

/**
 * Zero-API-key cloud neural Text-to-Speech powered by Firebase AI Logic (`gemini-3.1-flash-tts-preview`).
 * Uses App Check protection without requiring Bhashini or external TTS keys, and supports Dogri (Devanagari),
 * Hindi, and Indian English in a warm female voice with immediate cancellation on screen changes.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiSpeechClient {

    private val ttsModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            speechConfig = SpeechConfig(
                voice = Voice(VOICE_NAME)
            )
        }
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config
        )
    }

    private val clipCacheLock = Any()
    private val clipCache = object : LinkedHashMap<String, CachedSpeechClip>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSpeechClip>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    @Volatile
    private var activeTrack: AudioTrack? = null

    suspend fun synthesizeAndPlayTts(
        text: String,
        language: AppLanguage,
        speechRate: Float = 0.95f,
        shouldContinue: () -> Boolean = { true }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank() || !shouldContinue()) return@withContext Result.success(Unit)
            val cacheKey = "${language.code}|${text.take(MAX_TTS_CHARS)}"
            val cached = synchronized(clipCacheLock) { clipCache[cacheKey] }
            if (cached != null) {
                playFullPcmClip(cached.pcmBytes, cached.sampleRate, speechRate, shouldContinue)
                return@withContext Result.success(Unit)
            }

            val prompt = buildSpeechPrompt(text, language)
            val recordedPcm = ByteArrayOutputStream()
            var detectedSampleRate = DEFAULT_SAMPLE_RATE
            var track: AudioTrack? = null
            var totalFramesWritten = 0L

            withTimeout(STREAM_TIMEOUT_MS) {
                ttsModel.generateContentStream(prompt).collect { chunk ->
                    if (!shouldContinue()) {
                        throw CancellationException("Speech generation stopped by user or navigation")
                    }
                    val audioParts = buildList {
                        addAll(chunk.inlineDataParts)
                        if (isEmpty()) {
                            chunk.candidates.firstOrNull()?.content?.parts
                                ?.filterIsInstance<InlineDataPart>()
                                ?.let(::addAll)
                        }
                    }

                    for (part in audioParts) {
                        if (!shouldContinue()) break
                        val rawBytes = part.inlineData
                        if (rawBytes.isEmpty()) continue

                        val parsedRate = parseSampleRateFromMimeType(part.mimeType)
                        val cleanPcm = stripWavHeaderIfPresent(rawBytes)
                        if (cleanPcm.isEmpty()) continue

                        if (track == null) {
                            detectedSampleRate = parsedRate
                            track = createStreamingTrack(detectedSampleRate, speechRate)
                            activeTrack = track
                            track?.play()
                        }

                        if (recordedPcm.size() + cleanPcm.size <= MAX_CACHED_CLIP_BYTES) {
                            recordedPcm.write(cleanPcm)
                        }

                        val active = track ?: continue
                        val writtenBytes = writeChunkInterruptibly(active, cleanPcm, shouldContinue)
                        totalFramesWritten += (writtenBytes / BYTES_PER_FRAME)
                    }
                }
            }

            val active = track
            if (active == null || totalFramesWritten <= 0L) {
                stopPlayback()
                return@withContext Result.failure(IllegalStateException("Gemini TTS returned empty audio stream"))
            }

            // Cache short synthesized responses so repeated Sheru tips or replay taps start with 0ms latency
            val fullPcm = recordedPcm.toByteArray()
            if (fullPcm.isNotEmpty() && fullPcm.size <= MAX_CACHED_CLIP_BYTES) {
                synchronized(clipCacheLock) {
                    clipCache[cacheKey] = CachedSpeechClip(fullPcm, detectedSampleRate)
                }
            }

            waitForDrainInterruptibly(active, totalFramesWritten, detectedSampleRate, shouldContinue)
            releaseTrackSafely(active)
            Result.success(Unit)
        } catch (ce: CancellationException) {
            stopPlayback()
            throw ce
        } catch (e: Exception) {
            stopPlayback()
            Log.w(TAG, "Gemini TTS synthesis failed, caller will fall back cleanly", e)
            Result.failure(e)
        }
    }

    fun stopPlayback() {
        val track = activeTrack
        activeTrack = null
        releaseTrackSafely(track)
    }

    private suspend fun playFullPcmClip(
        pcmBytes: ByteArray,
        sampleRate: Int,
        speechRate: Float,
        shouldContinue: () -> Boolean
    ) {
        if (!shouldContinue() || pcmBytes.isEmpty()) return
        val track = createStreamingTrack(sampleRate, speechRate)
        activeTrack = track
        try {
            track.play()
            val writtenBytes = writeChunkInterruptibly(track, pcmBytes, shouldContinue)
            val totalFrames = writtenBytes / BYTES_PER_FRAME.toLong()
            waitForDrainInterruptibly(track, totalFrames, sampleRate, shouldContinue)
        } finally {
            releaseTrackSafely(track)
        }
    }

    private fun createStreamingTrack(sampleRate: Int, speechRate: Float): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        runCatching {
            val clamped = speechRate.coerceIn(0.80f, 1.20f)
            if (clamped != 1.0f) {
                track.playbackParams = PlaybackParams().setSpeed(clamped)
            }
        }
        return track
    }

    private fun writeChunkInterruptibly(
        track: AudioTrack,
        bytes: ByteArray,
        shouldContinue: () -> Boolean
    ): Int {
        var offset = 0
        while (offset < bytes.size && shouldContinue() && activeTrack === track) {
            val step = (bytes.size - offset).coerceAtMost(WRITE_CHUNK_BYTES)
            val written = track.write(bytes, offset, step, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
        return offset
    }

    private suspend fun waitForDrainInterruptibly(
        track: AudioTrack,
        totalFramesWritten: Long,
        sampleRate: Int,
        shouldContinue: () -> Boolean
    ) {
        if (totalFramesWritten <= 0L) return
        val maxWaitMs = ((totalFramesWritten * 1000L) / sampleRate.coerceAtLeast(8000)) + 1500L
        val startMs = System.currentTimeMillis()
        while (shouldContinue() && activeTrack === track) {
            val head = runCatching { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(totalFramesWritten)
            if (head >= totalFramesWritten) break
            if (System.currentTimeMillis() - startMs >= maxWaitMs) break
            delay(45L)
        }
    }

    private fun releaseTrackSafely(track: AudioTrack?) {
        if (track == null) return
        if (activeTrack === track) activeTrack = null
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    companion object {
        private const val TAG = "GeminiSpeechClient"
        const val MODEL_NAME = "gemini-3.1-flash-tts-preview"
        const val VOICE_NAME = "Sulafat" // Warm, natural female voice in Firebase AI Logic TTS
        private const val DEFAULT_SAMPLE_RATE = 24000
        private const val BYTES_PER_FRAME = 2 // 16-bit mono
        private const val WRITE_CHUNK_BYTES = 4096
        private const val STREAM_TIMEOUT_MS = 14_000L
        private const val MAX_TTS_CHARS = 420
        private const val MAX_CACHE_ENTRIES = 10
        private const val MAX_CACHED_CLIP_BYTES = 600_000

        fun buildSpeechPrompt(rawText: String, language: AppLanguage): String {
            val clipped = rawText.trim().take(MAX_TTS_CHARS)
            return when (language) {
                AppLanguage.ENGLISH ->
                    "Speak warmly, gently, and clearly in a pleasant Indian English female voice at a steady conversational pace: $clipped"
                AppLanguage.HINDI ->
                    "Speak warmly, gently, and clearly in a natural Indian Hindi female voice at a calm, helpful pace: $clipped"
                AppLanguage.DOGRI ->
                    "Speak warmly, gently, and clearly in a natural North Indian Dogri and Hindi female voice at a calm, helpful pace: $clipped"
            }
        }

        fun parseSampleRateFromMimeType(mimeType: String?): Int {
            if (mimeType.isNullOrBlank()) return DEFAULT_SAMPLE_RATE
            val match = Regex("""rate\s*=\s*(\d{4,6})""", RegexOption.IGNORE_CASE).find(mimeType)
            val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return DEFAULT_SAMPLE_RATE
            return parsed.coerceIn(8000, 48000)
        }

        fun stripWavHeaderIfPresent(bytes: ByteArray): ByteArray {
            if (bytes.size > 44 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()
            ) {
                return bytes.copyOfRange(44, bytes.size)
            }
            return bytes
        }
    }
}
