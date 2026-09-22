# EN16931 Semantic JSON (ESJ) — Specification

**Version 0.1 — release candidate: expected to become 1.0 unchanged unless review finds a defect**

Author: Christian Bürckert
Publisher: BSNSoft Solutions GmbH
License: Apache License, Version 2.0

This document specifies a JSON serialization of the semantic invoice model of EN 16931-1,
in the editions section 4.4 names. It is an independent work. It is not a CEN deliverable, it is
not a syntax binding in the sense of CEN/TS 16931-3, and it is not endorsed by CEN or by any
national standards body. It reproduces no normative text of EN 16931-1; where a fact of the
model is needed, the clause that states it is cited.

---

## 1. Scope

### 1.1 What this document specifies

This specification defines:

1. the **envelope** of an ESJ document (section 4),
2. the **semantic path** grammar that addresses business terms (section 5),
3. the **value model**: the shape of a value, and the grammars the semantic data types of
   the model require of its content (section 6),
4. the **canonical form** — one byte sequence per document — and the two **digests** derived
   from it (sections 7 and 8),
5. three **validation layers**, the shape of their findings and the three states of a
   validation result (section 9),
6. the role of the **term registry** as the source of structural facts (section 10),
7. **extensions** and the handling of profiles (section 11),
8. **security considerations** and reader limits (section 12).

Sections 13 (relational and key/value mapping), 14 (non-goals) and 15 (an ESJ document inside
a PDF) are informative. Section 3 defines the conformance classes; every normative requirement
in this document belongs to one of them.

### 1.2 What this document does not specify

ESJ does not define invoice semantics. Which business terms exist, what they mean, how they
are numbered, how often they may occur and which semantic data type each one has is decided by
EN 16931-1 in the edition a document names, clause 6.3 (the semantic model, Table 2) and
clause 6.5 (the semantic data types) of that edition. ESJ only decides how those facts are
written down as JSON.

The business rules of EN 16931-1, clause 6.4 (integrity constraints, identified as BR-*;
conditions, identified as BR-CO-*; and the VAT category rules) are **not checked in this
version**, and neither are the decimal restrictions BR-DEC-*, which are not a rule family of
clause 6.4 at all: they are defined by the CEN/TC 434 validation artefacts, which derive them
from the number of fraction digits a business term allows in the edition they are written for
(section 6.4). They are a separate layer, planned as rule packs identified by name and
version. A document may be structurally valid under this specification and still violate any
of them. See section 9.4.

Transport, signing, invoice archiving, syntax conversion and the code lists themselves are out
of scope as well.

### 1.3 Relationship to EN 16931-1

EN 16931-1 describes a semantic model. Its clause 4.4 defines what compliance to the core
invoice model means at three levels: for a specification, for a sending or receiving party, and
for an invoice instance document. The syntaxes that comply with the standard are listed
separately, in CEN/TS 16931-2, and ESJ is not one of them. An ESJ document is a representation
of the semantic content; whether a given process built on it satisfies a legal or contractual
requirement is not decided by this specification.

The default edition of this specification is EN 16931-1:2017+A1:2019 with the corrigendum
AC:2020 applied, written `EN16931-1:2017+A1:2019/AC:2020`. This version defines a second
edition beside it, EN 16931-1:2026, written `EN16931-1:2026`; whether a given build carries a
registry for one of them is a property of that build, and an implementation names the editions
it holds registries for (section 3). Neither is the only value the `semanticModel` member
admits: section 4.4 gives a grammar that a further edition satisfies too, and says what an
implementation does with an edition it has no registry for.

---

## 2. Notation and terminology

### 2.1 Requirement keywords

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**,
**SHOULD NOT**, **RECOMMENDED**, **MAY** and **OPTIONAL** in this document are to be
interpreted as described in BCP 14 [RFC2119] [RFC8174] when, and only when, they appear in all
capitals.

### 2.2 Grammar notation

Grammars are written in ABNF [RFC5234] with the case-sensitive string notation of [RFC7405];
`%s"BT"` therefore matches the two characters `BT` and nothing else. The collected grammar is
in Appendix A.

Where this document gives a regular expression, it is in the dialect of [ECMA-262], because
that is the dialect JSON Schema uses. The regular expressions are a convenience; the ABNF is
authoritative.

### 2.3 Terms used in this document

| Term | Meaning |
|---|---|
| business term (BT) | An information element of the semantic model that carries a value, identified as `BT-n`. |
| business group (BG) | A named grouping of business terms and other groups, identified as `BG-n`. |
| core term | A business term or business group of EN 16931-1 itself. |
| extension term | A business term or group defined outside EN 16931-1, carrying a namespace (section 5.6). |
| semantic path | The absolute address of one business term occurrence inside a document (section 5). |
| occurrence index | A zero-based ordinal that distinguishes repeated occurrences of a repeatable term or group. |
| group instance | One occurrence of a business group, identified by the path prefix that leads to it. |
| value object | The JSON object form of a value: the content and its supplementary components (section 6.1). |
| supplementary component | A part of a semantic data type beside its content: `scheme` and `schemeVersion` of an Identifier, `mimeCode` and `filename` of a Binary Object. |
| registry | The machine-readable term list of one edition, laid out as `model/en16931/<edition key>.json` for the core model (section 10). |
| canonical form | The single byte sequence defined in section 7 for a given document content. |
| pretty form | The indented, human-readable serialization defined in section 7.7. |
| document | A complete ESJ envelope as defined in section 4. |
| well-formed document | A document that satisfies validation layer L1, whether or not it also satisfies L2 and L3 (section 3.1). |
| conformant document | A well-formed document that is also consistent with the registry at layers L2 and L3 (section 3.1). |
| validation result | What a validator returns: a status of `VALID`, `INVALID` or `INDETERMINATE`, the findings, and which layers were evaluated (section 9.5). |
| not evaluated | Said of a layer or of a single check the validator did not carry out, for a reason it names; a result with one is never `VALID` (section 9.5). |

### 2.4 JSON

An ESJ document is a JSON text as defined in [RFC8259]. Where this document says *object*,
*member*, *string*, *number*, *array*, *true*, *false* or *null*, it uses those words in the
sense of [RFC8259].

---

## 3. Conformance

This specification defines five conformance classes. An implementation MAY implement any
subset; it MUST NOT claim a class it does not fully implement.

A class is claimed **per class and not per edition**. A reader and a canonicalizer are
edition-blind by construction; a writer and a validator work against the registry of one
edition, so an implementation of either MUST name the editions it holds registries for.

| Class | Needs a registry | An edition it has no registry for |
|---|---|---|
| Reader (3.2) | no | read like any other document |
| Canonicalizer (3.4) | no | canonicalized and hashed like any other document |
| Validator (3.5), L1 | no | evaluated |
| Validator (3.5), L2 and L3 | yes, the matching one and no other | not evaluated: `ESJ-L2-EDITION-UNKNOWN`, status `INDETERMINATE` (section 9.2) |
| Writer (3.3) | yes, the matching one and no other | not written |

### 3.1 Conformant document

A byte sequence is a **conformant ESJ document** if and only if:

1. it is a JSON text encoded in UTF-8 without a byte order mark (section 4.2),
2. its top level is an object whose members satisfy section 4,
3. every member name of `values` satisfies the path grammar of section 5.1,
4. every member value of `values` satisfies the shape rules of section 6.1,
5. it contains no duplicate member names anywhere (section 4.2), which is also the rule of
   section 5.5 that no path occurs twice,
6. it is consistent with the registry of the edition it names, in the sense of validation
   layers L2 and L3 (sections 9.2 and 9.3). That covers the structural rules of sections 5.2
   to 5.6 that need a registry or the whole document — the parent chain of a path (5.2), the
   index rule (5.3), the density of indices (5.4) and the structure of an extension segment
   (5.6) — and the content grammar the registry requires at each term (section 6.2).

Requirements 1 to 5 are layer L1; requirement 6 is layers L2 and L3. This list and the check
list of section 9.1 are the same layer, stated twice for two audiences.

One line runs through the split: a requirement is L1 when the bytes decide it, and L2 or L3
when a registry or the rest of the document does. Section 5 is therefore not one layer. Its
grammar (5.1) is requirement 3 and is decided by the characters of a member name alone; its
rule that two paths are equal only as strings (5.5) is requirement 5, because a repeated path
is a repeated member name; everything else in section 5 asks what a term's parents and
cardinality are, and only a registry answers that.

Requirement 6 is a property of the document and of the registry of the edition the document
names — not of the stock of registries any one implementation happens to hold. An
implementation with no registry for that edition cannot decide the requirement and MUST NOT
answer it by guessing in either direction: it reports that the model layers were not checked,
and its result is `INDETERMINATE` (sections 4.4 and 9.5). Where no registry for that edition
exists at all, the requirement has nothing to be measured against and the document's
conformance is simply undecided: it is well formed, and no party may call it more or less than
that.

A byte sequence that satisfies requirements 1 to 5 is a **well-formed ESJ document**, whether or
not it also satisfies requirement 6. Every conformant document is well formed; the converse does
not hold. The well-formed band is not a defect of this specification but the normal state of a
document in transit: a converter emits one before the registry has seen it, a validator is handed
one by definition, and an editor holds one while a mandatory term is still missing. An
implementation MAY process a well-formed document; where it does, it MUST report the findings of
the layers it was asked for, and MUST name the layers it did not evaluate (section 9.5).

Every one of the six requirements is a property of the byte sequence and of the registry the
document names. **The configurable limits of section 12.2 are not among them.** A limit is the
reading party's policy: a document is refused because processing it would cost more than its
recipient agreed to spend, not because anything about it is wrong. A reader that refuses one
for that reason reports `ESJ-L1-LIMIT`, and that finding says *not processed under this
configuration*, not *not conformant* — the same byte sequence is a conformant document for a
reader configured differently, and both readers are right. An implementation MUST NOT report a
limit as a defect of the document. Section 12.2 gives defaults so that two implementations
that adopt them refuse the same documents.

The bounds this document fixes itself stay normative, because they are decided by the bytes
rather than by a configuration: 64 characters for a decimal form (sections 6.4 and 7.6), 128
for an owner token (section 4.6). Each belongs to the grammar it is stated with and is
reported with that grammar's code.

Two of the five conformance classes are therefore defined over the well-formed band rather than
over the conformant one: a reader (section 3.2) and a canonicalizer (section 3.4) MUST work
without a registry, and neither can tell the two bands apart. A writer (section 3.3) and a
validator (section 3.5) do know the registry and are defined against the conformant band.

A conformant document is not necessarily a valid invoice: the business rules of EN 16931-1,
clause 6.4 are a separate layer, checked by rule packs and reported as their own findings, and
never part of ESJ conformance (section 9.4).

### 3.2 Conformant reader

A conformant reader:

1. accepts every **well-formed** document (section 3.1), measured against the limits it is
   configured with, and rejects every byte sequence that fails L1 — a reader needs no registry,
   so it neither decides nor claims conformance. In particular it accepts a document whose
   `semanticModel` satisfies the edition grammar of section 4.4 but names an edition it has no
   registry for, and reads, canonicalizes and digests that document like any other,
2. rejects duplicate member names, JSON numbers, `true`, `false` and `null` anywhere inside
   `values`, and any member not defined in this specification (strictness, section 4.2),
3. enforces the limits of section 12.2, running the defaults given there unless it is
   configured otherwise, and reports a limit violation as the finding `ESJ-L1-LIMIT` rather
   than truncating; because it meets such a violation while it parses, it MAY additionally
   abort with an exception carrying that code (section 9.5). Such a refusal is a statement
   about this reader's configuration and not about the document (section 3.1),
4. preserves the exact string content of every value; it MUST NOT trim, collapse, normalize
   Unicode, reorder or otherwise alter values, with the single exception of the line-ending
   normalization of section 6.8, which it MUST apply,
5. reports structural problems as findings (section 9.5). Where it cannot construct a document
   at all, it MAY instead end with an exception carrying the finding it stopped at; reporting
   and throwing are both conformant there, and section 9.5 says how the two relate. Where it
   returns findings it returns every one it saw and not only the first. An I/O failure is an
   exception in either case.

### 3.3 Conformant writer

A conformant writer produces documents that satisfy section 3.1, in canonical form
(section 7) or in pretty form (section 7.7). It knows the registry, so it knows the semantic
data type of every term it writes: it MUST write a decimal in the canonical decimal form of
section 6.4, a date and a time in the forms of section 6.5 and a binary object in the
canonical base64 of section 6.7, and it MUST write a value as a string where the content
carries no supplementary component and as an object where it does (section 6.1).

A writer is the only party in ESJ that turns a value of some other model into an ESJ value, so
it is the only one that ever converts a spelling. Handed the exact decimal value `100.00` it
writes `100`, because the trailing fraction zeros carry no numeric information and the
canonical decimal form has none (section 6.4). No later stage repeats that step for it.

A writer that takes its content from somewhere else — another syntax, a database, a document
written for a wider profile — and that knows the limits of the reader it writes for SHOULD
measure the document it is about to write against those limits, and SHOULD report what it
left out. This is a SHOULD and not a MUST because a writer need not know any reader's limits;
where it does, the reason to hold to them is that a reader refuses the document and not the
part that exceeded the bound (section 12.2), so one value too many costs the invoice and not
the value, and no later stage can repair that: repairing it would mean truncating a business
term. The bound on the length of a path is the one that is easiest to overlook, because it is
a property of the shape of the document rather than of its content; section 5.6 says where it
comes from.

### 3.4 Conformant canonicalizer

A conformant canonicalizer maps every **well-formed** document (section 3.1) to the byte
sequence defined in section 7, and produces the two digests of section 8 from it. Two
conformant canonicalizers MUST produce identical bytes for the same document content, in any
programming language.

A canonicalizer MUST NOT change the content of a value. It orders members, escapes strings,
normalizes line endings (section 6.8) and removes insignificant whitespace; it does not
rewrite a decimal, a date or a base64 string, and it has no registry with which it could tell
which of the three it is looking at. A document whose content does not satisfy the grammar its
term requires is refused at L2 by whatever holds the registry (section 9.2); it is never
repaired on the way through.

The class is deliberately defined over the well-formed band and not over the conformant one. A
canonicalizer needs no registry (section 7.4) and therefore cannot decide whether a document is
conformant; defining it over the conformant band would leave its behaviour undefined on exactly
the input a real pipeline hands it. The same holds for the edition: nothing in section 7 reads
a registry, so a canonicalizer produces the bytes and the digests of a document whose
`semanticModel` names an edition it knows nothing about (section 4.4). This is also what rule 3
of section 7.4 is for: it orders a term segment before an index segment, a pairing that cannot
occur in a conformant document but can occur in a well-formed one whose index rule was never
checked.

A canonicalizer reads untrusted input like a reader does, so it enforces the limits of
section 12.2 in the same way and reports `ESJ-L1-LIMIT` for a violation. That refusal is a
property of its configuration and not of the document (section 3.1).

### 3.5 Conformant validator

A conformant validator implements layers L1, L2 and L3 of section 9 against a registry, and
returns a validation result as defined in section 9.5: a status of `VALID`, `INVALID` or
`INDETERMINATE`, the findings, and the layers it evaluated. It MUST make the layers
individually selectable. It MUST NOT reject a document for a reason outside the layer it was
asked for.

It MUST NOT return `VALID` unless it evaluated all three layers. A layer it did not evaluate —
because the caller did not ask for it, because the document never was a byte sequence (next
paragraph), because no registry for the edition is available, or because a limit stopped the run
— makes the result `INDETERMINATE`, and the result names that layer and the reason
(section 9.5). This is the rule of section 9.5 and nothing narrower: the two sections state one
condition for `VALID`.

L1 is decided by the bytes (sections 3.1 and 9.1), and not every document has any. One that was
imported from another syntax, or built by an editor, was never a byte sequence this specification
describes, so L1 has nothing to decide about it and no validator can evaluate it. A result over
such a document is measured against L2 and L3 alone: the validator names L1 as not evaluated for
the reason `NOT-REQUESTED`, because that layer was not asked of the document and could not be,
and its own status is therefore `INDETERMINATE` however clean L2 and L3 came out. The verdict a
user sees is then not the validator's but that of the tool around it: a tool that composes a
verdict for such an input decides what the complete check for that input is, leaves L1 out of it,
and MAY answer `VALID` on the strength of L2 and L3. Where the document did arrive as bytes no
such composition is open: a verdict that leaves L1 out of a document that has bytes is a verdict
about less than the document. A validator that offers the layers separately therefore answers a
narrower question with a narrower verdict, and never with a `VALID` that a caller would read as
more than was checked.

---

## 4. The envelope

### 4.1 Shape

An ESJ document is a JSON object with the following members.

| Member | Required | Type | Section |
|---|---|---|---|
| `format` | yes | string, fixed | 4.3 |
| `version` | yes | string, fixed | 4.3 |
| `semanticModel` | yes | string | 4.4 |
| `values` | yes | object | 4.5 |
| `extensions` | no | object | 4.6 |
| `source` | no | object | 4.7 |

Example, complete and minimal in structure (not a valid invoice; see `examples/minimal.esj.json`
for the smallest document that satisfies all three validation layers):

```json
{
  "format": "EN16931-Semantic-JSON",
  "version": "0.1",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "values": {
    "/BT-1": "RE-2026-0001"
  }
}
```

### 4.2 General rules

1. The top level MUST be a JSON object. A document whose top level is an array, a string or
   any other JSON value is not an ESJ document. Every envelope member that is present MUST be
   of the JSON type the table of section 4.1 gives it: `{"values": []}`, `{"extensions": "x"}`
   and `{"source": 5}` are errors, reported as `ESJ-L1-ENVELOPE-VALUE` (section 9.6).
2. A document MUST be encoded in UTF-8. A reader MUST reject any other encoding and MUST
   reject a leading byte order mark (U+FEFF). A BOM is not whitespace and is not part of a
   JSON text.
3. A document MUST NOT contain a member name twice within the same object, at any depth. A
   reader MUST reject such a document; it MUST NOT silently keep the first or the last
   occurrence. This applies to `values`, where duplicates would mean two values for one
   semantic address, and to `extensions`, where the surrounding JSON is otherwise free.
4. A reader MUST reject any member not defined by this specification, at every level of the
   envelope, including inside `source`. Unknown members are errors, not data to ignore. This
   makes a document's meaning independent of the reader's version: a reader that does not know
   a member cannot quietly drop information. Forward compatibility is handled by `version` and
   by `extensions`, not by leniency.
5. Inside `values`, every leaf is a JSON string. A member of `values` is a string or a value
   object (section 6.1), and every member of a value object is a string. JSON numbers,
   `true`, `false`, `null`, arrays and objects nested any deeper MUST NOT appear there. The
   reason is determinism: a JSON number has no single textual form, and a reader that parses
   `100.00` into a binary floating point value has already lost the document. Inside
   `extensions`, all JSON value types are allowed (section 4.6).
6. The order of members in the document is not significant for its meaning. It is significant
   for the canonical form (section 7), and only there.

### 4.3 `format` and `version`

`format` MUST be the exact string `EN16931-Semantic-JSON`. `version` MUST be the exact string
`0.1`.

`version` identifies the version of **this specification**, not the version of the semantic
model. A reader that does not implement the version it finds MUST reject the document.

Version numbering follows `MAJOR.MINOR`. Until 1.0, any version MAY change the format in
incompatible ways.

### 4.4 `semanticModel`

`semanticModel` names the **edition** of the semantic model that the paths refer to. Its
value is an edition string:

