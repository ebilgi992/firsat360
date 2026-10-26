from datetime import datetime, timedelta, timezone
import pytest
from firsat360.community import validate_a101
from firsat360.connectors.base import ConnectorError

NOW = datetime(2026, 10, 26, tzinfo=timezone.utc)
GOOD = {'Bu Hafta': ['https://cdn2.a101.com.tr/dbmk89vnr/CALL/Image/get/example.jpg']}


def test_error_empty_old_and_untrusted_cdn_are_rejected():
    for payload, when in [({'error': 'Veriler alınamadı'}, NOW), ({}, NOW),
                          (GOOD, NOW - timedelta(days=4)),
                          ({'Bu Hafta': ['https://cdn2.a101.com.tr.evil.test/dbmk89vnr/CALL/Image/get/x']}, NOW)]:
        with pytest.raises(ConnectorError): validate_a101(payload, when, NOW)


def test_recent_brochure_is_only_discovery_never_a_product_or_price():
    result = validate_a101(GOOD, NOW, NOW)
    assert result['state'] == 'pending_official_dates'
    assert 'offers' not in result and 'price' not in result
