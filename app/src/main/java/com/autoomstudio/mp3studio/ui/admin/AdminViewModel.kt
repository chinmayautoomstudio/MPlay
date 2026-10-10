package com.autoomstudio.mp3studio.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.admin.AddAdminResult
import com.autoomstudio.mp3studio.data.admin.AdminActivity
import com.autoomstudio.mp3studio.data.admin.AdminBackend
import com.autoomstudio.mp3studio.data.admin.AdminBillingHealth
import com.autoomstudio.mp3studio.data.admin.AdminBillingResult
import com.autoomstudio.mp3studio.data.admin.AdminError
import com.autoomstudio.mp3studio.data.admin.AdminException
import com.autoomstudio.mp3studio.data.admin.AdminList
import com.autoomstudio.mp3studio.data.admin.AdminOverview
import com.autoomstudio.mp3studio.data.admin.AdminPaymentRow
import com.autoomstudio.mp3studio.data.admin.PaymentFilter
import com.autoomstudio.mp3studio.data.admin.AdminUsage
import com.autoomstudio.mp3studio.data.admin.AdminUserDetail
import com.autoomstudio.mp3studio.data.admin.AdminUserRow
import com.autoomstudio.mp3studio.data.admin.AuditEntry
import com.autoomstudio.mp3studio.data.admin.UserFilter
import com.autoomstudio.mp3studio.data.plan.epochMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Pages inside Settings > Admin; the first is the home page. */
sealed interface AdminPage {
    data object Home : AdminPage
    data class User(val id: String) : AdminPage
    data object Admins : AdminPage
    data object Usage : AdminPage
    data object Audit : AdminPage
    data object Activity : AdminPage
    data object Payments : AdminPage
}

data class PaymentListState(
    val query: String = "",
    val filter: PaymentFilter = PaymentFilter.All,
    val payments: List<AdminPaymentRow> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: AdminError? = null,
) {
    val hasMore: Boolean get() = payments.size < total
}

/** One page's data as fetched from the server. */
data class Loadable<T>(val data: T? = null, val loading: Boolean = false, val error: AdminError? = null)

/**
 * How a fetch shows itself: [Normal] with the page's own spinners, [Pull] under the pull-to-refresh indicator, and
 * [Auto] (the periodic refresh) without any indicator, keeping the shown data when it fails.
 */
