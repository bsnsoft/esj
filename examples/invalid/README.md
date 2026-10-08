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
| `encoding-byte-order-mark.esj.json` | the UTF-8 byte order mark `EF BB BF` before the document | L1 | `ESJ-L1-ENCODING` | no, not a JSON text |
| `encoding-not-utf8.esj.json` | BT-27 carries `ü` as the single ISO-8859-1 byte `FC` | L1 | `ESJ-L1-ENCODING` | no |
| `json-utf16-without-bom.esj.json` | the document encoded in UTF-16LE without a byte order mark: valid UTF-8, with a NUL byte after every character | L1 | `ESJ-L1-JSON` | no, not a JSON text |
| `json-trailing-comma.esj.json` | a comma after the last member of `values` | L1 | `ESJ-L1-JSON` | no, not a JSON text |
| `envelope-missing-values.esj.json` | the envelope has no `values` | L1 | `ESJ-L1-ENVELOPE-MEMBER` | yes |
| `envelope-wrong-version.esj.json` | `version` is `1.0` | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `semantic-model-grammar.esj.json` | `semanticModel` is `EN16931-1`, without the year the edition grammar of SPEC.md section 4.4 requires | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `source-unknown-member.esj.json` | `source` carries a member `profile`, which SPEC.md section 4.7 does not define | L1 | `ESJ-L1-ENVELOPE-MEMBER` | yes |
| `number-instead-of-string.esj.json` | BT-129 is the JSON number `1` instead of a string | L1 | `ESJ-L1-JSON-TYPE` | yes |
| `object-without-component.esj.json` | BT-1 is written `{"value": "RE-2026-0001"}`, an object with no supplementary component | L1 | `ESJ-L1-VALUE-SHAPE` | yes |
| `value-not-a-string.esj.json` | the `value` member of BT-29 is a JSON object instead of a string | L1 | `ESJ-L1-VALUE-SHAPE` | yes |
| `unknown-object-member.esj.json` | a value object carries a `type` member, which is not one of the five members SPEC.md section 6.1 defines | L1 | `ESJ-L1-VALUE-MEMBER` | yes |
| `scheme-version-without-scheme.esj.json` | BT-158 carries `schemeVersion` but no `scheme` | L1 | `ESJ-L1-VALUE-MEMBER` | yes |
| `empty-string-value.esj.json` | BT-27 is the empty string | L1 | `ESJ-L1-EMPTY-STRING` | yes |
| `empty-string-with-missing-term.esj.json` | BT-27 is the empty string, and the invoice line has no BT-130 either | L1 | `ESJ-L1-EMPTY-STRING` | yes |
| `unknown-envelope-member.esj.json` | an extra top level member `profile` | L1 | `ESJ-L1-ENVELOPE-MEMBER` | yes |
| `envelope-member-terminal-characters.esj.json` | an extra top level member whose name carries an escape sequence, a bell, NEXT LINE, U+2028, U+061C and U+202E | L1 | `ESJ-L1-ENVELOPE-MEMBER` | yes |
| `values-deep-array.esj.json` | `values` is an array nested 40 levels deep, past the nesting bound | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `path-leading-zero-index.esj.json` | `/BG-4/BT-29/00` — an occurrence index with a leading zero | L1 | `ESJ-L1-PATH-SYNTAX` | yes |
| `path-syntax-with-array-value.esj.json` | `/BG-25/0/BT 129` is no path, and the value under it is an array | L1 | `ESJ-L1-PATH-SYNTAX` and `ESJ-L1-JSON-TYPE` | yes |
| `path-syntax-terminal-characters.esj.json` | a member of `values` named `/BT-1` followed by one character of every class SPEC.md section 9.5 escapes, a tab, a quotation mark and a backslash | L1 | `ESJ-L1-PATH-SYNTAX` | yes |
| `owner-token-syntax.esj.json` | the extension owner `urn:example:v/2` is outside the grammar of SPEC.md section 4.6 | L1 | `ESJ-L1-OWNER-TOKEN` | yes |
| `owner-token-terminal-characters.esj.json` | an extension owner carrying C1 controls, U+2028, U+2029 and bidirectional controls | L1 | `ESJ-L1-OWNER-TOKEN` | yes |
| `empty-extensions.esj.json` | `extensions` is present and empty | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `source-empty-syntax.esj.json` | `source.syntax` is the empty string, which SPEC.md section 4.7 forbids | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `source-sha256-uppercase.esj.json` | `source.sha256` is written in uppercase hexadecimal | L1 | `ESJ-L1-ENVELOPE-VALUE` | yes |
| `duplicate-member.esj.json` | `/BT-1` appears twice in `values` | L1 (reader only) | `ESJ-L1-DUPLICATE-MEMBER` | no |
| `duplicate-envelope-member.esj.json` | `format` appears twice in the envelope | L1 (reader only) | `ESJ-L1-DUPLICATE-MEMBER` | no |
| `duplicate-in-extensions.esj.json` | a member name occurs twice below the owner token `de.example.vendor` | L1 (reader only) | `ESJ-L1-DUPLICATE-MEMBER` | no |
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
| `extension-number-exponent-overflow.esj.json` | `1e99999999999999999999` inside `extensions`: 23 characters, a canonical form of 10^20 digits | L1 | `ESJ-L1-EXT-NUMBER` | no |
| `extension-number-exponent-underflow.esj.json` | `1e-99999999999999999999` inside `extensions` | L1 | `ESJ-L1-EXT-NUMBER` | no |
| `extension-depth-33.esj.json` | the `extensions` subtree is nested 33 levels deep, one past the default of SPEC.md section 12.2 | limit | `ESJ-L1-LIMIT` | no |
| `value-depth-32.esj.json` | BT-129 is an array nested 32 levels deep | L1 | `ESJ-L1-JSON-TYPE` | yes |
| `value-depth-33.esj.json` | the same nested 33 levels, one past the default of SPEC.md section 12.2 for the walk past it | limit | `ESJ-L1-LIMIT` | yes, for another reason |
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
| `parent-chain.esj.json` | `/BG-4/BT-2`: BT-2 belongs to the document root, not to the seller group | L2 | `ESJ-L2-PARENT-CHAIN` | no |
| `index-required.esj.json` | `/BG-4/BT-29` without the occurrence index BT-29 carries as a repeatable term | L2 | `ESJ-L2-INDEX-REQUIRED` | no |
| `index-on-bt-1.esj.json` | `/BT-1/0` — an occurrence index on a term declared `1..1` | L2 | `ESJ-L2-INDEX-FORBIDDEN` | no |
| `index-gap.esj.json` | the only VAT breakdown sits at index 2, so indices 0 and 1 are missing | L3 | `ESJ-L3-INDEX-GAP` | no |
| `missing-mandatory-group.esj.json` | no buyer: BG-7, declared mandatory, has no instance | L3 | `ESJ-L3-MISSING-GROUP` | no |
| `missing-mandatory-term.esj.json` | the invoice line has no BT-130, which the registry declares mandatory | L3 | `ESJ-L3-MISSING-TERM` | no |
| `arithmetic-mismatch.esj.json` | BT-115 does not equal BT-112 − BT-113 + BT-114 | business rule | none | no |

