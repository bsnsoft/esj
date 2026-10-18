import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the inputs of this directory beside this file.
 *
 *     java dist/inputs/Make.java
 *
 * run from the root of the repository with a JDK of 17 or later. Every input is made from a
 * document the repository already carries — the corpus invoice 01.01a in UBL and in CII, the
 * standard ESJ example and the example hybrid PDF — by one change that says in this source
 * what makes it the case it is: cut short, one character or tag altered, a declaration added,
 * the same text in another encoding, or the parts of a PDF put together around a damaged one.
 * Characters an encoding has no byte for are written as character references, so that the
 * text of an invoice is the same in every encoding it is written in.
 */
public final class Make {

    /** The corpus invoice in UBL, and the same invoice in CII. */
    private static final Path UBL =
            Path.of("conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml");
    private static final Path CII =
            Path.of("conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml");

    /** The standard ESJ example. */
    private static final Path ESJ = Path.of("examples/standard-invoice.esj.json");

    /** The example hybrid invoice, whose XMP packet the PDFs here carry. */
    private static final Path HYBRID = Path.of("conformance/pdf/factur-x.pdf");

    /** Where the inputs go: beside this file. */
    private static final Path HERE = Path.of("dist/inputs");

    /** How much of a document a cut-short one keeps: the head, the parties begun. */
    private static final int CUT = 1500;

    /** The seller's trading name in the corpus invoice, which a recoded copy writes in its script. */
    private static final String SELLER = "[Seller trading name]";

    public static void main(String[] args) throws IOException {
        byte[] ublBytes = Files.readAllBytes(UBL);
        String ubl = new String(ublBytes, StandardCharsets.UTF_8);
        byte[] cii = Files.readAllBytes(CII);
        byte[] esj = Files.readAllBytes(ESJ);
        byte[] hybrid = Files.readAllBytes(HYBRID);

        // Not well-formed: the parser of the Java runtime refuses each, in a sentence of its own.
        write("truncated.xml", Arrays.copyOf(ublBytes, CUT));
        write("mismatched-tag.xml", utf8(once(ubl, "<cbc:ID>123456XX</cbc:ID>",
                "<cbc:ID>123456XX</cbc:Id>")));
        write("undeclared-entity.xml", utf8(once(ubl, "<cbc:ID>123456XX</cbc:ID>",
                "<cbc:ID>123456XX&nbsp;</cbc:ID>")));
        write("control-character.xml", utf8(once(ubl, "<cbc:ID>123456XX</cbc:ID>",
                "<cbc:ID>123456\u0001XX</cbc:ID>")));
        write("after-root.xml", utf8(ubl.stripTrailing() + "\n<extra/>\n"));

        // Refused before it is read: a document type declaration, an encoding nobody has.
        write("doctype.xml", utf8(once(once(ubl, "?>\n",
                "?>\n<!DOCTYPE ubl:Invoice [ <!ENTITY seller \"Seller\"> ]>\n"),
                SELLER, "&seller;")));
        write("unknown-encoding.xml", utf8(declare(ubl, "no-such-charset")));

        // Bytes of another encoding than the one declared, which a reader repairs or refuses.
        write("latin1-declared-utf8.xml",
                encode(ubl, StandardCharsets.ISO_8859_1));
        write("cp1252-declared-utf8.xml",
                encode(once(ubl, SELLER, "Seller € GmbH"), Charset.forName("windows-1252")));

        // Well-formed documents in an encoding other than UTF-8, each declared.
        write("windows-1252.xml", encode(declare(once(ubl, SELLER, "Seller € GmbH"),
                "windows-1252"), Charset.forName("windows-1252")));
        write("iso-8859-15.xml", encode(declare(once(ubl, SELLER, "Seller € GmbH"),
                "ISO-8859-15"), Charset.forName("ISO-8859-15")));
        write("shift-jis.xml", encode(declare(once(ubl, SELLER,
                "株式会社サンプル"), "Shift_JIS"),
                Charset.forName("Shift_JIS")));
        write("utf-16.xml", utf16(declare(ubl, "UTF-16")));

        // Well-formed and against the schema of its syntax: an element it does not define, a
        // date that is none.
        write("schema-invalid.xml", utf8(once(ubl, "<cbc:IssueDate>",
                "<cbc:NoSuchElement>x</cbc:NoSuchElement>\n    <cbc:IssueDate>")));
        write("datatype-invalid.xml", utf8(once(ubl, "<cbc:IssueDate>2016-04-04</cbc:IssueDate>",
                "<cbc:IssueDate>2016-13-45</cbc:IssueDate>")));

        // ESJ that is no JSON text, and JSON that is not UTF-8.
        write("truncated.esj.json", Arrays.copyOf(esj, 700));
        String json = new String(esj, StandardCharsets.UTF_8);
        write("syntax-error.esj.json", utf8(once(json, "\"/BT-1\": \"RE-2026-0042\",",
                "\"/BT-1\": \"RE-2026-0042\",,")));
        write("not-utf8.esj.json", insert(esj, "\"/BT-1\": \"", new byte[] {(byte) 0xff,
                (byte) 0xfe}));

        // PDFs: cut short, no PDF behind the header, and a sound container around a damaged part.
        write("truncated.pdf", Arrays.copyOf(hybrid, 1800));
        write("garbage.pdf", ascii("%PDF-1.7\nthis is not the body of a PDF\n"));
        byte[] xmp = between(hybrid, "<?xpacket begin", "<?xpacket end=\"w\"?>");
        write("attachment-truncated.pdf", pdf(Arrays.copyOf(cii, CUT), xmp));
        write("attachment-doctype.pdf", pdf(utf8(once(new String(cii, StandardCharsets.UTF_8),
                "?>\n", "?>\n<!DOCTYPE rsm:CrossIndustryInvoice [ <!ENTITY x \"x\"> ]>\n")), xmp));
        write("xmp-malformed.pdf", pdf(cii, Arrays.copyOf(xmp, 400)));

        // Nothing at all.
        write("empty.xml", new byte[0]);
    }

