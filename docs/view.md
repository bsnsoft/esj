# Viewing an invoice: `esj view`

*Part of [EN16931 Semantic JSON](../README.md).*

An electronic invoice has no file type of its own: a double-click shows raw XML, or a PDF without
the data inside it. `esj view` writes one page about a file — the file as it came, the invoice as
read from its data, and with `--report` the validation report — into a directory of its own and
opens it in the browser. It reads what every other command reads: UBL, CII, ESJ, and a PDF
carrying one of them.

```console
$ esj view --report invoice.pdf
/home/anna/Downloads/esj-view/esj-view-invoice-4120768315283942113/index.html
```

## The page

The **header** answers first, as text that can be selected and pasted: whether an electronic
invoice is embedded and which attachment it was taken from, its syntax and profile, the type,
number and date, seller, buyer and the amount due (the amount credited for a credit note), and
the verdict in the words of [the report](cli.md#the-report) — or *not checked* without `--report`.

| View | Shows |
|---|---|
| As received | a PDF input, copied byte for byte, in the PDF viewer of the browser |
| As read | the letter `esj render` draws from the data, in the same viewer; beside it a link to the HTML rendering of `esj render --html`, the accessible and searchable form ([`rendering.md`](rendering.md)) |
| Validation report | with `--report`: identity, checks, findings and what was done before the check, as sections of the page; the report itself as an HTML file and as a PDF, linked |

Side by side on a wide screen, one under the other on a narrow one. **The verdict is about the
data.** esj reads nothing off the page image; whether picture and data agree is the reader's to
see, and the page says so beside the verdict. Printing gives the overview and the report; the
invoice is printed from its own files.

The page runs no script and fetches nothing: every reference names a file of its directory, and
a content security policy in its head (`script-src 'none'; base-uri 'none'; form-action
'none'`) holds the browser to it. Every text of the document and of the run is escaped, and the
characters that steer the reading order are taken out, as in the report.

## Every outcome is a page

From a file manager nobody sees the error stream, so a refusal opens a page too: what it is, the
message of the run verbatim, and the next step. A refusal of the read leaves with the code every
other command leaves with for the same file; a page that shows the invoice with 0, or with
`--report` with the code of `esj validate`.

| Outcome | Exit | The page |
|---|---|---|
| Read | 0, with `--report` the verdict's (0, 1, 9) | header, views, report |
| PDF of a profile that is no EN 16931 invoice (MINIMUM, BASIC WL) | 0, with `--report` 1 | as read, with the profile named |
| PDF without an electronic invoice | 2 | the received PDF, *No electronic invoice embedded*: ask the sender for it |
| PDF whose attachments were not classified | 2 | the received PDF, and why nothing was established |
| PDF with several candidates, or one name for two files | 2 | a table: position, name, content, declared size, relationship, and the `esj view --report --attachment-index <n>` that reads each |
| ZUGFeRD 1.0 | 4 | the received PDF; it is no binding of EN 16931 |
| Encrypted PDF | 2 | no copy of the file: esj opens and decrypts no encrypted file |
| No invoice syntax, not well-formed, empty, a PDF esj cannot parse, a selector that names nothing | 2 | *File not read*, with the message |
| A limit of the run while reading | 7 | the message names the switch that raises the bound; the report of an ESJ input as written |
| The letter over `--max-pages` | 7, with `--report` the verdict's | the bound in place of the letter |
| The letter or the HTML refused (another edition, a term no loaded registry knows) | as read | the reason beside the view |

Only what a terminal alone can cause gets no page: a name that is no readable file, the standard
input, a command line that does not parse, a template that is not one — and exit codes 3 and 6,
where nothing could be written. A file of the directory that was not written in full leaves
with 6.

## Files and directory

| File | Bytes of |
|---|---|
| `index.html` | the page |
| `received/<file name>` | the input, for every PDF except an encrypted one and one over `--max-pdf-bytes` |
| `<stem>.letter.pdf` | `esj render <file> [--template …] --lang <l>` |
| `<stem>.kosit.html` | `esj render <file> --html --lang <l>` |
| `<stem>.report.html`, `<stem>.report.pdf` | `esj validate <file> --report-lang <l> --report …`, with `--report` |

`<stem>` is the input name without its last extension, a separator, a control character and any
of `<>:"|?*#%` as `_`, at most 80 characters, so twenty reports saved into one folder keep their
names. The same input and options give the same bytes.

`--out <dir>` names the directory, creates it where it is missing, replaces the files above in
it, and opens nothing. Without it the directory is a new `esj-view-<stem>-<number>`: on Linux below
`esj-view` in the downloads directory `xdg-user-dir DOWNLOAD` names — a browser in a snap or a
Flatpak reads neither the temporary directory nor a hidden one of the home — and in the temporary
directory on macOS, on Windows and where Linux names no downloads directory. **esj never deletes
it**: the browser reads it after the command has ended, and the footer of the page says where it
lies.

The standard output is the absolute path of the page, opened or not. The page is opened with
`open` on macOS, `xdg-open` on Linux where `DISPLAY` or `WAYLAND_DISPLAY` is set, and the URL
handler of the shell on Windows; a browser that cannot be started adds a warning and keeps the
exit code. `--output json` prints an object instead: `page`, `directory`, `opened`, `state`
(`read`, `no-invoice`, `not-established`, `several`, `unsupported`, `encrypted`, `not-read`,
`limit`), `files` (`received`, `letter`, `html`, `reportHtml`, `reportPdf`, `null` for one not
written), `message` (the error text or `null`) and `validation` (the object of
`esj validate --output json`, `null` without `--report`).

The language of the page, the letter, the HTML and the report is `--lang`, else German where the
first of `LC_ALL`, `LC_MESSAGES` and `LANG` that is set — on Windows the locale of the user —
says German, and English otherwise. Messages of the run stay English, as in a German report.
[`cli.md`](cli.md#view) lists the options.

## Open with

| System | What is installed | Opens |
|---|---|---|
| Linux, from the deb or rpm | `/usr/share/applications/esj.desktop` and `/usr/share/icons/hicolor/scalable/apps/esj.svg` | `esj view --report %f`, listed under *Open with* for `application/xml`, `text/xml` and `application/pdf` |
| macOS, the native archive | `ESJ Viewer.app` beside `esj`, signed ad hoc, not notarised | `esj view --report --lang <the user's>` for each file Finder hands it, with `esj` beside the bundle, else in `/opt/homebrew/bin` or `/usr/local/bin` |
| Windows | nothing; [`install.md`](install.md#esj-view-from-the-file-manager) has the one-line `esj-view.cmd` to choose once | `esj view --report <file>` |

None of them becomes the default program of a type: the desktop entry carries no default, and
the bundle declares itself an alternate viewer. A run of `ESJ Viewer.app` that ends without a page
shows what esj wrote in a dialog. The icon is a sheet with a pair of braces, a document that is
data; [`dist/desktop/`](../dist/desktop/README.md) has its one source and how the bundle is
built.

## Not in this version

Choosing a candidate, switching the language or synchronising the two frames on the page (each
needs a script), a dark mode, comparing picture and data, reading anything off the page image, a
file extension for ESJ, a default association, an installer, notarisation, and cleaning up old
view directories.
