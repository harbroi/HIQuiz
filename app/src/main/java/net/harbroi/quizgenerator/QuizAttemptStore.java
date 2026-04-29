package net.harbroi.quizgenerator;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QuizAttemptStore {

    private final QuizPreferencesManager preferencesManager;

    public QuizAttemptStore(Context context) {
        this.preferencesManager = new QuizPreferencesManager(context.getApplicationContext());
    }

    public void addAttempt(QuizAttempt attempt) {
        ArrayList<QuizAttempt> attempts = getAttempts();
        attempts.add(attempt);
        saveAttempts(attempts);
    }

    public ArrayList<QuizAttempt> getAttempts() {
        ArrayList<QuizAttempt> attempts = new ArrayList<>();
        try {
            JSONArray root = new JSONArray(preferencesManager.getQuizHistoryJson());
            for (int i = 0; i < root.length(); i++) {
                JSONObject item = root.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                JSONArray filesJson = item.optJSONArray("filesUsed");
                ArrayList<String> filesUsed = new ArrayList<>();
                if (filesJson != null) {
                    for (int j = 0; j < filesJson.length(); j++) {
                        String value = filesJson.optString(j, "");
                        if (!value.trim().isEmpty()) {
                            filesUsed.add(value);
                        }
                    }
                }

                attempts.add(new QuizAttempt(
                        item.optString("category", ""),
                        filesUsed,
                        item.optInt("questionCount", 0),
                        item.optInt("correctCount", 0),
                        item.optInt("wrongCount", 0),
                        item.optInt("percentage", 0),
                        item.optLong("dateTakenMillis", 0L)
                ));
            }
        } catch (Exception ignored) {
            attempts.clear();
        }

        attempts.sort(Comparator.comparingLong(QuizAttempt::getDateTakenMillis).reversed());
        return attempts;
    }

    private void saveAttempts(List<QuizAttempt> attempts) {
        JSONArray root = new JSONArray();

        ArrayList<QuizAttempt> sorted = new ArrayList<>(attempts);
        sorted.sort(Comparator.comparingLong(QuizAttempt::getDateTakenMillis).reversed());

        for (QuizAttempt attempt : sorted) {
            try {
                JSONObject item = new JSONObject();
                item.put("category", attempt.getCategory());
                item.put("questionCount", attempt.getQuestionCount());
                item.put("correctCount", attempt.getCorrectCount());
                item.put("wrongCount", attempt.getWrongCount());
                item.put("percentage", attempt.getPercentage());
                item.put("dateTakenMillis", attempt.getDateTakenMillis());

                JSONArray files = new JSONArray();
                for (String file : attempt.getFilesUsed()) {
                    files.put(file);
                }
                item.put("filesUsed", files);
                root.put(item);
            } catch (Exception ignored) {
                // Skip malformed record to avoid blocking save of other attempts.
            }
        }

        preferencesManager.saveQuizHistoryJson(root.toString());
    }
}
