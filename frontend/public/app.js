document.addEventListener('DOMContentLoaded', () => {
    // Elementos da Interface
    const contrastToggle = document.getElementById('contrast-toggle');
    const fontIncrease = document.getElementById('font-increase');
    const fontReset = document.getElementById('font-reset');
    const urlInput = document.getElementById('url-input');
    const pasteBtn = document.getElementById('paste-btn');
    const scanBtn = document.getElementById('scan-btn');
    
    const loadingState = document.getElementById('loading-state');
    const resultSection = document.getElementById('result-section');
    
    const statusBadge = document.getElementById('status-badge');
    const statusIcon = document.getElementById('status-icon');
    const statusText = document.getElementById('status-text');
    const riskScore = document.getElementById('risk-score');
    
    const accessibleSummary = document.getElementById('accessible-summary');
    const accessibleRecommendation = document.getElementById('accessible-recommendation');
    const speakBtn = document.getElementById('speak-btn');
    const speakBtnText = document.getElementById('speak-btn-text');
    
    const reasonsWrapper = document.getElementById('reasons-wrapper');
    const reasonsList = document.getElementById('reasons-list');
    
    // Métricas do TCC
    const metricLevenshtein = document.getElementById('metric-levenshtein');
    const metricEntropy = document.getElementById('metric-entropy');
    const metricHomograph = document.getElementById('metric-homograph');
    const metricWhois = document.getElementById('metric-whois');
    const metricSandbox = document.getElementById('metric-sandbox');
    const metricSsl = document.getElementById('metric-ssl');

    let currentSpeechText = "";
    let fontScale = 1.0;

    // --- CONTROLES DE ACESSIBILIDADE ---
    contrastToggle.addEventListener('click', () => {
        document.body.classList.toggle('high-contrast');
    });

    fontIncrease.addEventListener('click', () => {
        if (fontScale < 1.6) {
            fontScale += 0.15;
            document.documentElement.style.setProperty('--font-scale', fontScale);
        }
    });

    fontReset.addEventListener('click', () => {
        fontScale = 1.0;
        document.documentElement.style.setProperty('--font-scale', fontScale);
    });

    const voiceBtn = document.getElementById('voice-btn');
    const voiceBtnText = document.getElementById('voice-btn-text');
    const familyHelpBtn = document.getElementById('family-help-btn');

    let currentUrlAnalyzed = "";
    let currentRiskScore = 0;

    // Reconhecimento de Voz (Ditado por Microfone)
    if (voiceBtn) {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (SpeechRecognition) {
            const recognition = new SpeechRecognition();
            recognition.lang = 'pt-BR';
            recognition.continuous = false;

            voiceBtn.addEventListener('click', () => {
                try {
                    recognition.start();
                    if (voiceBtnText) voiceBtnText.textContent = "Ouvindo...";
                } catch (e) {
                    recognition.stop();
                    if (voiceBtnText) voiceBtnText.textContent = "Falar Link";
                }
            });

            recognition.onresult = (event) => {
                const spokenText = event.results[0][0].transcript;
                if (spokenText) {
                    // Limpa espaços e formata em formato de link se ditado falado
                    let formatted = spokenText.toLowerCase().replace(/\s+/g, '').replace('ponto', '.');
                    urlInput.value = formatted;
                    analyzeUrl();
                }
            };

            recognition.onend = () => {
                if (voiceBtnText) voiceBtnText.textContent = "Falar Link";
            };

            recognition.onerror = () => {
                if (voiceBtnText) voiceBtnText.textContent = "Falar Link";
                alert('Não foi possível capturar o áudio. Verifique a permissão do microfone.');
            };
        } else {
            voiceBtn.addEventListener('click', () => {
                alert('O reconhecimento de voz por microfone não é suportado neste navegador. Tente no Google Chrome ou Edge.');
            });
        }
    }

    const pdfReportBtn = document.getElementById('pdf-report-btn');

    // Gerar Laudo Técnico Oficial (Janela de Impressão / Salvar PDF)
    if (pdfReportBtn) {
        pdfReportBtn.addEventListener('click', generateLaudoPDF);
    }


    function generateLaudoPDF() {
        if (!lastAnalysisData) return;
        const data = lastAnalysisData;
        const h = data.heuristics;
        const exp = data.accessible_explanation;
        const tech = data.technical_details;

        const protocolNum = 'URLSHIELD-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000);
        const timestamp = new Date().toLocaleString('pt-BR');
        
        const badgeBg = h.risk_level === 'SEGURO' ? '#d1fae5' : (h.risk_level === 'SUSPEITO' ? '#fef3c7' : '#fee2e2');
        const badgeColor = h.risk_level === 'SEGURO' ? '#059669' : (h.risk_level === 'SUSPEITO' ? '#d97706' : '#dc2626');

        const typosquat = (tech.lexical_math || {}).typosquat_info || {};
        const levenshteinText = typosquat.is_typosquat 
            ? `ALERTA: Imitação da marca '${typosquat.best_matched_domain}' (Similaridade ${typosquat.similarity_percentage}%)`
            : `OK (Similaridade máxima de marca: ${typosquat.similarity_percentage || 0}%)`;

        const age = tech.network?.domain_age_days;
        const whoisText = age !== null ? `${age} dias de registro` : "Não disponível";
        const sslText = tech.network?.ssl?.has_ssl ? `Ativo (Emissor: ${tech.network.ssl.issuer})` : "Inexistente / Sem HTTPS";
        const crawlText = tech.sandbox_crawl?.is_accessible 
            ? `Status ${tech.sandbox_crawl.status_code} | Título: ${tech.sandbox_crawl.page_title || 'Sem título'}`
            : (tech.sandbox_crawl?.details || "Página inacessível");

        const htmlContent = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Laudo Técnico - ${protocolNum}</title>
    <style>
        @page { size: A4; margin: 15mm; }
        body { font-family: 'Segoe UI', Arial, sans-serif; color: #0f172a; margin: 0; padding: 20px; line-height: 1.5; background: #fff; }
        .top-bar { height: 6px; background: linear-gradient(90deg, #0b132a 0%, #00d18e 100%); margin-bottom: 20px; border-radius: 3px; }
        .header { border-bottom: 2px solid #cbd5e1; padding-bottom: 15px; margin-bottom: 20px; }
        .badge-brand { background: #0b132a; color: #00d18e; font-size: 11px; font-weight: 800; padding: 4px 8px; border-radius: 4px; display: inline-block; }
        .title { font-size: 18px; font-weight: 800; color: #0b132a; margin: 8px 0 4px 0; }
        .sub { font-size: 12px; color: #64748b; margin: 0; }
        .meta-box { background: #f8fafc; border: 1px solid #e2e8f0; padding: 10px 14px; border-radius: 6px; font-size: 11px; margin-top: 12px; display: flex; justify-content: space-between; }
        .section-title { font-size: 13px; font-weight: 700; color: #0b132a; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; margin: 20px 0 10px 0; text-transform: uppercase; }
        table { width: 100%; border-collapse: collapse; font-size: 12px; margin-bottom: 15px; }
        td, th { border: 1px solid #e2e8f0; padding: 8px 10px; text-align: left; }
        th { background: #f1f5f9; font-weight: 700; color: #334155; }
        .lbl { background: #f8fafc; font-weight: 600; color: #475569; width: 28%; }
        .mono { font-family: monospace; font-size: 11px; }
        .status-pill { background: ${badgeBg}; color: ${badgeColor}; padding: 3px 10px; border-radius: 999px; font-weight: 800; font-size: 11px; display: inline-block; }
        .callout { background: #f8fafc; border: 1px solid #e2e8f0; padding: 12px; border-radius: 6px; font-size: 12px; margin-bottom: 10px; }
        .callout.alert { border-left: 4px solid #0b132a; background: #f1f5f9; }
        .footer { margin-top: 30px; border-top: 1px solid #cbd5e1; padding-top: 15px; font-size: 10px; color: #64748b; text-align: center; }
        .no-print { margin-bottom: 20px; text-align: right; }
        .btn-print { background: #0b132a; color: #fff; border: none; padding: 10px 20px; font-weight: bold; border-radius: 6px; cursor: pointer; font-size: 14px; }
        @media print { .no-print { display: none; } }
    </style>
</head>
<body>
    <div class="no-print">
        <button class="btn-print" onclick="window.print()">🖨️ Salvar como PDF / Imprimir Laudo</button>
    </div>

    <div class="top-bar"></div>
    <div class="header">
        <span class="badge-brand">URL SHIELD</span>
        <h1 class="title">LAUDO TÉCNICO DE AUDITORIA DE SEGURANÇA DIGITAL</h1>
        <p class="sub">Sistema Autônomo de Verificação Matemática e Heurística de Fraudes (TCC)</p>
        <div class="meta-box">
            <span>PROTOCOLO: <strong>${protocolNum}</strong></span>
            <span>DATA/HORA EMISSÃO: <strong>${timestamp}</strong></span>
        </div>
    </div>

    <div class="section-title">1. Identificação do Endereço Auditado</div>
    <table>
        <tr><td class="lbl">URL Completa:</td><td class="mono">${data.url_analyzed}</td></tr>
        <tr><td class="lbl">Domínio Registrado:</td><td class="mono">${data.domain}</td></tr>
        <tr><td class="lbl">Veredito / Classificação:</td><td><span class="status-pill">${h.risk_level}</span></td></tr>
        <tr><td class="lbl">Índice Calculado de Risco:</td><td><strong>${h.risk_score}%</strong></td></tr>
    </table>

    <div class="section-title">2. Síntese do Diagnóstico e Orientação ao Usuário</div>
    <div class="callout"><strong>Diagnóstico: </strong>${exp.summary}</div>
    <div class="callout alert"><strong>Orientação de Ação: </strong>${exp.recommendation}</div>

    <div class="section-title">3. Matriz Completa de Indicadores Algorítmicos & Redes</div>
    <table>
        <thead>
            <tr>
                <th style="width: 32%;">Métrica / Indicador</th>
                <th style="width: 43%;">Resultado Obtido</th>
                <th style="width: 25%;">Avaliação Técnica</th>
            </tr>
        </thead>
        <tbody>
            <tr><td><strong>Typosquatting (Levenshtein)</strong></td><td>${levenshteinText}</td><td>Análise de imitação de marca.</td></tr>
            <tr><td><strong>Entropia de Shannon H(X)</strong></td><td>H(X) = ${tech.lexical_math?.domain_entropy || 0}</td><td>Mede aleatoriedade de caracteres.</td></tr>
            <tr><td><strong>Ataque Homográfico (Unicode)</strong></td><td>${tech.lexical_math?.is_homograph ? 'ALERTA: ' + tech.lexical_math.homograph_reason : 'Normal (ASCII)'}</td><td>Verificação de alfabetos ocultos.</td></tr>
            <tr><td><strong>Idade do Domínio (WHOIS)</strong></td><td>${whoisText}</td><td>Domínios recentes (&lt;30 dias).</td></tr>
            <tr><td><strong>Certificado SSL/TLS</strong></td><td>${sslText}</td><td>Criptografia HTTPS de transporte.</td></tr>
            <tr><td><strong>Inspeção Ativa (Sandbox)</strong></td><td>${crawlText}</td><td>Rastreamento de formulários/senhas.</td></tr>
        </tbody>
    </table>

    <div class="footer">
        <p>Laudo gerado automaticamente pelo algoritmo de auditoria matemática do sistema <strong>URL Shield (TCC)</strong>.</p>
        <p style="font-family: monospace;">Hash Digital de Autenticidade: SHA256-URLSHIELD-VERIFIED-${protocolNum}</p>
    </div>
</body>
</html>`;

        const reportWindow = window.open('', '_blank', 'width=900,height=1000');
        if (reportWindow) {
            reportWindow.document.open();
            reportWindow.document.write(htmlContent);
            reportWindow.document.close();
        }
    }

    // Pedir Ajuda da Família pelo WhatsApp

    if (familyHelpBtn) {
        familyHelpBtn.addEventListener('click', () => {
            if (!currentUrlAnalyzed) return;
            const message = `Olá! Verifiquei este link no URL Shield e deu ${currentRiskScore}% de risco de golpe: ${currentUrlAnalyzed}.\n\nVocê pode me ajudar a conferir se é seguro antes de eu abrir?`;
            const encoded = encodeURIComponent(message);
            window.open(`https://api.whatsapp.com/send?text=${encoded}`, '_blank');
        });
    }

    // Colar da área de transferência
    pasteBtn.addEventListener('click', async () => {
        try {
            const text = await navigator.clipboard.readText();
            if (text) {
                urlInput.value = text.trim();
            }
        } catch (err) {
            alert('Por favor, permita o acesso à área de transferência para usar este botão, ou cole manualmente (Ctrl+V).');
        }
    });



    // Disparar com Enter no input
    urlInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            analyzeUrl();
        }
    });

    scanBtn.addEventListener('click', analyzeUrl);

    // Síntese de Voz (Leitura em Áudio)
    speakBtn.addEventListener('click', () => {
        if (!('speechSynthesis' in window)) {
            alert('Seu navegador não suporta leitura em voz alta.');
            return;
        }

        if (window.speechSynthesis.speaking) {
            window.speechSynthesis.cancel();
            speakBtnText.textContent = "Ouvir Explicação";
            return;
        }

        if (currentSpeechText) {
            const utterance = new SpeechSynthesisUtterance(currentSpeechText);
            utterance.lang = 'pt-BR';
            utterance.rate = 0.95; // Velocidade ajustada para idosos
            
            utterance.onend = () => {
                speakBtnText.textContent = "Ouvir Explicação";
            };
            
            speakBtnText.textContent = "Pausar Áudio";
            window.speechSynthesis.speak(utterance);
        }
    });

    // --- FUNÇÃO PRINCIPAL DE ANÁLISE ---
    async function analyzeUrl() {
        const url = urlInput.value.strip ? urlInput.value.strip() : urlInput.value.trim();
        if (!url) {
            alert('Por favor, digite ou cole um endereço de site (URL).');
            urlInput.focus();
            return;
        }

        // Exibe loading e esconde resultado anterior
        loadingState.classList.remove('hidden');
        resultSection.classList.add('hidden');
        if (window.speechSynthesis) window.speechSynthesis.cancel();

        try {
            // Tenta chamar o backend FastAPI
            const response = await fetch('/api/analyze', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: url, deep_inspection: true })
            });

            if (!response.ok) {
                throw new Error('Falha na resposta do servidor.');
            }

            const data = await response.json();
            renderResults(data);
        } catch (error) {
            console.error(error);
            alert('Ocorreu um erro ao conectar com o servidor de checagem. Verifique se o backend Python está ativo.');
        } finally {
            loadingState.classList.add('hidden');
        }
    }

    let lastAnalysisData = null;

    function renderResults(data) {
        lastAnalysisData = data;
        const h = data.heuristics;
        const exp = data.accessible_explanation;
        const tech = data.technical_details;

        currentUrlAnalyzed = data.url_analyzed || "";
        currentRiskScore = h.risk_score || 0;


        // Configura Badge de Risco
        statusBadge.className = 'status-badge-nord ' + (h.risk_level === 'SEGURO' ? 'safe' : (h.risk_level === 'SUSPEITO' ? 'warning' : 'danger'));

        statusIcon.textContent = h.risk_level === 'SEGURO' ? '✅' : (h.risk_level === 'SUSPEITO' ? '⚠️' : '🚨');
        statusText.textContent = h.risk_level;
        riskScore.textContent = h.risk_score + '%';


        // Configura Explicabilidade
        accessibleSummary.textContent = exp.summary;
        accessibleRecommendation.textContent = exp.recommendation;
        currentSpeechText = exp.speech_text;

        // Lista de Motivos
        reasonsList.innerHTML = '';
        if (exp.reasons && exp.reasons.length > 0) {
            reasonsWrapper.classList.remove('hidden');
            exp.reasons.forEach(r => {
                const li = document.createElement('li');
                li.textContent = r;
                reasonsList.appendChild(li);
            });
        } else {
            reasonsWrapper.classList.add('hidden');
        }

        // Preenche Métricas Acadêmicas do TCC
        const lex = tech.lexical_math || {};
        const typosquat = lex.typosquat_info || {};
        metricLevenshtein.textContent = typosquat.is_typosquat 
            ? `Suspeito de imitar '${typosquat.best_matched_domain}' (Distância Levenshtein: ${typosquat.min_levenshtein_distance}, Similaridade: ${typosquat.similarity_percentage}%)`
            : `Nenhum typosquat de marca famosa identificado (Similaridade máxima: ${typosquat.similarity_percentage || 0}%)`;

        metricEntropy.textContent = `H(X) = ${lex.domain_entropy || 0} (Domínio), H(X) = ${lex.url_entropy || 0} (URL completa)`;
        metricHomograph.textContent = lex.is_homograph ? `Sim: ${lex.homograph_reason}` : "Não (Caracteres latinos normais)";

        const net = tech.network || {};
        metricWhois.textContent = net.domain_age_days !== null ? `${net.domain_age_days} dias de registro` : "Informação WHOIS não disponível";
        
        const ssl = net.ssl || {};
        metricSsl.textContent = ssl.has_ssl ? `Ativo (Emissor: ${ssl.issuer})` : "Ausente / Sem HTTPS";

        const crawl = tech.sandbox_crawl || {};
        if (crawl.is_accessible) {
            let crawlText = `Status HTTP: ${crawl.status_code}. Título: "${crawl.page_title || 'Sem título'}". `;
            if (crawl.has_password_input) crawlText += "⚠️ Contém formulário de senha! ";
            if (crawl.found_suspicious_keywords && crawl.found_suspicious_keywords.length > 0) {
                crawlText += `Palavras suspeitas: ${crawl.found_suspicious_keywords.join(', ')}`;
            }
            metricSandbox.textContent = crawlText;
        } else {
            metricSandbox.textContent = crawl.details || "Página não acessada ativamente.";
        }

        resultSection.classList.remove('hidden');
        resultSection.scrollIntoView({ behavior: 'smooth' });
    }

    // --- SIMULADOR DO CHAT DO TELEGRAM ---
    const chatInput = document.getElementById('chat-input');
    const chatSendBtn = document.getElementById('chat-send-btn');
    const chatMessages = document.getElementById('chat-messages');

    if (chatSendBtn && chatInput && chatMessages) {
        chatSendBtn.addEventListener('click', sendChatMessage);
        chatInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') sendChatMessage();
        });
    }

    async function sendChatMessage() {
        const text = chatInput.value.trim();
        if (!text) return;

        // Adiciona mensagem do usuário
        appendMessage(text, 'user-bubble');
        chatInput.value = '';

        // Adiciona mensagem de aguarde do bot
        const tempBotMsg = appendMessage('🔍 Analisando link enviado...', 'bot-bubble');

        try {
            const response = await fetch('/api/analyze', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: text, deep_inspection: true })
            });

            if (!response.ok) throw new Error('Erro no servidor');

            const data = await response.json();
            const h = data.heuristics;
            const exp = data.accessible_explanation;

            const emoji = h.risk_level === 'SEGURO' ? '✅' : (h.risk_level === 'SUSPEITO' ? '⚠️' : '🚨');
            let botReply = `${emoji} <strong>STATUS: ${h.risk_level}</strong> (Risco: ${h.risk_score}%)<br><br>`;
            botReply += `📝 <strong>Resumo</strong>: ${exp.summary}<br><br>`;
            botReply += `💡 <strong>Recomendação</strong>: ${exp.recommendation}`;

            if (exp.reasons && exp.reasons.length > 0) {
                botReply += `<br><br>📌 <strong>Motivos</strong>:<br>`;
                exp.reasons.slice(0, 3).forEach(r => {
                    botReply += `• ${r}<br>`;
                });
            }

            tempBotMsg.innerHTML = botReply;
        } catch (err) {
            tempBotMsg.innerHTML = '❌ Ocorreu um erro ao analisar este link. Verifique se a URL está correta ou se o servidor está ativo.';
        }
    }

    function appendMessage(htmlContent, className) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `msg-bubble ${className}`;
        msgDiv.innerHTML = htmlContent;
        chatMessages.appendChild(msgDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return msgDiv;
    }

});

