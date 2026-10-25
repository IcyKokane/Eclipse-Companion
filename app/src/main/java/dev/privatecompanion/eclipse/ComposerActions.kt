package dev.privatecompanion.eclipse

/** JVM-testable visibility/activation contract for the phone's always-visible composer. */
data class ComposerActions(
    val sendEnabled: Boolean,
    val showStop: Boolean,
    val showRetry: Boolean
)

object ComposerPolicy {
    fun evaluate(sending: Boolean, modelBusy: Boolean, lastRole: String?): ComposerActions =
        ComposerActions(
            sendEnabled = !sending && !modelBusy,
            showStop = sending,
            showRetry = !sending && !modelBusy && lastRole == "user"
        )
}
