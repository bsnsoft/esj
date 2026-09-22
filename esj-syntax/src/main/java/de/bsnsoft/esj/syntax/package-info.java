/**
 * The syntax engine: validation of a UBL 2.1 or UN/CEFACT CII invoice against the
 * official validation artefacts of its profile.
 *
 * <p>The artefacts are the XML Schema modules of the syntax and the Schematron rule sets
 * of EN 16931 and of the core invoice usage specification a document names, compiled to
 * XSLT by their publishers. They are executed as data. Nothing in this package is derived
 * from the expressions inside them, and no rule of theirs is restated in Java: the rules
 * are corrected and re-released by the bodies that own them, and a second opinion with its
 * own release cycle would be wrong on the day one of those corrections ships.
 *
 * <p>The artefacts travel with the repository as <em>validation packs</em>, under
 * {@code packs/} at its root, and are packaged into this module below
 * {@code de/bsnsoft/esj/syntax/packs} on the class path. A pack is named by
 * profile, profile version and release date — {@code xrechnung/3.0.2/2026-08-31} — and
 * carries a {@code pack.json} that says which component applies to which document syntax
 * and profile, under which licence each component is distributed, and the SHA-256 of every
 * file in the pack. The material under {@code packs/} is third-party work under its own
 * licence; {@code packs/SOURCES.md} records its provenance and {@code packs/README.md}
 * describes the layout.
 *
 * <h2>Where to start</h2>
 *
 * <p>{@link de.bsnsoft.esj.syntax.SyntaxValidator} validates one document and
 * answers with a {@link de.bsnsoft.esj.syntax.SyntaxReport}: a verdict, the
 * findings in a deterministic order, and which components ran and which did not with the
 * reason. {@link de.bsnsoft.esj.syntax.SyntaxOptions} says what a run may do —
 * which pack, how many bytes of input, how long. {@link de.bsnsoft.esj.syntax.Packs}
 * is the packs themselves, and {@link de.bsnsoft.esj.syntax.Pack#select}
 * decides which of a pack's components a document gets.
 * {@link de.bsnsoft.esj.syntax.ProfileLevels} answers what the profile a
 * document names makes of a rule, for a caller that reports findings of another engine
 * about the same document.
 *
 * <p>A limit that is reached and a document of a syntax no pack binds each raise an
 * exception rather than returning a verdict, so a document nobody managed to check never
 * reads as an invalid one.
 *
 * <p>Findings of this engine are findings about the document's binding to its XML syntax.
 * They are a layer of their own and are never presented as conformance to the ESJ format,
 * which is defined by layers L1 to L3 of the specification alone.
 */
package de.bsnsoft.esj.syntax;
