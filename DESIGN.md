<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

---
version: alpha
name: StarBurst-design-system
description: |
  The design system of the StarBurst client, built on Material 3 + Jetpack Compose. The brand centers on indigo (#6366F1), with violet and cyan forming a three-tier brand palette. The UI ships three semantic color schemes — Light / Dark / AMOLED — where AMOLED uses pure-black surfaces (#000000) for OLED power saving. Every visual token maps directly onto Compose's MaterialTheme.colorScheme / Typography; all radii, spacing and component styles live in ui/components and ui/theme, ready to be consumed by code. An opt-in Cartoon Style layer (CartoonStyle.kt) sits orthogonal to the palettes: it widens radii, adds 2.5dp ink outlines, hard 3dp offset shadows, a paper dot-grid backdrop and springy press/pop motion — without touching any color token.

colors:
  primary: "#6366F1"
  secondary: "#8B5CF6"
  tertiary: "#06B6D4"
  dark-primary: "#9DA3FF"
  dark-primary-container: "#2D2F6E"
  dark-surface: "#121218"
  dark-on-surface: "#E5E1E9"
  dark-surface-container: "#1E1E25"
  light-primary: "#4F52B8"
  light-primary-container: "#E0E0FF"
  light-surface: "#FCF8FF"
  light-on-surface: "#1C1B22"
  light-surface-container: "#F3EFF7"
  amoled-surface: "#000000"
  amoled-surface-container: "#0D0D12"
  status-connected: "#4CAF50"
  status-error: "#EF4444"
  status-warning: "#F59E0B"

typography:
  display-large:
    fontFamily: system sans-serif
    fontSize: 57sp
    fontWeight: 400
    lineHeight: 64sp
    letterSpacing: -0.25sp
  display-medium:
    fontFamily: system sans-serif
    fontSize: 45sp
    fontWeight: 400
    lineHeight: 52sp
  display-small:
    fontFamily: system sans-serif
    fontSize: 36sp
    fontWeight: 400
    lineHeight: 44sp
  headline-large:
    fontFamily: system sans-serif
    fontSize: 32sp
    fontWeight: 400
    lineHeight: 40sp
  headline-medium:
    fontFamily: system sans-serif
    fontSize: 28sp
    fontWeight: 400
    lineHeight: 36sp
  headline-small:
    fontFamily: system sans-serif
    fontSize: 24sp
    fontWeight: 400
    lineHeight: 32sp
  title-large:
    fontFamily: system sans-serif
    fontSize: 22sp
    fontWeight: 700
    lineHeight: 28sp
  title-medium:
    fontFamily: system sans-serif
    fontSize: 16sp
    fontWeight: 700
    lineHeight: 24sp
    letterSpacing: 0.15sp
  title-small:
    fontFamily: system sans-serif
    fontSize: 14sp
    fontWeight: 500
    lineHeight: 20sp
    letterSpacing: 0.1sp
  body-large:
    fontFamily: system sans-serif
    fontSize: 16sp
    fontWeight: 400
    lineHeight: 24sp
    letterSpacing: 0.5sp
  body-medium:
    fontFamily: system sans-serif
    fontSize: 14sp
    fontWeight: 400
    lineHeight: 20sp
    letterSpacing: 0.25sp
  body-small:
    fontFamily: system sans-serif
    fontSize: 12sp
    fontWeight: 400
    lineHeight: 16sp
    letterSpacing: 0.4sp
  label-large:
    fontFamily: system sans-serif
    fontSize: 14sp
    fontWeight: 500
    lineHeight: 20sp
    letterSpacing: 0.1sp
  label-medium:
    fontFamily: system sans-serif
    fontSize: 12sp
    fontWeight: 500
    lineHeight: 16sp
    letterSpacing: 0.5sp
  label-small:
    fontFamily: system sans-serif
    fontSize: 11sp
    fontWeight: 500
    lineHeight: 16sp
    letterSpacing: 0.5sp
  code:
    fontFamily: Monospace
    fontSize: 13sp
    fontWeight: 400
    lineHeight: 20sp

rounded:
  dialog: 20dp
  card: 12dp
  picker: 12dp
  search: 14dp
  chip: 9999px
  # cartoon style (opt-in, see "Cartoon Style")
  cartoon-dialog: 32dp
  cartoon-card: 26dp
  cartoon-picker: 24dp
  cartoon-search: 30dp
  cartoon-primary-button: 28dp
  cartoon-secondary-button: 22dp

cartoon:
  default-enabled: false
  ink-stroke-width: 2.5dp
  ink-color-light: "#241F33 @ 85%"
  ink-color-dark: "onSurface @ 45%"
  shadow-offset: 3dp
  shadow-blur: 0dp
  shadow-color-light: "#3A2E5C @ 30%"
  shadow-color-dark: "#000000 @ 65%"
  backdrop-dot-spacing: 18dp
  backdrop-dot-radius: 1.3dp
  backdrop-dot-color-light: "#241F33 @ 7%"
  backdrop-dot-color-dark: "#FFFFFF @ 5%"
  button-press-scale: 0.9
  button-spring: "damping 0.5 / stiffness MediumLow"
  dialog-pop-from: 0.8
  dialog-spring: "damping 0.55 / stiffness MediumLow"

spacing:
  xxs: 2dp
  xs: 4dp
  sm: 8dp
  md: 12dp
  lg: 16dp
  xl: 24dp
  xxl: 32dp

components:
  primary-button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.light-surface}"
    typography: "{typography.label-large}"
    rounded: full
    height: 40dp
  primary-button-destructive:
    backgroundColor: "{colors.status-error}"
    textColor: "{colors.light-surface}"
    typography: "{typography.label-large}"
    rounded: full
  primary-button-amoled:
    backgroundColor: "#000000"
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: full
  secondary-button:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: full
  secondary-button-outlined:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: full
  dialog:
    backgroundColor: "{colors.light-surface}"
    textColor: "{colors.light-on-surface}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.dialog}"
  dialog-amoled:
    backgroundColor: "#000000"
    textColor: "{colors.dark-on-surface}"
    rounded: "{rounded.dialog}"
  card:
    backgroundColor: "{colors.dark-surface-container}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.card}"
  list-item:
    backgroundColor: transparent
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.body-large}"
    rounded: none
  section-header:
    textColor: "{colors.primary}"
    typography: "{typography.label-medium}"
  loading-edge:
    backgroundColor: "{colors.primary}"
    typography: "{typography.body-small}"
  switch:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.label-medium}"
    rounded: full
