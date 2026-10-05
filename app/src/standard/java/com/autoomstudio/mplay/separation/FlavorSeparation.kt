package com.autoomstudio.mplay.separation

import android.content.Context
import com.autoomstudio.mplay.data.settings.AppSettings

/** Standard MPlay has no model: stems left by MPlay AI still play and can be deleted, but none are made. */
object FlavorSeparation {
    @Suppress("UNUSED_PARAMETER")
    fun createBackend(context: Context, settings: AppSettings): SeparationBackend = object : SeparationBackend {
        override fun checkAvailability() = SeparationAvailability.Unavailable(listOf(UnsupportedReason.NotIncluded))

        override suspend fun schedule() = Unit
    }
}
