# Reader fixtures

*Part of the [fixture manifest](../README.md).*

Documents layer L1 refuses, one variant of a defect each, for the defects `SPEC.md` sections 4,
9.5 and 9.6 decide: the encoding, the JSON text, the envelope, `source`, `extensions`, the order in
which a name and its value are judged, and the escaping of a subject. `../../../examples/invalid/`
holds one document per kind of error; these are the variants a second reader is most likely to
answer differently. The manifest records what each draws, in its `invalid` section.

Each file is the example of `SPEC.md` section 4.1, written on one line, with the defect named
below; the bytes of the encoding fixtures are named in hexadecimal. The files are not pretty
printed, because several of them are no JSON text at all.

| File | Defect |
|---|---|
| `encoding-overlong.esj.json` | BT-1 carries `C0 AF`, the overlong two-byte form of `/` |
| `encoding-encoded-surrogate.esj.json` | BT-1 carries `ED A0 80`, U+D800 encoded as bytes |
| `encoding-above-unicode.esj.json` | BT-1 carries `F4 90 80 80`, a code point above U+10FFFF |
| `encoding-truncated-sequence.esj.json` | BT-1 ends in `E2 82`, the first two bytes of a three-byte sequence |
| `encoding-stray-continuation.esj.json` | BT-1 carries `80`, a continuation byte with no lead byte |
| `encoding-utf16-with-bom.esj.json` | the example in UTF-16LE with its byte order mark FF FE |
| `encoding-decided-first.esj.json` | a member name that is no path, followed by the byte FF: the encoding is decided first and alone |
| `json-utf16be-without-bom.esj.json` | the example in UTF-16BE without a byte order mark |
| `json-utf32le-without-bom.esj.json` | the example in UTF-32LE without a byte order mark |
| `json-utf32be-without-bom.esj.json` | the example in UTF-32BE without a byte order mark |
| `json-empty.esj.json` | an empty byte sequence |
| `json-whitespace-only.esj.json` | a space and a line feed and nothing else |
| `json-top-level-array.esj.json` | the example inside a JSON array |
| `json-top-level-string.esj.json` | a JSON string at the top level |
| `json-trailing-text.esj.json` | the example followed by ` x` |
| `json-two-objects.esj.json` | the example written twice |
| `json-missing-comma.esj.json` | no comma between two members of `values` |
| `json-trailing-comma-in-envelope.esj.json` | a comma after the last envelope member |
| `json-missing-colon.esj.json` | `{"format" 1}`: a defined name, then no colon |
| `json-unclosed-string.esj.json` | the text ends inside a string |
| `json-unclosed-object.esj.json` | the text ends before `values` and the envelope are closed |
| `json-leading-zero-in-values.esj.json` | `"/BT-1": 01`, a number with a leading zero |
| `json-leading-zero-in-envelope.esj.json` | `"version": 01` |
| `json-fraction-without-digits.esj.json` | `"/BT-1": 1.` |
| `json-truncated-literal-in-envelope.esj.json` | `"format": tru` |
| `json-run-on-literal-in-envelope.esj.json` | `"format": truex` |
| `json-truncated-literal-in-values.esj.json` | `"/BT-1": tru` |
| `json-run-on-literal-in-values.esj.json` | `"/BT-1": truex` |
| `json-control-character-in-string.esj.json` | BT-1 carries an unescaped U+0001 |
| `json-invalid-escape.esj.json` | BT-1 carries the escape `\x`, which JSON does not have |
| `json-after-path-syntax.esj.json` | `"/BT-1x" "A"`: a name that is no path, then no colon |
| `name-before-missing-colon.esj.json` | `{"profile" 1}`: an undefined name, then no colon |
| `name-before-broken-value.esj.json` | `{"profile": tru}`: an undefined name, then no JSON value |
| `duplicate-before-broken-value.esj.json` | `format` a second time, its value no JSON value |
| `envelope-empty-object.esj.json` | `{}`: all four required members missing |
| `envelope-missing-format-and-model.esj.json` | neither `format` nor `semanticModel` |
| `envelope-unknown-member-long-name.esj.json` | an undefined top-level member with a name of 100 characters |
| `envelope-member-named-value.esj.json` | a top-level member named `value` |
| `envelope-wrong-format.esj.json` | `format` is `EN16931-Semantic-JSON-2` |
| `envelope-format-not-a-string.esj.json` | `format` is the number `1` |
| `envelope-semantic-model-null.esj.json` | `semanticModel` is `null` |
| `envelope-values-not-an-object.esj.json` | `values` is a string |
| `envelope-extensions-not-an-object.esj.json` | `extensions` is a string |
| `envelope-source-not-an-object.esj.json` | `source` is the number `5` |
| `envelope-format-surrogate.esj.json` | `format` carries the unpaired escape `\ud800` |
| `source-empty-object.esj.json` | `source` is present and empty |
| `source-syntax-not-a-string.esj.json` | `source.syntax` is the number `1` |
| `source-sha256-short.esj.json` | `source.sha256` has 63 hexadecimal digits |
| `source-duplicate-member.esj.json` | `source.syntax` appears twice |
| `source-unknown-member-surrogate-value.esj.json` | an undefined member of `source` whose value carries `\ud800`: its name ends the read |
| `extensions-duplicate-owner-token.esj.json` | the owner token `de.example.vendor` appears twice |
| `extensions-surrogate-in-owner-token.esj.json` | an owner token carrying `\ud800` |
| `extensions-surrogate-in-member-name.esj.json` | a member name below an owner token carrying `\ud800` |
| `extensions-surrogate-in-array-member-name.esj.json` | a member name inside an array below an owner token carrying `\udfff` |
| `extensions-surrogate-in-string.esj.json` | a string below an owner token carrying `\ud800` |
| `extensions-number-negative-65.esj.json` | `-1e-62`: its canonical form is 65 characters long |
| `extensions-number-fraction-65.esj.json` | `1e-63`: its canonical form is 65 characters long |
| `extensions-number-in-array.esj.json` | `1e400` as the second element of an array |
| `values-duplicate-through-escape.esj.json` | `/BT-1` and `\/BT-1`, the same name once the escape is read |
| `values-surrogate-in-structure-walked-past.esj.json` | `"/BT-1": ["\ud800"], "/BT-2": 01` (SPEC.md section 9.6) |
| `values-duplicate-in-structure-walked-past.esj.json` | a duplicate member name inside an array written under `/BT-1` |
| `values-value-object-member-named-syntax.esj.json` | a value object carrying a member `syntax`, which a value object does not define |
| `path-every-escaped-character.esj.json` | a member of `values` named `/BT-1` followed by every character section 9.5 escapes, written as JSON escapes |
| `path-every-escaped-character-and-surrogate.esj.json` | the same name with `\ud800` at its end: a name with no encoding, whose subject escapes all of them |
