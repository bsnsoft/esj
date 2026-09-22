# Negative fixtures

Each file is `minimal.esj.json` with exactly one defect injected; a few add the smallest
complete group that can carry the defect, and a few carry a second defect on purpose (below).
`edition-2026-time-without-offset.esj.json` is the same document under the 2026 edition, the
one that has a term of the semantic data type Time; it is listed in
`model/en16931/2026.paths` and is absent from a build made with the Maven profile
`without-edition-2026`. Every one of them MUST be rejected. The layer column names the
validation layer of SPEC.md section 9 that catches the defect, the finding code column the
code of SPEC.md section 9.6 reported for it, and the schema column says whether
`schema/esj.schema.json` alone is enough.

The format schema constrains the **shape** of a value and nothing about its content, because it
knows no business terms (SPEC.md section 6.2). Whether `100.00` is a canonical decimal, whether
`2026-02-30` is a day and whether `QUJDRR==` is canonical base64 are questions about the
semantic data type the registry records for the term: layer L2, which no format schema reaches.
`schema/esj-en16931-2017.schema.json`, generated from the registry, carries the per-term
grammars and catches the first and the third.

| File | Defect | Layer | Finding code | Rejected by the schema |
|---|---|---|---|---|
| `number-instead-of-string.esj.json` | BT-129 is the JSON number `1` instead of a string | L1 | `ESJ-L1-JSON-TYPE` | yes |
| `object-without-component.esj.json` | BT-1 is written `{"value": "RE-2026-0001"}`, an object with no supplementary component | L1 | `ESJ-L1-VALUE-SHAPE` | yes |
| `value-not-a-string.esj.json` | the `value` member of BT-29 is a JSON object instead of a string | L1 | `ESJ-L1-VALUE-SHAPE` | yes |
| `unknown-object-member.esj.json` | a value object carries a `type` member, which is not one of the five members SPEC.md section 6.1 defines | L1 | `ESJ-L1-VALUE-MEMBER` | yes |
| `scheme-version-without-scheme.esj.json` | BT-158 carries `schemeVersion` but no `scheme` | L1 | `ESJ-L1-VALUE-MEMBER` | yes |
| `empty-string-value.esj.json` | BT-27 is the empty string | L1 | `ESJ-L1-EMPTY-STRING` | yes |
| `empty-string-with-missing-term.esj.json` | BT-27 is the empty string, and the invoice line has no BT-130 either | L1 | `ESJ-L1-EMPTY-STRING` | yes |
| `unknown-envelope-member.esj.json` | an extra top level member `profile` | L1 | `ESJ-L1-ENVELOPE-MEMBER` | yes |
| `path-leading-zero-index.esj.json` | `/BG-4/BT-29/00` — an occurrence index with a leading zero | L1 | `ESJ-L1-PATH-SYNTAX` | yes |
| `owner-token-syntax.esj.json` | the extension owner `urn:example:v/2` is outside the grammar of SPEC.md section 4.6 | L1 | `ESJ-L1-OWNER-TOKEN` | yes |
| `empty-extensions.esj.json` | `extensions` is present and empty | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `source-empty-syntax.esj.json` | `source.syntax` is the empty string, which SPEC.md section 4.7 forbids | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `source-sha256-uppercase.esj.json` | `source.sha256` is written in uppercase hexadecimal | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `duplicate-member.esj.json` | `/BT-1` appears twice in `values` | L1 (reader only) | `ESJ-L1-DUPLICATE-MEMBER` | no |
| `duplicate-in-value-object.esj.json` | the value object at BT-29 carries `scheme` twice | L1 (reader only) | `ESJ-L1-DUPLICATE-MEMBER` | no |
| `duplicate-in-two-value-objects.esj.json` | two value objects each carry `scheme` twice | L1 (reader only) | `ESJ-L1-DUPLICATE-MEMBER` | no |
| `duplicate-path-of-value-objects.esj.json` | `/BT-1` appears twice in `values`, each time as an object with no supplementary component | L1 (reader only) | `ESJ-L1-VALUE-SHAPE` and `ESJ-L1-DUPLICATE-MEMBER` | yes, for the shape alone |
| `surrogate-and-duplicate-in-value-object.esj.json` | a value object carrying a member named `\ud800` and, after it, `scheme` twice | L1 | `ESJ-L1-SURROGATE` | yes, for the undefined member |
| `duplicate-and-surrogate-in-value-object.esj.json` | the same members with the repeated name written first | L1 | `ESJ-L1-DUPLICATE-MEMBER` | yes, for the undefined member |
| `surrogate-in-envelope-member-name.esj.json` | an envelope member named with the unpaired escape `\ud800` | L1 | `ESJ-L1-SURROGATE` | yes |
| `surrogate-in-source-member-name.esj.json` | a member of `source` named with the unpaired escape `\ud800` | L1 | `ESJ-L1-SURROGATE` | yes |
| `surrogate-in-path.esj.json` | a member name of `values` carrying the unpaired escape `\ud800` | L1 | `ESJ-L1-SURROGATE` | yes |
| `duplicate-below-bad-owner-token.esj.json` | a repeated member name below the owner `urn:example:v/2`, which is not an owner token | L1 | `ESJ-L1-OWNER-TOKEN` | yes |
| `surrogate-below-bad-owner-token.esj.json` | a member name carrying `\ud800` below the same owner | L1 | `ESJ-L1-OWNER-TOKEN` | yes |
| `lone-surrogate.esj.json` | BT-27 contains the unpaired escape `\ud800` | L1 | `ESJ-L1-SURROGATE` | no |
| `surrogate-in-shapeless-value-object.esj.json` | BT-1 is an object with no supplementary component whose `value` carries the unpaired escape `\ud800` | L1 | `ESJ-L1-SURROGATE` and `ESJ-L1-VALUE-SHAPE` | yes, for the shape alone |
| `source-lone-surrogate.esj.json` | `source.syntax` contains the unpaired escape `\ud800` | L1 | `ESJ-L1-SURROGATE` | no |
| `extension-number-too-long.esj.json` | `1e400` inside `extensions`: its canonical decimal form is 401 characters long | L1 | `ESJ-L1-EXT-NUMBER` | no |
| `extension-depth-33.esj.json` | the `extensions` subtree is nested 33 levels deep, one past the default of SPEC.md section 12.2 | limit | `ESJ-L1-LIMIT` | no |
| `value-object-members-17.esj.json` | the value object at BT-1 carries 17 members, one past the default of SPEC.md section 12.2 | limit | `ESJ-L1-LIMIT` | yes, for another reason |
| `decimal-trailing-zeros.esj.json` | BT-106 is written `100.00`, which is not the canonical spelling of that decimal | L2 | `ESJ-L2-DECIMAL` | no |
| `b2c-decimal-trailing-zeros.esj.json` | BT-B2C-001 of `model/b2c/0.1.json` is written `119.0000`; the fixture is measured with that registry loaded | L2 | `ESJ-L2-DECIMAL` | no |
| `decimal-too-long.esj.json` | BT-129 is a 65-character decimal, one over the bound of SPEC.md section 6.4 | L2 | `ESJ-L2-DECIMAL` | no |
| `calendar-impossible-date.esj.json` | BT-2 is `2026-02-30`, which matches the date pattern but is not a day | L2 | `ESJ-L2-DATE` | no |
| `edition-2026-time-without-offset.esj.json` | BT-166 is `09:15:00`, a time without the offset the grammar of SPEC.md section 6.5 requires | L2 | `ESJ-L2-TIME` | no |
| `base64-non-canonical.esj.json` | BT-125 is `QUJDRR==`, whose last quantum has non-zero pad bits | L2 | `ESJ-L2-BASE64` | no |
| `binary-without-filename.esj.json` | BT-125 carries `mimeCode` but not `filename`, which the registry declares mandatory | L2 | `ESJ-L2-COMPONENT-MISSING` | no |
| `scheme-on-text-value.esj.json` | a `scheme` on BT-27, whose registry datatype is `Text` and which lists no component | L2 | `ESJ-L2-COMPONENT-NOT-ALLOWED` | no |
| `unknown-term.esj.json` | `/BT-999` — a business term that does not exist in the model | L2 | `ESJ-L2-UNKNOWN-TERM` | no |
| `index-on-bt-1.esj.json` | `/BT-1/0` — an occurrence index on a term declared `1..1` | L2 | `ESJ-L2-INDEX-FORBIDDEN` | no |
| `index-gap.esj.json` | the only VAT breakdown sits at index 2, so indices 0 and 1 are missing | L3 | `ESJ-L3-INDEX-GAP` | no |
| `missing-mandatory-term.esj.json` | the invoice line has no BT-130, which the registry declares mandatory | L3 | `ESJ-L3-MISSING-TERM` | no |
| `arithmetic-mismatch.esj.json` | BT-115 does not equal BT-112 − BT-113 + BT-114 | business rule | none | no |

