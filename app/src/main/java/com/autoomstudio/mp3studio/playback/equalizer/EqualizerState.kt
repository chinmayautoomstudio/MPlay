package com.autoomstudio.mp3studio.playback.equalizer

/**
 * Full equalizer configuration shared by the UI, repository and audio processor.
 * [presetId] is [EqualizerPresetIds.CUSTOM], a [BuiltInPreset.id], or a user preset id.
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val gainsDb: FloatArray = EqualizerBands.flatGains(),
    val presetId: String = BuiltInPreset.Normal.id,
    val customGainsDb: FloatArray = EqualizerBands.flatGains(),
    val preampDb: Float = 0f,
    val bassBoost: Boolean = false,
    val virtualizer: Boolean = false,
    val loudness: Boolean = false,
    val savedPresets: List<SavedEqualizerPreset> = emptyList(),
) {
    /** Chip "on" when the equalizer is enabled and not a flat Normal with extras off. */
    val isActive: Boolean
        get() = enabled && (
            !isFlatNormal ||
                bassBoost ||
                virtualizer ||
                loudness ||
                preampDb != 0f
            )

    val isFlatNormal: Boolean
        get() = gainsDb.all { it == 0f } &&
            presetId == BuiltInPreset.Normal.id

    val isNeutralProcessing: Boolean
        get() = !enabled || (
            gainsDb.all { it == 0f } &&
                preampDb == 0f &&
                !bassBoost &&
                !virtualizer &&
                !loudness
            )

    fun displayPresetName(): String = when {
        presetId == EqualizerPresetIds.CUSTOM -> "Custom"
        EqualizerPresetIds.isUser(presetId) -> EqualizerPresetIds.userName(presetId) ?: "Custom"
        else -> BuiltInPreset.fromId(presetId)?.displayName ?: "Custom"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerState) return false
        return enabled == other.enabled &&
            presetId == other.presetId &&
            preampDb == other.preampDb &&
            bassBoost == other.bassBoost &&
            virtualizer == other.virtualizer &&
            loudness == other.loudness &&
            gainsDb.contentEquals(other.gainsDb) &&
            customGainsDb.contentEquals(other.customGainsDb) &&
            savedPresets == other.savedPresets
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + gainsDb.contentHashCode()
        result = 31 * result + presetId.hashCode()
        result = 31 * result + customGainsDb.contentHashCode()
        result = 31 * result + preampDb.hashCode()
        result = 31 * result + bassBoost.hashCode()
        result = 31 * result + virtualizer.hashCode()
        result = 31 * result + loudness.hashCode()
        result = 31 * result + savedPresets.hashCode()
        return result
    }

    companion object {
        val Default = EqualizerState()
    }
}
