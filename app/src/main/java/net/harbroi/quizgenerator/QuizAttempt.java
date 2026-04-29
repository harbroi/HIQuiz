package net.harbroi.quizgenerator;

import java.util.ArrayList;
import java.util.List;

public class QuizAttempt {

    private final String category;
    private final ArrayList<String> filesUsed;
    private final int questionCount;
    private final int correctCount;
    private final int wrongCount;
    private final int percentage;
    private final long dateTakenMillis;

    public QuizAttempt(
            String category,
            List<String> filesUsed,
            int questionCount,
            int correctCount,
            int wrongCount,
            int percentage,
            long dateTakenMillis
    ) {
        this.category = category == null ? "" : category;
        this.filesUsed = new ArrayList<>(filesUsed == null ? new ArrayList<>() : filesUsed);
        this.questionCount = questionCount;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.percentage = percentage;
        this.dateTakenMillis = dateTakenMillis;
    }

    public String getCategory() {
        return category;
    }

    public ArrayList<String> getFilesUsed() {
        return new ArrayList<>(filesUsed);
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public int getPercentage() {
        return percentage;
    }

    public long getDateTakenMillis() {
        return dateTakenMillis;
    }
}