Some of these deserve a note.

The four fixtures at the top are the value shape of SPEC.md section 6.1 from four sides: a
value that is nothing but content in the object form, which rule 3 forbids so that one content
has one canonical form; a `type` member, which an earlier draft of this format defined and this
one does not; a JSON object where the content belongs; and a JSON number where a whole value
belongs — the two halves of rule 5 of SPEC.md section 4.2.

`scheme-version-without-scheme.esj.json` is an L1 value member error (SPEC.md section 6.6,
rule 4). BT-158 declares `scheme` mandatory as well, but the model layers are not evaluated
over a document L1 rejected (SPEC.md section 9.5), so the L1 finding is the whole answer.

The five L2 content fixtures — the two decimals, the date, the base64 and the missing binary
component — need the registry: without a type token in the document, nothing else says that
BT-106 carries a decimal and BT-2 a date, so a validator run without one accepts all five, and
must. `decimal-trailing-zeros.esj.json` pins SPEC.md section 6.4: `100.00` is an error and is
never made into `100` on the way through.

`duplicate-member.esj.json` is not valid JSON in the sense of SPEC.md section 4.2, and a JSON
Schema validator never sees the defect: the parser in front of it has collapsed the two members
into one. With `duplicate-in-value-object.esj.json` it fixes what the finding points at: a name
repeated in `values` is no semantic path and carries none, while one repeated inside a value
object carries that member's path (SPEC.md section 9.5).

