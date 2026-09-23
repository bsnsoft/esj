package de.bsnsoft.esj.cli;

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
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;

/**
 * Builds the hybrid invoices the tests of this module hand to the tool.
 *
 * <p>They are generated rather than checked in. A binary fixture in a repository says
 * nothing about what makes it the case it is, and a hostile one is a file somebody has to
 * be careful with; a fixture built here is readable as source. The containers are the
 * ordinary shapes a producer writes — an invoice attached, declared in {@code /AF},
 * described by an XMP packet — because what these tests are about is what the command line
 * does with a container, not what the reader does with a broken one. The hostile cases
 * belong to {@code esj-pdf}, which owns the reader, and are tested there.
 */
final class TestPdfs {

    /** The conventional name of the invoice attachment of a Factur-X file. */
    static final String FACTUR_X = "factur-x.xml";

    /** The name a ZUGFeRD 1.0 file gives its invoice attachment. */
    static final String ZUGFERD_1 = "ZUGFeRD-invoice.xml";

    /** The relationship a hybrid invoice declares its XML with. */
    static final String ALTERNATIVE = "Alternative";

    /** The media type a hybrid invoice declares its XML with. */
    static final String XML = "text/xml";

    /**
     * The conformance level of the corpus invoices these tests embed, which write the
     * national specification of Germany in BT-24.
     */
    static final String XRECHNUNG = "XRECHNUNG";

