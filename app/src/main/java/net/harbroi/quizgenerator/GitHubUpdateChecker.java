package net.harbroi.quizgenerator;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GitHubUpdateChecker {

    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/harbroi/HIQuiz/releases/latest";

    public interface UpdateListener {
        void onUpdateAvailable(String latestVersion, String releaseUrl);
        void onNoUpdate();
        void onError();
    }

    public static void checkForUpdate(Context context, UpdateListener listener) {
        String currentVersion = getCurrentVersionName(context);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                URL url = new URL(RELEASES_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(listener::onError);
                    connection.disconnect();
                    return;
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                connection.disconnect();

                JSONObject json = new JSONObject(response.toString());
                String tagName = json.optString("tag_name", "");
                String htmlUrl = json.optString("html_url", "");

                // Strip leading 'v' prefix from tag (e.g. "v2.1" → "2.1")
                String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

                if (!latestVersion.isEmpty() && !latestVersion.equals(currentVersion)) {
                    mainHandler.post(() -> listener.onUpdateAvailable(latestVersion, htmlUrl));
                } else {
                    mainHandler.post(listener::onNoUpdate);
                }

            } catch (Exception e) {
                mainHandler.post(listener::onError);
            }
        }).start();
    }

    private static String getCurrentVersionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}

