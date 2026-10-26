package com.jarvis.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Polls only the user's private companion over HTTPS and displays generic notifications.
 * It never runs mail/calendar/application actions and does not put sensitive snippets in notifications.
 */
class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val base = prefs.getString(KEY_BASE_URL, "").orEmpty().trimEnd('/')
        val token = TokenVault.read(applicationContext) ?: return Result.success()
        if (!base.startsWith("https://") && !isAllowedLoopbackHttp(base)) return Result.success()

        return try {
            val email = request("$base/api/monitor/alerts", token, "GET")
            val jobs = request("$base/api/jobs/alerts", token, "GET")
            val reminders = request("$base/api/reminders/due", token, "POST")
            val seen = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty().toMutableSet()
            val firstRun = !prefs.getBoolean(KEY_NOTIFICATIONS_INITIALIZED, false)

            // Existing unread mail/job results are a baseline on first run, so old items don't spam the user.
            collectIds(email).forEach { (id, kind) ->
                if (seen.add("mail:$id") && !firstRun) notifyGeneric("mail:$id", kind)
            }
            collectIds(jobs).forEach { (id, kind) ->
                if (seen.add("job:$id") && !firstRun) notifyGeneric("job:$id", kind)
            }
            collectIds(reminders, isReminder = true).forEach { (id, kind) ->
                if (seen.add("reminder:$id")) notifyGeneric("reminder:$id", kind)
            }

            // Bound local deduplication metadata.
            val bounded = seen.toList().takeLast(300).toSet()
            prefs.edit()
                .putStringSet(KEY_NOTIFIED_IDS, bounded)
                .putBoolean(KEY_NOTIFICATIONS_INITIALIZED, true)
                .apply()
            Result.success()
        } catch (_: Exception) {
            // Do not log response bodies, URLs with credentials, personal data, or tokens.
            Result.retry()
        }
    }

    private fun isAllowedLoopbackHttp(value: String): Boolean = try {
        val uri = Uri.parse(value)
        uri.scheme == "http" && uri.host == "127.0.0.1" &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrBlank() || uri.path == "/")
    } catch (_: Exception) { false }

    private fun request(url: String, token: String, method: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 8000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (method == "POST") setRequestProperty("Content-Length", "0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Companion request failed.")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun collectIds(data: JSONObject, isReminder: Boolean = false): List<Pair<String, String>> {
        val items = data.optJSONArray("items") ?: return emptyList()
        val output = ArrayList<Pair<String, String>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val description = when {
                isReminder -> "Eine persönliche Erinnerung ist fällig."
                item.has("category") -> when (item.optString("category")) {
                    "job_offer" -> "Mögliche Job-Zusage erkannt. Bitte die E-Mail und Bedingungen prüfen."
                    "job_rejection" -> "Mögliche Bewerbungsabsage erkannt. Bitte die E-Mail prüfen."
                    "interview" -> "Mögliche Interview-Einladung erkannt. Bitte Termin und Details prüfen."
                    "job_application_update" -> "Ein Bewerbungs-Update wurde erkannt. Bitte die E-Mail prüfen."
                    else -> "Ein Bewerbungshinweis sollte geprüft werden."
                }
                item.optString("recommendation") == "apply" -> "JARVIS empfiehlt, diese Stelle zu prüfen. Es wurde keine Bewerbung versendet."
                item.optString("recommendation") == "review" -> "Eine passende Stelle braucht deine manuelle Prüfung. Es wurde keine Bewerbung versendet."
                else -> "Es gibt einen neuen JARVIS-Hinweis. Bitte öffne die App für Details."
            }
            output.add(id to description)
        }
        return output
    }

    private fun notifyGeneric(stableKey: String, description: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS Hinweise", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    this.description = "Neutrale Hinweise aus deinem privaten JARVIS-Companion."
                    setShowBadge(true)
                }
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, stableKey.hashCode(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("JARVIS")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(stableKey.hashCode() and 0x7fffffff, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "jarvis-companion-alert-poll"
        private const val PREFS = "jarvis_connection"
        private const val KEY_BASE_URL = "companion_base_url"
        private const val KEY_NOTIFIED_IDS = "notified_ids"
        private const val KEY_NOTIFICATIONS_INITIALIZED = "notifications_initialized"
        private const val CHANNEL_ID = "jarvis_local_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
    }
}