```abnf
edition     = model-token [ "+" amendment ] [ "/" corrigendum ] *( "+" amendment [ "/" corrigendum ] )
model-token = 1*( ALPHA / DIGIT ) *( "-" 1*( ALPHA / DIGIT ) ) ":" year
amendment   = %s"A" 1*DIGIT ":" year
corrigendum = %s"AC" [ 1*DIGIT ] ":" year
year        = 4DIGIT
```

The edition this document describes is written

```
EN16931-1:2017+A1:2019/AC:2020
```

and it is the edition an implementation writes unless it is asked for another. A further
edition is written the same way — `EN16931-1:2026` for a base edition with no amendment — and
is a different value of this member. `/BT-20` shows what that means: it addresses a business
term of the 2017 edition, and the 2026 edition places that term inside a group, where the path
that reaches it is a longer one. The paths of a document are addresses relative to the edition
it names, and nothing else. The registry that describes an edition spells it with the spaces
the standards body uses, `EN 16931-1:2017+A1:2019/AC:2020`; section 10 gives the mapping
between the two spellings.

**What L1 checks here, and what it does not.** At layer L1 the value of `semanticModel` MUST
satisfy the `edition` grammar above, and nothing more is asked of it. A value that does not
satisfy the grammar is `ESJ-L1-ENVELOPE-VALUE` (section 9.6). Whether a registry for that
edition exists is a property of the implementation and not of the document, and this
specification does not let the stock of registries one party happens to hold decide what a
document is.

Whether a matching registry is available is therefore an **L2** question (section 9.2). A
validator that has none for the edition the document names checks the model layers not at all:
it reports `ESJ-L2-EDITION-UNKNOWN` at document level, evaluates neither L2 nor L3, and returns
the status `INDETERMINATE` (section 9.5). It MUST NOT report the document as invalid for that
reason, and it MUST NOT fall back to the registry of another edition (section 10) — a path is
an address relative to an edition, and the same `/BT-1` under two editions may be two different
business terms. That is why a validator uses the registry whose `edition` matches this member
(section 10) and why the semantic digest covers it (section 8.2).

A **reader** and a **canonicalizer** need no registry at all (sections 3.2 and 3.4), so they
accept, canonicalize and digest a document of an edition they have no registry for. An
implementation written today therefore reads, stores, forwards and hashes a document of an
edition published after it, and claims about that document only what it can see. This is not
the treatment `version` gets (section 4.3), and the difference is the point: an unimplemented
format version means the reader does not know what the bytes mean, while an unknown edition
means only that it cannot look the paths up. That is what the two classes are defined over the
well-formed band for (section 3.1). The alternative — refusing the document — would lose
content in the one situation the format exists to survive, and would make an old reader the
reason a new invoice cannot be archived.

`semanticModel` is not a profile identifier. The profile is BT-24 inside the document; see
section 11.2.

### 4.5 `values`

`values` MUST be a JSON object. Each member name is a semantic path (section 5); each member
value is a value in one of the two shapes of section 6.1, a JSON string or a value object.

`values` MAY be empty at layer L1. A document whose `values` is empty cannot satisfy L3,
because the semantic model has mandatory terms at the root (section 9.3).

`values` is flat: the structure of the invoice lives in the member names, not in nested JSON
objects. Consequences:

* A group instance exists if and only if at least one path lies under it. Groups are never
  written down as such.
* A group instance that contains no value is not representable. This is a deliberate
  limitation; it costs nothing, because a group with no value carries no information in the
  semantic model either. Writers that need to record the presence of an empty group MUST use
  `extensions`.

### 4.6 `extensions`

`extensions` carries data that is not part of the semantic model.

1. `extensions` MUST be absent when it would be empty. An empty object is not allowed, because
   two documents with the same content would otherwise have two canonical forms.
2. Each member name is an **owner token** that identifies who defines the content below it. A
   reverse domain name is RECOMMENDED, for example `de.example.vendor`. An owner token MUST
   match this grammar, MUST be at most 128 characters long, and MUST NOT begin with `BT-` or
   `BG-`:

   ```abnf
   owner-token = owner-alnum [ *owner-char owner-alnum ]
   owner-alnum = ALPHA / DIGIT
   owner-char  = owner-alnum / "." / "_" / "-"
   ```

   The token is therefore ASCII, starts and ends with a letter or a digit, and contains no
   spaces, no slashes and no colons. JSON would allow any string; ESJ narrows it on purpose,
   because an owner token is the one part of an extension that leaves the document. It becomes
   a log line, a configuration key, a column name, a directory name and a command line
   argument, and each of those has its own opinion about punctuation. `urn:example:v/2` and
   `de.exämple.vendor` are not owner tokens; `de.example.vendor` and `example-vendor_2` are.
   The 128-character bound keeps the token usable as an identifier in those places.
3. The member value MAY be any JSON value, including numbers, booleans, `null`, arrays and
   nested objects. The nesting depth is limited (section 12.2). A JSON number is kept exactly
   as the sender wrote it: section 7.6 canonicalizes its spelling and never converts it to a
   binary floating point value, so no digit of it is lost. The one number ESJ refuses here is
   one whose canonical decimal form would exceed 64 characters (sections 7.6 and 6.4).
4. Extensions MUST NOT redefine, restate or contradict a core business term. A value that
   belongs to a business term of EN 16931-1 belongs in `values`, keyed by its path.
5. Extensions that define additional business terms with their own identifiers SHOULD place
   those terms in `values` with an extension namespace (section 5.6) and use `extensions` only
   for data that has no term at all.
6. A reader MUST NOT execute, resolve or dereference anything it finds in `extensions`. See
   section 12.4.

### 4.7 `source`

`source` records where the document came from. It is provenance metadata and is never part of
the semantic identity of the document: the semantic digest (section 8.2) does not cover it.

1. `source` MUST be absent when it would be empty.
2. The only allowed members are:
   * `syntax` — a non-empty string naming the syntax the content was extracted from, for
     example `UBL`, `CII` or `ESJ`;
   * `sha256` — the SHA-256 digest of the source bytes, as 64 lowercase hexadecimal digits.
3. Both members are OPTIONAL individually; at least one MUST be present.
4. Any other member is an error (section 4.2, rule 4).
5. Both members are strings of valid Unicode, like every other string of the document: a lone
   surrogate in either is an error and is reported as `ESJ-L1-SURROGATE` (sections 6.8 and 9.6).
   This is said here because `source` is the one place where the rule has nothing else to lean
   on — `sha256` has a grammar that a surrogate also breaks, but `syntax` has none at all, so a
   reader that only checks what a grammar constrains would accept a `syntax` that no
   canonicalizer can afterwards encode (section 3.4).
   `examples/invalid/source-lone-surrogate.esj.json` is the fixture.

`source.sha256` lets a receiver tie an ESJ document back to the exact bytes it was derived
from without having to keep those bytes in the same file. It is a digest of **the source
document from which this ESJ document was derived**, and never of the ESJ file that carries it,
which could not contain a digest of itself. That source document is usually an instance of
another syntax, a UBL or CII instance, but it need not be: where an ESJ document is derived
from an earlier ESJ document, `syntax` is `ESJ` and `sha256` is the digest of that earlier
file's bytes, so a transformation inside the format records its provenance like any other. It
is the only digest in ESJ that identifies a file at all; the two digests of section 8 identify
content (section 8.4).

### 4.8 Media type and file extension

The file extension is `.esj.json`.

The media type is `application/vnd.en16931-semantic+json`. **This media type is not registered
with IANA.** It is provisional and MUST be treated as such: it may change, and it MUST NOT be
used in a context that requires a registered type. Generic JSON consumers MAY use
`application/json`.

---

## 5. Semantic paths

### 5.1 Grammar

```abnf
path        = *( group-step ) bt-step

group-step  = "/" bg-term [ "/" index ]
bt-step     = "/" bt-term [ "/" index ]

bg-term     = %s"BG-" ( core-number / ext-suffix )
bt-term     = %s"BT-" ( core-number / ext-suffix )
ext-suffix  = namespace "-" ext-number

core-number = nonzero *DIGIT
ext-number  = 1*DIGIT
namespace   = UPPER *( UPPER / DIGIT )
index       = "0" / ( nonzero *DIGIT )

nonzero     = %x31-39
UPPER       = %x41-5A
DIGIT       = %x30-39
```

Notes on the grammar:

* A path is absolute: it always starts with `/`. There are no relative paths in ESJ.
* A path always ends at a business term, optionally followed by that term's occurrence index.
  Values exist only at business terms; a path that stops at a group addresses nothing.
* `core-number` has no leading zeros, because the identifiers of EN 16931-1 have none.
  `ext-number` is taken verbatim from the extension registry, which may use leading zeros
  (for example `BT-DEX-001`).
* `index` has no leading zeros either: `/BG-25/0/…` and `/BG-25/10/…` are paths, `/BG-25/00/…`
  is not.
* The grammar admits at most one index per segment, and no index directly after another index.

Equivalent regular expression, as used by `schema/esj.schema.json`:

```
^(?:/BG-(?:[1-9][0-9]*|[A-Z][A-Z0-9]*-[0-9]+)(?:/(?:0|[1-9][0-9]*))?)*
 /BT-(?:[1-9][0-9]*|[A-Z][A-Z0-9]*-[0-9]+)(?:/(?:0|[1-9][0-9]*))?$
```

(written over two lines here; it is one expression with no whitespace).

### 5.2 Terms are the identifiers of the standard

Core segments MUST use the business term and business group identifiers of EN 16931-1
exactly as the standard numbers them: `BT-1`, `BG-25`, `BT-131`. They MUST NOT be replaced by
element names of UBL or CII, by field names of an application, or by any other alias. This is
the whole point of the format: the address of a value is the identifier a reader can look up in
the standard.

A path MUST be structurally possible: the sequence of group segments MUST be exactly a chain
of parents a loaded registry records for the final term. For BT-146 the core registry gives the
chain BG-25, BG-29, so `/BG-25/0/BG-29/BT-146` is the well-formed shape; `/BG-29/BT-146` and
`/BG-25/0/BT-146` are both errors. A loaded extension registry MAY record a second chain for the
same core term, and then both are accepted; see section 5.6. This check belongs to layer L2
(section 9.2).

### 5.3 The index rule

Whether a segment carries an index is decided by the **declared** maximum cardinality in the
registry, never by how many occurrences a particular document happens to contain.

1. A term or group whose declared maximum cardinality is greater than 1 (written `n` in the
   registry) MUST be followed by an index segment.
2. A term or group whose declared maximum cardinality is 1 MUST NOT be followed by an index
   segment.

This applies to business terms as well as to business groups. In EN 16931-1 exactly two core
business terms are repeatable — BT-29 (Seller identifier) and BT-158 (Item classification
identifier) — so their paths always carry an index:

```
/BG-4/BT-29/0
/BG-25/0/BG-31/BT-158/0
```

An invoice with a single line still writes `/BG-25/0/…`, because BG-25 is declared `1..n`.
The benefit is that a path can be read, written and stored without knowing how many
occurrences exist elsewhere in the document, and that adding a second occurrence never rewrites
the first one's address.

### 5.4 Density of indices

Indices are zero-based and dense. For every group or repeated term, if index *k* occurs then
every index from 0 to *k*−1 occurs as well, within the same parent instance.

A gap is a structural error (layer L3, section 9.3). Indices carry no meaning beyond
distinguishing occurrences; they are not line numbers. The invoice line identifier is BT-126,
and it is independent of the index.

The order of the indices is the order of the occurrences as the writer intends them to be read.

### 5.5 Path equality and duplicates

Two paths are equal if and only if their strings are equal, code point by code point. There is
no normalization, no case folding and no equivalence between different spellings.

A document MUST NOT contain the same path twice in `values`. Because paths are member names,
this is the duplicate-member rule of section 4.2, rule 3.

### 5.6 Extension segments

An extension term carries a namespace between the kind and the number: `BG-DEX-01`,
`BT-DEX-001`. The namespace is a short uppercase token that identifies the extension, and the
number is written exactly as the extension registry writes it, including leading zeros.

Extension segments obey the same structural rules as core segments. Whether an extension term
is repeatable is decided by its own registry, in the same way as for core terms. Without a
loaded extension registry, a validator:

* MUST check the syntax of extension segments,
* MUST accept an extension segment with or without an index,
* MUST NOT report an unknown-term finding for it, and
* MUST report that the term was not checked, as an `info` finding with the code
  `ESJ-L2-NOT-CHECKED` (section 9.6), so that an unchecked term does not look like a defect.
  Such a result is `INDETERMINATE` and not `VALID`, which is what keeps it from being mistaken
  for a full validation (section 9.5).

The index rule of section 5.3 still decides the shape of the path: for a given extension term
exactly one of `/…/BT-DEX-001` and `/…/BT-DEX-001/0` is conformant, and which one is fixed by
the declared cardinality in the extension's registry. A document that writes both spellings for
one occurrence is not conformant, even though a validator without that registry cannot say so —
that is precisely what the "not checked" report above is for. Writers MUST NOT choose the
spelling freely, and a consumer MUST NOT treat the two spellings as the same address: they are
two different member names, they are ordered differently by section 7.4, and they produce two
different semantic digests.

An extension MUST NOT reuse the identifier of a core term with a different meaning. Core
identifiers never carry a namespace, so the two spaces cannot collide by construction.

**A namespace is permanent.** An extension namespace is a semantic namespace, not a version of
one. Once a term identifier has been published within a namespace, that identifier MUST NOT
change its meaning, its semantic data type or its structural semantics — its parent chains, its
cardinality, its supplementary components — in any later registry of the same namespace. An
incompatible revision MUST take a **new namespace**. A namespace MAY grow: a later registry MAY
add identifiers, and MAY record a further position for a term it already carries.

The rule is what lets a path be read without a registry version beside it. `/BG-DEX-01/0/…`
addresses the same group under every registry that carries the DEX namespace, so the semantic
digest of section 8.2 needs no registry identity inside it, an archived document keeps its
meaning when the extension publishes a later registry, and `imports` (section 10) stays what it
is: the core edition the extension was written against, never a version of the extension's own
terms. A registry file is still versioned, because the set of identifiers grows and a validator
must know which set it holds; what a version never does is change what a published identifier
means.

Without the rule every consumer of an extension term would need the registry version that was
current when the document was written in order to know what the term meant, and the version
would have to travel with the document — in the path, in the envelope, or in the digest. ESJ
puts the requirement on the publisher of the namespace instead, where it costs one decision per
revision rather than one member per document.

**Core terms under an extension group.** An extension registry MAY place core business terms
under one of its own groups instead of restating them with extension identifiers. It declares
this in the group's `reusesTerms` member, which lists the core term identifiers the group
carries directly. The effect on paths is:

1. For each identifier listed in `reusesTerms`, the extension registry records a second parent
   chain for that core term: the chain of the extension group, followed by the term itself.
2. A path that follows that chain is well formed while the extension registry is loaded. The
   chain the core registry records for the same term stays well formed too; the two are
   alternatives, not a replacement.
3. The reused term keeps everything else the core registry says about it: its semantic data
   type, its supplementary components and its own cardinality. The cardinality of the group is
   the extension registry's.
4. Without that extension registry loaded, a validator sees an unknown chain for a known core
   term. It MUST report `ESJ-L2-NOT-CHECKED` for the path, in the same way as for an unchecked
   extension term, rather than reporting the core term as misplaced.

Example: with `model/xrechnung/3.0.2.json` loaded, BG-DEX-01 carries BT-126 to BT-133 and
BG-DEX-07 carries BT-146 to BT-150, so `/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146` is well formed
beside the core chain `/BG-25/0/BG-29/BT-146`.

An extension MUST NOT use `reusesTerms` to change a core term's meaning, type or supplementary
components; it only places the term in an additional position (section 11.1).

**A group inside itself.** A registry MAY list a group's own identifier in that group's
`reusesTerms` member. It thereby declares the group recursive: an instance of the group may
carry further instances of the same group, to any depth. The XRechnung extension registry of
this repository declares BG-DEX-01, the sub invoice line, in this way, because the semantic
model it is derived from gives a sub invoice line the same content model as an invoice line and
a sub invoice line is part of that content.

The consequences are exactly two:

1. Paths nest. `/BG-25/0/BG-DEX-01/0/BG-DEX-01/2/BT-126` addresses the item name of the third
   sub invoice line of the first sub invoice line of the first invoice line. The index rule of
   section 5.3 and the density rule of section 5.4 apply per occurrence as everywhere else, so
   the indices of the inner group count within the enclosing instance and start at zero there.
2. One chain covers every depth. A registry enumerates chains, and no enumeration reaches every
   depth a document may write, so a validator MUST measure such a path against the chain in
   which the recursive group stands once: a path whose group chain becomes a recorded chain
   when consecutive repetitions of a recursive group are written as one is well formed. Every
   occurrence carries the same children in the same positions as the first, which is what makes
   that sound.

Nesting is not free: every level spends two path segments, and section 12.2 bounds a path at
sixteen of them, so the default limits of a reader admit a recursive group nested about six
levels deep. A reader MAY be configured with a higher bound.

That bound reaches the writer as well, and it reaches it as a whole-document matter: a reader
that meets a path past its bound refuses the document rather than the path (section 12.2), so
one level too many costs the invoice and not the level. Nothing about that argument is
peculiar to paths — one value too many and one string too long cost the invoice in the same
way — so the requirement on a writer is stated once, over the whole document, in section 3.3.

### 5.7 Limits

A path is limited in length and in the number of segments; see section 12.2.

---

## 6. The value model

### 6.1 The shape of a value

Every member of `values` is either a JSON **string** or a JSON **object**.

1. A value whose content carries no supplementary component is written as a JSON string.
   The string is the content.
2. A value that carries at least one supplementary component is written as a JSON object
   with the member `value`, which holds the content, and one member per component present.
   The components are `scheme` and `schemeVersion` for an identifier (section 6.6) and
   `mimeCode` and `filename` for a binary object (section 6.7).
3. The object form MUST NOT be used without a component. `{"value": "x"}` is an error,
   reported as `ESJ-L1-VALUE-SHAPE`.
4. `value`, `scheme`, `schemeVersion`, `mimeCode` and `filename` are the whole member set of
   a value object. A value object that carries any other member, or that lacks `value`, is
   an error, reported as `ESJ-L1-VALUE-MEMBER`.
5. Every member of a value object is a JSON string, and every one of them is non-empty. An
   empty string is `ESJ-L1-EMPTY-STRING`; a member that is a JSON object is
   `ESJ-L1-VALUE-SHAPE`, and a member that is a number, a boolean, `null` or an array is
   `ESJ-L1-JSON-TYPE` (section 4.2, rule 5). A business term that is present carries
   content; the content component is mandatory in every semantic data type (EN 16931-1,
   6.5.1), and a term with no content is not written at all.
6. `schemeVersion` MUST NOT be present without `scheme` (section 6.6, rule 4).
7. The canonical member order inside a value object is `value`, `scheme`, `schemeVersion`,
   `mimeCode`, `filename` (section 7.3).

Where more than one of these rules could describe one value object, which code a validator
reports is fixed by the precedence of section 9.6, so that the answer does not depend on the
order the members were written in.

Rules 1 to 3 together are one rule: **the content decides the shape**. A value with no
component has exactly one spelling, the string, and a value with a component has exactly one
spelling, the object. Examples of both:

```json
"/BT-1": "RE-2026-4711",
"/BT-2": "2026-09-19",
"/BG-4/BT-27": "Example GmbH",
"/BG-25/0/BT-131": "84.03",
"/BG-4/BT-29/0": { "value": "0088123456785", "scheme": "0088" },
"/BG-25/0/BG-31/BT-158/0": { "value": "9873242", "scheme": "TST", "schemeVersion": "19.05.01" },
"/BG-24/0/BT-125": { "value": "JVBERi0xLjQK", "mimeCode": "application/pdf", "filename": "note.pdf" }
```

