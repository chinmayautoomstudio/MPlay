package com.autoomstudio.mp3studio.data.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseAuthBackend(
    private val client: SupabaseClient,
    private val sessionStore: SessionManager,
    private val deviceId: () -> String?,
    private val deviceName: () -> String,
) : AuthBackend {

    override val status: Flow<BackendStatus> = client.auth.sessionStatus.map { status ->
        when (status) {
            SessionStatus.Initializing -> BackendStatus.Initializing
            is SessionStatus.Authenticated -> status.session.user?.toAccountUser()
                ?.let(BackendStatus::Authenticated)
                ?: BackendStatus.RefreshFailed(storedUser())
            is SessionStatus.NotAuthenticated -> BackendStatus.NotAuthenticated
            is SessionStatus.RefreshFailure -> BackendStatus.RefreshFailed(storedUser())
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) = mapErrors {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun signOut() {
        runCatching {
            withTimeout(3_000) { currentDeviceId()?.let { removeDevice(it) } }
        }
        try {
            client.auth.signOut()
        } catch (_: Exception) {
            // Offline or already revoked: the session must still be gone from the phone.
            client.auth.clearSession()
        }
    }

    override suspend fun signOutLocally() = client.auth.clearSession()

    override suspend fun refresh() = mapErrors { client.auth.refreshCurrentSession() }

    override suspend fun isDisabled(userId: String): Boolean = mapErrors {
        client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()
            ?.disabled ?: false
    }

    override suspend fun registerDevice(replaceDeviceId: String?): DeviceRegistrationResult {
        val id = currentDeviceId() ?: throw AuthCodeException("device_unknown")
        val text = try {
            invokeDevices("register", id, replaceDeviceId)
        } catch (e: RestException) {
            if (e.statusCode == 409 && "device_limit_reached" in deviceErrorText(e)) {
                return DeviceRegistrationResult.LimitReached(listDevices())
            }
            throw e
        }
        val dto = json.decodeFromString<DevicesResponse>(text)
        val devices = dto.devices.map(DeviceDto::toDevice)
        return if (dto.result == "limit_reached" || dto.error == "device_limit_reached") {
            DeviceRegistrationResult.LimitReached(devices.ifEmpty { listDevices() })
        } else {
            DeviceRegistrationResult.Registered(devices)
        }
    }

    override suspend fun verifyDevice(): DeviceVerificationResult {
        val id = currentDeviceId() ?: return DeviceVerificationResult.Revoked
        val text = try {
            invokeDevices("verify", id)
        } catch (e: RestException) {
            val text = deviceErrorText(e)
            return when {
                e.statusCode == 403 || "account_disabled" in text -> DeviceVerificationResult.Disabled
                "device_revoked" in text -> DeviceVerificationResult.Revoked
                else -> throw e
            }
        }
        val dto = json.decodeFromString<DevicesResponse>(text)
        return when (dto.error) {
            "account_disabled" -> DeviceVerificationResult.Disabled
            "device_revoked" -> DeviceVerificationResult.Revoked
            else -> DeviceVerificationResult.Ok(dto.devices.map(DeviceDto::toDevice))
        }
    }

    override suspend fun listDevices(): List<LoggedInDevice> {
        val text = invokeDevices("list", currentDeviceId())
        return json.decodeFromString<DevicesResponse>(text).devices.map(DeviceDto::toDevice)
    }

    override suspend fun removeDevice(deviceId: String): List<LoggedInDevice> {
        val text = invokeDevices("remove", deviceId)
        return json.decodeFromString<DevicesResponse>(text).devices.map(DeviceDto::toDevice)
    }

    override suspend fun currentUser(): AccountUser? =
        client.auth.currentUserOrNull()?.toAccountUser() ?: storedUser()

    private fun currentDeviceId(): String? = deviceId()

    private suspend fun invokeDevices(action: String, deviceId: String?, replaceDeviceId: String? = null): String {
        val body = buildJsonObject {
            put("action", action)
            deviceId?.let { put("deviceId", it) }
            put("deviceName", deviceName())
            replaceDeviceId?.let { put("replaceDeviceId", it) }
        }
        return client.functions.invoke("devices", body).bodyAsText()
    }

    private suspend fun storedUser(): AccountUser? = sessionStore.loadSessionOrNull()?.user?.toAccountUser()

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: AuthRestException) {
        throw AuthCodeException(e.error, e)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun deviceErrorText(e: RestException): String = "${e.error} ${e.description.orEmpty()}"
    }
}

internal fun UserInfo.toAccountUser(): AccountUser = AccountUser(
    id = id,
    email = email,
    name = userMetadata.string("full_name") ?: userMetadata.string("name"),
    avatarUrl = userMetadata.string("avatar_url") ?: userMetadata.string("picture"),
)

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
