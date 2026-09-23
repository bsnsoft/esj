# Example render templates

Four templates for `esj render --template`, and the artwork they stand on. The format is
[`schema/render-template.schema.json`](../../schema/render-template.schema.json) and
[`docs/templates.md`](../../docs/templates.md) is what each member does.

| File | What it is |
|---|---|
| `letterhead.json` | a letterhead as a PDF: page 1 under the first page, page 2 under the rest |
| `image.json` | a letterhead as two PNGs, with `mark.png` placed as a logo on the first page |
| `gross.json` | the PDF letterhead, and a place for the four displayed gross figures of the B2C extension |
| `letter.json` | the same letterhead in the **letter layout**: address field, reference line, fold and punch marks; the seller's business details stand in the foot of the first page, in the band that page reserves for them |
| `letterhead.pdf` | the letterhead, two A4 pages |
| `band-first.png`, `band-following.png` | the same letterhead as images, one point per pixel; an image carries no text, so they carry the hairline alone |
| `mark.png` | the logo |

The first three name the generic layout, the shape of the semantic model, in their `"layout"`
member. A template that names none is drawn in the letter layout, the default, and
`letter.json` names it all the same.

```console
esj render invoice.xml --template examples/templates/letterhead.json --out invoice.pdf
esj render invoice.xml --template examples/templates/letter.json --out letter.pdf
```

The artwork carries nobody's design, and it is deliberately restrained: the name of an example
company in the head of the sheet, a hairline under it, a mark of three squares — nothing behind
the text and nothing at the foot, so a branded page is the plain page with a sender's identity
on it. It is drawn by the tests of `esj-render`, which also check that these files are what that
code draws. Replace all of it. The name is the whole of the text on it: an address, a register
number or the word for a kind of document printed on the sheet would be a second sender and a
second title beside the ones the rendering writes out of the invoice.

`gross.json` places `BT-B2C-001`, `BT-B2C-002`, `BT-B2C-003` and `BT-B2C-010`, the displayed
gross figures of a consumer invoice. **That extension is not defined in this version** — there
is no registry for it here — and the template does not need one: `label` and `type` in the
placement are what carry such a term until there is. A document that carries none of the four
renders on `gross.json` exactly as it renders on `letterhead.json`: a place is filled only
where the document fills it.
