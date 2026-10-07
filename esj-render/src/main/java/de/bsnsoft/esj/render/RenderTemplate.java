package de.bsnsoft.esj.render;

import de.bsnsoft.esj.render.internal.Template;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A branded rendering: a letterhead, a logo, a colour scheme, fonts, page margins, and the
 * places the layout gives to terms of a model extension.
 *
 * <p>A template is a JSON file and the files it names beside it. What it does <b>not</b>
 * contain is a layout of its own: it may choose between the two {@link Layout}s this
 * module has, and every business term of the document reaches a page in either. A template
 * decides what the page looks like, which of the two layouts it is, which extension terms
 * get a place of their own, and the few things a business letter leaves to its sender —
 * nothing else. {@code schema/render-template.schema.json} is the shape of the file and
 * {@code docs/templates.md} is what each member does.
 *
 * <pre>{@code
 * RenderTemplate template = RenderTemplate.read(Path.of("letterhead/template.json"));
 * byte[] pdf = new PdfRenderer().render(document,
 *         RenderOptions.defaults().withLanguage(RenderLanguage.GERMAN).withTemplate(template));
 * }</pre>
 *
 * <h2>The extension terms</h2>
 *
 * <p>A template states, by identifier and position, which terms of a model extension it
 * has a place for; {@code docs/templates.md} says what a position is. The renderer fills a
 * place only where the document carries the term, and marks what it fills as a displayed
 * figure rather than as a business term of the standard. That is what makes a gross layout
 * for a consumer possible without a core term of EN 16931-1 ever carrying a gross figure:
 * the figures shown are the ones the extension records, and the net figures, the VAT
 * breakdown and the totals of the standard stay on the page beside them.
 *
 * <h2>What a template may refer to</h2>
 *
 * <p>A reference is the name of a file beside the template, and it stays there: a name
 * that is absolute, that leaves the directory of the template or that is not a plain
 * relative path is refused rather than followed, and so is one that reaches a file outside
 * that directory through a symbolic link, or that names something other than a regular
 * file — a device, a pipe, a directory. A file is measured before it is read and refused
 * past 32 MiB, and an image is measured before it is decoded and refused past 36 million
 * pixels, the size of a full page at 600 dots per inch: an image is decoded whole, at four
 * bytes a pixel and more, so a small file that declares a vast picture would otherwise cost
 * the heap of the run. {@link Files} is the interface behind the references, so a caller
 * that keeps its templates somewhere other than in a directory — a classpath, an archive,
 * a database — answers them itself; the bound on a file and the bound on an image hold for
 * what it answers too.
 *
 * <p>A template is configuration of the party that renders, as trusted as the rest of its
 * configuration, and these bounds do not make it anything else. They are there so that a
 * mistake in one — a link that points somewhere else, a scan saved at the wrong resolution
 * — is an error that names the file rather than a heap that runs out.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class RenderTemplate {

    private final Template model;

    private RenderTemplate(Template model) {
        this.model = model;
    }

    /**
     * Reads a template and the files it names beside it.
     *
     * @param file the template file
     * @return the template
     * @throws TemplateException    if the file, or a file it refers to, is not what a
     *                              template says it is
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static RenderTemplate read(Path file) {
        return new RenderTemplate(Template.read(Objects.requireNonNull(file, "file")));
    }

    /**
     * Reads a template whose referenced files a caller answers itself.
     *
     * @param json  the template file
     * @param files what answers the names the template refers to
     * @return the template
     * @throws TemplateException    if the template, or a file it refers to, is not what a
     *                              template says it is
     * @throws NullPointerException if an argument is {@code null}
     */
    public static RenderTemplate of(byte[] json, Files files) {
        return new RenderTemplate(Template.of(json, files));
    }

    /**
     * Returns what the template calls itself.
     *
     * @return the {@code name} member, or {@code "unnamed"} where the file has none
     */
    public String name() {
        return model.name();
    }

    /**
     * Returns the parsed template the layouts read.
     *
     * @return the model
     */
    Template model() {
        return model;
    }

    /**
     * What answers the names a template refers to.
     *
     * <p>An extension point: a caller implements it, and a later release adds to it only
     * default methods.
     */
    @FunctionalInterface
    public interface Files {

        /**
         * Returns the bytes of a file a template named.
         *
         * @param reference the name, as the template writes it
         * @return the file
         * @throws TemplateException if there is no such file
         */
        byte[] read(String reference);
    }
}
