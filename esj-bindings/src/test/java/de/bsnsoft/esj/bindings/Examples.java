package de.bsnsoft.esj.bindings;

import java.util.List;

/** The example documents of the repository, which the build copies onto the test class path. */
final class Examples {

    private Examples() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the examples this syntax holds whole, in the order {@code examples/README.md}
     * lists them. {@code b2c-gross} is not among them: its four terms come from an extension
     * registry no binding table of this release covers, so the writer names them instead of
     * writing them ({@code CiiWriterTest} measures that).
     */
    static List<String> names() {
        return List.of("smallest-valid", "standard-invoice", "multiple-lines", "allowances",
                "charges", "self-billed", "credit-note", "minimal", "extended",
                "extension-depth");
    }

    /** Returns the bytes of one example in its pretty form. */
    static byte[] bytes(String name) {
        return Corpus.bytes("/examples/" + name + ".esj.json");
    }
}
