package com.autoomstudio.mp3studio.ui.plans

import android.net.Uri
import com.autoomstudio.mp3studio.BuildConfig
import com.autoomstudio.mp3studio.data.billing.TxnId

/**
 * `https://<pay.returnHost>/pay/return?result=…&txn=…`, which the `payu-return` Edge Function redirects to after PayU.
 * The `result` parameter is ignored: only the server's check with PayU decides what happened.
 */
object PaymentReturnLink {
    const val PATH = "/pay/return"

    /** The transaction ID from the link ("" when it has none or a malformed one), or null when it isn't a return link. */
    fun parse(uri: Uri?): String? =
        uri?.let { parse(it.scheme, it.host, it.path, it.getQueryParameter("txn"), BuildConfig.PAY_RETURN_HOST) }

    fun parse(scheme: String?, host: String?, path: String?, txn: String?, expectedHost: String): String? {
        if (scheme != "https" || !host.equals(expectedHost, ignoreCase = true)) return null
        if (path == null || !path.trimEnd('/').equals(PATH, ignoreCase = false)) return null
        return TxnId.parse(txn).orEmpty()
    }
}
