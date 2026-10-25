package dev.privatecompanion.eclipse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
    private var sending = false
    private var conversationRevision = 0L
    private val replyGate = ReplySessionGate()
    private var draftText = ""
    private var modelBusy = false
    private var pane = "Chat"
    private var status = "Import a GGUF model to begin private AI conversations."
    private val violet = Color.rgb(170, 133, 238)
    private val light = Color.rgb(239, 231, 252)
    private val muted = Color.rgb(181, 170, 201)
    private val dark = Color.rgb(29, 25, 45)
    private val panel = Color.rgb(45, 38, 67)
    private val requestModel = 201
    private val requestImage = 202

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

    /** Android 15+ places content behind status, cutout and navigation bars. */
    private fun applySafeInsets(view: View) {
        if (Build.VERSION.SDK_INT >= 35) {
            view.setOnApplyWindowInsetsListener { target, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                target.setPadding(safe.left, safe.top, safe.right, safe.bottom)
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
        if (store.screenshotsAllowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun setScreenshotsAllowed(allow: Boolean) {
        val before = store.screenshotsAllowed
        if (!allow) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store.screenshotsAllowed = allow
        try {
            store.save()
            updateCaptureSecurity()
            render()
        } catch (e: Exception) {
            store.screenshotsAllowed = before
            updateCaptureSecurity()
            toast("Could not save screenshot setting: ${e.message}")
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun bg(color: Int, radius: Float = 20f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun pad(v: View, x: Int = 18, y: Int = 12) = v.setPadding(dp(x), dp(y), dp(x), dp(y))

    private fun label(text: String, size: Float = 16f, color: Int = light, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

    private fun input(text: String = "", hint: String = "", lines: Int = 1): EditText = EditText(this).apply {
        setText(text); this.hint = hint
        setTextColor(light); setHintTextColor(muted)
        textSize = 15f
        background = bg(panel, 18f)
        pad(this)
        if (lines > 1) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = lines }
        else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private fun button(title: String, onClick: () -> Unit): Button = Button(this).apply {
        text = title
        textSize = 14f
        isAllCaps = false
        maxLines = 2
        gravity = Gravity.CENTER
        minimumWidth = 0
        minimumHeight = dp(48)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        setTextColor(Color.WHITE)
        // Avoid OEM themes tinting the button into an opaque white rectangle.
        backgroundTintList = null
        background = bg(Color.rgb(100, 68, 151), dp(12).toFloat())
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
        val titleBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; pad(this, 16, 6) }
        titleBar.addView(label("ECLIPSE  ·  PRIVATE COMPANION", 17f, light, true))
        titleBar.addView(label("Offline only  •  On-device conversations", 12f, muted))
        root.addView(titleBar)
        // Two-column tabs remain legible on narrow screens and with larger fonts.
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; pad(this, 8, 2) }
        listOf(listOf("Chat", "Personality"), listOf("Appearance", "Privacy")).forEach { names ->
            val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            names.forEach { name ->
                val tab = button(name) { pane = name; render() }
                if (pane == name) tab.background = bg(Color.rgb(125, 86, 184), dp(12).toFloat())
                tabRow.addView(tab, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(2))
                })
            }
            tabs.addView(tabRow)
        }
        root.addView(tabs)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        chatScroll = scroll
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        render()
    }

    private fun render() {
        content.removeAllViews()
        when (pane) {
            "Chat" -> chatPane()
            "Personality" -> personalityPane()
            "Appearance" -> appearancePane()
            else -> privacyPane()
        }
        if (pane == "Chat") chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun chatPane() {
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
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
            background = bg(panel, 20f)
            clipToOutline = true
        }
        header.addView(image, LinearLayout.LayoutParams(dp(84), dp(84)).apply { rightMargin = dp(12) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(store.profile.name, 23f, light, true))
            addView(label(store.profile.preset + " · " + if (store.profile.adultMode) "Adult mode" else "Everyday mode", 14f, muted))
            addView(label(if (LocalModel.ready()) "Local model ready" else "Model not loaded", 13f, violet))
        })
        content.addView(header)
        space()
        if (status.startsWith("Local reply failed") || status.startsWith("Generation") || sending) {
            content.addView(label(status, 13f, muted))
            space(4)
        }
        if (store.messages.isEmpty()) {
            content.addView(label("Your conversation begins here. Import and load an on-device model, then say hello.", 15f, muted))
        }
        for (m in store.messages.takeLast(50)) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; pad(this, 13, 10) }
            box.background = bg(if (m.role == "user") Color.rgb(65, 52, 89) else panel)
            box.addView(label(if (m.role == "user") "YOU" else store.profile.name.uppercase(), 12f, violet, true))
            box.addView(label(m.content, 15f))
            content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })
        }
        if (store.messages.size > 50) content.addView(label("Showing the most recent 50 messages. Older messages remain saved locally.", 12f, muted))
        val edit = input(draftText, "Type a message to ${store.profile.name}...", 3)
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                draftText = s?.toString()?.take(3000) ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        content.addView(edit, LinearLayout.LayoutParams(-1, -2))
        space(8)
        content.addView(label("Conversation ideas · prepared on device (tap to edit)", 12f, muted))
        val dateIndex = System.currentTimeMillis() / 86_400_000L
        CompanionLogic.dailyStarters(store.profile, dateIndex).forEach { idea ->
            content.addView(button(idea.title) {
                edit.setText(idea.message)
                edit.setSelection(edit.text.length)
                edit.requestFocus()
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(5) })
        }
        space(8)
        if (!sending && store.messages.lastOrNull()?.role == "user") {
            content.addView(button("Generate/retry reply to last message") { startReply() },
                LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(7) })
        }
        if (sending) {
            content.addView(button("Stop current response") {
                replyGate.cancel()
                LocalModel.cancel()
                status = "Stopping local generation..."
                if (pane == "Chat") render()
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(7) })
        }
        val send = button(if (sending) "Thinking on device..." else "Send message") {
            if (sending || modelBusy) return@button
            if (!LocalModel.ready()) { toast("Load a GGUF model in Privacy first."); return@button }
            val userText = edit.text.toString().trim().take(3000)
            if (userText.isBlank()) return@button
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
        send.isEnabled = !sending && !modelBusy
        content.addView(send, LinearLayout.LayoutParams(-1, dp(52)))
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
        LocalModel.beginRequest() // resets previous stop before the executor starts
        sending = true
        status = "Generating locally; press Stop to cancel."
        if (pane == "Chat") render()
        worker.execute {
            val result = runCatching { LocalModel.respond(profileSnapshot, historySnapshot) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!replyGate.mayCommit(ticket) || revision != conversationRevision || store.messages.lastOrNull() != last) {
                    replyGate.finish(ticket)
                    if (revision == conversationRevision) status = "Generation stopped. Retry your last message when ready."
                    sending = false // History was cleared/replaced while inference was running
                    if (pane == "Chat") render()
                    return@runOnUiThread
                }
                replyGate.finish(ticket)
                result.onSuccess { output ->
                    store.messages.add(ChatEntry("assistant", output.take(3000), System.currentTimeMillis()))
                    try {
                        store.save()
                        conversationRevision++
                        status = "Local reply generated."
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

    private fun privacyPane() {
        section("Local model & privacy")
        content.addView(label("The app has no INTERNET permission. No account or API key is used. Load a compatible local GGUF instruct/chat model for responses.", 14f, muted))
        space()
        content.addView(label("Model: " + if (File(filesDir, "companion.gguf").exists()) "Installed locally" else "Not imported", 15f))
        content.addView(label("Engine: " + if (LocalModel.ready()) "Ready" else "Not loaded", 15f, violet))
        content.addView(label(status, 13f, muted))
        space()
        content.addView(button("Import GGUF model from phone") { chooseModel() })
        space(8)
        content.addView(button("Load local model") { loadModel() })
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
        section("Screen capture")
        content.addView(label(
            "Screenshots and screen recording are blocked by default for privacy. " +
                "You may allow them temporarily to report display problems. Turn protection on afterward.",
            13f, muted
        ))
        content.addView(button(
            if (store.screenshotsAllowed) "Screen capture: ALLOWED  •  Tap to block"
            else "Screen capture: BLOCKED  •  Tap to allow"
        ) {
            if (store.screenshotsAllowed) {
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
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            requestImage -> try {
                AvatarStudio.fromReference(this, uri)
                AvatarStudio.create(this, store.profile.appearance).recycle()
                pane = "Appearance"; render()
                toast("Reference transformed and stored privately")
            } catch (e: Exception) { toast("Image import failed: ${e.message}") }
            requestModel -> importModel(uri)
        }
    }

    private fun importModel(uri: Uri) {
        if (sending || modelBusy) { toast("Finish the current model operation or reply first"); return }
        modelBusy = true
        status = "Importing GGUF file into private local storage..."
        pane = "Privacy"; render()
        worker.execute {
            try {
                val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                if (size in 0..1023) error("File is too small to be a GGUF model")
                if (size > ModelFiles.MAX_BYTES) error("Model exceeds 8 GiB starter limit")
                val needed = if (size > 0) size + 100L * 1024 * 1024 else 3L * 1024 * 1024 * 1024
                if (filesDir.usableSpace < needed) error("Not enough free storage space")
                val stream = contentResolver.openInputStream(uri) ?: error("Cannot open model file")
                var lastProgress = 0L
                // Serial worker ensures existing generation finishes before model replacement.
                val copied = ModelFiles.importInto(filesDir, stream) { count ->
                    if (count - lastProgress >= 64L * 1024 * 1024) {
                        lastProgress = count
                        runOnUiThread {
                            status = "Importing model: ${count / (1024 * 1024)} MiB copied..."
                            if (pane == "Privacy") render()
                        }
                    }
                }
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

    override fun onStop() {
        // The AI should not complete and save a reply when the app leaves the foreground.
        if (sending) {
            replyGate.cancel()
            LocalModel.cancel()
            status = "Generation stopped when Eclipse left the foreground. Retry when ready."
        }
        super.onStop()
    }

    override fun onDestroy() {
        replyGate.cancel()
        LocalModel.cancel()
        super.onDestroy()
        // Do not unload the process-wide singleton during Activity recreation:
        // the replacement Activity may already be using the same local model.
        if (!isChangingConfigurations) worker.execute { LocalModel.unload() }
        worker.shutdown()
    }
}
