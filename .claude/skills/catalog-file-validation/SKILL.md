---
name: catalog-file-validation
description: Verify that FIRSAT360 catalog pages and PDFs are real files (magic bytes, size) and not HTML error pages or product thumbnails. Use when adding a source or when a catalog shows broken pages.
---
# Katalog dosyası doğrulama

- Türü sunucuya değil ilk baytlara göre belirle: `firsat360.connectors.base.sniff()` (jpeg/png/webp/pdf/html).
- Ağdan doğrulama: `PoliteClient.probe(url)` (GET + ilk 16 bayt, HEAD'e güvenme — File'da HEAD 302 döngüsüydü).
- Sayfa görselleri: `discovery.validate_images()` → JPEG/PNG/WEBP ve ≥15 KB; ürün küçük resimleri
  (`/catalog/product/`, `/urun/`) `SKIP` ile elenir. PDF: `kind == "pdf"` ve `%PDF` imzası.
- Mevcut veride sahte sayfalar: kalite ajanı `CatalogAgent` → `fake_pages` (yarısından çoğu SKIP'e uyan katalog).
- Kontrol: `cd backend && python -m pytest -q tests/test_discovery.py tests/test_api_discovery.py`
