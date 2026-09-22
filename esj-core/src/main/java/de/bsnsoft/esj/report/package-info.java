/**
 * What a whole validation run came to, in one shape a report can be written from.
 *
 * <p>A complete run puts several engines beside each other — the checks of a container,
 * the official artefacts of a profile, the structural layers, the business rules of the
 * standard — and those engines live in modules that do not know each other. This package
 * is the one vocabulary they are flattened into:
 * {@link de.bsnsoft.esj.report.ValidationOutcome} carries the identity of what
 * was judged, the check table, the findings grouped by the engine that produced them, and
 * the verdict.
 *
 * <p>It is a data model and nothing else: it decides no verdict, derives no row and sorts
 * no finding. Whoever ran the engines fills it in, and whoever renders it reads it. That
 * is what lets a renderer put a run on a page without depending on every module that took
 * part in it.
 *
 * <p>{@link de.bsnsoft.esj.validate} is the neighbouring package and a
 * different thing: it is the structural validator of this library and the result of that
 * one validator. An outcome here is the answer of a whole command.
 */
package de.bsnsoft.esj.report;
