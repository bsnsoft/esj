package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The account of what the server holds on disk: every byte counted before it is written, a
 * write that does not fit refused before a byte of it reaches the file, the share of a child
 * held from its start and replaced by what it wrote once it has ended, and every count given
 * back once its file is gone.
 */
class DiskTest {

    @TempDir
    private Path temp;

    @Test
    void aWriteThatDoesNotFitIsRefusedBeforeItReachesTheFile() throws IOException {
        Disk disk = new Disk(1000, temp);
        Path file = disk.newFile("body-", ".in");
        try (OutputStream out = disk.write(file)) {
            out.write(new byte[600]);
            assertEquals(600, disk.used());
            Disk.Full full = assertThrows(Disk.Full.class, () -> out.write(new byte[401]));
            assertTrue(full.getMessage().contains("--max-disk"), full.getMessage());
            out.write(new byte[400]);
        }
        assertEquals(1000, Files.size(file), "the refused write left nothing in the file");
        assertEquals(1000, disk.used());
        assertEquals(0, disk.free());
        disk.delete(file);
        assertFalse(Files.exists(file));
        assertEquals(0, disk.used(), "a file that is gone gives its bytes back");
    }

    @Test
    void aMovedFileKeepsItsCountAndAMissingOneIsFine() throws IOException {
        Disk disk = new Disk(1000, temp);
        Path file = disk.newFile("answer-", ".json");
        try (OutputStream out = disk.write(file)) {
            out.write(new byte[300]);
        }
        Path kept = disk.newFile("kept-", ".json");
        disk.move(file, kept);
        assertEquals(300, disk.used());
        assertEquals(300, Files.size(kept));
        disk.delete(file);
        assertEquals(300, disk.used(), "the old name counts nothing");
        disk.delete(kept);
        disk.delete(kept);
        assertEquals(0, disk.used());
    }

    @Test
    void aChildHoldsItsShareUntilWhatItWroteIsCounted() throws IOException {
        Disk disk = new Disk(Jobs.MAX_WRITTEN + 1000, temp);
        Path directory = Files.createTempDirectory(temp, "job-");
        disk.reserve(directory, Jobs.MAX_WRITTEN);
        assertEquals(Jobs.MAX_WRITTEN, disk.used());
        assertThrows(Disk.Full.class, () -> disk.reserve(temp.resolve("other"),
                Jobs.MAX_WRITTEN), "a second share does not fit");
        Path body = disk.newFile("body-", ".in");
        assertThrows(Disk.Full.class, () -> {
            try (OutputStream out = disk.write(body)) {
                out.write(new byte[1001]);
            }
        }, "the share is held, whatever the child has written");

        Files.write(directory.resolve(".stdout"), new byte[700]);
        Files.write(directory.resolve("invoice.pdf"), new byte[200]);
        assertEquals(900, Disk.size(directory));
        assertEquals(900, disk.settle(directory));
        assertEquals(900, disk.used(), "the share is replaced by what the child wrote");

        Path kept = disk.newFile("answer-", ".json");
        disk.move(directory.resolve(".stdout"), kept);
        disk.deleteTree(directory);
        assertFalse(Files.exists(directory));
        assertEquals(700, disk.used(), "what was moved out stays counted");
        disk.delete(kept);
        disk.delete(body);
        assertEquals(0, disk.used());
    }

    @Test
    void aDirectoryRemovedWhileItsChildRanGivesBackItsShare() throws IOException {
        Disk disk = new Disk(Jobs.MAX_WRITTEN, temp);
        Path directory = Files.createTempDirectory(temp, "job-");
        disk.reserve(directory, Jobs.MAX_WRITTEN);
        disk.deleteTree(directory);
        assertEquals(0, disk.used());
    }

    @Test
    void mebibytesAreWrittenWholeOrToOneDecimal() {
        assertEquals("448 MiB", Disk.mib(448L * 1024 * 1024));
        assertEquals("74.7 MiB", Disk.mib(Capacity.callFiles(32L * 1024 * 1024)));
    }
}