---

## Overview

StarBurst is a "terminal-as-a-service" mobile client: users manage multiple OpenCode
servers from their device, browse sessions, run commands in a real terminal emulator, and view
AI-generated content. The whole UI is built on **Material 3 (Material You)** with Jetpack Compose.
Every design token comes from `app/src/main/kotlin/org/hiylo/starburst/ui/theme/`
(`Color.kt` / `Theme.kt` / `Type.kt`) and `ui/components/` (`AppSurfaces.kt`, etc.).

Brand identity rests on three pillars: **indigo primary** (`#6366F1`), **pure-black AMOLED
surfaces** (`#000000`), and the **M3 semantic container hierarchy**. This is not a marketing web
style; it is a restrained, dark-first tool system — the default Dark surface is `#121218` and
cards express elevation with the `surfaceContainer` ladder (`#1E1E25` → `#262630` → `#31313B`)
instead of shadows. In AMOLED mode every surface collapses to pure black and cards are separated
by a 1dp outline to maximize OLED power saving.

The system supports three visual tiers: system-following Light/Dark, Android 12+ dynamic color
(Material You), and a user-opt-in AMOLED pure-black mode (`AmoledDarkColorScheme`). AMOLED does
not change component semantics — it only swaps surfaces to black and filled buttons to outlined
buttons.

