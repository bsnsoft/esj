package de.bsnsoft.esj.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * Builds the PDFs the tests of this module read.
 *
 * <p>They are generated rather than checked in, for two reasons. A hostile fixture that
 * is a file in the repository is a file somebody has to be careful with; and a fixture
 * built here says in its own source what makes it the case it is, which a binary never
 * does. The container is built with the same library that reads it, which is a real
 * limitation of these tests and is the reason the round trip through the conformance
 * corpus matters more than any single one of them.
 */
final class Pdfs {

    /** The conventional name of the invoice attachment of a Factur-X file. */
    static final String FACTUR_X = "factur-x.xml";

    /** The name a ZUGFeRD 1.0 file gives its invoice attachment. */
    static final String ZUGFERD_1 = "ZUGFeRD-invoice.xml";

    /** The relationship a hybrid invoice declares its XML with. */
    static final String ALTERNATIVE = "Alternative";

    /** The media type a hybrid invoice declares its XML with. */
    static final String XML = "text/xml";

    private Pdfs() {
        throw new AssertionError("no instances");
    }

    /** Returns a builder for a PDF with one page and nothing else. */
    static Builder builder() {
        return new Builder();
    }

    /** Returns the ordinary case: one attachment, in {@code /AF}, with a Factur-X packet. */
    static byte[] facturX(byte[] invoice) {
        return facturX(FACTUR_X, invoice, "EN 16931");
    }

    /** Returns a Factur-X file with an attachment name and a conformance level of choice. */
    static byte[] facturX(String name, byte[] invoice, String conformanceLevel) {
        return builder()
                .attach(Attachment.invoice(name, invoice))
                .xmp(xmp(name, conformanceLevel))
                .build();
    }

    /** Returns the XMP packet of a Factur-X file. */
    static String xmp(String fileName, String conformanceLevel) {
        return """
                <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                      <pdfaid:part>3</pdfaid:part>
                      <pdfaid:conformance>B</pdfaid:conformance>
                    </rdf:Description>
                    <rdf:Description rdf:about=""
                        xmlns:fx="urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#">
                      <fx:DocumentType>INVOICE</fx:DocumentType>
                      <fx:DocumentFileName>%s</fx:DocumentFileName>
                      <fx:Version>1.0</fx:Version>
                      <fx:ConformanceLevel>%s</fx:ConformanceLevel>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """.formatted(fileName, conformanceLevel);
    }

    /**
     * Returns the XMP packet of a PDF/A file that says nothing about an invoice.
     *
     * <p>It is what a rendering carries before anything is embedded into it, written here
     * so that the tests of the embedding have an input in every part of ISO 19005 and not
     * only in the one this project writes.
     *
     * @param part        the part the file declares, {@code pdfaid:part}
     * @param conformance the level it declares, {@code pdfaid:conformance}
     * @return the packet
     */
    static String pdfaXmp(int part, String conformance) {
        return """
                <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                      <pdfaid:part>%d</pdfaid:part>
                      <pdfaid:conformance>%s</pdfaid:conformance>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """.formatted(part, conformance);
    }

    /** Returns a PDF/A-3B file with one page, no attachment and nothing about an invoice. */
    static byte[] pdfa3() {
        return builder().xmp(pdfaXmp(3, "B")).build();
    }

