package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Walks the generated interfaces against the registry: every business term of the core
 * model has one accessor, with the shape its cardinality prescribes and the Java type its
 * semantic data type maps to, and the view carries nothing the registry does not.
 *
 * <p>The walk starts at the root view and follows the accessors of the business groups,
 * so it also proves that the interface of every group is reachable from the root. It runs
 * once per edition the build carries: a view belongs to one edition, and each one has to
 * be the view its own registry yields.
 */
class RegistryCoverageTest {

    /**
     * The editions the build carries, which is what this test runs over. Under the Maven
     * profile {@code without-edition-2026} the list is shorter and the walk is the same.
     *
     * @return the edition keys
     */
    static List<String> editions() {
        return Registry.editions();
    }

    @ParameterizedTest
    @MethodSource("editions")
    void everyTermOfTheRegistryHasOneAccessorAndTheViewCarriesNothingElse(String edition) {
        Registry registry = Registry.forEdition(edition);
        List<String> visited = new ArrayList<>();
        walk(registry, rootView(edition), null, visited);
        Set<String> groups = new LinkedHashSet<>();
        for (Term term : registry.terms()) {
            if (term.isGroup()) {
                groups.add(term.id());
            }
        }
        assertEquals(groups.size(), visited.size(), "every business group is viewed exactly once");
        assertTrue(visited.containsAll(groups));
    }

    /**
     * Returns the root view of an edition. The view of the edition the repository
     * defaults to lies beside the types every view shares; every other one lies in a
     * package of its own, and is reached by name so that this test compiles where that
     * package is left out.
     */
    private static Class<?> rootView(String edition) {
        String base = Invoice.class.getPackageName();
        String viewPackage = Registry.DEFAULT_EDITION.equals(edition)
                ? base
                : base + ".v" + edition;
        try {
            return Class.forName(viewPackage + ".Invoice");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("the build carries the registry of " + edition
                    + " and no view generated from it", e);
        }
    }

    private void walk(Registry registry, Class<?> type, String groupId, List<String> visited) {
        if (groupId != null) {
            visited.add(groupId);
        }
        List<Term> children = registry.children(groupId);
        List<String> expected = new ArrayList<>();
        for (Term child : children) {
            expected.add(child.slug());
        }
        List<String> declared = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!"document".equals(method.getName()) && !"path".equals(method.getName())) {
                declared.add(method.getName());
            }
        }
        declared.sort(String::compareTo);
        List<String> sortedExpected = new ArrayList<>(expected);
        sortedExpected.sort(String::compareTo);
        assertEquals(sortedExpected, declared, "the accessors of " + type.getSimpleName());

        for (Term child : children) {
            Method accessor = accessor(type, child.slug());
            Class<?> valueType = element(accessor, child);
            if (child.isGroup()) {
                walk(registry, valueType, child.id(), visited);
            } else {
                SemanticType datatype = child.datatype().orElseThrow();
                assertEquals(javaType(datatype), valueType.getSimpleName(),
                        "the Java type of " + child.id());
            }
        }
    }

    /**
     * The Java type an accessor of a business term of a given semantic data type returns.
     * It is written out here rather than read from the generator, so that the test states
     * the mapping of the change note independently of the code that emits it.
     */
    private static String javaType(SemanticType datatype) {
        return switch (datatype) {
            case TEXT, CODE, DOCUMENT_REFERENCE -> "String";
            case IDENTIFIER -> "Identifier";
            case DATE -> "LocalDate";
            case TIME -> "OffsetTime";
            case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE -> "BigDecimal";
            case BINARY_OBJECT -> "BinaryObject";
        };
    }

    private static Method accessor(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type.getSimpleName() + " has no accessor " + name, e);
        }
    }

    private static Class<?> element(Method accessor, Term term) {
        Type returned = accessor.getGenericReturnType();
        if (term.cardinality().isRepeatable()) {
            return argumentOf(returned, List.class, term);
        }
        if (term.cardinality().isMandatory()) {
            assertTrue(returned instanceof Class, "the accessor of " + term.id() + " returns a plain type");
            return (Class<?>) returned;
        }
        return argumentOf(returned, Optional.class, term);
    }

    private static Class<?> argumentOf(Type returned, Class<?> container, Term term) {
        assertTrue(returned instanceof ParameterizedType,
                "the accessor of " + term.id() + " returns " + container.getSimpleName());
        ParameterizedType parameterized = (ParameterizedType) returned;
        assertEquals(container, parameterized.getRawType(), "the accessor of " + term.id());
        return (Class<?>) parameterized.getActualTypeArguments()[0];
    }

    @Test
    void theTypedViewOfVersionZeroPointOneCarriesTheCoreModelAlone() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.bsnsoft.esj.typed.SubInvoiceLine"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.bsnsoft.esj.typed.ThirdPartyPayment"));
        for (Method method : Invoice.class.getDeclaredMethods()) {
            assertTrue(!"thirdPartyPayments".equals(method.getName()),
                    "the root view carries no extension group");
        }
        for (Method method : InvoiceLine.class.getDeclaredMethods()) {
            assertTrue(!"subInvoiceLines".equals(method.getName()),
                    "an invoice line carries no extension group");
        }
    }
}