**Key characteristics:**
- Standard Material 3 `ColorScheme`; brand colors enter only as accents such as `StarBurstPrimary`
  (`#6366F1`); the main UI consumes semantic tokens (`primary` / `surfaceContainer` / `outline`…)
- Dark-first: default surface `#121218`, body text `#E5E1E9`, containers use the `surfaceContainer*`
  ladder instead of shadows
- AMOLED pure-black mode: `#000000` surfaces + 1dp outlined cards + outlined buttons
- Centralized radius system: dialogs 20dp, cards/list items 12dp, search fields 14dp
- Terminal emulation and code rendering use a monospace font (`FontFamily.Monospace`); everything
  else uses the system sans-serif
- Fixed semantic status colors: connected green `#4CAF50`, error red `#EF4444`, warning amber `#F59E0B`

## Colors

> **Source files**: `Color.kt` (brand/status colors), `Theme.kt` (the three M3 palettes). All tokens
> are injected by `StarBurstTheme`; components always read `MaterialTheme.colorScheme` and never
> hard-code hex.

### Brand colors (Accent)
- **Primary Indigo** (`{colors.primary}` — `#6366F1`): core brand color. Light-mode primary button
  fill, AMOLED button outline/text, selection highlight, loading bar, section titles.
- **Secondary Purple** (`{colors.secondary}` — `#8B5CF6`): secondary brand emphasis, used for
  special highlights and gradients (e.g. the loading-bar transition color).
- **Tertiary Cyan** (`{colors.tertiary}` — `#06B6D4`): third accent, for accents that need to be
  told apart from indigo.

### Semantic colors (Status)
- **Connected** (`{colors.status-connected}` — `#4CAF50`): server health, connection success, success toasts.
- **Error** (`{colors.status-error}` — `#EF4444`): errors, destructive actions, disconnected warnings.
- **Warning** (`{colors.status-warning}` — `#F59E0B`): battery-optimization notices, caution warnings.

### Dark palette (default)
- **Surface** (`{colors.dark-surface}` — `#121218`): page background.
- **SurfaceContainer** (`#1E1E25`) → **SurfaceContainerHigh** (`#262630`) → **SurfaceContainerHighest** (`#31313B`): card and overlay layering.
- **Primary** (`{colors.dark-primary}` — `#9DA3FF`): primary in Dark (lightened for contrast).
- **PrimaryContainer** (`#2D2F6E`) / **OnPrimaryContainer** (`#DEE0FF`): selected-state container and its text.
- **OnSurface** (`#E5E1E9`) / **OnSurfaceVariant** (`#C8C5D0`): primary and secondary text.
- **Outline** (`#918F9A`) / **OutlineVariant** (`#47464F`): borders and dividers.

### Light palette
- **Surface** (`{colors.light-surface}` — `#FCF8FF`) / **OnSurface** (`#1C1B22`).
- **Primary** (`{colors.light-primary}` — `#4F52B8`) / **PrimaryContainer** (`#E0E0FF`): a deeper
  indigo that stays readable on white.
- **SurfaceContainer** (`#F3EFF7`) ladder is used for cards.

### AMOLED palette
`AmoledDarkColorScheme = DarkColorScheme.copy(...)` overrides to pure black:
- **Surface / Background / SurfaceContainerLowest**: `#000000`
- **SurfaceContainer**: `#0D0D12` · **Low**: `#080810` · **High**: `#141419` · **Highest**: `#1C1C24` · **SurfaceVariant**: `#1A1A22`
- Body text `OnSurface` stays `#E5E1E9`

## Typography

### Font family
- **Body/headings**: `FontFamily.Default` (system sans-serif). No third-party fonts, so Chinese and
  Latin render consistently.
- **Code/terminal**: `FontFamily.Monospace`, fixed at 13sp/20sp (`CodeTypography`), for chat code
  blocks and terminal output.

