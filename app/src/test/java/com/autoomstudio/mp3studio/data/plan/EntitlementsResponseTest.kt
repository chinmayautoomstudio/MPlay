package com.autoomstudio.mp3studio.data.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsResponseTest {

    private fun response(role: String?) = EntitlementsResponse(
        plan = "free",
        role = role,
        serverTime = "2027-01-15T08:00:01.5+00:00",
    )

    @Test
    fun theAdminRoleShowsTheAdminScreens() {
        assertTrue(response("admin").toEntitlements("u", 0).isAdmin)
    }

    @Test
    fun otherRolesAndOlderAnswersDoNot() {
        assertFalse(response("user").toEntitlements("u", 0).isAdmin)
        assertFalse(response(null).toEntitlements("u", 0).isAdmin)
    }
}
