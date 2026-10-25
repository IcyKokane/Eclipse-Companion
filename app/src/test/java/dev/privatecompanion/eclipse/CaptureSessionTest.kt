package dev.privatecompanion.eclipse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {
    @Test fun blockedOnCreation() {
        assertFalse(CaptureSession().isAllowed)
    }

    @Test fun canOptInTemporarily() {
        val session = CaptureSession()
        session.choose(true)
        assertTrue(session.isAllowed)
    }

    @Test fun canTurnCaptureBackOff() {
        val session = CaptureSession()
        session.choose(true)
        session.choose(false)
        assertFalse(session.isAllowed)
    }

    @Test fun backgroundAlwaysRevokes() {
        val session = CaptureSession()
        session.choose(true)
        session.background()
        assertFalse(session.isAllowed)
    }

    @Test fun freshSessionDoesNotInheritFormerPermission() {
        val previous = CaptureSession().apply { choose(true) }
        assertTrue(previous.isAllowed)
        assertFalse(CaptureSession().isAllowed)
    }
}
