# ISO Schematron XSLT 2 skeleton

The four stylesheets in this directory are the XSLT 2 implementation of ISO Schematron, the
"skeleton", in its 2010 release by Rick Jelliffe with the Academia Sinica Computing Centre,
Taiwan. `esj packs fetch` compiles the Schematron files a recipe fetches with them, in the three
stages the skeleton defines: `iso_dsdl_include.xsl`, `iso_abstract_expand.xsl`, then
`iso_svrl_for_xslt2.xsl`, which imports `iso_schematron_skeleton_for_saxon.xsl`. The compiled
artefacts of the bundled packs are the output of an XSLT 2 skeleton of the same family, run by
their publishers; which copy of it, the files do not say.

| Item | Value |
|---|---|
| Implementation | ISO Schematron, XSLT 2 skeleton, 2010 release (version entries up to 2010-07-10; `iso_abstract_expand.xsl` 2013-09-19) |
| License | MIT, in the head of every file and in the `LICENSE` beside this one |
| Taken from | Maven Central, `com.helger.schematron:ph-schematron-xslt:8.0.6`, directory `external/schematron/20100710-xslt2/` |
| That artefact | Apache License, Version 2.0, Philip Helger; SHA-1 `754ef0e56ad3818cdb235561deb128079a5be90a` (as Maven Central publishes it), SHA-256 `eac96bfab38187cfc22bb98b227f4e515beffb8ebf24a8bbcb38b392c41d169b` |
| Modified here | no; the copy carries seven changes of that artefact, each marked with a comment beginning `[ph]` (see `LICENSE`) |

## Files and digests

SHA-256 over the bytes of each file, lowercase hexadecimal:

| File | SHA-256 |
|---|---|
| `iso_abstract_expand.xsl` | `16e8e7da796100de38446e64c4b2845c318e316a35c66098381b6846f6c89aac` |
| `iso_dsdl_include.xsl` | `e59976a934bcffba9998607f4fce5009130d69f9530ebee9a858aa289ac03412` |
| `iso_schematron_skeleton_for_saxon.xsl` | `3fa57d52a633d2f73214e85f22005a40db3a6c9f7d7917c73e3ed5a6973c0787` |
| `iso_svrl_for_xslt2.xsl` | `a3a497c2e792700575111bce2bc646c435edc5296334c16a29fc7a114c2fa951` |
| `LICENSE` | `2596b52771ff546bb2c4b4e90f176450c5debf0cca62a9cfc605a188cab4a955` |

A test of this module recomputes the five digests over the files as they are shipped.

## What is not here

The skeleton's localized message files (`sch-messages-*.xhtml`), its XSLT 1 variants, its
documentation and the message-only front end `iso_schematron_message_xslt2.xsl` are not
vendored: the compilation writes SVRL in the skeleton's default language, and the resolver of
`SchematronCompiler` answers the four stylesheets above and nothing else.
