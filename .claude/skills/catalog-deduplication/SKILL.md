---
name: catalog-deduplication
description: Diagnose and fix duplicate catalog cards in FIRSAT360 (same catalog in several home sections, regional copies, stale records). Use when users see the same brochure twice.
---
# Mükerrer katalog

Katmanlar (sırayla kontrol et):
1. **Veri hattı:** `feed.catalog_group()` = mağaza + bölge eki çıkarılmış başlık + tarihler. Bölge kopyaları aynı grubu taşır.
   Bayat kayıt: `feed.is_stale()` (mağazanın son başarısından 2 gün önce görülmüş, ürünsüz) feed'e girmez.
2. **Uygulama:** `CatalogRules.collapse(list, region)` grubu tek karta indirger (kullanıcı bölgesi > bölgesiz > ilk).
   `HomeCatalogs.build` kimlik VE grup takibiyle her yayını tek bölüme koyar (raf → bugün başlayan → bitmek üzere → diğer).
3. **Durum:** tek kural `CatalogRules.state()` (UPCOMING/ACTIVE/EXPIRING_SOON/EXPIRED/UNKNOWN). Tarihsiz katalog asla EXPIRING_SOON değildir.
Test: `CoreTestsKt` içindeki `onur.*`, `home.noDuplicates`, `dedupe.*`; backend `tests/test_v12.py`.
Komut: `kotlinc -jvm-target 17 app/src/main/java/com/firsat360/app/core/*.kt app/src/test/java/com/firsat360/app/core/CoreTests.kt -include-runtime -d /tmp/ct.jar && java -cp /tmp/ct.jar com.firsat360.app.core.CoreTestsKt <0.6.1 feed.json>`
