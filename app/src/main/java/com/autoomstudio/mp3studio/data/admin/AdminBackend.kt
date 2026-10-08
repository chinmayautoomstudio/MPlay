package com.autoomstudio.mp3studio.data.admin

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant

/** Admin data and actions (PRD AD1-AD10). Nothing is cached. Every call throws [AdminException] on failure. */
interface AdminBackend {
    suspend fun overview(): AdminOverview
    suspend fun users(query: String, filter: UserFilter, offset: Int): AdminUserPage
    suspend fun user(id: String): AdminUserDetail
    suspend fun usage(): AdminUsage
    suspend fun setRole(userId: String, admin: Boolean)
    suspend fun setDisabled(userId: String, disabled: Boolean)
    suspend fun addAdmin(email: String): AddAdminResult
    suspend fun admins(): AdminList
    suspend fun revokeInvite(email: String)
    suspend fun grantPro(userId: String, until: Long)
    suspend fun revokePro(userId: String)
    suspend fun audit(before: Long?): List<AuditEntry>

    companion object {
        const val PAGE_SIZE = 50
    }
}

/** Calls the `admin` Edge Function with `{"action": ...}`. */
class SupabaseAdminBackend(private val client: SupabaseClient) : AdminBackend {

    override suspend fun overview(): AdminOverview = call("overview")

    override suspend fun users(query: String, filter: UserFilter, offset: Int): AdminUserPage = call("users") {
        put("query", query.take(100))
        put("filter", filter.wire)
        put("limit", AdminBackend.PAGE_SIZE)
        put("offset", offset)
    }

    override suspend fun user(id: String): AdminUserDetail = call("user") { put("userId", id) }

    override suspend fun usage(): AdminUsage = call("usage")

    override suspend fun setRole(userId: String, admin: Boolean) {
        call<JsonObject>("setRole") {
            put("userId", userId)
            put("role", if (admin) "admin" else "user")
        }
    }

    override suspend fun setDisabled(userId: String, disabled: Boolean) {
        call<JsonObject>("setDisabled") {
            put("userId", userId)
            put("disabled", disabled)
        }
    }

    override suspend fun addAdmin(email: String): AddAdminResult =
        when (call<ResultDto>("addAdmin") { put("email", email.trim()) }.result) {
            "promoted" -> AddAdminResult.Promoted
            "already_admin" -> AddAdminResult.AlreadyAdmin
            else -> AddAdminResult.Invited
        }

    override suspend fun admins(): AdminList = call("admins")

    override suspend fun revokeInvite(email: String) {
        call<JsonObject>("revokeInvite") { put("email", email) }
    }

    override suspend fun grantPro(userId: String, until: Long) {
        call<JsonObject>("grantPro") {
            put("userId", userId)
            put("until", Instant.ofEpochMilli(until).toString())
        }
    }

    override suspend fun revokePro(userId: String) {
        call<JsonObject>("revokePro") { put("userId", userId) }
    }

    override suspend fun audit(before: Long?): List<AuditEntry> = call<AuditDto>("audit") {
        put("limit", AdminBackend.PAGE_SIZE)
        if (before != null) put("before", before)
    }.entries

    private suspend inline fun <reified T> call(action: String, fields: JsonObjectBuilder.() -> Unit = {}): T {
        val body = buildJsonObject {
            put("action", action)
            fields()
        }
        val text = try {
            client.functions.invoke("admin", body).bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AdminException(errorOf(e), e)
        }
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw AdminException(AdminError.Other, e)
        }
    }

    @Serializable
    private data class ResultDto(val result: String)

    @Serializable
    private data class AuditDto(val entries: List<AuditEntry>)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Maps a failed call to what the screens show; the Edge Function answers refusals with `{"error": code}`. */
internal fun errorOf(e: Throwable): AdminError = when (e) {
    is RestException -> {
        val text = "${e.error} ${e.description.orEmpty()}"
        when {
            e.statusCode == 403 -> AdminError.Forbidden
            e.statusCode == 404 -> AdminError.NotFound
            e.statusCode != 409 -> AdminError.Other
            "last_admin" in text -> AdminError.LastAdmin
            "invalid_email" in text -> AdminError.InvalidEmail
            "invalid_date" in text -> AdminError.InvalidDate
            "\"self\"" in text -> AdminError.Self
            else -> AdminError.Other
        }
    }
    is HttpRequestException, is IOException -> AdminError.Offline
    else -> AdminError.Other
}
