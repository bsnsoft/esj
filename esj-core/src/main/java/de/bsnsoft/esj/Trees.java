package de.bsnsoft.esj;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Comparing, hashing and printing an {@link ExtensionValue} tree without recursion.
 *
 * <p>The tree of an extension can be built through the public API to any depth: the
 * nesting bound of the specification, section 12.2 is a limit a reader enforces on a
 * document, not a rule about the model. The three methods a record generates would answer
 * such a tree with a {@link StackOverflowError}, which is an {@code Error} and escapes
 * every catch a caller would write, so the two container records override them and the
 * walks live here, each with an explicit deque.
 */
final class Trees {

    private Trees() {
        throw new AssertionError("no instances");
    }

    /**
     * Compares two trees node by node.
     *
     * @param left  one tree
     * @param right the other tree
     * @return {@code true} if the two trees hold the same content in the same order
     */
    static boolean equal(ExtensionValue left, ExtensionValue right) {
        Deque<ExtensionValue> ours = new ArrayDeque<>();
        Deque<ExtensionValue> theirs = new ArrayDeque<>();
        ours.push(left);
        theirs.push(right);
        while (!ours.isEmpty()) {
            ExtensionValue one = ours.pop();
            ExtensionValue other = theirs.pop();
            if (one instanceof ExtensionValue.ObjectValue object) {
                if (!(other instanceof ExtensionValue.ObjectValue peer)
                        || !sameMembers(object, peer, ours, theirs)) {
                    return false;
                }
            } else if (one instanceof ExtensionValue.ArrayValue array) {
                if (!(other instanceof ExtensionValue.ArrayValue peer)
                        || !sameElements(array, peer, ours, theirs)) {
                    return false;
                }
            } else if (!one.equals(other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a hash of a whole tree. It is the hash the generated method would have
     * produced: the members of an object contribute as the entries of a map do, and the
     * elements of an array as the elements of a list do.
     *
     * @param root the tree
     * @return the hash code
     */
    static int hash(ExtensionValue root) {
        List<ExtensionValue> order = preOrder(root);
        Deque<Integer> results = new ArrayDeque<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            ExtensionValue node = order.get(i);
            if (node instanceof ExtensionValue.ObjectValue object) {
                int hash = 0;
                for (String name : object.members().keySet()) {
                    hash += name.hashCode() ^ results.pop();
                }
                results.push(hash);
            } else if (node instanceof ExtensionValue.ArrayValue array) {
                int hash = 1;
                for (int element = 0; element < array.elements().size(); element++) {
                    hash = 31 * hash + results.pop();
                }
                results.push(hash);
            } else {
                results.push(node.hashCode());
            }
        }
        return results.pop();
    }

    /**
     * Writes a whole tree in the shape a record prints.
     *
     * @param root the tree
     * @return the printed tree
     */
    static String print(ExtensionValue root) {
        StringBuilder out = new StringBuilder();
        Deque<Object> work = new ArrayDeque<>();
        work.push(root);
        while (!work.isEmpty()) {
            Object item = work.pop();
            if (item instanceof String literal) {
                out.append(literal);
            } else if (item instanceof ExtensionValue.ObjectValue object) {
                pushAll(work, objectParts(object));
            } else if (item instanceof ExtensionValue.ArrayValue array) {
                pushAll(work, arrayParts(array));
            } else {
                out.append(item);
            }
        }
        return out.toString();
    }

    private static boolean sameMembers(ExtensionValue.ObjectValue one,
                                       ExtensionValue.ObjectValue other,
                                       Deque<ExtensionValue> ours,
                                       Deque<ExtensionValue> theirs) {
        if (one.members().size() != other.members().size()) {
            return false;
        }
        var mine = one.members().entrySet().iterator();
        var yours = other.members().entrySet().iterator();
        while (mine.hasNext()) {
            Map.Entry<String, ExtensionValue> entry = mine.next();
            Map.Entry<String, ExtensionValue> peer = yours.next();
            if (!entry.getKey().equals(peer.getKey())) {
                return false;
            }
            ours.push(entry.getValue());
            theirs.push(peer.getValue());
        }
        return true;
    }

    private static boolean sameElements(ExtensionValue.ArrayValue one,
                                        ExtensionValue.ArrayValue other,
                                        Deque<ExtensionValue> ours,
                                        Deque<ExtensionValue> theirs) {
        if (one.elements().size() != other.elements().size()) {
            return false;
        }
        for (int i = 0; i < one.elements().size(); i++) {
            ours.push(one.elements().get(i));
            theirs.push(other.elements().get(i));
        }
        return true;
    }

    /**
     * Returns every node of the tree, an ancestor always before its descendants and the
     * children of a node in reverse order, which is what lets {@link #hash(ExtensionValue)}
     * consume the children's results in their own order as it walks the list backwards.
     */
    private static List<ExtensionValue> preOrder(ExtensionValue root) {
        List<ExtensionValue> order = new ArrayList<>();
        Deque<ExtensionValue> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            ExtensionValue node = pending.pop();
            order.add(node);
            if (node instanceof ExtensionValue.ObjectValue object) {
                List<ExtensionValue> members = new ArrayList<>(object.members().values());
                for (int i = members.size() - 1; i >= 0; i--) {
                    pending.push(members.get(i));
                }
            } else if (node instanceof ExtensionValue.ArrayValue array) {
                for (int i = array.elements().size() - 1; i >= 0; i--) {
                    pending.push(array.elements().get(i));
                }
            }
        }
        return order;
    }

    private static List<Object> objectParts(ExtensionValue.ObjectValue object) {
        List<Object> parts = new ArrayList<>();
        parts.add("ObjectValue[members={");
        boolean first = true;
        for (Map.Entry<String, ExtensionValue> entry : object.members().entrySet()) {
            if (!first) {
                parts.add(", ");
            }
            parts.add(entry.getKey() + "=");
            parts.add(entry.getValue());
            first = false;
        }
        parts.add("}]");
        return parts;
    }

    private static List<Object> arrayParts(ExtensionValue.ArrayValue array) {
        List<Object> parts = new ArrayList<>();
        parts.add("ArrayValue[elements=[");
        for (int i = 0; i < array.elements().size(); i++) {
            if (i > 0) {
                parts.add(", ");
            }
            parts.add(array.elements().get(i));
        }
        parts.add("]]");
        return parts;
    }

    private static void pushAll(Deque<Object> work, List<Object> parts) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            work.push(parts.get(i));
        }
    }
}
