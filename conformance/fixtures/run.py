#!/usr/bin/env python3
"""Run the fixture manifest against an implementation of ESJ.

The manifest beside this script is a list of cases written in no programming language:
documents with the digests they have to produce, documents that have to be rejected and
the finding code for each, the value grammars as accept and reject tables, and every
mutation of the conformance corpus in the form of a base document and the changes that
break it. A second implementation is conformant when it answers all of them.

    python3 conformance/fixtures/run.py                 # what the manifest contains
    python3 conformance/fixtures/run.py --binding ./my-binding --verbose

Everything after --binding is the command and belongs to it, `--` and options of its own
included; the runner's own options come before it.

Without --binding the script reads the manifest, follows its parts and checks that every
file it names is in the repository. That is the contract as data; nothing is executed.

With --binding it starts the command once and speaks one JSON object per line to its
standard input, reading one JSON object per line back. Six requests exist:

    {"op": "editions"}
        -> {"semanticModels": ["EN16931-1:..."]}   which editions the binding carries

    {"op": "digest", "file": "examples/minimal.esj.json"}
        -> {"semanticDigest": "...", "documentDigest": "...",
            "canonicalBytes": 1234, "values": 24}

    {"op": "canonicalize", "file": "..."}
        -> {"canonical": "<the canonical JSON text>"}

    {"op": "validate", "file": "..."}            a document read from the repository
    {"op": "validate", "document": { ... }}      a document passed inline
        -> {"findings": [{"path": "/BT-2", "code": "ESJ-L2-DATE"}]}

    A finding carries the path SPEC.md section 9.5 gives its code: the member's path where
    the finding is about one member of `values`, and the empty string where the member name
    is no path at all. Both the path and the code are compared. A document rejected at layer
    L1 is expected to draw findings of that layer alone, because the model layers are not
    evaluated over a document the reader refused a member of (section 9.5).

    {"op": "rules", "document": { ... }}
        -> {"rules": ["BR-CO-10"], "warnings": []}

    A rules request is answered with the pack the binding carries, unless it names another:

    {"op": "rules", "pack": "conformance/fixtures/arithmetic/pack.json",
     "file": "examples/minimal.esj.json"}
        -> {"rules": ["DIV-EXACT-35", ...], "warnings": []}

    The pack is a file of the rule language, relative to the repository root, whose rules
    are all written in the language: none written in code, and no code list snapshot. The
    binding compiles it against the registry of the document's edition and answers what
    that pack reports.

A validate answers with every finding of layers L1 to L3, errors and information alike,
whether the reader refused the document or a validator reported on it; the order does not
matter, and a finding that is not an error is ignored here. A document of an edition the
binding does not carry answers ESJ-L2-EDITION-UNKNOWN and nothing else, and its digests
are still expected to be right: reading, the canonical form and the digests need no
registry.

Paths in the manifest are relative to the repository root, which is two directories above
this script unless --repository says otherwise. The exit code is 0 when every case passed.
"""

import argparse
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
MANIFEST = HERE / "manifest.json"


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def manifests():
    """The manifest and every part of it this distribution carries, in order."""
    root = load(MANIFEST)
    files = [root]
    for name in root.get("parts", []):
        part = HERE / name
        if part.exists():
            files.append(load(part))
    return files


def documents(files, repository):
    """The documents of every manifest, each with the manifest it came from."""
    for manifest in files:
        for document in manifest.get("documents", []):
            yield manifest, document


def missing(files, repository):
    """Every file a manifest names and the repository does not have."""
    names = []
    for manifest in files:
        for registry in manifest.get("registries", []):
            names.append(registry["file"])
        for document in manifest.get("documents", []):
            names.append(document["file"])
            names.extend(document.get("registries", []))
            if "canonical" in document:
                names.append(document["canonical"])
        for document in manifest.get("invalid", []):
            names.append(document["file"])
        for case in manifest.get("canonicalOrder", []):
            names.extend([case["scrambled"], case["canonical"]])
        for grammar in manifest.get("grammars", []):
            names.append(grammar["base"])
        rules = manifest.get("rules")
        if rules:
            names.append("conformance/fixtures/" + rules["casesFile"])
            names.append(rules["directory"])
        arithmetic = manifest.get("arithmetic")
        if arithmetic:
            names.extend([arithmetic["pack"], arithmetic["base"]])
    return [name for name in names if not (repository / name).exists()]