    /**
     * One file to attach.
     *
     * @param name         the name the file specification gives it
     * @param content      the bytes
     * @param mediaType    the media type the embedded file stream declares, or
     *                     {@code null} for none
     * @param relationship the {@code /AFRelationship}, or {@code null} for none
     * @param associated   whether the catalog's {@code /AF} array refers to it
     * @param declaredSize the size the embedded file stream declares, which need not be
     *                     the size of the content
     * @param encoded      whether {@code content} is already the encoded stream of the
     *                     attachment rather than its bytes, which is how a stream that
     *                     decodes to more than this machine holds is written
     * @param filter       the filter the stream declares, or {@code null} for Flate
     * @param decodeParms  the {@code /DecodeParms} the stream declares, or {@code null}
     *                     for none
     */
    record Attachment(String name,
                      byte[] content,
                      String mediaType,
                      String relationship,
                      boolean associated,
                      long declaredSize,
                      boolean encoded,
                      COSName filter,
                      COSDictionary decodeParms) {

        /**
         * Creates an attachment whose stream declares no decode parameters, which is
         * every attachment but the one fixture that is about them.
         *
         * @param name         the name the file specification gives it
         * @param content      the bytes
         * @param mediaType    the media type, or {@code null} for none
         * @param relationship the {@code /AFRelationship}, or {@code null} for none
         * @param associated   whether the catalog's {@code /AF} array refers to it
         * @param declaredSize the size the embedded file stream declares
         * @param encoded      whether {@code content} is the encoded stream already
         * @param filter       the filter the stream declares, or {@code null} for Flate
         */
        Attachment(String name,
                   byte[] content,
                   String mediaType,
                   String relationship,
                   boolean associated,
                   long declaredSize,
                   boolean encoded,
                   COSName filter) {
            this(name, content, mediaType, relationship, associated, declaredSize, encoded,
                    filter, null);
        }

        /** Returns an ordinary XML attachment of a hybrid invoice. */
        static Attachment invoice(String name, byte[] content) {
            return new Attachment(name, content, XML, ALTERNATIVE, true, content.length,
                    false, null, null);
        }

        /**
         * Returns an attachment whose Flate stream decodes to {@code size} bytes of
         * nothing, which is the cheapest denial of service a container carries: a
         * compressed stream whose ratio the writer of the file chooses.
         */
        static Attachment bomb(String name, long size) {
            return new Attachment(name, deflatedZeros(size), "application/octet-stream",
                    "Supplement", false, size, true, null, null);
        }

        /**
         * Returns an attachment whose Flate stream declares a predictor of the width the
         * caller chooses, which is the number the library builds two row buffers from
         * before either decoder writes a byte.
         */
        static Attachment predicted(String name, byte[] raw, int columns) {
            return predicted(name, raw, predictor(columns));
        }

        /**
         * Returns an attachment whose Flate stream declares the predictor parameters the
         * caller spells out, whatever this format says about them.
         */
        static Attachment predicted(String name, byte[] raw, COSDictionary parameters) {
            return new Attachment(name, raw, XML, ALTERNATIVE, true, raw.length, true,
                    COSName.FLATE_DECODE, parameters);
        }

        /**
         * Returns an attachment whose stream declares a filter of the caller's choosing
         * and whose bytes are written as they are.
         */
        static Attachment filtered(String name, byte[] raw, COSName filter, String relationship) {
            return new Attachment(name, raw, "application/octet-stream", relationship,
                    false, raw.length, true, filter, null);
        }
    }

    /** Returns the parameters of a PNG predictor over one component of eight bits. */
    static COSDictionary predictor(int columns) {
        return predictor(12, 1, 8, columns);
    }

    /**
     * Returns predictor parameters as the caller spells them, including combinations this
     * format does not define.
     *
     * @param predictor the algorithm
     * @param colors    how many colour components a sample carries
     * @param bits      how many bits a component carries
     * @param columns   how many samples a row carries
     * @return the parameters
     */
    static COSDictionary predictor(int predictor, int colors, int bits, int columns) {
        COSDictionary parameters = new COSDictionary();
        parameters.setInt(COSName.PREDICTOR, predictor);
        parameters.setInt(COSName.COLORS, colors);
        parameters.setInt(COSName.BITS_PER_COMPONENT, bits);
        parameters.setInt(COSName.COLUMNS, columns);
        return parameters;
    }