### Hierarchy (full M3 scale)

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| display-large | 57sp | 400 | 64sp | -0.25sp | Rare large numbers / first screens |
| display-medium | 45sp | 400 | 52sp | 0 | — |
| display-small | 36sp | 400 | 44sp | 0 | Large empty-state icons |
| headline-large | 32sp | 400 | 40sp | 0 | — |
| headline-medium | 28sp | 400 | 36sp | 0 | Dialog titles |
| headline-small | 24sp | 400 | 32sp | 0 | — |
| title-large | 22sp | 700 | 28sp | 0 | Page titles |
| title-medium | 16sp | 700 | 24sp | 0.15sp | List-item titles, card body text |
| title-small | 14sp | 500 | 20sp | 0.1sp | Secondary titles |
| body-large | 16sp | 400 | 24sp | 0.5sp | List body text, button labels |
| body-medium | 14sp | 400 | 20sp | 0.25sp | Default body, descriptions |
| body-small | 12sp | 400 | 16sp | 0.4sp | Helper text, metadata |
| label-large | 14sp | 500 | 20sp | 0.1sp | Buttons, labels |
| label-medium | 12sp | 500 | 16sp | 0.5sp | Section titles, chips |
| label-small | 11sp | 500 | 16sp | 0.5sp | Status text, badges |

### Principles
Hierarchy is built mainly from **weight + size**: list-item titles use `titleMedium` (700), body
uses `bodyLarge/bodyMedium` (400), and status/helper text uses `labelSmall/bodySmall`. Headings lean
toward 700 (`titleLarge/titleMedium`), body toward 400, secondary/helper toward 500. All text is
referenced through `MaterialTheme.typography.*` — never scattered raw `TextStyle`s.

## Layout

### Spacing system
- **Base unit: 4dp**, common steps 2/4/8/12/16/24/32dp.
- List-item horizontal padding `12–16dp`; card padding `14–16dp`; dialog content `24dp`.
- Screen horizontal gutters are a uniform `16dp` (some lists use 12dp); component spacing `8dp`
  (`Arrangement.spacedBy(8.dp)`).
- Empty states start with `48dp` vertical padding.

### Page structure
- **Single-column phone layout**, no desktop adaptation. Every page is a `Scaffold` +
  `TopAppBar` (back arrow + title + actions on the right).
- **Session list**: `LazyColumn` + session cards with title (`titleMedium`), preview
  (`bodySmall` monospace), time, and a status badge.
- **Chat page**: terminal area (monospace, `terminal-area`) + bottom composer (rounded input
  `24dp` + circular send button).
- **Settings page**: sections grouped semantically (General/Notifications/Appearance/Chat
  display/Chat behavior/Advanced); each section uses a `SectionHeader` (`labelMedium` indigo)
  plus `ListItem`s (title + supporting text + trailing control).

### Grid & containers
- No fixed grid system; cards are full-width with 12dp radius.
- Provider icon grid `MCP grid` 3 columns; color pickers use horizontal scrolling rows.

### Whitespace philosophy
Tool-density: information first, cards `8dp` apart, sections `16dp` apart. In AMOLED mode the
pure-black background amplifies the energy-saving value of "black space"; hierarchy is carried by
outlines rather than whitespace.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 — Flat | No shadow, background color only | Page backgrounds, list items, sections |
| 1 — Container ladder | `surfaceContainer → High → Highest` progressively lighter | Cards, dropdowns, overlay bottom containers |
| 2 — Dialog | `surface` + 6dp tonalElevation (AMOLED: 0dp + 1dp outline) | AppDialog |
| 3 — AMOLED outline | Pure-black surface + `1dp outlineVariant(alpha 0.55)` | All cards/overlays in AMOLED mode |
| C — Cartoon | Hard 3dp down-right offset shadow, zero blur, no M3 elevation; 2.5dp ink outline drawn on top | All dialogs, buttons, cards, chat bubbles when Cartoon Style is on |

