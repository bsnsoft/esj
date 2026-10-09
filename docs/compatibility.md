# Compatibility

*Part of [EN16931 Semantic JSON](../README.md).*

From 1.0.0 this project follows semantic versioning. A major release may break what is listed
below; a minor release adds and never breaks it; a patch release fixes defects. Until then a 0.9
release may break the Java API, and the changelog names every change under *Migration from*.
Four things are versioned separately: the format, the command line tool, the Java libraries and
the data they ship.

## The format

The format is 0.1 until 1.0.0-rc.1, which raises it to 1.0 unchanged unless review finds a
defect. `SPEC.md` 1.0 fixes the document, the path grammar, the value shape, the canonical form
and both digests. A 1.x reader reads every 1.y document with y ≤ x, and reads documents of
format 0.1.
The semantic digest does not depend on the format version (section 8.2); the document digest
covers the `version` member (section 8.3), so a 0.1 document and its 1.0 rewrite have different
document digests. Finding codes keep their meaning once released (section 9.6), and an extension
namespace is permanent (section 5.6).

## The command line tool

Covered: command names, option names and their meaning, the exit codes 0–2 and 4–9
([`cli.md`](cli.md#exit-codes); 3 is the abort of the Java virtual machine, not the tool's), and
the members of `--output json` — none is removed, renamed or retyped within a major version; new
commands, options and members may appear. Not covered: the text written for people, messages on
the error stream, the layout of HTML and PDF reports and renderings. Output is deterministic
within one version, not across versions. A preview, and not covered: `esj serve` and `esj mcp`,
the REST API, the MCP tools and the JSON they answer with ([`serve.md`](serve.md)); the body of
`POST /api/validate` is the report of `esj validate --output json` and is covered as that.

## The Java libraries

Covered: the public types and members of the packages [`java-api.md`](java-api.md#packages)
marks **API**, binary and source compatible within a major version, on Java 17 or later. Raising
the minimum Java version is a major change.

Not covered:

- packages whose name contains `internal`;
- anything marked `@Preview` or documented as preview: the 2026 edition of the semantic model
  (`…typed.v2026`, `…rules.en16931.v2026`), `…upgrade`, the module `esj-b2c`, and the types
  their Javadoc names so. They may change in any minor release;
- `esj-generator` and `esj-cli`, which are tools and are not published as libraries.

Rules within the API:

| Kind | May gain in a minor release | Never |
|---|---|---|
| options (`…Options`, `Limits`, `PdfLimits`) | a setting: `defaults()` and one `withX(…)` per setting | a public constructor |
| record | a component; the earlier constructor stays | record patterns over it are not covered |
| enum | a constant | removal; a constant is deprecated instead |
| interface named an extension point | default methods | an abstract method |
| any other interface | methods | — |

- Extension points: `SemanticHandler`, `InvoiceReader`, `JavaRule`, `RulePackSource`,
  `InvoiceRules`, `PdfaCheck`, `RenderTemplate.Files`, `PackFetcher.Download` (preview).
- The generated views, editors and step builders, `Coded` and the steps of `InvoiceSteps` are
  sealed: this project implements them. `EditorList`, `ValueList`, `IdentifierList` and
  `SchemedIdentifierList` are not for implementation.
- Generated views keep the names, return shapes and Java types of a released edition. A new
  profile or a new code list snapshot adds types and constants and changes none.
- A value that changes with a release is a method, not a constant: `Esj.formatVersion()`,
  `Esj.defaultSemanticModel()`, `Registry.defaultEditionKey()`, `En16931Pack.version()`, the
  defaults of every options class. A public constant keeps its value.
- An array a method returns is a copy.
- Deprecated elements are removed in the next major release at the earliest.
- No type of a third-party library appears in an API signature. The minimum version of a
  dependency may rise in a minor release (Saxon-HE 13.0 today).
- Use every `de.bsnsoft.esj` module at the same version; `esj-bom` does this. Mixing versions
  is not supported.

The Maven profile `api-check` compares every library module with the release
`esj.api-baseline` names, leaving out what this section leaves out ([`releasing.md`](releasing.md)).
It reports before 1.0.0, compares with 1.0.0-rc.1 from that release on, and fails the build from
1.0.0.

## Data the libraries ship

Registries, rule packs, validation packs and code list snapshots are data with versions of their
own (`en16931/1.3.16`, `xrechnung/3.0.2/2026-08-31`, dated snapshots). A minor release may add
packs, add a newer pack and make it the default. A verdict can therefore change between minor
releases because the published rules changed — that is the purpose of a pack, not a break.
A release may tighten a resource limit or refuse a new kind of hostile input in a patch release.

## Other implementations

The TypeScript and C# bindings implement `SPEC.md`, not the Java API. They carry the version of
this project, and their compatibility is that of the format: they are measured against
`conformance/fixtures`.
