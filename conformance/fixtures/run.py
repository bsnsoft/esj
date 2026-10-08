#!/usr/bin/env python3
"""Run the fixture manifest against an implementation of ESJ.

The manifest beside this script is a list of cases written in no programming language:
documents with the digests they have to produce and the whole answer a validator gives about
them, documents that have to be rejected and that answer, documents read under bounds other
than the defaults, registries a loader has to take or refuse, the value grammars and the two
grammars of the envelope as accept and reject tables, and every mutation of the conformance
corpus in the form of a base document and the changes that break it. A second implementation
is conformant when it answers all of them.

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
    {"op": "validate", "file": "...", "limits": {"maxStringBytes": 64}}
    {"op": "validate", "file": "...", "registries": ["conformance/fixtures/registries/x.json"]}
        -> {"status": "INVALID",
            "notEvaluated": [{"layer": "L2", "reason": "PRECEDING-LAYER-FAILED"}, ...],
            "findings": [{"path": "/BT-2", "code": "ESJ-L2-DATE", "subject": "",
                          "severity": "error"}, ...]}

    A validate request is answered with the whole result of layers L1 to L3 over the bytes:
    the status of SPEC.md section 9.5, every layer not evaluated with its reason, and every
    finding, errors and information alike, with its path, code, subject and severity. Neither
    path nor subject is ever absent; empty is the empty string. `limits` names bounds of
    section 12.2 under the names that section gives them, each replacing the default for
    that one request. `registries` names registry files relative to the repository root,
    the core registry first and every further one combined with it as an extension; the
    binding validates with those instead of the ones it carries.

    {"op": "registry", "files": ["model/en16931/2017.json", "model/b2c/0.1.json"]}
        -> {"accepted": true}   or   {"accepted": false, "error": "..."}

    A registry request names registry files relative to the repository root. The first is
    read as the core, and every further one is read and combined with it as an extension,
    with the checks section 10 holds a registry and a combination to. A file that cannot be
    read at all is an error of the request: {"error": "..."}.

    {"op": "rules", "document": { ... }}
        -> {"rules": ["BR-CO-10"], "warnings": []}

    A rules request is answered with the pack the binding carries for the edition the
    document names, unless it names another:

    {"op": "rules", "pack": "conformance/fixtures/arithmetic/pack.json",
     "file": "examples/minimal.esj.json"}
        -> {"rules": ["DIV-EXACT-35", ...], "warnings": []}

    The pack is a file of the rule language, relative to the repository root, whose rules
    are all written in the language: none written in code, and no code list snapshot. The
    binding compiles it against the registry of the document's edition and answers what
    that pack reports.

The answer of a validate request is compared with the outcome the manifest records: the
status, the layers not evaluated, and every finding with its path, code, subject and
severity. SPEC.md fixes the order of the findings in two places, and they are compared in
order there: the findings of the reader (section 9.6) and the findings of one path at
layer L2 (section 9.2). Everything else is compared as a set. A document of an edition the
binding does not carry is not validated by the runner: its outcome is recorded for the
registries of that edition. Its digests are still compared, because reading, the canonical
form and the digests need no registry.

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
            names.extend(document.get("registries", []))
        for case in manifest.get("bounds", []):
            names.append(case["file"])
        for case in manifest.get("registryChecks", []):
            names.extend(case["files"])
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


def cases(files, repository):
    """The rule cases of every manifest file, each with the document it starts from.

    A case names its base document by path, or, where the file carries the documents its
    cases start from, by the name it has among the file's bases.
    """
    found = []
    bases = {}
    for manifest in files:
        rules = manifest.get("rules")
        if not rules:
            continue
        loaded = load(HERE / rules["casesFile"])
        carried = loaded.get("bases", {})
        for case in loaded["cases"]:
            if "baseDocument" in case:
                found.append((case, carried[case["baseDocument"]]))
            else:
                base = bases.setdefault(case["base"], load(repository / case["base"]))
                found.append((case, base))
    return found


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


def normalized(outcome):
    """An outcome in the form two outcomes are compared in.

    The findings of the reader stay a list, because section 9.6 fixes how far a reader reads
    and in which order it reports; the findings of one path at layer L2 stay a list, because
    section 9.2 fixes the order of the checks of a path; everything else is a set.
    """
    reader, model, rest = [], {}, []
    for finding in outcome.get("findings", []):
        row = [finding.get("path"), finding.get("code"), finding.get("subject"),
               finding.get("severity")]
        code = finding.get("code") or ""
        if code.startswith("ESJ-L1-"):
            reader.append(row)
        elif code.startswith("ESJ-L2-"):
            model.setdefault(finding.get("path") or "", []).append(row)
        else:
            rest.append(row)
    return {
        "status": outcome.get("status"),
        "notEvaluated": sorted([entry.get("layer"), entry.get("reason")]
                               for entry in outcome.get("notEvaluated", [])),
        "reader": reader,
        "model": sorted([path, rows] for path, rows in model.items()),
        "others": sorted(rest),
    }


def answered(answer):
    """The outcome of a validate answer, or the answer itself where it is an error."""
    if "error" in answer:
        return answer
    return normalized(answer)


def layer_one_errors(answer):
    """The codes of the errors of layer L1 a validate answer reports, in order."""
    return [finding["code"] for finding in answer.get("findings", [])
            if finding["code"].startswith("ESJ-L1-") and finding.get("severity") == "error"]


def errors_at(answer, path):
    """The codes of the errors a validate answer reports about one path."""
    return [finding["code"] for finding in answer.get("findings", [])
            if finding["code"].startswith("ESJ-L") and finding.get("severity") == "error"
            and finding["path"] == path]


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


def digests(document):
    """The digests a manifest entry records, as a digest request answers them."""
    return {key: document[key] for key in
            ("semanticDigest", "documentDigest", "canonicalBytes", "values")}


def evaluated(manifest, carried):
    """Whether the binding is asked to validate the cases of a manifest file.

    A part carries the fixtures of one edition, and their outcomes were recorded with the
    registry of that edition; a binding that does not carry it answers
    ESJ-L2-EDITION-UNKNOWN, as section 9.2 requires. The core manifest is always evaluated:
    its documents name the default edition, or an edition no registry describes.
    """
    return "semanticModel" not in manifest or manifest["semanticModel"] in carried


def envelope_document(base, member, candidate):
    """The base document with a candidate written into one member of the envelope."""
    document = json.loads(json.dumps(base))
    if member == "semanticModel":
        document["semanticModel"] = candidate
    else:
        document["extensions"] = {candidate: "x"}
    return document


def run(binding, files, repository, report):
    carried = set(binding.ask({"op": "editions"})["semanticModels"])
    for manifest, document in documents(files, repository):
        name = document["file"]
        answer = binding.ask({"op": "digest", "file": name})
        report.check(name + " digests", digests(document),
                     {key: answer.get(key) for key in
                      ("semanticDigest", "documentDigest", "canonicalBytes", "values")})
        if "canonical" in document:
            expected = (repository / document["canonical"]).read_text(encoding="utf-8")
            report.check(name + " canonical form", expected,
                         binding.ask({"op": "canonicalize", "file": name}).get("canonical"))
        if evaluated(manifest, carried):
            report.check(name + " outcome", normalized(document["outcome"]),
                         answered(binding.ask({"op": "validate", "file": name})))

    for manifest in files:
        evaluate = evaluated(manifest, carried)
        for case in manifest.get("invalid", []):
            name = case["file"]
            if "values" in case:
                answer = binding.ask({"op": "digest", "file": name})
                report.check(name + " digests", digests(case),
                             {key: answer.get(key) for key in
                              ("semanticDigest", "documentDigest", "canonicalBytes", "values")})
            if not evaluate:
                continue
            request = {"op": "validate", "file": name}
            if "registries" in case:
                request["registries"] = case["registries"]
            report.check(name + " is rejected (" + case["layer"] + ")",
                         normalized(case["outcome"]), answered(binding.ask(request)))

        for case in manifest.get("bounds", []):
            name = case["file"] + " under " + json.dumps(case["limits"], sort_keys=True)
            report.check(name, normalized(case["outcome"]),
                         answered(binding.ask({"op": "validate", "file": case["file"],
                                               "limits": case["limits"]})))

        for case in manifest.get("registryChecks", []):
            answer = binding.ask({"op": "registry", "files": case["files"]})
            report.check(" + ".join(case["files"]) + (" is taken" if case["accepted"]
                                                      else " is refused"),
                         case["accepted"], answer.get("accepted", answer))

        for case in manifest.get("canonicalOrder", []):
            expected = (repository / case["canonical"]).read_text(encoding="utf-8")
            report.check(case["scrambled"] + " canonical form", expected,
                         binding.ask({"op": "canonicalize",
                                      "file": case["scrambled"]}).get("canonical"))
            answer = binding.ask({"op": "digest", "file": case["scrambled"]})
            report.check(case["scrambled"] + " digest",
                         {"documentDigest": case["documentDigest"], "values": case["values"]},
                         {"documentDigest": answer.get("documentDigest"),
                          "values": answer.get("values")})

        for grammar in manifest.get("grammars", []):
            base = load(repository / grammar["base"])
            if base["semanticModel"] not in carried:
                continue
            if "member" in grammar:
                for candidate in grammar["accept"]:
                    answer = binding.ask({"op": "validate", "document": envelope_document(
                        base, grammar["member"], candidate)})
                    report.check(grammar["grammar"] + " accepts " + json.dumps(candidate),
                                 [], layer_one_errors(answer))
                for candidate in grammar["reject"]:
                    answer = binding.ask({"op": "validate", "document": envelope_document(
                        base, grammar["member"], candidate)})
                    report.check(grammar["grammar"] + " rejects " + json.dumps(candidate),
                                 [grammar["code"]], layer_one_errors(answer))
                continue
            for candidate in grammar["accept"]:
                document = apply(base, [{"path": grammar["path"], "value": candidate}])
                answer = binding.ask({"op": "validate", "document": document})
                report.check(grammar["datatype"] + " accepts " + json.dumps(candidate),
                             [], errors_at(answer, grammar["path"]))
            for candidate in grammar["reject"]:
                document = apply(base, [{"path": grammar["path"], "value": candidate}])
                answer = binding.ask({"op": "validate", "document": document})
                report.check(grammar["datatype"] + " rejects " + json.dumps(candidate),
                             [grammar["code"]], errors_at(answer, grammar["path"]))

    for case, base in cases(files, repository):
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
            print("%s: %d documents, %d rejected, %d under bounds, %d registry sets, %d grammars,"
                  " %d canonical order, %d rule cases, %d arithmetic rules"
                  % (manifest["part"],
                     len(manifest.get("documents", [])),
                     len(manifest.get("invalid", [])),
                     len(manifest.get("bounds", [])),
                     len(manifest.get("registryChecks", [])),
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
