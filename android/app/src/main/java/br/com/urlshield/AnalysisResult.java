package br.com.urlshield;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AnalysisResult {
    final String verdict;
    final String url;
    final String domain;
    final String title;
    final String summary;
    final String recommendation;
    final String speechText;
    final double riskScore;
    final List<String> reasons;
    final boolean offline;
    /** Domínio oficial que o link está imitando, ou "" se nenhum. */
    final String impersonates;
    /** O próprio domínio é oficial e conhecido (ex: itau.com.br). */
    final boolean trusted;

    private AnalysisResult(
            String verdict,
            String url,
            String domain,
            String title,
            String summary,
            String recommendation,
            String speechText,
            double riskScore,
            List<String> reasons,
            boolean offline,
            String impersonates,
            boolean trusted
    ) {
        this.verdict = verdict;
        this.url = url;
        this.domain = domain;
        this.title = title;
        this.summary = summary;
        this.recommendation = recommendation;
        this.speechText = speechText;
        this.riskScore = riskScore;
        this.reasons = reasons;
        this.offline = offline;
        this.impersonates = impersonates;
        this.trusted = trusted;
    }

    static AnalysisResult fromJson(JSONObject json) throws JSONException {
        JSONArray jsonReasons = json.optJSONArray("reasons");
        List<String> reasons = new ArrayList<>();
        if (jsonReasons != null) {
            for (int index = 0; index < jsonReasons.length(); index++) {
                String reason = jsonReasons.optString(index, "").trim();
                if (!reason.isEmpty()) {
                    reasons.add(reason);
                }
            }
        }

        return new AnalysisResult(
                json.getString("verdict"),
                json.getString("url"),
                json.optString("domain", ""),
                json.getString("title"),
                json.getString("summary"),
                json.getString("recommendation"),
                json.optString("speech_text", ""),
                json.optDouble("risk_score", 0.0),
                reasons,
                false,
                json.optString("impersonates", ""),
                json.optBoolean("trusted", false)
        );
    }

    /** Monta o resultado a partir do motor local, com os mesmos textos de backend/app/explainer.py e main.py. */
    static AnalysisResult fromOffline(OfflineAnalyzer.Report report) {
        String score = String.format(Locale.US, "%.1f", report.riskScore);
        String verdict;
        String title;
        String summary;
        String recommendation;
        String speechText;
        if (OfflineAnalyzer.LEVEL_DANGEROUS.equals(report.riskLevel)) {
            verdict = "BLOCK";
            title = "Não abra este link";
            summary = "ALERTA DE PERIGO! Este link é muito provavelmente uma tentativa de golpe (" + score + "% de risco).";
            recommendation = "Não clique no link nem forneça nenhuma informação. Apague a mensagem recebida.";
            speechText = "Alerta vermelho. O link " + report.domain + " é perigoso e parece ser um golpe. Não abra este site.";
        } else if (OfflineAnalyzer.LEVEL_SUSPICIOUS.equals(report.riskLevel)) {
            verdict = "WARN";
            title = "Tenha cuidado";
            summary = "Cuidado! Este link apresenta sinais suspeitos (" + score + "% de risco).";
            recommendation = "Evite informar dados pessoais ou fazer pagamentos neste site sem confirmar antes com um familiar.";
            speechText = "Cuidado. O link " + report.domain + " é suspeito. Não digite senhas ou dados bancários neste site.";
        } else {
            verdict = "ALLOW";
            title = "Nenhum sinal forte de golpe";
            summary = "Não encontramos sinais fortes de golpe neste endereço. O nível de risco calculado foi de " + score + "%.";
            recommendation = "Você pode continuar com atenção. Nenhuma análise consegue garantir segurança total.";
            speechText = "O link " + report.domain + " foi verificado. Não encontramos sinais fortes de golpe, mas continue atento a pedidos de senha, dinheiro ou dados pessoais.";
        }

        List<String> reasons = new ArrayList<>(report.reasons.subList(0, Math.min(3, report.reasons.size())));
        return new AnalysisResult(verdict, report.url, report.domain, title, summary,
                recommendation, speechText, report.riskScore, reasons, true,
                report.impersonates, report.trusted);
    }

    boolean isAllowed() {
        return "ALLOW".equals(verdict);
    }

    boolean isBlocked() {
        return "BLOCK".equals(verdict);
    }
}