The system **replaces shadows with the container-color ladder**: a "higher" layer is expressed by
lightening via `surfaceContainer*`, not by drop shadows. The only common animation is
`AppLoadingEdge` — a 3dp top highlight bar pulsing horizontally with a primary gradient
(`FastOutSlowInEasing`, 700ms round-trip) to signal loading.

## Shapes

### Border radius scale

| Token | Value | Use |
|---|---|---|
| `{rounded.dialog}` | 20dp | Dialog containers (AppDialog) |
| `{rounded.card}` | 12dp | Cards, session cards, list-item selected state (AppCardShape / AppPickerItemShape) |
| `{rounded.search}` | 14dp | Search fields (AppSearchShape) |
| full | 9999px | Buttons, inputs, send button, chips, switches |
| `{rounded.cartoon-dialog}` | 32dp | AppDialog with Cartoon Style on |
| `{rounded.cartoon-card}` | 26dp | Cards with Cartoon Style on |
| `{rounded.cartoon-picker}` | 24dp | Picker items with Cartoon Style on |
| `{rounded.cartoon-search}` | 30dp | Search fields with Cartoon Style on |

Radius vocabulary: **interactive elements as round as possible** (buttons/inputs/send key are fully
round), **containers 12dp**, **dialogs 20dp**. Icons are not clipped into rounded shapes (Material
icons keep their original form).

## Components

> **State note**: Material 3 components natively carry hover/focus/pressed/disabled states; this
> document only flags key overrides.

### Buttons (AppSurfaces.kt)
- **`primary-button` (AppPrimaryButton)**: solid fill `{colors.primary}`, text `onPrimary`. Light
  mode uses the default M3 `Button`; **AMOLED mode automatically switches to an outlined button** —
  black background + 1dp `{colors.primary}` outline + primary text.
- **`primary-button-destructive`**: fill `{colors.status-error}`, text `onError` (Light); AMOLED
  renders as an error-colored outlined button.
- **`secondary-button` (AppSecondaryButton)**: `TextButton`, text `{colors.primary}`, no background.
  With `outlined = true` it becomes a 1dp primary outline with a transparent/black background.
- **`dialog-actions` (AppDialogActions)**: right-aligned `[secondary, primary]` button pair with
  `spacedBy(8.dp)`; pass `destructiveConfirm` for destructive confirms.

### Dialogs
- **`dialog` (AppDialog)**: `BasicAlertDialog` + Surface, 20dp radius, `AlertDialogDefaults.containerColor`,
  6dp tonalElevation. In AMOLED the container is `#000000` with 0dp shadow and a 1dp outline.
- Titles use `titleMedium/headlineSmall`; body uses `bodyMedium` onSurfaceVariant.

### Cards & list items
- **`card`**: 12dp radius, `surfaceContainer` ladder background. Used for server cards, session
  cards, and file list items.
- **`list-item`**: transparent background, `bodyLarge` title + `bodySmall` supporting text +
  trailing `Switch`/`TextButton`/`›`. Heavily used in Settings and Diagnostics.
- **Selected state** (`AppPickerItemShape` 12dp): `primaryContainer.copy(alpha 0.5)` background +
  primary text + ✓ (AMOLED: black background + 1dp primary outline).

### Other
- **`switch`**: checked track `{colors.primary}`; AMOLED uses a black track + primary outline.
- **`loading-edge` (AppLoadingEdge)**: 3dp top primary-gradient pulsing bar.
- **Status badges/chips**: connected `{colors.status-connected}`, error `{colors.status-error}`,
  warning `{colors.status-warning}`.
- **Section titles**: `labelMedium` 500, `{colors.primary}`, `padding(start 16dp, top 16dp, bottom 4dp)`.

## Cartoon Style

> **Source files**: `ui/theme/CartoonStyle.kt` (state, shapes, ink/shadow tokens),
> `ui/theme/Theme.kt` (`cartoonStyle` parameter), `ui/components/AppSurfaces.kt`
> (adaptive shapes + dialog/button chrome). Toggle: Settings → Appearance → **Cartoon style**.

