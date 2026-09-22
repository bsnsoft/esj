package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.typed.build.InvoiceRules;
import de.bsnsoft.esj.typed.build.RuleViolation;
import java.util.Objects;

/**
 * The business rules an invoice is built against.
 *
 * <p>{@link #en16931()} is what the domain API runs by default: the rules of EN 16931-1 as
 * the bundled pack of {@code esj-rules} carries them, against the version of the CEN
 * artefacts that pack was verified against. Only a finding the pack calls fatal stops a
 * build; a warning and an information are carried into the report of
 * {@link Draft#validate()} and {@link Draft#buildReport()} and refuse no invoice.
 *
 * <p>The engine is compiled on first use and shared. Compiling it reads the pack and its
 * code list snapshots from the class path, which is worth doing once, and an engine holds
 * no state of a run.
 */
public final class Rules {

    /** The identifier of the rule pack the domain API runs by default. */
    public static final String PACK_ID = En16931.PACK_ID;

    /** The version of that pack, which is the release it was verified against. */
    public static final String PACK_VERSION = En16931.VERSION;

    private Rules() {
    }

    /**
     * Returns the rules of EN 16931-1, compiled once and shared.
     *
     * @return the rules, as the constrained builder takes them
     * @throws de.bsnsoft.esj.rules.RulePackException if this build does not
     *                                                           carry the pack
     */
    public static InvoiceRules en16931() {
        return of(Bundled.ENGINE);
    }

    /**
     * Returns the findings of a rule engine as the constrained builder takes them.
     *
     * <p>Every finding is handed over, each carrying whether the pack calls it fatal, so
     * that a warning and a rule the engine could not decide reach the report of the build
     * without refusing the invoice.
     *
     * @param engine the engine to run
     * @return the rules
     * @throws NullPointerException if {@code engine} is {@code null}
     */
    public static InvoiceRules of(RuleEngine engine) {
        Objects.requireNonNull(engine, "engine");
        return document -> engine.evaluate(document).stream()
                .map(finding -> new RuleViolation(finding.code(), finding.message(),
                        finding.paths(), finding.fatal()))
                .toList();
    }

    /** Compiles the bundled pack on first use, and not when the class is loaded. */
    private static final class Bundled {

        private static final RuleEngine ENGINE = En16931.engine(Registry.en16931());

        private Bundled() {
        }
    }
}
