package dev.privatecompanion.eclipse

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileWindowPaddingTest {
    @Test fun navBarPadsBottomOnPortraitPhone() {
        assertEquals(
            EdgePadding(0, 75, 0, 38),
            MobileWindowPadding.resolve(
                EdgePadding(0, 0, 0, 0),
                EdgePadding(0, 75, 0, 38),
                keyboardBottom = 0,
                keyboardVisible = false
            )
        )
    }

    @Test fun visibleKeyboardOverridesNavigationButDoesNotDoubleCount() {
        assertEquals(
            EdgePadding(0, 75, 0, 712),
            MobileWindowPadding.resolve(
                EdgePadding(0, 0, 0, 0),
                EdgePadding(0, 75, 0, 38),
                keyboardBottom = 712,
                keyboardVisible = true
            )
        )
    }

    @Test fun hiddenKeyboardCannotLeavePhantomBottomSpace() {
        assertEquals(
            EdgePadding(0, 75, 0, 38),
            MobileWindowPadding.resolve(
                EdgePadding(0, 0, 0, 0),
                EdgePadding(0, 75, 0, 38),
                keyboardBottom = 712,
                keyboardVisible = false
            )
        )
    }

    @Test fun retainErrorScreenMarginsAroundCutout() {
        assertEquals(
            EdgePadding(35, 122, 24, 54),
            MobileWindowPadding.resolve(
                EdgePadding(24, 24, 24, 24),
                EdgePadding(11, 98, 0, 30),
                keyboardBottom = 0,
                keyboardVisible = false
            )
        )
    }

    @Test fun supportsLandscapeSideNavigationInset() {
        assertEquals(
            EdgePadding(42, 16, 33, 9),
            MobileWindowPadding.resolve(
                EdgePadding(0, 0, 0, 0),
                EdgePadding(42, 16, 33, 9),
                keyboardBottom = 0,
                keyboardVisible = false
            )
        )
    }

    @Test fun ignoresNegativeInsetNoise() {
        assertEquals(
            EdgePadding(12, 12, 12, 12),
            MobileWindowPadding.resolve(
                EdgePadding(12, 12, 12, 12),
                EdgePadding(-5, -10, -8, -9),
                keyboardBottom = -100,
                keyboardVisible = true
            )
        )
    }
}
