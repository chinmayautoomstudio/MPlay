package com.autoomstudio.mp3studio.data.account

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class AccountDeletionTest {

    @Test
    fun refusalsAreReadFromTheConflictBody() {
        assertEquals(DeletionError.LastAdmin, deletionErrorOf(409, """{"error":"last_admin"}"""))
        assertEquals(DeletionError.ActiveSubscription, deletionErrorOf(409, """{"error":"active_subscription"}"""))
        assertEquals(DeletionError.Other, deletionErrorOf(409, """{"error":"something_new"}"""))
    }

    @Test
    fun otherStatusesAreGenericFailures() {
        assertEquals(DeletionError.Other, deletionErrorOf(500, """{"error":"last_admin"}"""))
        assertEquals(DeletionError.Other, deletionErrorOf(401, """{"error":"unauthorized"}"""))
    }

    @Test
    fun networkFailuresMeanOffline() {
        assertEquals(DeletionError.Offline, deletionErrorOf(IOException("no route")))
        assertEquals(DeletionError.Offline, deletionErrorOf(HttpRequestException("timeout", HttpRequestBuilder())))
        assertEquals(DeletionError.Other, deletionErrorOf(IllegalStateException()))
    }
}
