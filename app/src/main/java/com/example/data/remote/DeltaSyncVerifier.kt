package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.SignedUpdatePackage
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed interface PackageVerifyResult {
    data object Valid : PackageVerifyResult
    data class Rejected(val reasonEn: String, val reasonHi: String) : PackageVerifyResult
}

object DeltaSyncVerifier {
    private val supportedTables = setOf("CHECKLIST", "CACHED_FAQ", "SCAM_RULE", "FACILITY")

    fun isVerifyKeyConfigured(key: String = BuildConfig.SYNC_VERIFY_KEY): Boolean =
        key.isNotBlank() &&
            !key.endsWith("_DEFAULT_VALUE") &&
            !key.startsWith("YOUR_")

    fun canonicalPayload(pkg: SignedUpdatePackage): String =
        "${pkg.packageId}|${pkg.districtCode}|${pkg.tableName}|${pkg.version}|${pkg.rowsPayloadJson}"

    fun computeSha256Signature(pkg: SignedUpdatePackage): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalPayload(pkg).toByteArray(StandardCharsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    fun computeHmacSha256Signature(pkg: SignedUpdatePackage, secretKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(canonicalPayload(pkg).toByteArray(StandardCharsets.UTF_8))
        return "hmac:" + raw.joinToString("") { "%02x".format(it) }
    }

    fun verify(
        pkg: SignedUpdatePackage,
        currentTableVersion: Int,
        hmacKey: String = BuildConfig.SYNC_VERIFY_KEY
    ): PackageVerifyResult {
        if (pkg.packageId.isBlank()) {
            return PackageVerifyResult.Rejected(
                "Rejected UPDATE_PACKAGE: missing packageId (kept previous verified cache).",
                "अमान्य पैकेज ID — पुराना सत्यापित डेटा सुरक्षित रखा गया।"
            )
        }
        if (pkg.tableName !in supportedTables) {
            return PackageVerifyResult.Rejected(
                "Rejected UPDATE_PACKAGE: unsupported table '${pkg.tableName}'.",
                "अमान्य तालिका '${pkg.tableName}' — पुराना डेटा सुरक्षित रखा गया।"
            )
        }
        if (pkg.version < currentTableVersion) {
            return PackageVerifyResult.Rejected(
                "Rejected stale/downgraded UPDATE_PACKAGE v${pkg.version} < v$currentTableVersion — rolled back.",
                "पुराना पैकेज संस्करण v${pkg.version} अस्वीकार — पिछला सुरक्षित डेटा बहाल रखा गया।"
            )
        }
        val rowsValid = try {
            JSONArray(pkg.rowsPayloadJson)
            true
        } catch (_: Exception) {
            false
        }
        if (!rowsValid) {
            return PackageVerifyResult.Rejected(
                "Rejected malformed UPDATE_PACKAGE JSON — rolled back to last verified snapshot.",
                "पैकेज डेटा खराब था — पिछला सत्यापित डेटा सुरक्षित रखा गया।"
            )
        }

        val sig = pkg.signature.trim()
        if (sig.isBlank()) {
            return PackageVerifyResult.Rejected(
                "Missing cryptographic signature on UPDATE_PACKAGE — rolled back.",
                "पैकेज पर हस्ताक्षर नहीं मिला — पिछला डेटा सुरक्षित रखा गया।"
            )
        }

        val matches = if (isVerifyKeyConfigured(hmacKey) && sig.startsWith("hmac:")) {
            constantTimeEquals(sig.lowercase(), computeHmacSha256Signature(pkg, hmacKey).lowercase())
        } else if (sig.startsWith("sha256:") && !isVerifyKeyConfigured(hmacKey)) {
            constantTimeEquals(sig.lowercase(), computeSha256Signature(pkg).lowercase())
        } else {
            false
        }

        return if (matches) {
            PackageVerifyResult.Valid
        } else {
            PackageVerifyResult.Rejected(
                "Signature verification failed for ${pkg.packageId} — rolled back cleanly.",
                "हस्ताक्षर जांच असफल (${pkg.packageId}) — पिछला सत्यापित डेटा सुरक्षित रखा गया।"
            )
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val bytesA = a.toByteArray(StandardCharsets.UTF_8)
        val bytesB = b.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(bytesA, bytesB)
    }
}
