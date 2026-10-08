# Bounds

*Part of the [fixture manifest](../README.md).*

Documents read under bounds of `SPEC.md` section 12.2 below the defaults, each named in the
`bounds` section of the manifest with the bounds and the outcome: a bound at the document and one
below it, so that both sides of each are recorded. A byte bound is never set below 64, so that only
the member a case is about reaches it; `../../../examples/minimal.esj.json` and
`../../../examples/extension-depth.esj.json` are read under bounds as well.

Each file is `../../../examples/minimal.esj.json` with the member named below added or replaced.

| File | Content |
|---|---|
| `string.esj.json` | BT-153 is 32 times `ä`: 32 characters, 64 bytes |
| `normalized-string.esj.json` | BT-153 is 65 bytes as written and 64 once CR LF has become LF |
| `extension-string.esj.json` | a string of `extensions` of 65 bytes with a CR LF, which is not normalized there |
| `source-syntax.esj.json` | `source.syntax` of 65 bytes with a CR LF, which is not normalized there |
| `envelope-string.esj.json` | `format` is 65 characters long and not the fixed value |
| `semantic-model.esj.json` | `semanticModel` is an edition of 65 characters the grammar admits |
| `member-name.esj.json` | an undefined envelope member whose name is 65 bytes long |
| `owner-token.esj.json` | an owner token of 129 characters, one past its grammar |
| `long-bad-path.esj.json` | a member of `values` whose name is 65 bytes long and no path |
| `number-token.esj.json` | a number of 65 digits inside `extensions` |
| `number-in-values.esj.json` | a number of 65 digits as the value of `/BT-9` |
| `walked-past-string.esj.json` | an array under `/BT-9` holding a string of 200 bytes |
| `walked-past-number.esj.json` | an array under `/BT-9` holding a number of 65 digits |
| `walked-past-name.esj.json` | an array under `/BT-9` holding an object whose member name is 65 bytes long |
| `walked-past-depth.esj.json` | three nested arrays under `/BT-9` |
| `walked-past-value-member.esj.json` | two nested arrays as the `value` of a value object: levels 2 and 3 |
| `value-object-strings.esj.json` | an identifier whose `value` is 64 bytes and an attachment whose `value` is 128 |
| `empty-before-limit.esj.json` | an empty BT-27, and later a BT-153 of 65 bytes |
| `limit-before-empty.esj.json` | a BT-27 of 65 bytes, and later an empty BT-153 |
| `value-object-empty-before-limit.esj.json` | a value object whose empty `scheme` stands before a `value` of 65 bytes |
| `value-object-limit-before-empty.esj.json` | the same members the other way round |
| `value-object-members.esj.json` | a value object of two members |
| `binary-total.esj.json` | two attachments of 96 base64 characters, 72 bytes each, 144 together |
| `binary-total-padding.esj.json` | five attachments, `QUJD`, `QQ==`, `Q===`, `====` and `========`: 3, 1, 1, 1 and 4 bytes by the formula of section 12.2 |
| `extension-nodes.esj.json` | six nodes inside `extensions`: an array of three numbers, then an object with one member |
| `long-string-surrogate.esj.json` | BT-153 is 100 bytes followed by `\ud800`: a lone surrogate comes before the bound of a string |
| `long-name-surrogate.esj.json` | a member name of `values` of 106 bytes followed by `\ud800`: the bound comes before a lone surrogate in a name |
| `long-string-unclosed.esj.json` | the text ends inside BT-153 after 100 bytes: a string is read as a whole token before its bound |
| `long-name-bad-escape.esj.json` | a member name below an owner token of 100 bytes followed by the escape `\x`, which JSON does not have |
