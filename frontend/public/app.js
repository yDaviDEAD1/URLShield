document.addEventListener('DOMContentLoaded', () => {
    const $ = (id) => document.getElementById(id);

    const checkerForm = $('checker');
    const urlInput = $('url-input');
    const checkerError = $('checker-error');
    const pasteBtn = $('paste-btn');
    const voiceBtn = $('voice-btn');
    const voiceBtnText = $('voice-btn-text');

    const loadingState = $('loading-state');
    const loadingHint = $('loading-hint');
    const resultSection = $('result-section');

    const verdictSign = $('verdict-sign');
    const verdictTitle = $('verdict-title');
    const verdictSubtitle = $('verdict-subtitle');
    const verdictDomain = $('verdict-domain');
    const todoList = $('todo-list');
    const reasonsTitle = $('reasons-title');
    const reasonsList = $('reasons-list');
    const openSiteBtn = $('open-site-btn');
    const familyHelpBtn = $('family-help-btn');
    const speakBtn = $('speak-btn');
    const speakBtnText = $('speak-btn-text');

    const LOADING_HINT = loadingHint.textContent;

    // Cada nível de risco vira uma placa, com textos para quem não é técnico
    const VERDICTS = {
        PERIGOSO: {
            sign: 'stop',
            title: 'Não abra este link.',
            reasonsTitle: 'Por que a placa é Pare',
            todo: [
                'Não toque no link e não digite senha, CPF ou código.',
                'Apague a mensagem que você recebeu.',
                'Na dúvida, fale com a empresa pelos canais oficiais ou peça ajuda a alguém da família.'
            ]
        },
        SUSPEITO: {
            sign: 'warn',
            title: 'Atenção com este link.',
            reasonsTitle: 'Por que a placa é Atenção',
            todo: [
                'Só abra se você esperava receber este link.',
                'Não digite senha nem dados do banco nesse site.',
                'Na dúvida, mande para alguém da família conferir.'
            ]
        },
        SEGURO: {
            sign: 'go',
            title: 'Pode seguir, com atenção.',
            reasonsTitle: 'O que conferimos',
            todo: [
                'Nenhum banco pede senha ou código por mensagem.',
                'Se pedirem dinheiro com pressa, desconfie mesmo assim.'
            ]
        }
    };

    let lastAnalysis = null;

    // ---------- preferências de acessibilidade ----------
    const storage = {
        get(key) { try { return localStorage.getItem(key); } catch (e) { return null; } },
        set(key, value) { try { localStorage.setItem(key, value); } catch (e) { /* modo privado */ } }
    };

    const FONT_STEPS = [
        { scale: 1, label: 'Letra maior' },
        { scale: 1.15, label: 'Letra ainda maior' },
        { scale: 1.3, label: 'Letra normal' }
    ];
    let fontStep = Number(storage.get('urlshield-font-step')) || 0;
    const fontToggle = $('font-toggle');
    const fontToggleText = $('font-toggle-text');

    function applyFontStep() {
        const step = FONT_STEPS[fontStep] || FONT_STEPS[0];
        document.documentElement.style.setProperty('--font-scale', step.scale);
        fontToggleText.textContent = step.label;
    }
    fontToggle.addEventListener('click', () => {
        fontStep = (fontStep + 1) % FONT_STEPS.length;
        storage.set('urlshield-font-step', fontStep);
        applyFontStep();
    });
    applyFontStep();

    const contrastToggle = $('contrast-toggle');
    function applyContrast(on) {
        document.body.classList.toggle('high-contrast', on);
        contrastToggle.setAttribute('aria-pressed', String(on));
    }
    contrastToggle.addEventListener('click', () => {
        const on = !document.body.classList.contains('high-contrast');
        storage.set('urlshield-contrast', on ? '1' : '0');
        applyContrast(on);
    });
    applyContrast(storage.get('urlshield-contrast') === '1');

    // ---------- entrada do link ----------
    function showInputError(message) {
        checkerError.textContent = message;
        urlInput.setAttribute('aria-invalid', message ? 'true' : 'false');
    }

    pasteBtn.addEventListener('click', async () => {
        try {
            const text = await navigator.clipboard.readText();
            if (text && text.trim()) {
                urlInput.value = text.trim();
                showInputError('');
                urlInput.focus();
            } else {
                showInputError('Não há nenhum link copiado. Copie o link na mensagem e tente de novo.');
            }
        } catch (err) {
            showInputError('Não conseguimos colar sozinhos. Toque e segure na caixa branca e escolha “Colar”.');
            urlInput.focus();
        }
    });

    // O microfone só é pedido quando a pessoa toca em "Falar"
    let recognition = null;
    voiceBtn.addEventListener('click', () => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
            showInputError('Este navegador não entende voz. Tente no Google Chrome ou cole o link.');
            return;
        }
        if (!recognition) {
            recognition = new SpeechRecognition();
            recognition.lang = 'pt-BR';
            recognition.continuous = false;
            recognition.onresult = (event) => {
                const spoken = event.results[0][0].transcript || '';
                const link = spoken.toLowerCase()
                    .replace(/\s+ponto\s+/g, '.')
                    .replace(/\s+barra\s+/g, '/')
                    .replace(/\s+/g, '');
                if (link) {
                    urlInput.value = link;
                    analyzeUrl();
                }
            };
            recognition.onend = () => { voiceBtnText.textContent = 'Falar'; };
            recognition.onerror = () => {
                voiceBtnText.textContent = 'Falar';
                showInputError('Não conseguimos ouvir. Verifique se o microfone está liberado ou cole o link.');
            };
        }
        try {
            recognition.start();
            voiceBtnText.textContent = 'Ouvindo…';
            showInputError('');
        } catch (e) {
            recognition.stop();
        }
    });

    checkerForm.addEventListener('submit', (event) => {
        event.preventDefault();
        analyzeUrl();
    });

    // ---------- análise ----------
    async function analyzeUrl() {
        const url = urlInput.value.trim();
        if (!url) {
            showInputError('Cole ou fale o link que você recebeu.');
            urlInput.focus();
            return;
        }
        showInputError('');
        if (window.speechSynthesis) window.speechSynthesis.cancel();

        resultSection.classList.add('hidden');
        loadingHint.textContent = LOADING_HINT;
        loadingState.classList.remove('hidden');
        loadingState.scrollIntoView({ behavior: 'smooth', block: 'center' });

        // No plano gratuito o servidor "dorme"; avisa se a resposta demorar
        const slowTimer = setTimeout(() => {
            loadingHint.textContent = 'Está demorando um pouco mais que o normal. Na primeira consulta do dia isso pode levar até 1 minuto.';
        }, 7000);

        try {
            const response = await fetch('/api/analyze', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url, deep_inspection: true })
            });
            if (response.status === 400) {
                showInputError('Isso não parece um link. Confira se copiou o endereço inteiro.');
                urlInput.focus();
                return;
            }
            if (!response.ok) throw new Error('HTTP ' + response.status);
            renderResult(await response.json());
        } catch (error) {
            console.error(error);
            showInputError('Não conseguimos falar com o verificador agora. Tente de novo em alguns segundos.');
        } finally {
            clearTimeout(slowTimer);
            loadingState.classList.add('hidden');
        }
    }

    function signMarkup(kind) {
        if (kind === 'stop') return '<svg width="170" height="170"><use href="#sign-stop"/></svg>';
        if (kind === 'warn') return '<svg width="180" height="180"><use href="#sign-warn"/></svg>';
        return '';
    }

    function buildGoSign(domain) {
        const sign = document.createElement('div');
        sign.className = 'go-sign';
        sign.innerHTML =
            '<div class="go-sign-inner"><div class="go-sign-text">' +
            '<div class="go-sign-kicker">Destino</div><div class="go-sign-domain"></div>' +
            '</div><svg width="34" height="48" viewBox="0 0 24 32"><path d="M12 29V5M3 13l9-9 9 9" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg></div>';
        sign.querySelector('.go-sign-domain').textContent = domain;
        return sign;
    }

    // Frase principal: diz quem está sendo imitado, quando sabemos
    function buildSubtitle(level, data) {
        const lex = data.technical_details?.lexical_math || {};
        const typo = lex.typosquat_info || {};
        if (level === 'SEGURO') {
            return typo.is_trusted
                ? 'Este é um endereço oficial conhecido. Não achamos sinais de golpe.'
                : 'Não achamos sinais de golpe neste endereço.';
        }
        if (typo.is_typosquat && typo.best_matched_domain) {
            return level === 'PERIGOSO'
                ? `Ele está se passando por ${typo.best_matched_domain} para enganar você.`
                : `Ele se parece com ${typo.best_matched_domain}, mas não é o mesmo endereço.`;
        }
        if (lex.is_url_shortener) return 'Este link é encurtado. Ele esconde para onde vai levar você.';
        if (lex.is_homograph) return 'O endereço usa letras de outro alfabeto que parecem iguais às nossas.';
        return level === 'PERIGOSO'
            ? 'Encontramos sinais fortes de golpe neste endereço.'
            : 'Encontramos sinais suspeitos neste endereço.';
    }

    // Traduz cada penalidade do motor para linguagem simples (o detalhe técnico fica embaixo)
    function friendlyReason(penalty, data) {
        const lex = data.technical_details?.lexical_math || {};
        const net = data.technical_details?.network || {};
        switch (penalty.category) {
            case 'Typosquatting (Nome Imitação)':
                return [penalty.description, 'Comparação de nomes com a distância de Levenshtein'];
            case 'Ataque Homográfico':
                return ['O endereço usa letras de outro alfabeto que parecem iguais às nossas.', penalty.description];
            case 'Alta Entropia (Aleatoriedade)':
                return ['O nome do site parece inventado às pressas.', `Entropia de Shannon H(X) = ${lex.domain_entropy}`];
            case 'IP Numérico Direto':
                return ['O link usa números no lugar do nome de um site.', 'Endereço IP direto, sem domínio registrado'];
            case 'Link Encurtado':
                return ['O link é encurtado e esconde o endereço verdadeiro.', null];
            case 'Domínio Recém-Criado':
                return [`O site foi criado há apenas ${net.domain_age_days} dia(s).`, 'Golpistas criam e descartam sites o tempo todo'];
            case 'Sem Conexão Segura (SSL)':
                return ['O site não tem o cadeado de conexão segura.', 'Certificado SSL/TLS ausente ou inválido'];
            case "Caractere '@' no Link":
                return ['O link usa um truque com “@” para esconder o destino verdadeiro.', null];
            case 'Excesso de Hífens':
                return ['O endereço tem muitos hífens, comum em sites falsos.', null];
            case 'Captura de Senha em Domínio Imitação':
                return ['A página pede sua senha, mas não é o site oficial.', null];
            case 'Palavras-Chave de Golpe':
                return ['A página usa frases comuns em golpes.', penalty.description];
            default:
                return [penalty.description, null];
        }
    }

    function renderResult(data) {
        lastAnalysis = data;
        const level = data.heuristics.risk_level;
        const verdict = VERDICTS[level] || VERDICTS.SUSPEITO;

        resultSection.dataset.level = level;
        verdictSign.innerHTML = signMarkup(verdict.sign);
        if (verdict.sign === 'go') verdictSign.appendChild(buildGoSign(data.domain));
        verdictTitle.textContent = verdict.title;
        verdictSubtitle.textContent = buildSubtitle(level, data);
        verdictDomain.textContent = data.url_analyzed;

        todoList.innerHTML = '';
        verdict.todo.forEach((text) => {
            const li = document.createElement('li');
            li.textContent = text;
            todoList.appendChild(li);
        });

        reasonsTitle.textContent = verdict.reasonsTitle;
        reasonsList.innerHTML = '';
        const penalties = data.heuristics.penalties || [];
        if (penalties.length === 0) {
            const li = document.createElement('li');
            li.textContent = 'O nome do site, o cadeado de segurança e a página não mostraram sinais de golpe.';
            reasonsList.appendChild(li);
        }
        penalties.forEach((penalty) => {
            const [main, detail] = friendlyReason(penalty, data);
            const li = document.createElement('li');
            li.textContent = main;
            if (detail) {
                const small = document.createElement('small');
                small.textContent = detail;
                li.appendChild(small);
            }
            reasonsList.appendChild(li);
        });

        if (level === 'SEGURO') {
            openSiteBtn.href = data.url_analyzed;
            openSiteBtn.classList.remove('hidden');
        } else {
            openSiteBtn.classList.add('hidden');
            openSiteBtn.removeAttribute('href');
        }

        renderMetrics(data);

        resultSection.classList.remove('hidden');
        resultSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        verdictTitle.focus({ preventScroll: true });
    }

    function renderMetrics(data) {
        const tech = data.technical_details || {};
        const lex = tech.lexical_math || {};
        const typo = lex.typosquat_info || {};
        const net = tech.network || {};
        const ssl = net.ssl || {};
        const crawl = tech.sandbox_crawl || {};

        $('metric-levenshtein').textContent = typo.is_trusted
            ? `Domínio oficial reconhecido (${typo.best_matched_domain})`
            : typo.is_typosquat
                ? `Imita ${typo.best_matched_domain} · distância ${typo.min_levenshtein_distance} · similaridade ${typo.similarity_percentage}%`
                : `Sem imitação de marca (maior similaridade: ${typo.similarity_percentage || 0}%)`;
        $('metric-entropy').textContent = `Domínio: ${lex.domain_entropy ?? '-'} · URL completa: ${lex.url_entropy ?? '-'}`;
        $('metric-homograph').textContent = lex.is_homograph ? lex.homograph_reason : 'Somente caracteres latinos';
        $('metric-whois').textContent = net.domain_age_days != null ? `${net.domain_age_days} dias de registro` : 'Não disponível';
        $('metric-ssl').textContent = ssl.has_ssl ? `Válido · emissor ${ssl.issuer} · até ${ssl.valid_until}` : (ssl.details || 'Ausente');

        if (crawl.is_accessible) {
            const parts = [`HTTP ${crawl.status_code}`, `título “${crawl.page_title || 'sem título'}”`];
            if (crawl.has_password_input) parts.push('campo de senha');
            if (crawl.has_credit_card_input) parts.push('campo de cartão');
            if ((crawl.found_suspicious_keywords || []).length) parts.push('frases: ' + crawl.found_suspicious_keywords.join(', '));
            $('metric-sandbox').textContent = parts.join(' · ');
        } else {
            $('metric-sandbox').textContent = crawl.details || 'Página não inspecionada';
        }
        $('metric-score').textContent = `${data.heuristics.risk_score} de 100 (${data.heuristics.risk_level})`;
    }

    // ---------- ações do resultado ----------
    familyHelpBtn.addEventListener('click', () => {
        if (!lastAnalysis) return;
        const verdict = VERDICTS[lastAnalysis.heuristics.risk_level] || VERDICTS.SUSPEITO;
        const message = `Oi! Recebi este link e o URL Shield respondeu: “${verdict.title}”\n\n${lastAnalysis.url_analyzed}\n\nVocê pode me ajudar a conferir antes de eu abrir?`;
        window.open('https://api.whatsapp.com/send?text=' + encodeURIComponent(message), '_blank', 'noopener');
    });

    speakBtn.addEventListener('click', () => {
        if (!('speechSynthesis' in window) || !lastAnalysis) return;
        if (window.speechSynthesis.speaking) {
            window.speechSynthesis.cancel();
            speakBtnText.textContent = 'Ouvir';
            return;
        }
        const utterance = new SpeechSynthesisUtterance(lastAnalysis.accessible_explanation.speech_text);
        utterance.lang = 'pt-BR';
        utterance.rate = 0.92;
        utterance.onend = () => { speakBtnText.textContent = 'Ouvir'; };
        speakBtnText.textContent = 'Parar';
        window.speechSynthesis.speak(utterance);
    });

    $('new-check-btn').addEventListener('click', () => {
        if (window.speechSynthesis) window.speechSynthesis.cancel();
        resultSection.classList.add('hidden');
        urlInput.value = '';
        window.scrollTo({ top: 0, behavior: 'smooth' });
        urlInput.focus({ preventScroll: true });
    });

    $('pdf-report-btn').addEventListener('click', () => {
        if (lastAnalysis) openLaudo(lastAnalysis);
    });

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    // Laudo técnico em uma janela própria (imprimir / salvar como PDF)
    function openLaudo(data) {
        const h = data.heuristics;
        const exp = data.accessible_explanation;
        const verdict = VERDICTS[h.risk_level] || VERDICTS.SUSPEITO;
        const color = h.risk_level === 'SEGURO' ? '#0A6B3B' : (h.risk_level === 'SUSPEITO' ? '#8A6400' : '#C8102E');
        const protocol = 'URLSHIELD-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000);
        const timestamp = new Date().toLocaleString('pt-BR');
        const rows = Array.from(document.querySelectorAll('.metrics .metric'))
            .map((m) => `<tr><td class="lbl">${escapeHtml(m.querySelector('dt').textContent)}</td><td>${escapeHtml(m.querySelector('dd').textContent)}</td></tr>`)
            .join('');
        const reasons = (h.penalties || [])
            .map((p) => `<tr><td>${escapeHtml(p.category)}</td><td>${escapeHtml(p.description)}</td><td class="num">+${p.points}</td></tr>`)
            .join('') || '<tr><td colspan="3">Nenhuma penalidade aplicada.</td></tr>';

        const html = `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><title>Laudo ${protocol}</title>
<style>
@page { size: A4; margin: 15mm; }
body { font-family: 'Segoe UI', Arial, sans-serif; color: #17181A; margin: 0; padding: 24px; line-height: 1.5; }
.bar { height: 8px; background: repeating-linear-gradient(90deg, #F5C400 0 40px, #1D1F22 40px 64px); margin-bottom: 18px; }
h1 { font-size: 20px; margin: 4px 0; letter-spacing: .5px; }
.brand { font-weight: 800; letter-spacing: 1px; font-size: 12px; }
.sub { color: #4B4E53; font-size: 12px; margin: 0; }
.meta { display: flex; justify-content: space-between; border: 1px solid #D6D6D0; padding: 8px 12px; font-size: 11px; margin: 14px 0 6px; }
h2 { font-size: 13px; text-transform: uppercase; border-bottom: 2px solid #17181A; padding-bottom: 3px; margin: 20px 0 8px; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
td, th { border: 1px solid #D6D6D0; padding: 7px 9px; text-align: left; vertical-align: top; }
th { background: #F3F3F0; }
.lbl { width: 34%; background: #F8F8F6; font-weight: 600; }
.num { text-align: right; white-space: nowrap; }
.mono { font-family: Consolas, monospace; font-size: 11px; word-break: break-all; }
.verdict { display: inline-block; color: #fff; background: ${color}; padding: 3px 10px; font-weight: 800; border-radius: 4px; }
.box { border: 1px solid #D6D6D0; padding: 10px 12px; font-size: 12px; margin-bottom: 8px; }
.foot { margin-top: 26px; border-top: 1px solid #D6D6D0; padding-top: 10px; font-size: 10px; color: #6E706B; text-align: center; }
.no-print { text-align: right; margin-bottom: 14px; }
.no-print button { background: #17181A; color: #fff; border: none; padding: 10px 18px; font-weight: 700; border-radius: 6px; cursor: pointer; font-size: 14px; }
@media print { .no-print { display: none; } }
</style></head><body>
<div class="no-print"><button onclick="window.print()">Salvar como PDF / Imprimir</button></div>
<div class="bar"></div>
<div class="brand">URL SHIELD</div>
<h1>LAUDO TÉCNICO DE ANÁLISE DE LINK</h1>
<p class="sub">Verificação matemática e heurística de fraudes · Trabalho de Conclusão de Curso</p>
<div class="meta"><span>Protocolo: <strong>${protocol}</strong></span><span>Emissão: <strong>${escapeHtml(timestamp)}</strong></span></div>
<h2>1. Endereço analisado</h2>
<table>
<tr><td class="lbl">URL completa</td><td class="mono">${escapeHtml(data.url_analyzed)}</td></tr>
<tr><td class="lbl">Domínio registrado</td><td class="mono">${escapeHtml(data.domain)}</td></tr>
<tr><td class="lbl">Veredito</td><td><span class="verdict">${escapeHtml(verdict.title.replace(/\.$/, ''))}</span> (${escapeHtml(h.risk_level)})</td></tr>
<tr><td class="lbl">Índice de risco</td><td><strong>${h.risk_score} de 100</strong></td></tr>
</table>
<h2>2. Orientação ao usuário</h2>
<div class="box"><strong>Diagnóstico:</strong> ${escapeHtml(exp.summary)}</div>
<div class="box"><strong>Orientação:</strong> ${escapeHtml(exp.recommendation)}</div>
<h2>3. Penalidades aplicadas</h2>
<table><thead><tr><th style="width:30%">Indicador</th><th>Descrição</th><th style="width:10%">Pontos</th></tr></thead><tbody>${reasons}</tbody></table>
<h2>4. Métricas da análise</h2>
<table>${rows}</table>
<div class="foot">Laudo gerado automaticamente pelo URL Shield. Os resultados indicam risco estimado e não substituem a confirmação pelos canais oficiais.</div>
</body></html>`;

        const win = window.open('', '_blank', 'width=900,height=1000');
        if (win) {
            win.document.open();
            win.document.write(html);
            win.document.close();
        }
    }

    // ---------- simulador do robô do Telegram ----------
    const chatForm = $('chat-form');
    const chatInput = $('chat-input');
    const chatMessages = $('chat-messages');

    function addBubble(kind) {
        const bubble = document.createElement('div');
        bubble.className = 'bubble ' + kind;
        chatMessages.appendChild(bubble);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return bubble;
    }

    chatForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const text = chatInput.value.trim();
        if (!text) return;
        addBubble('user').textContent = text;
        chatInput.value = '';
        const reply = addBubble('bot');
        reply.textContent = 'Conferindo o link…';

        try {
            const response = await fetch('/api/analyze', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: text, deep_inspection: true })
            });
            if (response.status === 400) {
                reply.textContent = 'Isso não parece um link. Envie o endereço completo, por exemplo: site.com.br';
                return;
            }
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const data = await response.json();
            const level = data.heuristics.risk_level;
            const verdict = VERDICTS[level] || VERDICTS.SUSPEITO;

            reply.textContent = '';
            reply.classList.add(level === 'SEGURO' ? 'v-safe' : (level === 'SUSPEITO' ? 'v-warn' : 'v-danger'));
            const title = document.createElement('span');
            title.className = 'bubble-verdict';
            title.textContent = verdict.title;
            reply.appendChild(title);
            reply.appendChild(document.createTextNode(buildSubtitle(level, data)));

            const penalties = (data.heuristics.penalties || []).slice(0, 3);
            if (penalties.length) {
                const list = document.createElement('ul');
                penalties.forEach((p) => {
                    const li = document.createElement('li');
                    li.textContent = friendlyReason(p, data)[0];
                    list.appendChild(li);
                });
                reply.appendChild(list);
            }
        } catch (err) {
            reply.textContent = 'Não consegui conferir agora. Tente de novo em alguns segundos.';
        }
        chatMessages.scrollTop = chatMessages.scrollHeight;
    });
});
