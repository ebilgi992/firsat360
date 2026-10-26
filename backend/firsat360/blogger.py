"""Herkese açık Blogger v2/v3 JSON -> broşür adayları. Anahtar ve özel yapılandırma okunmaz.

current-products-online istemcisinin belgelenmiş alan şekilleriyle uyumludur;
uygulamanın Java kodunu veya kişiye ait Blogger API anahtarını kullanmaz.
"""
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse, urljoin
import re
from .normalize import fold, parse_tr_range
from .connectors.base import ConnectorError
from .connectors.htmlutil import parse, all_image_urls

HOSTS = {'a101': ('a101.com.tr',), 'bim': ('bim.com.tr',),
         'sok': ('sokmarket.com.tr', 'images.ceptesok.com'), 'migros': ('migros.com.tr',)}


def _text(value):
    return value.get('$t', '') if isinstance(value, dict) else value if isinstance(value, str) else ''


def parse_blogger_feed(payload, store, now):
    if store not in HOSTS or not isinstance(payload, dict):
        raise ConnectorError('Blogger kaynağı/şeması geçersiz')
    rows = payload.get('items')
    if rows is None: rows = payload.get('feed', {}).get('entry', [])
    if not isinstance(rows, list): raise ConnectorError('Blogger gönderi listesi geçersiz')
    candidates, skipped = [], {}
    for item in rows[:30]:
        reason = None
        if not isinstance(item, dict): continue
        title = _text(item.get('title'))
        content = _text(item.get('content'))
        source = item.get('url')
        if not source:
            source = next((v.get('href') for v in item.get('link', []) if isinstance(v, dict) and v.get('rel') == 'alternate'), '')
        if not isinstance(source, str) or not source.startswith('https://'):
            reason = 'invalid_source'
        try:
            published = datetime.fromisoformat(_text(item.get('published')).replace('Z','+00:00'))
            if published.tzinfo is None or not -timedelta(days=1) <= now - published <= timedelta(days=7): reason = 'stale_post'
        except (ValueError, TypeError): reason = 'missing_publication_date'
        if not any(w in fold(title) for w in ('brosur','katalog','afis','aktuel','aldin aldin','indirim')): reason = 'not_brochure'
        if reason:
            skipped[reason] = skipped.get(reason,0)+1; continue
        tree = parse(content)
        urls = [urljoin(source,u) for u in all_image_urls(tree)]
        # Küçük resim yerine gönderinin kendi büyük görsel bağlantısı varsa onu koru.
        full = [urljoin(source,a.attributes.get('href','')) for a in tree.css('a[href]')
                if re.search(r'\.(?:png|jpe?g|webp)(?:[?#]|$)', a.attributes.get('href',''), re.I)]
        urls = full or urls
        images = []
        for url in urls:
            p = urlparse(url)
            host = (p.hostname or '').lower()
            if p.scheme == 'https' and not p.username and not p.password and any(host == h or host.endswith('.'+h) for h in HOSTS[store]):
                if url not in images: images.append(url)
        if not images:
            skipped['no_verified_market_images'] = skipped.get('no_verified_market_images',0)+1; continue
        first, last = parse_tr_range(title, published.date())
        if last is not None and last < now.date():
            skipped['expired_campaign'] = skipped.get('expired_campaign',0)+1; continue
        candidates.append({'store':store,'title':title[:200],'source_url':source,'published_at':published.isoformat(),
            'valid_from':str(first) if first else None,'valid_to':str(last) if last else None,
            'images':images[:80], 'state':'pending_source_review'})
    return {'state':'candidate_only','candidates':candidates,'skipped':skipped,
            'note':'Üçüncü taraf gönderi; ürün/fiyat veya doğrulanmış resmî katalog değildir.'}


def discover_blogger_feeds(client, sources, now):
    if not sources:
        return {'state':'not_configured','required':'public_blogger_feed_url',
                'repository':'https://github.com/AbdulazizFMMS/current-products-online',
                'note':'Depo herkese açık canlı veri adresi sağlamıyor; başka uygulamanın erişim/API anahtarı kullanılmadı.'}
    results = []
    for source in sources[:4]:
        try:
            url, store = source['url'], source['store']
            p = urlparse(url)
            if p.scheme != 'https' or p.username or p.password or re.search(r'(?:[?&])(?:key|api[_-]?key|token|access_token|auth|secret)=',url,re.I):
                raise ConnectorError('Yalnız anahtarsız HTTPS public feed kabul edilir')
            results.append({'url':url, **parse_blogger_feed(client.get(url).json(),store,now)})
        except Exception as exc:
            results.append({'state':'unavailable','error':str(exc)[:200]})
    return {'state':'checked','sources':results}
