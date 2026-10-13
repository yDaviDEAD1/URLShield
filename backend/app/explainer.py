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
        summary = f"Não encontramos sinais fortes de golpe neste endereço. O nível de risco calculado foi de {score}%."
        speech_text = f"O link {domain} foi verificado. Não encontramos sinais fortes de golpe, mas continue atento a pedidos de senha, dinheiro ou dados pessoais."
        recommendation = "Você pode continuar com atenção. Nenhuma análise consegue garantir segurança total."
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


# Versão em linguagem simples de cada penalidade, para o pop-up do celular
FRIENDLY_REASONS = {
    "Ataque Homográfico": "O endereço usa letras de outro alfabeto que parecem iguais às nossas.",
    "Alta Entropia (Aleatoriedade)": "O nome do site parece inventado às pressas.",
    "IP Numérico Direto": "O link usa números no lugar do nome de um site.",
    "Link Encurtado": "O link é encurtado e esconde o endereço verdadeiro.",
    "Sem Conexão Segura (SSL)": "O site não tem o cadeado de conexão segura.",
    "Caractere '@' no Link": "O link usa um truque com \"@\" para esconder o destino verdadeiro.",
    "Excesso de Hífens": "O endereço tem muitos hífens, comum em sites falsos.",
    "Captura de Senha em Domínio Imitação": "A página pede sua senha, mas não é o site oficial.",
    "Palavras-Chave de Golpe": "A página usa frases comuns em golpes.",
}


def friendly_reasons(penalties):
    """Traduz as penalidades técnicas; as que já estão em linguagem simples são mantidas."""
    reasons = []
    for penalty in penalties:
        text = FRIENDLY_REASONS.get(penalty["category"], penalty["description"])
        if penalty["category"] == "Domínio Recém-Criado":
            text = penalty["description"].split(". ")[0] + "."
        reasons.append(text)
    return reasons