Nine fixtures are about a member name the reader cannot take; the code is `ESJ-L1-SURROGATE`
and not `ESJ-L1-JSON`, an escaped surrogate being well-formed JSON and only ill-formed Unicode.
SPEC.md section 9.6 decides three things about such a name that a reader cannot otherwise
learn.

| What is pinned | By |
|---|---|
| the rule reaches the envelope, `source` and `values` as it reaches a value object, so the code is `ESJ-L1-SURROGATE` and not `ESJ-L1-ENVELOPE-MEMBER` | `surrogate-in-envelope-member-name`, `surrogate-in-source-member-name`, `surrogate-in-path` |
| where one object carries both defects, the one the text reaches first is the one reported | `surrogate-and-duplicate-in-value-object`, `duplicate-and-surrogate-in-value-object` |
| the reader stops there and reports nothing after it | `duplicate-in-two-value-objects`, `duplicate-path-of-value-objects`, `duplicate-below-bad-owner-token`, `surrogate-below-bad-owner-token` |

`extension-number-too-long.esj.json` carries a legal JSON number. SPEC.md section 7.6, rule 2
makes it an error: a number inside `extensions` is canonicalized from its lexical form, and
`1e400` canonicalizes to a one followed by four hundred zeros, past the 64-character bound of
section 6.4. The code is `ESJ-L1-EXT-NUMBER` although the same bound inside `values` is an L2
one, because no registry is needed to know that the thing measured is a number. No floating
point type decides it, here or anywhere else in ESJ.

`extension-depth-33.esj.json` exceeds a configured limit, and SPEC.md section 3.1 keeps limits
out of conformance: a reader with a larger bound accepts it and is right to. `ESJ-L1-LIMIT`
says *not processed under this configuration*. With `examples/extension-depth.esj.json` (32
levels, accepted) it leaves no second reading of where the depth count starts: an
implementation that counts the `extensions` object itself as a level refuses the first, one
that counts a scalar as a level refuses it too. A reader may signal the limit by an exception
(SPEC.md section 9.5), and the reference implementation does.

`value-object-members-17.esj.json` is the second limit fixture. A value object has at most five
members (SPEC.md section 6.1), so the count never makes a real document wrong; the bound keeps
the collection a reader makes before it judges the set from being unbounded. Its schema column
says yes for another reason than its name — fifteen of the seventeen members are undefined
there — and a reader with a larger bound reports `ESJ-L1-VALUE-MEMBER` on the first of them.

`empty-string-with-missing-term.esj.json` is here for what a validator must **not** say about
it: the member L1 refused is absent from the document the reader hands back, so a validator
that ran the model layers over it anyway would report BT-130 missing — and BT-27 missing from
a document in which BT-27 stands. SPEC.md section 9.5 names L2 and L3 as not evaluated for the
reason `PRECEDING-LAYER-FAILED`, so the only finding is the L1 one.
`surrogate-in-shapeless-value-object.esj.json` draws two codes at once: SPEC.md section 9.6
gives `ESJ-L1-SURROGATE` precedence over every check that reads the same string and leaves a
check that reads the shape of the object untouched by it, so both are reported and the manifest
pins both.

A byte order mark is the one L1 defect with no fixture here: a file carrying one is not a JSON
text at all, so a fixture would test the tooling rather than the format (SPEC.md section 9.1).

`source-lone-surrogate.esj.json` is `ESJ-L1-SURROGATE` and not `ESJ-L1-ENVELOPE-VALUE`, SPEC.md
section 9.6 giving that code precedence over every check that reads the same string.
`source-empty-syntax.esj.json` and `source-sha256-uppercase.esj.json` are the pair for a
`source` of the wrong shape: both members are present and both are JSON strings, so nothing
about the envelope's types is wrong, and SPEC.md section 9.6 names `ESJ-L1-ENVELOPE-VALUE` for
the shape of section 4.7 explicitly.

`b2c-decimal-trailing-zeros.esj.json` is the one fixture no core registry can judge: BT-B2C-001
belongs to `model/b2c/0.1.json`, and a party without that registry reports `ESJ-L2-NOT-CHECKED`
for the path and nothing else (SPEC.md section 5.6). It is what makes an implementation that
ships the core registries alone answer differently, and the manifest names the registries every
document was measured with.

`arithmetic-mismatch.esj.json` is structurally conformant. The examples claim to add up, and a
checker of that claim has to be shown failing where it does not. Business rules are a separate
layer this version does not check; see SPEC.md section 9.4.
