package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.model.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The extension registries one run loads, and the registry they add up to.
 *
 * <p>{@code --extension} names them, separated by commas, and a run may name more than
 * one: the XRechnung extension and the B2C extension describe disjoint terms of the same
 * core model, and an invoice may carry both. What the option decides is what a reader
 * turns into a value rather than into a note, and what a validator checks rather than
 * reports as not checked (specification, section 11.1).
 *
 * @param loaded the extensions this run loads, in no particular order
 */
record Extensions(Set<Extension> loaded) {

    /** The registry each combination adds up to, built once per process. */
    private static final Map<Set<Extension>, Registry> REGISTRIES = new ConcurrentHashMap<>();

    /** Creates the value, defensively copying what it was given. */
    Extensions {
        Objects.requireNonNull(loaded, "loaded");
        Set<Extension> copy = EnumSet.noneOf(Extension.class);
        copy.addAll(loaded);
        loaded = Collections.unmodifiableSet(copy);
    }

    /** One extension registry this version ships. */
    enum Extension {

        /** The XRechnung extension of the German core invoice usage specification. */
        XRECHNUNG("xrechnung"),

        /** The B2C extension, which records the gross figures a consumer was shown. */
        B2C("b2c");

        private final String token;

        Extension(String token) {
            this.token = token;
        }

        /** Returns the word {@code --extension} is given to load this registry. */
        String token() {
            return token;
        }

        /** Returns the registry of this extension, which describes its terms alone. */
        Registry registry() {
            return this == XRECHNUNG ? Registry.xrechnungExtension() : Registry.b2cExtension();
        }
    }

    /**
     * Returns the run that loads no extension registry.
     *
     * @return the empty set of extensions
     */
    static Extensions none() {
        return new Extensions(EnumSet.noneOf(Extension.class));
    }

    /**
     * Returns the extensions a {@code --extension} value names.
     *
     * @param token the value of the option, or {@code null} where it was not given
     * @return the extensions to load
     * @throws CliException if the value names an extension this version does not ship, or
     *                      holds an empty name
     */
    static Extensions of(String token) {
        if (token == null) {
            return none();
        }
        Set<Extension> wanted = EnumSet.noneOf(Extension.class);
        for (String name : token.split(",", -1)) {
            wanted.add(extension(name.trim(), token));
        }
        return new Extensions(wanted);
    }

    private static Extension extension(String name, String token) {
        for (Extension candidate : Extension.values()) {
            if (candidate.token().equals(name)) {
                return candidate;
            }
        }
        throw CliException.input("--extension takes " + names()
                + ", separated by commas, not '" + (name.isEmpty() ? token : name) + "'");
    }

    /** Returns the names this version ships, for a message. */
    private static String names() {
        List<String> tokens = new ArrayList<>();
        for (Extension extension : Extension.values()) {
            tokens.add(extension.token());
        }
        return String.join(" or ", tokens);
    }

    /**
     * Tells whether this run loads no extension registry at all.
     *
     * @return whether nothing was named
     */
    boolean isEmpty() {
        return loaded.isEmpty();
    }

    /**
     * Returns the registry of every loaded extension, each describing its own terms
     * alone, in the order this class declares them.
     *
     * <p>A caller that has to decide per edition whether an extension fits needs the
     * extension registries themselves rather than the combination, because the decision
     * is made from the {@code imports} member of each of them.
     *
     * @return the extension registries, in a stable order
     */
    List<Registry> registries() {
        List<Registry> registries = new ArrayList<>();
        for (Extension extension : Extension.values()) {
            if (loaded.contains(extension)) {
                registries.add(extension.registry());
            }
        }
        return List.copyOf(registries);
    }

    /**
     * Returns the core registry with every loaded extension combined into it.
     *
     * <p>The combination is built once per process and per set of extensions, because it
     * is the registry a reader, a validator and a rule engine are all given, and reading
     * the files again for each of them would cost the same work three times.
     *
     * @return the registry this run works against
     */
    Registry registry() {
        return REGISTRIES.computeIfAbsent(loaded, Extensions::combine);
    }

    private static Registry combine(Set<Extension> extensions) {
        Registry registry = Registry.en16931();
        // The order the enum declares, so that one set of extensions gives one registry
        // whatever order the command line named them in.
        for (Extension extension : Extension.values()) {
            if (extensions.contains(extension)) {
                registry = registry.withExtension(extension.registry());
            }
        }
        return registry;
    }
}
