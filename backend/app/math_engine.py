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
    "apple.com", "netflix.com", "globo.com", "gov.br",
    # Pagamentos e Serviços Globais
    "paypal.com", "icloud.com", "linkedin.com", "correios.com.br"
]

# Encurtadores de link escondem o destino real do usuário
URL_SHORTENERS = [
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly",
    "rebrand.ly", "shorturl.at", "encurtador.com.br", "tiny.cc", "rb.gy", "s.id"
]

# Terminações baratas muito usadas em golpes (domínios descartáveis)
SUSPICIOUS_TLDS = [
    "top", "xyz", "shop", "online", "site", "click", "live", "icu", "buzz", "cfd",
    "sbs", "rest", "monster", "quest", "cyou", "bond", "lol", "store", "vip", "work"
]

# Substituições comuns de caracteres usadas em imitações (leetspeak)
LEET_MAP = str.maketrans({"0": "o", "1": "l", "3": "e", "4": "a", "5": "s", "7": "t", "8": "b", "@": "a", "$": "s"})


def is_trusted_domain(domain: str) -> bool:
    """Verifica se o domínio registrado é exatamente um domínio oficial (ou órgão .gov.br)."""
    domain = domain.lower()
    return domain in TRUSTED_DOMAINS or domain.endswith(".gov.br")

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
    registered = f"{domain_name_only}.{extracted.suffix}" if extracted.suffix else domain_name_only

    # Domínio oficial: não é imitação de si mesmo nem de marcas parecidas (ex: nubank x c6bank)
    if is_trusted_domain(registered):
        return {
            "is_typosquat": False,
            "is_trusted": True,
            "min_levenshtein_distance": 0,
            "best_matched_domain": registered,
            "similarity_percentage": 100.0,
            "impersonation_type": None
        }

    # Divide partes do domínio se houver hífens (ex: bancodobras1l-atualizacao -> ["bancodobras1l", "atualizacao"])
    parts = [domain_name_only] + [p for p in domain_name_only.split('-') if len(p) >= 3]

    # Marca oficial usada inteira em domínio de terceiros (ex: caixa-gov-fgts.top, nubank-cliente.com)
    # ou disfarçada com leetspeak (ex: paypa1 -> paypal)
    for part in parts:
        normalized = part.translate(LEET_MAP)
        for trusted in TRUSTED_DOMAINS:
            trusted_name = tldextract.extract(trusted).domain.lower()
            if len(trusted_name) < 4:
                continue  # nomes curtos (bb, x, gov) gerariam falsos positivos
            if normalized == trusted_name:
                dist = Levenshtein.distance(part, trusted_name)
                return {
                    "is_typosquat": True,
                    "is_trusted": False,
                    "min_levenshtein_distance": dist,
                    "best_matched_domain": trusted,
                    "similarity_percentage": round(Levenshtein.ratio(part, trusted_name) * 100, 2),
                    "impersonation_type": "leetspeak" if dist > 0 else "brand_in_domain"
                }

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
        "is_trusted": False,
        "min_levenshtein_distance": int(min_distance) if min_distance != float('inf') else -1,
        "best_matched_domain": best_match,
        "similarity_percentage": round(highest_similarity * 100, 2),
        "impersonation_type": "levenshtein" if is_typosquat else None
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
        "is_raw_ip": is_ip,
        "is_url_shortener": domain_full.lower() in URL_SHORTENERS,
        "tld": extracted.suffix.lower(),
        "is_suspicious_tld": extracted.suffix.lower() in SUSPICIOUS_TLDS
    }
