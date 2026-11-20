package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AiSourceMode
import com.example.data.model.AppLanguage
import com.example.data.model.BankingTask
import com.example.data.model.CashStatus
import com.example.data.model.ChatMessage
import com.example.data.model.DistrictCatalog
import com.example.data.model.FacilityRecord
import com.example.data.repository.FacilityGeoUtils
import com.example.data.repository.PincodeFacilityResolver
import com.example.ui.GraminSetuUiState
import com.example.ui.i18n.BilingualStrings

@Composable
fun HomeHubScreen(
    uiState: GraminSetuUiState,
    facilities: List<FacilityRecord>,
    onTaskSelected: (BankingTask) -> Unit,
    onOpenFacilities: () -> Unit,
    onOpenScamSentinel: () -> Unit,
    onOpenLanguageMenu: () -> Unit = {},
    onAskText: (String) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onClearChat: () -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = uiState.language
    val context = LocalContext.current
    var queryInput by remember { mutableStateOf("") }
    var showBeyondBankingDialog by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoice()
        }
    }

    val activeDistrict = remember(uiState.selectedDistrictCode) {
        DistrictCatalog.findByCode(uiState.selectedDistrictCode)
    }
    val hasActivePin = remember(uiState.activePincode) {
        PincodeFacilityResolver.isValidIndianPincode(uiState.activePincode)
    }
    val centerLat = if (hasActivePin) uiState.activePincodeLat else activeDistrict.centerLat
    val centerLon = if (hasActivePin) uiState.activePincodeLon else activeDistrict.centerLon
    val targetDistrictCode = if (hasActivePin) {
        PincodeFacilityResolver.districtCodeForPincode(uiState.activePincode)
    } else {
        activeDistrict.code
    }

    val nearestOpen = remember(facilities, targetDistrictCode, centerLat, centerLon) {
        val districtMatches = facilities.filter { it.districtCode == targetDistrictCode }
        val pool = if (districtMatches.isNotEmpty()) districtMatches else facilities
        val withDistance = pool.map { item ->
            val haversine = FacilityGeoUtils.haversineMeters(
                centerLat,
                centerLon,
                item.lat,
                item.lon
            )
            val computedDistance = if (item.lat == centerLat && item.lon == centerLon && item.distanceMeters > 0L) {
                item.distanceMeters
            } else {
                haversine
            }
            item.copy(distanceMeters = computedDistance)
        }.sortedBy { it.distanceMeters }

        withDistance.firstOrNull {
            it.isOpen && FacilityGeoUtils.effectiveCashStatus(it) == CashStatus.CASH_READY
        } ?: withDistance.firstOrNull { it.isOpen } ?: withDistance.firstOrNull()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        VoiceAskCard(
                            uiState = uiState,
                            queryInput = queryInput,
                            onQueryInputChange = { queryInput = it },
                            onOpenLanguageMenu = onOpenLanguageMenu,
                            onOpenFacilities = onOpenFacilities,
                            onOpenScamSentinel = onOpenScamSentinel,
                            onTaskSelected = onTaskSelected,
                            onClearChat = onClearChat,
                            onSpeakText = onSpeakText,
                            onMicClick = {
                                if (uiState.isListening) {
                                    onStopVoice()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) onStartVoice()
                                    else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onSendQuery = {
                                if (queryInput.isNotBlank()) {
                                    onAskText(queryInput)
                                    queryInput = ""
                                }
                            },
                            onQuickPrompt = { prompt ->
                                queryInput = ""
                                onAskText(prompt)
                            }
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TaskGridSection(lang = lang, onTaskSelected = onTaskSelected)
                    }
                }
            } else {
                VoiceAskCard(
                    uiState = uiState,
                    queryInput = queryInput,
                    onQueryInputChange = { queryInput = it },
                    onOpenLanguageMenu = onOpenLanguageMenu,
                    onOpenFacilities = onOpenFacilities,
                    onOpenScamSentinel = onOpenScamSentinel,
                    onTaskSelected = onTaskSelected,
                    onClearChat = onClearChat,
                    onSpeakText = onSpeakText,
                    onMicClick = {
                        if (uiState.isListening) {
                            onStopVoice()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onStartVoice()
                            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSendQuery = {
                        if (queryInput.isNotBlank()) {
                            onAskText(queryInput)
                            queryInput = ""
                        }
                    },
                    onQuickPrompt = { prompt ->
                        queryInput = ""
                        onAskText(prompt)
                    }
                )
                TaskGridSection(lang = lang, onTaskSelected = onTaskSelected)
            }

            // Nearest Live Facility Snapshot Card (with bankName + Haversine distance + verification freshness)
            if (nearestOpen != null) {
                val nearestStatus = FacilityGeoUtils.effectiveCashStatus(nearestOpen)
                val isUnknown = nearestStatus == CashStatus.UNKNOWN
                val badgeBg = when {
                    !nearestOpen.isOpen || nearestStatus == CashStatus.NO_CASH -> MaterialTheme.colorScheme.errorContainer
                    isUnknown -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val badgeTint = when {
                    !nearestOpen.isOpen || nearestStatus == CashStatus.NO_CASH -> MaterialTheme.colorScheme.error
                    isUnknown -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                }
                val verifiedBadge = if (nearestOpen.verifiedDate.isBlank()) {
                    BilingualStrings.pick(lang, "Unverified", "असत्यापित", "असत्यापित")
                } else {
                    BilingualStrings.pick(lang, "Verified ${nearestOpen.verifiedDate}", "सत्यापित ${nearestOpen.verifiedDate}", "जांचेआ ${nearestOpen.verifiedDate}")
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenFacilities)
                        .testTag("home_nearest_facility_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeBg,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isUnknown) Icons.Rounded.Info else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = badgeTint
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val areaTag = if (hasActivePin) {
                                "PIN ${uiState.activePincode}" +
                                    if (uiState.activePincodeArea.isNotBlank()) " • ${uiState.activePincodeArea}" else ""
                            } else {
                                BilingualStrings.pick(lang, activeDistrict.nameEn, activeDistrict.nameHi, activeDistrict.nameDoi)
                            }
                            Text(
                                text = BilingualStrings.pick(
                                    lang,
                                    "Nearest Center • ${nearestOpen.bankName} • $areaTag",
                                    "नजदीकी केंद्र • ${nearestOpen.bankName} • $areaTag",
                                    "सबतो नेड़े केंदर • ${nearestOpen.bankName} • $areaTag"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = BilingualStrings.pick(lang, nearestOpen.nameEn, nearestOpen.nameHi),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${FacilityGeoUtils.formatDistance(nearestOpen.distanceMeters)} • ${
                                    BilingualStrings.cashStatusLabel(
                                        nearestOpen.isOpen,
                                        nearestStatus,
                                        lang
                                    )
                                } • $verifiedBadge",
                                style = MaterialTheme.typography.bodyMedium,
                                color = badgeTint
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = BilingualStrings.pick(lang, "Open live status", "लाइव स्थिति देखें", "ताज़ा स्थिति दिक्खो"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Scam Guard Quick Strip
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenScamSentinel)
                    .testTag("home_scam_strip")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = BilingualStrings.pick(
                                lang,
                                "Got a suspicious SMS or OTP call?",
                                "क्या कोई संदिग्ध SMS या OTP कॉल आया?",
                                "कोई शक्की SMS जां OTP कॉल आया?"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = BilingualStrings.pick(
                                lang,
                                "Tap for Scam Guard pre-check & 1930 helpline",
                                "स्कैम गार्ड जांच और 1930 हेल्पलाइन के लिए दबाएं",
                                "ठगी जांच ते 1930 हेल्पलाइन आस्तै दबाओ"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Beyond Banking Platform Preview Strip (Team 05 Architecture)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBeyondBankingDialog = true }
                    .testTag("beyond_banking_platform_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = BilingualStrings.pick(
                                lang,
                                "BankSathi Platform: Beyond Banking",
                                "बैंकसाथी प्लेटफ़ॉर्म: बैंकिंग से आगे",
                                "बैंकसाथी प्लेटफ़ॉर्म: बैंकिंग थमां अग्गै"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = BilingualStrings.pick(
                                lang,
                                "Same offline voice + RAG core for Healthcare, Agriculture & Education",
                                "स्वास्थ्य, कृषि और शिक्षा के लिए एक ही ऑफलाइन वॉइस + RAG ढांचा",
                                "सेहत, खेतीबाड़ी ते पढ़ाई आस्तै इको जेहा ऑफलाइन वॉइस + RAG ढांचा"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = BilingualStrings.pick(lang, "View domains", "अन्य क्षेत्र देखें", "होरो क्षेत्र दिक्खो"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(84.dp))
        }
    }

    if (showBeyondBankingDialog) {
        BeyondBankingPlatformDialog(
            language = lang,
            onDismiss = { showBeyondBankingDialog = false }
        )
    }
}

@Composable
private fun VoiceAskCard(
    uiState: GraminSetuUiState,
    queryInput: String,
    onQueryInputChange: (String) -> Unit,
    onOpenLanguageMenu: () -> Unit,
    onOpenFacilities: () -> Unit,
    onOpenScamSentinel: () -> Unit,
    onTaskSelected: (BankingTask) -> Unit,
    onClearChat: () -> Unit,
    onSpeakText: (String) -> Unit,
    onMicClick: () -> Unit,
    onSendQuery: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    val lang = uiState.language
    val chatScrollState = rememberScrollState()
    val lastMsgPreview = uiState.chatMessages.lastOrNull()?.textEn.orEmpty()

    LaunchedEffect(uiState.chatMessages.size, lastMsgPreview.length) {
        if (uiState.chatMessages.isNotEmpty()) {
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "BankSathi Chat",
                            "बैंकसाथी चैट",
                            "बैंकसाथी चैट"
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val aiModeLine = when {
                        uiState.isLowRamFaqOnlyActive ->
                            BilingualStrings.pick(
                                lang,
                                "Low-RAM FAQ-Only Safeguard (${uiState.deviceMemoryStatus.totalRamGbLabel})",
                                "कम RAM सुरक्षा: FAQ-ओनली (${uiState.deviceMemoryStatus.totalRamGbLabel})",
                                "कट्ट RAM सुरक्षा: FAQ-ओनली (${uiState.deviceMemoryStatus.totalRamGbLabel})"
                            )
                        uiState.gemmaModelState is com.example.ai.GemmaModelState.Ready ->
                            BilingualStrings.pick(
                                lang,
                                "On-Device Gemma-2B Live Chat",
                                "ऑफलाइन जेमा-2B लाइव चैट",
                                "ऑफलाइन जेमा-2B लाइव चैट"
                            )
                        uiState.fastApiConfigured && uiState.isOnline ->
                            BilingualStrings.pick(
                                lang,
                                "FastAPI RAG + ChromaDB + Offline Cache",
                                "FastAPI RAG + ChromaDB + ऑफलाइन नियम",
                                "FastAPI RAG + ChromaDB + ऑफलाइन नियम"
                            )
                        uiState.isOnline ->
                            BilingualStrings.pick(
                                lang,
                                "Firebase Gemini + Offline Rules",
                                "फायरबेस जेमिनी + ऑफलाइन नियम",
                                "फायरबेस जेमिनी + ऑफलाइन नियम"
                            )
                        else ->
                            BilingualStrings.pick(
                                lang,
                                "Verified Offline Banking Rules",
                                "सत्यापित ऑफलाइन बैंकिंग नियम",
                                "पक्के ऑफलाइन बैंकिंग नियम"
                            )
                    }
                    val voiceTag = when {
                        uiState.bhashiniConfigured && uiState.isOnline ->
                            BilingualStrings.pick(lang, " • Bhashini (hi/doi/en)", " • भाषिनी (hi/doi/en)", " • भाषिनी (hi/doi/en)")
                        uiState.isOnline ->
                            BilingualStrings.pick(lang, " • Gemini Natural Voice", " • जेमिनी नेचुरल आवाज़", " • जेमिनी नेचुरल आवाज़")
                        uiState.googleTtsInstalled ->
                            BilingualStrings.pick(lang, " • Google Indian Voice", " • गूगल भारतीय आवाज़", " • गूगल भारतीय आवाज़")
                        else -> ""
                    }
                    Text(
                        text = "$aiModeLine$voiceTag",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.isLowRamFaqOnlyActive) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.chatMessages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = BilingualStrings.pick(lang, "New chat", "नई चैट", "नमीं चैट"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AssistChip(
                        onClick = onOpenLanguageMenu,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                BilingualStrings.pick(lang, "Languages", "भाषाएं", "भासां"),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("home_language_menu_chip")
                    )
                }
            }

            if (uiState.chatMessages.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(chatScrollState)
                            .padding(10.dp)
                            .testTag("chat_messages_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.chatMessages.forEach { msg ->
                            ChatMessageBubble(
                                message = msg,
                                language = lang,
                                onOpenFacilities = onOpenFacilities,
                                onOpenScamSentinel = onOpenScamSentinel,
                                onOpenTaskChecklist = { task -> onTaskSelected(task) },
                                onSpeak = { txt -> onSpeakText(txt) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onMicClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("tap_and_speak_button")
            ) {
                Icon(
                    imageVector = if (uiState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (uiState.isListening) {
                        BilingualStrings.pick(lang, "Listening... Tap to Stop", "सुन रहा हूँ... रोकने के लिए दबाएं", "सुणा दा आं... रोकने आस्तै दबाओ")
                    } else {
                        BilingualStrings.pick(lang, "Tap and Speak", "दबाकर बोलें", "दबाइयै बोलो")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.partialSpeechText.isNotBlank() && uiState.isListening) {
                Text(
                    text = "\"${uiState.partialSpeechText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = queryInput,
                    onValueChange = onQueryInputChange,
                    placeholder = {
                        Text(
                            BilingualStrings.pick(
                                lang,
                                "Ask: \"Withdraw ₹3000\" / \"KCC loan\"",
                                "पूछें: \"3000 रुपये निकालने हैं\"",
                                "पुच्छो: \"3000 रुपये कड्ढने न\""
                            )
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ask_banksathi_input")
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(
                        onClick = onSendQuery,
                        enabled = queryInput.isNotBlank() && !uiState.isAssistantThinking,
                        modifier = Modifier.testTag("ask_banksathi_send_button")
                    ) {
                        if (uiState.isAssistantThinking) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = BilingualStrings.pick(lang, "Ask BankSathi", "पूछें", "पुच्छो"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            val chips = when (lang) {
                AppLanguage.HINDI -> listOf("₹3,000 निकालने हैं", "PM-Kisan किस्त जांच", "केसीसी लोन कागज़", "ओटीपी कॉल आया")
                AppLanguage.DOGRI -> listOf("₹3,000 कड्ढने न", "PM-Kisan किस्त जांच", "केसीसी लोन कागज़", "ओटीपी कॉल आया")
                AppLanguage.ENGLISH -> listOf("Withdraw ₹3,000", "PM-Kisan subsidy check", "KCC loan papers", "Caller asked OTP")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { sample ->
                    AssistChip(
                        onClick = { onQuickPrompt(sample) },
                        enabled = !uiState.isAssistantThinking,
                        label = { Text(sample, style = MaterialTheme.typography.labelMedium) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            if (!uiState.assistantError.isNullOrBlank()) {
                Text(
                    text = uiState.assistantError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    language: AppLanguage,
    onOpenFacilities: () -> Unit,
    onOpenScamSentinel: () -> Unit,
    onOpenTaskChecklist: (BankingTask) -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val displayBody = BilingualStrings.pick(language, message.textEn, message.textHi, message.textDoi)

    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = displayBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        return
    }

    val isAbstain = message.abstained
    val isScam = message.isScamAlert
    val containerColor = when {
        isScam -> MaterialTheme.colorScheme.errorContainer
        isAbstain -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isScam -> MaterialTheme.colorScheme.onErrorContainer
        isAbstain -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = containerColor,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag(if (isAbstain) "abstain_safety_card" else "verified_answer_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                isScam -> Icons.Rounded.Shield
                                isAbstain -> Icons.Rounded.Info
                                else -> Icons.Rounded.VerifiedUser
                            },
                            contentDescription = null,
                            tint = when {
                                isScam -> MaterialTheme.colorScheme.error
                                isAbstain -> contentColor
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                message.isStreaming -> BilingualStrings.pick(language, "Thinking...", "सोच रहा हूँ...", "सोचा दा आं...")
                                isAbstain -> BilingualStrings.pick(
                                    language,
                                    "Not Sure — Never Guesses",
                                    "पक्की जानकारी नहीं — अंदाज़ा नहीं",
                                    "पक्की जानकारी नेईं — अंदाज़ा नेईं"
                                )
                                message.sourceMode != null -> BilingualStrings.sourceLabel(message.sourceMode, language)
                                else -> "BankSathi"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                    if (!message.isStreaming && displayBody.isNotBlank()) {
                        IconButton(onClick = { onSpeak(displayBody) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = BilingualStrings.pick(language, "Read aloud", "सुनें", "सुणो"),
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (message.isStreaming && displayBody.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text(
                            text = BilingualStrings.pick(language, "Generating answer...", "जवाब तैयार हो रहा है...", "जवाब तैयार होआ दा ऐ..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    Text(
                        text = if (message.isStreaming) "$displayBody ▍" else displayBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }

                if (!message.isStreaming && (!message.sourceId.isNullOrBlank() || !message.verifiedOn.isNullOrBlank())) {
                    Text(
                        text = buildString {
                            if (!message.sourceId.isNullOrBlank()) append("Source: ${message.sourceId}")
                            if (!message.sourceId.isNullOrBlank() && !message.verifiedOn.isNullOrBlank()) append(" • ")
                            if (!message.verifiedOn.isNullOrBlank()) append("Verified: ${message.verifiedOn}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }

                if (!message.isStreaming) {
                    if (isAbstain) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenFacilities,
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Text(BilingualStrings.pick(language, "Nearest Branch", "नजदीकी शाखा", "नेड़े शाखा"))
                            }
                            Button(
                                onClick = {
                                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:14448"))
                                    context.startActivity(dial)
                                },
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("14448 / 1930")
                            }
                        }
                    } else if (isScam || message.sourceId?.contains("SCAM", ignoreCase = true) == true) {
                        OutlinedButton(
                            onClick = onOpenScamSentinel,
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text(BilingualStrings.pick(language, "Open Scam Guard (1930)", "स्कैम गार्ड (1930) खोलें", "ठगी गार्ड (1930) खोह्लो"))
                        }
                    } else if (message.recommendedTask != null) {
                        OutlinedButton(
                            onClick = { onOpenTaskChecklist(message.recommendedTask) },
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text(
                                BilingualStrings.pick(
                                    language,
                                    "Open Bag Check: ${BilingualStrings.taskTitle(message.recommendedTask, language)}",
                                    "कागज़ जांच खोलें: ${BilingualStrings.taskTitle(message.recommendedTask, language)}",
                                    "कागज़ जांच खोह्लो: ${BilingualStrings.taskTitle(message.recommendedTask, language)}"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BeyondBankingPlatformDialog(
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                BilingualStrings.pick(
                    language,
                    "BankSathi Platform: Beyond Banking",
                    "बैंकसाथी प्लेटफ़ॉर्म: बैंकिंग से आगे",
                    "बैंकसाथी प्लेटफ़ॉर्म: बैंकिंग थमां अग्गै"
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = BilingualStrings.pick(
                        language,
                        "Reuses the same offline voice, regional languages, and verified RAG engine across domains:",
                        "यही ऑफलाइन आवाज़, क्षेत्रीय भाषा और सत्यापित ज्ञान तंत्र इन क्षेत्रों में काम करता है:",
                        "इहा ऑफलाइन आवाज़, क्षेत्रीय भासा ते पक्का ज्ञान तंत्र इनें खेत्रें च कम्म करदा ऐ:"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                DomainPreviewRow(
                    icon = Icons.Rounded.LocalHospital,
                    title = BilingualStrings.pick(language, "Healthcare (MoHFW / eSanjeevani / PHC)", "स्वास्थ्य (MoHFW / eSanjeevani / PHC)", "सेहत (MoHFW / eSanjeevani / PHC)"),
                    detail = BilingualStrings.pick(
                        language,
                        "No diagnosis or dosing advice. Red-flag symptoms route straight to a doctor/helpline; queries stay strictly on device.",
                        "दवा या निदान नहीं — गंभीर लक्षण तुरंत डॉक्टर/हेल्पलाइन पर भेजते हैं और सवाल केवल फोन पर रहते हैं।",
                        "दवाई जां इलाज नेईं — गंभीर लक्षण फौरन डॉक्टर/हेल्पलाइन पर भेजदे न ते सुआल फोन च गै रौंह्दे न।"
                    )
                )
                DomainPreviewRow(
                    icon = Icons.Rounded.Agriculture,
                    title = BilingualStrings.pick(language, "Agriculture (IMD / Agmarknet / PM-KISAN)", "कृषि (IMD मौसम / मंडी भाव / PM-KISAN)", "खेतीबाड़ी (IMD मौसम / मंडी भाव / PM-KISAN)"),
                    detail = BilingualStrings.pick(
                        language,
                        "Refreshes weather alerts and mandi prices on sync; offline mode displays last update with verified date stamp.",
                        "सिंक पर ताज़ा मौसम व मंडी भाव मिलते हैं; ऑफलाइन में पिछली सत्यापित तारीख दिखाई जाती है।",
                        "सिंक पर ताज़ा मौसम ते मंडी भाव मिल्दे न; ऑफलाइन च पिछली जांची तारीख दिखौंदी ऐ।"
                    )
                )
                DomainPreviewRow(
                    icon = Icons.Rounded.School,
                    title = BilingualStrings.pick(language, "Education (NCERT / DIKSHA)", "शिक्षा (NCERT / DIKSHA सामग्री)", "पढ़ाई (NCERT / DIKSHA)"),
                    detail = BilingualStrings.pick(
                        language,
                        "Curriculum-aligned voice Q&A by class and language with strict child privacy (zero child profile stored).",
                        "कक्षा व भाषा के अनुसार पढ़ाई सहायता — बच्चों की निजता हेतु कोई प्रोफाइल सेव नहीं होती।",
                        "जमात ते भासा दे हिसाब कन्नै पढ़ाई मदद — बच्चें दी निजता आस्तै कोई प्रोफाइल सेव नेईं हुंदी।"
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(BilingualStrings.pick(language, "Close", "बंद करें", "बंद करो"))
            }
        }
    )
}

@Composable
private fun DomainPreviewRow(
    icon: ImageVector,
    title: String,
    detail: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(text = detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TaskGridSection(
    lang: AppLanguage,
    onTaskSelected: (BankingTask) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = BilingualStrings.pick(lang, "What do you need today?", "आज आपको क्या काम है?", "अज्ज तुसेंगी क्या कम्म ऐ?"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TaskActionCard(
                task = BankingTask.WITHDRAW,
                icon = Icons.Rounded.CurrencyRupee,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                lang = lang,
                onClick = { onTaskSelected(BankingTask.WITHDRAW) },
                modifier = Modifier.weight(1f)
            )
            TaskActionCard(
                task = BankingTask.TRANSFER,
                icon = Icons.AutoMirrored.Rounded.Send,
                accentColor = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                lang = lang,
                onClick = { onTaskSelected(BankingTask.TRANSFER) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TaskActionCard(
                task = BankingTask.PASSBOOK_DBT,
                icon = Icons.Rounded.Description,
                accentColor = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                lang = lang,
                onClick = { onTaskSelected(BankingTask.PASSBOOK_DBT) },
                modifier = Modifier.weight(1f)
            )
            TaskActionCard(
                task = BankingTask.LOAN_KCC,
                icon = Icons.Rounded.AccountBalance,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                lang = lang,
                onClick = { onTaskSelected(BankingTask.LOAN_KCC) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TaskActionCard(
    task: BankingTask,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .height(132.dp)
            .clickable(onClick = onClick)
            .testTag("task_card_${task.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = containerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column {
                Text(
                    text = BilingualStrings.taskTitle(task, lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = BilingualStrings.taskSubtitle(task, lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
