package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.FacilityRecord
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.auth
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class AuthMeResponse(
    val uid: String,
    val email: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    val role: String = "CITIZEN",
    @Json(name = "auth_provider") val authProvider: String = "firebase_jwt",
    val permissions: List<String> = emptyList()
)

data class AuthRegisterRequest(
    val email: String,
    val password: String,
    @Json(name = "full_name") val fullName: String
)

data class AuthLoginRequest(
    val email: String,
    val password: String
)

data class AuthTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String = "bearer",
    @Json(name = "expires_in") val expiresIn: Int = 86400,
    val user: AuthMeResponse
)

object BackendSessionStore {
    @Volatile
    var accessToken: String? = null

    fun clear() {
        accessToken = null
    }
}

data class VerifyFacilityRequest(
    @Json(name = "facility_id") val facilityId: String,
    @Json(name = "cash_status") val cashStatus: String = "CASH_READY",
    @Json(name = "is_open") val isOpen: Boolean = true,
    val notes: String = ""
)

data class VerifyFacilityResponse(
    val facility: RemoteFacilityDto,
    @Json(name = "verified_by") val verifiedBy: String,
    @Json(name = "verified_date") val verifiedDate: String,
    val message: String
)

data class RagHistoryTurnDto(
    val role: String,
    val text: String
)

data class RagAskRequest(
    val query: String,
    val lang: String,
    @Json(name = "district_code") val districtCode: String,
    val history: List<RagHistoryTurnDto> = emptyList()
)

data class RagAskResponse(
    @Json(name = "answer_en") val answerEn: String,
    @Json(name = "answer_hi") val answerHi: String,
    @Json(name = "source_id") val sourceId: String? = null,
    @Json(name = "verified_on") val verifiedOn: String? = null,
    val abstained: Boolean = false,
    @Json(name = "recommended_task") val recommendedTask: String? = null,
    @Json(name = "suggested_amount_inr") val suggestedAmountInr: Int = 0
)

data class RemoteFacilityDto(
    val id: String,
    @Json(name = "name_en") val nameEn: String,
    @Json(name = "name_hi") val nameHi: String? = null,
    @Json(name = "bank_name") val bankName: String = "Gramin Vikas Bank",
    @Json(name = "facility_type") val facilityType: String = "BC_POINT",
    @Json(name = "village_or_area") val villageOrArea: String = "",
    @Json(name = "district_code") val districtCode: String = "lucknow_rampur",
    @Json(name = "distance_meters") val distanceMeters: Long = 0L,
    val lat: Double,
    val lon: Double,
    @Json(name = "is_open") val isOpen: Boolean = true,
    @Json(name = "cash_status") val cashStatus: String = "CASH_READY",
    @Json(name = "reports_count") val reportsCount: Long = 0L,
    @Json(name = "verified_date") val verifiedDate: String = ""
) {
    fun toDomain(userId: String): FacilityRecord = FacilityRecord(
        id = id,
        userId = userId,
        updatedByUid = userId,
        nameEn = nameEn,
        nameHi = nameHi?.takeIf { it.isNotBlank() } ?: nameEn,
        bankName = bankName,
        facilityType = facilityType,
        villageOrArea = villageOrArea,
        districtCode = districtCode,
        distanceMeters = distanceMeters,
        lat = lat,
        lon = lon,
        isOpen = isOpen,
        cashStatus = cashStatus,
        reportsCount = reportsCount,
        verifiedDate = verifiedDate,
        isActive = true
    )
}

data class NearbyFacilitiesResponse(
    @Json(name = "district_code") val districtCode: String = "lucknow_rampur",
    val facilities: List<RemoteFacilityDto> = emptyList()
)

data class DeltaSyncRequest(
    @Json(name = "district_code") val districtCode: String,
    @Json(name = "since_versions") val sinceVersions: Map<String, Int>
)

data class SignedUpdatePackageDto(
    @Json(name = "package_id") val packageId: String,
    @Json(name = "district_code") val districtCode: String,
    @Json(name = "table_name") val tableName: String,
    val version: Int,
    @Json(name = "rows_payload_json") val rowsPayloadJson: String,
    val signature: String
)

data class DeltaSyncResponse(
    val packages: List<SignedUpdatePackageDto> = emptyList()
)

