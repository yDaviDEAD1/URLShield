import os
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, HttpUrl
from typing import Dict, Any, Optional

from app.math_engine import analyze_url_lexical, TRUSTED_DOMAINS
from app.network_checker import analyze_network
from app.sandbox_crawler import inspect_page_safely
from app.heuristics import calculate_heuristic_score
from app.explainer import generate_accessible_explanation

app = FastAPI(
    title="URL Shield API",
    description="API de Detecção Matemática e Heurística de Fraudes em URLs (TCC)",
    version="1.0.0"
)

# Habilita CORS para o Frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class URLAnalysisRequest(BaseModel):
    url: str
    deep_inspection: Optional[bool] = True

def full_url_analysis(url: str, perform_deep_inspection: bool = True) -> Dict[str, Any]:
    """
    Função principal que orquestra a análise em 4 camadas.
    """
    cleaned_url = url.strip()
    if not cleaned_url.startswith("http://") and not cleaned_url.startswith("https://"):
        cleaned_url = "https://" + cleaned_url

    # 1. Análise Léxica & Matemática de Strings (Levenshtein, Entropia, Homógrafo)
    lexical_results = analyze_url_lexical(cleaned_url)
    domain_full = lexical_results["domain_full"]

    # 2. Análise de Infraestrutura & Rede (WHOIS, SSL, DNS)
    network_results = analyze_network(domain_full)

    # 3. Inspeção Ativa em Sandbox (opcional)
    crawl_results = {}
    if perform_deep_inspection:
        crawl_results = inspect_page_safely(cleaned_url)

    # 4. Cálculo Heurístico da Pontuação de Risco
    heuristic_results = calculate_heuristic_score(lexical_results, network_results, crawl_results)

    # 5. Explicabilidade para Idosos
    accessible_results = generate_accessible_explanation(heuristic_results, lexical_results, domain_full)

    return {
        "url_analyzed": cleaned_url,
        "domain": domain_full,
        "heuristics": heuristic_results,
        "accessible_explanation": accessible_results,
        "technical_details": {
            "lexical_math": lexical_results,
            "network": network_results,
            "sandbox_crawl": crawl_results
        }
    }

@app.get("/api/health")
def health_check():
    return {"status": "online", "service": "URL Shield API", "version": "1.0.0"}

@app.get("/api/trusted-domains")
def get_trusted_domains():
    return {"trusted_domains": TRUSTED_DOMAINS}

@app.post("/api/analyze")
def analyze_url_endpoint(payload: URLAnalysisRequest):
    if not payload.url or len(payload.url) < 3:
        raise HTTPException(status_code=400, detail="URL inválida ou muito curta.")
    
    try:
        report = full_url_analysis(payload.url, perform_deep_inspection=payload.deep_inspection)
        return report
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erro durante a análise da URL: {str(e)}")

# Monta a interface web estática (HTML/CSS/JS) para servir na raiz "/"
frontend_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "frontend", "public"))
if os.path.exists(frontend_path):
    app.mount("/", StaticFiles(directory=frontend_path, html=True), name="static")

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)

