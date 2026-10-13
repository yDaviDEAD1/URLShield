package br.com.urlshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Tela de configuração. No dia a dia a pessoa nem abre o app: os links tocados
 * caem direto no pop-up (LinkCheckActivity).
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_BROWSER_ROLE = 1001;

    private FrameLayout statusTile;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusDescription;
    private Button activateButton;
    private Button deactivateButton;
    private TextView browserName;
    private EditText testUrlInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        Links.applySystemBarInsets(this, root, root, true);

        statusTile = findViewById(R.id.statusTile);
        statusIcon = findViewById(R.id.statusIcon);
        statusTitle = findViewById(R.id.statusTitle);
        statusDescription = findViewById(R.id.statusDescription);
        activateButton = findViewById(R.id.activateButton);
        deactivateButton = findViewById(R.id.deactivateButton);
        browserName = findViewById(R.id.browserName);
        testUrlInput = findViewById(R.id.testUrlInput);

        activateButton.setOnClickListener(view -> requestBrowserRole());
        deactivateButton.setOnClickListener(view -> confirmDeactivation());
        findViewById(R.id.browserChangeButton).setOnClickListener(view -> pickBrowser());
        findViewById(R.id.testButton).setOnClickListener(view -> testLink());
        testUrlInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                testLink();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateProtectionStatus();
        updateBrowserName();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BROWSER_ROLE) {
            updateProtectionStatus();
        }
    }

    private void updateProtectionStatus() {
        boolean active = isBrowserRoleHeld();
        GradientDrawable tile = new GradientDrawable();
        tile.setCornerRadius(22 * getResources().getDisplayMetrics().density);
        if (active) {
            tile.setColor(getColor(R.color.safe));
            statusIcon.setImageResource(R.drawable.ic_ok);
            statusTitle.setText(R.string.protection_on_title);
            statusDescription.setText(R.string.protection_on_description);
            activateButton.setVisibility(View.GONE);
            deactivateButton.setVisibility(View.VISIBLE);
        } else {
            tile.setColor(getColor(R.color.warn));
            statusIcon.setImageResource(R.drawable.ic_warn);
            statusTitle.setText(R.string.setup_title);
            statusDescription.setText(R.string.setup_description);
            activateButton.setVisibility(View.VISIBLE);
            deactivateButton.setVisibility(View.GONE);
        }
        statusTile.setBackground(tile);
    }

    private void updateBrowserName() {
        String pkg = BrowserForwarder.preferredBrowser(this);
        browserName.setText(pkg == null ? getString(R.string.browser_none) : BrowserForwarder.label(this, pkg));
    }

    private void pickBrowser() {
        List<String> browsers = BrowserForwarder.installedBrowsers(this);
        if (browsers.isEmpty()) {
            new AlertDialog.Builder(this).setMessage(R.string.no_browser).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        CharSequence[] labels = new CharSequence[browsers.size()];
        String current = BrowserForwarder.preferredBrowser(this);
        int checked = 0;
        for (int i = 0; i < browsers.size(); i++) {
            labels[i] = BrowserForwarder.label(this, browsers.get(i));
            if (browsers.get(i).equals(current)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_pick_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    BrowserForwarder.setPreferredBrowser(this, browsers.get(which));
                    updateBrowserName();
                    dialog.dismiss();
                })
                .show();
    }

    private void testLink() {
        String link = Links.normalizeUrl(testUrlInput.getText().toString());
        if (link == null) {
            testUrlInput.setError(getString(R.string.invalid_link));
            return;
        }
        Intent check = new Intent(this, LinkCheckActivity.class);
        check.setAction(Intent.ACTION_VIEW);
        check.setData(Uri.parse(link));
        startActivity(check);
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

    @SuppressWarnings("deprecation")
    private void requestBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager manager = getSystemService(RoleManager.class);
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_BROWSER), REQUEST_BROWSER_ROLE);
                return;
            }
        }
        openDefaultAppsSettings();
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
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
