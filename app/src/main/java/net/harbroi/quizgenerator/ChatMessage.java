package net.harbroi.quizgenerator;

import java.io.Serializable;

public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_USER = "user";
    public static final String ROLE_MODEL = "model";

    private final String role;
    private final String text;

    public ChatMessage(String role, String text) {
        this.role = ROLE_MODEL.equals(role) ? ROLE_MODEL : ROLE_USER;
        this.text = text == null ? "" : text.trim();
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(ROLE_USER, text);
    }

    public static ChatMessage model(String text) {
        return new ChatMessage(ROLE_MODEL, text);
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }
}

