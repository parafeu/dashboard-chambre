# Foyer — phone version, home-screen widgets, new name and icon — design

Validated mockups (open in a browser): `docs/superpowers/mockups/2026-10-21-foyer/`
- `app-mobile.html` — the phone app (choose « Barre du bas : F2 · Barre flottante »; the other footers were rejected).
- `widgets.html` — the three widgets on a Pixel 9 home screen (final state; ignore the « Couleurs » toggle, Ambre is kept).
- `icone.html` — icon proposals; **I2 « Maison avec une flamme »** was chosen. This one is a fragment written for the
  brainstorm frame, so its page styles are missing when opened directly; the SVGs still render.

## Intent

Alexandre wants the house dashboard on his phone (Pixel 9): the same global dashboard as the living-room tablet, in
portrait, plus one home-screen widget per room to drive lights and shutters without opening the app. The app is
renamed **Foyer** and gets a real icon.

The phone version is **not a new room** and must **not reinvent anything**: every panel is the tablet's own panel
(same Compose components, sliders, buttons, colours, icons), only stacked vertically for a portrait screen. Every
time the mockups drifted from the tablet the user rejected them; when in doubt, do what the tablet does.

## Goals

1. Phone layout of the app, chosen automatically on a phone-sized screen.
2. Three Android home-screen widgets: Chambre, Salon, Cuisine.
3. Rename to « Foyer » and ship the adaptive launcher icon (with a monochrome layer for themed icons).
4. Consistency fix that also changes the tablets: **each room tab shows that room's own dashboard**. Today the
   living-room tablet's « Cuisine » tab shows an older screen (shutter, HA timers, shopping list, plug) instead of the
   kitchen dashboard (shutter + Mealie recipes). It must show the kitchen dashboard, on the tablet and on the phone.

## Non-goals / anti-goals

- No new room value in settings, no « Mobile » mode.
- No new visual language in the app: no horizontal sliders, no arrows beside sliders, no extra buttons. Anti-goal:
  any panel that looks different from the tablet's.
- Not on the phone: work agenda, camera wake/sleep, voice announcements, auto-return to Salon, battery reporting to
  Home Assistant (no Mobile App registration from the phone), start on boot, kiosk behaviour (immersive mode,
  screen kept on). The recipe screen may still keep the screen awake while open, as on the tablet.

## The phone app

