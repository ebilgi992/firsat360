# Lisanslar ve üçüncü taraf bileşenler

## Uygulamaya gömülü
| Bileşen | Lisans | Not |
|---|---|---|
| Barlow ve Barlow Condensed yazı tipleri (Jeremy Tribby) | SIL Open Font License 1.1 | Google Fonts sürümü, npm `@expo-google-fonts/barlow`, `@expo-google-fonts/barlow-condensed` 0.2.3 paketlerinden; değiştirilmeden gömüldü. Lisans: https://openfontlicense.org |
| The Agency ajan tanımları (`.claude/agents/`) | MIT | https://github.com/msitarzewski/agency-agents — `.claude/agents/LICENSE-agency-agents` |
| Kotlin standart kütüphanesi 2.2.20 | Apache-2.0 | ProGuard ile küçültülerek APK'ya eklendi |

## Fiyat hesaplama uyarlaması

`PriceCents` kuruş normalizasyonu, [gokhancvs/market-fiyati-mcp](https://github.com/gokhancvs/market-fiyati-mcp/blob/main/src/money.ts) yaklaşımından Kotlin’e uyarlandı. Copyright (c) 2026 Gökhan Çavuş, MIT. Tam lisans: [LICENSE-market-fiyati-mcp](docs/LICENSE-market-fiyati-mcp.txt). Depo kodunun lisansı, üçüncü taraf fiyat verisine erişim/yeniden yayım izni sayılmaz.

## Derleme araçları (dağıtıma dahil değil)
| Araç | Lisans | Kaynak |
|---|---|---|
| Kotlin derleyicisi 2.2.20 | Apache-2.0 | github.com/JetBrains/kotlin sürümleri |
| ProGuard 7.8.1 | GPL-2.0 (araç; çıktıya lisans yükümlülüğü getirmez) | github.com/Guardsquare/proguard sürümleri |
| android.jar API 36 / API 26 (yalnızca derleme ve API denetimi; APK'ya girmez) | Apache-2.0 (AOSP) | GitHub: Reginer/aosp-android-jar, Sable/android-platforms |
| aapt2 2.20 (Linux) | Apache-2.0 (AOSP) | npm `aaptjs3@2.0.2` paketindeki ikili |
| D8/R8 8.2.2 (`d8.jar`), `apksigner.jar` | BSD-3 (R8) / Apache-2.0 | npm `@drxiaozhi/minapk@0.4.0` (Google build-tools 34.0.0 ile aynı olduğu beyan edilen dosyalar) |
| zipalign | Apache-2.0 | Ubuntu 24.04 deposu (yoksa `tools/zipalign.py`) |
| 0.1.0'da kullanılan: API 23 jar, aapt, dalvik-exchange (dx) | Apache-2.0 | Ubuntu 24.04 deposu |

## Backend (Python)
FastAPI (MIT), Uvicorn (BSD-3), SQLAlchemy (MIT), psycopg (LGPL-3.0, dinamik bağlı), APScheduler (MIT),
httpx (BSD-3), selectolax (MIT), pytest (MIT), Pillow (HPND; yalnızca simge üretim betiği).

## Marka adları
A101, BİM, ŞOK ve Migros adları yalnızca verinin kaynağını belirtmek için kullanılır. Mağaza logoları ve ürün
görselleri uygulamaya dahil edilmemiştir. Uygulama simgesi özgün tasarımdır (`scripts/make_icons.py`).

