package de.bsnsoft.esj.syntax;

import java.time.Duration;
import java.util.Objects;

/**
 * What one component of a pack cost on one document.
 *
 * <p>The compilation is reported apart from the run because the two are paid at
 * different times. An artefact is compiled once per process and then reused, so a
 * long-lived process pays it on the first document and never again, while a command line
 * that starts a process per invoice pays it every time. A report that added the two
 * together would make the second look like the first.
 *
 * <p>A component that ran is not the same as one that reached a result. An artefact can
 * stop over a document — a value no arithmetic of the stylesheet can carry is the case
 * that happens — and it is here rather than left out of the list, because it was applied
 * to the document and it cost what it cost. {@code stopped} is what separates the two, so
 * that neither a reader nor a program can take the presence of a component in this list
 * for a statement that its rules were checked.
 *
 * @param component   the name of the component, as the pack manifest names it
 * @param engine      which check ran it
 * @param duration    how long the run over this document took
 * @param compilation how long compiling the artefact took, or {@link Duration#ZERO} when
 *                    it was already compiled in this process
 * @param stopped     whether the artefact stopped over this document instead of reaching
 *                    a result of its own, in which case it checked nothing and the report
 *                    carries its failure as a fatal finding
 */
public record ComponentRun(String component,
                           Engine engine,
                           Duration duration,
                           Duration compilation,
                           boolean stopped) {

    /**
     * Creates a record of one run.
     *
     * @param component   the name of the component, as the pack manifest names it
     * @param engine      which check ran it
     * @param duration    how long the run over this document took
     * @param compilation how long compiling the artefact took, or {@link Duration#ZERO} when
     *                    it was already compiled in this process
     * @param stopped     whether the artefact stopped over this document instead of reaching
     *                    a result of its own, in which case it checked nothing and the report
     *                    carries its failure as a fatal finding
     * @throws NullPointerException if an argument is {@code null}
     */
    public ComponentRun {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(compilation, "compilation");
    }
}
