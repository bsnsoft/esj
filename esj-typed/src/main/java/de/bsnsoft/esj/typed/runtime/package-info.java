/**
 * What a generated view and a generated editor stand on: how a child path is built, how
 * the value a document carries at a path becomes a Java value of the semantic data type
 * the registry records, and how the occurrences of a repeatable term or group are
 * counted, appended and removed.
 *
 * <p>These types are public because a generated view lives in a package of its own — one
 * per edition of the semantic model — and not because a caller has business with them. A
 * caller reaches all of it through the view and the editor: through
 * {@link de.bsnsoft.esj.typed.En16931} for the 2017 edition, and through the
 * entry point of the corresponding package for every other one. Nothing here is a second
 * document model, nothing here holds state, and nothing here decides whether a document
 * is valid.
 *
 * <p>The mapping from a semantic data type to a Java type is written down once, in
 * {@link de.bsnsoft.esj.typed.runtime.Values} for reading and in
 * {@link de.bsnsoft.esj.typed.runtime.Writers} for writing, and every
 * generated accessor and setter names a constant of those two. A reader parses when it is
 * called and refuses content that does not satisfy the grammar of its type, which is the
 * layer L2 check of the specification, section 6.2 met on the way to a Java value.
 */
package de.bsnsoft.esj.typed.runtime;
