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

if __name__ == "__main__":
    unittest.main()