Cartoon feel does **not** come from swapping colors — the existing `theme_scheme` palettes
(candy / ocean / sunset / flame / bubble) only change `ColorScheme`, which is why they read as "recolors".
Cartoon Style is a separate visual layer with five levers:

| Lever | Normal | Cartoon |
|---|---|---|
| Radius | dialog 20 / card 12 / picker 12 / search 14 / bubble 12 | dialog 32 / card 26 / picker 24 / search 30 / bubble 26-6-26-22 (user), 24 (assistant) |
| Ink outline | none (AMOLED: 1dp `outlineVariant`) | 2.5dp ink — `#241F33`@85% on light surfaces, `onSurface`@45% on dark |
| Shadow | none (container-color ladder instead) | hard 3dp down-right block, **zero blur**, `#3A2E5C`@30% light / black@65% dark; no M3 elevation |
| Backdrop | flat container color | paper dot-grid: 18dp spacing, 1.3dp dots, `#241F33`@7% light / white@5% dark |
| Motion | M3 defaults | buttons scale to 0.9 on press with a low-damping spring; dialogs pop in from 0.8 |

### How it is wired

- `LocalCartoonStyle` is a `staticCompositionLocalOf { false }`; `isCartoonStyle()` reads it.
  `StarBurstTheme` supplies both the local and the global switch in the same frame.
- `AppCardShape` / `AppDialogShape` / `AppPickerItemShape` / `AppSearchShape` are
  `AdaptiveRoundedShape(normal, cartoon)` instances. `createOutline` resolves the radius at
  **draw time** from `CartoonStyleState.enabled`, so the ~60 existing `shape = AppCardShape`
  call sites need no changes and upgrade app-wide.
- **One modifier does the whole job**: `Modifier.cartoonChrome(shape = AppCardShape)` =
  `cartoonOffsetShadow(shape).cartoonInkOutline(shape)`. It wraps any `Card` / `Surface` / `Box`
  without touching `border` or `elevation` parameters, so existing call sites keep their AMOLED
  borders and only add the chrome. When the style is off it returns the modifier unchanged —
  no draw nodes at all.
- Draw order matters and is fixed in `cartoonChrome`: the shadow's `drawWithContent` runs
  `drawShape` **before** `drawContent()` (so it sits behind the container's own background), while
  the ink outline's `drawWithContent` runs `drawContent()` **first** (so the 2.5dp line is not
  covered by the container background and stays a full-width stroke).
- `cartoonOffsetShadow` does **not** use `Modifier.shadow` — that API has no offset parameter and
  only blurs. The shadow is a canvas translate of the same shape path, which is what makes the edge
  hard and comic-book-like. Shape → path goes through `DrawScope.drawShapePath`: uniform radii
  come back as `Outline.Rectangle` (which does not carry the radius), so the radius is re-read from
  the `AdaptiveRoundedShape`; asymmetric bubbles come back as `Outline.Generic` and reuse the path.
- Press bounce lives in `cartoonButtonStyle()`: a `MutableInteractionSource` +
  `collectIsPressedAsState()` drives `animateFloatAsState(0.9f)` with
  `spring(dampingRatio = 0.5f, stiffness = MediumLow)`, then `.scale()` + `.cartoonChrome()`.
  `AppPrimaryButton` / `AppSecondaryButton` pass their `interactionSource` into the M3 button so
  ripple and scale read the same press state.
- Dialog pop is an `Animatable(0.8f → 1f)` on `AppDialog` with
  `spring(dampingRatio = 0.55f, stiffness = MediumLow)`. Note the `Animatable` import is
  `androidx.compose.animation.core.Animatable` — the `androidx.compose.ui.graphics` one is
  `Animatable<Color>` and silently type-mismatches.
- Backdrop is `Modifier.cartoonBackdrop()` on the root `Box` in `MainActivity`, so every screen
  gets the paper texture from one place. Alpha is deliberately near-invisible: it must not compete
  with information hierarchy.
