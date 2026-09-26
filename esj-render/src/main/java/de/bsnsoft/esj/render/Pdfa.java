package de.bsnsoft.esj.render;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

/**
 * What makes a rendering a PDF/A-3b file: the output intent, the XMP packet and the
 * version of the format.
 *
 * <p>PDF/A is an archiving profile of PDF, and level B of it is the readable one: the file
 * has to be reproducible on a machine that has none of the sender's fonts and none of the
 * sender's colour management, years from now, without reading anything from outside itself.
 * Three things carry that here, and the rest of the renderer already satisfied the profile
 * before this class existed.
 *
 * <ul>
 *   <li>The <b>fonts</b> are embedded — {@link Fonts}, two vendored faces, subset into
 *       every file, no standard-14 font anywhere.</li>
 *   <li>The <b>output intent</b> says how the greys of the page are to be read. The layout
 *       draws black text, grey labels and grey rules, which is {@code DeviceGray}, and
 *       PDF/A allows a device-dependent colour only in a file that also says what it means.
 *       The destination profile is the vendored sRGB profile of the ICC, embedded whole;
 *       {@code icc/README.md} beside it records its origin, its digest and its terms.</li>
 *   <li>The <b>XMP packet</b> declares part 3 and conformance level B, and repeats what the
 *       document information dictionary says. PDF/A requires the two to agree, so this class
 *       writes the packet out of that dictionary rather than out of a second set of values:
 *       they cannot drift, because there is only one of them.</li>
 * </ul>
 *
 * <p>Level B and not level A: level A additionally requires a tagged document — a structure
 * tree, a reading order, a language and alternate text for everything that is not text —
 * and a generic invoice layout that promised those would be promising something it has no
 * way to be right about. Nothing here writes {@code MarkInfo} or a structure tree.
 *
 * <h2>Why the packet is written as text</h2>
 *
 * <p>XMP is RDF/XML, and a serializer would be the obvious way to produce it. This one is a
 * string, because a rendering of this module is compared byte for byte against a checked-in
 * file: a packet whose attribute order, namespace prefixes or indentation came from whichever
 * XML writer the runtime happens to supply would be a difference between two machines that
 * nothing in this project could see coming. Four properties in a fixed order are cheaper to
 * write than to defend. The text of the document is escaped for XML on the way in, and the
 * characters that XML cannot carry at all — the C0 controls, the unpaired halves of a
 * surrogate pair — never reach it.
 *
 * <p>No date is written. An archival file usually carries a creation date, and this one
 * carries none, because a renderer whose output changes with the clock cannot be diffed;
 * {@code docs/rendering.md} says what that costs and why the trade goes this way.
 */
final class Pdfa {

    /** The part of ISO 19005 this rendering declares. */
    private static final String PART = "3";

    /** The conformance level within that part. */
    private static final String CONFORMANCE = "B";

    /** The version of PDF that part 3 of ISO 19005 is an archiving profile of. */
    private static final float PDF_VERSION = 1.7f;

    /**
     * The name the output intent gives the colour space of the page. It is not a
     * registered output condition, which is why the file carries the profile itself.
     */
    private static final String OUTPUT_CONDITION = "sRGB IEC61966-2.1";

    /** Where the vendored profile sits on the classpath of this module. */
    private static final String PROFILE = "/de/bsnsoft/esj/render/icc/sRGB2014.icc";

    /** The bytes of that profile, read once and embedded in every rendering. */
    private static final byte[] PROFILE_BYTES = profileBytes();

    /**
     * The number of components of that profile, which the stream carrying it has to state.
     * It is an RGB profile, which {@code VendoredProfileTest} holds it to, so three.
     */
    private static final int PROFILE_COMPONENTS = 3;

