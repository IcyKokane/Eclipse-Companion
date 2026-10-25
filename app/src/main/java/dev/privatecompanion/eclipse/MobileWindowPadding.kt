package dev.privatecompanion.eclipse

/** Pure geometry to keep app content out of status bars, display cutouts, navigation and the keyboard. */
data class EdgePadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

object MobileWindowPadding {
    /**
     * System bars and display cutouts are combined by Android before calling this function.
     * The keyboard can extend above the navigation bar; use the larger bottom inset, not their sum.
     * The initial view padding is preserved so error screens keep their readable inner margins.
     */
    fun resolve(
        initial: EdgePadding,
        safeArea: EdgePadding,
        keyboardBottom: Int,
        keyboardVisible: Boolean
    ): EdgePadding {
        val bottomInset = maxOf(safeArea.bottom, if (keyboardVisible) keyboardBottom else 0)
        return EdgePadding(
            left = initial.left + safeArea.left.coerceAtLeast(0),
            top = initial.top + safeArea.top.coerceAtLeast(0),
            right = initial.right + safeArea.right.coerceAtLeast(0),
            bottom = initial.bottom + bottomInset.coerceAtLeast(0)
        )
    }
}
