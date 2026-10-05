package com.autoomstudio.mplay.separation

/** What the device offers; read once per app start. */
data class DeviceSpecs(
    val supportedAbis: List<String>,
    val totalRamBytes: Long,
    val isLowRamDevice: Boolean,
    val freeStorageBytes: Long,
)

enum class UnsupportedReason {
    /** Standard MPlay: separation is only in the MPlay AI build. */
    NotIncluded,
    Architecture,
    Memory,
    Storage,
    ModelMissing,
}

sealed interface SeparationAvailability {
    data object Available : SeparationAvailability

    data class Unavailable(val reasons: List<UnsupportedReason>) : SeparationAvailability
}

/** Eligibility thresholds from the PRD; to be confirmed by the feasibility spike. */
object DeviceEligibility {
    /** Phones sold with 6 GB report roughly 5.3 to 5.7 GiB of total memory. */
    const val MIN_TOTAL_RAM_BYTES = 5L * 1024 * 1024 * 1024
    const val MIN_FREE_STORAGE_BYTES = 1L * 1024 * 1024 * 1024
    const val REQUIRED_ABI = "arm64-v8a"

    fun check(specs: DeviceSpecs): List<UnsupportedReason> = buildList {
        if (REQUIRED_ABI !in specs.supportedAbis) add(UnsupportedReason.Architecture)
        if (specs.isLowRamDevice || specs.totalRamBytes < MIN_TOTAL_RAM_BYTES) add(UnsupportedReason.Memory)
        if (specs.freeStorageBytes < MIN_FREE_STORAGE_BYTES) add(UnsupportedReason.Storage)
    }
}

/** Space a separation needs: both stems at 192 kbps plus a margin. Stems are renamed into place, never copied. */
object StorageEstimate {
    private const val BITRATE = 192_000L
    private const val STEMS = 2
    private const val CONTAINER_OVERHEAD = 1.05
    const val MARGIN_BYTES = 50L * 1024 * 1024

    fun stemBytes(durationMs: Long): Long =
        (STEMS * BITRATE / 8 * durationMs.coerceAtLeast(0) / 1000 * CONTAINER_OVERHEAD).toLong()

    fun requiredFreeBytes(durationMs: Long): Long = stemBytes(durationMs) + MARGIN_BYTES

    fun requiredFreeBytes(durationsMs: List<Long>): Long = durationsMs.sumOf(::stemBytes) + MARGIN_BYTES
}
