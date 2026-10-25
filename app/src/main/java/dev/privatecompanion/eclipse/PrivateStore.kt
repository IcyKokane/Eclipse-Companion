package dev.privatecompanion.eclipse

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PrivateStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "private_state.enc"))
    private val alias = "eclipse_companion_aes_v1"
    var profile = CompanionProfile()
    val messages = mutableListOf<ChatEntry>()
    var ageConfirmed = false
        private set
    // Default to secure display; only the owner may enable screen capture.
    var screenshotsAllowed = false

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun confirmAdult() {
        if (ageConfirmed) return
        ageConfirmed = true
        try {
            save()
        } catch (e: Exception) {
            // A Keystore or disk failure must not leave unsaved confirmation in memory.
            ageConfirmed = false
            throw e
        }
    }

    fun load() {
        if (!file.baseFile.exists()) return
        val ciphertext = file.openRead().use { it.readBytes() }
        require(ciphertext.size >= 29 && ciphertext.size <= 25 * 1024 * 1024) { "Invalid secure store" }
        require(ciphertext[0] == 1.toByte()) { "Unsupported secure store version" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, ciphertext.copyOfRange(1, 13)))
        val decoded = JSONObject(String(cipher.doFinal(ciphertext, 13, ciphertext.size - 13), Charsets.UTF_8))
        ageConfirmed = decoded.optBoolean("ageConfirmed", false)
        screenshotsAllowed = decoded.optBoolean("screenshotsAllowed", false)
        val person = decoded.optJSONObject("profile") ?: JSONObject()
        val trait = person.optJSONObject("traits") ?: JSONObject()
        val appearance = person.optJSONObject("appearance") ?: JSONObject()
        profile = CompanionProfile(
            name = person.optString("name", "Mira"),
            preset = person.optString("preset", "Affectionate"),
            traits = Traits(
                trait.optInt("warmth", 75), trait.optInt("playfulness", 55),
                trait.optInt("confidence", 60), trait.optInt("romance", 65),
                trait.optInt("humor", 50), trait.optInt("curiosity", 70),
                trait.optInt("initiative", 45)
            ).normalized(),
            appearance = Appearance(
                appearance.optString("hair", "Midnight"),
                appearance.optString("eyes", "Violet"),
                appearance.optString("skin", "Warm"),
                appearance.optString("outfit", "Casual"),
                appearance.optString("style", "Illustrated")
            ),
            adultMode = person.optBoolean("adultMode", false),
            memories = person.optString("memories", ""),
            conversationStyle = person.optString("conversationStyle", "Natural"),
            relationshipPace = person.optString("relationshipPace", "Balanced"),
            interests = person.optString("interests", "")
        )
        messages.clear()
        require(decoded.optInt("schema", 1) in 1..2) { "Unsupported secure state version" }
        val records = decoded.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until records.length()) {
            val m = records.optJSONObject(i) ?: continue
            val role = m.optString("role")
            if (CompanionLogic.requireValidRole(role)) {
                messages += ChatEntry(role, m.optString("content").take(3000), m.optLong("createdAt"))
            }
        }
        if (messages.size > 1000) messages.subList(0, messages.size - 1000).clear()
    }

    fun save() {
        val person = JSONObject().apply {
            put("name", profile.name.take(60)); put("preset", profile.preset)
            put("adultMode", profile.adultMode); put("memories", profile.memories.take(3000))
            put("conversationStyle", profile.conversationStyle); put("relationshipPace", profile.relationshipPace)
            put("interests", profile.interests.take(500))
            put("traits", JSONObject().apply {
                put("warmth", profile.traits.warmth); put("playfulness", profile.traits.playfulness)
                put("confidence", profile.traits.confidence); put("romance", profile.traits.romance)
                put("humor", profile.traits.humor); put("curiosity", profile.traits.curiosity)
                put("initiative", profile.traits.initiative)
            })
            put("appearance", JSONObject().apply {
                put("hair", profile.appearance.hair); put("eyes", profile.appearance.eyes)
                put("skin", profile.appearance.skin); put("outfit", profile.appearance.outfit)
                put("style", profile.appearance.style)
            })
        }
        val root = JSONObject().apply {
            put("schema", 2)
            put("ageConfirmed", ageConfirmed)
            put("screenshotsAllowed", screenshotsAllowed)
            put("profile", person)
            put("messages", JSONArray().apply {
                messages.takeLast(1000).filter { CompanionLogic.requireValidRole(it.role) }.forEach { m ->
                    put(JSONObject().apply {
                        put("role", m.role); put("content", m.content.take(3000)); put("createdAt", m.createdAt)
                    })
                }
            })
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // AndroidKeyStore forbids caller-supplied GCM IVs for encryption by default.
        // Let the provider generate a fresh random nonce and persist its 12 bytes.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val nonce = cipher.iv ?: error("Keystore did not generate an IV")
        require(nonce.size == 12) { "Unexpected Android Keystore GCM IV length" }
        val encrypted = byteArrayOf(1) + nonce + cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    fun wipeHistory() { messages.clear(); save() }
    fun wipeEverything() {
        messages.clear()
        profile = CompanionProfile()
        ageConfirmed = false
        screenshotsAllowed = false
        file.delete()
        File(context.filesDir, "portrait.png").delete()
        File(context.filesDir, "reference.png").delete()
        File(context.filesDir, "companion.gguf").delete()
        File(context.filesDir, "model_import.tmp").delete()
        File(context.filesDir, "model_previous.bak").delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
