"""Dört resmî sayfanın HTML kanıtını ve ayrıştırma sonucunu toplar; engelde durur."""
import json
from pathlib import Path
from firsat360.connectors.base import PoliteClient, ConnectorError
from firsat360.productpages import page_row

SLUGS = ['banvit-pilic-nugget-1000-g', 'doyfarm-pilic-nugget-1-kg',
         'renax-bulasik-makinesi-kapsulu-60li', 'solo-ultra-bambu-ozlu-kagit-havlu-16li']
out = Path('tk-evidence'); out.mkdir(exist_ok=True)
client = PoliteClient('FIRSAT360Bot/1.8 (+https://github.com/ebilgi992/firsat360)', min_interval_s=3)
rows = []
try:
    for slug in SLUGS:
        url = 'https://www.tkkoop.com.tr/urun/' + slug
        try:
            response = client.get(url)
            (out / (slug + '.html')).write_text(response.text, encoding='utf-8')
            row = page_row('tkkoop', url, response.text, str(response.url))
            rows.append(vars(row))
        except ConnectorError as exc:
            rows.append({'url': url, 'error': str(exc)})
            if any(s in str(exc) for s in ('403', '429', 'robots')): break
finally:
    client.close()
(out / 'report.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(rows, ensure_ascii=False, indent=2))