    /**
     * Returns a container that declares encryption in a trailer the library cannot use.
     *
     * <p>The {@code startxref} points nowhere and the file carries no {@code /Info}, which
     * is the one shape in which the brute-force rebuild of the trailer drops
     * {@code /Encrypt}: the trailer the library ends up with says nothing about
     * encryption, and the encryption dictionary is reached only as an object of the file.
     *
     * @param invoice the Flate bytes of the attachment
     * @return the file
     */
    static byte[] declaredEncryptionWithoutAUsableTrailer(byte[] invoice) {
        String head = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles"
                + " 5 0 R >> /AF [6 0 R] >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n"
                + "5 0 obj\n<< /Names [(" + FACTUR_X + ") 6 0 R] >>\nendobj\n"
                + "6 0 obj\n<< /Type /Filespec /F (" + FACTUR_X + ") /UF (" + FACTUR_X + ")"
                + " /AFRelationship /" + ALTERNATIVE + " /EF << /F 7 0 R >> >>\nendobj\n"
                + "7 0 obj\n<< /Type /EmbeddedFile /Subtype /text#2Fxml"
                + " /Filter /FlateDecode /Length " + invoice.length + " >>\nstream\n";
        String tail = "\nendstream\nendobj\n"
                + "12 0 obj\n<< /Filter /Standard /V 4 /R 4 /Length 128"
                + " /CF << /StdCF << /CFM /V2 /Length 16 >> >> /StmF /Identity"
                + " /StrF /Identity /O <00> /U <00> /P -1 >>\nendobj\n"
                + "trailer\n<< /Size 13 /Root 1 0 R /Encrypt 12 0 R /ID [<00> <00>]"
                + " >>\nstartxref\n999999999\n%%EOF\n";
        return file(head, invoice, tail);
    }

    /**
     * Returns the same file with the version and the revision of the security handler
     * written as indirect references.
     *
     * <p>A value behind a reference is the same value — {@code /V 13 0 R} declares the
     * same version as {@code /V 4} — and a reader that recognized the shape of an
     * encryption dictionary only where the file spelled it out would be told by the file
     * whether to look.
     *
     * @param invoice the Flate bytes of the attachment
     * @return the file
     */
    static byte[] declaredEncryptionWithIndirectVersion(byte[] invoice) {
        String head = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles"
                + " 5 0 R >> /AF [6 0 R] >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n"
                + "5 0 obj\n<< /Names [(" + FACTUR_X + ") 6 0 R] >>\nendobj\n"
                + "6 0 obj\n<< /Type /Filespec /F (" + FACTUR_X + ") /UF (" + FACTUR_X + ")"
                + " /AFRelationship /" + ALTERNATIVE + " /EF << /F 7 0 R >> >>\nendobj\n"
                + "7 0 obj\n<< /Type /EmbeddedFile /Subtype /text#2Fxml"
                + " /Filter /FlateDecode /Length " + invoice.length + " >>\nstream\n";
        String tail = "\nendstream\nendobj\n"
                + "12 0 obj\n<< /Filter /Standard /V 13 0 R /R 14 0 R /O <00> /U <00>"
                + " /P -1 >>\nendobj\n"
                + "13 0 obj\n4\nendobj\n"
                + "14 0 obj\n4\nendobj\n"
                + "trailer\n<< /Size 15 /Root 1 0 R /Encrypt 12 0 R /ID [<00> <00>]"
                + " >>\nstartxref\n999999999\n%%EOF\n";
        return file(head, invoice, tail);
    }