Rule 3 is the only rule of this section that forbids something a reader could otherwise accept
without harm, and it earns its place in the canonical form. Without it one content would have
two spellings, `"RE-2026-4711"` and `{"value": "RE-2026-4711"}`; two writers would choose
differently, and two documents with the same content would have two canonical byte sequences
and two digests. The rule costs a writer one condition — write the object when a component is
present — and it is the same condition a reader applies to decide which shape it is reading.

A value carries **no type token**. The semantic data type of a business term is a fact of the
model, the registry records it once (section 6.2), and writing it into every value again
would be a second place for one fact: two places can disagree, and a document in which they
disagree has no defined meaning. The registry is the one place.

### 6.2 The registry decides what the content must be

EN 16931-1, clause 6.5 defines the semantic data types. The registry records one of them per
business term, in the `datatype` member, and that record decides what the content of a value
at that term must look like and which supplementary components it may carry. The table holds
every type a registry of either edition names: the 2017 edition defines ten, and the 2026
edition adds `Time` (its clause 6.5.10).

| Registry `datatype` | Content | Components |
|---|---|---|
| `Text` | any non-empty string (6.8) | — |
| `Identifier` | any non-empty string | `scheme`, `schemeVersion`, where the registry lists them (6.6) |
| `Code` | any non-empty string | — |
| `Date` | the date grammar (6.5) | — |
| `Time` | the time grammar (6.5) | — |
| `Amount` | the decimal grammar (6.4) | — |
| `UnitPriceAmount` | the decimal grammar (6.4) | — |
| `Quantity` | the decimal grammar (6.4) | — |
| `Percentage` | the decimal grammar (6.4) | — |
| `DocumentReference` | any non-empty string | — |
| `BinaryObject` | canonical base64 (6.7) | `mimeCode` and `filename`, both REQUIRED |

These are layer **L2** checks. They need the registry, and an implementation without one
cannot make them (sections 3.2 and 9.2). The division is clean: **L1 decides the shape of a
value, L2 decides whether its content fits the term it sits at.** A content that does not
satisfy the grammar its datatype requires is reported as `ESJ-L2-DECIMAL`, `ESJ-L2-DATE`,
`ESJ-L2-TIME` or `ESJ-L2-BASE64`; a component the term does not allow is
`ESJ-L2-COMPONENT-NOT-ALLOWED`, and one the registry declares mandatory and the document omits
is `ESJ-L2-COMPONENT-MISSING` (section 9.6).

The grammars themselves are unchanged by where they are checked, and they are normative:
sections 6.4, 6.5 and 6.7 define them, and a document that violates one is not conformant
(section 3.1, requirement 6).

`schema/esj.schema.json` therefore constrains the shape and nothing about the content: it
knows no business terms. One generated schema per edition —
`schema/esj-en16931-2017.schema.json` and `schema/esj-en16931-2026.schema.json` — carries the
per-term typing for JSON Schema users: for each term path, the pattern the content must match,
or the object shape with the components that term allows. A JSON Schema user who wants typing
validates against the generated schema of the edition the document names; one who only wants
the format validates against `schema/esj.schema.json`.

The generated schema is **scoped to the terms of the one registry it was generated from**. Its
path patterns are that registry's paths and nothing else, so it refuses any document that
carries an extension term (section 5.6) — including a document that is conformant, because
section 5.6 forbids reporting an unknown term for an extension segment. A document using an
extension is therefore validated against `schema/esj.schema.json` plus the registries it
declares, the way a validator reaches L2 (sections 9.2 and 11.1), and not against the generated
schema. A generated schema that covered an extension as well would have to be generated from
the combined registries.

Because it is scoped to one edition, a generated schema MUST also pin `semanticModel` to that
registry's edition string. The format schema deliberately does not: it carries the edition
grammar of section 4.4 as a pattern, because L1 asks only that the value be a well-formed
edition identifier and a reader accepts a document of an edition it has no registry for
(sections 3.2 and 4.4). The two schemas therefore answer two questions — *is this an ESJ
document* and *is this an ESJ document of this edition, with these terms* — and only the second
may refuse a document over the edition it names.

### 6.3 What a value does not carry

Three omissions are deliberate and follow the standard:

* **An amount carries no currency.** The currency is a business term of its own: BT-5 for the
  invoice, BT-6 for the VAT accounting currency of BT-111 (EN 16931-1, 6.5.2 and 6.5.3).
* **A quantity carries no unit of measure.** The unit is BT-130 for the invoiced quantity and
  BT-150 for the item price base quantity (EN 16931-1, 6.5.4).
* **A code carries no code list identifier, version or agency.** Which list applies is fixed
  per business term by the semantic model (EN 16931-1, 6.5.8); the registry records its name
  in the `codeList` member for documentation, and membership in it is a business rule
  (section 10). The value is the code exactly as the list spells it.

`DocumentReference` is derived from Identifier but has only content in EN 16931 (6.5.7), so
the registry lists no component for a term of that type and such a value is always a string.

### 6.4 Decimals

A term whose registry datatype is `Amount`, `UnitPriceAmount`, `Quantity` or `Percentage`
carries a decimal number as its content, in exactly this form:

```abnf
decimal = [ "-" ] int [ "." frac ]
int     = "0" / ( nonzero *DIGIT )
frac    = *DIGIT nonzero
```

with `nonzero` and `DIGIT` as in section 5.1, and the additional rule that the sign MUST NOT
be present when the value is zero. (ABNF cannot express that condition; the regular expression
in `schema/esj-en16931-2017.schema.json` encodes it as an alternation.)

Therefore:

* no exponent: `1E2` and `1e-3` are errors;
* no leading plus sign;
* no leading zeros in the integer part: `007` is an error, `0.5` is correct;
* no trailing zeros in the fraction part: `100.00` is an error, `100` is correct;
* no empty fraction: `100.` is an error;
* no negative zero: `-0` and `-0.00` are errors, the value is written `0`;
* no thousands separators, no spaces, no comma as a decimal mark.

**`100.00` is never in a document, and a writer never puts it there.** Those are two rules,
and they are stated separately because they are about two different moments:

* A reader, a validator or a canonicalizer that meets `100.00` at a decimal term MUST refuse
  the document. `100.00` is not the canonical spelling of that decimal, and no spelling but
  the canonical one is conformant. The finding is `ESJ-L2-DECIMAL`, because deciding that the
  term is a decimal term needs the registry (section 6.2). An implementation that has no
  registry cannot make the check and MUST NOT make the value canonical instead: the content
  passes through it unchanged (section 3.4).
* A writer that is handed the exact decimal value `100.00` by a model outside ESJ — a
  `BigDecimal` with a scale of two, a `NUMERIC(12,2)` column, an XML element reading
  `100.00` — serializes it as `100`. Trailing fraction zeros carry no numeric information, so
  the two are the same number, and the canonical form has exactly one spelling for it.

Nothing between the two repairs anything. *Canonicalization* in this specification is about
the serialization of a document and never about the content of a value: the canonicalizer
orders members, escapes strings and removes whitespace (section 7), and a document whose
decimals are spelled some other way is refused rather than corrected. A pipeline that believes
otherwise hides the defect at its first hop and cannot say afterwards which party wrote the
number wrong.

Nothing is rounded anywhere: `42.0150` and `42.015` are the same number and `42.015` is its
canonical spelling, but `42.015` never becomes `42.02`, not even where a business rule would
allow only two fraction digits.

A decimal string MUST be at most **64 characters** long, counting the sign and the decimal
point. That is room for a signed number with more than sixty significant digits, which is far
beyond anything an invoice carries and beyond what most exact decimal types accept without
allocating. The bound belongs to the decimal grammar, not to the reader limits of section
12.2: it is fixed rather than configurable, it is decided by the bytes, and a decimal that
exceeds it is reported as `ESJ-L2-DECIMAL`. It exists so that a reader can size its parse
buffer before it parses: without it the general string limit of section 12.2 would allow a
one-megabyte number. The same bound applies to the canonical form of a JSON number inside
`extensions`, where it is an L1 check because no registry is involved (section 7.6).

A reader MUST parse decimals into an exact decimal type (for example `BigDecimal` in Java,
`decimal.Decimal` in Python, `numeric` in SQL). It MUST NOT parse them into binary floating
point. A writer produces the canonical form from an exact decimal by removing trailing
fraction zeros and writing the result in plain notation, then replacing a negative zero by
`0`.

**How many fraction digits a term allows is a fact of the registry of its edition**, and the
layers L1, L2 and L3 check none of them. The fact differs by edition and by term. In the 2017
edition the cap of two fraction digits is a property of the type Amount (6.5.2), Table 26 of
6.5.12 repeats it per business term, and Unit Price Amount, Quantity and Percentage carry no
cap at all: the corrigendum AC:2020 adds to 6.5.3, 6.5.4 and 6.5.5 that those three types are
floating with no limit. In the 2026 edition the type carries no cap and Table 28 of 6.5.13
gives the limit per term: for an amount the minor unit of its currency under ISO 4217, for a
unit price that minor unit plus two, and a constant for the terms where the edition fixes one.
The registry records the fact per term — `maxDecimals` where the edition fixes a number,
`maxDecimalsRule` where it names a rule (section 10) — and both members are documentation.

ESJ treats the bound as a **business rule** and not as a structural one, even where an edition
attaches it to the type. The check belongs with the arithmetic rules it serves (BR-DEC-* in
the artefacts of the 2017 edition), and those are a separate layer, decided by a rule pack
rather than by ESJ conformance (section 9.4). A rule pack is written for one edition and reads
the bound of that edition; a document is therefore never structurally invalid in ESJ for the
number of fraction digits it carries, and a changed bound changes no ESJ layer.

Rounding is a question of the model and of the rules that check it, not of the serialization:
an edition states how a total is rounded and what tolerance a calculation rule allows, and a
rule pack carries those facts for the edition it is written for (section 9.4). ESJ writes down
what the sender computed.

### 6.5 Dates and times

**Dates.** The content of a term whose registry datatype is `Date` is a calendar date with
no time of day and no time zone, written as:

```abnf
date  = year "-" month "-" day
year  = nonzero 3DIGIT
month = ( "0" nonzero ) / ( "1" %x30-32 )
day   = ( "0" nonzero ) / ( %x31-32 DIGIT ) / ( "3" %x30-31 )
```

The value MUST additionally be a date that exists in the proleptic Gregorian calendar:
`2026-02-30` matches the grammar and MUST be rejected; `2024-02-29` is valid, `2023-02-29` is
not. The year is written with exactly four digits and MUST NOT begin with a zero, so the
representable range is `1000-01-01` to `9999-12-31`.

The lower bound is part of the grammar and not a remark beside it, because the year is where
implementations would otherwise disagree: one date library accepts `0000-02-29` as a leap
day, another refuses the year zero outright, and the same document would then be conformant
for one validator and not for the other. No invoice carries a year below 1000, so the bound
costs nothing and buys agreement.

This is the ISO 8601 complete calendar date representation, which is what EN 16931-1, 6.5.9
requires of the semantic type. The standard leaves the syntactic format attribute to the
binding; ESJ fixes one format and does not carry a format attribute.

**Times.** The content of a term whose registry datatype is `Time` is a time of day together
with the offset from UTC it is stated in, written as:

```abnf
time         = hour ":" minute ":" second offset
hour         = ( ( "0" / "1" ) DIGIT ) / ( "2" %x30-33 )
minute       = %x30-35 DIGIT
second       = %x30-35 DIGIT
offset       = %s"Z" / ( ( "+" / "-" ) offset-value )
offset-value = ( ( ( "0" DIGIT ) / ( "1" %x30-33 ) ) ":" minute ) / "14:00"
```

The hour, the minute and the second are always two digits and all three are always present,
and the offset lies between `-14:00` and `+14:00` — the range of civil offsets, bounded by the
rule XML Schema uses for a time zone, so that a binding to a syntax that types a time as
`xs:time` neither widens nor narrows what ESJ admits. One further rule is not in the grammar:
the grammar admits `+00:00` and `-00:00`, and rule 2 below excludes them. Rules 1 and 3 are
stated because the grammar is deliberately narrower than ISO 8601 and an implementer should
read why, not because a checker generated from the ABNF would miss them.

1. The offset is REQUIRED — `offset` is not an optional element of the rule. EN 16931-1:2026,
   6.5.10 requires the representation to carry timezone information, and a time without one is
   a different value: `09:30:00` says when it was somewhere, not when it was.
2. UTC is written `Z`, in upper case. `+00:00` is an error, and so is `-00:00`. ESJ has one spelling per
   value, because a canonicalizer orders and escapes a document and never rewrites the content
   of a value (section 3.4) — a second spelling would be a second digest for one time. This is
   the rule the grammar does not carry.
3. There are no fractional seconds, no `24:00:00` and no leap second `:60` — `hour` stops at
   23, `second` at 59 and no production admits a fraction. ISO 8601-1:2019 admits all three;
   each is either a second spelling of a value that already has one or a value two date
   libraries read differently, and an invoice needs none of them. `23:59:59` is the last time
   of a day, and `00:00:00` of the next day is the instant after it.

This is the ISO 8601 time of day EN 16931-1:2026, 6.5.10 requires of the semantic type,
narrowed to one spelling per value. As with a date, the standard leaves the syntactic format
attribute to the binding, ESJ fixes one format, and no format attribute is carried.

The type is new in the 2026 edition and no term of the 2017 registry has it. A content outside
the grammar is `ESJ-L2-TIME` and is found at layer **L2**, like a date: deciding that a term
is a time term needs the registry (sections 6.2 and 9.6).

### 6.6 Identifiers

A term whose registry datatype is `Identifier` MAY carry `scheme` and `schemeVersion`. Both
are supplementary components of the Identifier type in EN 16931-1, 6.5.6, and both are
conditional: whether a scheme may or must be used is decided **per business term**, not by the
type. A value that carries one of them is written in the object form; one that carries
neither is written as a string (section 6.1).

1. `scheme` MUST NOT be present unless the registry lists a scheme component for that term.
2. `schemeVersion` MUST NOT be present unless the registry lists a scheme version component for
   that term. In the core model of either edition exactly one term has one: BT-158.
3. Where the registry declares a component as mandatory (minimum cardinality 1), it MUST be
   present. Which terms those are is a fact of the registry and differs between editions: the
   2017 registry declares it for BT-34, BT-49, BT-157 and BT-158, and the 2026 registry for
   those four and for eight more.
4. `schemeVersion` MUST NOT be present unless `scheme` is present in the same value object.
   This is a layer **L1** check, and `schema/esj.schema.json` expresses it as a
   `dependentRequired`. It does not depend on the registry: a scheme version identifies the
   version of a scheme, so it is meaningless without one, and no term in either registry
   declares a scheme version component without a scheme component. A value that carries only
   `schemeVersion` is therefore always an error, whichever term it sits at, and there is no
   reason to wait for L2 to say so. The code is `ESJ-L1-VALUE-MEMBER` (section 9.6).

Rules 1 to 3 are layer L2 checks; rule 4 is layer L1.

### 6.7 Binary objects

The registry datatype `BinaryObject` is carried by exactly one core term, BT-125 (Attached
document). Both supplementary components are mandatory in the standard (EN 16931-1, 6.5.11)
and therefore in ESJ, so such a value is always written in the object form (section 6.1) and
always carries all three members:

* `mimeCode` — the media type of the attachment, for example `application/pdf`;
* `filename` — the file name of the attachment.

`value` is the attachment encoded as base64 with the standard alphabet of [RFC4648],
section 4:

```abnf
b64       = *( 4b64char ) ( 4b64char / b64pad )
b64pad    = ( 2b64char b64tail2 "=" ) / ( b64char b64tail4 "==" )
b64char   = ALPHA / DIGIT / "+" / "/"
b64tail2  = "A" / "E" / "I" / "M" / "Q" / "U" / "Y" / "c" /
            "g" / "k" / "o" / "s" / "w" / "0" / "4" / "8"
b64tail4  = "A" / "Q" / "g" / "w"
```

Padding is REQUIRED. Line breaks, whitespace and the URL-safe alphabet MUST NOT be used.

The encoding MUST be **canonical** in the sense of [RFC4648], section 3.5: the bits that pad
the last encoding quantum MUST be zero. That is what `b64tail2` and `b64tail4` express. A
three-character final group carries two pad bits, so its last character is one of sixteen; a
two-character final group carries four pad bits, so its last character is one of four.
`QUJDRQ==` is canonical; `QUJDRR==` decodes to the same bytes and MUST be rejected, as
`ESJ-L2-BASE64` (section 6.2).

Without that rule one byte sequence would have several encodings, and a writer that re-encodes
from the decoded bytes and one that copies the string would produce different documents and
different digests for the same attachment. A canonicalizer never re-encodes (section 7.2,
rule 5), so the rule is what keeps the two writers in agreement.

The encoded value is limited in size (section 12.2).

A reader MUST NOT interpret, render or open the decoded bytes, and MUST NOT trust `mimeCode`
or `filename`; see section 12.5.

### 6.8 Text and line endings

The content of a term whose registry datatype is `Text` is any non-empty sequence of Unicode
code points. Line breaks may occur (EN 16931-1, 6.5.10) and MUST be preserved.

The only transformation ESJ applies to string content is line ending normalization: the
sequences CR LF (U+000D U+000A) and a lone CR MUST be normalized to a single LF (U+000A) when
a document is read or canonicalized. In the JSON text an LF inside a string is written as the
escape `\n` (section 7.5).

**Scope of the normalization.** It applies to every string inside `values`: to a value
written as a string, to the `value` member of a value object, and to the supplementary
components `scheme`, `schemeVersion`, `mimeCode` and `filename` alike. It is stated here,
under Text, because Text is the only semantic data type whose content normally contains line
breaks, but it is not restricted to it — a rule that depended on the datatype would need the
registry, and a reader has none (section 3.2). Member names are not affected: a path cannot contain CR or
LF. Strings inside `extensions` are **not** normalized; that subtree is passed through
unchanged apart from the member sorting and serialization of section 7.6, because ESJ does not
know what the owner means by a string there.

The string length limit of section 12.2 is measured on the **normalized** value, after CR LF
has become LF. A reader that streams MAY check the raw length first, since normalization never
lengthens a string.

Nothing else is touched: no trimming of leading or trailing spaces, no collapsing of runs of
whitespace, no Unicode normalization, no case folding. Two values that differ by a trailing
space are two different values.

Strings MUST be valid Unicode. The rule holds for **every** string of the document and not only
for those some other section constrains: every string inside `values`, every member name, every
string inside `extensions`, `source.syntax` and `source.sha256`, and `format`, `version` and
`semanticModel` alike. A lone surrogate MUST cause a reader or canonicalizer to reject the
document, because a document containing one has no stable byte representation and would break
digests. A reader that accepted one would also break the promise of section 3.4, that every
document a reader calls well formed is one a canonicalizer can turn into bytes.

---

## 7. Canonical form

### 7.1 Purpose

The canonical form gives a document content exactly one byte representation. Two documents with
the same content have the same canonical bytes, and therefore the same digests, regardless of
which implementation wrote them, in which order the members were produced, and with which
whitespace.

Everything in this section is a requirement on a conformant canonicalizer (section 3.4). A
document does not have to be stored in canonical form to be conformant.

### 7.2 Encoding and whitespace

1. The output is encoded in UTF-8, without a byte order mark.
2. There is no insignificant whitespace: no space after `:` or `,`, no indentation, no line
   breaks between members.
3. There is no trailing newline. The canonical form ends with the `}` that closes the document
   object.
