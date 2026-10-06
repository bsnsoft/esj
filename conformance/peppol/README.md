# Peppol BIS Billing 3.0: the evidence, fetched at test time

Nothing in this directory is OpenPeppol's. The Peppol BIS Billing 3.0 validation artefacts, as
published by OpenPeppol, carry no open-source licence, so they are not in this repository and
the pack that runs them is made on the user's machine by `esj packs fetch` from a recipe:
[`peppol-bis-billing-3.0.21.json`](../../packs/recipes/peppol-bis-billing-3.0.21.json), the
release `esj packs fetch peppol-bis-billing` follows, and
[`peppol-bis-billing-3.0.20.json`](../../packs/recipes/peppol-bis-billing-3.0.20.json).
The same holds for the documents each pack is measured against: OpenPeppol's own examples and
its own Schematron unit tests of the same release. This page names them; the tests fetch them.

Two test classes do, once for each release, and only with `-Desj.network=true`; without it they
are skipped with a message that names the property, and the build stays offline:

| Test | What it does |
|---|---|
| `esj-syntax` `PeppolUnitTestsTest` | fetches and compiles the pack, then runs every test of `rules/unit-UBL-PEPPOL/` and `rules/unit-CII-PEPPOL/` through the rule sets its configuration names and compares the rules that fire with the ones each test expects |
| `esj-cli` `PeppolPackTest` | runs `esj packs fetch` — for 3.0.21 as `esj packs fetch peppol-bis-billing` —, then `esj validate --packs` over every example of `rules/examples/` and over five documents made from `base-example.xml` by one change each |

Every file is fetched from the revision its recipe pins — the tag `v3.0.20`, and for 3.0.21, which
OpenPeppol did not tag, the commit `806866bd2bd91d7e9623b68f08164e8fbe9e67a0` — and weighed
against the digest below before it is used; a file that differs fails the test by name. The
continuous integration runs both in its JDK 21 build job (`.github/workflows/ci.yml`).

## Evidence

3.0.20 was run on 2026-09-30 and 3.0.21 on 2026-10-06, each with the recipe as it stands,
`mvn -B verify -Desj.network=true`. The two tests hold every count below, the rule table row by
row; the times were taken on a macOS arm64 machine under load, and are indications.

| | 3.0.21 | 3.0.20 |
|---|---|---|
| `esj packs fetch` | 4 files fetched, 4 rule sets compiled, 30 files written; about 1.5 seconds in process | 4 files fetched, 4 rule sets compiled, 30 files written; about 5 seconds with the jar (1.3 fetching, 3.3 compiling), 2.3 with the native executable of macOS arm64, which writes the same bytes |
| OpenPeppol's examples, `rules/examples/` | 10 of 10 `VALID`, exit 0 | 9 of 9 `VALID`, exit 0 |
| OpenPeppol's unit tests | 110 test sets, of which 2 hold their tests in a comment and run nothing; 493 tests; 493 expectations over 62 rules | 102 test sets, 2 of them without a test; 354 tests; 354 expectations over 58 rules |
| Rules whose every expectation holds | 62 of 62 | 58 of 58 |
| Rules with an expectation that does not hold | 0 | 0 |

A second fetch leaves a pack alone. With both releases in one pack directory, a Peppol document
is judged by 3.0.21, the newest release of the pack; `--pack peppol-bis-billing/3.0/3.0.20` runs
the older one.

A test set of `rules/unit-UBL-PEPPOL/` and `rules/unit-CII-PEPPOL/` names a configuration of
OpenPeppol's build and holds tests, each a document — mostly a fragment — and the rule
identifiers it expects to fire as an error or as a warning, or not to fire, sometimes with a
count. `PeppolUnitTestsTest` runs the rule sets of the configuration over each document as the
syntax engine runs them, without the XML Schema in front, because a fragment is no document the
schema admits and the tests do not ask about it: `peppolbis-en16931-base-3.0-ubl` and `-cii` run
the Peppol rules of the syntax, `peppolbis-en16931-01-3.0-ubl-invoice` runs the EN 16931 rules
with them. A rule is *identical* where every expectation about it holds, flag and count included;
a dash marks a rule the release has no test about.

