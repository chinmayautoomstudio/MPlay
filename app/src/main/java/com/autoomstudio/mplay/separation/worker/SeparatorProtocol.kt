package com.autoomstudio.mplay.separation.worker

/** Messages between [SeparatorClient] in the main process and [SeparatorService] in `:separator`. */
internal object SeparatorProtocol {
    /** Client to service. Data: [KEY_URI], [KEY_VOCALS], [KEY_INSTRUMENTAL]; replyTo receives the rest. */
    const val MSG_SEPARATE = 1

    /** Client to service. */
    const val MSG_CANCEL = 2

    /** Service to client. Data: [KEY_FRACTION], [KEY_REMAINING_MS] (absent until known or while cooling), [KEY_COOLING]. */
    const val MSG_PROGRESS = 3

    /** Service to client. */
    const val MSG_DONE = 4

    /** Service to client. Data: [KEY_ERROR] (a SeparationError name) or [KEY_CANCELLED], and [KEY_MESSAGE]. */
    const val MSG_FAILED = 5

    const val KEY_URI = "uri"
    const val KEY_VOCALS = "vocals"
    const val KEY_INSTRUMENTAL = "instrumental"
    const val KEY_FRACTION = "fraction"
    const val KEY_REMAINING_MS = "remaining_ms"
    const val KEY_COOLING = "cooling"
    const val KEY_ERROR = "error"
    const val KEY_CANCELLED = "cancelled"
    const val KEY_MESSAGE = "message"
}
