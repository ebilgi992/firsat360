---
name: catalog-e2e-testing
description: Run FIRSAT360's end-to-end catalog verification chain (source → file → DB → feed → app data) and report PASS/FAIL/BLOCKED per store. Use before a release.
---
# Uçtan uca katalog testi

1. `cd backend && python -m pytest -q` (beklenen: hepsi geçer, PostgreSQL testleri atlanır).
2. Canlı çalıştırma: `gh api -X POST repos/ebilgi992/firsat360/actions/workflows/384801012/dispatches -f ref=main`
   (tek market: `-f "inputs[stores]=onur"`). Bitince `veri` dalından DB'yi al.
3. Her market için: aktif katalog sayısı, sayfa/PDF sayısı, `pages_json`/`pdf_url` dolu mu, `last_checked_at` güncel mi.
4. Feed: `python -c "import json;d=json.load(open('site/feed.json'))"` → katalog `group`/`region`/`kind` alanları.
5. Uygulama verisi: `CoreTestsKt` (çekirdek) + gömülü `assets/feed.json` ile raf/ana sayfa testleri.
6. Cihaz/emülatör yoksa ekran adımlarını **BLOCKED** yaz; asla PASS yazma.
