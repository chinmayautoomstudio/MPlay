package com.autoomstudio.mp3studio.ui.admin

import com.autoomstudio.mp3studio.data.admin.AddAdminResult
import com.autoomstudio.mp3studio.data.admin.AdminBackend
import com.autoomstudio.mp3studio.data.admin.AdminError
import com.autoomstudio.mp3studio.data.admin.AdminException
import com.autoomstudio.mp3studio.data.admin.AdminList
import com.autoomstudio.mp3studio.data.admin.AdminOverview
import com.autoomstudio.mp3studio.data.admin.AdminProfile
import com.autoomstudio.mp3studio.data.admin.AdminUsage
import com.autoomstudio.mp3studio.data.admin.AdminUserDetail
import com.autoomstudio.mp3studio.data.admin.AdminUserPage
import com.autoomstudio.mp3studio.data.admin.AdminUserRow
import com.autoomstudio.mp3studio.data.admin.AuditEntry
import com.autoomstudio.mp3studio.data.admin.UserFilter
import com.autoomstudio.mp3studio.data.plan.UsageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeBackend : AdminBackend {
        var failWith: AdminError? = null
        val queries = mutableListOf<Pair<String, UserFilter>>()
        val roleChanges = mutableListOf<Pair<String, Boolean>>()

        private fun check() {
            failWith?.let { throw AdminException(it) }
        }

        override suspend fun overview(): AdminOverview {
            check()
            return AdminOverview(3, 0, 1, 2, 0, 1, 4, 0, 1, "2026-10-05")
        }

        override suspend fun users(query: String, filter: UserFilter, offset: Int): AdminUserPage {
            check()
            queries += query to filter
            return AdminUserPage(1, listOf(AdminUserRow("u1", "a@b.c", "A", "user", "free", false, "2026-10-08T00:00:00+00:00")))
        }

        override suspend fun user(id: String): AdminUserDetail {
            check()
            return AdminUserDetail(
                profile = AdminProfile(id, "a@b.c", "A", null, "user", false, "2026-10-08T00:00:00+00:00"),
                usage = UsageDto(10, 0, 0, 10, "2026-10-11T18:30:00+00:00", false),
            )
        }

        override suspend fun usage() = AdminUsage(emptyList(), emptyList()).also { check() }
        override suspend fun setRole(userId: String, admin: Boolean) {
            check()
            roleChanges += userId to admin
        }
        override suspend fun setDisabled(userId: String, disabled: Boolean) = check()
        override suspend fun addAdmin(email: String) = AddAdminResult.Invited.also { check() }
        override suspend fun admins() = AdminList(emptyList(), emptyList()).also { check() }
        override suspend fun revokeInvite(email: String) = check()
        override suspend fun grantPro(userId: String, until: Long) = check()
        override suspend fun revokePro(userId: String) = check()
        override suspend fun audit(before: Long?): List<AuditEntry> = emptyList<AuditEntry>().also { check() }
    }

    private val backend = FakeBackend()
    private val refreshed = mutableListOf<String>()

    private fun viewModel() = AdminViewModel(
        backend = backend,
        currentUserId = { "me" },
        refreshOwnPlan = { refreshed += it },
        searchDelayMillis = 300,
    ).also { it.enter() }

    @Test
    fun enteringLoadsTheOverviewAndTheFirstUsers() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(3, vm.overview.value.data?.users)
        assertEquals(listOf("u1"), vm.users.value.users.map { it.id })
    }

    @Test
    fun pagesStackAndBackReturnsToHome() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(AdminPage.User("u1"))
        assertEquals(AdminPage.User("u1"), vm.page.value)
        assertEquals("u1", vm.detail.value.data?.profile?.id)
        vm.open(AdminPage.Audit)
        assertTrue(vm.back())
        assertEquals(AdminPage.User("u1"), vm.page.value)
        assertTrue(vm.back())
        assertEquals(AdminPage.Home, vm.page.value)
        assertFalse(vm.back())
    }

    @Test
    fun typingWaitsBeforeSearching() = runTest(dispatcher) {
        val vm = viewModel()
        backend.queries.clear()
        vm.setQuery("a")
        vm.setQuery("an")
        advanceTimeBy(301)
        assertEquals(listOf("an" to UserFilter.All), backend.queries)
    }

    @Test
    fun aRefusalIsReportedWithItsReason() = runTest(dispatcher) {
        val vm = viewModel()
        backend.failWith = AdminError.LastAdmin
        vm.setRole("me", "me@x.y", admin = false)
        assertEquals(AdminMessage.Failed(AdminError.LastAdmin), vm.messages.first())
        assertFalse(vm.closed.value)
    }

    @Test
    fun changingYourOwnRoleRefreshesYourPlan() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setRole("me", "me@x.y", admin = false)
        assertEquals(listOf("me" to false), backend.roleChanges)
        assertEquals(listOf("me"), refreshed)
        assertEquals(AdminMessage.RoleChanged("me@x.y", admin = false), vm.messages.first())
    }

    @Test
    fun losingAdminAccessClosesTheScreens() = runTest(dispatcher) {
        val vm = viewModel()
        backend.failWith = AdminError.Forbidden
        vm.reload()
        assertTrue(vm.closed.value)
        assertEquals(listOf("me"), refreshed)
        assertEquals(AdminMessage.Failed(AdminError.Forbidden), vm.messages.first())
    }
}