Three fixtures are no JSON text, so a schema validator cannot parse them. The variants of each
kind of error are in [`conformance/fixtures/reader/`](../../conformance/fixtures/reader/README.md)
and [`conformance/fixtures/model/`](../../conformance/fixtures/model/README.md).

The four fixtures at the top are the value shape of SPEC.md section 6.1 from four sides: content
alone in the object form (rule 3), a `type` member of an earlier draft, a JSON object where the
content belongs, and a JSON number where a whole value belongs (section 4.2, rule 5).

`scheme-version-without-scheme.esj.json` is an L1 value member error (SPEC.md section 6.6,
rule 4). BT-158 declares `scheme` mandatory as well, but the model layers are not evaluated
over a document L1 rejected (SPEC.md section 9.5), so the L1 finding is the whole answer.

The five L2 content fixtures — the two decimals, the date, the base64 and the missing binary
component — need the registry, which alone says that BT-106 carries a decimal and BT-2 a date.
`decimal-trailing-zeros.esj.json` pins SPEC.md section 6.4: `100.00` is never made into `100`.

`duplicate-member.esj.json` is a defect a JSON Schema validator never sees: its parser has
collapsed the two members into one. With `duplicate-in-value-object.esj.json` it fixes what the
finding points at: a name repeated in `values` carries no path, one repeated inside a value object
carries that member's path (SPEC.md section 9.5).

