package dev.privatecompanion.eclipse

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerPolicyTest {
    @Test fun freshConversationAllowsSendButNotRetry() {
        assertEquals(ComposerActions(true, false, false), ComposerPolicy.evaluate(false, false, null))
    }

    @Test fun unansweredTurnShowsRetry() {
        assertEquals(ComposerActions(true, false, true), ComposerPolicy.evaluate(false, false, "user"))
    }

    @Test fun answeredTurnHidesRetry() {
        assertEquals(ComposerActions(true, false, false), ComposerPolicy.evaluate(false, false, "assistant"))
    }

    @Test fun duringGenerationOnlyStopIsAvailable() {
        assertEquals(ComposerActions(false, true, false), ComposerPolicy.evaluate(true, false, "user"))
    }

    @Test fun importingOrLoadingBlocksBothSendAndRetry() {
        assertEquals(ComposerActions(false, false, false), ComposerPolicy.evaluate(false, true, "user"))
    }

    @Test fun stopRemainsAvailableUntilGenerationFinishes() {
        assertEquals(ComposerActions(false, true, false), ComposerPolicy.evaluate(true, true, "user"))
    }
}
