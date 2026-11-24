package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AiSourceMode
import com.example.data.model.BankingTask
import com.example.data.model.FacilityType
import com.example.data.model.ReportStatus
import com.example.data.model.RiskLevel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraminSetuRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveFacilities_authenticatedUser_succeedsAndMirrorsUpdate() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = GraminSetuRepository(firestore)

        val facilityId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFacility(
                userId = uid,
                nameEn = "Test BC Center",
                nameHi = "टेस्ट बीसी केंद्र",
                facilityType = FacilityType.BC_POINT,
                villageOrArea = "Rampur",
                distanceMeters = 300L
            ).getOrThrow()
        }
        assertTrue(facilityId.isNotBlank())

        val facilities = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFacilities().first { list -> list.any { it.id == facilityId } }
        }
        val created = facilities.first { it.id == facilityId }
        assertEquals("Test BC Center", created.nameEn)

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = GraminSetuRepository(firestore)
        val reportResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.submitFacilityStatusReport(
                userId = bobUid,
                facility = created,
                reportStatus = ReportStatus.LONG_LINE,
                receiptReceived = true
            )
        }
        assertTrue(reportResult.isSuccess)
    }

    @Test
    fun saveAndObserveChecklist_ownerOnly_blocksCrossUserAccess() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = GraminSetuRepository(firestore)

        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveChecklist(
                userId = aliceUid,
                task = BankingTask.WITHDRAW,
                amountInr = 3000,
                recommendedFacilityType = FacilityType.BC_POINT,
                checkedDocIds = listOf("aadhaar_original"),
                receiptVerified = false
            ).getOrThrow()
        }
        assertEquals("chk_withdraw", docId)

        val aliceChecklists = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeUserChecklists(aliceUid).first { it.isNotEmpty() }
        }
        assertEquals(1, aliceChecklists.size)

        signInTestUser(BOB_EMAIL)
        val bobRepo = GraminSetuRepository(firestore)
        var capturedError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeUserChecklists(aliceUid).first()
            }
        } catch (t: Throwable) {
            capturedError = t
        }
        val firestoreEx = capturedError?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED for cross-user checklist read", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun saveScamCheck_ownerCanWriteAndRead_unauthenticatedRejected() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = GraminSetuRepository(firestore)

        val scamId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveScamCheck(
                userId = aliceUid,
                inputSnippet = "Share OTP for KYC update",
                riskLevel = RiskLevel.HIGH_RISK,
                explanationEn = "Do not share OTP.",
                explanationHi = "ओटीपी साझा न करें।",
                sourceMode = AiSourceMode.OFFLINE_HEURISTIC
            ).getOrThrow()
        }
        assertTrue(scamId.isNotBlank())

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeScamChecks(aliceUid).first { list -> list.any { it.id == scamId } }
        }
        assertTrue(logs.any { it.id == scamId })

        auth.signOut()
        var unauthError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeFacilities().first()
            }
        } catch (t: Throwable) {
            unauthError = t
        }
        val firestoreEx = unauthError?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED when signed out", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rural@test.com"
        const val BOB_EMAIL = "bob_rural@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
