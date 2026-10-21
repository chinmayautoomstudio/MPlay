package com.autoomstudio.mp3studio.data.billing

import com.autoomstudio.mp3studio.data.plan.BillingRegion

/** Indian and international numbers as PayU needs them; the same rules as the Edge Functions. */
object PhoneNumber {
    private val MOBILE = Regex("^[6-9][0-9]{9}$")
    private val E164 = Regex("^\\+[1-9][0-9]{7,14}$")
    private val SEPARATORS = Regex("[\\s\\-()]")

    /** The 10-digit Indian number from what the user typed, or null. */
    fun normalize(input: String): String? {
        var digits = input.replace(SEPARATORS, "")
        digits = when {
            digits.startsWith("+91") -> digits.substring(3)
            digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            else -> digits
        }
        return digits.takeIf { MOBILE.matches(it) }
    }

    /** Indian mobile or E.164, for a USD checkout. */
    fun normalizeIntl(input: String): String? = normalize(input) ?: input.replace(SEPARATORS, "").takeIf { E164.matches(it) }

    fun normalize(input: String, region: BillingRegion): String? =
        if (region == BillingRegion.International) normalizeIntl(input) else normalize(input)
}

/** Our transaction IDs; anything else arriving in a return link is ignored. */
object TxnId {
    private val PATTERN = Regex("^[A-Za-z0-9]{1,25}$")

    fun parse(value: String?): String? = value?.takeIf { PATTERN.matches(it) }
}
