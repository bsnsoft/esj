package de.bsnsoft.esj.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.CustomCode;
import de.bsnsoft.esj.invoice.code.NoteSubject;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.typed.build.BuildException;
import de.bsnsoft.esj.typed.build.BuildReport;
import de.bsnsoft.esj.typed.build.Profile;
import de.bsnsoft.esj.typed.build.RuleViolation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The item attributes of a line (BG-32, BT-160, BT-161) and the subject code of an invoice
 * note (BT-21): what the domain API writes for them, what it reads back and what it
 * refuses.
 */
class NotesAndItemAttributesTest {

    @Test
    void aLineCarriesItsAttributesInTheOrderTheyWereWritten() {
        Line line = Line.of("Lawn care").quantity(1, Unit.PIECE).unitPrice("500")
                .vat(Vat.standard(19))
                .attribute("Labour cost net (Section 35a EStG)", "412.50")
                .attribute("Colour", "green")
                .attribute("Colour", "blue");

        SemanticDocument document = minimal().line(line).build();

        assertEquals(Optional.empty(),
                document.value(SemanticPath.of("/BG-25/0/BG-31/BG-32/0/BT-160")));
        assertEquals(List.of(new ItemAttribute("Labour cost net (Section 35a EStG)", "412.50"),
                new ItemAttribute("Colour", "green"), new ItemAttribute("Colour", "blue")),
                line.attributes());
        assertEquals(SemanticValue.of("Labour cost net (Section 35a EStG)"),
                value(document, "/BG-25/1/BG-31/BG-32/0/BT-160"));
        assertEquals(SemanticValue.of("412.50"), value(document, "/BG-25/1/BG-31/BG-32/0/BT-161"));
        assertEquals(SemanticValue.of("Colour"), value(document, "/BG-25/1/BG-31/BG-32/2/BT-160"));
        assertEquals(SemanticValue.of("blue"), value(document, "/BG-25/1/BG-31/BG-32/2/BT-161"));
        assertEquals(Optional.empty(),
                document.value(SemanticPath.of("/BG-25/1/BG-31/BG-32/3/BT-160")));
    }

    @Test
    void anAttributeLeavesTheLineItWasAddedToUnchanged() {
        Line plain = Line.of("Sensor");
        Line attributed = plain.attribute("Colour", "blue");

        assertTrue(plain.attributes().isEmpty());
        assertEquals(1, attributed.attributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> attributed.attributes().add(new ItemAttribute("Size", "M")));
        assertEquals("Colour: blue", attributed.attributes().get(0).toString());
    }

    @Test
    void anAttributeNamesItsNameAndItsValue() {
        assertThrows(IllegalArgumentException.class, () -> Line.of("Sensor").attribute(" ", "1"));
        assertThrows(IllegalArgumentException.class, () -> Line.of("Sensor").attribute("Size", ""));
        assertThrows(NullPointerException.class, () -> Line.of("Sensor").attribute(null, "1"));
        assertThrows(NullPointerException.class, () -> Line.of("Sensor").attribute("Size", null));
        assertThrows(NullPointerException.class, () -> new ItemAttribute("Size", null));
    }

    @Test
    void theReadSideReturnsTheAttributesOfEveryLine() {
        SemanticDocument document = minimal()
                .line(Line.of("Second").quantity(1, Unit.PIECE).unitPrice("1")
                        .vat(Vat.standard(19)).attribute("Batch", "B-7"))
                .build();

        List<Line> lines = Invoice.read(document).lines();

        assertEquals(List.of(), lines.get(0).attributes());
        assertEquals(List.of(new ItemAttribute("Batch", "B-7")), lines.get(1).attributes());
    }

