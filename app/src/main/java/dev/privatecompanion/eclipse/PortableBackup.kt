package dev.privatecompanion.eclipse

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Device-independent encrypted backup format. No plaintext backup ever touches shared storage.
 *
 * [4-byte magic "ECLB"][1-byte format 1][16-byte random salt][12-byte random IV]
 * [AES-256-GCM ciphertext + 16-byte authentication tag]
 *
 * PBKDF2-HMAC-SHA256 (250,000 iterations). Header is authenticated as GCM AAD.
 * A passphrase is required because Android Keystore keys cannot migrate across app installs.
 */
object PortableBackup {
    private val magic = byteArrayOf(69, 67, 76, 66, 1)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 16
    private const val ITERATIONS = 250_000
    const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
    const val MAX_BACKUP_BYTES = MAX_PLAINTEXT_BYTES + 5 + SALT_SIZE + IV_SIZE + TAG_SIZE
    private const val HEADER_SIZE = 5 + SALT_SIZE + IV_SIZE
    private val random = SecureRandom()

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        require(passphrase.size in 12..256) { "Backup password must have 12–256 characters" }
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(encoded, "AES") }
            finally { encoded.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Backup data is outside the supported 16 MiB limit"
        }
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val header = magic + salt + iv
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    fun decrypt(archive: ByteArray, password: CharArray): ByteArray {
        require(archive.size in (HEADER_SIZE + TAG_SIZE + 1)..MAX_BACKUP_BYTES) {
            "Unsupported or oversized Eclipse backup"
        }
        require(archive.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Unrecognized Eclipse backup version"
        }
        val salt = archive.copyOfRange(magic.size, magic.size + SALT_SIZE)
        val iv = archive.copyOfRange(magic.size + SALT_SIZE, HEADER_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(archive, 0, HEADER_SIZE)
        val plaintext = cipher.doFinal(archive, HEADER_SIZE, archive.size - HEADER_SIZE)
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PLAINTEXT_BYTES) { "Invalid backup content size" }
        return plaintext
    }

    /** Reads an Android document provider without accepting unbounded input into memory. */
    fun readBounded(input: java.io.InputStream): ByteArray = input.use { source ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = source.read(buf)
            if (n == -1) break
            if (n == 0) error("Backup provider stopped responding")
            require(out.size() + n <= MAX_BACKUP_BYTES) { "Backup is too large" }
            out.write(buf, 0, n)
        }
        out.toByteArray()
    }
}
