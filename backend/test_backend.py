import unittest
from app.math_engine import calculate_shannon_entropy, calculate_typosquatting_distance, check_homograph_attack, analyze_url_lexical
from app.main import build_mobile_response, full_url_analysis

class TestURLShieldBackend(unittest.TestCase):

    def test_shannon_entropy(self):
        # Domínio comum com baixa entropia
        entropy_low = calculate_shannon_entropy("google.com")
        # Domínio gerado por algoritmo (DGA) com alta entropia
        entropy_high = calculate_shannon_entropy("x9q2m1z8b7w4p.xyz")
        
        self.assertLess(entropy_low, entropy_high)
        self.assertGreater(entropy_high, 3.2)

    def test_typosquatting_detection(self):
        # Imitação do Banco do Brasil
        res_bb = calculate_typosquatting_distance("bancodobras1l.com.br")
        self.assertTrue(res_bb["is_typosquat"])
        self.assertEqual(res_bb["best_matched_domain"], "bancodobrasil.com.br")
        
        # Imitação do Google
        res_g = calculate_typosquatting_distance("g00gle.com")
        self.assertTrue(res_g["is_typosquat"])
        self.assertEqual(res_g["best_matched_domain"], "google.com")

        # Domínio legítimo exato
        res_clean = calculate_typosquatting_distance("google.com")
        self.assertFalse(res_clean["is_typosquat"])

    def test_homograph_attack(self):
        # Punycode
        is_homo, reason = check_homograph_attack("xn--gogle-pqa.com")
        self.assertTrue(is_homo)

    def test_full_analysis_integration(self):
        # Teste de integração completo com URL maliciosa de teste
        result = full_url_analysis("http://bancodobras1l-atualizacao.xyz/login", perform_deep_inspection=False)
        self.assertIn("heuristics", result)
        self.assertIn("accessible_explanation", result)
        self.assertGreaterEqual(result["heuristics"]["risk_score"], 40.0)
        self.assertEqual(result["heuristics"]["risk_level"], "PERIGOSO")

    def test_mobile_response_blocks_dangerous_link(self):
        report = full_url_analysis(
            "http://bancodobras1l-atualizacao.xyz/login",
            perform_deep_inspection=False,
        )
        mobile = build_mobile_response(report)

        self.assertEqual(mobile["verdict"], "BLOCK")
        self.assertEqual(mobile["title"], "Não abra este link")
        self.assertEqual(mobile["primary_action"], "Fechar e voltar")
        self.assertLessEqual(len(mobile["reasons"]), 3)

    def test_mobile_safe_wording_does_not_promise_total_safety(self):
        report = {
            "url_analyzed": "https://example.com",
            "domain": "example.com",
            "heuristics": {
                "risk_level": "SEGURO",
                "risk_score": 0.0,
            },
            "accessible_explanation": {
                "summary": "Não encontramos sinais fortes de golpe.",
                "recommendation": "Continue com atenção.",
                "speech_text": "Continue com atenção.",
                "reasons": [],
            },
        }

        mobile = build_mobile_response(report)

        self.assertEqual(mobile["verdict"], "ALLOW")
        self.assertIn("Nenhum sinal forte", mobile["title"])

    def test_trusted_domain_is_not_typosquat(self):
        # nubank x c6bank têm distância 2, mas nubank.com.br é oficial
        res = calculate_typosquatting_distance("nubank.com.br")
        self.assertFalse(res["is_typosquat"])
        self.assertTrue(res["is_trusted"])

    def test_leetspeak_and_brand_in_domain(self):
        res_pp = calculate_typosquatting_distance("paypa1.com")
        self.assertTrue(res_pp["is_typosquat"])
        self.assertEqual(res_pp["best_matched_domain"], "paypal.com")

        res_cx = calculate_typosquatting_distance("caixa-gov-fgts.top")
        self.assertTrue(res_cx["is_typosquat"])
        self.assertEqual(res_cx["impersonation_type"], "brand_in_domain")

    def test_url_shortener_flagged(self):
        self.assertTrue(analyze_url_lexical("https://bit.ly/3abc")["is_url_shortener"])

    def test_invalid_url_rejected(self):
        with self.assertRaises(ValueError):
            full_url_analysis("httpx://???", perform_deep_inspection=False)

    def test_crawler_blocks_internal_addresses(self):
        from app.sandbox_crawler import inspect_page_safely
        for internal in ["http://127.0.0.1:8000/", "http://169.254.169.254/latest/meta-data/", "http://localhost/"]:
            result = inspect_page_safely(internal)
            self.assertFalse(result["is_accessible"])
            self.assertIn("rede privada", result["details"])

    def test_crawler_detects_phishing_form(self):
        import threading
        from http.server import HTTPServer, BaseHTTPRequestHandler
        from unittest import mock
        from app import sandbox_crawler

        page = (
            "<html><head><title>Banco - Atualizacao</title></head><body>"
            "<p>Sua conta foi suspensa. Valide seus dados.</p>"
            "<form><input class='campo' name='cpf'><input type='password' name='senha'>"
            "<input name='numero_cartao'></form></body></html>"
        ).encode("utf-8")

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(page)

            def log_message(self, *args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            # o servidor de teste é local, então a proteção SSRF é desligada só aqui
            with mock.patch.object(sandbox_crawler, "is_public_address", return_value=True):
                result = sandbox_crawler.inspect_page_safely(f"http://127.0.0.1:{server.server_port}/")
        finally:
            server.shutdown()

        self.assertTrue(result["has_password_input"])
        self.assertTrue(result["has_credit_card_input"])
        self.assertIn("sua conta foi suspensa", result["found_suspicious_keywords"])

if __name__ == "__main__":
    unittest.main()
