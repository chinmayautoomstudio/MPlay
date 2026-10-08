package com.autoomstudio.mp3studio.data.plan

import kotlinx.serialization.Serializable

enum class Plan { Free, Trial, Pro }

/** Features that depend on the plan (PRD section 6.3). Everything else is free for every signed-in user. */
enum class Feature { BpmDetector, SingAlong, UnlimitedSeparator }

/** Outcome of the trial claim the server made on this check, if it made one. */
enum class TrialClaim { Granted, Existing, Denied, Unavailable }

/**
 * What the server said the user may use, as last fetched. Times are epoch milliseconds. [checkedAt] is the phone's
 * clock when the answer arrived and [serverTime] the server's, so the policy can measure how old it is.
 */
@Serializable
data class Entitlements(
    val userId: String,
    val plan: Plan,
    val trialStartedAt: Long? = null,
    val trialEndsAt: Long? = null,
    val subscriptionStatus: String? = null,
    val subscriptionExpiresAt: Long? = null,
    val subscriptionNextBillingAt: Long? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val trialClaim: TrialClaim? = null,
    val serverTime: Long,
    val checkedAt: Long,
)
