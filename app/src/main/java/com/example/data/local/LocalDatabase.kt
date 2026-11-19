package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.data.model.AppLanguage
import com.example.data.model.CachedFaqRecord
import com.example.data.model.FacilityRecord
import com.example.data.model.LowRamOverrideMode
import com.example.data.model.ScamCheckRecord
import com.example.data.model.ScamRuleRecord
import com.example.data.model.SyncMetaRecord
import com.example.data.model.TripChecklistRecord
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

// =============================================================================
// BankSathi Canonical 8-Table Offline Schema (Team 05 ER Specification)
// =============================================================================

@Entity(tableName = "language")
data class LanguageEntity(
    @PrimaryKey @ColumnInfo(name = "lang_code") val langCode: String,
    val name: String
)

@Entity(
    tableName = "checklist",
    foreignKeys = [
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["lang_code"],
            childColumns = ["lang_code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lang_code")]
)
data class ChecklistMasterEntity(
    @PrimaryKey @ColumnInfo(name = "checklist_id") val checklistId: String,
    @ColumnInfo(name = "service_type") val serviceType: String,
    @ColumnInfo(name = "lang_code") val langCode: String,
    val version: Int
)

@Entity(
    tableName = "checklist_item",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistMasterEntity::class,
            parentColumns = ["checklist_id"],
            childColumns = ["checklist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("checklist_id")]
)
data class ChecklistItemEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: Int,
    @ColumnInfo(name = "checklist_id") val checklistId: String,
    @ColumnInfo(name = "step_no") val stepNo: Int,
    @ColumnInfo(name = "item_text") val itemText: String
)

@Entity(
    tableName = "cached_faq",
    foreignKeys = [
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["lang_code"],
            childColumns = ["lang_code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lang_code")]
)
data class CachedFaqEntity(
    @PrimaryKey @ColumnInfo(name = "faq_id") val faqId: Int,
    val question: String,
    val answer: String,
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "verified_on") val verifiedOn: String
) {
    fun toDomain(): CachedFaqRecord = CachedFaqRecord(
        faqId = faqId,
        question = question,
        answer = answer,
        langCode = langCode,
        sourceId = sourceId,
        verifiedOn = verifiedOn
    )
}

@Entity(
    tableName = "user_preference",
    foreignKeys = [
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["lang_code"],
            childColumns = ["lang_code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lang_code")]
)
data class UserPreferenceEntity(
    @PrimaryKey @ColumnInfo(name = "pref_id") val prefId: Int = 1,
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Float = 0.92f
)

@Entity(
    tableName = "scam_rule",
    foreignKeys = [
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["lang_code"],
            childColumns = ["lang_code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lang_code")]
)
data class ScamRuleEntity(
    @PrimaryKey @ColumnInfo(name = "rule_id") val ruleId: Int,
    @ColumnInfo(name = "trigger_pattern") val triggerPattern: String,
    val severity: Int,
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
) {
    fun toDomain(): ScamRuleRecord = ScamRuleRecord(
        ruleId = ruleId,
        triggerPattern = triggerPattern,
        severity = severity,
        langCode = langCode,
        updatedAt = updatedAt
    )
}

// No FK on FACILITY by design: queried directly by lat/lon
@Entity(tableName = "facility")
data class FacilityMasterCacheEntity(
    @PrimaryKey @ColumnInfo(name = "facility_id") val facilityId: String,
    val name: String,
    val bank: String,
    val type: String,
    @ColumnInfo(name = "district_code") val districtCode: String = "lucknow_rampur",
    val lat: Double,
    val lon: Double,
    val status: String,
    val verified: String
)

// No FK on SYNC_META by design: tracks one version per table
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey @ColumnInfo(name = "table_name") val tableName: String,
    val version: Int,
    @ColumnInfo(name = "synced_at") val syncedAt: String,
    @ColumnInfo(name = "district_code") val districtCode: String = "lucknow_rampur",
    @ColumnInfo(name = "package_id") val packageId: String = "seed_v1",
    @ColumnInfo(name = "signature_status") val signatureStatus: String = "VERIFIED_SEED"
) {
    fun toDomain(): SyncMetaRecord = SyncMetaRecord(
        tableName = tableName,
        version = version,
        syncedAt = syncedAt,
        districtCode = districtCode,
        packageId = packageId,
        signatureStatus = signatureStatus
    )
}

// =============================================================================
// App Runtime Cache Entities (User Progress, Logs & Cloud Sync Mirror)
// =============================================================================

