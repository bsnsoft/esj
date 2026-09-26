package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.CodeLists;
import de.bsnsoft.esj.rules.JavaRules;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RulePack;
import de.bsnsoft.esj.rules.RulePackSource;
import de.bsnsoft.esj.rules.RulePacks;
import java.util.List;

/**
 * The EN 16931 rule pack this build carries, ready to run.
 *
 * <p>A pack is three things that have to be brought together: the manifest and the rule files
 * it names, the code list snapshots it decides membership against, and the instances of the
 * rules written in Java. The manifest names the Java classes and does not load them — a file
 * that could name a class the engine then instantiates would be a file that decides what code
 * runs, and a pack may arrive from a directory a caller was handed. So the instances are
 * passed in, and this class is where the ones of this pack are listed, in the one place that
 * is allowed to know them.
 *
 * <p>What the pack is <em>not</em> is an authority on the business rules of EN 16931. The
 * artefacts of CEN/TC 434 and the core invoice usage specifications are; this pack names the
 * release it was verified against, that name appears in every finding, and its findings are a
 * layer of their own which is never presented as conformance to the ESJ format
 * ({@code SPEC.md} section 9.4).
 */
public final class En16931 {

    /** The identifier of the pack, which appears in every finding it produces. */
    public static final String PACK_ID = "en16931";

    /** The version of the pack, which is the release of the artefacts it was verified against. */
    public static final String VERSION = "1.3.16";

    /** The edition of the semantic model the rules of this pack are addresses in. */
    public static final String EDITION = "EN 16931-1:2017+A1:2019/AC:2020";

    private En16931() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the pack as its files write it.
     *
     * @return the manifest with the rules of every file it names
     * @throws de.bsnsoft.esj.rules.RulePackException if this build does not carry
     *                                                            the pack, or carries one that
     *                                                            is not a rule pack
     */
    public static RulePack pack() {
        return RulePacks.bundled(PACK_ID, VERSION);
    }

    /**
     * Returns the instances of the rules the manifest names as written in Java.
     *
     * @return the rules, one instance of each class the manifest names
     */
    public static JavaRules javaRules() {
        return JavaRules.of(List.of(
                new Br62(), new Br63(), new Br64(), new Br65(),
                new BrCl07(), new BrCl10(), new BrCl11(), new BrCl13(),
                new BrCl21(), new BrCl24(), new BrCl25(), new BrCl26(),
                new BrCo09(),
                new BrS08(),
                new BrZ01(), new BrZ08(),
                new BrE01(), new BrE08(),
                new BrAe01(), new BrAe08(),
                new BrIc01(), new BrIc08(),
                new BrG01(), new BrG08(),
                new BrO01(), new BrO08(),
                new BrAf08(), new BrAg08()));
    }

    /**
     * Returns this pack as a source, which is how a caller that selects by edition reaches
     * it.
     *
     * <p>It is the one pack this class names directly rather than through a service
     * declaration, because it is the pack of the edition the ecosystem validates against
     * today and a build without it would carry no rules at all.
     *
     * @return the source
     */
    public static RulePackSource source() {
        return new Source();
    }

    /** This pack, offered under the edition its rules are written for. */
    private static final class Source implements RulePackSource {

        @Override
        public String edition() {
            return EDITION;
        }

        @Override
        public RulePack pack() {
            return En16931.pack();
        }

        @Override
        public RuleEngine engine(Registry registry) {
            return En16931.engine(registry);
        }
    }

    /**
     * Compiles the pack against a registry, with the code list snapshots of this build and
     * the Java rules of this pack.
     *
     * @param registry the registry of the edition the documents will name
     * @return the compiled engine
     * @throws de.bsnsoft.esj.rules.RulePackException if the pack cannot be
     *                                                           compiled
     * @throws NullPointerException                              if {@code registry} is
     *                                                           {@code null}
     */
    public static RuleEngine engine(Registry registry) {
        RulePack pack = pack();
        return RuleEngine.compile(pack, registry, CodeLists.bundled(pack), javaRules());
    }
}
