package net.harbroi.quizgenerator;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GitHubUpdateChecker {

    private static final String TAG = "GitHubUpdateChecker";
    private static final String LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/harbroi/HIQuiz/releases/latest";
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/harbroi/HIQuiz/releases?per_page=10";

    public interface UpdateListener {
        void onUpdateAvailable(String latestVersion, String releaseUrl, String changelog, String apkUrl);
        void onNoUpdate();
        void onError(int statusCode, String message);
    }

    private static final class ReleaseInfo {
        final String version;
        final String releaseUrl;
        final String changelog;
        final String apkUrl;

        ReleaseInfo(String version, String releaseUrl, String changelog, String apkUrl) {
            this.version = version;
            this.releaseUrl = releaseUrl;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
        }
    }

    private static final class UpdateCheckException extends Exception {
        final int statusCode;

        UpdateCheckException(int statusCode, String message) {
            super(message == null ? "Unknown error" : message);
            this.statusCode = statusCode;
        }
    }

    public static void checkForUpdate(Context context, UpdateListener listener) {
        String currentVersion = normalizeVersion(getCurrentVersionName(context));
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                ReleaseInfo releaseInfo = fetchLatestRelease();
                if (releaseInfo == null) {
                    mainHandler.post(() -> listener.onError(-1, "Release metadata is empty."));
                    return;
                }

                if (isRemoteVersionNewer(currentVersion, releaseInfo.version)) {
                    mainHandler.post(() -> listener.onUpdateAvailable(
                            releaseInfo.version,
                            releaseInfo.releaseUrl,
                            releaseInfo.changelog,
                            releaseInfo.apkUrl
                    ));
                } else {
                    mainHandler.post(listener::onNoUpdate);
                }

            } catch (UpdateCheckException e) {
                Log.w(TAG, "Update check failed: " + e.statusCode + " " + e.getMessage());
                mainHandler.post(() -> listener.onError(e.statusCode, e.getMessage()));
            } catch (Exception e) {
                Log.w(TAG, "Update check failed", e);
                mainHandler.post(() -> listener.onError(-1, e.getMessage()));
            }
        }).start();
    }

    private static ReleaseInfo fetchLatestRelease() throws UpdateCheckException {
        UpdateCheckException latestError = null;
        try {
            JSONObject latest = getJsonObjectFromUrl(LATEST_RELEASE_API_URL);
            if (latest != null) {
                ReleaseInfo parsed = parseRelease(latest);
                if (parsed != null) {
                    return parsed;
                }
            }
        } catch (UpdateCheckException e) {
            latestError = e;
            Log.d(TAG, "Primary latest-release endpoint unavailable, trying releases list", e);
        }

        UpdateCheckException fallbackError = null;
        try {
            JSONArray releases = getJsonArrayFromUrl(RELEASES_API_URL);
            if (releases == null) {
                throw new UpdateCheckException(-1, "Releases list is empty.");
            }
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null) {
                    continue;
                }
                if (release.optBoolean("draft", false)) {
                    continue;
                }
                ReleaseInfo parsed = parseRelease(release);
                if (parsed != null) {
                    return parsed;
                }
            }
            throw new UpdateCheckException(-1, "No valid release found in releases list.");
        } catch (UpdateCheckException e) {
            fallbackError = e;
            Log.w(TAG, "Releases list fallback failed", e);
        }

        if (fallbackError != null) {
            throw fallbackError;
        }
        if (latestError != null) {
            throw latestError;
        }
        throw new UpdateCheckException(-1, "Unknown update-check error.");
    }

    private static ReleaseInfo parseRelease(JSONObject release) {
        String tagName = release.optString("tag_name", "");
        String latestVersion = normalizeVersion(tagName);
        if (latestVersion.isEmpty()) {
            return null;
        }

        String htmlUrl = release.optString("html_url", "");
        if (htmlUrl.isEmpty()) {
            htmlUrl = "https://github.com/harbroi/HIQuiz/releases/tag/" + tagName;
        }
        String changelog = release.optString("body", "").trim();

        String apkUrl = htmlUrl;
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.toLowerCase().endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", htmlUrl);
                    break;
                }
            }
        }

        return new ReleaseInfo(latestVersion, htmlUrl, changelog, apkUrl);
    }

    private static JSONObject getJsonObjectFromUrl(String urlString) throws UpdateCheckException {
        String response = getRawResponse(urlString);
        if (response.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(response);
        } catch (Exception e) {
            throw new UpdateCheckException(-1, "Invalid JSON object response: " + e.getMessage());
        }
    }

    private static JSONArray getJsonArrayFromUrl(String urlString) throws UpdateCheckException {
        String response = getRawResponse(urlString);
        if (response.isEmpty()) {
            return null;
        }
        try {
            return new JSONArray(response);
        } catch (Exception e) {
            throw new UpdateCheckException(-1, "Invalid JSON array response: " + e.getMessage());
        }
    }

    private static String getRawResponse(String urlString) throws UpdateCheckException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "HIQuiz-Android");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = "";
                if (connection.getErrorStream() != null) {
                    try (BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        StringBuilder builder = new StringBuilder();
                        while ((line = errorReader.readLine()) != null) {
                            builder.append(line);
                        }
                        errorBody = builder.toString().trim();
                    } catch (IOException ignored) {
                        // Best effort: keep HTTP code even if error body can't be read.
                    }
                }
                String responseMessage = connection.getResponseMessage();
                String detail = (responseMessage == null || responseMessage.isEmpty())
                        ? "HTTP error"
                        : responseMessage;
                if (!errorBody.isEmpty()) {
                    detail = detail + " | " + errorBody;
                }
                throw new UpdateCheckException(responseCode, detail);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            return response.toString();
        } catch (UpdateCheckException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateCheckException(-1, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        String version = raw.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        int suffixStart = version.indexOf('-');
        if (suffixStart >= 0) {
            version = version.substring(0, suffixStart);
        }
        return version.trim();
    }

    private static boolean isRemoteVersionNewer(String currentVersion, String remoteVersion) {
        String normalizedCurrent = normalizeVersion(currentVersion);
        String normalizedRemote = normalizeVersion(remoteVersion);
        if (normalizedRemote.isEmpty()) {
            return false;
        }
        if (normalizedCurrent.isEmpty()) {
            return true;
        }

        String[] currentParts = normalizedCurrent.split("\\.");
        String[] remoteParts = normalizedRemote.split("\\.");
        int max = Math.max(currentParts.length, remoteParts.length);

        for (int i = 0; i < max; i++) {
            int current = parseVersionPart(currentParts, i);
            int remote = parseVersionPart(remoteParts, i);
            if (remote > current) {
                return true;
            }
            if (remote < current) {
                return false;
            }
        }

        return false;
    }

    private static int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
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