    @Test
    void aNoteWithASubjectWritesTheCodeBesideTheText() {
        SemanticDocument document = minimal()
                .note("Delivered ex works.")
                .note(NoteSubject.GENERAL_INFORMATION,
                        "Labour cost under Section 35a EStG: 412.50 EUR")
                .note(NoteSubject.resolve("REG"), "Reverse charge does not apply.")
                .build();

        assertEquals(SemanticValue.of("Delivered ex works."), value(document, "/BG-1/0/BT-22"));
        assertEquals(Optional.empty(), document.value(SemanticPath.of("/BG-1/0/BT-21")));
        assertEquals(SemanticValue.of("AAI"), value(document, "/BG-1/1/BT-21"));
        assertEquals(SemanticValue.of("Labour cost under Section 35a EStG: 412.50 EUR"),
                value(document, "/BG-1/1/BT-22"));
        assertEquals(SemanticValue.of("REG"), value(document, "/BG-1/2/BT-21"));
        assertEquals(List.of("Delivered ex works.",
                "Labour cost under Section 35a EStG: 412.50 EUR",
                "Reverse charge does not apply."), Invoice.read(document).notes());
    }

    @Test
    void theDraftAndTheStepChainWriteTheSameNote() {
        SemanticDocument chained = minimal().note(NoteSubject.SUPPLIER_REMARKS, "Thank you.")
                .build();
        Draft draft = minimal().draft();
        SemanticDocument drafted = draft.note((Coded) NoteSubject.SUPPLIER_REMARKS, "Thank you.")
                .build();

        assertEquals(chained, drafted);
        assertEquals(SemanticValue.of("SUR"), value(drafted, "/BG-1/0/BT-21"));
    }

    @Test
    void aCodeTheListDoesNotCarryIsRefusedByTheRulePackNotByTheWriter() {
        Coded unknown = NoteSubject.resolve("XYZ");
        assertInstanceOf(CustomCode.class, unknown);
        assertSame(NoteSubject.GENERAL_INFORMATION, NoteSubject.resolve("AAI"));

        Draft draft = minimal().draft().note(unknown, "A note under an unknown subject.");
        BuildReport report = draft.validate();
        List<RuleViolation> clash = report.violations().stream()
                .filter(violation -> "BR-CL-08".equals(violation.rule()))
                .toList();

        assertEquals(1, clash.size(), report.lines().toString());
        assertTrue(clash.get(0).fatal(), clash.get(0).toString());
        BuildException refused = assertThrows(BuildException.class, draft::build);
        assertTrue(refused.report().lines().toString().contains("BR-CL-08"),
                refused.report().lines().toString());
    }

    @Test
    void aNoteWithASubjectStillStatesItsText() {
        assertThrows(IllegalArgumentException.class,
                () -> minimal().note(NoteSubject.GENERAL_INFORMATION, " "));
        assertThrows(NullPointerException.class,
                () -> minimal().note((NoteSubject) null, "Text"));
        assertThrows(NullPointerException.class,
                () -> Invoice.draft(Profile.EN16931).note((Coded) null, "Text"));
        assertFalse(minimal().draft().validate().violations().stream()
                .anyMatch(violation -> "BR-CL-08".equals(violation.rule())));
    }

    /** The snippet of the {@code docs/java-api.md} section "The domain API", write side. */
    @Test
    void theJavaApiPageSnippetWritesAnAttributeAndASubject() {
        Draft draft = minimal().draft();

        // docs/java-api.md: The domain API, item attributes and note subjects
        Line line = Line.of("Hedge trimming").quantity(5, Unit.HOUR).unitPrice("82.50")
                .vat(Vat.standard(19))
                .attribute("Labour cost, net", "412.50");
        draft.line(line)
                .note(NoteSubject.GENERAL_INFORMATION, "Contains labour cost of 412.50 EUR.");
        List<ItemAttribute> attributes = line.attributes();
        // end

        SemanticDocument document = draft.build();
        assertEquals(List.of(new ItemAttribute("Labour cost, net", "412.50")), attributes);
        assertEquals(attributes, Invoice.read(document).lines().get(1).attributes());
        assertEquals(SemanticValue.of("AAI"), value(document, "/BG-1/0/BT-21"));
    }

    private static InvoiceSteps.Buildable minimal() {
        return Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor").quantity(2, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."));
    }

    private static SemanticValue value(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).orElseThrow(
                () -> new AssertionError(path + " is not in the document"));
    }
}
