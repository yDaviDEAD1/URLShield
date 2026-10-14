package br.com.urlshield;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Motor de análise léxica executado no próprio aparelho, sem servidor.
 * Espelha backend/app/math_engine.py e heuristics.py (Levenshtein, entropia de Shannon,
 * homógrafos, leetspeak, IP direto, encurtadores) com os mesmos pesos e limiares.
 * Verificações de rede (WHOIS, certificado SSL) só existem na análise online.
 */
final class OfflineAnalyzer {
    static final String LEVEL_SAFE = "SEGURO";
    static final String LEVEL_SUSPICIOUS = "SUSPEITO";
    static final String LEVEL_DANGEROUS = "PERIGOSO";

    static final List<String> TRUSTED_DOMAINS = Arrays.asList(
            // Bancos e Finanças (Brasil)
            "caixa.gov.br", "bb.com.br", "bancodobrasil.com.br", "itau.com.br", "bradesco.com.br",
            "santander.com.br", "nubank.com.br", "bancopan.com.br", "inter.co",
            "pagbank.com.br", "mercadopago.com.br", "picpay.com", "c6bank.com.br",
            // E-commerce e Serviços
            "mercadolivre.com.br", "amazon.com.br", "magazineluiza.com.br",
            "americanas.com.br", "casasbahia.com.br", "shopee.com.br", "aliexpress.com",
            // Redes Sociais e Mensageria
            "whatsapp.com", "instagram.com", "facebook.com", "telegram.org",
            "tiktok.com", "youtube.com", "twitter.com", "x.com",
            // Big Techs & Portais
            "google.com", "google.com.br", "outlook.com", "live.com", "microsoft.com",
            "apple.com", "netflix.com", "globo.com", "gov.br",
            // Pagamentos e Serviços Globais
            "paypal.com", "icloud.com", "linkedin.com", "correios.com.br"
    );

    private static final Set<String> URL_SHORTENERS = new HashSet<>(Arrays.asList(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly",
            "rebrand.ly", "shorturl.at", "encurtador.com.br", "tiny.cc", "rb.gy", "s.id"
    ));

    // Terminações baratas muito usadas em golpes (mesma lista do backend)
    private static final Set<String> SUSPICIOUS_TLDS = new HashSet<>(Arrays.asList(
            "top", "xyz", "shop", "online", "site", "click", "live", "icu", "buzz", "cfd",
            "sbs", "rest", "monster", "quest", "cyou", "bond", "lol", "store", "vip", "work"
    ));

    // Sufixos públicos de dois níveis mais comuns (subconjunto da Public Suffix List)
    private static final Set<String> MULTI_LEVEL_SUFFIXES = new HashSet<>(Arrays.asList(
            "com.br", "net.br", "org.br", "gov.br", "edu.br", "art.br", "blog.br", "app.br",
            "eco.br", "emp.br", "ind.br", "inf.br", "jus.br", "leg.br", "mil.br", "mp.br",
            "co.uk", "org.uk", "gov.uk", "ac.uk", "com.au", "com.ar", "com.mx", "com.pt",
            "co.jp", "co.in", "com.cn", "com.co"
    ));

    private static final Map<Character, Character> LEET_MAP = new HashMap<>();

    static {
        LEET_MAP.put('0', 'o');
        LEET_MAP.put('1', 'l');
        LEET_MAP.put('3', 'e');
        LEET_MAP.put('4', 'a');
        LEET_MAP.put('5', 's');
        LEET_MAP.put('7', 't');
        LEET_MAP.put('8', 'b');
        LEET_MAP.put('@', 'a');
        LEET_MAP.put('$', 's');
    }

    private static final Pattern RAW_IP = Pattern.compile(
            "^(?:https?://)?(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(?:/.*)?$");

    static final class Report {
        final String url;
        final String domain;
        final double riskScore;
        final String riskLevel;
        final List<String> reasons;
        final String impersonates;
        final boolean trusted;

