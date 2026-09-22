/**
 * What a reader has to say about the document it read: the elements it could not place,
 * the values it could not read and the supplementary components it dropped.
 *
 * <p>A reader turns a document of a transport syntax into a semantic document, and the
 * difference between the two is not a validation finding — the findings of
 * {@code de.bsnsoft.esj.validate} describe a document that exists, these
 * notes describe what the input carried and the document does not. Every reader of this
 * project speaks this vocabulary, whichever syntax and whichever parser it reads with, so
 * a caller that handles the outcome of one reader handles the outcome of all of them.
 */
package de.bsnsoft.esj.imports;
