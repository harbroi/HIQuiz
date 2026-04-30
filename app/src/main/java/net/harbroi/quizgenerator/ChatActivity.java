package net.harbroi.quizgenerator;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_PROMPT = "extra_initial_prompt";

    private static final String STATE_MESSAGES = "state_messages";
    private static final String STATE_DRAFT = "state_draft";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final GeminiQuizService geminiQuizService = new GeminiQuizService();
    private final ArrayList<ChatMessage> conversation = new ArrayList<>();

    private QuizPreferencesManager preferencesManager;
    private ScrollView svConversation;
    private LinearLayout llConversationMessages;
    private TextView tvChatEmpty;
    private TextView tvChatStatus;
    private TextInputEditText etChatPrompt;
    private MaterialButton btnSendChat;
    private LinearProgressIndicator progressChat;
    private boolean isSending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        preferencesManager = new QuizPreferencesManager(this);

        MaterialButton btnBackHome = findViewById(R.id.btnBackHome);
        svConversation = findViewById(R.id.svConversation);
        llConversationMessages = findViewById(R.id.llConversationMessages);
        tvChatEmpty = findViewById(R.id.tvChatEmpty);
        tvChatStatus = findViewById(R.id.tvChatStatus);
        etChatPrompt = findViewById(R.id.etChatPrompt);
        btnSendChat = findViewById(R.id.btnSendChat);
        progressChat = findViewById(R.id.progressChat);

        btnBackHome.setOnClickListener(v -> finish());
        btnSendChat.setOnClickListener(v -> submitPrompt());

        if (savedInstanceState != null) {
            restoreConversation(savedInstanceState);
            String draft = savedInstanceState.getString(STATE_DRAFT, "");
            etChatPrompt.setText(draft);
        } else {
            String initialPrompt = getIntent().getStringExtra(EXTRA_INITIAL_PROMPT);
            if (initialPrompt != null && !initialPrompt.trim().isEmpty()) {
                sendMessage(initialPrompt.trim());
            }
        }

        if (conversation.isEmpty()) {
            showEmptyState(true);
        }
        if (tvChatStatus.getText() == null || tvChatStatus.getText().length() == 0) {
            tvChatStatus.setText(R.string.chat_status_idle);
        }
        if (!isSending) {
            setSending(false, tvChatStatus.getText() == null
                    ? getString(R.string.chat_status_idle)
                    : tvChatStatus.getText().toString());
        }
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_MESSAGES, new ArrayList<>(conversation));
        outState.putString(STATE_DRAFT, getEditTextValue(etChatPrompt));
    }

    private void restoreConversation(Bundle savedInstanceState) {
        Object savedMessages = savedInstanceState.getSerializable(STATE_MESSAGES);
        if (savedMessages instanceof ArrayList<?>) {
            for (Object item : (ArrayList<?>) savedMessages) {
                if (item instanceof ChatMessage) {
                    conversation.add((ChatMessage) item);
                }
            }
        }
        renderConversation();
    }

    private void submitPrompt() {
        String prompt = getEditTextValue(etChatPrompt).trim();
        if (prompt.isEmpty()) {
            etChatPrompt.requestFocus();
            etChatPrompt.setError(getString(R.string.chat_prompt_required));
            Toast.makeText(this, R.string.chat_prompt_required, Toast.LENGTH_SHORT).show();
            return;
        }
        sendMessage(prompt);
    }

    private void sendMessage(String prompt) {
        if (isSending) {
            return;
        }

        String apiKey = preferencesManager.getApiKey().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.error_api_key_required, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etChatPrompt.setError(null);
        etChatPrompt.setText("");

        ChatMessage userMessage = ChatMessage.user(prompt);
        conversation.add(userMessage);
        appendMessageBubble(userMessage);
        showEmptyState(false);
        setSending(true, getString(R.string.chat_status_sending));

        executorService.execute(() -> {
            try {
                String reply = geminiQuizService.sendChatMessage(
                        apiKey,
                        new ArrayList<>(conversation),
                        status -> runOnUiThread(() -> tvChatStatus.setText(status))
                );

                runOnUiThread(() -> {
                    ChatMessage modelMessage = ChatMessage.model(reply);
                    conversation.add(modelMessage);
                    appendMessageBubble(modelMessage);
                    setSending(false, getString(R.string.chat_status_done));
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    String errorMessage = exception.getMessage() == null
                            ? getString(R.string.chat_error_unknown)
                            : exception.getMessage();
                    setSending(false, getString(R.string.chat_error_prefix, errorMessage));
                    Toast.makeText(
                            ChatActivity.this,
                            getString(R.string.chat_error_prefix, errorMessage),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void renderConversation() {
        llConversationMessages.removeAllViews();
        for (ChatMessage message : conversation) {
            appendMessageBubble(message);
        }
        showEmptyState(conversation.isEmpty());
    }

    private void appendMessageBubble(ChatMessage message) {
        MaterialCardView cardView = new MaterialCardView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.topMargin = dpToPx(12);
        layoutParams.gravity = message.isUser() ? Gravity.END : Gravity.START;
        cardView.setLayoutParams(layoutParams);
        cardView.setCardElevation(0f);
        cardView.setRadius(dpToPxFloat(18));
        cardView.setStrokeWidth(message.isUser() ? 0 : dpToPx(1));
        cardView.setStrokeColor(ContextCompat.getColor(this, R.color.outline_soft));
        cardView.setCardBackgroundColor(ContextCompat.getColor(
                this,
                message.isUser() ? R.color.brand_primary : R.color.card_surface
        ));

        TextView textView = new TextView(this);
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int horizontalPadding = dpToPx(16);
        int verticalPadding = dpToPx(12);
        textView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        textView.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        textView.setText(message.getText());
        textView.setTextColor(ContextCompat.getColor(
                this,
                message.isUser() ? R.color.white : R.color.text_primary
        ));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setLineSpacing(0f, 1.15f);

        cardView.addView(textView);
        llConversationMessages.addView(cardView);
        scrollConversationToBottom();
    }

    private void setSending(boolean sending, String statusText) {
        isSending = sending;
        btnSendChat.setEnabled(!sending);
        etChatPrompt.setEnabled(!sending);
        btnSendChat.setText(sending ? R.string.chat_send_loading : R.string.chat_send);
        progressChat.setVisibility(sending ? View.VISIBLE : View.GONE);
        tvChatStatus.setText(statusText);
    }

    private void showEmptyState(boolean show) {
        tvChatEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void scrollConversationToBottom() {
        svConversation.post(() -> svConversation.fullScroll(View.FOCUS_DOWN));
    }

    private String getEditTextValue(TextInputEditText editText) {
        return editText == null || editText.getText() == null ? "" : editText.getText().toString();
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private float dpToPxFloat(int dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}