4. String content is normalized as described in section 6.8 before it is escaped: CR LF and a
   lone CR become LF. This applies to every string inside `values`, including the supplementary
   components `scheme`, `schemeVersion`, `mimeCode` and `filename`; it does not apply to member
   names, which cannot contain those characters, and it does not apply inside `extensions`
   (section 7.6). A reader has already applied it (section 3.2, rule 4), so for a document that
   came through a conformant reader this step changes nothing.
5. The content of a value is copied as it stands. The canonicalizer has no registry, so it
   does not know which content is a decimal, a date or a base64 string, and it rewrites none
   of them (section 3.4). An implementation that holds an attachment as bytes rather than as
   a string re-encodes it with the canonical base64 of section 6.7, which reproduces the
   string the document carried, because that encoding is unique.

### 7.3 Member order

1. Top level: `format`, `version`, `semanticModel`, `values`, `extensions`, `source`. Absent
   members are omitted; present members keep this relative order.
2. `values`: members in canonical path order (section 7.4).
3. Value objects: `value`, `scheme`, `schemeVersion`, `mimeCode`, `filename`, omitting absent
   members. A value written as a JSON string has no members to order.
4. `source`: `syntax`, `sha256`, omitting absent members.
5. `extensions`: owner tokens sorted by Unicode code point (section 7.6).

### 7.4 Canonical path order

The member names of `values` are ordered by comparing their **segment sequences**, not their
strings. Two paths are compared segment by segment from the left. The first position at which
they differ decides. If one path's segments are a prefix of the other's, the shorter path comes
first.

At a given position, the comparison of two segments is:

1. If both are **term segments**, compare in this order and stop at the first difference:
   1. kind: `BT` before `BG`;
   2. namespace: a core segment (no namespace) before any extension segment; two extension
      namespaces by Unicode code point;
   3. number: numerically, as defined below;
   4. if the numbers are numerically equal but spelled differently (leading zeros in an
      extension number), by the literal string, code point by code point.
2. If both are **index segments**, compare numerically, as defined below.
3. If one is a term segment and the other an index segment, the term segment comes first. In a
   document that is conformant against every registry its paths refer to, this case does not
   arise, because whether an index follows a term is fixed by that term's cardinality. It can
   arise when a registry is not available and the mistake therefore goes undetected
   (section 5.6). The rule exists so that the order is total for any input.

**Numerical comparison of digit strings.** The grammar of section 5.1 puts no bound on the
number of digits in an index or in a term number, and the path length limit of section 12.2
leaves room for far more digits than any integer type holds. A canonicalizer MUST therefore
compare digit strings by the following procedure, which gives the numerical order exactly and
needs no arbitrary-precision arithmetic:

1. Remove leading zeros from each digit string; if nothing is left, the result is `0`. (Index
   segments and core term numbers never carry leading zeros; extension numbers may.)
2. The string with fewer digits is the smaller number.
3. If both have the same number of digits, compare them byte by byte; the first differing byte
   decides.

Converting a digit string to a machine integer or to a floating point number before comparing
is not conformant. Two numbers that exceed the receiving type would then compare equal, or the
conversion would fail, and two implementations would disagree about member order and therefore
about both digests.

This order is defined without the registry: a canonicalizer needs nothing but the paths
themselves. That property is what makes the canonical form reproducible in an environment that
has no registry loaded.

*Informative.* The resulting order is the order of EN 16931-1, Table 2 at every level, with one
systematic exception: the terms added by A1:2019 for the third address line (BT-162 to BT-165)
sort after the other terms of their address group instead of directly after the second address
line. The registry's `order` member carries the Table 2 position for tools that want to present
values in the order of the standard; the canonical form never depends on it.

Worked example — these six paths in canonical order:

```
/BT-1
/BT-2
/BG-4/BT-27
/BG-4/BG-5/BT-40
/BG-25/0/BT-129
/BG-25/1/BT-129
```

`/BT-1` and `/BT-2` precede everything that starts with a `BG` segment (rule 1.1); `/BG-4/…`
precedes `/BG-25/…` because 4 < 25 numerically (rule 1.3), not because `4` < `2` as text; and
inside BG-4, `BT-27` precedes the group `BG-5` (rule 1.1 again).

### 7.5 String escaping

Every JSON string — member names as well as values — is escaped exactly as in [RFC8785],
section 3.2.2.2:

1. U+0008, U+0009, U+000A, U+000C and U+000D are written as the two-character escapes `\b`,
   `\t`, `\n`, `\f` and `\r`.
2. Every other code point in U+0000 to U+001F is written as `\u00xx` with **lowercase**
   hexadecimal digits.
3. U+0022 is written `\"` and U+005C is written `\\`.
4. Every other code point is written literally, as its UTF-8 bytes. In particular the solidus
   `/` is **not** escaped, and no code point above U+007F is escaped.

Example. A text value containing the characters `a`, `"`, `b`, `\`, `c`, LF, `d`, TAB, `e`,
U+0001, `f`, `€` is serialized as:

```
{"/BG-4/BT-27":"a\"b\\c\nd\te\u0001f€"}
```

### 7.6 Content of `extensions`

Inside `extensions` the JSON is free, so the canonicalizer follows [RFC8785] there, with the
two deviations stated after the rules:

1. Object members are sorted recursively; array element order is preserved.
2. A JSON number is canonicalized by applying the decimal grammar of section 6.4 to its
   **lexical** form. The canonicalizer takes the number as the document writes it — sign,
   integer digits, fraction digits and exponent — shifts the decimal point by the exponent,
   removes the leading zeros of the integer part and the trailing zeros of the fraction part,
   and writes the result in plain notation: no exponent, no plus sign, no empty fraction, and a
   zero without a sign. The output matches the `decimal` production of section 6.4. So:
   * `1e21` is written `1000000000000000000000`, and `1e-6` and `1e-7` are written `0.000001`
     and `0.0000001`;
   * `-0` and `-0.0` are both written `0`;
   * `12345678901234567890` and `1.0000000000000001` are already in that form and are written
     unchanged, digit for digit.

   This is pure string processing. No numeric type takes part in it, nothing is rounded, and no
   digit is lost. It is the same rule that governs decimals inside `values`; the difference is
   only that there the decimal is a JSON string and here it is a JSON number, so here the
   number's own spelling is the input.

   A reader and a canonicalizer MUST therefore have the lexical form of the number available. A
   parser that turns `1.0000000000000001` into a binary floating point value before the
   canonicalizer sees it has already changed the document; a streaming parser that hands over
   the raw token, or an exact decimal type, has not. This is a requirement on the parser
   configuration, like duplicate detection (section 12.3).

   The 64-character bound of section 6.4 applies to the canonical form. A JSON number whose
   canonical decimal form would be longer than 64 characters MUST be rejected at layer L1
   (`ESJ-L1-EXT-NUMBER`, section 9.6). It is an L1 check here, and an L2 one inside `values`,
   for the one reason that divides the two layers: here no registry is needed to know that
   the thing being measured is a number. `1e400` is such a number: it is legal JSON, and its
   canonical form is a 401-character digit string. The bound serves the same purpose as in
   section 6.4 — a reader can size its buffer before it reads — and it is the only class of
   JSON number that ESJ forbids inside `extensions`. The code is a different one from the code
   for a number inside `values`, where no number is allowed at all.
3. `true`, `false` and `null` are written as such.
4. Strings are escaped as in section 7.5.

**First deviation from [RFC8785]: numbers.** JCS prescribes, in its section 3.2.2.3, the
`Number::toString` algorithm of [ECMA-262] applied to the IEEE 754 double the number parses to.
ESJ deliberately does not do that. Under that algorithm `1e21` becomes `1e+21`,
`12345678901234567890` becomes `12345678901234567000` and `1.0000000000000001` becomes `1`: the
canonical form of a document, and with it both digests, would depend on a binary floating point
conversion, and two of those three numbers would lose content the sender wrote down. A format
whose central rule is that `100.00` must never pass through a double cannot make an exception
for the one subtree whose content ESJ does not otherwise touch. With the lexical rule above,
**ESJ nowhere in this specification converts a number to binary floating point** — not in
`values`, not in `extensions`, not in the canonicalizer, not in the digests. The price is that
a JCS library cannot be used unchanged for this step, and that is said here rather than
discovered by a mismatching digest.

**Second deviation from [RFC8785]: member order.** JCS sorts member names by their UTF-16 code
units. ESJ sorts them by **Unicode code point**, at every level inside `extensions`. The two
orders differ only when member names contain code points above U+FFFF next to code points in
U+E000 to U+FFFF, which can happen for the member names an owner chooses inside its own
subtree, where JSON allows any string.

The reason is that the canonical form is a byte sequence. Code point order is exactly the
lexicographic order of the UTF-8 encodings of those code points: UTF-8 was designed so that
comparing encoded bytes gives the same answer as comparing the code points they encode. An
implementation can therefore sort the encoded member names bytewise — a `memcmp` over the UTF-8
bytes it is about to write — and it has the required order, without decoding anything and
without any reasoning about surrogate pairs. UTF-16 code unit order is the one of the two that
does not agree with the bytes the canonical form is made of, because it places every
supplementary code point below U+E000 to U+FFFF. Implementations MUST use code point order;
they MUST NOT hand this step to a JCS library without checking how that library sorts, because
a conformant JCS library sorts by UTF-16 code units by definition.

The owner tokens themselves are sorted by the same rule, but for them the question does not
arise: an owner token is ASCII by section 4.6, so code point order, UTF-16 order and byte order
are the same order.

The member names of `values` are **not** sorted lexicographically; they use the path order of
section 7.4. This is the third, and last, difference from JCS.

Because a number inside `extensions` is canonicalized lexically, it survives ESJ exactly:
`1.0000000000000001` is still `1.0000000000000001` in the canonical form, and the digests are
taken over those digits. A number there is nevertheless the weaker place for an exact decimal,
for a reason outside this specification: most JSON tooling turns a number into a double the
moment it reads it, so a value ESJ preserves may still be destroyed by the next program in the
chain. A decimal that must survive a whole pipeline belongs in `values`, as a string, or in
`extensions` as a string.

### 7.7 Pretty form

The pretty form is the same content with layout added, for human readers and for storage in a
version control system:

1. the member order of section 7.3, including the path order of section 7.4;
2. two spaces of indentation per level;
3. one member per line, and one array element per line;
4. exactly one space after the `:` that separates a member name from its value, and nothing
   between a `,` and the line break that follows it;
5. an object with no members written as `{}` on one line, and an array with no elements as
   `[]`; `values` may be empty at layer L1 (section 4.5), and `extensions` may contain an empty
   object at any depth;
6. LF line endings;
7. UTF-8, and the escaping of section 7.5, so that non-ASCII characters appear literally;
8. inside `extensions`, the recursive member sorting of section 7.6, rule 1 — the pretty form
   orders those members exactly as the canonical form does.

A trailing newline at the end of the file is allowed and is RECOMMENDED for files stored in a
repository. The pretty form is not used for digests. The examples in `examples/` are stored in
pretty form.

Rules 4 and 5 are there because the pretty form is a serialization two implementations are
expected to agree on, not merely a display convenience: section 3.3 lets a conformant writer
emit it, and a repository that keeps pretty files under version control gets useful diffs only
if every writer lays them out the same way. The digests are unaffected either way.

Rule 8 exists for the same reason. Without it the member order inside `extensions` would be the
writer's choice, and the one subtree in which an owner is free to invent member names would be
the one subtree whose pretty file two writers lay out differently.

One thing the pretty form does not fix is the **spelling of a JSON number inside
`extensions`**. Section 7.6, rule 2 canonicalizes that spelling, so `1e21` and
`1000000000000000000000` are the same content and have the same canonical bytes and digests; a
pretty writer MAY keep the spelling it read instead of rewriting it. Two pretty files for one
document content can therefore differ in exactly that, and in nothing else. A writer that wants
a pretty file reproducible from the content alone writes its numbers in the canonical form of
section 7.6, rule 2. `examples/extended.esj.json` deliberately does not: it keeps the input
spellings, because its purpose is to be canonicalized (Appendix C).

Canonicalizing a pretty document and canonicalizing the same document in any other layout MUST
produce the same bytes.

---

## 8. Digests

### 8.1 General

Both digests are SHA-256, written as 64 lowercase hexadecimal digits. Both are computed over
canonical bytes (section 7); computing them over any other serialization is meaningless.

Both identify **content**, not a file. Two ESJ files that carry the same content in two
layouts — one pretty, one canonical, one with the members of `extensions` written in another
order — have the same two digests, because all of them canonicalize to the same bytes. That
is what the canonical form is for.

### 8.2 Semantic digest

The **semantic digest** is SHA-256 over the canonical serialization of this object:

```
{"semanticModel": <the document's semanticModel>, "values": <the document's values>}
```

with exactly those two members, in that order, and with the members of `values` in canonical
path order (section 7.4). Everything else of section 7 applies: UTF-8, no insignificant
whitespace, the member order of section 7.3 inside each value object, the escaping of
section 7.5.

It identifies the semantic content of the invoice, and nothing else. It does not change when:

* the format version or the `source` member changes,
* the `extensions` envelope member is added, changed or removed,
* the document is reformatted.

The second of those is about the `extensions` envelope member and about nothing else. An
extension **term** lives in `values`, keyed by a namespaced path (section 5.6), and adding,
changing or removing one **does** change the semantic digest — as it must, because such a term
carries content of the invoice. The two halves of an extension therefore fall on two sides of
this digest: its terms are part of the semantic content, its `extensions` subtree is not. That
is also why section 4.6, rule 5 asks an extension that defines terms to put them in `values`:
data addressed by a term is covered by the digest that answers *did the invoice change*, and
data in `extensions` is not.

It does change when the edition changes, and that is why `semanticModel` is inside it. A path
is an address relative to an edition, and a later edition may number its terms differently.
Without the edition in the digest, two documents carrying the same strings under two editions
would collide — one digest for two different sets of business terms — and a deduplication key
or an equivalence test built on it would be silently wrong for exactly the documents that
straddle an edition change. ESJ 0.1 defines two editions (section 4.4), so the same invoice
content under both has two semantic digests. That is correct and deliberate: they are two
statements about two models, and a migration of a document from one edition to the other —
which lies outside this specification — changes the digest by design.

### 8.3 Document digest

The **document digest** is SHA-256 over the whole canonical document, including `format`,
`version`, `semanticModel`, `extensions` and `source`.

It identifies the canonical content of the document: the semantic content together with
everything the envelope says about it. It does not identify a file (section 8.1).

### 8.4 Why two

They answer two different questions.

*Did the invoice change?* is a question about the semantic content. Two extractions of the
same invoice, one from UBL and one from CII, differ in `source` and therefore have different
document digests, but if the extraction is correct they have the **same semantic digest**.
That makes the semantic digest a practical test for converter equivalence, for detecting
whether a re-sent invoice actually differs, and as a deduplication key.

*Did anything around the invoice change?* — the format version, the `extensions` member, the
recorded provenance — is a question about the whole document, and the document digest answers
it. An extension **term** is not in that list: it lives in `values` and is part of the semantic
content, so it moves both digests (section 8.2). Two documents with the same semantic digest
and different document digests carry the same invoice with different metadata around it;
`examples/minimal.esj.json` and `examples/extended.esj.json` are that pair.

Neither of them answers *is this the same file I received*. That is a question about bytes,
and it lies outside ESJ: a file that differs from another only in indentation is the same
document by every definition this specification gives, and a receiver that has to prove which
bytes arrived keeps those bytes, or a digest of them, in the transport layer where they came
from. The one digest in ESJ that is about bytes is `source.sha256`, and the bytes it is about
are those of the **source** document the content was extracted from — the UBL or CII instance
— never those of the ESJ file carrying it (section 4.7).

A digest is not a signature: it protects against accidental change, not against a forger who
can also change the digest. Signing is out of scope for ESJ 0.1.

---

## 9. Validation

Validation is layered so that a caller can ask a precise question and get a precise answer.
The answer has three states, because a check can end in three ways: the document satisfies the
layers, it fails one of them, or something could not be evaluated. Section 9.5 defines the
states and the result that carries them.

### 9.1 L1 — format

L1 needs no registry. It checks:

* the document is well-formed JSON in UTF-8, with no BOM and no duplicate member names;
* the envelope members, their types and the fixed values of section 4, and that
  `semanticModel` satisfies the edition grammar of section 4.4 — not that a registry for that
  edition exists, which is an L2 question (sections 4.4 and 9.2);
* the path grammar of section 5.1, and nothing else about a path: which parents a term has and
  whether it carries an index are facts of the registry and belong to L2 (sections 5.2, 5.3
  and 9.2), and whether the indices under one parent are dense is a property of the whole
  document and belongs to L3 (sections 5.4 and 9.3);
* the shape of every value (section 6.1): a JSON string, or an object that carries `value`,
  at least one supplementary component, no member outside the set of five, and no
  `schemeVersion` without `scheme`;
* that every string inside `values` is non-empty, and that every string of the document is
  free of lone surrogates: every string inside `values`, every member name, every string
  inside `extensions`, the members of `source`, and `format`, `version` and `semanticModel`
  alike (section 6.8);
* the absence of JSON numbers, booleans, `null`, arrays and objects nested deeper than a
  value object inside `values`, and the 64-character bound on the canonical decimal form of
  a number inside `extensions` (section 7.6).

These are the checks of requirements 1 to 5 of section 3.1, in the order a reader meets them.
Every one of them is decided by the bytes alone: that is the whole of the criterion, and it is
why the path grammar is here while the rest of section 5 is not. What L1 does **not** check is
the content of a value against the term it sits at: whether `2026-02-30` is a date, whether
`100.00` is a canonical decimal and whether `QUJDRR==` is canonical base64 are questions about
the semantic data type of the term, the registry answers them, and they belong to L2
(sections 6.2 and 9.2).

A reader also enforces the limits of section 12.2 while it parses and reports
`ESJ-L1-LIMIT`. The code carries an L1 prefix because that is where a reader meets it, but a
limit is not part of conformance (section 3.1). A reader MAY abort with an exception at that
point (section 9.5); a validator handed an already-parsed document checks those limits that
are still measurable on it and reports the finding like any other.

`schema/esj.schema.json` is the machine-readable form of most of L1, but not of all of it. The
table below is the complete list of L1 checks the schema does **not** perform, including the
limits of section 12.2 it cannot express. A conformant reader MUST perform every one of them
itself:

| Check | Why the schema cannot do it |
|---|---|
| duplicate member names | a JSON parser has already collapsed them before the schema sees the document |
| the encoding, and a leading byte order mark | the schema sees a parsed document, not bytes |
| a lone surrogate in a string | the schema has no construct for it |
| a number inside `extensions` whose canonical decimal form exceeds 64 characters | `maxLength` applies to strings, and 2020-12 has no keyword for the spelling of a number; a validator has also already parsed `1e400` into whatever its language does with it |
| segments per path | the pattern bounds the characters, not the number of segments |
| nesting depth inside `extensions` | 2020-12 cannot express a depth bound |
| number of nodes inside `extensions` | the schema cannot sum a count over a whole subtree |
| document size | the schema sees a parsed document, not bytes |
| total decoded binary content | the schema cannot decode base64, and cannot sum a quantity across members |
| length limits in UTF-8 bytes | `maxLength` counts code points (section 12.2) |

The schema checks nothing at L2 or L3, by design rather than by limitation: it knows no
business terms. The generated schema of an edition — `schema/esj-en16931-2017.schema.json`
and `schema/esj-en16931-2026.schema.json` — carries the per-term content grammars and
component sets for JSON Schema users (section 6.2). Even that one cannot reach a date that
matches the pattern and does not exist in the calendar,
because the calendar is not a regular language, nor the cardinality rules of L3, because they
are properties of the document as a whole. And it is scoped to the terms of the registry it was
generated from: a document carrying an extension term is outside it and is refused, which is a
statement about the schema and not about the document (sections 5.6 and 6.2).

Where the schema can express a limit of section 12.2 it does, with the default value:
`maxProperties` on `values`, `maxLength` on string values. Those are guards, not the limit: a
reader configured with other limits is authoritative, and the code point count is at most as
strict as the byte count. The schema cannot make one bound depend on whether a sibling member
is present, so it applies the larger of the two string bounds to the `value` member of every
value object, while section 12.2 applies it only where the object carries `mimeCode` or
`filename` and the smaller one everywhere else. The schema is therefore the more permissive of
the two here, and a reader is authoritative — a reader that decides by the binary components it
can see, without a registry and without knowing the term.

The JSON type of an envelope member is not in the table above, because the schema does check
it: `{"values": []}`, `{"extensions": "x"}` and `{"source": 5}` are rejected by the schema and
by a reader alike, and a validator reports `ESJ-L1-ENVELOPE-VALUE` for them (section 9.6).

### 9.2 L2 — model

L2 needs a registry, and the registry it needs is the one whose `edition` matches the
document's `semanticModel` (sections 4.4 and 10).

Where no such registry is available, L2 and L3 are not evaluated at all. The validator reports
`ESJ-L2-EDITION-UNKNOWN` once, at document level, names both layers as not evaluated, and
returns the status `INDETERMINATE` (sections 4.4 and 9.5). It reports no finding about any
individual path, because without the registry there is nothing to measure one against.

Where the registry is available, L2 checks:

* every core term in a path exists in that registry;
* the group chain of the path matches a parent chain a loaded registry records for the term —
  the core chain, or a chain an extension registry supplies through `reusesTerms`, with
  consecutive repetitions of a recursive group written as one (sections 5.2 and 5.6);
* the content of every value satisfies the grammar the registry datatype of its term requires
  (section 6.2): the decimal grammar of section 6.4 with its 64-character bound
  (`ESJ-L2-DECIMAL`), the date grammar of section 6.5 including the calendar
  (`ESJ-L2-DATE`), the time grammar of section 6.5 (`ESJ-L2-TIME`), or the canonical base64
  of section 6.7 (`ESJ-L2-BASE64`);
* `scheme`, `schemeVersion`, `mimeCode` and `filename` are present only where the registry
  lists such a component for the term, and are present where the registry declares one
  mandatory (sections 6.6 and 6.7);
* the index rule of section 5.3, in both directions;
* the syntax of extension segments (section 5.6).

An extension term for which no registry is loaded is reported as `ESJ-L2-NOT-CHECKED` and
nothing about its content is decided: L1 has accepted its shape, and without a datatype there
is no grammar to hold it to (section 5.6).

### 9.3 L3 — cardinality

L3 needs a registry and looks at the document as a whole. It checks:

* indices are dense and zero-based, per parent instance (section 5.4);
* within every group instance, every child term and child group with a minimum cardinality of 1
  is present;
* the same at the root of the document, for the terms and groups the registry places there;
* no term or group exceeds its maximum cardinality.

L3 is the layer that answers "are all mandatory elements there". Because a group instance
exists only if it has content (section 4.5), a missing mandatory group is reported at the level
of its parent instance.

L3 counts the paths that **passed L2** and no others. A path whose term the registry does not
contain, whose group segments are not a parent chain, whose index segment is required or
forbidden, or which could not be checked at all, is not placed in the document's structure, and a
layer that counts occurrences per parent instance has nowhere to count it. A path that failed L2
is therefore neither an occurrence of a term nor an instance of a group here, and the findings of
the two layers do not double up on one defect: `/BT-1/0` and `/BT-1/1` on a term declared 1..1
draw `ESJ-L2-INDEX-FORBIDDEN` twice and then `ESJ-L3-MISSING-TERM`, because BT-1 has no
occurrence L3 can see, and not `ESJ-L3-MAX-CARDINALITY` on top. A validator asked for L3 alone
computes L2 all the same and reports nothing of it. Such a result is never `VALID`: L1 and L2
produced no findings the caller can see, so the result names them as not evaluated and its status
is `INDETERMINATE` (section 9.5).

A path the reader struck out at L1 is not there to be counted either. A member of `values` that
L1 refused is not an occurrence of a term nor an instance of a group, so a layer that counts
occurrences has nothing to count for it — and a validator that counted the document without it
would report the term as missing while it stands in the file, with one character wrong. Where
L1 found an error, a validator therefore MUST NOT report the model layers over what is left of
the document as evaluated: the document it would measure is not the document that was sent. It
names them as not evaluated for the reason `PRECEDING-LAYER-FAILED` (section 9.5).

### 9.4 Business rules are a separate layer

A conformant validator (section 3.5) checks L1, L2 and L3, and those three layers alone are
what ESJ conformance means. The business rules of EN 16931-1, clause 6.4 — the integrity
constraints (BR-*), the conditions (BR-CO-*) and the VAT category rules — are not among them,
and neither are the decimal restrictions BR-DEC-*, which the CEN/TC 434 validation artefacts
define from the allowed number of decimals of clause 6.5.12, Table 26, rather than clause 6.4,
nor any CIUS rule set. A tool may check all of them, and this repository's does; what it may
not do is call the result ESJ conformance.

They are the subject of **rule packs**: separate, versioned collections of rules, each
identified by name and version, whose findings are reported as their own layer with the rule
identifier as the finding code. A document that passes L1 to L3 is structurally sound; whether
it is a legally correct invoice is the question those rules answer. The rules are maintained,
versioned and published as validation artefacts by CEN/TC 434 and by the CIUS owners, and those
artefacts stay the authority: a rule pack is verified against them.

An implementation that checks business rules MUST report those findings as a separate layer and
MUST NOT present them as ESJ conformance. The converse holds as well: a `VALID` result of the
layers of this specification is not a statement about those rules, and a tool that composes the
two MUST compose the verdicts and not borrow one for the other (section 9.5).

*Informative, and nothing of this paragraph is normative:* the reference implementation of this
specification ships such a rule pack — `en16931/1.3.16`, the rules of clause 6.4 written over
business terms — and reports its findings in the layer this section requires; `docs/validation.md`
describes it and `conformance/rules/ledger.md` records what it was measured against.

### 9.5 The validation result

A validator returns a **validation result**. It carries a status, the findings, and a statement
of what was covered:

| Field | Meaning |
|---|---|
| `status` | `VALID`, `INVALID` or `INDETERMINATE`, decided by the rules below |
| `findings` | the findings, in the order the validator produced them |
| `evaluated` | the layers that were evaluated |
| `notEvaluated` | the layers that were not, each with the reason |

A **finding** has:

| Field | Meaning |
|---|---|
| `path` | the semantic path the finding is about, or empty for a document-level finding |
| `subject` | what the finding is about where `path` cannot name it, or empty where `path` names it (below) |
| `code` | a stable, machine-readable identifier of the kind of problem (section 9.6) |
| `severity` | `error`, `warning` or `info` |
| `message` | human-readable English text; a fragment of the document quoted inside it is escaped, as below |

**What `path` and `subject` point at.** A caller acts on a finding by its `code` and by the
place it names, so the place has to be fixed here rather than left to each implementation:

* A finding about one member of `values` carries that member's path. Where the member name is
  not a semantic path at all there is no path to carry — `ESJ-L1-PATH-SYNTAX`,
  `ESJ-L1-OWNER-TOKEN`, a `ESJ-L1-DUPLICATE-MEMBER` whose object is `values` itself, the
  envelope, `source` or something inside `extensions`, and an `ESJ-L1-LIMIT` a member name
  broke — and `path` is empty while `subject` names the place in the document as it was
  received, written as a member access that carries the name: `values["/BT-1x"]`,
  `extensions["not an owner token"]`. The name inside it is escaped the way a fragment quoted
  in a message is (below). A bound may be met *before* the reader holds the name — a member
  name longer than what the reader's parser assembles at all is one — and there is then no
  name to write: `subject` is empty and the message MUST name the offset in bytes, counted
  from zero, at which the reader stopped, so that the finding still points somewhere.
* A `ESJ-L1-DUPLICATE-MEMBER` whose object is a **value object** is the other case: that
  object is one member of `values`, so the finding carries that member's path, and `subject`
  names the place as a member access the way a finding about the shape of the same object
  does. The name that occurs twice is named in the message, and a caller that mends the
  document goes to the path.
* `ESJ-L3-MISSING-TERM` and `ESJ-L3-MISSING-GROUP` carry the path of the parent instance the
  term or group is missing from, and the root path where it is missing at the root of the
  document; `subject` carries the identifier of the missing term or group. A missing thing has
  no path of its own, and the instance that lacks it is what a caller has to go to.
* `ESJ-L3-INDEX-GAP` and `ESJ-L3-MAX-CARDINALITY` carry the path of the parent instance in the
  same way, and name the term or group they are about in `subject`.
* Every other code carries the path of the value, the member or the document it is about. A
  reader that knows the place as a member access — `values["/BG-4/BT-29"].scheme` — MAY carry
  that in `subject` as well, and a validator handed a document rather than bytes leaves the
  field empty.

A validator MUST fill `subject` wherever these rules give it one. Two findings that would
otherwise be indistinguishable — four mandatory terms missing at the root of a document draw
four `ESJ-L3-MISSING-TERM` findings with the same empty path — are then told apart without the
English message being parsed.

`error` means the document fails the layer the finding belongs to. `warning` means that the
finding does not by itself make the document fail that layer, but that something is worth
saying. `info` carries no judgement about the document at all; its use in ESJ 0.1 is the report
that something could not be checked, which is not a defect and MUST NOT be presented as one.

A severity is a property of one finding. Whether the document is conformant is a property of
the result as a whole, and `status` is where that is said. **An empty finding list is not
conformance**: a validator that evaluated nothing also reports nothing, and a caller that reads
the findings without the status cannot tell the two apart.

A message may quote the document it is about: the value that spells no date, the member name
that is not defined. That fragment is content a stranger wrote (section 12.6), so a validator
MUST escape it before putting it in a message: a backslash as `\\`, a quotation mark as `\"`, a
line feed, a carriage return and a tab as `\n`, `\r` and `\t`, and every other C0 control, the
delete character and the bidirectional formatting characters as `\uXXXX`. A message is then safe
to write to a terminal or a log line as it stands, an escape sequence in a value cannot rewrite
the line a reader sees, and a quotation mark in one cannot forge the rest of a location. The
escaping is reversible, but a caller that wants the characters the document carries reads the
document at the finding's `path` — or, where the path is empty, at the member its `subject` names
— rather than parsing the message: `code`, `path` and `subject` are what a program reacts to, and
the message is for a person. Only the message is held to an excerpt: `subject` is one of the
three fields a program reacts to and carries its name whole.

