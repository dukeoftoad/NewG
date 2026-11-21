# ToadMark tokens

**ToadMark** is the shared toad-family design language: one brand palette, one metric
scale, one motion character across toadGALLERY, toadHEALTH and toadPLAYER.

```
design/tokens/toadmark.json          ← single source of truth (edit this)
scripts/gen_tokens.py                ← deterministic generator
app/src/main/java/com/toadgallery/app/ui/theme/ToadMarkTokens.kt   ← Compose consumer (generated)
design/flutter/toadmark_tokens.dart  ← Flutter consumer (generated)
```

## How to change a token

1. Edit `design/tokens/toadmark.json`.
2. Run `python3 scripts/gen_tokens.py`.
3. Commit the JSON **and** both generated files together in one change.

The drift test (`ToadMarkTokensDriftTest`) fails CI whenever the committed
artifacts stop matching the JSON, so a stale consumer can never merge.

## What lives where

| Layer | File | Consumer |
|---|---|---|
| Brand colors (light/dark seeds, surfaces, secondary/tertiary) | `color.light` / `color.dark` | Palette definitions (`Color.kt`), both stacks |
| Accent colors (coral, sun, pond, lilypad) | `color.accent` | Favorites, memories, info pills |
| Neutral/text colors (cream, paper, ink, moss) | `color.neutral` | ToadMark light-scheme text and backgrounds |
| Radius scale (badge 6 / thumb 8 / cover 12 / control 16 / card 20 / sheet 28) | `radius` | Shapes (`Theme.kt`), chips, sheets, cards |
| Spacing scale (4/8/12/16/24) | `gap` | Layout padding and gaps |
| Touch target floor | `touch.minTarget` | `minimumInteractiveComponentSize` enforcement |
| Motion durations (fast 150 / medium 250 / slow 400 ms) | `motion.durationMs` | `MotionPolicy` |
| Spring presets (spatial = slight overshoot; effects = flat settle) | `motion.spring` | `MotionPolicy` spring helpers, future Flutter surfaces |
| Settings-row metrics | `row` | Settings and sub-page rows |
| Badge metrics | `badge` | Pills and badges |
| Typography policy (brand roles vs neutral roles) | `type` | Guidance — families stay app-side |

## Provenance and licensing

- Brand palette values re-expressed from toadPLAYER's `ToadTheme`
  (`lib/theme/toad_theme.dart`, dukeoftoad/toadPLAYER, **LGPL-3.0-only**, © 2026 zarzet).
- Accent and neutral values from toadHEALTH's `ToadColors` (`lib/theme.dart`,
  dukeoftoad/toadHEALTH, **MIT**, © 2025 dukeoftoad).
- Metric scale (radius/gap/touch/motion durations/row metrics) follows toadPLAYER's
  `AppTokens.standard` (`lib/theme/app_tokens.dart`, LGPL-3.0-only).
- Spring presets are tuned Compose `spring()` parameters that follow the
  M3-Expressive spatial/effects split; **no `material_3_expressive` package code is
  copied** and the package is not a dependency of toadGALLERY.

Only numerical values and color constants are taken. No source code is copied from
either upstream project, and all files generated from these values carry the
attribution headers above.

## The Compose ↔ Flutter bridge

The JSON is stack-neutral. toadGALLERY's Compose theme layer reads the generated
Kotlin object at compile time; future Flutter surfaces (the moving-forward stack per
the v2.5.0 language policy) consume `toadmark_tokens.dart` — the same numbers,
zero re-derivation. Until a cross-repo shared package is approved, each app keeps
its in-repo consumer: toadHEALTH/toadPLAYER keep their own Dart token classes, and
toadGALLERY is the canonical home of the combined system.
