package cn.videosecond.gate;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Local submission results for this edition of the training booklet. */
final class QuestionProgress {
    private static final String FILE = "ihc2027_results";
    private static final String CORRECT_IDS = "latest_correct_ids";

    static final class Entry {
        final int correctCount;
        final int wrongCount;
        final long lastCorrectMillis;
        private final boolean latestCorrect;

        private Entry(int correctCount, int wrongCount, long lastCorrectMillis, boolean latestCorrect) {
            this.correctCount = correctCount;
            this.wrongCount = wrongCount;
            this.lastCorrectMillis = lastCorrectMillis;
            this.latestCorrect = latestCorrect;
        }

        boolean attempted() { return correctCount > 0 || wrongCount > 0; }
        boolean correct() { return latestCorrect; }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static synchronized Entry get(Context context, int id) {
        SharedPreferences prefs = preferences(context);
        String key = Integer.toString(id);
        return new Entry(prefs.getInt(key + ".correct", 0), prefs.getInt(key + ".wrong", 0),
                prefs.getLong(key + ".time", 0L),
                prefs.getStringSet(CORRECT_IDS, Collections.emptySet()).contains(key));
    }

    static synchronized Set<String> correctIds(Context context) {
        return new HashSet<>(preferences(context).getStringSet(CORRECT_IDS, Collections.emptySet()));
    }

    static synchronized void record(Context context, int id, boolean correct, long elapsedMillis) {
        if (id < 1 || id > QuestionBank.QUESTION_COUNT) {
            throw new IllegalArgumentException("Unknown IHC question " + id);
        }
        SharedPreferences prefs = preferences(context);
        Set<String> solved = new HashSet<>(prefs.getStringSet(CORRECT_IDS, Collections.emptySet()));
        String key = Integer.toString(id);
        String countKey = key + (correct ? ".correct" : ".wrong");
        SharedPreferences.Editor edit = prefs.edit().putInt(countKey, prefs.getInt(countKey, 0) + 1);
        if (correct) {
            solved.add(key);
            edit.putLong(key + ".time", Math.max(0L, elapsedMillis));
        } else {
            solved.remove(key);
        }
        edit.putStringSet(CORRECT_IDS, solved).apply();
    }

    private QuestionProgress() {}
}
