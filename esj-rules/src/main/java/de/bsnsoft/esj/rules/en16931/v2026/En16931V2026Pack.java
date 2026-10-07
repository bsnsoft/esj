package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.Preview;
import de.bsnsoft.esj.model.MinorUnits;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.CodeList;
import de.bsnsoft.esj.rules.CodeLists;
import de.bsnsoft.esj.rules.JavaRules;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RulePack;
import de.bsnsoft.esj.rules.RulePackSource;
import de.bsnsoft.esj.rules.RulePacks;
import de.bsnsoft.esj.rules.internal.en16931.Br62;
import de.bsnsoft.esj.rules.internal.en16931.Br63;
import de.bsnsoft.esj.rules.internal.en16931.Br64;
import de.bsnsoft.esj.rules.internal.en16931.Br65;
import de.bsnsoft.esj.rules.internal.en16931.BrCl07;
import de.bsnsoft.esj.rules.internal.en16931.BrCl11;
import de.bsnsoft.esj.rules.internal.en16931.BrCl13;
import de.bsnsoft.esj.rules.internal.en16931.BrCl25;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br68;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br69;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br70;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br71;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br72;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br73;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br74;
import de.bsnsoft.esj.rules.internal.en16931.v2026.Br75;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrAe01;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrAe08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrCo09;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrCo14;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrCo18;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrCo49;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrCo50;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrE01;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrE08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrG01;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrG08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrIc01;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrIc08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrIg08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrIp08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrO01;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrO08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrS08;
import de.bsnsoft.esj.rules.internal.en16931.v2026.BrZ08;
import java.util.List;
import java.util.Optional;

/**
 * The EN 16931-1:2026 rule pack this build carries, ready to run.
 *
 * <p>The pack brings three things together: the manifest with the rule files it names and the
 * rules it takes over from the pack of the 2017 edition, the code list snapshots it decides
 * membership against, and the instances of the rules written in Java. The manifest names the
 * Java classes and does not load them, so the instances are listed here, in the one place
 * that is allowed to know them.
 *
 * <p>Eight of those instances come from the pack of the earlier edition: four ask a scheme of
 * the electronic addresses and of the item identifiers, and four ask that a scheme, where one
 * is stated, is on the list the standard names for it. Each of the eight is the same statement
 * about the same terms in both editions, so the class is shared rather than copied. The seven
 * rules that ask a scheme of the party identifiers are new in this edition and are here, and
 * {@code conformance/rules-2026/coverage.md} names the code list rules of the earlier pack
 * that this one does not carry, with what the edition changed about each of them.
 *
 * <p>No official validation artefact covers this edition. What the pack claims per rule is in
 * its manifest and counted in {@code conformance/rules-2026/coverage.md}; no finding of it is
 * corroborated by an official artefact.
 *
 * <p>The class is a preview, like the edition: it may change in any minor release.
 */
@Preview
public final class En16931V2026Pack implements RulePackSource {

    /** The identifier of the pack, which appears in every finding it produces. */
    public static final String PACK_ID = "en16931-2026";

    /** The version of the pack, which is the release of this project that published it. */
    public static final String VERSION = "0.1";

    /** The edition of the semantic model the rules of this pack are addresses in. */
    public static final String EDITION = "EN 16931-1:2026";

    /**
     * Creates the source. It is found by {@link java.util.ServiceLoader} and is created by
     * it.
     */
    public En16931V2026Pack() {
    }

    @Override
    public String edition() {
        return EDITION;
    }

    @Override
    public RulePack pack() {
        return RulePacks.bundled(PACK_ID, VERSION);
    }

    @Override
    public RuleEngine engine(Registry registry) {
        RulePack pack = pack();
        return RuleEngine.compile(pack, registry, CodeLists.bundled(pack), javaRules());
    }

    @Override
    public Optional<MinorUnits> currencyMinorUnits() {
        return Optional.of(minorUnits());
    }

    /**
     * Returns the minor units of the currency list snapshot this pack decides against, for a
     * policy that rounds the amounts of this edition to them.
     *
     * <p>The numbers are the ones the pack's own rules read, from the same file; nothing
     * else in this build carries a second copy. The totals derivation of the typed view of
     * this edition is built over them:
     * {@code invoice.derive(Totals.of(En16931V2026Pack.minorUnits()))}.
     *
     * @return the minor units, with the snapshot's list, publisher and day as their source
     * @throws de.bsnsoft.esj.rules.RulePackException if the snapshot is not in this build
     */
    public static MinorUnits minorUnits() {
        return Currencies.MINOR_UNITS;
    }

    /** The minor units of the pack's currency list, read once, when first asked for. */
    private static final class Currencies {

        private static final MinorUnits MINOR_UNITS = read();

        private Currencies() {
        }

        private static MinorUnits read() {
            CodeList currencies = CodeLists.bundled(RulePacks.bundled(PACK_ID, VERSION))
                    .require("iso-4217");
            return MinorUnits.of(currencies.minorUnits(), "the currency list snapshot "
                    + currencies.listId() + " of " + currencies.retrieved() + " in the rule pack "
                    + PACK_ID + " " + VERSION);
        }
    }

    /**
     * Returns the instances of the rules the manifest names as written in Java.
     *
     * @return the rules, one instance of each class the manifest names
     */
    public static JavaRules javaRules() {
        return JavaRules.of(List.of(
                new Br62(), new Br63(), new Br64(), new Br65(),
                new BrCl07(), new BrCl11(), new BrCl13(), new BrCl25(),
                new Br68(),
                new Br69(), new Br70(), new Br71(), new Br72(), new Br73(), new Br74(),
                new Br75(),
                new BrCo09(), new BrCo14(), new BrCo18(), new BrCo49(), new BrCo50(),
                new BrS08(), new BrZ08(), new BrE08(), new BrAe08(), new BrIc08(),
                new BrG08(), new BrO08(), new BrIg08(), new BrIp08(),
                new BrE01(), new BrAe01(), new BrIc01(), new BrG01(), new BrO01()));
    }
}
