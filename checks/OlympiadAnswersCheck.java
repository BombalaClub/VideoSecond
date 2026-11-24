package cn.videosecond.gate;

import java.util.HashSet;
import java.util.Set;

public final class OlympiadAnswersCheck {
    private static void check(boolean pass, String reason) {
        if (!pass) throw new AssertionError(reason);
    }

    private static void checkCross() {
        String[] digits = {"3", "4", "5", "6", "9"};
        String[] sample = {"3", "4", "9", "5", "6"};
        int solutions = 0;
        // Enumerate all five positions, including arrangements with a reused digit.
        for (int arrangement = 0; arrangement < 3125; arrangement++) {
            int code = arrangement;
            int[] values = new int[5];
            String[] given = new String[5];
            Set<String> used = new HashSet<>();
            for (int i = 0; i < given.length; i++) {
                given[i] = digits[code % digits.length];
                values[i] = Integer.parseInt(given[i]);
                used.add(given[i]);
                code /= digits.length;
            }
            int circle = values[0] + values[1] + values[3] + values[4];
            boolean valid = used.size() == digits.length
                    && circle == values[0] + values[2] + values[4]
                    && circle == values[1] + values[2] + values[3];
            check(OlympiadAnswers.matches(18, sample, given) == valid,
                    "Q18: failed arrangement " + String.join(", ", given));
            if (valid) solutions++;
        }
        check(solutions == 8, "Q18: expected eight symmetric solutions");
        check(!OlympiadAnswers.matches(18, sample, new String[]{"0","1","2","1","2"}),
                "equal sums without the specified digits");
        check(!OlympiadAnswers.matches(18, sample, new String[]{"3","4","9","5"}),
                "an incomplete diagram cannot pass");
    }

    public static void main(String[] args) {
        checkCross();
        String[] fields = {"50", "2000", "12000"};
        check(OlympiadAnswers.matches(62, fields, new String[]{"050", "2000", "12000"}),
                "valid three-part answer with a leading zero");
        check(!OlympiadAnswers.matches(62, fields, new String[]{"50", "12000", "2000"}),
                "swapped area and earnings");
        check(!OlympiadAnswers.matches(62, fields, new String[]{"50", "2000", ""}),
                "blank final answer");
        check(!OlympiadAnswers.matches(75, new String[]{"43", "16"}, new String[]{"43"}),
                "missing child's age");
        String[] large = {"40060000"};
        check(OlympiadAnswers.matches(7, large, large), "eight-digit answer");
        for (String invalid : new String[]{"", "x", "4e7", "42949672960040060000", "40060001"}) {
            check(!OlympiadAnswers.matches(7, large, new String[]{invalid}),
                    "invalid numerical input " + invalid);
        }
        check(OlympiadAnswers.matches(31, new String[]{"C"}, new String[]{"C"}), "letter choice");
        check(!OlympiadAnswers.matches(31, new String[]{"C"}, new String[]{"B"}), "wrong choice");
        check(OlympiadAnswers.matches(52, new String[]{"甲"}, new String[]{"甲"}), "Chinese choice");
        System.out.println("OlympiadAnswersCheck passed");
    }
}
