package dev.privatecompanion.eclipse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val store by lazy { PrivateStore(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var composerDock: LinearLayout
    private var composerEdit: EditText? = null
    private var streamingPreview = ""
    private var lastInferenceDiagnostics = ""
    private var previewLabel: TextView? = null
    private val navigationButtons = linkedMapOf<String, Button>()
    private var lastAppliedPadding = EdgePadding(0, 0, 0, 0)
    private var sending = false
    private var conversationRevision = 0L
    private val replyGate = ReplySessionGate()
    private var draftText = ""
    private val captureSession = CaptureSession()
    private var refreshPrivacyOnResume = false
    private var modelBusy = false
    private var pane = "Chat"
    private var status = "Import a GGUF model to begin private AI conversations."
    private val violet = Color.rgb(181, 156, 246)
    private val cyan = Color.rgb(108, 220, 215)
    private val light = Color.rgb(236, 243, 255)
    private val muted = Color.rgb(151, 170, 195)
    private val dark = Color.rgb(12, 17, 30)
    private val panel = Color.rgb(24, 33, 54)
    private val borderColor = Color.rgb(50, 65, 90)
    private val requestModel = 201
    private val requestImage = 202
    private val requestBackupCreate = 203
    private val requestBackupOpen = 204
    private var pendingEncryptedExport: ByteArray? = null
    private var backupBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pane = savedInstanceState?.getString("eclipse_pane")?.takeIf { it in listOf("Chat", "Personality", "Appearance", "Privacy") } ?: "Chat"
        draftText = savedInstanceState?.getString("eclipse_draft")?.take(3000) ?: ""
        // Always hide intimate on-screen content from screenshots and recent-app thumbnails.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        try { store.load() } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Secure data cannot be opened")
                .setMessage("Local encrypted data could not be read. No data has been overwritten. " +
                    (e.message ?: "Check Android Keystore availability."))
                .setCancelable(false).setPositiveButton("Close") { _, _ -> finish() }.show()
            return
        }
        // Allow screenshots only for the current session; relaunch always starts private.
        captureSession.background()
        updateCaptureSecurity()
        runCatching { ModelFiles.recover(filesDir) }.onFailure {
            status = "Previous model recovery needs attention: ${it.message}"
        }
        if (store.ageConfirmed) openUiSafely() else showAgeGate()
    }

    private fun showAgeGate() {
        AlertDialog.Builder(this)
            .setTitle("Adults only · 18+")
            .setMessage("Eclipse Companion is designed for adults. All conversations and model inference " +
                "stay on this device. Confirm that you are at least 18 to continue.")
            .setCancelable(false)
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setPositiveButton("I am 18 or older") { _, _ ->
                try {
                    store.confirmAdult()
                    openUiSafely()
                } catch (e: Exception) {
                    showStartupFailure("Saving age confirmation", e)
                }
            }
            .show()
    }

    /** Keep the Activity alive and the encrypted store intact if startup fails. */
    private fun openUiSafely() {
        try {
            showUi()
        } catch (e: Exception) {
            showStartupFailure("Opening the companion interface", e)
        }
    }

    private fun showStartupFailure(stage: String, error: Exception) {
        android.util.Log.e("EclipseStartup", stage, error)
        // A framework-only recovery view avoids depending on the failing chat/portrait UI.
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        view.addView(TextView(this).apply {
            text = "Eclipse Companion could not complete startup"
            textSize = 20f
            setTextColor(Color.WHITE)
        })
        view.addView(TextView(this).apply {
            text = "$stage failed (${error.javaClass.simpleName}). " +
                "Your existing chats and model have not been cleared. " +
                "Retry, or close Eclipse Companion and report this error.\n\n" +
                (error.message?.take(200) ?: "No additional error message was provided.")
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(20), 0, dp(24))
        })
        view.addView(Button(this).apply {
            text = "Retry startup"
            setOnClickListener {
                if (store.ageConfirmed) openUiSafely() else showAgeGate()
            }
        })
        view.addView(Button(this).apply {
            text = "Close app"
            setOnClickListener { finish() }
        })
        applySafeInsets(view)
        setContentView(view)
        view.requestApplyInsets()
    }

    /** Preserve view margins while reserving status/cutout, navigation and keyboard insets. */
    private fun applySafeInsets(view: View) {
        if (Build.VERSION.SDK_INT >= 35) {
            val original = EdgePadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            view.setOnApplyWindowInsetsListener { target, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val keyboard = insets.getInsets(WindowInsets.Type.ime())
                val padding = MobileWindowPadding.resolve(
                    initial = original,
                    safeArea = EdgePadding(safe.left, safe.top, safe.right, safe.bottom),
                    keyboardBottom = keyboard.bottom,
                    keyboardVisible = insets.isVisible(WindowInsets.Type.ime())
                )
                lastAppliedPadding = padding
                target.setPadding(padding.left, padding.top, padding.right, padding.bottom)
                insets
            }
            view.requestApplyInsets()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
    }

    private fun updateCaptureSecurity() {
        if (captureSession.isAllowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun setScreenshotsAllowed(allow: Boolean) {
        captureSession.choose(allow)
        updateCaptureSecurity()
        render()
    }

    /** Diagnostic information contains layout metrics only, not chat/profile/model content. */
    private fun copyLayoutDiagnostics() {
        val display = resources.displayMetrics
        val detail = """
            Eclipse Companion UI Preview 0.4.5
            Android SDK: ${Build.VERSION.SDK_INT}
            Display pixels: ${display.widthPixels} x ${display.heightPixels}
            Density: ${display.density}
            Font scale: ${resources.configuration.fontScale}
            Window padding px: ${lastAppliedPadding.left}, ${lastAppliedPadding.top}, ${lastAppliedPadding.right}, ${lastAppliedPadding.bottom}
            Screen capture allowed this session: ${captureSession.isAllowed}
        """.trimIndent()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Eclipse UI diagnostics", detail))
        toast("UI diagnostics copied (no chats or private memories)")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun bg(color: Int, radius: Float = 20f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun pad(v: View, x: Int = 18, y: Int = 12) = v.setPadding(dp(x), dp(y), dp(x), dp(y))

    private fun outlined(color: Int = panel, radius: Int = 14, stroke: Int = borderColor): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun label(text: String, size: Float = 16f, color: Int = light, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }

    private fun input(text: String = "", hint: String = "", lines: Int = 1): EditText = EditText(this).apply {
        setText(text); this.hint = hint
        setTextColor(light); setHintTextColor(muted)
        textSize = 15f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        background = outlined(panel, 14)
        pad(this)
        if (lines > 1) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = lines }
        else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private fun button(title: String, onClick: () -> Unit): Button = Button(this).apply {
        text = title
        textSize = 13f
        isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        maxLines = 2
        gravity = Gravity.CENTER
        minimumWidth = 0
        minimumHeight = dp(48)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        setTextColor(Color.WHITE)
        // Avoid OEM themes tinting the button into an opaque white rectangle.
        backgroundTintList = null
        background = outlined(Color.rgb(75, 62, 131), 13, Color.rgb(106, 93, 165))
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun section(title: String) { content.addView(label(title, 21f, light, true)) }

    private fun space(height: Int = 12) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }

    private fun showUi() {
        if (!store.ageConfirmed) return
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(dark) }
        applySafeInsets(root)
        setContentView(root)
        root.requestApplyInsets()
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(13), dp(20), dp(7))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("E C L I P S E", 17f, light, true))
        brand.addView(label("PRIVATE COMPANION", 10f, muted))
        titleBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        titleBar.addView(label("●  OFFLINE", 10f, cyan, true).apply {
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = outlined(Color.rgb(17, 46, 55), 30, Color.rgb(37, 105, 111))
        })
        root.addView(titleBar)

        navigationButtons.clear()
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(7), dp(12), dp(9))
        }
        listOf("Chat", "Personality", "Appearance", "Privacy").forEach { name ->
            val tab = button(name) { navigateTo(name) }.apply {
                textSize = 12f
                maxLines = 1
                isSingleLine = true
                background = if (pane == name) outlined(Color.rgb(55, 51, 101), 12, violet)
                    else outlined(panel, 12)
                setTextColor(if (pane == name) light else muted)
            }
            navigationButtons[name] = tab
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(ChatLayout.NAV_HEIGHT_DP), 1f).apply {
                setMargins(dp(ChatLayout.NAV_GAP_DP / 2), 0, dp(ChatLayout.NAV_GAP_DP / 2), 0)
            })
        }
        root.addView(tabs)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        chatScroll = scroll
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        // Dock is outside message scroll, and uses IME insets to stay above the keyboard.
        composerDock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = outlined(Color.rgb(16, 24, 40), 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(composerDock, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(2), dp(12), dp(8))
        })
        render()
    }

    /** Always reveal a new settings page from the top, and highlight the actual active tab. */
    private fun navigateTo(name: String) {
        if (name !in navigationButtons) return
        if (name != pane) {
            currentFocus?.let { field ->
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
                field.clearFocus()
            }
        }
        pane = name
        for ((tabName, button) in navigationButtons) {
            button.background = if (tabName == pane) outlined(Color.rgb(55, 51, 101), 12, violet)
                else outlined(panel, 12)
            button.setTextColor(if (tabName == pane) light else muted)
        }
        render()
        if (name != "Chat") {
            chatScroll.post { if (pane == name) chatScroll.scrollTo(0, 0) }
        }
    }

    private fun render() {
        content.removeAllViews()
        composerDock.removeAllViews()
        composerEdit = null
        previewLabel = null
        when (pane) {
            "Chat" -> {
                chatPane()
                composerPane()
                composerDock.visibility = View.VISIBLE
            }
            "Personality" -> { composerDock.visibility = View.GONE; personalityPane() }
            "Appearance" -> { composerDock.visibility = View.GONE; appearancePane() }
            else -> { composerDock.visibility = View.GONE; privacyPane() }
        }
        if (pane == "Chat") chatScroll.post {
            if (pane == "Chat") chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun chatBubble(title: String, message: String, owner: Boolean): TextView {
        val color = if (owner) Color.rgb(36, 43, 75) else panel
        val body = label(message, 15f, light).apply {
            setPadding(dp(12), dp(5), dp(12), dp(12))
            setTextIsSelectable(true)
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = outlined(color, 15, if (owner) Color.rgb(78, 85, 139) else borderColor)
            addView(label(title, 10f, if (owner) violet else cyan, true).apply {
                setPadding(dp(12), dp(10), dp(12), dp(2))
            })
            addView(body)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (owner) Gravity.END else Gravity.START
            addView(bubble, LinearLayout.LayoutParams(
                ChatLayout.bubbleWidth(resources.displayMetrics.widthPixels - dp(42)), -2
            ))
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(ChatLayout.MESSAGE_GAP_DP)
        })
        return body
    }

    private fun chatPane() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = outlined(Color.rgb(19, 27, 45), 18)
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val path = File(filesDir, "portrait.png")
            val portrait = runCatching {
                if (path.exists()) BitmapFactory.decodeFile(path.absolutePath)
                    ?: AvatarStudio.create(this@MainActivity, store.profile.appearance)
                else AvatarStudio.create(this@MainActivity, store.profile.appearance)
            }.onFailure {
                android.util.Log.e("EclipseStartup", "Portrait unavailable; using fallback icon", it)
            }.getOrNull()
            if (portrait != null) setImageBitmap(portrait)
            else setImageResource(android.R.drawable.ic_menu_gallery)
            background = outlined(panel, 14)
            clipToOutline = true
        }
        header.addView(image, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(12) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(store.profile.name, 21f, light, true))
            addView(label(store.profile.preset + " · " +
                if (store.profile.adultMode) "Adult mode" else "Everyday mode", 12f, muted))
            addView(label(if (LocalModel.ready()) "●  LOCAL AI READY" else "○  MODEL NOT LOADED",
                11f, if (LocalModel.ready()) cyan else muted, true))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)
        space(16)

        if (sending || status.startsWith("Local reply failed") ||
            status.startsWith("Generation") || status.startsWith("Reply ready")) {
            content.addView(label(status, 12f, if (sending) cyan else muted))
            space(10)
        }
        if (!LocalModel.ready()) {
            content.addView(label(
                "Import a compatible GGUF model to enable private on-device replies. " +
                    "You can customize your companion without a model.", 14f, muted
            ))
            space(8)
            content.addView(button("Set up local GGUF model") { navigateTo("Privacy") })
            space(12)
        } else if (store.messages.isEmpty()) {
            content.addView(label(
                "Start a conversation, or tap Ideas to find something to talk about.", 14f, muted
            ))
            space(12)
        }
        for (m in store.messages.takeLast(50)) {
            chatBubble(if (m.role == "user") "YOU" else store.profile.name.uppercase(),
                m.content, m.role == "user")
        }
        if (sending) {
            previewLabel = chatBubble(
                "${store.profile.name.uppercase()} · COMPOSING",
                streamingPreview.ifBlank { "Preparing a reply on your device…" },
                false
            )
        }
        if (store.messages.size > 50) {
            content.addView(label("Showing the latest 50 messages. Older messages remain encrypted.", 12f, muted))
        }
    }

    /** Single compact dock: Send changes to Stop, so buttons never collide or stack. */
    private fun composerPane() {
        val edit = input(draftText, "Message ${store.profile.name}…", 2).apply {
            maxLines = 3
            minLines = 2
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        composerEdit = edit
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                draftText = s?.toString()?.take(3000) ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        composerDock.addView(edit, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val actionState = ComposerPolicy.evaluate(sending, modelBusy, store.messages.lastOrNull()?.role)
        val retry = actionState.showRetry && draftText.isBlank()
        val send = button(
            if (actionState.showStop) "■  Stop response"
            else if (retry) "↻  Retry reply"
            else "Send"
        ) {
            if (sending) {
                replyGate.cancel()
                LocalModel.cancel()
                status = "Stopping local generation..."
                if (pane == "Chat") render()
                return@button
            }
            if (modelBusy) return@button
            if (!LocalModel.ready()) { toast("Load a GGUF model in Privacy first."); return@button }
            val userText = edit.text.toString().trim().take(3000)
            if (userText.isBlank()) {
                if (actionState.showRetry) startReply()
                return@button
            }
            store.messages.add(ChatEntry("user", userText, System.currentTimeMillis()))
            try { store.save() } catch (e: Exception) {
                store.messages.removeAt(store.messages.lastIndex)
                toast("Could not save message: ${e.message}")
                return@button
            }
            draftText = ""
            conversationRevision++
            startReply()
        }
        send.isEnabled = actionState.sendEnabled || actionState.showStop
        if (actionState.showStop) send.contentDescription = "Stop current response"
        if (actionState.showRetry) send.contentDescription = "Generate/retry reply to last message"
        actions.addView(send, LinearLayout.LayoutParams(0, dp(ChatLayout.COMPOSER_BUTTON_HEIGHT_DP), 2f))
        actions.addView(button("Ideas") { showStarterDialog() }.apply {
            background = outlined(panel, 13, borderColor)
            setTextColor(light)
        }, LinearLayout.LayoutParams(0, dp(ChatLayout.COMPOSER_BUTTON_HEIGHT_DP), 1f).apply {
            leftMargin = dp(ChatLayout.COMPOSER_GAP_DP)
        })
        composerDock.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ChatLayout.COMPOSER_GAP_DP)
        })
    }

    private fun showStarterDialog() {
        val ideas = CompanionLogic.dailyStarters(store.profile, System.currentTimeMillis() / 86_400_000L)
        AlertDialog.Builder(this).setTitle("Conversation ideas")
            .setItems(ideas.map { it.title }.toTypedArray()) { _, selected ->
                val edit = composerEdit ?: return@setItems
                edit.setText(ideas[selected].message)
                edit.setSelection(edit.text.length)
                edit.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startReply() {
        if (sending || modelBusy) return
        if (!LocalModel.ready()) { toast("Load a GGUF model in Privacy first."); return }
        val last = store.messages.lastOrNull()
        if (last?.role != "user") { toast("No unanswered user message to reply to"); return }
        val revision = conversationRevision
        val ticket = replyGate.begin()
        val profileSnapshot = store.profile
        val historySnapshot = store.messages.toList()
        val lengthSnapshot = store.replyLength
        val startedAt = android.os.SystemClock.elapsedRealtime()
        streamingPreview = ""
        LocalModel.beginRequest() // resets previous stop before the executor starts
        sending = true
        status = "Generating locally · ${lengthSnapshot.title} · tap Stop to cancel."
        if (pane == "Chat") render()
        worker.execute {
            val progress = ReplyProgress { partial ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && sending &&
                        replyGate.mayCommit(ticket) && revision == conversationRevision) {
                        streamingPreview = partial.take(3000)
                        if (pane == "Chat") {
                            previewLabel?.text = streamingPreview
                            chatScroll.post { if (pane == "Chat" && sending)
                                chatScroll.fullScroll(View.FOCUS_DOWN) }
                        }
                    }
                }
            }
            val result = runCatching {
                LocalModel.respond(profileSnapshot, historySnapshot, lengthSnapshot, progress)
            }
            val diagnostics = if (result.isSuccess) {
                runCatching { LocalModel.lastReplyDiagnostics() }.getOrDefault("")
            } else ""
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!replyGate.mayCommit(ticket) || revision != conversationRevision || store.messages.lastOrNull() != last) {
                    replyGate.finish(ticket)
                    if (revision == conversationRevision) status = "Generation stopped. Retry your last message when ready."
                    sending = false // History was cleared/replaced while inference was running
                    streamingPreview = ""
                    if (pane == "Chat") render()
                    return@runOnUiThread
                }
                replyGate.finish(ticket)
                result.onSuccess { output ->
                    store.messages.add(ChatEntry("assistant", output.take(3000), System.currentTimeMillis()))
                    try {
                        store.save()
                        conversationRevision++
                        val seconds = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000.0
                        lastInferenceDiagnostics = diagnostics
                        status = "Reply ready in ${"%.1f".format(java.util.Locale.US, seconds)} s · ${lengthSnapshot.title}"
                    } catch (e: Exception) {
                        store.messages.removeAt(store.messages.lastIndex)
                        status = "Local reply failed to save: ${e.message}"
                        toast("Reply not saved; retry is available")
                    }
                }.onFailure { error ->
                    status = "Local reply failed: ${error.message ?: "unknown error"}"
                    toast("Local reply stopped or failed. Retry is available.")
                }
                sending = false
                streamingPreview = ""
                if (pane == "Chat") render()
            }
        }
    }

    private fun personalityPane() {
        section("Personality Studio")
        content.addView(label("Choose a starting personality and tune the traits individually.", 14f, muted))
        space()
        content.addView(label("Companion name", 14f))
        val nameEdit = input(store.profile.name, "Companion name")
        content.addView(nameEdit)
        space(8)
        content.addView(button("Save name") {
            store.profile = store.profile.copy(name = nameEdit.text.toString().trim().take(60).ifBlank { "Mira" })
            store.save(); toast("Name saved")
        })
        space()
        content.addView(label("Personality presets", 15f, light, true))
        val presets = CompanionLogic.presets.keys.toList()
        content.addView(dropdown(presets, store.profile.preset) { chosen ->
            store.profile = store.profile.copy(preset = chosen, traits = CompanionLogic.presets.getValue(chosen))
            store.save()
            render()
        })
        space()
        content.addView(label("Conversation style", 15f, light, true))
        content.addView(dropdown(CompanionLogic.conversationStyles, store.profile.conversationStyle) { chosen ->
            store.profile = store.profile.copy(conversationStyle = chosen)
            store.save(); render()
        })
        space()
        content.addView(label("Relationship pace", 15f, light, true))
        content.addView(dropdown(CompanionLogic.relationshipPaces, store.profile.relationshipPace) { chosen ->
            store.profile = store.profile.copy(relationshipPace = chosen)
            store.save(); render()
        })
        space()
        listOf("Warmth", "Playfulness", "Confidence", "Romance", "Humor", "Curiosity", "Initiative").forEach { trait ->
            val t = store.profile.traits
            val value = when (trait) { "Warmth" -> t.warmth; "Playfulness" -> t.playfulness
                "Confidence" -> t.confidence; "Romance" -> t.romance; "Humor" -> t.humor
                "Curiosity" -> t.curiosity; else -> t.initiative }
            content.addView(label("$trait: $value / 100", 14f))
            val seek = SeekBar(this).apply { max = 100; progress = value; progressTintList = android.content.res.ColorStateList.valueOf(violet) }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {
                    val old = store.profile.traits
                    val updated = when (trait) {
                        "Warmth" -> old.copy(warmth = s.progress)
                        "Playfulness" -> old.copy(playfulness = s.progress)
                        "Confidence" -> old.copy(confidence = s.progress)
                        "Romance" -> old.copy(romance = s.progress)
                        "Humor" -> old.copy(humor = s.progress)
                        "Curiosity" -> old.copy(curiosity = s.progress)
                        else -> old.copy(initiative = s.progress)
                    }
                    store.profile = store.profile.copy(traits = updated)
                    store.save(); render()
                }
            })
            content.addView(seek)
        }
        space(10)
        section("Interests & hobbies")
        content.addView(label("Define what your companion likes to discuss (e.g. fantasy, music, games).", 13f, muted))
        val interestsEdit = input(store.profile.interests, "Fantasy stories, art, music...", 2)
        content.addView(interestsEdit)
        space(8)
        content.addView(button("Save interests") {
            store.profile = store.profile.copy(interests = interestsEdit.text.toString().take(500))
            store.save(); toast("Character interests saved")
        })
        space(10)
        section("Remember important details")
        content.addView(label("Facts entered here remain on your device and are included in the AI's context.", 13f, muted))
        val memories = input(store.profile.memories, "E.g. My favorite hobbies are...", 4)
        content.addView(memories)
        space(8)
        content.addView(button("Save memories") {
            store.profile = store.profile.copy(memories = memories.text.toString().take(3000))
            store.save(); toast("Private memories saved")
        })
    }

    /** Dialog choices avoid OEM white spinner surfaces covering dark text. */
    private fun dropdown(values: List<String>, selected: String, changed: (String) -> Unit): Button =
        button("Selected: $selected  •  Tap to change") {
            AlertDialog.Builder(this)
                .setTitle("Choose an option")
                .setSingleChoiceItems(values.toTypedArray(), values.indexOf(selected)) { dialog, index ->
                    dialog.dismiss()
                    if (values[index] != selected) changed(values[index])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

    private fun appearancePane() {
        section("Appearance Studio")
        content.addView(label("Choose an illustrated appearance or import a photo to create an offline stylized variation.", 14f, muted))
        val file = File(filesDir, "portrait.png")
        val image = ImageView(this).apply {
            val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)
                         else AvatarStudio.create(this@MainActivity, store.profile.appearance)
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        content.addView(image, LinearLayout.LayoutParams(-1, dp(240)))
        val options = listOf(
            Triple("Hair", listOf("Midnight", "Chestnut", "Blonde", "Silver", "Rose"), store.profile.appearance.hair),
            Triple("Eyes", listOf("Violet", "Blue", "Green", "Amber", "Brown"), store.profile.appearance.eyes),
            Triple("Skin", listOf("Fair", "Warm", "Olive", "Golden", "Deep"), store.profile.appearance.skin),
            Triple("Outfit", listOf("Casual", "Cozy", "Formal", "Fantasy"), store.profile.appearance.outfit),
            Triple("Style", listOf("Illustrated", "Soft", "Vivid", "Graphic Novel", "Monochrome"), store.profile.appearance.style)
        )
        options.forEach { (labelText, values, selected) ->
            content.addView(label(labelText, 15f, light, true))
            content.addView(dropdown(values, selected) { chosen ->
                val a = store.profile.appearance
                val changed = when (labelText) {
                    "Hair" -> a.copy(hair = chosen); "Eyes" -> a.copy(eyes = chosen)
                    "Skin" -> a.copy(skin = chosen); "Outfit" -> a.copy(outfit = chosen)
                    else -> a.copy(style = chosen)
                }
                store.profile = store.profile.copy(appearance = changed)
                store.save()
                AvatarStudio.create(this, changed).recycle()
                render()
            })
        }
        space()
        content.addView(button("Import reference image") { chooseImage() })
        space(8)
        content.addView(button("Create local avatar variation") {
            AvatarStudio.create(this, store.profile.appearance).recycle(); toast("Avatar created offline"); render()
        })
        space(8)
        content.addView(button("Remove reference image") {
            File(filesDir, "reference.png").delete()
            AvatarStudio.create(this, store.profile.appearance).recycle(); render()
        })
        space()
        content.addView(label("V0.4 uses local portrait drawing and image filters, not diffusion-based AI image generation. A true image-to-image model is planned for a later Android milestone.", 13f, muted))
    }

    /** The browser performs any external download; Eclipse itself never requests internet. */
    private fun openStarterModelDownload() {
        AlertDialog.Builder(this)
            .setTitle("Download a local GGUF model")
            .setMessage(
                "Open the official Qwen 2.5 1.5B Instruct Q4_K_M file in your phone browser? " +
                "It is about 1.12 GB. Browser internet and storage are required for this " +
                "one-time download. Eclipse remains offline. After download, return and use " +
                "Import GGUF model from phone. Model compatibility and speed vary by device."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ModelSetupGuide.QWEN_DOWNLOAD_URL))
                try {
                    startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    toast("No browser found. Open the Qwen GGUF download URL in a browser.")
                }
            }.show()
    }

    private fun installedModelDescription(): String {
        val model = File(filesDir, "companion.gguf")
        if (!model.exists()) return "No model imported yet."
        return try {
            val version = model.inputStream().use { ModelFiles.inspectHeader(it) }
            "Imported GGUF v$version · ${ModelSetupGuide.sizeLabel(model.length())} · " +
                if (LocalModel.ready()) "Loaded in memory" else "Not loaded into memory"
        } catch (e: Exception) {
            "Imported file needs attention: ${e.message ?: "GGUF header not recognized"}"
        }
    }

    private fun privacyPane() {
        section("Screen capture")
        content.addView(label(
            "Screenshots and screen recording are blocked by default for privacy. " +
                "You may allow them temporarily to report display problems. Turn protection on afterward.",
            13f, muted
        ))
        content.addView(button(
            if (captureSession.isAllowed) "Screen capture: ALLOWED  •  Tap to block"
            else "Screen capture: BLOCKED  •  Tap to allow"
        ) {
            if (captureSession.isAllowed) {
                setScreenshotsAllowed(false)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Allow screen capture?")
                    .setMessage("Screenshots, screen recordings and recent-app previews may expose visible conversations. Use only for troubleshooting.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Allow") { _, _ -> setScreenshotsAllowed(true) }
                    .show()
            }
        })
        space(8)
        content.addView(button("Copy UI layout diagnostics") { copyLayoutDiagnostics() })
        content.addView(label(
            "Copies only Android screen dimensions, font scale and safe-area padding; no conversations or companion memories.",
            12f, muted
        ))
        space(18)
        section("Set up an offline AI model")
        content.addView(label(
            "Eclipse has no INTERNET permission. Download an instruction-tuned GGUF once " +
                "using your phone's browser, then import it from Downloads. " +
                "The AI runs on your phone after loading.",
            14f, muted
        ))
        space(8)
        content.addView(label("Step 1 · Get a GGUF model", 16f, light, true))
        content.addView(label(
            "Starter suggestion: Qwen 2.5 1.5B Instruct Q4_K_M (about 1.12 GB). " +
                "Its official download opens in your browser, not inside Eclipse. " +
                "Other compatible GGUF chat models can also be imported.",
            13f, muted
        ))
        content.addView(button("Open official Qwen GGUF download") { openStarterModelDownload() })
        space(10)
        content.addView(label("Step 2 · Import from phone storage", 16f, light, true))
        content.addView(button("Import GGUF model from phone") { chooseModel() })
        content.addView(label("Saved model: ${installedModelDescription()}", 14f, light))
        content.addView(label("Free storage: ${ModelSetupGuide.sizeLabel(filesDir.usableSpace)}", 13f, muted))
        space(10)
        content.addView(label("Step 3 · Load into device memory", 16f, light, true))
        content.addView(button("Load local model") { loadModel() })
        content.addView(label(status, 13f, muted))
        space(8)
        content.addView(button("Unload model from memory") {
            if (modelBusy) { toast("Wait for the current model operation"); return@button }
            modelBusy = true
            replyGate.cancel()
            LocalModel.cancel()
            worker.execute {
                LocalModel.unload()
                runOnUiThread { modelBusy = false; status = "Model unloaded"; if (pane == "Privacy") render() }
            }
        })
        space(18)
        section("Response speed")
        if (lastInferenceDiagnostics.isNotBlank()) {
            content.addView(label("Last reply · $lastInferenceDiagnostics", 12f, muted))
            space(6)
            content.addView(button("Copy local response performance") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Eclipse local performance",
                    lastInferenceDiagnostics))
                toast("Copied token counts and timings only, no chat contents")
            })
            space(12)
        }
        content.addView(label(
            "Quick (default) generates fewer tokens with less recent history. " +
                "Detailed replies can take significantly longer on a phone CPU. " +
                "Replies begin appearing as soon as the local engine starts generating.",
            13f, muted
        ))
        space(8)
        content.addView(button("Reply length: ${store.replyLength.title}  ·  Tap to change") {
            val choices = ReplyLength.entries.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Choose offline reply length")
                .setSingleChoiceItems(choices.map { it.title }.toTypedArray(),
                    choices.indexOf(store.replyLength)) { dialog, index ->
                    dialog.dismiss()
                    val previous = store.replyLength
                    store.replyLength = choices[index]
                    try { store.save() } catch (e: Exception) {
                        store.replyLength = previous
                        toast("Could not save reply preference: ${e.message}")
                    }
                    render()
                }
                .setNegativeButton("Cancel", null).show()
        })
        space(16)
        section("Conversation preferences")
        content.addView(label("Adult mode is optional and off by default. It only applies to consensual adult character conversations; actual output depends on your chosen local model.", 14f, muted))
        content.addView(button(if (store.profile.adultMode) "Adult mode: ON (tap to disable)" else "Adult mode: OFF (tap to enable)") {
            if (store.profile.adultMode) {
                store.profile = store.profile.copy(adultMode = false); store.save(); render()
            } else AlertDialog.Builder(this).setTitle("Enable 18+ conversation mode?")
                .setMessage("This enables a mature-conversation prompt for the companion. Both characters must be adults; consent and boundaries apply.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Enable") { _, _ ->
                    store.profile = store.profile.copy(adultMode = true); store.save(); render()
                }.show()
        })
        space(18)
        section("App updates & privacy")
        val isDevelopmentPreview = packageName != "dev.privatecompanion.eclipse"
        content.addView(label(
            if (isDevelopmentPreview)
                "This is a separate test installation. It cannot update earlier Eclipse previews " +
                    "or automatically move their private data. Keep the old installation and export " +
                    "an encrypted backup before uninstalling any app."
            else
                "This installation uses the stable Eclipse package ID. Future updates must " +
                    "be signed with the same owner certificate and a higher versionCode to " +
                    "preserve local settings, history, and model files.",
            13f, muted
        ))
        space(12)
        section("Portable encrypted backup")
        content.addView(label(
            "Export your companion personality, memories and up to 1,000 chat messages " +
                "as an encrypted password-protected .eclipsebackup file. " +
                "The file can be restored in another Eclipse version that supports portable backups. " +
                "Portraits, reference images and GGUF model files are NOT included. " +
                "Older Eclipse versions cannot export in this format.",
            13f, muted
        ))
        space(8)
        content.addView(button("Export encrypted chats & settings") { beginBackupExport() })
        space(8)
        content.addView(button("Import encrypted chats & settings") { chooseBackupImport() })
        space(8)
        content.addView(label(
            "Choose a unique password with at least 12 characters. It cannot be recovered. " +
                "Import replaces this installation's companion settings and chats only, " +
                "after showing a separate confirmation. Keep your backup file private.",
            12f, muted
        ))
        space(18)
        section("Your data")
        content.addView(label("Chat history and settings are encrypted with a key in Android Keystore. Images and imported model files are private app files. Uninstalling or clearing app data deletes access to them; flash-memory secure erasure is not guaranteed.", 13f, muted))
        space()
        content.addView(button("Delete conversation history") {
            AlertDialog.Builder(this).setTitle("Delete all chats?")
                .setMessage("This removes saved messages but retains the character, memories, model and image.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    conversationRevision++
                    replyGate.cancel()
                    LocalModel.cancel()
                    store.wipeHistory()
                    toast("History deleted")
                    render()
                }
                .show()
        })
        space(8)
        content.addView(button("Erase all Eclipse data") {
            AlertDialog.Builder(this).setTitle("Erase ALL local companion data?")
                .setMessage("Deletes encrypted history, character settings, memories, images and imported model. This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Erase") { _, _ ->
                    conversationRevision++
                    replyGate.cancel()
                    LocalModel.cancel()
                    worker.execute {
                        LocalModel.unload()
                        runOnUiThread { store.wipeEverything(); finish() }
                    }
                }.show()
        })
    }

    private fun passwordInput(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
        setTextColor(light)
        setHintTextColor(muted)
        textSize = 16f
        background = bg(panel, dp(10).toFloat())
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun beginBackupExport() {
        if (backupBusy || modelBusy || sending) {
            toast("Finish the current operation before exporting a backup")
            return
        }
        val first = passwordInput("Backup password (12+ characters)")
        val second = passwordInput("Confirm backup password")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(first)
            addView(second)
        }
        AlertDialog.Builder(this)
            .setTitle("Create encrypted backup")
            .setMessage("The password is required for recovery. A forgotten password cannot be reset. " +
                "Only character settings and chats are included, not model files or images.")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Encrypt") { _, _ ->
                val pass = first.text.toString().toCharArray()
                val confirm = second.text.toString().toCharArray()
                try {
                    if (pass.size !in 12..256 || !pass.contentEquals(confirm)) {
                        toast("Use a matching password of 12–256 characters")
                    } else {
                        backupBusy = true
                        val plain = store.exportPlaintextSnapshot()
                        worker.execute {
                            try {
                                val encrypted = PortableBackup.encrypt(plain, pass)
                                runOnUiThread {
                                    if (isFinishing || isDestroyed) return@runOnUiThread
                                    pendingEncryptedExport = encrypted
                                    val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_TITLE,
                                            "Eclipse-Companion-${System.currentTimeMillis()}.eclipsebackup")
                                    }
                                    try {
                                        startActivityForResult(picker, requestBackupCreate)
                                    } catch (e: Exception) {
                                        pendingEncryptedExport = null
                                        backupBusy = false
                                        toast("Cannot open document picker: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    backupBusy = false
                                    toast("Backup encryption failed: ${e.message}")
                                }
                            } finally {
                                plain.fill(0)
                                pass.fill(0.toChar())
                            }
                        }
                    }
                } catch (e: Exception) {
                    backupBusy = false
                    toast("Cannot prepare backup: ${e.message}")
                } finally {
                    confirm.fill(0.toChar())
                }
            }.show()
    }

    private fun finishBackupExport(uri: Uri) {
        val encrypted = pendingEncryptedExport
        pendingEncryptedExport = null
        if (encrypted == null) {
            backupBusy = false
            toast("Backup preparation was interrupted; retry export")
            return
        }
        worker.execute {
            try {
                val stream = contentResolver.openOutputStream(uri, "w")
                    ?: error("Selected document cannot be written")
                stream.use { out ->
                    out.write(encrypted)
                    out.flush()
                }
                runOnUiThread {
                    backupBusy = false
                    toast("Encrypted backup written. Keep its password and file safely.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    backupBusy = false
                    toast("Backup write failed; delete any incomplete file: ${e.message}")
                }
            } finally {
                encrypted.fill(0)
            }
        }
    }

    private fun chooseBackupImport() {
        if (backupBusy || sending || modelBusy) {
            toast("Finish the current operation before importing a backup")
            return
        }
        backupBusy = true
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(picker, requestBackupOpen)
        } catch (e: Exception) {
            backupBusy = false
            toast("Cannot open backup picker: ${e.message}")
        }
    }

    private fun beginBackupImport(uri: Uri) {
        val passwordField = passwordInput("Backup password")
        AlertDialog.Builder(this).setTitle("Unlock encrypted backup")
            .setMessage("Choose the .eclipsebackup file's password. No changes will be made " +
                "until the backup has passed validation and you confirm replacement.")
            .setView(passwordField)
            .setNegativeButton("Cancel") { _, _ -> backupBusy = false }
            .setOnCancelListener { backupBusy = false }
            .setPositiveButton("Unlock") { _, _ ->
                val pass = passwordField.text.toString().toCharArray()
                worker.execute {
                    try {
                        val input = contentResolver.openInputStream(uri)
                            ?: error("Selected backup cannot be read")
                        val bytes = PortableBackup.readBounded(input)
                        val snapshot = try { store.decodePortable(bytes, pass) }
                            finally { bytes.fill(0) }
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            AlertDialog.Builder(this)
                                .setTitle("Replace chats and character settings?")
                                .setMessage(
                                    "This backup contains '${snapshot.profile.name}' and " +
                                        "${snapshot.messages.size} chat messages. " +
                                        "Restoring REPLACES the current chat history and companion " +
                                        "settings. Your existing imported GGUF and image files stay unchanged. " +
                                        "Make a backup of the current chats first if needed."
                                )
                                .setNegativeButton("Keep current data") { _, _ -> backupBusy = false }
                                .setOnCancelListener { backupBusy = false }
                                .setPositiveButton("Replace") { _, _ ->
                                    try {
                                        conversationRevision++
                                        replyGate.cancel()
                                        LocalModel.cancel()
                                        store.restorePortable(snapshot)
                                        draftText = ""
                                        backupBusy = false
                                        navigateTo("Chat")
                                        toast("Encrypted backup restored")
                                    } catch (e: Exception) {
                                        backupBusy = false
                                        toast("Restore failed; original data kept: ${e.message}")
                                    }
                                }.show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            backupBusy = false
                            toast("Backup unlock/validation failed: incorrect password or invalid file")
                        }
                    } finally {
                        pass.fill(0.toChar())
                    }
                }
            }.show()
    }

    private fun chooseModel() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
        }
        startActivityForResult(i, requestModel)
    }
    private fun chooseImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }
        startActivityForResult(i, requestImage)
    }

    @Deprecated("Activity result retained to keep this native Android starter dependency-light")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestBackupCreate) {
            if (resultCode == RESULT_OK && data?.data != null) finishBackupExport(data.data!!)
            else {
                pendingEncryptedExport?.fill(0)
                pendingEncryptedExport = null
                backupBusy = false
            }
            return
        }
        if (requestCode == requestBackupOpen) {
            if (resultCode == RESULT_OK && data?.data != null) beginBackupImport(data.data!!)
            else backupBusy = false
            return
        }
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            requestImage -> try {
                AvatarStudio.fromReference(this, uri)
                AvatarStudio.create(this, store.profile.appearance).recycle()
                navigateTo("Appearance")
                toast("Reference transformed and stored privately")
            } catch (e: Exception) { toast("Image import failed: ${e.message}") }
            requestModel -> importModel(uri)
        }
    }

    private fun importModel(uri: Uri) {
        if (sending || modelBusy) { toast("Finish the current model operation or reply first"); return }
        modelBusy = true
        status = "Importing GGUF file into private local storage..."
        navigateTo("Privacy")
        worker.execute {
            try {
                val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                val plan = ModelSetupGuide.checkImport(size, filesDir.usableSpace)
                if (!plan.permitted) error(plan.explanation)
                val stream = contentResolver.openInputStream(uri) ?: error("Cannot open model file")
                var lastProgress = 0L
                // Serial worker ensures existing generation finishes before model replacement.
                val copied = ModelFiles.importInto(filesDir, stream, progress = { count ->
                    if (count - lastProgress >= 64L * 1024 * 1024) {
                        lastProgress = count
                        runOnUiThread {
                            status = "Importing model: ${count / (1024 * 1024)} MiB copied..."
                            if (pane == "Privacy") render()
                        }
                    }
                })
                // Unload old weights before the owner requests the new model.
                LocalModel.unload()
                runOnUiThread {
                    modelBusy = false
                    status = "Model imported (${copied / (1024 * 1024)} MiB). Tap Load local model."
                    if (pane == "Privacy") render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    modelBusy = false
                    status = "Import failed; previous model preserved: ${e.message}"
                    if (pane == "Privacy") render()
                }
            }
        }
    }

    private fun loadModel() {
        if (sending || modelBusy) { toast("Finish the current model operation or reply first"); return }
        val file = File(filesDir, "companion.gguf")
        if (!file.exists()) { toast("Import a GGUF model first"); return }
        try {
            file.inputStream().use { ModelFiles.inspectHeader(it) }
        } catch (e: Exception) {
            status = "Imported file is not a supported GGUF: ${e.message}"
            render()
            return
        }
        modelBusy = true
        status = "Loading model locally. This may use several GB of RAM."
        render()
        worker.execute {
            val result = try { LocalModel.load(file.absolutePath) } catch (_: Exception) { false }
            runOnUiThread { modelBusy = false; status = if (result) "Local model loaded and ready." else "Model failed to load. Check RAM, model format and the compiled native engine."; render() }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("eclipse_pane", pane)
        outState.putString("eclipse_draft", draftText.take(3000))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        // Revocation also updates the button label after returning from another app.
        if (refreshPrivacyOnResume && ::composerDock.isInitialized && pane == "Privacy") {
            refreshPrivacyOnResume = false
            render()
        }
    }

    override fun onStop() {
        // The AI should not complete and save a reply when the app leaves the foreground.
        if (sending) {
            replyGate.cancel()
            LocalModel.cancel()
            status = "Generation stopped when Eclipse left the foreground. Retry when ready."
        }
        // Avoid capturing sensitive content in the recent-apps view once Eclipse is backgrounded.
        if (captureSession.isAllowed) {
            captureSession.background()
            updateCaptureSecurity()
            refreshPrivacyOnResume = true
        }
        super.onStop()
    }

    override fun onDestroy() {
        replyGate.cancel()
        LocalModel.cancel()
        pendingEncryptedExport?.fill(0)
        pendingEncryptedExport = null
        super.onDestroy()
        // Do not unload the process-wide singleton during Activity recreation:
        // the replacement Activity may already be using the same local model.
        if (!isChangingConfigurations) worker.execute { LocalModel.unload() }
        worker.shutdown()
    }
}
