package de.bsnsoft.esj.syntax;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Answers a schema's references out of the pack, and answers nothing else.
 *
 * <p>An XML Schema module imports the modules beside it by a relative reference, so
 * something has to resolve those. This resolver does it from the files the component
 * lists and from nowhere else: a reference that resolves to a file the component does not
 * list is not answered, and the platform then refuses it because external access is off.
 * A schema reference in the document — an {@code xsi:schemaLocation} pointing at a host —
 * therefore reaches no host.
 */
final class PackResolver implements LSResourceResolver {

    private final Pack pack;
    private final List<String> files;

    /**
     * Creates a resolver over the files of one component.
     *
     * @param pack  the pack the files are read from
     * @param files the paths inside the pack the component consists of
     */
    PackResolver(Pack pack, List<String> files) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.files = List.copyOf(Objects.requireNonNull(files, "files"));
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId,
                                   String systemId, String baseURI) {
        String path = resolve(systemId, baseURI);
        if (path == null || !files.contains(path)) {
            return null;
        }
        return new PackInput(pack.read(path), pack.uri(path));
    }

    /**
     * Resolves a reference of a schema against the schema it stands in, and returns the
     * path it names inside the pack, or {@code null} for a reference that leaves the pack.
     */
    private String resolve(String systemId, String baseURI) {
        if (systemId == null) {
            return null;
        }
        try {
            URI reference = new URI(systemId);
            URI resolved = baseURI == null ? reference : new URI(baseURI).resolve(reference);
            if (!PackFiles.SCHEME.equals(resolved.getScheme())) {
                return null;
            }
            String path = resolved.getPath();
            return path == null || !path.startsWith("/") ? null : path.substring(1);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /** One file of a pack, handed to the schema factory as the bytes it asked for. */
    private static final class PackInput implements LSInput {

        private final byte[] content;
        private String systemId;
        private String publicId;
        private String baseUri;
        private String encoding;
        private String stringData;
        private boolean certifiedText;

        private PackInput(byte[] content, String systemId) {
            this.content = content;
            this.systemId = systemId;
            this.baseUri = systemId;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            // The bytes are what a schema module is; nothing hands this one a reader.
        }

        @Override
        public InputStream getByteStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            // The content of a pack file is fixed; nothing replaces it.
        }

        @Override
        public String getStringData() {
            return stringData;
        }

        @Override
        public void setStringData(String stringData) {
            this.stringData = stringData;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            this.systemId = systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            this.publicId = publicId;
        }

        @Override
        public String getBaseURI() {
            return baseUri;
        }

        @Override
        public void setBaseURI(String baseUri) {
            this.baseUri = baseUri;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        @Override
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        @Override
        public boolean getCertifiedText() {
            return certifiedText;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            this.certifiedText = certifiedText;
        }
    }
}
