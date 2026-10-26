package com.jarvis.ai

import android.Manifest
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
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/** Android client for the user's self-hosted JARVIS companion. */
class MainActivity : Activity() {
    private var webView: WebView? = null
    private var baseUrl = ""
    private val prefs by lazy { getSharedPreferences("jarvis_connection", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 8, 12)
        window.navigationBarColor = Color.rgb(5, 8, 12)
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty()
        if (isAllowedBaseUrl(baseUrl)) showDashboard() else showSetup()
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
        page.addView(label("Mit deinem privaten Companion verbinden", 17f, Color.rgb(225, 238, 249), true))
        page.addView(label(
            "Der Companion läuft auf deinem eigenen PC oder Heimserver und stellt Ollama, Gedächtnis und Integrationen bereit. Gib die HTTPS-Adresse dieses Dienstes ein.",
            12f, Color.rgb(148, 169, 188)
        ).apply { setPadding(0, dp(8), 0, dp(14)) })

        val urlInput = EditText(this).apply {
            hint = "https://dein-server.example"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true); textSize = 13f
            setTextColor(Color.rgb(235, 246, 255)); setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12)); setText(baseUrl)
            background = inputBackground()
            contentDescription = "HTTPS-Adresse des JARVIS Companion"
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
                showSetup("Bitte eine gültige HTTPS-Adresse angeben. Unsichere HTTP-Verbindungen sind nicht erlaubt.")
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
            prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
            NotificationWorker.schedule(this)
            showDashboard()
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(14) })

        page.addView(label(
            "Der Token wird mit AES-GCM und einem Geräteschlüssel im Android Keystore verschlüsselt. Die Verbindung zum Companion muss HTTPS verwenden; der Server selbst sollte nur für dich über ein privates VPN erreichbar sein. JARVIS führt keine E-Mail-, Kalender- oder Bewerbungsaktion automatisch aus.",
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
        setContentView(page)
    }

    private fun inputBackground() = GradientDrawable().apply {
        setColor(Color.rgb(7, 15, 23))
        cornerRadius = dp(9).toFloat()
        setStroke(dp(1), Color.rgb(42, 60, 77))
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
                        "Companion nicht erreichbar. Prüfe HTTPS-Adresse und private VPN-Verbindung.",
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
        if (value.isBlank() || value.length > 240) false else {
            val uri = Uri.parse(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isNullOrBlank() || uri.path == "/")
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
    }
}
