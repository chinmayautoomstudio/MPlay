package com.autoomstudio.mp3studio.separation

import android.content.Context
import com.autoomstudio.mp3studio.separation.android.ModelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ModelState {
    data object Installed : ModelState

    /** [sizeBytes] is what installing it will take. */
    data class NotInstalled(val sizeBytes: Long) : ModelState
}

/** Where the separation model comes from. The UI only reacts to [state], so a downloading provider can replace this. */
interface ModelProvider {
    val state: StateFlow<ModelState>

    /** Re-checks after the model file changed, for example after an import. */
    fun refresh()
}

/** The model ships inside the APK; a file imported into app storage takes precedence (see [ModelSource.find]). */
class BundledModelProvider(private val context: Context) : ModelProvider {

    private val _state = MutableStateFlow(check())
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    override fun refresh() {
        _state.value = check()
    }

    private fun check(): ModelState =
        if (ModelSource.find(context) != null) ModelState.Installed else ModelState.NotInstalled(MODEL_BYTES)

    private companion object {
        /** htdemucs.onnx with float16 weights. */
        const val MODEL_BYTES = 90_555_835L
    }
}
