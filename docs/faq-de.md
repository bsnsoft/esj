# FAQ (Deutsch)

*Teil von [EN16931 Semantic JSON](../README.md).* Englisch: [`faq.md`](faq.md).

Eine Frage, ein Befehl oder ein Ausschnitt. `invoice.xml` steht für eine UBL- oder CII-Rechnung,
`invoice.pdf` für eine Factur-X- oder ZUGFeRD-Datei, `invoice.esj.json` für ein ESJ-Dokument,
`letterhead.json` für eine Render-Vorlage und `pages.pdf` für eine PDF/A-3-Datei ohne Rechnung.
Ein Test führt jede `esj`-Zeile bis auf die, die ein installiertes veraPDF braucht, über Dateien
des Repositorys aus; jeder andere Block steht wörtlich auf der verlinkten englischen Seite, wo
ein Test ihn ausführt.

## Lesen

**Wie lese ich eine E-Rechnung, egal in welcher Syntax sie kam?**
```sh
esj convert invoice.xml                     # UBL, CII oder invoice.pdf rein; das ESJ-Dokument raus
```

**Wie komme ich an einen einzelnen Wert, etwa Rechnungsnummer oder Gesamtbetrag?**
```sh
esj get invoice.xml /BT-1                   # die Rechnungsnummer
esj get invoice.pdf /BG-22/BT-112           # der Bruttobetrag, direkt aus dem Hybrid-PDF
```

**Wie komme ich an eine einzelne Rechnungsposition?**
```sh
esj get invoice.xml /BG-25/0/BT-131         # der Nettobetrag der ersten Position
```

**Wie sehe ich alle Werte auf einmal?**
```sh
esj list invoice.xml                        # eine Zeile je Term: Pfad, Typ, Wert
```

**Wie sehe ich auf einen Blick, was eine Datei ist?**
```sh
esj inspect invoice.pdf                     # Syntax, Profil, Parteien, Summen, Digests; Exit 9, nie ein Urteil
```

**Wie hole ich das XML aus einem ZUGFeRD-PDF?**
```sh
esj extract invoice.pdf --list
esj extract invoice.pdf --out factur-x.xml
```

**Wie mache ich eine E-Rechnung lesbar?**
```sh
esj render invoice.xml --html --out invoice.html   # eine Seite, ohne Abhängigkeiten
esj render invoice.xml --out invoice.pdf           # eine PDF/A-3b-Datei
```

## Prüfen

**Wie validiere ich eine E-Rechnung?**
```sh
esj validate invoice.xml                    # XML-Schema, Schematron und Geschäftsregeln; VALID, Exit 0
```

**Wie prüfe ich eine XRechnung gegen die KoSIT-Regeln?**
```sh
esj validate invoice.xml                    # das Paket folgt aus BT-24: xrechnung/3.0.2/2026-08-31
esj validate --extension xrechnung invoice.xml   # zusätzlich die Erweiterungsterme der XRechnung
```

**Wie prüfe ich ein Factur-X- oder ZUGFeRD-PDF, Container und Rechnung?**
```sh
esj validate invoice.pdf                    # Container: OK / Invoice: VALID
```

**Wie weise ich die Konformität in einer Datei nach?**
```sh
esj validate invoice.pdf --report proof.pdf # oder proof.html: Digests, Pakete, Befunde, die Rechnung
```

**Wie prüfe ich PDF/A, statt der Deklaration zu glauben?**
```sh
esj validate invoice.pdf --verapdf /opt/verapdf   # ein eigenes veraPDF, nie gebündelt
```

**Wie prüfe ich die Geschäftsregeln auf den Daten, ohne XML?**
```sh
esj validate invoice.esj.json               # das Regelpaket über die Terme; die Artefakte über das im Speicher geschriebene CII
```

**Wie erkenne ich, ob zwei Dateien dieselbe Rechnung sind, in UBL, CII oder PDF?**
```sh
esj diff invoice.xml invoice.pdf            # derselbe semantische Digest: kein Unterschied, Exit 0
```

