package com.autoomstudio.mp3studio.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.admin.AddAdminResult
import com.autoomstudio.mp3studio.data.admin.AdminBackend
import com.autoomstudio.mp3studio.data.admin.AdminError
import com.autoomstudio.mp3studio.data.admin.AdminException
import com.autoomstudio.mp3studio.data.admin.AdminList
import com.autoomstudio.mp3studio.data.admin.AdminOverview
import com.autoomstudio.mp3studio.data.admin.AdminUsage
import com.autoomstudio.mp3studio.data.admin.AdminUserDetail
import com.autoomstudio.mp3studio.data.admin.AdminUserRow
import com.autoomstudio.mp3studio.data.admin.AuditEntry
import com.autoomstudio.mp3studio.data.admin.UserFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pages inside Settings > Admin; the first is the home page. */
sealed interface AdminPage {
    data object Home : AdminPage
    data class User(val id: String) : AdminPage
    data object Admins : AdminPage
    data object Usage : AdminPage
    data object Audit : AdminPage
}

/** One page's data as fetched from the server. */
data class Loadable<T>(val data: T? = null, val loading: Boolean = false, val error: AdminError? = null)

data class UserListState(
    val query: String = "",
    val filter: UserFilter = UserFilter.All,
    val users: List<AdminUserRow> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: AdminError? = null,
) {
    val hasMore: Boolean get() = users.size < total
}

data class AuditState(
    val entries: List<AuditEntry> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: AdminError? = null,
)

sealed interface AdminMessage {
    data class Failed(val error: AdminError) : AdminMessage
    data class RoleChanged(val email: String, val admin: Boolean) : AdminMessage
    data class DisabledChanged(val disabled: Boolean) : AdminMessage
    data object ProGranted : AdminMessage
    data object ProRemoved : AdminMessage
    data class AdminAdded(val email: String, val result: AddAdminResult) : AdminMessage
    data object InviteRevoked : AdminMessage
}

/**
 * Settings > Admin (PRD AD1-AD10). Everything is fetched live from the `admin` Edge Function; nothing is cached.
 * When the server says the caller is no longer an Admin, the plan is refreshed (which hides the Admin entry) and
 * [closed] is set.
 */
