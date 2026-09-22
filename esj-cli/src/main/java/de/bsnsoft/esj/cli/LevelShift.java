package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.PackException;
import de.bsnsoft.esj.syntax.Packs;
import de.bsnsoft.esj.syntax.ProfileLevels;
import de.bsnsoft.esj.syntax.Severity;
import de.bsnsoft.esj.xr.XrSyntax;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where one profile levels a rule of the standard differently for two syntaxes.
 *
 * <p>A core invoice usage specification states its levels per scenario, and a scenario is
 * a profile <em>and</em> a syntax. The two are not obliged to agree: the XRechnung
 * extension profile levels {@code BR-CO-16} down to information for a UBL invoice and says
 * nothing about it for a cross industry invoice, where the level of the standard therefore
 * stands. An invoice that trips that rule is accordingly not refused as UBL and is refused
 * as CII — the same invoice, the same profile, the same rule.
 *
 * <p>That is a fact of the published specification and not a defect of this tool, and
 * nothing here changes it: {@code esj validate} levels a document by the table of the
 * syntax the document arrived in, which is the table the official artefacts ran with, and
 * it goes on doing so. What a conversion can do is say it, because a conversion is exactly
 * the step after which the other table applies: the file this run writes will be judged by
 * the target syntax's table by whoever validates it next.
 *
 * <p>The line names rules and claims nothing about this invoice. Deciding whether a
 * document trips one of them is a business rule check, which {@code esj convert} and
 * {@code esj render} do not make and are not made to pay for; {@code esj validate} of the
 * written file answers it, and the line says so.
 *
 * <p>Only rules of EN 16931 are compared. A syntax rule of a binding — {@code UBL-CR-646},
 * {@code CII-SR-452} — exists in one syntax and not in the other, so a table that levels
 * one of them is saying nothing that could carry over to a document of the other syntax.
 * The rules of the standard are the ones both syntaxes raise and are the ones identified
 * with the prefix EN 16931 gives them.
 */
final class LevelShift {

    /** The prefix the business rules of EN 16931 are identified with. */
    private static final String STANDARD_RULE = "BR-";

    private LevelShift() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the rules of the standard that the profile of a document levels more
     * strictly for the target syntax than for the syntax the document arrived in.
     *
     * @param source  the syntax the document arrived in, empty where it was never XML and
     *                there is no table it was judged by
     * @param target  the syntax this run writes
     * @param profile the customization identifier the document names in BT-24
     * @return the rule identifiers, in order, empty where the two tables say the same
     *         thing about every rule of the standard, where either of them is unknown, or
     *         where the document names no profile
     * @throws CliException if a bundled pack cannot be read
     */
    static List<String> stricterInTarget(Optional<XrSyntax> source,
                                         XrSyntax target,
                                         String profile) {
        if (profile.isEmpty() || source.isEmpty() || source.orElseThrow() == target) {
            return List.of();
        }
        ProfileLevels from = levels(source.orElseThrow(), profile);
        ProfileLevels to = levels(target, profile);
        if (!from.any() && !to.any()) {
            return List.of();
        }
        Set<String> stricter = new TreeSet<>();
        Set<String> codes = new TreeSet<>(from.codes());
        codes.addAll(to.codes());
        for (String code : codes) {
            if (code.startsWith(STANDARD_RULE) && stricter(from.level(code), to.level(code))) {
                stricter.add(code);
            }
        }
        return List.copyOf(stricter);
    }

    /**
     * Tells whether the target table weighs a rule more heavily than the source table.
     *
     * <p>A table that says nothing about a rule leaves the level the rule was raised at,
     * which is the level of the standard. The tables of the packs this project carries
     * lower a level or leave it, so a rule the source table lowers and the target table
     * passes over is a rule that weighs more after the conversion; a rule neither table
     * mentions weighs the same on both sides.
     */
    private static boolean stricter(Optional<Severity> from, Optional<Severity> to) {
        if (from.isEmpty()) {
            return false;
        }
        return to.map(level -> level.ordinal() < from.orElseThrow().ordinal()).orElse(true);
    }

    /** Returns the levels a bundled pack gives a document of one syntax and one profile. */
    private static ProfileLevels levels(XrSyntax syntax, String profile) {
        try {
            return Packs.levels(syntax, profile);
        } catch (PackException e) {
            throw CliException.input("the validation pack cannot be read: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Returns the sentence a command prints where the two tables differ.
     *
     * <p>The profile is not spelled out, although it is what levels the rules: a
     * customization identifier is a hundred characters of URN, the document names it and
     * {@code esj validate} prints it, and a line that carried it would be read by nobody.
     *
     * @param codes  what {@link #stricterInTarget} returned, which is not empty
     * @param source the syntax the document arrived in
     * @param target the syntax this run wrote
     * @return the line, without the {@code info:} the console prefixes it with
     */
    static String line(List<String> codes, XrSyntax source, XrSyntax target) {
        return "the profile this document names levels " + String.join(", ", codes)
                + " more strictly for " + describe(target) + " than for " + describe(source)
                + ", so the file written here can be refused where the source was not;"
                + " esj validate of it says whether this invoice trips it";
    }

    /** Returns what a line calls a syntax, as the conversion commands call it. */
    private static String describe(XrSyntax syntax) {
        return switch (syntax) {
            case CII -> "a cross industry invoice";
            case UBL_INVOICE -> "a UBL invoice";
            case UBL_CREDIT_NOTE -> "a UBL credit note";
        };
    }
}
