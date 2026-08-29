# TeeVClean visual system

## Direction

TeeVClean uses a **verified-clean** visual language. The TV frame represents the device, the
lime checkmark is a "clean sweep" that reads as a completed, safe cleanup, and the sparkle
signals a fresh, tidy result. The palette is shared 1:1 with the in-app Compose UI so the
launcher icon, banner, and running app all feel like one product.

## Assets

- `teevclean-brand.svg` — primary launcher mark, favicon source, and compact brand symbol.
- `teevclean-banner.svg` — wide hero/banner artwork for product pages, store listings, and onboarding.

Both files are vector assets and can be exported to PNG at the target Android TV density or used
as source artwork for the adaptive launcher icons in `app/src/main/res/drawable`.

## Palette

| Token | Hex | Use |
|---|---|---|
| Ink | `#101311` | App icon background and primary surfaces |
| Deep screen | `#0E1510` | TV display and elevated dark panels |
| Frame | `#1E2A1B` | Icon TV chassis and stand |
| Lime | `#B7F35B` | Checkmark, sparkle, links, focused/active navigation |
| Muted | `#9BA79C` | Secondary text |
| Soft white | `#FFFFFF` | Primary text and high-contrast details |

## Android TV usage

Keep the visual language high contrast and legible at the 10-foot distance. Lime is reserved for
the brand mark, the primary focused action, and completed/safe states; it should never be used
for large fills where it would overwhelm the calm ink surface.
