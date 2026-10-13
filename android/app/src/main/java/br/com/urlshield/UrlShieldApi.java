package br.com.urlshield;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UrlShieldApi {
    private UrlShieldApi() {
    }

    /** Falso quando o APK foi gerado sem servidor configurado (modo somente offline). */
    static boolean isConfigured() {
        return !BuildConfig.URL_SHIELD_API_URL.isEmpty();
    }

    static AnalysisResult analyze(String link) throws Exception {
        URL endpoint = new URL(BuildConfig.URL_SHIELD_API_URL + "/api/mobile/analyze");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(25_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONObject payload = new JSONObject();
        payload.put("url", link);
        payload.put("deep_inspection", false);

        try {
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = readFully(stream);

            if (status < 200 || status >= 300) {
                throw new IOException("Servidor respondeu com código " + status);
            }
            return AnalysisResult.fromJson(new JSONObject(response));
        } finally {
            connection.disconnect();
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }
}