class AdminViewModel(
    private val backend: AdminBackend,
    private val currentUserId: () -> String?,
    private val refreshOwnPlan: suspend (userId: String) -> Unit,
    private val searchDelayMillis: Long = 300,
) : ViewModel() {

    private val stack = MutableStateFlow<List<AdminPage>>(listOf(AdminPage.Home))
    private val _page = MutableStateFlow<AdminPage>(AdminPage.Home)
    val page: StateFlow<AdminPage> = _page.asStateFlow()

    private val _overview = MutableStateFlow(Loadable<AdminOverview>())
    val overview: StateFlow<Loadable<AdminOverview>> = _overview.asStateFlow()

    private val _users = MutableStateFlow(UserListState())
    val users: StateFlow<UserListState> = _users.asStateFlow()

    private val _detail = MutableStateFlow(Loadable<AdminUserDetail>())
    val detail: StateFlow<Loadable<AdminUserDetail>> = _detail.asStateFlow()

    private val _usage = MutableStateFlow(Loadable<AdminUsage>())
    val usage: StateFlow<Loadable<AdminUsage>> = _usage.asStateFlow()

    private val _admins = MutableStateFlow(Loadable<AdminList>())
    val admins: StateFlow<Loadable<AdminList>> = _admins.asStateFlow()

    private val _audit = MutableStateFlow(AuditState())
    val audit: StateFlow<AuditState> = _audit.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** An action is running; action buttons are disabled meanwhile. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    private val _messages = Channel<AdminMessage>(Channel.BUFFERED)
    val messages: Flow<AdminMessage> = _messages.receiveAsFlow()

    private var searchJob: Job? = null

    val me: String? get() = currentUserId()

    fun open(page: AdminPage) {
        stack.update { it + page }
        show(page)
    }

    /** Goes back one page; false on the home page, where the caller leaves Admin. */
    fun back(): Boolean {
        val current = stack.value
        if (current.size <= 1) return false
        stack.value = current.dropLast(1)
        show(stack.value.last())
        return true
    }

    private fun show(page: AdminPage) {
        _page.value = page
        reload()
    }

    /** Fetches the current page again. */
    fun reload() {
        when (val page = _page.value) {
            AdminPage.Home -> loadHome()
            is AdminPage.User -> {
                if (_detail.value.data?.profile?.id != page.id) _detail.value = Loadable()
                load(_detail) { backend.user(page.id) }
            }
            AdminPage.Admins -> load(_admins) { backend.admins() }
            AdminPage.Usage -> load(_usage) { backend.usage() }
            AdminPage.Audit -> loadAudit(more = false)
        }
    }

    private fun loadHome() {
        load(_overview) { backend.overview() }
        searchUsers(immediately = true)
    }

    fun setQuery(query: String) {
        _users.update { it.copy(query = query) }
        searchUsers(immediately = false)
    }

    fun setFilter(filter: UserFilter) {
        _users.update { it.copy(filter = filter) }
        searchUsers(immediately = true)
    }

    private fun searchUsers(immediately: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediately) delay(searchDelayMillis)
            val state = _users.value
            _users.update { it.copy(loading = true, error = null) }
            try {
                val page = backend.users(state.query.trim(), state.filter, offset = 0)
                _users.update { it.copy(users = page.users, total = page.total, loading = false) }
            } catch (e: AdminException) {
                _users.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }
    }

    fun loadMoreUsers() {
        val state = _users.value
        if (state.loading || !state.hasMore) return
        searchJob = viewModelScope.launch {
            _users.update { it.copy(loading = true, error = null) }
            try {
                val page = backend.users(state.query.trim(), state.filter, offset = state.users.size)
                _users.update { it.copy(users = it.users + page.users, total = page.total, loading = false) }
            } catch (e: AdminException) {
                _users.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }
    }

    fun loadAudit(more: Boolean) {
        val state = _audit.value
        if (state.loading) return
        val before = if (more) state.entries.lastOrNull()?.id else null
        viewModelScope.launch {
            _audit.update { it.copy(loading = true, error = null) }
            try {
                val entries = backend.audit(before)
                _audit.update {
                    it.copy(
                        entries = if (more) it.entries + entries else entries,
                        hasMore = entries.size >= AdminBackend.PAGE_SIZE,
                        loading = false,
                    )
                }
            } catch (e: AdminException) {
                _audit.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }
    }

    fun setRole(userId: String, email: String, admin: Boolean) = act(AdminMessage.RoleChanged(email, admin)) {
        backend.setRole(userId, admin)
        if (userId == currentUserId()) refreshOwnPlan(userId)
    }

    fun setDisabled(userId: String, disabled: Boolean) = act(AdminMessage.DisabledChanged(disabled)) {
        backend.setDisabled(userId, disabled)
    }

    fun grantPro(userId: String, until: Long) = act(AdminMessage.ProGranted) {
        backend.grantPro(userId, until)
        if (userId == currentUserId()) refreshOwnPlan(userId)
    }

    fun revokePro(userId: String) = act(AdminMessage.ProRemoved) {
        backend.revokePro(userId)
        if (userId == currentUserId()) refreshOwnPlan(userId)
    }

    fun addAdmin(email: String, onDone: (Boolean) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = backend.addAdmin(email)
                _messages.send(AdminMessage.AdminAdded(email.trim(), result))
                onDone(true)
                reload()
            } catch (e: AdminException) {
                if (e.error != AdminError.Forbidden) _messages.send(AdminMessage.Failed(e.error))
                onDone(false)
                onForbidden(e.error)
            } finally {
                _busy.value = false
            }
        }
    }

    fun revokeInvite(email: String) = act(AdminMessage.InviteRevoked) { backend.revokeInvite(email) }

    private fun act(success: AdminMessage, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
                _messages.send(success)
            } catch (e: AdminException) {
                if (e.error != AdminError.Forbidden) _messages.send(AdminMessage.Failed(e.error))
                onForbidden(e.error)
            } finally {
                _busy.value = false
            }
            if (!_closed.value) reload()
        }
    }

    private fun <T> load(target: MutableStateFlow<Loadable<T>>, fetch: suspend () -> T) {
        viewModelScope.launch {
            target.update { it.copy(loading = true, error = null) }
            try {
                target.value = Loadable(data = fetch())
            } catch (e: AdminException) {
                target.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }
    }

    /** Goes to the home page, as when Settings > Admin is opened again. */
    fun enter() {
        _closed.value = false
        stack.value = listOf(AdminPage.Home)
        show(AdminPage.Home)
    }

    private suspend fun onForbidden(error: AdminError) {
        if (error != AdminError.Forbidden || _closed.value) return
        _closed.value = true
        _messages.send(AdminMessage.Failed(AdminError.Forbidden))
        currentUserId()?.let { refreshOwnPlan(it) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MPlayApp).container
                AdminViewModel(
                    backend = container.adminBackend,
                    currentUserId = container::signedInUserId,
                    refreshOwnPlan = { container.entitlementsRepository.refresh(it) },
                )
            }
        }
    }
}
