from datetime import datetime, timedelta, timezone
import pytest
from firsat360.community import validate_robot, discover_robot
from firsat360.connectors.base import ConnectorError

NOW = datetime(2026,10,26,tzinfo=timezone.utc)
GOOD = {'Bu Hafta':['https://cdn2.a101.com.tr/dbmk89vnr/CALL/Image/get/example.jpg']}


def test_robot_rejects_archive_stale_data_and_recommitted_old_images():
    for payload, when, archived in [(GOOD,NOW,True),(GOOD,NOW-timedelta(days=4),False),
        ({'Bu Hafta':['https://ayb.akinoncdn.com/cms/2023/09/29/example.jpg']},NOW,False),
        ({'Bu Hafta':['https://evil.test/post.jpg']},NOW,False),({'error':'offline'},NOW,False)]:
        with pytest.raises(ConnectorError): validate_robot(payload,when,NOW,archived)


def test_robot_fresh_brochure_is_candidate_not_product():
    result = validate_robot(GOOD,NOW,NOW)
    assert result['state'] == 'pending_official_dates' and 'offers' not in result


def test_archived_repository_does_not_trigger_a101_or_cdn_requests():
    class Response:
        def json(self): return {'archived':True,'pushed_at':'2023-09-29T13:05:29Z'}
    class Client:
        urls = []
        def get(self, url): self.urls.append(url); return Response()
    client = Client()
    assert discover_robot(client,NOW)['state'] == 'archived'
    assert client.urls == ['https://api.github.com/repos/keyiflerolsun/A101AktuelRobot']