@Entity(tableName = "cached_facilities")
data class CachedFacilityEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val updatedByUid: String,
    val nameEn: String,
    val nameHi: String,
    val bankName: String,
    val facilityType: String,
    val villageOrArea: String,
    val districtCode: String = "lucknow_rampur",
    val distanceMeters: Long,
    val lat: Double,
    val lon: Double,
    val isOpen: Boolean,
    val cashStatus: String,
    val lastReportStatus: String,
    val reportsCount: Long,
    val verifiedDate: String,
    val updatedAtMillis: Long
) {
    fun toDomain(): FacilityRecord = FacilityRecord(
        id = id,
        userId = userId,
        updatedByUid = updatedByUid,
        nameEn = nameEn,
        nameHi = nameHi,
        bankName = bankName,
        facilityType = facilityType,
        villageOrArea = villageOrArea,
        districtCode = districtCode,
        distanceMeters = distanceMeters,
        lat = lat,
        lon = lon,
        isOpen = isOpen,
        cashStatus = cashStatus,
        lastReportStatus = lastReportStatus,
        reportsCount = reportsCount,
        verifiedDate = verifiedDate,
        isActive = true,
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomain(record: FacilityRecord): CachedFacilityEntity = CachedFacilityEntity(
            id = record.id,
            userId = record.userId,
            updatedByUid = record.updatedByUid,
            nameEn = record.nameEn,
            nameHi = record.nameHi,
            bankName = record.bankName,
            facilityType = record.facilityType,
            villageOrArea = record.villageOrArea,
            districtCode = record.districtCode,
            distanceMeters = record.distanceMeters,
            lat = record.lat,
            lon = record.lon,
            isOpen = record.isOpen,
            cashStatus = record.cashStatus,
            lastReportStatus = record.lastReportStatus,
            reportsCount = record.reportsCount,
            verifiedDate = record.verifiedDate,
            updatedAtMillis = record.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "cached_checklists")
data class CachedChecklistEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val taskType: String,
    val amountInr: Long,
    val recommendedFacilityType: String,
    val checkedDocIdsCsv: String,
    val receiptVerified: Boolean,
    val updatedAtMillis: Long
) {
    fun toDomain(): TripChecklistRecord = TripChecklistRecord(
        id = id,
        userId = userId,
        taskType = taskType,
        amountInr = amountInr,
        recommendedFacilityType = recommendedFacilityType,
        checkedDocIds = checkedDocIdsCsv.split(",").filter { it.isNotBlank() },
        receiptVerified = receiptVerified,
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomain(record: TripChecklistRecord): CachedChecklistEntity = CachedChecklistEntity(
            id = record.id,
            userId = record.userId,
            taskType = record.taskType,
            amountInr = record.amountInr,
            recommendedFacilityType = record.recommendedFacilityType,
            checkedDocIdsCsv = record.checkedDocIds.joinToString(","),
            receiptVerified = record.receiptVerified,
            updatedAtMillis = record.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "cached_scam_checks")
data class CachedScamCheckEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val inputSnippet: String,
    val riskLevel: String,
    val explanationEn: String,
    val explanationHi: String,
    val sourceMode: String,
    val createdAtMillis: Long
) {
    fun toDomain(): ScamCheckRecord = ScamCheckRecord(
        id = id,
        userId = userId,
        inputSnippet = inputSnippet,
        riskLevel = riskLevel,
        explanationEn = explanationEn,
        explanationHi = explanationHi,
        sourceMode = sourceMode,
        createdAt = Timestamp(Date(createdAtMillis))
    )

    companion object {
        fun fromDomain(record: ScamCheckRecord): CachedScamCheckEntity = CachedScamCheckEntity(
            id = record.id,
            userId = record.userId,
            inputSnippet = record.inputSnippet,
            riskLevel = record.riskLevel,
            explanationEn = record.explanationEn,
            explanationHi = record.explanationHi,
            sourceMode = record.sourceMode,
            createdAtMillis = record.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}

// =============================================================================
// DAOs
// =============================================================================

@Dao
interface BankSathiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLanguages(items: List<LanguageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklists(items: List<ChecklistMasterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItems(items: List<ChecklistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFaqs(items: List<CachedFaqEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserPreference(pref: UserPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScamRules(items: List<ScamRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacilityMasters(items: List<FacilityMasterCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMeta(items: List<SyncMetaEntity>)

    @Query("SELECT COUNT(*) FROM language")
    suspend fun languageCount(): Int

    @Query("SELECT * FROM cached_faq WHERE lang_code = :langCode ORDER BY faq_id ASC")
    fun observeFaqs(langCode: String): Flow<List<CachedFaqEntity>>

    @Query("SELECT * FROM cached_faq ORDER BY faq_id ASC")
    suspend fun getAllFaqs(): List<CachedFaqEntity>

    @Query("SELECT * FROM scam_rule ORDER BY severity DESC, rule_id ASC")
    fun observeScamRules(): Flow<List<ScamRuleEntity>>

    @Query("SELECT * FROM scam_rule ORDER BY severity DESC")
    suspend fun getAllScamRules(): List<ScamRuleEntity>

    @Query("SELECT * FROM sync_meta ORDER BY table_name ASC")
    fun observeSyncMeta(): Flow<List<SyncMetaEntity>>

    @Query("SELECT * FROM sync_meta ORDER BY table_name ASC")
    suspend fun getAllSyncMeta(): List<SyncMetaEntity>

    @Query("SELECT * FROM checklist_item WHERE checklist_id = :checklistId ORDER BY step_no ASC")
    fun observeChecklistSteps(checklistId: String): Flow<List<ChecklistItemEntity>>

    @Transaction
    suspend fun applyFaqDeltaTransactional(faqs: List<CachedFaqEntity>, meta: SyncMetaEntity) {
        if (faqs.isNotEmpty()) upsertFaqs(faqs)
        upsertSyncMeta(listOf(meta))
    }

    @Transaction
    suspend fun applyScamRuleDeltaTransactional(rules: List<ScamRuleEntity>, meta: SyncMetaEntity) {
        if (rules.isNotEmpty()) upsertScamRules(rules)
        upsertSyncMeta(listOf(meta))
    }

    @Transaction
    suspend fun applyFacilityDeltaTransactional(
        masters: List<FacilityMasterCacheEntity>,
        meta: SyncMetaEntity
    ) {
        if (masters.isNotEmpty()) upsertFacilityMasters(masters)
        upsertSyncMeta(listOf(meta))
    }
}

@Dao
interface FacilityDao {
    @Query("SELECT * FROM cached_facilities ORDER BY distanceMeters ASC")
    fun observeAll(): Flow<List<CachedFacilityEntity>>

    @Query("SELECT COUNT(*) FROM cached_facilities")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedFacilityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedFacilityEntity)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM cached_checklists WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<CachedChecklistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedChecklistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedChecklistEntity)
}

@Dao
interface ScamCheckDao {
    @Query("SELECT * FROM cached_scam_checks WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<CachedScamCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedScamCheckEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedScamCheckEntity)

    @Query("DELETE FROM cached_scam_checks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [
        LanguageEntity::class,
        ChecklistMasterEntity::class,
        ChecklistItemEntity::class,
        CachedFaqEntity::class,
        UserPreferenceEntity::class,
        ScamRuleEntity::class,
        FacilityMasterCacheEntity::class,
        SyncMetaEntity::class,
        CachedFacilityEntity::class,
        CachedChecklistEntity::class,
        CachedScamCheckEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GraminSetuDatabase : RoomDatabase() {
    abstract fun bankSathiDao(): BankSathiDao
    abstract fun facilityDao(): FacilityDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun scamCheckDao(): ScamCheckDao

    @Transaction
    open suspend fun applyFacilityPackageAtomic(
        masters: List<FacilityMasterCacheEntity>,
        cachedFacilities: List<CachedFacilityEntity>,
        meta: SyncMetaEntity
    ) {
        if (masters.isNotEmpty()) bankSathiDao().upsertFacilityMasters(masters)
        if (cachedFacilities.isNotEmpty()) facilityDao().upsertAll(cachedFacilities)
        bankSathiDao().upsertSyncMeta(listOf(meta))
    }

    companion object {
        @Volatile
        private var INSTANCE: GraminSetuDatabase? = null

        fun getInstance(context: Context): GraminSetuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GraminSetuDatabase::class.java,
                    "banksathi_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "banksathi_prefs")

data class BackendAuthSession(
    val accessToken: String,
    val uid: String,
    val email: String,
    val fullName: String,
    val role: String
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val GEMMA_MODEL_PATH = stringPreferencesKey("gemma_model_path")
        val HUSKY_OFFSET_X = floatPreferencesKey("husky_offset_x")
        val HUSKY_OFFSET_Y = floatPreferencesKey("husky_offset_y")
        val SELECTED_DISTRICT_CODE = stringPreferencesKey("selected_district_code")
        val LOW_RAM_FAQ_OVERRIDE = stringPreferencesKey("low_ram_faq_override")
        val ACTIVE_PINCODE = stringPreferencesKey("active_pincode")
        val ACTIVE_PINCODE_AREA = stringPreferencesKey("active_pincode_area")
        val ACTIVE_PINCODE_LAT = doublePreferencesKey("active_pincode_lat")
        val ACTIVE_PINCODE_LON = doublePreferencesKey("active_pincode_lon")
        val BACKEND_AUTH_TOKEN = stringPreferencesKey("backend_auth_token")
        val BACKEND_AUTH_UID = stringPreferencesKey("backend_auth_uid")
        val BACKEND_AUTH_EMAIL = stringPreferencesKey("backend_auth_email")
        val BACKEND_AUTH_NAME = stringPreferencesKey("backend_auth_name")
        val BACKEND_AUTH_ROLE = stringPreferencesKey("backend_auth_role")
    }

    val backendAuthSessionFlow: Flow<BackendAuthSession?> = context.dataStore.data.map { prefs ->
        val token = prefs[Keys.BACKEND_AUTH_TOKEN]
        val uid = prefs[Keys.BACKEND_AUTH_UID]
        val email = prefs[Keys.BACKEND_AUTH_EMAIL]
        if (token.isNullOrBlank() || uid.isNullOrBlank() || email.isNullOrBlank()) {
            null
        } else {
            BackendAuthSession(
                accessToken = token,
                uid = uid,
                email = email,
                fullName = prefs[Keys.BACKEND_AUTH_NAME] ?: "",
                role = prefs[Keys.BACKEND_AUTH_ROLE] ?: "CITIZEN"
            )
        }
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.LANGUAGE])
    }

    val voiceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.VOICE_ENABLED] ?: true
    }

    val playbackSpeedFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.PLAYBACK_SPEED] ?: 0.92f
    }

    val gemmaModelPathFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.GEMMA_MODEL_PATH]
    }

    val huskyOffsetFlow: Flow<Pair<Float, Float>> = context.dataStore.data.map { prefs ->
        (prefs[Keys.HUSKY_OFFSET_X] ?: 0f) to (prefs[Keys.HUSKY_OFFSET_Y] ?: 0f)
    }

    val selectedDistrictCodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_DISTRICT_CODE] ?: "lucknow_rampur"
    }

    val lowRamOverrideFlow: Flow<LowRamOverrideMode> = context.dataStore.data.map { prefs ->
        LowRamOverrideMode.fromStorage(prefs[Keys.LOW_RAM_FAQ_OVERRIDE])
    }

    val activePincodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PINCODE] ?: ""
    }

    val activePincodeAreaFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PINCODE_AREA] ?: ""
    }

    val activePincodeCoordsFlow: Flow<Pair<Double, Double>> = context.dataStore.data.map { prefs ->
        (prefs[Keys.ACTIVE_PINCODE_LAT] ?: 26.8467) to (prefs[Keys.ACTIVE_PINCODE_LON] ?: 80.9462)
    }

    suspend fun setBackendAuthSession(session: BackendAuthSession?) {
        context.dataStore.edit { prefs ->
            if (session == null) {
                prefs.remove(Keys.BACKEND_AUTH_TOKEN)
                prefs.remove(Keys.BACKEND_AUTH_UID)
                prefs.remove(Keys.BACKEND_AUTH_EMAIL)
                prefs.remove(Keys.BACKEND_AUTH_NAME)
                prefs.remove(Keys.BACKEND_AUTH_ROLE)
            } else {
                prefs[Keys.BACKEND_AUTH_TOKEN] = session.accessToken
                prefs[Keys.BACKEND_AUTH_UID] = session.uid
                prefs[Keys.BACKEND_AUTH_EMAIL] = session.email
                prefs[Keys.BACKEND_AUTH_NAME] = session.fullName
                prefs[Keys.BACKEND_AUTH_ROLE] = session.role
            }
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.75f, 1.25f) }
    }

    suspend fun setGemmaModelPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(Keys.GEMMA_MODEL_PATH)
            else prefs[Keys.GEMMA_MODEL_PATH] = path
        }
    }

    suspend fun setHuskyOffset(x: Float, y: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HUSKY_OFFSET_X] = x
            prefs[Keys.HUSKY_OFFSET_Y] = y
        }
    }

    suspend fun setSelectedDistrictCode(districtCode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_DISTRICT_CODE] = districtCode
        }
    }

    suspend fun setLowRamOverrideMode(mode: LowRamOverrideMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOW_RAM_FAQ_OVERRIDE] = mode.storageValue
        }
    }

    suspend fun setActivePincode(pincode: String, areaLabel: String, lat: Double, lon: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PINCODE] = pincode
            prefs[Keys.ACTIVE_PINCODE_AREA] = areaLabel
            prefs[Keys.ACTIVE_PINCODE_LAT] = lat
            prefs[Keys.ACTIVE_PINCODE_LON] = lon
        }
    }
}
