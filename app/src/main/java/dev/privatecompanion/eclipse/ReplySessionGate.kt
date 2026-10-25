package dev.privatecompanion.eclipse

/**
 * UI-thread-only commit gate for local inference.
 * Cancelling a request invalidates its ticket immediately, even if JNI has
 * already finished and its success callback is waiting on the main queue.
 * This class does not interrupt inference; callers also request nativeCancel.
 */
class ReplySessionGate {
    private var sequence = 0L
    private var active: Long? = null

    fun begin(): Long {
        check(active == null) { "A local reply is already active" }
        val ticket = ++sequence
        active = ticket
        return ticket
    }

    fun mayCommit(ticket: Long): Boolean = active == ticket

    fun finish(ticket: Long): Boolean {
        if (active != ticket) return false
        active = null
        return true
    }

    fun cancel() {
        active = null
        ++sequence
    }
}
