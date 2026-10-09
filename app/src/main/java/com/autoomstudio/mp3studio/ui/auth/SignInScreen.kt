package com.autoomstudio.mp3studio.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.account.AuthError
import com.autoomstudio.mp3studio.ui.components.pressBounce
import com.autoomstudio.mp3studio.ui.components.rememberAnimationsEnabled
import com.autoomstudio.mp3studio.ui.theme.EmphasizedDecelerateEasing
import com.autoomstudio.mp3studio.ui.theme.MotionShort
import com.autoomstudio.mp3studio.ui.theme.WordmarkStyle

private val StudioBrush = Brush.linearGradient(listOf(SignInColors.Blue, SignInColors.Violet, SignInColors.Magenta))

/** The only screen a signed-out user can reach (PRD AU1). There is deliberately no way to skip it. */
@Composable
fun SignInScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { snackbar.showSnackbar(context.getString(it.messageRes())) }
    }
    Box(modifier.fillMaxSize()) {
        SignInContent(
            signingIn = signingIn,
            animated = rememberAnimationsEnabled(),
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
    animated: Boolean,
    onSignIn: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    var entered by rememberSaveable { mutableStateOf(!animated) }
    LaunchedEffect(Unit) { entered = true }
    SignInBackdrop(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val heroHeight = (maxHeight * 0.36f).coerceIn(180.dp, 360.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp).padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlowingLogo(
                        animated = animated,
                        modifier = Modifier.entrance(entered, delayMillis = 0, offsetY = 0.dp, fromScale = 0.8f),
                    )
                    Spacer(Modifier.height(20.dp))
                    Title(Modifier.entrance(entered, delayMillis = 160, offsetY = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.signin_tagline),
                        style = TextStyle(fontSize = 17.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Normal),
                        color = SignInColors.Lavender,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.entrance(entered, delayMillis = 240, offsetY = 16.dp),
                    )
                }
                SignInHero(
                    animated = animated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .entrance(entered, delayMillis = 320, offsetY = 0.dp),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GoogleSignInButton(
                        signingIn = signingIn,
                        animated = animated,
                        onClick = onSignIn,
                        modifier = Modifier.entrance(entered, delayMillis = 440, offsetY = 24.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    LegalText(
                        onOpenTerms = onOpenTerms,
                        onOpenPrivacy = onOpenPrivacy,
                        modifier = Modifier.entrance(entered, delayMillis = 560, offsetY = 0.dp),
                    )
                }
            }
        }
    }
}

/** Fades in (and optionally slides up or scales in) once [visible] turns true, after [delayMillis]. */
@Composable
private fun Modifier.entrance(visible: Boolean, delayMillis: Int, offsetY: Dp, fromScale: Float = 1f): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = delayMillis, easing = EmphasizedDecelerateEasing),
        label = "signInEntrance",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * offsetY.toPx()
        val scale = fromScale + (1f - fromScale) * progress
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun rememberBreathing(animated: Boolean, periodMillis: Int): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0.5f) }
    return rememberInfiniteTransition(label = "signInBreathing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing",
    )
}

@Composable
private fun GlowingLogo(animated: Boolean, modifier: Modifier = Modifier) {
    val float = rememberBreathing(animated, periodMillis = 1600)
    val glow = rememberBreathing(animated, periodMillis = 1300)
    Box(
        modifier = modifier
            .size(150.dp)
            .graphicsLayer { translationY = (float.value - 0.5f) * 12.dp.toPx() }
            .drawBehind {
                val pulse = 0.6f + 0.4f * glow.value
                val radius = size.minDimension * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to SignInColors.Violet.copy(alpha = 0.55f * pulse),
                        0.45f to SignInColors.Blue.copy(alpha = 0.22f * pulse),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mp3studio_logo),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
    }
}

@Composable
private fun Title(modifier: Modifier = Modifier) {
    val text = remember {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append("MP3 ") }
            withStyle(SpanStyle(brush = StudioBrush)) { append("Studio") }
        }
    }
    Text(
        text = text,
        style = WordmarkStyle.copy(fontSize = 42.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
        modifier = modifier,
    )
}

@Composable
private fun GoogleSignInButton(signingIn: Boolean, animated: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val glow = rememberBreathing(animated, periodMillis = 2200)
    val label = stringResource(R.string.signin_google)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressBounce(interaction, pressedScale = 0.96f)
            .drawBehind {
                val strength = 0.7f + 0.3f * glow.value
                val radius = size.height / 2
                for (i in 1..6) {
                    val spread = i * 3.dp.toPx()
                    drawRoundRect(
                        color = SignInColors.Violet.copy(alpha = 0.07f * strength * (7 - i) / 6f),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(radius + spread),
                    )
                }
            }
            .clip(CircleShape)
            .background(SignInColors.ButtonFill)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = SignInColors.Violet),
                enabled = !signingIn,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = signingIn,
            transitionSpec = { fadeIn(tween(MotionShort)) togetherWith fadeOut(tween(MotionShort)) },
            label = "signInButton",
        ) { busy ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = SignInColors.Violet,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = if (busy) stringResource(R.string.signin_in_progress) else label,
                    color = SignInColors.ButtonText,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun LegalText(onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit, modifier: Modifier = Modifier) {
    val terms = stringResource(R.string.signin_terms)
    val privacy = stringResource(R.string.signin_privacy)
    val links = stringResource(R.string.signin_legal_links, terms, privacy)
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = SignInColors.Link, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
    )
    val text = remember(links, terms, privacy) {
        buildAnnotatedString {
            append(links)
            val termsStart = links.indexOf(terms)
            if (termsStart >= 0) {
                addLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpenTerms() }, termsStart, termsStart + terms.length)
            }
            val privacyStart = links.indexOf(privacy)
            if (privacyStart >= 0) {
                addLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpenPrivacy() }, privacyStart, privacyStart + privacy.length)
            }
        }
    }
    val style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.signin_legal_prefix), style = style, color = SignInColors.Lavender.copy(alpha = 0.8f))
        Text(text, style = style, color = SignInColors.Lavender.copy(alpha = 0.8f))
    }
}

@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.NoGoogleAccount -> R.string.auth_error_no_account
    AuthError.AccountUnavailable -> R.string.auth_error_account_unavailable
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

@Preview(widthDp = 393, heightDp = 852)
@Composable
private fun SignInContentPreview() {
    SignInContent(signingIn = false, animated = false, onSignIn = {}, onOpenTerms = {}, onOpenPrivacy = {})
}

@Preview(widthDp = 393, heightDp = 852)
@Composable
private fun SignInContentSigningInPreview() {
    SignInContent(signingIn = true, animated = false, onSignIn = {}, onOpenTerms = {}, onOpenPrivacy = {})
}
