# Lisanslar ve üçüncü taraf bileşenler

## Uygulamaya gömülü
| Bileşen | Lisans | Not |
|---|---|---|
| Inter yazı tipi (Regular, Medium, SemiBold, Bold) | SIL Open Font License 1.1 | Ubuntu `fonts-inter` paketinden; değiştirilmeden gömüldü. Lisans: https://openfontlicense.org |
| Kotlin standart kütüphanesi 2.2.20 | Apache-2.0 | ProGuard ile küçültülerek APK'ya eklendi |

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