| Rule | 3.0.20 | 3.0.21 |
|---|---|---|
| `PEPPOL-COMMON-R040` | 2 identical | 2 identical |
| `PEPPOL-COMMON-R041` | 4 identical | 4 identical |
| `PEPPOL-COMMON-R042` | 4 identical | 4 identical |
| `PEPPOL-COMMON-R043` | 12 identical | 12 identical |
| `PEPPOL-COMMON-R044` | 11 identical | 11 identical |
| `PEPPOL-COMMON-R045` | 11 identical | 11 identical |
| `PEPPOL-COMMON-R046` | 10 identical | 10 identical |
| `PEPPOL-COMMON-R047` | 11 identical | 11 identical |
| `PEPPOL-COMMON-R049` | 8 identical | 8 identical |
| `PEPPOL-COMMON-R050` | 6 identical | 6 identical |
| `PEPPOL-COMMON-R052` | 4 identical | 4 identical |
| `PEPPOL-COMMON-R053` | 4 identical | 4 identical |
| `PEPPOL-COMMON-R054` | — | 31 identical |
| `PEPPOL-COMMON-R055` | — | 31 identical |
| `PEPPOL-COMMON-R056-1` | — | 33 identical |
| `PEPPOL-COMMON-R057` | — | 31 identical |
| `PEPPOL-EN16931-CL001` | 4 identical | 4 identical |
| `PEPPOL-EN16931-CL002` | 6 identical | 6 identical |
| `PEPPOL-EN16931-CL003` | 6 identical | 6 identical |
| `PEPPOL-EN16931-CL006` | 2 identical | 2 identical |
| `PEPPOL-EN16931-CL007` | 4 identical | 4 identical |
| `PEPPOL-EN16931-CL008` | 8 identical | 8 identical |
| `PEPPOL-EN16931-F001` | 5 identical | 5 identical |
| `PEPPOL-EN16931-P0100` | 7 identical | 9 identical |
| `PEPPOL-EN16931-P0101` | 4 identical | 6 identical |
| `PEPPOL-EN16931-P0104` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0105` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0106` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0107` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0108` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0109` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0110` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0111` | 3 identical | 3 identical |
| `PEPPOL-EN16931-P0112` | 4 identical | 4 identical |
| `PEPPOL-EN16931-R001` | 5 identical | 5 identical |
| `PEPPOL-EN16931-R002` | 7 identical | 7 identical |
| `PEPPOL-EN16931-R003` | 7 identical | 7 identical |
| `PEPPOL-EN16931-R004` | 6 identical | 10 identical |
| `PEPPOL-EN16931-R005` | 6 identical | 6 identical |
| `PEPPOL-EN16931-R006` | 3 identical | 3 identical |
| `PEPPOL-EN16931-R007` | 4 identical | 9 identical |
| `PEPPOL-EN16931-R010` | 4 identical | 4 identical |
| `PEPPOL-EN16931-R020` | 4 identical | 4 identical |
| `PEPPOL-EN16931-R040` | 12 identical | 12 identical |
| `PEPPOL-EN16931-R041` | 11 identical | 11 identical |
| `PEPPOL-EN16931-R042` | 11 identical | 11 identical |
| `PEPPOL-EN16931-R043` | 14 identical | 14 identical |
| `PEPPOL-EN16931-R044` | 5 identical | 5 identical |
| `PEPPOL-EN16931-R046` | 4 identical | 4 identical |
| `PEPPOL-EN16931-R051` | 6 identical | 6 identical |
| `PEPPOL-EN16931-R053` | 5 identical | 5 identical |
| `PEPPOL-EN16931-R054` | 7 identical | 7 identical |
| `PEPPOL-EN16931-R055` | 4 identical | 4 identical |
| `PEPPOL-EN16931-R061` | 5 identical | 5 identical |
| `PEPPOL-EN16931-R080` | 7 identical | 7 identical |
| `PEPPOL-EN16931-R100` | 7 identical | 7 identical |
| `PEPPOL-EN16931-R101` | 4 identical | 4 identical |
| `PEPPOL-EN16931-R110` | 9 identical | 9 identical |
| `PEPPOL-EN16931-R111` | 9 identical | 9 identical |
| `PEPPOL-EN16931-R120` | 12 identical | 12 identical |
| `PEPPOL-EN16931-R121` | 8 identical | 8 identical |
| `PEPPOL-EN16931-R130` | 7 identical | 7 identical |

Documents made from `base-example.xml` (the same file in both releases) by one change each,
through `esj validate --packs`:

| Change | Exit | Rules that fire, from the artefacts and the native rule pack |
|---|---|---|
| no invoice number (BT-1) | 1 | `BR-02`, `PEPPOL-EN16931-R008` |
| VAT category code `X` on a line | 1 | `BR-CL-18`, `BR-S-08` |
| category `S` with the exemption reason `VATEX-EU-G` | 1 | `BR-S-10`, `PEPPOL-EN16931-P0104` |
| a business process no Peppol process names (BT-23) | 1 | `PEPPOL-EN16931-R007` |
| a specification identifier no pack names (BT-24) | 9 | none: the bundled pack is chosen and no CIUS rule runs; with `--pack` naming the Peppol pack the Peppol rule sets do not apply either |

Both releases answer each change alike. The last row is the design, not a gap: the specification
identifier is how a pack and its rule sets are chosen, so `PEPPOL-EN16931-R004`, which asks for
that identifier, cannot fire on a document that is judged by the pack. The unit tests hold it,
with the other rules.

## From 3.0.20 to 3.0.21

What the four rule sets of the two releases name, by rule identifier and flag, compared on the
fetched files. Rules whose test changed but whose identifier and flag did not are not listed.

| Rule set | New | From `warning` to `fatal` | Withdrawn |
|---|---|---|---|
| Peppol, UBL (159 → 165 rules) | `PEPPOL-COMMON-R054`, `-R055`, `-R056-1`, `-R056-2`, `-R057`, `DE-R-T02`, each `warning` | `PEPPOL-COMMON-R052`, `-R053`, `DK-R-003`, `DK-R-017` | — |
| Peppol, CII (85 → 90 rules) | `PEPPOL-COMMON-R054`, `-R055`, `-R056-1`, `-R056-2`, `-R057`, each `warning` | `PEPPOL-COMMON-R052`, `-R053` | — |
| EN 16931, UBL (1.3.15 → 1.3.16) | `UBL-SR-56` (`fatal`) | — | `BR-CO-25` |
| EN 16931, CII (1.3.15 → 1.3.16) | `CII-SR-467` to `CII-SR-494`, 28 rules, 25 `fatal` and `CII-SR-474` to `-476` `warning` | — | `BR-CO-25` |

`PEPPOL-COMMON-R040`, the GS1 check digit of a GLN (scheme `0088`), reads different fields in the
two syntaxes in both releases: in UBL `EndpointID`, party identifiers and `CompanyID`, in CII
every identifier with that scheme, the item's standard identifier (BT-157) and the delivery
location (BT-71) among them. Five of the ten 3.0.21 examples carry a `0088` value there that is no
valid GLN: written to CII by `esj convert --to cii` they fail the rule, where the UBL original
passes.

## Files

The files of each release, SHA-256 and path, under the heading of its recipe. Two unit test
files end in `.xm` rather than `.xml` in the publisher's repository; they are listed and run like
the others.

### `peppol-bis-billing-3.0.21`

Below the commit `806866bd2bd91d7e9623b68f08164e8fbe9e67a0` (branch `2026-Q2-QA2`; the release
has no tag), retrieved 2026-10-06.

```text
aa3df18eb8c634624637eb229891d989c5cfb7cd0d08894ff8e58c58f247ea5b  rules/examples/Allowance-example.xml
59f96ae9a77ed3eda4ac17f499994fbd1b050432edf3bb8c117d7f3bca8d5f95  rules/examples/Vat-category-S.xml
08e0ad82e0dbe7e16d7533c01761843343a56954ea24881d0f7f1cce06f8879e  rules/examples/base-creditnote-correction.xml
1b7cc3ff1834c8963f2c93f30f171b58002cbf0b2c52dc8765e7e83aebb9f7c9  rules/examples/base-example.xml
97e9dd5fc1747a9a2447b38bae8d1de3317d2636714369bdd99ba7dd1fb0ee1a  rules/examples/base-example_profile02.xml
000781ee8cb7794a140bb1308f7f7a2c9ded3623571b4297aef38423971ab5a4  rules/examples/base-negative-inv-correction.xml
cdb84e4ce1a770f6e4a8949dcbe37493bc37aeded5232b592a1a48feb221a504  rules/examples/sales-order-example.xml
4f2f20de65a8c088bba6c43177c75b5066bf30f54b996bc1729a82097a6591db  rules/examples/vat-category-E.xml
212c4eede960a775f8e79dca6cd6430e6f8c105560fbfdeaa03d5e6587eaa1ec  rules/examples/vat-category-O.xml
fed3f3f393591e1c628f2da4a3aa77e8c66e4ed8ed4368e18837782beab60daa  rules/examples/vat-category-Z.xml
90b9078daed0980b7ba4ec9df2456ae1f6d5677d34d68b9f66bbeafeb73bbf0b  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R042.xml
987b7f81eb3e4bc7914f6e43ca8252c687a085e21b47210dba4fa552cd850b22  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R043.xml
1babbddc50a71a11885a94d83470ce7659bfea7fb9b1f045d9007a1c0e78324c  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R044.xml
c194f80b8421ee3eff856e544aaf5af2d4370e75eae0054d60f75ef97f14896b  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R045.xml
53a5ae478686aa95405ee6696e578b261438728be843f5fe342d0f5bdd0d6957  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R046.xml
ac0bae7ad1223c13808f923213d922491e18e547c5e85cfacfe05bbc3a367ee2  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R047.xml
8f7440d7a7365bf8617c435a3046aa64e1aae24246bc02c4c86e331263ccc4e7  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R048.xm
4f5e4e6b8061d34679dde2efda7c0c1b6ed12565ec66cd263ced612a42c9579d  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R049.xml
4894b1150f740028e34e497f73a9998f74ce28970b17de208f3e64b549833e95  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R050.xml
cf186067b9f9377ec3dbecdfa887c4d1b031c5819fcf89d8aac80553a7f40a31  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R052.xml
dee2ad4059f0b9275c0630fc8d447c347c33385ed9ebb4b714dd31a2c8a22205  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R053.xml
d190a907c6f72aa6b072a3fa000c67e5812a97f5ea0e454502ddef3e3ae2b21d  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R054.xml
63a70d28ea7bda7859847f8a150d001b9ceb5345d6b9b74e79430e2b04db3460  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R055.xml
568c47b83f1314b3d4b49337aeb1d39d5b2c4aaddad3460bdabdd51f37b0b259  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R056-1.xml
23e04a5d74fb86b82ada07318eb0f2c52daf99e85456b82b55803d9ea42734c6  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R057.xml
b97e510a62ddcaa59d0c9f5bce786091bcf442d9d4c098167f3053c0ff82f6c8  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL001.xml
b19acfecf3501dfcf24a10d7e344fb71bc15e2e1f2a62cda8f5e5e8b707193d7  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL002.xml
d4a770299b614511dcefa37c868d4bd4d4bfe94bc3c95ceb86c598cf6d36b63a  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL003.xml
bb98cc1489ec2c056852798bebf073bce98795b8032d8b0741239c2288009f3e  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL007.xml
ad6e6264f6fe1237666fcda5c5246915b06f32da1b06555109f114d056ab0b79  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL008.xml
75d6fbe85753c00b152754ad2cc45fb7d508a3abbffb2d4b1c0071c2946a6bae  rules/unit-CII-PEPPOL/PEPPOL-EN16931-F001.xml
d8d9e5ad54deefff150072182eef2aa4e59250d19248e57ef60c581ce5db8da4  rules/unit-CII-PEPPOL/PEPPOL-EN16931-P0100.xml
ae478d8b0034dd927a32548477a500d951e7848ee004c7dcb6b0d51c224a1ea4  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R001.xml
742b799fcc4cfc6a59d2179961c694965bdaf93d5cdeca82486f71b211aa7bd0  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R002.xml
a7688c261ec780f196602163add3223b2ce45a2162a9778e1c20eca1715235ad  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R003.xml
eb64e64245c828d208e9c75aabf1e712c37b106a14054a0af7dcb404343e62ab  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R004.xml
1966843abccad5c920364ccb6605faeaa81ab6a2cdd7b27da9e4749749885ef0  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R005.xml
a4e4d95c16c3c32f0a706173ed1efe5aadfda9f5ba49156d70f200562df97e84  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R006.xml
7e418bff6304d23bcd697dc054d6c3cf1f2baac7fb0169b1ecf1e3a4e5beafdc  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R007.xml
1f4f809f21c108eb2fecb86161e84ee63593d98de1df23dfce301ff4bf8717d6  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R010.xml
6757c71a17a82529163dfb5db5d347462dd6a56c70e766218dd9b4f639c7be39  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R020.xml
2f8c587584b89af70ed053e1f7f40b997e4d743520b775d11f03b8bde529a548  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R040.xml
4bb984cabedaa175e4e2aef3ec65c9af2a073a47c9429b7daad7690cb246990a  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R041.xml
02b64491e6c55502e7a0bfa57c8b088e2c7a5b2149ab0aea7662b2ab4ae4294d  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R042.xml
509c43901e661989603ecca2e52cc79f1118c0d20e734023f827748f277bab19  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R043.xml
fd4289c261cadd0d3c866a272581b642573d23cc97ca7ec7065a36b6025139ac  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R053.xml
1b306a93f5f2fb6ef43c0af79ace13d6cd80f96e9ce4ad0243d7423a7ba2bd08  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R054.xml
fb346d0cfc903b59697ba636a4ec743c130d93254be75ac7e5acf9a5d5626543  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R055.xml
dd27693d37b344b886e0846fb9866ded50d9f7b0e6046dfd8fee9c6f15732694  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R061.xml
c40e1d895faae4ddc3010ddb1c0160b138270ff5f70c01f96fbfbda00a52462c  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R080.xml
7f335fc033ff25bb143e72aeebeac35ef3849005a6ba0f11cc6efc606a948f7d  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R100.xml
e2c9089a7d4c43cf383d1bd21c5300f05d35795f78ada29af45a9c48f948a0e9  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R101.xml
c8300231416e66557281ee00317e2bbb3437b80261e8cf76e222aa1b52c73048  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R110.xml
4179d455f5ee480da104a4b6831f94c6dcafb6e2ce6780166470d97fdb4eb3af  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R111.xml
272d5a9dc1b835931e1dff02d1127eced116057eb375529d79fe366eb4ec8f5b  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R120.xml
a9a0306fda2b5be773835e39dc5fb50042c17e076c2d70c48ae1198887fe779a  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R121.xml
2cc0c7bd8398bcd51f67c4ae61c44bb98fe6007707e56ecaddf79430587c27c5  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R130.xml
cf89f5d735c7031cf33f361c181698a713d9fc6ad43aeafeab5b1972354255da  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-EAS.xml
fa9b81411509f0912a5a2d09be672e12da5c85a603e76542292b694f73163567  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R040.xml
4e5cd7637bd3d03fb783aae26b378cae46b6987c48dc920a2ecba1c8a1829f87  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R041.xml
4c2156e0f824b788f91a78411079b6c611272692fb306eebb6c4dfb26ca49c36  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R042.xml
866ac3f40f5dfec8dd456b94f5fc87cecfe5b50ad70c978c88a54ac298b40a43  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R043.xml
54bd139777d230560cfa47bf59607452141758bc6c8633815172cc9bb269dfb6  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R044.xml
a7745c70cafa0c34dc4697f0a7c2c0ea9e71471a6cc83f740f58cef45b07edcb  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R045.xml
78d19c7de0723bd130c1d88d5b9a6b34cd7beda1612ab464d0f99e0ca183aa8f  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R046.xml
125368ae15483c5a3d1ad2c4a0c65843805450c7ea3d78d9893f16b4ed9c86c4  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R047.xml
b195b91a13e332fc6a2d1d4f7b7397440e489285b1249e630f40aa309d7098ae  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R048.xm
4d9de2f919d327fdc07256406cf40547fca08c00eeea88a86710cbd67ce7398f  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R049.xml
3f8a66951cdee7c4cbfad98a1e0d529f0979293dd9ac873ad6a9a59610015ffc  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R050.xml
51a522cd302d3019714c5a5b0e0539acc7ff381dd11ae80d9176e8ac6f075e35  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R052.xml
953d431397396d564667ce410b5c1173ad7935490b15a9d9a5f0e9c3405a4bf0  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R053.xml
a850c5b605ea1d4f6f49f518ba137695993449439a045c8c829633c78e3c44d3  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R054.xml
b67d75edddc0285bb0728d2a425f2cd0f1e31ec62fc923ff45c9d9c64dbae41d  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R055.xml
5224c46d70e7d80a6c5ee51f18cf8b128c5d22f4dfbb20f0080ef531d6e6fcc0  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R056-1.xml
df36915da5ae9b113a81982e1c12803a6d054192c7cbdad7d72a6fe3c919a78c  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R057.xml
ba8f765aea1efb27456733f6dcbf2454621d3b651cce873860bb09c4fcccbd18  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL001.xml
4200ad9894d25afde8fee8d602e1afbfff7e46abe4713ae1afebc9bacf0dcf3b  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL002.xml
3abd4ac7a67178c50a1eeb7019442db401a96eaad4ab249a288a306c47355ba6  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL003.xml
dbf7d31368f5204456fb1f76545ed475582b7125315a907e797ab68951b2befc  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL006.xml
0e657b61df3725bc04e6b774ed1320cfcc5aabaf146c460de869fae05e6e13eb  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL007.xml
18a27b00d166dac9eafd5dcf2a913d429da69b3e689e086d29cf466330f5f7cc  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL008.xml
ef3052f1dac1505322054120719e61f467f85f55a0b8b6ce284858f020341259  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-F001.xml
f890ef91d5b5d0a12614f4a98a944c84c55c4d07899a23ae64f8caa1ab74f5bf  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0100.xml
51aac668ede6075def80380e6c491d36cdf7e5accb0fb24bbef769bfcfa0d21f  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0101.xml
292541476be12b400d90e0636d740dee7314f8dbe6eb441ae12b1871840d81bb  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0104.xml
b4431542729fb1e98485941b156d155e935bcfebc8fa11f6d8e07dda6729d363  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0105.xml
4886c5281d5632b11fb8a2c322f09dcc6c4e503713dac04a88aa611941fabadf  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0106.xml
fcf586cf99b2f849a53cdb564dcdabca18e821cd83a20eb9a464f8db93a47ac2  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0107.xml
3b095268e76f640e464764b63fd4b37c6f7ac52d37d324caa10f4f350b6ca84c  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0108.xml
9bd50de4d9710bcebc39bbec57ab29bafffe9217803a2557422d45c479ca96bc  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0109.xml
5919104742e69c463eb1502c8e570ef51eb1d14f8cda597744d64bbb11df9ca8  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0110.xml
07d26f1ee55653d0fdf6030258d238f5e6c7737c257e02a7299035dcc32a122d  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0111.xml
10c07f7eb39167967f0192cf13e6daac0e3d6fdf4668db0933ee9cf39d8eeedb  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0112.xml
74d2c2b5e2de912accd0b85e5b01f7c5a462bfb9b2774833c526899a1780cb53  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R001.xml
c4c65821cd78d4d5a71445d989a6611834168efe260da35e791d1e316c66e62a  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R002.xml
89b8b3146665acfa51892beb06161e0f8f336edad7a9e6f6e8bb10d61188b80c  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R003.xml
49d61a473c2b1a3f76d1840b2a15e68943cbc3d7abe50e320ecc0a4be758cc55  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R004.xml
586a401c24965641461511878da90ae237e40261da76f6ad77d023c4d83947a7  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R005.xml
2181dba440fad278255b857161b9c709b3a8a36f9c5dc89a65c2f3515bb49730  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R007.xml
de4053d0b0a1e78c6f0e6521484ecb9bb7f76bf540fb0e06099b153c51934f42  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R010.xml
d63233ff4be71eafe5186a526e8116f0e973ce7e159c6304dad5630f88a13418  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R020.xml
98e3b05dd2bf9422baea6696bd87bf7ed0c1c9b6d3513949cb17edb53b49af99  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R040.xml
54724c8536cfc664951986af125a83eaca546505de387fea53c1f55cb44214b4  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R041.xml
da31f940c3a89d9214343d60ba53d95053cf6ed9e1b5c3c5ec742bf796aebff9  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R042.xml
9b721dd318a2f8d1b6645534f6ffe49dce3eb22b2fe6e8577acd2ddc8313a7ce  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R043.xml
935fcfa92f1fa7cefeab7c2b060fc120e6a6ccf50e7504ad5b89270757f03dc5  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R044.xml
117e3a94207eb55f6e8a1df70f0964058823264a96573b130f0e2eabb4547cc0  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R046.xml
2d42b24af3bd51d1ea9ebb8b76b89cf878e545b576626a6c186a60f88f1a6235  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R051.xml
1582e526896487c2386e50121fd3aa119c44ac3cef270e3fcef8004f692d03d8  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R053.xml
a57a1f7dfd65621034289c884148d52cabbf11b5e076b4be7d3cac4a5765db4c  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R054.xml
a97c2c2d4aac86efdeb9d5c56de24ecec7c591cf9c426549922664540dcdd657  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R055.xml
17d683bb738fa2be3f3651d0bb618b23353f30727a0d894efe798f36acd819c4  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R061.xml
8305296670ba58ea17db73ad60f7364d340ea80108228230d3181a023d452615  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R080.xml
a8aa2a1a76b395bf017df3cb3dc40c220b63c22a38984a97fe9a7391df000338  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R100.xml
88638c9faa9db4a18abcdab5ac6f36b31c2f31793e974930c0362778f93142ff  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R101.xml
dad562e145d3198369843f528bc8afb9d2397ea9f3eb27d6a8cf76c2ad0cadfe  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R110.xml
33b6aedfec3e371d11d811d728b5467b190495c5deb3ba01a3e9065204b0eeed  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R111.xml
e0b9540f9e1c8a5351e623e679bd477836491acb95d3e0d876dd864acfb6772e  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R120.xml
08ba843a866d900ef231715371221975c40a62ce6904cd2b391f558771303f44  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R121.xml
9cb71597c3b72a3813a7d0f0f363c47673c3f124410149c4d9f599c427cbbdd4  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R130.xml
```

### `peppol-bis-billing-3.0.20`

Below the tag `v3.0.20` (commit `261c458474e27d58a25be629cccac28883171c92`), retrieved
2026-09-30.

```text
aa3df18eb8c634624637eb229891d989c5cfb7cd0d08894ff8e58c58f247ea5b  rules/examples/Allowance-example.xml
59f96ae9a77ed3eda4ac17f499994fbd1b050432edf3bb8c117d7f3bca8d5f95  rules/examples/Vat-category-S.xml
08e0ad82e0dbe7e16d7533c01761843343a56954ea24881d0f7f1cce06f8879e  rules/examples/base-creditnote-correction.xml
1b7cc3ff1834c8963f2c93f30f171b58002cbf0b2c52dc8765e7e83aebb9f7c9  rules/examples/base-example.xml
000781ee8cb7794a140bb1308f7f7a2c9ded3623571b4297aef38423971ab5a4  rules/examples/base-negative-inv-correction.xml
cdb84e4ce1a770f6e4a8949dcbe37493bc37aeded5232b592a1a48feb221a504  rules/examples/sales-order-example.xml
4743f9dbe958cdb9f7871faa06eef003b4fea32673861a69b523c5eca52a780b  rules/examples/vat-category-E.xml
effff0baac622e1486c34240f06b9361cf58aaedbca44ac65826f1171d78f925  rules/examples/vat-category-O.xml
aaec0e1b6737c19164b4bcd26d0adce970d14e0a3fdeeb456b514993a92ff164  rules/examples/vat-category-Z.xml
90b9078daed0980b7ba4ec9df2456ae1f6d5677d34d68b9f66bbeafeb73bbf0b  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R042.xml
987b7f81eb3e4bc7914f6e43ca8252c687a085e21b47210dba4fa552cd850b22  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R043.xml
1babbddc50a71a11885a94d83470ce7659bfea7fb9b1f045d9007a1c0e78324c  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R044.xml
c194f80b8421ee3eff856e544aaf5af2d4370e75eae0054d60f75ef97f14896b  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R045.xml
53a5ae478686aa95405ee6696e578b261438728be843f5fe342d0f5bdd0d6957  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R046.xml
ac0bae7ad1223c13808f923213d922491e18e547c5e85cfacfe05bbc3a367ee2  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R047.xml
8f7440d7a7365bf8617c435a3046aa64e1aae24246bc02c4c86e331263ccc4e7  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R048.xm
4f5e4e6b8061d34679dde2efda7c0c1b6ed12565ec66cd263ced612a42c9579d  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R049.xml
4894b1150f740028e34e497f73a9998f74ce28970b17de208f3e64b549833e95  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R050.xml
9ea0aff7ba850704d19913d2b78c94f32759e91538eaac306a37be9fbd8163fd  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R052.xml
c75b0f530c9ce10bd2bb582dcab0b7a3b6630e60b4b515ea3edf9360077b116a  rules/unit-CII-PEPPOL/PEPPOL-COMMON-R053.xml
b97e510a62ddcaa59d0c9f5bce786091bcf442d9d4c098167f3053c0ff82f6c8  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL001.xml
b19acfecf3501dfcf24a10d7e344fb71bc15e2e1f2a62cda8f5e5e8b707193d7  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL002.xml
d4a770299b614511dcefa37c868d4bd4d4bfe94bc3c95ceb86c598cf6d36b63a  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL003.xml
bb98cc1489ec2c056852798bebf073bce98795b8032d8b0741239c2288009f3e  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL007.xml
ad6e6264f6fe1237666fcda5c5246915b06f32da1b06555109f114d056ab0b79  rules/unit-CII-PEPPOL/PEPPOL-EN16931-CL008.xml
75d6fbe85753c00b152754ad2cc45fb7d508a3abbffb2d4b1c0071c2946a6bae  rules/unit-CII-PEPPOL/PEPPOL-EN16931-F001.xml
efa36461072f827a0e20c122733f66c9d9f806ee07639b58d88a80647fec70c6  rules/unit-CII-PEPPOL/PEPPOL-EN16931-P0100.xml
ae478d8b0034dd927a32548477a500d951e7848ee004c7dcb6b0d51c224a1ea4  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R001.xml
742b799fcc4cfc6a59d2179961c694965bdaf93d5cdeca82486f71b211aa7bd0  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R002.xml
a7688c261ec780f196602163add3223b2ce45a2162a9778e1c20eca1715235ad  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R003.xml
ef5f4d933db5e48296bc2cb1eb5600be71307d8e4d571ef8197dfa373ac79380  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R004.xml
1966843abccad5c920364ccb6605faeaa81ab6a2cdd7b27da9e4749749885ef0  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R005.xml
a4e4d95c16c3c32f0a706173ed1efe5aadfda9f5ba49156d70f200562df97e84  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R006.xml
7e418bff6304d23bcd697dc054d6c3cf1f2baac7fb0169b1ecf1e3a4e5beafdc  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R007.xml
1f4f809f21c108eb2fecb86161e84ee63593d98de1df23dfce301ff4bf8717d6  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R010.xml
6757c71a17a82529163dfb5db5d347462dd6a56c70e766218dd9b4f639c7be39  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R020.xml
2f8c587584b89af70ed053e1f7f40b997e4d743520b775d11f03b8bde529a548  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R040.xml
4bb984cabedaa175e4e2aef3ec65c9af2a073a47c9429b7daad7690cb246990a  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R041.xml
02b64491e6c55502e7a0bfa57c8b088e2c7a5b2149ab0aea7662b2ab4ae4294d  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R042.xml
509c43901e661989603ecca2e52cc79f1118c0d20e734023f827748f277bab19  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R043.xml
fd4289c261cadd0d3c866a272581b642573d23cc97ca7ec7065a36b6025139ac  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R053.xml
1b306a93f5f2fb6ef43c0af79ace13d6cd80f96e9ce4ad0243d7423a7ba2bd08  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R054.xml
fb346d0cfc903b59697ba636a4ec743c130d93254be75ac7e5acf9a5d5626543  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R055.xml
dd27693d37b344b886e0846fb9866ded50d9f7b0e6046dfd8fee9c6f15732694  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R061.xml
c40e1d895faae4ddc3010ddb1c0160b138270ff5f70c01f96fbfbda00a52462c  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R080.xml
7f335fc033ff25bb143e72aeebeac35ef3849005a6ba0f11cc6efc606a948f7d  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R100.xml
e2c9089a7d4c43cf383d1bd21c5300f05d35795f78ada29af45a9c48f948a0e9  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R101.xml
c8300231416e66557281ee00317e2bbb3437b80261e8cf76e222aa1b52c73048  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R110.xml
4179d455f5ee480da104a4b6831f94c6dcafb6e2ce6780166470d97fdb4eb3af  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R111.xml
272d5a9dc1b835931e1dff02d1127eced116057eb375529d79fe366eb4ec8f5b  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R120.xml
a9a0306fda2b5be773835e39dc5fb50042c17e076c2d70c48ae1198887fe779a  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R121.xml
2cc0c7bd8398bcd51f67c4ae61c44bb98fe6007707e56ecaddf79430587c27c5  rules/unit-CII-PEPPOL/PEPPOL-EN16931-R130.xml
cf89f5d735c7031cf33f361c181698a713d9fc6ad43aeafeab5b1972354255da  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-EAS.xml
fa9b81411509f0912a5a2d09be672e12da5c85a603e76542292b694f73163567  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R040.xml
4e5cd7637bd3d03fb783aae26b378cae46b6987c48dc920a2ecba1c8a1829f87  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R041.xml
4c2156e0f824b788f91a78411079b6c611272692fb306eebb6c4dfb26ca49c36  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R042.xml
866ac3f40f5dfec8dd456b94f5fc87cecfe5b50ad70c978c88a54ac298b40a43  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R043.xml
54bd139777d230560cfa47bf59607452141758bc6c8633815172cc9bb269dfb6  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R044.xml
a7745c70cafa0c34dc4697f0a7c2c0ea9e71471a6cc83f740f58cef45b07edcb  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R045.xml
78d19c7de0723bd130c1d88d5b9a6b34cd7beda1612ab464d0f99e0ca183aa8f  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R046.xml
125368ae15483c5a3d1ad2c4a0c65843805450c7ea3d78d9893f16b4ed9c86c4  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R047.xml
b195b91a13e332fc6a2d1d4f7b7397440e489285b1249e630f40aa309d7098ae  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R048.xm
4d9de2f919d327fdc07256406cf40547fca08c00eeea88a86710cbd67ce7398f  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R049.xml
3f8a66951cdee7c4cbfad98a1e0d529f0979293dd9ac873ad6a9a59610015ffc  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R050.xml
d35976de531eccc2f933f921b0ba2971b0c6147daf0be323f31b6445263bc6e5  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R052.xml
07da1a6dd97743e13f6ef3f7b6dafba03f6d1deb3b8f8de7053101185bd9def4  rules/unit-UBL-PEPPOL/PEPPOL-COMMON-R053.xml
ba8f765aea1efb27456733f6dcbf2454621d3b651cce873860bb09c4fcccbd18  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL001.xml
4200ad9894d25afde8fee8d602e1afbfff7e46abe4713ae1afebc9bacf0dcf3b  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL002.xml
3abd4ac7a67178c50a1eeb7019442db401a96eaad4ab249a288a306c47355ba6  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL003.xml
dbf7d31368f5204456fb1f76545ed475582b7125315a907e797ab68951b2befc  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL006.xml
0e657b61df3725bc04e6b774ed1320cfcc5aabaf146c460de869fae05e6e13eb  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL007.xml
18a27b00d166dac9eafd5dcf2a913d429da69b3e689e086d29cf466330f5f7cc  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-CL008.xml
ef3052f1dac1505322054120719e61f467f85f55a0b8b6ce284858f020341259  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-F001.xml
6261fe722e9b2445d7a70cacf9c8852e2a992794c57e977944251a2e61a2668a  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0100.xml
3ad8249a214cfc088658de2d0c8dfb47d73d0eebb4955a5ada77c3a4b6d4eccb  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0101.xml
292541476be12b400d90e0636d740dee7314f8dbe6eb441ae12b1871840d81bb  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0104.xml
b4431542729fb1e98485941b156d155e935bcfebc8fa11f6d8e07dda6729d363  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0105.xml
4886c5281d5632b11fb8a2c322f09dcc6c4e503713dac04a88aa611941fabadf  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0106.xml
fcf586cf99b2f849a53cdb564dcdabca18e821cd83a20eb9a464f8db93a47ac2  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0107.xml
3b095268e76f640e464764b63fd4b37c6f7ac52d37d324caa10f4f350b6ca84c  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0108.xml
9bd50de4d9710bcebc39bbec57ab29bafffe9217803a2557422d45c479ca96bc  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0109.xml
5919104742e69c463eb1502c8e570ef51eb1d14f8cda597744d64bbb11df9ca8  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0110.xml
07d26f1ee55653d0fdf6030258d238f5e6c7737c257e02a7299035dcc32a122d  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0111.xml
6c2b888c9c0777a9ccfef54bfee6daa71b898259f6e768adad0eb880bc93ae7f  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-P0112.xml
74d2c2b5e2de912accd0b85e5b01f7c5a462bfb9b2774833c526899a1780cb53  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R001.xml
c4c65821cd78d4d5a71445d989a6611834168efe260da35e791d1e316c66e62a  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R002.xml
89b8b3146665acfa51892beb06161e0f8f336edad7a9e6f6e8bb10d61188b80c  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R003.xml
f7f38badffa441aacddccd2d0676adf7dcb1f6d1dfdedc98545df34ed6ddfdbf  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R004.xml
586a401c24965641461511878da90ae237e40261da76f6ad77d023c4d83947a7  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R005.xml
6964b894cbad9cf1fffc2e5c6732f46d06d8cd3578c597a804bfb39a12861dd7  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R007.xml
de4053d0b0a1e78c6f0e6521484ecb9bb7f76bf540fb0e06099b153c51934f42  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R010.xml
d63233ff4be71eafe5186a526e8116f0e973ce7e159c6304dad5630f88a13418  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R020.xml
98e3b05dd2bf9422baea6696bd87bf7ed0c1c9b6d3513949cb17edb53b49af99  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R040.xml
54724c8536cfc664951986af125a83eaca546505de387fea53c1f55cb44214b4  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R041.xml
da31f940c3a89d9214343d60ba53d95053cf6ed9e1b5c3c5ec742bf796aebff9  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R042.xml
9b721dd318a2f8d1b6645534f6ffe49dce3eb22b2fe6e8577acd2ddc8313a7ce  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R043.xml
935fcfa92f1fa7cefeab7c2b060fc120e6a6ccf50e7504ad5b89270757f03dc5  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R044.xml
117e3a94207eb55f6e8a1df70f0964058823264a96573b130f0e2eabb4547cc0  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R046.xml
2d42b24af3bd51d1ea9ebb8b76b89cf878e545b576626a6c186a60f88f1a6235  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R051.xml
1582e526896487c2386e50121fd3aa119c44ac3cef270e3fcef8004f692d03d8  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R053.xml
a57a1f7dfd65621034289c884148d52cabbf11b5e076b4be7d3cac4a5765db4c  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R054.xml
a97c2c2d4aac86efdeb9d5c56de24ecec7c591cf9c426549922664540dcdd657  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R055.xml
17d683bb738fa2be3f3651d0bb618b23353f30727a0d894efe798f36acd819c4  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R061.xml
8305296670ba58ea17db73ad60f7364d340ea80108228230d3181a023d452615  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R080.xml
a8aa2a1a76b395bf017df3cb3dc40c220b63c22a38984a97fe9a7391df000338  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R100.xml
88638c9faa9db4a18abcdab5ac6f36b31c2f31793e974930c0362778f93142ff  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R101.xml
dad562e145d3198369843f528bc8afb9d2397ea9f3eb27d6a8cf76c2ad0cadfe  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R110.xml
33b6aedfec3e371d11d811d728b5467b190495c5deb3ba01a3e9065204b0eeed  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R111.xml
e0b9540f9e1c8a5351e623e679bd477836491acb95d3e0d876dd864acfb6772e  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R120.xml
08ba843a866d900ef231715371221975c40a62ce6904cd2b391f558771303f44  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R121.xml
9cb71597c3b72a3813a7d0f0f363c47673c3f124410149c4d9f599c427cbbdd4  rules/unit-UBL-PEPPOL/PEPPOL-EN16931-R130.xml
```