    /**
     * Returns the same file with the encryption dictionary inside an object stream that
     * nothing in the file refers to.
     *
     * <p>This is the shape the refusal of an encrypted file does not reach. The trailer
     * the parse ends with is rebuilt without {@code /Encrypt}, and the object the entry
     * pointed at is compressed into an object stream whose objects are never asked for,
     * so no parse of the file ever holds the dictionary. Nothing is decrypted either —
     * that is the point — so the attachment beside it stays whatever its bytes are.
     *
     * @param attachmentStream the bytes of the attachment stream, which the file declares
     *                         as Flate
     * @return the file
     */
    static byte[] declaredEncryptionInsideAnObjectStream(byte[] attachmentStream) {
        String pairs = "20 0\n";
        byte[] objects = deflated((pairs + "<< /Filter /Standard /V 4 /R 4 /O <00>"
                + " /U <00> /P -1 >>").getBytes(StandardCharsets.ISO_8859_1));
        String head = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles"
                + " 5 0 R >> /AF [6 0 R] >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n"
                + "5 0 obj\n<< /Names [(" + FACTUR_X + ") 6 0 R] >>\nendobj\n"
                + "6 0 obj\n<< /Type /Filespec /F (" + FACTUR_X + ") /UF (" + FACTUR_X + ")"
                + " /AFRelationship /" + ALTERNATIVE + " /EF << /F 7 0 R >> >>\nendobj\n"
                + "7 0 obj\n<< /Type /EmbeddedFile /Subtype /text#2Fxml"
                + " /Filter /FlateDecode /Length " + attachmentStream.length
                + " >>\nstream\n";
        String tail = "\nendstream\nendobj\n"
                + "10 0 obj\n<< /Type /ObjStm /N 1 /First " + pairs.length()
                + " /Filter /FlateDecode /Length " + objects.length + " >>\nstream\n"
                + new String(objects, StandardCharsets.ISO_8859_1)
                + "\nendstream\nendobj\n"
                + "trailer\n<< /Size 21 /Root 1 0 R /Encrypt 20 0 R /ID [<00> <00>]"
                + " >>\nstartxref\n999999999\n%%EOF\n";
        return file(head, attachmentStream, tail);
    }

    /**
     * Returns a PDF whose object stream inflates to {@code size} bytes of nothing.
     *
     * <p>The file is written byte by byte rather than by the library, because the library
     * will not produce one: an object stream is what a parser decodes for itself while it
     * opens a file, so a hostile one is the shape of container that ends a process before
     * a single attachment has been looked at.
     */
    static byte[] objectStreamBomb(long size) {
        return objectStreamBomb(deflatedZeros(size), "/Type /ObjStm", "");
    }

    /**
     * Returns the same file with the dictionary of the object stream written as the
     * caller spells it.
     *
     * <p>The spellings are the point of these fixtures. A bound that reads the dictionary
     * of a stream with a scan of its own holds only where the scan agrees with the
     * library's lexer, and every one of these files is a place where two such readings
     * can part: a comment between a name and its value, a string holding the bracket pair
     * that ends a dictionary, a name written with {@code #xx} escapes, a length written
     * as a reference, padding wider than any window. The library reads all of them as
     * what they say, which is why the bound belongs where the library hands the stream
     * over rather than in front of it.
     *
     * @param payload         the Flate stream the object stream carries
     * @param type            what stands in the dictionary before the mandatory entries
     * @param afterDictionary what stands between the dictionary and the stream keyword
     * @return the file
     */
    static byte[] objectStreamBomb(byte[] payload, String type, String afterDictionary) {
        return objectStreamBomb(payload, type, afterDictionary,
                "/Length " + payload.length, "");
    }

    /**
     * Returns the same file with the length entry and any further object written as the
     * caller spells them.
     *
     * @param payload         the Flate stream the object stream carries
     * @param type            what stands in the dictionary before the mandatory entries
     * @param afterDictionary what stands between the dictionary and the stream keyword
     * @param length          the length entry, which may be a reference
     * @param extraObjects    objects written after the object stream
     * @return the file
     */
    static byte[] objectStreamBomb(byte[] payload,
                                   String type,
                                   String afterDictionary,
                                   String length,
                                   String extraObjects) {
        return objectStreamFile(type + " /N 1 /First 8 /Filter /FlateDecode " + length,
                afterDictionary, payload, extraObjects);
    }

    /**
     * Returns the same file with the whole dictionary of the object stream written as the
     * caller spells it, so that a fixture can choose the filter chain or the number of
     * objects the stream declares.
     *
     * @param dictionary what stands between the brackets of the stream dictionary
     * @param payload    the bytes of the stream
     * @return the file
     */
    static byte[] objectStreamWith(String dictionary, byte[] payload) {
        return objectStreamFile(dictionary, "", payload, "");
    }