    private Pdfa() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns a text of the document as the title of the file, or {@code null} where the
     * document has none to give.
     *
     * <p>The same string becomes the {@code Title} of the information dictionary and the
     * {@code dc:title} of the XMP packet, and it is cleaned once here so that the two are
     * the same string rather than two cleanings of one value. A control character becomes a
     * space — XML carries none of them and a title is one line — a character that directs
     * the reading order becomes a space for the reason {@link Characters} gives, and half of
     * a surrogate pair that lost its other half becomes a question mark, the way an
     * unprintable character does on the page.
     *
     * @param text the value of the document, which may be {@code null}
     * @return the title, or {@code null} where there is nothing to say
     */
    static String title(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder title = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char unit = text.charAt(i);
            if (Character.isHighSurrogate(unit) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                title.append(unit).append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (Character.isSurrogate(unit)) {
                title.append('?');
            } else if (unit < ' ' || unit == 0x7f || Characters.directional(unit)) {
                title.append(' ');
            } else {
                title.append(unit);
            }
            i++;
        }
        String cleaned = title.toString().strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Declares a document to be PDF/A-3b and gives it what that requires.
     *
     * <p>The information dictionary has to be the one the document will be saved with: the
     * XMP packet is written out of it, and PDF/A requires the two to say the same thing.
     *
     * @param pdf         the document, before it is saved
     * @param information the information dictionary of that document
     * @throws RenderException if the profile or the packet could not be written
     */
    static void declare(PDDocument pdf, PDDocumentInformation information) {
        pdf.setVersion(PDF_VERSION);
        try {
            PDOutputIntent intent = outputIntent(pdf);
            intent.setInfo(OUTPUT_CONDITION);
            intent.setOutputCondition(OUTPUT_CONDITION);
            intent.setOutputConditionIdentifier(OUTPUT_CONDITION);
            pdf.getDocumentCatalog().addOutputIntent(intent);

            PDMetadata metadata = new PDMetadata(pdf);
            try (OutputStream out = metadata.createOutputStream()) {
                out.write(packet(information).getBytes(StandardCharsets.UTF_8));
            }
            pdf.getDocumentCatalog().setMetadata(metadata);
        } catch (IOException e) {
            throw new RenderException("the PDF/A declaration could not be written", e);
        }
    }

    /**
     * Returns the output intent of a document, with the vendored profile as its destination
     * profile, byte for byte.
     *
     * <p>PDFBox builds the same dictionary from an input stream of the profile, but on the
     * way it reads the profile into a {@code java.awt.color.ICC_Profile} and embeds what that
     * object gives back, and what it gives back is up to the colour management of the
     * runtime: a build whose engine re-serializes a profile it was handed writes its own name
     * into the header as the preferred CMM, and the stream is no longer the ICC's file. So
     * the stream is filled from the bytes here, in the shape PDFBox gives it — Flate-encoded,
     * with {@code N} stating the components — and no runtime gets to touch the profile.
     */
    private static PDOutputIntent outputIntent(PDDocument pdf) throws IOException {
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, COSName.OUTPUT_INTENT);
        dictionary.setItem(COSName.S, COSName.GTS_PDFA1);
        PDStream profile = new PDStream(pdf, new ByteArrayInputStream(PROFILE_BYTES),
                COSName.FLATE_DECODE);
        profile.getCOSObject().setInt(COSName.N, PROFILE_COMPONENTS);
        dictionary.setItem(COSName.DEST_OUTPUT_PROFILE, profile);
        return new PDOutputIntent(dictionary);
    }

    /**
     * Returns the XMP packet of a document with that information dictionary.
     *
     * <p>Four schemas, all of them predefined: the PDF/A identification, which says what
     * this file claims to be; Dublin Core, which carries the title and the media type;
     * the XMP basic schema, which carries the tool; and the PDF schema, which carries the
     * producer. Every property of the information dictionary this renderer sets has its
     * counterpart here, and neither side carries anything the other does not.
     *
     * @param information the information dictionary
     * @return the packet
     */
    private static String packet(PDDocumentInformation information) {
        String title = information.getTitle();
        StringBuilder xmp = new StringBuilder(1024);
        xmp.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
                .append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
                .append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
                .append("  <rdf:Description rdf:about=\"\"")
                .append(" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n")
                .append("   <pdfaid:part>").append(PART).append("</pdfaid:part>\n")
                .append("   <pdfaid:conformance>").append(CONFORMANCE)
                .append("</pdfaid:conformance>\n")
                .append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\"")
                .append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                .append("   <dc:format>application/pdf</dc:format>\n");
        if (title != null) {
            xmp.append("   <dc:title>\n")
                    .append("    <rdf:Alt>\n")
                    .append("     <rdf:li xml:lang=\"x-default\">").append(escaped(title))
                    .append("</rdf:li>\n")
                    .append("    </rdf:Alt>\n")
                    .append("   </dc:title>\n");
        }
        xmp.append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\"")
                .append(" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n")
                .append("   <xmp:CreatorTool>").append(escaped(information.getCreator()))
                .append("</xmp:CreatorTool>\n")
                .append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\"")
                .append(" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n")
                .append("   <pdf:Producer>").append(escaped(information.getProducer()))
                .append("</pdf:Producer>\n")
                .append("  </rdf:Description>\n")
                .append(" </rdf:RDF>\n")
                .append("</x:xmpmeta>\n")
                .append("<?xpacket end=\"w\"?>");
        return xmp.toString();
    }

    /**
     * Returns a text with the five characters of XML escaped.
     *
     * <p>The apostrophe and the quotation mark are escaped although no value of this packet
     * stands in an attribute, because a reader of this method should not have to check that
     * none ever will.
     */
    private static String escaped(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static byte[] profileBytes() {
        try (InputStream in = Pdfa.class.getResourceAsStream(PROFILE)) {
            if (in == null) {
                throw new RenderException("the ICC profile " + PROFILE
                        + " is not on the classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
