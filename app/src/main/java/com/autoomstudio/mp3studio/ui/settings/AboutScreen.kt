package com.autoomstudio.mp3studio.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.BuildConfig
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.ui.library.DetailBackButton

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val context = LocalContext.current
    // The in-app theme setting does not change the resource night mode, so the logo follows the actual colors.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val website = stringResource(R.string.about_website)
    val email = stringResource(R.string.about_email)
    val privacyUrl = stringResource(R.string.about_privacy_url)
    val termsUrl = stringResource(R.string.about_terms_url)
    val emailSubject = stringResource(R.string.about_email_subject, BuildConfig.VERSION_NAME)
    val noBrowser = stringResource(R.string.about_no_browser)
    val noMailApp = stringResource(R.string.about_no_mail_app)

    Column(modifier.verticalScroll(rememberScrollState())) {
        DetailBackButton(onBack = onBack)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Image(
                painter = painterResource(if (darkTheme) R.drawable.ic_mplay_logo_dark else R.drawable.ic_mplay_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AboutHeader(stringResource(R.string.about_made_by))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AutoomStudioLogo(darkTheme)
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        LinkItem(
            icon = Icons.Outlined.Language,
            label = stringResource(R.string.about_website_label),
            value = stringResource(R.string.about_website_display),
            onClick = { context.openOrMessage(Intent(Intent.ACTION_VIEW, Uri.parse(website)), noBrowser, onMessage) },
            onLongClick = { context.copy(website, onMessage) },
        )
        LinkItem(
            icon = Icons.Outlined.Email,
            label = stringResource(R.string.about_email_label),
            value = email,
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                }
                context.openOrMessage(intent, noMailApp, onMessage)
            },
            onLongClick = { context.copy(email, onMessage) },
        )

        AboutHeader(stringResource(R.string.about_privacy_title))
        Text(
            text = stringResource(R.string.about_privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LinkItem(
            icon = Icons.Outlined.Policy,
            label = stringResource(R.string.about_privacy_policy),
            value = null,
            onClick = { context.openOrMessage(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)), noBrowser, onMessage) },
            onLongClick = { context.copy(privacyUrl, onMessage) },
        )
        LinkItem(
            icon = Icons.Outlined.Description,
            label = stringResource(R.string.about_terms),
            value = null,
            onClick = { context.openOrMessage(Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl)), noBrowser, onMessage) },
            onLongClick = { context.copy(termsUrl, onMessage) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_licenses)) },
            supportingContent = { Text(stringResource(R.string.settings_licenses_summary)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(onClick = onOpenLicenses),
        )

        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        )
    }
}

/** The wordmark's dark-brown lettering is hard to read on dark backgrounds, so dark theme pairs the icon with text. */
@Composable
private fun AutoomStudioLogo(darkTheme: Boolean) {
    val name = stringResource(R.string.about_company_name)
    if (!darkTheme) {
        Image(
            painter = painterResource(R.drawable.autoom_studio_wordmark),
            contentDescription = name,
            modifier = Modifier.height(48.dp),
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.autoom_studio_icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AboutHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun LinkItem(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        supportingContent = value?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

private fun Context.openOrMessage(intent: Intent, missingAppMessage: String, onMessage: (String) -> Unit) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onMessage(missingAppMessage)
    }
}

private fun Context.copy(value: String, onMessage: (String) -> Unit) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(value, value))
    // Android 13 and later show their own confirmation when the clipboard changes.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onMessage(getString(R.string.about_copied, value))
}
