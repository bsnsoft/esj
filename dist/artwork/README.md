# Artwork of the packaging smoke test

*Part of [EN16931 Semantic JSON](../../README.md).*

Four render templates and the images they name, for the cases `render-artwork-*` of
[`../cases.sh`](../cases.sh). They are not examples to copy — those are under
[`examples/templates/`](../../examples/templates/README.md) — but the image formats a template
may bring, each in a form that takes its own way through the Java runtime:

| Template | Letterhead, first page | Letterhead, following pages | Logo |
|---|---|---|---|
| `jpeg.json` | `sheet.jpg`, a JPEG in colour | `sheet-grey.jpg`, a greyscale progressive JPEG | `logo.jpg`, a JPEG in colour |
| `icc.json` | `sheet-icc.png`, an RGB PNG with an ICC profile (`iCCP`) | `sheet-icc.jpg`, a JPEG with an ICC profile (`APP2`) | `logo-icc.png`, an RGB PNG with an ICC profile |
| `png.json` | `sheet-grey8.png`, an 8-bit greyscale PNG | `sheet-srgb.png`, an RGB PNG with an `sRGB` chunk | `logo-alpha.png`, an RGB PNG with an alpha channel |
| `grey.json` | `sheet-grey1.png`, a 1-bit greyscale PNG | `sheet-grey16.png`, a 16-bit greyscale PNG | `logo-grey-alpha.png`, a greyscale PNG with an alpha channel |

A PNG in RGB or with a palette, not interlaced and without an alpha channel — what
`examples/templates/` carries — is copied into the PDF as it is. Every other image is read by
the Java runtime first: a JPEG by the decoder in `libjavajpeg`; a PNG in grey, interlaced or
with an alpha channel by the PNG reader of ImageIO, into a raster
and a colour model of the bit depth; a colour profile by the colour management of the runtime,
which also reads its built-in sRGB and grey profiles for an `sRGB` chunk and for grey with an
alpha channel. A native executable carries those paths only where the GraalVM agent saw
a run take them ([`../native-image.sh`](../native-image.sh)); these cases are those runs, and
[`../smoke.sh`](../smoke.sh) compares each rendering of a packaged artefact with the jar's,
byte for byte.

The images are drawn by [`Draw.java`](Draw.java), from the root of the repository:

```console
$ java dist/artwork/Draw.java
```

The logo is the three squares of the example mark and the sheet a hairline across the head of
an A4 page at one pixel per five points, set pixel by pixel rather than drawn, and encoded by
the JDK's own writers; JDK 17, 21 and 25 wrote the same bytes. Nothing here is anybody's
picture. The colour profile in the three images that carry one is
`esj-render/src/main/resources/de/bsnsoft/esj/render/icc/sRGB2014.icc`, unaltered, which the
International Color Consortium allows to be copied and embedded without restriction ([`NOTICE`](../../NOTICE)).
