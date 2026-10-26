package dev.privatecompanion.eclipse

import java.io.File
import java.io.InputStream
import java.io.FileOutputStream

/** No network use, Android dependencies, or non-app file writes. GGUF header alone is not a proof
 * of a valid or compatible model; llama.cpp performs full validation on load.
 */
object ModelFiles {
    const val MAX_BYTES: Long = 8L * 1024 * 1024 * 1024
    private val magic = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())

    fun inspectHeader(input: InputStream): Int {
        val header = ByteArray(8)
        var read = 0
        while (read < header.size) {
            val count = input.read(header, read, header.size - read)
            if (count < 0) throw IllegalArgumentException("GGUF header is truncated")
            if (count == 0) error("Model provider returned no data")
            read += count
        }
        require(header.copyOfRange(0, 4).contentEquals(magic)) { "Selected file is not GGUF" }
        val version = (header[4].toInt() and 0xff) or
            ((header[5].toInt() and 0xff) shl 8) or
            ((header[6].toInt() and 0xff) shl 16) or
            ((header[7].toInt() and 0xff) shl 24)
        require(version in 2..3) { "Unsupported GGUF header version: $version" }
        return version
    }

    /** Validates while streaming; staging is always deleted on failure. Previous valid model
     * survives any copy failure or rename failure. Caller serializes model load/unload with this.
     */
    fun importInto(
        dir: File, source: InputStream, limit: Long = MAX_BYTES,
        progress: (Long) -> Unit = {},
        reserveFreeBytes: Long = ModelSetupGuide.RESERVED_DISK_BYTES,
        freeBytes: () -> Long = { dir.usableSpace }
    ): Long {
        require(limit >= 1024) { "Invalid size limit" }
        require(reserveFreeBytes >= 0) { "Invalid disk reserve" }
        require(dir.isDirectory) { "Model directory missing" }
        val target = File(dir, "companion.gguf")
        val staging = File(dir, "model_import.tmp")
        val backup = File(dir, "model_previous.bak")
        recover(dir)
        if (staging.exists() && !staging.delete()) error("Cannot clear previous temporary import")
        var written = 0L
        try {
            source.use { input ->
                val head = ByteArray(8)
                var offset = 0
                while (offset < head.size) {
                    val n = input.read(head, offset, head.size - offset)
                    if (n < 0) error("GGUF header is truncated")
                    if (n == 0) error("Model provider returned no data")
                    offset += n
                }
                inspectHeader(head.inputStream())
                FileOutputStream(staging).use { output ->
                    output.write(head)
                    written = 8
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) error("Model provider returned no data")
                        written += count
                        require(written <= limit) { "Model exceeds size limit" }
                        // Guard unknown-size providers and storage changing during a long import.
                        check(freeBytes() >= reserveFreeBytes) {
                            "Free storage fell below the 128 MiB import reserve"
                        }
                        output.write(buffer, 0, count)
                        progress(written)
                    }
                    output.fd.sync()
                }
            }
            require(written >= 1024) { "File is too small to be a GGUF model" }
            // Existing models are not touched until the entire replacement is on disk.
            require(!backup.exists()) { "Previous model recovery is required" }
            val hadModel = target.exists()
            if (hadModel) check(target.renameTo(backup)) { "Cannot preserve existing model" }
            if (!staging.renameTo(target)) {
                if (hadModel) check(backup.renameTo(target)) { "Recovery failed; previous model is in model_previous.bak" }
                error("Cannot finalize model import")
            }
            if (hadModel) backup.delete() // Unlinked only after new target exists.
            return written
        } finally {
            staging.delete()
        }
    }

    /** Recover model after process death between old-model backup and new-model promotion. */
    fun recover(dir: File) {
        val target = File(dir, "companion.gguf")
        val backup = File(dir, "model_previous.bak")
        if (!backup.exists()) return
        if (!target.exists()) check(backup.renameTo(target)) { "Unable to recover previous model" }
        else backup.delete()
    }
}
