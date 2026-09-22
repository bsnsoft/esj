package de.bsnsoft.esj.cli;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON value built in memory and written out deterministically.
 *
 * <p>The fixture manifest of {@code conformance/fixtures/} is generated from the
 * implementation and then compared with the file byte for byte, so the serialization has
 * to be a function of the value and of nothing else: member order is insertion order,
 * indentation is two spaces, and there is exactly one newline at the end. Jackson is on
 * this module's class path, but its pretty printer writes a layout that changed between
 * releases, and a fixture file that reformats itself on a dependency upgrade is a fixture
 * file nobody trusts.
 *
 * <p>Escaping follows the rule the specification, section 7.5 states for the canonical
 * form. The manifest is not a semantic document and does not have to be canonical JSON,
 * but there is no reason for a second escaping rule in one repository.
 */
abstract sealed class Manifest {

    private Manifest() {
    }

    /** Returns a string value. */
    static Manifest of(String value) {
        return new Text(value);
    }

    /** Returns a number value; the manifest carries counts and byte lengths only. */
    static Manifest of(long value) {
        return new Number(value);
    }

    /** Returns a boolean value. */
    static Manifest of(boolean value) {
        return new Flag(value);
    }

    /** Returns an empty object, whose members keep the order they are put in. */
    static Object object() {
        return new Object();
    }

    /** Returns an empty array. */
    static Array array() {
        return new Array();
    }

    /** Returns an array of strings. */
    static Array of(List<String> values) {
        Array array = new Array();
        for (String value : values) {
            array.add(of(value));
        }
        return array;
    }

    /**
     * Returns this value as the bytes of a JSON text, with a trailing newline.
     *
     * @return the bytes to write or to compare
     */
    final byte[] bytes() {
        StringBuilder out = new StringBuilder();
        write(out, 0);
        out.append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    abstract void write(StringBuilder out, int depth);

    /** A JSON object. */
    static final class Object extends Manifest {

        private final Map<String, Manifest> members = new LinkedHashMap<>();

        private Object() {
        }

        /** Adds a member, or replaces one of the same name in place. */
        Object put(String name, Manifest value) {
            members.put(name, value);
            return this;
        }

        /** Adds a string member. */
        Object put(String name, String value) {
            return put(name, of(value));
        }

        /** Adds a numeric member. */
        Object put(String name, long value) {
            return put(name, of(value));
        }

        /** Adds a boolean member. */
        Object put(String name, boolean value) {
            return put(name, of(value));
        }

        /** Adds a member unless the array is empty, so that a manifest carries no noise. */
        Object putUnlessEmpty(String name, Array value) {
            return value.elements.isEmpty() ? this : put(name, value);
        }

        @Override
        void write(StringBuilder out, int depth) {
            if (members.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            boolean first = true;
            for (Map.Entry<String, Manifest> member : members.entrySet()) {
                if (!first) {
                    out.append(",\n");
                }
                first = false;
                indent(out, depth + 1);
                escape(out, member.getKey());
                out.append(": ");
                member.getValue().write(out, depth + 1);
            }
            out.append('\n');
            indent(out, depth);
            out.append('}');
        }
    }

    /** A JSON array. */
    static final class Array extends Manifest {

        private final List<Manifest> elements = new ArrayList<>();

        private Array() {
        }

        /** Appends an element. */
        Array add(Manifest value) {
            elements.add(value);
            return this;
        }

        /** Returns how many elements the array holds. */
        int size() {
            return elements.size();
        }

        @Override
        void write(StringBuilder out, int depth) {
            if (elements.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            boolean first = true;
            for (Manifest element : elements) {
                if (!first) {
                    out.append(",\n");
                }
                first = false;
                indent(out, depth + 1);
                element.write(out, depth + 1);
            }
            out.append('\n');
            indent(out, depth);
            out.append(']');
        }
    }

    private static final class Text extends Manifest {

        private final String value;

        private Text(String value) {
            this.value = value;
        }

        @Override
        void write(StringBuilder out, int depth) {
            escape(out, value);
        }
    }

    private static final class Number extends Manifest {

        private final long value;

        private Number(long value) {
            this.value = value;
        }

        @Override
        void write(StringBuilder out, int depth) {
            out.append(value);
        }
    }

    private static final class Flag extends Manifest {

        private final boolean value;

        private Flag(boolean value) {
            this.value = value;
        }

        @Override
        void write(StringBuilder out, int depth) {
            out.append(value);
        }
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static void escape(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
