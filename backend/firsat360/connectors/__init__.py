from .a101 import A101Connector
from .bim import BimConnector
from .discovery import SOURCES as DISCOVERY_SOURCES
from .discovery import DiscoveryConnector
from .extra import (A101_AFIS_URL, CompositeConnector, FlipbookConnector, FlipbookIndexConnector, FuncConnector,
                    HakmarConnector, MigroskopConnector, MoneyCatalogsConnector, PublitasConnector, TkkoopConnector, parse_a101_afis)
from .migros import MigrosConnector
from .onur import OnurConnector
from .sok import SokConnector

CLIENT_BRIDGED = ("a101", "sok", "migros", "gratis", "carrefoursa", "rossmann")  # broşürü istemci köprüsünde
ALL_STORES = ("a101", "bim", "sok", "migros", "tkkoop", "hakmar", "watsons", "metro", "bizimtoptan", "gratis",
              "rossmann", "onur", "carrefoursa", "file")


def live_connectors(max_pages: int = 8):
    """Tüm mağazaların otomatik bağlayıcıları. Her biri resmî kaynaktan okur; anlık görüntü gerekmez."""
    return _base_connectors(max_pages)


def _base_connectors(max_pages: int):
    return {
        # Broşür kaynakları istemci köprüsünde (config/client_bridge.json): A101 afişleri, Migroskop dijital, Gratis,
        # CarrefourSA ve Rossmann sunucuda KAZINMAZ. Ürün/fiyat kaynakları ve köprüde olmayan resmî kataloglar sürer.
        "a101": A101Connector(),
        "bim": BimConnector(max_pages=max_pages),  # aktüel ürünler + afişler
        "sok": SokConnector(max_pages=max_pages),  # haftanın fırsatları + kampanya görselleri
        "migros": CompositeConnector("migros", [MigrosConnector(max_pages=max_pages), MoneyCatalogsConnector()]),
        "tkkoop": TkkoopConnector(),
        "hakmar": HakmarConnector(),
        "watsons": PublitasConnector("watsons", "https://katalog.watsons.com.tr/"),
        "metro": PublitasConnector("metro", "https://brosurler.metro-tr.com/"),
        "bizimtoptan": FlipbookIndexConnector("bizimtoptan", "https://www.bizimtoptan.com.tr/pages/katalog"),
        # Alternatif toplama motoru: statik tarama → tarayıcıyla açma → bulunamadıysa raporla
        **{sid: DiscoveryConnector(src) for sid, src in DISCOVERY_SOURCES.items() if sid not in CLIENT_BRIDGED},
        "onur": OnurConnector(),  # kampanya sayfalarındaki resmî insert PDF'leri
    }
