package de.bsnsoft.esj.syntax;

/**
 * Where the bytes of a pack come from: the class path of this module for a bundled pack,
 * a directory for one a caller points at.
 */
@FunctionalInterface
interface PackFiles {

    /**
     * The scheme the artefacts of a pack are loaded under. Nothing can dereference it, so
     * a reference inside an artefact reaches the resolver of this module rather than a
     * file or a host.
     */
    String SCHEME = "esj-pack";

    /**
     * Reads one file of the pack.
     *
     * @param path the path of the file inside the pack
     * @return the bytes
     * @throws PackException if the file cannot be read
     */
    byte[] read(String path);
}
