package net.harbroi.quizgenerator;

import java.io.Serializable;

public class FlashCard implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String question;
    private final String answer;

    public FlashCard(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }
}

