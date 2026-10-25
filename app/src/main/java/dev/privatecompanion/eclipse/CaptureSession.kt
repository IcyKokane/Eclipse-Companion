package dev.privatecompanion.eclipse

/** Explicit, non-persistent permission for showing Eclipse in a screenshot or recording. */
class CaptureSession {
    var isAllowed: Boolean = false
        private set

    /** Never infer capture consent from a restored Activity or encrypted disk state. */
    fun choose(allowed: Boolean) {
        isAllowed = allowed
    }

    /** Re-enable FLAG_SECURE before Eclipse remains in recent-app thumbnails. */
    fun background() {
        isAllowed = false
    }
}
