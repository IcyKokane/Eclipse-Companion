package dev.privatecompanion.eclipse

/** JNI backing is llama.cpp compiled into the APK, no HTTP endpoints. */
fun interface ReplyProgress { fun onPartial(text: String) }

object LocalModel {
    @Volatile private var loadedLibrary = false
    @Volatile private var loadedModel = false
    private external fun nativeLoad(path: String, threads: Int, contextSize: Int): Boolean
    private external fun nativeReply(roles: Array<String>, contents: Array<String>, maxTokens: Int, temperature: Float, listener: ReplyProgress?): String
    private external fun nativeUnload()
    private external fun nativeCancel()
    private external fun nativeResetCancel()
    @Volatile private var cancelRequested = false

    fun supported(): Boolean {
        if (loadedLibrary) return true
        return try { System.loadLibrary("eclipse_jni"); loadedLibrary = true; true }
        catch (_: UnsatisfiedLinkError) { false }
    }

    @Synchronized fun load(path: String): Boolean {
        if (!supported()) return false
        cancelRequested = false
        nativeUnload()
        loadedModel = nativeLoad(path, 4, 4096)
        return loadedModel
    }

    fun ready(): Boolean = loadedModel

    @Synchronized fun respond(
        profile: CompanionProfile, history: List<ChatEntry>,
        length: ReplyLength = ReplyLength.QUICK, progress: ReplyProgress? = null
    ): String {
        if (!loadedModel) throw IllegalStateException("Import a local GGUF model first")
        val recent = CompanionLogic.contextWindow(history, length.historyTurns)
        val roles = arrayOf("system") + recent.map { it.role }
        val contents = arrayOf(CompanionLogic.systemPrompt(profile)) + recent.map { it.content }
        if (cancelRequested) { cancelRequested = false; throw IllegalStateException("Generation cancelled") }
        val answer = try { nativeReply(roles, contents, length.maxTokens, 0.8f, progress) } finally { cancelRequested = false }
        if (answer.startsWith("ECLIPSE_ERROR:")) {
            throw IllegalStateException(answer.removePrefix("ECLIPSE_ERROR:").trim())
        }
        return answer
    }

    /** Called on the UI thread before enqueueing a NEW generation. */
    fun beginRequest() {
        cancelRequested = false
        if (loadedLibrary) nativeResetCancel()
    }

    /** This must NOT synchronize on LocalModel: JNI generation holds the worker lock. */
    fun cancel() {
        cancelRequested = true
        if (loadedLibrary) nativeCancel()
    }

    @Synchronized fun unload() {
        if (loadedLibrary) nativeUnload()
        loadedModel = false
        cancelRequested = false
    }
}
