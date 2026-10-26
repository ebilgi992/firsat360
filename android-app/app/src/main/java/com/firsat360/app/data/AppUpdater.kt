package com.firsat360.app.data

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.firsat360.app.BuildInfo
import com.firsat360.app.core.AppLog
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Yalnızca bu deponun APK'sı, checksum ve kurulu imzayla eşleşirse sistem kurucusuna verilir. */
object AppUpdater {
    private const val PREFIX = "https://github.com/ebilgi992/firsat360/releases/download/"
    private val checking = AtomicBoolean(false)
    private var offered: String? = null

    private fun read(url: String, limit: Int): ByteArray {
        require(url.startsWith("https://"))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "FIRSAT360/${BuildInfo.VERSION}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            require(conn.responseCode == 200) { "Sunucu ${conn.responseCode}" }
            require(conn.url.protocol == "https")
            return conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16384)
                while (true) { val n = input.read(buf); if (n < 0) break
                    require(out.size() + n <= limit) { "Dosya boyutu sınırı aşıldı" }; out.write(buf, 0, n) }
                out.toByteArray()
            }
        } finally { conn.disconnect() }
    }

    fun check(a: Activity, manual: Boolean = false) {
        if (!checking.compareAndSet(false, true)) return
        Thread {
            try {
                val releases = JSONArray(String(read("https://api.github.com/repos/ebilgi992/firsat360/releases?per_page=10", 1024 * 1024), Charsets.UTF_8))
                var chosen: org.json.JSONObject? = null
                var code = BuildInfo.VERSION_CODE
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("draft")) continue
                    val assets = release.getJSONArray("assets")
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val flavor = if (a.packageName == "com.firsat360.beta") "beta" else "app"
                        val c = Regex("FIRSAT360-(\\d+)-$flavor\\.apk").matchEntire(asset.optString("name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                        if (c > code && asset.optString("browser_download_url").startsWith(PREFIX)) { chosen = release; code = c }
                    }
                }
                val release = chosen
                if (release == null) {
                    if (manual) a.runOnUiThread { Toast.makeText(a, "Yeni sürüm bulunamadı", Toast.LENGTH_LONG).show() }
                } else if (manual || offered != release.optString("tag_name")) {
                    val version = release.optString("tag_name")
                    val assets = release.getJSONArray("assets")
                    val flavor = if (a.packageName == "com.firsat360.beta") "beta" else "app"
                    val name = "FIRSAT360-$code-$flavor.apk"
                    val all = (0 until assets.length()).map { assets.getJSONObject(it) }
                    val apk = all.first { it.optString("name") == name }.getString("browser_download_url")
                    val checksum = all.first { it.optString("name") == "$name.sha256" }.getString("browser_download_url")
                    require(apk.startsWith(PREFIX) && checksum.startsWith(PREFIX))
                    a.runOnUiThread {
                        if (!a.isFinishing && !a.isDestroyed) {
                            offered = version
                            AlertDialog.Builder(a).setTitle("Yeni sürüm hazır: $version")
                                .setMessage("${release.optString("body").take(1400)}\n\nUygulama içinde indirilir. Android kurulum onayı ister ve güncelleme sırasında uygulamayı yeniden başlatabilir. Verileriniz korunur.")
                                .setPositiveButton("İndir ve güncelle") { _, _ -> download(a, apk, checksum, code) }
                                .setNegativeButton("Daha sonra", null).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.w("update.check", e.javaClass.simpleName)
                if (manual) a.runOnUiThread { Toast.makeText(a, "Güncelleme kontrol edilemedi; bağlantınızı kontrol edin", Toast.LENGTH_LONG).show() }
            } finally { checking.set(false) }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun download(a: Activity, apk: String, checksum: String, code: Int) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(a).setTitle("Güncelleme izni")
                .setMessage("Android ayarlarında bu uygulama için kuruluma izin verin. Döndüğünüzde Ayarlar › Güncellemeleri kontrol et'e dokunun.")
                .setPositiveButton("İzni aç") { _, _ -> a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))) }
                .setNegativeButton("Vazgeç", null).show(); return
        }
        val cancelled = AtomicBoolean(false)
        val progress = ProgressDialog(a).apply { setTitle("Güncelleme indiriliyor"); setMessage("Dosya ve imza doğrulanıyor…"); setCancelable(true); setOnCancelListener { cancelled.set(true) }; show() }
        Thread {
            val file = File(a.cacheDir, "update.apk")
            try {
                val hash = String(read(checksum, 512), Charsets.UTF_8).trim().split(Regex("\\s+"))[0].lowercase()
                require(hash.matches(Regex("[0-9a-f]{64}"))) { "Geçersiz doğrulama dosyası" }
                val bytes = read(apk, 80 * 1024 * 1024)
                require(!cancelled.get()) { "İptal edildi" }
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                require(actual == hash) { "Dosya doğrulaması başarısız" }
                file.writeBytes(bytes)
                val pm = a.packageManager
                val candidate = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES) ?: error("Geçersiz APK")
                val installed = pm.getPackageInfo(a.packageName, PackageManager.GET_SIGNATURES)
                require(candidate.packageName == a.packageName && candidate.versionCode == code && candidate.versionCode > installed.versionCode) { "Yanlış paket veya sürüm" }
                val old = installed.signatures?.map { it.toCharsString() }?.toSet() ?: emptySet()
                val new = candidate.signatures?.map { it.toCharsString() }?.toSet() ?: emptySet()
                require(old.isNotEmpty() && old == new) { "Uygulama imzası eşleşmiyor" }
                require(!cancelled.get()) { "İptal edildi" }
                val installer = pm.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(a.packageName); setSize(file.length()) }
                val id = installer.createSession(params)
                try {
                    installer.openSession(id).use { session ->
                        session.openWrite("base.apk", 0, file.length()).use { output -> file.inputStream().use { it.copyTo(output) }; session.fsync(output) }
                        val intent = Intent(a, UpdateResultActivity::class.java)
                        val mutable = if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                        val result = PendingIntent.getActivity(a, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                        session.commit(result.intentSender)
                    }
                } catch (e: Exception) { installer.abandonSession(id); throw e }
            } catch (e: Exception) {
                a.runOnUiThread { if (!a.isFinishing && !a.isDestroyed) AlertDialog.Builder(a).setTitle("Güncelleme tamamlanamadı").setMessage(e.message ?: "Tekrar deneyin").setPositiveButton("Tamam", null).show() }
            } finally { file.delete(); a.runOnUiThread { if (!a.isFinishing && !a.isDestroyed) progress.dismiss() } }
        }.start()
    }
}

class UpdateResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(this, "FIRSAT360 güncellendi", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, "Kurulum tamamlanmadı; tekrar deneyebilirsiniz", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
