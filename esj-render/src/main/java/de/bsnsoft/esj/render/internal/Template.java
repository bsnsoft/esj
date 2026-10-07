package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.render.Layout;
import de.bsnsoft.esj.render.RenderTemplate;
import de.bsnsoft.esj.render.RenderLanguage;
import de.bsnsoft.esj.render.TemplateException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
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
 * The parsed form of a render template, which {@link RenderTemplate} hands to the layouts.
 *
 * <p>The file format, the references and their bounds are described at
 * {@link RenderTemplate}; this class reads a file into the values the layouts ask for.
 * Instances are immutable and safe to share between threads.
 */
public final class Template {

    /** What the {@code template} member of a file this version reads says. */
    private static final String FORMAT = "esj-render-template/0.1";

    /** The largest file a template may refer to, in bytes. */
    static final int MAX_REFERENCE_BYTES = 32 * 1024 * 1024;

    /**
     * The largest image a template may bring, in pixels: a full page of A4 or of US letter
     * at 600 dots per inch, which is twice the resolution print is made at.
     */
    static final long MAX_IMAGE_PIXELS = 36_000_000L;

    /** The eight bytes every PNG begins with. */
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

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

    private Template(String name, Artwork letterheadFirst, Artwork letterheadFollowing,
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
    public static Template read(Path file) {
        Objects.requireNonNull(file, "file");
        Path directory = file.toAbsolutePath().normalize().getParent();
        return of(bytes(file), reference -> referenced(directory, reference));
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
    public static Template of(byte[] json, RenderTemplate.Files files) {
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
        return new Template(
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

    private static Artwork artwork(Object letterhead, String which, RenderTemplate.Files files) {
        Object sheet = TemplateJson.member(letterhead, which);
        if (sheet == null) {
            return null;
        }
        String where = "letterhead: " + which;
        String reference = required(TemplateJson.text(sheet, "file", where), where + ": file");
        byte[] bytes = read(files, reference);
        Artwork.Kind kind = kind(bytes, reference);
        if (kind == Artwork.Kind.IMAGE) {
            measured(bytes, reference);
        }
        Float page = TemplateJson.number(sheet, "page", where);
        int number = page == null ? 1 : Math.round(page);
        if (kind == Artwork.Kind.PDF && number < 1) {
            throw new TemplateException(where + ": page is the page of the PDF to take,"
                    + " counted from one, not " + number);
        }
        return new Artwork(bytes, kind, number);
    }

    private static Logo logo(Object logo, RenderTemplate.Files files) {
        if (logo == null) {
            return null;
        }
        String where = "logo";
        String reference = required(TemplateJson.text(logo, "file", where), where + ": file");
        byte[] bytes = read(files, reference);
        if (kind(bytes, "the logo") != Artwork.Kind.IMAGE) {
            throw new TemplateException("logo: a logo is a PNG or a JPEG");
        }
        measured(bytes, reference);
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

    private static byte[] font(Object tree, String weight, RenderTemplate.Files files) {
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

    /**
     * Refuses an image that is larger than {@link #MAX_IMAGE_PIXELS}, from the size its
     * header states, before anything decodes it.
     *
     * @param bytes     the image, a PNG or a JPEG
     * @param reference the name the template gave it, for the message
     * @throws TemplateException if it is larger, or if its header states no size
     */
    static void measured(byte[] bytes, String reference) {
        long[] size = starts(bytes, PNG_MAGIC) ? pngSize(bytes) : jpegSize(bytes);
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            throw new TemplateException("the render template refers to " + reference
                    + ", an image whose header does not state its width and height");
        }
        if (size[0] * size[1] > MAX_IMAGE_PIXELS) {
            throw new TemplateException("the render template refers to " + reference
                    + ", an image of " + size[0] + " × " + size[1] + " pixels, larger than"
                    + " the " + MAX_IMAGE_PIXELS + " pixels a template may bring");
        }
    }

    /**
     * Returns the width and the height a PNG states in its first chunk, which the format
     * requires to be the header, or {@code null} where it is not there.
     */
    private static long[] pngSize(byte[] bytes) {
        if (bytes.length < 24 || !starts(bytes, PNG_SIGNATURE)
                || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D'
                || bytes[15] != 'R') {
            return null;
        }
        return new long[] {unsigned(bytes, 16), unsigned(bytes, 20)};
    }

    /**
     * Returns the width and the height a JPEG states in its frame header, or {@code null}
     * where the segments before the image data hold none. The segments are walked by the
     * lengths they state, so nothing of the image data is read.
     */
    private static long[] jpegSize(byte[] bytes) {
        int at = 2;
        while (at + 3 < bytes.length) {
            if ((bytes[at] & 0xff) != 0xff) {
                return null;
            }
            int marker = bytes[at + 1] & 0xff;
            if (marker == 0xff) {
                at++;
                continue;
            }
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd8) {
                at += 2;
                continue;
            }
            if (marker == 0xd9 || marker == 0xda) {
                return null;
            }
            int length = (bytes[at + 2] & 0xff) << 8 | bytes[at + 3] & 0xff;
            if (length < 2) {
                return null;
            }
            boolean frame = marker >= 0xc0 && marker <= 0xcf
                    && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
            if (frame) {
                if (at + 8 >= bytes.length) {
                    return null;
                }
                long height = (bytes[at + 5] & 0xff) << 8 | bytes[at + 6] & 0xff;
                long width = (bytes[at + 7] & 0xff) << 8 | bytes[at + 8] & 0xff;
                return new long[] {width, height};
            }
            at += 2 + length;
        }
        return null;
    }

    /** Returns four bytes at an offset as an unsigned big-endian number. */
    private static long unsigned(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) << 24 | (bytes[offset + 1] & 0xffL) << 16
                | (bytes[offset + 2] & 0xffL) << 8 | bytes[offset + 3] & 0xffL;
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

    private static byte[] read(RenderTemplate.Files files, String reference) {
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
     * Returns the bytes of a file a template refers to in the directory it stands in,
     * refusing a name that leaves that directory, a link out of it, something that is not
     * a regular file and a file past {@link #MAX_REFERENCE_BYTES} — each before a byte of
     * it is read.
     */
    private static byte[] referenced(Path directory, String reference) {
        Path file = resolve(directory, reference);
        Path real;
        Path home;
        try {
            real = file.toRealPath();
            home = directory.toRealPath();
        } catch (NoSuchFileException e) {
            throw new TemplateException("the render template refers to " + reference
                    + " and there is no such file", e);
        } catch (IOException e) {
            throw new TemplateException("the render template refers to " + reference
                    + ", which could not be read: " + e.getMessage(), e);
        }
        if (!real.startsWith(home)) {
            throw new TemplateException("a render template refers to a file beside it, and '"
                    + reference + "' is a link to a file outside the directory of the"
                    + " template");
        }
        if (!java.nio.file.Files.isRegularFile(real)) {
            throw new TemplateException("the render template refers to " + reference
                    + ", which is not a regular file");
        }
        try {
            long size = java.nio.file.Files.size(real);
            if (size > MAX_REFERENCE_BYTES) {
                throw new TemplateException("the render template refers to " + reference
                        + ", which is " + size + " bytes and larger than the "
                        + MAX_REFERENCE_BYTES + " a template may bring");
            }
        } catch (IOException e) {
            throw new TemplateException("the render template refers to " + reference
                    + ", which could not be read: " + e.getMessage(), e);
        }
        return bounded(real, "the file " + reference + " the render template refers to");
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

    /**
     * Reads the template file itself, as far as {@link #MAX_REFERENCE_BYTES} and one byte
     * further: a template is a few kilobytes of JSON, and a name that turned out to be a
     * device that never ends is refused at that bound rather than read into the heap.
     */
    private static byte[] bytes(Path file) {
        return bounded(file, "the render template file " + file.getFileName());
    }

    /**
     * Reads a file, refusing it once it is past {@link #MAX_REFERENCE_BYTES}.
     *
     * @param file    the file
     * @param subject what the messages call it
     */
    private static byte[] bounded(Path file, String subject) {
        try (InputStream in = java.nio.file.Files.newInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_REFERENCE_BYTES) {
                    throw new TemplateException(subject + " is larger than the "
                            + MAX_REFERENCE_BYTES + " bytes a template may bring");
                }
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new TemplateException(subject + " could not be read: " + e.getMessage(), e);
        }
    }
}
