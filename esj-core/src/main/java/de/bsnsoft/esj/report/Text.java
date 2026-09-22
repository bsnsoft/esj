package de.bsnsoft.esj.report;

import java.util.List;
import java.util.Objects;

/**
 * One text of a report, which is either this project's sentence or somebody else's.
 *
 * <p>The distinction decides one thing and is worth a type for it: whether a report may
 * write the text in the language it is written in. A {@link Phrased} text is a sentence
 * this project wrote — the label of a check row, the reason a layer did not run, the line
 * under the verdict — and a German report writes it in German. A {@link Words} text came
 * out of the run and stands as it is, whatever the language of the report: the message of
 * a rule its publisher wrote, the identifier of a pack, a switch of the command line. A
 * report that translated the second kind would be putting words into another publisher's
 * rule, and one that left the first kind untranslated is the half-English page this type
 * exists to prevent.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public sealed interface Text {

    /**
     * Returns a sentence of this project's own, with the arguments it takes.
     *
     * @param phrase    which sentence
     * @param arguments what it says about, in the order the phrase takes them; each is
     *                  text of the run and is never translated
     * @return the text
     * @throws NullPointerException if an argument is {@code null}
     */
    static Text of(Phrase phrase, String... arguments) {
        return new Phrased(phrase, List.of(arguments));
    }

    /**
     * Returns a text that came out of the run and is printed as it stands.
     *
     * @param words the words
     * @return the text
     * @throws NullPointerException if {@code words} is {@code null}
     */
    static Text words(String words) {
        return new Words(words);
    }

    /**
     * A sentence of this project's own.
     *
     * @param phrase    which sentence
     * @param arguments what it says about, in the order the phrase takes them
     */
    record Phrased(Phrase phrase, List<String> arguments) implements Text {

        /**
         * Copies the arguments and refuses a missing member.
         *
         * @param phrase    which sentence
         * @param arguments what it says about, in the order the phrase takes them
         */
        public Phrased {
            Objects.requireNonNull(phrase, "phrase");
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * Words of the run, printed as they stand.
     *
     * @param words the words
     */
    record Words(String words) implements Text {

        /**
         * Refuses a missing member.
         *
         * @param words the words
         */
        public Words {
            Objects.requireNonNull(words, "words");
        }
    }
}
