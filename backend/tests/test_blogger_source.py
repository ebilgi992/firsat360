from datetime import datetime, timezone
from firsat360.blogger import parse_blogger_feed, discover_blogger_feeds

NOW=datetime(2026,10,26,tzinfo=timezone.utc)


def post(**overrides):
    return dict({'title':'A101 24 - 30 Ekim 2026 Aktüel Broşür','published':'2026-10-24T08:00:00Z',
                 'url':'https://example.blogspot.com/2026/10/brosur.html',
                 'content':'<a href="https://cdn2.a101.com.tr/afis.jpg"><img src="https://cdn2.a101.com.tr/thumb.jpg"></a>'}, **overrides)


def test_public_blogger_v3_and_v2_keep_large_image_and_provenance():
    result=parse_blogger_feed({'items':[post()]},'a101',NOW)
    c=result['candidates'][0]
    assert c['images']==['https://cdn2.a101.com.tr/afis.jpg'] and c['state']=='pending_source_review'
    p=post(); v2={'feed':{'entry':[{'title':{'$t':p['title']},'content':{'$t':p['content']},'published':{'$t':p['published']},'link':[{'rel':'alternate','href':p['url']}]}]}}
    assert parse_blogger_feed(v2,'a101',NOW)['candidates']


def test_old_posts_and_unverified_mirror_images_do_not_become_current_catalogs():
    assert not parse_blogger_feed({'items':[post(published='2023-09-29T00:00:00Z')]},'a101',NOW)['candidates']
    assert not parse_blogger_feed({'items':[post(content='<img src="https://blogger.googleusercontent.com/mirror.jpg">')]},'a101',NOW)['candidates']


def test_no_feed_configuration_means_no_private_key_or_remote_requests():
    class Client:
        def get(self,*a): raise AssertionError('Ağ isteği yapılmamalı')
    assert discover_blogger_feeds(Client(),[],NOW)['state']=='not_configured'
