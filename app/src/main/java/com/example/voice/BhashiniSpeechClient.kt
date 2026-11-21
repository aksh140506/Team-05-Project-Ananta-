package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class BhashiniPipelineConfig(
    val callbackUrl: String,
    val authHeaderName: String,
    val authHeaderValue: String,
    val asrServiceId: String?,
    val ttsServiceId: String?
)

class BhashiniSpeechClient(
    private val context: Context,
    private val userId: String = BuildConfig.BHASHINI_USER_ID,
    private val ulcaApiKey: String = BuildConfig.BHASHINI_ULCA_API_KEY,
    private val pipelineId: String = BuildConfig.BHASHINI_PIPELINE_ID
) {
    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Supports Hindi (hi), Dogri (doi), and English (en) pipeline caching
    private val pipelineCache = ConcurrentHashMap<String, BhashiniPipelineConfig>()
    private val isRecordingFlag = AtomicBoolean(false)

    @Volatile
    private var activePlayer: MediaPlayer? = null

    fun isConfigured(): Boolean =
        isCredentialSet(userId) && isCredentialSet(ulcaApiKey) && isCredentialSet(pipelineId)

    fun isSupportedLanguage(language: AppLanguage): Boolean =
        language == AppLanguage.HINDI || language == AppLanguage.ENGLISH || language == AppLanguage.DOGRI

    suspend fun warmUpPipelines(languages: List<AppLanguage> = AppLanguage.entries) {
        if (!isConfigured()) return
        withContext(Dispatchers.IO) {
            coroutineScope {
                languages.map { lang ->
                    async {
                        runCatching { getOrFetchPipelineConfig(normalizeLangCode(lang)) }
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun record16kHzWavAndTranscribe(
        language: AppLanguage,
        maxDurationMs: Long = 6500L
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Bhashini credentials not configured"))
        }
        val langCode = normalizeLangCode(language)
        val wavBytes = capturePcmAsWav16kHz(maxDurationMs).getOrElse { err ->
            return@withContext Result.failure(err)
        }
        transcribeWavBytes(wavBytes, langCode)
    }

    fun stopRecordingEarly() {
        isRecordingFlag.set(false)
    }

    suspend fun transcribeWavBytes(wavBytes: ByteArray, langCode: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedLang = when (langCode) {
                    "hi", "doi" -> langCode
                    else -> "en"
                }
                val cfg = getOrFetchPipelineConfigOrFallback(normalizedLang)
                val effectiveLang = cfg.first
                val pipelineCfg = cfg.second
                val asrService = pipelineCfg.asrServiceId
                    ?: error("No Bhashini ASR service returned for $effectiveLang")

                val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
                val bodyJson = JSONObject().apply {
                    put("pipelineTasks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("taskType", "asr")
                            put("config", JSONObject().apply {
                                put("language", JSONObject().apply {
                                    put("sourceLanguage", effectiveLang)
                                })
                                put("serviceId", asrService)
                                put("audioFormat", "wav")
                                put("samplingRate", 16000)
                            })
                        })
                    })
                    put("inputData", JSONObject().apply {
                        put("audio", JSONArray().apply {
                            put(JSONObject().apply {
                                put("audioContent", audioBase64)
                            })
                        })
                    })
                }

                val responseJson = postJson(
                    url = pipelineCfg.callbackUrl,
                    body = bodyJson.toString(),
                    headers = mapOf(pipelineCfg.authHeaderName to pipelineCfg.authHeaderValue)
                )
                val transcript = JSONObject(responseJson)
                    .optJSONArray("pipelineResponse")
                    ?.optJSONObject(0)
                    ?.optJSONArray("output")
                    ?.optJSONObject(0)
                    ?.optString("source")
                    ?.trim()
                    .orEmpty()

                if (transcript.isBlank()) error("Bhashini ASR returned empty transcript")
                transcript
            }
        }

    suspend fun synthesizeAndPlayTts(
        text: String,
        language: AppLanguage,
        shouldContinue: () -> Boolean = { true }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured()) error("Bhashini credentials not configured")
            if (text.isBlank() || !shouldContinue()) return@runCatching

            val normalizedLang = normalizeLangCode(language)
            val (effectiveLang, cfg) = getOrFetchPipelineConfigOrFallback(normalizedLang)
            val ttsService = cfg.ttsServiceId
                ?: error("No Bhashini TTS service returned for $effectiveLang")

            val bodyJson = JSONObject().apply {
                put("pipelineTasks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("taskType", "tts")
                        put("config", JSONObject().apply {
                            put("language", JSONObject().apply {
                                put("sourceLanguage", effectiveLang)
                            })
                            put("serviceId", ttsService)
                            put("gender", "female")
                            put("samplingRate", 22050)
                        })
                    })
                })
                put("inputData", JSONObject().apply {
                    put("input", JSONArray().apply {
                        put(JSONObject().apply {
                            put("source", text.take(450))
                        })
                    })
                })
            }

            val responseJson = postJson(
                url = cfg.callbackUrl,
                body = bodyJson.toString(),
                headers = mapOf(cfg.authHeaderName to cfg.authHeaderValue)
            )
            if (!shouldContinue()) return@runCatching

            val audioB64 = JSONObject(responseJson)
                .optJSONArray("pipelineResponse")
                ?.optJSONObject(0)
                ?.optJSONArray("audio")
                ?.optJSONObject(0)
                ?.optString("audioContent")
                .orEmpty()
            if (audioB64.isBlank()) error("Bhashini TTS returned empty audio")

            val decoded = Base64.decode(audioB64, Base64.DEFAULT)
            val tempAudio = File(context.cacheDir, "bhashini_tts_${System.currentTimeMillis()}.wav")
            tempAudio.writeBytes(decoded)
            if (shouldContinue()) {
                playAudioFile(tempAudio)
            } else {
                tempAudio.delete()
            }
        }
    }

    fun stopPlayback() {
        val player = activePlayer
        activePlayer = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
    }

    private fun playAudioFile(file: File) {
        stopPlayback()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener { mp ->
                try {
                    mp.release()
                } catch (_: Exception) {
                }
                if (activePlayer === mp) activePlayer = null
                file.delete()
            }
            prepare()
            start()
        }
        activePlayer = player
    }

    private fun getOrFetchPipelineConfigOrFallback(langCode: String): Pair<String, BhashiniPipelineConfig> {
        return try {
            val cfg = getOrFetchPipelineConfig(langCode)
            if (cfg.ttsServiceId != null || cfg.asrServiceId != null) {
                langCode to cfg
            } else if (langCode == "doi") {
                "hi" to getOrFetchPipelineConfig("hi")
            } else {
                langCode to cfg
            }
        } catch (e: Exception) {
            if (langCode == "doi") {
                Log.w(TAG, "Bhashini doi pipeline unavailable, falling back to hi", e)
                "hi" to getOrFetchPipelineConfig("hi")
            } else {
                throw e
            }
        }
    }

    private fun getOrFetchPipelineConfig(langCode: String): BhashiniPipelineConfig {
        pipelineCache[langCode]?.let { return it }

        val configReq = JSONObject().apply {
            put("pipelineTasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("taskType", "asr")
                    put("config", JSONObject().apply {
                        put("language", JSONObject().put("sourceLanguage", langCode))
                    })
                })
                put(JSONObject().apply {
                    put("taskType", "tts")
                    put("config", JSONObject().apply {
                        put("language", JSONObject().put("sourceLanguage", langCode))
                    })
                })
            })
            put("pipelineRequestConfig", JSONObject().apply {
                put("pipelineId", pipelineId)
            })
        }

        val rawResp = postJson(
            url = ULCA_PIPELINE_CONFIG_URL,
            body = configReq.toString(),
            headers = mapOf(
                "userID" to userId,
                "ulcaApiKey" to ulcaApiKey
            )
        )
        val root = JSONObject(rawResp)
        val endpoint = root.getJSONObject("pipelineInferenceAPIEndPoint")
        val callbackUrl = endpoint.optString("callbackUrl", DEFAULT_DHRUVA_COMPUTE_URL)
        val inferenceKeyObj = endpoint.getJSONObject("inferenceApiKey")
        val headerName = inferenceKeyObj.optString("name", "Authorization")
        val headerValue = inferenceKeyObj.getString("value")

        var asrId: String? = null
        var ttsId: String? = null
        val respConfigs = root.optJSONArray("pipelineResponseConfig")
        if (respConfigs != null) {
            for (i in 0 until respConfigs.length()) {
                val taskObj = respConfigs.getJSONObject(i)
                val taskType = taskObj.optString("taskType")
                val serviceId = taskObj.optJSONArray("config")
                    ?.optJSONObject(0)
                    ?.optString("serviceId")
                if (taskType == "asr" && !serviceId.isNullOrBlank()) asrId = serviceId
                if (taskType == "tts" && !serviceId.isNullOrBlank()) ttsId = serviceId
            }
        }

        val parsed = BhashiniPipelineConfig(
            callbackUrl = callbackUrl,
            authHeaderName = headerName,
            authHeaderValue = headerValue,
            asrServiceId = asrId,
            ttsServiceId = ttsId
        )
        pipelineCache[langCode] = parsed
        return parsed
    }

    @SuppressLint("MissingPermission")
    private fun capturePcmAsWav16kHz(maxDurationMs: Long): Result<ByteArray> = runCatching {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            .coerceAtLeast(sampleRate / 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            encoding,
            minBuf * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord failed to initialize")
        }

        val pcmOut = ByteArrayOutputStream()
        val buffer = ByteArray(minBuf)
        val startMs = System.currentTimeMillis()
        var speechDetected = false
        var lastSpeechMs = startMs
        isRecordingFlag.set(true)
        try {
            recorder.startRecording()
            while (isRecordingFlag.get()) {
                val now = System.currentTimeMillis()
                if ((now - startMs) >= maxDurationMs) break
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pcmOut.write(buffer, 0, read)
                    val rms = computePcm16Rms(buffer, read)
                    if (rms > SPEECH_RMS_THRESHOLD) {
                        speechDetected = true
                        lastSpeechMs = now
                    } else if (speechDetected && (now - lastSpeechMs) >= TRAILING_SILENCE_STOP_MS && (now - startMs) >= 1200L) {
                        // Adaptive VAD auto-stop cuts recording latency by 2-4 seconds once user finishes speaking
                        break
                    }
                }
            }
        } finally {
            isRecordingFlag.set(false)
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
        }

        val pcmBytes = pcmOut.toByteArray()
        if (pcmBytes.size < 1600) {
            error("Recorded audio too short for speech recognition")
        }
        wrapPcmWithWavHeader(pcmBytes, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
    }

    private fun computePcm16Rms(bytes: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toDouble()
            sumSq += sample * sample
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sumSq / count)
    }

    private fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>
    ): String {
        val reqBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val payload = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Bhashini HTTP ${resp.code}: $payload")
                error("Bhashini HTTP ${resp.code}")
            }
            return payload
        }
    }

    companion object {
        private const val TAG = "BhashiniSpeech"
        private const val SPEECH_RMS_THRESHOLD = 720.0
        private const val TRAILING_SILENCE_STOP_MS = 1250L
        private const val ULCA_PIPELINE_CONFIG_URL =
            "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline"
        private const val DEFAULT_DHRUVA_COMPUTE_URL =
            "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"

        fun normalizeLangCode(language: AppLanguage): String = when (language) {
            AppLanguage.HINDI -> "hi"
            AppLanguage.DOGRI -> "doi"
            AppLanguage.ENGLISH -> "en"
        }

        fun isCredentialSet(value: String?): Boolean =
            !value.isNullOrBlank() &&
                !value.endsWith("_DEFAULT_VALUE") &&
                !value.startsWith("YOUR_")

        fun wrapPcmWithWavHeader(
            pcmData: ByteArray,
            sampleRate: Int = 16000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + pcmData.size)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1.toShort()) // PCM
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign)
                putShort(bitsPerSample.toShort())
                put("data".toByteArray())
                putInt(pcmData.size)
            }.array()
            return header + pcmData
        }
    }
}
