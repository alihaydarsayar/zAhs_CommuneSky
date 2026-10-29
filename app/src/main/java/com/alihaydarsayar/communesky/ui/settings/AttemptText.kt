package com.alihaydarsayar.communesky.ui.settings

import android.content.Context
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.work.AttemptKind
import com.alihaydarsayar.communesky.work.AttemptOutcome

/** Bir bildirim denemesinin sonucunu kullanıcının dilinde tek cümleyle anlatır. */
object AttemptText {

    fun of(context: Context, kind: AttemptKind, outcome: AttemptOutcome, detail: String? = null): String {
        val reason = when (outcome) {
            AttemptOutcome.Sent -> return context.getString(if (kind == AttemptKind.Test) R.string.attempt_test_sent else R.string.attempt_sent)
            AttemptOutcome.SentOldData -> return context.getString(R.string.attempt_sent_old)
            AttemptOutcome.NotVisible -> return context.getString(R.string.attempt_not_visible)
            AttemptOutcome.Fallback -> return context.getString(R.string.attempt_fallback)
            AttemptOutcome.StaleNotice -> return context.getString(R.string.attempt_stale_notice)
            AttemptOutcome.Missed -> return context.getString(R.string.attempt_missed)
            AttemptOutcome.Error -> return context.getString(R.string.attempt_error, detail ?: "?")
            AttemptOutcome.NoPermission -> R.string.attempt_reason_no_permission
            AttemptOutcome.AppOff -> R.string.attempt_reason_app_off
            AttemptOutcome.ChannelOff -> R.string.attempt_reason_channel_off
            AttemptOutcome.NoData -> R.string.attempt_reason_no_data
            AttemptOutcome.DataTooOld -> R.string.attempt_reason_data_too_old
            AttemptOutcome.Failed -> R.string.attempt_reason_failed
        }
        val prefix = if (kind == AttemptKind.Test || outcome == AttemptOutcome.Failed) R.string.attempt_not_shown else R.string.attempt_skipped
        return context.getString(prefix, context.getString(reason))
    }
}
