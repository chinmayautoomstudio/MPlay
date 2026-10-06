package com.autoomstudio.mplay.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.ui.library.DetailBackButton

private data class OpenSourceComponent(
    val name: String,
    val notice: String,
    val license: String,
    @param:RawRes val text: Int,
)

private const val APACHE = "Apache License 2.0"
private const val MIT = "MIT License"

/** What ships inside the APK: the separation model, its runtime and the libraries linked into the app. */
private val COMPONENTS = listOf(
    OpenSourceComponent(
        name = "HT-Demucs separation model (Demucs)",
        notice = "Copyright (c) Meta Platforms, Inc. and affiliates",
        license = MIT,
        text = R.raw.license_demucs,
    ),
    OpenSourceComponent("ONNX Runtime", "Copyright (c) Microsoft Corporation", MIT, R.raw.license_onnxruntime),
    OpenSourceComponent(
        name = "AndroidX: Jetpack Compose, Media3, Room, WorkManager, Glance, DataStore",
        notice = "Copyright The Android Open Source Project",
        license = APACHE,
        text = R.raw.license_apache_2_0,
    ),
    OpenSourceComponent("Material Icons", "Copyright Google LLC", APACHE, R.raw.license_apache_2_0),
    OpenSourceComponent(
        name = "Kotlin and kotlinx.coroutines",
        notice = "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
        license = APACHE,
        text = R.raw.license_apache_2_0,
    ),
    OpenSourceComponent("Guava", "Copyright The Guava Authors", APACHE, R.raw.license_apache_2_0),
    OpenSourceComponent("Coil", "Copyright Coil Contributors", APACHE, R.raw.license_apache_2_0),
    OpenSourceComponent("Lottie for Android", "Copyright Airbnb, Inc.", APACHE, R.raw.license_apache_2_0),
)

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    var openIndex by rememberSaveable { mutableIntStateOf(-1) }
    val open = COMPONENTS.getOrNull(openIndex)
    BackHandler(enabled = backEnabled) { if (open != null) openIndex = -1 else onBack() }

    if (open != null) {
        LicenseText(open, onBack = { openIndex = -1 }, modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "back") { DetailBackButton(onBack = onBack) }
        item(key = "header") {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.licenses_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(items = COMPONENTS, key = { _, component -> component.name }) { index, component ->
            ListItem(
                headlineContent = { Text(component.name) },
                supportingContent = { Text("${component.notice}\n${component.license}") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable { openIndex = index },
            )
        }
    }
}

@Composable
private fun LicenseText(component: OpenSourceComponent, onBack: () -> Unit, modifier: Modifier) {
    val resources = LocalResources.current
    val text = remember(component.text) {
        resources.openRawResource(component.text).bufferedReader().use { it.readText() }.reflow()
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        DetailBackButton(onBack = onBack)
        Text(
            text = component.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = component.notice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

/** License files are wrapped at ~80 columns; joins those lines so paragraphs wrap to the screen instead. */
private fun String.reflow(): String =
    replace("\r\n", "\n")
        .split(Regex("\n\\s*\n"))
        .joinToString("\n\n") { paragraph -> paragraph.trim().replace(Regex("\\s+"), " ") }
