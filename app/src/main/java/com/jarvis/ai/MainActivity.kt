package com.jarvis.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * JARVIS Android client for a user-operated companion service.
 * Only the HTTPS endpoint is remembered. The dashboard API token is entered
 * by the user on the dashboard and is not stored by this Activity.
 */
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
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(5, 8, 12))
        }
        val mark = label("J", 62f, Color.rgb(225, 244, 255), true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 25, 37)); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(57, 94, 122))
            }
        }
        page.addView(mark, LinearLayout.LayoutParams(dp(84), dp(84)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18)
        })
        page.addView(label("J A R V I S", 23f, Color.rgb(235, 246, 255), true).apply {
            gravity = Gravity.CENTER
        })
        page.addView(label("P E R S O N A L   A S S I S T A N T", 9f, Color.rgb(115, 153, 181)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(25))
        })
        page.addView(label("Mit deinem privaten Companion verbinden", 17f, Color.rgb(225, 238, 249), true))
        page.addView(label(
            "Der Companion läuft auf deinem eigenen PC oder Heimserver und stellt Ollama, Gedächtnis und Integrationen bereit. Gib die HTTPS-Adresse dieses Dienstes ein.",
            12f, Color.rgb(148, 169, 188)
        ).apply { setPadding(0, dp(8), 0, dp(14)) })

        val input = EditText(this).apply {
            hint = "https://dein-server.example"
            setSingleLine(true); textSize = 13f
            setTextColor(Color.rgb(235, 246, 255)); setHintTextColor(Color.rgb(95, 116, 134))
            setPadding(dp(12), dp(12), dp(12), dp(12)); setText(baseUrl)
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 15, 23)); cornerRadius = dp(9).toFloat()
                setStroke(dp(1), Color.rgb(42, 60, 77))
            }
            contentDescription = "HTTPS-Adresse des JARVIS Companion"
        }
        page.addView(input, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })
        if (message.isNotBlank()) {
            page.addView(label(message, 11f, Color.rgb(242, 138, 145)).apply {
                setPadding(0, 0, 0, dp(12))
            })
        }
        page.addView(button("Verbinden") {
            val candidate = input.text.toString().trim().trimEnd('/')
            if (!isAllowedBaseUrl(candidate)) {
                showSetup("Bitte eine gültige HTTPS-Adresse angeben. Unsichere HTTP-Verbindungen sind nicht erlaubt.")
            } else {
                baseUrl = candidate
                prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
                showDashboard()
            }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(18) })
        page.addView(label(
            "Sicherheit: Nutze HTTPS über eine private VPN-Verbindung (z. B. Tailscale Serve). Öffne den API-Port niemals direkt im Internet. Der API-Schlüssel wird erst im Dashboard eingegeben und von dieser App nicht gespeichert.",
            10f, Color.rgb(103, 128, 148)
        ))
        setContentView(page)
    }

    private fun showDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(5, 8, 12))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
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
        web.settings.javaScriptEnabled = true // Required for the companion dashboard.
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
                // Open OAuth login and job boards in the external browser, never carrying dashboard headers.
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
        web.loadUrl("$baseUrl/")
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
            .setMessage("Die Serveradresse bleibt gespeichert. Der API-Schlüssel wird nicht von der App gespeichert.")
            .setNegativeButton("Weiter nutzen", null)
            .setPositiveButton("Schließen") { _, _ -> finish() }
            .show()
        else super.onBackPressed()
    }

    companion object { private const val KEY_BASE_URL = "companion_base_url" }
}
