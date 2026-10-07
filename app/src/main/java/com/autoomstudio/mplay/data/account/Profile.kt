package com.autoomstudio.mplay.data.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of `public.profiles`. The app may change only [displayName] and [avatarUrl]. */
@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "user",
    val disabled: Boolean = false,
)
