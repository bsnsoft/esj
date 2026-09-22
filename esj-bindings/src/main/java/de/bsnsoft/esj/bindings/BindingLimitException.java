package de.bsnsoft.esj.bindings;

/**
 * Signals that an input document asks for more than the reader grants it: more bytes than
 * it accepts, deeper element nesting than it walks, or a larger element than it holds in
 * memory to decide a predicate over.
 *
 * <p>The three bounds answer three different questions. The bound on bytes is the front
 * door and is met before anything is parsed. The bound on nesting keeps the reader's own
 * recursion whole. The bound on a buffered element is the one that makes the memory
 * claim of this module true: a predicate that asks about a child element is decided over
 * the element it stands on, so that element is held whole, and a document that writes an
 * element of unbounded size where a predicate stands is refused rather than read into a
 * heap of the sender's choosing.
 */
public final class BindingLimitException extends BindingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public BindingLimitException(String message) {
        super(message);
    }
}
