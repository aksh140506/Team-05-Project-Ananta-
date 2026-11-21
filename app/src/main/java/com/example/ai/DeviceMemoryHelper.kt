package com.example.ai

import android.app.ActivityManager
import android.content.Context
import com.example.data.model.DeviceMemoryStatus
import com.example.data.model.LowRamOverrideMode

object DeviceMemoryHelper {
    const val LOW_RAM_THRESHOLD_MB = 3584L

    fun inspect(context: Context): DeviceMemoryStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val totalMb = (info.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)
        val isLowRam = (am?.isLowRamDevice == true) || totalMb <= LOW_RAM_THRESHOLD_MB
        return DeviceMemoryStatus(
            totalRamMb = totalMb,
            isLowRamDevice = isLowRam,
            thresholdMb = LOW_RAM_THRESHOLD_MB
        )
    }

    fun shouldUseFaqOnlyMode(
        memoryStatus: DeviceMemoryStatus,
        overrideMode: LowRamOverrideMode
    ): Boolean = when (overrideMode) {
        LowRamOverrideMode.AUTO -> memoryStatus.isLowRamDevice
        LowRamOverrideMode.FORCE_FAQ -> true
        LowRamOverrideMode.ALLOW_GEMMA -> false
    }
}
