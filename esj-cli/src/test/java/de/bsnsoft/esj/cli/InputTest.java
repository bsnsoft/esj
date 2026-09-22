package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How much of an input the tool reads before it refuses it.
 *
 * <p>Two bounds apply and they differ by a factor of sixteen, so which of them the read is
 * held to is worth a test of its own: an XML input is held to what the importer reads of
 * one, an ESJ document to the document size of the specification, section 12.2.
 *
 * <p>The case these tests are about is the one a caller on the other end of a pipe
 * decides: the writer chooses where its writes end, and a first write of three bytes must
 * not buy the larger bound for a document that is plainly XML. So the input arrives here
 * in chunks the test dictates, which is what a pipe does and what a
 * {@link ByteArrayInputStream} never does.
 */
class InputTest {

    @TempDir
    private Path directory;

    /** An input that hands out its first bytes in a write of its own, as a pipe may. */
    private static final class Chunked extends FilterInputStream {

        private int first;

        private Chunked(byte[] content, int first) {
            super(new ByteArrayInputStream(content));
            this.first = first;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (first > 0) {
                int taken = Math.min(first, length);
                first = 0;
                return super.read(bytes, offset, taken);
            }
            return super.read(bytes, offset, length);
        }
    }

    @Test
    void holdsAnXmlInputToTheImporterBoundWhenTheFirstChunkIsOnlyAByteOrderMark() {
        assertRefusedAtTheImporterBound(oversizeXml(new byte[] {(byte) 0xEF, (byte) 0xBB,
                (byte) 0xBF}), 3);
    }

    @Test
    void holdsAnXmlInputToTheImporterBoundWhenTheFirstChunkIsOnlyWhitespace() {
        assertRefusedAtTheImporterBound(oversizeXml("  ".getBytes(StandardCharsets.UTF_8)), 2);
    }

    @Test
    void holdsAnXmlInputToTheImporterBoundWhenItAnnouncesAWideEncoding() {
        // The bound an input is read within may not be a bound the input chooses. A
        // document whose byte order mark announces UTF-16 is XML, and the byte after the
        // mark is the padding of that encoding rather than the first character: a reader
        // that looked at it would conclude "not XML" and hold an invoice to the document
        // bound, which is sixteen times the one the same invoice in UTF-8 is held to.
        assertRefusedAtTheImporterBound(oversizeWideXml(), 2);
    }

    @Test
    void doesNotDecodeTheWholeOfAWideInputToRecognizeIt() {
        // Only the root element is being asked for. An input decoded whole costs three
        // times its length — the bytes, the buffer of characters and the string — which
        // is a cost the input decides by choosing its encoding.
        byte[] wide = oversizeWideXml();

        assertEquals(Optional.of(Boolean.TRUE),
                InputDetector.beginsXml(wide, Math.min(wide.length, 8192)));
    }

    @Test
    void readsAFileOfKnownLengthIntoOneArray() throws IOException {
        // A file the bound accepts is read into an array of the file's length and not
        // into a buffer that is copied out of: the copy is the whole of the input a
        // second time, at the moment the input is largest, and it decides whether a
        // process with a given heap reads a file its profile accepts. What a test can
        // hold to is the identity of the array — the bytes the tool works on are the
        // array that was filled, so nothing was copied.
        byte[] content = "{\"semanticModel\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        Path file = directory.resolve("document.json");
        Files.write(file, content);

        Input input = Input.read(file.toString(), console());

        assertArrayEquals(content, input.bytes());
        assertEquals(content.length, input.bytes().length);
    }

    @Test
    void readsAFileThatGrewWhileItWasBeingRead() throws IOException {
        // The length a file system reports is a fact about the past. A file that is
        // longer than it said is read to its end all the same, and only the bound
        // refuses it.
        byte[] content = new byte[9000];
        Arrays.fill(content, (byte) ' ');
        content[0] = '{';
        content[content.length - 1] = '}';
        Path file = directory.resolve("grown.json");
        Files.write(file, Arrays.copyOf(content, 8500));
        Files.write(file, content);

        Input input = Input.read(file.toString(), console());

        assertEquals(content.length, input.bytes().length);
    }

    private Console console() {
        return new Console(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
                new ByteArrayOutputStream(), new GlobalOptions());
    }

    private static void assertRefusedAtTheImporterBound(byte[] content, int first) {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"convert", "-"}, new Chunked(content, first),
                new ByteArrayOutputStream(), err);
        String said = err.toString(StandardCharsets.UTF_8);
        assertEquals(ExitCode.LIMIT, code, said);
        assertTrue(said.contains("bytes of XML this run reads"),
                "the bound of the kind the input turned out to be: " + said);
        assertTrue(said.contains("--max-input-bytes"),
                "the switch that raises it: " + said);
    }

    /** Returns an XML document past the importer's bound, written in UTF-16 with a mark. */
    private static byte[] oversizeWideXml() {
        StringBuilder text = new StringBuilder("\uFEFF<?xml version=\"1.0\"?><a>");
        while (text.length() * 2 < XrImporter.DEFAULT_MAX_INPUT_BYTES + 1024) {
            text.append('x');
        }
        return text.toString().getBytes(StandardCharsets.UTF_16LE);
    }

    /** Returns an XML document past the importer's bound, behind the given prefix. */
    private static byte[] oversizeXml(byte[] prefix) {
        int length = (int) XrImporter.DEFAULT_MAX_INPUT_BYTES + 1024;
        byte[] content = new byte[prefix.length + length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        byte[] head = "<?xml version=\"1.0\"?><a>".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(head, 0, content, prefix.length, head.length);
        for (int i = prefix.length + head.length; i < content.length; i++) {
            content[i] = 'x';
        }
        return content;
    }
}
