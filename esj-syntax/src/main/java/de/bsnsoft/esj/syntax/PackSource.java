package de.bsnsoft.esj.syntax;

/**
 * Where the artefacts of a pack came from.
 *
 * <p>A report that named only the identity a pack declares about itself would say the
 * same thing about the reviewed release this build carries and about any directory a
 * caller pointed {@code --pack} at, because a manifest is free to call itself whatever it
 * likes. The caller who supplied the directory knows what is in it; the person who reads
 * the report afterwards, or the repository the report is checked into, does not. So the
 * provenance travels with the pack.
 */
public enum PackSource {

    /**
     * The pack is one this build carries: its files were vendored with the source, their
     * digests are recorded in {@code packs/SOURCES.md} and their licences are shipped
     * beside them.
     */
    BUNDLED("bundled"),

    /** The pack was read from a directory the caller named. */
    SUPPLIED("supplied");

    private final String token;

    PackSource(String token) {
        this.token = token;
    }

    /**
     * Returns the token a report writes this provenance with.
     *
     * @return the token
     */
    public String token() {
        return token;
    }
}
