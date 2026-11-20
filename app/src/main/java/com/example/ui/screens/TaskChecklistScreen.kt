package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.VerifiedUser
import com.example.R
import com.example.data.model.BankingTask
import com.example.data.model.CachedFaqRecord
import com.example.data.model.CashStatus
import com.example.data.model.DistrictCatalog
import com.example.data.model.FacilityRecord
import com.example.data.repository.FacilityGeoUtils
import com.example.data.repository.PincodeFacilityResolver
import com.example.ui.GraminSetuUiState
import com.example.ui.i18n.BilingualStrings

@Composable
fun TaskChecklistScreen(
    uiState: GraminSetuUiState,
    facilities: List<FacilityRecord>,
    cachedFaqs: List<CachedFaqRecord> = emptyList(),
    onSelectTaskAndAmount: (BankingTask, Int) -> Unit,
    onToggleDocument: (String) -> Unit,
    onVerifyReceipt: (Boolean) -> Unit,
    onViewAllFacilities: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = uiState.language
    val rec = uiState.recommendation

    val activeDistrict = remember(uiState.selectedDistrictCode) {
        DistrictCatalog.findByCode(uiState.selectedDistrictCode)
    }
    val hasActivePin = remember(uiState.activePincode) {
        PincodeFacilityResolver.isValidIndianPincode(uiState.activePincode)
    }
    val targetCode = if (hasActivePin) {
        PincodeFacilityResolver.districtCodeForPincode(uiState.activePincode)
    } else {
        activeDistrict.code
    }
    val centerLat = if (hasActivePin) uiState.activePincodeLat else activeDistrict.centerLat
    val centerLon = if (hasActivePin) uiState.activePincodeLon else activeDistrict.centerLon

    val matchingFacility = remember(facilities, rec.facilityType, targetCode, centerLat, centerLon) {
        val districtMatches = facilities.filter { it.districtCode == targetCode }
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

        withDistance.firstOrNull { it.parsedType() == rec.facilityType && it.isOpen }
            ?: withDistance.firstOrNull { it.parsedType() == rec.facilityType }
            ?: withDistance.firstOrNull { it.isOpen }
            ?: withDistance.firstOrNull()
    }

    val packedCount = rec.documents.count { uiState.checkedDocIds.contains(it.id) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Task Switcher Pills + Checklist v1 badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgePill(
                text = "Checklist v1",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                textColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            BankingTask.entries.forEach { task ->
                val selected = task == uiState.selectedTask
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTaskAndAmount(task, uiState.selectedAmountInr) },
                    label = {
                        Text(
                            text = BilingualStrings.taskTitle(task, lang),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Amount Presets for Cash Out
        if (uiState.selectedTask == BankingTask.WITHDRAW) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "How much cash do you need?",
                            "आपको कितने रुपये निकालने हैं?",
                            "तुसेंगी किन्ने रुपये कड्ढने न?"
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1000, 3000, 5000, 10000, 15000, 25000).forEach { amt ->
                            val selected = uiState.selectedAmountInr == amt
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTaskAndAmount(BankingTask.WITHDRAW, amt) },
                                label = { Text("₹$amt") },
                                modifier = Modifier.testTag("amount_chip_$amt")
                            )
                        }
                    }
                }
            }
        }

        // Recommended Facility Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = BilingualStrings.pick(lang, "Recommended Facility", "आपके काम की सही जगह", "तुंदे कम्म आस्तै सही थाहर"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = BilingualStrings.facilityTypeLabel(rec.facilityType, lang),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = BilingualStrings.pick(lang, rec.reasonEn, rec.reasonHi, rec.reasonDoi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (matchingFacility != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val effectiveStatus = FacilityGeoUtils.effectiveCashStatus(matchingFacility)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = BilingualStrings.pick(lang, matchingFacility.nameEn, matchingFacility.nameHi),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${FacilityGeoUtils.formatDistance(matchingFacility.distanceMeters)} • ${
                                        BilingualStrings.cashStatusLabel(
                                            matchingFacility.isOpen,
                                            effectiveStatus,
                                            lang
                                        )
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        !matchingFacility.isOpen || effectiveStatus == CashStatus.NO_CASH ->
                                            MaterialTheme.colorScheme.error
                                        effectiveStatus == CashStatus.UNKNOWN ->
                                            MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                            }
                            OutlinedButton(onClick = onViewAllFacilities) {
                                Text(BilingualStrings.pick(lang, "All Centers", "सभी केंद्र", "सारे केंदर"))
                            }
                        }
                    }
                }
            }
        }

        // Visual Bag Check Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_bag_check_banner_1794314111352),
                contentDescription = BilingualStrings.pick(
                    lang,
                    "Bag check items illustration",
                    "थैले में रखने वाले जरूरी कागजात",
                    "थैले च रक्खने आह्ले जरूरी कागजात"
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = BilingualStrings.pick(
                        lang,
                        "Bag Check: $packedCount / ${rec.documents.size} packed",
                        "बैग जांच: $packedCount / ${rec.documents.size} तैयार",
                        "थैला जांच: $packedCount / ${rec.documents.size} तैयार"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Interactive Checklist Rows
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rec.documents.forEach { doc ->
                val checked = uiState.checkedDocIds.contains(doc.id)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (checked) 0.dp else 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (checked) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onToggleDocument(doc.id) }
                        .testTag("checklist_item_${doc.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (checked) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = BilingualStrings.pick(lang, doc.titleEn, doc.titleHi, doc.titleDoi),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = BilingualStrings.pick(lang, doc.noteEn, doc.noteHi, doc.noteDoi),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (doc.isOriginalRequired) {
                                    BadgePill(
                                        text = BilingualStrings.pick(lang, "Original", "असली", "असली"),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (doc.copiesNeeded > 0) {
                                    BadgePill(
                                        text = BilingualStrings.pick(
                                            lang,
                                            "${doc.copiesNeeded} Copies",
                                            "${doc.copiesNeeded} कॉपी",
                                            "${doc.copiesNeeded} कॉपी"
                                        ),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        textColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Post-Visit AePS Receipt Verification Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (uiState.receiptVerified) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = BilingualStrings.pick(
                            lang,
                            "After Your Visit: Receipt Check",
                            "काम पूरा होने के बाद: रसीद जांच",
                            "कम्म होणे दे बाद: रसीद जांच"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = BilingualStrings.pick(
                        lang,
                        "Did you receive a printed paper receipt & check your bank SMS?",
                        "क्या आपको छपी हुई रसीद मिली और आपने बैंक SMS जांचा?",
                        "क्या तुसेंगी छपी होई रसीद मिली ते तुसें बैंक SMS जांचेआ?"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onVerifyReceipt(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("receipt_yes_button")
                    ) {
                        Text(BilingualStrings.pick(lang, "Yes, Got Receipt", "हाँ, रसीद मिली", "हां, रसीद मिली"))
                    }
                    OutlinedButton(
                        onClick = { onVerifyReceipt(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("receipt_no_button")
                    ) {
                        Text(BilingualStrings.pick(lang, "No Receipt", "रसीद नहीं मिली", "रसीद नेईं मिली"))
                    }
                }
            }
        }

        if (cachedFaqs.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().testTag("cached_faq_section")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = BilingualStrings.pick(
                                lang,
                                "Verified Offline FAQs (CACHED_FAQ)",
                                "सत्यापित ऑफलाइन प्रश्न-उत्तर (CACHED_FAQ)",
                                "पक्के ऑफलाइन सुआल-जवाब (CACHED_FAQ)"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    cachedFaqs.take(4).forEach { faq ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = faq.question,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = faq.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${faq.sourceId} • Verified: ${faq.verifiedOn}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(84.dp))
    }
}

@Composable
private fun BadgePill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
