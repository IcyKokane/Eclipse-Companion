package dev.privatecompanion.eclipse

/**
 * Offline GGUF model onboarding and disk preflight.
 * Opening the optional upstream download URL is delegated to the user's browser;
 * Eclipse itself continues to request no INTERNET permission.
 */
object ModelSetupGuide {
    const val QWEN_MODEL_NAME = "Qwen2.5 1.5B Instruct Q4_K_M"
    const val QWEN_DOWNLOAD_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val RESERVED_DISK_BYTES = 128L * 1024 * 1024
    const val UNKNOWN_LENGTH_MIN_FREE = RESERVED_DISK_BYTES + 256L * 1024 * 1024

    data class StorageCheck(val permitted: Boolean, val explanation: String)

    /**
     * The old model remains on disk during copying, so any replacement requires
     * enough additional space for the entire new GGUF plus a safety reserve.
     * Unknown-size document providers can begin with 384 MiB, but the streaming
     * importer must enforce the reserve during the copy.
     */
    fun checkImport(sourceLength: Long, available: Long): StorageCheck {
        if (sourceLength in 0..1023) return StorageCheck(false, "File is too small to be a GGUF model.")
        if (sourceLength > ModelFiles.MAX_BYTES) return StorageCheck(false, "GGUF exceeds the 8 GiB model limit.")
        if (available < 0) return StorageCheck(false, "Free storage could not be checked.")
        if (sourceLength < 0) {
            return if (available >= UNKNOWN_LENGTH_MIN_FREE) StorageCheck(
                true, "Unknown download size: import will check free storage as it copies."
            ) else StorageCheck(
                false, "Free at least 384 MiB before importing an unknown-size model."
            )
        }
        val required = sourceLength + RESERVED_DISK_BYTES
        return if (available >= required) {
            StorageCheck(true, "Sufficient free storage to stage this model.")
        } else {
            StorageCheck(false, "Not enough free storage to copy this model and keep a 128 MiB reserve.")
        }
    }

    fun sizeLabel(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        val mib = bytes / (1024L * 1024)
        return if (mib >= 1024) {
            "${"%.2f".format(java.util.Locale.US, mib.toDouble() / 1024)} GiB"
        } else {
            "$mib MiB"
        }
    }
}
