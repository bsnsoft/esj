package de.bsnsoft.esj.render;

/**
 * Reads a rendering the way a reader of it does: as the text that is displayed.
 *
 * <p>The tests of this module ask whether a value of the document is in the rendering, and
 * that question is about the text a reader sees and not about the markup around it. This
 * class therefore drops the inlined style sheet and the two inlined scripts, replaces
 * every tag with a line break, resolves the character references the serializer wrote and
 * collapses the whitespace — which is the last thing a browser does as well, and the
 * reason it can be done here: the HTML serializer of the transformation indents its output
 * and wraps long text lines, so the text of a rendering carries line breaks the document
 * never had.
 */
final class Html {

    private Html() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the displayed text of a rendering, with whitespace collapsed.
     *
     * @param html the rendering
     * @return the text a reader sees
     */
    static String text(String html) {
        String text = html
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<[^>]*>", "\n")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'");
        return Presentation.flatten(text);
    }

    /**
     * Counts how often a needle occurs in a rendering.
     *
     * @param html   the rendering
     * @param needle what to count
     * @return the number of occurrences
     */
    static int count(String html, String needle) {
        int count = 0;
        for (int i = html.indexOf(needle); i >= 0; i = html.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
