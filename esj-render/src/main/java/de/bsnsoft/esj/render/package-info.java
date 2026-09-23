/**
 * Renders an ESJ document for a human reader, as HTML and as a PDF.
 *
 * <p>{@link de.bsnsoft.esj.render.HtmlRenderer} writes one self-contained HTML
 * page: the document is written as the semantic XR representation by {@code esj-xr} and
 * handed to the HTML stylesheet of the KoSIT XRechnung visualization, which is vendored
 * unmodified in this module under the Apache License, Version 2.0. This project therefore
 * ships a rendering without having designed one, and a reader who has seen an electronic
 * invoice in Germany before recognizes it.
 *
 * <p>{@link de.bsnsoft.esj.render.PdfRenderer} writes a PDF, and that one is
 * laid out by this project: PDFBox draws text and lines, by default as the letter a business
 * sends and on request in the shape of the semantic model, and neither is a template.
 * Whatever a layout has no place of its own for is printed under a last heading with its
 * label and its semantic path, so that no value of a document is quietly missing. Liberation
 * Sans is vendored beside the code under the SIL Open Font License, and a subset of it is
 * embedded in every file.
 *
 * <p>Nothing in this package knows what an invoice looks like beyond those two layouts.
 * What the HTML side knows is how to put the document in front of a stylesheet without
 * letting that stylesheet reach the file system or the network: the three files it imports,
 * the two localization files it reads and the three files it inlines are answered out of the
 * classpath of this module, and no other reference is answered at all. What the PDF side
 * knows is a page, a cursor and a table.
 *
 * <p>Both renderings are net, as the semantic model is, and derive nothing. Gross figures for
 * a consumer belong to an extension of the model and to a renderer that was asked for them;
 * {@code docs/rendering.md} says so in one paragraph, and the design decisions of the README
 * say it again.
 *
 * <p>A rendering is a function of the document, the options and the version of this module.
 * Rendering the same document twice gives the same string and the same bytes.
 */
package de.bsnsoft.esj.render;
