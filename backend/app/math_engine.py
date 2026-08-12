import math
import re
from typing import Dict, List, Any, Tuple
import tldextract
import Levenshtein

# Lista de domínios confiáveis alvos de phishing frequente no Brasil e no mundo
TRUSTED_DOMAINS = [
    # Bancos e Finanças (Brasil)
    "caixa.gov.br", "bb.com.br", "bancodobrasil.com.br", "itau.com.br", "bradesco.com.br",
    "santander.com.br", "nubank.com.br", "bancopan.com.br", "inter.co",
    "pagbank.com.br", "mercadopago.com.br", "picpay.com", "c6bank.com.br",
    # E-commerce e Serviços
    "mercadolivre.com.br", "amazon.com.br", "magazineluiza.com.br",
    "americanas.com.br", "casasbahia.com.br", "shopee.com.br", "aliexpress.com",
    # Redes Sociais e Mensageria
    "whatsapp.com", "instagram.com", "facebook.com", "telegram.org",
    "tiktok.com", "youtube.com", "twitter.com", "x.com",
    # Big Techs & Portais
    "google.com", "google.com.br", "outlook.com", "live.com", "microsoft.com",
    "apple.com", "netflix.com", "globo.com", "gov.br"
]

def calculate_shannon_entropy(text: str) -> float:
    """
    Calcula a Entropia de Shannon de uma string H(X) = -sum(P(x_i) * log2(P(x_i))).
    Mede a imprevisibilidade/aleatoriedade de caracteres na URL.
    Domínios legítimos tendem a ter menor entropia (ex: google = ~2.0),
    enquanto domínios gerados por algoritmo (DGA) ou maliciosos têm alta entropia (ex: x9q2m1z8b = ~3.8).
    """
    if not text:
        return 0.0
    
    length = len(text)
    char_counts: Dict[str, int] = {}
    for char in text:
        char_counts[char] = char_counts.get(char, 0) + 1
        
    entropy = 0.0
    for count in char_counts.values():
        prob = count / length
        entropy -= prob * math.log2(prob)
        
    return round(entropy, 4)

def check_homograph_attack(domain: str) -> Tuple[bool, str]:
    """
    Detecta ataques homográficos Unicode (IDN Spoofing) e caracteres de alfabetos misturados
    (ex: usar o 'а' cirílico no lugar do 'a' latino).
    """
    is_homograph = False
    reason = "Nenhum caractere homógrafo detectado."
    
    # Checa punycode (xn--)
    if domain.startswith("xn--") or ".xn--" in domain:
        return True, "Domínio em código Punycode (IDN), que permite esconder caracteres visuais falsos."
    
    # Checa se possui caracteres fora do conjunto ASCII seguro
    non_ascii = [c for c in domain if ord(c) > 127]
    if non_ascii:
        is_homograph = True
        reason = f"Contém caracteres não-latinos ({', '.join(set(non_ascii))}) disfarçados de letras comuns."
        
    return is_homograph, reason

def calculate_typosquatting_distance(domain: str) -> Dict[str, Any]:
    """
    Calcula a menor Distância de Levenshtein entre o domínio informado
    (e suas partes compostas por hífens) e os domínios oficiais conhecidos.
    """
    extracted = tldextract.extract(domain)
    domain_name_only = extracted.domain.lower()
    
    # Divide partes do domínio se houver hífens (ex: bancodobras1l-atualizacao -> ["bancodobras1l", "atualizacao"])
    parts = [domain_name_only] + [p for p in domain_name_only.split('-') if len(p) >= 3]
    
    best_match = ""
    min_distance = float('inf')
    highest_similarity = 0.0
    
    for part in parts:
        for trusted in TRUSTED_DOMAINS:
            trusted_extracted = tldextract.extract(trusted)
            trusted_name = trusted_extracted.domain.lower()
            
            # Distância de Levenshtein
            dist = Levenshtein.distance(part, trusted_name)
            ratio = Levenshtein.ratio(part, trusted_name)
            
            if dist > 0:  # exclui correspondência exata perfeita
                if ratio > highest_similarity:
                    highest_similarity = ratio
                    best_match = trusted
                    min_distance = dist
                elif ratio == highest_similarity and dist < min_distance:
                    best_match = trusted
                    min_distance = dist
            
    is_typosquat = False
    if (0 < min_distance <= 2 and highest_similarity >= 0.60) or (min_distance == 3 and highest_similarity >= 0.70):
        is_typosquat = True
        
    return {
        "is_typosquat": is_typosquat,
        "min_levenshtein_distance": int(min_distance) if min_distance != float('inf') else -1,
        "best_matched_domain": best_match,
        "similarity_percentage": round(highest_similarity * 100, 2)
    }



def analyze_url_lexical(url: str) -> Dict[str, Any]:
    """
    Executa a análise estatística e matemática completa da string da URL.
    """
    extracted = tldextract.extract(url)
    domain_full = f"{extracted.domain}.{extracted.suffix}" if extracted.suffix else extracted.domain
    subdomain = extracted.subdomain
    
    # Medidas Matemáticas
    url_length = len(url)
    domain_length = len(domain_full)
    subdomain_count = len(subdomain.split('.')) if subdomain else 0
    
    # Entropia de Shannon no domínio e na URL completa
    domain_entropy = calculate_shannon_entropy(domain_full)
    url_entropy = calculate_shannon_entropy(url)
    
    # Estatística de caracteres
    digit_count = sum(c.isdigit() for c in domain_full)
    digit_ratio = round(digit_count / max(domain_length, 1), 4)
    
    special_chars = {
        "hyphens": url.count("-"),
        "at_symbols": url.count("@"),
        "double_slashes": url.count("//") - 1 if url.count("//") > 0 else 0,
        "dots": url.count(".")
    }
    
    # Verificação de Homógrafo e Typosquatting
    is_homograph, homograph_reason = check_homograph_attack(domain_full)
    typosquat_info = calculate_typosquatting_distance(domain_full)
    
    # Verificação se é IP numérico direto (ex: http://192.168.1.1/login)
    is_ip = bool(re.match(r'^(?:http[s]?://)?(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?(?:/.*)?$', url))

    return {
        "domain_full": domain_full,
        "subdomain": subdomain,
        "url_length": url_length,
        "domain_length": domain_length,
        "subdomain_count": subdomain_count,
        "domain_entropy": domain_entropy,
        "url_entropy": url_entropy,
        "digit_ratio": digit_ratio,
        "special_chars": special_chars,
        "is_homograph": is_homograph,
        "homograph_reason": homograph_reason,
        "typosquat_info": typosquat_info,
        "is_raw_ip": is_ip
    }
