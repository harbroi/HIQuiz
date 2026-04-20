package net.harbroi.quizgenerator;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String MIME_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_TYPE_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final int MAX_NAMES_TO_SHOW = 3;
    private static final int MIN_QUESTION_COUNT = 1;
    private static final int MAX_QUESTION_COUNT = 100;
    private static final String PREFS_NAME = "quiz_generator_prefs";
    private static final String PREF_API_KEY = "pref_api_key";

    private final List<Uri> selectedFileUris = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final GeminiQuizService geminiQuizService = new GeminiQuizService();

    private MaterialButton btnGenerateQuiz;
    private TextView tvSelectedFilesInfo;
    private TextView tvGenerationStatus;
    private TextInputEditText etQuestionCount;
    private LinearProgressIndicator progressGeneration;

    private final ActivityResultLauncher<String[]> pickDocumentsLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                selectedFileUris.clear();
                List<String> names = new ArrayList<>();
                Set<String> seenUris = new LinkedHashSet<>();

                for (Uri uri : uris) {
                    if (!isSupportedFile(uri)) {
                        continue;
                    }
                    if (!seenUris.add(uri.toString())) {
                        continue;
                    }
                    persistReadPermission(uri);
                    selectedFileUris.add(uri);
                    names.add(getDisplayName(uri));
                }

                updateSelectedFilesInfo(names);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialButton btnBackHome = findViewById(R.id.btnBackHome);
        MaterialButton btnUploadFiles = findViewById(R.id.btnUploadFiles);
        btnGenerateQuiz = findViewById(R.id.btnGenerateQuiz);
        tvSelectedFilesInfo = findViewById(R.id.tvSelectedFilesInfo);
        tvGenerationStatus = findViewById(R.id.tvGenerationStatus);
        etQuestionCount = findViewById(R.id.etQuestionCount);
        progressGeneration = findViewById(R.id.progressGeneration);

        tvSelectedFilesInfo.setText(R.string.upload_files_none_selected);
        tvGenerationStatus.setText(R.string.generation_status_idle);
        tvGenerationStatus.setVisibility(View.VISIBLE);

        btnUploadFiles.setOnClickListener(v -> pickDocumentsLauncher.launch(
                new String[]{MIME_TYPE_PDF, MIME_TYPE_DOCX, MIME_TYPE_PPTX}
        ));

        btnBackHome.setOnClickListener(v -> openHomeActivity());
        btnGenerateQuiz.setOnClickListener(v -> generateQuiz());
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }

    private void generateQuiz() {
        String apiKey = getSavedApiKey();
        if (selectedFileUris.isEmpty()) {
            showValidationError(R.string.error_select_files);
            return;
        }
        if (apiKey.isEmpty()) {
            showValidationError(R.string.error_api_key_required);
            openHomeActivity();
            return;
        }

        Integer questionCount = parseQuestionCount();
        if (questionCount == null) {
            return;
        }

        setGenerating(true, getString(R.string.generation_status_preparing), false);

        executorService.execute(() -> {
            try {
                ArrayList<QuizQuestion> questions = geminiQuizService.generateQuiz(
                        apiKey,
                        questionCount,
                        new ArrayList<>(selectedFileUris),
                        getContentResolver(),
                        status -> runOnUiThread(() -> setGenerating(true, status, false))
                );

                runOnUiThread(() -> {
                    setGenerating(false, getString(R.string.generation_status_success), false);
                    openQuizActivity(questions);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> setGenerating(false, exception.getMessage(), true));
            }
        });
    }

    private Integer parseQuestionCount() {
        String questionCountText = getTrimmedText(etQuestionCount);
        if (questionCountText.isEmpty()) {
            etQuestionCount.requestFocus();
            etQuestionCount.setError(getString(R.string.error_question_count_required));
            showValidationError(R.string.error_question_count_required);
            return null;
        }

        try {
            int questionCount = Integer.parseInt(questionCountText);
            if (questionCount < MIN_QUESTION_COUNT || questionCount > MAX_QUESTION_COUNT) {
                etQuestionCount.requestFocus();
                etQuestionCount.setError(getString(R.string.error_question_count_invalid));
                showValidationError(R.string.error_question_count_invalid);
                return null;
            }

            etQuestionCount.setError(null);
            return questionCount;
        } catch (NumberFormatException exception) {
            etQuestionCount.requestFocus();
            etQuestionCount.setError(getString(R.string.error_question_count_invalid));
            showValidationError(R.string.error_question_count_invalid);
            return null;
        }
    }

    private boolean isSupportedFile(Uri uri) {
        ContentResolver resolver = getContentResolver();
        String mimeType = resolver.getType(uri);

        if (MIME_TYPE_PDF.equals(mimeType) || MIME_TYPE_DOCX.equals(mimeType) || MIME_TYPE_PPTX.equals(mimeType)) {
            return true;
        }

        String displayName = getDisplayName(uri).toLowerCase(Locale.US);
        return displayName.endsWith(".pdf")
                || displayName.endsWith(".docx")
                || displayName.endsWith(".pptx");
    }

    private String getDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        }

        String fallback = uri.getLastPathSegment();
        return (fallback == null || fallback.trim().isEmpty()) ? uri.toString() : fallback;
    }

    private void updateSelectedFilesInfo(List<String> names) {
        if (selectedFileUris.isEmpty()) {
            tvSelectedFilesInfo.setText(R.string.upload_files_none_selected);
            return;
        }

        List<String> shownNames = names.subList(0, Math.min(MAX_NAMES_TO_SHOW, names.size()));
        String joined = String.join(", ", shownNames);
        if (names.size() > MAX_NAMES_TO_SHOW) {
            int remaining = names.size() - MAX_NAMES_TO_SHOW;
            joined = joined + ", " + getString(R.string.upload_files_more, remaining);
        }

        tvSelectedFilesInfo.setText(getString(R.string.upload_files_selected, selectedFileUris.size(), joined));
    }

    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers do not allow persistable permissions; current-session access is still fine.
        }
    }

    private void openQuizActivity(ArrayList<QuizQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            setGenerating(false, getString(R.string.error_no_questions_generated), true);
            return;
        }

        Intent intent = new Intent(this, QuizActivity.class);
        intent.putExtra(QuizActivity.EXTRA_QUIZ_QUESTIONS, questions);
        startActivity(intent);
    }

    private void setGenerating(boolean isGenerating, String status, boolean isError) {
        btnGenerateQuiz.setEnabled(!isGenerating);
        btnGenerateQuiz.setText(isGenerating ? R.string.generate_quiz_loading : R.string.generate_quiz);
        progressGeneration.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
        tvGenerationStatus.setVisibility(View.VISIBLE);
        tvGenerationStatus.setText(status);
        tvGenerationStatus.setTextColor(getColor(isError ? R.color.status_error : R.color.text_secondary));
    }

    private void showValidationError(int stringResId) {
        String message = getString(stringResId);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        setGenerating(false, message, true);
    }

    private String getTrimmedText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String getSavedApiKey() {
        String savedApiKey = getQuizPreferences().getString(PREF_API_KEY, "");
        return savedApiKey.trim();
    }

    private void openHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private SharedPreferences getQuizPreferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }
}

