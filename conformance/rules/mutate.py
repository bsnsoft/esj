#!/usr/bin/env python3
"""Apply the mutations of mutations.json to the corpus instances they are made from.

A mutation is data rather than a file: an instance of the conformance corpus, a location
in it, and one change to make there. The broken documents are never checked in, which
keeps the repository free of invoices that look real and are wrong, keeps every mutation
readable beside the rule it is aimed at, and lets the whole set survive a release of the
corpus that reformats an instance.

This script is how a reader gets the documents out. The build does not use it: the test
in esj-cli applies the same data in memory, from the same file.

    python3 conformance/rules/mutate.py --out /tmp/mutations
    python3 conformance/rules/mutate.py --only br-co-10-ubl --print

Four operations are all a mutation needs and deliberately not more: set the string value
of what an expression selects, remove it, put a fragment beside it, or give an element an
attribute. An expression that selects nothing is an error rather than a change that does
nothing, because a set whose expressions have gone stale would otherwise keep passing by
validating the corpus over and over.
"""

import argparse
import json
import pathlib
import sys
import xml.etree.ElementTree as ElementTree

HERE = pathlib.Path(__file__).resolve().parent
SET = HERE / "mutations" / "mutations.json"
CORPUS = HERE.parent / "kosit"


def load():
    with SET.open(encoding="utf-8") as handle:
        return json.load(handle)


def register(namespaces):
    for prefix, uri in namespaces.items():
        ElementTree.register_namespace(prefix, uri)


def select(tree, xpath, namespaces):
    """Resolve one of the expressions the set is written in.

    The subset used here is a chain of prefixed element names, each with a one-based
    position, optionally ending in an attribute or in a name without a position, which
    means every element of that name.
    """
    steps = split(xpath)
    if not steps:
        raise ValueError(f"{xpath} selects nothing")
    attribute = None
    if steps[-1].startswith("@"):
        attribute = steps.pop()[1:]
    nodes = [(None, tree.getroot())]
    first = steps.pop(0)
    if qname(first, namespaces) != tree.getroot().tag:
        raise ValueError(f"{xpath} does not start at the document element")
    for step in steps:
        name, index, predicate = parse(step)
        found = []
        for _, node in nodes:
            children = [child for child in node if child.tag == qname(name, namespaces)]
            if predicate is not None:
                children = [child for child in children if matches(child, predicate, namespaces)]
            if index is None:
                found.extend((node, child) for child in children)
            elif index <= len(children):
                found.append((node, children[index - 1]))
        nodes = found
    if attribute is not None:
        return [(node, qname(attribute, namespaces) if ":" in attribute else attribute)
                for _, node in nodes]
    return nodes


def split(path):
    """Split a path on the solidus, leaving the ones inside a predicate alone."""
    steps = []
    depth = 0
    current = ""
    for character in path:
        if character == "[":
            depth += 1
        elif character == "]":
            depth -= 1
        if character == "/" and depth == 0:
            if current:
                steps.append(current)
            current = ""
            continue
        current += character
    if current:
        steps.append(current)
    return steps


def matches(element, predicate, namespaces):
    """Decide the one predicate shape the set uses: a child path with a value."""
    path, _, wanted = predicate.partition("=")
    wanted = wanted.strip().strip('"')
    nodes = [element]
    for step in split(path.strip()):
        name, index, _ = parse(step)
        if name.startswith("@"):
            return any(node.get(qname(name[1:], namespaces) if ":" in name[1:] else name[1:])
                       == wanted for node in nodes)
        found = []
        for node in nodes:
            children = [child for child in node if child.tag == qname(name, namespaces)]
            found.extend(children if index is None else children[index - 1:index])
        if not found:
            return False
        nodes = found
    return any((node.text or "").strip() == wanted for node in nodes)


def parse(step):
    name, _, rest = step.partition("[")
    if not rest:
        return name, None, None
    body = rest.rstrip("]")
    if body.isdigit():
        return name, int(body), None
    return name, None, body


def qname(name, namespaces):
    prefix, _, local = name.partition(":")
    if not local:
        return prefix
    return f"{{{namespaces[prefix]}}}{local}"


def fragment(xml, namespaces):
    declarations = " ".join(f'xmlns:{p}="{u}"' for p, u in namespaces.items())
    wrapper = ElementTree.fromstring(f"<fragment {declarations}>{xml}</fragment>")
    return list(wrapper)


def apply(mutation, namespaces):
    source = CORPUS / mutation["source"]
    tree = ElementTree.parse(source)
    for change in mutation["changes"]:
        operation = change["op"]
        value = change.get("value", "")
        selected = select(tree, change["xpath"], namespaces)
        if not selected:
            raise ValueError(f'{mutation["id"]} selects nothing with {change["xpath"]}')
        for parent, node in selected:
            if isinstance(node, str):
                if operation == "delete":
                    element = parent
                    element.attrib.pop(node, None)
                else:
                    parent.set(node, value)
                continue
            if operation == "set":
                node.text = value
                for child in list(node):
                    node.remove(child)
            elif operation == "delete":
                parent.remove(node)
            elif operation == "attribute":
                name, _, attribute_value = value.partition("=")
                node.set(name, attribute_value)
            elif operation in ("insert-before", "insert-after"):
                position = list(parent).index(node) + (1 if operation == "insert-after" else 0)
                for offset, element in enumerate(fragment(value, namespaces)):
                    parent.insert(position + offset, element)
            else:
                raise ValueError(f'{mutation["id"]}: unknown operation {operation}')
    return ElementTree.tostring(tree.getroot(), encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", help="the directory to write the documents to")
    parser.add_argument("--only", help="one mutation identifier")
    parser.add_argument("--print", action="store_true", dest="show",
                        help="write the document to standard output")
    parser.add_argument("--list", action="store_true", help="list the set and stop")
    arguments = parser.parse_args()

    document = load()
    namespaces = document["namespaces"]
    register(namespaces)
    mutations = [m for m in document["mutations"]
                 if arguments.only is None or m["id"] == arguments.only]
    if not mutations:
        print(f"no mutation {arguments.only}", file=sys.stderr)
        return 1
    if arguments.list:
        for mutation in mutations:
            print(f'{mutation["id"]:34s} {mutation.get("rule", "-"):10s}'
                  f' {mutation["outcome"]:8s} {mutation["note"]}')
        return 0
    out = pathlib.Path(arguments.out) if arguments.out else None
    if out:
        out.mkdir(parents=True, exist_ok=True)
    for mutation in mutations:
        xml = apply(mutation, namespaces)
        if arguments.show:
            sys.stdout.write(xml.decode("utf-8"))
        if out:
            (out / f'{mutation["id"]}.xml').write_bytes(xml)
    if out:
        print(f"wrote {len(mutations)} documents to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
