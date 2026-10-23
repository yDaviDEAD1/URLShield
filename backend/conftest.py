"""Keep pytest independent of external DNS, WHOIS, TLS and PSL updates."""
from copy import deepcopy

import pytest
import tldextract

from app import main


@pytest.fixture
def healthy_network():
    return {
        "domain_age_days": 365,
        "is_recent_domain": False,
        "ssl": {"has_ssl": True},
        "dns": {"has_a_record": True, "has_mx_record": True},
    }


@pytest.fixture(autouse=True)
def offline_public_suffix_list(monkeypatch):
    monkeypatch.setattr(tldextract, "extract", tldextract.TLDExtract(suffix_list_urls=()))


@pytest.fixture(autouse=True)
def isolate_legacy_network_calls(request, monkeypatch, healthy_network):
    # The original orchestration tests do not assert live infrastructure data.
    # Keep their assertions intact while removing external service dependence.
    if request.path.name == "test_backend.py":
        monkeypatch.setattr(main, "analyze_network", lambda _: deepcopy(healthy_network))
