package dev.privatecompanion.eclipse

object ReplySessionGateChecks {
    fun run(): Int {
        var checks = 0
        fun test(name: String, ok: Boolean) { check(ok) { "FAILED: $name" }; checks++ }
        val gate = ReplySessionGate()
        val first = gate.begin()
        test("new generation can commit", gate.mayCommit(first))
        test("invalid id cannot commit", !gate.mayCommit(first + 1))
        gate.cancel()
        test("cancel invalidates reply", !gate.mayCommit(first))
        test("cancelled reply cannot finish", !gate.finish(first))
        val second = gate.begin()
        test("cancelled generation never revives", !gate.mayCommit(first))
        test("new ticket distinct", second != first)
        test("new generation can commit", gate.mayCommit(second))
        test("older generation cannot finish newer", !gate.finish(first))
        test("newer still active", gate.mayCommit(second))
        test("finish active reply", gate.finish(second))
        test("finished reply cannot repeat", !gate.mayCommit(second))
        test("finished reply cannot re-finish", !gate.finish(second))
        val third = gate.begin()
        test("fresh ticket monotonic", third > second)
        gate.cancel(); gate.cancel()
        test("repeat cancel safe", !gate.mayCommit(third))
        val fourth = gate.begin()
        test("ticket skips cancellations", fourth > third + 1)
        test("latest may commit", gate.mayCommit(fourth))
        return checks
    }
}
