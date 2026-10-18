# Inputs of the packaging smoke test for `esj view`

*Part of [EN16931 Semantic JSON](../../README.md).*

[`not-well-formed.xml`](not-well-formed.xml) is the head of a UBL invoice that ends in the middle
of an element: the input of the case `view-not-well-formed` of [`../cases.sh`](../cases.sh). A
page for a file that is not read is one of the states of `esj view` ([`docs/view.md`](../../docs/view.md)),
and a native executable carries the path the XML reader takes to refuse such a file only where a
traced run reached it: the native executable of 1.0.0-rc.2 left with exit code 5 on it, the jar
with 2. [`../smoke.sh`](../smoke.sh) compares the page and the exit code of every packaged
artefact with the jar's. The file is written for this purpose and is nobody's invoice.
