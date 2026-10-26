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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    private val cloudPrefs by lazy { getSharedPreferences("jarvis_ollama_cloud", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cloudMessages = mutableListOf<JSONObject>()
    private var cloudTranscript: TextView? = null
    private var cloudScroll: ScrollView? = null
    private var cloudInput: EditText? = null
    private var cloudStatus: TextView? = null
    private var modelInput: EditText? = null
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
        val cloudSelected = cloudPrefs.getBoolean("selected_mode", false)
        if (cloudSelected && !OllamaKeyVault.read(this).isNullOrBlank()) showOllamaChat()
        else if (isAllowedBaseUrl(baseUrl)) showDashboard()
        else if (!OllamaKeyVault.read(this).isNullOrBlank()) showOllamaChat()
        else showSetup()
        requestNotificationPermissionIfNeeded()
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
        webView?.apply { stopLoading(); destroy() }
        webView = null
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(5, 8, 12))
        }

        val mark = label("J", 62f, Color.rgb(225, 244, 255), true).apply {
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 25, 37)); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(57, 94, 122))
            }
        }
        page.addView(mark, LinearLayout.LayoutParams(dp(84), dp(84)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18)
        })
        page.addView(label("J A R V I S", 23f, Color.rgb(235, 246, 255), true).apply {
            gravity = android.view.Gravity.CENTER
        })
        page.addView(label("P E R S O N A L   A S S I S T A N T", 9f, Color.rgb(115, 153, 181)).apply {
            gravity = android.view.Gravity.CENTER; setPadding(0, dp(6), 0, dp(25))
        })

        page.addView(button("Ollama Cloud direkt testen (Free-Kontingent)") {
            showOllamaCloudSetup()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(18) })
        page.addView(label("Mit deinem privaten Companion verbinden", 17f, Color.rgb(225, 238, 249), true))
        page.addView(label(
            "Der Companion stellt KI, Gedächtnis und Integrationen bereit. Für JARVIS direkt auf diesem Handy nutze http://127.0.0.1:8765. Andere Geräte dürfen nur über eine private HTTPS-Verbindung verbinden.",
            12f, Color.rgb(148, 169, 188)
        ).apply { setPadding(0, dp(8), 0, dp(14)) })

        val urlInput = EditText(this).apply {
            hint = "http://127.0.0.1:8765 (auf diesem Handy)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true); textSize = 13f
            setTextColor(Color.rgb(235, 246, 255)); setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12)); setText(baseUrl)
            background = inputBackground()
            contentDescription = "Adresse des JARVIS Companion; HTTP nur für 127.0.0.1"
        }
        page.addView(urlInput, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })

        val tokenExists = !TokenVault.read(this).isNullOrBlank()
        val tokenInput = EditText(this).apply {
            hint = if (tokenExists) "API-Token gespeichert — leer lassen, um ihn zu behalten" else "JARVIS_API_TOKEN aus deiner lokalen .env"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true); textSize = 12f
            setTextColor(Color.rgb(235, 246, 255)); setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = inputBackground()
            contentDescription = "Lokaler JARVIS API-Schlüssel"
        }
        page.addView(tokenInput, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })

        if (message.isNotBlank()) {
            page.addView(label(message, 11f, Color.rgb(242, 138, 145)).apply {
                setPadding(0, 0, 0, dp(12))
            })
        }
        page.addView(button("Sicher verbinden") {
            val candidate = urlInput.text.toString().trim().trimEnd('/')
            val enteredToken = tokenInput.text.toString().trim()
            if (!isAllowedBaseUrl(candidate)) {
                showSetup("Bitte HTTPS verwenden. HTTP ist ausschließlich für 127.0.0.1 auf diesem Handy erlaubt.")
                return@button
            }
            if (enteredToken.isNotEmpty()) {
                if (enteredToken.length < 32) {
                    showSetup("Der API-Schlüssel muss mindestens 32 Zeichen lang sein.")
                    return@button
                }
                try {
                    TokenVault.save(this, enteredToken)
                } catch (_: Exception) {
                    showSetup("Der Schlüssel konnte nicht sicher gespeichert werden. Prüfe, ob Android Keystore verfügbar ist.")
                    return@button
                }
            } else if (TokenVault.read(this).isNullOrBlank()) {
                showSetup("Gib den API-Schlüssel aus deiner lokalen .env ein.")
                return@button
            }
            baseUrl = candidate
            cloudPrefs.edit().putBoolean("selected_mode", false).apply()
            prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
            NotificationWorker.schedule(this)
            showDashboard()
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(14) })

        page.addView(label(
            "Der Token wird mit AES-GCM und einem Geräteschlüssel im Android Keystore verschlüsselt. HTTP ist nur für 127.0.0.1 auf diesem Gerät erlaubt; andere Server müssen HTTPS nutzen und privat erreichbar sein. JARVIS führt keine E-Mail-, Kalender- oder Bewerbungsaktion automatisch aus.",
            10f, Color.rgb(103, 128, 148)
        ))
        if (tokenExists || baseUrl.isNotBlank()) {
            page.addView(button("Verbindung und gespeicherten Schlüssel löschen") {
                AlertDialog.Builder(this)
                    .setTitle("Verbindung entfernen?")
                    .setMessage("Die gespeicherte Serveradresse und der verschlüsselte API-Schlüssel werden von diesem Gerät entfernt.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Entfernen") { _, _ ->
                        TokenVault.clear(this)
                        prefs.edit().remove(KEY_BASE_URL).remove(KEY_NOTIFIED_IDS).remove(KEY_NOTIFICATIONS_INITIALIZED).apply()
                        androidx.work.WorkManager.getInstance(this).cancelUniqueWork(NotificationWorker.UNIQUE_WORK_NAME)
                        baseUrl = ""
                        showSetup()
                    }
                    .show()
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(page) })
    }

    private fun inputBackground() = GradientDrawable().apply {
        setColor(Color.rgb(7, 15, 23))
        cornerRadius = dp(9).toFloat()
        setStroke(dp(1), Color.rgb(42, 60, 77))
    }


    private fun showOllamaCloudSetup(message: String = "") {
        stopWakeWordListening()
        cloudTranscript = null
        cloudInput = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            setBackgroundColor(Color.rgb(5, 8, 12))
        }
        root.addView(label("J A R V I S", 25f, Color.rgb(235, 246, 255), true))
        root.addView(label("Ollama Cloud verbinden", 18f, Color.rgb(225, 238, 249), true).apply {
            setPadding(0, dp(12), 0, dp(8))
        })
        root.addView(label(
            "Du brauchst einen Ollama-API-Key von ollama.com/settings/keys. Der Key wird mit Android Keystore verschlüsselt gespeichert. Für den Chat werden deine Eingaben an Ollama Cloud übertragen. Laut Ollama werden Prompts nicht zum Training verwendet oder protokolliert.",
            12f, Color.rgb(148, 169, 188)
        ).apply { setPadding(0, 0, 0, dp(14)) })

        val keyInput = EditText(this).apply {
            hint = if (OllamaKeyVault.read(this@MainActivity).isNullOrBlank()) "Ollama API-Key einfügen" else "API-Key gespeichert — leer lassen, um ihn zu behalten"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            textSize = 13f
            setTextColor(Color.rgb(235, 246, 255))
            setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = inputBackground()
        }
        root.addView(keyInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })

        val currentModel = cloudPrefs.getString("model_name", "gpt-oss:20b").orEmpty()
        val modelEdit = EditText(this).apply {
            hint = "Modellname aus deinem Ollama-Zugang"
            setText(currentModel)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            textSize = 13f
            setTextColor(Color.rgb(235, 246, 255))
            setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = inputBackground()
        }
        root.addView(modelEdit, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })
        root.addView(label(
            "Kostenhinweis: Free enthält nur ein begrenztes monatliches Starter-Kontingent, keine unbegrenzte kostenlose API. Bei aufgebrauchtem Kontingent kann JARVIS nicht mehr antworten. Füge kein kostenpflichtiges Guthaben hinzu. Falls dein Ollama-Konto bereits gekauftes Guthaben hat, können API-Aufrufe dieses verwenden. Wähle nur ein Modell, das dein Free-Konto als Starter-Modell anbietet.",
            11f, Color.rgb(226, 190, 130)
        ).apply { setPadding(0, 0, 0, dp(14)) })

        if (message.isNotBlank()) {
            root.addView(label(message, 12f, Color.rgb(242, 138, 145)).apply {
                setPadding(0, 0, 0, dp(12))
            })
        }

        root.addView(button("Key speichern und JARVIS öffnen") {
            val entered = keyInput.text.toString().trim()
            val key = if (entered.isNotEmpty()) entered else OllamaKeyVault.read(this)
            if (key.isNullOrBlank() || key.length < 12) {
                showOllamaCloudSetup("Bitte zuerst einen gültigen Ollama-API-Key einfügen.")
                return@button
            }
            val model = modelEdit.text.toString().trim()
            if (model.isBlank() || model.length > 100 || model.any { it.isWhitespace() }) {
                showOllamaCloudSetup("Bitte eine gültige Modellkennung ohne Leerzeichen eingeben.")
                return@button
            }
            try {
                if (entered.isNotEmpty()) OllamaKeyVault.save(this, entered)
                cloudPrefs.edit().putString("model_name", model).putBoolean("selected_mode", true).apply()
                cloudMessages.clear()
                showOllamaChat()
            } catch (_: Exception) {
                showOllamaCloudSetup("Der API-Key konnte nicht sicher gespeichert werden.")
            }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })

        root.addView(button("Ollama-Key löschen") {
            AlertDialog.Builder(this)
                .setTitle("Ollama-Key löschen?")
                .setMessage("Der verschlüsselt gespeicherte Ollama-API-Key wird von diesem Gerät entfernt.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen") { _, _ ->
                    OllamaKeyVault.clear(this)
                    cloudPrefs.edit().remove("model_name").apply()
                    cloudMessages.clear()
                    showSetup()
                }
                .show()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })

        root.addView(button("Stattdessen privaten JARVIS-Server nutzen") {
            showSetup()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll)
    }

    private fun showOllamaChat() {
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
        header.addView(label("JARVIS", 22f, Color.rgb(235, 246, 255), true),
            LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(button("Einstellungen") { showOllamaCloudSetup() },
            LinearLayout.LayoutParams(-2, dp(42)))
        root.addView(header)

        val modelName = cloudPrefs.getString("model_name", "gpt-oss:20b").orEmpty()
        val modelField = EditText(this).apply {
            hint = "Ollama-Modell"
            setText(modelName)
            setSingleLine(true)
            textSize = 12f
            setTextColor(Color.rgb(235, 246, 255))
            setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = inputBackground()
        }
        modelInput = modelField
        root.addView(modelField, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(4) })
        root.addView(label(
            "Ollama Cloud: Antworten kommen aus der Cloud, nicht lokal. Free-Kontingent ist begrenzt. Bitte keine Passwörter, privaten E-Mails oder sensiblen Dokumente senden.",
            10f, Color.rgb(178, 151, 111)
        ).apply { setPadding(0, 0, 0, dp(6)) })

        val transcriptScroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(7, 13, 20))
            isFillViewport = false
        }
        val transcriptView = label(
            "JARVIS: Ich bin bereit. Schreib mir eine Nachricht oder aktiviere das Wakeword, solange diese App geöffnet ist.",
            14f, Color.rgb(225, 238, 249)
        ).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        cloudScroll = transcriptScroll
        cloudTranscript = transcriptView
        transcriptScroll.addView(transcriptView)
        root.addView(transcriptScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(4); bottomMargin = dp(8)
        })

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val promptInput = EditText(this).apply {
            hint = "Schreib JARVIS eine Nachricht …"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 4
            setTextColor(Color.rgb(235, 246, 255))
            setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = inputBackground()
        }
        cloudInput = promptInput
        inputRow.addView(promptInput, LinearLayout.LayoutParams(-1, -2))
        inputRow.addView(button("Senden") {
            val prompt = cloudInput?.text?.toString()?.trim().orEmpty()
            if (prompt.isNotBlank()) {
                cloudInput?.setText("")
                sendOllamaPrompt(prompt, false)
            }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(5) })
        root.addView(inputRow)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        voiceRow.addView(button("„Jarvis“ aktivieren") {
            startWakeWordListening()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        voiceRow.addView(button("Mikrofon stoppen") {
            stopWakeWordListening(disablePreference = true)
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
        root.addView(voiceRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })

        val statusView = label(
            "Wakeword aus. Zum Sprachtest die Erkennung aktivieren.",
            10f, Color.rgb(135, 158, 178)
        ).apply { setPadding(dp(2), dp(4), dp(2), dp(3)) }
        cloudStatus = statusView
        root.addView(statusView)
        root.addView(button("Zum bisherigen Server-Verbindungsbildschirm") {
            stopWakeWordListening()
            showSetup()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(2) })
        setContentView(root)
        autoStartWakeWordIfEnabled()
    }

    private fun autoStartWakeWordIfEnabled() {
        if (!cloudPrefs.getBoolean("wakeword_enabled", false) ||
            cloudTranscript == null || wakeWordActive || wakeStartPending
        ) return
        wakeStartPending = true
        mainHandler.post {
            wakeStartPending = false
            if (!isFinishing && cloudTranscript != null && !wakeWordActive &&
                cloudPrefs.getBoolean("wakeword_enabled", false)
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

    private fun sendOllamaPrompt(prompt: String, speakReply: Boolean) {
        if (cloudRequestPending) {
            runOnUiThread { Toast.makeText(this, "Eine JARVIS-Anfrage läuft bereits.", Toast.LENGTH_SHORT).show() }
            return
        }
        if (prompt.length > 4000) {
            runOnUiThread { Toast.makeText(this, "Bitte kürze deine Nachricht auf 4.000 Zeichen.", Toast.LENGTH_LONG).show() }
            return
        }
        val key = OllamaKeyVault.read(this)
        if (key.isNullOrBlank()) {
            runOnUiThread { showOllamaCloudSetup("Der Ollama-API-Key fehlt. Bitte erneut verbinden.") }
            return
        }
        val model = modelInput?.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cloudPrefs.getString("model_name", "gpt-oss:20b").orEmpty()
        if (model.isBlank() || model.length > 100 || model.any { it.isWhitespace() }) {
            runOnUiThread { Toast.makeText(this, "Ungültige Modellkennung.", Toast.LENGTH_LONG).show() }
            return
        }
        cloudPrefs.edit().putString("model_name", model).apply()
        cloudMessages.add(JSONObject().put("role", "user").put("content", prompt))
        val requestMessages = JSONArray()
        requestMessages.put(JSONObject()
            .put("role", "system")
            .put("content", "Du bist JARVIS, ein freundlicher, ruhiger, intelligenter persönlicher Assistent. Antworte auf Deutsch, wenn der Nutzer Deutsch spricht. Erkläre anfängerfreundlich. Behaupte nie, Geräte gesteuert, Nachrichten versendet oder Dateien verändert zu haben, wenn du das nicht tatsächlich getan hast. Du hast in dieser App keinen Zugriff auf Apps, Dateien, E-Mail oder Kalender. Erfinde keine Aktionen oder Fakten. Frage vor wichtigen oder irreversiblen Aktionen nach Bestätigung."))
        cloudMessages.takeLast(10).forEach { requestMessages.put(it) }
        appendCloudTranscript("Du: $prompt")
        updateCloudStatus("JARVIS denkt …")
        cloudRequestPending = true
        cloudInput?.isEnabled = false

        Thread {
            try {
                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", requestMessages)
                    .put("stream", false)
                    .put("options", JSONObject().put("num_predict", 512))
                val connection = (URL("https://ollama.com/api/chat").openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 120000
                    doOutput = true
                    instanceFollowRedirects = false
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(bytes) }
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    try { connection.errorStream?.close() } catch (_: Exception) {}
                    connection.disconnect()
                    val friendly = when (statusCode) {
                        401, 403 -> "Ollama hat den API-Key abgelehnt. Prüfe den Key unter ollama.com/settings/keys."
                        402, 429 -> "Das Ollama-Kontingent oder Modelllimit ist erreicht. JARVIS kauft kein Guthaben hinzu. Prüfe die Free-Nutzung auf ollama.com."
                        else -> "Ollama antwortet mit HTTP $statusCode. Prüfe Modellname, Zugriff und Free-Kontingent."
                    }
                    throw IllegalStateException(friendly)
                }
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                val answer = JSONObject(responseText).optJSONObject("message")?.optString("content").orEmpty().trim()
                if (answer.isBlank()) throw IllegalStateException("Ollama hat eine leere Antwort geliefert.")
                cloudMessages.add(JSONObject().put("role", "assistant").put("content", answer))
                runOnUiThread {
                    cloudRequestPending = false
                    cloudInput?.isEnabled = true
                    appendCloudTranscript("JARVIS: $answer")
                    updateCloudStatus("Antwort von Ollama Cloud · Modell $model")
                    if (speakReply && wakeWordActive) speakWakeText(answer.take(1200))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    cloudRequestPending = false
                    cloudInput?.isEnabled = true
                    val msg = e.message?.take(240) ?: "Verbindung zu Ollama fehlgeschlagen."
                    appendCloudTranscript("JARVIS: $msg")
                    updateCloudStatus("Anfrage fehlgeschlagen")
                    if (speakReply && wakeWordActive) speakWakeText("Entschuldigung, ich konnte gerade nicht antworten. $msg")
                }
            }
        }.start()
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
        cloudPrefs.edit().putBoolean("wakeword_enabled", true).apply()
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
                sendOllamaPrompt(heard, true)
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
        if (disablePreference) cloudPrefs.edit().putBoolean("wakeword_enabled", false).apply()
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
        rootView().let { root ->
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(7, 13, 20)); setStroke(dp(1), Color.rgb(27, 42, 56))
                }
            }
            bar.addView(label("JARVIS", 15f, Color.rgb(225, 241, 255), true),
                LinearLayout.LayoutParams(0, dp(48), 1f))
            bar.addView(button("Verbindung ändern") { showSetup() },
                LinearLayout.LayoutParams(-2, dp(42)))
            root.addView(bar)

            val web = WebView(this)
            webView = web
            web.setBackgroundColor(Color.rgb(5, 8, 12))
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = false
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.settings.javaScriptCanOpenWindowsAutomatically = false
            web.settings.setSupportMultipleWindows(false)
            web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            web.settings.safeBrowsingEnabled = true
            web.webChromeClient = WebChromeClient()
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url
                    if (isSameOrigin(target.toString(), baseUrl)) return false
                    // OAuth login and job listings are opened externally; the API token is not forwarded.
                    if (target.scheme == "https" && request.isForMainFrame) {
                        return try {
                            startActivity(Intent(Intent.ACTION_VIEW, target)); true
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "Link kann nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (!isSameOrigin(url, baseUrl)) return
                    val token = TokenVault.read(this@MainActivity) ?: return
                    // Fill the dashboard's session-only field. The page itself does not persist the token.
                    val quoted = JSONObject.quote(token)
                    view.evaluateJavascript(
                        "(function(){var e=document.getElementById('token');" +
                            "if(e&&!e.value){e.value=$quoted;var b=document.getElementById('connect');if(b)b.click();}})();",
                        null
                    )
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) Toast.makeText(
                        this@MainActivity,
                        "JARVIS-Server nicht erreichbar. Prüfe Adresse und ob Termux auf diesem Handy läuft.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
                    callback.backToSafety(true)
                }
            }
            root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
            setContentView(root)
        }
        NotificationWorker.schedule(this)
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
        private const val KEY_BASE_URL = "companion_base_url"
        private const val KEY_NOTIFIED_IDS = "notified_ids"
        private const val KEY_NOTIFICATIONS_INITIALIZED = "notifications_initialized"
        private const val REQUEST_NOTIFICATIONS = 876
        private const val REQUEST_MICROPHONE = 877
    }
}
