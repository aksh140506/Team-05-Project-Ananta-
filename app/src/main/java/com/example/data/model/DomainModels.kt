package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val apiValue: String) {
    CITIZEN("CITIZEN"),
    VERIFIER("VERIFIER"),
    ADMIN("ADMIN");

    companion object {
        fun fromApi(raw: String?): UserRole =
            entries.firstOrNull { it.apiValue.equals(raw?.trim(), ignoreCase = true) } ?: CITIZEN
    }
}

enum class AppLanguage(val code: String, val displayLabel: String, val localeTag: String) {
    ENGLISH("en", "EN", "en-IN"),
    HINDI("hi", "हिन्दी", "hi-IN"),
    DOGRI("doi", "डोगरी", "doi-IN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class RegionalLanguageOption(
    val langCode: String,
    val displayName: String,
    val nativeName: String,
    val isFullUiSupported: Boolean
)

enum class BankingTask {
    WITHDRAW,
    TRANSFER,
    PASSBOOK_DBT,
    LOAN_KCC
}

enum class FacilityType {
    BC_POINT,
    IPPB,
    ATM,
    BRANCH
}

enum class CashStatus {
    CASH_READY,
    LOW_CASH,
    NO_CASH,
    UNKNOWN
}

enum class ReportStatus {
    GOT_CASH,
    LONG_LINE,
    NO_CASH_CLOSED
}

enum class RiskLevel {
    SAFE,
    CAUTION,
    HIGH_RISK
}

enum class AiSourceMode {
    GEMMA_LOCAL,
    FASTAPI_RAG,
    GEMINI_AI,
    OFFLINE_HEURISTIC
}

enum class HuskyMood {
    IDLE,
    LISTENING,
    THINKING,
    GUIDING,
    ALERT
}

enum class LowRamOverrideMode(val storageValue: String) {
    AUTO("AUTO"),
    FORCE_FAQ("FORCE_FAQ"),
    ALLOW_GEMMA("ALLOW_GEMMA");

    companion object {
        fun fromStorage(raw: String?): LowRamOverrideMode =
            entries.firstOrNull { it.storageValue == raw } ?: AUTO
    }
}

data class DistrictPack(
    val code: String,
    val nameEn: String,
    val nameHi: String,
    val centerLat: Double,
    val centerLon: Double,
    val nameDoi: String = nameHi
)

object DistrictCatalog {
    val defaultDistricts: List<DistrictPack> = listOf(
        DistrictPack(
            code = "lucknow_rampur",
            nameEn = "Lucknow Rural • Rampur",
            nameHi = "लखनऊ ग्रामीण • रामपुर",
            centerLat = 26.8467,
            centerLon = 80.9462,
            nameDoi = "लखनऊ ग्राएँ • रामपुर"
        ),
        DistrictPack(
            code = "barabanki_block",
            nameEn = "Barabanki • Dewa Block",
            nameHi = "बाराबंकी • देवा ब्लॉक",
            centerLat = 26.9265,
            centerLon = 81.1960,
            nameDoi = "बाराबंकी • देवा ब्लॉक"
        ),
        DistrictPack(
            code = "sitapur_tehsil",
            nameEn = "Sitapur • Biswan Tehsil",
            nameHi = "सीतापुर • बिसवां तहसील",
            centerLat = 27.4932,
            centerLon = 80.9998,
            nameDoi = "सीतापुर • बिसवां तहसील"
        )
    )

    fun findByCode(code: String?): DistrictPack =
        defaultDistricts.firstOrNull { it.code == code } ?: defaultDistricts.first()
}

data class DeviceMemoryStatus(
    val totalRamMb: Long,
    val isLowRamDevice: Boolean,
    val thresholdMb: Long = 3584L
) {
    val totalRamGbLabel: String
        get() = String.format(java.util.Locale.US, "%.1f GB", totalRamMb / 1024.0)
}

data class SignedUpdatePackage(
    val packageId: String,
    val districtCode: String,
    val tableName: String,
    val version: Int,
    val rowsPayloadJson: String,
    val signature: String
)

data class SyncApplyResult(
    val success: Boolean,
    val appliedPackages: Int,
    val rolledBack: Boolean,
    val sourceLabel: String,
    val messageEn: String,
    val messageHi: String
)

@IgnoreExtraProperties
data class FacilityRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val updatedByUid: String = "",
    val nameEn: String = "",
    val nameHi: String = "",
    val bankName: String = "Gramin Vikas Bank",
    val facilityType: String = FacilityType.BC_POINT.name,
    val villageOrArea: String = "",
    val districtCode: String = "lucknow_rampur",
    val distanceMeters: Long = 0L,
    val lat: Double = 26.8467,
    val lon: Double = 80.9462,
    val isOpen: Boolean = true,
    val cashStatus: String = CashStatus.CASH_READY.name,
    val lastReportStatus: String = "NONE",
    val reportsCount: Long = 0L,
    val verifiedDate: String = "2026-11-21",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedType(): FacilityType =
        FacilityType.entries.firstOrNull { it.name == facilityType } ?: FacilityType.BC_POINT