- Persistence: `cartoon_style` in DataStore, mirrored to `SyncSettings.cartoonStyle` so it
  syncs across devices like every other appearance setting.

### Coverage

Full chrome (radius + ink + offset shadow): all dialogs, all app buttons
(`AppPrimaryButton` / `AppSecondaryButton`), Home screen cards, all Settings cards (shared
`SettingsCard`), chat message bubbles.

Radius-only, no chrome: the ~55 remaining `Surface(shape = AppCardShape, ...)` call sites outside
`HomeScreen` / `SettingsDisplayNames`. They read as "rounder" but not "sticker" — append
`.cartoonChrome(AppCardShape)` to their `modifier` to bring them up.

### Known gaps

- **Font is not wired.** `res/font/` does not exist and the whole app uses `FontFamily.Default`
  (`Type.kt`). A rounded/hand-drawn font family is the single biggest remaining lever for cartoon
  feel; it needs a licensed `.ttf` (e.g. Baloo 2 / Fredoka for Latin, Smiley Sans / 站酷快乐体 for
  Chinese) dropped into `app/src/main/res/font/` and a cartoon-aware `Typography` built from it.
  No dead hook is left in code for this — add it when the asset arrives.
- **`cornerRadiusPx` only understands `AdaptiveRoundedShape`.** `RoundedCornerShape` stores its
  radii as `CornerSize` and exposes no `Dp`, so a plain `RoundedCornerShape` passed to
  `cartoonChrome` gets a 0-radius ink outline. Always pass an `AdaptiveRoundedShape` (uniform) or
  an asymmetric `RoundedCornerShape` (which takes the `Outline.Generic` path branch).
- Top bars are not chromed: there is no shared `TopAppBar` component, so it would mean ~30 edits
  across screens.
- No illustrations, mascot, or emoji-as-icon system yet.

## Do's and Don'ts

### Do
- Read all colors from `MaterialTheme.colorScheme` and all text from `MaterialTheme.typography`;
  **never hard-code hex or sizes in components**. Brand colors enter the system only through
  `StarBurstPrimary/Secondary/Tertiary` in `Color.kt`.
- Express hierarchy with the `surfaceContainer*` ladder; no shadows by default.
- Use the `AppPrimaryButton / AppSecondaryButton` wrappers — AMOLED semantics (black + outline)
  are handled by the wrapper, business code never knows.
- Keep semantic status colors fixed: connected green `#4CAF50`, error red `#EF4444`, warning amber `#F59E0B`.
- Render code and terminal content in `FontFamily.Monospace` 13sp; Chinese and UI text use the
  system sans-serif.
- Follow the radius system: buttons/inputs fully round, containers 12dp, dialogs 20dp, search 14dp.
  Under Cartoon Style the same tokens resolve to their `cartoon-*` values automatically.
- Read cartoon state only through `isCartoonStyle()` / `LocalCartoonStyle`; never branch on
  `themeScheme` to guess whether the app should look cartoon.

### Don't
- Do not add a fourth theme palette; any color outside Light/Dark/AMOLED must map back to an
  existing token.
- Do not fabricate ad-hoc palettes with `darkColorScheme/lightColorScheme` in business pages —
  always go through `StarBurstTheme`.
- Do not treat AMOLED as "just another dark mode" — it only swaps surfaces to pure black + outlines,
  without changing component semantics or hierarchy.
- Do not encode cartoon colors into `theme_scheme`. Cartoon Style changes radii, outlines and
  shadows; it must stay orthogonal so it composes with any palette and with AMOLED.
- Do not introduce marketing-style visuals (gradient backgrounds, decorative illustrations) in the
  default style. Cartoon Style is the single sanctioned opt-in for shadows and outlines, and it
  stays off by default — the system remains a restrained, information-first tool.
- Do not render code/terminal content with a non-monospace font, cartoon style or not.

## Responsive Behavior

