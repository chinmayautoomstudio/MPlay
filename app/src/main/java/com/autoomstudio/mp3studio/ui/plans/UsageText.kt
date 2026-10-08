package com.autoomstudio.mp3studio.ui.plans

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.SeparatorUsage

/** "7/10 songs used this week. 3 remaining. Resets Mon, 12 Oct." (PRD US6). */
@Composable
fun usageSummary(usage: SeparatorUsage): String = stringResource(
    R.string.usage_summary,
    usage.limit - usage.remaining,
    usage.limit,
    usage.remaining,
    resetDay(usage.resetsAt),
)

/** The weekly reset in local time, for example "Mon, 12 Oct". */
@Composable
fun resetDay(resetsAt: Long): String = DateUtils.formatDateTime(
    LocalContext.current,
    resetsAt,
    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or
        DateUtils.FORMAT_ABBREV_ALL,
)
