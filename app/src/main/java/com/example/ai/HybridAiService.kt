package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.data.model.AiSourceMode
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantAnswer
import com.example.data.model.BankingTask
import com.example.data.model.CachedFaqRecord
import com.example.data.model.ChatMessage
import com.example.data.model.HuskyMood
import com.example.data.model.RiskLevel
import com.example.data.model.ScamAnalysisResult
import com.example.data.remote.BankSathiBackendClient
import com.example.data.remote.RagAskRequest
import com.example.data.remote.RagHistoryTurnDto
import com.example.data.remote.RemoteScamCheckRequest
import com.example.data.repository.BankingRuleEngine
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface GemmaModelState {
    data object NotLoaded : GemmaModelState
    data object Loading : GemmaModelState
    data class Ready(val modelName: String, val path: String) : GemmaModelState
    data class Error(val message: String) : GemmaModelState
}

class HybridAiService(
    private val context: Context,
    private val backendClient: BankSathiBackendClient = BankSathiBackendClient()
) {

    private val appContext = context.applicationContext
    private val gemmaExecutor = Executors.newSingleThreadExecutor()
    private val gemmaDispatcher = gemmaExecutor.asCoroutineDispatcher()
    private val gemmaMutex = Mutex()
    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    fun isFastApiConfigured(): Boolean = backendClient.isConfigured

    private val strictSafetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.LOW_AND_ABOVE),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.LOW_AND_ABOVE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.LOW_AND_ABOVE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.LOW_AND_ABOVE)
    )

    private val chatSystemInstruction = content {
        text(
            """
            You are BankSathi (बैंकसाथी), a trusted rural Indian banking & scam-safety companion.
            STRICT GUARDRAILS:
            1. Scope: ONLY answer rural Indian banking, Bank Mitra (BC), ATM, IPPB Post Office, PM-Kisan/DBT, Jan Dhan (BSBDA), KCC loans, and cyber fraud safety (1930). Politely refuse off-topic requests.
            2. Zero Hallucination: Never invent live personal bank balances, transaction statuses, or unverified interest rates. If uncertain, advise checking at the nearest branch or calling 14448 / 1930 and end with [ABSTAIN].
            3. Verified Facts:
               - Cash <= ₹10,000 -> BC Point (Aadhaar biometric, ZERO extra commission allowed by RBI; always take a printed receipt).
               - Cash > ₹10,000 -> Bank Branch or ATM with Passbook/Debit Card.
               - Money transfer -> IPPB Post Office or BC Point (printed receipt mandatory).
               - Passbook / PM-Kisan / Jan Dhan (BSBDA) -> zero-balance account, 4+ free withdrawals/month.
               - KCC Loan -> Bank Branch; up to ₹2,00,000 collateral-free (Khatauni + 2 copies, Aadhaar+PAN copies, Passbook, 3 photos).
               - Fraud / OTP / PIN / Fake KYC / Double thumb scan without receipt -> NEVER share OTP/PIN; call 1930 & report within 3 working days (RBI Zero Liability).
            4. Concise Spoken Style: Reply in 2-4 warm, clear, jargon-free sentences without markdown bold/asterisks so it sounds natural when read aloud.
            """.trimIndent()
        )
    }

    private val scamSchema = Schema.obj(
        properties = mapOf(
            "riskLevel" to Schema.enumeration(
                values = listOf("SAFE", "CAUTION", "HIGH_RISK"),
                description = "Fraud risk level."
            ),
            "explanationEn" to Schema.string(description = "Plain-language explanation in English under 50 words."),
            "explanationHi" to Schema.string(description = "Plain-language explanation in Hindi Devanagari under 50 words."),
            "explanationDoi" to Schema.string(description = "Plain-language explanation in Dogri Devanagari under 50 words."),
            "actionTipEn" to Schema.string(description = "Concrete next safety step in English."),
            "actionTipHi" to Schema.string(description = "Concrete next safety step in Hindi Devanagari."),
            "actionTipDoi" to Schema.string(description = "Concrete next safety step in Dogri Devanagari.")
        ),
        optionalProperties = listOf("explanationDoi", "actionTipDoi")
    )

    private val geminiChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.45f
                maxOutputTokens = 360
            },
            safetySettings = strictSafetySettings,
            systemInstruction = chatSystemInstruction
        )
    }

    private val geminiScamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scamSchema
                temperature = 0.1f
                maxOutputTokens = 360
            },
            safetySettings = strictSafetySettings
        )
    }

    fun detectKnownGemmaPaths(): String? {
        val modelDir = File(appContext.filesDir, "models")
        val internalCandidate = modelDir.listFiles()
            ?.firstOrNull { it.isFile && (it.name.endsWith(".bin") || it.name.endsWith(".task")) }
        if (internalCandidate != null) return internalCandidate.absolutePath

        val systemCandidates = listOf(
            "/data/local/tmp/llm/gemma2-2b-it-cpu-int4.task",
            "/data/local/tmp/llm/gemma-2b-it-cpu-int4.bin",
            "/data/local/tmp/llm/gemma2b.task",
            "/data/local/tmp/llm/gemma2b.bin"
        )
        return systemCandidates.firstOrNull { path ->
            val f = File(path)
            f.exists() && f.canRead()
        }
    }

    suspend fun importGemmaModelFromUri(uri: Uri, displayName: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val modelsDir = File(appContext.filesDir, "models").apply { mkdirs() }
                val safeName = (displayName ?: "gemma2-2b-it-cpu-int4.task")
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetFile = File(modelsDir, safeName)
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: error("Unable to open selected model file.")
                targetFile.absolutePath
            }
        }

    suspend fun loadGemmaModel(path: String): GemmaModelState = withContext(gemmaDispatcher) {
        gemmaMutex.withLock {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) {
                    return@withLock GemmaModelState.Error("Gemma-2B model file not found or unreadable.")
                }
                if (loadedModelPath == path && llmInference != null) {
                    return@withLock GemmaModelState.Ready(file.name, path)
                }
                llmInference?.close()
                llmInference = null
                loadedModelPath = null

                llmInference = createInferenceWithSafeTokenWindow(path)
                loadedModelPath = path
                GemmaModelState.Ready(file.name, path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load local Gemma-2B model", e)
                llmInference = null
                loadedModelPath = null
                GemmaModelState.Error(e.localizedMessage ?: "Failed to initialize Gemma-2B runtime.")
            }
        }
    }

    private fun createInferenceWithSafeTokenWindow(path: String): LlmInference {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .build()
            LlmInference.createFromOptions(appContext, options)
        } catch (firstErr: Exception) {
            Log.w(TAG, "1024-token window init failed, retrying with 512", firstErr)
            val fallbackOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(512)
                .build()
            LlmInference.createFromOptions(appContext, fallbackOptions)
        }
    }

    suspend fun unloadGemmaModel() = withContext(gemmaDispatcher) {
        gemmaMutex.withLock {
            try {
                llmInference?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing Gemma-2B runtime", e)
            } finally {
                llmInference = null
                loadedModelPath = null
            }
        }
    }

    fun isGemmaReady(): Boolean = llmInference != null

    suspend fun askAssistant(
        query: String,
        cachedFaqs: List<CachedFaqRecord> = emptyList()
    ): Result<AssistantAnswer> = askAssistantChat(
        query = query,
        language = AppLanguage.ENGLISH,
        history = emptyList(),
        cachedFaqs = cachedFaqs
    )

    suspend fun askAssistantChat(
        query: String,
        language: AppLanguage,
        history: List<ChatMessage>,
        cachedFaqs: List<CachedFaqRecord> = emptyList(),
        districtCode: String = "lucknow_rampur",
        lowRamFaqOnly: Boolean = false,
        onPartialText: (String) -> Unit = {}
    ): Result<AssistantAnswer> {
        // Layer 1 Guardrail: Prompt injection & strict banking-scope enforcement before any LLM latency
        val decision = BankingSafetyGuardrails.inspectInput(query)
        if (decision is InputGuardrailDecision.Block) {
            return Result.success(decision.answer)
        }
        val safeQuery = (decision as InputGuardrailDecision.Allow).sanitizedQuery
        val isScamDetected = decision.isScamSensitive

        val deterministicHint = BankingRuleEngine.analyzeOfflineQuery(safeQuery, cachedFaqs)
        val (matchedEnFaq, matchedHiFaq) = BankingRuleEngine.findBestCachedFaqMatch(safeQuery, cachedFaqs)
        val matchedDoiFaq = BankingRuleEngine.findDogriFaqMatch(safeQuery, cachedFaqs)
        val groundedFaqSnippet = buildString {
            when (language) {
                AppLanguage.ENGLISH -> if (matchedEnFaq != null) append("[${matchedEnFaq.sourceId}] ${matchedEnFaq.answer}")
                AppLanguage.HINDI -> if (matchedHiFaq != null) append("[${matchedHiFaq.sourceId}] ${matchedHiFaq.answer}")
                AppLanguage.DOGRI -> {
                    val f = matchedDoiFaq ?: matchedHiFaq
                    if (f != null) append("[${f.sourceId}] ${f.answer}")
                }
            }
        }.trim()

        // Step 1: Prefer local on-device Gemma-2B when loaded AND Low-RAM FAQ-Only safeguard is not active
        if (!lowRamFaqOnly && llmInference != null) {
            val localResult = runCatching {
                queryGemmaChat(
                    query = safeQuery,
                    language = language,
                    history = history,
                    groundedContext = groundedFaqSnippet,
                    isScamWarning = isScamDetected,
                    ruleHint = deterministicHint,
                    sourceId = matchedEnFaq?.sourceId ?: deterministicHint.sourceId,
                    verifiedOn = matchedEnFaq?.verifiedOn ?: deterministicHint.verifiedOn,
                    onPartialText = onPartialText
                )
            }
            if (localResult.isSuccess) {
                return localResult
            }
            val err = localResult.exceptionOrNull()
            if (err is CancellationException) throw err
            Log.w(TAG, "Local Gemma-2B chat inference failed, falling back cleanly", err)
            if (!isOnline()) {
                return Result.success(deterministicHint.copy(isFallbackFromError = true))
            }
        }

        // Step 2: Online FastAPI + ChromaDB RAG -> fail-fast fallback to Firebase AI Logic
        if (isOnline()) {
            val fastApi = backendClient.api
            if (fastApi != null) {
                val fastApiResult = runCatching {
                    withContext(Dispatchers.IO) {
                        withTimeout(FASTAPI_TIMEOUT_MS) {
                            val historyDto = history.filter { !it.isStreaming && it.textEn.isNotBlank() }
                                .takeLast(4)
                                .map { msg ->
                                    RagHistoryTurnDto(
                                        role = if (msg.isUser) "user" else "assistant",
                                        text = when (language) {
                                            AppLanguage.ENGLISH -> msg.textEn
                                            AppLanguage.HINDI -> msg.textHi
                                            AppLanguage.DOGRI -> msg.textDoi.ifBlank { msg.textHi }
                                        }
                                    )
                                }
                            val resp = fastApi.askRag(
                                RagAskRequest(
                                    query = safeQuery,
                                    lang = language.code,
                                    districtCode = districtCode,
                                    history = historyDto
                                )
                            )
                            val rawAnswer = if (language == AppLanguage.ENGLISH) {
                                resp.answerEn.ifBlank { resp.answerHi }
                            } else {
                                resp.answerHi.ifBlank { resp.answerEn }
                            }
                            val (verifiedText, wasForcedAbstain) = BankingSafetyGuardrails.sanitizeAndVerifyOutput(
                                rawReply = rawAnswer,
                                language = language,
                                isScamContext = isScamDetected
                            )
                            val parsedTask = resp.recommendedTask
                                ?.let { name -> BankingTask.entries.firstOrNull { it.name == name } }
                                ?: if (!resp.abstained && !wasForcedAbstain && !isScamDetected) deterministicHint.recommendedTask else null
                            AssistantAnswer(
                                replyEn = if (language == AppLanguage.ENGLISH) verifiedText else resp.answerEn.ifBlank { verifiedText },
                                replyHi = if (language == AppLanguage.HINDI) verifiedText else resp.answerHi.ifBlank { verifiedText },
                                replyDoi = if (language == AppLanguage.DOGRI) verifiedText else resp.answerHi.ifBlank { verifiedText },
                                recommendedTask = if (isScamDetected || resp.abstained || wasForcedAbstain) null else parsedTask,
                                suggestedAmountInr = if (parsedTask != null) {
                                    resp.suggestedAmountInr.takeIf { it > 0 } ?: deterministicHint.suggestedAmountInr
                                } else 0,
                                sourceMode = AiSourceMode.FASTAPI_RAG,
                                mood = if (isScamDetected || resp.abstained || wasForcedAbstain) HuskyMood.ALERT else HuskyMood.GUIDING,
                                abstained = resp.abstained || wasForcedAbstain,
                                sourceId = resp.sourceId ?: "FASTAPI_CHROMADB_RAG",
                                verifiedOn = resp.verifiedOn ?: "2026-11-23"
                            )
                        }
                    }
                }
                if (fastApiResult.isSuccess) {
                    return fastApiResult
                }
                val apiErr = fastApiResult.exceptionOrNull()
                if (apiErr is CancellationException) throw apiErr
                Log.w(TAG, "FastAPI RAG endpoint failed, falling back to Firebase AI Logic", apiErr)
            }

            try {
                val cloudAnswer = withContext(Dispatchers.IO) {
                    withTimeout(AI_TIMEOUT_MS) {
                        queryFirebaseAiChat(
                            query = safeQuery,
                            language = language,
                            history = history,
                            groundedContext = groundedFaqSnippet,
                            isScamWarning = isScamDetected,
                            ruleHint = deterministicHint,
                            sourceId = matchedEnFaq?.sourceId ?: deterministicHint.sourceId,
                            verifiedOn = matchedEnFaq?.verifiedOn ?: deterministicHint.verifiedOn,
                            onPartialText = onPartialText
                        )
                    }
                }
                return Result.success(cloudAnswer)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Firebase AI chat timed out after ${AI_TIMEOUT_MS / 1000}s; falling back to verified rules", e)
                return Result.success(deterministicHint.copy(isFallbackFromError = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI chat failed; falling back to verified rules", e)
                return Result.success(deterministicHint.copy(isFallbackFromError = true))
            }
        }

        // Step 3: Low-RAM FAQ-Only / Offline deterministic verified rules fallback
        return Result.success(deterministicHint)
    }

    suspend fun analyzeScam(
        snippet: String,
        language: AppLanguage = AppLanguage.ENGLISH,
        lowRamFaqOnly: Boolean = false
    ): Result<ScamAnalysisResult> {
        val trimmed = snippet.trim()
        if (trimmed.isBlank()) {
            return Result.success(BankingRuleEngine.analyzeOfflineScam(""))
        }

        // Latency fast-path: if unmistakable HIGH_RISK offline fraud triggers hit (OTP/PIN/AnyDesk/KYC link/no receipt),
        // return deterministic RBI 1930 alert immediately (<5ms) when user is on-device or low-RAM,
        // or let local Gemma/cloud enrich ambiguous inputs.
        val deterministicScam = BankingRuleEngine.analyzeOfflineScam(trimmed)
        if (deterministicScam.riskLevel == RiskLevel.HIGH_RISK && BankingRuleEngine.isImmediateScamPhrase(trimmed)) {
            return Result.success(deterministicScam)
        }

        if (!lowRamFaqOnly && llmInference != null) {
            val localScam = runCatching { analyzeScamWithGemmaLocal(trimmed) }
            if (localScam.isSuccess) {
                return localScam
            }
            val err = localScam.exceptionOrNull()
            if (err is CancellationException) throw err
        }

        if (isOnline()) {
            val fastApi = backendClient.api
            if (fastApi != null) {
                val remoteScam = runCatching {
                    withContext(Dispatchers.IO) {
                        withTimeout(FASTAPI_TIMEOUT_MS) {
                            val resp = fastApi.checkScam(
                                RemoteScamCheckRequest(snippet = trimmed, lang = language.code)
                            )
                            val risk = RiskLevel.entries.firstOrNull { it.name == resp.riskLevel }
                                ?: RiskLevel.CAUTION
                            ScamAnalysisResult(
                                riskLevel = risk,
                                explanationEn = resp.explanationEn,
                                explanationHi = resp.explanationHi,
                                explanationDoi = resp.explanationHi,
                                actionTipEn = resp.actionTipEn,
                                actionTipHi = resp.actionTipHi,
                                actionTipDoi = resp.actionTipHi,
                                sourceMode = AiSourceMode.FASTAPI_RAG
                            )
                        }
                    }
                }
                if (remoteScam.isSuccess) {
                    return remoteScam
                }
                val apiErr = remoteScam.exceptionOrNull()
                if (apiErr is CancellationException) throw apiErr
                Log.w(TAG, "FastAPI scam check failed, falling back to Firebase AI Logic", apiErr)
            }

            try {
                val cloudScam = withContext(Dispatchers.IO) {
                    withTimeout(AI_TIMEOUT_MS) {
                        analyzeScamWithFirebaseAi(trimmed)
                    }
                }
                return Result.success(cloudScam)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Firebase AI scam analysis timed out after ${AI_TIMEOUT_MS / 1000}s; returning offline rule check", e)
                return Result.success(deterministicScam.copy(isFallbackFromError = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI scam analysis failed; returning offline rule check", e)
                return Result.success(deterministicScam.copy(isFallbackFromError = true))
            }
        }

        return Result.success(deterministicScam)
    }

    private suspend fun queryGemmaChat(
        query: String,
        language: AppLanguage,
        history: List<ChatMessage>,
        groundedContext: String,
        isScamWarning: Boolean,
        ruleHint: AssistantAnswer,
        sourceId: String?,
        verifiedOn: String?,
        onPartialText: (String) -> Unit
    ): AssistantAnswer = withContext(gemmaDispatcher) {
        gemmaMutex.withLock {
            val engine = checkNotNull(llmInference) { "Gemma-2B is not loaded." }
            val prompt = buildGemmaPrompt(
                query = query,
                language = language,
                history = history,
                groundedContext = groundedContext,
                isScamWarning = isScamWarning
            )

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.50f)
                .setTopK(40)
                .setTopP(0.92f)
                .setRandomSeed(((System.currentTimeMillis() xor query.hashCode().toLong()) and 0x7FFFFFFFL).toInt())
                .build()

            val session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
            val rawOutput = try {
                session.addQueryChunk(prompt)
                val streamed = StringBuilder()
                val future = session.generateResponseAsync(
                    ProgressListener { partialResult, _ ->
                        if (partialResult != null) {
                            streamed.append(partialResult)
                            val cleanedPreview = sanitizeModelText(streamed.toString())
                            if (cleanedPreview.isNotBlank() && !cleanedPreview.trimStart().startsWith("{")) {
                                onPartialText(cleanedPreview)
                            }
                        }
                    }
                )
                val completed = awaitListenableFuture(future).orEmpty()
                completed.ifBlank { streamed.toString() }
            } finally {
                session.close()
            }

            val cleaned = sanitizeModelText(rawOutput)
            if (cleaned.length < 3) {
                error("Gemma-2B returned empty text.")
            }

            val jsonCandidate = extractJsonObject(cleaned)
            val parsedJson = parseJsonAssistantResponse(
                jsonText = jsonCandidate,
                sourceMode = AiSourceMode.GEMMA_LOCAL,
                sourceId = sourceId ?: "GEMMA_LOCAL_CHAT",
                verifiedOn = verifiedOn ?: "2026-11-23"
            )
            if (parsedJson != null && (parsedJson.replyEn.isNotBlank() || parsedJson.replyHi.isNotBlank())) {
                return@withLock parsedJson.copy(
                    replyEn = parsedJson.replyEn.ifBlank { parsedJson.replyHi },
                    replyHi = parsedJson.replyHi.ifBlank { parsedJson.replyEn },
                    replyDoi = parsedJson.replyDoi.ifBlank { parsedJson.replyHi.ifBlank { parsedJson.replyEn } },
                    mood = if (isScamWarning || parsedJson.abstained) HuskyMood.ALERT else HuskyMood.GUIDING
                )
            }

            val isModelAbstain = cleaned.contains("[ABSTAIN]", ignoreCase = true)
            if (isModelAbstain && !ruleHint.abstained) {
                return@withLock ruleHint
            }
            val withoutAbstain = cleaned.replace("[ABSTAIN]", "", ignoreCase = true).trim()
            val (verifiedReply, forcedAbstain) = BankingSafetyGuardrails.sanitizeAndVerifyOutput(
                rawReply = withoutAbstain,
                language = language,
                isScamContext = isScamWarning
            )
            val finalAbstained = isModelAbstain || forcedAbstain
            if (finalAbstained && !ruleHint.abstained) {
                return@withLock ruleHint
            }
            val detectedTask = if (isScamWarning || finalAbstained) null else inferTaskFromText(query, verifiedReply) ?: ruleHint.recommendedTask

            AssistantAnswer(
                replyEn = verifiedReply,
                replyHi = verifiedReply,
                replyDoi = verifiedReply,
                recommendedTask = detectedTask,
                suggestedAmountInr = if (detectedTask != null) ruleHint.suggestedAmountInr else 0,
                sourceMode = AiSourceMode.GEMMA_LOCAL,
                mood = if (isScamWarning || finalAbstained) HuskyMood.ALERT else HuskyMood.GUIDING,
                abstained = finalAbstained,
                sourceId = if (finalAbstained) "ABSTAIN_SAFETY_PATH" else (sourceId ?: "GEMMA_2B_CHAT"),
                verifiedOn = verifiedOn ?: "2026-11-23"
            )
        }
    }

    private fun buildGemmaPrompt(
        query: String,
        language: AppLanguage,
        history: List<ChatMessage>,
        groundedContext: String,
        isScamWarning: Boolean
    ): String {
        val langRule = when (language) {
            AppLanguage.DOGRI -> "Reply naturally in simple, warm Dogri (Devanagari script, e.g., तुंदा, नेड़े दा, कड्ढो, रक्खो, ऐ) so village citizens in Jammu region understand easily."
            AppLanguage.HINDI -> "Reply naturally and conversationally in simple Hindi (Devanagari script). Keep sentences warm, clear, and easy for village citizens."
            AppLanguage.ENGLISH -> "Reply naturally and conversationally in clear, friendly plain English."
        }

        val scamDirective = if (isScamWarning) {
            "URGENT SAFETY WARNING: User mentioned potential fraud/OTP/KYC link/no receipt. Warn them NEVER to share OTP/PIN or tap unknown links, and call 1930 immediately."
        } else {
            ""
        }

        val systemPreamble = """
            You are BankSathi (बैंकसाथी), a rural Indian banking assistant.
            $langRule
            Banking Facts:
            - Cash <= ₹10,000: Bank Mitra (BC Point) Aadhaar biometric, NO extra fee allowed. Always take printed receipt.
            - Cash > ₹10,000: Bank Branch or ATM with Passbook/Debit Card.
            - Transfer: IPPB Post Office or BC Point.
            - PM-Kisan / Jan Dhan (BSBDA): zero balance, 4+ free withdrawals/month.
            - KCC Loan: up to ₹2,00,000 collateral-free at Branch (Khatauni+2 copies, Aadhaar+PAN copies, Passbook, 3 photos).
            - Fraud / No Receipt: Call 1930 & report within 3 working days (RBI Zero Liability).
            - Out-of-scope or unknown private balances: Politely refuse/advise Branch or 14448 / 1930 and append [ABSTAIN].
            ${if (groundedContext.isNotBlank()) "Verified Context: $groundedContext" else ""}
            $scamDirective
        """.trimIndent()

        val recentTurns = history
            .filter { !it.isStreaming && it.textEn.isNotBlank() }
            .takeLast(3)

        val sb = StringBuilder()
        if (recentTurns.isEmpty()) {
            sb.append("<start_of_turn>user\n")
            sb.append(systemPreamble).append("\n\nQuestion: ").append(query).append("\n<end_of_turn>\n")
            sb.append("<start_of_turn>model\n")
        } else {
            recentTurns.forEachIndexed { idx, msg ->
                val txt = when (language) {
                    AppLanguage.ENGLISH -> msg.textEn
                    AppLanguage.HINDI -> msg.textHi
                    AppLanguage.DOGRI -> msg.textDoi.ifBlank { msg.textHi }
                }
                if (msg.isUser) {
                    sb.append("<start_of_turn>user\n")
                    if (idx == 0) sb.append(systemPreamble).append("\n\n")
                    sb.append(txt).append("\n<end_of_turn>\n")
                } else {
                    sb.append("<start_of_turn>model\n").append(txt).append("\n<end_of_turn>\n")
                }
            }
            sb.append("<start_of_turn>user\n").append(query).append("\n<end_of_turn>\n<start_of_turn>model\n")
        }
        return sb.toString()
    }

    private suspend fun queryFirebaseAiChat(
        query: String,
        language: AppLanguage,
        history: List<ChatMessage>,
        groundedContext: String,
        isScamWarning: Boolean,
        ruleHint: AssistantAnswer,
        sourceId: String?,
        verifiedOn: String?,
        onPartialText: (String) -> Unit
    ): AssistantAnswer {
        val langTag = when (language) {
            AppLanguage.DOGRI -> "Reply in warm, natural Dogri (Devanagari script, authentic Jammu Dogri phrasing like तुंदा, नेड़े दा, कड्ढो, रक्खो, ऐ)."
            AppLanguage.HINDI -> "Reply in warm, natural, jargon-free Hindi (Devanagari)."
            AppLanguage.ENGLISH -> "Reply in warm, natural, jargon-free English."
        }

        val recentTurns = history
            .filter { !it.isStreaming && it.textEn.isNotBlank() }
            .takeLast(4)
            .map { msg ->
                val role = if (msg.isUser) "user" else "model"
                val txt = when (language) {
                    AppLanguage.ENGLISH -> msg.textEn
                    AppLanguage.HINDI -> msg.textHi
                    AppLanguage.DOGRI -> msg.textDoi.ifBlank { msg.textHi }
                }
                content(role) { text(txt) }
            }

        val turnPrompt = buildString {
            append(langTag)
            if (groundedContext.isNotBlank()) append("\nVerified Context: $groundedContext")
            if (isScamWarning) append("\nURGENT: Emphasize NEVER sharing OTP/PIN and calling 1930 immediately.")
            append("\nUser Question: ").append(query)
        }

        val chat = geminiChatModel.startChat(history = recentTurns)
        val accumulated = StringBuilder()
        chat.sendMessageStream(turnPrompt).collect { chunk ->
            val piece = chunk.text.orEmpty()
            if (piece.isNotEmpty()) {
                accumulated.append(piece)
                val preview = accumulated.toString()
                    .replace("[ABSTAIN]", "", ignoreCase = true)
                    .replace("**", "")
                    .trim()
                if (preview.isNotBlank()) onPartialText(preview)
            }
        }

        val raw = accumulated.toString().trim()
        if (raw.isBlank()) {
            return if (!ruleHint.abstained) ruleHint else BankingRuleEngine.abstainAnswer()
        }
        val modelAbstained = raw.contains("[ABSTAIN]", ignoreCase = true)
        if (modelAbstained && !ruleHint.abstained) {
            return ruleHint
        }
        val cleanBase = raw.replace("[ABSTAIN]", "", ignoreCase = true).trim()
        val (verifiedText, forcedAbstain) = BankingSafetyGuardrails.sanitizeAndVerifyOutput(
            rawReply = cleanBase,
            language = language,
            isScamContext = isScamWarning
        )
        val finalAbstained = modelAbstained || forcedAbstain
        if (finalAbstained && !ruleHint.abstained) {
            return ruleHint
        }
        val task = if (isScamWarning || finalAbstained) null else inferTaskFromText(query, verifiedText) ?: ruleHint.recommendedTask

        return AssistantAnswer(
            replyEn = verifiedText,
            replyHi = verifiedText,
            replyDoi = verifiedText,
            recommendedTask = task,
            suggestedAmountInr = if (task != null) ruleHint.suggestedAmountInr else 0,
            sourceMode = AiSourceMode.GEMINI_AI,
            mood = if (isScamWarning || finalAbstained) HuskyMood.ALERT else HuskyMood.GUIDING,
            abstained = finalAbstained,
            sourceId = if (finalAbstained) "ABSTAIN_SAFETY_PATH" else (sourceId ?: "GEMINI_VERIFIED_CHAT"),
            verifiedOn = verifiedOn ?: "2026-11-23"
        )
    }

    private suspend fun analyzeScamWithGemmaLocal(snippet: String): ScamAnalysisResult =
        withContext(gemmaDispatcher) {
            gemmaMutex.withLock {
                val engine = checkNotNull(llmInference) { "Gemma-2B is not loaded." }
                val prompt = """
                    <start_of_turn>user
                    Analyze this banking SMS or call claim for rural fraud in India.
                    Advise calling 1930 and reporting within 3 working days on HIGH_RISK.
                    Return JSON ONLY with keys: riskLevel (SAFE|CAUTION|HIGH_RISK), explanationEn, explanationHi, explanationDoi, actionTipEn, actionTipHi, actionTipDoi.
                    Input: "$snippet"
                    <end_of_turn>
                    <start_of_turn>model
                """.trimIndent()
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.20f)
                    .setTopK(32)
                    .build()
                val session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
                val raw = try {
                    session.addQueryChunk(prompt)
                    session.generateResponse().orEmpty()
                } finally {
                    session.close()
                }
                val parsed = parseJsonScamResponse(extractJsonObject(raw), AiSourceMode.GEMMA_LOCAL)
                if (parsed != null) return@withLock parsed

                val cleaned = sanitizeModelText(raw)
                val ruleBaseline = BankingRuleEngine.analyzeOfflineScam(snippet)
                if (cleaned.length > 16 && !cleaned.startsWith("{")) {
                    ruleBaseline.copy(
                        explanationEn = cleaned,
                        explanationHi = cleaned,
                        explanationDoi = cleaned,
                        sourceMode = AiSourceMode.GEMMA_LOCAL
                    )
                } else {
                    ruleBaseline.copy(sourceMode = AiSourceMode.GEMMA_LOCAL)
                }
            }
        }

    private suspend fun analyzeScamWithFirebaseAi(snippet: String): ScamAnalysisResult {
        val prompt = """
            Evaluate this message, phone call claim, or banking counter situation for rural Indian financial fraud (OTP vishing, fake KYC SMS, screen-share apps, extra BC fee, AePS double thumbprint / no paper receipt).
            Mention 1930 helpline and RBI 3-working-day Zero Liability reporting on HIGH_RISK.
            Provide concise, jargon-free guidance in English, Hindi (Devanagari), and Dogri (Devanagari).
            Input: "$snippet"
        """.trimIndent()

        val accumulated = StringBuilder()
        geminiScamModel.generateContentStream(prompt).collect { chunk ->
            accumulated.append(chunk.text.orEmpty())
        }
        val jsonText = accumulated.toString().trim()
        if (jsonText.isBlank()) {
            return BankingRuleEngine.analyzeOfflineScam(snippet)
        }
        return parseJsonScamResponse(jsonText, AiSourceMode.GEMINI_AI)
            ?: BankingRuleEngine.analyzeOfflineScam(snippet)
    }

    private suspend fun <T> awaitListenableFuture(future: ListenableFuture<T>): T =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { future.cancel(true) }
            future.addListener({
                try {
                    if (!future.isCancelled && cont.isActive) {
                        cont.resume(future.get())
                    }
                } catch (e: Exception) {
                    if (cont.isActive) {
                        cont.resumeWithException(e.cause ?: e)
                    }
                }
            }, gemmaExecutor)
        }

    private fun sanitizeModelText(raw: String): String {
        return raw
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>model", "")
            .replace("<eos>", "")
            .replace("<bos>", "")
            .trim()
    }

    private fun inferTaskFromText(query: String, reply: String): BankingTask? {
        val combined = "$query $reply".lowercase()
        return when {
            listOf("kcc", "kisan credit", "लोन", "केसीसी", "खतौनी", "khatauni").any { combined.contains(it) } -> BankingTask.LOAN_KCC
            listOf("passbook", "dbt", "pm-kisan", "pm kisan", "bsbda", "पासबुक", "सब्सिडी", "किस्त").any { combined.contains(it) } -> BankingTask.PASSBOOK_DBT
            listOf("transfer", "send money", "ippb", "post office", "पैसे भेज", "डाकघर").any { combined.contains(it) } -> BankingTask.TRANSFER
            listOf("withdraw", "cash", "bc point", "aeps", "पैसे निकाल", "पैसे कड्ढ", "बैंक मित्र").any { combined.contains(it) } -> BankingTask.WITHDRAW
            else -> null
        }
    }

    private fun extractJsonObject(rawText: String): String {
        val start = rawText.indexOf('{')
        val end = rawText.lastIndexOf('}')
        return if (start >= 0 && end > start) rawText.substring(start, end + 1) else ""
    }

    private fun parseJsonAssistantResponse(
        jsonText: String,
        sourceMode: AiSourceMode,
        sourceId: String,
        verifiedOn: String
    ): AssistantAnswer? {
        if (jsonText.isBlank()) return null
        return try {
            val json = JSONObject(jsonText)
            val taskStr = json.optString("task", "NONE")
            val task = BankingTask.entries.firstOrNull { it.name == taskStr }
            val isScam = json.optBoolean("isScamAlert", false)
            val abstained = json.optBoolean("abstained", false)
            val replyHi = json.optString("replyHi", "")
            AssistantAnswer(
                replyEn = json.optString("replyEn", ""),
                replyHi = replyHi,
                replyDoi = json.optString("replyDoi", replyHi),
                recommendedTask = if (abstained) null else task,
                suggestedAmountInr = json.optInt("suggestedAmountInr", 2000),
                sourceMode = sourceMode,
                mood = if (isScam || abstained) HuskyMood.ALERT else HuskyMood.GUIDING,
                abstained = abstained,
                sourceId = if (abstained) "ABSTAIN_SAFETY_PATH" else sourceId,
                verifiedOn = verifiedOn
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonScamResponse(jsonText: String, sourceMode: AiSourceMode): ScamAnalysisResult? {
        if (jsonText.isBlank()) return null
        return try {
            val json = JSONObject(jsonText)
            val risk = RiskLevel.entries.firstOrNull { it.name == json.optString("riskLevel") }
                ?: RiskLevel.CAUTION
            val expHi = json.optString("explanationHi", "")
            val tipHi = json.optString("actionTipHi", "")
            ScamAnalysisResult(
                riskLevel = risk,
                explanationEn = json.optString("explanationEn", ""),
                explanationHi = expHi,
                explanationDoi = json.optString("explanationDoi", expHi).ifBlank { expHi },
                actionTipEn = json.optString("actionTipEn", ""),
                actionTipHi = tipHi,
                actionTipDoi = json.optString("actionTipDoi", tipHi).ifBlank { tipHi },
                sourceMode = sourceMode
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun shutdown() {
        try {
            llmInference?.close()
        } catch (_: Exception) {
        }
        gemmaExecutor.shutdown()
    }

    private companion object {
        const val TAG = "HybridAiService"
        const val FASTAPI_TIMEOUT_MS = 5_000L
        const val AI_TIMEOUT_MS = 30_000L
    }
}
