"""Migros resmî katalog arşivleri ve ŞOK kampanya afişleri — 21.10.2026 resmî sayfalarından alınmış yapı parçaları."""
from datetime import date

from firsat360.connectors.extra import parse_money_catalogs
from firsat360.connectors.sok import parse_campaign_banners

D = date(2026, 10, 21)
MONEY = """<div class="insert" id="insertListContainer"><div class="head"><div>
<div class="image"><img data-src="https://moneyclubkart.azureedge.net/mcstage/insert-kapak-mockup-5m-284-639270494248011163.png" class="lazy"></div>
<div class="detail"><h4><span>Güncel 5M Migroskop Dijital</span><br> 8 - 21 Ekim 2026</h4>
<h5 class="text-center"><span>Sayı: <b>284</b></span></h5>
<button id="" type="button" class="btn btn-white-purple-line center-block _df_button"
 source="https://moneyclubkart.azureedge.net/mcstage/284-8-21-ekim-5m-2-639278426759455263.pdf" mcdate="8 - 21 Ekim 2026"> KATALOĞU GÖR </button>
</div></div></div>
<div class="item"><a class="_df_button" source="https://moneyclubkart.azureedge.net/mcstage/283-5m.pdf" mcdate="24 Eyl&#xFC;l - 07 Ekim 2026">
<div class="image"><img data-src="https://moneyclubkart.azureedge.net/mcstage/kapak-283.png" class="lazy"></div></a>
<p>Sayı: <b>283</b></p><p>24 Eylül - 07 Ekim 2026</p></div>
<div class="item"><a class="_df_button" source="https://evil.example.com/x.pdf" mcdate="1 - 31 Ekim 2026"></a></div>"""

SOK = """<div class="PromoListingContent_listingCart__9uMvP"><div class="PromoListingContent_listingCartImg__mkOkP">
<img src="https://images.ceptesok.com/cdn-cgi/image/width=512,height=260,fit=pad,quality=80,format=webp/promotion-assets/sub-folder/1c219942.jpg" alt="Ücretsiz Teslimat"/></div>
<div class="PromoListingContent_listingCartDetail__xQZIk"><p class="PromoListingContent_listingCartDetailTitle__6foCk">Ücretsiz Teslimat</p>
<div><p>Stoklarla sınırlıdır.</p><a href="/ucretsiz-teslimat-prm-c1f73e10-3cf9-4ef1-a954-32fa6dcdb901"><div>Kampanya Detayı</div></a></div></div></div>"""


def test_money_current_issue_button_and_archive():
    items = parse_money_catalogs(MONEY, D)
    assert len(items) == 1  # 283 bitti (7 Ekim), yabancı alan adındaki PDF alınmaz
    it = items[0]
    assert it["issue"] == "284" and it["valid_from"] == date(2026, 10, 8) and it["valid_to"] == D
    assert it["pdf"].endswith("/284-8-21-ekim-5m-2-639278426759455263.pdf")
    assert it["cover"].endswith("insert-kapak-mockup-5m-284-639270494248011163.png")


def test_money_html_entities_in_dates():
    items = parse_money_catalogs(MONEY, date(2026, 10, 1), max_items=5)
    assert any(i["issue"] == "283" and i["valid_from"] == date(2026, 9, 24) for i in items)


def test_sok_new_card_layout_banners():
    pages = parse_campaign_banners(SOK)
    assert len(pages) == 1 and pages[0]["caption"] == "Ücretsiz Teslimat"
    assert pages[0]["url"] == "https://images.ceptesok.com/promotion-assets/sub-folder/1c219942.jpg"
    assert "width=512" in pages[0]["thumb_url"] and pages[0]["link"].endswith("-prm-c1f73e10-3cf9-4ef1-a954-32fa6dcdb901")