**The three states.** A check that does not end in a yes ends in one of two ways — *the
document is wrong* and *I could not tell* — and a result that folds them together is read as
the wrong one of the two by whoever reads it next. So:

* **`VALID`** — all three layers L1, L2 and L3 were evaluated, no finding of severity `error`
  was reported, and the result carries none of `ESJ-L1-LIMIT`, `ESJ-L2-NOT-CHECKED` and
  `ESJ-L2-EDITION-UNKNOWN`, the three codes that record something not evaluated. A result that
  says of one path that it could not be checked has not checked the document.
* **`INVALID`** — at least one finding of severity `error` other than `ESJ-L1-LIMIT` was
  reported. A defect found is a defect whatever else was not reached, so this state outranks
  `INDETERMINATE`.
* **`INDETERMINATE`** — no such error was reported and something was not evaluated: a layer was
  not evaluated, or the result carries `ESJ-L1-LIMIT`, `ESJ-L2-NOT-CHECKED` or
  `ESJ-L2-EDITION-UNKNOWN`.

A document that never was a byte sequence has no `VALID` result of its own, because L1 cannot be
evaluated of it: its result is `INDETERMINATE` with L1 named `NOT-REQUESTED`, and only a tool
that composes a verdict over such an input may answer `VALID` on the strength of L2 and L3
(section 3.5).

Findings of severity `warning` never change the status. The two `info` codes named above do,
and they do so as records of something not evaluated rather than as findings about the
document. A validator MUST NOT derive the status from the severities alone: `ESJ-L1-LIMIT` has
severity `error` and yields `INDETERMINATE`, because it says that this implementation stopped
and not that the document is wrong (sections 3.1 and 12.2).

**`VALID` is a statement about three layers and about nothing else.** It says that the document
satisfies L1, L2 and L3 of this specification — that it is a conformant ESJ document in the
sense of section 3.1. It does not say that the invoice satisfies the business rules of
EN 16931-1: those are a separate layer and are not checked in this version (section 9.4). It
does not say that the invoice is arithmetically correct, complete for any profile, or usable
under any national requirement. An implementation that composes this result with components of
its own — a syntax validator, a rule pack, a CIUS check — composes verdicts, and MUST NOT
present this one as theirs.

**What was not evaluated is part of the result.** The result names the layers that were
evaluated. For every layer that was not, it names the reason, from a closed vocabulary, so that
a caller can branch on it without reading English:

| Reason | When |
|---|---|
| `LIMIT` | a limit of section 12.2 stopped the run before this layer was complete (section 12.2) |
| `PRECEDING-LAYER-FAILED` | the layer could not run because an earlier one did not produce what it needs |
| `EDITION-UNKNOWN` | no registry is available for the edition the document names (sections 4.4 and 9.2) |
| `NOT-REQUESTED` | the caller asked for a subset of the layers and this one is outside it, or the layer could not be asked of this document at all — L1 of a document that was never read from bytes (section 3.5) |

The rows are in the order of precedence the next paragraph fixes.

**Which reason a layer carries.** More than one of them can be true of one layer at one time —
a limit stopped a run over a document of an unknown edition — and a caller that branches on the
reason must get the same token from two implementations. The order below ranks the reasons a run
**established**, and never a reason it would have established had it gone further: a question the
run never reached is not an answer it may report. So: a layer the caller did not ask for keeps
`NOT-REQUESTED` whatever else happened, because nothing was ever going to answer it; for every
other layer the first of `LIMIT`, `PRECEDING-LAYER-FAILED` and `EDITION-UNKNOWN` that was
established is the one reported, in that order.

`PRECEDING-LAYER-FAILED` comes before `EDITION-UNKNOWN` because a run whose L1 failed never
reached the edition question: whether a registry describes the edition the document names is an
L2 question (section 9.2), and a run that stopped before L2 did not decide it, so
`EDITION-UNKNOWN` was not established at all. This is the same rule section 9.3 states from the
other side — where L1 found an error, the model layers are named as not evaluated for the reason
`PRECEDING-LAYER-FAILED` — and the two sections are one rule.

A reader that ends with an exception at the first defect it meets establishes fewer reasons than
one that reports and reads on as far as section 9.6 takes it: it may never reach the oversized
string a limit would have stopped it at, and it then reports
`PRECEDING-LAYER-FAILED` where a reader that read further reports `LIMIT`. Both are conformant,
because both report the first reason *they* established; what a caller branches on is what to do
next — raise a bound, or read the earlier layer's findings — and not a claim that nothing else
was in the way. The same order decides which reason survives where two results about one document
are composed into one (section 3.5).

A check narrower than a whole layer is reported the same way but as a finding:
`ESJ-L2-NOT-CHECKED` says that one path could not be checked because the extension registry
that describes it is not loaded (section 5.6), while the rest of L2 ran. Either way the rule is
the same — what was not evaluated is said, and it is what makes the result `INDETERMINATE`
rather than `VALID`.

An implementation that adds components of its own extends this vocabulary with reasons of its
own and documents them. It MUST NOT reuse one of these four for a different situation, and it
MUST NOT report `VALID` while one of its own required components did not run.

**A reader and a validator answer differently, and both are conformant.** A validator reports
findings and does not throw on invalid user data: a document with fifty problems produces fifty
findings in one pass, not one stack trace. A reader is asked for something else — it is asked
for a document, and where it cannot construct one there is nothing to hand back — so a reader
MAY end with an exception that carries the finding it stopped at, and a reader MAY instead
return the findings. Both are conformant, an implementation SHOULD say which it does, and an
implementation that offers both SHOULD carry the same finding in either shape. The code is the
same in both, so a caller that must handle both portably catches the exception and inspects the
findings. Exceptions for I/O failures are untouched by any of this.

A limit violation of section 12.2 is reported with the code `ESJ-L1-LIMIT`. It is the one
finding that is not a statement about the document but about this implementation's
configuration (section 3.1), and a caller should treat it so: forwarding the document to a
party with a larger bound is a sensible response to it, and would not be to an
`ESJ-L1-PATH-SYNTAX`. A reader that meets a limit while it parses MAY additionally abort with
an exception carrying that code, because parsing on is exactly what the limit was there to
prevent; a validator handed an already-parsed document reports the finding and continues. A
caller that must handle both portably catches that exception and inspects the findings; the
code is the same in either case. This code never makes a result `INVALID`: it makes it
`INDETERMINATE`, unless some other error finding in the same result makes it `INVALID` on its
own account.

### 9.6 Finding codes

A finding code identifies the **kind** of problem, so that a caller can react to it without
parsing English text. Codes are stable: a code that appears in a released version of this
specification keeps its meaning.

Every code defined by ESJ begins with `ESJ-`. That prefix is reserved for this specification. A
validator that reports problems of its own MUST use a different prefix, so that its codes can
never be confused with these.

A conformant validator MUST use these codes for the checks they name. It MUST NOT invent a
second code for a problem this table already covers.

Where more than one row could describe an input, three rules of precedence decide which one a
validator reports. They are stated as rules rather than as lists of pairs, so that a code added
in a later version falls under them without the list having to be revisited.

**A surrogate outranks content.** `ESJ-L1-SURROGATE` takes precedence over *every* code whose
check reads the content of the same string. A string carrying a lone surrogate has no UTF-8
encoding at all (section 6.8), so nothing built on its content can be decided about it: the
surrogate is a fact about the bytes, while a grammar is a fact about the characters those bytes
would have spelled, and there are none. An owner token, a path, a decimal, a date, a time,
a base64 content, a member of `source`, and the value of `format`, `version` or
`semanticModel` that carries one is therefore `ESJ-L1-SURROGATE` and not `ESJ-L1-OWNER-TOKEN`,
`ESJ-L1-PATH-SYNTAX`, `ESJ-L2-DECIMAL`, `ESJ-L2-DATE`, `ESJ-L2-TIME`, `ESJ-L2-BASE64` or
`ESJ-L1-ENVELOPE-VALUE`. The rule reaches across the layers for the same reason it holds
inside one: a string with no encoding has no content for a grammar to read, whichever layer
the grammar belongs to. Those codes are examples of the rule and not its definition.

A check that reads a *different* string is untouched by the rule, and a validator reports both
codes: an undefined member name beside a value that carries a surrogate is
`ESJ-L1-ENVELOPE-MEMBER` or `ESJ-L1-VALUE-MEMBER` **and** `ESJ-L1-SURROGATE`, because the two
checks read two strings and neither displaces the other. Reporting only the member set would
hide the one fact a caller cannot work around: the code that says the member set is wrong names
something to mend, while the surrogate says the document has no UTF-8 encoding at all
(section 6.8) and must not be handed on whatever else is mended. Inside a value object the rule
therefore reaches every member string, including one the object does not define, and a value
object may draw `ESJ-L1-SURROGATE` beside the code the order below gives it. A member that is
itself a JSON object is not descended into, because the next rule stops there.

