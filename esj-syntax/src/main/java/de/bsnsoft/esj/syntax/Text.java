package de.bsnsoft.esj.syntax;

import java.util.regex.Pattern;

/**
 * The one thing this module does to a text it did not write.
 *
 * <p>The messages of the artefacts are laid out for an XML file: a rule's text arrives
 * with the line breaks and the indentation of the stylesheet it was compiled into. A
 * report prints it as one sentence, so the runs of whitespace become single spaces and
 * the ends are trimmed. Nothing else is done to it — not a translation, not a shortening
 * and not a rewording.
 *
 * <p>A message is not always the artefact's words alone. A schema validator quotes the
 * value it refused, and that value is content a stranger wrote, so whatever steers a
 * terminal rather than saying something is collapsed with the whitespace: the C0 and C1
 * controls, the Unicode line and paragraph separators, and the format characters that
 * reorder the text around them. Java's own definition of whitespace covers none of the
 * last three, and a line separator that survived into a report would let the sender of an
 * invoice break the line where they liked and write a sentence that reads like a result
 * of this tool.
 */
final class Text {

    /**
     * What a message is collapsed on: whitespace, every control character, every format
     * character, and the line and paragraph separators.
     */
    private static final Pattern COLLAPSED =
            Pattern.compile("[\\s\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+");

    private Text() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns a text with its runs of whitespace, control characters, format characters
     * and line separators collapsed to single spaces and its ends trimmed.
     *
     * @param text the text, possibly {@code null}
     * @return the normalized text, empty where there was none
     */
    static String normalize(String text) {
        return text == null ? "" : COLLAPSED.matcher(text).replaceAll(" ").strip();
    }
}
