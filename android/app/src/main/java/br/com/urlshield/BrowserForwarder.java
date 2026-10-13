package br.com.urlshield;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Encaminha links aprovados para o navegador de verdade (Chrome, Samsung Internet...).
 * Como o URL Shield é o navegador padrão, um ACTION_VIEW comum voltaria para ele mesmo;
 * por isso o link é enviado explicitamente ao pacote escolhido na configuração.
 */
final class BrowserForwarder {
    private static final String PREFS = "urlshield";
    private static final String KEY_BROWSER = "browser_package";
    private static final String CHROME = "com.android.chrome";

    private BrowserForwarder() {
    }

    /** Pacotes de navegadores instalados, sem o próprio URL Shield. */
    static List<String> installedBrowsers(Context context) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"));
        probe.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> handlers = context.getPackageManager()
                .queryIntentActivities(probe, PackageManager.MATCH_ALL);

        Set<String> packages = new LinkedHashSet<>();
        for (ResolveInfo info : handlers) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(context.getPackageName())) {
                packages.add(pkg);
            }
        }
        return new ArrayList<>(packages);
    }

    static String preferredBrowser(Context context) {
        List<String> browsers = installedBrowsers(context);
        String saved = prefs(context).getString(KEY_BROWSER, null);
        if (saved != null && browsers.contains(saved)) {
            return saved;
        }
        if (browsers.contains(CHROME)) {
            return CHROME;
        }
        return browsers.isEmpty() ? null : browsers.get(0);
    }

    static void setPreferredBrowser(Context context, String pkg) {
        prefs(context).edit().putString(KEY_BROWSER, pkg).apply();
    }

    static CharSequence label(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException error) {
            return pkg;
        }
    }

    /** Abre o link no navegador escolhido. Retorna false se não houver nenhum. */
    static boolean open(Context context, String url) {
        Uri uri = Uri.parse(url);
        String pkg = preferredBrowser(context);
        if (pkg != null) {
            Intent direct = new Intent(Intent.ACTION_VIEW, uri);
            direct.addCategory(Intent.CATEGORY_BROWSABLE);
            direct.setPackage(pkg);
            direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(direct);
                return true;
            } catch (ActivityNotFoundException ignored) {
                // navegador desinstalado entre a escolha e agora: cai no seletor
            }
        }

        Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_VIEW, uri), null);
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new ComponentName[]{
                new ComponentName(context, LinkCheckActivity.class)
        });
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooser);
            return true;
        } catch (ActivityNotFoundException error) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
