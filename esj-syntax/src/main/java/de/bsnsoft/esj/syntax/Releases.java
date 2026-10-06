package de.bsnsoft.esj.syntax;

import java.util.Comparator;

/**
 * The order of the releases of one pack: {@code 3.0.9} before {@code 3.0.10} before
 * {@code 3.0.21}, and {@code 2026-08-31} before {@code 2026-10-15}.
 *
 * <p>A release is compared part by part, the parts split at {@code .} and {@code -}: two
 * parts of digits by their value, anything else by its characters, and a release that is
 * the beginning of another before it. The order is total, so the newest of several releases
 * is one release and never a choice.
 */
final class Releases {

    /** Oldest first. */
    static final Comparator<String> ORDER = Releases::compare;

    private Releases() {
        throw new AssertionError("no instances");
    }

    /** Compares two releases, oldest first. */
    static int compare(String left, String right) {
        String[] a = left.split("[.-]", -1);
        String[] b = right.split("[.-]", -1);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int order = part(a[i], b[i]);
            if (order != 0) {
                return order;
            }
        }
        int order = Integer.compare(a.length, b.length);
        return order != 0 ? order : left.compareTo(right);
    }

    private static int part(String a, String b) {
        boolean numbers = !a.isEmpty() && !b.isEmpty() && a.chars().allMatch(Releases::digit)
                && b.chars().allMatch(Releases::digit);
        if (numbers) {
            String x = a.replaceFirst("^0+(?=.)", "");
            String y = b.replaceFirst("^0+(?=.)", "");
            int order = Integer.compare(x.length(), y.length());
            return order != 0 ? order : x.compareTo(y);
        }
        return a.compareTo(b);
    }

    private static boolean digit(int c) {
        return c >= '0' && c <= '9';
    }
}
