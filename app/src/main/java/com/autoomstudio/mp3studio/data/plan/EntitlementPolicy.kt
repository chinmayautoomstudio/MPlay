package com.autoomstudio.mp3studio.data.plan

import java.util.concurrent.TimeUnit

/**
 * Decides from the cached [Entitlements] what the user may use right now (PRD PL3, PL4). The server is the source of
 * truth; this only stops a cached Pro or Trial from outliving its end date or the offline grace window.
 */
object EntitlementPolicy {

    /** Pro and Trial keep working offline this long after the last successful check (PL4). */
    val GRACE_MS: Long = TimeUnit.DAYS.toMillis(7)

    /** A phone clock this far behind the last check means the clock was turned back; the cache isn't trusted. */
    private val CLOCK_BACK_TOLERANCE_MS: Long = TimeUnit.HOURS.toMillis(1)

    const val TRIAL_REMINDER_DAYS = 3

    /** True when the cache is too old (or the clock was turned back) to unlock anything. */
    fun isStale(entitlements: Entitlements, now: Long): Boolean {
        val age = now - entitlements.checkedAt
        return age > GRACE_MS || age < -CLOCK_BACK_TOLERANCE_MS
    }

    /** The plan in effect now. No cache, a stale cache or an elapsed end date all mean Free. */
    fun effectivePlan(entitlements: Entitlements?, now: Long): Plan {
        if (entitlements == null || isStale(entitlements, now)) return Plan.Free
        val serverNow = entitlements.serverTime + (now - entitlements.checkedAt).coerceAtLeast(0)
        val trialActive = entitlements.trialEndsAt?.let { serverNow < it } == true
        return when (entitlements.plan) {
            Plan.Pro -> when {
                entitlements.subscriptionExpiresAt?.let { serverNow >= it } != true -> Plan.Pro
                trialActive -> Plan.Trial
                else -> Plan.Free
            }
            Plan.Trial -> if (trialActive) Plan.Trial else Plan.Free
            Plan.Free -> Plan.Free
        }
    }

    fun canUse(feature: Feature, entitlements: Entitlements?, now: Long): Boolean = when (feature) {
        Feature.BpmDetector, Feature.SingAlong, Feature.UnlimitedSeparator ->
            effectivePlan(entitlements, now) != Plan.Free
    }

    /**
     * This week's separator usage to show a Free user (PRD US6); null on Trial or Pro, or before the first check.
     * Once the cached week has ended, completed uses no longer count and the reset moves to the next Monday.
     */
    fun separatorUsage(entitlements: Entitlements?, now: Long): SeparatorUsage? {
        if (effectivePlan(entitlements, now) != Plan.Free) return null
        val usage = entitlements?.usage ?: return null
        val serverNow = entitlements.serverTime + (now - entitlements.checkedAt).coerceAtLeast(0)
        if (serverNow < usage.resetsAt) return usage
        val weekMs = TimeUnit.DAYS.toMillis(7)
        val weeksPassed = (serverNow - usage.resetsAt) / weekMs + 1
        return usage.copy(
            used = 0,
            remaining = (usage.limit - usage.reserved).coerceAtLeast(0),
            resetsAt = usage.resetsAt + weeksPassed * weekMs,
        )
    }

    /** Whole days left in a running trial, rounded up; null when the user isn't on Trial. */
    fun trialDaysLeft(entitlements: Entitlements?, now: Long): Int? {
        if (effectivePlan(entitlements, now) != Plan.Trial) return null
        val endsAt = entitlements?.trialEndsAt ?: return null
        val serverNow = entitlements.serverTime + (now - entitlements.checkedAt).coerceAtLeast(0)
        val dayMs = TimeUnit.DAYS.toMillis(1)
        return ((endsAt - serverNow + dayMs - 1) / dayMs).toInt().coerceAtLeast(1)
    }
}