    fun parsedCashStatus(): CashStatus =
        CashStatus.entries.firstOrNull { it.name == cashStatus } ?: CashStatus.UNKNOWN
}

@IgnoreExtraProperties
data class StatusReportRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val facilityId: String = "",
    val status: String = ReportStatus.GOT_CASH.name,
    val receiptReceived: Boolean? = null,
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TripChecklistRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskType: String = BankingTask.WITHDRAW.name,
    val amountInr: Long = 0L,
    val recommendedFacilityType: String = FacilityType.BC_POINT.name,
    val checkedDocIds: List<String> = emptyList(),
    val receiptVerified: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScamCheckRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val inputSnippet: String = "",
    val riskLevel: String = RiskLevel.CAUTION.name,
    val explanationEn: String = "",
    val explanationHi: String = "",
    val sourceMode: String = AiSourceMode.OFFLINE_HEURISTIC.name,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun parsedRisk(): RiskLevel =
        RiskLevel.entries.firstOrNull { it.name == riskLevel } ?: RiskLevel.CAUTION

    fun parsedSource(): AiSourceMode =
        AiSourceMode.entries.firstOrNull { it.name == sourceMode } ?: AiSourceMode.OFFLINE_HEURISTIC
}

data class DocumentRequirement(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val noteEn: String,
    val noteHi: String,
    val isOriginalRequired: Boolean,
    val copiesNeeded: Int = 0,
    val titleDoi: String = "",
    val noteDoi: String = ""
)

data class RoutingRecommendation(
    val task: BankingTask,
    val amountInr: Int,
    val facilityType: FacilityType,
    val reasonEn: String,
    val reasonHi: String,
    val documents: List<DocumentRequirement>,
    val reasonDoi: String = ""
)

data class CachedFaqRecord(
    val faqId: Int,
    val question: String,
    val answer: String,
    val langCode: String,
    val sourceId: String,
    val verifiedOn: String
)

data class ScamRuleRecord(
    val ruleId: Int,
    val triggerPattern: String,
    val severity: Int,
    val langCode: String,
    val updatedAt: String
)

data class SyncMetaRecord(
    val tableName: String,
    val version: Int,
    val syncedAt: String,
    val districtCode: String = "lucknow_rampur",
    val packageId: String = "seed_v1",
    val signatureStatus: String = "VERIFIED_SEED"
)

data class AssistantAnswer(
    val replyEn: String,
    val replyHi: String,
    val recommendedTask: BankingTask? = null,
    val suggestedAmountInr: Int = 0,
    val sourceMode: AiSourceMode = AiSourceMode.OFFLINE_HEURISTIC,
    val mood: HuskyMood = HuskyMood.GUIDING,
    val abstained: Boolean = false,
    val sourceId: String? = null,
    val verifiedOn: String? = null,
    val replyDoi: String = "",
    val isFallbackFromError: Boolean = false
)

data class ScamAnalysisResult(
    val riskLevel: RiskLevel,
    val explanationEn: String,
    val explanationHi: String,
    val actionTipEn: String,
    val actionTipHi: String,
    val sourceMode: AiSourceMode,
    val explanationDoi: String = "",
    val actionTipDoi: String = "",
    val isFallbackFromError: Boolean = false
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val textEn: String,
    val textHi: String = textEn,
    val sourceMode: AiSourceMode? = null,
    val recommendedTask: BankingTask? = null,
    val suggestedAmountInr: Int = 0,
    val isScamAlert: Boolean = false,
    val abstained: Boolean = false,
    val sourceId: String? = null,
    val verifiedOn: String? = null,
    val isStreaming: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
    val textDoi: String = textHi
)
