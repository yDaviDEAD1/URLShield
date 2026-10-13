package br.com.urlshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pop-up que aparece por cima do WhatsApp/SMS quando a pessoa toca em um link.
 * Mostra o veredito e, se ela decidir abrir, encaminha o link ao navegador de verdade.
 */
public final class LinkCheckActivity extends Activity implements TextToSpeech.OnInitListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View loadingBlock;
    private LinearLayout verdictBlock;
    private FrameLayout verdictTile;
    private ImageView verdictIcon;
    private TextView verdictTitle;
    private TextView verdictSubtitle;
    private TextView verdictDomain;
    private View bodyBlock;
    private TextView reasonsLabel;
    private LinearLayout reasonsList;
    private View actionsBlock;
    private Button primaryButton;
    private Button listenButton;
    private Button continueButton;
    private TextView offlineNote;

    private String currentUrl;
    private AnalysisResult currentResult;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_check);
        bindViews();
        Links.applySystemBarInsets(this, findViewById(R.id.scrim), findViewById(R.id.sheet));
        textToSpeech = new TextToSpeech(this, this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void bindViews() {
        loadingBlock = findViewById(R.id.loadingBlock);
        verdictBlock = findViewById(R.id.verdictBlock);
        verdictTile = findViewById(R.id.verdictTile);
        verdictIcon = findViewById(R.id.verdictIcon);
        verdictTitle = findViewById(R.id.verdictTitle);
        verdictSubtitle = findViewById(R.id.verdictSubtitle);
        verdictDomain = findViewById(R.id.verdictDomain);
        bodyBlock = findViewById(R.id.bodyBlock);
        reasonsLabel = findViewById(R.id.reasonsLabel);
        reasonsList = findViewById(R.id.reasonsList);
        actionsBlock = findViewById(R.id.actionsBlock);
        primaryButton = findViewById(R.id.primaryButton);
        listenButton = findViewById(R.id.listenButton);
        continueButton = findViewById(R.id.continueButton);
        offlineNote = findViewById(R.id.offlineNote);

        // Tocar fora do cartão fecha sem abrir nada: sempre é seguro
        findViewById(R.id.scrim).setOnClickListener(view -> finish());
        primaryButton.setOnClickListener(view -> {
            if (currentResult != null && currentResult.isAllowed()) {
                openInBrowser();
            } else {
                finish();
            }
        });
        continueButton.setOnClickListener(view -> confirmContinue());
        findViewById(R.id.familyButton).setOnClickListener(view -> shareWithFamily());
        listenButton.setOnClickListener(view -> speakResult());
    }

    private void handleIntent(Intent intent) {
        String link = Links.extractLink(intent);
        if (link == null) {
            finish();
            return;
        }
        analyze(link);
    }

    private void analyze(String link) {
        currentUrl = link;
        currentResult = null;
        showLoading();
        executor.execute(() -> {
            // 1. Motor local: responde na hora e funciona sem internet nem servidor
            AnalysisResult offlineResult = null;
            try {
                offlineResult = AnalysisResult.fromOffline(OfflineAnalyzer.analyze(link));
            } catch (Exception ignored) {
                // segue para a análise online
            }

            // 2. Perigo claro já detectado localmente: bloqueia sem esperar o servidor
            if (offlineResult != null && offlineResult.isBlocked()) {
                AnalysisResult blocked = offlineResult;
                mainHandler.post(() -> showResult(blocked));
                return;
            }

            // 3. Servidor disponível: análise completa (WHOIS, certificado SSL, DNS)
            if (UrlShieldApi.isConfigured()) {
                try {
                    AnalysisResult onlineResult = UrlShieldApi.analyze(link);
                    mainHandler.post(() -> showResult(onlineResult));
                    return;
                } catch (Exception ignored) {
                    // servidor fora do ar ou sem internet: usa o resultado local
                }
            }

            AnalysisResult fallback = offlineResult;
            if (fallback != null) {
                mainHandler.post(() -> showResult(fallback));
            } else {
                mainHandler.post(this::showFailed);
            }
        });
    }

    private void showLoading() {
        loadingBlock.setVisibility(View.VISIBLE);
        verdictBlock.setVisibility(View.GONE);
        bodyBlock.setVisibility(View.GONE);
        actionsBlock.setVisibility(View.GONE);
    }

    private void showResult(AnalysisResult result) {
        if (isFinishing()) {
            return;
        }
        currentResult = result;
        currentUrl = result.url;
        loadingBlock.setVisibility(View.GONE);
        verdictBlock.setVisibility(View.VISIBLE);
        bodyBlock.setVisibility(View.VISIBLE);
        actionsBlock.setVisibility(View.VISIBLE);
        listenButton.setVisibility(View.VISIBLE);

        int background;
        int ink;
        int tile;
        int chip;
        int dot;
        int icon;
        int title;
        if (result.isBlocked()) {
            background = getColor(R.color.danger);
            ink = getColor(R.color.danger_ink);
            tile = 0x29FFFFFF;
            chip = 0x38000000;
            dot = getColor(R.color.danger);
            icon = R.drawable.ic_stop;
            title = R.string.title_block;
        } else if (result.isAllowed()) {
            background = getColor(R.color.safe_bg);
            ink = getColor(R.color.safe_ink);
            tile = getColor(R.color.safe);
            chip = 0x242E6B4E;
            dot = getColor(R.color.safe);
            icon = R.drawable.ic_ok;
            title = R.string.title_allow;
        } else {
            background = getColor(R.color.warn_bg);
            ink = getColor(R.color.warn_ink);
            tile = getColor(R.color.warn);
            chip = 0x24A35F00;
            dot = getColor(R.color.warn);
            icon = R.drawable.ic_warn;
            title = R.string.title_warn;
        }

        verdictBlock.setBackground(topRounded(background));
        verdictTile.setBackground(rounded(tile, 22));
        verdictIcon.setImageResource(icon);
        verdictTitle.setText(title);
        verdictTitle.setTextColor(ink);
        verdictSubtitle.setText(subtitleFor(result));
        verdictSubtitle.setTextColor(ink);
        verdictDomain.setText(result.domain.isEmpty() ? result.url : result.domain);
        verdictDomain.setTextColor(ink);
        verdictDomain.setBackground(rounded(chip, 8));
        verdictDomain.setVisibility(View.VISIBLE);

        if (result.isAllowed()) {
            reasonsLabel.setText(R.string.label_remember);
            fillReasons(Arrays.asList(getString(R.string.tip_bank), getString(R.string.tip_hurry)), dot);
            primaryButton.setText(R.string.open_site);
            primaryButton.setBackgroundResource(R.drawable.btn_safe);
            primaryButton.setTextColor(getColor(R.color.white));
            primaryButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow, 0);
            continueButton.setVisibility(View.GONE);
        } else {
            reasonsLabel.setText(R.string.label_why);
            fillReasons(result.reasons, dot);
            primaryButton.setText(R.string.close_and_return);
            primaryButton.setBackgroundResource(R.drawable.btn_ink);
            primaryButton.setTextColor(getColor(R.color.paper));
            primaryButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            continueButton.setVisibility(View.VISIBLE);
        }
        bodyBlock.setVisibility(bodyBlockHasContent() ? View.VISIBLE : View.GONE);
        offlineNote.setVisibility(result.offline ? View.VISIBLE : View.GONE);

        verdictTitle.announceForAccessibility(verdictTitle.getText() + ". " + verdictSubtitle.getText());
    }

    /** Frase principal: diz quem está sendo imitado, quando sabemos. */
    private String subtitleFor(AnalysisResult result) {
        boolean imitates = !result.impersonates.isEmpty();
        if (result.isBlocked()) {
            return imitates
                    ? "Ele está se passando por " + result.impersonates + " para enganar você."
                    : "Encontramos sinais fortes de golpe neste endereço.";
        }
        if (result.isAllowed()) {
            return result.trusted
                    ? "Este é um endereço oficial conhecido. Não achamos sinais de golpe."
                    : "Não achamos sinais de golpe neste endereço.";
        }
        if (imitates) {
            return "Ele se parece com " + result.impersonates + ", mas não é o mesmo endereço.";
        }
        for (String reason : result.reasons) {
            if (reason.toLowerCase(Locale.ROOT).contains("encurtado")) {
                return "Este link é encurtado. Ele esconde para onde vai levar você.";
            }
        }
        return "Encontramos sinais suspeitos neste endereço.";
    }

    private void fillReasons(List<String> reasons, int dotColor) {
        reasonsList.removeAllViews();
        for (String reason : reasons) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, dp(12));

            View dot = new View(this);
            dot.setBackground(rounded(dotColor, 4));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
            dotParams.topMargin = dp(9);
            dotParams.rightMargin = dp(14);
            row.addView(dot, dotParams);

            TextView text = new TextView(this);
            text.setText(reason);
            text.setTextColor(getColor(R.color.ink));
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            text.setLineSpacing(0, 1.2f);
            text.setGravity(Gravity.START);
            row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            reasonsList.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.line));
            reasonsList.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private boolean bodyBlockHasContent() {
        return reasonsList.getChildCount() > 0;
    }

    private void showFailed() {
        if (isFinishing()) {
            return;
        }
        currentResult = null;
        loadingBlock.setVisibility(View.GONE);
        verdictBlock.setVisibility(View.VISIBLE);
        bodyBlock.setVisibility(View.GONE);
        actionsBlock.setVisibility(View.VISIBLE);

        verdictBlock.setBackground(topRounded(getColor(R.color.paper_2)));
        verdictTile.setBackground(rounded(getColor(R.color.ink), 22));
        verdictIcon.setImageResource(R.drawable.ic_help);
        verdictTitle.setText(R.string.failed_title);
        verdictTitle.setTextColor(getColor(R.color.ink));
        verdictSubtitle.setText(R.string.failed_summary);
        verdictSubtitle.setTextColor(getColor(R.color.ink_2));
        verdictDomain.setText(Uri.parse(currentUrl).getHost());
        verdictDomain.setTextColor(getColor(R.color.ink));
        verdictDomain.setBackground(rounded(0x14000000, 8));

        primaryButton.setText(R.string.close_and_return);
        primaryButton.setBackgroundResource(R.drawable.btn_ink);
        primaryButton.setTextColor(getColor(R.color.paper));
        primaryButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        listenButton.setVisibility(View.GONE);
        continueButton.setVisibility(View.GONE);
        offlineNote.setVisibility(View.GONE);
    }

    private void confirmContinue() {
        if (currentResult == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(R.string.confirm_message)
                .setNegativeButton(R.string.confirm_back, null)
                .setPositiveButton(R.string.confirm_open, (dialog, which) -> openInBrowser())
                .show();
    }

    private void openInBrowser() {
        if (currentResult == null) {
            return;
        }
        if (BrowserForwarder.open(this, currentResult.url)) {
            finish();
        } else {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private void shareWithFamily() {
        if (currentUrl == null) {
            return;
        }
        String message;
        if (currentResult == null) {
            message = "O URL Shield não conseguiu conferir este link e não abriu o site. "
                    + "Você pode me ajudar?\n\n" + currentUrl;
        } else {
            message = "Oi! Recebi este link e o URL Shield respondeu: \""
                    + verdictTitle.getText() + "\"\n\n" + currentResult.url
                    + "\n\nVocê pode me ajudar a conferir antes de eu abrir?";
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(share, getString(R.string.share_title)));
    }

    private void speakResult() {
        if (currentResult == null) {
            return;
        }
        if (!ttsReady) {
            Toast.makeText(this, R.string.tts_starting, Toast.LENGTH_SHORT).show();
            return;
        }
        String speech = currentResult.speechText.isEmpty()
                ? verdictTitle.getText() + ". " + verdictSubtitle.getText()
                : currentResult.speechText;
        textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "urlshield-result");
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

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private GradientDrawable topRounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        float r = dp(28);
        shape.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
