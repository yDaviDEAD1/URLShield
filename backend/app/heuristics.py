from typing import Dict, Any, List

def calculate_heuristic_score(lexical_data: Dict[str, Any], network_data: Dict[str, Any], crawl_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Consolida todas as métricas (Matemática, Rede e Inspeção Ativa) em uma pontuação de risco ponderada (0 a 100%).
    """
    score = 0.0
    penalties: List[Dict[str, Any]] = []

    # 1. Typosquatting (Distância de Levenshtein pequena com marca famosa)
    typosquat = lexical_data.get("typosquat_info", {})
    if typosquat.get("is_typosquat"):
        matched = typosquat.get("best_matched_domain")
        dist = typosquat.get("min_levenshtein_distance")
        score += 40.0
        penalties.append({
            "category": "Typosquatting (Nome Imitação)",
            "points": 40.0,
            "description": f"O endereço tenta se passar por '{matched}' alterando apenas {dist} caractere(s)."
        })

    # 2. Ataque Homográfico (Unicode disfarçado)
    if lexical_data.get("is_homograph"):
        score += 35.0
        penalties.append({
            "category": "Ataque Homográfico",
            "points": 35.0,
            "description": lexical_data.get("homograph_reason")
        })

    # 3. Entropia de Shannon Alta no Domínio (> 3.5)
    domain_entropy = lexical_data.get("domain_entropy", 0.0)
    if domain_entropy > 3.5:
        score += 15.0
        penalties.append({
            "category": "Alta Entropia (Aleatoriedade)",
            "points": 15.0,
            "description": f"O nome do domínio possui alta imprevisibilidade matemática ({domain_entropy}), típica de links gerados por robôs."
        })

    # 4. Uso de IP direto em vez de domínio
    if lexical_data.get("is_raw_ip"):
        score += 25.0
        penalties.append({
            "category": "IP Numérico Direto",
            "points": 25.0,
            "description": "O link usa um endereço IP numérico direto sem um nome de domínio registrado."
        })

    # 5. Domínio Recente (< 30 dias)
    if network_data.get("is_recent_domain"):
        age = network_data.get("domain_age_days", 0)
        score += 20.0
        penalties.append({
            "category": "Domínio Recém-Criado",
            "points": 20.0,
            "description": f"Este site foi registrado há apenas {age} dia(s). Golpistas frequentemente criam e descartam sites novos."
        })

    # 6. Ausência ou Erro de SSL
    ssl_info = network_data.get("ssl", {})
    if not ssl_info.get("has_ssl"):
        score += 15.0
        penalties.append({
            "category": "Sem Conexão Segura (SSL)",
            "points": 15.0,
            "description": "O site não possui um certificado de segurança válido."
        })

    # 7. Símbolos Especiais em Excesso (@ ou hífens)
    special = lexical_data.get("special_chars", {})
    if special.get("at_symbols", 0) > 0:
        score += 20.0
        penalties.append({
            "category": "Caractere '@' no Link",
            "points": 20.0,
            "description": "O caractere '@' na URL faz o navegador ignorar a primeira parte e redirecionar para um destino oculto."
        })
    if special.get("hyphens", 0) > 3:
        score += 10.0
        penalties.append({
            "category": "Excesso de Hífens",
            "points": 10.0,
            "description": "Domínios de golpe frequentemente usam múltiplos hífens para parecerem legítimos."
        })

    # 8. Achados da Inspeção Ativa em Sandbox
    if crawl_data.get("has_password_input") and typosquat.get("is_typosquat"):
        score += 25.0
        penalties.append({
            "category": "Captura de Senha em Domínio Imitação",
            "points": 25.0,
            "description": "A página pede senha de acesso, mas o endereço não pertence à empresa oficial."
        })

    found_kw = crawl_data.get("found_suspicious_keywords", [])
    if found_kw:
        score += 15.0
        penalties.append({
            "category": "Palavras-Chave de Golpe",
            "points": 15.0,
            "description": f"Encontradas expressões típicas de fraudes na página: {', '.join(found_kw[:3])}."
        })

    # Limita o score máximo em 100%
    final_score = min(round(score, 1), 100.0)

    # Classificação
    if final_score <= 25.0:
        level = "SEGURO"
        color = "#10B981"  # Verde
    elif final_score <= 65.0:
        level = "SUSPEITO"
        color = "#F59E0B"  # Amarelo
    else:
        level = "PERIGOSO"
        color = "#EF4444"  # Vermelho

    return {
        "risk_score": final_score,
        "risk_level": level,
        "badge_color": color,
        "penalties": penalties
    }
