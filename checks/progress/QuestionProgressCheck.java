package cn.videosecond.gate;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public final class QuestionProgressCheck {
    private static final Set<Integer> UNFINISHED = Set.of(18, 62, 75);

    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }

    private static void status(Context context, int id, int right, int wrong,
                               boolean latestCorrect, long lastSuccessMillis) {
        QuestionProgress.Entry entry = QuestionProgress.get(context, id);
        check(entry.correctCount == right && entry.wrongCount == wrong, "submission counts, Q" + id);
        check(entry.attempted() == (right + wrong > 0), "attempted, Q" + id);
        check(entry.correct() == latestCorrect, "latest result, Q" + id);
        check(entry.lastCorrectMillis == lastSuccessMillis, "latest successful duration, Q" + id);
        int writes = SharedPreferences.writes;
        QuestionBank.Question detail = QuestionBank.forReview(context, id);
        check((detail != null) == latestCorrect, "detail access, Q" + id);
        if (detail != null) check(detail.id == id && !detail.prompt.isEmpty(), "original question, Q" + id);
        check(SharedPreferences.writes == writes, "review changed persisted state, Q" + id);
    }

    private static void oldInstallation(Context context) {
        for (String name : new String[]{"ihc2027_gate", "ihc2027_practice"}) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                    .putStringSet("seen", Set.of("7", "11", "18", "62", "75")).putInt("last", 11).apply();
        }
    }

    private static void upgrade(Context context) {
        check(QuestionBank.size(context) == 100, "all 100 numbers listed");
        for (int id = 1; id <= 100; id++) status(context, id, 0, 0, false, 0L);
        check(QuestionBank.forReview(context, 0) == null && QuestionBank.forReview(context, 101) == null,
                "invalid numbers must be locked");
        for (boolean preview : new boolean[]{false, true}) {
            int drawn = QuestionBank.next(context, preview).id;
            check(drawn != 7 && drawn != 11 && !UNFINISHED.contains(drawn), "upgrade preserved exposure order");
            status(context, drawn, 0, 0, false, 0L);
        }
        QuestionProgress.record(context, 7, false, 1234L);
        status(context, 7, 0, 1, false, 0L);
        QuestionProgress.record(context, 7, true, 34987L);
        status(context, 7, 1, 1, true, 34987L);
    }

    private static void restart(Context context) {
        status(context, 7, 1, 1, true, 34987L);
        QuestionProgress.record(context, 7, false, 90000L);
        status(context, 7, 1, 2, false, 34987L);
        QuestionProgress.record(context, 7, true, 4321L);
        status(context, 7, 2, 2, true, 4321L);
        // After the upgrade, only two wrong questions and one never-answered question remain.
        for (int id = 1; id <= 100; id++) {
            if (id != 7 && !UNFINISHED.contains(id)) QuestionProgress.record(context, id, true, 12000L);
        }
        QuestionProgress.record(context, 18, false, 100L);
        QuestionProgress.record(context, 75, false, 100L);
    }

    private static void twoCycles(Context context, boolean preview, Set<Integer> pool) {
        int last = -1;
        for (int cycle = 0; cycle < 2; cycle++) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < pool.size(); i++) {
                int id = QuestionBank.next(context, preview).id;
                check(pool.contains(id) && seen.add(id), "repeated or ineligible Q" + id + ", mode=" + preview);
                check(last != id, "immediate repeat across cycles, Q" + id);
                last = id;
            }
        }
    }

    private static void review(Context context) {
        status(context, 7, 2, 2, true, 4321L);
        status(context, 18, 0, 1, false, 0L);
        status(context, 62, 0, 0, false, 0L);
        status(context, 75, 0, 1, false, 0L);
        twoCycles(context, false, UNFINISHED);
        twoCycles(context, true, UNFINISHED);
        QuestionProgress.record(context, 18, true, 7890L);
        QuestionBank.Question diagram = QuestionBank.forReview(context, 18);
        check(diagram != null && diagram.diagram && diagram.labels.length == 5, "correct multi-field diagram detail");
        QuestionProgress.record(context, 62, true, 321L);
        for (boolean preview : new boolean[]{false, true}) {
            for (int draw = 0; draw < 3; draw++) {
                check(QuestionBank.next(context, preview).id == 75, "single unfinished question must be reissued");
            }
        }
        status(context, 75, 0, 1, false, 0L);
        QuestionProgress.record(context, 75, true, 654L);
        Set<Integer> all = new HashSet<>();
        for (int id = 1; id <= 100; id++) all.add(id);
        twoCycles(context, false, all);
        twoCycles(context, true, all);
        QuestionProgress.record(context, 62, false, 10000L);
        status(context, 62, 1, 1, false, 321L);
        check(QuestionBank.next(context, false).id == 62, "gate returns a missed review to priority");
        check(QuestionBank.next(context, true).id == 62, "practice sees the latest gate result");
    }

    public static void main(String[] args) {
        Context context = new Context(Paths.get(args[1]), Paths.get(args[2]));
        switch (args[0]) {
            case "old-install": oldInstallation(context); break;
            case "upgrade": upgrade(context); break;
            case "restart": restart(context); break;
            case "review": review(context); break;
            default: throw new AssertionError("Unknown phase");
        }
    }
}
