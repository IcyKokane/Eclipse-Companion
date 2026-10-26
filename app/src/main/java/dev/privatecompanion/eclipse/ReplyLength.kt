package dev.privatecompanion.eclipse

/** Conservative on-device CPU inference profiles; no network execution. */
enum class ReplyLength(val title: String, val maxTokens: Int, val historyTurns: Int) {
    QUICK("Quick · shorter", 96, 6),
    BALANCED("Balanced", 160, 9),
    DETAILED("Detailed · slower", 240, 12);

    companion object {
        fun fromStored(raw: String?): ReplyLength =
            entries.firstOrNull { it.name == raw } ?: QUICK
    }
}

/** Pure color and spacing values for the compact Eclipse chat shell. */
object ChatLayout {
    const val NAV_HEIGHT_DP = 46
    const val NAV_GAP_DP = 7
    const val COMPOSER_GAP_DP = 10
    const val COMPOSER_BUTTON_HEIGHT_DP = 50
    const val MESSAGE_GAP_DP = 12
    const val CHAT_MAX_BUBBLE_PERCENT = 86

    fun bubbleWidth(availableWidth: Int): Int =
        (availableWidth.coerceAtLeast(0) * CHAT_MAX_BUBBLE_PERCENT / 100)
}
