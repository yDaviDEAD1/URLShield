# 🛡️ URL Shield — Detecção Matemática de Fraudes & Acessibilidade para Idosos

> **Projeto de Trabalho de Conclusão de Curso (TCC)**  
> Software de checagem inteligente de URLs, detecção de *typosquatting*, phishing e golpes com foco em acessibilidade para usuários idosos e integração com Telegram.

---

## 🌟 Funcionalidades Principais

- 🧮 **Motor Matemático de Análise Léxica**:
  - **Distância de Levenshtein & Similaridade**: Detecção de marcas imitadas (*Typosquatting*, como `bancodobras1l` ou `g00gle`).
  - **Entropia de Shannon \(H(X)\)**: Mede a imprevisibilidade/aleatoriedade de caracteres no domínio.
  - **Detecção Homográfica (Unicode/IDN)**: Identifica caracteres cirílicos disfarçados de letras latinas.
- 🌐 **Verificação de Infraestrutura & Rede**:
  - Consulta **WHOIS** (idade do domínio em dias).
  - Inspeção de certificado **SSL/TLS** e registros **DNS A/MX**.
- 🕵️ **Inspeção Ativa em Sandbox**:
  - Rastreamento de redirecionamentos HTTP e verificação de formulários de captura de senhas/cartões em HTML.
- ♿ **Acessibilidade para Idosos**:
  - **Ditado por Voz (`🎙️ Falar Link`)**: Reconhecimento de voz por microfone.
  - **Ajuda da Família pelo WhatsApp (`💬`)**: Encaminhamento direto de alertas com mensagem pré-formatada para familiares.
  - **Sintetizador de Voz (`🔊 Ouvir Explicação`)**: Leitura em áudio com velocidade ajustada.
  - **Modo Alto Contraste & Ajuste de Fonte**.
- 🤖 **Integração com Telegram Bot**:
  - Atendimento automatizado via chat no Telegram e simulador interativo na web.

---

## 🏗️ Arquitetura do Projeto

```
url-shield-tcc/
├── backend/
│   ├── app/
│   │   ├── main.py              # API FastAPI & Servidor Web
│   │   ├── math_engine.py       # Levenshtein, Entropia & Homógrafo
│   │   ├── network_checker.py   # WHOIS, SSL & DNS
│   │   ├── sandbox_crawler.py   # Inspeção Ativa de Páginas Web
│   │   ├── heuristics.py        # Consolidador de Score de Risco
│   │   ├── explainer.py         # Explicabilidade em Linguagem Acessível
│   │   └── telegram_bot.py      # Bot do Telegram
│   ├── requirements.txt
│   ├── test_backend.py          # Suíte de Testes Unitários
│   └── run_bot.py               # Executável do Telegram Bot
└── frontend/
    └── public/
        ├── index.html           # Interface Web (Padrão NordVPN)
        ├── styles.css           # Estilização CSS Responsiva
        └── app.js               # Conexão API e Reconhecimento de Voz
```

---

## 🚀 Como Executar Localmente

### 1. Iniciar o Servidor Web & API
```powershell
cd backend
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn app.main:app --port 8000 --reload
```
Acesse no navegador: **`http://localhost:8000`**

### 2. Rodar os Testes Unitários
```powershell
python test_backend.py
```

### 3. Iniciar o Bot do Telegram
```powershell
python run_bot.py SEU_TELEGRAM_BOT_TOKEN
```
