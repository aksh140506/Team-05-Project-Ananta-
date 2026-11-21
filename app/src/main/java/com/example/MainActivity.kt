package com.example

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.DeviceMemoryHelper
import com.example.ai.GemmaModelState
import com.example.ai.HybridAiService
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOutGoogle
import com.example.data.local.AppPreferences
import com.example.data.local.GraminSetuDatabase
import com.example.data.model.AppLanguage
import com.example.data.repository.GraminSetuRepository
import com.example.firebase.configureAppCheck
import com.example.ui.GraminSetuViewModel
import com.example.ui.components.MovableHuskyOverlay
import com.example.ui.i18n.BilingualStrings
import com.example.ui.screens.FacilitiesScreen
import com.example.ui.screens.GemmaAndAccountDialog
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.ScamSentinelScreen
import com.example.ui.screens.TaskChecklistScreen
import com.example.ui.theme.GraminSetuTheme
import com.example.voice.VoiceGuideManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_HOME = "home"
const val ROUTE_CHECKLIST = "checklist"
const val ROUTE_FACILITIES = "facilities"
const val ROUTE_SCAM = "scam_sentinel"

private data class NavDestinationItem(
    val route: String,
    val labelEn: String,
    val labelHi: String,
    val labelDoi: String,
    val icon: ImageVector
)

private val navDestinations = listOf(
    NavDestinationItem(ROUTE_HOME, "Home", "होम", "होम", Icons.Rounded.Home),
    NavDestinationItem(ROUTE_CHECKLIST, "Bag Check", "कागज़", "कागज़", Icons.Rounded.Checklist),
    NavDestinationItem(ROUTE_FACILITIES, "Live Status", "केंद्र", "केंद्र", Icons.Rounded.LocationOn),
    NavDestinationItem(ROUTE_SCAM, "Scam Guard", "सुरक्षा", "सुरक्षा", Icons.Rounded.Shield)
)

class MainActivity : ComponentActivity() {
    private val incomingSharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure App Check with launch intent before Compose UI or ViewModels
        configureAppCheck(this, intent)
        extractSharedText(intent)

