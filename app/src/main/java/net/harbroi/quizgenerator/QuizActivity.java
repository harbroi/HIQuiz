package net.harbroi.quizgenerator;

import android.content.res.ColorStateList;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.ArrayList;

public class QuizActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_QUESTIONS = "extra_quiz_questions";

    private LinearLayout llQuestionsContainer;
    private MaterialButton btnSubmitQuiz;

    private ArrayList<QuizQuestion> questions = new ArrayList<>();
    private boolean isSubmitted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        llQuestionsContainer = findViewById(R.id.llQuestionsContainer);
        TextView tvQuizSummary = findViewById(R.id.tvQuizSummary);
        btnSubmitQuiz = findViewById(R.id.btnSubmitQuiz);

        questions = getQuizQuestions();
        if (questions.isEmpty()) {
            Toast.makeText(this, R.string.quiz_empty_state, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvQuizSummary.setText(getString(R.string.quiz_summary, questions.size()));
        renderQuestions(questions);

        btnSubmitQuiz.setOnClickListener(v -> submitQuiz());
    }

    @SuppressWarnings("unchecked")
    private ArrayList<QuizQuestion> getQuizQuestions() {
        Object extra;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extra = getIntent().getSerializableExtra(EXTRA_QUIZ_QUESTIONS, ArrayList.class);
        } else {
            extra = getIntent().getSerializableExtra(EXTRA_QUIZ_QUESTIONS);
        }
        if (extra instanceof ArrayList<?>) {
            return (ArrayList<QuizQuestion>) extra;
        }
        return new ArrayList<>();
    }

    private void renderQuestions(ArrayList<QuizQuestion> questions) {
        LayoutInflater inflater = LayoutInflater.from(this);
        llQuestionsContainer.removeAllViews();

        for (int index = 0; index < questions.size(); index++) {
            QuizQuestion question = questions.get(index);
            View questionView = inflater.inflate(R.layout.item_quiz_question, llQuestionsContainer, false);

            TextView tvQuestionNumber = questionView.findViewById(R.id.tvQuestionNumber);
            TextView tvQuestionText = questionView.findViewById(R.id.tvQuestionText);
            RadioGroup rgOptions = questionView.findViewById(R.id.rgOptions);
            TextView tvAnswerStatus = questionView.findViewById(R.id.tvAnswerStatus);
            TextView tvAnswerDetails = questionView.findViewById(R.id.tvAnswerDetails);

            tvQuestionNumber.setText(getString(R.string.quiz_question_number, index + 1));
            tvQuestionText.setText(question.getQuestionText());

            addOptionButtons(rgOptions, question, isSubmitted);
            bindQuestionFeedback(question, tvAnswerStatus, tvAnswerDetails, isSubmitted);
            llQuestionsContainer.addView(questionView);
        }
    }

    private void addOptionButtons(RadioGroup radioGroup, QuizQuestion question, boolean submitted) {
        radioGroup.removeAllViews();
        ArrayList<String> options = question.getOptions();
        int selectedIndex = question.getSelectedOptionIndex();
        int correctIndex = question.getCorrectAnswerIndex();

        for (int index = 0; index < options.size(); index++) {
            MaterialRadioButton radioButton = new MaterialRadioButton(this);
            radioButton.setId(View.generateViewId());
            radioButton.setText(options.get(index));
            radioButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            radioButton.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            radioButton.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_primary)));
            radioButton.setBackgroundResource(R.drawable.bg_quiz_option);
            radioButton.setPadding(dp(18), dp(18), dp(18), dp(18));
            radioButton.setMinHeight(dp(60));

            RadioGroup.LayoutParams layoutParams = new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index > 0) {
                layoutParams.topMargin = dp(12);
            }
            radioButton.setLayoutParams(layoutParams);

            final int optionIndex = index;
            if (!submitted) {
                radioButton.setOnClickListener(v -> question.setSelectedOptionIndex(optionIndex));
            }

            if (submitted) {
                radioButton.setEnabled(false);
                applyResultOptionStyle(radioButton, optionIndex, selectedIndex, correctIndex);
            }

            radioGroup.addView(radioButton);
            if (selectedIndex == index) {
                radioButton.setChecked(true);
            }
        }

        if (!submitted) {
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                for (int index = 0; index < group.getChildCount(); index++) {
                    View child = group.getChildAt(index);
                    if (child instanceof MaterialRadioButton) {
                        MaterialRadioButton radioButton = (MaterialRadioButton) child;
                        if (radioButton.getId() == checkedId) {
                            question.setSelectedOptionIndex(index);
                            break;
                        }
                    }
                }
            });
        }
    }

    private void bindQuestionFeedback(
            QuizQuestion question,
            TextView tvAnswerStatus,
            TextView tvAnswerDetails,
            boolean submitted
    ) {
        if (!submitted) {
            tvAnswerStatus.setVisibility(View.GONE);
            tvAnswerDetails.setVisibility(View.GONE);
            return;
        }

        int correctIndex = question.getCorrectAnswerIndex();
        int selectedIndex = question.getSelectedOptionIndex();
        ArrayList<String> options = question.getOptions();

        String correctAnswer = getOptionText(options, correctIndex, getString(R.string.quiz_no_answer));
        String selectedAnswer = getOptionText(options, selectedIndex, getString(R.string.quiz_no_answer));
        boolean isCorrect = correctIndex >= 0 && selectedIndex == correctIndex;

        tvAnswerStatus.setVisibility(View.VISIBLE);
        tvAnswerDetails.setVisibility(View.VISIBLE);
        tvAnswerStatus.setText(isCorrect ? R.string.quiz_status_correct : R.string.quiz_status_wrong);
        tvAnswerStatus.setTextColor(getColor(isCorrect ? R.color.status_success : R.color.status_error));

        if (isCorrect) {
            tvAnswerDetails.setText(getString(R.string.quiz_correct_answer_only, correctAnswer));
        } else {
            tvAnswerDetails.setText(getString(R.string.quiz_wrong_answer_details, selectedAnswer, correctAnswer));
        }
    }

    private void applyResultOptionStyle(
            MaterialRadioButton radioButton,
            int optionIndex,
            int selectedIndex,
            int correctIndex
    ) {
        if (optionIndex == correctIndex) {
            radioButton.setBackgroundResource(R.drawable.bg_quiz_option_correct);
        } else if (optionIndex == selectedIndex) {
            radioButton.setBackgroundResource(R.drawable.bg_quiz_option_wrong);
        } else {
            radioButton.setBackgroundResource(R.drawable.bg_quiz_option);
        }
    }

    private void submitQuiz() {
        if (isSubmitted) {
            return;
        }

        isSubmitted = true;
        ScoreResult scoreResult = calculateScore();

        btnSubmitQuiz.setEnabled(false);
        btnSubmitQuiz.setText(R.string.quiz_submitted);
        renderQuestions(questions);

        Intent summaryIntent = new Intent(this, SummaryActivity.class);
        summaryIntent.putExtra(SummaryActivity.EXTRA_TOTAL_COUNT, scoreResult.totalCount);
        summaryIntent.putExtra(SummaryActivity.EXTRA_CORRECT_COUNT, scoreResult.correctCount);
        summaryIntent.putExtra(SummaryActivity.EXTRA_WRONG_COUNT, scoreResult.wrongCount);
        summaryIntent.putExtra(SummaryActivity.EXTRA_PERCENTAGE, scoreResult.percentage);
        summaryIntent.putExtra(SummaryActivity.EXTRA_QUESTIONS, questions);
        startActivity(summaryIntent);
    }

    private ScoreResult calculateScore() {
        int total = questions.size();
        int correct = 0;

        for (QuizQuestion question : questions) {
            if (question.getSelectedOptionIndex() == question.getCorrectAnswerIndex()
                    && question.getCorrectAnswerIndex() >= 0) {
                correct++;
            }
        }

        int wrong = total - correct;
        int percentage = total == 0 ? 0 : Math.round((correct * 100f) / total);
        return new ScoreResult(total, correct, wrong, percentage);
    }


    private String getOptionText(ArrayList<String> options, int index, String fallback) {
        if (index >= 0 && index < options.size()) {
            return options.get(index);
        }
        return fallback;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private static class ScoreResult {
        private final int totalCount;
        private final int correctCount;
        private final int wrongCount;
        private final int percentage;

        private ScoreResult(int totalCount, int correctCount, int wrongCount, int percentage) {
            this.totalCount = totalCount;
            this.correctCount = correctCount;
            this.wrongCount = wrongCount;
            this.percentage = percentage;
        }
    }
}

