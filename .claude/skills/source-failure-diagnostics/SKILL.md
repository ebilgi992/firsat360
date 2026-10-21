---
name: source-failure-diagnostics
description: Diagnose why a FIRSAT360 store source fails (403, robots.txt, wrong domain, redirect loop, JS rendering) from live run evidence. Use when a store's status is error in status.json or the quality report.
---
# Kaynak hatası teşhisi

1. Canlı kanıt: `git fetch origin veri && git show origin/veri:firsat360.db > live.db` ve `logs/firsat360.log`.
   `select id,status,last_error from stores;` — 403 mesajında `[engelleyen: Cloudflare|Akamai|…; içerik; yönlendirme]` var.
2. Sınıflandır:
   - `cf-mitigated: challenge` / robots.txt bile 403 → veri merkezi IP bot sınaması. **Aşılmaz.** Uygulama cihazda resmî
     sayfayı açar ve "Sayfa sayfa aç" ile görselleri toplar (`WebScreen.collectPages`, `DeviceBrochures`). (A101, CarrefourSA)
   - robots.txt yasak → uy, cihaz yolu.
   - Aynı adrese çok sayıda 302 → yönlendirme döngüsü (HEAD); `probe()` kullan, `max_redirects=5`.
   - 200 ama 0 sonuç → yanlış sayfa/alan adı veya JS; `kaynak-teshis` ile ham HTML'e bak.
3. Geri çekilme: son 3 deneme engelse `pipeline.blocked_backoff` 20 saat atlar; elle `stores=` girdisi atlamaz.
4. Sonucu `docs/CATALOG_RECOVERY_REPORT.md` ve `docs/DATA_SOURCE_STATUS.md`'ye yaz.
