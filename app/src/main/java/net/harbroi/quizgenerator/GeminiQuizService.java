package net.harbroi.quizgenerator;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GeminiQuizService {

    public interface StatusListener {
        void onStatus(String message);
    }

    private static final String[] MODEL_NAMES = {
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite-preview"
    };
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 90_000;
    private static final Random RANDOM = new Random();

    public ArrayList<QuizQuestion> generateQuiz(
            String apiKey,
            int questionCount,
            List<Uri> fileUris,
            ContentResolver contentResolver,
            StatusListener statusListener
    ) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API key is required.");
        }
        if (fileUris == null || fileUris.isEmpty()) {
            throw new IOException("At least one file is required.");
        }

        if (statusListener != null) {
            statusListener.onStatus("Preparing files and prompt…");
        }

        String requestJson = buildRequestJson(buildPrompt(questionCount), fileUris, contentResolver);

        for (String modelName : MODEL_NAMES) {
            if (statusListener != null) {
                statusListener.onStatus("Generating questions…");
            }

            ApiResponse response = executeWithRetry(apiKey, modelName, requestJson);
            if (response.isSuccessful()) {
                String output = extractResponseText(response.body);
                ArrayList<QuizQuestion> questions = parseQuizQuestions(output);
                if (!questions.isEmpty()) {
                    return questions;
                }
                throw new IOException("No quiz questions were generated.");
            }

            if (!response.shouldRetrySameModel()) {
                throw new IOException("Error: " + response.body);
            }
        }

        throw new IOException("All models failed or are currently unavailable.");
    }

    public ArrayList<FlashCard> generateFlashCards(
            String apiKey,
            int cardCount,
            List<Uri> fileUris,
            ContentResolver contentResolver,
            StatusListener statusListener
    ) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API key is required.");
        }
        if (fileUris == null || fileUris.isEmpty()) {
            throw new IOException("At least one file is required.");
        }

        if (statusListener != null) {
            statusListener.onStatus("Preparing files and prompt...");
        }

        String requestJson = buildRequestJson(buildFlashPrompt(cardCount), fileUris, contentResolver);

        for (String modelName : MODEL_NAMES) {
            if (statusListener != null) {
                statusListener.onStatus("Generating flash cards...");
            }

            ApiResponse response = executeWithRetry(apiKey, modelName, requestJson);
            if (response.isSuccessful()) {
                String output = extractResponseText(response.body);
                ArrayList<FlashCard> cards = parseFlashCards(output);
                if (!cards.isEmpty()) {
                    return cards;
                }
                throw new IOException("No flash cards were generated.");
            }

            if (!response.shouldRetrySameModel()) {
                throw new IOException("Error: " + response.body);
            }
        }

        throw new IOException("All models failed or are currently unavailable.");
    }

    public String sendChatMessage(
            String apiKey,
            List<net.harbroi.quizgenerator.ChatMessage> conversation,
            StatusListener statusListener
    ) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API key is required.");
        }
        if (conversation == null || conversation.isEmpty()) {
            throw new IOException("Please enter a prompt first.");
        }

        if (statusListener != null) {
            statusListener.onStatus("Preparing chat request...");
        }

        String requestJson = buildChatRequestJson(conversation);

        for (String modelName : MODEL_NAMES) {
            if (statusListener != null) {
                statusListener.onStatus("Waiting for HIQuiz AI response...");
            }

            ApiResponse response = executeWithRetry(apiKey, modelName, requestJson);
            if (response.isSuccessful()) {
                String output = extractResponseText(response.body).trim();
                if (!output.isEmpty()) {
                    return output;
                }
                throw new IOException("HIQuiz AI returned an empty response.");
            }

            if (!response.shouldRetrySameModel()) {
                throw new IOException("Error: " + response.body);
            }
        }

        throw new IOException("All models failed or are currently unavailable.");
    }

    private String buildRequestJson(String prompt, List<Uri> fileUris, ContentResolver contentResolver) throws IOException {
        try {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));

            for (Uri uri : fileUris) {
                String displayName = getDisplayName(uri, contentResolver);
                String mimeType = resolveMimeType(uri, contentResolver);

                if (isDocxFile(displayName, mimeType) || isPptxFile(displayName, mimeType)) {
                    String extractedText = isPptxFile(displayName, mimeType)
                            ? extractTextFromPptx(uri, contentResolver).trim()
                            : extractTextFromDocx(uri, contentResolver).trim();
                    if (extractedText.isEmpty()) {
                        throw new IOException("Could not extract text from file: " + displayName);
                    }

                    parts.put(new JSONObject().put(
                            "text",
                            "Study content from \"" + displayName + "\":\n" + extractedText
                    ));
                } else {
                    JSONObject inlineData = new JSONObject()
                            .put("mime_type", mimeType)
                            .put("data", readFileAsBase64(uri, contentResolver));
                    parts.put(new JSONObject().put("inline_data", inlineData));
                }
            }

            JSONObject contents = new JSONObject().put("parts", parts);
            JSONObject generationConfig = new JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json");

            return new JSONObject()
                    .put("contents", new JSONArray().put(contents))
                    .put("generationConfig", generationConfig)
                    .toString();
        } catch (JSONException exception) {
            throw new IOException("Failed to build the request.", exception);
        }
    }

    private String buildChatRequestJson(List<net.harbroi.quizgenerator.ChatMessage> conversation) throws IOException {
        try {
            JSONArray contents = new JSONArray();
            for (net.harbroi.quizgenerator.ChatMessage message : conversation) {
                if (message == null || message.getText().trim().isEmpty()) {
                    continue;
                }

                JSONArray parts = new JSONArray()
                        .put(new JSONObject().put("text", message.getText()));

                contents.put(new JSONObject()
                        .put("role", message.isUser() ? "user" : "model")
                        .put("parts", parts));
            }

            if (contents.length() == 0) {
                throw new IOException("Please enter a prompt first.");
            }

            JSONObject generationConfig = new JSONObject()
                    .put("temperature", 0.7)
                    .put("responseMimeType", "text/plain");

            return new JSONObject()
                    .put("contents", contents)
                    .put("generationConfig", generationConfig)
                    .toString();
        } catch (JSONException exception) {
            throw new IOException("Failed to build the chat request.", exception);
        }
    }

    private String buildPrompt(int questionCount) {
        return "Create exactly " + questionCount + " multiple-choice quiz questions based strictly on the attached study materials. "
                + "Distribute the questions as evenly as possible across all provided files."
                + "Convert each statement into a question form, while preserving the original wording as much as possible."
                + "Example:\n" +
                "- Statement: \"A database is an organized collection of data that can be easily accessed, managed, and updated.\"\n" +
                "- Question: \"Which of the following is an organized collection of data that can be easily accessed, managed, and updated?\"\n"
                + "Ensure that:\n" +
                "- Only one correct answer is included per question\n" +
                "- Distractors (wrong choices) are relevant and plausible "
                + "Return only valid JSON with this shape: {\"questions\":[{\"question\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"answerIndex\":0}]}. "
                + "Rules: exactly 4 options per question, randomize correct answer (A, B, C, or D), only one correct answer, answerIndex must be zero-based, no markdown fences, no extra commentary, and keep the wording concise and classroom-appropriate.";
    }

    private String buildFlashPrompt(int cardCount) {
        return "Create exactly " + cardCount + " identification flash cards based strictly on the attached study materials. "
                + "Each flash card must contain one concise question and one short answer. "
                + "The answer should be a keyword, name, date, formula, or short phrase only (prefer 1-6 words). "
                + "Do not use full-sentence answers unless absolutely necessary. "
                + "Avoid multiple-choice formatting and avoid long explanations. "
                + "Return only valid JSON with this shape: {\"flashCards\":[{\"question\":\"...\",\"answer\":\"...\"}]}. "
                + "No markdown fences and no extra commentary.";
    }

    private ApiResponse executeWithRetry(String apiKey, String modelName, String requestJson) throws IOException {
        ApiResponse lastResponse = null;

        int attempt = 1;
        while (attempt <= MAX_RETRY_ATTEMPTS) {
            lastResponse = postRequest(apiKey, modelName, requestJson);
            if (!lastResponse.shouldRetrySameModel() || attempt == MAX_RETRY_ATTEMPTS) {
                return lastResponse;
            }

            long backoffMillis = (long) Math.pow(2, attempt) * 1000L + RANDOM.nextInt(1000);
            try {
                Thread.sleep(backoffMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Generation was interrupted.", exception);
            }

            attempt++;
        }

        return lastResponse == null ? new ApiResponse(500, "Unknown error") : lastResponse;
    }

    private ApiResponse postRequest(String apiKey, String modelName, String requestJson) throws IOException {
        HttpURLConnection connection = null;
        try {
            String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                    + modelName + ":generateContent?key=" + encodedApiKey);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestJson.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = stream == null ? "" : readStream(stream);
            return new ApiResponse(responseCode, responseBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractResponseText(String responseBody) throws IOException {
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new IOException("No candidates were returned.");
            }

            JSONObject firstCandidate = candidates.optJSONObject(0);
            if (firstCandidate == null) {
                throw new IOException("HIQuiz returned an invalid candidate payload.");
            }

            JSONObject content = firstCandidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            JSONObject firstPart = parts == null ? null : parts.optJSONObject(0);
            String text = (firstPart == null || !firstPart.has("text"))
                    ? null
                    : firstPart.optString("text", "");
            if (text == null || text.trim().isEmpty()) {
                throw new IOException("HIQuiz did not return a text response.");
            }
            return text;
        } catch (JSONException exception) {
            throw new IOException("Failed to parse the response.", exception);
        }
    }

    private ArrayList<QuizQuestion> parseQuizQuestions(String rawOutput) throws IOException {
        try {
            String jsonPayload = sanitizeJson(rawOutput);
            Object parsed = new JSONTokener(jsonPayload).nextValue();
            JSONArray questionsArray;

            if (parsed instanceof JSONObject) {
                questionsArray = ((JSONObject) parsed).optJSONArray("questions");
            } else if (parsed instanceof JSONArray) {
                questionsArray = (JSONArray) parsed;
            } else {
                questionsArray = null;
            }

            if (questionsArray == null || questionsArray.length() == 0) {
                return new ArrayList<>();
            }

            ArrayList<QuizQuestion> questions = new ArrayList<>();
            for (int index = 0; index < questionsArray.length(); index++) {
                JSONObject item = questionsArray.optJSONObject(index);
                if (item == null) {
                    continue;
                }

                String questionText = item.optString("question", "").trim();
                JSONArray optionsArray = item.optJSONArray("options");
                if (questionText.isEmpty() || optionsArray == null || optionsArray.length() < 2) {
                    continue;
                }

                ArrayList<String> options = new ArrayList<>();
                for (int optionIndex = 0; optionIndex < optionsArray.length(); optionIndex++) {
                    String optionText = optionsArray.optString(optionIndex, "").trim();
                    if (!optionText.isEmpty()) {
                        options.add(optionText);
                    }
                }

                if (options.size() < 2) {
                    continue;
                }

                int answerIndex = item.optInt("answerIndex", -1);
                if (answerIndex < 0 || answerIndex >= options.size()) {
                    answerIndex = -1;
                }

                questions.add(new QuizQuestion(questionText, options, answerIndex));
            }
            return questions;
        } catch (JSONException exception) {
            throw new IOException("The generated quiz format was invalid.", exception);
        }
    }

    private ArrayList<FlashCard> parseFlashCards(String rawOutput) throws IOException {
        try {
            String jsonPayload = sanitizeJson(rawOutput);
            Object parsed = new JSONTokener(jsonPayload).nextValue();
            JSONArray cardsArray;

            if (parsed instanceof JSONObject) {
                JSONObject root = (JSONObject) parsed;
                cardsArray = root.optJSONArray("flashCards");
                if (cardsArray == null) {
                    cardsArray = root.optJSONArray("cards");
                }
            } else if (parsed instanceof JSONArray) {
                cardsArray = (JSONArray) parsed;
            } else {
                cardsArray = null;
            }

            if (cardsArray == null || cardsArray.length() == 0) {
                return new ArrayList<>();
            }

            ArrayList<FlashCard> cards = new ArrayList<>();
            for (int index = 0; index < cardsArray.length(); index++) {
                JSONObject item = cardsArray.optJSONObject(index);
                if (item == null) {
                    continue;
                }

                String questionText = item.optString("question", "").trim();
                String answerText = item.optString("answer", "").trim();
                if (questionText.isEmpty() || answerText.isEmpty()) {
                    continue;
                }

                cards.add(new FlashCard(questionText, answerText));
            }
            return cards;
        } catch (JSONException exception) {
            throw new IOException("The generated flash card format was invalid.", exception);
        }
    }

    private String sanitizeJson(String rawOutput) {
        String cleaned = rawOutput == null ? "" : rawOutput.trim();
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }

        int objectStart = cleaned.indexOf('{');
        int objectEnd = cleaned.lastIndexOf('}');
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');

        if (objectStart >= 0 && objectEnd > objectStart) {
            return cleaned.substring(objectStart, objectEnd + 1);
        }
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return cleaned.substring(arrayStart, arrayEnd + 1);
        }
        return cleaned;
    }

    private String resolveMimeType(Uri uri, ContentResolver contentResolver) {
        String mimeType = contentResolver.getType(uri);
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            return mimeType;
        }

        String displayName = getDisplayName(uri, contentResolver).toLowerCase(Locale.US);
        if (displayName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (displayName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (displayName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }

        String extension = MimeTypeMap.getFileExtensionFromUrl(displayName);
        if (extension != null) {
            String guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (guessed != null && !guessed.trim().isEmpty()) {
                return guessed;
            }
        }
        return "application/octet-stream";
    }

    private String readFileAsBase64(Uri uri, ContentResolver contentResolver) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException("Could not open file: " + uri);
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
        }
    }

    private boolean isDocxFile(String displayName, String mimeType) {
        String normalizedName = displayName == null ? "" : displayName.toLowerCase(Locale.US);
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        return normalizedName.endsWith(".docx")
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(normalizedMime);
    }

    private boolean isPptxFile(String displayName, String mimeType) {
        String normalizedName = displayName == null ? "" : displayName.toLowerCase(Locale.US);
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        return normalizedName.endsWith(".pptx")
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(normalizedMime);
    }

    private String extractTextFromDocx(Uri uri, ContentResolver contentResolver) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             ZipInputStream zipInputStream = inputStream == null ? null : new ZipInputStream(inputStream)) {
            if (zipInputStream == null) {
                throw new IOException("Could not open DOCX file: " + uri);
            }

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = readXmlFromZipEntry(zipInputStream);
                    return xmlToPlainText(xml);
                }
            }
        }

        throw new IOException("DOCX content was not found.");
    }

    private String extractTextFromPptx(Uri uri, ContentResolver contentResolver) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             ZipInputStream zipInputStream = inputStream == null ? null : new ZipInputStream(inputStream)) {
            if (zipInputStream == null) {
                throw new IOException("Could not open PPTX file: " + uri);
            }

            Map<Integer, String> slideTextByIndex = new TreeMap<>();
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!isPptxSlideEntry(entryName)) {
                    continue;
                }

                int slideIndex = parseSlideIndex(entryName);
                String xml = readXmlFromZipEntry(zipInputStream);
                String text = xmlToPlainText(xml);
                if (!text.isEmpty()) {
                    slideTextByIndex.put(slideIndex, text);
                }
            }

            if (slideTextByIndex.isEmpty()) {
                throw new IOException("PPTX slide content was not found.");
            }

            StringBuilder builder = new StringBuilder();
            for (String slideText : slideTextByIndex.values()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(slideText);
            }
            return builder.toString();
        }
    }

    private boolean isPptxSlideEntry(String entryName) {
        return entryName != null
                && entryName.startsWith("ppt/slides/slide")
                && entryName.endsWith(".xml");
    }

    private int parseSlideIndex(String entryName) {
        int start = "ppt/slides/slide".length();
        int end = entryName.lastIndexOf(".xml");
        if (end <= start) {
            return Integer.MAX_VALUE;
        }

        try {
            return Integer.parseInt(entryName.substring(start, end));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private String readXmlFromZipEntry(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    private String xmlToPlainText(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return "";
        }

        String text = xml
                .replaceAll("</(?:w|a):p>", "\n")
                .replaceAll("<(?:w|a):br[^>]*/>", "\n")
                .replaceAll("<(?:w|a):tab[^>]*/>", "\t")
                .replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#10;", "\n");

        text = text.replaceAll("[\\t ]+", " ");
        text = text.replaceAll("\\s*\\n\\s*", "\n");
        return text.trim();
    }

    private String getDisplayName(Uri uri, ContentResolver contentResolver) {
        try (android.database.Cursor cursor = contentResolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
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
        return fallback == null ? uri.toString() : fallback;
    }

    private String readStream(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
        }
        return builder.toString();
    }

    private static class ApiResponse {
        private final int statusCode;
        private final String body;

        private ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        private boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }

        private boolean shouldRetrySameModel() {
            return statusCode == HttpURLConnection.HTTP_UNAVAILABLE || statusCode == 429;
        }
    }
}

