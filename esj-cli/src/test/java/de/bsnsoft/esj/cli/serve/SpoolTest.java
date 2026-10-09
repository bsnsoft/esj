package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The spool an answer is written into before its call gives back its child's place: the
 * first {@link Spool#MEMORY} bytes in the heap, the rest in a file that is gone once the spool
 * is given back, and a value written into it once that a message carries unchanged. The file
 * is counted against the disk, and an answer that does not fit is not kept at all.
 */
class SpoolTest {

    @TempDir
    private Path temp;

    @Test
    void aSpoolKeepsASmallAnswerInTheHeapAndALargeOneInAFile() throws IOException {
        Disk disk = new Disk(Long.MAX_VALUE, temp);
        Spool small = new Spool(disk);
        small.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        small.close();
        assertEquals(2, small.size());
        assertEquals(0, files(), "a small answer has no file");
        small.release();

        byte[] bytes = new byte[3 * Spool.MEMORY + 17];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31);
        }
        Spool large = new Spool(disk);
        for (int at = 0; at < bytes.length; at += 1000) {
            large.write(bytes, at, Math.min(1000, bytes.length - at));
        }
        large.close();
        assertEquals(bytes.length, large.size());
        assertEquals(1, files(), "past " + Spool.MEMORY + " bytes, a file");
        assertEquals(bytes.length, disk.used(), "counted against the disk");
        ByteArrayOutputStream copy = new ByteArrayOutputStream();
        large.copyTo(copy);
        assertArrayEquals(bytes, copy.toByteArray());
        large.release();
        assertEquals(0, files(), "given back, the file is gone");
        assertEquals(0, disk.used(), "and its bytes are free again");
    }

    @Test
    void anAnswerThatDoesNotFitTheDiskIsNotKept() throws IOException {
        List<Jv> items = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            items.add(Jv.of("value " + i));
        }
        Jv large = Jv.object().put("values", Jv.array(items)).build();
        Disk disk = new Disk(64 * 1024, temp);
        Disk.Full full = assertThrows(Disk.Full.class, () -> Spool.of(large, true, disk));
        assertTrue(full.getMessage().contains("--max-disk"), full.getMessage());
        assertEquals(0, files(), "nothing of it is left");
        assertEquals(0, disk.used());
        Spool small = Spool.of(Jv.object().put("error", "full").build(), true, disk);
        assertEquals(0, files(), "a small answer needs no room on the disk");
        small.release();
    }

    @Test
    void aWrittenValueIsCarriedByAMessageAsItWouldBeWritten() throws IOException {
        List<Jv> items = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            items.add(Jv.of("value " + i + " \u202E\u0001 é"));
        }
        Jv result = Jv.object().put("values", Jv.array(items)).put("n", 5000).build();
        Jv message = Jv.object().put("jsonrpc", "2.0").put("id", 7).put("result", result)
                .build();
        Disk disk = new Disk(Long.MAX_VALUE, temp);
        Jv carried = Jv.object().put("jsonrpc", "2.0").put("id", 7)
                .put("result", new Jv.Raw(Spool.of(result, false, disk))).build();
        assertTrue(files() > 0, "the value is past the heap part of a spool");
        assertArrayEquals(message.toBytes(), carried.toBytes());
        assertArrayEquals(Jv.array(List.of(Jv.of(1), message, Jv.of(2))).toBytes(),
                Jv.array(List.of(Jv.of(1), carried, Jv.of(2))).toBytes(),
                "with the separators of the message around it");
        ByteArrayOutputStream streamed = new ByteArrayOutputStream();
        carried.writeTo(streamed, false);
        assertArrayEquals(message.toBytes(), streamed.toByteArray());
        Jv.release(carried);
        assertEquals(0, files());
        assertEquals(0, disk.used());
    }

    private long files() throws IOException {
        try (Stream<Path> list = Files.list(temp)) {
            return list.count();
        }
    }
}
