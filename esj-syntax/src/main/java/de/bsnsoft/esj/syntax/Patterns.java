package de.bsnsoft.esj.syntax;

import java.util.List;

/**
 * The profile patterns a manifest is written with.
 *
 * <p>A pattern is an exact customization identifier, an identifier followed by {@code *},
 * which matches any suffix, or {@code *} alone, which matches every profile. That is the
 * whole language, and it is deliberately not a regular expression: what a component or a
 * level table applies to has to be readable in a manifest by somebody checking whether
 * the right rules ran over their invoice.
 */
final class Patterns {

    private Patterns() {
        throw new AssertionError("no instances");
    }

    /**
     * Tells whether one of the patterns matches a customization identifier.
     *
     * @param patterns        the patterns, in manifest order
     * @param customizationId the identifier the document names in BT-24, empty where it
     *                        names none
     * @return whether one of them matches
     */
    static boolean matchProfile(List<String> patterns, String customizationId) {
        for (String pattern : patterns) {
            if (matches(pattern, customizationId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String customizationId) {
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith("*")) {
            return customizationId.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(customizationId);
    }
}
