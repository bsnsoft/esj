#!/usr/bin/env python3
"""Generate the typed read view of one edition from its term registry.

The registry is the one place that knows which business terms exist, what they are
called and how often they may occur, so the view over them is generated from it rather
than written by hand: a term the registry gains is a property this view gains on the
next run, and one it loses cannot linger here.

    python3 bindings/csharp/tools/generate-typed-view.py 2017 2026

The slug of a term is the name stem the registry carries (SPEC.md section 10). It is
language-neutral, so this generator applies the convention of its target: PascalCase for
a property, and the singular of a repeatable group's plural slug for the name of the
class that views one of its instances. The generated file is checked in beside the
handwritten code and a test holds it to the registry, so a stale file fails the build
rather than the next caller.
"""

import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPOSITORY = HERE.parent.parent.parent
TARGET = HERE.parent / "En16931.SemanticJson" / "Typed"

RESERVED = {
    "Document", "Instance", "Index", "Read", "ReadAll", "GroupPresent", "GroupList", "Step",
    "Equals", "GetHashCode", "GetType", "ToString", "MemberwiseClone", "Finalize",
}


def pascal(slug):
    """The property name of a slug: its first letter in upper case."""
    return slug[0].upper() + slug[1:]


def singular(slug):
    """The instance name of a repeatable group's plural slug (SPEC.md section 10)."""
    if slug.endswith("ies"):
        return slug[:-3] + "y"
    if slug.endswith("s"):
        return slug[:-1]
    raise SystemExit("the slug %s of a repeatable group is not a plural" % slug)


def stem_of(term):
    """The stem of a business group: the singular of a repeatable group's plural slug."""
    slug = term["slug"]
    return singular(slug) if term["max"] == "n" else slug


def class_names(terms):
    """The name of the class that views one instance of each business group.

    A slug names a concept and the same concept stands under more than one group, so two
    groups may carry one stem (SPEC.md section 10, "one concept, one stem"). A class name
    has to be unique all the same, so a second group of a stem is named after its parent
    as well, and a third after its identifier.
    """
    byId = {}
    taken = {"Invoice", "TypedView", "PathIndex"}
    for term in terms:
        if term["kind"] != "BG":
            continue
        candidate = pascal(stem_of(term))
        if candidate in taken and term["parent"]:
            parent = next(one for one in terms if one["id"] == term["parent"])
            candidate = pascal(stem_of(parent)) + candidate
        if candidate in taken:
            candidate = pascal(stem_of(term)) + term["id"].replace("-", "").capitalize()
        if candidate in taken:
            raise SystemExit("two groups are both called %s" % candidate)
        taken.add(candidate)
        byId[term["id"]] = candidate
    return byId


def children(terms, parent):
    return [term for term in terms if term["parent"] == parent]


def property_name(term, used, where, enclosing):
    name = pascal(term["slug"])
    if name in RESERVED or name == enclosing:
        name = name + "Value"
    if name in used:
        raise SystemExit("two children of %s are both called %s" % (where, name))
    used.add(name)
    return name


def summary(term):
    """One line of documentation: what the term is, as the registry names it."""
    return "%s (%s), %s" % (term["name"], term["id"], cardinality(term))


def cardinality(term):
    return "%s..%s" % (term["min"], term["max"])