### Breakpoints & adaptation
Android is natively responsive; there are no web breakpoints. The adaptation axes are **screen
width (phone vs tablet) and density (dp)**:
- Portrait phone (default): single column, full-width cards, list items stretch horizontally.
- Landscape/tablet: `LazyColumn` content stays single-column; the chat composer stays docked to the
  bottom; where necessary an outer `Box` constrains the max content width and centers it.
- System font scale: Compose `Text` uses `sp` throughout and follows accessibility scaling automatically.

### Touch targets
- Buttons ≥ 40dp tall; icon buttons `36dp` with `12dp` radius; composer send button `36dp` fully round.
- List items are fully tappable (`Modifier.clickable` fills the row), row height ≥ 48dp.
- M3 switches, sliders etc. carry their own 48dp minimum touch area.

### Collapse strategy
- Top bar: on narrow screens keep back + title + essential actions only; overflow goes into the "⋮" menu.
- Long lists (Settings/Diagnostics): `verticalScroll` the whole page; group inside sections with `ListItem`.
- Terminal: horizontal overflow scrolls (`horizontalScroll`); vertical keeps scroll-back buffering.

## Iteration Guide

1. Iterate component by component: change tokens in `ui/components` or `ui/theme`, and business
   pages take effect immediately — verify color/type parsing first, then the AMOLED and Light tiers,
   then toggle Cartoon Style on to confirm radii/outlines/shadows follow.
2. Prefer existing vocabulary for new components (`AppDialog` / `AppCard` / `AppPrimaryButton` /
   `AppPickerItemShape` 12dp + container ladder); only introduce new tokens when needed.
3. When adding a color, fill all three tiers at once: `DarkColorScheme`, `LightColorScheme`,
   `AmoledDarkColorScheme` — never fewer.
4. Always use existing `MaterialTheme.typography` tiers for text; extend `Type.kt`'s `Typography`
   only when no tier fits.
5. After every palette change, check AMOLED contrast: body text must stay `#E5E1E9` on pure black,
   outlines `outlineVariant(0.55)`.
6. Destructive actions uniformly use the error color (`{colors.status-error}`), never a new red.
7. New surfaces must opt into cartoon chrome explicitly: `shape` comes from an `AdaptiveRoundedShape`
   token (radius upgrades for free), and outline + shadow come from one `.cartoonChrome(shape)`
   appended to the `modifier`. Do not add `border =` or `elevation =` for the cartoon layer —
   the chrome draws both, and `border`/`elevation` keep their existing AMOLED meaning.
8. Keep cartoon logic out of color palettes: `CartoonStyle.kt` owns shape/outline/shadow, `Theme.kt`
   owns color. A cartoon accent color belongs to `StarBurstAccents`, not to the cartoon layer.

## Known Gaps

- **`sp` font units**: this is an Android Compose app, so sizes/line-heights use `sp` (plus some
  `dp`). `npx @google/design.md lint` is a CSS/px-oriented tool and flags `sp`/`dp` as "not a valid
  dimension". That is a tool-vs-platform unit mismatch, not a design flaw — keep `sp`/`dp` as-is
  when generating Compose code.
- **Hover/focus states** are not separately documented — handled natively by M3 components, no
  custom overrides.
- **Dynamic color (Material You)**: once enabled on Android 12+, the palette is generated by the
  system and this document's indigo palette no longer applies (only the AMOLED black surface is
  layered on top); consistency rules under dynamic color are not separately documented.
- **Accessibility**: beyond `sp` sizes and touch heights, contrast and TalkBack semantics are not
  covered here. White text on `#6366F1` is ~4.26:1 contrast, close to but slightly below WCAG AA
  4.5:1 — prefer `primaryContainer`/`onPrimaryContainer` for body-text scenarios.
- **Landscape/tablet layouts** are not exhaustively covered in previews and docs; the current
  baseline is portrait phone.
- **WebView-embedded pages** (e.g. GitHub login) fall outside this design system's scope; styling
  follows the host page.
