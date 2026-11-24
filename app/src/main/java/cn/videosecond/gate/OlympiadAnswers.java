package cn.videosecond.gate;

import java.util.Arrays;

/** Grades whole-number fields and named choices, including equivalent layouts for problem 18. */
final class OlympiadAnswers {
    static boolean matches(int id, String[] expected, String[] given) {
        if (given == null || given.length != expected.length) return false;
        String[] values = new String[given.length];
        for (int i = 0; i < given.length; i++) {
            if (given[i] == null) return false;
            String value = given[i].trim();
            if (value.isEmpty()) return false;
            values[i] = value.replaceFirst("^0+(?=[0-9])", "");
        }
        if (id == 18) return fitsCross(values);
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].equalsIgnoreCase(values[i])) return false;
        }
        return true;
    }

    private static boolean fitsCross(String[] values) {
        if (values.length != 5 || !"9".equals(values[2])) return false;
        String[] numbers = values.clone();
        Arrays.sort(numbers);
        if (!Arrays.equals(numbers, new String[]{"3", "4", "5", "6", "9"})) return false;
        int vertical = Integer.parseInt(values[0]) + Integer.parseInt(values[4]);
        int horizontal = Integer.parseInt(values[1]) + Integer.parseInt(values[3]);
        return vertical == 9 && horizontal == 9;
    }

    private OlympiadAnswers() {}
}