def invalid(manifest):
    """The negative fixtures of one manifest, as the rows of each file, in order.

    A file is one case and its rows are one answer: a document may be wrong in two ways at
    layer L1 and draw two codes, and SPEC.md section 9.6 fixes how far a reader reads, so the
    rows of such a file are the whole answer and are compared as a list. A fixture caught by a
    model layer carries the first row alone, because what a validator reports over a whole
    document is the business of the `documents` part.
    """
    rows = {}
    for document in manifest.get("invalid", []):
        rows.setdefault(document["file"], []).append(document)
    return rows


def cases(files, repository):
    """The rule cases, read from the file the manifest points at."""
    for manifest in files:
        rules = manifest.get("rules")
        if rules:
            return load(HERE / rules["casesFile"])["cases"]
    return []


def apply(base, changes):
    """Builds the document of a rule case: the base document with its values changed."""
    document = json.loads(json.dumps(base))
    values = document["values"]
    for change in changes:
        if change.get("remove"):
            values.pop(change["path"], None)
        else:
            values[change["path"]] = change["value"]
    return document


class Binding:
    """The command under test, talked to over one pipe for the whole run."""

    def __init__(self, command):
        self.process = subprocess.Popen(
            command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            text=True, encoding="utf-8")

    def ask(self, request):
        self.process.stdin.write(json.dumps(request) + "\n")
        self.process.stdin.flush()
        answer = self.process.stdout.readline()
        if not answer:
            raise SystemExit("the binding closed its output on " + json.dumps(request))
        return json.loads(answer)

    def close(self):
        self.process.stdin.close()
        self.process.wait()


class Report:
    """What passed and what did not."""

    def __init__(self, verbose):
        self.verbose = verbose
        self.passed = 0
        self.failures = []

    def check(self, name, expected, actual):
        if expected == actual:
            self.passed += 1
            if self.verbose:
                print("ok    " + name)
        else:
            self.failures.append((name, expected, actual))
            print("FAIL  " + name)
            print("      expected " + json.dumps(expected))
            print("      answered " + json.dumps(actual))


def errors(answer):
    """The error findings of an answer, as pairs, in no particular order."""
    return sorted((finding["path"], finding["code"])
                  for finding in answer.get("findings", [])
                  if finding["code"].startswith("ESJ-L")
                  and not finding["code"].endswith("NOT-CHECKED")
                  and not finding["code"].endswith("EDITION-UNKNOWN"))


def outcome(rule, reported):
    """Whether a rule is among the identifiers a pack reported, in words."""
    return "reported" if rule in reported else "silent"


def arithmetic(binding, files, repository, report, carried):
    """The arithmetic of the rule language, one check per rule of the pack that pins it.

    Every rule is checked on its own, so that a failure names the quotient that is wrong:
    the note of that rule in the pack says what it pins. An answer that is no report at all,
    such as an error, fails every rule of the pack.
    """
    for manifest in files:
        section = manifest.get("arithmetic")
        if not section:
            continue
        if load(repository / section["base"])["semanticModel"] not in carried:
            continue
        answer = binding.ask({"op": "rules", "pack": section["pack"], "file": section["base"]})
        reported = answer.get("rules")
        for rule in load(repository / section["pack"])["rules"]:
            report.check(rule["id"] + " of " + section["pack"],
                         outcome(rule["id"], section["expect"]["rules"]),
                         outcome(rule["id"], reported) if reported is not None else answer)
        report.check(section["pack"] + " warnings", section["expect"]["warnings"],
                     answer.get("warnings"))


