package com.jarvis.ai

import android.Manifest
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.CalendarContract
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Android client for the user's self-hosted JARVIS companion. */
class MainActivity : Activity() {
    private var webView: WebView? = null
    private var baseUrl = ""
    private val prefs by lazy { getSharedPreferences("jarvis_connection", MODE_PRIVATE) }
    private val groqPrefs by lazy { getSharedPreferences("jarvis_groq_api", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cloudMessages = mutableListOf<JSONObject>()
    private var cloudTranscript: TextView? = null
    private var cloudScroll: ScrollView? = null
    private var cloudInput: EditText? = null
    private var cloudStatus: TextView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var wakeWordActive = false
    private var awaitingVoiceCommand = false
    private var pendingWakePermission = false
    private var cloudRequestPending = false
    private var wakeStartPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 8, 12)
        window.navigationBarColor = Color.rgb(5, 8, 12)
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty()
        if (!GroqKeyVault.read(this).isNullOrBlank()) showGroqChat() else showGroqSetup()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.rgb(225, 244, 255))
        background = GradientDrawable().apply {
            setColor(Color.rgb(18, 43, 62))
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.rgb(49, 85, 115))
        }
        setOnClickListener { action() }
    }

    private fun showSetup(message: String = "") {
        showGroqSetup(message)
    }

        private fun inputBackground() = GradientDrawable().apply {
        setColor(Color.rgb(7, 15, 23))
        cornerRadius = dp(9).toFloat()
        setStroke(dp(1), Color.rgb(42, 60, 77))
    }


    private fun showGroqSetup(message: String = "") {
        stopWakeWordListening()
        cloudTranscript = null
        cloudInput = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(5, 8, 12))
        }
        root.addView(label("J", 62f, Color.rgb(225, 244, 255), true).apply {
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 25, 37)); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(57, 94, 122))
            }
        }, LinearLayout.LayoutParams(dp(84), dp(84)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18)
        })
        root.addView(label("J A R V I S", 23f, Color.rgb(235, 246, 255), true).apply { gravity = android.view.Gravity.CENTER })
        root.addView(label("DIREKT MIT GROQ VERBINDEN", 10f, Color.rgb(115, 153, 181)).apply {
            gravity = android.view.Gravity.CENTER; setPadding(0, dp(6), 0, dp(24))
        })
        root.addView(label("Dein Groq API-Key", 18f, Color.rgb(225, 238, 249), true))
        root.addView(label(
            "Füge deinen Groq API-Key ein. JARVIS speichert ihn verschlüsselt mit Android Keystore. Du brauchst keinen Server, kein Terminal und keine zweite App. Chat-Nachrichten werden zur Antwortgenerierung an Groq übermittelt; sende keine Passwörter oder besonders vertraulichen Inhalte.",
            12f, Color.rgb(148, 169, 188)
        ).apply { setPadding(0, dp(8), 0, dp(14)) })
        root.addView(button("Groq API-Key erstellen") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }
            catch (_: Exception) { Toast.makeText(this, "Öffne console.groq.com/keys im Browser.", Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(12) })
        val keyExists = !GroqKeyVault.read(this).isNullOrBlank()
        val keyInput = EditText(this).apply {
            hint = if (keyExists) "Groq-Key gespeichert — neuen Key optional einfügen" else "Groq API-Key hier einfügen"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true); textSize = 13f
            setTextColor(Color.rgb(235, 246, 255)); setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = inputBackground()
            contentDescription = "Groq API-Key"
        }
        root.addView(keyInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        if (message.isNotBlank()) root.addView(label(message, 12f, Color.rgb(242, 138, 145)).apply { setPadding(0, 0, 0, dp(12)) })
        root.addView(button("Key speichern und JARVIS starten") {
            val entered = keyInput.text.toString().trim()
            val key = if (entered.isNotEmpty()) entered else GroqKeyVault.read(this)
            if (key.isNullOrBlank() || key.length < 20) {
                showGroqSetup("Bitte füge einen gültigen Groq API-Key ein."); return@button
            }
            try {
                if (entered.isNotEmpty()) GroqKeyVault.save(this, entered)
                groqPrefs.edit().putBoolean("selected_mode", true).apply()
                cloudMessages.clear()
                showGroqChat()
            } catch (_: Exception) {
                showGroqSetup("Der API-Key konnte nicht sicher gespeichert werden.")
            }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })
        if (keyExists) root.addView(button("Gespeicherten Groq-Key löschen") {
            AlertDialog.Builder(this).setTitle("Groq-Key löschen?")
                .setMessage("Der verschlüsselte Groq API-Key wird von diesem Gerät entfernt.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen") { _, _ ->
                    GroqKeyVault.clear(this); groqPrefs.edit().clear().apply()
                    cloudMessages.clear(); showGroqSetup()
                }.show()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        root.addView(label("Groq-Quotas und Preise können sich ändern. Prüfe vor Nutzung, ob dein Groq-Konto Abrechnung aktiviert hat; API-Aufrufe können dann kostenpflichtig sein. JARVIS setzt kein Ausgabenlimit durch und bezahlt nichts selbst.",
            10f, Color.rgb(178, 151, 111)).apply { setPadding(0, dp(16), 0, 0) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun showGroqChat() {
        stopWakeWordListening()
        webView?.apply { stopLoading(); destroy() }
        webView = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(5, 8, 12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(label("J A R V I S", 21f, Color.rgb(235, 246, 255), true), LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(label("● ONLINE", 10f, Color.rgb(93, 220, 176), true).apply {
            setPadding(0, 0, dp(10), 0)
        })
        header.addView(button("API-Key") { showGroqSetup() }, LinearLayout.LayoutParams(-2, dp(40)))
        root.addView(header)

        val core = JarvisCoreView(this).apply { contentDescription = "Animierter JARVIS-Kern" }
        root.addView(core, LinearLayout.LayoutParams(-1, dp(168)).apply {
            topMargin = dp(1); bottomMargin = dp(2)
        })
        root.addView(label("J.A.R.V.I.S. CORE  /  GROQ LINK  /  LOCAL TOOLS",
            9f, Color.rgb(103, 180, 222), true).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(5))
            letterSpacing = 0.08f
        })
        root.addView(label("Chat läuft über Groq. Die Tool-Kacheln öffnen Apps oder starten Systemdialoge; E-Mail-Inhalte und WhatsApp-Nachrichten werden nicht ohne separate Freigabe ausgelesen.",
            10f, Color.rgb(141, 161, 179)).apply { setPadding(dp(2), 0, dp(2), dp(7)) })
        val transcriptScroll = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 13, 20))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(26, 48, 65))
            }
            isFillViewport = false
        }
        val transcriptView = label("JARVIS: Ich bin bereit. Schreib mir eine Nachricht oder aktiviere die Spracheingabe, solange die App geöffnet ist.",
            14f, Color.rgb(225, 238, 249)).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        cloudScroll = transcriptScroll; cloudTranscript = transcriptView
        transcriptScroll.addView(transcriptView)
        root.addView(transcriptScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4); bottomMargin = dp(8) })
        val promptInput = EditText(this).apply {
            hint = "Schreib JARVIS eine Nachricht …"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2; maxLines = 4
            setTextColor(Color.rgb(235, 246, 255)); setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = inputBackground()
        }
        cloudInput = promptInput
        root.addView(promptInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(button("Senden") {
            val prompt = cloudInput?.text?.toString()?.trim().orEmpty()
            if (prompt.isNotBlank()) { cloudInput?.setText(""); sendGroqPrompt(prompt, groqPrefs.getBoolean("speak_replies", true)) }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(5) })
        root.addView(label("VERBUNDENE APP-TOOLS", 9f, Color.rgb(103, 180, 222), true).apply {
            setPadding(dp(2), dp(8), dp(2), dp(4)); letterSpacing = 0.08f
        })
        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolsRow.addView(button("▦ Kalender") { openCalendarTool() }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { rightMargin = dp(4) })
        toolsRow.addView(button("✉ E-Mail") { openEmailTool() }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        toolsRow.addView(button("◉ WhatsApp") { openWhatsAppTool() }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { leftMargin = dp(4) })
        root.addView(toolsRow)

        val voiceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        voiceRow.addView(button("„Jarvis“ aktivieren") { startWakeWordListening() }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { rightMargin = dp(4) })
        voiceRow.addView(button("Stimme / Audio") { showVoiceSettings() }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        voiceRow.addView(button("Stopp") { stopWakeWordListening(disablePreference = true); textToSpeech?.stop() }, LinearLayout.LayoutParams(0, dp(45), 0.65f).apply { leftMargin = dp(4) })
        root.addView(voiceRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        cloudStatus = label("VOICE READY · Wakeword aus. App für Sprachsteuerung geöffnet lassen.", 10f, Color.rgb(135, 158, 178))
            .apply { setPadding(dp(2), dp(5), dp(2), dp(3)) }
        root.addView(cloudStatus)
        setContentView(root)
        ensureTextToSpeech()
        autoStartWakeWordIfEnabled()
    }

        private fun autoStartWakeWordIfEnabled() {
        if (!groqPrefs.getBoolean("wakeword_enabled", false) ||
            cloudTranscript == null || wakeWordActive || wakeStartPending
        ) return
        wakeStartPending = true
        mainHandler.post {
            wakeStartPending = false
            if (!isFinishing && cloudTranscript != null && !wakeWordActive &&
                groqPrefs.getBoolean("wakeword_enabled", false)
            ) startWakeWordListening()
        }
    }

    override fun onResume() {
        super.onResume()
        autoStartWakeWordIfEnabled()
    }

    private fun appendCloudTranscript(line: String) {
        runOnUiThread {
            val transcript = cloudTranscript ?: return@runOnUiThread
            transcript.append("\n\n$line")
            cloudScroll?.post { cloudScroll?.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun sendGroqPrompt(prompt: String, speakReply: Boolean) {
        if (handleLocalToolCommand(prompt)) return
        if (cloudRequestPending) { Toast.makeText(this, "Eine JARVIS-Anfrage läuft bereits.", Toast.LENGTH_SHORT).show(); return }
        if (prompt.length > 4000) { Toast.makeText(this, "Bitte kürze deine Nachricht auf 4.000 Zeichen.", Toast.LENGTH_LONG).show(); return }
        val key = GroqKeyVault.read(this)
        if (key.isNullOrBlank()) { runOnUiThread { showGroqSetup("Der Groq API-Key fehlt. Bitte füge ihn erneut ein.") }; return }

        cloudMessages.add(JSONObject().put("role", "user").put("content", prompt))
        val requestMessages = JSONArray()
        requestMessages.put(JSONObject().put("role", "system").put("content",
            "Du bist JARVIS: ein ruhiger, souveräner, präziser Assistent mit futuristischem Ton. Antworte auf Deutsch, wenn der Nutzer Deutsch spricht. Erkläre anfängerfreundlich. Diese Android-App hat lokale Schnellaktionen zum Öffnen von Kalender, E-Mail und WhatsApp; das Öffnen erfolgt nur bei ausdrücklichem Nutzerwunsch. Ein Kalendereintrag wird über einen Android-Systemdialog vorbereitet und muss vom Nutzer abgeschlossen werden. Du kannst keine E-Mail-Inhalte oder WhatsApp-Nachrichten lesen, nicht selbst Nachrichten senden, keine Termine im Hintergrund speichern und keine Apps fernsteuern. Behaupte nie, etwas ausgeführt zu haben, wenn es nicht tatsächlich ausgeführt wurde. Erfinde keine Aktionen oder Fakten. Frage vor wichtigen oder irreversiblen Aktionen nach Bestätigung."))
        cloudMessages.takeLast(10).forEach { requestMessages.put(it) }
        appendCloudTranscript("Du: $prompt")
        updateCloudStatus("JARVIS denkt …")
        cloudRequestPending = true; cloudInput?.isEnabled = false
        Thread {
            var connection: javax.net.ssl.HttpsURLConnection? = null
            try {
                val payload = JSONObject().put("model", GROQ_MODEL).put("messages", requestMessages)
                    .put("stream", false).put("max_completion_tokens", 1024)
                connection = (URL(GROQ_CHAT_URL).openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 15000; readTimeout = 120000; doOutput = true
                    instanceFollowRedirects = false
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    try { connection.errorStream?.close() } catch (_: Exception) {}
                    val msg = when (statusCode) {
                        401, 403 -> "Groq hat den API-Key abgelehnt. Öffne API-Key und füge einen gültigen Key ein."
                        429 -> "Groq-Limit erreicht oder zu viele Anfragen. JARVIS kauft kein Guthaben hinzu. Prüfe die Groq Console."
                        402 -> "Groq verlangt Guthaben für diese Anfrage. JARVIS führt keine Zahlung aus."
                        400 -> "Groq hat die Anfrage abgelehnt. Prüfe das Modell und deinen API-Key."
                        else -> "Groq antwortet mit HTTP $statusCode. Prüfe Internetverbindung und Groq Console."
                    }
                    throw IllegalStateException(msg)
                }
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val answer = JSONObject(responseText).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty().trim()
                if (answer.isBlank()) throw IllegalStateException("Groq hat eine leere Antwort geliefert.")
                cloudMessages.add(JSONObject().put("role", "assistant").put("content", answer))
                runOnUiThread {
                    cloudRequestPending = false; cloudInput?.isEnabled = true
                    appendCloudTranscript("JARVIS: $answer")
                    updateCloudStatus("Verbunden mit Groq · Modell $GROQ_MODEL")
                    if (speakReply) speakWakeText(answer.take(1200))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    cloudRequestPending = false; cloudInput?.isEnabled = true
                    val msg = e.message?.take(240) ?: "Verbindung zu Groq fehlgeschlagen."
                    appendCloudTranscript("JARVIS: $msg"); updateCloudStatus("Anfrage fehlgeschlagen")
                    if (speakReply) speakWakeText("Entschuldigung, ich konnte gerade nicht antworten. $msg")
                }
            } finally { try { connection?.disconnect() } catch (_: Exception) {} }
        }.start()
    }

    private fun applyJarvisVoicePreset() {
        val pitch = groqPrefs.getFloat("voice_pitch", 0.82f)
        val rate = groqPrefs.getFloat("voice_rate", 0.90f)
        textToSpeech?.setPitch(pitch.coerceIn(0.65f, 1.25f))
        textToSpeech?.setSpeechRate(rate.coerceIn(0.70f, 1.15f))
    }

    private fun showVoiceSettings() {
        ensureTextToSpeech()
        val choices = arrayOf(
            "JARVIS · Tief & ruhig (empfohlen)",
            "Natürlich · Standardtempo",
            "Deutsche Systemstimme auswählen",
            if (groqPrefs.getBoolean("speak_replies", true)) "Antworten vorlesen: EIN" else "Antworten vorlesen: AUS",
            "Stimme testen"
        )
        AlertDialog.Builder(this).setTitle("JARVIS · Sprachsystem").setItems(choices) { _, which ->
            when (which) {
                0 -> {
                    groqPrefs.edit().putFloat("voice_pitch", 0.82f).putFloat("voice_rate", 0.90f).apply()
                    applyJarvisVoicePreset()
                    speakWakeText("System bereit. Ich bin JARVIS. Wie kann ich helfen?")
                }
                1 -> {
                    groqPrefs.edit().putFloat("voice_pitch", 1.0f).putFloat("voice_rate", 1.0f).apply()
                    applyJarvisVoicePreset()
                    speakWakeText("Hallo. Die natürliche Sprachausgabe ist aktiv.")
                }
                2 -> showInstalledGermanVoices()
                3 -> {
                    val enabled = !groqPrefs.getBoolean("speak_replies", true)
                    groqPrefs.edit().putBoolean("speak_replies", enabled).apply()
                    Toast.makeText(this, if (enabled) "Sprachausgabe eingeschaltet." else "Sprachausgabe ausgeschaltet.", Toast.LENGTH_SHORT).show()
                    if (enabled) speakWakeText("Sprachausgabe aktiviert.")
                }
                4 -> speakWakeText("Systemdiagnose abgeschlossen. JARVIS ist startklar und wartet auf deine Anweisung.")
            }
        }.setNegativeButton("Schließen", null).show()
    }

    private fun showInstalledGermanVoices() {
        val engine = textToSpeech
        if (engine == null) {
            Toast.makeText(this, "Sprachsystem wird initialisiert. Bitte erneut versuchen.", Toast.LENGTH_SHORT).show()
            return
        }
        val voices = engine.voices?.filter { it.locale.language == "de" }?.sortedBy { it.name } ?: emptyList()
        if (voices.isEmpty()) {
            Toast.makeText(this, "Keine deutsche Systemstimme gefunden. Installiere eine deutsche Stimme in den Android-Sprachoptionen.", Toast.LENGTH_LONG).show()
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (_: Exception) {}
            return
        }
        val names = voices.map { voice -> voice.name + if (voice.isNetworkConnectionRequired) " · online" else " · lokal" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Deutsche Stimme wählen").setItems(names) { _, which ->
            try {
                engine.voice = voices[which]
                groqPrefs.edit().putString("voice_name", voices[which].name).apply()
                Toast.makeText(this, "Stimme ausgewählt: ${voices[which].name}", Toast.LENGTH_SHORT).show()
                speakWakeText("Stimme aktiviert. JARVIS ist startklar.")
            } catch (_: Exception) {
                Toast.makeText(this, "Diese Systemstimme konnte nicht aktiviert werden.", Toast.LENGTH_LONG).show()
            }
        }.setNegativeButton("Abbrechen", null).show()
    }

    private fun openCalendarTool() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
            appendCloudTranscript("JARVIS · TOOL: Kalender geöffnet.")
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI))
                appendCloudTranscript("JARVIS · TOOL: Kalender-Systemdialog geöffnet.")
            } catch (_: Exception) {
                Toast.makeText(this, "Keine Kalender-App verfügbar. Installiere oder aktiviere eine Kalender-App.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openEmailTool() {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            appendCloudTranscript("JARVIS · TOOL: E-Mail-App zum Verfassen geöffnet. Noch nichts wurde gesendet.")
        } catch (_: Exception) {
            Toast.makeText(this, "Keine E-Mail-App verfügbar. Richte zuerst eine Mail-App ein.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openWhatsAppTool() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (intent != null) {
                startActivity(intent)
                appendCloudTranscript("JARVIS · TOOL: WhatsApp geöffnet. Es wurde keine Nachricht gesendet.")
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")))
                appendCloudTranscript("JARVIS · TOOL: WhatsApp-Webseite geöffnet. Es wurde keine Nachricht gesendet.")
            }
        } catch (_: Exception) {
            Toast.makeText(this, "WhatsApp konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleLocalToolCommand(prompt: String): Boolean {
        val text = prompt.trim()
        val lower = text.lowercase(Locale.GERMANY)
        val opens = listOf("öffne", "oeffne", "starte", "öffnen", "oeffnen", "open", "geh zu", "gehe zu").any { lower.contains(it) }
        val createsEvent = listOf("erstelle", "erstell", "plane", "trag ein", "trage ein", "hinzufügen", "hinzufuegen").any { lower.contains(it) } &&
            listOf("termin", "kalendereintrag", "meeting").any { lower.contains(it) }
        if (createsEvent) {
            val title = text.substringAfter("termin", text.substringAfter("Kalendereintrag", "Neuer JARVIS-Termin"))
                .trim().trim(':', '-', ' ')
                .ifBlank { "Neuer JARVIS-Termin" }.take(100)
            AlertDialog.Builder(this)
                .setTitle("Kalendereintrag vorbereiten?")
                .setMessage("Ich öffne jetzt den Kalender mit dem Titel „$title“. Bitte prüfe Datum und Uhrzeit und speichere den Termin selbst.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Kalender öffnen") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, title))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Keine kompatible Kalender-App gefunden.", Toast.LENGTH_LONG).show()
                    }
                }.show()
            appendCloudTranscript("JARVIS · TOOL: Kalendereintrag vorbereitet; Speichern bleibt deine Entscheidung.")
            updateCloudStatus("Lokales Tool · Kalender")
            return true
        }
        if (opens && lower.contains("whatsapp")) {
            openWhatsAppTool(); updateCloudStatus("Lokales Tool · WhatsApp"); return true
        }
        if (opens && (lower.contains("e-mail") || lower.contains("email") || lower.contains("mail"))) {
            openEmailTool(); updateCloudStatus("Lokales Tool · E-Mail"); return true
        }
        if (opens && (lower.contains("kalender") || lower.contains("calendar"))) {
            openCalendarTool(); updateCloudStatus("Lokales Tool · Kalender"); return true
        }
        return false
    }

    private fun updateCloudStatus(text: String) {
        runOnUiThread { cloudStatus?.text = text }
    }

    private fun startWakeWordListening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "Für die lokale Wakeword-Erkennung ist Android 12 oder neuer erforderlich.", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingWakePermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Toast.makeText(this, "Diese Android-Version stellt keine lokale Spracherkennung bereit. Aus Datenschutzgründen wird kein Cloud-Mikrofon-Fallback gestartet.", Toast.LENGTH_LONG).show()
            return
        }
        stopWakeWordListening()
        wakeWordActive = true
        awaitingVoiceCommand = false
        groqPrefs.edit().putBoolean("wakeword_enabled", true).apply()
        ensureTextToSpeech()
        try {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        updateCloudStatus(if (awaitingVoiceCommand) "Ich höre zu …" else "Ich höre auf „Jarvis“ … (App geöffnet lassen)")
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (wakeWordActive) scheduleListeningCycle(700)
                    }
                    override fun onResults(results: Bundle?) {
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        handleWakeRecognition(heard)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            updateCloudStatus("Wakeword aktiv: Sage „Jarvis“. App geöffnet lassen.")
            scheduleListeningCycle(250)
        } catch (_: Exception) {
            stopWakeWordListening()
            Toast.makeText(this, "Die lokale Spracherkennung konnte nicht gestartet werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureTextToSpeech() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(Locale.GERMANY)
                applyJarvisVoicePreset()
                val preferredVoice = groqPrefs.getString("voice_name", null)
                if (!preferredVoice.isNullOrBlank()) {
                    textToSpeech?.voices?.firstOrNull { it.name == preferredVoice }?.let { textToSpeech?.voice = it }
                }
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (wakeWordActive) scheduleListeningCycle(350)
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            if (wakeWordActive) scheduleListeningCycle(500)
                        }
                    }
                })
            }
        }
    }

    private fun speakWakeText(text: String) {
        val engine = textToSpeech
        if (engine == null) {
            scheduleListeningCycle(500)
            return
        }
        val utterance = "jarvis_" + System.currentTimeMillis()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance)
        if (result == TextToSpeech.ERROR) scheduleListeningCycle(500)
    }

    private fun handleWakeRecognition(heardText: String) {
        if (!wakeWordActive) return
        val heard = heardText.trim()
        if (awaitingVoiceCommand) {
            awaitingVoiceCommand = false
            if (heard.isNotBlank()) {
                sendGroqPrompt(heard, true)
            } else {
                scheduleListeningCycle(350)
            }
            return
        }
        if (Regex("\\bjarvis\\b", RegexOption.IGNORE_CASE).containsMatchIn(heard)) {
            awaitingVoiceCommand = true
            updateCloudStatus("JARVIS erkannt. Ich antworte …")
            speakWakeText("Ich bin startklar. Was kann ich für dich tun?")
        } else {
            scheduleListeningCycle(350)
        }
    }

    private fun scheduleListeningCycle(delayMs: Long) {
        mainHandler.postDelayed({
            if (!wakeWordActive || isFinishing) return@postDelayed
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (_: Exception) {
                scheduleListeningCycle(900)
            }
        }, delayMs)
    }

    private fun stopWakeWordListening(disablePreference: Boolean = false) {
        wakeWordActive = false
        if (disablePreference) groqPrefs.edit().putBoolean("wakeword_enabled", false).apply()
        awaitingVoiceCommand = false
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        if (cloudStatus != null) cloudStatus?.text = "Wakeword aus."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            val shouldStart = pendingWakePermission && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            pendingWakePermission = false
            if (shouldStart) startWakeWordListening()
            else Toast.makeText(this, "Mikrofonberechtigung wurde nicht erteilt.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        stopWakeWordListening()
        super.onStop()
    }

    override fun onDestroy() {
        stopWakeWordListening()
        try { textToSpeech?.stop(); textToSpeech?.shutdown() } catch (_: Exception) {}
        textToSpeech = null
        super.onDestroy()
    }

    private fun showDashboard() {
        showGroqChat()
    }

        private fun rootView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(5, 8, 12))
    }

    private fun isSameOrigin(candidate: String, base: String): Boolean = try {
        val a = Uri.parse(candidate); val b = Uri.parse(base)
        a.scheme == b.scheme && a.host.equals(b.host, true) && a.port == b.port
    } catch (_: Exception) { false }

    private fun isAllowedBaseUrl(value: String): Boolean = try {
        if (value.isBlank() || value.length > 240) {
            false
        } else {
            val uri = Uri.parse(value)
            val scheme = uri.scheme?.lowercase()
            val hasSafeAuthority = !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isNullOrBlank() || uri.path == "/")
            val isHttps = scheme == "https"
            // Insecure HTTP is permitted only to the same Android device's loopback.
            val isSameDeviceHttp = scheme == "http" && uri.host == "127.0.0.1"
            hasSafeAuthority && (isHttps || isSameDeviceHttp)
        }
    } catch (_: Exception) { false }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val web = webView
        if (web != null && web.canGoBack()) web.goBack()
        else if (web != null) AlertDialog.Builder(this)
            .setTitle("JARVIS verlassen?")
            .setMessage("Die Verbindung bleibt gespeichert. Der API-Schlüssel liegt verschlüsselt im Android Keystore.")
            .setNegativeButton("Weiter nutzen", null)
            .setPositiveButton("Schließen") { _, _ -> finish() }
            .show()
        else super.onBackPressed()
    }

    companion object {
        private const val GROQ_MODEL = "openai/gpt-oss-20b"
        private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val KEY_BASE_URL = "companion_base_url"
        private const val KEY_NOTIFIED_IDS = "notified_ids"
        private const val KEY_NOTIFICATIONS_INITIALIZED = "notifications_initialized"
        private const val REQUEST_NOTIFICATIONS = 876
        private const val REQUEST_MICROPHONE = 877
    }
}
