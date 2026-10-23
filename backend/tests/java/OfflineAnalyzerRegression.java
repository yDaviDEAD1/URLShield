package br.com.urlshield;

/** Runs the real, dependency-free Android lexical engine on a standard JVM. */
public final class OfflineAnalyzerRegression {
    public static void main(String[] args) {
        check("https://itau.com.br/?q=@----", 0, "");
        check("https://itau.com.br/#@----", 0, "");
        check("https://itau.com.br/a@----?q=@----#@----", 0, "");
        check("https://itau.com.br/a%40b?x=----", 0, "");
        check("https://person@itau.com.br/", 20, "");
        check("https://a----b@itau.com.br/", 20, "");
        check("https://a-b-c-d-e.itau.com.br/", 10, "");
        check("https://a-b-c-d.itau.com.br/", 0, "");
        check("https://x1.com", 0, "");
        check("https://bb1.com", 0, "");
        check("https://paypa1.com", 40, "paypal.com");
        check("https://g00gle.com", 40, "google.com");
        check("https://caixa-gov-fgts.top", 70, "caixa.gov.br");
        System.out.println("13 Android regression checks passed");
    }

    private static void check(String url, double score, String impersonates) {
        OfflineAnalyzer.Report report = OfflineAnalyzer.analyze(url);
        if (report.riskScore != score || !report.impersonates.equals(impersonates)) {
            throw new AssertionError(url + ": expected " + score + " / " + impersonates
                    + ", got " + report.riskScore + " / " + report.impersonates);
        }
    }
}
