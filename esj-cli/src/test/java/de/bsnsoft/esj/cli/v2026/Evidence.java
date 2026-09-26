package de.bsnsoft.esj.cli.v2026;

import de.bsnsoft.esj.cli.Oracle;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The ledger of {@code conformance/rules-2026/} as a tree, for the test of this package.
 *
 * <p>The file is read with the reader the mutation set is read with, so that the two
 * measurements of one pack agree on what a number in a file is before they compare any.
 */
final class Evidence {

    private Evidence() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns a JSON file of the repository as maps, lists and strings.
     *
     * @param resource the resource path, absolute
     * @return the tree
     */
    static Object json(String resource) {
        return Oracle.json(Oracle.bytes(resource));
    }

    /**
     * Returns a file of the repository as text.
     *
     * @param resource the resource path, absolute
     * @return its text, UTF-8
     */
    static String text(String resource) {
        return new String(Oracle.bytes(resource), StandardCharsets.UTF_8);
    }

    /**
     * Returns a member of an object.
     *
     * @param object the object
     * @param member its name
     * @return the value, or {@code null} where the object has no such member
     */
    static Object get(Object object, String member) {
        return ((Map<?, ?>) object).get(member);
    }

    /**
     * Returns a member of an object as a number.
     *
     * @param object the object
     * @param member its name
     * @return the value as an integer
     */
    static int number(Object object, String member) {
        return Integer.parseInt(String.valueOf(get(object, member)));
    }
}
