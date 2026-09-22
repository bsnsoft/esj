# Validation packs

A **validation pack** is a directory of the official validation artefacts for one invoice
profile, in the form a machine can execute: XML Schema modules and compiled Schematron
stylesheets, exactly as their publishers released them, with a manifest that says which file
applies to which document.

The packs are here because the syntax engine of `esj validate` runs them rather than
reimplementing them. The rules of EN 16931 and of a CIUS are maintained, versioned and
corrected by their owners; a Java translation of an XPath expression would be a second
opinion with its own release cycle, and a wrong one on the day the owner ships a fix. So the
artefacts travel as data and the engine is the only thing this project writes.

Nothing under `packs/` is this project's work. Every file keeps the licence it came with, and
[`SOURCES.md`](SOURCES.md) records for each one where it came from, under which licence, with
which digest and on which day it was fetched.

## Layout

```text
packs/
  README.md                       this file
  SOURCES.md                      provenance, licences and digests
  <pack id>/<version>/<release>/  one pack
    pack.json                     the manifest
    cen/<version>/                the CEN/TC 434 EN 16931 Schematron, compiled to XSLT
    xrechnung-schematron/<v>/     the XRechnung Schematron, compiled to XSLT
    xsd/ubl-2.1/                  the UBL 2.1 schema modules Invoice and CreditNote import
    xsd/cii-d16b/                 the schema modules CrossIndustryInvoice imports
```

A pack is named by three parts, and all three are needed to say which rules ran: the profile
(`xrechnung`), the version of that profile (`3.0.2`) and the release date of the artefact
bundle (`2026-08-31`). Two releases of the same profile version differ — a bugfix release
adds, removes or re-levels rules — so the release date is part of the identity and appears in
every report.

## The manifest

`pack.json` has four parts.

**`components`** is what the engine reads. One entry per artefact: its `role` (`xsd` or
`schematron-xslt`), the `entry` file to load per document syntax, the files that belong to it,
its `license`, the licence or notice file beside it, the URL it was published at and the
archive it was taken from, and `unmodified`, which is always true.

**`levels`** is what each profile says about rules of those artefacts. An artefact flags each
of its rules, and that flag is what it says about the rule in general; a core invoice usage
specification may say something else about a rule for documents of its own profile, and it is
the body entitled to say it. One entry per profile: the syntax and profile it applies to, and
a level of `fatal`, `warning` or `information` per rule identifier. A finding then carries
both — the flag the artefact set, and the level the profile gives it, which is the one the
verdict is made on. `SOURCES.md` records where each table was published. A pack whose profiles
level nothing has no `levels` member, and every flag then stands as the artefact set it.

**`baseProfiles`** is the list of specification identifiers that name no core invoice usage
specification — `urn:cen.eu:en16931:2017` for the pack below. It exists because a rule set left
out for the profile is two different facts: a document that names EN 16931 and no CIUS leaves
the CIUS rule sets unused and has been checked completely, and a document that names a
specification this pack carries no rules for leaves them unused and has been checked less
thoroughly than it asked to be. The components cannot tell the two apart — the same rule sets
are skipped in both cases — so the manifest says which identifiers are the first case, and only
the second leaves the engine's verdict `INDETERMINATE`. A pack without the member treats every
skipped rule set as a gap.

**`files`** is the inventory: every file of the pack except `pack.json` itself, with its
SHA-256. It is what makes the pack reviewable. A test in `esj-syntax` recomputes every digest
over the bytes as they are packaged and fails if one differs, if a listed file is missing, or
if a file is present that the inventory does not list. A pack cannot drift quietly, and a file
cannot be slipped in.

## How a document selects a pack

Two facts decide, and both are read from the document itself:

1. the **syntax** — `ubl-invoice`, `ubl-creditnote` or `cii`, from the root element;
2. the **profile** — the customization identifier in BT-24 (Specification identifier).

A component applies when the document's syntax is in its `appliesTo.syntax` and the profile
matches one of the patterns in `appliesTo.profile`. A pattern is an exact identifier, or an
identifier followed by `*`, which matches any suffix; `*` alone matches every profile and is
what the schema components carry, because an XML Schema does not depend on the profile.

So a UBL invoice that names the XRechnung 3.0 CIUS is checked against the UBL 2.1 schema, the
EN 16931 UBL Schematron and the XRechnung UBL Schematron, and a CII document that names no
CIUS is checked against the CII schema and the EN 16931 CII Schematron alone.

A level table is chosen the same way, and the first one of the manifest that names the syntax
and matches the profile is the one that applies. A document whose profile matches none of them
is judged on the flags of the artefacts alone.

`esj --list-packs` prints what a build carries, component by component, with the licence of
each. `esj validate --pack <directory|id>` overrides the choice: a directory holding a
`pack.json`, which is how a release newer than a build reaches the engine, or the identity of a
bundled pack. Nothing is ever fetched at run time, and a pack directory is a directory of files
the tool will execute, so where it came from is the caller's business.

## Adding a release

A release is **never** overwritten. A new bundle is a new directory beside the old one, and the
old digests stay valid for every report that ever named them. Adding one is:

1. build the directory from the upstream archives, with every file copied byte for byte;
2. write `pack.json`, including the level tables and the inventory;
3. add the rows to `SOURCES.md` and the entries to `NOTICE` at the root of the repository;
4. name the directory in the index the engine finds its packs by,
   `esj-syntax/src/main/resources/de/bsnsoft/esj/syntax/bundled-packs.json` —
   a class path inside a jar is not a directory that can be listed, so the packs are
   named rather than discovered;
5. run `mvn -B -q verify`, which checks the digests, the inventory and that the index and
   the packaged packs are the same list.

## Size

A pack is a few megabytes: this one is 2.7 MiB over 28 files, and most of it is the two
compiled CEN stylesheets. Only supported releases stay in the tree. When a release stops being
supported it is removed in a release of this project rather than kept forever — the history
still has it, and a report that names it still names something that can be reconstructed from
the digests in `SOURCES.md`.

Two things keep the directory from growing without bound. Only what the engine *executes* is
here: no Schematron sources, no scenario configuration, no documentation, no example
instances, and no validator engine. And the schema sets are the transitive import closure of
the document schemas, computed rather than guessed, so a schema library arrives with the files
that are reachable from `Invoice`, `CreditNote` and `CrossIndustryInvoice` and with no others.
