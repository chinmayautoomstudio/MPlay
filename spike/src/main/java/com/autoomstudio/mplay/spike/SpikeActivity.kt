package com.autoomstudio.mplay.spike

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoomstudio.mplay.separation.android.Accelerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Feasibility spike (PRD section 7): runs the real separation pipeline on a picked song and records speed,
 * memory, battery and heat. Push the model with
 * `adb push htdemucs.onnx /sdcard/Android/data/com.autoomstudio.mplay.ai.spike/files/` (or bundle it as an asset).
 */
class SpikeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SpikeScreen() }
            }
        }
    }
}

@Composable
private fun SpikeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val log = remember { mutableStateListOf<String>() }
    var song by remember { mutableStateOf<Uri?>(null) }
    var threads by remember { mutableStateOf(4) }
    var overlap by remember { mutableStateOf(0.25) }
    var accelerator by remember { mutableStateOf(Accelerator.Cpu) }
    var seconds by remember { mutableStateOf<Int?>(30) }
    var soak by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { song = it ?: song }
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex) }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MPlay AI feasibility spike", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = !running) {
            Text(song?.lastPathSegment ?: "Pick a song")
        }
        ChoiceRow("Threads", listOf(2, 4, 6), threads, { "$it" }) { threads = it }
        ChoiceRow("Overlap", listOf(0.25, 0.1), overlap, { "${(it * 100).toInt()}%" }) { overlap = it }
        ChoiceRow("Runtime", Accelerator.entries, accelerator, { it.name }) { accelerator = it }
        ChoiceRow("Length", listOf(30, null), seconds, { if (it == null) "Full song" else "${it}s" }) { seconds = it }
        ChoiceRow("Heat run", listOf(false, true), soak, { if (it) "Repeat 5 min" else "Once" }) { soak = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = song != null && !running,
                onClick = {
                    val uri = song ?: return@Button
                    running = true
                    cancelRequested = false
                    val config = SpikeConfig(threads, overlap, accelerator, seconds, if (soak) 5 else null)
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            val runner = SpikeRunner(context.applicationContext) { line ->
                                scope.launch(Dispatchers.Main) { log += line }
                            }
                            try {
                                runner.run(uri, config) { cancelRequested }
                            } catch (e: Throwable) {
                                scope.launch(Dispatchers.Main) { log += "Error: $e" }
                            }
                        }
                        running = false
                    }
                },
            ) { Text("Run") }
            OutlinedButton(enabled = running, onClick = { cancelRequested = true }) { Text("Cancel") }
            OutlinedButton(enabled = !running, onClick = { log.clear() }) { Text("Clear") }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(log) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, modifier = Modifier.padding(top = 14.dp))
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(name(option)) })
        }
    }
}