    /** The root element of a ZUGFeRD 1.0 invoice, which this project does not read. */
    static final byte[] ZUGFERD_1_INVOICE = ("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CrossIndustryDocument\
             xmlns:rsm="urn:ferd:CrossIndustryDocument:invoice:1p0">
              <rsm:SpecifiedExchangedDocumentContext/>
            </rsm:CrossIndustryDocument>
            """).getBytes(StandardCharsets.UTF_8);

    /** The element of a cross industry invoice that BT-24 stands in. */
    private static final String GUIDELINE = "GuidelineSpecifiedDocumentContextParameter";

    /** The element BT-24 itself is, inside that group. */
    private static final String ID_OPEN = ":ID>";

    /** The end tag of that element. */
    private static final String ID_CLOSE = "</ram:ID>";

    /**
     * Returns an invoice of the corpus with the specification identifier of a profile of
     * the Factur-X family, so that a test can build a container whose invoice is what the
     * container says it is.
     *
     * @param invoice    the invoice to rewrite
     * @param identifier the content BT-24 is to carry
     * @return the invoice, with BT-24 replaced
     */
    static byte[] withSpecificationIdentifier(byte[] invoice, String identifier) {
        String text = new String(invoice, StandardCharsets.UTF_8);
        int start = text.indexOf(GUIDELINE);
        if (start < 0) {
            throw new IllegalArgumentException("this invoice carries no BT-24 to replace");
        }
        int open = text.indexOf(ID_OPEN, start) + ID_OPEN.length();
        int close = text.indexOf(ID_CLOSE, open);
        return (text.substring(0, open) + identifier + text.substring(close))
                .getBytes(StandardCharsets.UTF_8);
    }

    private TestPdfs() {
        throw new AssertionError("no instances");
    }

    /** Returns a builder for a PDF with one page and nothing else. */
    static Builder builder() {
        return new Builder();
    }

    /** Returns the ordinary case: one invoice attachment, in {@code /AF}, with a packet. */
    static byte[] facturX(byte[] invoice) {
        return facturX(FACTUR_X, invoice, XRECHNUNG);
    }

    /** Returns a Factur-X file with an attachment name and a conformance level of choice. */
    static byte[] facturX(String name, byte[] invoice, String conformanceLevel) {
        return builder()
                .attach(name, invoice)
                .xmp(xmp(name, conformanceLevel))
                .build();
    }

    /** Returns the XMP packet of a Factur-X file, PDF/A-3B and the four properties. */
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
     * One file to attach.
     *
     * @param name         the name the file specification gives it
     * @param content      the bytes
     * @param mediaType    the media type the embedded file stream declares, or
     *                     {@code null} for none
     * @param relationship the {@code /AFRelationship}, or {@code null} for none
     * @param associated   whether the catalog's {@code /AF} array refers to it
     * @param filter       the filter the embedded file stream declares, with the bytes
     *                     written as they are, or {@code null} for the ordinary Flate
     *                     stream this builder encodes
     * @param declaredSize what {@code /Params /Size} says, or {@code null} for the length
     *                     the content actually has
     */
    record Attachment(String name,
                      byte[] content,
                      String mediaType,
                      String relationship,
                      boolean associated,
                      COSName filter,
                      Integer declaredSize) {

        /** Creates an attachment whose stream declares the size it has. */
        Attachment(String name,
                   byte[] content,
                   String mediaType,
                   String relationship,
                   boolean associated,
                   COSName filter) {
            this(name, content, mediaType, relationship, associated, filter, null);
        }

        /** Creates an attachment of the ordinary shape, whose stream is Flate. */
        Attachment(String name,
                   byte[] content,
                   String mediaType,
                   String relationship,
                   boolean associated) {
            this(name, content, mediaType, relationship, associated, null);
        }

        /**
         * Returns this attachment with a stream that declares a size it does not have.
         *
         * @param size what {@code /Params /Size} is to say
         * @return the attachment
         */
        Attachment declaring(int size) {
            return new Attachment(name, content, mediaType, relationship, associated,
                    filter, size);
        }

        /** Returns an ordinary XML attachment of a hybrid invoice. */
        static Attachment invoice(String name, byte[] content) {
            return new Attachment(name, content, XML, ALTERNATIVE, true);
        }

        /**
         * Returns an attachment whose stream declares a filter whose decoder allocates
         * from the numbers in the file before it writes anything, which the reader of
         * this project does not run.
         */
        static Attachment filtered(String name, byte[] raw) {
            return new Attachment(name, raw, "image/jpeg", "Supplement", false,
                    COSName.DCT_DECODE);
        }
    }

    /**
     * Returns a PDF whose object stream inflates to {@code size} bytes of nothing.
     *
     * <p>Written byte by byte rather than by the library, because the library does not
     * produce one: an object stream is what a parser decodes for itself while it opens a
     * file, so this is the shape of container that reaches the heap before any command of
     * this tool has looked at an attachment.
     */
    static byte[] objectStreamBomb(long size) {
        byte[] payload = deflatedZeros(size);
        return objectStreamWith("/Type /ObjStm /N 1 /First 8 /Filter /FlateDecode /Length "
                + payload.length, payload);
    }

    /**
     * Returns the same file with the dictionary of the object stream written as the
     * caller spells it, so that a fixture can choose the filter chain, its parameters or
     * the number of objects the stream declares.
     *
     * @param dictionary what stands between the brackets of the stream dictionary
     * @param payload    the bytes of the stream
     * @return the file
     */
    static byte[] objectStreamWith(String dictionary, byte[] payload) {
        String head = "%PDF-1.5\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n"
                + "10 0 obj\n<< " + dictionary + " >>\nstream\n";
        String tail = "\nendstream\nendobj\ntrailer\n<< /Size 11 /Root 1 0 R"
                + " >>\nstartxref\n999999999\n%%EOF\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(head.getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(payload);
        out.writeBytes(tail.getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    /**
     * Returns a container that declares encryption in a trailer the library cannot use.
     *
     * <p>The {@code startxref} points nowhere and the file carries no {@code /Info}, which
     * is the one shape in which the brute-force rebuild of the trailer drops
     * {@code /Encrypt}: the trailer the library ends up with says nothing about
     * encryption, and the encryption dictionary is reached only as an object of the file.
     *
     * @return the file
     */
    static byte[] declaredEncryptionWithoutAUsableTrailer() {
        String head = "%PDF-1.5\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\n"
                + "12 0 obj\n<< /Filter /Standard /V 4 /R 4 /Length 128"
                + " /CF << /StdCF << /CFM /V2 /Length 16 >> >> /StmF /Identity"
                + " /StrF /Identity /O <00> /U <00> /P -1 >>\nendobj\n"
                + "trailer\n<< /Size 13 /Root 1 0 R /Encrypt 12 0 R /ID [<00> <00>]"
                + " >>\nstartxref\n999999999\n%%EOF\n";
        return head.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Returns the Flate stream that {@code size} bytes of zeros compress to. */
    private static byte[] deflatedZeros(long size) {
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
        private String userPassword;
        private List<String> keys;
        private int[] leaves;
        private final List<Attachment> annotations = new ArrayList<>();
        private final List<Attachment> onThePage = new ArrayList<>();

        private Builder() {
        }

        /**
         * Puts an attachment into a file attachment annotation of the first page, and
         * nowhere else: neither the name tree nor the document's associated files array
         * refers to it.
         */
        Builder annotate(Attachment attachment) {
            annotations.add(attachment);
            return this;
        }

        /**
         * Puts an attachment into the associated files array of the first page, and
         * nowhere else.
         */
        Builder associateWithThePage(Attachment attachment) {
            onThePage.add(attachment);
            return this;
        }

        /**
         * Keys the entries of the name tree as the caller spells them, one key per
         * attachment, a key given twice included; the entries are written into the array
         * one after the other, because a map, which is what the library offers, has one
         * value per key.
         */
        Builder keys(String... keys) {
            this.keys = List.of(keys);
            return this;
        }

        /** Splits the entries of the name tree across children of an empty root. */
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

        Builder userPassword(String password) {
            this.userPassword = password;
            return this;
        }

        /**
         * Returns the embedded file stream of an attachment: an ordinary Flate stream, or,
         * where the attachment names a filter, its bytes written as they are under that
         * name.
         */
        private static PDEmbeddedFile stream(PDDocument document, Attachment attachment)
                throws IOException {
            if (attachment.filter() == null) {
                return new PDEmbeddedFile(document,
                        new ByteArrayInputStream(attachment.content()), COSName.FLATE_DECODE);
            }
            PDEmbeddedFile stream = new PDEmbeddedFile(document);
            try (OutputStream out = stream.getCOSObject().createRawOutputStream()) {
                out.write(attachment.content());
            }
            stream.getCOSObject().setItem(COSName.FILTER, attachment.filter());
            return stream;
        }

        byte[] build() {
            try (PDDocument document = new PDDocument()) {
                document.addPage(new PDPage(PDRectangle.A4));
                write(document);
                page(document);
                if (xmp != null) {
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

        /** Writes what the first page refers to: its annotations and its associated files. */
        private void page(PDDocument document) throws IOException {
            PDPage page = document.getPage(0);
            if (!annotations.isEmpty()) {
                List<PDAnnotation> written = new ArrayList<>();
                for (Attachment attachment : annotations) {
                    PDAnnotationFileAttachment annotation = new PDAnnotationFileAttachment();
                    annotation.setFile(specification(document, attachment));
                    annotation.setRectangle(new PDRectangle(20, 20, 16, 16));
                    written.add(annotation);
                }
                page.setAnnotations(written);
            }
            if (!onThePage.isEmpty()) {
                COSArray array = new COSArray();
                for (Attachment attachment : onThePage) {
                    array.add(specification(document, attachment).getCOSObject());
                }
                page.getCOSObject().setItem(COSName.getPDFName("AF"), array);
            }
        }

        /** Returns the file specification of an attachment, embedding its stream. */
        private static PDComplexFileSpecification specification(PDDocument document,
                                                                Attachment attachment)
                throws IOException {
            PDEmbeddedFile stream = stream(document, attachment);
            if (attachment.mediaType() != null) {
                stream.setSubtype(attachment.mediaType());
            }
            stream.setSize(attachment.declaredSize() == null
                    ? attachment.content().length : attachment.declaredSize());
            PDComplexFileSpecification specification = new PDComplexFileSpecification();
            specification.setFile(attachment.name());
            specification.setFileUnicode(attachment.name());
            specification.setEmbeddedFile(stream);
            specification.setEmbeddedFileUnicode(stream);
            if (attachment.relationship() != null) {
                specification.getCOSObject().setItem(COSName.getPDFName("AFRelationship"),
                        COSName.getPDFName(attachment.relationship()));
            }
            return specification;
        }

        private void write(PDDocument document) throws IOException {
            if (attachments.isEmpty()) {
                return;
            }
            Map<String, PDComplexFileSpecification> tree = new LinkedHashMap<>();
            COSArray associated = new COSArray();
            int ordinal = 0;
            for (Attachment attachment : attachments) {
                PDEmbeddedFile stream = stream(document, attachment);
                if (attachment.mediaType() != null) {
                    stream.setSubtype(attachment.mediaType());
                }
                stream.setSize(attachment.declaredSize() == null
                        ? attachment.content().length : attachment.declaredSize());
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
                // attachments need not be, so the key carries an ordinal in front of it.
                tree.put(String.format("%03d-%s", ordinal++, attachment.name()), specification);
                if (attachment.associated()) {
                    associated.add(specification.getCOSObject());
                }
            }
            if (keys != null || leaves != null) {
                raw(document, new ArrayList<>(tree.values()));
            } else {
                PDEmbeddedFilesNameTreeNode node = new PDEmbeddedFilesNameTreeNode();
                node.setNames(tree);
                PDDocumentNameDictionary names =
                        new PDDocumentNameDictionary(document.getDocumentCatalog());
                names.setEmbeddedFiles(node);
                document.getDocumentCatalog().setNames(names);
            }
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("AF"), associated);
        }

        /** Writes the name tree entry by entry, with the keys and the nodes asked for. */
        private void raw(PDDocument document, List<PDComplexFileSpecification> specifications) {
            List<String> written = keys != null ? keys
                    : attachments.stream().map(Attachment::name).toList();
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