    /** Replaces the one occurrence of a text, and fails where there is none or more than one. */
    private static String once(String text, String old, String replacement) {
        int at = text.indexOf(old);
        if (at < 0 || text.indexOf(old, at + 1) >= 0) {
            throw new IllegalStateException("not exactly once: " + old);
        }
        return text.substring(0, at) + replacement + text.substring(at + old.length());
    }

    /** Writes another encoding into the XML declaration. */
    private static String declare(String xml, String encoding) {
        return once(xml, "encoding=\"UTF-8\"", "encoding=\"" + encoding + "\"");
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /** UTF-16 with a byte order mark, big-endian, as Java writes it. */
    private static byte[] utf16(String text) {
        return text.getBytes(StandardCharsets.UTF_16);
    }

    /** The text in an encoding, a character it has no byte for written as a reference. */
    private static byte[] encode(String text, Charset charset) throws CharacterCodingException {
        CharsetEncoder encoder = charset.newEncoder();
        StringBuilder written = new StringBuilder();
        text.codePoints().forEach(point -> {
            String character = new String(Character.toChars(point));
            written.append(encoder.canEncode(character) ? character : "&#" + point + ";");
        });
        ByteBuffer bytes = encoder.encode(CharBuffer.wrap(written));
        return Arrays.copyOf(bytes.array(), bytes.limit());
    }

    /** The bytes with others put in after the first occurrence of a marker. */
    private static byte[] insert(byte[] bytes, String marker, byte[] inserted) {
        int at = indexOf(bytes, ascii(marker), 0) + marker.length();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, at);
        out.writeBytes(inserted);
        out.write(bytes, at, bytes.length - at);
        return out.toByteArray();
    }

    /** The bytes from the start of one marker to the end of another. */
    private static byte[] between(byte[] bytes, String start, String end) {
        int from = indexOf(bytes, ascii(start), 0);
        int to = indexOf(bytes, ascii(end), from) + end.length();
        return Arrays.copyOfRange(bytes, from, to);
    }

    private static int indexOf(byte[] bytes, byte[] marker, int from) {
        for (int at = from; at <= bytes.length - marker.length; at++) {
            if (Arrays.equals(bytes, at, at + marker.length, marker, 0, marker.length)) {
                return at;
            }
        }
        throw new IllegalStateException("not found: " + new String(marker, StandardCharsets.US_ASCII));
    }

    /**
     * A one-page hybrid invoice in the shape of the example: the invoice attached as
     * {@code factur-x.xml} with the media type {@code text/xml}, named in the name tree and in
     * the catalog's {@code /AF} array with the relationship {@code Alternative}, and an XMP
     * packet. Every object is written uncompressed and found through a cross-reference table.
     */
    private static byte[] pdf(byte[] invoice, byte[] xmp) {
        Map<Integer, byte[]> objects = new TreeMap<>();
        objects.put(1, ascii("<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R"
                + " /Names << /EmbeddedFiles << /Names [(factur-x.xml) 5 0 R] >> >>"
                + " /AF [5 0 R] >>"));
        objects.put(2, ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                + " /Resources << >> >>"));
        objects.put(4, stream("/Type /Metadata /Subtype /XML", xmp));
        objects.put(5, ascii("<< /Type /Filespec /F (factur-x.xml) /UF (factur-x.xml)"
                + " /AFRelationship /Alternative /EF << /F 6 0 R /UF 6 0 R >> >>"));
        objects.put(6, stream("/Type /EmbeddedFile /Subtype /text#2Fxml /Params << /Size "
                + invoice.length + " >>", invoice));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("%PDF-1.7\n"));
        out.writeBytes(new byte[] {'%', (byte) 0xe2, (byte) 0xe3, (byte) 0xcf, (byte) 0xd3, '\n'});
        Map<Integer, Integer> offsets = new TreeMap<>();
        objects.forEach((number, object) -> {
            offsets.put(number, out.size());
            out.writeBytes(ascii(number + " 0 obj\n"));
            out.writeBytes(object);
            out.writeBytes(ascii("\nendobj\n"));
        });
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1)
                + "\n0000000000 65535 f \n");
        offsets.values().forEach(offset -> table.append(String.format("%010d 00000 n \n", offset)));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(ascii(table.toString()));
        return out.toByteArray();
    }

    /** A stream object of the given dictionary entries and content. */
    private static byte[] stream(String entries, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("<< " + entries + " /Length " + content.length + " >>\nstream\n"));
        out.writeBytes(content);
        out.writeBytes(ascii("\nendstream"));
        return out.toByteArray();
    }

    private static void write(String name, byte[] bytes) throws IOException {
        Files.write(HERE.resolve(name), bytes);
    }
}
