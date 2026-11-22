# Vendored sRGB ICC profile

`sRGB2014.icc` is a byte-for-byte unmodified copy of the v2 sRGB profile of the ICC profile
library named below. The PDF renderer of this module embeds it in every document it writes, as
the destination profile of the PDF/A output intent. A PDF/A-3b file has to say which colour
space its device-dependent greys and colours are read in. It has to carry the profile that
says so, not name one the reader may or may not have.

| Item | Value |
|---|---|
| Publisher | International Color Consortium (ICC) |
| Library | <https://registry.color.org/profile-library/> |
| Page | <https://registry.color.org/rgb-registry/srgbprofiles>, the v2 sRGB profile |
| File | `sRGB2014.icc`, 3024 bytes |
| SHA-256 | `384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a` |
| Compared with | <https://registry.color.org/rgb-registry/profiles/sRGB2014.icc>, byte-identical, 2026-10-07 |
| Profile version | ICC 2.0, device class `mntr`, data colour space `RGB`, PCS `XYZ` |
| Copyright tag inside the file | `Copyright International Color Consortium, 2015` |
| Modified | no |

A test of this module recomputes the digest over the file as it is shipped and reads the header
back out of it. A change to either fails the build instead of quietly diverging from this
table.

## Licence

The ICC states the terms of its profile library as follows, verbatim:

> This profile is made available by the International Color Consortium, and may be copied,
> distributed, embedded, made, used, and sold without restriction. Altered versions of this
> profile shall have the original identification and copyright information removed and shall
> not be misrepresented as the original profile.

Nothing here alters the file. It is shipped as it stands and embedded as it stands, which is
what an output intent needs and what those terms permit. The NOTICE of this repository names
the profile, its publisher and these terms.

## Why this profile

A generic invoice layout is written for sRGB: the page is black text, grey labels and a grey
rule. Those greys are `DeviceGray`, which PDF/A allows only when the file also says how to
interpret a device-dependent colour. This project embeds the v2 profile, not the v4 preference
profile, which is a perceptual rendering profile and larger. The whole output intent costs
3 kilobytes per rendering.

The profile is not this project's, and this project did not generate it. A hand-built profile
would be 3 kilobytes of arithmetic nobody could check against anything. This one is the
reference file, identified by its digest, which any reader can compare against the copy the
ICC publishes.
