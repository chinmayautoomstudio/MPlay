package com.autoomstudio.mplay.ui.metronome

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** The Metronome tab (MT1). */
@Composable
fun MetronomeScreen(
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tapCount by viewModel.tapCount.collectAsStateWithLifecycle()
    MetronomeControls(
        state = state,
        beat = viewModel.beat,
        tapCount = tapCount,
        onUpdate = viewModel::update,
        onToggle = viewModel::toggle,
        onTap = viewModel::tap,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
