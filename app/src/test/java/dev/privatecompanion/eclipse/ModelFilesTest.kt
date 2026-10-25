package dev.privatecompanion.eclipse

import java.io.File
import java.io.InputStream
import java.nio.file.Files

object ModelFilesChecks {
    private fun gguf(bytes: Int = 2048, version: Int = 3): ByteArray = ByteArray(bytes).apply {
        require(bytes >= 8)
        this[0] = 'G'.code.toByte(); this[1] = 'G'.code.toByte()
        this[2] = 'U'.code.toByte(); this[3] = 'F'.code.toByte()
        this[4] = version.toByte()
    }
    private inline fun fail(block: () -> Unit): Boolean = try { block(); false } catch (_: Exception) { true }

    fun run(): Int {
        var count = 0
        fun checkThat(ok: Boolean, note: String) { check(ok) { note }; count++ }
        checkThat(ModelFiles.inspectHeader(gguf().inputStream()) == 3, "header v3")
        checkThat(ModelFiles.inspectHeader(gguf(version=2).inputStream()) == 2, "header v2")
        checkThat(fail { ModelFiles.inspectHeader(ByteArray(3).inputStream()) }, "truncated header rejected")
        checkThat(fail { ModelFiles.inspectHeader(ByteArray(10).inputStream()) }, "bad magic rejected")
        checkThat(fail { ModelFiles.inspectHeader(gguf(version=1).inputStream()) }, "old version rejected")
        checkThat(fail { ModelFiles.inspectHeader(gguf(version=4).inputStream()) }, "unknown version rejected")
        val dir = Files.createTempDirectory("eclipse-model-tests-").toFile()
        try {
            val old = gguf().also { it[30] = 47 }
            val newer = gguf(4096).also { it[30] = 53 }
            val target = File(dir, "companion.gguf")
            var progress = 0L
            checkThat(ModelFiles.importInto(dir, old.inputStream()) == old.size.toLong(), "initial import")
            checkThat(target.readBytes().contentEquals(old), "original bytes match")
            checkThat(fail { ModelFiles.importInto(dir, ByteArray(3).inputStream()) }, "bad replacement rejected")
            checkThat(target.readBytes().contentEquals(old), "original preserved after bad header")
            checkThat(fail { ModelFiles.importInto(dir, gguf(512).inputStream()) }, "too small replacement rejected")
            checkThat(target.readBytes().contentEquals(old), "original preserved after small import")
            checkThat(fail { ModelFiles.importInto(dir, newer.inputStream(), limit = 1024) }, "size cap enforced")
            checkThat(target.readBytes().contentEquals(old), "original preserved after size cap")
            val broken = object : InputStream() {
                var readCount = 0
                override fun read(): Int {
                    if (readCount >= 1500) throw java.io.IOException("provider interrupted")
                    return old[readCount++].toInt() and 0xff
                }
            }
            checkThat(fail { ModelFiles.importInto(dir, broken) }, "broken source rejected")
            checkThat(target.readBytes().contentEquals(old), "original preserved on copy failure")
            checkThat(ModelFiles.importInto(dir, newer.inputStream(), progress = { progress = it }) == newer.size.toLong(), "replacement")
            checkThat(progress == newer.size.toLong(), "copy progress")
            checkThat(target.readBytes().contentEquals(newer), "replacement matches source")
            checkThat(!File(dir,"model_import.tmp").exists(), "staging cleared")
            checkThat(!File(dir,"model_previous.bak").exists(), "backup cleared")
            val backup = File(dir, "model_previous.bak")
            checkThat(target.renameTo(backup), "simulate interrupted promotion")
            ModelFiles.recover(dir)
            checkThat(target.readBytes().contentEquals(newer), "old model restored after interrupted promotion")
            checkThat(!backup.exists(), "recovery backup removed")
            backup.writeBytes(old)
            ModelFiles.recover(dir)
            checkThat(target.readBytes().contentEquals(newer), "valid target wins over stale backup")
            checkThat(!backup.exists(), "stale backup removed")
            checkThat(fail { ModelFiles.importInto(dir, old.inputStream(), limit=12) }, "small limit rejected")
        } finally { dir.deleteRecursively() }
        return count
    }
}
