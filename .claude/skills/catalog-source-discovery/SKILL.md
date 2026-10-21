---
name: catalog-source-discovery
description: Find a Turkish market's official brochure/catalog source (PDF, page images, flipbook, campaign page) and wire it as an adapter in FIRSAT360. Use when a store shows no catalog or a new store is added.
---
# Katalog kaynağı keşfi

1. Resmî alan adını doğrula (Wikipedia/şirket sitesi). Örnek ders: File = `file.com.tr`, `filemarket.com.tr` değil.
2. `robots.txt` oku (WebFetch). Yasaksa veya 403 ise dur → `source-failure-diagnostics`.
3. Ham sayfayı gör: GitHub → Actions → **kaynak-teshis** → `urls` = sayfa adresleri; sonra
   `git fetch origin teshis && git show origin/teshis:1.body`. (Bu ortam mağaza sitelerine doğrudan erişemez.)
4. Ara: `.pdf`, `pics/`, `insert`, `katalog`, `afis`, `campaign=`, flipbook alanları, `<base href>`, JSON blokları.
   Bölge/kampanya parametreli sayfaları ayrı aç (Onur: insertler `?region=…&campaign=…` sayfasında).
5. Adaptör: `backend/firsat360/connectors/<market>.py` (örnek: `onur.py`) veya `discovery.py` → `SOURCES`.
   Kayıt: `connectors/__init__.py`, `config.py` (STORES, STORE_STYLE, BROCHURE_URLS, CHAIN_RANK).
6. Ham HTML'den regresyon testi yaz (`tests/test_<market>.py`), sonra Actions → gunluk-veri → `stores=<market>`.

Kurallar: CAPTCHA/Cloudflare sınaması/giriş aşılmaz; üçüncü taraf toplayıcı (Cimri, Akakçe) görselleri kullanılmaz.
