"""Regression coverage for audit findings F06, F07 and F08."""
from datetime import datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace
import shutil
import subprocess

import pytest

from app import main, math_engine, network_checker
from app.heuristics import calculate_heuristic_score


@pytest.mark.parametrize("offset", [None, 0, 5.5, -12, 14])
def test_whois_age_normalizes_naive_and_aware_dates(monkeypatch, offset):
    # A six-hour margin avoids clock-boundary flakiness without freezing time.
    created = datetime.now(timezone.utc) - timedelta(days=5, hours=6)
    created = (created.replace(tzinfo=None) if offset is None
               else created.astimezone(timezone(timedelta(hours=offset))))
    monkeypatch.setattr(network_checker, "_load_whois_entry",
                        lambda _: SimpleNamespace(creation_date=created))
    assert network_checker.get_domain_age_days("example.com") == 5


def test_whois_uses_first_valid_datetime_in_list(monkeypatch):
    created = datetime.now(timezone.utc) - timedelta(days=5, hours=6)
    dates = [None, "not a date", created, created - timedelta(days=100)]
    monkeypatch.setattr(network_checker, "_load_whois_entry",
                        lambda _: SimpleNamespace(creation_date=dates))
    assert network_checker.get_domain_age_days("example.com") == 5


@pytest.mark.parametrize("dates", [[], [None, "invalid"], None])
def test_whois_without_valid_date_remains_unknown(monkeypatch, dates):
    monkeypatch.setattr(network_checker, "_load_whois_entry",
                        lambda _: SimpleNamespace(creation_date=dates))
    assert network_checker.get_domain_age_days("example.com") is None


def test_whois_future_date_does_not_produce_negative_age(monkeypatch):
    created = datetime.now(timezone.utc) + timedelta(days=1)
    monkeypatch.setattr(network_checker, "_load_whois_entry",
                        lambda _: SimpleNamespace(creation_date=created))
    assert network_checker.get_domain_age_days("example.com") == 0


def test_recent_aware_whois_date_still_adds_twenty_points(monkeypatch):
    created = datetime.now(timezone.utc) - timedelta(days=5, hours=6)
    monkeypatch.setattr(network_checker, "_load_whois_entry",
                        lambda _: SimpleNamespace(creation_date=created))
    monkeypatch.setattr(network_checker, "check_ssl_certificate", lambda _: {"has_ssl": True})
    monkeypatch.setattr(network_checker, "check_dns_records", lambda _: {})
    network = network_checker.analyze_network("itau.com.br")
    report = calculate_heuristic_score(math_engine.analyze_url_lexical("https://itau.com.br"), network, {})
    assert network["domain_age_days"] == 5
    assert network["is_recent_domain"] is True
    assert report["risk_score"] == 20


@pytest.mark.parametrize("url,expected_score,at_count,hyphen_count", [
    ("https://itau.com.br/?q=@----", 0, 0, 0),
    ("https://itau.com.br/#@----", 0, 0, 0),
    ("https://itau.com.br/a@----?q=@----#@----", 0, 0, 0),
    ("https://itau.com.br/a%40b?x=----", 0, 0, 0),
    ("https://person@itau.com.br/", 20, 1, 0),
    ("https://a----b@itau.com.br/", 20, 1, 0),
    ("https://a-b-c-d-e.itau.com.br/", 10, 0, 4),
    ("https://a-b-c-d.itau.com.br/", 0, 0, 3),
])
def test_special_characters_use_authority_and_host_only(healthy_network, url, expected_score, at_count, hyphen_count):
    lexical = math_engine.analyze_url_lexical(url)
    assert lexical["special_chars"]["at_symbols"] == at_count
    assert lexical["special_chars"]["hyphens"] == hyphen_count
    assert calculate_heuristic_score(lexical, healthy_network, {})["risk_score"] == expected_score


def test_full_analysis_official_query_stays_zero(monkeypatch, healthy_network):
    monkeypatch.setattr(main, "analyze_network", lambda _: healthy_network)
    report = main.full_url_analysis("https://itau.com.br/?q=@----", perform_deep_inspection=False)
    assert report["heuristics"]["risk_score"] == 0
    assert report["heuristics"]["risk_level"] == "SEGURO"
    assert report["heuristics"]["penalties"] == []


@pytest.mark.parametrize("domain", ["x1.com", "bb1.com"])
def test_short_brand_does_not_trigger_approximate_match(domain):
    assert math_engine.calculate_typosquatting_distance(domain)["is_typosquat"] is False


def test_three_character_brands_are_still_compared(monkeypatch):
    monkeypatch.setattr(math_engine, "TRUSTED_DOMAINS", ["abc.com", "x.com", "bb.com"])
    result = math_engine.calculate_typosquatting_distance("abx.com")
    assert result["is_typosquat"] is True
    assert result["best_matched_domain"] == "abc.com"


@pytest.mark.parametrize("domain,official", [
    ("paypa1.com", "paypal.com"),
    ("g00gle.com", "google.com"),
    ("bancodobras1l.com.br", "bancodobrasil.com.br"),
    ("caixa-gov-fgts.top", "caixa.gov.br"),
])
def test_existing_brand_impersonation_is_preserved(domain, official):
    result = math_engine.calculate_typosquatting_distance(domain)
    assert result["is_typosquat"] is True
    assert result["best_matched_domain"] == official


def test_android_offline_regressions(tmp_path):
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        pytest.skip("JDK required for Android Java engine regression checks")
    root = Path(__file__).resolve().parents[2]
    source = root / "android/app/src/main/java/br/com/urlshield/OfflineAnalyzer.java"
    harness = Path(__file__).parent / "java/OfflineAnalyzerRegression.java"
    # Java 8 bytecode also runs when the default java and javac use different JDKs.
    compiled = subprocess.run([javac, "--release", "8", "-encoding", "UTF-8", "-d", str(tmp_path),
                               str(source), str(harness)], capture_output=True, text=True, timeout=60)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    run = subprocess.run([java, "-cp", str(tmp_path), "br.com.urlshield.OfflineAnalyzerRegression"],
                         capture_output=True, text=True, timeout=30)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "13 Android regression checks passed" in run.stdout
