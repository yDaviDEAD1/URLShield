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

    // Gerar Laudo Técnico em PDF
    if (pdfReportBtn) {
        pdfReportBtn.addEventListener('click', () => {
            if (!currentUrlAnalyzed) return;
            populateLaudoTemplate();
            window.print();
        });
    }

    function populateLaudoTemplate() {
        if (!lastAnalysisData) return;
        const data = lastAnalysisData;
        const h = data.heuristics;
        const exp = data.accessible_explanation;
        const tech = data.technical_details;

        const randomProtocol = 'URLSHIELD-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000);
        document.getElementById('laudo-protocol-num').textContent = randomProtocol;
        document.getElementById('laudo-timestamp').textContent = new Date().toLocaleString('pt-BR');

        document.getElementById('laudo-url').textContent = data.url_analyzed;
        document.getElementById('laudo-domain').textContent = data.domain;
        document.getElementById('laudo-risk-level').textContent = h.risk_level;
        document.getElementById('laudo-risk-score').textContent = h.risk_score + '%';

        document.getElementById('laudo-summary-text').textContent = exp.summary;
        document.getElementById('laudo-recommendation-text').textContent = "Orientação: " + exp.recommendation;

        const typosquat = (tech.lexical_math || {}).typosquat_info || {};
        document.getElementById('laudo-metric-levenshtein').textContent = typosquat.is_typosquat 
            ? `ALERTA: Imitação de '${typosquat.best_matched_domain}' (Similaridade ${typosquat.similarity_percentage}%)`
            : `OK (Similaridade máx: ${typosquat.similarity_percentage || 0}%)`;

        document.getElementById('laudo-metric-entropy').textContent = `H(X) = ${tech.lexical_math?.domain_entropy || 0}`;
        document.getElementById('laudo-metric-homograph').textContent = tech.lexical_math?.is_homograph ? `ALERTA: ${tech.lexical_math.homograph_reason}` : "Normal (ASCII)";
        
        const age = tech.network?.domain_age_days;
        document.getElementById('laudo-metric-whois').textContent = age !== null ? `${age} dias` : "Não informado";
        
        const ssl = tech.network?.ssl || {};
        document.getElementById('laudo-metric-ssl').textContent = ssl.has_ssl ? `Válido (${ssl.issuer})` : "SEM SSL / Inseguro";

        const crawl = tech.sandbox_crawl || {};
        document.getElementById('laudo-metric-sandbox').textContent = crawl.is_accessible 
            ? `Status ${crawl.status_code} | Título: ${crawl.page_title || 'N/A'}`
            : (crawl.details || "Inacessível");
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

