package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.rules.en16931.En16931;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * The rule packs this build carries, by the edition of the semantic model each is written
 * for.
 *
 * <p>Which editions a build holds a pack for is a property of the build and not of this
 * class. The pack of the edition the documents of the ecosystem name today is compiled in
 * and is named here; every other one announces itself as a {@link RulePackSource} service,
 * so that a distribution which leaves an edition out leaves out the pack, the Java rules of
 * that pack and the declaration together, and nothing that stays behind names any of them.
 *
 * <p>A caller asks for the edition a document names and either gets a pack or does not. Not
 * getting one is not a defect of the document: the business rules are then a component of
 * the check that is missing, the verdict is the third state, and {@code docs/validation.md}
 * says which cause a report prints for it.
 */
public final class RulePackSources {

    private RulePackSources() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns every pack this build carries, in the order the editions were published.
     *
     * @return the sources, one per edition
     */
    public static List<RulePackSource> all() {
        Map<String, RulePackSource> byEdition = new LinkedHashMap<>();
        RulePackSource standing = En16931.source();
        byEdition.put(standing.edition(), standing);
        for (RulePackSource source : ServiceLoader.load(RulePackSource.class,
                RulePackSources.class.getClassLoader())) {
            if (byEdition.put(source.edition(), source) != null) {
                throw new RulePackException("this build carries two rule packs for the edition "
                        + source.edition() + "; an edition has one pack");
            }
        }
        return List.copyOf(new ArrayList<>(byEdition.values()));
    }

    /**
     * Returns the pack of one edition, where this build carries one.
     *
     * @param edition the edition, in the spelling of the registry that describes it
     * @return the source, or an empty optional
     * @throws NullPointerException if {@code edition} is {@code null}
     */
    public static Optional<RulePackSource> forEdition(String edition) {
        Objects.requireNonNull(edition, "edition");
        for (RulePackSource source : all()) {
            if (source.edition().equals(edition)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the editions this build carries a pack for.
     *
     * @return the editions, in the order they were published
     */
    public static List<String> editions() {
        List<String> editions = new ArrayList<>();
        for (RulePackSource source : all()) {
            editions.add(source.edition());
        }
        return List.copyOf(editions);
    }
}
