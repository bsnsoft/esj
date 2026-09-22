package de.bsnsoft.esj.rules;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A rule pack as its file writes it: a header that says what the pack is and what it was
 * checked against, the code list snapshots its rules ask about, the Java rules it carries,
 * and the rules themselves.
 *
 * <p>{@code verifiedAgainst} is the member that keeps this project honest. A rule pack of
 * this project is not an authority on the business rules of EN 16931 — the artefacts of
 * CEN/TC 434 and the core invoice usage specifications are, and they are versioned and
 * re-released. A pack therefore names the release of those artefacts its behaviour was
 * compared against, that name appears in every report, and no claim about agreement is made
 * anywhere without the ledger that measured it.
 *
 * <p>{@code codeLists} maps the identifier a rule names a list by to the day of the
 * snapshot this pack uses. It is what makes a verdict reproducible: see {@link CodeLists}.
 *
 * <p>{@code files} names the rule files the pack is made of. A pack of two hundred rules is
 * unreadable as one file and impossible to review as one diff, so the rules are split by
 * family; the manifest names the files and {@link RulePacks#bundled} reads them, and the
 * result is one set of rules in which an identifier appears once.
 *
 * <p>{@code javaRules} names the classes of the rules the language cannot express. It names
 * them and does not load them: this class is data read from a file, and a file that could
 * name a class the engine then instantiates would be a file that chooses what runs. The
 * caller passes the instances to {@link RuleEngine}, which checks that exactly the named
 * ones arrived.
 *
 * @param id             the identifier of the pack, for example {@code en16931}
 * @param version        the version of the pack, for example {@code 1.3.16}
 * @param verifiedAgainst the release of the official artefacts the pack is checked against
 * @param description    one paragraph on what the pack contains
 * @param codeLists      the snapshot day of each code list the rules name, by list
 *                       identifier
 * @param javaRules      the class names of the rules written in Java
 * @param files          the rule files the manifest is made of, each relative to the
 *                       directory of the manifest
 * @param rules          the rules written in the rule language: those the manifest writes
 *                       itself first, then those of its files, in the order of the files
 */
public record RulePack(String id,
                       String version,
                       String verifiedAgainst,
                       String description,
                       Map<String, String> codeLists,
                       List<String> javaRules,
                       List<String> files,
                       List<RuleDefinition> rules) {

    /**
     * Copies the collections and checks that every part is present.
     *
     * @param id             the identifier of the pack, for example {@code en16931}
     * @param version        the version of the pack, for example {@code 1.3.16}
     * @param verifiedAgainst the release of the official artefacts the pack is checked against
     * @param description    one paragraph on what the pack contains
     * @param codeLists      the snapshot day of each code list the rules name, by list
     *                       identifier
     * @param javaRules      the class names of the rules written in Java
     * @param files          the rule files the manifest is made of, each relative to the
     *                       directory of the manifest
     * @param rules          the rules written in the rule language: those the manifest writes
     *                       itself first, then those of its files, in the order of the files
     * @throws NullPointerException if a part is {@code null}
     */
    public RulePack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(verifiedAgainst, "verifiedAgainst");
        Objects.requireNonNull(description, "description");
        codeLists = Map.copyOf(codeLists);
        javaRules = List.copyOf(javaRules);
        files = List.copyOf(files);
        rules = List.copyOf(rules);
    }

    /**
     * Returns the identifier and the version, which is how a report names a pack.
     *
     * @return the pack name, for example {@code en16931/1.3.16}
     */
    public String name() {
        return id + "/" + version;
    }

    /**
     * Returns the pack name and how many rules of each kind it carries.
     *
     * @return a short description of the pack
     */
    @Override
    public String toString() {
        return name() + " (" + rules.size() + " rules in the rule language, "
                + javaRules.size() + " in Java)";
    }
}
