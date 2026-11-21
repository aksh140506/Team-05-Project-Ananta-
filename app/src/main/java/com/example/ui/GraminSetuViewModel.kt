package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DeviceMemoryHelper
import com.example.ai.GemmaModelState
import com.example.ai.HybridAiService
import com.example.data.local.AppPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantAnswer
import com.example.data.model.BankingTask
import com.example.data.model.CachedFaqRecord
import com.example.data.model.CashStatus
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceMemoryStatus
import com.example.data.model.DistrictCatalog
import com.example.data.model.FacilityRecord
import com.example.data.model.FacilityType
import com.example.data.model.HuskyMood
import com.example.data.model.LowRamOverrideMode
import com.example.data.model.RegionalLanguageOption
import com.example.data.model.ReportStatus
import com.example.data.model.RiskLevel
import com.example.data.model.RoutingRecommendation
import com.example.data.model.ScamAnalysisResult
import com.example.data.model.ScamCheckRecord
import com.example.data.model.ScamRuleRecord
import com.example.data.model.SyncMetaRecord
import com.example.data.model.TripChecklistRecord
import com.example.data.model.UserRole
import com.example.data.repository.BankingRuleEngine
import com.example.data.repository.GraminSetuRepository
import com.example.data.repository.PincodeFacilityResolver
import com.example.ui.i18n.BilingualStrings
import com.example.voice.VoiceGuideManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

