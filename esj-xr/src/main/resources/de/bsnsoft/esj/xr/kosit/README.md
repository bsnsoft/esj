# Vendored KoSIT stylesheets

The files in this directory are copies, byte for byte unmodified, of the XSLT
stylesheets that transform a UBL 2.1 or UN/CEFACT CII invoice into the semantic XR
representation.

| Item | Value |
|---|---|
| Repository | <https://github.com/itplr-kosit/xrechnung-visualization> |
| Tag | `v2026-08-31` |
| Path in that repository | `src/xsl/` |
| License | Apache License, Version 2.0 (see the `LICENSE` file beside this one) |
| Publisher | Koordinierungsstelle für IT-Standards (KoSIT) / XStandards Einkauf |
| Modified | no |

## Files and digests

SHA-256 over the bytes of each file, lowercase hexadecimal:

| File | SHA-256 |
|---|---|
| `cii-xr.xsl` | `216c591ef7bd887e2f89d5623bd20fbf73a9b48ae51f93fdbb0e46d1645f63e3` |
| `common-xr.xsl` | `3155e48fe19dd23ff74e3f191721e28c7a51a5e76a093fb1e2ee60bfe08edc41` |
| `functions.xsl` | `1e8e2c414a19d63007408856649037c8654f5c7391618c48010865dac60db3ed` |
| `ubl-creditnote-xr.xsl` | `8cf629bd45887447d605b442201bad5f14dff118133b358adb4468c5fd31cf89` |
| `ubl-invoice-xr.xsl` | `ef2c2af5efbc5e130a4fe4b67c62080ff179a67df08522dbf9cfcf305093c899` |
| `LICENSE` | `e0d7665e91531aebb79e4feaa415796f076b0f93e9eddd6d1d05efe9d93808ac` |

A test of this module recomputes these digests over the files as they are shipped, so a
change to one of them is a failing build rather than a quiet divergence from the table.

## Why these five

`ubl-invoice-xr.xsl`, `ubl-creditnote-xr.xsl` and `cii-xr.xsl` are the entry points, one
per document type. Each of them includes `common-xr.xsl`, which in turn includes
`functions.xsl`; those two are here because the three entry points need them and for no
other reason. The localization files the upstream repository keeps beside them are not
here: the variables that read them are never evaluated on the way to the XR
representation, which the corpus test of this module demonstrates for every instance it
reads.

The resolver of this module answers these five file names and nothing else, so an
`xsl:include` cannot reach the file system or the network.

## Relationship to XRechnung

This module is not an XRechnung implementation and not a KoSIT deliverable. It uses the
stylesheets for one thing only: to be told which business term of EN 16931-1 a syntax
element stands for, so that this repository does not have to carry a second opinion on
the syntax bindings of the standard.
