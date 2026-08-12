import httpx
from bs4 import BeautifulSoup
from typing import Dict, Any, List
import urllib.parse

SUSPICIOUS_KEYWORDS = [
    "atualização cadastral", "bloqueio de conta", "sua conta foi suspensa",
    "recadastramento obrigatório", "valide seus dados", "código de segurança",
    "confirme sua senha", "premio exclusivo", "resgate seu pix", "ganhou um pix",
    "urgente", "sua conta será cancelada", "promoção imperdível"
]

def inspect_page_safely(url: str) -> Dict[str, Any]:
    """
    Realiza uma inspeção ativa da página em um ambiente controlado (sandbox),
    analisando a cadeia de redirecionamentos, título, formulários e palavras-chave suspeitas.
    """
    result = {
        "status_code": None,
        "final_url": url,
        "redirect_chain": [],
        "has_password_input": False,
        "has_credit_card_input": False,
        "page_title": "",
        "found_suspicious_keywords": [],
        "is_accessible": False,
        "details": "Página não pôde ser inspecionada."
    }
    
    # Adiciona http:// se não fornecido
    if not url.startswith("http://") and not url.startswith("https://"):
        url = "https://" + url

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 (URLShield-Inspection/1.0)"
    }
    
    try:
        # Usa httpx em modo assíncrono/síncrono controlado com timeout rápido
        with httpx.Client(follow_redirects=True, timeout=5.0, verify=False) as client:
            response = client.get(url, headers=headers)
            
            result["status_code"] = response.status_code
            result["final_url"] = str(response.url)
            result["is_accessible"] = response.status_code == 200
            
            # Registra histórico de redirecionamentos
            if response.history:
                result["redirect_chain"] = [str(r.url) for r in response.history] + [str(response.url)]
                
            # Se obteve resposta com conteúdo HTML
            content_type = response.headers.get("content-type", "").lower()
            if "text/html" in content_type:
                soup = BeautifulSoup(response.text, "html.parser")
                
                # Título da página
                title_tag = soup.find("title")
                if title_tag and title_tag.string:
                    result["page_title"] = title_tag.string.strip()
                    
                # Procura por campos de senha ou cartão
                password_inputs = soup.find_all("input", {"type": "password"})
                result["has_password_input"] = len(password_inputs) > 0
                
                card_inputs = soup.find_all("input", lambda tag: tag.get("name") and any(k in tag.get("name").lower() for k in ["card", "cartao", "cvv", "ccv"]))
                result["has_credit_card_input"] = len(card_inputs) > 0
                
                # Procura palavras-chave de golpe no texto da página
                page_text = soup.get_text().lower()
                found_kw = [kw for kw in SUSPICIOUS_KEYWORDS if kw in page_text]
                result["found_suspicious_keywords"] = found_kw
                
                result["details"] = "Inspeção ativa realizada com sucesso."
    except Exception as e:
        result["details"] = f"Não foi possível acessar a página de forma segura: {str(e)}"
        
    return result
