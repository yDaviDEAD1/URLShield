package br.com.urlshield;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.util.Locale;

public final class SafeBrowserActivity extends Activity {
    static final String EXTRA_APPROVED_URL = "approved_url";

    private WebView webView;
    private String approvedUrl;
    private String approvedHost;
    private TextView domainView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_browser);

        approvedUrl = getIntent().getStringExtra(EXTRA_APPROVED_URL);
        if (approvedUrl == null || approvedUrl.trim().isEmpty()) {
            finish();
            return;
        }

        domainView = findViewById(R.id.browserDomain);
        Uri approvedUri = Uri.parse(approvedUrl);
        approvedHost = normalizeHost(approvedUri.getHost());
        domainView.setText(approvedUri.getHost());

        findViewById(R.id.browserCloseButton).setOnClickListener(view -> finish());
        findViewById(R.id.browserBackButton).setOnClickListener(view -> handleBackNavigation());

        webView = findViewById(R.id.safeWebView);
        configureWebView();
        configureBackNavigation();
        webView.loadUrl(approvedUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        // O navegador protegido precisa renderizar sites modernos. Navegações de
        // quadro principal continuam interceptadas e passam por nova análise.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return false;
                }

                Uri nextUri = request.getUrl();
                if (isNavigationInsideApprovedSite(nextUri)) {
                    return false;
                }

                Intent scan = new Intent(SafeBrowserActivity.this, MainActivity.class);
                scan.setAction(Intent.ACTION_VIEW);
                scan.setData(nextUri);
                startActivity(scan);
                finish();
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Uri currentUri = Uri.parse(url);
                String currentHost = currentUri.getHost();
                if (currentHost != null && !currentHost.isEmpty()) {
                    domainView.setText(currentHost);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });
    }

    private boolean isNavigationInsideApprovedSite(Uri uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        String nextHost = normalizeHost(uri.getHost());
        return approvedHost != null && approvedHost.equals(nextHost);
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private void configureBackNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackNavigation
            );
        }
    }

    private void handleBackNavigation() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
