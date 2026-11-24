package com.example.data

import com.example.ai.DeviceMemoryHelper
import com.example.data.model.AppLanguage
import com.example.data.model.CashStatus
import com.example.data.model.DeviceMemoryStatus
import com.example.data.model.DistrictCatalog
import com.example.data.model.FacilityRecord
import com.example.data.model.FacilityType
import com.example.data.model.LowRamOverrideMode
import com.example.data.model.SignedUpdatePackage
import com.example.data.model.UserRole
import com.example.data.remote.BankSathiBackendClient
import com.example.data.remote.DeltaSyncVerifier
import com.example.data.repository.FacilityGeoUtils
import com.example.data.repository.PincodeFacilityResolver
import com.example.voice.BhashiniSpeechClient
import com.example.voice.GeminiSpeechClient
import com.example.voice.VoiceGuideManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BankSathiAlignmentTest {

    @Test
    fun `haversine distance calculates realistic rural block distances`() {
        val lucknow = DistrictCatalog.findByCode("lucknow_rampur")
        val barabanki = DistrictCatalog.findByCode("barabanki_block")
        val samePoint = FacilityGeoUtils.haversineMeters(
            lucknow.centerLat,
            lucknow.centerLon,
            lucknow.centerLat,
            lucknow.centerLon
        )
        assertEquals(0L, samePoint)

        val distBetweenBlocks = FacilityGeoUtils.haversineMeters(
            lucknow.centerLat,
            lucknow.centerLon,
            barabanki.centerLat,
            barabanki.centerLon
        )
        assertTrue("Expected > 20 km between district packs, got $distBetweenBlocks", distBetweenBlocks > 20_000L)
        assertTrue("Expected < 60 km between neighboring districts, got $distBetweenBlocks", distBetweenBlocks < 60_000L)
        assertEquals("2.4 km", FacilityGeoUtils.formatDistance(2400L))
        assertEquals("450m", FacilityGeoUtils.formatDistance(450L))
    }

    @Test
    fun `facility verification staleness enforces 7 day window and returns UNKNOWN`() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val refMillis = fmt.parse("2026-11-22")!!.time

        assertTrue(FacilityGeoUtils.isVerificationStale("", refMillis))
        assertTrue(FacilityGeoUtils.isVerificationStale("invalid-date", refMillis))
        assertTrue(FacilityGeoUtils.isVerificationStale("2026-11-10", refMillis))
        assertFalse(FacilityGeoUtils.isVerificationStale("2026-11-20", refMillis))

        val staleFacility = FacilityRecord(
            id = "f_stale",
            nameEn = "Old BC Center",
            cashStatus = CashStatus.CASH_READY.name,
            verifiedDate = "2026-11-08"
        )
        val freshFacility = FacilityRecord(
            id = "f_fresh",
            nameEn = "Fresh BC Center",
            cashStatus = CashStatus.CASH_READY.name,
            verifiedDate = "2026-11-21"
        )
        assertEquals(CashStatus.UNKNOWN, FacilityGeoUtils.effectiveCashStatus(staleFacility, refMillis))
        assertEquals(CashStatus.CASH_READY, FacilityGeoUtils.effectiveCashStatus(freshFacility, refMillis))
    }

    @Test
    fun `delta sync signature helpers detect payload tampering and hmac key mismatch`() {
        val basePkg = SignedUpdatePackage(
            packageId = "pkg_lucknow_v2",
            districtCode = "lucknow_rampur",
            tableName = "FACILITY",
            version = 2,
            rowsPayloadJson = """[{"id":"fac_1","nameEn":"Rampur BC","distanceMeters":450}]""",
            signature = ""
        )
        val shaSig = DeltaSyncVerifier.computeSha256Signature(basePkg)
        assertTrue(shaSig.startsWith("sha256:"))

        val tampered = basePkg.copy(rowsPayloadJson = """[{"id":"fac_1","nameEn":"Evil","distanceMeters":10}]""")
        assertNotEquals(shaSig, DeltaSyncVerifier.computeSha256Signature(tampered))

        val hmacSigA = DeltaSyncVerifier.computeHmacSha256Signature(basePkg, "key-a")
        val hmacSigB = DeltaSyncVerifier.computeHmacSha256Signature(basePkg, "key-b")
        assertTrue(hmacSigA.startsWith("hmac:"))
        assertNotEquals(hmacSigA, hmacSigB)
        assertFalse(DeltaSyncVerifier.isVerifyKeyConfigured("SYNC_VERIFY_KEY_DEFAULT_VALUE"))
        assertTrue(DeltaSyncVerifier.isVerifyKeyConfigured("actual-hmac-secret"))
    }

    @Test
    fun `device memory helper enforces FAQ-Only safeguard on 3GB RAM unless overridden`() {
        val phone3Gb = DeviceMemoryStatus(totalRamMb = 3072L, isLowRamDevice = true)
        val phone6Gb = DeviceMemoryStatus(totalRamMb = 6144L, isLowRamDevice = false)

        assertTrue(DeviceMemoryHelper.shouldUseFaqOnlyMode(phone3Gb, LowRamOverrideMode.AUTO))
        assertTrue(DeviceMemoryHelper.shouldUseFaqOnlyMode(phone3Gb, LowRamOverrideMode.FORCE_FAQ))
        assertFalse(DeviceMemoryHelper.shouldUseFaqOnlyMode(phone3Gb, LowRamOverrideMode.ALLOW_GEMMA))

        assertFalse(DeviceMemoryHelper.shouldUseFaqOnlyMode(phone6Gb, LowRamOverrideMode.AUTO))
        assertTrue(DeviceMemoryHelper.shouldUseFaqOnlyMode(phone6Gb, LowRamOverrideMode.FORCE_FAQ))
    }

    @Test
    fun `bhashini speech helper builds valid 16kHz WAV header and supports hi, doi, and en`() {
        val pcm = ByteArray(320) { 0x11 }
        val wav = BhashiniSpeechClient.wrapPcmWithWavHeader(pcm)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("data", String(wav, 36, 4))

        assertEquals("hi", BhashiniSpeechClient.normalizeLangCode(AppLanguage.HINDI))
        assertEquals("doi", BhashiniSpeechClient.normalizeLangCode(AppLanguage.DOGRI))
        assertEquals("en", BhashiniSpeechClient.normalizeLangCode(AppLanguage.ENGLISH))
        assertFalse(BhashiniSpeechClient.isCredentialSet("BHASHINI_USER_ID_DEFAULT_VALUE"))
        assertFalse(BhashiniSpeechClient.isCredentialSet(""))
        assertTrue(BhashiniSpeechClient.isCredentialSet("my-real-user-id"))
    }

    @Test
    fun `pincode resolver validates 6-digit Indian pincodes and builds nearest Bank, BC and Post Office centers`() {
        assertTrue(PincodeFacilityResolver.isValidIndianPincode("226001"))
        assertTrue(PincodeFacilityResolver.isValidIndianPincode("180001"))
        assertFalse(PincodeFacilityResolver.isValidIndianPincode("026001"))
        assertFalse(PincodeFacilityResolver.isValidIndianPincode("22600"))
        assertFalse(PincodeFacilityResolver.isValidIndianPincode("2260012"))
        assertFalse(PincodeFacilityResolver.isValidIndianPincode("abc123"))

        val resolvedJammu = runBlocking {
            PincodeFacilityResolver.resolveAndBuildFacilities(
                rawPincode = "180001",
                isOnline = false,
                userId = "test_user"
            )
        }
        assertEquals("180001", resolvedJammu.pincode)
        val expectedDistrictCode = PincodeFacilityResolver.districtCodeForPincode("180001")
        assertTrue(resolvedJammu.facilities.all { it.districtCode == expectedDistrictCode })
        assertTrue(resolvedJammu.facilities.any { it.parsedType() == FacilityType.BRANCH })
        assertTrue(resolvedJammu.facilities.any { it.parsedType() == FacilityType.BC_POINT })
        assertTrue(resolvedJammu.facilities.any { it.parsedType() == FacilityType.IPPB })
        assertTrue(resolvedJammu.facilities.any { it.parsedType() == FacilityType.ATM })
        val distances = resolvedJammu.facilities.map { it.distanceMeters }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `pincode resolver parses India Post API payload cleanly`() {
        val sampleJson = """
            [{
              "Status": "Success",
              "PostOffice": [
                {
                  "Name": "Jammu Cantt",
                  "BranchType": "Sub Post Office",
                  "Block": "Jammu",
                  "District": "Jammu",
                  "State": "Jammu and Kashmir"
                },
                {
                  "Name": "Gandhi Nagar",
                  "BranchType": "Sub Post Office",
                  "Block": "Jammu",
                  "District": "Jammu",
                  "State": "Jammu and Kashmir"
                }
              ]
            }]
        """.trimIndent()
        val parsed = PincodeFacilityResolver.parseIndiaPostJson(sampleJson)
        assertEquals(2, parsed.size)
        assertEquals("Jammu Cantt", parsed[0].name)
        assertEquals("Jammu", parsed[0].district)

        assertTrue(PincodeFacilityResolver.parseIndiaPostJson("""[{"Status":"Error","PostOffice":null}]""").isEmpty())
    }

    @Test
    fun `zero-key gemini tts helpers and indian female voice scoring work accurately`() {
        assertEquals(24000, GeminiSpeechClient.parseSampleRateFromMimeType("audio/pcm"))
        assertEquals(16000, GeminiSpeechClient.parseSampleRateFromMimeType("audio/L16;codec=pcm;rate=16000"))

        val rawPcm = ByteArray(16) { 0x2A }
        val wrapped = BhashiniSpeechClient.wrapPcmWithWavHeader(rawPcm, sampleRate = 24000)
        val stripped = GeminiSpeechClient.stripWavHeaderIfPresent(wrapped)
        assertEquals(rawPcm.size, stripped.size)

        val doiPrompt = GeminiSpeechClient.buildSpeechPrompt("₹3000 कड्ढो", AppLanguage.DOGRI)
        assertTrue(doiPrompt.contains("Dogri"))
        assertTrue(doiPrompt.contains("₹3000 कड्ढो"))

        val femaleHindiScore = VoiceGuideManager.scoreVoiceDescriptor(
            voiceName = "hi-in-x-hia-network",
            langCode = "hi",
            countryCode = "IN",
            quality = 400,
            isNetworkRequired = true,
            targetLanguage = AppLanguage.HINDI,
            isOnline = true
        )
        val maleHindiScore = VoiceGuideManager.scoreVoiceDescriptor(
            voiceName = "hi-in-x-hid-local",
            langCode = "hi",
            countryCode = "IN",
            quality = 400,
            isNetworkRequired = false,
            targetLanguage = AppLanguage.HINDI,
            isOnline = true
        )
        assertTrue(
            "Expected female Indian voice ($femaleHindiScore) to score strictly higher than male voice ($maleHindiScore)",
            femaleHindiScore > maleHindiScore
        )

        val femaleEnInScore = VoiceGuideManager.scoreVoiceDescriptor(
            voiceName = "en-in-x-ena-local",
            langCode = "en",
            countryCode = "IN",
            quality = 400,
            isNetworkRequired = false,
            targetLanguage = AppLanguage.ENGLISH,
            isOnline = false
        )
        val maleEnUsScore = VoiceGuideManager.scoreVoiceDescriptor(
            voiceName = "en-us-x-end-local",
            langCode = "en",
            countryCode = "US",
            quality = 400,
            isNetworkRequired = false,
            targetLanguage = AppLanguage.ENGLISH,
            isOnline = false
        )
        assertTrue(femaleEnInScore > maleEnUsScore)
    }

    @Test
    fun `user role mapping and backend base url validation behave safely`() {
        assertEquals(UserRole.CITIZEN, UserRole.fromApi(null))
        assertEquals(UserRole.CITIZEN, UserRole.fromApi("unknown"))
        assertEquals(UserRole.VERIFIER, UserRole.fromApi("verifier"))
        assertEquals(UserRole.ADMIN, UserRole.fromApi("ADMIN"))

        assertFalse(BankSathiBackendClient("").isConfigured)
        assertFalse(BankSathiBackendClient("BANKSATHI_API_BASE_URL_DEFAULT_VALUE").isConfigured)
        assertFalse(BankSathiBackendClient("ftp://10.0.2.2:8000/").isConfigured)
        assertTrue(BankSathiBackendClient("http://10.0.2.2:8000/").isConfigured)
        assertTrue(BankSathiBackendClient("https://api.banksathi.in").isConfigured)
    }
}
