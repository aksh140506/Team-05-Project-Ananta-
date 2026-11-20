package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.RiskLevel
import com.example.data.model.ScamCheckRecord
import com.example.ui.GraminSetuUiState
import com.example.ui.i18n.BilingualStrings

@Composable
fun ScamSentinelScreen(
    uiState: GraminSetuUiState,
    history: List<ScamCheckRecord>,
    scamRulesCount: Int = 5,
    onAnalyzeScam: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSharedText: String? = null
) {
    val lang = uiState.language
    val context = LocalContext.current
    var messageInput by remember { mutableStateOf(initialSharedText ?: "") }
    var showPracticeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            messageInput = initialSharedText
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Golden Rural Safety Rules Banner
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneInTalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "ScamGuard Pre-Check ($scamRulesCount Local Rules + 1930)",
                            "स्कैम गार्ड जांच ($scamRulesCount ऑफलाइन नियम + 1930)",
                            "ठगी गार्ड जांच ($scamRulesCount ऑफलाइन नियम + 1930)"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = BilingualStrings.pick(
                        lang,
                        "1. BankSathi runs ScamGuard BEFORE answering any query.\n2. Never speak an OTP/PIN or tap KYC links from SMS.\n3. Fraud happened? Call 1930 & report within 3 working days (RBI Zero Liability).",
                        "1. बैंकसाथी हर जवाब से पहले स्कैम गार्ड जांच चलाता है।\n2. कॉल पर OTP/PIN न बताएं और SMS में आए KYC लिंक न दबाएं।\n3. धोखा होने पर तुरंत 1930 पर कॉल करें व 3 कार्य दिवस में बैंक को बताएं (RBI नियम)।",
                        "1. बैंकसाथी हर जवाब थमां पैहले ठगी गार्ड जांच चलांदा ऐ।\n2. कॉल पर OTP/PIN नेईं दस्सो ते SMS च आए KYC लिंक नेईं दबाओ।\n3. ठगी होए तां फौरन 1930 पर कॉल करो ते 3 दिनें च बैंक गी दस्सो (RBI नियम)।"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Message / Scenario Checker Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = BilingualStrings.pick(
                        lang,
                        "Check a Message or Call Claim",
                        "मैसेज या कॉल की जांच करें",
                        "मैसेज जां कॉल दी जांच करो"
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = {
                        Text(
                            BilingualStrings.pick(
                                lang,
                                "Paste SMS or type what the caller/agent asked...",
                                "यहाँ SMS पेस्ट करें या लिखें कि कॉलर ने क्या कहा...",
                                "इत्थै SMS पेस्ट करो जां लिखो कि कॉलर ने क्या आक्खेआ..."
                            )
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scam_input_field")
                )

                val samples = when (lang) {
                    AppLanguage.HINDI -> listOf(
                        "आपका KYC बंद हो रहा है, bit.ly लिंक दबाएं और OTP बताएं",
                        "एजेंट ने कहा अंगूठा फेल हुआ, दोबारा लगाएं पर रसीद नहीं दी",
                        "बैंक से 2000 रुपये निकलने का आधिकारिक SMS आया है"
                    )
                    AppLanguage.DOGRI -> listOf(
                        "तुंदा KYC बंद होआ दा ऐ, bit.ly लिंक दबाओ ते OTP दस्सो",
                        "एजेंट ने आक्खेआ अंगूठा फेल होआ, दुबारा लाओ पर रसीद नेईं दित्ती",
                        "बैंक थमां 2000 रुपये कड्ढने दा पक्का SMS आया ऐ"
                    )
                    AppLanguage.ENGLISH -> listOf(
                        "Your bank KYC expired! Click bit.ly/kyc and share OTP",
                        "Agent says thumb scan failed, scan again with no receipt",
                        "Official bank SMS: Rs.2000 debited via BC point"
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    samples.forEach { sample ->
                        AssistChip(
                            onClick = {
                                messageInput = sample
                                onAnalyzeScam(sample)
                            },
                            enabled = !uiState.isScamAnalyzing,
                            label = { Text(sample.take(28) + "…") }
                        )
                    }
                }

                Button(
                    onClick = { onAnalyzeScam(messageInput) },
                    enabled = messageInput.isNotBlank() && !uiState.isScamAnalyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("scam_check_button")
                ) {
                    if (uiState.isScamAnalyzing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(imageVector = Icons.Rounded.Shield, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = BilingualStrings.pick(lang, "Check Safety Now", "सुरक्षा जांचें", "सुरक्षा जांचो"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!uiState.scamError.isNullOrBlank()) {
                    Text(
                        text = uiState.scamError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Latest Analysis Result
        uiState.lastScamResult?.let { res ->
            val containerColor = when (res.riskLevel) {
                RiskLevel.HIGH_RISK -> MaterialTheme.colorScheme.errorContainer
                RiskLevel.CAUTION -> MaterialTheme.colorScheme.tertiaryContainer
                RiskLevel.SAFE -> MaterialTheme.colorScheme.secondaryContainer
            }
            val contentColor = when (res.riskLevel) {
                RiskLevel.HIGH_RISK -> MaterialTheme.colorScheme.onErrorContainer
                RiskLevel.CAUTION -> MaterialTheme.colorScheme.onTertiaryContainer
                RiskLevel.SAFE -> MaterialTheme.colorScheme.onSecondaryContainer
            }
            val icon = when (res.riskLevel) {
                RiskLevel.HIGH_RISK -> Icons.Rounded.Dangerous
                RiskLevel.CAUTION -> Icons.Rounded.Warning
                RiskLevel.SAFE -> Icons.Rounded.CheckCircle
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scam_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = BilingualStrings.riskLabel(res.riskLevel, lang),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                        Text(
                            text = BilingualStrings.sourceLabel(res.sourceMode, lang),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = BilingualStrings.pick(lang, res.explanationEn, res.explanationHi, res.explanationDoi),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = BilingualStrings.pick(lang, res.actionTipEn, res.actionTipHi, res.actionTipDoi),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    if (res.riskLevel == RiskLevel.HIGH_RISK) {
                        Button(
                            onClick = {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                                context.startActivity(dialIntent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("call_1930_button")
                        ) {
                            Icon(imageVector = Icons.Rounded.Call, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = BilingualStrings.pick(
                                    lang,
                                    "Call 1930 Cyber Helpline",
                                    "1930 साइबर हेल्पलाइन पर कॉल करें",
                                    "1930 साइबर हेल्पलाइन पर कॉल करो"
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Safe Rs. 0 Payment Practice Card (Play-safe alternative to risky bank-app overlays)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "Safe Practice Mode (Rs. 0 Risk)",
                            "सुरक्षित अभ्यास (बिना पैसे कटे सीखें)",
                            "सुरक्षित अभ्यास (बिना पैसे कटे सिखो)"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = BilingualStrings.pick(
                        lang,
                        "Practice reading UPI screens, spotting fake 'Receive Money' PIN traps, and sharing any SMS directly to BankSathi.",
                        "UPI स्क्रीन समझना, नकली 'पैसे पाने के लिए PIN डालें' चाल पहचानना और SMS सीधे बैंकसाथी को शेयर करना सीखें।",
                        "UPI स्क्रीन समझना, नकली 'पैसे लैने आस्तै PIN पाओ' चाल पछाणना ते SMS सीधे बैंकसाथी गी शेयर करना सिखो।"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                OutlinedButton(
                    onClick = { showPracticeDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_practice_sandbox_button")
                ) {
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "Start 3-Step Safety Drill",
                            "3-कदम सुरक्षा अभ्यास शुरू करें",
                            "3-कदम सुरक्षा अभ्यास शुरू करो"
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Saved Recent Checks
        if (history.isNotEmpty()) {
            Text(
                text = BilingualStrings.pick(lang, "Recent Safety Checks", "हाल की जांच", "हाल ही जांच"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            history.take(5).forEach { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when (item.parsedRisk()) {
                                RiskLevel.HIGH_RISK -> MaterialTheme.colorScheme.errorContainer
                                RiskLevel.CAUTION -> MaterialTheme.colorScheme.tertiaryContainer
                                RiskLevel.SAFE -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.inputSnippet,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = BilingualStrings.riskLabel(item.parsedRisk(), lang),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(84.dp))
    }

    if (showPracticeDialog) {
        SafeUpiPracticeDialog(
            language = lang,
            onDismiss = { showPracticeDialog = false }
        )
    }
}

@Composable
private fun SafeUpiPracticeDialog(
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val stepsEn = listOf(
        "Step 1 of 3 — Check Receiver Name" to
            "Before paying, look closely at the green/black verified shop name on screen. If a stranger asks you to type an amount just to 'receive' a prize, STOP — you never enter your PIN to receive money!",
        "Step 2 of 3 — Watch the PIN Rule" to
            "Your 4 or 6 digit UPI PIN ONLY sends money out of your account. Banking apps automatically block floating screens for your safety. Never share your PIN, OTP, or screen on AnyDesk.",
        "Step 3 of 3 — Quick Share From SMS/WhatsApp" to
            "Whenever a confusing bank message arrives, tap Share -> BankSathi from your Messages or WhatsApp app. BankSathi will inspect it right away in Hindi or English!"
    )
    val stepsHi = listOf(
        "कदम 1 / 3 — दुकानदार का नाम जांचें" to
            "पैसे भेजने से पहले स्क्रीन पर लिखा सही नाम पढ़ें। अगर कोई अनजान व्यक्ति कहे कि 'इनाम पाने के लिए PIN डालें', तो तुरंत रुकें — पैसे लेने के लिए कभी PIN नहीं लगता!",
        "कदम 2 / 3 — PIN और रसीद का नियम" to
            "आपका 4 या 6 अंकों का PIN सिर्फ पैसे भेजने के लिए होता है। आपकी सुरक्षा के लिए बैंक ऐप ऊपर तैरने वाली स्क्रीन ब्लॉक करते हैं। कभी भी OTP, PIN या AnyDesk शेयर न करें।",
        "कदम 3 / 3 — SMS या WhatsApp से सीधा शेयर" to
            "कोई भी संदिग्ध मैसेज आए तो Messages या WhatsApp में 'Share -> BankSathi' दबाएं। बैंकसाथी तुरंत आसान हिंदी में बता देगा कि यह सुरक्षित है या धोखाधड़ी!"
    )
    val stepsDoi = listOf(
        "कदम 1 / 3 — दुकानदार दा नां जांचो" to
            "पैसे भेजने थमां पैहले स्क्रीन पर लिखेआ सही नां पढ़ो। जेकर कोई अनजान आखे 'इनाम लैने आस्तै PIN पाओ', तां फौरन रुको — पैसे लैने आस्तै कदें PIN नेईं लगदा!",
        "कदम 2 / 3 — PIN ते रसीद दा नियम" to
            "तुंदा 4 जां 6 अंकें दा PIN सिर्फ पैसे भेजने आस्तै हुंदा ऐ। तुंदी सुरक्षा आस्तै बैंक ऐप उप्पर तैरने आह्ली स्क्रीन बंद करदे न। कदें बी OTP, PIN जां AnyDesk शेयर नेईं करो।",
        "कदम 3 / 3 — SMS जां WhatsApp थमां सीधा शेयर" to
            "कोई बी शक्की मैसेज आए तां Messages जां WhatsApp च 'Share -> BankSathi' दबाओ। बैंकसाथी फौरन सोक्खी डोगरी च दस्सी देग कि एह् सुरक्षित ऐ जां ठगी!"
    )
    val current = when (language) {
        AppLanguage.HINDI -> stepsHi[step]
        AppLanguage.DOGRI -> stepsDoi[step]
        AppLanguage.ENGLISH -> stepsEn[step]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = current.first,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = current.second,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step < 2) step++ else onDismiss()
                }
            ) {
                Text(
                    if (step < 2) BilingualStrings.pick(language, "Next Step", "अगला कदम", "अगला कदम")
                    else BilingualStrings.pick(language, "Got It!", "समझ गया!", "समझी गेआ!")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(BilingualStrings.pick(language, "Close", "बंद करें", "बंद करो"))
            }
        }
    )
}

