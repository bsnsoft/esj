package de.bsnsoft.esj;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a package, a type or a member as a preview.
 *
 * <p>A preview is published so that it can be used and judged before it is settled. It may
 * change or be removed in any minor release, and it is not covered by the compatibility
 * promise the rest of the API carries: a release that changes it says so in its change
 * log, and that is all it promises.
 *
 * <p>The mark reaches as far as Java names reach: everything in a package marked as a
 * preview is one, and so is every member of a type marked as one. A member of a type that
 * is not a preview can be one on its own, which is how a stable interface carries a
 * method that hands out a preview type.
 *
 * <p>The annotation is kept in the class files and read by the API compatibility check of
 * the build; nothing reads it at run time.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR,
        ElementType.FIELD})
public @interface Preview {
}