def accessors(terms, term, out, covered, names, enclosing):
    """Writes the properties of one business group instance, or of the document root."""
    parent = None if term is None else term["id"]
    where = "the root" if term is None else parent
    used = set()
    for child in children(terms, parent):
        covered.append(child["id"])
        name = property_name(child, used, where, enclosing)
        doc = summary(child)
        if child["kind"] == "BT":
            if child["max"] == "n":
                out.append("    /// <summary>%s.</summary>" % doc)
                out.append("    public IReadOnlyList<SemanticValue> %s => ReadAll(\"%s\");"
                           % (name, child["id"]))
            else:
                out.append("    /// <summary>%s.</summary>" % doc)
                out.append("    public SemanticValue? %s => Read(\"%s\");" % (name, child["id"]))
        else:
            view = names[child["id"]]
            if child["max"] == "n":
                out.append("    /// <summary>%s.</summary>" % doc)
                out.append("    public IReadOnlyList<%s> %s => GroupList(\"%s\","
                           % (view, name, child["id"]))
                out.append("        (document, index, path) => new %s(document, index, path));" % view)
            else:
                out.append("    /// <summary>Tells whether the document carries %s.</summary>"
                           % child["name"])
                out.append("    public bool Has%s => GroupPresent(\"%s\");" % (name, child["id"]))
                out.append("")
                out.append("    /// <summary>%s.</summary>" % doc)
                out.append("    public %s %s => new(Document, Index, Step(\"%s\"));"
                           % (view, name, child["id"]))
        out.append("")


def generate(edition, model):
    terms = model["terms"]
    namespace = "En16931.SemanticJson.Typed.V" + edition
    out = [
        "// Generated from model/en16931/%s.json by bindings/csharp/tools/generate-typed-view.py." % edition,
        "// The registry is the source of the terms; this file is checked in and a test holds it to it.",
        "using System;",
        "using System.Collections.Generic;",
        "",
        "namespace %s;" % namespace,
        "",
        "/// <summary>",
        "/// The invoice of %s, read through the names the registry gives its terms." % model["edition"],
        "/// </summary>",
        "/// <remarks>",
        "/// Every property answers from the document it was handed and none of them writes. A term",
        "/// the document does not carry reads as <c>null</c>, and a repeatable one as an empty list;",
        "/// the content of a value is held to its semantic data type only where a caller asks for it,",
        "/// because deciding that is layer L2 and belongs to the validator.",
        "/// </remarks>",
        "public sealed class Invoice : TypedView",
        "{",
        "    /// <summary>Reads a document.</summary>",
        "    /// <param name=\"document\">the document</param>",
        "    public Invoice(SemanticDocument document)",
        "        : this(document, new PathIndex(document), SemanticPath.Root())",
        "    {",
        "    }",
        "",
        "    internal Invoice(SemanticDocument document, PathIndex index, SemanticPath path)",
        "        : base(document, index, path)",
        "    {",
        "    }",
        "",
    ]
    covered = []
    names = class_names(terms)
    accessors(terms, None, out, covered, names, "Invoice")
    out.append("    /// <summary>The identifiers of the terms this view carries an accessor for.</summary>")
    out.append("    public static IReadOnlyList<string> Covered { get; } = new[]")
    out.append("    {")
    placeholder = len(out)
    out.append("")
    out.append("    };")
    out.append("}")
    out.append("")

    for term in terms:
        if term["kind"] != "BG":
            continue
        view = names[term["id"]]
        out.append("/// <summary>%s (%s), as one instance of the group.</summary>" % (term["name"], term["id"]))
        out.append("public sealed class %s : TypedView" % view)
        out.append("{")
        out.append("    internal %s(SemanticDocument document, PathIndex index, SemanticPath path)"
                   % view)
        out.append("        : base(document, index, path)")
        out.append("    {")
        out.append("    }")
        out.append("")
        accessors(terms, term, out, covered, names, view)
        out = out[:-1]
        out.append("}")
        out.append("")

    lines = ",\n".join("        \"%s\"" % identifier for identifier in covered)
    out[placeholder] = lines + ","
    return "\n".join(out).rstrip() + "\n"


def main():
    editions = sys.argv[1:] or ["2017"]
    for edition in editions:
        path = REPOSITORY / "model" / "en16931" / ("%s.json" % edition)
        with path.open(encoding="utf-8") as handle:
            model = json.load(handle)
        target = TARGET / ("Invoice%s.cs" % edition)
        target.write_text(generate(edition, model), encoding="utf-8")
        print("%s: %d terms" % (target.name, len(model["terms"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
