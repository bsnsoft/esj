package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.Preview;
import de.bsnsoft.esj.model.MinorUnits;
import de.bsnsoft.esj.model.Registry;
import java.util.Optional;

/**
 * A rule pack this build carries, with the instances of its Java rules, offered under the
 * edition of the semantic model it is written for.
 *
 * <p>A pack is data and a Java rule is code, and a caller that wants to run the pack of the
 * edition a document names has to have both in hand. This interface is where they meet: a
 * pack names its Java classes and does not load them ({@link JavaRules}), and the class that
 * implements this interface is the one place in a build that is allowed to know which
 * instances belong to which pack.
 *
 * <p>Implementations of this interface are found with {@link java.util.ServiceLoader}, which
 * is what makes an edition separable. The facts of an edition — its registry, its rule pack,
 * the rules the language cannot express for it — are files a distribution may leave out, and
 * nothing that stays behind may name them, or the build without them would not compile. A
 * service declaration is a file like any other: it is left out with the edition it announces,
 * and {@link RulePackSources} then simply finds one source fewer.
 *
 * @see RulePackSources
 */
public interface RulePackSource {

    /**
     * Returns the edition of the semantic model this pack is written for, in the spelling of
     * the registry that describes it.
     *
     * @return the edition, for example {@code EN 16931-1:2017+A1:2019/AC:2020}
     */
    String edition();

    /**
     * Returns the pack as its files write it.
     *
     * @return the manifest with the rules of every file and every share it names
     * @throws RulePackException if this build does not carry the pack
     */
    RulePack pack();

    /**
     * Compiles the pack against a registry.
     *
     * @param registry the registry of the edition this source names
     * @return the compiled engine
     * @throws RulePackException    if the pack cannot be compiled, which includes a registry
     *                              of another edition
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    RuleEngine engine(Registry registry);

    /**
     * Returns the minor units of the currency list snapshot this pack decides against,
     * where its edition bounds the fraction digits of a term by the minor unit of the
     * currency in use.
     *
     * <p>A caller that evaluates such a bound outside the rule engine — an upgrade that
     * reports a value exceeding it, a policy that rounds to it — reads the numbers here, so
     * that it decides against the same snapshot the rules of the pack do.
     *
     * <p>The method is a preview, like {@link MinorUnits} itself: it may change in any
     * minor release.
     *
     * @return the minor units, empty for a pack whose edition bounds every term by a
     *         constant
     * @throws RulePackException if the snapshot is not in this build
     */
    @Preview
    default Optional<MinorUnits> currencyMinorUnits() {
        return Optional.empty();
    }
}
