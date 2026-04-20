package net.harbroi.quizgenerator;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class SummaryActivity extends AppCompatActivity {

    public static final String EXTRA_TOTAL_COUNT = "extra_total_count";
    public static final String EXTRA_CORRECT_COUNT = "extra_correct_count";
    public static final String EXTRA_WRONG_COUNT = "extra_wrong_count";
    public static final String EXTRA_PERCENTAGE = "extra_percentage";
    public static final String EXTRA_QUESTIONS = "extra_questions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        TextView tvResultSummary = findViewById(R.id.tvResultSummary);
        TextView tvResultBreakdown = findViewById(R.id.tvResultBreakdown);
        LinearLayout llSummaryQuestions = findViewById(R.id.llSummaryQuestions);
        MaterialButton btnBackToMain = findViewById(R.id.btnBackToMain);

        int totalCount = getIntent().getIntExtra(EXTRA_TOTAL_COUNT, 0);
        int correctCount = getIntent().getIntExtra(EXTRA_CORRECT_COUNT, 0);
        int wrongCount = getIntent().getIntExtra(EXTRA_WRONG_COUNT, 0);
        int percentage = getIntent().getIntExtra(EXTRA_PERCENTAGE, 0);

        tvResultSummary.setText(getString(
                R.string.quiz_result_summary,
                correctCount,
                totalCount,
                percentage
        ));
        tvResultBreakdown.setText(getString(
                R.string.quiz_result_breakdown,
                correctCount,
                wrongCount
        ));

        renderQuestionReview(llSummaryQuestions, getQuizQuestions());

        btnBackToMain.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    @SuppressWarnings("unchecked")
    private ArrayList<QuizQuestion> getQuizQuestions() {
        Object extra;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extra = getIntent().getSerializableExtra(EXTRA_QUESTIONS, ArrayList.class);
        } else {
            extra = getIntent().getSerializableExtra(EXTRA_QUESTIONS);
        }

        if (extra instanceof ArrayList<?>) {
            return (ArrayList<QuizQuestion>) extra;
        }
        return new ArrayList<>();
    }

    private void renderQuestionReview(LinearLayout container, ArrayList<QuizQuestion> questions) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int index = 0; index < questions.size(); index++) {
            QuizQuestion question = questions.get(index);
            View item = inflater.inflate(R.layout.item_summary_question, container, false);

            TextView tvNumber = item.findViewById(R.id.tvSummaryQuestionNumber);
            TextView tvQuestion = item.findViewById(R.id.tvSummaryQuestionText);
            TextView tvStatus = item.findViewById(R.id.tvSummaryAnswerStatus);
            TextView tvUser = item.findViewById(R.id.tvSummaryUserAnswer);
            TextView tvCorrect = item.findViewById(R.id.tvSummaryCorrectAnswer);

            int selectedIndex = question.getSelectedOptionIndex();
            int correctIndex = question.getCorrectAnswerIndex();
            ArrayList<String> options = question.getOptions();

            String selectedAnswer = getOptionText(options, selectedIndex, getString(R.string.quiz_no_answer));
            String correctAnswer = getOptionText(options, correctIndex, getString(R.string.quiz_no_answer));
            boolean isCorrect = correctIndex >= 0 && selectedIndex == correctIndex;

            tvNumber.setText(getString(R.string.quiz_question_number, index + 1));
            tvQuestion.setText(question.getQuestionText());
            tvStatus.setText(isCorrect ? R.string.quiz_status_correct : R.string.quiz_status_wrong);
            tvStatus.setTextColor(getColor(isCorrect ? R.color.status_success : R.color.status_error));
            tvUser.setText(getString(R.string.summary_user_answer, selectedAnswer));
            tvCorrect.setText(getString(R.string.summary_correct_answer, correctAnswer));

            container.addView(item);
        }
    }

    private String getOptionText(ArrayList<String> options, int index, String fallback) {
        if (index >= 0 && index < options.size()) {
            return options.get(index);
        }
        return fallback;
    }
}

