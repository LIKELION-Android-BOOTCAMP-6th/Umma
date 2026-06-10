package com.app.umma.data.push

/**
 * Builds the phone-owned SRS notification text that Wear OS mirrors to the watch.
 */
object SrsNotificationContentPolicy {
    const val MAX_TITLE_LENGTH = 22
    const val MAX_BODY_LENGTH = 56

    /**
     * Returns sanitized notification content from optional [title] and [body] payload values.
     */
    fun build(
        title: String?,
        body: String?,
        fallbackTitle: String,
        fallbackBody: String
    ): SrsNotificationContent {
        val safeTitle = title.sanitizedOrFallback(fallbackTitle)
            .ellipsize(MAX_TITLE_LENGTH)
        val safeBody = body.sanitizedOrFallback(fallbackBody)
            .ellipsize(MAX_BODY_LENGTH)

        return SrsNotificationContent(
            title = safeTitle,
            body = safeBody
        )
    }

    private fun String?.sanitizedOrFallback(fallback: String): String {
        val normalized = this
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()

        return normalized.ifEmpty { fallback }
    }

    private fun String.ellipsize(maxLength: Int): String {
        if (length <= maxLength) return this
        if (maxLength <= ELLIPSIS.length) return take(maxLength)
        return take(maxLength - ELLIPSIS.length).trimEnd() + ELLIPSIS
    }

    private const val ELLIPSIS = "..."
}

/**
 * Notification title and body after fallback, whitespace normalization, and length limiting.
 */
data class SrsNotificationContent(
    val title: String,
    val body: String
)