data class GraminSetuUiState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val selectedRegionalCode: String = "en",
    val voiceEnabled: Boolean = true,
    val playbackSpeed: Float = 0.95f,
    val isOnline: Boolean = true,
    val selectedTask: BankingTask = BankingTask.WITHDRAW,
    val selectedAmountInr: Int = 2000,
    val recommendation: RoutingRecommendation = BankingRuleEngine.routeTask(BankingTask.WITHDRAW, 2000),
    val checkedDocIds: Set<String> = emptySet(),
    val receiptVerified: Boolean = false,
    val huskyMood: HuskyMood = HuskyMood.IDLE,
    val huskyBubbleEn: String = "Namaste! I am Sheru. Tap me for quick help or drag anywhere.",
    val huskyBubbleHi: String = "नमस्ते! मैं शेरू हूँ। मदद के लिए दबाएं या कहीं भी खिसकाएं।",
    val huskyBubbleDoi: String = "नमस्ते! मैं शेरू आं। मदद आस्तै दबाओ जां कुतै बी सरकाओ।",
    val isBubbleVisible: Boolean = true,
    val isQuickMenuVisible: Boolean = false,
    val isListening: Boolean = false,
    val partialSpeechText: String = "",
    val speechRetryCount: Int = 0,
    val showLanguageMenu: Boolean = false,
    val isAssistantThinking: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val lastAnswer: AssistantAnswer? = null,
    val assistantError: String? = null,
    val isScamAnalyzing: Boolean = false,
    val lastScamResult: ScamAnalysisResult? = null,
    val scamError: String? = null,
    val gemmaModelState: GemmaModelState = GemmaModelState.NotLoaded,
    val statusFeedbackMessage: String? = null,
    val huskyOffsetX: Float = -1f,
    val huskyOffsetY: Float = -1f,
    val selectedDistrictCode: String = "lucknow_rampur",
    val radiusKmFilter: Int = 10,
    val deviceMemoryStatus: DeviceMemoryStatus = DeviceMemoryStatus(4096L, false),
    val lowRamOverrideMode: LowRamOverrideMode = LowRamOverrideMode.AUTO,
    val isLowRamFaqOnlyActive: Boolean = false,
    val fastApiConfigured: Boolean = false,
    val bhashiniConfigured: Boolean = false,
    val googleTtsInstalled: Boolean = false,
    val lastSyncVerificationMessage: String? = null,
    val lastSyncRolledBack: Boolean = false,
    val activePincode: String = "",
    val activePincodeArea: String = "",
    val activePincodeLat: Double = 26.8467,
    val activePincodeLon: Double = 80.9462,
    val isResolvingPincode: Boolean = false,
    val pincodeError: String? = null,
    val showPincodePrompt: Boolean = true,
    val isSignedIn: Boolean = false,
    val userRole: UserRole = UserRole.CITIZEN,
    val backendUserEmail: String? = null,
    val backendUserName: String? = null,
    val isBackendAuthLoading: Boolean = false,
    val backendAuthError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class GraminSetuViewModel(
    private val repository: GraminSetuRepository,
    private val aiService: HybridAiService,
    private val voiceManager: VoiceGuideManager,
    private val preferences: AppPreferences,
    private val currentUserId: String,
    private val deviceMemoryStatus: DeviceMemoryStatus = DeviceMemoryStatus(4096L, false),
    private val isSignedIn: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GraminSetuUiState(
            isOnline = aiService.isOnline(),
            deviceMemoryStatus = deviceMemoryStatus,
            isLowRamFaqOnlyActive = deviceMemoryStatus.isLowRamDevice,
            fastApiConfigured = aiService.isFastApiConfigured(),
            bhashiniConfigured = voiceManager.isBhashiniConfigured(),
            googleTtsInstalled = voiceManager.hasGoogleTtsInstalled(),
            isSignedIn = isSignedIn
        )
    )
    val uiState: StateFlow<GraminSetuUiState> = _uiState.asStateFlow()

    val regionalLanguages: List<RegionalLanguageOption> = listOf(
        RegionalLanguageOption("hi", "Hindi", "हिन्दी", true),
        RegionalLanguageOption("doi", "Dogri", "डोगरी", true),
        RegionalLanguageOption("en", "English", "English", true)
    )

    val facilitiesState: StateFlow<DataUiState<List<FacilityRecord>>> =
        repository.observeFacilities()
            .map<List<FacilityRecord>, DataUiState<List<FacilityRecord>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing facilities", rootError)
                emit(DataUiState.Error(rootError.message ?: "Unable to sync facilities right now."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    val cachedFacilities: StateFlow<List<FacilityRecord>> =
        (repository.observeCachedFacilities() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val checklistsState: StateFlow<List<TripChecklistRecord>> =
        repository.observeUserChecklists(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing checklists", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val scamHistoryState: StateFlow<List<ScamCheckRecord>> =
        repository.observeScamChecks(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing scam checks", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cachedFaqsState: StateFlow<List<CachedFaqRecord>> =
        _uiState
            .map { it.language.code }
            .distinctUntilChanged()
            .flatMapLatest { code -> repository.observeCachedFaqs(code) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val scamRulesState: StateFlow<List<ScamRuleRecord>> =
        repository.observeScamRules()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val syncMetaState: StateFlow<List<SyncMetaRecord>> =
        repository.observeSyncMeta()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private var tipIndex = 0
    private var assistantJob: Job? = null
    private var scamJob: Job? = null
    @Volatile
    private var inMemoryAllFaqs: List<CachedFaqRecord> = emptyList()

    init {
        voiceManager.warmUp()
        viewModelScope.launch {
            repository.seedBankSathiCatalogIfEmpty()
            repository.seedInitialFacilitiesIfEmpty(currentUserId)
            inMemoryAllFaqs = repository.getAllCachedFaqs()
            val savedBackendSession = preferences.backendAuthSessionFlow.first()
            if (savedBackendSession != null && !isSignedIn) {
                com.example.data.remote.BackendSessionStore.accessToken = savedBackendSession.accessToken
                _uiState.update {
                    it.copy(
                        isSignedIn = true,
                        backendUserEmail = savedBackendSession.email,
                        backendUserName = savedBackendSession.fullName,
                        userRole = UserRole.fromApi(savedBackendSession.role)
                    )
                }
                val liveRole = repository.fetchBackendRole()
                _uiState.update { it.copy(userRole = liveRole) }
            } else if (isSignedIn) {
                val role = repository.fetchBackendRole()
                _uiState.update { it.copy(userRole = role) }
            }
        }
        viewModelScope.launch {
            val savedLang = preferences.languageFlow.first()
            val savedVoice = preferences.voiceEnabledFlow.first()
            val savedSpeed = preferences.playbackSpeedFlow.first()
            val savedOffset = preferences.huskyOffsetFlow.first()
            val savedDistrict = preferences.selectedDistrictCodeFlow.first()
            val savedPincode = preferences.activePincodeFlow.first()
            val savedPincodeArea = preferences.activePincodeAreaFlow.first()
            val savedPincodeCoords = preferences.activePincodeCoordsFlow.first()
            val savedLowRamOverride = preferences.lowRamOverrideFlow.first()
            val hasValidSavedPin = PincodeFacilityResolver.isValidIndianPincode(savedPincode)
            val resolvedDistrictCode = if (hasValidSavedPin) {
                PincodeFacilityResolver.districtCodeForPincode(savedPincode)
            } else {
                DistrictCatalog.findByCode(savedDistrict).code
            }
            val faqOnlyActive = DeviceMemoryHelper.shouldUseFaqOnlyMode(deviceMemoryStatus, savedLowRamOverride)
            val savedModelPath = if (faqOnlyActive) null else {
                preferences.gemmaModelPathFlow.first() ?: aiService.detectKnownGemmaPaths()
            }
            val normalizedSavedOffsetX = if (savedOffset.first == 0f && savedOffset.second == 0f) -1f else savedOffset.first
            val normalizedSavedOffsetY = if (savedOffset.first == 0f && savedOffset.second == 0f) -1f else savedOffset.second
            _uiState.update {
                it.copy(
                    language = savedLang,
                    selectedRegionalCode = savedLang.code,
                    voiceEnabled = savedVoice,
                    playbackSpeed = savedSpeed,
                    huskyOffsetX = normalizedSavedOffsetX,
                    huskyOffsetY = normalizedSavedOffsetY,
                    selectedDistrictCode = resolvedDistrictCode,
                    activePincode = if (hasValidSavedPin) savedPincode else "",
                    activePincodeArea = if (hasValidSavedPin) savedPincodeArea else "",
                    activePincodeLat = if (hasValidSavedPin) savedPincodeCoords.first else 26.8467,
                    activePincodeLon = if (hasValidSavedPin) savedPincodeCoords.second else 80.9462,
                    showPincodePrompt = !hasValidSavedPin,
                    lowRamOverrideMode = savedLowRamOverride,
                    isLowRamFaqOnlyActive = faqOnlyActive,
                    fastApiConfigured = aiService.isFastApiConfigured(),
                    bhashiniConfigured = voiceManager.isBhashiniConfigured()
                )
            }
            if (hasValidSavedPin && cachedFacilities.value.none { it.districtCode == resolvedDistrictCode }) {
                repository.resolveAndAddPincodeFacilities(
                    pincode = savedPincode,
                    isOnline = aiService.isOnline(),
                    userId = currentUserId
                )
            }
            if (!faqOnlyActive && !savedModelPath.isNullOrBlank()) {
                loadGemmaModelFromPath(savedModelPath)
            }
        }
        viewModelScope.launch {
            checklistsState.collect { list ->
                val currentTask = _uiState.value.selectedTask
                val match = list.firstOrNull { it.taskType == currentTask.name }
                if (match != null) {
                    _uiState.update {
                        it.copy(
                            checkedDocIds = match.checkedDocIds.toSet(),
                            receiptVerified = match.receiptVerified
                        )
                    }
                }
            }
        }
    }

    /**
     * Immediately stops all active speech synthesis, playback, and microphone listening whenever the
     * active screen/tab changes, app goes to background, or modal dialogs open.
     */
    fun onScreenChanged(route: String) {
        stopAllAudio()
        _uiState.update { it.copy(isQuickMenuVisible = false, isBubbleVisible = false) }
    }

    fun stopAllAudio() {
        voiceManager.stopSpeaking()
        if (_uiState.value.isListening) {
            voiceManager.stopListening()
            _uiState.update { it.copy(isListening = false, huskyMood = HuskyMood.IDLE) }
        }
    }

    fun toggleLanguage() {
        stopAllAudio()
        val next = when (_uiState.value.language) {
            AppLanguage.ENGLISH -> AppLanguage.HINDI
            AppLanguage.HINDI -> AppLanguage.DOGRI
            AppLanguage.DOGRI -> AppLanguage.ENGLISH
        }
        _uiState.update { it.copy(language = next, selectedRegionalCode = next.code) }
        viewModelScope.launch {
            preferences.setLanguage(next)
            repository.updateUserPreference(next.code, _uiState.value.playbackSpeed)
        }
    }

    fun openLanguageMenu() {
        stopAllAudio()
        _uiState.update { it.copy(showLanguageMenu = true) }
    }

    fun dismissLanguageMenu() {
        _uiState.update { it.copy(showLanguageMenu = false) }
    }

    fun selectLanguageFromMenu(option: RegionalLanguageOption) {
        stopAllAudio()
        val mapped = AppLanguage.fromCode(option.langCode)
        _uiState.update {
            it.copy(
                language = mapped,
                selectedRegionalCode = option.langCode,
                showLanguageMenu = false,
                speechRetryCount = 0,
                assistantError = null,
                statusFeedbackMessage = BilingualStrings.pick(
                    mapped,
                    "Language set to ${option.displayName}",
                    "भाषा ${option.nativeName} चुनी गई",
                    "भासा ${option.nativeName} चुनी गेई"
                )
            )
        }
        viewModelScope.launch {
            preferences.setLanguage(mapped)
            repository.updateUserPreference(mapped.code, _uiState.value.playbackSpeed)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.80f, 1.20f)
        _uiState.update { it.copy(playbackSpeed = clamped) }
        viewModelScope.launch {
            preferences.setPlaybackSpeed(clamped)
            repository.updateUserPreference(_uiState.value.language.code, clamped)
        }
    }

    fun selectDistrictPack(districtCode: String) {
        val resolved = DistrictCatalog.findByCode(districtCode)
        _uiState.update { it.copy(selectedDistrictCode = resolved.code) }
        viewModelScope.launch {
            preferences.setSelectedDistrictCode(resolved.code)
            val result = repository.syncDistrictPack(resolved.code, _uiState.value.radiusKmFilter)
            _uiState.update { state ->
                state.copy(
                    isOnline = aiService.isOnline(),
                    lastSyncRolledBack = result.rolledBack,
                    lastSyncVerificationMessage = BilingualStrings.pick(
                        state.language,
                        result.messageEn,
                        result.messageHi
                    ),
                    statusFeedbackMessage = BilingualStrings.pick(
                        state.language,
                        result.messageEn,
                        result.messageHi
                    )
                )
            }
        }
    }

    fun setRadiusFilter(radiusKm: Int) {
        _uiState.update { it.copy(radiusKmFilter = radiusKm) }
    }

    fun openPincodePrompt() {
        _uiState.update { it.copy(showPincodePrompt = true, pincodeError = null) }
    }

    fun dismissPincodePrompt() {
        if (_uiState.value.activePincode.isNotBlank()) {
            _uiState.update { it.copy(showPincodePrompt = false, pincodeError = null) }
        }
    }

    fun submitPincode(rawPincode: String) {
        val cleanPin = rawPincode.trim()
        val lang = _uiState.value.language
        if (!PincodeFacilityResolver.isValidIndianPincode(cleanPin)) {
            _uiState.update {
                it.copy(
                    pincodeError = BilingualStrings.pick(
                        lang,
                        "Please enter a valid 6-digit Indian pincode (e.g. 226001).",
                        "कृपया सही 6 अंकों का भारतीय पिनकोड दर्ज करें (जैसे 226001)।",
                        "कृपा करियै सही 6 अंका दा भारतीय पिनकोड भरो (जि’यां 180001 जां 226001)।"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOnline = aiService.isOnline(),
                    isResolvingPincode = true,
                    pincodeError = null
                )
            }
            val onlineNow = aiService.isOnline()
            val result = repository.resolveAndAddPincodeFacilities(
                pincode = cleanPin,
                isOnline = onlineNow,
                userId = currentUserId
            )
            result.onSuccess { resolved ->
                val districtCode = PincodeFacilityResolver.districtCodeForPincode(resolved.pincode)
                preferences.setActivePincode(
                    pincode = resolved.pincode,
                    areaLabel = resolved.areaLabel,
                    lat = resolved.centerLat,
                    lon = resolved.centerLon
                )
                preferences.setSelectedDistrictCode(districtCode)
                val branchCount = resolved.facilities.count { it.parsedType() == FacilityType.BRANCH }
                val bcCount = resolved.facilities.count { it.parsedType() == FacilityType.BC_POINT }
                val poCount = resolved.facilities.count { it.parsedType() == FacilityType.IPPB }
                val feedback = BilingualStrings.pick(
                    lang,
                    "PIN ${resolved.pincode} (${resolved.areaLabel}): Added $branchCount Bank(s), $bcCount BC Mitra & $poCount Post Office Bank.",
                    "पिनकोड ${resolved.pincode} (${resolved.areaLabel}): $branchCount बैंक, $bcCount बैंक मित्र (BC) और $poCount डाकघर बैंक जोड़े गए।",
                    "पिनकोड ${resolved.pincode} (${resolved.areaLabel}): $branchCount बैंक, $bcCount बैंक मित्र (BC) ते $poCount डाकघर बैंक जोड़े गे।"
                )
                _uiState.update { state ->
                    state.copy(
                        activePincode = resolved.pincode,
                        activePincodeArea = resolved.areaLabel,
                        activePincodeLat = resolved.centerLat,
                        activePincodeLon = resolved.centerLon,
                        selectedDistrictCode = districtCode,
                        radiusKmFilter = 10,
                        isResolvingPincode = false,
                        pincodeError = null,
                        showPincodePrompt = false,
                        lastSyncRolledBack = false,
                        statusFeedbackMessage = feedback
                    )
                }
            }.onFailure { err ->
                val offlineRes = repository.resolveAndAddPincodeFacilities(
                    pincode = cleanPin,
                    isOnline = false,
                    userId = currentUserId
                )
                offlineRes.onSuccess { resolved ->
                    val districtCode = PincodeFacilityResolver.districtCodeForPincode(resolved.pincode)
                    preferences.setActivePincode(
                        pincode = resolved.pincode,
                        areaLabel = resolved.areaLabel,
                        lat = resolved.centerLat,
                        lon = resolved.centerLon
                    )
                    preferences.setSelectedDistrictCode(districtCode)
                    _uiState.update { state ->
                        state.copy(
                            activePincode = resolved.pincode,
                            activePincodeArea = resolved.areaLabel,
                            activePincodeLat = resolved.centerLat,
                            activePincodeLon = resolved.centerLon,
                            selectedDistrictCode = districtCode,
                            radiusKmFilter = 10,
                            isResolvingPincode = false,
                            pincodeError = null,
                            showPincodePrompt = false,
                            statusFeedbackMessage = BilingualStrings.pick(
                                lang,
                                "Added offline Bank, BC & Post Office centers for PIN ${resolved.pincode}.",
                                "पिनकोड ${resolved.pincode} के ऑफलाइन बैंक, BC और डाकघर केंद्र जोड़े गए।",
                                "पिनकोड ${resolved.pincode} दे ऑफलाइन बैंक, BC ते डाकघर केंदर जोड़े गे।"
                            )
                        )
                    }
                }.onFailure { fallbackErr ->
                    _uiState.update {
                        it.copy(
                            isResolvingPincode = false,
                            pincodeError = fallbackErr.localizedMessage ?: err.localizedMessage ?: BilingualStrings.pick(
                                lang,
                                "Could not load pincode centers. Please verify the 6-digit pincode.",
                                "पिनकोड केंद्र लोड नहीं हो सके। कृपया 6 अंकों का पिनकोड जांचें।",
                                "पिनकोड केंदर लोड नेईं होए। कृपा करियै 6 अंका दा पिनकोड जांचो।"
                            )
                        )
                    }
                }
            }
        }
    }

    fun setLowRamFaqOnlyEnabled(faqOnly: Boolean) {
        val override = if (faqOnly) LowRamOverrideMode.FORCE_FAQ else LowRamOverrideMode.ALLOW_GEMMA
        val effectiveFaqOnly = DeviceMemoryHelper.shouldUseFaqOnlyMode(deviceMemoryStatus, override)
        _uiState.update { state ->
            state.copy(
                lowRamOverrideMode = override,
                isLowRamFaqOnlyActive = effectiveFaqOnly,
                statusFeedbackMessage = if (effectiveFaqOnly) {
                    BilingualStrings.pick(
                        state.language,
                        "FAQ-Only safeguard active (${deviceMemoryStatus.totalRamGbLabel} RAM protected).",
                        "FAQ-ओनली सुरक्षा चालू है (${deviceMemoryStatus.totalRamGbLabel} RAM सुरक्षित)।",
                        "FAQ-ओनली सुरक्षा चालू ऐ (${deviceMemoryStatus.totalRamGbLabel} RAM सुरक्षित)।"
                    )
                } else {
                    BilingualStrings.pick(
                        state.language,
                        "Full on-device SLM + Cloud chat enabled.",
                        "पूर्ण जेमा-2B और क्लाउड चैट चालू की गई।",
                        "पूरा जेमा-2B ते क्लाउड चैट चालू ऐ।"
                    )
                }
            )
        }
        viewModelScope.launch {
            preferences.setLowRamOverrideMode(override)
            if (effectiveFaqOnly) {
                aiService.unloadGemmaModel()
                _uiState.update { it.copy(gemmaModelState = GemmaModelState.NotLoaded) }
            }
        }
    }

    fun triggerDeltaSyncRefresh() {
        val currentState = _uiState.value
        if (currentState.activePincode.isNotBlank()) {
            submitPincode(currentState.activePincode)
            return
        }
        viewModelScope.launch {
            val result = repository.syncDistrictPack(
                districtCode = currentState.selectedDistrictCode,
                radiusKm = currentState.radiusKmFilter
            )
            _uiState.update {
                val localizedMsg = BilingualStrings.pick(it.language, result.messageEn, result.messageHi)
                it.copy(
                    isOnline = aiService.isOnline(),
                    lastSyncRolledBack = result.rolledBack,
                    lastSyncVerificationMessage = localizedMsg,
                    statusFeedbackMessage = localizedMsg
                )
            }
        }
    }

    fun toggleVoice() {
        val next = !_uiState.value.voiceEnabled
        _uiState.update { it.copy(voiceEnabled = next) }
        if (!next) voiceManager.stopSpeaking()
        viewModelScope.launch {
            preferences.setVoiceEnabled(next)
        }
    }

    fun testNaturalVoice() {
        val state = _uiState.value
        val sample = BilingualStrings.pick(
            state.language,
            "Namaste! I am your BankSathi guide. Always verify your printed receipt and never share your OTP.",
            "नमस्ते! मैं आपकी बैंकसाथी मददगार हूँ। हमेशा पक्की रसीद लें और कभी अपना OTP न बताएं।",
            "नमस्ते! मैं तुंदी बैंकसाथी मददगार आं। हमेशा पक्की रसीद लेओ ते कदें अपना OTP नेईं दस्सो।"
        )
        voiceManager.speak(sample, state.language, state.playbackSpeed)
    }

    fun openVoicePackInstaller() {
        val opened = voiceManager.openOfflineVoicePackInstaller()
        if (!opened) {
            val lang = _uiState.value.language
            _uiState.update {
                it.copy(
                    statusFeedbackMessage = BilingualStrings.pick(
                        lang,
                        "Open Android Settings > Accessibility > Text-to-speech > Google Speech Services to download Hindi & English India voices.",
                        "ऑफलाइन आवाज़ के लिए फ़ोन की Settings > Accessibility > Text-to-speech में Google की हिंदी/अंग्रेज़ी आवाज़ चुनें।",
                        "ऑफलाइन आवाज़ आस्तै फोन दी Settings > Accessibility > Text-to-speech च Google दी हिंदी/अंग्रेज़ी आवाज़ चुनो।"
                    )
                )
            }
        }
    }

    fun saveHuskyOffset(x: Float, y: Float) {
        _uiState.update { it.copy(huskyOffsetX = x, huskyOffsetY = y) }
        viewModelScope.launch {
            preferences.setHuskyOffset(x, y)
        }
    }

    fun dismissBubble() {
        voiceManager.stopSpeaking()
        _uiState.update { it.copy(isBubbleVisible = false, isQuickMenuVisible = false) }
    }

    fun speakCurrentGuidance() {
        val state = _uiState.value
        val text = BilingualStrings.pick(
            state.language,
            state.huskyBubbleEn,
            state.huskyBubbleHi,
            state.huskyBubbleDoi
        )
        voiceManager.speak(text, state.language, state.playbackSpeed)
    }

    fun onHuskyTapped(currentRoute: String = "home") {
        val wasMenuOpen = _uiState.value.isQuickMenuVisible
        if (wasMenuOpen) {
            _uiState.update { it.copy(isQuickMenuVisible = false, isBubbleVisible = false) }
            return
        }

        val tip = when (currentRoute) {
            "checklist" -> Triple(
                "Tap each document below as you pack it—and always confirm your paper receipt!",
                "अपना बैग तैयार करते समय हर कागज़ पर टिक लगाएं और पक्की रसीद ज़रूर लें!",
                "अपना थैला तैयार करदे होई हर कागज़ पर टिक लाओ ते पक्की रसीद ज़रूर लेओ!"
            )
            "facilities" -> Triple(
                "Enter your 6-digit pincode to see nearest Bank, BC & Post Office Bank centers.",
                "अपने पास के बैंक, बैंक मित्र (BC) और डाकघर देखने के लिए 6 अंकों का पिनकोड दर्ज करें।",
                "अपने नेड़े दे बैंक, बैंक मित्र (BC) ते डाकघर दिक्खने आस्तै 6 अंका दा पिनकोड भरो।"
            )
            "scam_sentinel" -> Triple(
                "Paste any suspicious SMS or caller demand here—BankSathi checks 1930 rules offline too.",
                "कोई भी संदिग्ध SMS यहाँ जांचें—बैंकसाथी बिना इंटरनेट भी 1930 नियम जांचता है।",
                "कोई बी शक्की SMS इत्थै जांचो—बैंकसाथी बिना इंटरनेट बी 1930 नियम जांचदा ऐ।"
            )
            else -> {
                val tips = listOf(
                    Triple(
                        "For ₹10,000 or less, your village Bank Mitra (BC) is closest and charges ₹0 extra fee.",
                        "₹10,000 तक के लिए गाँव का बैंक मित्र (बीसी) सबसे पास है। RBI नियम अनुसार अलग शुल्क मना है।",
                        "₹10,000 तक्कर तुंदे पिंड दा बैंक मित्र (BC) सबतों नेड़े ऐ—कोई बद्ध फीस नेईं लगदी।"
                    ),
                    Triple(
                        "Never share OTP, PIN, or click KYC links. Use Scan Scam below or call 1930 anytime.",
                        "कभी OTP, PIN न बताएं और न केवाईसी लिंक दबाएं। नीचे से तुरंत स्कैम जांचें।",
                        "कदें OTP जां PIN नेईं दस्सो। हेठ दित्ते बटन थमां फौरन ठगी जांच करो जां 1930 मिलाओ।"
                    ),
                    Triple(
                        "Always carry your original Aadhaar card and collect a printed receipt after AePS.",
                        "हमेशा असली आधार साथ रखें और अंगूठे के बाद छपी रसीद जरूर लें।",
                        "हमेशा असली आधार कन्नै रक्खो ते अंगूठे दे बाद छपी रसीद ज़रूर लेओ।"
                    )
                )
                tips[tipIndex++ % tips.size]
            }
        }

        _uiState.update {
            it.copy(
                huskyMood = HuskyMood.GUIDING,
                huskyBubbleEn = tip.first,
                huskyBubbleHi = tip.second,
                huskyBubbleDoi = tip.third,
                isBubbleVisible = true,
                isQuickMenuVisible = true
            )
        }
    }

    fun selectTask(task: BankingTask, amountInr: Int = _uiState.value.selectedAmountInr) {
        val rec = BankingRuleEngine.routeTask(task, amountInr)
        val savedChecklist = checklistsState.value.firstOrNull { it.taskType == task.name }
        val checkedSet = savedChecklist?.checkedDocIds?.toSet() ?: emptySet()
        val receiptDone = savedChecklist?.receiptVerified ?: false

        _uiState.update {
            it.copy(
                selectedTask = task,
                selectedAmountInr = amountInr,
                recommendation = rec,
                checkedDocIds = checkedSet,
                receiptVerified = receiptDone,
                huskyMood = HuskyMood.GUIDING,
                huskyBubbleEn = rec.reasonEn,
                huskyBubbleHi = rec.reasonHi,
                huskyBubbleDoi = rec.reasonDoi,
                isBubbleVisible = true,
                isQuickMenuVisible = false
            )
        }
        if (_uiState.value.voiceEnabled) {
            voiceManager.speak(
                BilingualStrings.pick(_uiState.value.language, rec.reasonEn, rec.reasonHi, rec.reasonDoi),
                _uiState.value.language,
                _uiState.value.playbackSpeed
            )
        }
    }

    fun toggleChecklistDocument(docId: String) {
        val current = _uiState.value
        val nextSet = if (current.checkedDocIds.contains(docId)) {
            current.checkedDocIds - docId
        } else {
            current.checkedDocIds + docId
        }
        _uiState.update { it.copy(checkedDocIds = nextSet) }
        viewModelScope.launch {
            repository.saveChecklist(
                userId = currentUserId,
                task = current.selectedTask,
                amountInr = current.selectedAmountInr,
                recommendedFacilityType = current.recommendation.facilityType,
                checkedDocIds = nextSet.toList(),
                receiptVerified = current.receiptVerified
            )
        }
    }

    fun markReceiptVerified(verified: Boolean) {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                receiptVerified = verified,
                huskyMood = if (verified) HuskyMood.GUIDING else HuskyMood.ALERT,
                huskyBubbleEn = if (verified) {
                    "Great! Always keep your printed receipt and verify the bank SMS balance."
                } else {
                    "Warning: Ask the BC Agent for a printed receipt before leaving the counter!"
                },
                huskyBubbleHi = if (verified) {
                    "बहुत बढ़िया! छपी रसीद संभालकर रखें और बैंक बैलेंस एसएमएस भी जांच लें।"
                } else {
                    "सावधान: काउंटर छोड़ने से पहले बैंक मित्र से छपी हुई रसीद जरूर मांगें!"
                },
                huskyBubbleDoi = if (verified) {
                    "बहुत बढ़िया! छपी रसीद संभाली रक्खो ते बैंक बैलेंस SMS बी जांचो।"
                } else {
                    "सावधान: काउंटर छड्डने थमां पैहले बैंक मित्र थमां छपी रसीद ज़रूर मंगो!"
                },
                isBubbleVisible = true,
                isQuickMenuVisible = false
            )
        }
        viewModelScope.launch {
            repository.saveChecklist(
                userId = currentUserId,
                task = current.selectedTask,
                amountInr = current.selectedAmountInr,
                recommendedFacilityType = current.recommendation.facilityType,
                checkedDocIds = current.checkedDocIds.toList(),
                receiptVerified = verified
            )
        }
    }

    fun startVoiceListening(onQueryReady: (String) -> Unit = {}) {
        _uiState.update {
            it.copy(
                isListening = true,
                partialSpeechText = "",
                huskyMood = HuskyMood.LISTENING,
                assistantError = null,
                isQuickMenuVisible = false
            )
        }
        voiceManager.startListening(
            language = _uiState.value.language,
            onPartial = { partial ->
                _uiState.update { it.copy(partialSpeechText = partial) }
            },
            onResult = { spoken ->
                _uiState.update {
                    it.copy(
                        isListening = false,
                        partialSpeechText = spoken,
                        speechRetryCount = 0
                    )
                }
                onQueryReady(spoken)
                askAssistant(spoken)
            },
            onError = { _ ->
                val nextRetry = _uiState.value.speechRetryCount + 1
                val openMenu = nextRetry >= 2
                val lang = _uiState.value.language
                val msg = if (openMenu) {
                    BilingualStrings.pick(
                        lang,
                        "Still unclear after 2 tries—never guessing. Pick a language or tap a topic.",
                        "दो बार कोशिश के बाद भी आवाज़ साफ नहीं मिली — कृपया भाषा चुनें या विषय दबाएं।",
                        "दो बारी कोशिश दे बाद बी आवाज़ साफ नेईं मिली — कृपा करियै भासा चुनो जां विषय दबाओ।"
                    )
                } else {
                    BilingualStrings.pick(
                        lang,
                        "Didn't catch that clearly (attempt $nextRetry/2). Please tap Speak and repeat.",
                        "आवाज़ साफ नहीं मिली (कोशिश $nextRetry/2)। कृपया फिर से बोलें।",
                        "आवाज़ साफ नेईं मिली (कोशिश $nextRetry/2)। कृपा करियै दुबारा बोलो।"
                    )
                }
                _uiState.update {
                    it.copy(
                        isListening = false,
                        huskyMood = HuskyMood.IDLE,
                        speechRetryCount = if (openMenu) 0 else nextRetry,
                        showLanguageMenu = openMenu,
                        assistantError = msg
                    )
                }
            }
        )
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
        _uiState.update { it.copy(isListening = false, huskyMood = HuskyMood.IDLE) }
    }

    fun clearChat() {
        assistantJob?.cancel()
        voiceManager.stopSpeaking()
        _uiState.update {
            it.copy(
                chatMessages = emptyList(),
                lastAnswer = null,
                assistantError = null,
                isAssistantThinking = false,
                huskyMood = HuskyMood.IDLE
            )
        }
    }

    fun speakMessage(text: String) {
        val s = _uiState.value
        if (text.isNotBlank()) {
            voiceManager.speak(text, s.language, s.playbackSpeed)
        }
    }

    fun askAssistant(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        assistantJob?.cancel()
        voiceManager.stopSpeaking()
        val now = System.currentTimeMillis()
        val userMsgId = "user_$now"
        val botMsgId = "bot_${now + 1}"
        val activeLang = _uiState.value.language

        val userMsg = ChatMessage(
            id = userMsgId,
            isUser = true,
            textEn = trimmed,
            textHi = trimmed,
            textDoi = trimmed,
            timestampMs = now
        )
        val pendingBotMsg = ChatMessage(
            id = botMsgId,
            isUser = false,
            textEn = "",
            textHi = "",
            textDoi = "",
            isStreaming = true,
            timestampMs = now + 1
        )

        assistantJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOnline = aiService.isOnline(),
                    isAssistantThinking = true,
                    assistantError = null,
                    huskyMood = HuskyMood.THINKING,
                    isQuickMenuVisible = false,
                    chatMessages = listOf(userMsg, pendingBotMsg)
                )
            }
            val faqsSnapshot = if (inMemoryAllFaqs.isNotEmpty()) {
                inMemoryAllFaqs
            } else {
                repository.getAllCachedFaqs().also { inMemoryAllFaqs = it }
            }
            val result = aiService.askAssistantChat(
                query = trimmed,
                language = activeLang,
                history = emptyList(),
                cachedFaqs = faqsSnapshot,
                districtCode = _uiState.value.selectedDistrictCode,
                lowRamFaqOnly = _uiState.value.isLowRamFaqOnlyActive,
                onPartialText = { partial ->
                    _uiState.update { state ->
                        val updatedList = state.chatMessages.map { msg ->
                            if (msg.id == botMsgId) {
                                msg.copy(textEn = partial, textHi = partial, textDoi = partial, isStreaming = true)
                            } else msg
                        }
                        state.copy(chatMessages = updatedList)
                    }
                }
            )
            result.onSuccess { answer ->
                val matchedTask = if (answer.abstained) null else answer.recommendedTask
                val updatedRec = if (matchedTask != null) {
                    BankingRuleEngine.routeTask(matchedTask, answer.suggestedAmountInr.coerceAtLeast(500))
                } else {
                    _uiState.value.recommendation
                }
                val isScam = answer.mood == HuskyMood.ALERT && !answer.abstained
                val fallbackNotice = if (answer.isFallbackFromError) {
                    BilingualStrings.pick(
                        activeLang,
                        "Live AI unavailable — showing verified offline BankSathi guidance.",
                        "लाइव AI उपलब्ध नहीं था — सत्यापित ऑफलाइन बैंकसाथी सलाह दिखाई जा रही है।",
                        "लाइव AI उपलब्ध नेईं हा — पक्की ऑफलाइन बैंकसाथी सलाह दस्सी गेई ऐ।"
                    )
                } else null
                _uiState.update { state ->
                    val finalizedList = state.chatMessages.map { msg ->
                        if (msg.id == botMsgId) {
                            msg.copy(
                                textEn = answer.replyEn,
                                textHi = answer.replyHi,
                                textDoi = answer.replyDoi.ifBlank { answer.replyHi },
                                sourceMode = answer.sourceMode,
                                recommendedTask = matchedTask,
                                suggestedAmountInr = answer.suggestedAmountInr,
                                isScamAlert = isScam,
                                abstained = answer.abstained,
                                sourceId = answer.sourceId,
                                verifiedOn = answer.verifiedOn,
                                isStreaming = false
                            )
                        } else msg
                    }
                    state.copy(
                        isAssistantThinking = false,
                        assistantError = null,
                        statusFeedbackMessage = fallbackNotice ?: state.statusFeedbackMessage,
                        chatMessages = finalizedList,
                        lastAnswer = answer,
                        selectedTask = matchedTask ?: state.selectedTask,
                        selectedAmountInr = if (matchedTask != null) answer.suggestedAmountInr else state.selectedAmountInr,
                        recommendation = updatedRec,
                        huskyMood = answer.mood,
                        huskyBubbleEn = answer.replyEn,
                        huskyBubbleHi = answer.replyHi,
                        huskyBubbleDoi = answer.replyDoi.ifBlank { answer.replyHi },
                        isBubbleVisible = true
                    )
                }
                if (_uiState.value.voiceEnabled) {
                    voiceManager.speak(
                        BilingualStrings.pick(_uiState.value.language, answer.replyEn, answer.replyHi, answer.replyDoi),
                        _uiState.value.language,
                        _uiState.value.playbackSpeed
                    )
                }
            }.onFailure {
                val offlineFallback = BankingRuleEngine.analyzeOfflineQuery(trimmed, faqsSnapshot)
                    .copy(isFallbackFromError = true)
                val matchedTask = if (offlineFallback.abstained) null else offlineFallback.recommendedTask
                val updatedRec = if (matchedTask != null) {
                    BankingRuleEngine.routeTask(matchedTask, offlineFallback.suggestedAmountInr.coerceAtLeast(500))
                } else {
                    _uiState.value.recommendation
                }
                val isScam = offlineFallback.mood == HuskyMood.ALERT && !offlineFallback.abstained
                val fallbackNotice = BilingualStrings.pick(
                    activeLang,
                    "Live AI unavailable — showing verified offline BankSathi guidance.",
                    "लाइव AI उपलब्ध नहीं था — सत्यापित ऑफलाइन बैंकसाथी सलाह दिखाई जा रही है।",
                    "लाइव AI उपलब्ध नेईं हा — पक्की ऑफलाइन बैंकसाथी सलाह दस्सी गेई ऐ।"
                )
                _uiState.update { state ->
                    val finalizedList = state.chatMessages.map { msg ->
                        if (msg.id == botMsgId) {
                            msg.copy(
                                textEn = offlineFallback.replyEn,
                                textHi = offlineFallback.replyHi,
                                textDoi = offlineFallback.replyDoi.ifBlank { offlineFallback.replyHi },
                                sourceMode = offlineFallback.sourceMode,
                                recommendedTask = matchedTask,
                                suggestedAmountInr = offlineFallback.suggestedAmountInr,
                                isScamAlert = isScam,
                                abstained = offlineFallback.abstained,
                                sourceId = offlineFallback.sourceId,
                                verifiedOn = offlineFallback.verifiedOn,
                                isStreaming = false
                            )
                        } else msg
                    }
                    state.copy(
                        isAssistantThinking = false,
                        assistantError = null,
                        statusFeedbackMessage = fallbackNotice,
                        chatMessages = finalizedList,
                        lastAnswer = offlineFallback,
                        selectedTask = matchedTask ?: state.selectedTask,
                        selectedAmountInr = if (matchedTask != null) offlineFallback.suggestedAmountInr else state.selectedAmountInr,
                        recommendation = updatedRec,
                        huskyMood = offlineFallback.mood,
                        huskyBubbleEn = offlineFallback.replyEn,
                        huskyBubbleHi = offlineFallback.replyHi,
                        huskyBubbleDoi = offlineFallback.replyDoi.ifBlank { offlineFallback.replyHi },
                        isBubbleVisible = true
                    )
                }
                if (_uiState.value.voiceEnabled) {
                    voiceManager.speak(
                        BilingualStrings.pick(
                            _uiState.value.language,
                            offlineFallback.replyEn,
                            offlineFallback.replyHi,
                            offlineFallback.replyDoi
                        ),
                        _uiState.value.language,
                        _uiState.value.playbackSpeed
                    )
                }
            }
        }
    }

    fun analyzeScam(snippet: String) {
        if (snippet.isBlank()) return
        scamJob?.cancel()
        voiceManager.stopSpeaking()
        scamJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOnline = aiService.isOnline(),
                    isScamAnalyzing = true,
                    scamError = null,
                    huskyMood = HuskyMood.THINKING,
                    isQuickMenuVisible = false
                )
            }
            val result = aiService.analyzeScam(
                snippet = snippet,
                language = _uiState.value.language,
                lowRamFaqOnly = _uiState.value.isLowRamFaqOnlyActive
            )
            val activeLang = _uiState.value.language
            result.onSuccess { analysis ->
                val mood = if (analysis.riskLevel == RiskLevel.HIGH_RISK) HuskyMood.ALERT else HuskyMood.GUIDING
                val fallbackNotice = if (analysis.isFallbackFromError) {
                    BilingualStrings.pick(
                        activeLang,
                        "Live AI unavailable — checked with offline RBI 1930 scam rules.",
                        "लाइव AI उपलब्ध नहीं — ऑफलाइन RBI 1930 नियमों से जांच की गई।",
                        "लाइव AI उपलब्ध नेईं — ऑफलाइन RBI 1930 नियमें कन्नै जांच कीती गेई।"
                    )
                } else null
                _uiState.update {
                    it.copy(
                        isScamAnalyzing = false,
                        scamError = null,
                        statusFeedbackMessage = fallbackNotice ?: it.statusFeedbackMessage,
                        lastScamResult = analysis,
                        huskyMood = mood,
                        huskyBubbleEn = analysis.explanationEn,
                        huskyBubbleHi = analysis.explanationHi,
                        huskyBubbleDoi = analysis.explanationDoi.ifBlank { analysis.explanationHi },
                        isBubbleVisible = true
                    )
                }
                if (_uiState.value.voiceEnabled) {
                    voiceManager.speak(
                        BilingualStrings.pick(
                            _uiState.value.language,
                            analysis.explanationEn,
                            analysis.explanationHi,
                            analysis.explanationDoi
                        ),
                        _uiState.value.language,
                        _uiState.value.playbackSpeed
                    )
                }
                repository.saveScamCheck(
                    userId = currentUserId,
                    inputSnippet = snippet,
                    riskLevel = analysis.riskLevel,
                    explanationEn = analysis.explanationEn,
                    explanationHi = analysis.explanationHi,
                    sourceMode = analysis.sourceMode
                )
            }.onFailure {
                val fallback = BankingRuleEngine.analyzeOfflineScam(snippet).copy(isFallbackFromError = true)
                val fallbackNotice = BilingualStrings.pick(
                    activeLang,
                    "Live AI unavailable — checked with offline RBI 1930 scam rules.",
                    "लाइव AI उपलब्ध नहीं — ऑफलाइन RBI 1930 नियमों से जांच की गई।",
                    "लाइव AI उपलब्ध नेईं — ऑफलाइन RBI 1930 नियमें कन्नै जांच कीती गेई।"
                )
                _uiState.update {
                    it.copy(
                        isScamAnalyzing = false,
                        scamError = null,
                        statusFeedbackMessage = fallbackNotice,
                        lastScamResult = fallback,
                        huskyMood = if (fallback.riskLevel == RiskLevel.HIGH_RISK) HuskyMood.ALERT else HuskyMood.GUIDING,
                        huskyBubbleEn = fallback.explanationEn,
                        huskyBubbleHi = fallback.explanationHi,
                        huskyBubbleDoi = fallback.explanationDoi.ifBlank { fallback.explanationHi },
                        isBubbleVisible = true
                    )
                }
                if (_uiState.value.voiceEnabled) {
                    voiceManager.speak(
                        BilingualStrings.pick(
                            _uiState.value.language,
                            fallback.explanationEn,
                            fallback.explanationHi,
                            fallback.explanationDoi
                        ),
                        _uiState.value.language,
                        _uiState.value.playbackSpeed
                    )
                }
                repository.saveScamCheck(
                    userId = currentUserId,
                    inputSnippet = snippet,
                    riskLevel = fallback.riskLevel,
                    explanationEn = fallback.explanationEn,
                    explanationHi = fallback.explanationHi,
                    sourceMode = fallback.sourceMode
                )
            }
        }
    }

    fun submitFacilityReport(
        facility: FacilityRecord,
        status: ReportStatus,
        receiptReceived: Boolean?
    ) {
        viewModelScope.launch {
            val res = repository.submitFacilityStatusReport(
                userId = currentUserId,
                facility = facility,
                reportStatus = status,
                receiptReceived = receiptReceived
            )
            res.onSuccess {
                val msg = if (receiptReceived == false) {
                    BilingualStrings.pick(
                        _uiState.value.language,
                        "Status updated! Remember to always ask for a printed paper receipt.",
                        "स्थिति अपडेट हो गई! हमेशा छपी हुई कागज की रसीद जरूर मांगें।",
                        "स्थिति अपडेट होई गेई! हमेशा छपी रसीद ज़रूर मंगो।"
                    )
                } else {
                    BilingualStrings.pick(
                        _uiState.value.language,
                        "Thank you! Community status updated.",
                        "धन्यवाद! गाँव के सभी लोगों के लिए स्थिति अपडेट हो गई।",
                        "धन्यवाद! पिंड दे सारे लोकें आस्तै जानकारी अपडेट होई गेई।"
                    )
                }
                _uiState.update {
                    it.copy(
                        statusFeedbackMessage = msg,
                        receiptVerified = receiptReceived ?: it.receiptVerified,
                        huskyMood = if (receiptReceived == false) HuskyMood.ALERT else HuskyMood.GUIDING,
                        huskyBubbleEn = if (receiptReceived == false) {
                            "Next time insist on a printed AePS receipt before leaving!"
                        } else {
                            "Thanks for updating verified live status for your village!"
                        },
                        huskyBubbleHi = if (receiptReceived == false) {
                            "अगली बार काउंटर छोड़ने से पहले छपी रसीद जरूर लें!"
                        } else {
                            "गाँव की मदद के लिए सत्यापित जानकारी साझा करने का धन्यवाद!"
                        },
                        huskyBubbleDoi = if (receiptReceived == false) {
                            "अगली बारी काउंटर छड्डने थमां पैहले छपी रसीद ज़रूर लेओ!"
                        } else {
                            "पिंड दी मदद आस्तै पक्की जानकारी सांझी करने दा धन्यवाद!"
                        },
                        isBubbleVisible = true
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusFeedbackMessage = err.localizedMessage ?: "Failed to update facility status.")
                }
            }
        }
    }

    fun addFacility(
        nameEn: String,
        nameHi: String,
        type: FacilityType,
        villageOrArea: String,
        distanceMeters: Long
    ) {
        if (nameEn.isBlank() || villageOrArea.isBlank()) return
        val state = _uiState.value
        val districtCode = if (state.activePincode.isNotBlank()) {
            PincodeFacilityResolver.districtCodeForPincode(state.activePincode)
        } else {
            state.selectedDistrictCode
        }
        viewModelScope.launch {
            val res = repository.addFacility(
                userId = currentUserId,
                nameEn = nameEn,
                nameHi = nameHi.ifBlank { nameEn },
                facilityType = type,
                villageOrArea = villageOrArea,
                distanceMeters = distanceMeters,
                districtCode = districtCode,
                centerLat = if (state.activePincode.isNotBlank()) state.activePincodeLat else null,
                centerLon = if (state.activePincode.isNotBlank()) state.activePincodeLon else null
            )
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        statusFeedbackMessage = BilingualStrings.pick(
                            it.language,
                            "New touchpoint added to verified directory.",
                            "नया केंद्र सत्यापित सूची में जुड़ गया है।",
                            "नमां केंद्र पक्की सूची च जुड़या गेआ ऐ।"
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusFeedbackMessage = err.localizedMessage ?: "Could not add facility.")
                }
            }
        }
    }

    fun registerSupabaseAccount(
        email: String,
        password: String,
        fullName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackendAuthLoading = true, backendAuthError = null) }
            val result = repository.registerBackendUser(email, password, fullName)
            result.onSuccess { resp ->
                val session = com.example.data.local.BackendAuthSession(
                    accessToken = resp.accessToken,
                    uid = resp.user.uid,
                    email = resp.user.email ?: email.trim().lowercase(),
                    fullName = resp.user.fullName ?: fullName.trim(),
                    role = resp.user.role
                )
                preferences.setBackendAuthSession(session)
                _uiState.update {
                    it.copy(
                        isBackendAuthLoading = false,
                        backendAuthError = null,
                        isSignedIn = true,
                        backendUserEmail = session.email,
                        backendUserName = session.fullName,
                        userRole = UserRole.fromApi(session.role),
                        statusFeedbackMessage = BilingualStrings.pick(
                            it.language,
                            "Account created & signed in as ${session.email} (${session.role}).",
                            "खाता बन गया: ${session.email} (${session.role})।",
                            "खाता बणी गेआ: ${session.email} (${session.role})।"
                        )
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBackendAuthLoading = false,
                        backendAuthError = err.localizedMessage ?: "Registration failed."
                    )
                }
            }
        }
    }

    fun loginSupabaseAccount(
        email: String,
        password: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackendAuthLoading = true, backendAuthError = null) }
            val result = repository.loginBackendUser(email, password)
            result.onSuccess { resp ->
                val session = com.example.data.local.BackendAuthSession(
                    accessToken = resp.accessToken,
                    uid = resp.user.uid,
                    email = resp.user.email ?: email.trim().lowercase(),
                    fullName = resp.user.fullName ?: "",
                    role = resp.user.role
                )
                preferences.setBackendAuthSession(session)
                _uiState.update {
                    it.copy(
                        isBackendAuthLoading = false,
                        backendAuthError = null,
                        isSignedIn = true,
                        backendUserEmail = session.email,
                        backendUserName = session.fullName,
                        userRole = UserRole.fromApi(session.role),
                        statusFeedbackMessage = BilingualStrings.pick(
                            it.language,
                            "Signed in as ${session.email} (${session.role}).",
                            "साइन इन सफल: ${session.email} (${session.role})।",
                            "साइन इन होई गेआ: ${session.email} (${session.role})।"
                        )
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBackendAuthLoading = false,
                        backendAuthError = err.localizedMessage ?: "Sign in failed."
                    )
                }
            }
        }
    }

    fun clearBackendAuthError() {
        _uiState.update { it.copy(backendAuthError = null) }
    }

    fun signOutBackendAccount() {
        viewModelScope.launch {
            com.example.data.remote.BackendSessionStore.clear()
            preferences.setBackendAuthSession(null)
            if (!isSignedIn) {
                _uiState.update {
                    it.copy(
                        isSignedIn = false,
                        backendUserEmail = null,
                        backendUserName = null,
                        userRole = UserRole.CITIZEN
                    )
                }
            }
        }
    }

    fun verifyFacilityAsReviewer(facility: FacilityRecord) {
        val lang = _uiState.value.language
        if (!_uiState.value.isSignedIn) {
            _uiState.update {
                it.copy(
                    statusFeedbackMessage = BilingualStrings.pick(
                        lang,
                        "401 Sign-In Required: Open the AI & Account button to sign in with Email or Google.",
                        "401 साइन-इन आवश्यक: आधिकारिक सत्यापन के लिए ईमेल या Google से साइन इन करें।",
                        "401 साइन-इन ज़रूरी: पक्की जांच आस्तै ईमेल जां Google कन्नै साइन इन करो।"
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val res = repository.verifyFacilityOfficial(
                userId = currentUserId,
                facility = facility,
                cashStatus = CashStatus.CASH_READY
            )
            res.onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        statusFeedbackMessage = BilingualStrings.pick(
                            lang,
                            "Verified '${updated.nameEn}' for ${updated.verifiedDate}.",
                            "'${updated.nameHi}' आज (${updated.verifiedDate}) के लिए सत्यापित हो गया।",
                            "'${updated.nameHi}' अज्ज (${updated.verifiedDate}) आस्तै जांचेआ गेआ।"
                        )
                    )
                }
            }.onFailure { err ->
                val raw = err.localizedMessage ?: "403 Forbidden: Verifier role required."
                val display = if (raw.contains("403")) {
                    BilingualStrings.pick(
                        lang,
                        "403 Protected Route: Only VERIFIER or ADMIN accounts can stamp official facility verification.",
                        "403 सुरक्षित रूट: केवल VERIFIER या ADMIN खाता ही आधिकारिक सत्यापन कर सकता है।",
                        "403 सुरक्षित रूट: सिर्फ VERIFIER जां ADMIN खाता ही पक्की जांच करी सकदा ऐ।"
                    )
                } else {
                    raw
                }
                _uiState.update { it.copy(statusFeedbackMessage = display) }
            }
        }
    }

    fun clearStatusFeedback() {
        _uiState.update { it.copy(statusFeedbackMessage = null) }
    }

    fun importAndLoadGemmaModel(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(gemmaModelState = GemmaModelState.Loading) }
            val imported = aiService.importGemmaModelFromUri(uri, displayName)
            imported.onSuccess { path ->
                preferences.setGemmaModelPath(path)
                val state = aiService.loadGemmaModel(path)
                _uiState.update { it.copy(gemmaModelState = state) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(gemmaModelState = GemmaModelState.Error(err.localizedMessage ?: "Import failed"))
                }
            }
        }
    }

    private fun loadGemmaModelFromPath(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(gemmaModelState = GemmaModelState.Loading) }
            val state = aiService.loadGemmaModel(path)
            _uiState.update { it.copy(gemmaModelState = state) }
        }
    }

    fun unloadGemmaModel() {
        viewModelScope.launch {
            aiService.unloadGemmaModel()
            preferences.setGemmaModelPath(null)
            _uiState.update { it.copy(gemmaModelState = GemmaModelState.NotLoaded) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
        aiService.shutdown()
    }

    private companion object {
        const val TAG = "BankSathiVM"
    }
}
