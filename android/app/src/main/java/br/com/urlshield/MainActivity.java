package br.com.urlshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQUEST_BROWSER_ROLE = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout setupPanel;
    private LinearLayout loadingPanel;
    private LinearLayout resultPanel;
    private TextView protectionStatus;
    private EditText testUrlInput;
    private TextView statusIcon;
    private TextView resultTitle;
    private TextView resultDomain;
    private TextView riskScore;
    private TextView resultSummary;
    private TextView resultRecommendation;
    private TextView reasonsLabel;
    private TextView resultReasons;
    private Button activateButton;
    private Button deactivateButton;
    private Button primaryButton;
    private Button familyButton;
    private Button listenButton;
    private Button continueButton;

    private AnalysisResult currentResult;
    private String currentUrl;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();
        textToSpeech = new TextToSpeech(this, this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (setupPanel != null && setupPanel.getVisibility() == View.VISIBLE) {
            updateProtectionStatus();
        }
    }

    private void bindViews() {
        setupPanel = findViewById(R.id.setupPanel);
        loadingPanel = findViewById(R.id.loadingPanel);
        resultPanel = findViewById(R.id.resultPanel);
        protectionStatus = findViewById(R.id.protectionStatus);
        testUrlInput = findViewById(R.id.testUrlInput);
        statusIcon = findViewById(R.id.statusIcon);
        resultTitle = findViewById(R.id.resultTitle);
        resultDomain = findViewById(R.id.resultDomain);
        riskScore = findViewById(R.id.riskScore);
        resultSummary = findViewById(R.id.resultSummary);
        resultRecommendation = findViewById(R.id.resultRecommendation);
        reasonsLabel = findViewById(R.id.reasonsLabel);
        resultReasons = findViewById(R.id.resultReasons);
        activateButton = findViewById(R.id.activateButton);
        deactivateButton = findViewById(R.id.deactivateButton);
        primaryButton = findViewById(R.id.primaryButton);
        familyButton = findViewById(R.id.familyButton);
        listenButton = findViewById(R.id.listenButton);
        continueButton = findViewById(R.id.continueButton);
    }

    private void bindActions() {
        activateButton.setOnClickListener(view -> requestBrowserRole());
        deactivateButton.setOnClickListener(view -> confirmDeactivation());
        findViewById(R.id.testButton).setOnClickListener(view -> {
            String link = normalizeUrl(testUrlInput.getText().toString());
            if (link == null) {
                testUrlInput.setError("Digite um endereço válido.");
                return;
            }
            analyze(link);
        });
        primaryButton.setOnClickListener(view -> {
            if (currentResult != null && currentResult.isAllowed()) {
                openApprovedUrl();
            } else {
                finish();
            }
        });
        continueButton.setOnClickListener(view -> confirmContinue());
        familyButton.setOnClickListener(view -> shareWithFamily());
        listenButton.setOnClickListener(view -> speakResult());
    }

    private void handleIntent(Intent intent) {
        String link = extractLink(intent);
        if (link != null) {
            analyze(link);
        } else {
            showSetup();
        }
    }

    private String extractLink(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            return normalizeUrl(intent.getDataString());
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                Matcher matcher = Patterns.WEB_URL.matcher(sharedText);
                if (matcher.find()) {
                    return normalizeUrl(matcher.group());
                }
            }
        }
        return null;
    }

    private String normalizeUrl(String input) {
        if (input == null) {
            return null;
        }
        String candidate = input.trim();
        if (candidate.isEmpty()) {
            return null;
        }
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            if (uri.getHost() == null) {
                return null;
            }
            return candidate;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void analyze(String link) {
        currentUrl = link;
        showLoading();
        executor.execute(() -> {
            try {
                AnalysisResult result = UrlShieldApi.analyze(link);
                mainHandler.post(() -> showResult(result));
            } catch (Exception error) {
                mainHandler.post(this::showFailClosedError);
            }
        });
    }

    private void showSetup() {
        currentResult = null;
        currentUrl = null;
        setupPanel.setVisibility(View.VISIBLE);
        loadingPanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.GONE);
        updateProtectionStatus();
    }

    private void showLoading() {
        currentResult = null;
        setupPanel.setVisibility(View.GONE);
        loadingPanel.setVisibility(View.VISIBLE);
        resultPanel.setVisibility(View.GONE);
    }

    private void showResult(AnalysisResult result) {
        currentResult = result;
        currentUrl = result.url;
        setupPanel.setVisibility(View.GONE);
        loadingPanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.VISIBLE);

        resultTitle.setText(result.title);
        resultDomain.setText(result.domain);
        riskScore.setText(getString(R.string.risk_format, result.riskScore));
        resultSummary.setText(result.summary);
        resultRecommendation.setText(result.recommendation);
        familyButton.setVisibility(View.VISIBLE);
        listenButton.setVisibility(View.VISIBLE);

        if (result.reasons.isEmpty()) {
            reasonsLabel.setVisibility(View.GONE);
            resultReasons.setVisibility(View.GONE);
        } else {
            reasonsLabel.setVisibility(View.VISIBLE);
            resultReasons.setVisibility(View.VISIBLE);
            StringBuilder bullets = new StringBuilder();
            for (String reason : result.reasons) {
                if (bullets.length() > 0) {
                    bullets.append('\n');
                }
                bullets.append("• ").append(reason);
            }
            resultReasons.setText(bullets.toString());
        }

        if (result.isAllowed()) {
            statusIcon.setText("✅");
            riskScore.setBackgroundResource(R.drawable.status_safe);
            primaryButton.setText(R.string.open_site);
            continueButton.setVisibility(View.GONE);
        } else if (result.isBlocked()) {
            statusIcon.setText("⛔");
            riskScore.setBackgroundResource(R.drawable.status_danger);
            primaryButton.setText(R.string.close_and_return);
            continueButton.setText(R.string.continue_anyway);
            continueButton.setVisibility(View.VISIBLE);
        } else {
            statusIcon.setText("⚠️");
            riskScore.setBackgroundResource(R.drawable.status_warning);
            primaryButton.setText(R.string.close_and_return);
            continueButton.setText(R.string.continue_anyway);
            continueButton.setVisibility(View.VISIBLE);
        }

        resultTitle.announceForAccessibility(result.title + ". " + result.recommendation);
    }

    private void showFailClosedError() {
        setupPanel.setVisibility(View.GONE);
        loadingPanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.VISIBLE);
        currentResult = null;

        statusIcon.setText("🛡️");
        resultTitle.setText(R.string.verification_failed_title);
        resultDomain.setText("");
        riskScore.setText(R.string.site_not_opened);
        riskScore.setBackgroundResource(R.drawable.status_warning);
        resultSummary.setText(R.string.verification_failed_summary);
        resultRecommendation.setText(R.string.verification_failed_recommendation);
        reasonsLabel.setVisibility(View.GONE);
        resultReasons.setVisibility(View.GONE);
        primaryButton.setText(R.string.close_and_return);
        continueButton.setVisibility(View.GONE);
        familyButton.setVisibility(View.VISIBLE);
        listenButton.setVisibility(View.GONE);
    }

    private void updateProtectionStatus() {
        boolean active = isBrowserRoleHeld();
        if (active) {
            protectionStatus.setText(R.string.protection_active);
            protectionStatus.setTextColor(getColor(R.color.green));
            activateButton.setVisibility(View.GONE);
            deactivateButton.setVisibility(View.VISIBLE);
        } else {
            protectionStatus.setText(R.string.setup_description);
            protectionStatus.setTextColor(getColor(R.color.muted));
            activateButton.setVisibility(View.VISIBLE);
            deactivateButton.setVisibility(View.GONE);
        }
        familyButton.setVisibility(View.VISIBLE);
        listenButton.setVisibility(View.VISIBLE);
    }

    private boolean isBrowserRoleHeld() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        RoleManager manager = getSystemService(RoleManager.class);
        return manager != null
                && manager.isRoleAvailable(RoleManager.ROLE_BROWSER)
                && manager.isRoleHeld(RoleManager.ROLE_BROWSER);
    }

    private void requestBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager manager = getSystemService(RoleManager.class);
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                startActivityForResult(
                        manager.createRequestRoleIntent(RoleManager.ROLE_BROWSER),
                        REQUEST_BROWSER_ROLE
                );
                return;
            }
        }

        Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        try {
            startActivity(settingsIntent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void confirmDeactivation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.deactivate_title)
                .setMessage(R.string.deactivate_message)
                .setNegativeButton(R.string.keep_protection, null)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> openDefaultAppsSettings())
                .show();
    }

    private void openDefaultAppsSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        try {
            startActivity(settingsIntent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void confirmContinue() {
        if (currentResult == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Você quer assumir o risco?")
                .setMessage("O URLShield recomenda não abrir este endereço. Nunca informe senhas, códigos ou dados bancários.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Abrir mesmo assim", (dialog, which) -> openApprovedUrl())
                .show();
    }

    private void openApprovedUrl() {
        if (currentResult == null) {
            return;
        }
        Intent browser = new Intent(this, SafeBrowserActivity.class);
        browser.putExtra(SafeBrowserActivity.EXTRA_APPROVED_URL, currentResult.url);
        startActivity(browser);
    }

    private void shareWithFamily() {
        if (currentUrl == null) {
            return;
        }
        String message;
        if (currentResult == null) {
            message = "O URLShield não conseguiu verificar este link e não abriu o site. "
                    + "Você pode me ajudar?\n\n" + currentUrl;
        } else {
            message = "Preciso de ajuda para verificar este link.\n\n"
                    + currentResult.title + "\n"
                    + currentResult.summary + "\n\n"
                    + currentResult.url;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(share, "Pedir ajuda usando"));
    }

    private void speakResult() {
        if (currentResult == null || currentResult.speechText.isEmpty()) {
            return;
        }
        if (!ttsReady) {
            Toast.makeText(this, "A leitura em voz alta ainda está iniciando.", Toast.LENGTH_SHORT).show();
            return;
        }
        textToSpeech.speak(currentResult.speechText, TextToSpeech.QUEUE_FLUSH, null, "urlshield-result");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int languageResult = textToSpeech.setLanguage(new Locale("pt", "BR"));
            textToSpeech.setSpeechRate(0.9f);
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                    && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
