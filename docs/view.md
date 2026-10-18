# Viewing an invoice: `esj view`

*Part of [EN16931 Semantic JSON](../README.md).*

An electronic invoice has no file type of its own: a double-click shows raw XML, or a PDF without
the data inside it. `esj view` shows it as one page in the browser, or with `--text` in the
terminal. It reads UBL, CII, ESJ, and a PDF carrying one of them.

```console
$ esj view --report invoice.pdf
/home/anna/Downloads/esj-view/esj-view-invoice-4120768315283942113/index.html
```

## The page

At the top: seller → buyer, the invoice number, its date and the amount due — for any type but a
commercial invoice the type by name, and for a credit note its number, its date and the amount
credited. On the right the result: *GÜLTIG* / *VALID*, *UNGÜLTIG* / *INVALID*, *NICHT
ENTSCHIEDEN* / *UNDECIDED*, or *NICHT GEPRÜFT* / *NOT CHECKED* without `--report`, and under it
the findings counted by severity. Under the header, one tab per view; a tab is there only where
its content is, and the first one is selected:

| Tab (de / en) | Shows |
|---|---|
| PDF | a PDF input, copied byte for byte, in the PDF viewer of the browser |
| Rechnung / Invoice | the letter `esj render` draws from the data, in the same viewer |
| KoSIT | the HTML rendering of `esj render --html` ([`rendering.md`](rendering.md)); none for a document of the 2026 edition |
| Prüfbericht / Report | with `--report`: verdict, identity, checks and findings, and one link that saves the report as a PDF |

To see the PDF beside the invoice, open the page in a second window; printing prints the selected
tab. The verdict is about the data of the electronic invoice, not about the page image of a PDF.

The page runs no script and fetches nothing: the tabs are radio buttons styled by CSS, every
reference names a file of its directory, and a content security policy (`script-src 'none';
base-uri 'none'; form-action 'none'`) holds the browser to it. The KoSIT rendering runs its own
script for its own tabs, in a frame sandboxed without the origin of the page. Every text of the
document is escaped, and the characters that steer the reading order are taken out.

## In the terminal

`esj view --text` writes no page and opens nothing. It prints the invoice as `esj render --text`
does — seller, buyer, number and dates, the lines as a table, totals, payment, notes and further
details, plain UTF-8 text `--width` characters wide (default: `COLUMNS` where it is at least 60,
otherwise 100) — and with `--report` the lines of `esj validate` under a rule. A PDF prints the
invoice embedded in it, never its page image; a file that leads nowhere prints the line the page
would show, with the exit code below. `--text` with `--output json` is refused with exit code 2.

## Every outcome is a page

From a file manager nobody sees the error stream, so a file that leads nowhere opens a page too:
what the file is, and what to ask the sender for. The message of the run goes to the error stream
and into `--output json`.

| Outcome | Exit | The page says |
|---|---|---|
| Read | 0, with `--report` the verdict's (0, 1, 9) | header and tabs |
| PDF of a profile that is no EN 16931 invoice (MINIMUM, BASIC WL) | 0, with `--report` 1 | the invoice, with the profile named |
| PDF without an electronic invoice | 2 | *This PDF contains no e-invoice.* Ask the sender for it |
| PDF whose attachments were not classified | 2 | it cannot be determined whether it contains one; ask for the XML |
| PDF with several candidates, or one name for two files | 2 | a table: number, file name, content, size, relationship, and the `esj view --report --attachment-index <n>` that opens each; ask which applies |
| ZUGFeRD 1.0 | 4 | it does not conform to EN 16931; ask for an EN 16931 invoice |
| Encrypted PDF | 2 | ask for an unencrypted copy; the file is not copied |
| Not well-formed, no invoice syntax, empty, a PDF that cannot be parsed, a selector that names nothing | 2 | *not a readable e-invoice*; ask the sender to send it again |
| A limit of the run while reading | 7 | the bound in a reader's words and the option that raises it, with `--report` of an ESJ input the report |
| The letter over `--max-pages` | 7, with `--report` the verdict's | the bound, in the *Invoice* tab |
| The letter refused (an edition this build does not carry, a document that fails the format) | as read | *This invoice cannot be shown here*, in the *Invoice* tab |

Only what a terminal alone can cause gets no page: a name that is no readable file, the standard
input, a command line or a template that does not parse, and exit codes 3 and 6.

## Files and directory

| File | Bytes of |
|---|---|
| `index.html` | the page |
| `received/<file name>` | the input, for every PDF except an encrypted one and one over `--max-pdf-bytes` |
| `<stem>.letter.pdf` | `esj render <file> [--template …] [--no-payment-code] --lang <l>` |
| `<stem>.kosit.html` | `esj render <file> --html --lang <l>` |
| `<stem>.report.html`, `<stem>.report.pdf` | `esj validate <file> --report-lang <l> --report …`, with `--report` |

`<stem>` is the input name without its last extension, a separator, a control character and any
of `<>:"|?*#%` as `_`, at most 80 characters. The same input and options give the same bytes.
`--out <dir>` names the directory, creates it where it is missing, replaces the files above in
it, and opens nothing. Without it the directory is a new `esj-view-<stem>-<number>`: on Linux below
`esj-view` in the downloads directory `xdg-user-dir DOWNLOAD` names (a browser in a snap or a
Flatpak reads neither the temporary directory nor a hidden one of the home), elsewhere in the
temporary directory. **esj never deletes it**; the footer of the page names it.

The standard output is the absolute path of the page. It is opened with `open` on macOS,
`xdg-open` on Linux where `DISPLAY` or `WAYLAND_DISPLAY` is set, and the URL handler of the shell
on Windows; a browser that cannot be started adds a warning and keeps the exit code.
`--output json` prints an object instead: `page`, `directory`, `opened`, `state` (what the file
turned out to be: `read`, `no-invoice`, `not-established`, `several`, `unsupported`, `encrypted`,
`not-read`, `limit`), `files` (`received`, `letter`, `html`, `reportHtml`, `reportPdf`, each
`null` where not written), `message` (the error text or `null`) and `validation` (the object of
`esj validate --output json`, `null` without `--report`).

The language is `--lang`, else German where the first of `LC_ALL`, `LC_MESSAGES` and `LANG` that
is set (on Windows the locale of the user) says German, and English otherwise. `--no-payment-code`
leaves the EPC QR code out of the letter. [`cli.md`](cli.md#view) lists the options.

## Open with

| System | What is installed | Opens |
|---|---|---|
| Linux, from the deb or rpm | `/usr/share/applications/esj.desktop` and `/usr/share/icons/hicolor/scalable/apps/esj.svg` | `esj view --report %f`, listed under *Open with* for `application/xml`, `text/xml` and `application/pdf` |
| macOS, the native archive | `ESJ Viewer.app` beside `esj`, signed ad hoc, not notarised | `esj view --report --lang <the user's>` for each file Finder hands it, with `esj` beside the bundle, else in `/opt/homebrew/bin` or `/usr/local/bin` |
| Windows | nothing; [`install.md`](install.md#esj-view-from-the-file-manager) has the one-line `esj-view.cmd` to choose once | `esj view --report <file>` |

None of them becomes the default program of a type. A run of `ESJ Viewer.app` that ends without a
page shows what esj wrote in a dialog. [`dist/desktop/`](../dist/desktop/README.md) has the icon and
how the bundle is built.

## Not in this version

Choosing a candidate, switching the language or showing two views side by side on the page (each
needs a script), a dark mode, a window of its own around the page, comparing picture and data,
reading anything off the page image, a file extension for ESJ, a default association, an
installer, notarisation, and cleaning up old view directories.
