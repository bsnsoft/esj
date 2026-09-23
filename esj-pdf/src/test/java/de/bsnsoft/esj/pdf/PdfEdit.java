package de.bsnsoft.esj.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;

/**
 * Changes a PDF the tests of this module built, at the level of its objects.
 *
 * <p>A few fixtures are a well-formed file with one thing done to it that no writing API
 * offers, such as an entry of the name tree written twice. They are made by loading the file
 * the builder wrote, editing its objects and saving it again, so that the one thing that
 * differs from an ordinary file is visible in the source of the edit.
 */
final class PdfEdit {

    private PdfEdit() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the file with the first entry of its embedded files name tree written again
     * behind the others, the same key and the same file specification, {@code times} more
     * times.
     *
     * @param pdf   a file whose name tree is one node
     * @param times how many more times the entry is to stand in the tree
     * @return the edited file
     */
    static byte[] repeatNameTreeEntry(byte[] pdf, int times) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSDictionary names = document.getDocumentCatalog().getCOSObject()
                    .getCOSDictionary(COSName.NAMES);
            COSArray entries = names.getCOSDictionary(COSName.EMBEDDED_FILES)
                    .getCOSArray(COSName.NAMES);
            COSBase key = entries.get(0);
            COSBase value = entries.get(1);
            for (int i = 0; i < times; i++) {
                entries.add(key);
                entries.add(value);
            }
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a file attachment annotation on its first page, whose file
     * specification embeds the attachment and which nothing else in the file refers to.
     *
     * @param pdf        the file
     * @param attachment what the annotation carries
     * @return the edited file
     */
    static byte[] annotate(byte[] pdf, Pdfs.Attachment attachment) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDAnnotationFileAttachment annotation = new PDAnnotationFileAttachment();
            annotation.setFile(specification(document, attachment));
            annotation.setRectangle(new PDRectangle(20, 20, 16, 16));
            annotate(document.getPage(0), annotation);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a file attachment annotation on its first page whose file
     * specification is the one the first entry of the name tree names: one file, referred
     * to from two places.
     *
     * @param pdf the file
     * @return the edited file
     */
    static byte[] annotateTheFirstEntry(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray entries = treeEntries(document);
            PDAnnotationFileAttachment annotation = new PDAnnotationFileAttachment();
            annotation.getCOSObject().setItem(COSName.FS, entries.get(1));
            annotation.setRectangle(new PDRectangle(20, 20, 16, 16));
            annotate(document.getPage(0), annotation);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with the attachment in the associated files array of its first
     * page, and nowhere else.
     *
     * @param pdf        the file
     * @param attachment what the page refers to
     * @return the edited file
     */
    static byte[] associateWithThePage(byte[] pdf, Pdfs.Attachment attachment) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray array = new COSArray();
            array.add(specification(document, attachment).getCOSObject());
            document.getPage(0).getCOSObject().setItem(COSName.getPDFName("AF"), array);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a text annotation on its first page whose associated files
     * array refers to the attachment, and which nothing else refers to.
     *
     * @param pdf        the file
     * @param attachment what the annotation refers to
     * @return the edited file
     */
    static byte[] associateWithAnAnnotation(byte[] pdf, Pdfs.Attachment attachment) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDAnnotationText annotation = new PDAnnotationText();
            annotation.setRectangle(new PDRectangle(20, 20, 16, 16));
            COSArray array = new COSArray();
            array.add(specification(document, attachment).getCOSObject());
            annotation.getCOSObject().setItem(COSName.getPDFName("AF"), array);
            annotate(document.getPage(0), annotation);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a text annotation on its first page that carries a file
     * specification under {@code /FS}, which is not what a text annotation does: only a
     * file attachment annotation carries a file.
     *
     * @param pdf        the file
     * @param attachment what the annotation names
     * @return the edited file
     */
    static byte[] textAnnotationNamingAFile(byte[] pdf, Pdfs.Attachment attachment) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDAnnotationText annotation = new PDAnnotationText();
            annotation.setRectangle(new PDRectangle(20, 20, 16, 16));
            annotation.getCOSObject().setItem(COSName.FS,
                    specification(document, attachment).getCOSObject());
            annotate(document.getPage(0), annotation);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a second entry in its name tree, under {@code key}, whose file
     * specification is a new one that embeds the stream the first entry embeds: two file
     * specifications, one file.
     *
     * @param pdf a file whose name tree is one node
     * @param key the key of the new entry
     * @return the edited file
     */
    static byte[] secondSpecificationOfTheFirstStream(byte[] pdf, String key) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray entries = treeEntries(document);
            COSDictionary first = (COSDictionary) entries.getObject(1);
            COSBase stream = first.getCOSDictionary(COSName.EF).getItem(COSName.F);
            COSDictionary files = new COSDictionary();
            files.setItem(COSName.F, stream);
            COSDictionary second = new COSDictionary();
            second.setItem(COSName.TYPE, COSName.FILESPEC);
            second.setString(COSName.F, key);
            second.setString(COSName.UF, key);
            second.setItem(COSName.EF, files);
            entries.add(new COSString(key));
            entries.add(second);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with one more entry in its name tree, under {@code key}, whose value
     * is the embedded file stream of the first entry itself rather than a file
     * specification of it.
     *
     * @param pdf a file whose name tree is one node
     * @param key the key of the new entry
     * @return the edited file
     */
    static byte[] streamAsTheValueOfAnEntry(byte[] pdf, String key) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray entries = treeEntries(document);
            COSDictionary first = (COSDictionary) entries.getObject(1);
            COSBase stream = first.getCOSDictionary(COSName.EF).getItem(COSName.F);
            entries.add(0, stream);
            entries.add(0, new COSString(key));
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a second stream under {@code /UF} of the embedded file
     * dictionary of the first entry's file specification, beside the one under {@code /F}.
     *
     * @param pdf     a file whose name tree is one node
     * @param content what the second stream holds
     * @return the edited file
     */
    static byte[] anotherStreamUnderTheUnicodeEntry(byte[] pdf, byte[] content) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSDictionary first = (COSDictionary) treeEntries(document).getObject(1);
            PDEmbeddedFile stream = new PDEmbeddedFile(document,
                    new ByteArrayInputStream(content), COSName.FLATE_DECODE);
            stream.setSubtype(Pdfs.XML);
            stream.setSize(content.length);
            first.getCOSDictionary(COSName.EF).setItem(COSName.UF, stream.getCOSObject());
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with a page tree that refers to itself: the root lists itself among
     * its children.
     *
     * @param pdf the file
     * @return the edited file
     */
    static byte[] pageTreeThatLoops(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSDictionary root = document.getDocumentCatalog().getCOSObject()
                    .getCOSDictionary(COSName.PAGES);
            root.getCOSArray(COSName.KIDS).add(root);
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the file with {@code count} more empty pages.
     *
     * @param pdf   the file
     * @param count how many pages to add
     * @return the edited file
     */
    static byte[] withMorePages(byte[] pdf, int count) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (int i = 0; i < count; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            return saved(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void annotate(PDPage page, PDAnnotation annotation) throws IOException {
        List<PDAnnotation> annotations = new ArrayList<>(page.getAnnotations());
        annotations.add(annotation);
        page.setAnnotations(annotations);
    }

    private static COSArray treeEntries(PDDocument document) {
        return document.getDocumentCatalog().getCOSObject()
                .getCOSDictionary(COSName.NAMES)
                .getCOSDictionary(COSName.EMBEDDED_FILES)
                .getCOSArray(COSName.NAMES);
    }

    /** Returns a file specification that embeds an attachment, as the builder writes one. */
    private static PDComplexFileSpecification specification(PDDocument document,
                                                            Pdfs.Attachment attachment)
            throws IOException {
        PDEmbeddedFile stream = new PDEmbeddedFile(document,
                new ByteArrayInputStream(attachment.content()), COSName.FLATE_DECODE);
        if (attachment.mediaType() != null) {
            stream.setSubtype(attachment.mediaType());
        }
        stream.setSize(attachment.content().length);
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

    /**
     * Saves an edited file without object streams, so that every object stands in the body
     * of the file where a reader of the fixture can find it, and so that a bound on the
     * objects a walk visits is met by the walk and not by the object streams first.
     */
    static byte[] saved(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out, CompressParameters.NO_COMPRESSION);
        return out.toByteArray();
    }
}
