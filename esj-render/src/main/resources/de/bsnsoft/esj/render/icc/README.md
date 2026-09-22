# Vendored sRGB ICC profile

`sRGB2014.icc` is a copy, byte for byte unmodified, of the v2 sRGB profile of the ICC
profile library named below. The PDF renderer of this module embeds it in every document it
writes, as the destination profile of the PDF/A output intent: a PDF/A-3b file has to say
which colour space its device-dependent greys and colours are to be read in, and it has to
carry the profile that says it rather than name one the reader may or may not have.

| Item | Value |
|---|---|
| Publisher | International Color Consortium (ICC) |
| Library | <https://registry.color.org/profile-library/> |
| Page | <https://registry.color.org/rgb-registry/srgbprofiles>, the v2 sRGB profile |
| File | `sRGB2014.icc`, 3024 bytes |
| SHA-256 | `384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a` |
| Profile version | ICC 2.0, device class `mntr`, data colour space `RGB`, PCS `XYZ` |
| Copyright tag inside the file | `Copyright International Color Consortium, 2015` |
| Modified | no |

A test of this module recomputes the digest over the file as it is shipped and reads the
header back out of it, so a change to either is a failing build rather than a quiet
divergence from this table.

## Licence

The ICC states the terms of its profile library as follows, verbatim:

> This profile is made available by the International Color Consortium, and may be copied,
> distributed, embedded, made, used, and sold without restriction. Altered versions of this
> profile shall have the original identification and copyright information removed and shall
> not be misrepresented as the original profile.

Nothing here alters the file: it is shipped as it stands and embedded as it stands, which is
what an output intent needs and what those terms permit. The NOTICE of this repository names
the profile, its publisher and these terms.

## Why this profile

sRGB is the colour space a generic invoice layout is written for: the page is black text,
grey labels and a grey rule, and those greys are `DeviceGray`, which PDF/A only allows a file
to use when the file also says how a device-dependent colour is to be interpreted. The v2
profile is the one to embed rather than the v4 preference profile, which is a perceptual
rendering profile and larger; three kilobytes per rendering is the whole cost of the output
intent.

The profile is not this project's, and it is not one this project generated. A hand-built
profile would be three kilobytes of arithmetic nobody could check against anything; this one
is the reference file, identified by its digest, which any reader can compare against the
copy the ICC publishes.
