package net.harbroi.quizgenerator;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "quiz_generator_prefs";
    private static final String PREF_API_KEY = "pref_api_key";
    private static final String PREF_LAST_SEEN_CHANGELOG_VERSION = "pref_last_seen_changelog_version";
    private static final String CHANGELOG_ASSET_FILE = "changelog_updates.txt";

    private TextInputEditText etApiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etApiKey = findViewById(R.id.etApiKey);
        MaterialButton btnMultipleChoice = findViewById(R.id.btnMultipleChoice);
        MaterialButton btnFlashCards = findViewById(R.id.btnFlashCards);

        restoreApiKey();
        showChangelogIfNeeded();

        etApiKey.setOnFocusChangeListener((View v, boolean hasFocus) -> {
            if (!hasFocus) {
                saveApiKey();
            }
        });

        btnMultipleChoice.setOnClickListener(v -> {
            saveApiKey();
            startActivity(new Intent(this, MainActivity.class));
        });

        btnFlashCards.setOnClickListener(v -> {
            saveApiKey();
            startActivity(new Intent(this, FlashActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (etApiKey != null && !etApiKey.hasFocus()) {
            restoreApiKey();
        }
    }

    private void saveApiKey() {
        if (etApiKey == null) {
            return;
        }

        getQuizPreferences().edit().putString(PREF_API_KEY, getTrimmedText(etApiKey)).apply();
    }

    private void restoreApiKey() {
        if (etApiKey == null) {
            return;
        }

        String savedApiKey = getQuizPreferences().getString(PREF_API_KEY, "");
        String currentText = etApiKey.getText() == null ? "" : etApiKey.getText().toString();
        if (!savedApiKey.equals(currentText)) {
            etApiKey.setText(savedApiKey);
            etApiKey.setSelection(savedApiKey.length());
        }
    }

    private void showChangelogIfNeeded() {
        String currentVersionToken = getCurrentVersionToken();
        String lastSeenVersion = getQuizPreferences().getString(PREF_LAST_SEEN_CHANGELOG_VERSION, "");
        if (currentVersionToken.equals(lastSeenVersion)) {
            return;
        }

        List<String> changelogItems = readChangelogItems();
        String message = buildChangelogMessage(currentVersionToken, changelogItems);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.changelog_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.changelog_close, (dialog, which) -> {
                    getQuizPreferences().edit()
                            .putString(PREF_LAST_SEEN_CHANGELOG_VERSION, currentVersionToken)
                            .apply();
                    dialog.dismiss();
                })
                .show();
    }

    private String getCurrentVersionToken() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName == null ? "" : packageInfo.versionName;
            long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
            return versionName + " (" + versionCode + ")";
        } catch (PackageManager.NameNotFoundException exception) {
            return "unknown";
        }
    }

    private List<String> readChangelogItems() {
        List<String> items = new ArrayList<>();
        try (InputStream inputStream = getAssets().open(CHANGELOG_ASSET_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    items.add(trimmed);
                }
            }
        } catch (IOException exception) {
            items.clear();
        }

        return items;
    }

    private String buildChangelogMessage(String currentVersionToken, List<String> changelogItems) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.changelog_version, currentVersionToken));
        builder.append("\n\n");

        if (changelogItems.isEmpty()) {
            builder.append(getString(R.string.changelog_empty));
            return builder.toString();
        }

        for (int index = 0; index < changelogItems.size(); index++) {
            builder.append(getString(R.string.changelog_line_item, index + 1, changelogItems.get(index)));
            if (index < changelogItems.size() - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String getTrimmedText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private SharedPreferences getQuizPreferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }
}

