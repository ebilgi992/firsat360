from .a101 import A101Connector
from .bim import BimConnector
from .discovery import SOURCES as DISCOVERY_SOURCES
from .discovery import DiscoveryConnector
from .extra import (A101_AFIS_URL, CompositeConnector, FlipbookConnector, FlipbookIndexConnector, FuncConnector,
                    HakmarConnector, MigroskopConnector, PublitasConnector, TkkoopConnector, parse_a101_afis)
from .migros import MigrosConnector
from .sok import SokConnector

ALL_STORES = ("a101", "bim", "sok", "migros", "tkkoop", "hakmar", "watsons", "metro", "bizimtoptan", "gratis",
              "rossmann", "onur", "carrefoursa", "file")


def live_connectors(max_pages: int = 8):
    """Tüm mağazaların otomatik bağlayıcıları. Her biri resmî kaynaktan okur; anlık görüntü gerekmez."""
    return {
        "a101": CompositeConnector("a101", [A101Connector(),
                                            FuncConnector("a101", A101_AFIS_URL, parse_a101_afis)]),
        "bim": BimConnector(max_pages=max_pages),  # aktüel ürünler + afişler
        "sok": SokConnector(max_pages=max_pages),  # haftanın fırsatları + kampanya görselleri
        "migros": CompositeConnector("migros", [MigrosConnector(max_pages=max_pages),
                                                MigroskopConnector()]),
        "tkkoop": TkkoopConnector(),
        "hakmar": HakmarConnector(),
        "watsons": PublitasConnector("watsons", "https://katalog.watsons.com.tr/"),
        "metro": PublitasConnector("metro", "https://brosurler.metro-tr.com/"),
        "bizimtoptan": FlipbookIndexConnector("bizimtoptan", "https://www.bizimtoptan.com.tr/pages/katalog"),
        "gratis": FlipbookIndexConnector("gratis", "https://www.gratis.com/gratis-katalog"),
        # Alternatif toplama motoru: statik tarama → tarayıcıyla açma → bulunamadıysa raporla
        **{sid: DiscoveryConnector(src) for sid, src in DISCOVERY_SOURCES.items()},
    }