def run(binding, files, repository, report):
    carried = set(binding.ask({"op": "editions"})["semanticModels"])
    for manifest, document in documents(files, repository):
        name = document["file"]
        answer = binding.ask({"op": "digest", "file": name})
        report.check(name + " digests", {
            "semanticDigest": document["semanticDigest"],
            "documentDigest": document["documentDigest"],
            "canonicalBytes": document["canonicalBytes"],
            "values": document["values"],
        }, {key: answer.get(key) for key in
            ("semanticDigest", "documentDigest", "canonicalBytes", "values")})
        if "canonical" in document:
            expected = (repository / document["canonical"]).read_text(encoding="utf-8")
            report.check(name + " canonical form", expected,
                         binding.ask({"op": "canonicalize", "file": name})["canonical"])
        if document["semanticModel"] in carried:
            expected = sorted((finding["path"], finding["code"])
                              for finding in document.get("findings", []))
            report.check(name + " findings", expected,
                         errors(binding.ask({"op": "validate", "file": name})))

    for manifest in files:
        for name, rows in invalid(manifest).items():
            answer = binding.ask({"op": "validate", "file": name})
            found = errors(answer)
            if rows[0]["layer"] == "business-rule":
                report.check(name + " is structurally sound", [], [code for _, code in found])
                continue
            if rows[0]["layer"] in ("L1", "limit"):
                # A document layer L1 refused is answered whole: section 9.6 fixes how far a
                # reader reads, so the rows are the list and not a sample of it.
                report.check(name + " is rejected at layer L1, with these findings alone",
                             sorted((row.get("path", ""), row["code"]) for row in rows), found)
                continue
            report.check(name + " is rejected", True,
                         (rows[0].get("path", ""), rows[0]["code"]) in found)

        for case in manifest.get("canonicalOrder", []):
            expected = (repository / case["canonical"]).read_text(encoding="utf-8")
            report.check(case["scrambled"] + " canonical form", expected,
                         binding.ask({"op": "canonicalize",
                                      "file": case["scrambled"]})["canonical"])

        for grammar in manifest.get("grammars", []):
            base = load(repository / grammar["base"])
            if base["semanticModel"] not in carried:
                continue
            for candidate in grammar["accept"]:
                document = apply(base, [{"path": grammar["path"], "value": candidate}])
                answer = binding.ask({"op": "validate", "document": document})
                report.check(grammar["datatype"] + " accepts " + json.dumps(candidate),
                             [], [code for path, code in errors(answer)
                                  if path == grammar["path"]])
            for candidate in grammar["reject"]:
                document = apply(base, [{"path": grammar["path"], "value": candidate}])
                answer = binding.ask({"op": "validate", "document": document})
                report.check(grammar["datatype"] + " rejects " + json.dumps(candidate),
                             [grammar["code"]], [code for path, code in errors(answer)
                                                 if path == grammar["path"]])

    bases = {}
    for case in cases(files, repository):
        base = bases.setdefault(case["base"], load(repository / case["base"]))
        if base["semanticModel"] not in carried:
            continue
        answer = binding.ask({"op": "rules",
                              "document": apply(base, case["changes"])})
        report.check(case["id"], case["expect"],
                     {"rules": answer.get("rules", []),
                      "warnings": answer.get("warnings", [])})

    arithmetic(binding, files, repository, report, carried)


def split(argv):
    """The runner's own arguments and the binding command, split at --binding.

    Everything after --binding belongs to the command, so it is taken out before argparse
    reads the rest. argparse would otherwise drop the first -- of the line, which is how a
    command such as `dotnet run --project X -- .` passes its own arguments on.
    """
    if "--binding" not in argv:
        return argv, None
    at = argv.index("--binding")
    return argv[:at], argv[at + 1:]


def main():
    mine, command = split(sys.argv[1:])
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repository", default=str(HERE.parent.parent),
                        help="the root of the checkout; paths are relative to it")
    parser.add_argument("--binding", nargs=argparse.REMAINDER,
                        help="the command to run, and its arguments; everything after it,"
                             " -- included, belongs to the command")
    parser.add_argument("--verbose", action="store_true", help="name every case that passed")
    arguments = parser.parse_args(mine)
    arguments.binding = command
    repository = pathlib.Path(arguments.repository).resolve()

    files = manifests()
    absent = missing(files, repository)
    if absent:
        for name in absent:
            print("missing  " + name)
        return 1

    if not arguments.binding:
        for manifest in files:
            section = manifest.get("arithmetic")
            print("%s: %d documents, %d rejected, %d grammars, %d canonical order, %d rule cases,"
                  " %d arithmetic rules"
                  % (manifest["part"],
                     len(manifest.get("documents", [])),
                     len(manifest.get("invalid", [])),
                     len(manifest.get("grammars", [])),
                     len(manifest.get("canonicalOrder", [])),
                     manifest.get("rules", {}).get("cases", 0),
                     len(load(repository / section["pack"])["rules"]) if section else 0))
        print("every file the manifest names is in " + str(repository))
        return 0

    report = Report(arguments.verbose)
    binding = Binding(arguments.binding)
    try:
        run(binding, files, repository, report)
    finally:
        binding.close()
    print("%d passed, %d failed" % (report.passed, len(report.failures)))
    return 1 if report.failures else 0


if __name__ == "__main__":
    sys.exit(main())
