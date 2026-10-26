package dev.privatecompanion.eclipse

import org.junit.Assert.*
import org.junit.Test

class ChatLayoutAndReplyLengthTest {
    @Test fun quickIsDefaultForNewOrUnknownSetting() {
        assertEquals(ReplyLength.QUICK, ReplyLength.fromStored(null))
        assertEquals(ReplyLength.QUICK, ReplyLength.fromStored("unsupported"))
    }

    @Test fun settingRoundTrips() {
        for (item in ReplyLength.entries) {
            assertEquals(item, ReplyLength.fromStored(item.name))
        }
    }

    @Test fun longerProfilesRequireMoreWork() {
        assertTrue(ReplyLength.QUICK.maxTokens < ReplyLength.BALANCED.maxTokens)
        assertTrue(ReplyLength.BALANCED.maxTokens < ReplyLength.DETAILED.maxTokens)
        assertTrue(ReplyLength.QUICK.historyTurns < ReplyLength.DETAILED.historyTurns)
        assertTrue(ReplyLength.DETAILED.maxTokens <= 512)
    }

    @Test fun navigationAndComposerHaveVisibleSeparation() {
        assertTrue(ChatLayout.NAV_GAP_DP >= 6)
        assertTrue(ChatLayout.COMPOSER_GAP_DP >= 8)
        assertTrue(ChatLayout.COMPOSER_BUTTON_HEIGHT_DP >= 48)
        assertTrue(ChatLayout.MESSAGE_GAP_DP >= 10)
    }

    @Test fun chatBubblesLeaveGutter() {
        assertEquals(344, ChatLayout.bubbleWidth(400))
        assertEquals(0, ChatLayout.bubbleWidth(-1))
    }
}
