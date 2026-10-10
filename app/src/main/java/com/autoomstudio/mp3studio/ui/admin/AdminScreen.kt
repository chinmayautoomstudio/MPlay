package com.autoomstudio.mp3studio.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge as M3Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AdminOverview
import com.autoomstudio.mp3studio.data.admin.AdminUserRow
import com.autoomstudio.mp3studio.data.admin.UserFilter
import com.autoomstudio.mp3studio.ui.components.MPlayWordmark

/** Profile > Admin (PRD section 6.7). Only offered to Admins; the server refuses everyone else. */
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: AdminViewModel = viewModel(factory = AdminViewModel.Factory),
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        if (!entered) {
            entered = true
            viewModel.enter()
        }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { onMessage(it.text(resources)) } }
    LaunchedEffect(closed) { if (closed) onBack() }
    val goBack = { if (!viewModel.back()) onBack() }
    BackHandler(enabled = backEnabled, onBack = goBack)

    when (val current = page) {
        AdminPage.Home -> AdminHome(viewModel, modifier)
        is AdminPage.User -> AdminUserScreen(viewModel, current.id, goBack, modifier)
        AdminPage.Admins -> AdminAdminsScreen(viewModel, goBack, modifier)
        AdminPage.Usage -> AdminUsageScreen(viewModel, goBack, modifier)
        AdminPage.Audit -> AdminAuditScreen(viewModel, goBack, modifier)
        AdminPage.Activity -> AdminActivityScreen(viewModel, goBack, modifier)
        AdminPage.Payments -> AdminPaymentsScreen(viewModel, goBack, modifier)
    }
}