private enum class LoadMode { Normal, Pull, Auto }

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
    data class Billing(val result: AdminBillingResult) : AdminMessage
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
    activitySeenAt: Flow<Long>,
    private val markActivitySeen: suspend (epochMillis: Long) -> Unit,
    notificationsEnabled: Flow<Boolean> = flowOf(true),
    private val saveNotificationsEnabled: suspend (Boolean) -> Unit = {},
    private val searchDelayMillis: Long = 300,
) : ViewModel() {

    /** Whether new activity is also posted as phone notifications (`AdminActivityWorker`). */
    val notificationsEnabled: StateFlow<Boolean> =
        notificationsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { saveNotificationsEnabled(enabled) }
    }

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

    private val _activity = MutableStateFlow(Loadable<List<AdminActivity>>())
    val activity: StateFlow<Loadable<List<AdminActivity>>> = _activity.asStateFlow()

    private val _payments = MutableStateFlow(PaymentListState())
    val payments: StateFlow<PaymentListState> = _payments.asStateFlow()

    private val _health = MutableStateFlow(Loadable<AdminBillingHealth>())
    val health: StateFlow<Loadable<AdminBillingHealth>> = _health.asStateFlow()

    /** Activity events newer than the last one the admin saw; shown as the bell's badge. */
    val unread: StateFlow<Int> = combine(_activity, activitySeenAt) { activity, seenAt ->
        activity.data.orEmpty().count { eventMillis(it) > seenAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _busy = MutableStateFlow(false)

    /** An action is running; action buttons are disabled meanwhile. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)

    /** A pull-to-refresh is running. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

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
        loadPage(LoadMode.Normal)
    }

    /** Pull-to-refresh: fetches the current page again and keeps [refreshing] set until every part has answered. */
    fun refresh() {
        if (_refreshing.value || _closed.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                loadPage(LoadMode.Pull).joinAll()
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * The periodic refresh: quietly fetches the current page again. Skipped while an action, a pull or a load is
     * running, and lists the admin has paged past their first page are left alone so their scroll position holds.
     */
    fun autoRefresh() {
        if (_busy.value || _refreshing.value || _closed.value) return
        loadPage(LoadMode.Auto)
    }

    private fun loadPage(mode: LoadMode): List<Job> {
        val auto = mode == LoadMode.Auto
        return when (val page = _page.value) {
            AdminPage.Home -> listOfNotNull(
                load(_overview, mode) { backend.overview() },
                loadActivity(markSeen = false, mode),
                _users.value.takeUnless { auto && (it.loading || it.users.size > AdminBackend.PAGE_SIZE) }
                    ?.let { searchUsers(immediately = true, mode) },
            )
            is AdminPage.User -> {
                if (_detail.value.data?.profile?.id != page.id) _detail.value = Loadable()
                listOf(load(_detail, mode) { backend.user(page.id) })
            }
            AdminPage.Admins -> listOf(load(_admins, mode) { backend.admins() })
            AdminPage.Usage -> listOf(load(_usage, mode) { backend.usage() })
            AdminPage.Audit -> listOfNotNull(
                _audit.value.takeUnless { auto && it.entries.size > AdminBackend.PAGE_SIZE }
                    ?.let { loadAudit(more = false, mode) },
            )
            AdminPage.Activity -> listOf(loadActivity(markSeen = true, mode))
            AdminPage.Payments -> listOfNotNull(
                load(_health, mode) { backend.billingHealth() },
                _payments.value.takeUnless { auto && (it.loading || it.payments.size > AdminBackend.PAGE_SIZE) }
                    ?.let { searchPayments(immediately = true, mode) },
            )
        }
    }

    fun setPaymentQuery(query: String) {
        _payments.update { it.copy(query = query) }
        searchPayments(immediately = false)
    }

    fun setPaymentFilter(filter: PaymentFilter) {
        _payments.update { it.copy(filter = filter) }
        searchPayments(immediately = true)
    }

    private fun searchPayments(immediately: Boolean, mode: LoadMode = LoadMode.Normal): Job {
        searchJob?.cancel()
        return viewModelScope.launch {
            if (!immediately) delay(searchDelayMillis)
            val state = _payments.value
            if (mode == LoadMode.Normal) _payments.update { it.copy(loading = true, error = null) }
            try {
                val page = backend.payments(state.query.trim(), state.filter, offset = 0)
                _payments.update {
                    it.copy(payments = page.payments, total = page.total, loading = false, error = null)
                }
            } catch (e: AdminException) {
                if (mode != LoadMode.Auto) _payments.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }.also { searchJob = it }
    }

    fun loadMorePayments() {
        val state = _payments.value
        if (state.loading || !state.hasMore) return
        searchJob = viewModelScope.launch {
            _payments.update { it.copy(loading = true, error = null) }
            try {
                val page = backend.payments(state.query.trim(), state.filter, offset = state.payments.size)
                _payments.update { it.copy(payments = it.payments + page.payments, total = page.total, loading = false) }
            } catch (e: AdminException) {
                _payments.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }
    }

    fun reverifyPayment(txnId: String) = actFor { AdminMessage.Billing(backend.reverifyPayment(txnId)) }

    fun refundPayment(txnId: String) = actFor { AdminMessage.Billing(backend.refundPayment(txnId)) }

    fun cancelSubscription(userId: String) = actFor {
        val result = backend.cancelSubscription(userId)
        if (userId == currentUserId()) refreshOwnPlan(userId)
        AdminMessage.Billing(result)
    }

    fun revokeSubscription(userId: String) = actFor {
        val result = backend.revokeSubscription(userId)
        if (userId == currentUserId()) refreshOwnPlan(userId)
        AdminMessage.Billing(result)
    }

    private fun loadActivity(markSeen: Boolean, mode: LoadMode = LoadMode.Normal): Job =
        viewModelScope.launch {
            if (mode == LoadMode.Normal) _activity.update { it.copy(loading = true, error = null) }
            try {
                val events = backend.activity()
                _activity.value = Loadable(data = events)
                if (markSeen) events.maxOfOrNull(::eventMillis)?.let { markActivitySeen(it) }
            } catch (e: AdminException) {
                if (mode != LoadMode.Auto) _activity.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }

    fun setQuery(query: String) {
        _users.update { it.copy(query = query) }
        searchUsers(immediately = false)
    }

    fun setFilter(filter: UserFilter) {
        _users.update { it.copy(filter = filter) }
        searchUsers(immediately = true)
    }

    private fun searchUsers(immediately: Boolean, mode: LoadMode = LoadMode.Normal): Job {
        searchJob?.cancel()
        return viewModelScope.launch {
            if (!immediately) delay(searchDelayMillis)
            val state = _users.value
            if (mode == LoadMode.Normal) _users.update { it.copy(loading = true, error = null) }
            try {
                val page = backend.users(state.query.trim(), state.filter, offset = 0)
                _users.update { it.copy(users = page.users, total = page.total, loading = false, error = null) }
            } catch (e: AdminException) {
                if (mode != LoadMode.Auto) _users.update { it.copy(loading = false, error = e.error) }
                onForbidden(e.error)
            }
        }.also { searchJob = it }
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
        loadAudit(more, LoadMode.Normal)
    }

    private fun loadAudit(more: Boolean, mode: LoadMode): Job? {
        val state = _audit.value
        if (state.loading) return null
        val before = if (more) state.entries.lastOrNull()?.id else null
        return viewModelScope.launch {
            if (mode == LoadMode.Normal) _audit.update { it.copy(loading = true, error = null) }
            try {
                val entries = backend.audit(before)
                _audit.update {
                    it.copy(
                        entries = if (more) it.entries + entries else entries,
                        hasMore = entries.size >= AdminBackend.PAGE_SIZE,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: AdminException) {
                if (mode != LoadMode.Auto) _audit.update { it.copy(loading = false, error = e.error) }
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

    private fun act(success: AdminMessage, block: suspend () -> Unit) = actFor {
        block()
        success
    }

    private fun actFor(block: suspend () -> AdminMessage) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _messages.send(block())
            } catch (e: AdminException) {
                if (e.error != AdminError.Forbidden) _messages.send(AdminMessage.Failed(e.error))
                onForbidden(e.error)
            } finally {
                _busy.value = false
            }
            if (!_closed.value) reload()
        }
    }

    private fun <T> load(
        target: MutableStateFlow<Loadable<T>>,
        mode: LoadMode = LoadMode.Normal,
        fetch: suspend () -> T,
    ): Job = viewModelScope.launch {
        if (mode == LoadMode.Normal) target.update { it.copy(loading = true, error = null) }
        try {
            target.value = Loadable(data = fetch())
        } catch (e: AdminException) {
            if (mode != LoadMode.Auto || target.value.data == null) {
                target.update { it.copy(loading = false, error = e.error) }
            }
            onForbidden(e.error)
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
        /** How often the visible Admin page is fetched again while the app is in the foreground. */
        const val AUTO_REFRESH_MILLIS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MPlayApp).container
                AdminViewModel(
                    backend = container.adminBackend,
                    currentUserId = container::signedInUserId,
                    refreshOwnPlan = { container.entitlementsRepository.refresh(it) },
                    activitySeenAt = container.appSettings.adminActivitySeenAt,
                    markActivitySeen = container.appSettings::setAdminActivitySeenAt,
                    notificationsEnabled = container.appSettings.adminNotificationsEnabled,
                    saveNotificationsEnabled = container.appSettings::setAdminNotificationsEnabled,
                )
            }
        }
    }
}

private fun eventMillis(event: AdminActivity): Long = runCatching { epochMillis(event.at) }.getOrDefault(0L)
