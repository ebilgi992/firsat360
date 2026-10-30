package com.firsat360.app.data

import android.content.Context
import java.net.HttpURLConnection

/**
 * Uygulama içi tarayıcıyla (WebView) aynı oturum: köprüyle bulunan broşür sayfaları, kullanıcının resmî sayfayı açtığı
 * oturumun çerezleri ve tarayıcı kimliğiyle indirilir — sayfayı tarayıcıda görmekle aynı istek. Hiçbir doğrulama
 * otomatik geçilmez; çerez yalnızca kullanıcının uygulamada gerçekten açtığı sitelerden gelir.
 */
object WebSession {
    @Volatile private var ua: String? = null

    fun init(ctx: Context) {
        if (ua != null) return
        ua = try { android.webkit.WebSettings.getDefaultUserAgent(ctx.applicationContext) } catch (e: Exception) { null }
    }

    fun apply(conn: HttpURLConnection, url: String) {
        ua?.let { conn.setRequestProperty("User-Agent", it) }
        try {
            android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Cookie", it) }
        } catch (e: Exception) { }
    }
}
