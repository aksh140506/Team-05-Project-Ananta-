package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import com.example.data.model.AppLanguage
import com.example.data.model.CashStatus
import com.example.data.model.DistrictCatalog
import com.example.data.model.FacilityRecord
import com.example.data.model.FacilityType
import com.example.data.model.ReportStatus
import com.example.data.model.SyncMetaRecord
import com.example.data.model.UserRole
import com.example.data.repository.FacilityGeoUtils
import com.example.data.repository.PincodeFacilityResolver
import com.example.ui.DataUiState
import com.example.ui.i18n.BilingualStrings
import java.util.Locale

@Composable
fun FacilitiesScreen(
    language: AppLanguage,
    facilitiesState: DataUiState<List<FacilityRecord>>,
    cachedFacilities: List<FacilityRecord>,
    selectedDistrictCode: String = "lucknow_rampur",
    radiusKmFilter: Int = 10,
    syncMeta: List<SyncMetaRecord> = emptyList(),
    lastSyncRolledBack: Boolean = false,
    activePincode: String = "",
    activePincodeArea: String = "",
    activePincodeLat: Double = 26.8467,
    activePincodeLon: Double = 80.9462,
    isResolvingPincode: Boolean = false,
    pincodeError: String? = null,
    showPincodePrompt: Boolean = true,
    userRole: UserRole = UserRole.CITIZEN,
    isSignedIn: Boolean = false,
    onSubmitPincode: (String) -> Unit = {},
    onRequestChangePincode: () -> Unit = {},
    onDismissPincodePrompt: () -> Unit = {},
    onSelectDistrict: (String) -> Unit = {},
    onRadiusFilterChange: (Int) -> Unit = {},
    onRefreshSync: () -> Unit = {},
    onSubmitReport: (FacilityRecord, ReportStatus, Boolean?) -> Unit,
    onVerifyFacility: (FacilityRecord) -> Unit = {},
    onAddFacility: (String, String, FacilityType, String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<FacilityType?>(null) }
    var openOnly by remember { mutableStateOf(false) }
    var reportingFacility by remember { mutableStateOf<FacilityRecord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pincodeInput by remember(activePincode, showPincodePrompt) { mutableStateOf(activePincode) }

    LaunchedEffect(activePincode) {
        if (activePincode.isNotBlank() && pincodeInput.isBlank()) {
            pincodeInput = activePincode
        }
    }

    val hasValidPincode = PincodeFacilityResolver.isValidIndianPincode(activePincode)
    if (!hasValidPincode || showPincodePrompt) {
        PincodeFirstGate(
            language = language,
            pincodeInput = pincodeInput,
            activePincode = activePincode,
            isResolving = isResolvingPincode,
            errorText = pincodeError,
            onPincodeInputChange = { next ->
                pincodeInput = next.filter { ch -> ch.isDigit() }.take(6)
            },
            onSubmit = { onSubmitPincode(pincodeInput) },
            onQuickSample = { sample ->
                pincodeInput = sample
                onSubmitPincode(sample)
            },
            onDismiss = onDismissPincodePrompt,
            modifier = modifier
        )
        return
    }

    val activeDistrict = remember(selectedDistrictCode) {
        DistrictCatalog.findByCode(selectedDistrictCode)
    }
    val targetCode = PincodeFacilityResolver.districtCodeForPincode(activePincode)

    val rawList = remember(facilitiesState, cachedFacilities) {
        when (facilitiesState) {
            is DataUiState.Success -> (facilitiesState.data + cachedFacilities).distinctBy { it.id }
            else -> cachedFacilities
        }
    }

    val districtWithDistance = remember(rawList, targetCode, activePincodeLat, activePincodeLon) {
        val pinMatches = rawList.filter { it.districtCode == targetCode }
        val pool = if (pinMatches.isNotEmpty()) pinMatches else rawList
        pool.map { item ->
            val haversine = FacilityGeoUtils.haversineMeters(
                activePincodeLat,
                activePincodeLon,
                item.lat,
                item.lon
            )
            val computedDistance = if (item.lat == activePincodeLat && item.lon == activePincodeLon && item.distanceMeters > 0L) {
                item.distanceMeters
            } else {
                haversine
            }
            item.copy(distanceMeters = computedDistance)
        }.sortedBy { it.distanceMeters }
    }

    val branchCount = remember(districtWithDistance) {
        districtWithDistance.count { it.parsedType() == FacilityType.BRANCH }
    }
    val bcCount = remember(districtWithDistance) {
        districtWithDistance.count { it.parsedType() == FacilityType.BC_POINT }
    }
    val postOfficeCount = remember(districtWithDistance) {
        districtWithDistance.count { it.parsedType() == FacilityType.IPPB }
    }
    val unknownCount = remember(districtWithDistance) {
        districtWithDistance.count { FacilityGeoUtils.effectiveCashStatus(it) == CashStatus.UNKNOWN }
    }

    val filtered = remember(districtWithDistance, selectedFilter, openOnly, radiusKmFilter) {
        districtWithDistance.filter { item ->
            val matchesType = selectedFilter == null || item.parsedType() == selectedFilter
            val matchesOpen = !openOnly || (item.isOpen && FacilityGeoUtils.effectiveCashStatus(item) != CashStatus.UNKNOWN)
            val matchesRadius = radiusKmFilter <= 0 || item.distanceMeters <= radiusKmFilter * 1000L
            matchesType && matchesOpen && matchesRadius
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Active Pincode & Added Coverage Header
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("active_pincode_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PinDrop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PIN $activePincode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = activePincodeArea.ifBlank {
                                        BilingualStrings.pick(
                                            language,
                                            activeDistrict.nameEn,
                                            activeDistrict.nameHi,
                                            activeDistrict.nameDoi
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onRefreshSync,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("sync_district_pack_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(BilingualStrings.pick(language, "Refresh", "ताज़ा", "ताज़ा"))
                            }
                            OutlinedButton(
                                onClick = onRequestChangePincode,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("change_pincode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EditLocationAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(BilingualStrings.pick(language, "Change", "बदलें", "बदलो"))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PincodeSummaryBadge(
                            label = BilingualStrings.pick(
                                language,
                                "$branchCount Bank",
                                "$branchCount बैंक",
                                "$branchCount बैंक"
                            )
                        )
                        PincodeSummaryBadge(
                            label = BilingualStrings.pick(
                                language,
                                "$bcCount BC Mitra",
                                "$bcCount बैंक मित्र (BC)",
                                "$bcCount बैंक मित्र (BC)"
                            )
                        )
                        PincodeSummaryBadge(
                            label = BilingualStrings.pick(
                                language,
                                "$postOfficeCount Post Office Bank",
                                "$postOfficeCount डाकघर बैंक",
                                "$postOfficeCount डाकघर बैंक"
                            )
                        )
                    }
                }
            }

            // Radius & Facility Type Filter bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text(BilingualStrings.pick(language, "All Centers", "सभी केंद्र", "सारे केंदर")) },
                    modifier = Modifier.testTag("filter_type_all")
                )
                FacilityType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedFilter == type,
                        onClick = { selectedFilter = if (selectedFilter == type) null else type },
                        label = { Text(BilingualStrings.facilityTypeLabel(type, language)) },
                        modifier = Modifier.testTag("filter_type_${type.name.lowercase()}")
                    )
                }
                listOf(2, 5, 10, 0).forEach { km ->
                    FilterChip(
                        selected = radiusKmFilter == km,
                        onClick = { onRadiusFilterChange(km) },
                        label = {
                            Text(
                                if (km == 0) BilingualStrings.pick(language, "All km", "सभी दूरी", "सारी दूरी")
                                else "${km} km"
                            )
                        },
                        modifier = Modifier.testTag("radius_chip_$km")
                    )
                }
                FilterChip(
                    selected = openOnly,
                    onClick = { openOnly = !openOnly },
                    label = { Text(BilingualStrings.pick(language, "Verified Open", "केवल सत्यापित खुले", "सिर्फ जांचे खुले")) }
                )
            }

            if (unknownCount > 0 || lastSyncRolledBack) {
                Surface(
                    color = if (lastSyncRolledBack) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (unknownCount > 0) {
                            BilingualStrings.pick(
                                language,
                                "$unknownCount center(s) marked Unknown (verified >7 days ago or missing date).",
                                "$unknownCount केंद्र की स्थिति 'अज्ञात' है (7 दिन से पुराना सत्यापन)।",
                                "$unknownCount केंदरें दी स्थिति 'अज्ञात' ऐ (7 दिन थमां पुराणी जांच)।"
                            )
                        } else {
                            BilingualStrings.pick(
                                language,
                                "Sync check rolled back — showing safe local cache.",
                                "सिंक जांच में त्रुटि — सुरक्षित स्थानीय सूची दिख रही है।",
                                "सिंक जांच च गड़बड़ी — सुरक्षित स्थानीय सूची दिखा दी ऐ।"
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lastSyncRolledBack) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "No centers match this filter within ${if (radiusKmFilter == 0) "your area" else "$radiusKmFilter km"}.",
                                "इस फ़िल्टर में कोई केंद्र नहीं मिला — 'सभी दूरी' चुनें।",
                                "इस फ़िल्टर च कोई केंदर नेईं मिल्लेआ — 'सारी दूरी' चुनो।"
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 110.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { facility ->
                        FacilityCard(
                            facility = facility,
                            language = language,
                            userRole = userRole,
                            isSignedIn = isSignedIn,
                            onReportClick = { reportingFacility = facility },
                            onVerifyClick = { onVerifyFacility(facility) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .testTag("add_facility_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = BilingualStrings.pick(language, "Add center", "नया केंद्र जोड़ें", "नमां केंदर जोड़ो")
            )
        }
    }

    reportingFacility?.let { facility ->
        FacilityReportDialog(
            facility = facility,
            language = language,
            onDismiss = { reportingFacility = null },
            onSubmit = { status, receipt ->
                onSubmitReport(facility, status, receipt)
                reportingFacility = null
            }
        )
    }

    if (showAddDialog) {
        AddFacilityDialog(
            language = language,
            onDismiss = { showAddDialog = false },
            onSave = { nameEn, nameHi, type, area, dist ->
                onAddFacility(nameEn, nameHi, type, area, dist)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FacilityCard(
    facility: FacilityRecord,
    language: AppLanguage,
    userRole: UserRole = UserRole.CITIZEN,
    isSignedIn: Boolean = false,
    onReportClick: () -> Unit,
    onVerifyClick: () -> Unit = {}
) {
    val effectiveCash = FacilityGeoUtils.effectiveCashStatus(facility)
    val isUnknownOverdue = effectiveCash == CashStatus.UNKNOWN
    val isClosedOrOut = !isUnknownOverdue && (!facility.isOpen || effectiveCash == CashStatus.NO_CASH)

    val statusColor = when {
        isUnknownOverdue -> MaterialTheme.colorScheme.tertiary
        isClosedOrOut -> MaterialTheme.colorScheme.error
        effectiveCash == CashStatus.LOW_CASH -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusContainer = when {
        isUnknownOverdue -> MaterialTheme.colorScheme.tertiaryContainer
        isClosedOrOut -> MaterialTheme.colorScheme.errorContainer
        effectiveCash == CashStatus.LOW_CASH -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusIcon = when {
        isUnknownOverdue -> Icons.AutoMirrored.Rounded.HelpOutline
        isClosedOrOut -> Icons.Rounded.ErrorOutline
        effectiveCash == CashStatus.LOW_CASH -> Icons.Rounded.Schedule
        else -> Icons.Rounded.CheckCircle
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("facility_card_${facility.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = statusContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = BilingualStrings.pick(language, facility.nameEn, facility.nameHi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${facility.bankName} • ${BilingualStrings.facilityTypeLabel(facility.parsedType(), language)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${facility.villageOrArea} (${FacilityGeoUtils.formatDistance(facility.distanceMeters)}) • GPS ${
                            String.format(Locale.US, "%.4f", facility.lat)
                        }, ${String.format(Locale.US, "%.4f", facility.lon)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUnknownOverdue) Icons.Rounded.WarningAmber else Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = if (isUnknownOverdue) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val verifiedDisplay = facility.verifiedDate.ifBlank {
                            BilingualStrings.pick(language, "Not verified yet", "अभी सत्यापित नहीं", "अजें जांचेआ नेईं")
                        }
                        Text(
                            text = if (isUnknownOverdue) {
                                BilingualStrings.pick(
                                    language,
                                    "Unverified / Overdue: $verifiedDisplay",
                                    "सत्यापन पुराना: $verifiedDisplay",
                                    "जांच पुराणी ऐ: $verifiedDisplay"
                                )
                            } else {
                                BilingualStrings.pick(
                                    language,
                                    "Verified: $verifiedDisplay",
                                    "सत्यापित तारीख: $verifiedDisplay",
                                    "जांच तारीख: $verifiedDisplay"
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnknownOverdue) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val canOfficialVerify = isSignedIn && (userRole == UserRole.VERIFIER || userRole == UserRole.ADMIN)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusContainer,
                    modifier = Modifier.testTag("facility_status_${facility.id}")
                ) {
                    Text(
                        text = BilingualStrings.cashStatusLabel(facility.isOpen, effectiveCash, language),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onVerifyClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (canOfficialVerify) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (canOfficialVerify) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("verify_facility_${facility.id}")
                    ) {
                        Icon(
                            imageVector = if (canOfficialVerify) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                            contentDescription = BilingualStrings.pick(
                                language,
                                "Official verify",
                                "आधिकारिक सत्यापन",
                                "पक्की जांच"
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = BilingualStrings.pick(language, "Verify", "सत्यापित करें", "जांचो"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onReportClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("report_status_${facility.id}")
                    ) {
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Update (${facility.reportsCount})",
                                "अपडेट (${facility.reportsCount})",
                                "अपडेट (${facility.reportsCount})"
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FacilityReportDialog(
    facility: FacilityRecord,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSubmit: (ReportStatus, Boolean?) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(ReportStatus.GOT_CASH) }
    var receiptReceived by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = BilingualStrings.pick(
                    language,
                    "Update Live Status",
                    "ताज़ा स्थिति बताएं",
                    "ताज़ा स्थिति दस्सो"
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = BilingualStrings.pick(language, facility.nameEn, facility.nameHi),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ReportStatus.entries.forEach { st ->
                    val isSelected = selectedStatus == st
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStatus = st }
                    ) {
                        Text(
                            text = BilingualStrings.reportStatusLabel(st, language),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                if (selectedStatus == ReportStatus.GOT_CASH) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Did you get a paper receipt?",
                                "क्या आपको कागज़ की रसीद मिली?",
                                "क्या तुसेंगी कागज़ दी रसीद मिली?"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = receiptReceived,
                            onCheckedChange = { receiptReceived = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        selectedStatus,
                        if (selectedStatus == ReportStatus.GOT_CASH) receiptReceived else null
                    )
                },
                modifier = Modifier.testTag("confirm_status_report")
            ) {
                Text(BilingualStrings.pick(language, "Share Update", "साझा करें", "सांझा करो"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(BilingualStrings.pick(language, "Cancel", "रद्द करें", "रद्द करो"))
            }
        }
    )
}

@Composable
private fun AddFacilityDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String, FacilityType, String, Long) -> Unit
) {
    var nameEn by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("500") }
    var selectedType by remember { mutableStateOf(FacilityType.BC_POINT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(BilingualStrings.pick(language, "Add Village Banking Point", "नया बैंकिंग केंद्र जोड़ें", "नमां बैंकिंग केंदर जोड़ो"))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(BilingualStrings.pick(language, "Center / Agent Name", "केंद्र या एजेंट का नाम", "केंदर जां एजेंट दा नां")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text(BilingualStrings.pick(language, "Village / Chowk", "गाँव / चौक", "पिंड / चौक")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(BilingualStrings.pick(language, "Distance (meters)", "दूरी (मीटर में)", "दूरी (मीटर च)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FacilityType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(BilingualStrings.facilityTypeLabel(t, language)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dist = distanceText.toLongOrNull() ?: 500L
                    onSave(nameEn, nameEn, selectedType, area, dist)
                },
                enabled = nameEn.trim().length >= 2 && area.trim().length >= 2
            ) {
                Text(BilingualStrings.pick(language, "Save", "सेव करें", "सेव करो"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(BilingualStrings.pick(language, "Cancel", "रद्द करें", "रद्द करो"))
            }
        }
    )
}

@Composable
private fun PincodeFirstGate(
    language: AppLanguage,
    pincodeInput: String,
    activePincode: String,
    isResolving: Boolean,
    errorText: String?,
    onPincodeInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onQuickSample: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activePincode.isNotBlank()) {
        BackHandler { onDismiss() }
    }

    val samplePincodes = remember(language) {
        listOf(
            "226001" to BilingualStrings.pick(language, "226001 • Lucknow", "226001 • लखनऊ", "226001 • लखनऊ"),
            "180001" to BilingualStrings.pick(language, "180001 • Jammu", "180001 • जम्मू", "180001 • जम्मू"),
            "225001" to BilingualStrings.pick(language, "225001 • Barabanki", "225001 • बाराबंकी", "225001 • बाराबंकी"),
            "110001" to BilingualStrings.pick(language, "110001 • Delhi", "110001 • दिल्ली", "110001 • दिल्ली")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pincode_prompt_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PinDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Enter Your 6-Digit Pincode",
                                "अपना 6 अंकों का पिनकोड डालें",
                                "अपना 6 अंका दा पिनकोड भरो"
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Find & auto-add nearest Bank Branch, BC & Post Office Bank",
                                "पास के बैंक, बैंक मित्र (BC) और डाकघर बैंक तुरंत जोड़ें",
                                "नेड़े दे बैंक, बैंक मित्र (BC) ते डाकघर बैंक फौरन जोड़ो"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PincodeCoveragePill(
                        icon = Icons.Rounded.AccountBalance,
                        label = BilingualStrings.pick(language, "Bank Branch", "बैंक शाखा", "बैंक शाखा")
                    )
                    PincodeCoveragePill(
                        icon = Icons.Rounded.Storefront,
                        label = BilingualStrings.pick(language, "Bank Mitra (BC)", "बैंक मित्र (BC)", "बैंक मित्र (BC)")
                    )
                    PincodeCoveragePill(
                        icon = Icons.Rounded.LocalPostOffice,
                        label = BilingualStrings.pick(language, "Post Office Bank", "डाकघर बैंक", "डाकघर बैंक")
                    )
                }

                OutlinedTextField(
                    value = pincodeInput,
                    onValueChange = onPincodeInputChange,
                    label = {
                        Text(
                            BilingualStrings.pick(
                                language,
                                "6-digit Area Pincode",
                                "6 अंकों का पिनकोड (जैसे 226001)",
                                "6 अंका दा पिनकोड (जि’यां 180001)"
                            )
                        )
                    },
                    placeholder = { Text("226001") },
                    singleLine = true,
                    isError = !errorText.isNullOrBlank(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isResolving && pincodeInput.length == 6) {
                                onSubmit()
                            }
                        }
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pincode_input")
                )

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("pincode_error_text")
                    )
                }

                Button(
                    onClick = onSubmit,
                    enabled = !isResolving && pincodeInput.length == 6,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pincode_submit_button")
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            BilingualStrings.pick(
                                language,
                                "Adding Bank, BC & Post Office...",
                                "बैंक, BC व डाकघर जुड़ रहे हैं...",
                                "बैंक, BC ते डाकघर जुड़े जा दे न..."
                            )
                        )
                    } else {
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Find & Add Nearest Centers",
                                "नजदीकी बैंक, BC व डाकघर जोड़ें",
                                "नेड़े दे बैंक, BC ते डाकघर जोड़ो"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = BilingualStrings.pick(
                        language,
                        "Quick-fill popular pincodes:",
                        "जल्दी चुनें:",
                        "फौरन चुनो:"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    samplePincodes.forEach { (pin, label) ->
                        AssistChip(
                            onClick = { if (!isResolving) onQuickSample(pin) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("quick_pin_$pin")
                        )
                    }
                }

                if (activePincode.isNotBlank()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                            .testTag("cancel_pincode_button")
                    ) {
                        Text(
                            BilingualStrings.pick(
                                language,
                                "Keep PIN $activePincode",
                                "पिन $activePincode ही रखें",
                                "पिन $activePincode गै रक्खो"
                            )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(84.dp))
    }
}

@Composable
private fun PincodeCoveragePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PincodeSummaryBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
