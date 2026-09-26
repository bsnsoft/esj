package de.bsnsoft.esj.rules;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A rule pack as its file writes it: a header that says what the pack is, which edition of
 * the semantic model it is written for and what it was checked against, the code list
 * snapshots its rules ask about, the Java rules it carries, the rule files and the shared
 * rules of other packs it is made of, and the rules themselves.
 *
 * <p>{@code edition} is the edition of the semantic model the rules are addresses in. A path
 * is an address relative to an edition, the business rules of a standard are renumbered,
 * added to and withdrawn between editions, and running the pack of one edition over a
 * document of another would report arithmetic about terms the document does not have. The
 * member is therefore the one thing that decides whether a pack may be compiled against a
 * registry and run over a document, and neither the rules nor the file names are consulted
 * for it.
 *
 * <p>{@code verifiedAgainst} is the member that keeps this project honest. A rule pack of
 * this project is not an authority on the business rules of EN 16931 — the artefacts of
 * CEN/TC 434 and the core invoice usage specifications are, and they are versioned and
 * re-released. A pack that has such artefacts names the release its behaviour was compared
 * against, that name appears in every report, and no claim about agreement is made anywhere
 * without the ledger that measured it. A pack written for an edition no artefact release
 * covers leaves the member out and says per rule what stands behind it instead
 * ({@link RuleOracle}).
 *
 * <p>{@code codeLists} maps the identifier a rule names a list by to the day of the
 * snapshot this pack uses. It is what makes a verdict reproducible: see {@link CodeLists}.
 * {@code codeListSources} says which pack the file of that snapshot lies in, so that a pack
 * whose edition names the same list as another pack reads the same bytes instead of
 * carrying a second copy of them.
 *
 * <p>{@code files} names the rule files the pack is made of. A pack of two hundred rules is
 * unreadable as one file and impossible to review as one diff, so the rules are split by
 * family; the manifest names the files and {@link RulePacks#bundled} reads them, and the
 * result is one set of rules in which an identifier appears once.
 *
 * <p>{@code shares} names rules of another pack that this pack takes over unchanged. A rule
 * of one edition that the next edition leaves alone is the same statement about the same
 * terms, and copying it would make two files that have to be kept equal by hand; the pack
 * names the other pack, the file and the identifiers instead, and states the oracle under
 * which it takes them. A shared rule that addresses a path this pack's edition moved does
 * not compile, which is the guard that keeps sharing from being a guess.
 *
 * <p>{@code javaRules} names the classes of the rules the language cannot express, each with
 * its oracle. It names them and does not load them: this class is data read from a file, and
 * a file that could name a class the engine then instantiates would be a file that chooses
 * what runs. The caller passes the instances to {@link RuleEngine}, which checks that exactly
 * the named ones arrived.
 *
 * @param id              the identifier of the pack, for example {@code en16931}
 * @param version         the version of the pack, for example {@code 1.3.16}
 * @param edition         the edition of the semantic model the rules are written for, in the
 *                        spelling of the registry that describes it
 * @param verifiedAgainst the release of the official artefacts the pack is checked against,
 *                        empty where no artefact release covers this edition
 * @param description     one paragraph on what the pack contains
 * @param codeLists       the snapshot day of each code list the rules name, by list
 *                        identifier
 * @param codeListSources the pack each snapshot is read from, by list identifier, written
 *                        {@code id/version}
 * @param javaRules       the rules written in Java, by class name and oracle
 * @param files           the rule files the manifest is made of, each relative to the
 *                        directory of the manifest
 * @param shares          the rules this pack takes over from another pack
 * @param rules           the rules written in the rule language: those the manifest writes
 *                        itself first, then the shared ones, then those of its files
 */
public record RulePack(String id,
                       String version,
                       String edition,
                       Optional<String> verifiedAgainst,
                       String description,
                       Map<String, String> codeLists,
                       Map<String, String> codeListSources,
                       List<JavaRuleRef> javaRules,
                       List<String> files,
                       List<Share> shares,
                       List<RuleDefinition> rules) {

    /**
     * One rule written in Java, as the manifest names it.
     *
     * @param className the binary name of the class
     * @param oracle    what stands behind the rule in this pack
     */
    public record JavaRuleRef(String className, RuleOracle oracle) {

        /**
         * Checks that both parts are present.
         *
         * @param className the binary name of the class
         * @param oracle    what stands behind the rule in this pack
         * @throws NullPointerException if a part is {@code null}
         */
        public JavaRuleRef {
            Objects.requireNonNull(className, "className");
            Objects.requireNonNull(oracle, "oracle");
        }
    }

    /**
     * Rules of another pack that this pack takes over unchanged.
     *
     * @param pack    the identifier of the pack they are read from
     * @param version the version of that pack
     * @param file    the rule file of that pack, relative to its manifest
     * @param oracle  what stands behind those rules in this pack, which is not what stands
     *                behind them in the pack they come from
     * @param rules   the identifiers taken over; a rule of the file that is not named here
     *                is not taken over
     */
    public record Share(String pack, String version, String file, RuleOracle oracle,
                        List<String> rules) {

        /**
         * Copies the identifiers and checks that every part is present.
         *
         * @param pack    the identifier of the pack they are read from
         * @param version the version of that pack
         * @param file    the rule file of that pack, relative to its manifest
         * @param oracle  what stands behind those rules in this pack
         * @param rules   the identifiers taken over
         * @throws NullPointerException if a part is {@code null}
         */
        public Share {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(oracle, "oracle");
            rules = List.copyOf(rules);
        }

        /**
         * Returns the pack the rules are read from, as a report names a pack.
         *
         * @return the pack name, for example {@code en16931/1.3.16}
         */
        public String name() {
            return pack + "/" + version;
        }
    }

    /**
     * Copies the collections and checks that every part is present.
     *
     * @param id              the identifier of the pack, for example {@code en16931}
     * @param version         the version of the pack, for example {@code 1.3.16}
     * @param edition         the edition of the semantic model the rules are written for
     * @param verifiedAgainst the release of the official artefacts the pack is checked
     *                        against, empty where none covers this edition
     * @param description     one paragraph on what the pack contains
     * @param codeLists       the snapshot day of each code list the rules name, by list
     *                        identifier
     * @param codeListSources the pack each snapshot is read from, by list identifier
     * @param javaRules       the rules written in Java, by class name and oracle
     * @param files           the rule files the manifest is made of
     * @param shares          the rules this pack takes over from another pack
     * @param rules           the rules written in the rule language
     * @throws NullPointerException if a part is {@code null}
     */
    public RulePack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(edition, "edition");
        Objects.requireNonNull(verifiedAgainst, "verifiedAgainst");
        Objects.requireNonNull(description, "description");
        codeLists = Map.copyOf(codeLists);
        codeListSources = Map.copyOf(codeListSources);
        javaRules = List.copyOf(javaRules);
        files = List.copyOf(files);
        shares = List.copyOf(shares);
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
     * Returns the class names of the rules written in Java, in the order the manifest names
     * them.
     *
     * @return the class names
     */
    public List<String> javaRuleClasses() {
        return javaRules.stream().map(JavaRuleRef::className).toList();
    }

    /**
     * Returns what stands behind a rule of this pack.
     *
     * @param ruleId the identifier of the rule
     * @return the oracle, or an empty optional if this pack has no such rule
     * @throws NullPointerException if {@code ruleId} is {@code null}
     */
    public Optional<RuleOracle> oracleOf(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId");
        for (RuleDefinition rule : rules) {
            if (rule.id().equals(ruleId)) {
                return Optional.of(rule.oracle());
            }
        }
        return Optional.empty();
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