**Shape outranks what is nested below it.** `ESJ-L1-JSON-TYPE` covers the five JSON value types
that rule 5 of section 4.2 forbids inside `values` — number, `true`, `false`, `null` and array —
wherever they appear there: as a member of `values`, as a member of a value object, or nested
below either. `ESJ-L1-VALUE-SHAPE` covers what is left of "the wrong shape in the right place":
a member of `values` that is an object with no supplementary component, and a member of a value
object that is itself a JSON object. Where both could apply, because a forbidden type sits below
a member whose own shape is already wrong, the shape is what is reported and the subtree below
it is not examined further.
So `"/BT-1": null`, `"/BG-4/BT-29/0": {"value": ["a"], "scheme": "0088"}` and
`"/BG-25/0/BT-129": 1` are all `ESJ-L1-JSON-TYPE`, while
`"/BT-1": {"value": "RE-2026-0001"}` and
`"/BG-4/BT-29/0": {"value": {"x": "y"}, "scheme": "0088"}` are `ESJ-L1-VALUE-SHAPE`.

`ESJ-L1-VALUE-MEMBER` is the third of that family and is about the member **set** rather than
about a shape: a member outside the five of section 6.1, a missing `value`, or a
`schemeVersion` with no `scheme`. An object with an undefined member and no component at all
is `ESJ-L1-VALUE-SHAPE`, because the shape is decided before the set is examined.

**The shape of a member outranks the member set, and the member set outranks the content of a
member.** A JSON object is an unordered set of members, and section 7.3 fixes a canonical order
precisely because the order an input is written in carries no meaning. The code a caller
branches on must therefore be a function of the object and not of its spelling, so the checks
of one value object are ordered as checks rather than walked member by member. A validator
reports the **first** of these that the object matches:

1. the object carries no supplementary component — `ESJ-L1-VALUE-SHAPE`;
2. some member is itself a JSON object — `ESJ-L1-VALUE-SHAPE`;
3. some member is a JSON number, `true`, `false`, `null` or an array — `ESJ-L1-JSON-TYPE`;
4. the member set is not one this specification defines: a member outside the five of
   section 6.1, a missing `value`, or a `schemeVersion` with no `scheme` —
   `ESJ-L1-VALUE-MEMBER`;
5. some member is the empty string — `ESJ-L1-EMPTY-STRING`.

Steps 1 to 3 are the two rules above applied inside one object; steps 4 and 5 add that a set
this specification does not define leaves nothing for a check of a member's content to be
about. So `{"scheme": ""}` is `ESJ-L1-VALUE-MEMBER`, because the object carries no `value`, and
not `ESJ-L1-EMPTY-STRING`; `{"value": "X", "scheme": "0088", "foo": ""}` is
`ESJ-L1-VALUE-MEMBER`, because `foo` is not one of the five; `{"foo": "a", "scheme": null,
"value": "X"}` is `ESJ-L1-JSON-TYPE` and not `ESJ-L1-VALUE-MEMBER`, and so is the same object
with its members written in any other order; `{"foo": "a", "scheme": {"a": "b"}, "value": "X"}`
is `ESJ-L1-VALUE-SHAPE` for the same reason. `ESJ-L1-EMPTY-STRING` is the code for an object
whose set is right and one of whose strings is empty, as in
`{"value": "0088123456785", "scheme": ""}`.

With those rules one value object draws one of those five codes, whichever order its members
are written in, and `ESJ-L1-SURROGATE` is the one code that may stand beside it: the first rule
puts it outside this order, because it reads a string where these five read a shape, a set, or
another string.

**A member name the reader cannot take stops it there.** Two defects are held against the JSON
text rather than against the value written in it: a member name that occurs twice
(`ESJ-L1-DUPLICATE-MEMBER`, section 4.2, rule 3) and a member name that carries a lone
surrogate (`ESJ-L1-SURROGATE`). Neither leaves an object for the order above to judge — a set
that carries one name twice is not one object, and a name with no UTF-8 encoding names nothing
— so a reader reports that code and judges the object no further: none of the five stands
beside it, and no string inside it is read for the rule above. A lone surrogate in a member
**value** is the other case and is untouched by this: the object is judged, and the surrogate
stands beside the code the order gives it, that code being `ESJ-L1-VALUE-SHAPE` as readily as
any other. So `{"value": "A\ud800"}` draws
`ESJ-L1-SURROGATE` and `ESJ-L1-VALUE-SHAPE`, `{"value": "X", "scheme": "0088", "foo":
"\ud800"}` draws `ESJ-L1-SURROGATE` and `ESJ-L1-VALUE-MEMBER`, and
`{"value": "A", "\ud800": "y"}` draws `ESJ-L1-SURROGATE` alone.

The rule is about a member name and not about the object the name sits in, so it reaches every
object of a document alike: a value object, `values`, the envelope, `source`, and every object
below an owner token of `extensions`. A name with no encoding names no envelope member either,
so `{"\ud800": "y"}` at the top level and `"source": {"\ud800": "UBL"}` draw
`ESJ-L1-SURROGATE` and not `ESJ-L1-ENVELOPE-MEMBER`: the code that says the member set is wrong
names something to mend, and a name that spells nothing leaves nothing to mend it by. One such
name draws one code, and the object it names is judged no further, so the two never stand
together and neither is reported twice about one object — a member written under a name that
already occurred is not judged a second time either.

Where one object carries both defects, the one **the text reaches first** is the one reported.
Every other rule of this section is stated so that the code does not depend on the order the
members were written in, because that order carries no meaning (section 7.3). These two are the
exception, and they are the exception for the reason they are held against the text at all: a
duplicated name and a name with no encoding are places in the JSON text rather than properties
of an object, and a reader that has been stopped before it holds an object has nothing but the
text to rank them by. So `{"value": "A", "\ud800": "y", "zz": "1", "zz": "2"}` draws
`ESJ-L1-SURROGATE`, and the same members with the repeated name written first draw
`ESJ-L1-DUPLICATE-MEMBER`.

**How far a reader reads.** A reader reports the findings it established and stops at the first
defect it cannot read past, so that the findings of a document are a function of its bytes and
two conformant readers answer one byte sequence with one list.

A defect confined to one member of `values` is one a reader reads past: its name against the
path grammar, and the shape, the member set and the content of the value written under it. The
reader reports the finding, leaves that member out of the document it builds (section 9.3), and
judges the members after it. Every other defect of layer L1 ends the read, none of them being
confined to one value: the encoding, the JSON text, the envelope, `source`, `extensions`, a
member name that occurs twice, a member name carrying a lone surrogate, and a limit of
section 12.2, which is what a limit is for. The findings of such a document are therefore those
the reader had established before that defect, and that defect last.

A reader that ends with an exception instead of reporting (section 9.5) stops at the first
finding of that list and carries it, whichever kind of defect it is.

**A limit outranks what the limit stopped the reader from reading.** Where a bound of
section 12.2 stops a reader before it has judged a member, the finding is `ESJ-L1-LIMIT` and
not the code that member would have drawn had the reader read it. The reader did not judge the
member, and the two answers are not interchangeable: `ESJ-L1-LIMIT` leaves the result
`INDETERMINATE` and invites a retry against a larger bound, while the shape codes of the order
above make it `INVALID` and tell the next program along to reject the document (section 9.5).
A reader that walks past a JSON structure nested deeper than the bound allows, or that meets a
number inside `extensions` longer than the string bound, therefore reports the limit and not
`ESJ-L1-JSON-TYPE`, `ESJ-L1-VALUE-SHAPE` or `ESJ-L1-EXT-NUMBER` (section 12.2).

Outside that rule and the surrogate pair the codes are disjoint: no input is described by two
rows.

**An unknown edition outranks an unchecked term.** Where no registry for the edition the
document names is available, a validator reports `ESJ-L2-EDITION-UNKNOWN` once and nothing
else at L2: it checked no path, so it has no path to report `ESJ-L2-NOT-CHECKED` about
(section 9.2). The two codes therefore never stand together, and a caller that meets the first
knows that the second says nothing extra.

| Code | Layer | Severity | Reported when |
|---|---|---|---|
| `ESJ-L1-ENCODING` | L1 | error | the byte sequence is not UTF-8, or it starts with a byte order mark |
| `ESJ-L1-JSON` | L1 | error | the byte sequence is not a JSON text, or its top level is not an object |
| `ESJ-L1-DUPLICATE-MEMBER` | L1 | error | a member name occurs twice in one object, at any depth |
| `ESJ-L1-ENVELOPE-MEMBER` | L1 | error | a required envelope member is missing, or an undefined one is present, including inside `source` |
| `ESJ-L1-ENVELOPE-VALUE` | L1 | error | `format` or `version` does not carry its fixed value, `semanticModel` does not satisfy the edition grammar of section 4.4, an envelope member is not of the JSON type section 4.1 gives it, `extensions` or `source` is present but empty, or a member of `source` does not satisfy section 4.7 |
| `ESJ-L1-OWNER-TOKEN` | L1 | error | a member name of `extensions` is not an owner token (section 4.6) |
| `ESJ-L1-PATH-SYNTAX` | L1 | error | a member name of `values` does not match the path grammar (section 5.1) |
| `ESJ-L1-JSON-TYPE` | L1 | error | a JSON number, `true`, `false`, `null` or array appears anywhere inside `values` |
| `ESJ-L1-EXT-NUMBER` | L1 | error | a JSON number inside `extensions` whose canonical decimal form exceeds 64 characters (section 7.6) |
| `ESJ-L1-VALUE-SHAPE` | L1 | error | a member of `values` is an object that carries no supplementary component, or a member of a value object is itself a JSON object (section 6.1) |
| `ESJ-L1-VALUE-MEMBER` | L1 | error | a value object lacks `value`, carries a member outside the five of section 6.1, or carries `schemeVersion` without `scheme` (section 6.6, rule 4) |
| `ESJ-L1-EMPTY-STRING` | L1 | error | a value written as a string, or a member of a value object, is the empty string |
| `ESJ-L1-SURROGATE` | L1 | error | a string contains a lone surrogate (section 6.8) |
| `ESJ-L1-LIMIT` | L1 | error | a limit of section 12.2 is exceeded, so this implementation declines to process the document under its configuration; it is not a defect of the document (sections 3.1 and 9.5), and a reader MAY signal it by an exception as well |
| `ESJ-L2-UNKNOWN-TERM` | L2 | error | a core segment names a term the registry does not contain |
| `ESJ-L2-PARENT-CHAIN` | L2 | error | the group segments are not a parent chain a loaded registry records for the term (sections 5.2, 5.6) |
| `ESJ-L2-DECIMAL` | L2 | error | the content of a term whose registry datatype is a decimal one does not match the canonical decimal form, or exceeds 64 characters (sections 6.2 and 6.4) |
| `ESJ-L2-DATE` | L2 | error | the content of a `Date` term does not match the date grammar, or names a day that does not exist (sections 6.2 and 6.5) |
| `ESJ-L2-TIME` | L2 | error | the content of a `Time` term does not match the time grammar, which requires the offset and admits one spelling per value (sections 6.2 and 6.5) |
| `ESJ-L2-BASE64` | L2 | error | the content of a `BinaryObject` term is not canonical padded base64 (sections 6.2 and 6.7) |
| `ESJ-L2-COMPONENT-NOT-ALLOWED` | L2 | error | a supplementary component is present where the registry lists no such component for the term |
| `ESJ-L2-COMPONENT-MISSING` | L2 | error | a component the registry declares mandatory is absent, which for a `BinaryObject` term means `mimeCode` or `filename` |
| `ESJ-L2-INDEX-REQUIRED` | L2 | error | a repeatable term or group carries no index segment |
| `ESJ-L2-INDEX-FORBIDDEN` | L2 | error | a term or group with maximum cardinality 1 carries an index segment |
| `ESJ-L2-NOT-CHECKED` | L2 | info | an extension segment, or a core term re-rooted by an extension, could not be checked because that registry is not loaded (section 5.6) |
| `ESJ-L2-EDITION-UNKNOWN` | L2 | info | no registry is available for the edition the document names, so neither L2 nor L3 was evaluated (sections 4.4 and 9.2) |
| `ESJ-L3-INDEX-GAP` | L3 | error | indices under one parent instance are not dense and zero-based |
| `ESJ-L3-MISSING-TERM` | L3 | error | a term with minimum cardinality 1 is absent from a group instance or from the root |
| `ESJ-L3-MISSING-GROUP` | L3 | error | a group with minimum cardinality 1 has no instance in a group instance or at the root |
| `ESJ-L3-MAX-CARDINALITY` | L3 | error | a term or group occurs more often than its maximum cardinality allows |

The last clause of the `ESJ-L1-ENVELOPE-VALUE` row covers a `source` whose members are present
and of the right JSON type but of the wrong shape: an empty `syntax`, and a `sha256` that is not
64 lowercase hexadecimal digits. It is named here because the neighbouring codes do not reach
it. `ESJ-L1-EMPTY-STRING` is written for a string inside **`values`**, and `source` is not one
(section 2.3); `ESJ-L1-ENVELOPE-MEMBER` is about a member that is missing or undefined, and
these members are neither. Without the clause the code a caller branches on would be undefined
for exactly the documents section 4.7 exists to refuse. `examples/invalid/source-empty-syntax.esj.json`
and `examples/invalid/source-sha256-uppercase.esj.json` are the two fixtures.

The severity column is the severity the validator MUST use when it reports that code at the
layer it was asked for. A validator MAY additionally downgrade an `error` to a `warning` when
the caller asked for a layer the finding does not belong to; it MUST NOT upgrade. A downgrade
never buys a `VALID`: the layer such a finding belongs to is by construction one the caller did
not ask for, so the result names it as not evaluated and its status is `INDETERMINATE`
(section 9.5).

The two `info` codes are the only ones that report something that was **not** evaluated, and
they are the reason a caller cannot read the severities alone: a result whose worst severity is
`info` may still be a result in which nothing of the model was checked.

---

## 10. The registry

The registry is the machine-readable form of the structural facts of one edition of the
semantic model: the identifiers, the parent relation, the cardinalities, the semantic data
types and the supplementary components. Layers L2 and L3 are defined against it, and so is
the content grammar of every value (section 6.2).

1. The registry is the **normative source of structure** for this specification. Where this
   document names a cardinality or a data type as an example, the registry decides.
2. The registry contains no normative prose of EN 16931-1. It carries identifiers, names,
   numbers and the author's own descriptions.
3. `slug`, `order`, `codeList`, `schemeList`, `maxDecimals`, `maxDecimalsRule`, `reqIds`,
   `description` and `notes` are documentation and code generation aids. They MUST NOT affect
   L1, L2, L3 or the canonical form. `maxDecimals` and `maxDecimalsRule` are the two forms the
   fraction-digit bound of a term takes, and a term carries at most one of them (section 6.4);
   `reqIds` carries the requirement identifiers of an edition whose term table has such a
   column.
4. An extension registry, for example `model/xrechnung/3.0.2.json`, has the same shape and
   supplies the structure of extension terms (section 5.6).
5. An extension registry MAY declare `"transport": "none"`, with a `transportNote` that states why.
   It means that the terms the file defines are bound by no transport syntax by design. A validator
   that runs the validation artefacts of a syntax over the XML an ESJ document is written to
   MAY count that component as having run where everything the writer left behind is a term of
   such a registry, and MUST then name those terms in its report. The member affects nothing else: L1,
   L2, L3, the canonical form and both digests are what they would be without it.

A registry declares components only where the semantic data type has them, and only in a
combination a value can satisfy. Three rules follow from section 6 and are checked when a
registry is read, not left to the documents measured against it, because a term no value can
satisfy is a defect of the registry rather than of any invoice: a `BinaryObject` term carries
exactly `mimeCode` and `filename` and both mandatory — the two are a property of the type, so
an empty component list is as wrong there as a wrong one; `schemeVersion` is declared only
beside `scheme`; and `schemeVersion` is declared mandatory only beside a `scheme` that is also
mandatory, because section 6.6, rule 4 refuses a value that carries the version without the
scheme, so a term declaring the one mandatory and the other optional could be written in no
way at all. `model/registry.schema.json` states the same three rules for a reader that checks
a registry file with JSON Schema.

### One registry per edition

A registry file describes exactly one edition and names it in its `edition` member, in the
spelling the standards body uses, for example `EN 16931-1:2017+A1:2019/AC:2020`. The edition
string a document writes in `semanticModel` is that spelling **with every space removed**,
which is what the grammar of section 4.4 admits: `EN16931-1:2017+A1:2019/AC:2020`. The
mapping is mechanical and exact in both directions, because an edition string contains no other
whitespace and no space is significant in it.

A validator selects the registry whose edition matches the document's `semanticModel` under
that mapping and uses no other. It MUST NOT validate a document against a registry of a
different edition, because a path is an address relative to an edition and a term may be
renumbered between two of them. Where it holds no registry for that edition it validates
against none: it reports `ESJ-L2-EDITION-UNKNOWN`, leaves L2 and L3 unevaluated and returns
`INDETERMINATE` (sections 4.4 and 9.5).

The files are laid out by model and edition: `model/en16931/2017.json` carries
`EN 16931-1:2017+A1:2019/AC:2020` and `model/en16931/2026.json` carries `EN 16931-1:2026`. A
further edition arrives as a new file beside them, never as a rewrite of one of them, and a
registry file of an edition carries that edition whole: it has no `imports`, shares nothing
with the registry of another edition and is read without it. A document that names the 2017
edition must still be validatable against exactly the terms that edition had, years after a
successor exists.

Which registries a build carries is a property of that build and not of this specification: a
distribution may ship one edition, both, or an edition this document does not name, and a
validator answers `ESJ-L2-EDITION-UNKNOWN` for the ones it does not hold (section 9.2).

An extension registry declares what it builds on in its `imports` member, as a list of
`{"model": …, "edition": …}`, where `edition` is the imported registry's own `edition` string.
Combining an extension with a core registry whose edition it does not name is an error: the
extension's parents, its `reusesTerms` and its cardinalities were all checked against one list
of terms.

`imports` names the **edition of another model** an extension builds on, and never a version of
the extension's own namespace. It does not have to, because a namespace is permanent
(section 5.6): an identifier published in a namespace keeps its meaning, its semantic data type
and its structural semantics in every later registry of that namespace, so a path carrying one
can be read without knowing which registry version was current when the document was written.
A registry file of an extension is still versioned — the set of identifiers grows, and a
validator must know which set it holds — but the version says which identifiers are known and
never what one of them means.

### The registry names code lists and never contains them

Code lists are named, not enumerated: the registry records that BT-3 takes its value from
UNTDID 1001 and that BT-118 takes its value from UNTDID 5305, without a version, and ESJ does
not ship the lists and does not check membership. Checking a code against a list is a business
rule (section 9.4).

This is a rule about **where a versioned fact may live**, not a convenience. A code list gains
and loses codes over time, while the registry decides whether a document is structurally
conformant, and that verdict must not move: a document conformant today must be conformant in
ten years, against the same registry and against any later copy of it. If membership were
checked there, the registry would have to carry either the codes or the version of the list it
was written against, and either way the same unchanged document would get two different
structural verdicts from two releases of one tool — a verdict that expires, discovered years
later on an archived invoice by a party who cannot ask the sender anything.

The facts are therefore split by how they age. *Which list a term draws from* does not change
and belongs in the registry. *Which codes that list held* changes and belongs in a **rule
pack**: a snapshot of the lists taken at a release date, versioned, published separately, and
named in every result it produces. A membership finding is then relative to a rule pack a
report can name, and reproducible because that pack still exists. ESJ 0.1 ships none;
`codeList` is carried so that one can be built against it.

