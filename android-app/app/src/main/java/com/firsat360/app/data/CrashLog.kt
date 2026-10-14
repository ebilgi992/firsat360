package com.firsat360.app.data

import android.content.Context
import android.os.Build
import com.firsat360.app.BuildInfo
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Beta hata kaydı: yakalanmayan bir hata olursa ayrıntıyı cihazdaki uygulama klasörüne yazar (internete göndermez).
 * Bir sonraki açılışta ana sayfa kullanıcıya "raporu paylaş" seçeneği sunar.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(val app: Context, val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            save(app, e, "yakalanmayan hata (iş parçacığı: ${t.name})")
            previous?.uncaughtException(t, e)
        }
    }

    fun format(e: Throwable, where: String): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return buildString {
            append("FIRSAT360 hata raporu\n")
            append("Sürüm: ${BuildInfo.VERSION} (${BuildInfo.VERSION_CODE})\n")
            append("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Yer: $where\n\n")
            append(sw.toString().take(12000))
        }
    }

    fun save(ctx: Context, e: Throwable, where: String) {
        try { File(ctx.filesDir, FILE).writeText(format(e, where), Charsets.UTF_8) } catch (ignored: Throwable) { }
    }

    fun pending(ctx: Context): String? = try {
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText(Charsets.UTF_8)
    } catch (ignored: Throwable) { null }

    fun clear(ctx: Context) { try { File(ctx.filesDir, FILE).delete() } catch (ignored: Throwable) { } }
}
