package de.bsnsoft.esj.validate;

/**
 * The three validation layers of the specification, section 9. Each finding code belongs
 * to exactly one of them.
 */
public enum ValidationLayer {

    /** Format: the checks that need no registry (specification, section 9.1). */
    L1,

    /** Model: the checks against the registry for one path (specification, section 9.2). */
    L2,

    /** Cardinality: the checks over the document as a whole (specification, section 9.3). */
    L3
}
