"""Türkçe metin, fiyat, tarih ve kategori normalizasyonu.

Kural: Bilinmeyen değer tahmin edilmez. Ayrıştırılamayan fiyat/tarih ``None`` döner.
"""
from __future__ import annotations

import hashlib
import re
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation

_TR_UPPER_TO_LOWER = str.maketrans({"I": "ı", "İ": "i"})
_FOLD = str.maketrans({"ç": "c", "ğ": "g", "ı": "i", "ö": "o", "ş": "s", "ü": "u", "â": "a", "î": "i", "û": "u"})


def tr_lower(text: str) -> str:
    """Türkçe kurallarına göre küçük harfe çevirir (I→ı, İ→i)."""
    return text.translate(_TR_UPPER_TO_LOWER).lower()


def fold(text: str) -> str:
    """Arama anahtarı: Türkçe küçük harf + aksan katlama + tek boşluk."""
    t = tr_lower(text or "").translate(_FOLD)
    t = re.sub(r"[^0-9a-z%&+.,/ ]+", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def clean(text: str | None) -> str | None:
    if text is None:
        return None
    t = re.sub(r"\s+", " ", text).strip()
    return t or None


_BULLETS = re.compile(r"\s*[•·▪●◦;|]+\s*")


def clean_variant(text: str | None) -> str | None:
    """Ürün açıklaması/ölçüsü: madde işaretleri ("• 2 L", "• • Napoliten 360 g • Arrabbiata 360 g") temizlenir,
    parçalar virgülle birleşir. Yalnızca işaretten oluşan açıklama (ör. "•") yok sayılır."""
    if not text:
        return None
    parts = [p.strip(" -–,") for p in _BULLETS.split(text)]
    parts = [p for p in dict.fromkeys(parts) if p and re.search(r"[0-9A-Za-zÇĞİÖŞÜçğıöşü]", p)]
    return ", ".join(parts) or None


_PRICE_RE = re.compile(r"(\d{1,3}(?:[.\s]\d{3})+|\d+)\s*(?:[.,]\s*(\d{1,2}))?")


def parse_price(text: str | None) -> Decimal | None:
    """'445, 00 ₺', '1.500, 00 ₺', '₺349,00', '139.00 TL', '249,00₺' → Decimal.

    Ayrıştırılamazsa None döner (asla tahmin edilmez).
    """
    if not text or text.strip() in {"-", ""}:
        return None
    t = text.replace("₺", " ").replace("TL", " ").replace("tl", " ").replace(" ", " ").strip()
    if not re.search(r"\d", t):
        return None
    t = re.sub(r"\s+", "", t)
    if "," in t and "." in t:
        if t.rfind(",") > t.rfind("."):
            t = t.replace(".", "").replace(",", ".")
        else:
            t = t.replace(",", "")
    elif "," in t:
        t = t.replace(",", ".")
    elif "." in t:
        whole, _, frac = t.rpartition(".")
        if len(frac) == 3:  # binlik ayıracı: 1.500
            t = t.replace(".", "")
    m = re.fullmatch(r"\d+(?:\.\d{1,2})?", t)
    if not m:
        m2 = re.search(r"\d+(?:\.\d{1,2})?", t)
        if not m2:
            return None
        t = m2.group(0)
    try:
        value = Decimal(t).quantize(Decimal("0.01"))
    except InvalidOperation:
        return None
    if value <= 0:
        return None
    return value


def kurus_to_decimal(value) -> Decimal | None:
    try:
        v = int(value)
    except (TypeError, ValueError):
        return None
    if v <= 0:
        return None
    return (Decimal(v) / Decimal(100)).quantize(Decimal("0.01"))


def format_try(value: Decimal | None) -> str | None:
    if value is None:
        return None
    whole, frac = f"{value:.2f}".split(".")
    groups = []
    while len(whole) > 3:
        groups.insert(0, whole[-3:])
        whole = whole[:-3]
    groups.insert(0, whole)
    return f"{'.'.join(groups)},{frac} ₺"


MONTHS = {
    "ocak": 1, "subat": 2, "mart": 3, "nisan": 4, "mayis": 5, "haziran": 6,
    "temmuz": 7, "agustos": 8, "eylul": 9, "ekim": 10, "kasim": 11, "aralik": 12,
}


def _infer_year(month: int, day: int, observed: date) -> date | None:
    """Yıl yazmayan tarihlerde gözlem tarihine en yakın makul yılı seçer."""
    for year in (observed.year, observed.year - 1, observed.year + 1):
        try:
            d = date(year, month, day)
        except ValueError:
            return None
        if observed - timedelta(days=300) <= d <= observed + timedelta(days=65):
            return d
    return None


def _date_fold(text: str) -> str:
    """Tarih için katlama: noktalama (tire dahil) korunur."""
    return re.sub(r"\s+", " ", tr_lower(text).translate(_FOLD)).strip()


def parse_tr_date(text: str | None, observed: date) -> date | None:
    """'13 Ekim Salı', 'Kampanya 07 Ekim tarihinden itibaren', '1 Ekim 2026' → date."""
    if not text:
        return None
    f = _date_fold(text)
    m = re.search(r"(\d{1,2})\s+(" + "|".join(MONTHS) + r")(?:\s+(\d{4}))?", f)
    if not m:
        return None
    day, month = int(m.group(1)), MONTHS[m.group(2)]
    if m.group(3):
        try:
            return date(int(m.group(3)), month, day)
        except ValueError:
            return None
    return _infer_year(month, day, observed)


def parse_tr_range(text: str | None, observed: date) -> tuple[date | None, date | None]:
    """'10-16 Ekim', '1 ve 31 Ekim 2026 tarihleri arasında' → (başlangıç, bitiş)."""
    if not text:
        return None, None
    f = _date_fold(text)
    months = "|".join(MONTHS)
    # "24 Mart-31 Aralık", "28 Eylül - 4 Ekim 2026" (iki farklı ay)
    m2 = re.search(r"(\d{1,2})\s+(" + months + r")\s*(?:-|–|ve)\s*(\d{1,2})\s+(" + months + r")(?:\s+(\d{4}))?", f)
    if m2:
        d1, mo1, d2, mo2 = int(m2.group(1)), MONTHS[m2.group(2)], int(m2.group(3)), MONTHS[m2.group(4)]
        if m2.group(5):
            y = int(m2.group(5))
            try:
                return date(y if mo1 <= mo2 else y - 1, mo1, d1), date(y, mo2, d2)
            except ValueError:
                return None, None
        start = _infer_year(mo1, d1, observed)
        if start is None:
            return None, None
        try:
            end = date(start.year if mo2 >= mo1 else start.year + 1, mo2, d2)
        except ValueError:
            return start, None
        return start, end
    m = re.search(r"(\d{1,2})\s*(?:-|ve|–)\s*(\d{1,2})\s+(" + months + r")(?:\s+(\d{4}))?", f)
    if not m:
        single = parse_tr_date(text, observed)
        return single, None
    d1, d2, month = int(m.group(1)), int(m.group(2)), MONTHS[m.group(3)]
    if m.group(4):
        y = int(m.group(4))
        try:
            return date(y, month, d1), date(y, month, d2)
        except ValueError:
            return None, None
    start = _infer_year(month, d1, observed)
    end = _infer_year(month, d2, observed)
    return start, end


def month_end(d: date) -> date:
    """Ayın son günü."""
    nxt = date(d.year + (d.month == 12), d.month % 12 + 1, 1)
    return nxt - timedelta(days=1)


def parse_tr_month(text: str | None, observed: date) -> tuple[date | None, date | None]:
    """Yalnızca ay adı (gün yok) yazan başlık: "Ekim Kataloğu", "ekim-katalog-2026" → (1 Ekim, 31 Ekim).
    Gün içeren metinler için parse_tr_range kullanılır; bu fonksiyon yalnızca o sonuç vermediğinde çağrılmalıdır.
    Birden çok farklı ay geçiyorsa belirsizdir → (None, None)."""
    if not text:
        return None, None
    f = re.sub(r"[-_/]+", " ", _date_fold(text))
    found = re.findall(r"(?<![a-z])(" + "|".join(MONTHS) + r")(?![a-z])", f)
    if len(set(found)) != 1:
        return None, None
    month = MONTHS[found[0]]
    y = re.search(r"(?<!\d)(20\d{2})(?!\d)", f)
    start = date(int(y.group(1)), month, 1) if y else _infer_year(month, 1, observed)
    if start is None:
        return None, None
    return start, month_end(start)


# ---------------------------------------------------------------- kategoriler
CATEGORIES = {
    "elektronik": "Elektronik",
    "ev-yasam": "Ev & Yaşam",
    "giyim": "Giyim & Ayakkabı",
    "oyuncak": "Oyuncak & Kitap",
    "spor": "Spor & Outdoor",
    "bebek": "Anne & Bebek",
    "temizlik": "Temizlik",
    "kisisel-bakim": "Kişisel Bakım",
    "sut-kahvalti": "Süt & Kahvaltılık",
    "et-tavuk": "Et, Tavuk & Şarküteri",
    "dondurulmus": "Dondurulmuş",
    "atistirmalik": "Atıştırmalık & Tatlı",
    "icecek": "İçecek",
    "temel-gida": "Temel Gıda",
    "saglik": "Takviye & Sporcu Gıdası",
    "diger": "Diğer",
}

# Sıra önemlidir: ilk eşleşen kazanır.
_RULES: list[tuple[str, tuple[str, ...]]] = [
    ("dondurulmus", ("dondurulmus", "milfoy", "tirtikli patates", "ispanak 450")),
    ("kisisel-bakim", ("vucut sutu", "sac spreyi", "prezervatif")),
    ("temizlik", ("leke cikarici",)),
    ("spor", ("bilek agirligi",)),
    ("icecek", ("coffee mate",)),
    ("sut-kahvalti", (" bal ", "cicek bali")),
    # Çok kelimeli, başka kurallarla çakışan ifadeler önce gelir.
    ("ev-yasam", ("ayakkabi kutusu", "bot saklama", "saklama kabi", "kamp sandalye", "rattan", "termos", "kilim",
                  "semsiye", "yumak", "telefon standi")),
    ("elektronik", (" tv ", "soundbar", "cep telefonu", "espresso", "kahve makinesi", "blender", " utu ", "supurge",
                    "tost makinesi", "cay makinesi", "damacana pompasi", "buharli temizleyici", "masaj aleti",
                    "masaj cihazi", "masaj tabancasi", "karin kemeri", "ems sistem")),
    ("spor", ("dumble", "kettlebell", "pilates", "yoga", "direnc bandi", "egzersiz", "boks", "fitness", "kapi bari",
              "push up", "loop band", "powerband", "el yayi", "dart board", "thigh master", "kavrama seti",
              "esneme bandi", "suspansiyon", "spor aleti", "spor cantasi")),
    ("oyuncak", ("oyuncak", "hot wheels", "barbie", "figur", "kutu oyunu", "denge oyunu", "pelus", "kitap",
                 "metal arabalar", "yaris pisti", "yaris arabas", "kopuk tabancasi", "enchantimals", "firlatici",
                 "surpriz paket", "vahsi hayvanlar", "salon seti", "sprey atan araba", "rampasi", "minix")),
    ("giyim", ("terlik", "corap", "tayt", "boxer", "slip", "kadin atlet", "erkek atlet", "ayakkabi", "esofman",
               "tshirt", "t shirt", "gomlek", "pijama", "bralet", " atki", "ev giyim", "sortlu takim")),
    ("ev-yasam", ("tava", "kek kalibi", "bicak", "tabag", "bardag", "kupa", "tencere", "badya", "aski seti",
                  "suzgec", "organizer", "kutu", "sampuanlik", "sunumluk", "duzenleyici", "koltuk", "celik urunler")),
    ("temizlik", ("oda kokusu", "stick up")),
    ("kisisel-bakim", ("makyaj", "fondoten")),
    ("temel-gida", ("zeytinyag", "aycicek yag", "vanilin", "kabartma tozu")),
    ("spor", ("eliptik", "kosu bandi", "trambolin", "yumruk topu")),
    ("elektronik", ("bluetooth", "kulaklik", "hoparlor", "3d yazici", "3d tarayici", "lazer gravur", "buzdolabi",
                    "ankastre", "isitici", "vantilator", "hava temizleyici", "elektrikli")),
    ("bebek", ("bebek", "emzik", "wee baby", "body atlet", "kanz", "devam sutu", "bebelac")),
    ("saglik", ("protein tozu", "kolajen", "kreatin", "pre supreme", "omega 3", "magnezyum", " b12 ",
                "pirinc kremasi", "protein bar")),
    ("temizlik", ("deterjan", "islak havlu", "perwoll", "camasir", "bulasik", "yumusatici", "temizleyici",
                  "temizlik", "tuvalet kagidi", "kagit havlu", "pecete", "mendil", "strec film", "sunger",
                  "beyazlatici", "havlu 12", "tuvalet temizleme", "makinasi tablet", "sodasi", "inox",
                  "mikrofiber", "kapsulu")),
    ("kisisel-bakim", ("sampuan", "kotex", "dis macunu", "dis fircasi", "dus jeli", "sabun", " ped ", "ped 8",
                       "maske", "nemlendirici", "losyon", "parfum", "lip balm", "el kremi", "deodorant", "vazelin",
                       "allik", "kapatici", "tint", "tarak", "gunes koruyucu", "serum", "yuz temizleme",
                       "micellar", "agiz bakim", "jilet", "gillette", "slalom", "kolonya", "tarama spreyi")),
    ("et-tavuk", ("sucuk", "sosis", "salam", "pilic", " kofte ", "doner", "bonfile", "baget", " hindi ",
                  "pirzola", "kebap")),
    ("sut-kahvalti", ("peynir", "yogurt", "sut ", "sut 1", "yagli sut", "kefir", "kaymak", "kremsos", "recel",
                      "zeytin", "tahin", "helva", "findik kremasi", "fistik ezmesi", "kakaolu krema")),
    ("icecek", ("icecek", "coca cola", "maden suyu", "su 5 l", " cay ", "monarch", "enerji", "soguk cay",
                "c+ limon", "gazli")),
    ("atistirmalik", ("cikolata", "gofret", "biskuvi", " kek ", "kek 1", "browni", "kurabiye", "cips", "seker",
                      "marshmallow", " bar ", "kruvasan", "hurma", "mango", "draje", "albeni", "metro", "probis",
                      "luppo", "patlagi", "pankek", "puding", "milka")),
    ("temel-gida", ("yag", "salca", "pure domates", "ton baligi", "pirinc", "bulgur", "mercimek", "nohut",
                    "makarna", "eriste", " un 5", "tuz", "lavas", "ekmek", "corba", "sos", "granola",
                    "spagetti", "linguine", "tagliatelle")),
    ("ev-yasam", ("dolap", "ayna", "kavanoz", "saklama kabi", "catal kasik", " mum ")),
]

_URL_CATEGORY = {
    "/elektronik/": "elektronik",
    "/anne-bebek-oyuncak/": "bebek",
    "/ev-yasam/": "ev-yasam",
    "/donuk-hazir-yemek-meze/": "dondurulmus",
    "/sut-urunleri-kahvaltilik/": "sut-kahvalti",
    "/et-tavuk-sarkuteri/": "et-tavuk",
    "/temel-gida/": "temel-gida",
    "/su-icecek/": "icecek",
    "/kisisel-bakim/": "kisisel-bakim",
}


def categorize(name: str, brand: str | None = None, variant: str | None = None,
               url: str | None = None) -> tuple[str, str]:
    """(kategori_anahtarı, kaynak) döner. kaynak = 'source_url' veya 'keyword_rule'."""
    if url:
        for frag, key in _URL_CATEGORY.items():
            if frag in url:
                return key, "source_url"
    hay = " " + fold(" ".join(x for x in (brand, name, variant) if x)) + " "
    for key, words in _RULES:
        for w in words:
            if w in hay:
                return key, "keyword_rule"
    return "diger", "keyword_rule"


_SIZE_RE = re.compile(r"(?<![0-9])(\d+(?:[.,]\d+)?)\s*(?:x\s*(\d+(?:[.,]\d+)?)\s*)?(kg|gr|g|ml|lt|l|cl|cc)(?![a-z])")
_UNIT = {"gr": "g", "lt": "l"}


def size_token(*texts: str | None) -> str:
    """Ürün kimliği için ilk ambalaj ölçüsü: '3x140 g' → '3x140g', '1,5 Kg' → '1.5kg'. Yoksa ''."""
    for t in texts:
        m = _SIZE_RE.search(fold(t or ""))
        if m:
            a, b, unit = m.group(1).replace(",", "."), m.group(2), _UNIT.get(m.group(3), m.group(3))
            return f"{a}x{b.replace(',', '.')}{unit}" if b else f"{a}{unit}"
    return ""


def _hash_id(store_id: str, parts: list[str]) -> str:
    return f"{store_id}-" + hashlib.sha1("|".join([store_id, *parts]).encode("utf-8"), usedforsecurity=False).hexdigest()[:14]


def offer_id(store_id: str, catalog_id: str | None, brand: str | None, name: str, variant: str | None) -> str:
    """Kararlı ürün kimliği: mağaza + katalog + marka + ad + ambalaj ölçüsü.

    Açıklama metni (variant) kimliğe yalnızca ölçü olarak girer; böylece aynı ürünün açıklaması kaynakta
    ya da aktarımda biraz farklı yazıldığında fiyat geçmişi kopmaz. Ölçüsü aynı, açıklaması farklı iki ürün
    aynı çalıştırmada çakışırsa ``offer_id_full`` kullanılır (bkz. pipeline.ingest).
    """
    return _hash_id(store_id, [catalog_id or "", fold(brand or ""), fold(name), size_token(variant, name)])


def offer_id_full(store_id: str, catalog_id: str | None, brand: str | None, name: str, variant: str | None) -> str:
    return _hash_id(store_id, [catalog_id or "", fold(brand or ""), fold(name), fold(variant or ""), "full"])
