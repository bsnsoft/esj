package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.typed.En16931;
import java.util.Objects;

/**
 * The two ways into the constrained builder.
 *
 * <p>{@link #create(Profile)} opens the step chain: one step per member the model
 * declares mandatory, in the order of the model, and the terminal step — the only one
 * that can end the chain — reachable only once every one of them has been written. A
 * caller writing an invoice by hand has the compiler on its side.
 *
 * <p>{@link #draft(Profile)} opens the typed editor instead: every member in any order,
 * and the same completeness checked when {@code build()} runs. A caller mapping from a
 * model of its own rarely has the members in the order of EN 16931-1 and would otherwise
 * have to buffer them.
 *
 * <p>Both produce the same document, both write the values the profile fixes when they
 * are opened, and neither decides anything about content.
 */
public final class InvoiceBuilder {

    private InvoiceBuilder() {
    }

    /**
     * Opens the step chain of a profile over a new, empty invoice.
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @param <S>     the first step of that profile's chain
     * @return the first step
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static <S> S create(Profile<S> profile) {
        return Objects.requireNonNull(profile, "profile").start(En16931.newInvoice());
    }

    /**
     * Opens a draft of a profile over a new, empty invoice.
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @return the draft
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static InvoiceDraft draft(Profile<?> profile) {
        return new InvoiceDraft(Objects.requireNonNull(profile, "profile"));
    }
}