    private static byte[] objectStreamFile(String dictionary,
                                           String afterDictionary,
                                           byte[] payload,
                                           String extraObjects) {
        StringBuilder body = new StringBuilder()
                .append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
                .append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
                .append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                        + " >>\nendobj\n")
                .append("10 0 obj\n<< ").append(dictionary)
                .append(" >>").append(afterDictionary).append("\nstream\n");
        return file(body.toString(), payload,
                "\nendstream\nendobj\n" + extraObjects + "trailer\n<< /Size 12 /Root 1 0 R"
                        + " >>\nstartxref\n999999999\n%%EOF\n");
    }

    /**
     * Returns a well-formed container — a valid cross-reference stream, one invoice
     * attachment — in which the {@code /Filter} of the attachment is written as an
     * indirect reference to an object that lives inside an object stream, and that object
     * stream carries {@code payload}.
     *
     * <p>The point of the shape is when the reference is resolved: while this module is
     * decoding the attachment, which is the one window in which its budget is suspended.
     * A suspension that named the container rather than the stream would let the library
     * decode the object stream unmeasured.
     *
     * @param invoice the Flate bytes of the attachment
     * @param payload the Flate bytes of the object stream
     * @return the file
     */
    static byte[] indirectFilterInObjectStream(byte[] invoice, byte[] payload) {
        // Object 20 is the name /FlateDecode, and it lives inside object stream 10.
        String first = "20 0 ";
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.writeBytes(HEADER.getBytes(StandardCharsets.ISO_8859_1));
        Map<Integer, Integer> offsets = new LinkedHashMap<>();
        offsets.put(1, pdf.size());
        text(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles 5 0 R >>"
                + " /AF [6 0 R] >>\nendobj\n");
        offsets.put(2, pdf.size());
        text(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        offsets.put(3, pdf.size());
        text(pdf, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n");
        offsets.put(5, pdf.size());
        text(pdf, "5 0 obj\n<< /Names [(" + FACTUR_X + ") 6 0 R] >>\nendobj\n");
        offsets.put(6, pdf.size());
        text(pdf, "6 0 obj\n<< /Type /Filespec /F (" + FACTUR_X + ") /UF (" + FACTUR_X + ")"
                + " /AFRelationship /" + ALTERNATIVE + " /EF << /F 7 0 R >> >>\nendobj\n");
        offsets.put(7, pdf.size());
        text(pdf, "7 0 obj\n<< /Type /EmbeddedFile /Subtype /text#2Fxml /Filter 20 0 R"
                + " /Length " + invoice.length + " >>\nstream\n");
        pdf.writeBytes(invoice);
        text(pdf, "\nendstream\nendobj\n");
        offsets.put(10, pdf.size());
        text(pdf, "10 0 obj\n<< /Type /ObjStm /N 1 /First " + first.length()
                + " /Filter /FlateDecode /Length " + payload.length + " >>\nstream\n");
        pdf.writeBytes(payload);
        text(pdf, "\nendstream\nendobj\n");
        int xref = pdf.size();
        ByteArrayOutputStream table = new ByteArrayOutputStream();
        for (int n = 0; n <= 20; n++) {
            if (offsets.containsKey(n)) {
                entry(table, 1, offsets.get(n), 0);
            } else if (n == 11) {
                entry(table, 1, xref, 0);
            } else if (n == 20) {
                entry(table, 2, 10, 0);
            } else {
                entry(table, 0, 0, 65535);
            }
        }
        byte[] entries = table.toByteArray();
        text(pdf, "11 0 obj\n<< /Type /XRef /Size 21 /Root 1 0 R /W [1 4 2] /Length "
                + entries.length + " >>\nstream\n");
        pdf.writeBytes(entries);
        text(pdf, "\nendstream\nendobj\nstartxref\n" + xref + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    /** Writes one cross-reference stream entry in the widths {@code /W [1 4 2]}. */
    private static void entry(ByteArrayOutputStream table, int type, int second, int third) {
        table.write(type);
        table.write(second >>> 24);
        table.write(second >>> 16);
        table.write(second >>> 8);
        table.write(second);
        table.write(third >>> 8);
        table.write(third);
    }

    private static void text(ByteArrayOutputStream out, String written) {
        out.writeBytes(written.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Returns a Flate stream that decodes to {@code size} bytes and carries the bytes of
     * the {@code endstream} keyword verbatim inside it.
     *
     * <p>Deflate emits input it cannot compress as a stored block, so the keyword
     * survives the compression and stands in the file at an offset an attacker chooses.
     * A scan that looks for the keyword to find where a stream ends cuts the stream
     * there and measures the first few bytes of it.
     *
     * @param size how many bytes the stream decodes to
     * @return the stream
     */
    static byte[] deflatedWithEndstreamInside(long size) {
        Deflater deflater = new Deflater(Deflater.NO_COMPRESSION);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1 << 16];
        // A stored block: deflate emits input it is told not to compress as it stands,
        // so the keyword survives the compression and stands in the file verbatim.
        deflater.setInput("\nendstream\n".getBytes(StandardCharsets.ISO_8859_1));
        while (!deflater.needsInput()) {
            out.write(buffer, 0,
                    deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH));
        }
        deflater.setLevel(Deflater.BEST_COMPRESSION);
        byte[] zeros = new byte[1 << 16];
        long written = 0;
        while (written < size) {
            int block = (int) Math.min(zeros.length, size - written);
            deflater.setInput(zeros, 0, block);
            written += block;
            while (!deflater.needsInput()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
        }
        deflater.finish();
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /**
     * Returns a PDF whose cross-reference stream inflates to {@code size} bytes of
     * nothing, which is the same attack one level further down: the table that finds the
     * objects is itself a stream the parser decodes before it can find anything.
     *
     * @param size how many bytes the stream decodes to
     * @return the file
     */
    static byte[] crossReferenceStreamBomb(long size) {
        return crossReferenceStreamBomb(deflatedZeros(size), "");
    }

    /**
     * Returns the same file with further entries in the dictionary of the
     * cross-reference stream, so that a predictor or any other parameter can be given.
     *
     * @param payload the Flate stream the cross-reference stream carries
     * @param extra   what to add to the dictionary
     * @return the file
     */
    static byte[] crossReferenceStreamBomb(byte[] payload, String extra) {
        String objects = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n";
        int offset = HEADER.length() + objects.length();
        String head = objects + "4 0 obj\n<< /Type /XRef /Size 5 /Root 1 0 R /W [1 4 2]"
                + extra + " /Filter /FlateDecode /Length " + payload.length + " >>\nstream\n";
        return file(head, payload,
                "\nendstream\nendobj\nstartxref\n" + offset + "\n%%EOF\n");
    }

    /** The header every hand-written fixture of this class carries. */
    private static final String HEADER = "%PDF-1.5\n%\u00e2\u00e3\u00cf\u00d3\n";

    /** Joins a hand-written file out of its text and the one stream that is not text. */
    private static byte[] file(String head, byte[] stream, String tail) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes((HEADER + head).getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(stream);
        out.writeBytes(tail.getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    /**
     * Returns the Flate stream of some bytes, for a fixture that has to write the encoded
     * form of an attachment itself.
     *
     * @param bytes what the stream decodes to
     * @return the stream
     */
    static byte[] deflated(byte[] bytes) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1 << 16];
        deflater.setInput(bytes);
        deflater.finish();
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /**
     * Returns the Flate stream that {@code size} bytes of zeros compress to.
     *
     * @param size how many bytes the stream decodes to
     * @return the stream
     */
    static byte[] deflatedZeros(long size) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] zeros = new byte[1 << 16];
        byte[] buffer = new byte[1 << 16];
        long written = 0;
        while (written < size) {
            int block = (int) Math.min(zeros.length, size - written);
            deflater.setInput(zeros, 0, block);
            written += block;
            while (!deflater.needsInput()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
        }
        deflater.finish();
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** Builds one PDF. */
    static final class Builder {

        private final List<Attachment> attachments = new ArrayList<>();
        private String xmp;
        private byte[] xmpStream;
        private COSDictionary xmpDecodeParms;
        private String userPassword;
        private boolean nestedNameTree;
        private boolean nameTreeKeysAreNames;
        private List<String> keys;
        private int[] leaves;

        private Builder() {
        }

        /**
         * Keys the entries of the name tree as the caller spells them, one key per
         * attachment in the order they were attached, a key given twice included.
         *
         * <p>The entries are written into the {@code /Names} array one after the other
         * rather than through the map the library offers, which is the one way to write a
         * tree that lists two files under one key: a map has one value per key.
         *
         * @param keys the keys, as many as there are attachments
         * @return this builder
         */
        Builder keys(String... keys) {
            this.keys = List.of(keys);
            return this;
        }

        /**
         * Splits the entries of the name tree across child nodes of an empty root, the
         * first {@code sizes[0]} entries into the first child and so on, so that a key can
         * be listed once in one node and once in another.
         *
         * @param sizes how many entries each child holds
         * @return this builder
         */
        Builder leaves(int... sizes) {
            this.leaves = sizes.clone();
            return this;
        }

        Builder attach(Attachment attachment) {
            attachments.add(attachment);
            return this;
        }

        Builder attach(String name, byte[] content) {
            return attach(Attachment.invoice(name, content));
        }

        Builder xmp(String packet) {
            this.xmp = packet;
            return this;
        }

        /**
         * Sets the metadata stream from its Flate bytes, so that a packet which decodes to
         * more than this machine holds can be written without ever being decoded here.
         */
        Builder xmpStream(byte[] flate) {
            this.xmpStream = flate;
            return this;
        }

        /** Sets the {@code /DecodeParms} of the metadata stream. */
        Builder xmpDecodeParms(COSDictionary parameters) {
            this.xmpDecodeParms = parameters;
            return this;
        }

        Builder userPassword(String password) {
            this.userPassword = password;
            return this;
        }

        /**
         * Keys the entries of the name tree by the names of the attachments, which is
         * what a file written by an ordinary producer does. The default keeps an ordinal
         * in front of each key, so that a fixture can carry two attachments of one name.
         */
        Builder nameTreeKeysAreNames() {
            this.nameTreeKeysAreNames = true;
            return this;
        }

        /**
         * Puts the entries one level down, as the children of an empty root node. A name
         * tree may be nested and a reader has to walk it; a writer that rewrote the tree
         * as one node without walking it would lose whatever it did not see.
         */
        Builder nestedNameTree() {
            this.nestedNameTree = true;
            return this;
        }

        byte[] build() {
            try (PDDocument document = new PDDocument()) {
                document.addPage(new PDPage(PDRectangle.A4));
                write(document);
                if (xmpStream != null) {
                    PDMetadata metadata = new PDMetadata(document);
                    try (OutputStream out = metadata.getCOSObject().createRawOutputStream()) {
                        out.write(xmpStream);
                    }
                    metadata.getCOSObject().setItem(COSName.FILTER, COSName.FLATE_DECODE);
                    if (xmpDecodeParms != null) {
                        metadata.getCOSObject().setItem(COSName.DECODE_PARMS, xmpDecodeParms);
                    }
                    document.getDocumentCatalog().setMetadata(metadata);
                } else if (xmp != null) {
                    PDMetadata metadata = new PDMetadata(document);
                    metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
                    document.getDocumentCatalog().setMetadata(metadata);
                }
                if (userPassword != null) {
                    document.protect(new StandardProtectionPolicy("owner", userPassword,
                            new AccessPermission()));
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.save(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Returns an embedded file stream whose raw bytes are written as they are and
         * declared to be Flate, so that a stream that decodes to more than this machine
         * holds can be put into a fixture without ever being decoded here.
         */
        private static PDEmbeddedFile encoded(PDDocument document,
                                              byte[] raw,
                                              COSName filter,
                                              COSDictionary decodeParms) throws IOException {
            PDEmbeddedFile stream = new PDEmbeddedFile(document);
            try (OutputStream out = stream.getCOSObject().createRawOutputStream()) {
                out.write(raw);
            }
            stream.getCOSObject().setItem(COSName.FILTER,
                    filter == null ? COSName.FLATE_DECODE : filter);
            if (decodeParms != null) {
                stream.getCOSObject().setItem(COSName.DECODE_PARMS, decodeParms);
            }
            return stream;
        }

        private void write(PDDocument document) throws IOException {
            if (attachments.isEmpty()) {
                return;
            }
            Map<String, PDComplexFileSpecification> tree = new LinkedHashMap<>();
            COSArray associated = new COSArray();
            int ordinal = 0;
            for (Attachment attachment : attachments) {
                PDEmbeddedFile stream = attachment.encoded()
                        ? encoded(document, attachment.content(), attachment.filter(),
                                attachment.decodeParms())
                        : new PDEmbeddedFile(document,
                                new ByteArrayInputStream(attachment.content()),
                                COSName.FLATE_DECODE);
                if (attachment.mediaType() != null) {
                    stream.setSubtype(attachment.mediaType());
                }
                stream.setSize((int) attachment.declaredSize());
                PDComplexFileSpecification specification = new PDComplexFileSpecification();
                specification.setFile(attachment.name());
                specification.setFileUnicode(attachment.name());
                specification.setEmbeddedFile(stream);
                specification.setEmbeddedFileUnicode(stream);
                if (attachment.relationship() != null) {
                    specification.getCOSObject().setItem(COSName.getPDFName("AFRelationship"),
                            COSName.getPDFName(attachment.relationship()));
                }
                // The key of a name tree entry has to be unique and the names of two
                // attachments need not be, so the key carries an ordinal in front of it —
                // unless a fixture asks for the keys a real file writes, which are the
                // names themselves.
                tree.put(nameTreeKeysAreNames
                        ? attachment.name()
                        : String.format("%03d-%s", ordinal, attachment.name()),
                        specification);
                ordinal++;
                if (attachment.associated()) {
                    associated.add(specification.getCOSObject());
                }
            }
            if (keys != null || leaves != null) {
                writeRaw(document, new ArrayList<>(tree.values()), associated);
                return;
            }
            PDEmbeddedFilesNameTreeNode node = new PDEmbeddedFilesNameTreeNode();
            node.setNames(tree);
            if (nestedNameTree) {
                PDEmbeddedFilesNameTreeNode root = new PDEmbeddedFilesNameTreeNode();
                root.setKids(List.of(node));
                node = root;
            }
            PDDocumentNameDictionary names =
                    new PDDocumentNameDictionary(document.getDocumentCatalog());
            names.setEmbeddedFiles(node);
            document.getDocumentCatalog().setNames(names);
            if (associated.size() > 0) {
                document.getDocumentCatalog().getCOSObject()
                        .setItem(COSName.getPDFName("AF"), associated);
            }
        }

        /**
         * Writes the name tree entry by entry, with the keys the caller gave and in the
         * nodes the caller asked for, and the associated files array beside it.
         */
        private void writeRaw(PDDocument document,
                              List<PDComplexFileSpecification> specifications,
                              COSArray associated) {
            List<String> written = keys != null ? keys
                    : attachments.stream().map(Attachment::name).toList();
            if (written.size() != specifications.size()) {
                throw new IllegalStateException("one key per attachment");
            }
            COSDictionary root = new COSDictionary();
            if (leaves == null) {
                root.setItem(COSName.NAMES, entries(written, specifications, 0,
                        specifications.size()));
            } else {
                COSArray kids = new COSArray();
                int from = 0;
                for (int size : leaves) {
                    COSDictionary leaf = new COSDictionary();
                    leaf.setItem(COSName.NAMES, entries(written, specifications, from,
                            from + size));
                    kids.add(leaf);
                    from += size;
                }
                root.setItem(COSName.KIDS, kids);
            }
            COSDictionary names = new COSDictionary();
            names.setItem(COSName.EMBEDDED_FILES, root);
            document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
            if (associated.size() > 0) {
                document.getDocumentCatalog().getCOSObject()
                        .setItem(COSName.getPDFName("AF"), associated);
            }
        }

        private static COSArray entries(List<String> keys,
                                        List<PDComplexFileSpecification> specifications,
                                        int from,
                                        int to) {
            COSArray array = new COSArray();
            for (int i = from; i < to; i++) {
                array.add(new COSString(keys.get(i)));
                array.add(specifications.get(i).getCOSObject());
            }
            return array;
        }
    }
}
