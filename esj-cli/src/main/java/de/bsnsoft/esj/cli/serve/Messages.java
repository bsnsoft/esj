package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads one message of a client — a JSON-RPC message of MCP — within bounds that do not
 * depend on how large an upload may be.
 *
 * <p>A message is read from its stream as it arrives and never as a whole: its member names,
 * strings and numbers together may have {@link #MAX_CHARACTERS} characters and it may hold
 * {@link #MAX_VALUES} values, which is far more than any request of these tools needs and
 * little enough for every thread of the server to hold one: about 0.2 MiB at both bounds
 * ({@link Capacity#REQUEST}). The one string that may be
 * longer is the {@code content_base64} of an upload over HTTP: it is written to a file of
 * the server's temporary directory as it is read, held as a {@link Jv.Blob}, and decoded
 * from there into the store, so that an upload of {@code --max-upload} bytes costs the heap
 * a buffer and not its length.
 */
final class Messages {

    /** The most characters of a message beside the content of an upload. */
    static final int MAX_CHARACTERS = 64 * 1024;

    /** The most values of a message: members, items and scalars. */
    static final int MAX_VALUES = 1_024;

    /** A message past {@link #MAX_CHARACTERS} or {@link #MAX_VALUES}. */
    static final class TooLarge extends Exception {
        private static final long serialVersionUID = 1L;

        TooLarge(String message) {
            super(message);
        }
    }

    /**
     * Where the content of an upload goes while a message is read.
     *
     * @param disk          the account its file is made in and counted against; a content
     *                      that does not fit fails the reading with {@link Disk.Full}
     * @param maxCharacters the longest content kept; a longer one is read to its end and
     *                      marked incomplete
     * @param files         the files made, for the caller to remove
     */
    record Uploads(Disk disk, long maxCharacters, List<Path> files) {
    }

    private final JsonCursor cursor;
    private final Optional<Uploads> uploads;
    private long characters;
    private int values;

    private Messages(InputStream in, Optional<Uploads> uploads) {
        this.cursor = new JsonCursor(in);
        this.uploads = uploads;
    }

    /**
     * Reads one message, refusing anything after it.
     *
     * @param in      the bytes, UTF-8
     * @param uploads where the content of an upload goes; empty, it is read as any string
     * @return the message
     * @throws Jv.JsonException where the bytes are not one JSON value
     * @throws TooLarge         where the message is past a bound
     * @throws IOException      where the stream fails
     */
    static Jv read(InputStream in, Optional<Uploads> uploads) throws TooLarge, IOException {
        Messages messages = new Messages(in, uploads);
        try {
            if (messages.cursor.next() == JsonCursor.Token.END) {
                throw new Jv.JsonException("the message is empty");
            }
            Jv message = messages.value(new ArrayList<>());
            messages.cursor.next();
            return message;
        } catch (JsonCursor.Malformed e) {
            throw new Jv.JsonException("the message is not JSON: " + e.getMessage());
        }
    }

    private Jv value(List<String> names) throws TooLarge, IOException {
        if (++values > MAX_VALUES) {
            throw new TooLarge("the message holds more than " + MAX_VALUES + " values");
        }
        switch (cursor.current()) {
            case STRING -> {
                JsonCursor.Text text = cursor.text(remaining() + 1);
                count(text.length());
                return Jv.of(text.prefix());
            }
            case NUMBER -> {
                count(cursor.number().length());
                return new Jv.Num(cursor.number());
            }
            case TRUE -> {
                return Jv.of(true);
            }
            case FALSE -> {
                return Jv.of(false);
            }
            case NULL -> {
                return Jv.NULL;
            }
            case BEGIN_ARRAY -> {
                List<Jv> items = new ArrayList<>();
                while (cursor.next() != JsonCursor.Token.END_ARRAY) {
                    names.add(null);
                    items.add(value(names));
                    names.remove(names.size() - 1);
                }
                return new Jv.Arr(items);
            }
            case BEGIN_OBJECT -> {
                Map<String, Jv> members = new LinkedHashMap<>();
                while (cursor.next() != JsonCursor.Token.END_OBJECT) {
                    String name = cursor.name();
                    count(name.length());
                    cursor.next();
                    Jv member;
                    if (isUpload(names, name)) {
                        member = upload();
                    } else {
                        names.add(name);
                        member = value(names);
                        names.remove(names.size() - 1);
                    }
                    if (members.put(name, member) != null) {
                        throw new Jv.JsonException("the member " + Visible.text(name)
                                + " appears twice");
                    }
                }
                return new Jv.Obj(members);
            }
            default -> throw new Jv.JsonException("the message is not JSON: unexpected "
                    + cursor.current());
        }
    }

    /** Tells whether a member is the content of an upload, params.arguments.content_base64. */
    private boolean isUpload(List<String> names, String name) {
        int size = names.size();
        return uploads.isPresent() && "content_base64".equals(name) && size >= 2
                && "arguments".equals(names.get(size - 1)) && "params".equals(names.get(size - 2))
                && cursor.current() == JsonCursor.Token.STRING;
    }

    private Jv upload() throws IOException {
        Uploads to = uploads.orElseThrow();
        Path file = to.disk().newFile("upload-", ".b64");
        to.files().add(file);
        long length;
        try (OutputStream out = new java.io.BufferedOutputStream(to.disk().write(file))) {
            length = cursor.textTo(out, to.maxCharacters());
        }
        return new Jv.Blob(file, length, length <= to.maxCharacters());
    }

    private int remaining() {
        return (int) Math.max(0, MAX_CHARACTERS - characters);
    }

    private void count(long length) throws TooLarge {
        characters += length;
        if (characters > MAX_CHARACTERS) {
            throw new TooLarge("the message is longer than " + MAX_CHARACTERS + " characters"
                    + (uploads.isPresent() ? " beside the content of an upload" : ""));
        }
    }
}
