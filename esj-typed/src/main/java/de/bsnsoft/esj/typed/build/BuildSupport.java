package de.bsnsoft.esj.typed.build;

import java.util.HashSet;
import java.util.Set;

/**
 * What every implementation of a chain shares: the record of which members have been
 * written into this group instance, so that a member the model allows at most once is
 * refused the second time.
 *
 * <p>A mandatory member cannot be written twice through the chain at all — its step
 * returns the following step, which has no setter for it, so a second call does not
 * compile. An optional member sits on the terminal step, which returns itself, and there
 * the second call is caught here.
 */
abstract class BuildSupport {

    private final Set<String> written = new HashSet<>();

    BuildSupport() {
    }

    /**
     * Records that a member has been written and refuses a second write.
     *
     * @param term the identifier of the business term or business group
     * @param name the name of that term or group
     * @throws IllegalStateException if the member has already been written here
     */
    protected final void once(String term, String name) {
        if (!written.add(term)) {
            throw new IllegalStateException(term + " " + name + " occurs at most once in a group"
                    + " instance and has already been written through this builder");
        }
    }
}