@Composable
private fun AdminHome(viewModel: AdminViewModel, modifier: Modifier) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val me = viewModel.me
    var confirm by remember { mutableStateOf<RowConfirm?>(null) }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(unread, onOpenActivity = { viewModel.open(AdminPage.Activity) }) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.admin_dashboard_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.admin_dashboard_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                LoadableContent(overview, onRetry = viewModel::reload) {
                    StatsCard(it, onOpenUsage = { viewModel.open(AdminPage.Usage) })
                }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(IntrinsicSize.Max),
            ) {
                ShortcutCard(
                    rememberVectorPainter(Icons.Outlined.Groups),
                    stringResource(R.string.admin_overview_admins),
                    stringResource(R.string.admin_open_admins_summary),
                    Modifier.weight(1f),
                ) { viewModel.open(AdminPage.Admins) }
                ShortcutCard(
                    rememberVectorPainter(Icons.Outlined.GraphicEq),
                    stringResource(R.string.admin_open_usage),
                    stringResource(R.string.admin_open_usage_summary),
                    Modifier.weight(1f),
                ) { viewModel.open(AdminPage.Usage) }
                ShortcutCard(
                    rememberVectorPainter(Icons.Outlined.Schedule),
                    stringResource(R.string.admin_open_audit),
                    stringResource(R.string.admin_open_audit_summary),
                    Modifier.weight(1f),
                ) { viewModel.open(AdminPage.Audit) }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(IntrinsicSize.Max),
            ) {
                ShortcutCard(
                    rememberVectorPainter(Icons.Outlined.Payments),
                    stringResource(R.string.admin_open_payments),
                    stringResource(R.string.admin_open_payments_summary),
                    Modifier.weight(1f),
                ) { viewModel.open(AdminPage.Payments) }
            }
        }
        item {
            AdminCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                UsersHeader(users.total, users.query, viewModel::setQuery)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    UserFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = users.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filterName(filter)) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                users.users.forEachIndexed { index, user ->
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(start = 72.dp, end = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    AdminUserRowItem(
                        user,
                        isMe = user.id == me,
                        busy = busy,
                        onOpen = { viewModel.open(AdminPage.User(user.id)) },
                        onEnable = { viewModel.setDisabled(user.id, false) },
                        onConfirm = { confirm = it },
                    )
                }
                when {
                    users.error != null -> ErrorBlock(users.error!!, onRetry = viewModel::reload)
                    users.loading -> Row(Modifier.padding(16.dp)) { CircularProgressIndicator() }
                    users.users.isEmpty() -> Text(
                        stringResource(R.string.admin_no_users),
                        modifier = Modifier.padding(16.dp),
                    )
                    users.hasMore -> OutlinedButton(
                        onClick = viewModel::loadMoreUsers,
                        modifier = Modifier.padding(16.dp),
                    ) { Text(stringResource(R.string.admin_load_more)) }
                    else -> Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    confirm?.let { which ->
        val label = which.user.email ?: displayName(which.user)
        val (title, message) = when (which.action) {
            RowAction.MakeAdmin -> R.string.admin_make_admin_title to R.string.admin_make_admin_message
            RowAction.RemoveAdmin -> R.string.admin_remove_admin_title to R.string.admin_remove_admin_message
            RowAction.Disable -> R.string.admin_disable_title to R.string.admin_disable_message
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(message, label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (which.action) {
                        RowAction.MakeAdmin -> viewModel.setRole(which.user.id, label, admin = true)
                        RowAction.RemoveAdmin -> viewModel.setRole(which.user.id, label, admin = false)
                        RowAction.Disable -> viewModel.setDisabled(which.user.id, true)
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

private enum class RowAction { MakeAdmin, RemoveAdmin, Disable }

private data class RowConfirm(val user: AdminUserRow, val action: RowAction)

@Composable
private fun Header(unread: Int, onOpenActivity: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
    ) {
        MPlayWordmark()
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_crown),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.admin_badge_admin),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onOpenActivity,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            BadgedBox(badge = { if (unread > 0) M3Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.admin_notifications))
            }
        }
    }
}

@Composable
private fun StatsCard(overview: AdminOverview, onOpenUsage: () -> Unit) {
    AdminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatTile(rememberVectorPainter(Icons.Outlined.Groups), AdminColors.Blue,
                    overview.users, stringResource(R.string.admin_stat_total), Modifier.weight(1f))
                StatTile(painterResource(R.drawable.ic_crown), AdminColors.Amber,
                    overview.pro, stringResource(R.string.admin_stat_pro), Modifier.weight(1f))
                StatTile(rememberVectorPainter(Icons.Outlined.CardGiftcard), AdminColors.Blue,
                    overview.trial, stringResource(R.string.admin_stat_trial), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile(rememberVectorPainter(Icons.Outlined.Person), AdminColors.Teal,
                    overview.free, stringResource(R.string.admin_stat_free), Modifier.weight(1f))
                StatTile(rememberVectorPainter(Icons.Outlined.Block), AdminColors.Red,
                    overview.disabled, stringResource(R.string.admin_overview_disabled), Modifier.weight(1f))
                StatTile(rememberVectorPainter(Icons.Outlined.Shield), AdminColors.Violet,
                    overview.admins, stringResource(R.string.admin_overview_admins), Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenUsage)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            IconTile(rememberVectorPainter(Icons.AutoMirrored.Outlined.TrendingUp), AdminColors.Green, size = 32.dp)
            Text(
                stringResource(R.string.admin_week_summary, overview.completed, overview.reserved, overview.denied),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(icon: Painter, tint: Color, value: Int, label: String, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconTile(icon, tint, size = 36.dp)
        Column(Modifier.padding(start = 8.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShortcutCard(icon: Painter, title: String, summary: String, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    AdminCard(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        onClick = onClick,
        brush = Brush.linearGradient(listOf(primary.copy(alpha = 0.14f), primary.copy(alpha = 0.03f))),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconTile(icon, AdminColors.Violet, size = 32.dp)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun UsersHeader(total: Int, query: String, onQuery: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column {
            Text(stringResource(R.string.admin_overview_users), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(
                pluralStringResource(R.plurals.admin_users_count, total, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        val textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.admin_search),
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                }
            },
        )
    }
}

@Composable
private fun AdminUserRowItem(
    user: AdminUserRow,
    isMe: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onEnable: () -> Unit,
    onConfirm: (RowConfirm) -> Unit,
) {
    val name = displayName(user)
    val isAdmin = user.role == "admin"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        InitialsAvatar(name, enabled = !user.disabled)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMe) "$name ${stringResource(R.string.admin_you)}" else name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isAdmin) {
                    Icon(
                        painterResource(R.drawable.ic_crown),
                        contentDescription = null,
                        tint = AdminColors.Amber,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp),
                    )
                }
            }
            user.email?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                PlanChip(user.plan)
                Text(
                    " • " + stringResource(R.string.admin_this_week, user.usedThisWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isAdmin) Badge(stringResource(R.string.admin_badge_admin))
            if (user.disabled) Badge(stringResource(R.string.admin_badge_disabled))
        }
        RowMenu(user, isMe, isAdmin, busy, onOpen, onEnable, onConfirm)
    }
}

@Composable
private fun RowMenu(
    user: AdminUserRow,
    isMe: Boolean,
    isAdmin: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onEnable: () -> Unit,
    onConfirm: (RowConfirm) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.admin_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_view_details)) },
                onClick = {
                    open = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (isAdmin) R.string.admin_remove_admin else R.string.admin_make_admin)) },
                enabled = !busy,
                onClick = {
                    open = false
                    onConfirm(RowConfirm(user, if (isAdmin) RowAction.RemoveAdmin else RowAction.MakeAdmin))
                },
            )
            if (!isMe) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (user.disabled) R.string.admin_enable else R.string.admin_disable)) },
                    enabled = !busy,
                    onClick = {
                        open = false
                        if (user.disabled) {
                            onEnable()
                        } else {
                            onConfirm(RowConfirm(user, RowAction.Disable))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String, enabled: Boolean) {
    val palette = listOf(AdminColors.Blue, AdminColors.Violet, AdminColors.Teal, AdminColors.Amber, AdminColors.Red)
    val tint = palette[Math.floorMod(name.hashCode(), palette.size)]
    Box(Modifier.size(44.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(name), style = MaterialTheme.typography.titleSmall, color = tint, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (enabled) AdminColors.Green else Color.Gray),
        )
    }
}

@Composable
private fun PlanChip(plan: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Text(
            planName(plan),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun displayName(user: AdminUserRow): String =
    user.name?.takeIf { it.isNotBlank() } ?: user.email ?: stringResource(R.string.admin_no_name)

private fun initials(name: String): String =
    name.substringBefore('@')
        .split(' ', '.', '_', '-')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

@Composable
internal fun Badge(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun filterName(filter: UserFilter): String = stringResource(
    when (filter) {
        UserFilter.All -> R.string.admin_filter_all
        UserFilter.Pro -> R.string.plan_pro
        UserFilter.Trial -> R.string.plan_trial
        UserFilter.Free -> R.string.plan_free
        UserFilter.Disabled -> R.string.admin_filter_disabled
        UserFilter.Admins -> R.string.admin_filter_admins
    },
)
