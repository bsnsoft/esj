# Vendored KoSIT visualization, HTML side

The files in this directory are copies, byte for byte unmodified, of the HTML
visualization of the semantic XR representation: the stylesheet that turns an XR document
into one self-contained HTML page, the two stylesheets it imports, the two localization
files it reads and the three files it inlines.

| Item | Value |
|---|---|
| Repository | <https://github.com/itplr-kosit/xrechnung-visualization> |
| Tag | `v2026-08-31` |
| Path in that repository | `src/xsl/` |
| License | Apache License, Version 2.0 (see the `LICENSE` file beside this one) |
| Publisher | Koordinierungsstelle für IT-Standards (KoSIT) / XStandards Einkauf |
| Modified | no |

`FileSaver-v2.0.5.js` is third-party material inside that repository and carries its own
licence: it is FileSaver.js by Eli Grey, published under the MIT licence, and the file
states that in a comment at its head, by a link. It is vendored here as it stands there,
and the NOTICE of this repository names it separately.

The MIT licence requires the copyright notice and the permission notice to be included in
every copy, and neither the file nor the repository it was taken from carries the text
itself. `FileSaver-LICENSE.txt` beside it is that text, with the copyright notice the file
states. It is the one file in this directory that is not a copy from the repository above;
everything else here is.

`xrechnung-html.xsl` is not run on its own. The stylesheet of this project,
`../xsl/esj-html.xsl`, imports it and overrides one of its template rules — the one that
writes an external document location into a link target and an attachment into the
arguments of a script call. The bytes here are untouched by that, which is what
`xsl:import` is for; `docs/rendering.md` says what the override does and why.

## Files and digests

SHA-256 over the bytes of each file, lowercase hexadecimal:

| File | SHA-256 |
|---|---|
| `FileSaver-v2.0.5.js` | `6060c139808ad689ae6f055ea65eb8eaa90314fcceda72af727a369a7e69f263` |
| `FileSaver-LICENSE.txt` | `9deccce57a52c1f86f06e4944bf8979aa1590c34a4974d69e94b9e915afd0db4` |
| `common-xr.xsl` | `3155e48fe19dd23ff74e3f191721e28c7a51a5e76a093fb1e2ee60bfe08edc41` |
| `functions.xsl` | `1e8e2c414a19d63007408856649037c8654f5c7391618c48010865dac60db3ed` |
| `l10n/de.xml` | `6f29bb1b02ace03e0753315e455d7516f871123967e2668c54954e242c4c2d67` |
| `l10n/en.xml` | `7966d747319d92f918f58420e309303f3b178ca4b68faad479e88b45ce7252d2` |
| `xrechnung-html.xsl` | `7a8bdd9f2653bfa1042d86a963af2071a9168cfbf0aef5be27975abedef20ccc` |
| `xrechnung-viewer.css` | `f480e173816b701522eb15eccd782ca10e439d9a34838a8688f45b6eed1ebed5` |
| `xrechnung-viewer.js` | `fb31206bb89009f358891d6123573d425f6c310527b0c7f64ee041e9993c8d92` |
| `LICENSE` | `e0d7665e91531aebb79e4feaa415796f076b0f93e9eddd6d1d05efe9d93808ac` |

A test of this module recomputes these digests over the files as they are shipped, so a
change to one of them is a failing build rather than a quiet divergence from the table.
Nine of the ten are compared against the repository named above as well; the tenth,
`FileSaver-LICENSE.txt`, is the licence text and is only pinned here.

## Why these nine

`xrechnung-html.xsl` is the entry point. It imports `common-xr.xsl`, which includes
`functions.xsl`; those two are here because it needs them. `functions.xsl` reads its labels
out of `l10n/<language>.xml` with `doc()`, which is why the two localization files are
here and why there are two languages and not more. `xrechnung-html.xsl` inlines
`xrechnung-viewer.css`, `xrechnung-viewer.js` and `FileSaver-v2.0.5.js` with
`unparsed-text()`, which is what makes a rendering one file rather than a directory.

`common-xr.xsl` and `functions.xsl` are also vendored, at the same tag and with the same
digests, in `esj-xr`, where the stylesheets that read UBL and CII include them. They are
copied twice rather than shared, so that each module ships the closure of what it runs and
neither can be broken by a change made for the other.

The resolvers of this module answer these file names and nothing else, so neither an
`xsl:import`, nor a `doc()`, nor an `unparsed-text()` can reach the file system or the
network.

## Relationship to XRechnung

This module is not an XRechnung implementation and not a KoSIT deliverable. It uses the
stylesheet for one thing: to put an invoice in front of a human reader in a layout that
readers of electronic invoices in Germany already know, so that this project does not have
to invent one.
