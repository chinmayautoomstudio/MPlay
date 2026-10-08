package com.autoomstudio.mp3studio.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
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
import com.autoomstudio.mp3studio.ui.library.DetailBackButton

/** Settings > Admin (PRD section 6.7). Only offered to Admins; the server refuses everyone else. */
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
        AdminPage.Home -> AdminHome(viewModel, goBack, modifier)
        is AdminPage.User -> AdminUserScreen(viewModel, current.id, goBack, modifier)
        AdminPage.Admins -> AdminAdminsScreen(viewModel, goBack, modifier)
        AdminPage.Usage -> AdminUsageScreen(viewModel, goBack, modifier)
        AdminPage.Audit -> AdminAuditScreen(viewModel, goBack, modifier)
    }
}

@Composable
private fun AdminHome(viewModel: AdminViewModel, onBack: () -> Unit, modifier: Modifier) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me = viewModel.me

    LazyColumn(modifier) {
        item { DetailBackButton(onBack = onBack) }
        item {
            Text(
                stringResource(R.string.admin_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LoadableContent(overview, onRetry = viewModel::reload) { OverviewCard(it) }
            }
        }
        item {
            NavRow(Icons.Outlined.AdminPanelSettings, stringResource(R.string.admin_overview_admins),
                stringResource(R.string.admin_open_admins_summary)) { viewModel.open(AdminPage.Admins) }
        }
        item {
            NavRow(Icons.Outlined.GraphicEq, stringResource(R.string.admin_open_usage),
                stringResource(R.string.admin_open_usage_summary)) { viewModel.open(AdminPage.Usage) }
        }
        item {
            NavRow(Icons.Outlined.History, stringResource(R.string.admin_open_audit),
                stringResource(R.string.admin_open_audit_summary)) { viewModel.open(AdminPage.Audit) }
        }
        item {
            OutlinedTextField(
                value = users.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.admin_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                UserFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = users.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filterName(filter)) },
                    )
                }
            }
        }
        item {
            Text(
                pluralStringResource(R.plurals.admin_users_count, users.total, users.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(users.users, key = { it.id }) { user ->
            UserRow(user, isMe = user.id == me) { viewModel.open(AdminPage.User(user.id)) }
        }
        item {
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
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: AdminOverview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Count(stringResource(R.string.admin_overview_users), overview.users, Modifier.weight(1f))
                Count(stringResource(R.string.plan_pro), overview.pro, Modifier.weight(1f))
                Count(stringResource(R.string.plan_trial), overview.trial, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Count(stringResource(R.string.plan_free), overview.free, Modifier.weight(1f))
                Count(stringResource(R.string.admin_overview_disabled), overview.disabled, Modifier.weight(1f))
                Count(stringResource(R.string.admin_overview_admins), overview.admins, Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.admin_week_summary, overview.completed, overview.reserved, overview.denied),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Count(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun UserRow(user: AdminUserRow, isMe: Boolean, onClick: () -> Unit) {
    val name = user.name?.takeIf { it.isNotBlank() } ?: user.email ?: stringResource(R.string.admin_no_name)
    ListItem(
        headlineContent = {
            Text(
                if (isMe) "$name ${stringResource(R.string.admin_you)}" else name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                user.email?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(stringResource(R.string.admin_user_row, planName(user.plan), user.usedThisWeek))
            }
        },
        trailingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (user.role == "admin") Badge(stringResource(R.string.admin_badge_admin))
                if (user.disabled) Badge(stringResource(R.string.admin_badge_disabled))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

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
