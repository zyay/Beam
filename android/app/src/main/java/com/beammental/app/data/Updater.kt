package com.beammental.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.beammental.app.BuildConfig
import com.beammental.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import java.security.MessageDigest
import kotlin.math.roundToInt

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val htmlUrl: String,
)

/** Sealed state for the in-app update banner + Settings card. */
sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class WaitingWifi(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Ready(val info: UpdateInfo, val file: File, val canInstallOver: Boolean) : UpdateUi
    data class Failed(val info: UpdateInfo, val reason: String) : UpdateUi
}

/** Self-update from the app's GitHub Releases.
 *
 *  Downloads run in a process-lifetime scope (SupervisorJob) so they survive
 *  the activity that triggered them — closing chat or settings won't cancel
 *  an in-flight download. State is exposed as a [StateFlow] so any screen
 *  (chat banner, settings card, future widget) can observe it. */
object Updater {

    private const val REPO_LATEST = "https://api.github.com/repos/zyay/beam-mental-health/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/zyay/beam-mental-health/releases"
    private const val CHANNEL = "updates"
    private const val NOTIFY_DOWNLOADING = 21
    private const val NOTIFY_READY = 22
    private const val NOTIFY_AVAILABLE = 23

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val state: StateFlow<UpdateUi> = _state.asStateFlow()

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
                htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_PAGE,
            )
        }.getOrNull()
    }

    /** True if the downloaded APK can be installed as a normal update over the
     *  currently installed app. Returns false when signatures differ — in that
     *  case Android's package installer will reject the install, and the user
     *  must uninstall the old version first. */
    fun canInstallOver(context: Context, file: File): Boolean = runCatching {
        val installed = installedCertSha256(context) ?: return@runCatching false
        val downloaded = apkCertSha256(context, file) ?: return@runCatching false
        installed == downloaded
    }.getOrDefault(false)

    /** Starts a download in the process-lifetime scope. Updates the [state]
     *  flow and the system notification as it goes. If a download is already
     *  running for the same version, this is a no-op. */
    fun startDownload(
        context: Context,
        info: UpdateInfo,
        allowMetered: Boolean = false,
    ) {
        val cur = _state.value
        if (cur is UpdateUi.Downloading && cur.info == info) return
        _state.value = UpdateUi.Downloading(info, 0f)
        scope.launch {
            cleanup(context, info)
            notifyDownloading(context, info, 0)
            val file = download(context, info) { p ->
                _state.value = UpdateUi.Downloading(info, p)
                notifyDownloading(context, info, (p * 100).roundToInt())
            }
            if (file != null) {
                val canOver = canInstallOver(context, file)
                _state.value = UpdateUi.Ready(info, file, canOver)
                notifyReady(context, info, file, canOver)
            } else {
                val reason = "Nepodarilo sa stiahnuť. Skús to na Wi-Fi alebo manuálne."
                _state.value = UpdateUi.Failed(info, reason)
                notifyFailed(context, info, reason)
            }
        }
    }

    /** Manual entry point from Settings. Fetches latest, then either starts
     *  the download immediately (when [allowMetered] is true or we're on
     *  Wi-Fi) or parks it in WaitingWifi state. */
    fun checkAndMaybeStart(context: Context, allowMetered: Boolean = false) {
        scope.launch {
            val info = checkLatest() ?: return@launch
            // keep any in-flight / already-downloaded state if it matches
            val cur = _state.value
            if (cur is UpdateUi.Downloading && cur.info == info) return@launch
            if (cur is UpdateUi.Ready && cur.info == info) return@launch
            cleanup(context, info)
            downloaded(context, info)?.let { f ->
                _state.value = UpdateUi.Ready(info, f, canInstallOver(context, f))
                return@launch
            }
            if (allowMetered || onWifi(context)) {
                startDownload(context, info, allowMetered)
            } else {
                _state.value = UpdateUi.WaitingWifi(info)
                notifyAvailable(context, info)
            }
        }
    }

    /** Drops stale update files that don't belong to this version. */
    fun cleanup(context: Context, keep: UpdateInfo) {
        val dir = updateDir(context)
        dir.listFiles()?.forEach { f ->
            if (f.name != "Beam-v${keep.versionName}.apk") f.delete()
        }
    }

    /** Already fully downloaded APK for this version, if any. */
    fun downloaded(context: Context, info: UpdateInfo): File? {
        val f = File(updateDir(context), "Beam-v${info.versionName}.apk")
        return if (f.isFile && f.length() > 0) f else null
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

    fun openInBrowser(context: Context, info: UpdateInfo) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun onWifi(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    /** Fires when Wi-Fi becomes available; caller unregisters via [unregisterWifi].
     *  Never throws — a failed registration just means no auto-download. */
    fun observeWifi(context: Context, onWifiAvailable: () -> Unit): ConnectivityManager.NetworkCallback? {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { onWifiAvailable() }
        }
        return runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                cb,
            )
            cb
        }.getOrNull()
    }

    fun unregisterWifi(context: Context, cb: ConnectivityManager.NetworkCallback?) {
        if (cb == null) return
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }

    fun notificationsEnabled(context: Context): Boolean = canNotify(context)

    // ---------- internals ----------

    private suspend fun download(
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

    private fun updateDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "update").apply { mkdirs() }

    private fun installedCertSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkg: PackageInfo = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val sig: Signature? = if (Build.VERSION.SDK_INT >= 28) {
            pkg.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pkg.signatures?.firstOrNull()
        }
        sig?.toByteArray()?.let { sha256(it) }
    }.getOrNull()

    private fun apkCertSha256(context: Context, apk: File): String? = runCatching {
        val pm = context.packageManager
        val pkg: PackageInfo = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return@runCatching null
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
                ?: return@runCatching null
        }
        val sig: Signature? = if (Build.VERSION.SDK_INT >= 28) {
            pkg.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pkg.signatures?.firstOrNull()
        }
        sig?.toByteArray()?.let { sha256(it) }
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val out = md.digest(bytes)
        return out.joinToString("") { "%02x".format(it) }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL, "Aktualizácie", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Sťahovanie a inštalácia novej verzie Beam."
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun notifyDownloading(context: Context, info: UpdateInfo, pct: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, NOTIFY_DOWNLOADING, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_beam)
            .setContentTitle("Beam ${info.versionName} sa sťahuje")
            .setContentText("$pct %")
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (pi != null) b.setContentIntent(pi)
        post(context, NOTIFY_DOWNLOADING, b.build())
    }

    private fun notifyReady(context: Context, info: UpdateInfo, file: File, canOver: Boolean) {
        // cancel the in-progress notification
        NotificationManagerCompat.from(context).cancel(NOTIFY_DOWNLOADING)
        if (!canNotify(context)) return
        ensureChannel(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, NOTIFY_READY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (canOver) "Klepni a nainštaluj novú verziu." else
            "Klepni — ak inštalácia zlyhá, odinštaluj starú verziu a skús znova."
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_beam)
            .setContentTitle("Beam ${info.versionName} je stiahnutý")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
        post(context, NOTIFY_READY, b.build())
    }

    private fun notifyFailed(context: Context, info: UpdateInfo, reason: String) {
        NotificationManagerCompat.from(context).cancel(NOTIFY_DOWNLOADING)
        if (!canNotify(context)) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, NOTIFY_READY, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_beam)
            .setContentTitle("Beam ${info.versionName} sa nepodarilo stiahnuť")
            .setContentText(reason)
            .setAutoCancel(true)
        if (pi != null) b.setContentIntent(pi)
        post(context, NOTIFY_READY, b.build())
    }

    private fun notifyAvailable(context: Context, info: UpdateInfo) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, NOTIFY_AVAILABLE, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_beam)
            .setContentTitle("Beam ${info.versionName} je vonku")
            .setContentText("Stiahne sa, keď budeš na Wi-Fi.")
            .setAutoCancel(true)
        if (pi != null) b.setContentIntent(pi)
        post(context, NOTIFY_AVAILABLE, b.build())
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
