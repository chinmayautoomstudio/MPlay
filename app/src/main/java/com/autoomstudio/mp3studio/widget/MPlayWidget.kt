package com.autoomstudio.mp3studio.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.util.UnstableApi
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.MainActivity
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.playback.SignedInMediaButtonReceiver
import com.autoomstudio.mp3studio.ui.theme.NeonDarkColors
import com.autoomstudio.mp3studio.ui.theme.NeonLightColors

class MPlayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MPlayApp).container
        val store = container.widgetStateStore
        val initial = store.current()
        val auth = container.authRepository
        auth.awaitReady()
        provideContent {
            val state by store.state.collectAsState(initial)
            val artwork by store.artwork.collectAsState(null)
            val authState by auth.state.collectAsState()
            GlanceTheme(colors = WidgetColors) {
                if (authState is AuthState.SignedIn) {
                    WidgetContent(state, if (state.isIdle) null else artwork)
                } else {
                    SignedOutContent()
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 48.dp)
        val MEDIUM = DpSize(180.dp, 48.dp)
        val WIDE = DpSize(250.dp, 48.dp)
    }
}

private val WidgetColors = ColorProviders(light = NeonLightColors, dark = NeonDarkColors)

@Composable
private fun WidgetContent(state: WidgetState, artwork: Bitmap?) {
    val context = LocalContext.current
    val width = LocalSize.current.width
    val wide = width >= MPlayWidget.WIDE.width
    val showText = width >= MPlayWidget.MEDIUM.width
    val openPlayer = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, !state.isIdle)
    val title = if (state.isIdle) context.getString(R.string.widget_idle_title) else state.title
    val subtitle = if (state.isIdle) context.getString(R.string.widget_idle_message) else state.artist

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp)
            .clickable(actionStartActivity(openPlayer)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            Artwork(artwork, contentDescription = context.getString(R.string.album_art_description, title))
            Spacer(GlanceModifier.width(10.dp))
        }
        if (showText) {
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
        // Without text the buttons share the width, since three full-size ones don't fit the smallest widget.
        val buttonModifier = if (showText) GlanceModifier.size(48.dp) else GlanceModifier.defaultWeight().height(48.dp)
        ControlButton(
            icon = R.drawable.ic_widget_previous,
            description = context.getString(R.string.action_previous),
            onClick = actionRunCallback<SkipActionCallback>(
                actionParametersOf(SkipActionCallback.KEY_FORWARD to false),
            ),
            modifier = buttonModifier,
        )
        ControlButton(
            icon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = context.getString(if (state.isPlaying) R.string.action_pause else R.string.action_play),
            onClick = actionSendBroadcast(mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)),
            modifier = buttonModifier,
            prominent = true,
        )
        ControlButton(
            icon = R.drawable.ic_widget_next,
            description = context.getString(R.string.action_next),
            onClick = actionRunCallback<SkipActionCallback>(
                actionParametersOf(SkipActionCallback.KEY_FORWARD to true),
            ),
            modifier = buttonModifier,
        )
    }
}

/** No controls while signed out (PRD AU4); a tap opens the app at the sign-in screen. */
@Composable
private fun SignedOutContent() {
    val context = LocalContext.current
    val openApp = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity(openApp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_music_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(24.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = context.getString(R.string.widget_signed_out),
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun Artwork(artwork: Bitmap?, contentDescription: String) {
    Box(
        modifier = GlanceModifier
            .size(ARTWORK_SIZE)
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: Int,
    description: String,
    onClick: Action,
    modifier: GlanceModifier,
    prominent: Boolean = false,
) {
    Box(
        modifier = modifier
            .cornerRadius(24.dp)
            .then(if (prominent) GlanceModifier.background(GlanceTheme.colors.primary) else GlanceModifier)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(
                if (prominent) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
            ),
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

/**
 * Play/pause goes through Media3's receiver so it can start the service in the foreground and
 * resume the last session when the app isn't running.
 */
@OptIn(UnstableApi::class)
private fun mediaButtonIntent(context: Context, keyCode: Int): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON)
        .setClass(context, SignedInMediaButtonReceiver::class.java)
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

private val ARTWORK_SIZE = 48.dp
