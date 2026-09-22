package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.typed.Identifier;
import de.bsnsoft.esj.typed.IdentifierList;

/** Writes the occurrences of one repeatable identifier term into a document builder. */
final class TermIdentifierList extends TermValueList<Identifier> implements IdentifierList {

    TermIdentifierList(SemanticDocument.Builder builder, SemanticPath step) {
        super(builder, step, Values.IDENTIFIER, Writers.IDENTIFIER);
    }

    @Override
    public IdentifierList add(Identifier value) {
        append(value);
        return this;
    }

    @Override
    public IdentifierList add(String value) {
        return add(Identifier.of(value));
    }

    @Override
    public IdentifierList add(String value, String scheme) {
        return add(Identifier.of(value, scheme));
    }

    @Override
    public IdentifierList add(String value, String scheme, String schemeVersion) {
        return add(Identifier.of(value, scheme, schemeVersion));
    }

    @Override
    public IdentifierList remove(int index) {
        removeAt(index);
        return this;
    }

    @Override
    public IdentifierList clear() {
        builder.removeUnder(step);
        return this;
    }
}