The same holds one level down. Where the model fixes the code list an **identification
scheme** is drawn from, the registry records its name in the `schemeList` member of the
`scheme` component of that term — `ISO 6523 ICD`, `CEF EAS`, `UNTDID 1153`, `UNTDID 7143` in
the core model, each justified in the term's `notes` by the rule of the CEN validation
artefacts that fixes it. It is documentation on the same terms as `codeList`: which list the
scheme comes from does not change, which codes it holds does, and only a rule pack checks
membership. No other component carries the member.

### `slug` is a name stem, not a name

`slug` is an ESJ convention with no standing in any standard, and it is language-neutral: a
lowerCamelCase ASCII stem matching `[a-z][A-Za-z0-9]*`, unique among the children of one
parent. It is not an identifier in any particular language. A generator applies the naming
convention of its target — Java `netAmount()`, C# `NetAmount`, TypeScript `netAmount` — and
escapes that language's reserved words itself, because the set of reserved words differs from
language to language and the registry must not encode one language's set into a file every
generator reads.

Uniqueness among siblings is what a generator needs; it is not enough for a person. Two further
rules make the stems readable:

1. **One concept, one stem.** A semantic concept that appears under more than one group carries
   the same slug everywhere. The VAT category code is `vatCategoryCode` and the VAT category
   rate is `vatRate` under BG-20, BG-21, BG-23 and BG-30 alike, although sibling uniqueness
   would have allowed a shorter stem in the last two. A writer meets all four in one invoice,
   and a stem that changes with the group is a name to look up rather than a name to know.
2. **A shortened stem is still the name of the concept.** A slug may drop the qualifier the
   model's term name carries for disambiguation — BT-131 "Invoice line net amount" is
   `netAmount` inside a line — but only where what is left still names the thing. Dropping one
   word out of a compound is not that: BT-81 "Payment means type code" is `paymentMeansTypeCode`
   and not `meansTypeCode`, because *payment means* is the concept and *means* on its own is
   not.

3. **A repeatable group's slug is a plural.** The slug of a group the model declares `0..n` or
   `1..n` names the list of its instances, and a generator forms the name of one instance from
   it: `invoiceLines` yields `invoiceLine`, `latePaymentPenalties` yields `latePaymentPenalty`.
   Those are the two forms a generator takes — a trailing `s`, and a trailing `ies` for a `y` —
   so a slug in any other shape is one a generated API cannot name an instance of.

All three rules are editorial. They constrain what a registry may declare, not what a document
may say, and changing a slug changes a generated API without touching a single document.

---

## 11. Extensions and profiles

### 11.1 Extension terms

A national or sectoral extension that adds business terms declares them in its own registry and
addresses them with namespaced segments (section 5.6). An extension term sits in the structure
exactly where its registry says it does, which may be under a core group.

An extension registry may also re-root core terms under its own groups rather than mint new
identifiers for terms that already exist. It declares that with `reusesTerms` (section 5.6);
the core term then has one more well-formed position, and keeps its type, its components and
its own cardinality. This is what `model/xrechnung/3.0.2.json` does for the sub invoice
line tree, which repeats the structure of BG-25 one level deeper.

An extension term's values are written like any other: a string, or an object when the
extension registry lists a supplementary component for the term (section 6.1). Without that
registry a validator accepts either shape and reports `ESJ-L2-NOT-CHECKED` (section 5.6).

**The extension registries of this repository (informative).** Neither is part of EN 16931-1,
neither is required to read a document, and this list says which namespaces the repository has
published, not which namespaces exist:

| Namespace | Registry | Terms | What it records |
|---|---|---|---|
| `DEX` | `model/xrechnung/3.0.2.json` | 12 | the sub invoice line tree and the third party payment of XRechnung 3.0.2 |
| `B2C` | `model/b2c/0.1.json` | 4 | the gross unit price, gross line total, line VAT amount and gross invoice total a consumer was shown or agreed to; the registry defines no arithmetic relation between them or to a core term |

An extension MUST NOT:

* redefine a core term, its cardinality or its semantic data type,
* change the meaning of a core term,
* require a core term to be absent,
* change the meaning, the semantic data type or the structural semantics of one of its **own**
  published identifiers — that is the permanence rule of section 5.6, and an incompatible
  revision takes a new namespace.

A later registry of the same namespace MAY add identifiers and MAY record a further position
for a term it already carries; neither of those changes what a published identifier means, and
neither makes a document written against the earlier registry mean something else.

Removing or narrowing is the business of a CIUS, and a CIUS is expressed in rules, not in the
serialization (EN 16931-1, clause 7.3 describes what a CIUS may specify).

### 11.2 Profiles

ESJ has **no envelope member for the profile**. The profile identifier is BT-24 (Specification
identifier), a business term of the model itself, and it is written like any other value:

```json
"/BG-2/BT-24": "urn:cen.eu:en16931:2017"
```

EN 16931-1, clause 7.6 describes carrying the assigned identifier of a core invoice usage
specification in that business term so that the receiver can apply the right processing. ESJ
follows that and does not duplicate the information in the envelope, because two places for one
fact is one place too many — they can disagree.

A document that uses a CIUS carries that CIUS's identifier in BT-24. ESJ does not interpret
the value; it is an identifier to ESJ and a processing instruction to the application.

---

## 12. Security considerations

### 12.1 Threat model

An ESJ document is untrusted input. It arrives from a business partner, possibly through a
mail gateway, and a reader must not be the weakest part of the chain. The threats considered
here are resource exhaustion, parser confusion, and content that is safe to store but unsafe to
act on.

ESJ removes one class of problem by construction: there is no XML, so there are no external
entities, no DTDs, no entity expansion, no XInclude, no XSLT and no schema resolution over the
network. An importer that reads UBL or CII still faces all of those, and must handle them; ESJ
confines that exposure to the importer.

### 12.2 Limits

A reader MUST enforce these limits and MUST make them configurable. They are a **policy of
the reader and not a property of the document**: exceeding one is reported as `ESJ-L1-LIMIT`
and says that this reader, as configured, declines to process the document, which is a
different statement from calling the document non-conformant (section 3.1). The defaults below
exist so that two implementations that adopt them refuse the same documents.

Two bounds this specification fixes are deliberately **not** among them, because they are
decided by the bytes rather than by a configuration: the 64-character bound on a decimal form
(sections 6.4 and 7.6) and the 128-character bound on an owner token (section 4.6). Each
belongs to the grammar it is stated with and is reported with that grammar's code —
`ESJ-L2-DECIMAL`, `ESJ-L1-EXT-NUMBER`, `ESJ-L1-OWNER-TOKEN` — never as `ESJ-L1-LIMIT`. These
are the defaults:

| Limit | Default | Measured in |
|---|---|---|
| document size | 64 MiB | bytes of the encoded document |
| number of members in `values` | 100 000 | members |
| number of members of one value object | 16 | members |
| segments per path | 16 | segments, counting index segments |
| path length | 256 | bytes of the UTF-8 encoding of the member name |
| string value length | 1 MiB (the content of a binary object: 32 MiB) | bytes of the UTF-8 encoding of the normalized value (section 6.8) |
| total decoded binary content | 48 MiB | bytes after base64 decoding |
| nesting depth inside `extensions` | 32 | levels of object or array |
| number of nodes inside `extensions` | 100 000 | nodes, counting every scalar and every container |

The larger of the two string bounds applies to the `value` member of a value object that
carries `mimeCode` or `filename`, and the smaller one to every other string inside `values`,
the supplementary components included. A reader enforcing the bound while it parses has no
registry and cannot know the semantic data type of the term; the presence of a binary
component is what it can see, and it is enough. A validator that holds the registry MAY apply
the bound by datatype instead: the two agree on every document conformant at L2, because a
binary component at a term that is not a binary object is already an error there
(section 6.2).

The number of members of one value object is bounded for the same reason the members of
`values` are: a reader that collects the members of an object before it judges them holds
whatever the object carries, and a value object is the one container inside `values` that a
reader has to collect rather than count. At most five members are ever defined (section 6.1),
so the sixth already makes the object an error; the default of 16 therefore refuses no document
this specification allows, and exists only so that a reader may count as it walks and refuse an
object with a million members before it holds it. Exceeding it is `ESJ-L1-LIMIT` like every
other bound here, not `ESJ-L1-VALUE-MEMBER`: the reader stopped, it did not finish judging.

The nesting depth bound also covers JSON a reader has to **walk past** inside `values`. A
member of `values` is a string or a value object, and a member of a value object is a string
(section 6.1), so an object or an array written in either place is an error whatever its depth
— but a reader has to get past it to reach the next member, and it MUST bound that walk rather
than follow it to any depth. It is counted from the same place there as inside `extensions`
(below): the envelope is not a level and neither is the `values` object, so a container written
as the value of a member of `values` is level 1, and one written as the value of a member of a
value object is level 2. A reader that stops at the bound reports `ESJ-L1-LIMIT`, and so does a reader
that refuses such a member — one that nests deeper than the bound — before it walks it: the limit
outranks the shape code the member would otherwise have drawn (section 9.6). A member that stays
inside the bound draws that shape code as before, whether the reader walked it or refused it at
the first `{`: nothing stopped the reader there, so there is no limit to report. While a refusal was the whole of the answer the choice
could be left open; it cannot be, now that the two answers differ in what they tell the next
program along — a limit is no verdict and invites a retry against a larger bound, a shape code
is a verdict of `INVALID`. Two readers running the defaults of this section therefore give one
answer about such a document, and not merely the same refusal.

The number of nodes inside `extensions` is the count of every value in that subtree, taken over
the whole document rather than per owner token: every string, number, `true`, `false` and
`null`, and every object and array, counts as one node, and the value of an owner-token member
is the first of them. It is a separate bound because the depth bound and the string bound
together do not bound the subtree: `[0,0,0,…]` is one level deep and holds nothing but
one-character tokens, and a reader that builds a node per token pays far more than the document
weighs. A reader counts as it walks, so that the bound fires before it allocates, and reports
`ESJ-L1-LIMIT`. The default is the bound on the members of `values`, which leaves every
realistic extension untouched.

The nesting depth inside `extensions` is counted from the value of an owner-token member: that
value is level 1, a container inside it is level 2, and so on; the `extensions` object itself is
not a level, and neither is the envelope around it. Only an object and an array are levels — a
string, a number, `true`, `false` and `null` are values inside a level and never a level of
their own. With the default an owner may therefore write 32 nested containers and not 33, which
is what `examples/extension-depth.esj.json` and `examples/invalid/extension-depth-33.esj.json`
hold, one on each side of the boundary. This is said because it is the only one of the limits
whose counting base is not self-evident: the others are counted in bytes or in members, and an
off-by-one here would make two implementations that both run the defaults disagree about a whole
class of documents, which section 3.1 promises they do not. The same is why the counting base of
the walk past inside `values` is fixed above rather than left to whatever a reader's parser
happens to count.

The bound on a string is also the bound on the **spelling** of a JSON number inside
`extensions`: a reader refuses a number token longer than that as `ESJ-L1-LIMIT` before it
builds it. The 64-character bound of section 7.6, rule 2 applies to the number's canonical
form and cannot size a parse buffer, because a spelling of any length may canonicalize to a
short one — `1.` followed by a million zeros is the number `1`. The two bounds answer two
questions: what the document may mean, and what a reader is willing to hold while it finds out.
The bound is on the token as the document spells it, counted in its own characters, which for a
JSON number are ASCII and therefore its UTF-8 bytes as well; a reader MUST NOT leave the exact
boundary to whatever its parser happens to count, because two readers running the defaults have
to refuse the same tokens.

The bound on a string value covers `source.syntax` as well. Section 4.7 asks for a short name
such as `UBL` or `CII` and says nothing about length, so without this sentence the only bound on
it would be whichever one a reader's parser happened to apply — in one implementation an
accidental function of the *binary* bound, so that lowering `maxBinaryValueBytes` would silently
start refusing a `source.syntax` that a default reader accepts. A sender cannot predict that,
and section 3.1 promises it does not have to. `format`, `version` and `semanticModel` need no
bound: each is compared against a fixed value and refused the moment it differs.

Every length limit is counted in **UTF-8 bytes**, not in characters, code points or UTF-16 code
units. A character count would make the same document acceptable to a reader written in one
language and too large for a reader written in another: one CJK ideograph is one code point,
one UTF-16 code unit and three UTF-8 bytes. Bytes are also the measure that actually bounds
memory. A path is pure ASCII by its grammar, so for paths the three counts coincide.

`schema/esj.schema.json` cannot express this: JSON Schema `maxLength` counts code points. Its
`maxLength` of 256 on a path is therefore a slightly weaker check than the limit above, not a
different one, and the reader remains authoritative (section 9.1). Being weaker is the right
direction for a guard: a schema that refused a document a conformant reader accepts would
make the schema, rather than the reader, decide what this specification allows.

Exceeding a limit MUST be reported with the code `ESJ-L1-LIMIT` (section 9.6); a reader that
meets it while parsing MAY additionally abort with an exception carrying that code
(section 9.5). A reader MUST NOT truncate a value, skip a member or silently continue, because
the result would be a document that differs from what the sender sent while looking complete.
It MUST NOT present the refusal as a verdict on the document either (section 3.1): this code
never makes a result `INVALID`, the layers the run did not complete are named with the reason
`LIMIT`, and the status is `INDETERMINATE` unless another error finding decides otherwise
(section 9.5).

A reader MAY also refuse a *configuration*, which is a different thing from refusing a document.
A bound larger than the reader could ever enforce — one past the longest byte sequence it can
hold, or one it would have to enlarge by the nesting of the envelope before handing it to its
parser — is a defect in the call and not in any document. A reader that refuses such a bound
MUST do so when the bound is given rather than when a document arrives, and MUST say which bound
it refused. It MUST NOT quietly clamp the value instead, because two readers that clamp at
different places classify the same document differently, which section 3.1 promises they do not.

The limits exist because JSON makes it cheap to write a small file that is expensive to parse:
deep nesting, enormous strings, or hundreds of thousands of members. Streaming parsers MUST be
configured with matching constraints rather than relying on checks after parsing.

### 12.3 Parser configuration

A reader:

1. MUST reject duplicate member names (section 4.2). A parser that keeps the last value for a
   repeated name allows two readers to see two different invoices in one file.
2. MUST NOT use reflection-driven or polymorphic deserialization, and MUST NOT instantiate a
   class named in the input. There is no type information in an ESJ document that could name a
   class, and no reader should invent a mechanism that would.
3. MUST NOT resolve any URL, path or reference found in the document while reading it.
4. MUST be able to see a JSON number inside `extensions` as it is written, as a raw token or as
   an exact decimal. A parser that hands over a `double` has already changed the document
   (section 7.6, rule 2).
5. SHOULD parse with a streaming parser and build only the document model defined here.

### 12.4 Extensions

`extensions` is arbitrary JSON from an untrusted source. A reader MUST treat it as opaque data.
It MUST NOT be interpreted as instructions, executed, used to select code paths that were not
explicitly enabled for that owner token, or passed to a deserializer that can construct
arbitrary objects. Nesting inside `extensions` is limited (section 12.2).

### 12.5 Binary objects

An attachment is a file a stranger sent. A reader MUST NOT open, render or execute it. In
particular:

* `filename` is attacker-controlled. It MUST NOT be used as a file system path. It may contain
  path separators, `..`, control characters, right-to-left override characters and a misleading
  double extension. A consumer that stores attachments MUST generate its own file name.
* `mimeCode` is attacker-controlled and MUST NOT be trusted as the actual type of the content.
  EN 16931-1, 6.5.11 names the media types a receiver is expected to accept; this is a
  statement about interoperability, not a guarantee about the bytes.
* The decoded size MUST be bounded before decoding (section 12.2). Base64 expands by a factor
  of 4/3, and a size check on the encoded string is a sufficient bound.

### 12.6 Values are data, not instructions

Every string in `values` comes from the sender. Values are displayed, printed, stored and
indexed; they MUST be escaped for whatever context they are put into — HTML, SQL, CSV, a shell,
a log file. A text value may contain line breaks, control characters permitted by JSON,
bidirectional formatting characters, or a leading `=` that a spreadsheet would treat as a
formula. None of that makes the document invalid, and none of it may be acted upon.

A validator's findings are the one place this specification does the escaping itself: a message
that quotes a value carries it escaped, by section 9.5, so that the report of a defect is not
the way the defect reaches a terminal. A finding's `subject` is escaped the same way but is not
shortened: what a hostile document must not decide is the length of a log line, and a `subject`
is a field a program reads rather than a line anyone writes out.

### 12.7 Digests and signatures

The digests of section 8 detect accidental modification and identify content. They are not
authentication: anyone who can change the document can change the digest. ESJ 0.1 defines no
signature format. An application that needs authenticity should sign the canonical bytes with
an established mechanism and keep the result outside the document.

### 12.8 Confidentiality

An invoice contains personal and commercial data. The canonical form and the semantic digest
are stable, which is useful for deduplication and equally useful to anyone who wants to
recognise a known document. A semantic digest published outside a trusted context can confirm a
guess about an invoice's content. Digests SHOULD be treated as data about the invoice, not as
opaque tokens.

---

## 13. Relational and key/value mapping (informative)

This section is informative. It is here because the flat shape of `values` is what makes ESJ
easy to store, and the mapping is worth writing down once.

### 13.1 A table of values

```sql
CREATE TABLE invoice_value (
  document_id     BIGINT       NOT NULL,
  path            VARCHAR(256) NOT NULL,
  content         TEXT         NOT NULL,
  scheme          VARCHAR(64),
  scheme_version  VARCHAR(64),
  mime_code       VARCHAR(128),
  filename        VARCHAR(255),
  sort_key        VARBINARY(320) NOT NULL,
  PRIMARY KEY (document_id, path)
);
```

One row per member of `values`: `content` holds the value, whether the document wrote it as a
string or as the `value` member of an object, and the four component columns are null where
the document has no such component. The primary key is the document plus the path, which is
exactly the uniqueness rule of section 5.5. Decimal values may additionally be stored in a
`NUMERIC` column; the string remains authoritative, because it is what the digests are
computed over.

There is **no type column**, and adding one would be a mistake. The semantic data type of a
row follows from its path through the registry (section 6.2): it is a property of the term,
not of the occurrence. A copy per row is a second place for one fact, it can disagree with the
registry after an edition changes, and it costs a column on every row of the largest table in
the schema. A join against a small term table — `(path_pattern, datatype, max_decimals,
code_list)` loaded from the registry — answers the same question and cannot drift.

`sort_key` is a byte string that reproduces the canonical path order (section 7.4) under plain
byte comparison, so that `ORDER BY sort_key` returns the canonical sequence without application
logic. One construction: encode each segment as one byte for the segment class (term or index),
one byte for the kind, the namespace padded or length-prefixed, and the number as a fixed-width
big-endian integer.

Useful indexes are `(path, content)` for "which invoices mention this value" and a partial index
on the paths an application queries often, for example `/BT-1` or `/BG-22/BT-115`.

### 13.2 A key/value store

The key is the document identifier followed by the path; the value is the content, or the
serialized value object where the value carries components. Any ordered key/value store then
returns a whole invoice, or a whole group instance, as one range scan: the prefix
`…/BG-25/3/` selects the fourth invoice line and everything under it, including its nested
groups.

Most values are strings with no component, so most entries are the content and nothing else —
which is what makes the store readable with the tooling that is already there.

### 13.3 Why this is not an accident

A path is a stable, human-readable key derived from a published standard. It does not change
when an application is refactored, a class is renamed or a library is replaced. That is the
practical reason for addressing values by EN 16931 identifiers rather than by field names: the
storage layer inherits the stability of the standard.

