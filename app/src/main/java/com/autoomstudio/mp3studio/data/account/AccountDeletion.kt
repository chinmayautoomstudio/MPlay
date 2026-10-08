package com.autoomstudio.mp3studio.data.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Why the server didn't delete the account. */
enum class DeletionError {
    /** The account is the last enabled Admin; make someone else Admin first. */
    LastAdmin,

    /** A paid subscription still renews; cancel it first. */
    ActiveSubscription,
    Offline,
    Other,
}

class AccountDeletionException(val error: DeletionError, cause: Throwable? = null) :
    Exception("Account deletion failed: $error", cause)

/** Deletes the signed-in account and its server-side data (PRD AU9, PR4). */
fun interface AccountDeletionBackend {
    /** Throws [AccountDeletionException] when the account wasn't deleted. */
    suspend fun deleteAccount()
}

class SupabaseAccountDeletionBackend(private val client: SupabaseClient) : AccountDeletionBackend {
    override suspend fun deleteAccount() {
        try {
            client.functions.invoke("delete-account", buildJsonObject { put("confirm", CONFIRMATION) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountDeletionException(deletionErrorOf(e), e)
        }
    }

    private companion object {
        const val CONFIRMATION = "DELETE"
    }
}

/** The `delete-account` Edge Function answers refusals with 409 `{"error": code}`. */
internal fun deletionErrorOf(e: Throwable): DeletionError = when (e) {
    is RestException -> deletionErrorOf(e.statusCode, "${e.error} ${e.description.orEmpty()}")
    is HttpRequestException, is IOException -> DeletionError.Offline
    else -> DeletionError.Other
}

internal fun deletionErrorOf(status: Int, body: String): DeletionError = when {
    status != 409 -> DeletionError.Other
    "last_admin" in body -> DeletionError.LastAdmin
    "active_subscription" in body -> DeletionError.ActiveSubscription
    else -> DeletionError.Other
}