        enableEdgeToEdge()
        setContent {
            GraminSetuTheme {
                AppNavigation(
                    sharedScamText = incomingSharedText.value,
                    onSharedScamTextConsumed = { incomingSharedText.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)
    }

    private fun extractSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!shared.isNullOrBlank()) {
                incomingSharedText.value = shared
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    sharedScamText: String? = null,
    onSharedScamTextConsumed: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val effectiveUserId = currentUser?.uid ?: GraminSetuRepository.LOCAL_DEVICE_USER_ID
    val isSignedIn = currentUser != null

    key(effectiveUserId) {
        AuthenticatedGraminSetuApp(
            currentUserId = effectiveUserId,
            userEmail = currentUser?.email,
            isSignedIn = isSignedIn,
            sharedScamText = sharedScamText,
            onSharedScamTextConsumed = onSharedScamTextConsumed
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedGraminSetuApp(
    currentUserId: String,
    userEmail: String?,
    isSignedIn: Boolean,
    sharedScamText: String?,
    onSharedScamTextConsumed: () -> Unit
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: GraminSetuViewModel = viewModel(
        key = "$currentUserId-$isSignedIn",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val roomDb = GraminSetuDatabase.getInstance(app)
                val repository = GraminSetuRepository(
                    db = firestore,
                    facilityDao = roomDb.facilityDao(),
                    checklistDao = roomDb.checklistDao(),
                    scamCheckDao = roomDb.scamCheckDao(),
                    bankSathiDao = roomDb.bankSathiDao(),
                    isCloudSyncEnabled = isSignedIn,
                    roomDb = roomDb
                )
                GraminSetuViewModel(
                    repository = repository,
                    aiService = HybridAiService(app),
                    voiceManager = VoiceGuideManager(app),
                    preferences = AppPreferences(app),
                    currentUserId = currentUserId,
                    deviceMemoryStatus = DeviceMemoryHelper.inspect(app),
                    isSignedIn = isSignedIn
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val facilitiesState by viewModel.facilitiesState.collectAsStateWithLifecycle()
    val cachedFacilities by viewModel.cachedFacilities.collectAsStateWithLifecycle()
    val scamHistory by viewModel.scamHistoryState.collectAsStateWithLifecycle()
    val cachedFaqs by viewModel.cachedFaqsState.collectAsStateWithLifecycle()
    val scamRules by viewModel.scamRulesState.collectAsStateWithLifecycle()
    val syncMeta by viewModel.syncMetaState.collectAsStateWithLifecycle()

    val facilitiesList = remember(facilitiesState, cachedFacilities) {
        when (val s = facilitiesState) {
            is com.example.ui.DataUiState.Success -> (s.data + cachedFacilities).distinctBy { it.id }
            else -> cachedFacilities
        }
    }

    LaunchedEffect(uiState.statusFeedbackMessage) {
        val msg = uiState.statusFeedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusFeedback()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME
    var showEngineDialog by remember { mutableStateOf(false) }
    var optionalAuthError by remember { mutableStateOf<String?>(null) }

    var lastSharedSnippet by remember { mutableStateOf<String?>(null) }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    var previousRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        if (previousRoute != null && previousRoute != currentRoute) {
            viewModel.onScreenChanged(currentRoute)
        }
        previousRoute = currentRoute
    }

    LaunchedEffect(showEngineDialog, uiState.showLanguageMenu) {
        if (showEngineDialog || uiState.showLanguageMenu) {
            viewModel.stopAllAudio()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.stopAllAudio()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val quickVoicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            navigateToTab(ROUTE_HOME)
            viewModel.startVoiceListening { _ -> }
        }
    }

    LaunchedEffect(sharedScamText) {
        val text = sharedScamText?.trim()
        if (!text.isNullOrBlank()) {
            lastSharedSnippet = text
            navigateToTab(ROUTE_SCAM)
            viewModel.analyzeScam(text)
            onSharedScamTextConsumed()
        }
    }

    val lang = uiState.language

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "BankSathi",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    uiState.isLowRamFaqOnlyActive -> MaterialTheme.colorScheme.tertiaryContainer
                                    uiState.gemmaModelState is GemmaModelState.Ready -> MaterialTheme.colorScheme.secondaryContainer
                                    uiState.fastApiConfigured && uiState.isOnline -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = when {
                                        uiState.isLowRamFaqOnlyActive ->
                                            BilingualStrings.pick(lang, "FAQ-Only", "FAQ-मोड", "FAQ-मोड")
                                        uiState.gemmaModelState is GemmaModelState.Ready ->
                                            BilingualStrings.pick(lang, "Gemma-2B", "जेमा-2B", "जेमा-2B")
                                        uiState.fastApiConfigured && uiState.isOnline ->
                                            BilingualStrings.pick(lang, "FastAPI RAG", "लाइव RAG", "लाइव RAG")
                                        uiState.isOnline ->
                                            BilingualStrings.pick(lang, "Hybrid AI", "हाइब्रिड एआई", "हाइब्रिड AI")
                                        else ->
                                            BilingualStrings.pick(lang, "Offline", "ऑफलाइन", "ऑफलाइन")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        uiState.isLowRamFaqOnlyActive -> MaterialTheme.colorScheme.onTertiaryContainer
                                        uiState.gemmaModelState is GemmaModelState.Ready -> MaterialTheme.colorScheme.onSecondaryContainer
                                        uiState.fastApiConfigured && uiState.isOnline -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            onClick = { viewModel.toggleLanguage() },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .testTag("language_toggle_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = when (lang) {
                                        AppLanguage.ENGLISH -> "हिन्दी"
                                        AppLanguage.HINDI -> "डोगरी"
                                        AppLanguage.DOGRI -> "EN"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleVoice() },
                            modifier = Modifier.testTag("voice_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.voiceEnabled) Icons.AutoMirrored.Rounded.VolumeUp
                                else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = BilingualStrings.pick(lang, "Toggle voice", "आवाज़ बदलें", "आवाज़ बदलो"),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { showEngineDialog = true },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("engine_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = BilingualStrings.pick(lang, "AI model & account", "एआई मॉडल व खाता", "AI मॉडल ते खाता")
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = BilingualStrings.pick(lang, dest.labelEn, dest.labelHi, dest.labelDoi)
                                    )
                                },
                                label = {
                                    Text(
                                        text = BilingualStrings.pick(lang, dest.labelEn, dest.labelHi, dest.labelDoi),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = BilingualStrings.pick(lang, dest.labelEn, dest.labelHi, dest.labelDoi)
                                    )
                                },
                                label = {
                                    Text(BilingualStrings.pick(lang, dest.labelEn, dest.labelHi, dest.labelDoi))
                                },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(ROUTE_HOME) {
                            HomeHubScreen(
                                uiState = uiState,
                                facilities = facilitiesList,
                                onTaskSelected = { task ->
                                    viewModel.selectTask(task)
                                    navigateToTab(ROUTE_CHECKLIST)
                                },
                                onOpenFacilities = { navigateToTab(ROUTE_FACILITIES) },
                                onOpenScamSentinel = { navigateToTab(ROUTE_SCAM) },
                                onOpenLanguageMenu = { viewModel.openLanguageMenu() },
                                onAskText = { query -> viewModel.askAssistant(query) },
                                onStartVoice = {
                                    viewModel.startVoiceListening { _ -> }
                                },
                                onStopVoice = { viewModel.stopVoiceListening() },
                                onClearChat = { viewModel.clearChat() },
                                onSpeakText = { text -> viewModel.speakMessage(text) }
                            )
                        }
                        composable(ROUTE_CHECKLIST) {
                            TaskChecklistScreen(
                                uiState = uiState,
                                facilities = facilitiesList,
                                cachedFaqs = cachedFaqs,
                                onSelectTaskAndAmount = { task, amount ->
                                    viewModel.selectTask(task, amount)
                                },
                                onToggleDocument = { docId ->
                                    viewModel.toggleChecklistDocument(docId)
                                },
                                onVerifyReceipt = { verified ->
                                    viewModel.markReceiptVerified(verified)
                                },
                                onViewAllFacilities = { navigateToTab(ROUTE_FACILITIES) }
                            )
                        }
                        composable(ROUTE_FACILITIES) {
                            FacilitiesScreen(
                                language = lang,
                                facilitiesState = facilitiesState,
                                cachedFacilities = facilitiesList,
                                selectedDistrictCode = uiState.selectedDistrictCode,
                                radiusKmFilter = uiState.radiusKmFilter,
                                syncMeta = syncMeta,
                                lastSyncRolledBack = uiState.lastSyncRolledBack,
                                activePincode = uiState.activePincode,
                                activePincodeArea = uiState.activePincodeArea,
                                activePincodeLat = uiState.activePincodeLat,
                                activePincodeLon = uiState.activePincodeLon,
                                isResolvingPincode = uiState.isResolvingPincode,
                                pincodeError = uiState.pincodeError,
                                showPincodePrompt = uiState.showPincodePrompt,
                                userRole = uiState.userRole,
                                isSignedIn = uiState.isSignedIn,
                                onSubmitPincode = { pin -> viewModel.submitPincode(pin) },
                                onRequestChangePincode = { viewModel.openPincodePrompt() },
                                onDismissPincodePrompt = { viewModel.dismissPincodePrompt() },
                                onSelectDistrict = { districtCode ->
                                    viewModel.selectDistrictPack(districtCode)
                                },
                                onRadiusFilterChange = { radius ->
                                    viewModel.setRadiusFilter(radius)
                                },
                                onRefreshSync = { viewModel.triggerDeltaSyncRefresh() },
                                onSubmitReport = { facility, status, receipt ->
                                    viewModel.submitFacilityReport(facility, status, receipt)
                                },
                                onVerifyFacility = { facility ->
                                    viewModel.verifyFacilityAsReviewer(facility)
                                },
                                onAddFacility = { nameEn, nameHi, type, area, dist ->
                                    viewModel.addFacility(nameEn, nameHi, type, area, dist)
                                }
                            )
                        }
                        composable(ROUTE_SCAM) {
                            ScamSentinelScreen(
                                uiState = uiState,
                                history = scamHistory,
                                scamRulesCount = scamRules.size.coerceAtLeast(5),
                                initialSharedText = lastSharedSnippet,
                                onAnalyzeScam = { snippet -> viewModel.analyzeScam(snippet) }
                            )
                        }
                    }

                    // Movable, guiding fluffy orange husky overlay across all screens
                    MovableHuskyOverlay(
                        mood = uiState.huskyMood,
                        bubbleText = BilingualStrings.pick(
                            lang,
                            uiState.huskyBubbleEn,
                            uiState.huskyBubbleHi,
                            uiState.huskyBubbleDoi
                        ),
                        isBubbleVisible = uiState.isBubbleVisible,
                        isQuickMenuVisible = uiState.isQuickMenuVisible,
                        voiceEnabled = uiState.voiceEnabled,
                        language = lang,
                        savedOffsetX = uiState.huskyOffsetX,
                        savedOffsetY = uiState.huskyOffsetY,
                        onOffsetCommitted = { x, y -> viewModel.saveHuskyOffset(x, y) },
                        onHuskyTapped = { viewModel.onHuskyTapped(currentRoute) },
                        onSpeakBubble = { viewModel.speakCurrentGuidance() },
                        onToggleMute = { viewModel.toggleVoice() },
                        onDismissBubble = { viewModel.dismissBubble() },
                        onQuickVoiceAsk = {
                            viewModel.dismissBubble()
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                navigateToTab(ROUTE_HOME)
                                viewModel.startVoiceListening { _ -> }
                            } else {
                                quickVoicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onQuickScanScam = {
                            viewModel.dismissBubble()
                            navigateToTab(ROUTE_SCAM)
                        },
                        onQuickOpenBag = {
                            viewModel.dismissBubble()
                            navigateToTab(ROUTE_CHECKLIST)
                        },
                        onQuickNearestCash = {
                            viewModel.dismissBubble()
                            navigateToTab(ROUTE_FACILITIES)
                        }
                    )
                }
            }
        }
    }

    if (showEngineDialog) {
        GemmaAndAccountDialog(
            language = lang,
            gemmaModelState = uiState.gemmaModelState,
            playbackSpeed = uiState.playbackSpeed,
            syncMeta = syncMeta,
            deviceMemoryStatus = uiState.deviceMemoryStatus,
            isLowRamFaqOnlyActive = uiState.isLowRamFaqOnlyActive,
            fastApiConfigured = uiState.fastApiConfigured,
            bhashiniConfigured = uiState.bhashiniConfigured,
            googleTtsInstalled = uiState.googleTtsInstalled,
            lastSyncVerificationMessage = uiState.lastSyncVerificationMessage,
            userEmail = userEmail ?: uiState.backendUserEmail,
            userRole = uiState.userRole,
            isSignedIn = uiState.isSignedIn,
            isOnline = uiState.isOnline,
            authError = optionalAuthError ?: uiState.backendAuthError,
            isAuthLoading = uiState.isBackendAuthLoading,
            onPlaybackSpeedChange = { speed -> viewModel.setPlaybackSpeed(speed) },
            onLowRamFaqOnlyChange = { faqOnly -> viewModel.setLowRamFaqOnlyEnabled(faqOnly) },
            onRefreshDeltaSync = { viewModel.triggerDeltaSyncRefresh() },
            onTestNaturalVoice = { viewModel.testNaturalVoice() },
            onInstallOfflineVoicePack = { viewModel.openVoicePackInstaller() },
            onImportGemmaUri = { uri, name -> viewModel.importAndLoadGemmaModel(uri, name) },
            onUnloadGemma = { viewModel.unloadGemmaModel() },
            onEmailLogin = { email, password ->
                optionalAuthError = null
                viewModel.loginSupabaseAccount(email, password) {
                    showEngineDialog = false
                }
            },
            onEmailRegister = { email, password, fullName ->
                optionalAuthError = null
                viewModel.registerSupabaseAccount(email, password, fullName) {
                    showEngineDialog = false
                }
            },
            onSignInWithGoogle = {
                optionalAuthError = null
                viewModel.clearBackendAuthError()
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                        optionalAuthError = null
                        showEngineDialog = false
                    },
                    onAuthError = { msg -> optionalAuthError = msg },
                    onAuthCancelled = { optionalAuthError = null },
                    scope = scope
                )
            },
            onSignOut = {
                showEngineDialog = false
                viewModel.signOutBackendAccount()
                if (isSignedIn) {
                    signOutGoogle(credentialManager, scope)
                }
            },
            onDismiss = {
                viewModel.stopAllAudio()
                optionalAuthError = null
                viewModel.clearBackendAuthError()
                showEngineDialog = false
            }
        )
    }

    if (uiState.showLanguageMenu) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLanguageMenu() },
            title = {
                Text(
                    BilingualStrings.pick(
                        lang,
                        "Choose Your Language (Never Guess)",
                        "अपनी भाषा चुनें (गलत अंदाज़ा नहीं)",
                        "अपनी भासा चुनो (गलत अंदाज़ा नेईं)"
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "After 2 unclear voice retries, BankSathi lets you pick your language cleanly instead of guessing:",
                            "दो बार आवाज़ स्पष्ट न होने पर बैंकसाथी अंदाज़ा नहीं लगाता — यहाँ अपनी भाषा चुनें:",
                            "दो बारी आवाज़ साफ नेईं होने पर बैंकसाथी अंदाज़ा नेईं लांदा — इत्थै अपनी भासा चुनो:"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    viewModel.regionalLanguages.forEach { option ->
                        val selected = uiState.selectedRegionalCode == option.langCode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectLanguageFromMenu(option) }
                                .testTag("lang_option_${option.langCode}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { viewModel.selectLanguageFromMenu(option) }
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "${option.nativeName} • ${option.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLanguageMenu() }) {
                    Text(BilingualStrings.pick(lang, "Close", "बंद करें", "बंद करो"))
                }
            }
        )
    }
}