**When it applies:** on a phone-sized screen (Android's usual threshold: smallest width < 600 dp). The room chosen in
settings is ignored in phone layout; the phone always shows the global dashboard. Tablets keep today's behaviour.

**Header:** date, online indicator, settings button, and the weather block (current + hourly, tap toggles hours /
week, as on the tablet). No clock (the status bar has it) and no battery.

**Tabs** — the same three rooms as the living-room tablet's rail, in this order, each showing its room's dashboard:
- **Salon**: `LightPanel` (title « Lumières », no colours) and `CoverPanel` for the living room. Temperature and
  humidity (`sensor.salon_temperature`, `sensor.salon_humidity`) shown on the room heading line. No agenda.
- **Cuisine**: the kitchen dashboard (`CuisineApp` content): `CoverPanel` for the kitchen shutter + the Mealie
  recipes panel (search, filters, « Au menu ce soir », grid — 2 columns on the phone), and the recipe screen.
- **Chambre**: the bedroom dashboard (`ChambrePanels` content): `LightPanel` (with colours), `CoverPanel`,
  `ScenesPanel` (« Ambiance »).

Panels are stacked one under the other in a scrolling column, each at a fixed comfortable height (the mockup uses
≈ 300 dp for the light sliders and ≈ 336 dp for the shutter slider). The shutter panel is the tablet's everywhere:
vertical slider filled from the top + the column of four buttons (open, stop, close, half) + the status line.

**Navigation:** a **floating pill bar** at the bottom (Material 3 Expressive floating toolbar style): three buttons
with the room icons used by the tablet rail (`chair`, `kitchen`, `bed`); only the selected room shows its name, on an
amber background. Content scrolls under it with enough bottom padding. Opening a recipe hides the bar (full-screen
recipe with its back button, as on the tablet).

**Settings on the phone:** the same settings screen, showing only what the phone uses: Home Assistant URL + token,
Mealie URL + API key. Room, camera, voice, calendar, boot and battery sections are hidden.

## Living-room tablet change (goal 4)

The salon tablet's « Cuisine » tab shows the kitchen dashboard instead of `salon/CuisineScreen.kt`.

**Builder decision (not confirmed by the user, he did not object):** what the old screen had that the kitchen dashboard
lacks — the three HA kitchen timers with 5 / 10 min buttons (their end is still announced aloud by the salon
tablet), the shopping-list count and the kitchen-tablet plug — is **added to the kitchen dashboard** so nothing is
lost, and therefore appears on the kitchen tablet, the salon tablet and the phone alike. Placement inside the kitchen
dashboard is left to the builder, using the existing tablet components (`Section`, the timer rows, `InfoTile`).
The rail summary for Cuisine stays « Volet … · minuteurs … ».

## Widgets

Three widgets, style « Google Home »: large rounded tiles, one per device, a tile turns amber when the light is on or
the shutter open. Colours: the app's **Ambre** palette (not Material You). Each widget has a title row with the room
name and, top right, a round **open-app** button (`open_in_new`); tapping the title or that button opens the app on
that room's tab.

| Widget | Size (Pixel 9 4×5 grid) | Content |
|---|---|---|
| **Chambre** (H1) | 4×2 | Tile « Lumière » (state « 40 % » / « Éteinte ») with round − and + buttons; tile « Volet » (« Ouvert », « Fermé », « Ouvert à N % ») with round ▲ and ▼. |
| **Salon** (SA) | 4×2 | Same as Chambre for the living room; title row also shows « 21,4 °C · 52 % ». |
| **Cuisine** (KA) | 4×1 | Title row « Cuisine » + open-app button; one tile « Volet » with round ▲ ■ ▼. |

The three together exactly fill one 4×5 page.

Behaviour:
- Light tile tap = toggle; − / + = brightness −10 / +10 % (turning the light on if it was off), clamped 10–100.
- Shutter tile tap = open if closed, close if open, stop if moving; ▲ / ■ / ▼ = open / stop / close.
- No scenes, colours or colour temperature in widgets (the user only wants light and shutters).
- Widgets act even when the app is closed (they call Home Assistant directly with the saved URL + token) and show
  the last known state; refresh after each action and periodically (frequency left to the builder; Android limits
  periodic work to ≥ 15 min). If HA is unreachable, show the last state dimmed rather than an error.
- Tile sizes in the mockup are an estimate; check on the real Pixel 9 that the 4×1 kitchen widget fits.

## Name and icon

- Displayed name: **Foyer** (launcher label, settings title, anywhere the app names itself).
- Keep the application id `fr.alex.chambre` so the update installs over the current app on the tablets without
  losing settings. Home Assistant device names (« Tablette chambre », « Tablette salon », « Tablette cuisine ») and
  entity ids do not change.
- Icon **I2**: on the app's brown background (`#261E17`), a solid amber house (`#FFB86B`) with a flame cut out in the
  centre (`#4A2800`), as drawn in `icone.html`. Adaptive icon (foreground / background layers on the 108 dp canvas,
  glyph inside the 66 dp safe zone) plus a **monochrome** layer (house with the flame cut out) for Android 13+
  themed icons. The SVG paths in the mockup are a starting point; redraw cleanly as a vector drawable.

## Constraints

- `minSdk` is already 28 on `master` (the bedroom tablet now runs LineageOS 16 = Android 9). Everything here must
  work on API 28 for the tablets; the phone and widgets features need only run on the Pixel 9, but the code must still
  build and install on API 28.
- **One APK for tablets and phone (builder decision):** the release build only packages `armeabi-v7a`, which a 64-bit
  only Pixel 9 cannot install. Add `arm64-v8a` to the release ABI filters (bigger APK, no build flavours). On the
  phone, camera / OpenCV code is never started.
- The phone's settings must not be able to switch on tablet-only features (announcements, battery reporting, camera,
  boot start), even if a room value is stored.
- Icons: Material icons only (`Icons.Filled.*`), as in the rest of the app.
- **Network: home only (user decision).** One HA URL and one Mealie URL, no internal / external switching; away from
  home the user connects through his own WireGuard VPN. `homeassistant.local` is an mDNS name that usually does not
  resolve through a VPN, so on the phone the user may enter an IP address (or another name) instead: the URL field
  must accept it as it does today. Nothing else to build for remote access.
- Testing against the real Home Assistant drives real devices: never tap blindly on the emulator connected to the real
  HA; announce tests that move a real light or shutter.

## Left to the builder

Exact phone breakpoint and panel heights; how widgets are implemented (Glance or RemoteViews) and refreshed; where the
timers / shopping list / plug go inside the kitchen dashboard; the default tab when the app opens without a widget
(Salon unless there is a better reason); version number bump and README update.
