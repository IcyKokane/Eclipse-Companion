package dev.privatecompanion.eclipse

/** JNI backing is llama.cpp compiled into the APK, no HTTP endpoints. */
fun interface ReplyProgress { fun onPartial(text: String) }

object LocalModel {
    @Volatile private var loadedLibrary = false
    @Volatile private var loadedModel = false
    @Volatile private var loadedCpuThreads = 0
    private external fun nativeLoad(path: String, threads: Int, contextSize: Int): Boolean
    private external fun nativeReply(roles: Array<String>, contents: Array<String>, maxTokens: Int, temperature: Float, listener: ReplyProgress?): String
    private external fun nativeUnload()
    private external fun nativeCancel()
    private external fun nativeResetCancel()
    private external fun nativeReplyDiagnostics(): String
    @Volatile private var cancelRequested = false

    fun supported(): Boolean {
        if (loadedLibrary) return true
        return try { System.loadLibrary("eclipse_jni"); loadedLibrary = true; true }
        catch (_: UnsatisfiedLinkError) { false }
    }

    @Synchronized fun load(path: String, threads: Int = 4): Boolean {
        require(threads in 1..8) { "Invalid local CPU worker count" }
        // Reload must not leave a stale ready=true flag when JNI unloading/loading fails.
        loadedModel = false
        loadedCpuThreads = 0
        if (!supported()) return false
        cancelRequested = false
        nativeUnload()
        val ok = nativeLoad(path, threads, 4096)
        loadedModel = ok
        loadedCpuThreads = if (ok) threads else 0
        return ok
    }

    fun ready(): Boolean = loadedModel
    fun activeCpuThreads(): Int = if (loadedModel) loadedCpuThreads else 0

    /** CPU-only timing/counter diagnostics; never includes prompts or personal chat text. */
    @Synchronized fun lastReplyDiagnostics(): String =
        if (loadedLibrary && loadedModel) nativeReplyDiagnostics() else ""

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
        loadedCpuThreads = 0
        cancelRequested = false
    }
}
