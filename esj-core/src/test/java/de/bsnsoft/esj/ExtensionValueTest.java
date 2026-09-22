package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the immutable tree that holds the content of an extension subtree. */
class ExtensionValueTest {

    @Test
    void theMembersOfAnObjectAreHeldInCodePointOrder() {
        Map<String, ExtensionValue> members = new LinkedHashMap<>();
        members.put("z", ExtensionValue.of("last"));
        members.put("a", ExtensionValue.of("first"));
        members.put("😀", ExtensionValue.of("supplementary"));
        members.put("ﬀ", ExtensionValue.of("basic"));

        ExtensionValue.ObjectValue object = ExtensionValue.object(members);

        assertEquals(List.of("a", "z", "ﬀ", "😀"),
                List.copyOf(object.members().keySet()));
    }

    @Test
    void anObjectAndAnArrayAreCopiedAndUnmodifiable() {
        Map<String, ExtensionValue> members = new LinkedHashMap<>();
        members.put("a", ExtensionValue.of("one"));
        List<ExtensionValue> elements = new java.util.ArrayList<>(List.of(ExtensionValue.of("one")));

        ExtensionValue.ObjectValue object = ExtensionValue.object(members);
        ExtensionValue.ArrayValue array = ExtensionValue.array(elements);
        members.put("b", ExtensionValue.of("two"));
        elements.add(ExtensionValue.of("two"));

        assertEquals(1, object.members().size());
        assertEquals(1, array.elements().size());
        assertThrows(UnsupportedOperationException.class,
                () -> object.members().put("c", ExtensionValue.nullValue()));
        assertThrows(UnsupportedOperationException.class,
                () -> array.elements().add(ExtensionValue.nullValue()));
    }

    @Test
    void anArrayKeepsTheOrderOfItsElements() {
        ExtensionValue.ArrayValue array = ExtensionValue.array(List.of(
                ExtensionValue.of("c"), ExtensionValue.of("a"), ExtensionValue.of("b")));

        assertEquals(List.of(ExtensionValue.of("c"), ExtensionValue.of("a"), ExtensionValue.of("b")),
                array.elements());
    }

    @Test
    void aNumberKeepsEveryDigitTheSenderWrote() {
        BigDecimal exact = new BigDecimal("1.0000000000000001");

        assertEquals(exact, ExtensionValue.of(exact).value());
        assertEquals(new BigDecimal("12345678901234567890"),
                ExtensionValue.of(new BigDecimal("12345678901234567890")).value());
    }

    @Test
    void theOtherJsonValuesHaveTheirOwnShapes() {
        assertEquals("text", ExtensionValue.of("text").value());
        assertEquals(true, ExtensionValue.of(true).value());
        assertEquals(ExtensionValue.NullValue.NULL, ExtensionValue.nullValue());
    }

    @Test
    void theMemberOrderIsCodePointOrderAndNotUtf16Order() {
        assertEquals(-1, Integer.signum(ExtensionValue.memberOrder().compare("ﬀ", "😀")));
        assertEquals(1, Integer.signum("ﬀ".compareTo("😀")));
    }

    @Test
    void equalsHashCodeAndToStringAgreeWithTheShallowCase() {
        ExtensionValue object = ExtensionValue.object(
                Map.of("b", ExtensionValue.of("x"), "a", ExtensionValue.nullValue()));
        ExtensionValue same = ExtensionValue.object(
                Map.of("a", ExtensionValue.nullValue(), "b", ExtensionValue.of("x")));
        ExtensionValue other = ExtensionValue.object(Map.of("a", ExtensionValue.of("x")));

        assertEquals(same, object);
        assertEquals(same.hashCode(), object.hashCode());
        assertNotEquals(other, object);
        assertEquals("ObjectValue[members={a=NULL, b=StringValue[value=x]}]", object.toString());

        ExtensionValue array = ExtensionValue.array(
                List.of(ExtensionValue.of(true), ExtensionValue.of(new BigDecimal("1.5"))));
        assertEquals(ExtensionValue.array(
                List.of(ExtensionValue.of(true), ExtensionValue.of(new BigDecimal("1.5")))),
                array);
        assertEquals("ArrayValue[elements=[BooleanValue[value=true], NumberValue[value=1.5]]]",
                array.toString());
        assertNotEquals(ExtensionValue.array(List.of(ExtensionValue.of(true))), array);
        assertEquals(ExtensionValue.array(List.of()), ExtensionValue.array(List.of()));
    }

    /**
     * A tree the public API can build but the reader of a document would refuse for its
     * depth (specification, section 12.2). None of the three methods may answer it with a
     * {@link StackOverflowError}, which is an error and escapes every catch a caller
     * would write.
     */
    @Test
    void aTreeDeeperThanTheStackIsStillComparedHashedAndPrinted() {
        ExtensionValue deep = nest(50_000);
        ExtensionValue twin = nest(50_000);
        ExtensionValue shorter = nest(49_999);

        assertEquals(twin, deep);
        assertEquals(twin.hashCode(), deep.hashCode());
        assertNotEquals(shorter, deep);
        assertEquals(50_000 * "ObjectValue[members={a=".length() + "NULL".length()
                + 50_000 * "}]".length(), deep.toString().length());
    }

    @Test
    void aDeepArrayIsComparedHashedAndPrintedToo() {
        ExtensionValue deep = ExtensionValue.nullValue();
        for (int i = 0; i < 50_000; i++) {
            deep = ExtensionValue.array(List.of(deep));
        }
        ExtensionValue twin = ExtensionValue.nullValue();
        for (int i = 0; i < 50_000; i++) {
            twin = ExtensionValue.array(List.of(twin));
        }

        assertEquals(twin, deep);
        assertEquals(twin.hashCode(), deep.hashCode());
        assertEquals(50_000 * "ArrayValue[elements=[".length() + "NULL".length()
                + 50_000 * "]]".length(), deep.toString().length());
    }

    private static ExtensionValue nest(int depth) {
        ExtensionValue value = ExtensionValue.nullValue();
        for (int i = 0; i < depth; i++) {
            value = ExtensionValue.object(Map.of("a", value));
        }
        return value;
    }
}
