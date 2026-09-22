# Invoices in a database

*Part of [EN16931 Semantic JSON](../README.md).*

Store the invoice, not its transport syntax. A document is a map from semantic path to value:
the whole invoice in one column, the terms an application cares about in indexes and views of
their own, nothing in the schema coupled to UBL or CII. Do not index UBL, do not index CII;
index `/BT-1`.

The statements are PostgreSQL 14 or newer, and every one is executed in the order this page
prints it, by `esj-xr/src/test/java/de/bsnsoft/esj/xr/StorageExamplesTest.java`
against a server it starts itself and fills with the UBL and the CII rendering of one invoice.

## A table and a query

```sql
CREATE TABLE invoice (
    id              BIGSERIAL PRIMARY KEY,
    document        JSONB     NOT NULL,
    semantic_digest CHAR(64)  NOT NULL
);
```

Not the final schema: [jsonb is not canonical ESJ](#jsonb-is-not-canonical-esj) adds two
columns, and [Validate before you store](#validate-before-you-store) runs before the `INSERT`.

```sql
SELECT id,
       document -> 'values' ->> '/BT-1'                    AS invoice_number,
       document -> 'values' ->> '/BG-4/BT-27'              AS seller_name,
      (document -> 'values' ->> '/BG-22/BT-112')::numeric  AS total_with_vat
  FROM invoice;
```

`/BT-1` is the business term "Invoice number" of EN 16931, and the query is the same whether the
invoice arrived as UBL or as CII. `semantic_digest` is the digest over the semantic content
(`SPEC.md` section 8), the same in either syntax; the Java that writes a row is in
[Validate before you store](#validate-before-you-store).

## Views

```sql
CREATE VIEW invoice_summary AS
SELECT id,
       document -> 'values' ->> '/BT-1'                    AS invoice_number,
      (document -> 'values' ->> '/BT-2')::date             AS issue_date,
       document -> 'values' ->> '/BT-5'                    AS currency,
       document -> 'values' ->> '/BG-4/BT-27'              AS seller_name,
      (document -> 'values' ->> '/BG-22/BT-112')::numeric  AS total_with_vat,
      (document -> 'values' ->> '/BG-22/BT-115')::numeric  AS amount_due
  FROM invoice;
```

```sql
SELECT invoice_number, seller_name, total_with_vat
  FROM invoice_summary
 ORDER BY issue_date DESC;
```

ESJ is the persistence model, the view the query model: the rest of the application sees columns.
The UBL and the CII rendering of one invoice give two rows here that differ only in `id`.

## Expression indexes

```sql
CREATE INDEX invoice_number_idx ON invoice ((document -> 'values' ->> '/BT-1'));
CREATE INDEX seller_name_idx    ON invoice ((document -> 'values' ->> '/BG-4/BT-27'));
CREATE INDEX invoice_total_idx  ON invoice (((document -> 'values' ->> '/BG-22/BT-112')::numeric));
```

A date needs one step more. `text::date` reads the session's `DateStyle`, so PostgreSQL refuses
it in an index expression — *functions in index expression must be marked IMMUTABLE* — and the
same refusal applies to a generated column. `to_date` with an explicit pattern is immutable:

```sql
CREATE FUNCTION esj_date(text) RETURNS date
    LANGUAGE sql IMMUTABLE STRICT
    AS $$ SELECT to_date($1, 'YYYY-MM-DD') $$;

CREATE INDEX invoice_date_idx ON invoice (esj_date(document -> 'values' ->> '/BT-2'));
```

```sql
SELECT document -> 'values' ->> '/BT-1' AS invoice_number
  FROM invoice
 WHERE document -> 'values' ->> '/BG-4/BT-27' = ?
 ORDER BY esj_date(document -> 'values' ->> '/BT-2') DESC;
```

An index is attached to a business term, so it keeps working when the next invoice of that
seller arrives in the other syntax.

## Materialized views

```sql
CREATE MATERIALIZED VIEW invoice_summary_mv AS
SELECT id                                                  AS document_id,
       document -> 'values' ->> '/BT-1'                    AS invoice_number,
       esj_date(document -> 'values' ->> '/BT-2')          AS issue_date,
       document -> 'values' ->> '/BT-5'                    AS currency,
       document -> 'values' ->> '/BG-4/BT-27'              AS seller_name,
      (document -> 'values' ->> '/BG-22/BT-112')::numeric  AS total_with_vat
  FROM invoice;

CREATE UNIQUE INDEX invoice_summary_mv_document_idx ON invoice_summary_mv (document_id);
CREATE INDEX invoice_summary_mv_date_idx   ON invoice_summary_mv (issue_date DESC);
CREATE INDEX invoice_summary_mv_seller_idx ON invoice_summary_mv (seller_name);
CREATE INDEX invoice_summary_mv_total_idx  ON invoice_summary_mv (total_with_vat);
```

```sql
REFRESH MATERIALIZED VIEW CONCURRENTLY invoice_summary_mv;
```

```sql
SELECT invoice_number, seller_name, total_with_vat
  FROM invoice_summary_mv
 ORDER BY issue_date DESC;
```

`CONCURRENTLY` keeps the view readable while it refreshes; it needs the unique index above and a
transaction of its own, so inside `BEGIN … COMMIT` — or on a JDBC connection with auto-commit off
— it is refused. The stored form stays generic; the read model is what is tuned.

## Projections

```text
                         ┌── invoice_summary
                         ├── payment queue
ESJ, stored once ────────┼── VAT reporting
                         ├── search index
                         └── analytics
```

An accounting list reads BT-1, BT-2, BT-27, BT-112 and BT-115; a payment workflow the payment
terms and the account of BG-17; a VAT report the breakdown of BG-23 — each a projection of the
same stored invoice, none of them prescribed by ESJ.

## The key/value table

```sql
CREATE TABLE invoice_value (
    document_id    BIGINT       NOT NULL REFERENCES invoice ON DELETE CASCADE,
    path           VARCHAR(256) NOT NULL,
    content        TEXT         NOT NULL,
    scheme         VARCHAR(64),
    scheme_version VARCHAR(64),
    mime_code      VARCHAR(128),
    filename       VARCHAR(255),
    PRIMARY KEY (document_id, path)
);
CREATE INDEX invoice_value_path_document ON invoice_value (path, document_id);
CREATE INDEX invoice_value_number_idx    ON invoice_value (content) WHERE path = '/BT-1';
CREATE INDEX invoice_value_lookup        ON invoice_value (path, md5(content));
```

One row per value, which joins and aggregates like any other table. The column is `content` and
not `value` because `VALUE` is reserved. The four nullable columns are the supplementary
components the semantic model gives some terms: the scheme and its version of an identifier, the
media type and the file name of a binary object. The general lookup index is over `md5(content)`,
because BT-125 carries an attached document as base64 and a B-tree entry of a PostgreSQL page
cannot hold one; the partial index on `/BT-1` fits an invoice number and needs no digest.

```java
try (PreparedStatement values = connection.prepareStatement(
        "INSERT INTO invoice_value (document_id, path, content,"
                + " scheme, scheme_version, mime_code, filename)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
    for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
        SemanticValue value = entry.getValue();
        values.setLong(1, id);
        values.setString(2, entry.getKey().toString());
        values.setString(3, value.canonicalContent());
        values.setString(4, value.scheme());
        values.setString(5, value.schemeVersion());
        values.setString(6, value.mimeCode());
        values.setString(7, value.filename());
        values.addBatch();
    }
    values.executeBatch();
}
```

One term over every invoice, one value looked up through the digest index, and the lines of one
invoice in the canonical order — numeric, so line 10 sorts after line 2:

```sql
SELECT document_id, content AS invoice_number
  FROM invoice_value
 WHERE path = '/BT-1';
```

```sql
SELECT document_id
  FROM invoice_value
 WHERE path = '/BT-1' AND md5(content) = md5(?);
```

```sql
SELECT path, content
  FROM invoice_value
 WHERE document_id = ? AND path LIKE '/BG-25/%'
 ORDER BY (substring(path from '^/BG-25/([0-9]+)/'))::int, path;
```

The rows are the whole semantic content, so a document rebuilt from them has the semantic digest
the stored one had; the provenance is outside that digest (`SPEC.md` section 4.7) and stays in
the `invoice` row.

## Typed projections

The ESJ value is authoritative and a typed database value is a derived query projection:
`/BG-22/BT-112 -> "2915.5"` is the invoice, `2915.5::numeric` is what the index and the sum are
built on, `/BT-2 -> "2026-01-31"` projects to `date`.

```sql
SELECT currency, count(*) AS invoices, sum(total_with_vat) AS total
  FROM invoice_summary
 GROUP BY currency;
```

Choose the scale deliberately: an amount carries as many decimals as the edition of its
registry allows — two in 2017, the minor unit of its currency in 2026 — and a unit price, a
quantity and a percentage as many as they need (`SPEC.md` section 6.4). `numeric` without a
scale keeps every digit, where `numeric(19, 2)` would silently round a unit price.

## Other stores

A key/value store takes the document digest as the key and the canonical bytes as the value, or
one entry per semantic path where single terms are read; a document store keeps the same JSON and
indexes the same paths; a search index takes the paths a query reaches as its fields. Of what is
above, only `jsonb` and its operators, `RETURNING`, `substring(… from …)` and the index
expressions are PostgreSQL's own.

## Source of truth

For outgoing invoices ESJ can be the stored invoice, with the transport representations
generated from it:

```text
                    ┌── UBL
                    ├── CII
ERP ──> ESJ ────────┼── Factur-X / PDF
        stored      ├── API
                    └── reporting
```

A stored document goes back out as a cross industry invoice or as a UBL document: of the 86
corpus instances, 85 and 40 are accepted by the official artefacts and 80 and 86 read back as
the document they were written from, each difference named in
[`../conformance/writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md). Incoming invoices run the other
way — UBL or CII, import, ESJ, storage — and the original is worth keeping: `source.sha256` binds a document to
the bytes it came from but cannot reconstruct them, and those bytes carry signatures and
archival evidence, outside the semantic content.

## jsonb is not canonical ESJ

```sql
ALTER TABLE invoice
    ADD COLUMN canonical_esj   BYTEA    NOT NULL,
    ADD COLUMN document_digest CHAR(64) NOT NULL;
```

`jsonb` keeps neither member order nor the bytes that arrived, so serializing the column back
does not reproduce the canonical form and its digest. `canonical_esj` is the authoritative
serialization and `document` the queryable copy of it. Two added columns rather than a second
`CREATE TABLE`, because this page runs top to bottom and `NOT NULL` wants an empty table.

| Question | Column |
|---|---|
| Are these the bytes I stored? | `document_digest`, recomputed over `canonical_esj` |
| Do I have this invoice already, in any syntax? | `semantic_digest` |
| Did the invoice change, or only its packaging? | `semantic_digest` |

## Validate before you store

Incoming bytes, ESJ reader, validation, canonical bytes and digests, insert. Generic JSON
handling drops what layer L1 has to see — duplicate member names above all — so a document that
came from outside is read by the reader before anything puts it into a `jsonb` column.

```java
SemanticDocument document = EsjReader.strict().read(bytes);
ValidationResult result = StructuralValidator.validate(document, Registry.en16931());
if (result.status() == ValidationStatus.INVALID) {
    throw new IllegalArgumentException(result.findings().toString());
}

byte[] canonical = Canonicalizer.canonicalBytes(document);
long id;
try (PreparedStatement insert = connection.prepareStatement(
        "INSERT INTO invoice (document, canonical_esj, semantic_digest, document_digest)"
                + " VALUES (?::jsonb, ?, ?, ?) RETURNING id")) {
    insert.setString(1, new String(canonical, UTF_8));
    insert.setBytes(2, canonical);
    insert.setString(3, Canonicalizer.semanticDigest(document));
    insert.setString(4, Canonicalizer.documentDigest(document));
    try (ResultSet key = insert.executeQuery()) {
        key.next();
        id = key.getLong(1);
    }
}
```

An invoice the application built itself is serialized from that model and has passed the same
validator on the way ([`getting-started.md`](getting-started.md)); an XML or PDF invoice passes
the importer first ([`java-api.md`](java-api.md#reading-ubl-and-cii)).

## The schema does not mirror the standard

One column per business term ties the table to one edition of EN 16931, leaves a few hundred
nullable columns, needs a migration for every term that is added and has nowhere to put a group
that repeats. A semantic path carries the index in it — `/BG-25/0/BT-126`, `/BG-25/1/BT-126` — so
three lines and three hundred are one schema, and an extension term is a row, not a column.

## Why `/BT-1` and not an XPath

PostgreSQL stores and queries XML as well, but an index on an XML path is an index on a transport
syntax: UBL and CII put the invoice number in different elements, so a UBL XPath index means "the
invoice number, as UBL writes it" and CII needs a second one. `/BT-1` means the invoice number.
