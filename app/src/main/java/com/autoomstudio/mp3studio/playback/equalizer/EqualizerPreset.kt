package com.autoomstudio.mp3studio.playback.equalizer

/**
 * Built-in preset id, or [CUSTOM], or a user-saved name prefixed with [USER_PREFIX].
 */
enum class BuiltInPreset(val id: String, val displayName: String, val gainsDb: FloatArray) {
    Normal("normal", "Normal", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Pop("pop", "Pop", floatArrayOf(-1f, 2f, 3f, 4f, 2f, 0f, -1f)),
    Rock("rock", "Rock", floatArrayOf(5f, 3f, -1f, -1f, 3f, 4f, 5f)),
    HipHop("hip_hop", "Hip Hop", floatArrayOf(6f, 5f, 1f, -1f, 1f, 2f, 3f)),
    Jazz("jazz", "Jazz", floatArrayOf(3f, 2f, 1f, -1f, 1f, 3f, 4f)),
    Classical("classical", "Classical", floatArrayOf(4f, 3f, 0f, -2f, 0f, 3f, 4f)),
    Electronic("electronic", "Electronic", floatArrayOf(5f, 4f, 1f, 0f, 2f, 4f, 5f)),
    Vocal("vocal", "Vocal", floatArrayOf(-3f, -2f, 1f, 3f, 4f, 2f, 0f)),
    BassBoost("bass_boost", "Bass Boost", floatArrayOf(7f, 6f, 3f, 0f, 0f, 0f, 0f)),
    ;

    companion object {
        fun fromId(id: String): BuiltInPreset? = entries.firstOrNull { it.id == id }
    }
}

object EqualizerPresetIds {
    const val CUSTOM = "custom"
    const val USER_PREFIX = "user:"

    fun userId(name: String): String = USER_PREFIX + name

    fun isUser(id: String): Boolean = id.startsWith(USER_PREFIX)

    fun userName(id: String): String? =
        if (isUser(id)) id.removePrefix(USER_PREFIX) else null
}

data class SavedEqualizerPreset(
    val name: String,
    val gainsDb: FloatArray,
    val preampDb: Float = 0f,
) {
    val id: String get() = EqualizerPresetIds.userId(name)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedEqualizerPreset) return false
        return name == other.name &&
            preampDb == other.preampDb &&
            gainsDb.contentEquals(other.gainsDb)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + gainsDb.contentHashCode()
        result = 31 * result + preampDb.hashCode()
        return result
    }
}
