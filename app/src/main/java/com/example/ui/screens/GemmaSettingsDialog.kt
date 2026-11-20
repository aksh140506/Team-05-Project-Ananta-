package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.example.ai.GemmaModelState
import com.example.data.model.AppLanguage
import com.example.data.model.DeviceMemoryStatus
import com.example.data.model.SyncMetaRecord
import com.example.data.model.UserRole
import com.example.ui.i18n.BilingualStrings
import kotlin.math.roundToInt

@Composable
fun GemmaAndAccountDialog(
    language: AppLanguage,
    gemmaModelState: GemmaModelState,
    playbackSpeed: Float,
    syncMeta: List<SyncMetaRecord>,
    deviceMemoryStatus: DeviceMemoryStatus = DeviceMemoryStatus(4096L, false),
    isLowRamFaqOnlyActive: Boolean = false,
    fastApiConfigured: Boolean = false,
    bhashiniConfigured: Boolean = false,
    googleTtsInstalled: Boolean = false,
    lastSyncVerificationMessage: String? = null,
    userEmail: String?,
    userRole: UserRole = UserRole.CITIZEN,
    isSignedIn: Boolean,
    isOnline: Boolean,
    authError: String? = null,
    isAuthLoading: Boolean = false,
    onPlaybackSpeedChange: (Float) -> Unit,
    onLowRamFaqOnlyChange: (Boolean) -> Unit = {},
    onRefreshDeltaSync: () -> Unit,
    onTestNaturalVoice: () -> Unit = {},
    onInstallOfflineVoicePack: () -> Unit = {},
    onImportGemmaUri: (Uri, String?) -> Unit,
    onUnloadGemma: () -> Unit,
    onEmailLogin: (String, String) -> Unit = { _, _ -> },
    onEmailRegister: (String, String, String) -> Unit = { _, _, _ -> },
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var fullNameInput by rememberSaveable { mutableStateOf("") }
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var displayName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    displayName = cursor.getString(idx)
                }
            }
            onImportGemmaUri(uri, displayName)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(BilingualStrings.pick(language, "BankSathi AI, Voice & Sync", "बैंकसाथी एआई, आवाज़ व सिंक", "बैंकसाथी AI, आवाज़ ते सिंक"))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Low-RAM Safeguard Card (3 GB Device Crash Mitigation)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isLowRamFaqOnlyActive) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = BilingualStrings.pick(
                                        language,
                                        "Low-RAM FAQ-Only Mode",
                                        "कम-RAM FAQ-ओनली सुरक्षा",
                                        "कट्ट-RAM FAQ-ओनली सुरक्षा"
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = BilingualStrings.pick(
                                    language,
                                    "Detected RAM: ${deviceMemoryStatus.totalRamGbLabel}. Auto-enabled on <=3.5 GB phones to prevent SLM memory crashes.",
                                    "फोन RAM: ${deviceMemoryStatus.totalRamGbLabel}। 3 GB फोन को क्रैश से बचाने के लिए तेज़ FAQ मोड।",
                                    "फोन RAM: ${deviceMemoryStatus.totalRamGbLabel}। 3 GB फोन गी क्रैश थमां बचाने आस्तै तेज़ FAQ मोड।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isLowRamFaqOnlyActive,
                            onCheckedChange = onLowRamFaqOnlyChange,
                            modifier = Modifier.testTag("low_ram_faq_toggle")
                        )
                    }
                }

                // On-Device Stack Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = BilingualStrings.pick(
                                    language,
                                    "On-Device: Gemma-2B int4 + Room SQLite",
                                    "ऑफलाइन: जेमा-2B + Room SQLite",
                                    "ऑफलाइन: जेमा-2B + Room SQLite"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Multi-turn Gemma-2B streaming chat grounded in RBI/NPCI rules & CACHED_FAQ (with ScamGuard pre-check).",
                                "RBI/NPCI नियमों व CACHED_FAQ से जुड़ी जेमा-2B लाइव चैट (स्कैम गार्ड सुरक्षा सहित)।",
                                "RBI/NPCI नियमें ते CACHED_FAQ कन्नै जुड़ी जेमा-2B लाइव चैट (ठगी गार्ड सुरक्षा सहित)।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when {
                            isLowRamFaqOnlyActive -> {
                                Text(
                                    text = BilingualStrings.pick(
                                        language,
                                        "Paused by FAQ-Only Safeguard. Turn off FAQ-Only above to load Gemma-2B.",
                                        "FAQ-ओनली सुरक्षा चालू है। जेमा-2B लोड करने के लिए ऊपर इसे बंद करें।",
                                        "FAQ-ओनली सुरक्षा चालू ऐ। जेमा-2B लोड करने आस्तै उप्पर इसी बंद करो।"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            gemmaModelState is GemmaModelState.Ready -> {
                                Text(
                                    text = BilingualStrings.pick(
                                        language,
                                        "Active: ${gemmaModelState.modelName}",
                                        "सक्रिय: ${gemmaModelState.modelName}",
                                        "चालू: ${gemmaModelState.modelName}"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            gemmaModelState is GemmaModelState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(BilingualStrings.pick(language, "Loading Gemma-2B...", "मॉडल लोड हो रहा है...", "मॉडल लोड होआ दा ऐ..."))
                                }
                            }
                            gemmaModelState is GemmaModelState.Error -> {
                                Text(
                                    text = gemmaModelState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {
                                Text(
                                    text = BilingualStrings.pick(
                                        language,
                                        if (isOnline) "Cloud RAG + Offline Verified Rules active. Load local Gemma-2B (.task/.bin) anytime."
                                        else "Offline Verified Rules + CACHED_FAQ active. Load Gemma-2B (.task/.bin) for offline SLM rephrasing.",
                                        if (isOnline) "क्लाउड RAG + ऑफलाइन सत्यापित नियम सक्रिय हैं। ऑफलाइन SLM के लिए Gemma-2B चुनें।"
                                        else "ऑफलाइन सत्यापित नियम + CACHED_FAQ सक्रिय हैं। Gemma-2B फ़ाइल चुनें।",
                                        if (isOnline) "क्लाउड RAG + ऑफलाइन पक्के नियम चालू न। ऑफलाइन SLM आस्तै Gemma-2B चुनो।"
                                        else "ऑफलाइन पक्के नियम + CACHED_FAQ चालू न। Gemma-2B फ़ाइल चुनो।"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !isLowRamFaqOnlyActive,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("load_gemma_model_button")
                            ) {
                                Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(BilingualStrings.pick(language, "Load Gemma .task", "मॉडल चुनें", "मॉडल चुनो"))
                            }
                            if (gemmaModelState is GemmaModelState.Ready) {
                                OutlinedButton(onClick = onUnloadGemma) {
                                    Text(BilingualStrings.pick(language, "Unload", "हटाएं", "हटाओ"))
                                }
                            }
                        }
                    }
                }

                // Voice Playback Speed (USER_PREFERENCE.playback_speed)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = BilingualStrings.pick(
                                    language,
                                    "Voice Speed: ${((playbackSpeed * 100).roundToInt() / 100f)}x",
                                    "आवाज़ की गति: ${((playbackSpeed * 100).roundToInt() / 100f)}x",
                                    "आवाज़ दी रफ़्तार: ${((playbackSpeed * 100).roundToInt() / 100f)}x"
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = playbackSpeed.coerceIn(0.80f, 1.20f),
                            onValueChange = onPlaybackSpeedChange,
                            valueRange = 0.80f..1.20f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onTestNaturalVoice,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_natural_voice_button")
                            ) {
                                Text(BilingualStrings.pick(language, "Test Voice", "आवाज़ सुनें", "आवाज़ सुणो"))
                            }
                            OutlinedButton(
                                onClick = onInstallOfflineVoicePack,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("install_offline_voice_button")
                            ) {
                                Text(BilingualStrings.pick(language, "Offline Pack", "ऑफलाइन पैक", "ऑफलाइन पैक"))
                            }
                        }
                    }
                }

                // Authentic Backend & Signed Delta-Sync Status Card (SYNC_META)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = BilingualStrings.pick(
                                    language,
                                    "Pipeline & Signed Delta-Sync",
                                    "क्लाउड व हस्ताक्षरित डेल्टा-सिंक",
                                    "क्लाउड ते जांचेआ डेल्टा-सिंक"
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (fastApiConfigured) {
                                BilingualStrings.pick(
                                    language,
                                    "• FastAPI + PostGIS + ChromaDB: Configured (App Check Protected)",
                                    "• FastAPI + PostGIS + ChromaDB: सक्रिय (App Check)",
                                    "• FastAPI + PostGIS + ChromaDB: चालू (App Check)"
                                )
                            } else {
                                BilingualStrings.pick(
                                    language,
                                    "• FastAPI Endpoint: Fallback to Firebase AI / Signed Cache",
                                    "• FastAPI एंडपॉइंट: अभी Firebase AI / स्थानीय पैक सक्रिय",
                                    "• FastAPI एंडपॉइंट: हून Firebase AI / स्थानीय पैक चालू"
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                bhashiniConfigured && isOnline -> BilingualStrings.pick(
                                    language,
                                    "• Voice (hi, doi, en): Bhashini + Gemini Natural Voice + Google TTS fallback",
                                    "• आवाज़ (hi, doi, en): भाषिनी + जेमिनी नेचुरल वॉइस + गूगल बैकअप",
                                    "• आवाज़ (hi, doi, en): भाषिनी + जेमिनी नेचुरल वॉइस + गूगल बैकअप"
                                )
                                isOnline -> BilingualStrings.pick(
                                    language,
                                    "• Natural Voice (hi, doi, en): Gemini 3.1 Flash TTS (Firebase AI, Zero API Key) + Google TTS",
                                    "• नेचुरल आवाज़ (hi, doi, en): जेमिनी AI महिला आवाज़ (बिना अलग API के) + गूगल बैकअप",
                                    "• नेचुरल आवाज़ (hi, doi, en): जेमिनी AI महिला आवाज़ (बिना अलग API दे) + गूगल बैकअप"
                                )
                                googleTtsInstalled -> BilingualStrings.pick(
                                    language,
                                    "• Offline Voice (hi, doi, en): Google Indian Female TTS active (Hindi fallback for Dogri)",
                                    "• ऑफलाइन आवाज़ (hi, doi, en): गूगल भारतीय महिला आवाज़ सक्रिय",
                                    "• ऑफलाइन आवाज़ (hi, doi, en): गूगल भारतीय महिला आवाज़ चालू"
                                )
                                else -> BilingualStrings.pick(
                                    language,
                                    "• Offline Voice: System TTS active — tap 'Offline Pack' above to add Google India voice",
                                    "• ऑफलाइन आवाज़: फ़ोन TTS सक्रिय — बेहतर आवाज़ के लिए ऊपर 'ऑफलाइन पैक' दबाएं",
                                    "• ऑफलाइन आवाज़: फोन TTS चालू — बधिया आवाज़ आस्तै उप्पर 'ऑफलाइन पैक' दबाओ"
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        syncMeta.forEach { meta ->
                            Text(
                                text = "• ${meta.tableName}: v${meta.version} [${meta.districtCode}] • ${meta.signatureStatus}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (!lastSyncVerificationMessage.isNullOrBlank()) {
                            Text(
                                text = lastSyncVerificationMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = onRefreshDeltaSync,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_sync_button")
                        ) {
                            Icon(imageVector = Icons.Rounded.Sync, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(BilingualStrings.pick(language, "Verify & Sync District Pack", "डेल्टा-सिंक जांचें", "डेल्टा-सिंक जांचो"))
                        }
                    }
                }

                if (isSignedIn) {
                    val roleLabel = when (userRole) {
                        UserRole.ADMIN -> BilingualStrings.pick(language, "Admin", "एडमिन", "एडमिन")
                        UserRole.VERIFIER -> BilingualStrings.pick(language, "Verified Reviewer", "सत्यापित समीक्षक", "सत्यापित समीक्षक")
                        UserRole.CITIZEN -> BilingualStrings.pick(language, "Citizen", "नागरिक", "नागरिक")
                    }
                    val isPrivileged = userRole == UserRole.VERIFIER || userRole == UserRole.ADMIN
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isPrivileged) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_role_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (isPrivileged) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPrivileged) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("user_role_badge")
                                ) {
                                    Text(
                                        text = "${userRole.apiValue} • $roleLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPrivileged) MaterialTheme.colorScheme.onSecondary
                                        else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = BilingualStrings.pick(
                                        language,
                                        "Cloud sync active: $userEmail",
                                        "क्लाउड सिंक सक्रिय: $userEmail",
                                        "क्लाउड सिंक चालू: $userEmail"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isPrivileged) {
                                    BilingualStrings.pick(
                                        language,
                                        "Protected Route Unlocked: Authorized to stamp official facility verification (/v1/facilities/verify).",
                                        "सुरक्षित रूट सक्रिय: आप केंद्रों की आधिकारिक जांच (/v1/facilities/verify) कर सकते हैं।",
                                        "सुरक्षित रूट चालू: तुस केंदरें दी पक्की जांच (/v1/facilities/verify) करी सकदे ओ।"
                                    )
                                } else {
                                    BilingualStrings.pick(
                                        language,
                                        "RBAC: Citizen accounts can report updates & sync packs; official facility stamps require VERIFIER or ADMIN role.",
                                        "RBAC: नागरिक खाता रिपोर्ट व सिंक कर सकता है; आधिकारिक मुहर केवल VERIFIER/ADMIN के लिए है।",
                                        "RBAC: नागरिक खाता रिपोर्ट ते सिंक करी सकदा ऐ; पक्की मुहर सिर्फ VERIFIER/ADMIN आस्तै ऐ।"
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(BilingualStrings.pick(language, "Sign Out", "साइन आउट करें", "साइन आउट करो"))
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = BilingualStrings.pick(
                                    language,
                                    if (isRegisterMode) "Create Supabase Account" else "Sign In (Supabase DB)",
                                    if (isRegisterMode) "नया खाता बनाएं (Supabase)" else "साइन इन करें (Supabase DB)",
                                    if (isRegisterMode) "नमां खाता बणाओ (Supabase)" else "साइन इन करो (Supabase DB)"
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = BilingualStrings.pick(
                                    language,
                                    "Sign in with your Supabase PostgreSQL account or Google for facility verification & cloud sync.",
                                    "केंद्र सत्यापन व क्लाउड सिंक के लिए अपने ईमेल खाते या Google से जुड़ें।",
                                    "केंद्र जांच ते क्लाउड सिंक आस्तै अपने ईमेल खाते जां Google कन्नै जुड़ो।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isRegisterMode) {
                                OutlinedTextField(
                                    value = fullNameInput,
                                    onValueChange = { fullNameInput = it },
                                    singleLine = true,
                                    label = { Text(BilingualStrings.pick(language, "Full Name", "पूरा नाम", "पूरा नां")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_full_name_input")
                                )
                            }
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                singleLine = true,
                                label = { Text(BilingualStrings.pick(language, "Email", "ईमेल", "ईमेल")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email_input")
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                label = {
                                    Text(
                                        BilingualStrings.pick(
                                            language,
                                            if (isRegisterMode) "Password (min 8 chars)" else "Password",
                                            if (isRegisterMode) "पासवर्ड (कम से कम 8 अक्षर)" else "पासवर्ड",
                                            if (isRegisterMode) "पासवर्ड (कट्ट थमां कट्ट 8 अक्खर)" else "पासवर्ड"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_password_input")
                            )
                            if (!authError.isNullOrBlank()) {
                                Text(
                                    text = authError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = {
                                    if (isRegisterMode) {
                                        onEmailRegister(emailInput, passwordInput, fullNameInput)
                                    } else {
                                        onEmailLogin(emailInput, passwordInput)
                                    }
                                },
                                enabled = !isAuthLoading &&
                                    emailInput.isNotBlank() &&
                                    passwordInput.length >= (if (isRegisterMode) 8 else 1) &&
                                    (!isRegisterMode || fullNameInput.trim().length >= 2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auth_email_submit_button")
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    BilingualStrings.pick(
                                        language,
                                        if (isRegisterMode) "Create Account" else "Sign In with Email",
                                        if (isRegisterMode) "खाता बनाएं" else "ईमेल से साइन इन",
                                        if (isRegisterMode) "खाता बणाओ" else "ईमेल कन्नै साइन इन"
                                    )
                                )
                            }
                            TextButton(
                                onClick = { isRegisterMode = !isRegisterMode },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auth_toggle_mode_button")
                            ) {
                                Text(
                                    BilingualStrings.pick(
                                        language,
                                        if (isRegisterMode) "Already have an account? Sign In" else "New here? Create Account",
                                        if (isRegisterMode) "पहले से खाता है? साइन इन करें" else "नया खाता बनाना है? यहाँ दबाएं",
                                        if (isRegisterMode) "पैह्ले थमां खाता ऐ? साइन इन करो" else "नमां खाता बणाना ऐ? इत्थै दबाओ"
                                    )
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onSignInWithGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("optional_google_signin_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(BilingualStrings.pick(language, "Sign in with Google", "Google से साइन इन करें", "Google कन्नै साइन इन"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(BilingualStrings.pick(language, "Close", "बंद करें", "बंद करो"))
            }
        }
    )
}
