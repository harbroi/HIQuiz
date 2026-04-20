package net.harbroi.quizgenerator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QuizQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String questionText;
    private final ArrayList<String> options;
    private final int correctAnswerIndex;
    private int selectedOptionIndex = -1;

    public QuizQuestion(String questionText, List<String> options, int correctAnswerIndex) {
        this.questionText = questionText;
        this.options = new ArrayList<>(options);
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public ArrayList<String> getOptions() {
        return new ArrayList<>(options);
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }

    public int getSelectedOptionIndex() {
        return selectedOptionIndex;
    }

    public void setSelectedOptionIndex(int selectedOptionIndex) {
        this.selectedOptionIndex = selectedOptionIndex;
    }
}

