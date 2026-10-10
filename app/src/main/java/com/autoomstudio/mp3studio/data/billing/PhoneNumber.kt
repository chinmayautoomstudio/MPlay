package com.autoomstudio.mp3studio.data.billing

/** Indian mobile numbers as PayU needs them; the same rules as `normalizePhone` in the Edge Functions. */
object PhoneNumber {
    private val MOBILE = Regex("^[6-9][0-9]{9}$")
    private val SEPARATORS = Regex("[\\s\\-()]")

    /** The 10-digit number from what the user typed (spaces, dashes, +91, 91 or a leading 0 allowed), or null. */
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
}

/** Our transaction IDs; anything else arriving in a return link is ignored. */
object TxnId {
    private val PATTERN = Regex("^[A-Za-z0-9]{1,25}$")

    fun parse(value: String?): String? = value?.takeIf { PATTERN.matches(it) }
}
