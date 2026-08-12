from typing import Dict, Any

def generate_accessible_explanation(heuristic_data: Dict[str, Any], lexical_data: Dict[str, Any], domain: str) -> Dict[str, Any]:
    """
    Gera um relatório de explicabilidade em linguagem simples, clara e direta para idosos,
    ideal para ser lido em voz alta (síntese de áudio).
    """
    level = heuristic_data.get("risk_level")
    score = heuristic_data.get("risk_score")
    penalties = heuristic_data.get("penalties", [])
    
    if level == "SEGURO":
        summary = f"Este endereço de site parece seguro. O nível de risco é de apenas {score}%."
        speech_text = f"Atenção. O link {domain} foi verificado e é seguro. Não foram encontrados sinais de golpe ou imitação."
        recommendation = "Você pode navegar com tranquilidade."
    elif level == "SUSPEITO":
        summary = f"Cuidado! Este link apresenta sinais suspeitos ({score}% de risco)."
        speech_text = f"Cuidado. O link {domain} é suspeito. Não digite senhas ou dados bancários neste site."
        recommendation = "Evite informar dados pessoais ou fazer pagamentos neste site sem confirmar antes com um familiar."
    else:
        summary = f"ALERTA DE PERIGO! Este link é muito provavelmente uma tentativa de golpe ({score}% de risco)."
        speech_text = f"Alerta vermelho. O link {domain} é perigoso e parece ser um golpe. Não abra este site."
        recommendation = "Não clique no link nem forneça nenhuma informação. Apague a mensagem recebida."

    # Pontos de atenção simples
    simple_reasons = []
    for p in penalties:
        simple_reasons.append(p["description"])

    return {
        "summary": summary,
        "speech_text": speech_text,
        "recommendation": recommendation,
        "reasons": simple_reasons
    }
