"""A101_Afis: yalnızca resmî CDN bağlantıları için yardımcı keşif kaynağı.

Üçüncü tarafın commit saati kampanya tarihi değildir. Tarihler resmî kaynaktan
onaylanmadan ürün, fiyat, güncel katalog veya resmî görsel doğrulaması üretilmez.
"""
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

from .connectors.base import ConnectorError

DATA_URL = 'https://raw.githubusercontent.com/codermert/A101_Afis/main/A101.json'
COMMITS_URL = 'https://api.github.com/repos/codermert/A101_Afis/commits?path=A101.json&per_page=1'


def validate_a101(payload, committed_at, now):
    if not isinstance(payload, dict) or payload.get('error'):
        raise ConnectorError('A101_Afis geçerli veri sağlamıyor')
    if committed_at.tzinfo is None or not timedelta(0) <= now - committed_at <= timedelta(hours=72):
        raise ConnectorError('A101_Afis çıktısı eski veya zamanı geçersiz')
    groups = {}
    allowed = {'Bu Hafta', 'Gelecek Hafta', 'Haftanın Yıldızları', 'Büyük olduğu için UCUZ'}
    for name, urls in payload.items():
        if name not in allowed or not isinstance(urls, list):
            raise ConnectorError('A101_Afis şeması değişti')
        clean = []
        for url in urls:
            p = urlparse(url) if isinstance(url, str) else None
            if not p or p.scheme != 'https' or p.hostname != 'cdn2.a101.com.tr' or not p.path.startswith('/dbmk89vnr/CALL/Image/get/') or p.username or p.password:
                raise ConnectorError('A101_Afis resmî CDN dışı adres içeriyor')
            if url not in clean: clean.append(url)
        if clean: groups[name] = clean
    if not groups: raise ConnectorError('A101_Afis boş')
    return {'state': 'pending_official_dates', 'groups': groups,
            'committed_at': committed_at.isoformat(), 'source': DATA_URL,
            'note': 'Yalnızca broşür adayı; ürün/fiyat/görsel doğrulaması değildir.'}


def discover_a101(client, now=None):
    now = now or datetime.now(timezone.utc)
    payload = client.get(DATA_URL).json()
    # Hata/boş veri için commit API'sine gereksiz istek yapılmaz.
    if not isinstance(payload, dict) or payload.get('error') or not payload:
        raise ConnectorError('A101_Afis geçerli veri sağlamıyor')
    commits = client.get(COMMITS_URL).json()
    if not commits: raise ConnectorError('A101_Afis commit tarihi yok')
    when = datetime.fromisoformat(commits[0]['commit']['committer']['date'].replace('Z', '+00:00'))
    return validate_a101(payload, when, now)
