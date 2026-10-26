package dev.privatecompanion.eclipse

import org.junit.Assert.*
import org.junit.Test

class PortableBackupTest {
    private fun pass() = "portable passphrase with 20+ chars".toCharArray()
    private fun fails(work: () -> Unit): Boolean = try { work(); false } catch (_: Exception) { true }

    @Test fun roundTripPortableData() {
        val input = """{"schema":2,"profile":{"name":"Mira"},"messages":[]}""".toByteArray()
        val encrypted = PortableBackup.encrypt(input, pass())
        assertFalse(encrypted.contentEquals(input))
        assertArrayEquals(input, PortableBackup.decrypt(encrypted, pass()))
    }

    @Test fun encryptsDifferentlyEveryTime() {
        val data = "Example private chat".toByteArray()
        assertFalse(PortableBackup.encrypt(data, pass()).contentEquals(PortableBackup.encrypt(data, pass())))
    }

    @Test fun wrongPasswordDoesNotRevealData() {
        val encrypted = PortableBackup.encrypt("Sensitive".toByteArray(), pass())
        assertTrue(fails { PortableBackup.decrypt(encrypted, "different secure passphrase".toCharArray()) })
    }

    @Test fun tamperedCiphertextIsRejected() {
        val encrypted = PortableBackup.encrypt("Private".toByteArray(), pass())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertTrue(fails { PortableBackup.decrypt(encrypted, pass()) })
    }

    @Test fun tamperedSaltOrIvOrVersionIsRejected() {
        for (position in listOf(4, 8, 23)) {
            val encrypted = PortableBackup.encrypt("Private".toByteArray(), pass())
            encrypted[position] = (encrypted[position].toInt() xor 1).toByte()
            assertTrue(fails { PortableBackup.decrypt(encrypted, pass()) })
        }
    }

    @Test fun shortPasswordAndOversizedDataRefused() {
        assertTrue(fails { PortableBackup.encrypt("data".toByteArray(), "short".toCharArray()) })
        assertTrue(fails { PortableBackup.encrypt(ByteArray(PortableBackup.MAX_PLAINTEXT_BYTES + 1), pass()) })
    }

    @Test fun truncatedAndOversizedArchivesRefused() {
        val encrypted = PortableBackup.encrypt("hello".toByteArray(), pass())
        assertTrue(fails { PortableBackup.decrypt(encrypted.copyOfRange(0, 8), pass()) })
        assertTrue(fails { PortableBackup.decrypt(ByteArray(PortableBackup.MAX_BACKUP_BYTES + 1), pass()) })
    }

    @Test fun boundedProviderReadAndOverflow() {
        val data = ByteArray(512) { it.toByte() }
        assertArrayEquals(data, PortableBackup.readBounded(data.inputStream()))
        assertTrue(fails {
            PortableBackup.readBounded(ByteArray(PortableBackup.MAX_BACKUP_BYTES + 1).inputStream())
        })
    }

    @Test fun rejectsEmptyPayload() {
        assertTrue(fails { PortableBackup.encrypt(ByteArray(0), pass()) })
    }
}