Nine fixtures are about a member name the reader cannot take: `ESJ-L1-SURROGATE`, an escaped
surrogate being well-formed JSON and ill-formed Unicode. SPEC.md section 9.6 decides three things.

| What is pinned | By |
|---|---|
| the rule reaches the envelope, `source` and `values` as it reaches a value object, so the code is `ESJ-L1-SURROGATE` and not `ESJ-L1-ENVELOPE-MEMBER` | `surrogate-in-envelope-member-name`, `surrogate-in-source-member-name`, `surrogate-in-path` |
| where one object carries both defects, the one the text reaches first is the one reported | `surrogate-and-duplicate-in-value-object`, `duplicate-and-surrogate-in-value-object` |
| the reader stops there and reports nothing after it | `duplicate-in-two-value-objects`, `duplicate-path-of-value-objects`, `duplicate-below-bad-owner-token`, `surrogate-below-bad-owner-token` |

`extension-number-too-long.esj.json` carries a legal JSON number whose canonical form (SPEC.md
section 7.6, rule 2) is 401 characters, past the bound of section 6.4; no registry is needed to
know it is a number, so the code is of layer L1. The two `extension-number-exponent-*` fixtures
have exponents past every integer type: a reader sizes the canonical form and never writes it.

`extension-depth-33.esj.json` exceeds a configured limit (SPEC.md section 3.1): a reader with a
larger bound accepts it. With `examples/extension-depth.esj.json` (32 levels, accepted) it fixes
where the depth count starts: counting the `extensions` object or a scalar as a level refuses both. `value-depth-32` and `value-depth-33` fix the
same base for the walk past a structure inside `values`: the value of a member of `values` is
level 1 (SPEC.md section 12.2). `values-deep-array` is no limit: `values` is refused at its first
token and nothing after it is walked.

`value-object-members-17.esj.json` is the second limit fixture: a value object has at most five
members (SPEC.md section 6.1), and the bound keeps what a reader collects before it judges the set
bounded. The schema refuses it for its undefined members; a larger bound draws `ESJ-L1-VALUE-MEMBER`.

`empty-string-with-missing-term.esj.json` is here for what a validator must **not** say about
it: the member L1 refused is absent from the document the reader hands back, so a validator
that ran the model layers over it anyway would report BT-130 missing — and BT-27 missing from
a document in which BT-27 stands. SPEC.md section 9.5 names L2 and L3 as not evaluated for the
reason `PRECEDING-LAYER-FAILED`, so the only finding is the L1 one.
`surrogate-in-shapeless-value-object.esj.json` draws two codes at once: SPEC.md section 9.6
gives `ESJ-L1-SURROGATE` precedence over every check that reads the same string and leaves a
check that reads the shape of the object untouched by it, so both are reported and the manifest
pins both. `path-syntax-with-array-value.esj.json` draws two codes as well: a name that is no path
is confined to its member, and the value under it is judged all the same (section 9.6).

The three `*-terminal-characters` fixtures carry, in a member name, characters SPEC.md section
9.5 escapes in a `subject` — C0 and C1 controls, DEL, U+2028, U+2029, bidirectional formatting
characters — and the manifest records each subject as the reference escapes it.

`source-lone-surrogate.esj.json` is `ESJ-L1-SURROGATE` and not `ESJ-L1-ENVELOPE-VALUE` (SPEC.md
section 9.6). `source-empty-syntax.esj.json` and `source-sha256-uppercase.esj.json` are a `source`
of the wrong shape with members of the right type, which section 9.6 names
`ESJ-L1-ENVELOPE-VALUE` explicitly.

`b2c-decimal-trailing-zeros.esj.json` is the one fixture no core registry can judge: BT-B2C-001
belongs to `model/b2c/0.1.json`, and without that registry a party reports `ESJ-L2-NOT-CHECKED`
(SPEC.md section 5.6); the manifest names the registries every document was measured with.

`arithmetic-mismatch.esj.json` is structurally conformant and breaks a business rule, a separate
layer (SPEC.md section 9.4).
