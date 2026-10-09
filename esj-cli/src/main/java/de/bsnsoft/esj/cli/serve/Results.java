package de.bsnsoft.esj.cli.serve;

import de.bsnsoft.esj.Esj;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a child wrote into the result of a tool.
 *
 * <p>Nothing here decides anything about an invoice. A verdict is the child's
 * {@code verdict}, a finding is the child's finding with its code, severity, path and
 * message, a value is the value of the document the child wrote; this class chooses which
 * of them a model sees first, cuts long lists and long texts and says how much it cut.
 *
 * <p>What a child wrote is never read whole. A report or a document is read from the file
 * the child wrote it to, token by token ({@link JsonCursor}), and only what a result
 * carries is held: about {@link #KEPT} characters of it in all — the values of a
 * {@code get}, the notes of a conversion; the {@link #FINDINGS} heaviest findings of a
 * validation, each of a bounded size, and 16 Ki characters of the rest of its report — a
 * value of at most {@link #VALUE} characters, a page of at most {@link #PAGE} bytes. A value
 * past those is named with its length and not carried, a list past them is cut and says so;
 * the child's own bounds, which {@code --limits large} raises to millions of values, never
 * reach the heap of the server ({@link Capacity}).
 */
final class Results {

    /** The most findings a validation result lists. */
    static final int FINDINGS = 20;

    /** The most findings the text of a validation result names. */
    static final int FINDINGS_IN_TEXT = 5;

    /** The longest message of a finding a result carries, in characters. */
    static final int MESSAGE = 400;

    /** The most paths of one finding a result carries. */
    static final int PATHS = 16;

    /** The longest code, severity, path or subject of a finding, in characters. */
    static final int FIELD = 256;

    /** The longest other text of a report a result carries: a reason, a note's member. */
    static final int TEXT = 4096;

    /** The most notes of a conversion a result lists. */
    static final int NOTES = 20;

    /** The most values a {@code get} returns. */
    static final int VALUES = 500;

    /** The largest document or invoice a result carries inline, in bytes. */
    static final int INLINE = 64 * 1024;

    /** The most characters of a child's answer one result keeps, every string together. */
    static final int KEPT = 128 * 1024;

    /** The longest value of a document a result carries, in characters. */
    static final int VALUE = 32 * 1024;

    /** The longest page of {@code inspect} or {@code extract --list} a result carries, in bytes. */
    static final int PAGE = 64 * 1024;

    /** The order of the severities, the heaviest first. */
    private static final List<String> SEVERITIES =
            List.of("fatal", "error", "warning", "information", "info");

    /** The most distinct severities that are counted apart. */
    private static final int SEVERITY_NAMES = 16;

    /** The checks of a validation report, with the member each one's findings are in. */
    private static final Map<List<String>, String> CHECKS = checks();

    /** The members of a validation report a result carries as they are. */
    private static final Set<String> REPORT = Set.of("verdict", "detected", "semanticModel",
            "reasons", "notChecked");

    /** The members of the container block a result carries. */
    private static final List<String> CONTAINER = List.of("ok", "attachment", "kind", "profile");

    /** The members of a conversion report a result carries as they are. */
    private static final List<String> CONVERSION = List.of("detected", "to", "wrote",
            "complete", "values", "dropped");

    /** The values a summary names, by path. */
    private static final List<String> SUMMARY = List.of("/BT-1", "/BT-2", "/BT-3", "/BT-5",
            "/BT-9", "/BT-10", "/BG-2/BT-24", "/BG-4/BT-27", "/BG-7/BT-44", "/BG-22/BT-106",
            "/BG-22/BT-109", "/BG-22/BT-110", "/BG-22/BT-112", "/BG-22/BT-113",
            "/BG-22/BT-115");

    private static final Pattern GROUP_LINE = Pattern.compile("^/BG-25/([0-9]+)(/|$)");

    private Results() {
    }

    private static Map<List<String>, String> checks() {
        Map<List<String>, String> checks = new LinkedHashMap<>();
        // The checks that name business terms come first, so that of two findings of one
        // severity the one with a path is the one a model reads first.
        checks.put(List.of("container"), "container");
        checks.put(List.of("xml"), "xml");
        checks.put(List.of("layers", "l1"), "l1");
        checks.put(List.of("layers", "l2"), "l2");
        checks.put(List.of("layers", "l3"), "l3");
        checks.put(List.of("rules"), "rules");
        checks.put(List.of("syntax"), "syntax");
        checks.put(List.of("written", "syntax"), "written-syntax");
        return checks;
    }

    /**
     * What may still be kept of one answer, and whether anything was left out.
     */
    static final class Budget {
        private long characters;
        private int values;
        private boolean cut;

        Budget(long characters, int values) {
            this.characters = characters;
            this.values = values;
        }

        /** Tells whether something was left out for want of room. */
        boolean cut() {
            return cut;
        }

        private boolean spent() {
            return characters <= 0 || values <= 0;
        }
    }

    /**
     * Reads the value the cursor stands on, within a budget: a string past {@code longest}
     * characters or past the room left is cut and says how much it left out, and a container
     * whose room runs out ends where it ran out, the rest of it read past.
     *
     * @param cursor  the cursor, on the first token of the value
     * @param budget  what may still be kept
     * @param longest the longest string kept whole
     * @return the value
     */
    static Jv value(JsonCursor cursor, Budget budget, int longest) throws IOException {
        budget.values--;
        switch (cursor.current()) {
            case STRING -> {
                JsonCursor.Text text = cursor.text((int) Math.max(0,
                        Math.min(longest, budget.characters)));
                budget.characters -= text.prefix().length();
                if (!text.complete()) {
                    budget.cut = true;
                }
                return Jv.of(text.cut());
            }
            case NUMBER -> {
                return new Jv.Num(cursor.number());
            }
            case TRUE -> {
                return Jv.of(true);
            }
            case FALSE -> {
                return Jv.of(false);
            }
            case NULL -> {
                return Jv.NULL;
            }
            case BEGIN_ARRAY -> {
                List<Jv> items = new ArrayList<>();
                while (cursor.next() != JsonCursor.Token.END_ARRAY) {
                    if (budget.spent()) {
                        budget.cut = true;
                        cursor.skip();
                    } else {
                        items.add(value(cursor, budget, longest));
                    }
                }
                return new Jv.Arr(items);
            }
            case BEGIN_OBJECT -> {
                Map<String, Jv> members = new LinkedHashMap<>();
                while (cursor.next() != JsonCursor.Token.END_OBJECT) {
                    String name = cursor.name();
                    cursor.next();
                    if (budget.spent()) {
                        budget.cut = true;
                        cursor.skip();
                    } else {
                        budget.characters -= name.length();
                        members.put(name, value(cursor, budget, longest));
                    }
                }
                return new Jv.Obj(members);
            }
            default -> throw new JsonCursor.Malformed("unexpected " + cursor.current());
        }
    }

    /** Opens a child's JSON answer and moves to its first token, which is an object. */
    private static JsonCursor object(InputStream in) throws IOException {
        JsonCursor cursor = new JsonCursor(in);
        if (cursor.next() != JsonCursor.Token.BEGIN_OBJECT) {
            throw new JsonCursor.Malformed("the answer is not a JSON object");
        }
        return cursor;
    }

    /** Reads past the end of a child's JSON answer, which has nothing after its object. */
    private static void end(JsonCursor cursor) throws IOException {
        if (cursor.next() != JsonCursor.Token.END) {
            throw new JsonCursor.Malformed("the answer goes on after its object");
        }
    }

    // ---------------------------------------------------------------------------------
    // validate

    /** One finding as a result carries it, with where it ranks. */
    private record Finding(int weight, int check, long sequence, Jv.Obj condensed) {
    }

    /** What a validation report comes to, read once. */
    private static final class Validation {
        private final Map<String, Jv> report = new LinkedHashMap<>();
        private final Map<String, Jv> container = new LinkedHashMap<>();
        private boolean hasContainer;
        private final List<Finding> heaviest = new ArrayList<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private long total;
        private long sequence;
        /** The room of the members of the report beside its findings, which are short. */
        private final Budget budget = new Budget(16 * 1024, 2_000);
        private boolean pathsCut;

        private void count(String severity) {
            String key = counts.containsKey(severity) || counts.size() < SEVERITY_NAMES
                    ? severity : "other";
            counts.merge(key, 1, Integer::sum);
        }

        /** Keeps a finding where it is among the {@link #FINDINGS} heaviest so far. */
        private void offer(Finding finding) {
            Comparator<Finding> order = Comparator.comparingInt(Finding::weight)
                    .thenComparingInt(Finding::check).thenComparingLong(Finding::sequence);
            int at = heaviest.size();
            while (at > 0 && order.compare(heaviest.get(at - 1), finding) > 0) {
                at--;
            }
            if (at < FINDINGS) {
                heaviest.add(at, finding);
                if (heaviest.size() > FINDINGS) {
                    heaviest.remove(heaviest.size() - 1);
                }
            }
        }
    }

    /**
     * The result of {@code validate}: the verdict and the heaviest findings.
     *
     * @param report the report the child wrote with {@code --output json}
     * @param exit   its exit code
     * @return the text and the structured result
     * @throws IOException where the report cannot be read or is not JSON
     */
    static Outcome validate(InputStream report, int exit) throws IOException {
        Validation validation = new Validation();
        JsonCursor cursor = object(report);
        walk(cursor, List.of(), validation);
        end(cursor);
        String verdict = validation.report.get("verdict") instanceof Jv.Str str ? str.value()
                : "UNKNOWN";
        List<Jv> shown = new ArrayList<>();
        validation.heaviest.forEach(finding -> shown.add(finding.condensed()));
        Jv.Builder countObject = Jv.object();
        for (String severity : SEVERITIES) {
            if (validation.counts.containsKey(severity)) {
                countObject.put(severity, validation.counts.get(severity));
            }
        }
        validation.counts.forEach((severity, count) -> {
            if (!SEVERITIES.contains(severity)) {
                countObject.put(severity, count);
            }
        });
        Jv.Builder result = Jv.object()
                .put("verdict", verdict)
                .put("exitCode", exit)
                .put("detected", validation.report.getOrDefault("detected", Jv.NULL))
                .put("semanticModel", validation.report.getOrDefault("semanticModel", Jv.NULL))
                .put("container", validation.hasContainer ? container(validation) : Jv.NULL)
                .put("reasons", validation.report.getOrDefault("reasons", Jv.array(List.of())))
                .put("notChecked", validation.report.getOrDefault("notChecked",
                        Jv.array(List.of())))
                .put("findingCounts", countObject.build())
                .put("findingsTotal", validation.total)
                .put("findings", Jv.array(shown));
        boolean complete = validation.total <= FINDINGS && !validation.budget.cut()
                && !validation.pathsCut;
        if (!complete) {
            result.put("complete", false);
        }
        return new Outcome(Outcome.Status.OK, exit, validationText(verdict, validation),
                result.build(), Optional.empty(), Optional.empty());
    }

    /** Walks the objects of a validation report towards its findings. */
    private static void walk(JsonCursor cursor, List<String> path, Validation validation)
            throws IOException {
        String check = CHECKS.get(path);
        while (cursor.next() != JsonCursor.Token.END_OBJECT) {
            String name = cursor.name();
            JsonCursor.Token token = cursor.next();
            List<String> below = new ArrayList<>(path);
            below.add(name);
            if (path.isEmpty() && REPORT.contains(name)) {
                validation.report.put(name, value(cursor, validation.budget,
                        "verdict".equals(name) ? 64 : TEXT));
            } else if (path.equals(List.of("container")) && CONTAINER.contains(name)) {
                validation.container.put(name, value(cursor, validation.budget, TEXT));
            } else if (check != null && "findings".equals(name)
                    && token == JsonCursor.Token.BEGIN_ARRAY) {
                findings(cursor, check, validation);
            } else if (token == JsonCursor.Token.BEGIN_OBJECT && leadsToAFinding(below)) {
                if (below.equals(List.of("container"))) {
                    validation.hasContainer = true;
                }
                walk(cursor, below, validation);
            } else {
                cursor.skip();
            }
        }
    }

    private static boolean leadsToAFinding(List<String> path) {
        for (List<String> check : CHECKS.keySet()) {
            if (check.size() >= path.size() && check.subList(0, path.size()).equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static void findings(JsonCursor cursor, String check, Validation validation)
            throws IOException {
        int checkIndex = new ArrayList<>(CHECKS.values()).indexOf(check);
        while (cursor.next() != JsonCursor.Token.END_ARRAY) {
            if (cursor.current() != JsonCursor.Token.BEGIN_OBJECT) {
                cursor.skip();
                continue;
            }
            Jv.Obj condensed = finding(cursor, check, validation);
            validation.total++;
            String severity = condensed.string("severity").orElse("unknown");
            validation.count(severity);
            int weight = SEVERITIES.indexOf(severity);
            validation.offer(new Finding(weight < 0 ? SEVERITIES.size() : weight, checkIndex,
                    validation.sequence++, condensed));
        }
    }

    /** Reads one finding into the form a result carries, whatever else it has. */
    private static Jv.Obj finding(JsonCursor cursor, String check, Validation validation)
            throws IOException {
        Jv code = Jv.NULL;
        Jv severity = Jv.NULL;
        String path = null;
        List<Jv> paths = new ArrayList<>();
        int pathsTotal = 0;
        String subject = null;
        String location = null;
        String message = "";
        // A finding is read within a budget of its own, so that it costs the same whether
        // or not it ends up among the ones kept.
        Budget own = new Budget(Long.MAX_VALUE, 1_000);
        while (cursor.next() != JsonCursor.Token.END_OBJECT) {
            String name = cursor.name();
            JsonCursor.Token token = cursor.next();
            switch (name) {
                case "code" -> code = value(cursor, own, FIELD);
                case "severity" -> severity = value(cursor, own, FIELD);
                case "path", "subject", "location", "message" -> {
                    if (token != JsonCursor.Token.STRING) {
                        cursor.skip();
                        continue;
                    }
                    JsonCursor.Text text = cursor.text("path".equals(name)
                            || "subject".equals(name) ? FIELD : MESSAGE);
                    switch (name) {
                        case "path" -> path = text.cut();
                        case "subject" -> subject = text.cut();
                        case "location" -> location = text.cut();
                        default -> message = text.cut();
                    }
                }
                case "paths" -> {
                    if (token != JsonCursor.Token.BEGIN_ARRAY) {
                        cursor.skip();
                        continue;
                    }
                    while (cursor.next() != JsonCursor.Token.END_ARRAY) {
                        pathsTotal++;
                        if (paths.size() < PATHS) {
                            paths.add(value(cursor, own, FIELD));
                        } else {
                            cursor.skip();
                        }
                    }
                }
                default -> cursor.skip();
            }
        }
        Jv.Builder condensed = Jv.object()
                .put("check", check)
                .put("code", code)
                .put("severity", severity);
        List<Jv> all = new ArrayList<>();
        if (path != null && !path.isEmpty()) {
            all.add(Jv.of(path));
        }
        all.addAll(paths);
        if (!all.isEmpty()) {
            condensed.put("paths", Jv.array(all));
        }
        if (pathsTotal > PATHS) {
            condensed.put("pathsTotal", pathsTotal + (path != null && !path.isEmpty() ? 1 : 0));
            validation.pathsCut = true;
        }
        if (subject != null && !subject.isEmpty()) {
            condensed.put("subject", subject);
        }
        if (location != null) {
            condensed.put("location", location);
        }
        condensed.put("message", message);
        return condensed.build();
    }

    private static Jv container(Validation validation) {
        Jv.Builder container = Jv.object();
        for (String name : CONTAINER) {
            container.put(name, validation.container.getOrDefault(name, Jv.NULL));
        }
        return container.build();
    }

    private static String validationText(String verdict, Validation validation) {
        StringBuilder text = new StringBuilder(verdict);
        switch (verdict) {
            case "VALID" -> text.append(": the complete check for this kind of input ran and"
                    + " found nothing fatal.");
            case "INVALID" -> text.append(": the check found ")
                    .append(counted(validation.counts.getOrDefault("fatal", 0)
                            + validation.counts.getOrDefault("error", 0), "fatal finding"))
                    .append('.');
            case "INDETERMINATE" -> {
                text.append(": nothing fatal was found, and part of the complete check did not"
                        + " run or did not complete");
                List<String> missing = new ArrayList<>();
                if (validation.report.get("reasons") instanceof Jv.Arr reasons) {
                    for (Jv reason : reasons.items()) {
                        if (reason instanceof Jv.Obj object) {
                            missing.add(object.string("component").orElse("?") + " ("
                                    + object.string("cause").orElse("?") + ")");
                        }
                    }
                }
                text.append(missing.isEmpty() ? "." : ": " + String.join(", ", missing) + ".");
            }
            default -> text.append('.');
        }
        if (validation.container.get("ok") instanceof Jv.Bool ok && !ok.value()) {
            text.append(" The PDF container has a fatal finding of its own.");
        }
        int shown = 0;
        for (Finding entry : validation.heaviest) {
            if (shown == FINDINGS_IN_TEXT) {
                break;
            }
            Jv.Obj finding = entry.condensed();
            text.append("\n- ").append(finding.string("code").orElse("?"))
                    .append(" (").append(finding.string("severity").orElse("?")).append(')');
            List<String> where = new ArrayList<>();
            if (finding.get("paths").orElse(null) instanceof Jv.Arr paths) {
                for (Jv path : paths.items()) {
                    if (path instanceof Jv.Str str && where.size() < 3) {
                        where.add(str.value());
                    }
                }
            }
            if (!where.isEmpty()) {
                text.append(" at ").append(String.join(", ", where));
            }
            text.append(": ").append(finding.string("message").orElse(""));
            shown++;
        }
        if (validation.total > shown) {
            text.append("\n(").append(validation.total - shown).append(" more findings")
                    .append(validation.total > FINDINGS
                            ? "; the structured result lists the " + FINDINGS + " heaviest,"
                                    + " a report (report=html or pdf) all of them)"
                            : " in the structured result and the report)");
        }
        return text.toString();
    }

    // ---------------------------------------------------------------------------------
    // summary and get

    /** A value of a document that is too long to carry. */
    private record Omitted(String path, long characters) {
        Jv json() {
            Jv.Builder object = Jv.object().put("path", path);
            if (characters >= 0) {
                object.put("characters", characters);
            }
            return object.build();
        }
    }

    /**
     * Reads the value of a document the cursor stands on, whole or not at all.
     *
     * @param cursor  the cursor, on the value
     * @param longest the longest value carried, in characters
     * @param inner   for an object, the member whose value is wanted; empty for the whole
     * @return the value, or empty where it is longer than {@code longest}, with its length
     */
    private static Optional<Jv> documentValue(JsonCursor cursor, int longest,
                                              Optional<String> inner, long[] length)
            throws IOException {
        JsonCursor.Token token = cursor.current();
        if (token == JsonCursor.Token.STRING) {
            JsonCursor.Text text = cursor.text(longest);
            length[0] = text.length();
            return text.complete() ? Optional.of(Jv.of(text.prefix())) : Optional.empty();
        }
        if (token == JsonCursor.Token.BEGIN_OBJECT && inner.isPresent()) {
            Optional<Jv> found = Optional.of(Jv.NULL);
            length[0] = 0;
            while (cursor.next() != JsonCursor.Token.END_OBJECT) {
                String name = cursor.name();
                cursor.next();
                if (name.equals(inner.get())) {
                    found = documentValue(cursor, longest, Optional.empty(), length);
                } else {
                    cursor.skip();
                }
            }
            return found;
        }
        Budget own = new Budget(longest, 256);
        Jv value = value(cursor, own, longest);
        length[0] = own.cut() ? -1 : longest - own.characters;
        return own.cut() ? Optional.empty() : Optional.of(value);
    }

    /**
     * Reads the {@code semanticModel} and the {@code values} of an ESJ document, handing every
     * value to a visitor that reads it or moves on.
     */
    private interface Visitor {
        void value(String path, JsonCursor cursor) throws IOException;
    }

    private static Jv document(InputStream in, Visitor visitor) throws IOException {
        JsonCursor cursor = object(in);
        Jv semanticModel = Jv.NULL;
        while (cursor.next() != JsonCursor.Token.END_OBJECT) {
            String name = cursor.name();
            JsonCursor.Token token = cursor.next();
            if ("semanticModel".equals(name)) {
                semanticModel = value(cursor, new Budget(FIELD, 4), FIELD);
            } else if ("values".equals(name) && token == JsonCursor.Token.BEGIN_OBJECT) {
                while (cursor.next() != JsonCursor.Token.END_OBJECT) {
                    String path = cursor.name();
                    cursor.next();
                    visitor.value(path, cursor);
                    if (cursor.current() == JsonCursor.Token.STRING
                            || cursor.current() == JsonCursor.Token.BEGIN_OBJECT
                            || cursor.current() == JsonCursor.Token.BEGIN_ARRAY) {
                        cursor.skip();
                    }
                }
            } else {
                cursor.skip();
            }
        }
        end(cursor);
        return semanticModel;
    }

    /**
     * The result of {@code summary}: what the document states, by business term.
     *
     * @param canonical the ESJ document the child wrote
     * @return the outcome
     * @throws IOException where the document cannot be read or is not JSON
     */
    static Outcome summary(InputStream canonical) throws IOException {
        Map<String, Jv> values = new LinkedHashMap<>();
        List<Omitted> omitted = new ArrayList<>();
        long[] count = new long[1];
        long[] lines = new long[1];
        String[] lastLine = new String[1];
        // One matcher for every value of a document that may have millions of them.
        Matcher matcher = GROUP_LINE.matcher("");
        Jv semanticModel = document(canonical, (path, cursor) -> {
            count[0]++;
            matcher.reset(path);
            // The canonical form orders the paths by their segments, the index of a line
            // numerically, so the paths of one line follow each other and a line is counted
            // where its index changes.
            if (matcher.find() && !matcher.group(1).equals(lastLine[0])) {
                lastLine[0] = matcher.group(1);
                lines[0]++;
            }
            if (SUMMARY.contains(path)) {
                long[] length = new long[1];
                Optional<Jv> value = documentValue(cursor, VALUE, Optional.of("value"), length);
                if (value.isPresent()) {
                    values.put(path, value.get());
                } else {
                    omitted.add(new Omitted(path, length[0]));
                }
            }
        });
        Jv.Obj totals = Jv.object()
                .put("lineNetTotal", values.get("/BG-22/BT-106"))
                .put("totalWithoutVat", values.get("/BG-22/BT-109"))
                .put("totalVat", values.get("/BG-22/BT-110"))
                .put("totalWithVat", values.get("/BG-22/BT-112"))
                .put("paidAmount", values.get("/BG-22/BT-113"))
                .put("amountDue", values.get("/BG-22/BT-115"))
                .build();
        Jv.Builder result = Jv.object()
                .put("semanticModel", semanticModel)
                .put("invoiceNumber", values.get("/BT-1"))
                .put("issueDate", values.get("/BT-2"))
                .put("typeCode", values.get("/BT-3"))
                .put("currency", values.get("/BT-5"))
                .put("dueDate", values.get("/BT-9"))
                .put("buyerReference", values.get("/BT-10"))
                .put("profile", values.get("/BG-2/BT-24"))
                .put("seller", values.get("/BG-4/BT-27"))
                .put("buyer", values.get("/BG-7/BT-44"))
                .put("lines", lines[0])
                .put("totals", totals)
                .put("values", count[0]);
        if (!omitted.isEmpty()) {
            result.put("omitted", omitted(omitted));
        }
        String text = "Invoice " + plain(values, "/BT-1") + " (BT-1) of " + plain(values, "/BT-2")
                + " (BT-2), type code " + plain(values, "/BT-3") + ", currency "
                + plain(values, "/BT-5") + "; seller " + plain(values, "/BG-4/BT-27")
                + ", buyer " + plain(values, "/BG-7/BT-44") + "; " + counted(lines[0], "line")
                + "; total with VAT " + plain(values, "/BG-22/BT-112") + " (BT-112), amount due "
                + plain(values, "/BG-22/BT-115") + " (BT-115). Read, not validated."
                + omittedText(omitted);
        return new Outcome(Outcome.Status.OK, 0, text, result.build(), Optional.empty(),
                Optional.empty());
    }

    /**
     * The result of {@code get}: the values at the paths that were asked for.
     *
     * @param canonical the ESJ document the child wrote
     * @param paths     the paths, each exact, a group, or with {@code *} for an index
     * @return the outcome
     * @throws IOException where the document cannot be read or is not JSON
     */
    static Outcome get(InputStream canonical, List<String> paths) throws IOException {
        // One matcher per path asked for, for every value of a document that may have
        // millions of them.
        List<Matcher> patterns = new ArrayList<>();
        paths.forEach(path -> patterns.add(pattern(path).matcher("")));
        boolean[] matched = new boolean[paths.size()];
        Jv.Builder found = Jv.object();
        List<String> foundPaths = new ArrayList<>();
        List<Omitted> omitted = new ArrayList<>();
        boolean[] cut = new boolean[1];
        long[] room = {KEPT};
        document(canonical, (path, cursor) -> {
            boolean any = false;
            for (int i = 0; i < patterns.size(); i++) {
                if (patterns.get(i).reset(path).matches()) {
                    matched[i] = true;
                    any = true;
                }
            }
            if (!any) {
                return;
            }
            if (foundPaths.size() >= VALUES || room[0] <= 0) {
                cut[0] = true;
                return;
            }
            long[] length = new long[1];
            Optional<Jv> value = documentValue(cursor, VALUE, Optional.empty(), length);
            if (value.isEmpty()) {
                omitted.add(new Omitted(path, length[0]));
            } else if (length[0] > room[0]) {
                cut[0] = true;
            } else {
                room[0] -= length[0] + path.length();
                found.put(path, value.get());
                foundPaths.add(path);
            }
        });
        List<Jv> missing = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            if (!matched[i]) {
                missing.add(Jv.of(paths.get(i)));
            }
        }
        Jv.Obj valuesFound = found.build();
        Jv.Builder result = Jv.object()
                .put("values", valuesFound)
                .put("missing", Jv.array(missing))
                .put("truncated", cut[0]);
        if (!omitted.isEmpty()) {
            result.put("omitted", omitted(omitted));
        }
        StringBuilder text = new StringBuilder();
        valuesFound.members().forEach((path, value) -> text.append(path).append(" = ")
                .append(value instanceof Jv.Str str ? str.value()
                        : new String(value.toBytes(), StandardCharsets.UTF_8))
                .append('\n'));
        for (Jv path : missing) {
            text.append(((Jv.Str) path).value()).append(": no value\n");
        }
        if (cut[0]) {
            text.append("(cut at ").append(Math.min(VALUES, foundPaths.size()))
                    .append(" values)\n");
        }
        text.append(omittedText(omitted).strip());
        return new Outcome(Outcome.Status.OK, 0, text.toString().stripTrailing(),
                result.build(), Optional.empty(), Optional.empty());
    }

    private static Jv omitted(List<Omitted> omitted) {
        List<Jv> items = new ArrayList<>();
        omitted.forEach(entry -> items.add(entry.json()));
        return Jv.array(items);
    }

    private static String omittedText(List<Omitted> omitted) {
        StringBuilder text = new StringBuilder();
        for (Omitted entry : omitted) {
            text.append("\n").append(entry.path()).append(": a value of ")
                    .append(entry.characters() >= 0 ? entry.characters() + " characters"
                            : "more than " + VALUE + " characters")
                    .append(", longer than a result carries; convert to=esj writes the whole"
                            + " document");
        }
        return text.toString();
    }

    /** Turns a path with {@code *} for an index into a pattern for itself and what is below. */
    static Pattern pattern(String path) {
        StringBuilder regex = new StringBuilder();
        for (String segment : path.substring(1).split("/", -1)) {
            regex.append('/').append("*".equals(segment) ? "[0-9]+" : Pattern.quote(segment));
        }
        return Pattern.compile(regex + "(/.*)?");
    }

    // ---------------------------------------------------------------------------------
    // convert, render, extract, inspect

    /**
     * The result of {@code convert}: what was written and what had no place.
     *
     * @param report the report the child wrote with {@code --output json}
     * @param exit   its exit code, 0 or 8
     * @param inline the converted document where it is small enough to carry, else empty
     * @return the outcome
     * @throws IOException where the report cannot be read or is not JSON
     */
    static Outcome convert(InputStream report, int exit, Optional<Jv> inline) throws IOException {
        Map<String, Jv> members = new LinkedHashMap<>();
        List<Jv> notes = new ArrayList<>();
        long total = 0;
        Budget budget = new Budget(KEPT, 20_000);
        JsonCursor cursor = object(report);
        while (cursor.next() != JsonCursor.Token.END_OBJECT) {
            String name = cursor.name();
            JsonCursor.Token token = cursor.next();
            if (CONVERSION.contains(name)) {
                members.put(name, value(cursor, budget, TEXT));
            } else if ("notPlaced".equals(name) && token == JsonCursor.Token.BEGIN_ARRAY) {
                while (cursor.next() != JsonCursor.Token.END_ARRAY) {
                    total++;
                    if (notes.size() < NOTES) {
                        notes.add(value(cursor, budget, VALUE));
                    } else {
                        cursor.skip();
                    }
                }
            } else {
                cursor.skip();
            }
        }
        end(cursor);
        String wrote = members.get("wrote") instanceof Jv.Str str ? str.value() : "?";
        String to = members.get("to") instanceof Jv.Str str ? str.value() : "";
        Jv.Builder result = Jv.object()
                .put("exitCode", exit)
                .put("detected", members.getOrDefault("detected", Jv.NULL))
                .put("to", members.getOrDefault("to", Jv.NULL))
                .put("wrote", wrote)
                .put("complete", members.getOrDefault("complete", Jv.NULL))
                .put("values", members.getOrDefault("values", Jv.NULL))
                .put("dropped", members.getOrDefault("dropped", Jv.NULL))
                .put("notPlacedTotal", total)
                .put("notPlaced", Jv.array(notes));
        inline.ifPresent(value -> result.put("esj".equals(to) ? "document" : "xml", value));
        String text = exit == 8
                ? "Nothing was written: " + counted(total, "part") + " of the document "
                        + (total == 1 ? "has" : "have") + " no place in the target syntax, and"
                        + " fail_on_loss refuses such a conversion."
                : "Wrote " + wrote + (total == 0 ? ", complete."
                        : "; " + counted(total, "value") + " had no place in the target syntax"
                                + " and were not written.");
        return new Outcome(Outcome.Status.OK, exit, text, result.build(), Optional.empty(),
                Optional.empty());
    }

    /**
     * The result of {@code render}.
     *
     * @param format the format that was written
     * @param embed  whether the invoice was written into the PDF
     * @return the outcome
     */
    static Outcome render(String format, boolean embed) {
        Jv.Obj result = Jv.object().put("format", format).put("embedded", embed).build();
        String text = "html".equals(format) ? "Rendered the invoice as one HTML page."
                : embed ? "Rendered the invoice as a PDF/A-3b file carrying it as Factur-X."
                : "Rendered the invoice as a PDF/A-3b file.";
        return new Outcome(Outcome.Status.OK, 0, text, result, Optional.empty(),
                Optional.empty());
    }

    /**
     * The result of {@code extract --list}: the attachments, one line each as the child
     * wrote them.
     *
     * @param stdout the listing
     * @return the outcome
     */
    static Outcome attachments(String stdout) {
        List<Jv> lines = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            if (line.startsWith("  ")) {
                lines.add(Jv.of(safe(line.strip())));
            }
        }
        Jv.Obj result = Jv.object().put("attachments", Jv.array(lines)).build();
        return new Outcome(Outcome.Status.OK, 0, safe(stdout.strip()), result, Optional.empty(),
                Optional.empty());
    }

    /**
     * The result of {@code extract}: the invoice the PDF carries.
     *
     * @param inline the invoice where it is small enough to carry
     * @return the outcome
     */
    static Outcome extracted(Optional<String> inline) {
        Jv.Builder result = Jv.object();
        inline.ifPresent(xml -> result.put("xml", xml));
        return new Outcome(Outcome.Status.OK, 0, "Took the electronic invoice out of the PDF,"
                + " unchanged.", result.build(), Optional.empty(), Optional.empty());
    }

    /**
     * The result of {@code inspect}: the page as the child wrote it.
     *
     * @param stdout the page
     * @param exit   its exit code
     * @return the outcome
     */
    static Outcome inspect(String stdout, int exit) {
        String page = safe(stdout.stripTrailing());
        Jv.Obj result = Jv.object().put("exitCode", exit).put("page", page).build();
        return new Outcome(Outcome.Status.OK, exit, page, result, Optional.empty(),
                Optional.empty());
    }

    /**
     * Reads the beginning of a page a child wrote: at most {@link #PAGE} bytes, and a line
     * that says how much more there was.
     *
     * @param file the page
     * @return its text
     * @throws IOException where it cannot be read
     */
    static String page(Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        long size = Files.size(file);
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(PAGE);
        }
        String text = decode(head);
        return size > head.length ? text + "\n… (" + (size - head.length) + " more bytes)"
                : text;
    }

    /** Decodes UTF-8 that may end in the middle of a character. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String plain(Map<String, Jv> values, String path) {
        Jv value = values.get(path);
        return value instanceof Jv.Str str ? str.value() : "(absent)";
    }

    /** Cuts a text at a length, saying how much it left out. */
    static String cut(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + " … (" + (text.length() - max) + " more characters)";
    }

    /** Replaces every character that steers a terminal but the line feed. */
    static String safe(String text) {
        StringBuilder safe = new StringBuilder(text.length());
        text.codePoints().forEach(c -> {
            if (c == '\n' || !Esj.steersATerminal(c)) {
                safe.appendCodePoint(c);
            } else {
                safe.append(String.format(java.util.Locale.ROOT, "\\u%04X", c));
            }
        });
        return safe.toString();
    }

    private static String counted(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
