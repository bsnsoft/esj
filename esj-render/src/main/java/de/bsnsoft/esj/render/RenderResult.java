package de.bsnsoft.esj.render;

import de.bsnsoft.esj.xr.ExportReport;
import java.util.Objects;

/**
 * A rendering and the report of what did not reach it.
 *
 * <p>The baseline rendering goes through the XR representation, and that representation
 * carries the business terms of the semantic model and nothing else. A value the report
 * names is a value the reader of the rendering will not see: an extension subtree, a term
 * the XR representation has no element for, a supplementary component its element cannot
 * carry. An empty report means that everything the document says reached the stylesheet —
 * what the stylesheet then chooses to show is its own matter, and
 * {@code docs/rendering.md} says which terms it leaves out.
 *
 * @param html   the rendered document
 * @param report what did not reach the stylesheet
 */
public record RenderResult(String html, ExportReport report) {

    /**
     * Checks both members.
     *
     * @param html   the rendered document
     * @param report what did not reach the stylesheet
     * @throws NullPointerException if an argument is {@code null}
     */
    public RenderResult {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(report, "report");
    }
}
