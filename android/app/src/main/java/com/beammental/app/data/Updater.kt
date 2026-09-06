package com.beammental.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.beammental.app.BuildConfig
import com.beammental.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/** Self-update from the app's GitHub Releases:
 *  checks the latest tag, auto-downloads the APK over Wi-Fi and
 *  raises a notification when it's ready to install. */
object Updater {

    private const val REPO_LATEST = "https://api.github.com/repos/zyay/beam-mental-health/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIFY_READY = 21
    private const val NOTIFY_AVAILABLE = 22

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /** Latest release newer than the running build, or null (offline / none / parse fail). */
    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val r = client.newCall(Request.Builder().url(REPO_LATEST).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string() ?: return@runCatching null
            }
            val root = json.parseToJsonElement(r).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null
            val asset = root["assets"]?.jsonArray
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
                ?: return@runCatching null
            UpdateInfo(
                versionName = tag.removePrefix("v"),
                apkUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null,
                sizeBytes = asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }.getOrNull()
    }

    /** Already fully downloaded APK for this version, if any. */
    fun downloaded(context: Context, info: UpdateInfo): File? {
        val f = File(updateDir(context), "Beam-v${info.versionName}.apk")
        return if (f.isFile && f.length() > 0) f else null
    }

    /** Streams the APK to the app's external files dir; null on failure. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = updateDir(context)
            val file = File(dir, "Beam-v${info.versionName}.apk")
            val part = File(dir, "Beam-v${info.versionName}.apk.part")
            val req = Request.Builder().url(info.apkUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body ?: return@runCatching null
                val total = body.contentLength()
                var lastPct = -1
                body.byteStream().use { input ->
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(16384)
                        var done = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done.toFloat() / total * 100f).roundToInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
            }
            if (file.exists()) file.delete()
            if (!part.renameTo(file)) {
                part.copyTo(file, overwrite = true)
                part.delete()
            }
            file
        }.getOrNull()
    }

    fun install(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Fires when Wi-Fi becomes available; caller unregisters via [unregisterWifi]. */
    fun observeWifi(context: Context, onWifiAvailable: () -> Unit): ConnectivityManager.NetworkCallback {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onWifiAvailable()
            }
        }
        cm?.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            cb,
        )
        return cb
    }

    fun unregisterWifi(context: Context, cb: ConnectivityManager.NetworkCallback) {
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }

    /** Drops stale update files that don't belong to this version. */
    fun cleanup(context: Context, keep: UpdateInfo) {
        val dir = updateDir(context)
        dir.listFiles()?.forEach { f ->
            if (f.name != "Beam-v${keep.versionName}.apk") f.delete()
        }
    }

    /** "Beam 1.7.0 je stiahnutá — klepni na inštaláciu." */
    fun notifyReady(context: Context, info: UpdateInfo, file: File) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                "application/vnd.android.package-archive",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = android.app.PendingIntent.getActivity(
            context, NOTIFY_READY, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context, NOTIFY_READY,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_beam)
                .setContentTitle("Beam ${info.versionName} je stiahnutý")
                .setContentText("Klepni na notifikáciu a nainštaluj novú verziu.")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build(),
        )
    }

    /** "Beam 1.7.0 je vonku — stiahne sa na Wi-Fi." (not on Wi-Fi right now) */
    fun notifyAvailable(context: Context, info: UpdateInfo) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = intent?.let {
            android.app.PendingIntent.getActivity(
                context, NOTIFY_AVAILABLE, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }
        post(
            context, NOTIFY_AVAILABLE,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_beam)
                .setContentTitle("Beam ${info.versionName} je vonku")
                .setContentText("Nová verzia sa stiahne sama, keď budeš na Wi-Fi.")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build(),
        )
    }

    fun notificationsEnabled(context: Context): Boolean = canNotify(context)

    private fun updateDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "update").apply { mkdirs() }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL, "Aktualizácie", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nová verzia apky a pripravená inštalácia."
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** "1.7.0" vs "1.6.0" (also accepts a leading v). */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        if (r.isEmpty() || l.isEmpty()) return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrNull(i) ?: 0
            val lv = l.getOrNull(i) ?: 0
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun parseVersion(s: String): List<Int> =
        s.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
}