        Report(String url, String domain, double riskScore, String riskLevel, List<String> reasons,
               String impersonates, boolean trusted) {
            this.url = url;
            this.domain = domain;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.reasons = reasons;
            this.impersonates = impersonates;
            this.trusted = trusted;
        }
    }

    private OfflineAnalyzer() {
    }

    static Report analyze(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String[] hostParts = splitHost(host);
        String domainName = hostParts[0];
        String registered = hostParts[1];

        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        // 1. Typosquatting / imitação de marca
        Impersonation impersonation = findImpersonation(domainName, registered);
        if (impersonation != null) {
            score += 40.0;
            if ("brand_in_domain".equals(impersonation.type)) {
                reasons.add("O endereço usa o nome da marca '" + impersonation.matched
                        + "', mas não pertence à empresa oficial.");
            } else {
                reasons.add("O endereço tenta se passar por '" + impersonation.matched
                        + "' alterando apenas " + impersonation.distance + " caractere(s).");
            }
        }

        // 2. Ataque homográfico
        String homographReason = homographReason(host);
        if (homographReason != null) {
            score += 35.0;
            reasons.add("O endereço usa letras de outro alfabeto que parecem iguais às nossas.");
        }

        // 3. Entropia de Shannon alta no domínio
        double entropy = shannonEntropy(registered);
        if (entropy > 3.5) {
            score += 15.0;
            reasons.add("O nome do site parece inventado às pressas.");
        }

        // 4. IP numérico direto
        if (RAW_IP.matcher(url).matches()) {
            score += 25.0;
            reasons.add("O link usa números no lugar do nome de um site.");
        }

        // 4b. Encurtador de link
        if (URL_SHORTENERS.contains(registered)) {
            score += 30.0;
            reasons.add("O link é encurtado e esconde o endereço verdadeiro.");
        }

        // 4c. Terminação de domínio barata
        String tld = registered.substring(registered.lastIndexOf('.') + 1);
        if (SUSPICIOUS_TLDS.contains(tld)) {
            score += 15.0;
            reasons.add("O endereço termina de um jeito muito usado em sites de golpe.");
        }

        // 6. Sem conexão segura (offline só é possível verificar o esquema https)
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            score += 15.0;
            reasons.add("O link não usa o cadeado de conexão segura.");
        }

        // 7. Símbolos especiais
        if (url.indexOf('@') >= 0) {
            score += 20.0;
            reasons.add("O link usa um truque com \"@\" para esconder o destino verdadeiro.");
        }
        if (countChar(url, '-') > 3) {
            score += 10.0;
            reasons.add("O endereço tem muitos hífens, comum em sites falsos.");
        }

