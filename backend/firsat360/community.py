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


ROBOT_REPO = 'keyiflerolsun/A101AktuelRobot'
ROBOT_DATA = 'https://raw.githubusercontent.com/' + ROBOT_REPO + '/main/A101.json'


def validate_robot(payload, committed_at, now, archived=False):
    """Yayımlı JSON şeması için bağımsız adaptör; robotun GPL/Telegram/scraper kodunu çalıştırmaz."""
    if archived:
        raise ConnectorError('A101AktuelRobot arşivlenmiş; çalışan güncel kaynak değil')
    if committed_at.tzinfo is None or not timedelta(0) <= now - committed_at <= timedelta(hours=72):
        raise ConnectorError('A101AktuelRobot çıktısı 72 saatten eski veya zamanı geçersiz')
    if not isinstance(payload, dict) or not payload or payload.get('error'):
        raise ConnectorError('A101AktuelRobot JSON çıktısı geçersiz')
    allowed = {'Bu Hafta', 'Gelecek Hafta', 'Haftanın Yıldızları', 'Büyük olduğu için UCUZ'}
    groups = {}
    for name, urls in payload.items():
        if name not in allowed or not isinstance(urls, list) or len(urls) > 80:
            raise ConnectorError('A101AktuelRobot şeması değişmiş veya sayfa sınırı aşılmış')
        clean = []
        for url in urls:
            if not isinstance(url, str): raise ConnectorError('A101AktuelRobot görsel adresi geçersiz')
            p = urlparse(url)
            official = (p.hostname == 'ayb.akinoncdn.com' and p.path.startswith('/cms/')) or (p.hostname == 'cdn2.a101.com.tr' and p.path.startswith('/dbmk89vnr/CALL/Image/get/'))
            if p.scheme != 'https' or not official or p.username or p.password:
                raise ConnectorError('A101AktuelRobot resmî CDN kalıbı dışında adres içeriyor')
            # Yeni commit altında tekrar eklenen eski CMS görselleri de güncel sayılmaz.
            import re
            from datetime import date
            stamp = re.search(r'/cms/(\d{4})/(\d{2})/(\d{2})/', p.path)
            if stamp:
                try: image_day = date(*map(int, stamp.groups()))
                except ValueError as exc: raise ConnectorError('A101AktuelRobot görsel tarihi geçersiz') from exc
                if not -7 <= (now.date() - image_day).days <= 14:
                    raise ConnectorError('A101AktuelRobot eski CMS görseli yayımlıyor')
            if url not in clean: clean.append(url)
        if clean: groups[name] = clean
    if not groups: raise ConnectorError('A101AktuelRobot boş broşür listesi döndürdü')
    return {'state': 'pending_official_dates', 'source': 'https://github.com/' + ROBOT_REPO,
            'committed_at': committed_at.isoformat(), 'groups': groups,
            'note': 'Yalnız broşür adayıdır; resmî kampanya tarihi doğrulanmadan güncel katalog/ürün sayılmaz.'}


def discover_robot(client, now=None):
    now = now or datetime.now(timezone.utc)
    source = 'https://github.com/' + ROBOT_REPO
    metadata = client.get('https://api.github.com/repos/' + ROBOT_REPO).json()
    if metadata.get('archived'):
        return {'state': 'archived', 'source': source, 'pushed_at': metadata.get('pushed_at'),
                'note': 'Kaynak arşivlenmiş; eski broşürler güncel yayına alınmadı.'}
    commits = client.get('https://api.github.com/repos/' + ROBOT_REPO + '/commits?path=A101.json&per_page=1').json()
    if not commits: raise ConnectorError('A101AktuelRobot veri commit tarihi bulunamadı')
    when = datetime.fromisoformat(commits[0]['commit']['committer']['date'].replace('Z', '+00:00'))
    # Eski dosya için veri/CDN istekleri yapılmaz.
    if not timedelta(0) <= now - when <= timedelta(hours=72):
        return {'state': 'stale', 'source': source, 'committed_at': when.isoformat(), 'note': 'Veri 72 saatten eski; yayına alınmadı.'}
    return validate_robot(client.get(ROBOT_DATA).json(), when, now)
