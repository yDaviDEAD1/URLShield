package br.com.urlshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pop-up que aparece por cima do WhatsApp/SMS quando a pessoa toca em um link.
 * Mostra o veredito e, se ela decidir abrir, encaminha o link ao navegador de verdade.
 */
public final class LinkCheckActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final String TAG = "URLShield";
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
    private Button moreReasonsButton;
    private View actionsBlock;
    private Button primaryButton;
    private Button listenButton;
    private Button continueButton;
    private TextView offlineNote;

    private String currentUrl;
    private AnalysisResult currentResult;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private boolean ttsFailed;
    private String pendingSpeech;
    private MediaPlayer player;
    private final Runnable audioTimeout = this::onServerAudioTimeout;
    private String audioSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_check);
        bindViews();
        Links.applySystemBarInsets(this, findViewById(R.id.scrim), findViewById(R.id.sheet), false);
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
        moreReasonsButton = findViewById(R.id.moreReasonsButton);
        moreReasonsButton.setPaintFlags(moreReasonsButton.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
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

        // Tamanho proporcional ao risco: "Pode abrir" não lista nada, "Tenha cuidado"
        // só mostra motivo se a frase principal não explicar, "Não abra" mostra até 2.
        int visibleReasons;
        if (result.isAllowed()) {
            visibleReasons = 0;
        } else if (result.isBlocked()) {
            visibleReasons = 2;
        } else {
            visibleReasons = subtitleExplainsRisk(result) ? 0 : 1;
        }
        // Se a frase principal já explica e só há um motivo, não há o que expandir
        boolean nothingToAdd = result.isAllowed()
                || (!result.isBlocked() && subtitleExplainsRisk(result) && result.reasons.size() <= 1);
        showReasons(nothingToAdd ? java.util.Collections.<String>emptyList() : result.reasons,
                visibleReasons, dot);

        if (result.isAllowed()) {
            primaryButton.setText(R.string.open_site);
            primaryButton.setBackgroundResource(R.drawable.btn_safe);
            primaryButton.setTextColor(getColor(R.color.white));
            primaryButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow, 0);
            continueButton.setVisibility(View.GONE);
        } else {
            primaryButton.setText(R.string.close_and_return);
            primaryButton.setBackgroundResource(R.drawable.btn_ink);
            primaryButton.setTextColor(getColor(R.color.paper));
            primaryButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            continueButton.setVisibility(View.VISIBLE);
        }
        centerButtons();
        offlineNote.setVisibility(result.offline ? View.VISIBLE : View.GONE);

        // Vibração curta no perigo, para quem não está olhando direito para a tela
        if (result.isBlocked()) {
            verdictBlock.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
        }

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

    private boolean subtitleExplainsRisk(AnalysisResult result) {
        if (!result.impersonates.isEmpty()) {
            return true;
        }
        for (String reason : result.reasons) {
            if (reason.toLowerCase(Locale.ROOT).contains("encurtado")) {
                return true;
            }
        }
        return false;
    }

    /** Mostra os primeiros motivos; os demais ficam atrás de um botão "Ver…". */
    private void showReasons(List<String> reasons, int visible, int dotColor) {
        int shown = Math.min(visible, reasons.size());
        fillReasons(reasons.subList(0, shown), dotColor);
        int hidden = reasons.size() - shown;
        if (hidden > 0) {
            moreReasonsButton.setText(shown == 0
                    ? getString(R.string.see_reasons, reasons.size())
                    : getString(R.string.see_all_reasons, reasons.size()));
            moreReasonsButton.setVisibility(View.VISIBLE);
            moreReasonsButton.setOnClickListener(view -> {
                fillReasons(reasons, dotColor);
                moreReasonsButton.setVisibility(View.GONE);
                reasonsLabel.setVisibility(View.VISIBLE);
                reasonsList.getChildAt(Math.min(shown * 2, reasonsList.getChildCount() - 1))
                        .sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
            });
        } else {
            moreReasonsButton.setVisibility(View.GONE);
        }
        reasonsLabel.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        bodyBlock.setVisibility(reasons.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void fillReasons(List<String> reasons, int dotColor) {
        reasonsList.removeAllViews();
        for (String reason : reasons) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(10));

            View dot = new View(this);
            dot.setBackground(rounded(dotColor, 4));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
            dotParams.topMargin = dp(9);
            dotParams.rightMargin = dp(14);
            row.addView(dot, dotParams);

            TextView text = new TextView(this);
            text.setText(reason);
            text.setTextColor(getColor(R.color.ink));
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            text.setLineSpacing(0, 1.2f);
            text.setGravity(Gravity.START);
            row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            reasonsList.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.line));
            reasonsList.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }
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
        centerButtons();
    }

    private void centerButtons() {
        Links.centerIconAndText(primaryButton);
        Links.centerIconAndText(findViewById(R.id.familyButton));
        Links.centerIconAndText(listenButton);
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
        Intent chooser = Intent.createChooser(share, getString(R.string.share_title));
        // Não oferece o próprio URL Shield como destino do compartilhamento
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new android.content.ComponentName[]{
                new android.content.ComponentName(this, LinkCheckActivity.class)
        });
        startActivity(chooser);
    }

    private void speakResult() {
        if (currentResult == null) {
            return;
        }
        // Tocar de novo = parar
        if (player != null || textToSpeech.isSpeaking()) {
            stopAllSpeech();
            return;
        }
        String speech = speechText();
        warnIfMediaMuted();
        if (UrlShieldApi.isConfigured() && hasInternet()) {
            playServerAudio(speech);
        } else {
            speakWithDeviceVoice(speech);
        }
    }

    /** O que é lido: veredito + frase principal (curto, para caber no limite do servidor). */
    private String speechText() {
        String text = verdictTitle.getText() + ". " + verdictSubtitle.getText();
        return text.length() > 380 ? text.substring(0, 380) : text;
    }

    /**
     * Principal: áudio MP3 gerado pelo servidor (/api/tts), tocado como mídia.
     * A voz embutida do Android depende do mecanismo instalado e falha em vários aparelhos.
     */
    private void playServerAudio(String speech) {
        audioSpeech = speech;
        Uri uri = Uri.parse(BuildConfig.URL_SHIELD_API_URL + "/api/tts")
                .buildUpon().appendQueryParameter("text", speech).build();
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        player.setOnPreparedListener(mp -> {
            mainHandler.removeCallbacks(audioTimeout);
            Log.i(TAG, "Áudio do servidor tocando");
            mp.start();
        });
        player.setOnCompletionListener(mp -> {
            Log.i(TAG, "Áudio do servidor terminou");
            releasePlayer();
        });
        player.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "Áudio do servidor falhou (" + what + "/" + extra + "), usando a voz do aparelho");
            releasePlayer();
            speakWithDeviceVoice(speech);
            return true;
        });
        try {
            player.setDataSource(this, uri);
            player.prepareAsync();
            listenButton.setText(R.string.stop_listening);
            Links.centerIconAndText(listenButton);
            mainHandler.postDelayed(audioTimeout, 6_000);
        } catch (Exception error) {
            Log.w(TAG, "Não foi possível carregar o áudio", error);
            releasePlayer();
            speakWithDeviceVoice(speech);
        }
    }

    private void onServerAudioTimeout() {
        if (player != null && !player.isPlaying()) {
            Log.w(TAG, "Áudio do servidor demorou demais, usando a voz do aparelho");
            releasePlayer();
            speakWithDeviceVoice(audioSpeech);
        }
    }

    private void releasePlayer() {
        mainHandler.removeCallbacks(audioTimeout);
        if (player != null) {
            player.release();
            player = null;
        }
        listenButton.setText(R.string.listen);
        Links.centerIconAndText(listenButton);
    }

    private void stopAllSpeech() {
        releasePlayer();
        textToSpeech.stop();
    }

    /** Reserva (sem internet ou servidor fora): voz do próprio Android. */
    private void speakWithDeviceVoice(String speech) {
        if (ttsFailed) {
            showNoVoiceHelp();
            return;
        }
        if (!ttsReady) {
            // O motor de voz ainda está iniciando: fala assim que ficar pronto
            pendingSpeech = speech;
            Toast.makeText(this, R.string.tts_starting, Toast.LENGTH_SHORT).show();
            return;
        }
        speak(speech);
    }

    private boolean hasInternet() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager == null) {
            return false;
        }
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** Volume de mídia no zero é a causa mais comum de "não saiu som". */
    private void warnIfMediaMuted() {
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio != null && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Toast.makeText(this, R.string.media_muted, Toast.LENGTH_LONG).show();
        }
    }

    private void speak(String speech) {
        int result = textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "urlshield-result");
        Log.i(TAG, "TTS speak -> " + (result == TextToSpeech.SUCCESS ? "ok" : "erro " + result));
        if (result != TextToSpeech.SUCCESS) {
            showNoVoiceHelp();
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS indisponível, status " + status);
            ttsFailed = true;
            return;
        }
        // Prefere português do Brasil; sem ele, usa qualquer voz em português ou a padrão
        Locale[] candidates = {new Locale("pt", "BR"), new Locale("pt"), Locale.getDefault()};
        for (Locale locale : candidates) {
            int availability = textToSpeech.setLanguage(locale);
            if (availability != TextToSpeech.LANG_MISSING_DATA
                    && availability != TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.i(TAG, "TTS pronto em " + locale);
                break;
            }
        }
        textToSpeech.setSpeechRate(0.9f);
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.i(TAG, "TTS falando");
            }

            @Override
            public void onDone(String utteranceId) {
                Log.i(TAG, "TTS terminou");
            }

            @Override
            public void onError(String utteranceId) {
                Log.w(TAG, "TTS erro ao falar");
            }
        });
        ttsReady = true;
        if (pendingSpeech != null) {
            speak(pendingSpeech);
            pendingSpeech = null;
        }
    }

    /** Sem motor de voz: leva às configurações para instalar uma voz. */
    private void showNoVoiceHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.no_voice_title)
                .setMessage(R.string.no_voice_message)
                .setNegativeButton(R.string.confirm_back, null)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    try {
                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (Exception ignored) {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                })
                .show();
    }

    private void releasePlayerQuietly() {
        mainHandler.removeCallbacks(audioTimeout);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onStop() {
        // Saiu do pop-up (abriu o site, voltou ao WhatsApp): para a leitura
        if (player != null || (textToSpeech != null && textToSpeech.isSpeaking())) {
            stopAllSpeech();
        }
        super.onStop();
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
        releasePlayerQuietly();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
