package com.example.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class VoiceGuideManager(
    context: Context,
    private val bhashiniClient: BhashiniSpeechClient = BhashiniSpeechClient(context.applicationContext),
    private val geminiSpeechClient: GeminiSpeechClient = GeminiSpeechClient()
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var bhashiniAsrJob: Job? = null
    private var cloudTtsJob: Job? = null
    private val speechGeneration = AtomicLong(0L)

    init {
        initTextToSpeech(preferGoogleEngine = hasGoogleTtsInstalled())
    }

    private fun initTextToSpeech(preferGoogleEngine: Boolean) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                selectBestIndianFemaleVoice(tts, AppLanguage.ENGLISH, isOnlineForSpeech())
            } else if (preferGoogleEngine) {
                Log.w(TAG, "Google TTS engine init failed (status=$status), falling back to default system TTS")
                runCatching { tts?.shutdown() }
                initTextToSpeech(preferGoogleEngine = false)
            } else {
                ttsReady = false
                Log.w(TAG, "TextToSpeech initialization returned status=$status")
            }
        }
        tts = if (preferGoogleEngine) {
            TextToSpeech(appContext, listener, GOOGLE_TTS_PACKAGE)
        } else {
            TextToSpeech(appContext, listener)
        }
    }

    fun hasGoogleTtsInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(GOOGLE_TTS_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun openOfflineVoicePackInstaller(): Boolean {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            if (hasGoogleTtsInstalled()) {
                setPackage(GOOGLE_TTS_PACKAGE)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedInstall = runCatching {
            appContext.startActivity(installIntent)
            true
        }.getOrDefault(false)
        if (launchedInstall) return true

        val settingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            appContext.startActivity(settingsIntent)
            true
        }.getOrDefault(false)
    }

    fun warmUp() {
        if (isOnlineForSpeech() && bhashiniClient.isConfigured()) {
            scope.launch {
                runCatching { bhashiniClient.warmUpPipelines() }
            }
        }
    }

    fun isBhashiniConfigured(): Boolean = bhashiniClient.isConfigured()

    fun isOnlineForSpeech(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun speak(text: String, language: AppLanguage, speechRate: Float = 0.95f) {
        val cleanedText = sanitizeTextForSpeech(text, language)
        if (cleanedText.isBlank()) return

        // Cancel any previous playback & increment generation ID so late async audio aborts cleanly
        stopSpeaking()
        val activeGen = speechGeneration.get()
        val online = isOnlineForSpeech()

        if (bhashiniClient.isConfigured() && bhashiniClient.isSupportedLanguage(language) && online) {
            cloudTtsJob = scope.launch {
                val res = bhashiniClient.synthesizeAndPlayTts(
                    text = cleanedText,
                    language = language,
                    shouldContinue = { speechGeneration.get() == activeGen }
                )
                if (speechGeneration.get() != activeGen) {
                    bhashiniClient.stopPlayback()
                    return@launch
                }
                if (res.isFailure) {
                    Log.w(TAG, "Bhashini TTS failed, trying zero-key Gemini natural voice", res.exceptionOrNull())
                    val geminiRes = geminiSpeechClient.synthesizeAndPlayTts(
                        text = cleanedText,
                        language = language,
                        speechRate = speechRate,
                        shouldContinue = { speechGeneration.get() == activeGen }
                    )
                    if (speechGeneration.get() != activeGen) {
                        geminiSpeechClient.stopPlayback()
                        return@launch
                    }
                    if (geminiRes.isFailure) {
                        speakWithAndroidTts(cleanedText, language, speechRate, activeGen)
                    }
                }
            }
            return
        }

        if (online) {
            cloudTtsJob = scope.launch {
                val geminiRes = geminiSpeechClient.synthesizeAndPlayTts(
                    text = cleanedText,
                    language = language,
                    speechRate = speechRate,
                    shouldContinue = { speechGeneration.get() == activeGen }
                )
                if (speechGeneration.get() != activeGen) {
                    geminiSpeechClient.stopPlayback()
                    return@launch
                }
                if (geminiRes.isFailure) {
                    Log.w(TAG, "Gemini TTS unavailable, falling back to Google/Android Indian female TTS", geminiRes.exceptionOrNull())
                    speakWithAndroidTts(cleanedText, language, speechRate, activeGen)
                }
            }
        } else {
            speakWithAndroidTts(cleanedText, language, speechRate, activeGen)
        }
    }

    private fun speakWithAndroidTts(
        text: String,
        language: AppLanguage,
        speechRate: Float,
        expectedGeneration: Long
    ) {
        val engine = tts ?: return
        if (!ttsReady || text.isBlank() || speechGeneration.get() != expectedGeneration) return

        val online = isOnlineForSpeech()
        val chosenVoice = selectBestIndianFemaleVoice(engine, language, online)
        if (chosenVoice != null) {
            engine.voice = chosenVoice
        } else {
            val targetLocale = when (language) {
                AppLanguage.DOGRI -> {
                    val doiRes = engine.isLanguageAvailable(Locale.forLanguageTag("doi-IN"))
                    if (doiRes >= TextToSpeech.LANG_AVAILABLE) Locale.forLanguageTag("doi-IN")
                    else Locale.forLanguageTag("hi-IN")
                }
                AppLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
                AppLanguage.ENGLISH -> Locale.forLanguageTag("en-IN")
            }
            val result = engine.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.forLanguageTag("hi-IN"))
            }
        }

        // Warm, pleasant conversational female prosody (avoiding robotic monotone or high pitch)
        engine.setPitch(1.03f)
        engine.setSpeechRate(speechRate.coerceIn(0.80f, 1.20f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "banksathi_${expectedGeneration}_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        speechGeneration.incrementAndGet()
        cloudTtsJob?.cancel()
        cloudTtsJob = null
        bhashiniClient.stopPlayback()
        geminiSpeechClient.stopPlayback()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun isSpeechRecognitionSupported(): Boolean =
        bhashiniClient.isConfigured() || SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        language: AppLanguage,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopSpeaking()
        stopListening()

        if (bhashiniClient.isConfigured() && bhashiniClient.isSupportedLanguage(language) && isOnlineForSpeech()) {
            onPartial(
                when (language) {
                    AppLanguage.ENGLISH -> "Listening via Bhashini..."
                    AppLanguage.HINDI -> "भाषिणी आवाज़ सुन रहा है..."
                    AppLanguage.DOGRI -> "भाषिणी तुंदी आवाज़ सुणा दा ऐ..."
                }
            )
            bhashiniAsrJob = scope.launch {
                val res = bhashiniClient.record16kHzWavAndTranscribe(language)
                if (res.isSuccess) {
                    onResult(res.getOrThrow())
                } else {
                    Log.w(TAG, "Bhashini ASR failed, falling back to Android SpeechRecognizer", res.exceptionOrNull())
                    startAndroidRecognizer(language, onPartial, onResult, onError)
                }
            }
            return
        }
        startAndroidRecognizer(language, onPartial, onResult, onError)
    }

    private fun startAndroidRecognizer(
        language: AppLanguage,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError(
                when (language) {
                    AppLanguage.ENGLISH -> "Speech recognition is unavailable on this device. Choose a language or type below."
                    AppLanguage.HINDI -> "इस डिवाइस पर आवाज़ पहचान उपलब्ध नहीं है, कृपया भाषा चुनें या नीचे लिखें।"
                    AppLanguage.DOGRI -> "इस फोन पर आवाज़ पहचान उपलब्ध नेईं ऐ, कृपा करियै भाषा चुनो जां हेठ लिखो।"
                }
            )
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                onError(
                    when (language) {
                        AppLanguage.ENGLISH -> "Didn't catch that clearly. Please tap Speak and try again."
                        AppLanguage.HINDI -> "आवाज़ साफ नहीं मिली। कृपया फिर से बोलें।"
                        AppLanguage.DOGRI -> "आवाज़ साफ नेईं मिली। कृपा करियै दुबारा बोलो।"
                    }
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) onResult(spoken)
                else onError("No speech detected.")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) onPartial(partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // For Dogri offline ASR where device speech recognizer lacks doi-IN, fall back cleanly to Devanagari Hindi hi-IN
        val recognitionLocale = if (language == AppLanguage.DOGRI) "hi-IN" else language.localeTag
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        bhashiniClient.stopRecordingEarly()
        bhashiniAsrJob?.cancel()
        bhashiniAsrJob = null
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    fun release() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "VoiceGuideManager"
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

        private val femaleVoiceHints = listOf(
            "hia", "hic", "hie", "ena", "enc", "ene", "female", "woman",
            "sangeeta", "lekha", "swara", "neerja", "kavya", "kalpana", "heera",
            "raveena", "aditi", "kajal", "wavenet", "neural"
        )
        private val maleVoiceHints = listOf("hib", "hid", "enb", "end", "male")

        fun selectBestIndianFemaleVoice(
            engine: TextToSpeech?,
            language: AppLanguage,
            isOnline: Boolean
        ): Voice? {
            val voices = runCatching { engine?.voices }.getOrNull() ?: return null
            if (voices.isEmpty()) return null

            val targetLangs = when (language) {
                AppLanguage.DOGRI -> listOf("doi", "hi")
                AppLanguage.HINDI -> listOf("hi")
                AppLanguage.ENGLISH -> listOf("en")
            }

            return voices
                .filter { voice ->
                    val vLang = voice.locale.language.lowercase()
                    val networkAllowed = isOnline || !voice.isNetworkConnectionRequired
                    val missing = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                    vLang in targetLangs && networkAllowed && !missing
                }
                .maxByOrNull { voice -> scoreVoicePleasantness(voice, language, isOnline) }
        }

        private fun scoreVoicePleasantness(voice: Voice, language: AppLanguage, isOnline: Boolean): Int {
            return scoreVoiceDescriptor(
                voiceName = voice.name,
                langCode = voice.locale.language,
                countryCode = voice.locale.country,
                quality = voice.quality,
                isNetworkRequired = voice.isNetworkConnectionRequired,
                targetLanguage = language,
                isOnline = isOnline
            )
        }

        fun scoreVoiceDescriptor(
            voiceName: String,
            langCode: String,
            countryCode: String,
            quality: Int,
            isNetworkRequired: Boolean,
            targetLanguage: AppLanguage,
            isOnline: Boolean
        ): Int {
            var score = 0
            val name = voiceName.lowercase()
            val country = countryCode.uppercase()
            val lang = langCode.lowercase()

            if (targetLanguage == AppLanguage.DOGRI && lang == "doi") score += 220
            if (country == "IN") score += 160 else if (country == "GB") score += 20

            val isExplicitlyMale = maleVoiceHints.any { hint ->
                hint != "male" && name.contains(hint)
            } || (name.contains("male") && !name.contains("female"))

            if (femaleVoiceHints.any { name.contains(it) } && !isExplicitlyMale) score += 150
            if (isExplicitlyMale) score -= 140

            // Prefer natural/neural network voices when online; crisp local high-quality voices offline
            if (isOnline && (name.contains("network") || name.contains("neural"))) score += 65
            if (!isOnline && !isNetworkRequired && name.contains("local")) score += 50

            score += when {
                quality >= Voice.QUALITY_VERY_HIGH -> 60
                quality >= Voice.QUALITY_HIGH -> 45
                quality >= Voice.QUALITY_NORMAL -> 20
                else -> 0
            }
            return score
        }

        fun sanitizeTextForSpeech(rawText: String, language: AppLanguage): String {
            val currencyWord = when (language) {
                AppLanguage.ENGLISH -> "rupees "
                AppLanguage.HINDI -> "रुपये "
                AppLanguage.DOGRI -> "रुपये "
            }
            return rawText
                .replace("₹", currencyWord)
                .replace(Regex("""[*_`#>|]"""), " ")
                .replace("•", ", ")
                .replace("—", ", ")
                .replace(Regex("""\[(.*?)]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }
}
