package com.example.data.repository

import com.example.data.model.CashStatus
import com.example.data.model.FacilityRecord
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object FacilityGeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    const val DEFAULT_MAX_VERIFICATION_AGE_DAYS = 7

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Long {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return (EARTH_RADIUS_METERS * c).toLong().coerceAtLeast(0L)
    }

    fun isVerificationStale(
        verifiedDate: String?,
        referenceTimeMillis: Long = System.currentTimeMillis(),
        maxAgeDays: Int = DEFAULT_MAX_VERIFICATION_AGE_DAYS
    ): Boolean {
        val trimmed = verifiedDate?.trim().orEmpty()
        if (trimmed.isBlank()) return true

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = try {
            parser.parse(trimmed)
        } catch (_: Exception) {
            null
        } ?: return true

        val ageMillis = referenceTimeMillis - parsedDate.time
        val maxAgeMillis = (maxAgeDays.toLong() + 1L) * 24L * 60L * 60L * 1000L
        return ageMillis > maxAgeMillis
    }

    fun effectiveCashStatus(
        facility: FacilityRecord,
        referenceTimeMillis: Long = System.currentTimeMillis(),
        maxAgeDays: Int = DEFAULT_MAX_VERIFICATION_AGE_DAYS
    ): CashStatus {
        if (isVerificationStale(facility.verifiedDate, referenceTimeMillis, maxAgeDays)) {
            return CashStatus.UNKNOWN
        }
        return facility.parsedCashStatus()
    }

    fun formatDistance(distanceMeters: Long): String =
        if (distanceMeters >= 1000L) {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters}m"
        }
}
