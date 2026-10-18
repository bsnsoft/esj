# The desktop entries of `esj view`

*Part of [EN16931 Semantic JSON](../../README.md).*

What offers [`esj view`](../../docs/view.md) under *Open with* for an XML or a PDF file, on each
desktop, and the one source of its icon.

| File | What it is | Used by |
|---|---|---|
| [`esj.svg`](esj.svg) | the icon: a white sheet with a folded corner and a pair of braces, a document that is data; ink outline, the fold in the rule grey of the page, nothing else — no PDF red, no check mark, because a viewer's icon must not look like a verdict | installed as it stands by the Linux packages ([`../packages.sh`](../packages.sh)), as `/usr/share/icons/hicolor/scalable/apps/esj.svg`; drawn into the macOS icon by `Icon.java` |
| [`esj.desktop`](esj.desktop) | the desktop entry *ESJ Viewer*: `esj view --report %f` for `application/xml`, `text/xml` and `application/pdf`, `NoDisplay=true`, so that it is listed under *Open with* and not among the applications, and no default of either type | installed by the Linux packages as `/usr/share/applications/esj.desktop` |
| [`Icon.java`](Icon.java) | writes the `.icns` of the macOS bundle from `esj.svg`: the paths drawn with Java2D at 16 to 1024 pixels, the sheet at seventy per cent of a tile in the panel colour of the page that fills the image, as macOS 26 wants an application icon, each PNG under its code of the format | [`../viewer-app.sh`](../viewer-app.sh) |
| [`viewer.applescript`](viewer.applescript) | the program of `ESJ Viewer.app`: for each file Finder hands it, `esj view --report --lang <the user's>`, with `esj` beside the bundle or where Homebrew installs it; a dialog where `esj` is missing or a run ends without a page | compiled by [`../viewer-app.sh`](../viewer-app.sh) |

`dist/package.sh native` builds `ESJ Viewer.app` beside the executable on macOS with
[`../viewer-app.sh`](../viewer-app.sh): `osacompile` compiles the script into an applet, because
Finder hands a document to an application as an event an applet receives without native code; the
bundle is given the identifier `de.bsnsoft.esj.viewer`, one document type for `public.xml` and
`com.adobe.pdf` with the role *Viewer* and the rank *Alternate*, and the icon, and is signed ad
hoc. `esj_viewer_app` of [`../checks.sh`](../checks.sh) checks those properties and the signature.
The icon of the bundle is drawn with nothing but the JDK, so no rasterizer and no `iconutil` is
needed:

```console
$ java dist/desktop/Icon.java dist/desktop/esj.svg esj.icns preview/
```

Whether a desktop lists the entry, and whether Finder lists the bundle, is checked by hand before
a release: GNOME and KDE with `NoDisplay=true` (should one of them hide the entry from *Open
with*, the key goes), and the macOS versions the release is built on.
