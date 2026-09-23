package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 *         RenderOptions.in(RenderLanguage.GERMAN).with(template));
 * }</pre>
 *
 * <h2>The extension terms</h2>
 *
 * <p>A template states, by identifier and position, which terms of a model extension it
 * has a place for; {@link Placement} says what a position is. The renderer fills a place
 * only where the document carries the term, and marks what it fills as a displayed figure
 * rather than as a business term of the standard. That is what makes a gross layout for a
 * consumer possible without a core term of EN 16931-1 ever carrying a gross figure: the
 * figures shown are the ones the extension records, and the net figures, the VAT breakdown
 * and the totals of the standard stay on the page beside them.
 *
 * <h2>What a template may refer to</h2>
 *
 * <p>A reference is the name of a file beside the template, and it stays there: a name
 * that is absolute, that leaves the directory of the template or that is not a plain
 * relative path is refused rather than followed. {@link Files} is the interface behind
 * that, so a caller that keeps its templates somewhere other than in a directory — a
 * classpath, an archive, a database — answers the references itself.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class RenderTemplate {

    /** What the {@code template} member of a file this version reads says. */
    private static final String FORMAT = "esj-render-template/0.1";

    /** The largest file a template may refer to, in bytes. */
    private static final int MAX_REFERENCE_BYTES = 32 * 1024 * 1024;

    /** The first bytes of a PDF. */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    /** The first bytes of a PNG. */
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    /** The first bytes of a JPEG. */
    private static final byte[] JPEG_MAGIC = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};

    /**
     * The first bytes a TrueType file may begin with: the version {@code 1.0} of the
     * outline format, the tag {@code true} some files carry instead, and {@code ttcf} for
     * a collection. A font with CFF outlines begins with {@code OTTO} and is not one of
     * them, which is why it is named here rather than left to the parser.
     */
    private static final List<byte[]> TRUETYPE_MAGIC = List.of(
            new byte[] {0, 1, 0, 0}, new byte[] {'t', 'r', 'u', 'e'},
            new byte[] {'t', 't', 'c', 'f'});

    private final String name;
    private final Artwork letterheadFirst;
    private final Artwork letterheadFollowing;
    private final Logo logo;
    private final Palette palette;
    private final Margins marginsFirst;
    private final Margins marginsFollowing;
    private final byte[] regularFont;
    private final byte[] boldFont;
    private final List<Placement> placements;
    private final Layout layout;
    private final LetterOptions letter;
    private final Overrides overridesFirst;
    private final Overrides overridesFollowing;

    private RenderTemplate(String name, Artwork letterheadFirst, Artwork letterheadFollowing,
                           Logo logo, Palette palette, Margins marginsFirst,
                           Margins marginsFollowing, byte[] regularFont, byte[] boldFont,
                           List<Placement> placements, Layout layout, LetterOptions letter,
                           Overrides overridesFirst, Overrides overridesFollowing) {
        this.name = name;
        this.letterheadFirst = letterheadFirst;
        this.letterheadFollowing = letterheadFollowing;
        this.logo = logo;
        this.palette = palette;
        this.marginsFirst = marginsFirst;
        this.marginsFollowing = marginsFollowing;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.placements = placements;
        this.layout = layout;
        this.letter = letter;
        this.overridesFirst = overridesFirst;
        this.overridesFollowing = overridesFollowing;
    }

    /**
     * The margins one page kind of a template names, before they are put over the margins
     * of a layout. A member the template left out is {@code null} and keeps what the
     * layout decided.
     *
     * @param top    the top margin, or {@code null}
     * @param bottom the bottom margin, or {@code null}
     * @param left   the left margin, or {@code null}
     * @param right  the right margin, or {@code null}
     */
    private record Overrides(Float top, Float bottom, Float left, Float right) {

        /** Returns margins with these over them. */
        Margins over(Margins base) {
            return base.with(top, bottom, left, right);
        }
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
        Objects.requireNonNull(file, "file");
        Path directory = file.toAbsolutePath().normalize().getParent();
        return of(bytes(file), reference -> bytes(resolve(directory, reference)));
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
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(files, "files");
        Object tree = TemplateJson.read(json);
        String format = TemplateJson.text(tree, "template", "the render template");
        if (!FORMAT.equals(format)) {
            throw new TemplateException("a render template this version reads says \"template\":"
                    + " \"" + FORMAT + "\", and this one says '" + format + "'");
        }
        Object letterhead = TemplateJson.member(tree, "letterhead");
        Overrides firstOverrides = overrides(tree, "first");
        Overrides followingOverrides = overrides(tree, "following");
        Margins first = firstOverrides.over(Margins.defaults());
        Margins following = followingOverrides.over(first);
        if (first.left() != following.left() || first.right() != following.right()) {
            throw new TemplateException("margins: the first page and the pages after it keep"
                    + " the same left and right margin, because a table lays out its columns"
                    + " once");
        }
        Artwork firstSheet = artwork(letterhead, "first", files);
        return new RenderTemplate(
                name(tree),
                firstSheet,
                letterhead == null || TemplateJson.member(letterhead, "following") == null
                        ? firstSheet : artwork(letterhead, "following", files),
                logo(TemplateJson.member(tree, "logo"), files),
                palette(TemplateJson.member(tree, "colors")),
                first,
                following,
                font(tree, "regular", files),
                font(tree, "bold", files),
                placements(tree),
                layout(tree),
                letter(TemplateJson.member(tree, "letter")),
                firstOverrides,
                followingOverrides);
    }

    /**
     * Returns what the template calls itself.
     *
     * @return the {@code name} member, or {@code "unnamed"} where the file has none
     */
    public String name() {
        return name;
    }

    /** What answers the names a template refers to. */
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

    /**
     * Returns the layout this template asks for, empty where it names none: the choice is
     * then the caller's and, where the caller makes none either,
     * {@link RenderOptions#DEFAULT_LAYOUT}.
     */
    Optional<Layout> layout() {
        return Optional.ofNullable(layout);
    }

    /** Returns what this template decided about the letter itself. */
    LetterOptions letter() {
        return letter;
    }

    /**
     * Returns the margins of the first page with this template's own over them.
     *
     * @param base the margins of the layout
     * @return the margins
     */
    Margins marginsFirst(Margins base) {
        return overridesFirst.over(base);
    }

    /**
     * Returns the margins of every page after the first with this template's own over them.
     *
     * @param base the margins of the layout
     * @return the margins
     */
    Margins marginsFollowing(Margins base) {
        return overridesFollowing.over(marginsFirst(base));
    }

    /** Returns the colours of this template. */
    Palette palette() {
        return palette;
    }

    /** Returns the margins of the first page. */
    Margins marginsFirst() {
        return marginsFirst;
    }

    /** Returns the margins of every page after the first. */
    Margins marginsFollowing() {
        return marginsFollowing;
    }

    /** Returns the letterhead of the first page, or {@code null} where there is none. */
    Artwork letterheadFirst() {
        return letterheadFirst;
    }

    /** Returns the letterhead of the pages after it, or {@code null}. */
    Artwork letterheadFollowing() {
        return letterheadFollowing;
    }

    /** Returns the logo, or {@code null} where the template brings none. */
    Logo logo() {
        return logo;
    }

    /** Returns the regular face of this template, or {@code null} for the vendored one. */
    byte[] regularFont() {
        return regularFont;
    }

    /** Returns the bold face, or {@code null} for the vendored one. */
    byte[] boldFont() {
        return boldFont;
    }

    /** Returns the places this template gives to terms of a model extension. */
    List<Placement> placements() {
        return placements;
    }

    /**
     * A letterhead: one page of a PDF, or an image, to be drawn under the text of a page.
     *
     * @param bytes the file
     * @param kind  what kind of file it is
     * @param page  the page of a PDF to take, counted from one; ignored for an image
     */
    record Artwork(byte[] bytes, Kind kind, int page) {

        /** What a letterhead or a logo is stored as. */
        enum Kind {

            /** A PDF, of which one page is imported as a form. */
            PDF,

            /** A raster image: PNG or JPEG. */
            IMAGE
        }
    }

    /**
     * A logo, and where it goes on the page.
     *
     * <p>The position is measured the way a reader measures a page: {@code x} from the
     * left edge and {@code y} from the <em>top</em> edge, both to the top left corner of
     * the image, in points.
     *
     * @param bytes     the image
     * @param x         how far from the left edge
     * @param y         how far below the top edge
     * @param width     how wide it is drawn
     * @param height    how tall it is drawn
     * @param everyPage whether it goes on every page or on the first one only
     */
    record Logo(byte[] bytes, float x, float y, float width, float height, boolean everyPage) {
    }

    private static String name(Object tree) {
        String written = TemplateJson.text(tree, "name", "the render template");
        return written == null || written.isBlank() ? "unnamed" : written;
    }

    private static Palette palette(Object colors) {
        Palette defaults = Palette.defaults();
        if (colors == null) {
            return defaults;
        }
        return new Palette(
                ink(colors, "text", defaults.text()),
                ink(colors, "muted", defaults.muted()),
                ink(colors, "rule", defaults.rule()),
                ink(colors, "heading", defaults.heading()),
                ink(colors, "tableHeaderFill", defaults.tableHeaderFill()),
                ink(colors, "tableHeaderText", defaults.tableHeaderText()));
    }

    private static Ink ink(Object colors, String name, Ink fallback) {
        String written = TemplateJson.text(colors, name, "colors");
        return written == null ? fallback : Ink.parse(written, "colors: " + name);
    }

    private static Overrides overrides(Object tree, String which) {
        Object page = TemplateJson.member(TemplateJson.member(tree, "margins"), which);
        if (page == null) {
            return new Overrides(null, null, null, null);
        }
        String where = "margins: " + which;
        return new Overrides(TemplateJson.number(page, "top", where),
                TemplateJson.number(page, "bottom", where),
                TemplateJson.number(page, "left", where),
                TemplateJson.number(page, "right", where));
    }

    /** Returns the layout a template asks for, or {@code null} where it says nothing. */
    private static Layout layout(Object tree) {
        String written = TemplateJson.text(tree, "layout", "the render template");
        if (written == null) {
            return null;
        }
        for (Layout layout : Layout.values()) {
            if (layout.name().toLowerCase(java.util.Locale.ROOT).equals(written)) {
                return layout;
            }
        }
        throw new TemplateException("layout is generic or letter, not '" + written + "'");
    }

    /** Returns what a template decided about the letter, or the defaults. */
    private static LetterOptions letter(Object options) {
        if (options == null) {
            return LetterOptions.defaults();
        }
        LetterOptions defaults = LetterOptions.defaults();
        String window = TemplateJson.text(options, "addressWindow", "letter");
        String details = TemplateJson.text(options, "sellerDetails", "letter");
        String information = TemplateJson.text(options, "information", "letter");
        Float printedHead = TemplateJson.number(options, "printedHead", "letter");
        return new LetterOptions(
                window == null ? defaults.window() : LetterOptions.Window.of(window),
                flag(options, "foldMarks"),
                flag(options, "holeMark"),
                details == null ? defaults.sellerDetails()
                        : LetterOptions.SellerDetails.of(details),
                information == null ? defaults.information()
                        : LetterOptions.Information.of(information),
                printedHead == null ? defaults.printedHead() : printedHead,
                flag(options, "paymentCode", defaults.paymentCode()));
    }

    /** Returns a boolean member of the letter, false where the template says nothing. */
    private static boolean flag(Object options, String name) {
        return flag(options, name, false);
    }

    /** Returns a boolean member of the letter, or a default where it says nothing. */
    private static boolean flag(Object options, String name, boolean absent) {
        Object member = TemplateJson.member(options, name);
        if (member == null) {
            return absent;
        }
        if (!(member instanceof Boolean value)) {
            throw new TemplateException("letter: " + name + " is true or false");
        }
        return value;
    }

    private static Artwork artwork(Object letterhead, String which, Files files) {
        Object sheet = TemplateJson.member(letterhead, which);
        if (sheet == null) {
            return null;
        }
        String where = "letterhead: " + which;
        String reference = required(TemplateJson.text(sheet, "file", where), where + ": file");
        byte[] bytes = read(files, reference);
        Artwork.Kind kind = kind(bytes, reference);
        Float page = TemplateJson.number(sheet, "page", where);
        int number = page == null ? 1 : Math.round(page);
        if (kind == Artwork.Kind.PDF && number < 1) {
            throw new TemplateException(where + ": page is the page of the PDF to take,"
                    + " counted from one, not " + number);
        }
        return new Artwork(bytes, kind, number);
    }

    private static Logo logo(Object logo, Files files) {
        if (logo == null) {
            return null;
        }
        String where = "logo";
        byte[] bytes = read(files,
                required(TemplateJson.text(logo, "file", where), where + ": file"));
        if (kind(bytes, "the logo") != Artwork.Kind.IMAGE) {
            throw new TemplateException("logo: a logo is a PNG or a JPEG");
        }
        float width = size(TemplateJson.number(logo, "width", where), where + ": width");
        float height = size(TemplateJson.number(logo, "height", where), where + ": height");
        Float x = TemplateJson.number(logo, "x", where);
        Float y = TemplateJson.number(logo, "y", where);
        String pages = TemplateJson.text(logo, "pages", where);
        if (pages != null && !"first".equals(pages) && !"all".equals(pages)) {
            throw new TemplateException("logo: pages is first or all, not '" + pages + "'");
        }
        return new Logo(bytes, x == null ? 0f : x, y == null ? 0f : y, width, height,
                "all".equals(pages));
    }

    private static float size(Float written, String where) {
        if (written == null || written <= 0) {
            throw new TemplateException(where + " is a positive number of points");
        }
        return written;
    }

    private static byte[] font(Object tree, String weight, Files files) {
        Object fonts = TemplateJson.member(tree, "fonts");
        if (fonts == null) {
            return null;
        }
        String regular = TemplateJson.text(fonts, "regular", "fonts");
        String bold = TemplateJson.text(fonts, "bold", "fonts");
        if (regular == null || bold == null) {
            throw new TemplateException("fonts: a template that brings a face brings both of"
                    + " them, regular and bold");
        }
        String reference = "regular".equals(weight) ? regular : bold;
        byte[] face = read(files, reference);
        if (TRUETYPE_MAGIC.stream().noneMatch(magic -> starts(face, magic))) {
            throw new TemplateException("fonts: " + reference + " is not a TrueType font,"
                    + " which is what this renderer embeds");
        }
        return face;
    }

    private static List<Placement> placements(Object tree) {
        List<Placement> placements = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object entry : TemplateJson.list(tree, "extensionTerms", "the render template")) {
            String where = "extensionTerms";
            String term = required(TemplateJson.text(entry, "term", where), where + ": term");
            if (!isExtensionTerm(term)) {
                throw new TemplateException(where + ": " + term + " is not a business term of"
                        + " an extension, and a template gives a place only to those — a term"
                        + " of EN 16931-1 has one already");
            }
            if (!seen.add(term)) {
                throw new TemplateException(where + ": " + term + " is placed twice");
            }
            Placement.Position position = Placement.Position.of(
                    required(TemplateJson.text(entry, "position", where), where + ": position"));
            placements.add(new Placement(term, position, labels(entry), type(entry)));
        }
        return List.copyOf(placements);
    }

    private static Map<RenderLanguage, String> labels(Object entry) {
        Object label = TemplateJson.member(entry, "label");
        Map<RenderLanguage, String> labels = new EnumMap<>(RenderLanguage.class);
        if (label == null) {
            return labels;
        }
        for (RenderLanguage language : RenderLanguage.values()) {
            String written = TemplateJson.text(label, language.code(), "extensionTerms: label");
            if (written != null && !written.isBlank()) {
                labels.put(language, written);
            }
        }
        return labels;
    }

    private static SemanticType type(Object entry) {
        String written = TemplateJson.text(entry, "type", "extensionTerms");
        if (written == null) {
            return null;
        }
        return SemanticType.findByRegistryDatatype(written).orElseThrow(() ->
                new TemplateException("extensionTerms: type is a semantic data type of"
                        + " EN 16931-1, such as Amount or UnitPriceAmount, not '"
                        + written + "'"));
    }

    /** Tells whether an identifier names a business term of an extension. */
    private static boolean isExtensionTerm(String term) {
        try {
            return SemanticPath.of("/" + term).termSegment().isExtension();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Artwork.Kind kind(byte[] bytes, String what) {
        if (starts(bytes, PDF_MAGIC)) {
            return Artwork.Kind.PDF;
        }
        if (starts(bytes, PNG_MAGIC) || starts(bytes, JPEG_MAGIC)) {
            return Artwork.Kind.IMAGE;
        }
        throw new TemplateException(what + " is neither a PDF nor a PNG nor a JPEG");
    }

    private static boolean starts(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] read(Files files, String reference) {
        byte[] bytes = files.read(reference);
        if (bytes == null) {
            throw new TemplateException("the render template refers to " + reference
                    + " and there is no such file");
        }
        if (bytes.length > MAX_REFERENCE_BYTES) {
            throw new TemplateException("the render template refers to " + reference
                    + ", which is " + bytes.length + " bytes and larger than the "
                    + MAX_REFERENCE_BYTES + " a template may bring");
        }
        return bytes;
    }

    private static String required(String value, String where) {
        if (value == null || value.isBlank()) {
            throw new TemplateException(where + " is required");
        }
        return value;
    }

    /**
     * Returns the path a reference names beside a template, refusing one that would leave
     * the directory the template stands in.
     */
    private static Path resolve(Path directory, String reference) {
        Path candidate = Path.of(reference);
        if (candidate.isAbsolute() || reference.contains("\\")) {
            throw new TemplateException("a render template refers to a file beside it, by a"
                    + " relative name, and '" + reference + "' is not one");
        }
        Path resolved = directory.resolve(candidate).normalize();
        if (!resolved.startsWith(directory)) {
            throw new TemplateException("a render template refers to a file beside it, and '"
                    + reference + "' leaves the directory of the template");
        }
        return resolved;
    }

    private static byte[] bytes(Path file) {
        try {
            return java.nio.file.Files.readAllBytes(file);
        } catch (IOException e) {
            throw new TemplateException("the render template file " + file.getFileName()
                    + " could not be read: " + e.getMessage(), e);
        }
    }
}