data class RemoteScamCheckRequest(
    val snippet: String,
    val lang: String
)

data class RemoteScamCheckResponse(
    @Json(name = "risk_level") val riskLevel: String,
    @Json(name = "explanation_en") val explanationEn: String,
    @Json(name = "explanation_hi") val explanationHi: String,
    @Json(name = "action_tip_en") val actionTipEn: String,
    @Json(name = "action_tip_hi") val actionTipHi: String
)

interface BankSathiApiService {
    @POST("v1/auth/register")
    suspend fun registerUser(@Body request: AuthRegisterRequest): AuthTokenResponse

    @POST("v1/auth/login")
    suspend fun loginUser(@Body request: AuthLoginRequest): AuthTokenResponse

    @GET("v1/auth/me")
    suspend fun getAuthMe(): AuthMeResponse

    @POST("v1/rag/ask")
    suspend fun askRag(@Body request: RagAskRequest): RagAskResponse

    @GET("v1/facilities/nearby")
    suspend fun getNearbyFacilities(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius_km") radiusKm: Int,
        @Query("district_code") districtCode: String
    ): NearbyFacilitiesResponse

    @POST("v1/facilities/verify")
    suspend fun verifyFacility(@Body request: VerifyFacilityRequest): VerifyFacilityResponse

    @POST("v1/sync/delta")
    suspend fun fetchDeltaSync(@Body request: DeltaSyncRequest): DeltaSyncResponse

    @POST("v1/scam/check")
    suspend fun checkScam(@Body request: RemoteScamCheckRequest): RemoteScamCheckResponse
}

class BankSathiBackendClient(
    private val rawBaseUrl: String = BuildConfig.BANKSATHI_API_BASE_URL
) {
    val isConfigured: Boolean
        get() = rawBaseUrl.isNotBlank() &&
            !rawBaseUrl.endsWith("_DEFAULT_VALUE") &&
            (rawBaseUrl.startsWith("https://") || rawBaseUrl.startsWith("http://"))

    fun hasActiveSession(): Boolean {
        return Firebase.auth.currentUser != null || !BackendSessionStore.accessToken.isNullOrBlank()
    }

    val api: BankSathiApiService? by lazy {
        if (!isConfigured) {
            null
        } else {
            val normalizedUrl = if (rawBaseUrl.endsWith("/")) rawBaseUrl else "$rawBaseUrl/"
            val securityHeadersInterceptor = Interceptor { chain ->
                val appCheckToken = fetchAppCheckTokenBestEffort()
                val bearerToken = fetchFirebaseIdTokenBestEffort()
                    ?: BackendSessionStore.accessToken?.takeIf { it.isNotBlank() }
                val reqBuilder = chain.request().newBuilder()
                if (!appCheckToken.isNullOrBlank()) {
                    reqBuilder.header("X-Firebase-AppCheck", appCheckToken)
                }
                if (!bearerToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $bearerToken")
                }
                chain.proceed(reqBuilder.build())
            }
            val logger = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            }
            val okHttp = OkHttpClient.Builder()
                .connectTimeout(4500, TimeUnit.MILLISECONDS)
                .readTimeout(8000, TimeUnit.MILLISECONDS)
                .writeTimeout(8000, TimeUnit.MILLISECONDS)
                .addInterceptor(securityHeadersInterceptor)
                .addInterceptor(logger)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(BankSathiApiService::class.java)
        }
    }

    private fun fetchAppCheckTokenBestEffort(): String? {
        return try {
            val task = FirebaseAppCheck.getInstance().getAppCheckToken(false)
            Tasks.await(task, 1500, TimeUnit.MILLISECONDS).token
        } catch (e: Exception) {
            Log.w(TAG, "App Check token unavailable for FastAPI call", e)
            null
        }
    }

    private fun fetchFirebaseIdTokenBestEffort(): String? {
        return try {
            val user = Firebase.auth.currentUser ?: return null
            Tasks.await(user.getIdToken(false), 1500, TimeUnit.MILLISECONDS).token
        } catch (e: Exception) {
            Log.w(TAG, "Firebase ID token unavailable for FastAPI call", e)
            null
        }
    }

    private companion object {
        const val TAG = "BankSathiBackend"
    }
}
