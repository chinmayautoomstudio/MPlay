package com.autoomstudio.mp3studio.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.plan.EntitlementPolicy
import com.autoomstudio.mp3studio.data.plan.Entitlements
import com.autoomstudio.mp3studio.data.plan.Feature
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import com.autoomstudio.mp3studio.data.plan.TrialClaim
import com.autoomstudio.mp3studio.di.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The plan as screens show it. [plan] is what is unlocked now; [cachedPlan] is what the server last said, which
 * differs when the cache is [stale] (offline longer than the grace window).
 */
data class PlanUiState(
    val plan: Plan,
    val cachedPlan: Plan?,
    val stale: Boolean,
    val trialDaysLeft: Int?,
    val trialEndsAt: Long?,
    val trialClaim: TrialClaim?,
    val subscriptionExpiresAt: Long?,
    val subscriptionNextBillingAt: Long?,
    val cancelAtPeriodEnd: Boolean,
    val checkedAt: Long?,
    /** Free users' AI Vocal Separator uses this week; null on Trial and Pro. */
    val usage: SeparatorUsage? = null,
) {
    fun unlocks(feature: Feature): Boolean = when (feature) {
        Feature.BpmDetector, Feature.SingAlong, Feature.UnlimitedSeparator -> plan != Plan.Free
    }

    val showTrialReminder: Boolean
        get() = trialDaysLeft != null && trialDaysLeft <= EntitlementPolicy.TRIAL_REMINDER_DAYS

    companion object {
        fun of(entitlements: Entitlements?, now: Long) = PlanUiState(
            plan = EntitlementPolicy.effectivePlan(entitlements, now),
            cachedPlan = entitlements?.plan,
            stale = entitlements != null && EntitlementPolicy.isStale(entitlements, now),
            trialDaysLeft = EntitlementPolicy.trialDaysLeft(entitlements, now),
            trialEndsAt = entitlements?.trialEndsAt,
            trialClaim = entitlements?.trialClaim,
            subscriptionExpiresAt = entitlements?.subscriptionExpiresAt,
            subscriptionNextBillingAt = entitlements?.subscriptionNextBillingAt,
            cancelAtPeriodEnd = entitlements?.cancelAtPeriodEnd ?: false,
            checkedAt = entitlements?.checkedAt,
            usage = EntitlementPolicy.separatorUsage(entitlements, now),
        )
    }
}

class PlansViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.entitlementsRepository

    val state: StateFlow<PlanUiState> = repository.entitlements
        .map { cached -> PlanUiState.of(cached?.takeIf { it.userId == container.signedInUserId() }, repository.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlanUiState.of(null, repository.now()))

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshFailed = Channel<Unit>(Channel.CONFLATED)

    /** Fires when a manual refresh couldn't reach the server. */
    val refreshFailed: Flow<Unit> = _refreshFailed.receiveAsFlow()

    private val _openPlansRequest = MutableStateFlow(false)

    /** "See plans" from an upgrade sheet; the main screen opens Settings > Plans and clears it. */
    val openPlansRequest: StateFlow<Boolean> = _openPlansRequest.asStateFlow()

    suspend fun canUse(feature: Feature): Boolean = repository.canUse(feature, container.signedInUserId())

    fun refresh() {
        val userId = container.signedInUserId() ?: return
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            if (!repository.refresh(userId)) _refreshFailed.trySend(Unit)
            _refreshing.value = false
        }
    }

    fun openPlans() {
        _openPlansRequest.value = true
    }

    fun onOpenPlansHandled() {
        _openPlansRequest.value = false
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlansViewModel((this[APPLICATION_KEY] as MPlayApp).container) }
        }
    }
}
