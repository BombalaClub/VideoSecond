package cn.videosecond.gate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** The offline IHC grade-three training set, with separate practice and gate draws. */
final class QuestionBank {
    static final int QUESTION_COUNT = 100;
    private static final String[] ASSETS = {"olympiad_01.json", "olympiad_02.json", "olympiad_03.json"};
    private static final Random RANDOM = new Random();
    private static List<Question> questions;

    static final class Question {
        final int id;
        final String topic, prompt, unit, firstHint, secondHint;
        final String[] labels, options;
        final boolean diagram;
        private final String[] answers;

        Question(JSONObject row) throws JSONException {
            id = row.getInt("id");
            topic = row.getString("topic");
            prompt = row.getString("prompt");
            unit = row.optString("unit", "");
            diagram = row.optBoolean("diagram", false);
            answers = strings(row.getJSONArray("answers"));
            labels = strings(row.optJSONArray("labels"));
            options = strings(row.optJSONArray("options"));
            JSONArray hints = row.getJSONArray("hints");
            firstHint = hints.getString(0);
            secondHint = hints.getString(1);
        }

        int fieldCount() { return answers.length; }

        boolean accepts(String[] input) {
            return OlympiadAnswers.matches(id, answers, input);
        }
    }

    private static String[] strings(JSONArray array) throws JSONException {
        if (array == null) return new String[0];
        String[] values = new String[array.length()];
        for (int i = 0; i < values.length; i++) values[i] = array.getString(i);
        return values;
    }

    private static List<Question> load(Context context) {
        List<Question> result = new ArrayList<>(QUESTION_COUNT);
        try {
            for (String asset : ASSETS) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        context.getAssets().open(asset), StandardCharsets.UTF_8))) {
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line).append('\n');
                    JSONArray rows = new JSONArray(json.toString());
                    for (int i = 0; i < rows.length(); i++) {
                        Question q = new Question(rows.getJSONObject(i));
                        if (q.id != result.size() + 1) throw new JSONException("Unexpected IHC question ID " + q.id);
                        result.add(q);
                    }
                }
            }
            if (result.size() != QUESTION_COUNT) throw new JSONException("Expected " + QUESTION_COUNT + " IHC questions");
        } catch (IOException | JSONException ex) {
            throw new IllegalStateException("Unable to read the packaged IHC question bank", ex);
        }
        return result;
    }

    private static List<Question> questions(Context context) {
        if (questions == null) questions = load(context);
        return questions;
    }

    static synchronized int size(Context context) {
        return questions(context).size();
    }

    static synchronized Question forReview(Context context, int id) {
        if (!QuestionProgress.get(context, id).correct()) return null;
        return forPendingQuiz(context, id);
    }

    // Resume only an already-issued quiz from private saved instance state.
    static synchronized Question forPendingQuiz(Context context, int id) {
        if (id < 1 || id > QUESTION_COUNT) return null;
        return questions(context).get(id - 1);
    }

    static synchronized Question next(Context context, boolean preview) {
        List<Question> booklet = questions(context);
        Set<String> solved = QuestionProgress.correctIds(context);
        List<Question> pool = new ArrayList<>();
        for (Question q : booklet) {
            if (!solved.contains(Integer.toString(q.id))) pool.add(q);
        }
        boolean allCorrect = pool.isEmpty();
        if (allCorrect) pool = booklet;

        SharedPreferences prefs = context.getSharedPreferences(
                preview ? "ihc2027_practice" : "ihc2027_gate", Context.MODE_PRIVATE);
        int previous = prefs.getInt("last", 0);
        // Keep old draw history as exposure only. A later missed review can rejoin
        // the unfinished pool without resetting the all-correct review rotation.
        String seenKey = allCorrect ? "review_seen" : "seen";
        Set<String> seen = new HashSet<>(prefs.getStringSet(seenKey, Collections.emptySet()));
        List<Question> remaining = new ArrayList<>();
        for (Question q : pool) {
            if (!seen.contains(Integer.toString(q.id))) remaining.add(q);
        }
        if (remaining.isEmpty()) {
            seen.clear();
            remaining.addAll(pool);
        }
        if (remaining.size() > 1) remaining.removeIf(q -> q.id == previous);
        Question next;
        Question introductory = booklet.get((preview ? 38 : 43) - 1);
        if (previous == 0 && remaining.contains(introductory)) {
            next = introductory;
        } else {
            next = remaining.get(RANDOM.nextInt(remaining.size()));
        }
        seen.add(Integer.toString(next.id));
        prefs.edit().putStringSet(seenKey, seen).putInt("last", next.id).apply();
        return next;
    }

    private QuestionBank() {}
}