        double finalScore = Math.min(Math.round(score * 10.0) / 10.0, 100.0);
        String level;
        if (finalScore <= 25.0) {
            level = LEVEL_SAFE;
        } else if (finalScore <= 65.0) {
            level = LEVEL_SUSPICIOUS;
        } else {
            level = LEVEL_DANGEROUS;
        }
        return new Report(url, registered, finalScore, level, reasons,
                impersonation != null ? impersonation.matched : "", isTrusted(registered));
    }

    /** Retorna {nome do domínio, domínio registrado}, ex: "www.itau.com.br" -> {"itau", "itau.com.br"}. */
    static String[] splitHost(String host) {
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return new String[]{host, host};
        }
        String lastTwo = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if (MULTI_LEVEL_SUFFIXES.contains(lastTwo)) {
            if (labels.length < 3) {
                return new String[]{"", lastTwo};
            }
            String name = labels[labels.length - 3];
            return new String[]{name, name + "." + lastTwo};
        }
        String name = labels[labels.length - 2];
        return new String[]{name, lastTwo};
    }

    static boolean isTrusted(String registered) {
        return TRUSTED_DOMAINS.contains(registered) || registered.endsWith(".gov.br");
    }

    private static final class Impersonation {
        final String matched;
        final int distance;
        final String type;

        Impersonation(String matched, int distance, String type) {
            this.matched = matched;
            this.distance = distance;
            this.type = type;
        }
    }

    private static Impersonation findImpersonation(String domainName, String registered) {
        if (isTrusted(registered) || domainName.isEmpty()) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        parts.add(domainName);
        for (String part : domainName.split("-")) {
            if (part.length() >= 3) {
                parts.add(part);
            }
        }

        // Marca inteira em domínio de terceiros ou disfarçada com leetspeak
        for (String part : parts) {
            String normalized = normalizeLeet(part);
            for (String trusted : TRUSTED_DOMAINS) {
                String trustedName = splitHost(trusted)[0];
                if (trustedName.length() >= 4 && normalized.equals(trustedName)) {
                    int distance = levenshtein(part, trustedName);
                    return new Impersonation(trusted, distance, distance > 0 ? "leetspeak" : "brand_in_domain");
                }
            }
        }

        // Menor distância de Levenshtein / maior similaridade
        String bestMatch = "";
        int minDistance = Integer.MAX_VALUE;
        double highestSimilarity = 0.0;
        for (String part : parts) {
            for (String trusted : TRUSTED_DOMAINS) {
                String trustedName = splitHost(trusted)[0];
                int distance = levenshtein(part, trustedName);
                double ratio = similarityRatio(part, trustedName);
                if (distance > 0) {
                    if (ratio > highestSimilarity) {
                        highestSimilarity = ratio;
                        bestMatch = trusted;
                        minDistance = distance;
                    } else if (ratio == highestSimilarity && distance < minDistance) {
                        bestMatch = trusted;
                        minDistance = distance;
                    }
                }
            }
        }

        boolean isTyposquat = (minDistance > 0 && minDistance <= 2 && highestSimilarity >= 0.60)
                || (minDistance == 3 && highestSimilarity >= 0.70);
        return isTyposquat ? new Impersonation(bestMatch, minDistance, "levenshtein") : null;
    }

    private static String normalizeLeet(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            Character replacement = LEET_MAP.get(c);
            normalized.append(replacement != null ? replacement : c);
        }
        return normalized.toString();
    }

    static String homographReason(String host) {
        if (host.startsWith("xn--") || host.contains(".xn--")) {
            return "Domínio em código Punycode (IDN), que permite esconder caracteres visuais falsos.";
        }
        Set<Character> nonAscii = new HashSet<>();
        for (char c : host.toCharArray()) {
            if (c > 127) {
                nonAscii.add(c);
            }
        }
        if (nonAscii.isEmpty()) {
            return null;
        }
        StringBuilder chars = new StringBuilder();
        for (Character c : nonAscii) {
            if (chars.length() > 0) {
                chars.append(", ");
            }
            chars.append(c);
        }
        return "Contém caracteres não-latinos (" + chars + ") disfarçados de letras comuns.";
    }

    /** H(X) = -Σ P(x_i) · log2 P(x_i) */
    static double shannonEntropy(String text) {
        if (text.isEmpty()) {
            return 0.0;
        }
        Map<Character, Integer> counts = new HashMap<>();
        for (char c : text.toCharArray()) {
            Integer current = counts.get(c);
            counts.put(c, current == null ? 1 : current + 1);
        }
        double entropy = 0.0;
        for (int count : counts.values()) {
            double probability = (double) count / text.length();
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return Math.round(entropy * 10000.0) / 10000.0;
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Equivalente a Levenshtein.ratio do Python: 2·LCS / (|a| + |b|). */
    static double similarityRatio(String a, String b) {
        int total = a.length() + b.length();
        if (total == 0) {
            return 1.0;
        }
        int[][] lcs = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                lcs[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? lcs[i - 1][j - 1] + 1
                        : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        return 2.0 * lcs[a.length()][b.length()] / total;
    }

    private static int countChar(String text, char target) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == target) {
                count++;
            }
        }
        return count;
    }
}
