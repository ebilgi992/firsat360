package com.firsat360.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.firsat360.app.MainActivity
import com.firsat360.app.R
import com.firsat360.app.core.AppLog
import com.firsat360.app.core.Dates
import com.firsat360.app.core.Digest
import com.firsat360.app.core.Feed
import com.firsat360.app.core.FeedFetch
import com.firsat360.app.core.Notice
import java.io.File
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Günlük katalog bildirimi.
 *
 * Zamanlama sunucudadır: FIRSAT360 veri hattı her gün kaynakları kontrol eder, yeni katalogları bir kez duyurur ve
 * yayını günceller. Telefon tarafında:
 *  - JobScheduler (WorkManager'ın platform karşılığı) ağ varken ~3 saatte bir yayını kontrol eder (değişmemişse indirmez),
 *  - AlarmManager (kesin olmayan pencere, ek izin gerektirmez) bildirim saatinde bir kontrol daha başlatır.
 * Android pil optimizasyonu (Doze, üretici kısıtlamaları) bu işleri geciktirebilir; tam dakikasında garanti verilmez.
 * Uygulama kapalıyken anında push için sunucu tarafı ntfy desteği vardır (docs/CANLI_KURULUM.md).
 */
object Notifier {
    const val CH_CATALOG = "catalogs"
    private const val JOB_PERIODIC = 3601
    private const val JOB_NOW = 3602
    private const val NOTIFY_ID = 4100

    fun channels(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CH_CATALOG, "Günün katalogları", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Takip ettiğiniz marketlerde yeni katalog yayınlandığında, günde en fazla bir özet" })
            nm.deleteNotificationChannel("prices")  // 0.4'teki fiyat alarmı kanalı kaldırıldı
        } catch (e: Exception) { }
    }

    fun permitted(ctx: Context): Boolean = try {
        (Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true)
    } catch (e: Exception) { false }

    /** Bildirimi gösterir. Gösterildiyse true (izin yoksa false; uygulama çökmez). */
    fun post(ctx: Context, n: Notice): Boolean {
        if (!permitted(ctx)) return false
        return try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_kind", n.kind); putExtra("open_id", n.targetId)
            }
            val pi = PendingIntent.getActivity(ctx, n.key.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = android.app.Notification.Builder(ctx, CH_CATALOG)
                .setSmallIcon(R.drawable.ic_stat_firsat)
                .setColor(0xFFF43F5E.toInt())
                .setContentTitle(n.title)
                .setContentText(n.body.substringBefore('\n'))
                .setStyle(android.app.Notification.BigTextStyle().bigText(n.body))
                .setAutoCancel(true).setContentIntent(pi)
                .setCategory(android.app.Notification.CATEGORY_RECOMMENDATION)
            nm.notify(NOTIFY_ID, b.build())  // tek bildirim: yenisi eskisinin yerini alır (birleştirme)
            true
        } catch (e: Exception) { AppLog.e("notify.post", e.message); false }
    }

    /** Yeni feed geldiğinde veya zamanlanmış kontrolde çağrılır. Gösterilen bildirimi döner (yoksa null). */
    @Synchronized
    fun evaluate(ctx: Context, feed: Feed, now: OffsetDateTime = OffsetDateTime.now()): Notice? {
        return try {
            val us = UserStore(ctx)
            val seen = us.seenCatalogs()
            if (seen == null) {  // ilk çalıştırma: mevcut kataloglar "görülmüş" (bildirim bombardımanı yok)
                us.markCatalogsSeen(feed.catalogs.map { it.id }); return null
            }
            if (!us.notifyCatalogs) { us.markCatalogsSeen(Digest.fresh(feed, seen, now).map { it.id }); return null }
            val d = Digest.decide(feed, seen, us.mutedStores(), now, us.notifyMinute)
            val n = d.notice
            var shown: Notice? = null
            if (n != null && n.key !in us.notifiedKeys()) {
                if (post(ctx, n)) { us.addNotified(listOf(n.key)); us.lastNotifiedAt = System.currentTimeMillis(); shown = n }
                else if (!permitted(ctx)) AppLog.w("notify.denied")
            }
            // İzin yoksa da işlendi sayılır: izin sonradan verilince eski kataloglar yığılmaz.
            if (n != null || d.handled.isNotEmpty()) us.markCatalogsSeen(d.handled)
            shown
        } catch (e: Exception) { AppLog.e("notify.eval", e.message); null }
    }

    /** Arka plan kontrollerini (yeniden) planlar. Bildirim kapalıysa iptal eder. */
    fun schedule(ctx: Context) {
        try {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            val us = UserStore(ctx)
            if (!us.notifyCatalogs || Sync.feedUrl(ctx).isBlank()) {
                js.cancel(JOB_PERIODIC); cancelAlarm(ctx); return
            }
            val net = if (us.syncWifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
            val existing = js.allPendingJobs.firstOrNull { it.id == JOB_PERIODIC }
            if (existing == null || existing.networkType != net) {
                js.schedule(JobInfo.Builder(JOB_PERIODIC, ComponentName(ctx, CheckJobService::class.java))
                    .setRequiredNetworkType(net)
                    .setPeriodic(3L * 60 * 60 * 1000, 60L * 60 * 1000)
                    .setBackoffCriteria(30L * 60 * 1000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setPersisted(true)
                    .build())
            }
            scheduleAlarm(ctx, us.notifyMinute)
        } catch (e: Exception) { AppLog.e("notify.schedule", e.message) }
    }

    /** Hemen bir kez kontrol (ağ gelince). */
    fun checkSoon(ctx: Context) {
        try {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            val net = if (UserStore(ctx).syncWifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
            js.schedule(JobInfo.Builder(JOB_NOW, ComponentName(ctx, CheckJobService::class.java))
                .setRequiredNetworkType(net)
                .setOverrideDeadline(2L * 60 * 60 * 1000)
                .setBackoffCriteria(15L * 60 * 1000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
        } catch (e: Exception) { AppLog.e("notify.soon", e.message) }
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(ctx, 0,
        Intent(ctx, DailyAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun scheduleAlarm(ctx: Context, minuteOfDay: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now(Dates.TR)
        var at = now.withHour(minuteOfDay / 60).withMinute(minuteOfDay % 60).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        // Kesin alarm izni (SCHEDULE_EXACT_ALARM) istenmez: 20 dakikalık pencere, pil dostu.
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), 20L * 60 * 1000, alarmIntent(ctx))
    }

    private fun cancelAlarm(ctx: Context) {
        try { ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx)) } catch (e: Exception) { }
    }

    /** Arka planda tek kontrol: yayını al (değişmemişse indirme), bildirimi değerlendir, temizlik yap. */
    fun runCheck(ctx: Context): Boolean {
        Maintenance.run(ctx)
        val r = Sync.fetch(ctx, attempts = 2)
        val feed: Feed? = when (r.status) {
            FeedFetch.Status.UPDATED -> r.text?.let { try { Feed.parse(it) } catch (e: Exception) { null } }
            FeedFetch.Status.NOT_MODIFIED, FeedFetch.Status.FAILED -> try {
                File(ctx.filesDir, "feed-cache.json").takeIf { it.exists() }?.readText(Charsets.UTF_8)?.let { Feed.parse(it) }
            } catch (e: Exception) { null }
        }
        // Veri değişmese de saat gelmiş olabilir: bekleyen kataloglar bildirim saatinde gösterilir.
        if (feed != null) evaluate(ctx, feed)
        return r.status != FeedFetch.Status.FAILED
    }
}

class CheckJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            val ok = try { Notifier.runCheck(applicationContext) } catch (t: Throwable) { AppLog.e("job", t.message); false }
            jobFinished(params, !ok)  // başarısızsa sistem artan beklemeyle yeniden dener
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

/** Bildirim saatinde: ağ gelince kontrol et, ertesi günü planla. */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        Notifier.checkSoon(ctx)
        Notifier.schedule(ctx)
    }
}

/** Yeniden başlatma / uygulama güncellemesi sonrası alarmı yeniden kur (JobScheduler işi kalıcıdır). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) Notifier.schedule(ctx)
    }
}
