package net.harbroi.quizgenerator;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class FlashQuestionActivity extends AppCompatActivity {

    public static final String EXTRA_FLASH_CARDS = "extra_flash_cards";

    private final ArrayList<FlashCard> flashCards = new ArrayList<>();

    private int currentIndex = 0;
    private boolean isAnswerRevealed = false;

    private TextView tvCardCounter;
    private TextView tvQuestion;
    private TextView tvAnswer;
    private MaterialButton btnRevealAnswer;
    private MaterialButton btnNextAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash_question);

        MaterialButton btnBackToFlashSetup = findViewById(R.id.btnBackToFlashSetup);
        tvCardCounter = findViewById(R.id.tvCardCounter);
        tvQuestion = findViewById(R.id.tvFlashQuestion);
        tvAnswer = findViewById(R.id.tvFlashAnswer);
        btnRevealAnswer = findViewById(R.id.btnRevealAnswer);
        btnNextAction = findViewById(R.id.btnNextAction);

        flashCards.addAll(getFlashCardsFromIntent());
        if (flashCards.isEmpty()) {
            Toast.makeText(this, R.string.error_no_flash_cards_generated, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnBackToFlashSetup.setOnClickListener(v -> finish());
        btnRevealAnswer.setOnClickListener(v -> revealAnswer());
        btnNextAction.setOnClickListener(v -> handleNextAction());

        renderCurrentCard();
    }

    @SuppressWarnings("unchecked")
    private ArrayList<FlashCard> getFlashCardsFromIntent() {
        Object extra;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extra = getIntent().getSerializableExtra(EXTRA_FLASH_CARDS, ArrayList.class);
        } else {
            extra = getIntent().getSerializableExtra(EXTRA_FLASH_CARDS);
        }

        if (extra instanceof ArrayList<?>) {
            return (ArrayList<FlashCard>) extra;
        }
        return new ArrayList<>();
    }

    private void renderCurrentCard() {
        FlashCard flashCard = flashCards.get(currentIndex);

        isAnswerRevealed = false;
        tvCardCounter.setText(getString(R.string.flash_card_progress, currentIndex + 1, flashCards.size()));
        tvQuestion.setText(flashCard.getQuestion());
        tvAnswer.setText(flashCard.getAnswer());
        tvAnswer.setVisibility(View.GONE);

        btnRevealAnswer.setVisibility(View.VISIBLE);
        btnRevealAnswer.setEnabled(true);
        btnNextAction.setVisibility(View.GONE);
    }

    private void revealAnswer() {
        if (isAnswerRevealed) {
            return;
        }

        isAnswerRevealed = true;
        tvAnswer.setVisibility(View.VISIBLE);
        btnRevealAnswer.setEnabled(false);
        btnRevealAnswer.setVisibility(View.GONE);

        boolean isLastCard = currentIndex == flashCards.size() - 1;
        btnNextAction.setVisibility(View.VISIBLE);
        btnNextAction.setText(isLastCard ? R.string.flash_back_to_activity : R.string.flash_next_question);
    }

    private void handleNextAction() {
        if (!isAnswerRevealed) {
            return;
        }

        boolean isLastCard = currentIndex == flashCards.size() - 1;
        if (isLastCard) {
            Intent intent = new Intent(this, FlashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return;
        }

        currentIndex++;
        renderCurrentCard();
    }
}

