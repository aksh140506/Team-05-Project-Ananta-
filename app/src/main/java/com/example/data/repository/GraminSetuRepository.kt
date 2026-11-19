package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.BankSathiDao
import com.example.data.local.CachedChecklistEntity
import com.example.data.local.CachedFacilityEntity
import com.example.data.local.CachedFaqEntity
import com.example.data.local.CachedScamCheckEntity
import com.example.data.local.ChecklistDao
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.ChecklistMasterEntity
import com.example.data.local.FacilityDao
import com.example.data.local.FacilityMasterCacheEntity
import com.example.data.local.GraminSetuDatabase
import com.example.data.local.LanguageEntity
import com.example.data.local.ScamCheckDao
import com.example.data.local.ScamRuleEntity
import com.example.data.local.SyncMetaEntity
import com.example.data.local.UserPreferenceEntity
import com.example.data.model.AiSourceMode
import com.example.data.model.BankingTask
import com.example.data.model.CachedFaqRecord
import com.example.data.model.CashStatus
import com.example.data.model.DistrictCatalog
import com.example.data.model.FacilityRecord
import com.example.data.model.FacilityType
import com.example.data.model.ReportStatus
import com.example.data.model.RiskLevel
import com.example.data.model.ScamCheckRecord
import com.example.data.model.ScamRuleRecord
import com.example.data.model.SignedUpdatePackage
import com.example.data.model.SyncApplyResult
import com.example.data.model.SyncMetaRecord
import com.example.data.model.TripChecklistRecord
import com.example.data.model.UserRole
import com.example.data.remote.BankSathiBackendClient
import com.example.data.remote.DeltaSyncRequest
import com.example.data.remote.DeltaSyncVerifier
import com.example.data.remote.OperationType
import com.example.data.remote.PackageVerifyResult
import com.example.data.remote.VerifyFacilityRequest
import com.example.data.remote.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GraminSetuRepository(
    private val db: FirebaseFirestore,
    private val facilityDao: FacilityDao? = null,
    private val checklistDao: ChecklistDao? = null,
    private val scamCheckDao: ScamCheckDao? = null,
    private val bankSathiDao: BankSathiDao? = null,
    private val isCloudSyncEnabled: Boolean = true,
    private val roomDb: GraminSetuDatabase? = null,
    private val backendClient: BankSathiBackendClient = BankSathiBackendClient()
) {
    constructor(
        context: Context,
        facilityDao: FacilityDao? = null,
        checklistDao: ChecklistDao? = null,
        scamCheckDao: ScamCheckDao? = null,
        bankSathiDao: BankSathiDao? = null,
        isCloudSyncEnabled: Boolean = true,
        roomDb: GraminSetuDatabase? = null,
        backendClient: BankSathiBackendClient = BankSathiBackendClient()
    ) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        facilityDao = facilityDao,
        checklistDao = checklistDao,
        scamCheckDao = scamCheckDao,
        bankSathiDao = bankSathiDao,
        isCloudSyncEnabled = isCloudSyncEnabled,
        roomDb = roomDb,
        backendClient = backendClient
    )

    suspend fun seedBankSathiCatalogIfEmpty() {
        val dao = bankSathiDao ?: return
        val existingCount = dao.languageCount()
        if (existingCount > 0) {
            ensureDogriCatalogSeeded(dao)
            return
        }

        val languages = listOf(
            LanguageEntity("en", "English"),
            LanguageEntity("hi", "हिन्दी (Hindi)"),
            LanguageEntity("doi", "डोगरी (Dogri)")
        )
        dao.upsertLanguages(languages)
        dao.upsertUserPreference(UserPreferenceEntity(prefId = 1, langCode = "hi", playbackSpeed = 0.95f))

        val checklists = listOf(
            ChecklistMasterEntity("chk_withdraw_en", BankingTask.WITHDRAW.name, "en", 1),
            ChecklistMasterEntity("chk_withdraw_hi", BankingTask.WITHDRAW.name, "hi", 1),
            ChecklistMasterEntity("chk_withdraw_doi", BankingTask.WITHDRAW.name, "doi", 1),
            ChecklistMasterEntity("chk_transfer_en", BankingTask.TRANSFER.name, "en", 1),
            ChecklistMasterEntity("chk_transfer_hi", BankingTask.TRANSFER.name, "hi", 1),
            ChecklistMasterEntity("chk_transfer_doi", BankingTask.TRANSFER.name, "doi", 1),
            ChecklistMasterEntity("chk_dbt_en", BankingTask.PASSBOOK_DBT.name, "en", 1),
            ChecklistMasterEntity("chk_dbt_hi", BankingTask.PASSBOOK_DBT.name, "hi", 1),
            ChecklistMasterEntity("chk_dbt_doi", BankingTask.PASSBOOK_DBT.name, "doi", 1),
            ChecklistMasterEntity("chk_kcc_en", BankingTask.LOAN_KCC.name, "en", 1),
            ChecklistMasterEntity("chk_kcc_hi", BankingTask.LOAN_KCC.name, "hi", 1),
            ChecklistMasterEntity("chk_kcc_doi", BankingTask.LOAN_KCC.name, "doi", 1)
        )
        dao.upsertChecklists(checklists)

        val checklistItems = listOf(
            ChecklistItemEntity(1, "chk_withdraw_en", 1, "Original Aadhaar Card for biometric scan"),
            ChecklistItemEntity(2, "chk_withdraw_en", 2, "Registered Mobile Phone for SMS balance alert"),
            ChecklistItemEntity(3, "chk_withdraw_hi", 1, "बायोमेट्रिक जांच के लिए असली आधार कार्ड"),
            ChecklistItemEntity(4, "chk_withdraw_hi", 2, "बैलेंस SMS के लिए बैंक से जुड़ा मोबाइल फोन"),
            ChecklistItemEntity(5, "chk_kcc_en", 1, "Original Khatauni land record + 2 photocopies"),
            ChecklistItemEntity(6, "chk_kcc_en", 2, "Aadhaar + PAN (2 copies), Passbook, and 3 photos"),
            ChecklistItemEntity(7, "chk_kcc_hi", 1, "असली खतौनी (जमीन का कागज़) व 2 फोटोकॉपी"),
            ChecklistItemEntity(8, "chk_kcc_hi", 2, "आधार + पैन की 2 कॉपी, पासबुक और 3 फोटो")
        )
        dao.upsertChecklistItems(checklistItems)

        val faqs = listOf(
            CachedFaqEntity(
                faqId = 1,
                question = "Can a Bank Mitra or BC charge extra fee for withdrawing cash?",
                answer = "No. Under RBI Business Correspondent rules, a Bank Mitra cannot demand extra fees or commission from customers. Always take a printed receipt.",
                langCode = "en",
                sourceId = "RBI_BC_BSBDA_01",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 2,
                question = "क्या बैंक मित्र पैसे निकालने के अलग से पैसे मांग सकता है?",
                answer = "नहीं। RBI के नियम के अनुसार बैंक मित्र (BC) ग्राहक से अलग से शुल्क या कमीशन नहीं ले सकता। हमेशा छपी रसीद लें।",
                langCode = "hi",
                sourceId = "RBI_BC_BSBDA_01",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 3,
                question = "How much KCC agricultural loan is available without collateral?",
                answer = "Under RBI guidelines, Kisan Credit Card (KCC) crop and allied loans up to ₹2,00,000 do not require collateral security. Visit a bank branch with Khatauni and KYC.",
                langCode = "en",
                sourceId = "RBI_KCC_MASTER_01",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 4,
                question = "बिना ज़मीन गिरवी रखे कितना KCC कृषि लोन मिल सकता है?",
                answer = "RBI नियमों के तहत ₹2,00,000 तक का केसीसी (KCC) कृषि ऋण बिना गारंटी मिल सकता है। खतौनी व केवाईसी लेकर बैंक शाखा जाएं।",
                langCode = "hi",
                sourceId = "RBI_KCC_MASTER_01",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 5,
                question = "Is there a minimum balance in a Jan Dhan (BSBDA) account?",
                answer = "No. PMJDY / BSBDA accounts are zero-balance accounts with unlimited deposits and at least 4 free withdrawals per month.",
                langCode = "en",
                sourceId = "RBI_BSBDA_DBT_02",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 6,
                question = "क्या जन धन (BSBDA) खाते में न्यूनतम बैलेंस रखना जरूरी है?",
                answer = "नहीं। जन धन (BSBDA) जीरो-बैलेंस खाता है। इसमें जमा की कोई सीमा नहीं है और महीने में कम से कम 4 निकासी मुफ्त हैं।",
                langCode = "hi",
                sourceId = "RBI_BSBDA_DBT_02",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 7,
                question = "What should I do if money is stolen via fraud call or fake KYC link?",
                answer = "Call 1930 immediately, report on cybercrime.gov.in, and inform your bank within 3 working days to claim RBI Zero Liability protection.",
                langCode = "en",
                sourceId = "RBI_ZERO_LIABILITY_1930",
                verifiedOn = "2026-11-23"
            ),
            CachedFaqEntity(
                faqId = 8,
                question = "अगर ठगी कॉल या नकली KYC लिंक से पैसे कट जाएं तो क्या करें?",
                answer = "तुरंत 1930 पर कॉल करें, cybercrime.gov.in पर शिकायत दर्ज करें और RBI जीरो लायबिलिटी के लिए 3 कार्य दिवस में बैंक को बताएं।",
                langCode = "hi",
                sourceId = "RBI_ZERO_LIABILITY_1930",
                verifiedOn = "2026-11-23"
            )
        ) + buildDogriSeedFaqs()
        dao.upsertFaqs(faqs)

        val scamRules = listOf(
            ScamRuleEntity(1, "otp|pin|cvv|ओटीपी|पिन", 3, "hi", "2026-11-14"),
            ScamRuleEntity(2, "anydesk|teamviewer|quicksupport|.apk", 3, "en", "2026-11-14"),
            ScamRuleEntity(3, "kyc link|bit.ly|blocked|केवाईसी लिंक|बंद हो", 3, "hi", "2026-11-14"),
            ScamRuleEntity(4, "दो बार अंगूठा|no receipt|बिना रसीद|failed scan", 3, "hi", "2026-11-14"),
            ScamRuleEntity(5, "extra fee|commission|अलग से शुल्क", 2, "en", "2026-11-14")
        )
        dao.upsertScamRules(scamRules)

        val masterFacilities = defaultLocalFacilityRecords(LOCAL_DEVICE_USER_ID).map {
            FacilityMasterCacheEntity(
                facilityId = it.id,
                name = it.nameEn,
                bank = it.bankName,
                type = it.facilityType,
                districtCode = it.districtCode,
                lat = it.lat,
                lon = it.lon,
                status = it.cashStatus,
                verified = it.verifiedDate
            )
        }
        dao.upsertFacilityMasters(masterFacilities)

        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        dao.upsertSyncMeta(
            listOf(
                SyncMetaEntity("CHECKLIST", 1, nowStr, "all", "seed_chk_v1", "VERIFIED_SEED"),
                SyncMetaEntity("CACHED_FAQ", 1, nowStr, "all", "seed_faq_v1", "VERIFIED_SEED"),
                SyncMetaEntity("SCAM_RULE", 1, nowStr, "all", "seed_scam_v1", "VERIFIED_SEED"),
                SyncMetaEntity("FACILITY", 1, nowStr, "lucknow_rampur", "seed_fac_v1", "VERIFIED_SEED")
            )
        )
    }

    private suspend fun ensureDogriCatalogSeeded(dao: BankSathiDao) {
        val existingFaqs = dao.getAllFaqs()
        if (existingFaqs.none { it.langCode == "doi" }) {
            dao.upsertLanguages(listOf(LanguageEntity("doi", "डोगरी (Dogri)")))
            dao.upsertChecklists(
                listOf(
                    ChecklistMasterEntity("chk_withdraw_doi", BankingTask.WITHDRAW.name, "doi", 1),
                    ChecklistMasterEntity("chk_transfer_doi", BankingTask.TRANSFER.name, "doi", 1),
                    ChecklistMasterEntity("chk_dbt_doi", BankingTask.PASSBOOK_DBT.name, "doi", 1),
                    ChecklistMasterEntity("chk_kcc_doi", BankingTask.LOAN_KCC.name, "doi", 1)
                )
            )
            dao.upsertFaqs(buildDogriSeedFaqs())
        } else if (existingFaqs.none { it.faqId == 13 }) {
            dao.upsertFaqs(buildExtraRuralSeedFaqs())
        }
    }

    private fun buildExtraRuralSeedFaqs(): List<CachedFaqEntity> = listOf(
        CachedFaqEntity(
            faqId = 13,
            question = "What if AePS thumb impression or biometric scan fails at BC point?",
            answer = "Wash and dry your finger, try another finger, and wait before retrying. Never scan twice without a printed receipt or checking your bank SMS balance. Try IPPB Post Office or ATM/Branch if needed.",
            langCode = "en",
            sourceId = "NPCI_AEPS_BIOMETRIC_05",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 14,
            question = "अगर बैंक मित्र के पास AePS अंगूठा न लगे तो क्या करें?",
            answer = "हाथ धोकर अच्छी तरह पोंछ लें और दूसरी उंगली से कोशिश करें। बिना छपी रसीद या बैंक SMS देखे दोबारा अंगूठा न लगाएं। दिक्कत होने पर डाकघर (IPPB) या बैंक शाखा जाएं।",
            langCode = "hi",
            sourceId = "NPCI_AEPS_BIOMETRIC_05",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 15,
            question = "जे बैंक मित्र कोल AePS अंगूठा नेईं लग्गै तां के करिये?",
            answer = "हत्थ धोइयै पोंछो ते दूई उंगली लाओ। बिना छपी रसीद जां बैंक SMS देखे दुबारा अंगूठा नेईं लाओ। जरूरत होए तां डाकघर (IPPB) जां बैंक शाखा जाओ।",
            langCode = "doi",
            sourceId = "NPCI_AEPS_BIOMETRIC_05",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 16,
            question = "How do I link Aadhaar NPCI seeding for PM-Kisan or DBT subsidy?",
            answer = "Visit your Bank Branch or India Post Payments Bank (IPPB) with your Original Aadhaar Card, Passbook, and registered mobile phone to complete NPCI DBT Aadhaar mapping.",
            langCode = "en",
            sourceId = "NPCI_DBT_SEEDING_06",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 17,
            question = "PM-Kisan या सब्सिडी के लिए बैंक खाते में NPCI आधार सीडिंग कैसे कराएं?",
            answer = "असली आधार कार्ड, पासबुक और बैंक से जुड़ा मोबाइल फोन लेकर अपनी बैंक शाखा या डाकघर (IPPB) जाएं और NPCI आधार मैपिंग करवाएं।",
            langCode = "hi",
            sourceId = "NPCI_DBT_SEEDING_06",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 18,
            question = "PM-Kisan जां सब्सिडी आस्तै खाते च NPCI आधार सीडिंग कियां कराइये?",
            answer = "असली आधार कार्ड, पासबुक ते मोबाइल फोन लेई के अपनी बैंक शाखा जां डाकघर (IPPB) जाओ ते NPCI आधार सीडिंग कराओ।",
            langCode = "doi",
            sourceId = "NPCI_DBT_SEEDING_06",
            verifiedOn = "2026-11-23"
        )
    )

    private fun buildDogriSeedFaqs(): List<CachedFaqEntity> = listOf(
        CachedFaqEntity(
            faqId = 9,
            question = "क्या बैंक मित्र पैसे कड्ढने आस्तै बद्ध फीस मंगी सकदा ऐ?",
            answer = "नेईं। RBI नियमें मुजब बैंक मित्र (BC) ग्राहक थमां बद्ध फीस जां कमीशन नेईं लेई सकदा। हमेशा छपी रसीद लेओ।",
            langCode = "doi",
            sourceId = "RBI_BC_BSBDA_01",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 10,
            question = "बिना ज़मीन गिरवी रक्खे किन्ना KCC खेती लोन मिल्दा ऐ?",
            answer = "RBI नियमें मुजब ₹2,00,000 तक्कर दा KCC खेती लोन बिना गारंटी दे बैंक शाखा चा मिल्दा ऐ। खतौनी ते KYC लेई के जाओ।",
            langCode = "doi",
            sourceId = "RBI_KCC_MASTER_01",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 11,
            question = "क्या जन धन (BSBDA) खाते च कट्टो-कट्ट बैलेंस रक्खना ज़रूरी ऐ?",
            answer = "नेईं। जन धन (BSBDA) जीरो-बैलेंस खाता ऐ। इस च जमा दी कोई हद नेईं ते महीने च 4 बारी मुफ्त निकासी ऐ।",
            langCode = "doi",
            sourceId = "RBI_BSBDA_DBT_02",
            verifiedOn = "2026-11-23"
        ),
        CachedFaqEntity(
            faqId = 12,
            question = "जे ठगी कॉल जां नकली KYC लिंक कन्नै पैसे कट्टी जान तां के करिये?",
            answer = "फौरन 1930 पर कॉल करो, cybercrime.gov.in पर रिपोर्ट करो ते 3 कमकाजी दिनें दे अंदर बैंक गी दस्सो (RBI Zero Liability)।",
            langCode = "doi",
            sourceId = "RBI_ZERO_LIABILITY_1930",
            verifiedOn = "2026-11-23"
        )
    ) + buildExtraRuralSeedFaqs()

    fun observeCachedFaqs(langCode: String): Flow<List<CachedFaqRecord>> =
        bankSathiDao?.observeFaqs(langCode)?.map { list -> list.map { it.toDomain() } }
            ?: flowOf(emptyList())

    suspend fun getAllCachedFaqs(): List<CachedFaqRecord> =
        bankSathiDao?.getAllFaqs()?.map { it.toDomain() } ?: emptyList()

    fun observeScamRules(): Flow<List<ScamRuleRecord>> =
        bankSathiDao?.observeScamRules()?.map { list -> list.map { it.toDomain() } }
            ?: flowOf(emptyList())

    fun observeSyncMeta(): Flow<List<SyncMetaRecord>> =
        bankSathiDao?.observeSyncMeta()?.map { list -> list.map { it.toDomain() } }
            ?: flowOf(emptyList())

    suspend fun updateUserPreference(langCode: String, playbackSpeed: Float) {
        bankSathiDao?.upsertUserPreference(
            UserPreferenceEntity(
                prefId = 1,
                langCode = langCode,
                playbackSpeed = playbackSpeed
            )
        )
    }

    suspend fun syncDistrictPack(
        districtCode: String,
        radiusKm: Int = 10
    ): SyncApplyResult {
        val dao = bankSathiDao
        val currentMetas = dao?.getAllSyncMeta().orEmpty()
        val versionMap = currentMetas.associate { it.tableName to it.version }
        val district = DistrictCatalog.findByCode(districtCode)

        // Attempt remote FastAPI + PostGIS delta sync when configured
        val fastApi = backendClient.api
        if (fastApi != null) {
            val remoteResult = runCatching {
                withContext(Dispatchers.IO) {
                    val deltaResp = fastApi.fetchDeltaSync(
                        DeltaSyncRequest(
                            districtCode = district.code,
                            sinceVersions = versionMap
                        )
                    )
                    var applied = 0
                    for (dto in deltaResp.packages) {
                        val pkg = SignedUpdatePackage(
                            packageId = dto.packageId,
                            districtCode = dto.districtCode,
                            tableName = dto.tableName,
                            version = dto.version,
                            rowsPayloadJson = dto.rowsPayloadJson,
                            signature = dto.signature
                        )
                        val res = verifyAndApplySignedPackage(pkg)
                        if (!res.success) {
                            return@withContext res
                        }
                        applied += res.appliedPackages
                    }

                    // Also query PostGIS nearby facilities and refresh cache with verified coords
                    val nearby = fastApi.getNearbyFacilities(
                        lat = district.centerLat,
                        lon = district.centerLon,
                        radiusKm = radiusKm.coerceAtLeast(5),
                        districtCode = district.code
                    )
                    if (nearby.facilities.isNotEmpty()) {
                        val mapped = nearby.facilities.map { it.toDomain(LOCAL_DEVICE_USER_ID) }
                        facilityDao?.upsertAll(mapped.map { CachedFacilityEntity.fromDomain(it) })
                    }

                    SyncApplyResult(
                        success = true,
                        appliedPackages = applied,
                        rolledBack = false,
                        sourceLabel = "FASTAPI_POSTGIS_DELTA",
                        messageEn = "FastAPI + PostGIS pack verified for ${district.nameEn} ($applied delta updates).",
                        messageHi = "${district.nameHi} के लिए FastAPI + PostGIS पैक सत्यापित और अपडेट हो गया।"
                    )
                }
            }
            if (remoteResult.isSuccess) {
                return remoteResult.getOrThrow()
            }
            val err = remoteResult.exceptionOrNull()
            if (err is CancellationException) throw err
        }

        // Offline / Local District Pack signed verification fallback
        val nextFacVer = (versionMap["FACILITY"] ?: 1).coerceAtLeast(1)
        val districtRows = defaultLocalFacilityRecords(LOCAL_DEVICE_USER_ID)
            .filter { it.districtCode == district.code }
        val jsonArray = JSONArray().apply {
            districtRows.forEach { rec ->
                put(JSONObject().apply {
                    put("id", rec.id)
                    put("nameEn", rec.nameEn)
                    put("nameHi", rec.nameHi)
                    put("bankName", rec.bankName)
                    put("facilityType", rec.facilityType)
                    put("villageOrArea", rec.villageOrArea)
                    put("districtCode", rec.districtCode)
                    put("lat", rec.lat)
                    put("lon", rec.lon)
                    put("isOpen", rec.isOpen)
                    put("cashStatus", rec.cashStatus)
                    put("reportsCount", rec.reportsCount)
                    put("verifiedDate", rec.verifiedDate)
                })
            }
        }
        val unsignedPkg = SignedUpdatePackage(
            packageId = "local_${district.code}_v$nextFacVer",
            districtCode = district.code,
            tableName = "FACILITY",
            version = nextFacVer,
            rowsPayloadJson = jsonArray.toString(),
            signature = ""
        )
        val signedPkg = unsignedPkg.copy(
            signature = if (DeltaSyncVerifier.isVerifyKeyConfigured()) {
                DeltaSyncVerifier.computeHmacSha256Signature(
                    unsignedPkg,
                    com.example.BuildConfig.SYNC_VERIFY_KEY
                )
            } else {
                DeltaSyncVerifier.computeSha256Signature(unsignedPkg)
            }
        )
        return verifyAndApplySignedPackage(signedPkg)
    }

    suspend fun verifyAndApplySignedPackage(pkg: SignedUpdatePackage): SyncApplyResult {
        val dao = bankSathiDao ?: return SyncApplyResult(
            success = false,
            appliedPackages = 0,
            rolledBack = true,
            sourceLabel = "NO_DAO",
            messageEn = "Offline database unavailable.",
            messageHi = "ऑफलाइन डेटाबेस उपलब्ध नहीं है।"
        )

        val currentVersion = dao.getAllSyncMeta()
            .firstOrNull { it.tableName == pkg.tableName }
            ?.version ?: 0

        when (val verdict = DeltaSyncVerifier.verify(pkg, currentVersion)) {
            is PackageVerifyResult.Rejected -> {
                return SyncApplyResult(
                    success = false,
                    appliedPackages = 0,
                    rolledBack = true,
                    sourceLabel = "SIGNATURE_ROLLBACK",
                    messageEn = verdict.reasonEn,
                    messageHi = verdict.reasonHi
                )
            }
            PackageVerifyResult.Valid -> Unit
        }

        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val sigBadge = if (pkg.signature.startsWith("hmac:")) "VERIFIED_HMAC_SHA256" else "VERIFIED_SHA256"
        val meta = SyncMetaEntity(
            tableName = pkg.tableName,
            version = pkg.version,
            syncedAt = nowStr,
            districtCode = pkg.districtCode,
            packageId = pkg.packageId,
            signatureStatus = sigBadge
        )

        return try {
            val array = JSONArray(pkg.rowsPayloadJson)
            when (pkg.tableName) {
                "FACILITY" -> {
                    val masters = mutableListOf<FacilityMasterCacheEntity>()
                    val cached = mutableListOf<CachedFacilityEntity>()
                    val center = DistrictCatalog.findByCode(pkg.districtCode)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val lat = obj.getDouble("lat")
                        val lon = obj.getDouble("lon")
                        val record = FacilityRecord(
                            id = obj.getString("id"),
                            userId = LOCAL_DEVICE_USER_ID,
                            updatedByUid = LOCAL_DEVICE_USER_ID,
                            nameEn = obj.getString("nameEn"),
                            nameHi = obj.optString("nameHi", obj.getString("nameEn")),
                            bankName = obj.optString("bankName", "Gramin Vikas Bank"),
                            facilityType = obj.optString("facilityType", FacilityType.BC_POINT.name),
                            villageOrArea = obj.optString("villageOrArea", ""),
                            districtCode = obj.optString("districtCode", pkg.districtCode),
                            distanceMeters = FacilityGeoUtils.haversineMeters(
                                center.centerLat,
                                center.centerLon,
                                lat,
                                lon
                            ),
                            lat = lat,
                            lon = lon,
                            isOpen = obj.optBoolean("isOpen", true),
                            cashStatus = obj.optString("cashStatus", CashStatus.UNKNOWN.name),
                            reportsCount = obj.optLong("reportsCount", 0L),
                            verifiedDate = obj.optString("verifiedDate", "")
                        )
                        masters.add(
                            FacilityMasterCacheEntity(
                                facilityId = record.id,
                                name = record.nameEn,
                                bank = record.bankName,
                                type = record.facilityType,
                                districtCode = record.districtCode,
                                lat = record.lat,
                                lon = record.lon,
                                status = record.cashStatus,
                                verified = record.verifiedDate
                            )
                        )
                        cached.add(CachedFacilityEntity.fromDomain(record))
                    }
                    if (roomDb != null) {
                        roomDb.applyFacilityPackageAtomic(masters, cached, meta)
                    } else {
                        facilityDao?.upsertAll(cached)
                        dao.applyFacilityDeltaTransactional(masters, meta)
                    }
                }

                "CACHED_FAQ" -> {
                    val faqs = mutableListOf<CachedFaqEntity>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        faqs.add(
                            CachedFaqEntity(
                                faqId = obj.getInt("faqId"),
                                question = obj.getString("question"),
                                answer = obj.getString("answer"),
                                langCode = obj.optString("langCode", "hi"),
                                sourceId = obj.optString("sourceId", "RBI_VERIFIED"),
                                verifiedOn = obj.optString("verifiedOn", "")
                            )
                        )
                    }
                    dao.applyFaqDeltaTransactional(faqs, meta)
                }

                "SCAM_RULE" -> {
                    val rules = mutableListOf<ScamRuleEntity>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        rules.add(
                            ScamRuleEntity(
                                ruleId = obj.getInt("ruleId"),
                                triggerPattern = obj.getString("triggerPattern"),
                                severity = obj.optInt("severity", 2),
                                langCode = obj.optString("langCode", "hi"),
                                updatedAt = obj.optString("updatedAt", nowStr)
                            )
                        )
                    }
                    dao.applyScamRuleDeltaTransactional(rules, meta)
                }

                else -> {
                    dao.upsertSyncMeta(listOf(meta))
                }
            }

            SyncApplyResult(
                success = true,
                appliedPackages = 1,
                rolledBack = false,
                sourceLabel = sigBadge,
                messageEn = "Verified ${pkg.tableName} pack (${pkg.packageId}, v${pkg.version}) applied safely.",
                messageHi = "${pkg.tableName} पैक (${pkg.packageId}, v${pkg.version}) सुरक्षित रूप से अपडेट हो गया।"
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SyncApplyResult(
                success = false,
                appliedPackages = 0,
                rolledBack = true,
                sourceLabel = "TRANSACTION_ROLLBACK",
                messageEn = "Package error during write (${e.message}) — rolled back to previous state.",
                messageHi = "पैकेज लिखने में त्रुटि — पिछला सुरक्षित डेटा बहाल रखा गया।"
            )
        }
    }

    suspend fun recordDeltaSyncRefresh(districtCode: String = "lucknow_rampur"): SyncApplyResult =
        syncDistrictPack(districtCode = districtCode)

    fun observeFacilities(): Flow<List<FacilityRecord>> {
        if (!isCloudSyncEnabled) {
            return facilityDao?.observeAll()?.map { list -> list.map { it.toDomain() } }
                ?: flowOf(defaultLocalFacilityRecords(LOCAL_DEVICE_USER_ID))
        }
        return flow {
            val path = COLLECTION_FACILITIES
            emitAll(
                db.collection(path)
                    .whereEqualTo("isActive", true)
                    .snapshots()
                    .map { snapshot ->
                        val items = snapshot.toObjects(
                            FacilityRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedBy { it.distanceMeters }
                        facilityDao?.upsertAll(items.map { CachedFacilityEntity.fromDomain(it) })
                        items
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                        throw error
                    }
            )
        }
    }

    fun observeCachedFacilities(): Flow<List<FacilityRecord>>? =
        facilityDao?.observeAll()?.map { list -> list.map { it.toDomain() } }

    suspend fun resolveAndAddPincodeFacilities(
        pincode: String,
        isOnline: Boolean,
        userId: String
    ): Result<ResolvedPincodeResult> = runCatching {
        val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }
        val resolved = PincodeFacilityResolver.resolveAndBuildFacilities(
            rawPincode = pincode,
            isOnline = isOnline,
            userId = safeUid
        )
        val cachedEntities = resolved.facilities.map { CachedFacilityEntity.fromDomain(it) }
        val masterEntities = resolved.facilities.map {
            FacilityMasterCacheEntity(
                facilityId = it.id,
                name = it.nameEn,
                bank = it.bankName,
                type = it.facilityType,
                districtCode = it.districtCode,
                lat = it.lat,
                lon = it.lon,
                status = it.cashStatus,
                verified = it.verifiedDate
            )
        }
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val syncMeta = SyncMetaEntity(
            tableName = "FACILITY",
            version = 1,
            syncedAt = nowStr,
            districtCode = PincodeFacilityResolver.districtCodeForPincode(resolved.pincode),
            packageId = if (resolved.resolvedOnline) "pin_${resolved.pincode}_live" else "pin_${resolved.pincode}_offline",
            signatureStatus = if (resolved.resolvedOnline) "PINCODE_LIVE" else "PINCODE_OFFLINE_CACHE"
        )
        if (roomDb != null) {
            roomDb.applyFacilityPackageAtomic(masterEntities, cachedEntities, syncMeta)
        } else {
            facilityDao?.upsertAll(cachedEntities)
            bankSathiDao?.applyFacilityDeltaTransactional(masterEntities, syncMeta)
        }
        resolved
    }

    suspend fun seedInitialFacilitiesIfEmpty(userId: String): Result<Unit> = runCatching {
        if (!isCloudSyncEnabled) {
            val dao = facilityDao ?: return@runCatching
            if (dao.count() == 0) {
                dao.upsertAll(
                    defaultLocalFacilityRecords(userId.ifBlank { LOCAL_DEVICE_USER_ID })
                        .map { CachedFacilityEntity.fromDomain(it) }
                )
            }
            return@runCatching
        }
        require(userId.isNotBlank()) { "Authenticated userId is required" }
        val existing = db.collection(COLLECTION_FACILITIES)
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .await()
        if (!existing.isEmpty) return@runCatching

        val seeds = defaultVillageFacilities(userId)
        for ((docId, payload) in seeds) {
            db.collection(COLLECTION_FACILITIES).document(docId).set(payload).await()
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.CREATE, COLLECTION_FACILITIES)
    }

    suspend fun addFacility(
        userId: String,
        nameEn: String,
        nameHi: String,
        facilityType: FacilityType,
        villageOrArea: String,
        distanceMeters: Long,
        bankName: String = "Gramin Vikas Bank",
        districtCode: String = "lucknow_rampur",
        centerLat: Double? = null,
        centerLon: Double? = null
    ): Result<String> = runCatching {
        val docId = "fac_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val fallbackCenter = DistrictCatalog.findByCode(districtCode)
        val effectiveDistrictCode = if (districtCode.startsWith("pin_")) districtCode else fallbackCenter.code
        val effectiveLat = centerLat ?: fallbackCenter.centerLat
        val effectiveLon = centerLon ?: fallbackCenter.centerLon

        facilityDao?.upsert(
            CachedFacilityEntity(
                id = docId,
                userId = safeUid,
                updatedByUid = safeUid,
                nameEn = nameEn.trim(),
                nameHi = nameHi.trim(),
                bankName = bankName.trim().ifEmpty { "Gramin Vikas Bank" },
                facilityType = facilityType.name,
                villageOrArea = villageOrArea.trim(),
                districtCode = effectiveDistrictCode,
                distanceMeters = distanceMeters.coerceIn(0L, 100_000L),
                lat = effectiveLat,
                lon = effectiveLon,
                isOpen = true,
                cashStatus = CashStatus.CASH_READY.name,
                lastReportStatus = "NONE",
                reportsCount = 0L,
                verifiedDate = today,
                updatedAtMillis = System.currentTimeMillis()
            )
        )

        if (isCloudSyncEnabled && userId.isNotBlank()) {
            val payload = mapOf(
                "userId" to userId,
                "updatedByUid" to userId,
                "nameEn" to nameEn.trim(),
                "nameHi" to nameHi.trim(),
                "facilityType" to facilityType.name,
                "villageOrArea" to villageOrArea.trim(),
                "distanceMeters" to distanceMeters.coerceIn(0L, 100_000L),
                "isOpen" to true,
                "cashStatus" to CashStatus.CASH_READY.name,
                "lastReportStatus" to "NONE",
                "reportsCount" to 0L,
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            runCatching {
                db.collection(COLLECTION_FACILITIES).document(docId).set(payload).await()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                handleFirestoreError(error, OperationType.CREATE, COLLECTION_FACILITIES)
            }
        }
        docId
    }

    suspend fun submitFacilityStatusReport(
        userId: String,
        facility: FacilityRecord,
        reportStatus: ReportStatus,
        receiptReceived: Boolean?
    ): Result<Unit> = runCatching {
        require(facility.id.isNotBlank()) { "Facility ID must not be blank" }
        val (newIsOpen, newCashStatus) = when (reportStatus) {
            ReportStatus.GOT_CASH -> true to CashStatus.CASH_READY.name
            ReportStatus.LONG_LINE -> true to CashStatus.LOW_CASH.name
            ReportStatus.NO_CASH_CLOSED -> false to CashStatus.NO_CASH.name
        }
        val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Always persist updated status locally first so offline or pincode-added centers update immediately
        facilityDao?.upsert(
            CachedFacilityEntity.fromDomain(
                facility.copy(
                    isOpen = newIsOpen,
                    cashStatus = newCashStatus,
                    lastReportStatus = reportStatus.name,
                    reportsCount = facility.reportsCount + 1L,
                    verifiedDate = today,
                    updatedByUid = safeUid
                )
            )
        )

        if (isCloudSyncEnabled && !facility.id.startsWith("pin_")) {
            require(userId.isNotBlank()) { "Authenticated userId is required" }
            val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val reportRef = db.collection(COLLECTION_FACILITIES)
                .document(facility.id)
                .collection(SUBCOLLECTION_REPORTS)
                .document(reportId)

            val reportPayload = buildMap<String, Any> {
                put("userId", userId)
                put("facilityId", facility.id)
                put("status", reportStatus.name)
                if (receiptReceived != null) put("receiptReceived", receiptReceived)
                put("isPublic", true)
                put("createdAt", FieldValue.serverTimestamp())
            }

            val facilityRef = db.collection(COLLECTION_FACILITIES).document(facility.id)
            val facilityUpdate = mapOf(
                "isOpen" to newIsOpen,
                "cashStatus" to newCashStatus,
                "lastReportStatus" to reportStatus.name,
                "reportsCount" to (facility.reportsCount + 1L),
                "verifiedDate" to today,
                "updatedByUid" to userId,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            runCatching {
                facilityRef.update(facilityUpdate).await()
                reportRef.set(reportPayload).await()
            }.onFailure { err ->
                if (err is CancellationException) throw err
                handleFirestoreError(
                    err,
                    OperationType.UPDATE,
                    "$COLLECTION_FACILITIES/${facility.id}"
                )
            }
        }
        Unit
    }

    suspend fun registerBackendUser(
        email: String,
        password: String,
        fullName: String
    ): Result<com.example.data.remote.AuthTokenResponse> = runCatching {
        val fastApi = backendClient.api
            ?: error("Set BANKSATHI_API_BASE_URL in Secrets / .env to connect to FastAPI + Supabase.")
        val response = withContext(Dispatchers.IO) {
            fastApi.registerUser(
                com.example.data.remote.AuthRegisterRequest(
                    email = email.trim().lowercase(),
                    password = password,
                    fullName = fullName.trim()
                )
            )
        }
        com.example.data.remote.BackendSessionStore.accessToken = response.accessToken
        response
    }.recoverCatching { err ->
        if (err is CancellationException) throw err
        throw IllegalStateException(extractBackendErrorMessage(err))
    }

    suspend fun loginBackendUser(
        email: String,
        password: String
    ): Result<com.example.data.remote.AuthTokenResponse> = runCatching {
        val fastApi = backendClient.api
            ?: error("Set BANKSATHI_API_BASE_URL in Secrets / .env to connect to FastAPI + Supabase.")
        val response = withContext(Dispatchers.IO) {
            fastApi.loginUser(
                com.example.data.remote.AuthLoginRequest(
                    email = email.trim().lowercase(),
                    password = password
                )
            )
        }
        com.example.data.remote.BackendSessionStore.accessToken = response.accessToken
        response
    }.recoverCatching { err ->
        if (err is CancellationException) throw err
        throw IllegalStateException(extractBackendErrorMessage(err))
    }

    private fun extractBackendErrorMessage(err: Throwable): String {
        if (err is retrofit2.HttpException) {
            val raw = err.response()?.errorBody()?.string().orEmpty()
            val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
            if (!detail.isNullOrBlank()) return detail
        }
        return err.localizedMessage ?: "Authentication request failed."
    }

    suspend fun fetchBackendRole(): UserRole {
        val fastApi = backendClient.api ?: return UserRole.CITIZEN
        return runCatching {
            withContext(Dispatchers.IO) {
                UserRole.fromApi(fastApi.getAuthMe().role)
            }
        }.getOrElse { err ->
            if (err is CancellationException) throw err
            UserRole.CITIZEN
        }
    }

    suspend fun verifyFacilityOfficial(
        userId: String,
        facility: FacilityRecord,
        cashStatus: CashStatus = CashStatus.CASH_READY,
        notes: String = "Field verifier check passed"
    ): Result<FacilityRecord> = runCatching {
        val hasBackendAuth = backendClient.hasActiveSession()
        require((isCloudSyncEnabled && userId.isNotBlank() && userId != LOCAL_DEVICE_USER_ID) || hasBackendAuth) {
            "Sign in with email/password or Google first to access protected verification routes."
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val fastApi = backendClient.api
        val updatedRecord = if (fastApi != null) {
            val resp = withContext(Dispatchers.IO) {
                fastApi.verifyFacility(
                    VerifyFacilityRequest(
                        facilityId = facility.id,
                        cashStatus = cashStatus.name,
                        isOpen = cashStatus != CashStatus.NO_CASH,
                        notes = notes
                    )
                )
            }
            resp.facility.toDomain(userId).copy(
                verifiedDate = resp.verifiedDate.ifBlank { today },
                isActive = true
            )
        } else {
            val role = fetchBackendRole()
            require(role == UserRole.VERIFIER || role == UserRole.ADMIN) {
                "Access denied (403): VERIFIER or ADMIN role required on backend."
            }
            facility.copy(
                cashStatus = cashStatus.name,
                isOpen = cashStatus != CashStatus.NO_CASH,
                verifiedDate = today,
                reportsCount = facility.reportsCount + 1L,
                updatedByUid = userId
            )
        }

        facilityDao?.upsert(CachedFacilityEntity.fromDomain(updatedRecord))
        if (isCloudSyncEnabled && !facility.id.startsWith("pin_")) {
            runCatching {
                db.collection(COLLECTION_FACILITIES).document(facility.id).update(
                    mapOf(
                        "cashStatus" to updatedRecord.cashStatus,
                        "isOpen" to updatedRecord.isOpen,
                        "verifiedDate" to updatedRecord.verifiedDate,
                        "reportsCount" to updatedRecord.reportsCount,
                        "updatedByUid" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        updatedRecord
    }

    fun observeUserChecklists(userId: String): Flow<List<TripChecklistRecord>> {
        if (!isCloudSyncEnabled) {
            val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }
            return checklistDao?.observeByUser(safeUid)?.map { list -> list.map { it.toDomain() } }
                ?: flowOf(emptyList())
        }
        return flow {
            val path = "$COLLECTION_USERS/$userId/$SUBCOLLECTION_CHECKLISTS"
            emitAll(
                db.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(SUBCOLLECTION_CHECKLISTS)
                    .whereEqualTo("userId", userId)
                    .snapshots()
                    .map { snapshot ->
                        val items = snapshot.toObjects(
                            TripChecklistRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        checklistDao?.upsertAll(items.map { CachedChecklistEntity.fromDomain(it) })
                        items
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                        throw error
                    }
            )
        }
    }

    suspend fun saveChecklist(
        userId: String,
        task: BankingTask,
        amountInr: Int,
        recommendedFacilityType: FacilityType,
        checkedDocIds: List<String>,
        receiptVerified: Boolean
    ): Result<String> = runCatching {
        val docId = "chk_${task.name.lowercase()}"
        val boundedDocs = checkedDocIds.distinct().take(10)
        val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }

        if (isCloudSyncEnabled) {
            require(userId.isNotBlank()) { "Authenticated userId is required" }
            val docRef = db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_CHECKLISTS)
                .document(docId)

            val existing = docRef.get().await()
            if (existing.exists()) {
                val updatePayload = mapOf(
                    "amountInr" to amountInr.toLong().coerceIn(0L, 500_000L),
                    "recommendedFacilityType" to recommendedFacilityType.name,
                    "checkedDocIds" to boundedDocs,
                    "receiptVerified" to receiptVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to userId,
                    "taskType" to task.name,
                    "amountInr" to amountInr.toLong().coerceIn(0L, 500_000L),
                    "recommendedFacilityType" to recommendedFacilityType.name,
                    "checkedDocIds" to boundedDocs,
                    "receiptVerified" to receiptVerified,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
        }

        checklistDao?.upsert(
            CachedChecklistEntity(
                id = docId,
                userId = safeUid,
                taskType = task.name,
                amountInr = amountInr.toLong(),
                recommendedFacilityType = recommendedFacilityType.name,
                checkedDocIdsCsv = boundedDocs.joinToString(","),
                receiptVerified = receiptVerified,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        docId
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.WRITE, "$COLLECTION_USERS/$userId/$SUBCOLLECTION_CHECKLISTS")
    }

    fun observeScamChecks(userId: String): Flow<List<ScamCheckRecord>> {
        if (!isCloudSyncEnabled) {
            val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }
            return scamCheckDao?.observeByUser(safeUid)?.map { list -> list.map { it.toDomain() } }
                ?: flowOf(emptyList())
        }
        return flow {
            val path = "$COLLECTION_USERS/$userId/$SUBCOLLECTION_SCAM_CHECKS"
            emitAll(
                db.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(SUBCOLLECTION_SCAM_CHECKS)
                    .whereEqualTo("userId", userId)
                    .snapshots()
                    .map { snapshot ->
                        val items = snapshot.toObjects(
                            ScamCheckRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                        scamCheckDao?.upsertAll(items.map { CachedScamCheckEntity.fromDomain(it) })
                        items
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                        throw error
                    }
            )
        }
    }

    suspend fun saveScamCheck(
        userId: String,
        inputSnippet: String,
        riskLevel: RiskLevel,
        explanationEn: String,
        explanationHi: String,
        sourceMode: AiSourceMode
    ): Result<String> = runCatching {
        val docId = "scam_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val safeUid = userId.ifBlank { LOCAL_DEVICE_USER_ID }
        val trimmedSnippet = inputSnippet.trim().ifEmpty { "Unknown alert" }.take(600)
        val trimmedEn = explanationEn.trim().ifEmpty { "Check complete" }.take(1200)
        val trimmedHi = explanationHi.trim().ifEmpty { "जांच पूरी हुई" }.take(1200)

        val firestoreSourceMode = if (sourceMode == AiSourceMode.FASTAPI_RAG) {
            AiSourceMode.GEMINI_AI.name
        } else {
            sourceMode.name
        }

        if (isCloudSyncEnabled) {
            require(userId.isNotBlank()) { "Authenticated userId is required" }
            val docRef = db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_SCAM_CHECKS)
                .document(docId)

            val payload = mapOf(
                "userId" to userId,
                "inputSnippet" to trimmedSnippet,
                "riskLevel" to riskLevel.name,
                "explanationEn" to trimmedEn,
                "explanationHi" to trimmedHi,
                "sourceMode" to firestoreSourceMode,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }

        scamCheckDao?.upsert(
            CachedScamCheckEntity(
                id = docId,
                userId = safeUid,
                inputSnippet = trimmedSnippet,
                riskLevel = riskLevel.name,
                explanationEn = trimmedEn,
                explanationHi = trimmedHi,
                sourceMode = sourceMode.name,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        docId
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.CREATE, "$COLLECTION_USERS/$userId/$SUBCOLLECTION_SCAM_CHECKS")
    }

    suspend fun deleteScamCheck(userId: String, checkId: String): Result<Unit> = runCatching {
        require(checkId.isNotBlank()) { "checkId must not be blank" }
        if (isCloudSyncEnabled) {
            require(userId.isNotBlank()) { "Authenticated userId is required" }
            db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_SCAM_CHECKS)
                .document(checkId)
                .delete()
                .await()
        }
        scamCheckDao?.deleteById(checkId)
        Unit
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.DELETE, "$COLLECTION_USERS/$userId/$SUBCOLLECTION_SCAM_CHECKS/$checkId")
    }

    private fun defaultVillageFacilities(userId: String): List<Pair<String, Map<String, Any>>> {
        val now = FieldValue.serverTimestamp()
        fun entry(
            id: String,
            nameEn: String,
            nameHi: String,
            bankName: String,
            type: FacilityType,
            area: String,
            dist: Long,
            lat: Double,
            lon: Double,
            open: Boolean,
            cash: CashStatus,
            lastReport: String,
            count: Long
        ) = id to mapOf(
            "userId" to userId,
            "updatedByUid" to userId,
            "nameEn" to nameEn,
            "nameHi" to nameHi,
            "bankName" to bankName,
            "facilityType" to type.name,
            "villageOrArea" to area,
            "distanceMeters" to dist,
            "lat" to lat,
            "lon" to lon,
            "isOpen" to open,
            "cashStatus" to cash.name,
            "lastReportStatus" to lastReport,
            "reportsCount" to count,
            "verifiedDate" to "2026-11-14",
            "isActive" to true,
            "createdAt" to now,
            "updatedAt" to now
        )

        return listOf(
            entry(
                id = "fac_bc_ramesh_chowk",
                nameEn = "Ramesh Kirana Bank Mitra (BC)",
                nameHi = "रमेश किराना बैंक मित्र (बीसी)",
                bankName = "SBI CSP",
                type = FacilityType.BC_POINT,
                area = "Rampur Main Chowk",
                dist = 350L,
                lat = 26.8467,
                lon = 80.9462,
                open = true,
                cash = CashStatus.CASH_READY,
                lastReport = ReportStatus.GOT_CASH.name,
                count = 12L
            ),
            entry(
                id = "fac_ippb_rampur_post",
                nameEn = "Rampur Post Office (IPPB)",
                nameHi = "रामपुर डाकघर (IPPB)",
                bankName = "India Post Payments Bank",
                type = FacilityType.IPPB,
                area = "Panchayat Bhavan Road",
                dist = 780L,
                lat = 26.8491,
                lon = 80.9488,
                open = true,
                cash = CashStatus.CASH_READY,
                lastReport = ReportStatus.GOT_CASH.name,
                count = 8L
            ),
            entry(
                id = "fac_atm_mandi_gate",
                nameEn = "Kisan Mandi ATM",
                nameHi = "किसान मंडी एटीएम",
                bankName = "Bank of Baroda",
                type = FacilityType.ATM,
                area = "Anaj Mandi Gate",
                dist = 1450L,
                lat = 26.8515,
                lon = 80.9520,
                open = true,
                cash = CashStatus.LOW_CASH,
                lastReport = ReportStatus.LONG_LINE.name,
                count = 5L
            ),
            entry(
                id = "fac_bc_sharma_csc",
                nameEn = "Sharma CSC & AePS Kendra",
                nameHi = "शर्मा सीएससी और आधार केंद्र",
                bankName = "PNB BC Outlet",
                type = FacilityType.BC_POINT,
                area = "Bus Stand Lane",
                dist = 1900L,
                lat = 26.8530,
                lon = 80.9410,
                open = false,
                cash = CashStatus.NO_CASH,
                lastReport = ReportStatus.NO_CASH_CLOSED.name,
                count = 4L
            ),
            entry(
                id = "fac_branch_grameen_block",
                nameEn = "Gramin Vikas Bank Branch",
                nameHi = "ग्रामीण विकास बैंक मुख्य शाखा",
                bankName = "Aryavart Gramin Bank",
                type = FacilityType.BRANCH,
                area = "Block Tehsil Headquarters",
                dist = 5200L,
                lat = 26.8720,
                lon = 80.9640,
                open = true,
                cash = CashStatus.CASH_READY,
                lastReport = ReportStatus.GOT_CASH.name,
                count = 19L
            )
        )
    }

    private fun defaultLocalFacilityRecords(userId: String): List<FacilityRecord> = listOf(
        // District 1: Lucknow Rural / Rampur
        FacilityRecord(
            id = "fac_bc_ramesh_chowk",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Ramesh Kirana Bank Mitra (BC)",
            nameHi = "रमेश किराना बैंक मित्र (बीसी)",
            bankName = "SBI CSP",
            facilityType = FacilityType.BC_POINT.name,
            villageOrArea = "Rampur Main Chowk",
            districtCode = "lucknow_rampur",
            distanceMeters = 350L,
            lat = 26.8492,
            lon = 80.9482,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = ReportStatus.GOT_CASH.name,
            reportsCount = 12L,
            verifiedDate = "2026-11-21"
        ),
        FacilityRecord(
            id = "fac_ippb_rampur_post",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Rampur Post Office (IPPB)",
            nameHi = "रामपुर डाकघर (IPPB)",
            bankName = "India Post Payments Bank",
            facilityType = FacilityType.IPPB.name,
            villageOrArea = "Panchayat Bhavan Road",
            districtCode = "lucknow_rampur",
            distanceMeters = 780L,
            lat = 26.8518,
            lon = 80.9510,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = ReportStatus.GOT_CASH.name,
            reportsCount = 8L,
            verifiedDate = "2026-11-22"
        ),
        FacilityRecord(
            id = "fac_atm_mandi_gate",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Kisan Mandi ATM",
            nameHi = "किसान मंडी एटीएम",
            bankName = "Bank of Baroda",
            facilityType = FacilityType.ATM.name,
            villageOrArea = "Anaj Mandi Gate",
            districtCode = "lucknow_rampur",
            distanceMeters = 1450L,
            lat = 26.8575,
            lon = 80.9540,
            isOpen = true,
            cashStatus = CashStatus.LOW_CASH.name,
            lastReportStatus = ReportStatus.LONG_LINE.name,
            reportsCount = 5L,
            verifiedDate = "2026-11-20"
        ),
        FacilityRecord(
            id = "fac_bc_sharma_csc",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Sharma CSC & AePS Kendra",
            nameHi = "शर्मा सीएससी और आधार केंद्र",
            bankName = "PNB BC Outlet",
            facilityType = FacilityType.BC_POINT.name,
            villageOrArea = "Bus Stand Lane",
            districtCode = "lucknow_rampur",
            distanceMeters = 1900L,
            lat = 26.8610,
            lon = 80.9350,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = "NONE",
            reportsCount = 2L,
            verifiedDate = "2026-11-08" // Intentionally > 7 days old -> evaluates to Unknown
        ),
        FacilityRecord(
            id = "fac_branch_grameen_block",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Gramin Vikas Bank Branch",
            nameHi = "ग्रामीण विकास बैंक मुख्य शाखा",
            bankName = "Aryavart Gramin Bank",
            facilityType = FacilityType.BRANCH.name,
            villageOrArea = "Block Tehsil Headquarters",
            districtCode = "lucknow_rampur",
            distanceMeters = 5200L,
            lat = 26.8850,
            lon = 80.9740,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = ReportStatus.GOT_CASH.name,
            reportsCount = 19L,
            verifiedDate = "2026-11-21"
        ),
        // District 2: Barabanki • Dewa Block
        FacilityRecord(
            id = "fac_bbk_dewa_bc",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Verma Jan Seva & SBI BC Point",
            nameHi = "वर्मा जन सेवा व SBI बैंक मित्र",
            bankName = "SBI CSP",
            facilityType = FacilityType.BC_POINT.name,
            villageOrArea = "Dewa Sharif Bazar",
            districtCode = "barabanki_block",
            distanceMeters = 480L,
            lat = 26.9295,
            lon = 81.1995,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = ReportStatus.GOT_CASH.name,
            reportsCount = 14L,
            verifiedDate = "2026-11-21"
        ),
        FacilityRecord(
            id = "fac_bbk_ippb_post",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Dewa Sub Post Office (IPPB)",
            nameHi = "देवा उप डाकघर (IPPB)",
            bankName = "India Post Payments Bank",
            facilityType = FacilityType.IPPB.name,
            villageOrArea = "Tehsil Road",
            districtCode = "barabanki_block",
            distanceMeters = 1250L,
            lat = 26.9348,
            lon = 81.2030,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = ReportStatus.GOT_CASH.name,
            reportsCount = 7L,
            verifiedDate = "2026-11-22"
        ),
        FacilityRecord(
            id = "fac_bbk_old_atm",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Mati Road Coop ATM",
            nameHi = "माटी रोड सहकारी एटीएम",
            bankName = "UP Cooperative Bank",
            facilityType = FacilityType.ATM.name,
            villageOrArea = "Mati Tiraha",
            districtCode = "barabanki_block",
            distanceMeters = 3600L,
            lat = 26.9520,
            lon = 81.2190,
            isOpen = true,
            cashStatus = CashStatus.LOW_CASH.name,
            lastReportStatus = "NONE",
            reportsCount = 1L,
            verifiedDate = "" // Missing date -> evaluates to Unknown
        ),
        // District 3: Sitapur • Biswan Tehsil
        FacilityRecord(
            id = "fac_stp_biswan_branch",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Biswan Regional Gramin Branch",
            nameHi = "बिसवां क्षेत्रीय ग्रामीण शाखा",
            bankName = "Aryavart Gramin Bank",
            facilityType = FacilityType.BRANCH.name,
            villageOrArea = "Station Road Biswan",
            districtCode = "sitapur_tehsil",
            distanceMeters = 650L,
            lat = 27.4975,
            lon = 81.0038,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = ReportStatus.GOT_CASH.name,
            reportsCount = 16L,
            verifiedDate = "2026-11-22"
        ),
        FacilityRecord(
            id = "fac_stp_csc_jahangirabad",
            userId = userId,
            updatedByUid = userId,
            nameEn = "Tiwari Digital Bank Mitra (BC)",
            nameHi = "तिवारी डिजिटल बैंक मित्र (बीसी)",
            bankName = "Bank of India CSP",
            facilityType = FacilityType.BC_POINT.name,
            villageOrArea = "Jahangirabad Crossing",
            districtCode = "sitapur_tehsil",
            distanceMeters = 2800L,
            lat = 27.5150,
            lon = 81.0120,
            isOpen = true,
            cashStatus = CashStatus.CASH_READY.name,
            lastReportStatus = "NONE",
            reportsCount = 3L,
            verifiedDate = "2026-11-05" // Stale > 7 days -> evaluates to Unknown
        )
    )

    companion object {
        const val LOCAL_DEVICE_USER_ID = "local_device_user"
        const val COLLECTION_FACILITIES = "facilities"
        const val SUBCOLLECTION_REPORTS = "reports"
        const val COLLECTION_USERS = "users"
        const val SUBCOLLECTION_CHECKLISTS = "checklists"
        const val SUBCOLLECTION_SCAM_CHECKS = "scamChecks"
    }
}
