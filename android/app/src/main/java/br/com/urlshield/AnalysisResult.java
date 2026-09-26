package br.com.urlshield;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

    private AnalysisResult(
            String verdict,
            String url,
            String domain,
            String title,
            String summary,
            String recommendation,
            String speechText,
            double riskScore,
            List<String> reasons
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
                reasons
        );
    }

    boolean isAllowed() {
        return "ALLOW".equals(verdict);
    }

    boolean isBlocked() {
        return "BLOCK".equals(verdict);
    }
}
