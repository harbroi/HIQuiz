package net.harbroi.quizgenerator;

import android.content.Context;
import android.content.SharedPreferences;

public class QuizPreferencesManager {

    public static final String PREFS_NAME = "quiz_generator_prefs";
    public static final String PREF_API_KEY = "pref_api_key";
    public static final String PREF_LAST_SEEN_CHANGELOG_VERSION = "pref_last_seen_changelog_version";
    public static final String PREF_QUIZ_HISTORY = "pref_quiz_history";

    private final SharedPreferences sharedPreferences;

    public QuizPreferencesManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveApiKey(String apiKey) {
        sharedPreferences.edit().putString(PREF_API_KEY, apiKey == null ? "" : apiKey.trim()).apply();
    }

    public String getApiKey() {
        String value = sharedPreferences.getString(PREF_API_KEY, "");
        return value == null ? "" : value.trim();
    }

    public void saveLastSeenChangelogVersion(String versionToken) {
        sharedPreferences.edit().putString(PREF_LAST_SEEN_CHANGELOG_VERSION, versionToken).apply();
    }

    public String getLastSeenChangelogVersion() {
        String value = sharedPreferences.getString(PREF_LAST_SEEN_CHANGELOG_VERSION, "");
        return value == null ? "" : value;
    }

    public void saveQuizHistoryJson(String json) {
        sharedPreferences.edit().putString(PREF_QUIZ_HISTORY, json).apply();
    }

    public String getQuizHistoryJson() {
        String value = sharedPreferences.getString(PREF_QUIZ_HISTORY, "[]");
        return value == null || value.trim().isEmpty() ? "[]" : value;
    }
}

