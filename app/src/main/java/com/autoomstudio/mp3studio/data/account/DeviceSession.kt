package com.autoomstudio.mp3studio.data.account

import com.autoomstudio.mp3studio.data.plan.epochMillis
import kotlinx.serialization.Serializable

const val MAX_SIGNED_IN_DEVICES = 2

data class LoggedInDevice(
    val deviceId: String,
    val deviceName: String,
    val signedInAt: Long,
    val lastSeenAt: Long,
    val isCurrent: Boolean,
)

sealed interface DeviceRegistrationResult {
    data class Registered(val devices: List<LoggedInDevice>) : DeviceRegistrationResult
    data class LimitReached(val devices: List<LoggedInDevice>) : DeviceRegistrationResult
}

sealed interface DeviceVerificationResult {
    data class Ok(val devices: List<LoggedInDevice>) : DeviceVerificationResult
    data object Disabled : DeviceVerificationResult
    data object Revoked : DeviceVerificationResult
}

@Serializable
data class DeviceDto(
    val deviceId: String,
    val deviceName: String,
    val signedInAt: String,
    val lastSeenAt: String,
    val current: Boolean = false,
) {
    fun toDevice() = LoggedInDevice(
        deviceId = deviceId,
        deviceName = deviceName,
        signedInAt = epochMillis(signedInAt),
        lastSeenAt = epochMillis(lastSeenAt),
        isCurrent = current,
    )
}

@Serializable
internal data class DevicesResponse(
    val result: String? = null,
    val error: String? = null,
    val devices: List<DeviceDto> = emptyList(),
    val maxDevices: Int = MAX_SIGNED_IN_DEVICES,
)
