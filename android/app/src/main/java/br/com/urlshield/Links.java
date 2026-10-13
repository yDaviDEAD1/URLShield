package br.com.urlshield;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.util.Patterns;
import android.view.View;
import android.view.WindowInsets;

import java.net.URI;
import java.util.regex.Matcher;

/** Utilidades compartilhadas pelas telas: leitura de links e margens das barras do sistema. */
final class Links {
    private Links() {
    }

    /** Link recebido ao tocar (ACTION_VIEW) ou compartilhado como texto (ACTION_SEND). */
    static String extractLink(Intent intent) {
        if (intent == null) {
            return null;
        }
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

    static String normalizeUrl(String input) {
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
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                return null;
            }
            return candidate;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    /**
     * Desenha atrás das barras do sistema (obrigatório a partir do Android 15) e
     * aplica a altura delas como espaçamento extra nas views indicadas.
     */
    @SuppressWarnings("deprecation")
    static void applySystemBarInsets(Activity activity, View topTarget, View bottomTarget) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.getWindow().setDecorFitsSystemWindows(false);
        } else {
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        final int topBase = topTarget.getPaddingTop();
        final int bottomBase = bottomTarget.getPaddingBottom();
        View decor = activity.getWindow().getDecorView();
        decor.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            topTarget.setPadding(topTarget.getPaddingLeft(), topBase + top,
                    topTarget.getPaddingRight(), topTarget == bottomTarget ? bottomBase + bottom : topTarget.getPaddingBottom());
            if (bottomTarget != topTarget) {
                bottomTarget.setPadding(bottomTarget.getPaddingLeft(), bottomTarget.getPaddingTop(),
                        bottomTarget.getPaddingRight(), bottomBase + bottom);
            }
            return view.onApplyWindowInsets(insets);
        });
        decor.requestApplyInsets();
    }
}