---

## 14. Non-goals (informative)

ESJ does not:

* replace EN 16931 or define invoice semantics,
* redefine taxation or any national VAT requirement,
* claim to be the authority on the business rules of CEN/TC 434 or of a CIUS — those
  artefacts and the CIUS owners are, and ESJ's own rule checks are verified against them,
* make a document legally valid by itself,
* preserve syntax details of UBL or CII, such as element order, prefixes, comments or
  attributes that carry no semantic content,
* guarantee a lossless round trip for data outside the EN 16931 semantic model,
* replace national profiles such as XRechnung,
* define transport, signing, encryption or archiving.

---

## 15. An ESJ document inside a PDF (informative)

This section is informative. It describes a convention, not a requirement of this
specification: nothing here changes what an ESJ document is, and a producer that follows none
of it is still conformant.

A hybrid invoice — a PDF/A-3 file carrying an electronic invoice as an embedded file — may
carry the same invoice as an ESJ document beside that invoice, for a consumer that would
rather read the semantic model than a syntax binding. The invoice XML remains the electronic
invoice of such a file; the ESJ document is an enclosure.

| | |
|---|---|
| File name | `invoice.esj.json` |
| Media type | `application/json` |
| Relationship | `Supplement`, referenced from the document catalog's `/AF` array and from `/Names /EmbeddedFiles` |
| Content | the canonical bytes of the document (section 7) |

**The agreement rule.** An ESJ document carried this way agrees with the invoice when both
hold:

1. both name the same semantic model (section 4.4);
2. the ESJ document satisfies layer L2 (section 3.5) under the registry of that model: every
   business term it states is a term of that edition, and every value satisfies the datatype,
   the supplementary components and the group chain the registry records for its term;
3. every value the invoice states is stated in the ESJ document with the same semantic path
   (section 5), the same content and the same supplementary components (section 6);
4. every value the ESJ document states and the invoice does not stands at a semantic path at
   least one of whose business terms the syntax binding of that invoice does not bind.

The ESJ document is measured on layers L1 and L2 and no further. Layer L3 and the business rules
are questions about the invoice, and the invoice of such a file is the invoice syntax, judged as
any invoice of that syntax is.

Condition 1 is not a formality: the model edition names the registry a consumer reads the
document under, and two editions need not agree on the scale of an amount, on which
supplementary components are mandatory or on the path a term stands at. Condition 2 is what
keeps condition 1 from being a statement about a header: an ESJ document naming one edition and
stating a business term of another would otherwise pass, because no syntax binds such a term and
condition 4 would take it in. Only errors of layer L2 count there; a path only an extension
registry defines is reported by that layer as not checked (section 11) and is not an error.

Condition 4 is what the ESJ document adds: a term of a model extension (section 11) has no
place in the invoice syntax, and carrying it is not a disagreement. A term the syntax does bind
is a term the invoice would have stated, so a difference there means the two files describe two
invoices. A value inside a group the syntax has no place for could not have reached the invoice
however well its own term is bound, which is why one unbound term of the path is enough.

A producer that cannot satisfy the rule — because the syntax binding had no place for a core
value — omits the ESJ document rather than writing one that disagrees. A consumer that finds
one may check the rule and, where it does not hold, treat the file as defective; the verdict is
about the file and not about the invoice in it, which is judged as any invoice of that syntax
is. The `extensions` member (section 4.6) takes no part in the rule: it holds data of no
business term, so there is no term for a binding to bind.

---

## Appendix A. Collected grammar (normative)

ABNF as in [RFC5234] with the case-sensitive literals of [RFC7405].

```abnf
; ---- value shape ---------------------------------------------------------
; A member of "values" is a JSON string, or a JSON object with the member
; "value" and at least one supplementary component. The object form without a
; component is an error (section 6.1). Written over JSON rather than over
; characters, and in the canonical member order of section 7.3, which is the
; order the production below derives. A document may write the members of a
; value object in any other order without changing what it means (section 4.2,
; rule 6, and section 9.6).
;
;   value        = content / value-object
;   value-object = "{" content-member 1*component-member "}"
;   content-member   = %s"value" ":" content
;   component-member = ( %s"scheme" / %s"schemeVersion" /
;                        %s"mimeCode" / %s"filename" ) ":" content
;   content      = a non-empty JSON string (section 6.1, rule 5)
;
; Which grammar below the content must satisfy is decided by the registry
; datatype of the term the path addresses, and is checked at layer L2
; (section 6.2).

; ---- model edition -------------------------------------------------------
; the value of the "semanticModel" member (section 4.4)
edition     = model-token [ "+" amendment ] [ "/" corrigendum ]
              *( "+" amendment [ "/" corrigendum ] )
model-token = 1*( ALPHA / DIGIT ) *( "-" 1*( ALPHA / DIGIT ) ) ":" year
amendment   = %s"A" 1*DIGIT ":" year
corrigendum = %s"AC" [ 1*DIGIT ] ":" year
year        = 4DIGIT

; ---- semantic path -------------------------------------------------------
path        = *( group-step ) bt-step

group-step  = "/" bg-term [ "/" index ]
bt-step     = "/" bt-term [ "/" index ]

bg-term     = %s"BG-" ( core-number / ext-suffix )
bt-term     = %s"BT-" ( core-number / ext-suffix )
ext-suffix  = namespace "-" ext-number

core-number = nonzero *DIGIT
ext-number  = 1*DIGIT
namespace   = UPPER *( UPPER / DIGIT )
index       = "0" / ( nonzero *DIGIT )

; ---- decimal content -----------------------------------------------------
; the content of a term whose registry datatype is Amount, UnitPriceAmount,
; Quantity or Percentage (sections 6.2 and 6.4)
; additional rules: the sign is absent when the value is zero, and the whole
; string is at most 64 characters long (section 6.4). The same production is
; the canonical form of a JSON number inside "extensions", which is obtained
; from that number's lexical form (section 7.6, rule 2).
decimal     = [ "-" ] int [ "." frac ]
int         = "0" / ( nonzero *DIGIT )
frac        = *DIGIT nonzero

; ---- date content --------------------------------------------------------
; the content of a term whose registry datatype is Date (sections 6.2 and 6.5)
; additional rule: the date exists in the proleptic Gregorian calendar
date        = year "-" month "-" day
year        = nonzero 3DIGIT      ; 1000 to 9999
month       = ( "0" nonzero ) / ( "1" %x30-32 )
day         = ( "0" nonzero ) / ( %x31-32 DIGIT ) / ( "3" %x30-31 )

; ---- time content --------------------------------------------------------
; the content of a term whose registry datatype is Time (sections 6.2 and 6.5)
; additional rule: the grammar admits +00:00 and -00:00, and UTC is written Z,
; so a numeric offset of zero is excluded beside the grammar (section 6.5). The
; offset is mandatory, and fractional seconds, 24:00:00 and the leap second :60
; are outside the rules below rather than beside them.
time         = hour ":" minute ":" second offset
hour         = ( ( "0" / "1" ) DIGIT ) / ( "2" %x30-33 )
minute       = %x30-35 DIGIT
second       = %x30-35 DIGIT
offset       = %s"Z" / ( ( "+" / "-" ) offset-value )
offset-value = ( ( ( "0" DIGIT ) / ( "1" %x30-33 ) ) ":" minute ) / "14:00"

; ---- binary object content -----------------------------------------------
; the content of a term whose registry datatype is BinaryObject (sections 6.2
; and 6.7); such a value always carries mimeCode and filename
; additional rule: the pad bits of the last quantum are zero (RFC 4648, 3.5),
; which is what b64tail2 and b64tail4 encode
b64         = *( 4b64char ) ( 4b64char / b64pad )
b64pad      = ( 2b64char b64tail2 "=" ) / ( b64char b64tail4 "==" )
b64char     = ALPHA / DIGIT / "+" / "/"
b64tail2    = "A" / "E" / "I" / "M" / "Q" / "U" / "Y" / "c" /
              "g" / "k" / "o" / "s" / "w" / "0" / "4" / "8"
b64tail4    = "A" / "Q" / "g" / "w"

; ---- extension owner token -----------------------------------------------
; additional rules: at most 128 characters, and not beginning with "BT-" or "BG-"
owner-token = owner-alnum [ *owner-char owner-alnum ]
owner-alnum = ALPHA / DIGIT
owner-char  = owner-alnum / "." / "_" / "-"

; ---- primitives ----------------------------------------------------------
nonzero     = %x31-39
UPPER       = %x41-5A
DIGIT       = %x30-39          ; as in RFC 5234
ALPHA       = %x41-5A / %x61-7A ; as in RFC 5234
```

---

## Appendix B. Worked example (informative)

A four-value document in pretty form. Three of its values carry no supplementary component
and are written as strings; the fourth carries a scheme and is written as an object
(section 6.1):

```json
{
  "format": "EN16931-Semantic-JSON",
  "version": "0.1",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "values": {
    "/BT-1": "RE-2026-0001",
    "/BT-2": "2026-01-15",
    "/BG-4/BT-29/0": {
      "value": "0088123456785",
      "scheme": "0088"
    },
    "/BG-25/0/BT-131": "100"
  }
}
```

Its canonical form is these 236 bytes, on one line:

```
{"format":"EN16931-Semantic-JSON","version":"0.1","semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"/BT-1":"RE-2026-0001","/BT-2":"2026-01-15","/BG-4/BT-29/0":{"value":"0088123456785","scheme":"0088"},"/BG-25/0/BT-131":"100"}}
```

Digests:

```
semantic digest  27d43ab507a929218e8278be315a736467d9a5f881bf232fe1843b06bd6105b8
document digest  fdc75e42190b6aec5cf5454fe07e615af71f33fc275e297cb6aeefec0525e42a
```

The document digest is SHA-256 over those 236 bytes. The semantic digest is SHA-256 over the
187 bytes of the two-member object of section 8.2 — the edition and the values, in that order,
canonically serialized:

```
{"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"/BT-1":"RE-2026-0001","/BT-2":"2026-01-15","/BG-4/BT-29/0":{"value":"0088123456785","scheme":"0088"},"/BG-25/0/BT-131":"100"}}
```

Three things in those bytes are worth pointing at:

* `/BG-4/BT-29/0` sorts after `/BT-2` and before `/BG-25/0/BT-131`, because a `BT` segment
  precedes a `BG` segment at the same position and 4 is numerically less than 25
  (section 7.4);
* inside the one value object, `value` precedes `scheme` (section 7.3, rule 3);
* `"100"` is a decimal and is written as a string like every other content, with no numeric
  type anywhere in the serialization.

Adding a `source` member changes the document digest and leaves the semantic digest unchanged.
`examples/extended.esj.json` and `examples/minimal.esj.json` are that pair as checked-in files.

This document is well formed but not conformant (section 3.1): it satisfies L1 but lacks the
mandatory terms the model requires at L3. The smallest document that passes all three layers is
`examples/minimal.esj.json`.

**The same content under the 2026 edition.** The document below carries those four values under
`EN16931-1:2026`, and one more: BT-166, the invoice issue time, whose semantic data type `Time`
that edition adds (section 6.5).

```json
{
  "format": "EN16931-Semantic-JSON",
  "version": "0.1",
  "semanticModel": "EN16931-1:2026",
  "values": {
    "/BT-1": "RE-2026-0001",
    "/BT-2": "2026-01-15",
    "/BT-166": "09:30:00+02:00",
    "/BG-4/BT-29/0": {
      "value": "0088123456785",
      "scheme": "0088"
    },
    "/BG-25/0/BT-131": "100"
  }
}
```

Its canonical form is these 247 bytes, on one line:

```
{"format":"EN16931-Semantic-JSON","version":"0.1","semanticModel":"EN16931-1:2026","values":{"/BT-1":"RE-2026-0001","/BT-2":"2026-01-15","/BT-166":"09:30:00+02:00","/BG-4/BT-29/0":{"value":"0088123456785","scheme":"0088"},"/BG-25/0/BT-131":"100"}}
```

Digests:

```
semantic digest  8f12f3ea4a038fa8877b9b8084793c0ff4d90190dfe6897b4a44b04d464f2d1c
document digest  38b3abe5cfa1d8f705844b7169b56d8aecd35ffbd9ae65ec0aaa83d4c8b7b10f
```

The document digest is SHA-256 over those 247 bytes, and the semantic digest over the 198
bytes of the two-member object of section 8.2:

```
{"semanticModel":"EN16931-1:2026","values":{"/BT-1":"RE-2026-0001","/BT-2":"2026-01-15","/BT-166":"09:30:00+02:00","/BG-4/BT-29/0":{"value":"0088123456785","scheme":"0088"},"/BG-25/0/BT-131":"100"}}
```

Two things in the pair are worth pointing at:

* `/BT-166` sorts after `/BT-2` and before `/BG-4/BT-29/0`: a `BT` segment precedes a `BG`
  segment at the same position and 166 is numerically greater than 2 (section 7.4). The
  edition plays no part in the order, and a canonicalizer produces these bytes without a
  registry — including one that has never heard of the 2026 edition (section 3.4).
* The semantic digest differs from the one above although the four shared values are spelled
  identically, because `semanticModel` is inside it (section 8.2). The two documents are two
  statements about two models.

---

## Appendix C. Example documents (informative)

`examples/` contains ten synthetic documents in pretty form. They use fictitious parties
(`Example GmbH`, `Muster AG`), fictitious VAT identifiers and the well-known example IBAN
`DE89370400440532013000`. Their arithmetic is self-consistent wherever the terms a rule relates
are present.

| File | What it shows |
|---|---|
| `minimal.esj.json` | only the mandatory terms of the model |
| `standard-invoice.esj.json` | a typical business invoice: addresses, contacts, credit transfer, one VAT breakdown, three lines |
| `multiple-lines.esj.json` | ten lines, periods, item attributes and classifications, an attachment, two VAT rates |
| `allowances.esj.json` | allowances on the document level and on a line |
| `charges.esj.json` | charges on the document level and on a line, a tax representative, a payment card |
| `self-billed.esj.json` | invoice type 389, a payee, a direct debit, a reverse charge line |
| `credit-note.esj.json` | invoice type 381 with a preceding invoice reference |
| `extended.esj.json` | the values of `minimal.esj.json` plus `extensions` and `source`, built as a trap for section 7.6 |
| `extension-depth.esj.json` | the values of `minimal.esj.json` plus an `extensions` subtree nested exactly 32 levels deep: the deepest the reference configuration of section 12.2 accepts |
| `b2c-gross.esj.json` | a three-line consumer invoice carrying the four terms of the B2C extension beside the net core terms |

Each document has a `*.canonical.esj.json` twin holding its canonical bytes (section 7). A
conformant canonicalizer applied to the pretty document MUST reproduce that file byte for byte;
the twins are the cross-implementation check of section 3.4.

Between them the ten carry values of both shapes: most are strings, and identifiers with a
scheme, item classifications with a scheme version and the one attachment are written as value
objects (section 6.1).

Eight of the ten exercise section 7.3 to 7.5 only, because they carry neither `extensions` nor
`source`. `extended.esj.json` exists for section 7.6, which is where two implementations diverge
in silence. Its `extensions` subtree contains, deliberately: two member names whose relative
order differs between Unicode code point order and UTF-16 code unit order, so that an
implementation that reaches for a JCS library without checking how it sorts produces different
bytes and therefore a different document digest; numbers whose spelling the lexical
canonicalization of section 7.6, rule 2 has to change — `1e21`, `1e-6`, `1e-7` and a negative
zero written once as an integer and once as a fraction — beside two numbers it has to leave
alone although a double would not hold them: a twenty-digit integer and `1.0000000000000001`;
an array whose element order must survive; and `true`, `false` and `null`. Its `source` carries
both members, so the member order of section 7.3, rule 4 is exercised too.

The numbers keep their input spelling in the pretty file, which section 7.7 allows: the file is
there to be canonicalized, and an implementation that reached for a JCS library would produce
`1e+21`, `12345678901234567000` and `1` for three of them instead.

Because `extended.esj.json` has exactly the `values` of `minimal.esj.json`, the two documents
have the **same semantic digest** and different document digests. That is section 8.4 in one
pair of files.

`extension-depth.esj.json` exists for the one limit of section 12.2 whose counting base two
implementations can read differently. It sits on the accepting side of the boundary;
`examples/invalid/extension-depth-33.esj.json` sits one level past it, and the pair pins the
base down as a document rather than as prose.

`examples/invalid/` contains documents that MUST be rejected, one per kind of error, with a
note in its README naming the layer and the finding code that reject each one.

---

## References

**Normative**

| Key | Document |
|---|---|
| [EN16931-1] | EN 16931-1:2017+A1:2019, *Electronic invoicing — Part 1: Semantic data model of the core elements of an electronic invoice*, together with EN 16931-1:2017+A1:2019/AC:2020 |
| [EN16931-1-2026] | EN 16931-1:2026, *Electronic invoicing — Part 1: Semantic data model of the core elements of an electronic invoice* |
| [RFC2119] | S. Bradner, *Key words for use in RFCs to Indicate Requirement Levels*, BCP 14, RFC 2119, March 1997 |
| [RFC8174] | B. Leiba, *Ambiguity of Uppercase vs Lowercase in RFC 2119 Key Words*, BCP 14, RFC 8174, May 2017 |
| [RFC8259] | T. Bray, Ed., *The JavaScript Object Notation (JSON) Data Interchange Format*, STD 90, RFC 8259, December 2017 |
| [RFC8785] | A. Rundgren, B. Jordan, S. Erdtman, *JSON Canonicalization Scheme (JCS)*, RFC 8785, June 2020 |
| [RFC4648] | S. Josefsson, *The Base16, Base32, and Base64 Data Encodings*, RFC 4648, October 2006 |
| [RFC5234] | D. Crocker, Ed., P. Overell, *Augmented BNF for Syntax Specifications: ABNF*, STD 68, RFC 5234, January 2008 |
| [RFC7405] | P. Kyzivat, *Case-Sensitive String Support in ABNF*, RFC 7405, December 2014 |
| [FIPS180-4] | NIST, *Secure Hash Standard (SHS)*, FIPS PUB 180-4, August 2015 (SHA-256) |
| [JSONSchema] | *JSON Schema: A Media Type for Describing JSON Documents*, draft 2020-12 |

**Informative**

| Key | Document |
|---|---|
| [CENTS16931-2] | CEN/TS 16931-2:2017, *Electronic invoicing — Part 2: List of syntaxes that comply with EN 16931-1* |
| [ISO8601-1] | ISO 8601-1:2019, *Date and time — Representations for information interchange — Part 1: Basic rules* |
| [ISO4217] | ISO 4217, *Codes for the representation of currencies* |
| [ISO3166-1] | ISO 3166-1, *Codes for the representation of names of countries and their subdivisions — Part 1: Country codes* |
| [ISO15000-5] | ISO 15000-5:2014, *Electronic business eXtensible Markup Language (ebXML) — Part 5: Core Components Specification (CCS)* |
| [UNTDID] | UN/CEFACT, *United Nations Trade Data Interchange Directory*, code lists 1001, 2005, 4451, 4461, 5189, 5305, 7143, 7161 |
| [RECS20] | UNECE Recommendation 20, *Codes for Units of Measure Used in International Trade*, with the Recommendation 21 extension |
| [RFC4151] | T. Kindberg, S. Hawke, *The 'tag' URI Scheme*, RFC 4151, October 2005 |
| [ECMA-262] | Ecma International, *ECMAScript Language Specification* |
| [CEN-VA] | CEN/TC 434, *eInvoicing validation artefacts*, the Schematron rule sets for the UBL and CII bindings |

`docs/sources.md` records where these documents can be obtained and under which licence.
