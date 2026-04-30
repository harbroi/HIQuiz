package net.harbroi.quizgenerator;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final String CHANGELOG_ASSET_FILE = "changelog_updates.txt";
    private static final String LEGACY_CHANGELOG_ASSET_FILE = "changelog_updated.txt";
    private static final int ABOUT_UPDATES_LIMIT = 5;

    private QuizPreferencesManager preferencesManager;

    private LinearLayout sectionHome;
    private LinearLayout sectionSettings;
    private LinearLayout sectionHistory;
    private LinearLayout sectionAbout;
    private TextInputEditText etApiKey;
    private LinearLayout llHistoryItems;
    private TextView tvEmptyHistory;
    private TextView tvAboutVersion;
    private TextView tvAboutLatestUpdates;
    private boolean updateCheckInProgress;
    private boolean updateCheckCompleted;
    private boolean updateDialogShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        preferencesManager = new QuizPreferencesManager(this);

        sectionHome = findViewById(R.id.sectionHome);
        sectionSettings = findViewById(R.id.sectionSettings);
        sectionHistory = findViewById(R.id.sectionHistory);
        sectionAbout = findViewById(R.id.sectionAbout);
        etApiKey = findViewById(R.id.etApiKey);
        llHistoryItems = findViewById(R.id.llHistoryItems);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        tvAboutVersion = findViewById(R.id.tvAboutVersion);
        tvAboutLatestUpdates = findViewById(R.id.tvAboutLatestUpdates);
        TextView tvAiStudioLink = findViewById(R.id.tvAiStudioLink);
        MaterialButton btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        MaterialButton btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

        MaterialButton btnMultipleChoice = findViewById(R.id.btnMultipleChoice);
        MaterialButton btnFlashCards = findViewById(R.id.btnFlashCards);
        MaterialButton navHome = findViewById(R.id.navHome);
        MaterialButton navSettings = findViewById(R.id.navSettings);
        MaterialButton navHistory = findViewById(R.id.navHistory);
        MaterialButton navAbout = findViewById(R.id.navAbout);

        btnMultipleChoice.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        btnFlashCards.setOnClickListener(v -> startActivity(new Intent(this, FlashActivity.class)));

        navHome.setOnClickListener(v -> showSection(sectionHome));
        navSettings.setOnClickListener(v -> showSection(sectionSettings));
        navHistory.setOnClickListener(v -> {
            showSection(sectionHistory);
            renderHistory();
        });
        navAbout.setOnClickListener(v -> {
            showSection(sectionAbout);
            tvAboutVersion.setText("Version: " + getCurrentVersionToken());
            renderAboutLatestUpdates();
        });

        tvAiStudioLink.setOnClickListener(v -> openAiStudioLink());
        btnSaveApiKey.setOnClickListener(v -> {
            saveApiKey();
            Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show();
        });
        btnCheckUpdate.setOnClickListener(v -> checkForAppUpdate(true));

        etApiKey.setText(preferencesManager.getApiKey());

        showSection(sectionHome);
        showChangelogIfNeeded(() -> checkForAppUpdate(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (etApiKey != null && !etApiKey.hasFocus()) {
            etApiKey.setText(preferencesManager.getApiKey());
        }
        if (sectionHistory != null && sectionHistory.getVisibility() == View.VISIBLE) {
            renderHistory();
        }
        checkForAppUpdate(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void showSection(View target) {
        sectionHome.setVisibility(target == sectionHome ? View.VISIBLE : View.GONE);
        sectionSettings.setVisibility(target == sectionSettings ? View.VISIBLE : View.GONE);
        sectionHistory.setVisibility(target == sectionHistory ? View.VISIBLE : View.GONE);
        sectionAbout.setVisibility(target == sectionAbout ? View.VISIBLE : View.GONE);
    }

    private void saveApiKey() {
        if (etApiKey == null) {
            return;
        }
        String value = etApiKey.getText() == null ? "" : etApiKey.getText().toString();
        preferencesManager.saveApiKey(value);
    }

    private void renderHistory() {
        llHistoryItems.removeAllViews();
        ArrayList<QuizAttempt> attempts = new QuizAttemptStore(this).getAttempts();
        if (attempts.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyHistory.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(this);
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

        for (QuizAttempt attempt : attempts) {
            View item = inflater.inflate(R.layout.item_history_attempt, llHistoryItems, false);
            TextView tvCategory = item.findViewById(R.id.tvHistoryCategory);
            TextView tvFiles = item.findViewById(R.id.tvHistoryFiles);
            TextView tvQuestions = item.findViewById(R.id.tvHistoryQuestionCount);
            TextView tvScore = item.findViewById(R.id.tvHistoryScore);
            TextView tvDate = item.findViewById(R.id.tvHistoryDate);

            Date dateTaken = new Date(attempt.getDateTakenMillis());
            String dateText = dateFormat.format(dateTaken) + " " + timeFormat.format(dateTaken);

            tvCategory.setText("Category: " + attempt.getCategory());
            tvFiles.setText("Files used: " + joinFiles(attempt.getFilesUsed()));
            tvQuestions.setText("Number of questions: " + attempt.getQuestionCount());
            tvScore.setText("Score: " + attempt.getCorrectCount() + "/" + attempt.getQuestionCount()
                    + " (" + attempt.getPercentage() + "%) - Wrong: " + attempt.getWrongCount());
            tvDate.setText("Date taken: " + dateText);

            llHistoryItems.addView(item);
        }
    }

    private String joinFiles(ArrayList<String> files) {
        if (files == null || files.isEmpty()) {
            return "No files recorded";
        }
        return android.text.TextUtils.join(", ", files);
    }

    private void openAiStudioLink() {
        String url = getString(R.string.ai_studio_url);
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.open_link_chooser_title)));
        } catch (ActivityNotFoundException exception) {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI Studio URL", url));
                Toast.makeText(this, R.string.error_open_link_copied, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.error_open_link_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void renderAboutLatestUpdates() {
        if (tvAboutLatestUpdates == null) {
            return;
        }

        List<String> changelogItems = readChangelogItems();
        if (changelogItems.isEmpty()) {
            tvAboutLatestUpdates.setText(R.string.changelog_empty);
            return;
        }

        int maxCount = Math.min(ABOUT_UPDATES_LIMIT, changelogItems.size());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < maxCount; index++) {
            builder.append("• ").append(changelogItems.get(index));
            if (index < maxCount - 1) {
                builder.append("\n");
            }
        }

        tvAboutLatestUpdates.setText(builder.toString());
    }

    private void showChangelogIfNeeded(Runnable onComplete) {
        String currentVersionToken = getCurrentVersionToken();
        String lastSeenVersion = preferencesManager.getLastSeenChangelogVersion();
        if (currentVersionToken.equals(lastSeenVersion)) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        List<String> changelogItems = readChangelogItems();
        String message = buildChangelogMessage(currentVersionToken, changelogItems);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.changelog_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.changelog_close, (dialog, which) -> {
                    preferencesManager.saveLastSeenChangelogVersion(currentVersionToken);
                    dialog.dismiss();
                    if (onComplete != null) {
                        onComplete.run();
                    }
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

        if (!readChangelogFromAsset(CHANGELOG_ASSET_FILE, items)) {
            readChangelogFromAsset(LEGACY_CHANGELOG_ASSET_FILE, items);
        }

        return items;
    }

    private boolean readChangelogFromAsset(String assetFileName, List<String> items) {
        try (InputStream inputStream = getAssets().open(assetFileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    items.add(trimmed);
                }
            }
            return true;
        } catch (IOException exception) {
            items.clear();
            return false;
        }
    }

    private void checkForAppUpdate(boolean force) {
        if (updateCheckInProgress) {
            return;
        }
        if (!force && (updateCheckCompleted || updateDialogShown)) {
            return;
        }

        if (force) {
            updateCheckCompleted = false;
            updateDialogShown = false;
        }

        updateCheckInProgress = true;

        GitHubUpdateChecker.checkForUpdate(this, new GitHubUpdateChecker.UpdateListener() {
            @Override
            public void onUpdateAvailable(String latestVersion, String releaseUrl, String changelog, String apkUrl) {
                updateCheckInProgress = false;
                updateCheckCompleted = true;
                if (updateDialogShown) {
                    return;
                }
                if (isFinishing() || isDestroyed()) return;
                updateDialogShown = true;

                // Build the dialog message: version header + changelog body
                StringBuilder message = new StringBuilder();
                message.append(getString(R.string.update_available_version, latestVersion));
                List<String> localChangelogItems = readChangelogItems();
                if (!localChangelogItems.isEmpty()) {
                    message.append("\n\n").append(getString(R.string.update_whats_new)).append("\n");
                    for (int index = 0; index < localChangelogItems.size(); index++) {
                        message.append("- ").append(localChangelogItems.get(index));
                        if (index < localChangelogItems.size() - 1) {
                            message.append("\n");
                        }
                    }
                } else {
                    message.append("\n\n").append(getString(R.string.changelog_empty));
                }

                new MaterialAlertDialogBuilder(HomeActivity.this)
                        .setTitle(R.string.update_available_title)
                        .setMessage(message.toString())
                        .setPositiveButton(R.string.update_download_apk, (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                            startActivity(intent);
                        })
                        .setNeutralButton(R.string.update_view_release, (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl));
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.update_dismiss, null)
                        .show();
            }

            @Override
            public void onNoUpdate() {
                updateCheckInProgress = false;
                updateCheckCompleted = true;
                if (force) {
                    Toast.makeText(HomeActivity.this, R.string.update_no_update_message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int statusCode, String message) {
                updateCheckInProgress = false;
                String detail = (message == null || message.isEmpty()) ? "Unknown error" : message;
                String reason = statusCode > 0 ? "HTTP " + statusCode + ": " + detail : detail;
                Log.w(TAG, "Update check failed - " + reason);
                Toast.makeText(
                        HomeActivity.this,
                        getString(R.string.update_check_failed_with_reason, reason),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
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
}
