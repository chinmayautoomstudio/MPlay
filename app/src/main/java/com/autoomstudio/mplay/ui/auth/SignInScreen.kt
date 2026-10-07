package com.autoomstudio.mplay.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.account.AuthError
import com.autoomstudio.mplay.ui.components.MPlayWordmark
import com.autoomstudio.mplay.ui.theme.NeonBrush
import com.autoomstudio.mplay.ui.theme.WordmarkStyle

/** The only screen a signed-out user can reach (PRD AU1). There is deliberately no way to skip it. */
@Composable
fun SignInScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { snackbar.showSnackbar(context.getString(it.messageRes())) }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SignInContent(
            signingIn = signingIn,
            onSignIn = { viewModel.signInWithGoogle(context) },
            onOpenTerms = { context.openUrl(context.getString(R.string.about_terms_url)) },
            onOpenPrivacy = { context.openUrl(context.getString(R.string.about_privacy_url)) },
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SignInContent(
    signingIn: Boolean,
    onSignIn: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MPlayWordmark(style = WordmarkStyle.copy(fontSize = 40.sp, lineHeight = 48.sp))
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(NeonBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = Color.White,
            )
        }
        Text(
            text = stringResource(R.string.signin_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.signin_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSignIn,
            enabled = !signingIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (signingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.signin_in_progress))
            } else {
                Text(stringResource(R.string.signin_google))
            }
        }
        Text(
            text = stringResource(R.string.signin_legal),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.signin_terms)) }
            TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.signin_privacy)) }
        }
    }
}

@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.NoGoogleAccount -> R.string.auth_error_no_account
    AuthError.PlayServicesUnavailable -> R.string.auth_error_play_services
    AuthError.Network -> R.string.auth_error_network
    AuthError.AccountDisabled -> R.string.auth_error_disabled
    AuthError.SessionRevoked -> R.string.auth_error_session
    AuthError.NotConfigured -> R.string.auth_error_not_configured
    AuthError.Cancelled, AuthError.Unknown -> R.string.auth_error_unknown
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser; the links are also on the About screen after sign-in.
    }
}
