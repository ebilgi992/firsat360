import json
from firsat360.connectors.a101 import parse_jsonld_products
from firsat360.connectors.extra import parse_tk_campaign
from datetime import date


def test_a101_structured_try_data_keeps_currency_and_source_boundaries():
    product = {'@type': 'Product', 'name': 'Süt 1 L', 'url': '/kapida/sut_p-1234',
               'image': 'https://cdn2.a101.com.tr/product.jpg', 'offers': {'price': '29.95', 'priceCurrency': 'TRY'}}
    def parse(p): return parse_jsonld_products('<script type="application/ld+json">'+json.dumps(p)+'</script>')
    assert str(parse(product)[0].price) == '29.95'
    assert parse(product)[0].image_url.startswith('https://cdn2.a101.com.tr')
    assert not parse(dict(product, offers={'price': '29.95', 'priceCurrency': 'USD'}))
    assert not parse(dict(product, url='https://evil.test/sut_p-1234'))
    assert parse(dict(product, image='https://evil.test/logo.jpg'))[0].image_url is None
    assert not parse(dict(product, offers=[{'price': 20, 'priceCurrency':'TRY'}, {'price':30,'priceCurrency':'TRY'}]))


def test_tk_campaign_uses_full_title_for_pack_identity():
    html = '''<h1>26 - 30 Ekim 2026</h1><article><a href="/urun/renax-60li" title="Renax Bulaşık Makinesi Kapsülü 60'lı">Renax Bulaşık...</a><p>249,00 TL</p></article>'''
    _, offers, _ = parse_tk_campaign(html, 'https://www.tkkoop.com.tr/haftalik-kampanyalar/test', date(2026,10,26))
    assert offers and "60'lı" in offers[0].name
