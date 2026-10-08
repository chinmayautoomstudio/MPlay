package com.autoomstudio.mp3studio.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Brightness5
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.settings.ThemeMode
import com.autoomstudio.mp3studio.ui.plans.PlanUiState
import com.autoomstudio.mp3studio.ui.plans.PlansViewModel
import com.autoomstudio.mp3studio.ui.theme.NeonBrush
import kotlin.math.ceil

/** The Profile tab: account, plan and the entry points to every settings page (PRD AU5, AU6). */
@Composable
fun ProfileScreen(
    themeMode: ThemeMode,
    onBack: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSeparation: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    plansViewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val plan by plansViewModel.state.collectAsStateWithLifecycle()
    var confirmingLogOut by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        account?.let { current ->
            IdentityRow(
                name = current.name,
                email = current.email,
                avatarUrl = current.avatarUrl,
                onEdit = onOpenEdit,
            )
        }

        SubscriptionCard(plan = plan, onClick = onOpenPlans)

        if (plan.isAdmin) {
            CategoryRow(
                icon = Icons.Outlined.AdminPanelSettings,
                title = stringResource(R.string.admin_title),
                subtitle = stringResource(R.string.admin_row_summary),
                onClick = onOpenAdmin,
            )
        }
        CategoryRow(
            icon = Icons.Rounded.Brightness5,
            title = stringResource(R.string.profile_appearance),
            subtitle = stringResource(
                when (themeMode) {
                    ThemeMode.System -> R.string.profile_theme_system
                    ThemeMode.Light -> R.string.settings_theme_light
                    ThemeMode.Dark -> R.string.settings_theme_dark
                },
            ),
            onClick = onOpenAppearance,
        )
        CategoryRow(
            icon = Icons.Rounded.MusicNote,
            title = stringResource(R.string.profile_library),
            subtitle = stringResource(R.string.profile_library_summary),
            onClick = onOpenLibrary,
        )
        CategoryRow(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.profile_separation),
            subtitle = stringResource(R.string.profile_separation_summary),
            onClick = onOpenSeparation,
        )
        CategoryRow(
            icon = Icons.Rounded.PlayArrow,
            title = stringResource(R.string.profile_playback),
            subtitle = stringResource(R.string.profile_playback_summary),
            onClick = onOpenPlayback,
        )
        CategoryRow(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.profile_about),
            subtitle = stringResource(R.string.profile_about_summary),
            onClick = onOpenAbout,
        )
        CategoryRow(
            icon = Icons.AutoMirrored.Outlined.Logout,
            title = stringResource(R.string.account_log_out),
            subtitle = null,
            onClick = { confirmingLogOut = true },
            accent = MaterialTheme.colorScheme.error,
            showChevron = false,
        )
        Spacer(Modifier.height(8.dp))
    }

    if (confirmingLogOut) {
        LogOutDialog(
            onConfirm = {
                confirmingLogOut = false
                accountViewModel.signOut()
            },
            onDismiss = { confirmingLogOut = false },
        )
    }
}

/** The account photo used as the Profile tab icon, ringed in the primary color while selected. */
@Composable
fun ProfileTabIcon(avatarUrl: String?, selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .padding(3.dp),
    ) {
        Avatar(avatarUrl, size = 24.dp)
    }
}

@Composable
private fun IdentityRow(name: String, email: String, avatarUrl: String?, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .border(BorderStroke(2.dp, NeonBrush), CircleShape)
                .padding(4.dp),
        ) {
            Avatar(avatarUrl, size = 56.dp)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onEdit,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.profile_edit),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionCard(plan: PlanUiState, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(primary.copy(alpha = 0.10f), primary.copy(alpha = 0.26f))),
                )
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val back = Path().apply {
                        moveTo(w * 0.45f, h)
                        cubicTo(w * 0.62f, h * 0.70f, w * 0.80f, h * 0.95f, w, h * 0.35f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(back, Brush.horizontalGradient(listOf(Color.Transparent, tertiary.copy(alpha = 0.35f))))
                    val front = Path().apply {
                        moveTo(w * 0.55f, h)
                        cubicTo(w * 0.72f, h * 0.80f, w * 0.86f, h * 1.0f, w, h * 0.60f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(front, Brush.horizontalGradient(listOf(Color.Transparent, primary.copy(alpha = 0.45f))))
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonBrush),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    stringResource(R.string.profile_subscription_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val appName = stringResource(R.string.profile_subscription_app)
                val planName = planName(plan.plan)
                Text(
                    buildAnnotatedString {
                        append(appName)
                        append(' ')
                        withStyle(SpanStyle(color = primary)) { append(planName) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subscriptionDetail(plan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun planName(plan: Plan): String = stringResource(
    when (plan) {
        Plan.Free -> R.string.plan_free
        Plan.Trial -> R.string.plan_trial
        Plan.Pro -> R.string.plan_pro
    },
)

@Composable
private fun subscriptionDetail(plan: PlanUiState): String {
    if (plan.checkedAt == null) return stringResource(R.string.plan_checking)
    plan.trialDaysLeft?.let { return pluralStringResource(R.plurals.profile_days_left, it, it) }
    if (plan.plan == Plan.Pro) {
        val endsAt = plan.subscriptionExpiresAt ?: plan.subscriptionNextBillingAt
            ?: return stringResource(R.string.profile_subscription_active)
        val days = ceil((endsAt - System.currentTimeMillis()) / DAY_MILLIS).toInt().coerceAtLeast(0)
        return pluralStringResource(R.plurals.profile_days_left, days, days)
    }
    return stringResource(R.string.profile_subscription_free_hint)
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0

@Composable
private fun CategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    showChevron: Boolean = true,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (showChevron) MaterialTheme.colorScheme.onSurface else accent,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
