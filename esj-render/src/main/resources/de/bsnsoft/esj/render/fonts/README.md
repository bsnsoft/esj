# Vendored Liberation Sans

The two font files in this directory are byte-for-byte unmodified copies from the release
archive named below. The PDF renderer of this module embeds them in every document it writes.
So an umlaut, a Greek letter or a currency sign of an invoice reaches the reader, and a later
PDF/A profile has a font to point at. No standard-14 font is used: those are not embedded, and
what they can show depends on the machine that opens the PDF.

| Item | Value |
|---|---|
| Project | Liberation Fonts |
| Repository | <https://github.com/liberationfonts/liberation-fonts> |
| Release | `2.1.5`, archive `liberation-fonts-ttf-2.1.5.tar.gz` |
| Path in that archive | `liberation-fonts-ttf-2.1.5/` |
| License | SIL Open Font License, Version 1.1 (see the `LICENSE` file beside this one) |
| Copyright | Digitized data copyright 2010 Google Corporation; copyright 2012 Red Hat, Inc. |
| Reserved Font Name | Liberation |
| Modified | no |

## Files and digests

SHA-256 over the bytes of each file, lowercase hexadecimal:

| File | SHA-256 |
|---|---|
| `LiberationSans-Bold.ttf` | `788abee4c806d660e8aee46689dd8540cd4bb98da03dcc9d171ce3efd99a9173` |
| `LiberationSans-Regular.ttf` | `76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8` |
| `LICENSE` | `93fed46019c38bbe566b479d22148e2e8a1e85ada614accb0211c37b2c61c19b` |

The archive those three files were taken out of has the SHA-256
`7191c669bf38899f73a2094ed00f7b800553364f90e2637010a69c0e268f25d0`.

A test of this module recomputes the three digests over the files as they are shipped. A change
to one of them fails the build instead of quietly diverging from this table.

## Why these two, and only these two

A generic invoice layout needs one upright text face, and one bold face for headings, table
headers and the amount due. Italics carry no meaning in it, and a monospaced face would only be
a second opinion about the same text. So the regular and the bold weight of Liberation Sans are
the whole of what is vendored: 2 files instead of the 12 of the archive.

The OFL is a font licence, not a software licence. It permits redistributing and embedding the
font, asks that a modified version does not use the Reserved Font Name, and forbids selling the
fonts on their own. Nothing in this project modifies or sells them. They are copied as they
stand and embedded in the documents the renderer writes, which the licence's permission to
embed covers explicitly. The NOTICE of this repository names the fonts, their copyright holders
and their licence.

## What the renderer does with them

The renderer embeds a subset of each face: the glyphs the document being rendered uses, with
the font's own metrics and its own `head` table. The subset is a function of the text and of
the font file, so rendering one document twice embeds the same bytes twice. That lets you
compare the renderings of two runs byte for byte.