**Wie bekomme ich ein Ergebnis, das ein Programm lesen kann?**
```sh
esj validate invoice.xml --output json      # invoice.ok, syntax.findings, rules.findings, reasons
```

**Wie unterscheide ich »ungültig« von »konnte nicht geprüft werden«?**
Am Exit-Code: 0 `VALID`, 1 `INVALID`, 9 `INDETERMINATE` mit `reasons`, 7 ein Limit und kein
Urteil ([`cli.md`](cli.md#exit-codes)).

**Welche offiziellen Artefakte laufen, in welcher Version?**
```sh
esj --list-packs                            # XML-Schema, EN-16931-Schematron, XRechnung-Schematron, Lizenzen
```

## Speichern und abfragen

**Wie speichere ich eine E-Rechnung?**
```sql
CREATE TABLE invoice (
    id              BIGSERIAL PRIMARY KEY,
    document        JSONB     NOT NULL,
    semantic_digest CHAR(64)  NOT NULL
);
```

**Wie frage ich Rechnungen in der Datenbank ab?**
```sql
SELECT document -> 'values' ->> '/BT-1' AS invoice_number
  FROM invoice
 WHERE document -> 'values' ->> '/BG-4/BT-27' = ?
```

**Wie indiziere ich die Rechnungsnummer?**
```sql
CREATE INDEX invoice_number_idx ON invoice ((document -> 'values' ->> '/BT-1'));
```

**Wie erkenne ich ein Duplikat, egal in welcher Syntax es kam?**
```sh
esj canonicalize invoice.xml --digest       # semantic: gleich für UBL und CII derselben Rechnung
```

**Wie unterscheide ich eine geänderte Rechnung von einer geänderten Datei?**
```sh
esj canonicalize invoice.esj.json --digest  # semantic: der Inhalt; document: die ganze Datei
```

**Wie behalte ich den Bezug zu den Bytes, die ankamen?**
```sh
esj convert invoice.xml                     # endet mit "source": Syntax und SHA-256 der Eingabe
```

## Erzeugen

**Wie erzeuge ich aus meinen Daten eine E-Rechnung, ohne UBL oder CII zu kennen?**
```sh
esj convert invoice.esj.json --to ubl --out invoice.ubl.xml   # oder --to cii
```

**Wie konvertiere ich UBL nach CII oder zurück?**
```sh
esj convert invoice.xml --to cii --out invoice.cii.xml
```

**Wie bekomme ich ein gut aussehendes PDF auf meinem Briefpapier?**
```sh
esj render invoice.esj.json --template letterhead.json --out invoice.pdf   # das Brief-Layout, DIN 5008
```

**Wie erzeuge ich ein Factur-X- oder ZUGFeRD-PDF?**
```sh
esj render invoice.esj.json --template letterhead.json --embed cii --out invoice.pdf
```

**Wie hänge ich eine E-Rechnung an ein PDF, das ich schon habe?**
```sh
esj embed pages.pdf invoice.esj.json --out invoice.pdf   # XML und ESJ angehängt; PDF/A-3 bleibt, was es war
```

**Wie kommt ein GiroCode auf die Rechnung?**
```sh
esj render invoice.esj.json --layout letter --out invoice.pdf   # im Zahlungsblock, wo die Rechnung eine Überweisung nennt
```

**Wie zeige ich einem Verbraucher die Bruttopreise (B2C)?**
```sh
esj render examples/b2c-gross.esj.json --extension b2c --template examples/templates/gross.json --out invoice.pdf
```

**Wie hebe ich eine Rechnung auf EN 16931-1:2026?**
```sh
esj upgrade invoice.esj.json --to 2026 --out invoice-2026.esj.json   # nennt jeden offenen Punkt, rundet nie
```

## Integrieren

**Wie rufe ich es aus einem Service auf, für Eingaben, denen ich nicht traue?**
```sh
java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar validate - --output json --max-runtime 30s < invoice.xml
```

**Wie verarbeite ich sehr große Rechnungen, 80 MB und 300 000 Positionen?**
```sh
esj validate --limits large --max-runtime 10m invoice.xml   # Streaming-Reader; Regeln linear in den Positionen
```

**Wie bekomme ich es auf eine Maschine ohne Java?**
```sh
dist/package.sh native                      # eine native Binary; auch runtime-image, docker, zip
```

**Wie bilde ich eine E-Rechnung auf mein eigenes Datenmodell ab?**
```sh
esj list invoice.xml --format json          # ein Objekt je Term: path, datatype, value
```

**Wie nutze ich es aus TypeScript?** ([`bindings-ts.md`](bindings-ts.md))
```ts
const document = readDocumentOrThrow(await readFile('invoice.esj.json'));
document.values.get('/BG-25/0/BT-131');           // { value: '1080' }
```

**Wie nutze ich es aus C#?** ([`bindings-csharp.md`](bindings-csharp.md))
```csharp
SemanticDocument invoice = EsjReader.Strict().Read(File.ReadAllBytes("invoice.esj.json"));

string digest = Canonicalizer.SemanticDigest(invoice);
```

## Java

Die Zeilen stammen aus [`getting-started.md`](getting-started.md), [`java-api.md`](java-api.md),
[`b2c.md`](b2c.md) und dem README.

**Wie lese ich eine E-Rechnung in Java?**
```java
SemanticDocument fromXml = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
```

**Wie lese ich nach Namen statt nach Term-Nummer?**
```java
Invoice invoice = En16931.view(document);
LocalDate issueDate = invoice.issueDate();
String sellerName = invoice.seller().name();
BigDecimal amountDue = invoice.documentTotals().amountDueForPayment();
```

**Wie schreibe ich eine Rechnung in den Worten der Domäne?** Die Kette geht weiter mit
Verkäufer, Käufer, Zahlung und Positionen; `build()` leitet die Summen ab und prüft die Schichten
L2 und L3 sowie die fatalen Regeln.
```java
SemanticDocument document = Invoice.create(Profile.EN16931)
        .number("RE-2026-0211")
        .issued(LocalDate.of(2026, 5, 12))
        .currency(CurrencyCode.EUR)
```

**Wie ändere ich eine bestehende Rechnung?**
```java
InvoiceEditor amended = En16931.edit(document);
amended.invoiceLines().remove(0);
amended.buyerReference("KOST-4711");
```

**Wie leite ich Positionsbeträge, USt-Aufschlüsselung und Summen ab?**
```java
DerivationReport report = invoice.derive(Totals.STANDARD);
```

**Wie prüfe ich die Geschäftsregeln in Java?**
```java
RuleEngine engine = En16931.engine(Registry.en16931());

List<RuleFinding> findings = engine.evaluate(document);
boolean rejected = findings.stream().anyMatch(RuleFinding::fatal);
```

**Wie führe ich die offiziellen Artefakte in Java aus?**
```java
SyntaxReport report = SyntaxValidator.validate(Files.readAllBytes(invoice));
```

**Wie schreibe ich UBL oder CII aus Java?**
```java
WriteResult cii = CiiWriter.writeWithReport(document, WriterOptions.defaults());
WriteResult ubl = UblWriter.writeWithReport(document, WriterOptions.defaults());
```

**Wie erzeuge ich das Hybrid-PDF aus Java?**
```java
byte[] pages = new PdfRenderer().render(invoice, RenderOptions.in(RenderLanguage.ENGLISH));
EmbedResult hybrid = FacturX.embedWithReport(pages, invoice,
        EmbedOptions.of(FacturXProfile.EN_16931));
```

**Wie speichere ich aus Java: die kanonischen Bytes und die Digests?**
```java
byte[] canonical = Canonicalizer.canonicalBytes(document);
String semanticDigest = Canonicalizer.semanticDigest(document);
String documentDigest = Canonicalizer.documentDigest(document);
```

**Wie behalte ich die Bruttowerte, die ein Verbraucher gesehen hat?**
```java
Gross gross = Gross.on(invoice);
gross.line(0).displayedGrossUnitPrice("99.99");
gross.displayedGrossTotal("99.99");
```
