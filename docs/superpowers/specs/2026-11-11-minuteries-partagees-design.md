# Minuteries partagées — design

Validated mockup: https://claude.ai/artifact/HaEvnWL72DWxx9dGBjQGxQ (screens « A · Panneau Minuteries »,
« Nouvelle minuterie », « Sonnerie sur la tablette du salon »; screen B was rejected).

## Intent

Recipe timer chips create a timer in Android's Clock app. When it rings on the Lenovo, it has to be stopped from
the system notification shade, which is awkward with wet hands and in kiosk mode. The cook also wants several
timers at once, timers with a free duration (no recipe open), and **every wall tablet ringing together**, so a
timer started in the kitchen is heard from the living room or the bedroom.

The three Home Assistant timers (`timer.minuteur_1..3` and their `script.demarrer_minuteurN_*` scripts) do not
work and are removed.

## Goals

- Timers live in the app, shared by all wall tablets through Home Assistant; any number may run at once.
- Started from a recipe's timer chip or from « + Nouvelle minuterie » (free duration, optional name).
- When a timer ends, every tablet with ringing enabled plays a loud alarm, in a loop, and shows the ringing
  screen, waking from its sleep overlay. « Arrêter » on any tablet stops it on all of them.
- A per-tablet setting turns ringing on or off (e.g. off in the bedroom at night).
- A tablet that restarts or reconnects finds the running and ringing timers again.

## Non-goals

- The phone (Foyer, phone layout and widgets): no timers there for now; it ignores them.
- Pause / resume. Editing a running timer beyond « +1 min ».
- Voice announcement of the end (the ring replaces the old « fin des minuteurs » announcement).

## Anti-goals (a failure even if it works)

- A ring that can only be silenced through Android's UI, or that keeps ringing on one tablet after it was stopped
  on another.
- A timer that silently disappears (HA restart, tablet offline) while it was running.
- Any new dependency on the Android Clock app.

## What it looks like (mockup A)

- **Cuisine screen** (Lenovo, and the Salon tablet's « Cuisine » tab, which reuses `CuisinePanels`): a new
  « Minuteries » panel between Volet and Recettes (~330 dp wide; Volet narrows, recipes go to two columns).
  - One card per timer, soonest first: name (large), origin line (recipe · step title, or « Lancée au salon /
    en cuisine » for a free timer), remaining time in large primary digits (m:ss, h:mm:ss
    above an hour), a thin progress bar, « +1 min » and a × (delete, no confirmation).
  - A ringing timer's card turns primary (orange) with « Terminée » and an « Arrêter » button; ringing cards go first.
  - « + Nouvelle minuterie » at the bottom of the panel. When the list overflows, it scrolls; the button stays.
  - Empty state: the button only, with a short line « Aucune minuterie ».
- **Nouvelle minuterie** (dialog): large − / + stepper in whole minutes (1–180, starts at 5), presets
  1 / 3 / 5 / 10 / 15 / 30 min that set the value, an optional name field (blank → « Minuterie »), Annuler / Lancer.
- **Recipe timer chips**: a tap creates a shared timer named after the step title (else the recipe name), origin
  « <recipe> · étape N ». Snackbar « Minuterie de 10 min lancée », as today.
- **Sonnerie** (every tablet with ringing on, any room, any screen including the settings and an open recipe):
  full-screen overlay over the dimmed dashboard: header « Minuterie terminée · lancée en cuisine », one large
  primary card per ringing timer (name, duration, « sonne depuis m:ss », « Arrêter »), further ringing timers as
  secondary cards with their own « Arrêter ». Footnote « Arrêter ici l'arrête sur toutes les tablettes. »
  The overlay goes away when no timer rings any more.
- **Chambre tablet**: no Minuteries panel and no way to start a timer there (it has no Cuisine screen); it only
  rings (overlay + « Arrêter ») when its setting is on. Free timers are therefore only « Lancée en cuisine » or
  « Lancée au salon ».
- **Settings**: a switch « Sonner pour les minuteries » (default on) on every tablet, Chambre included, in the room
  card (near « Tester la voix » where it exists). Off: no overlay and no sound on that tablet.
  The kitchen summary on the Salon tablet (« 2 minuteurs en cours ») reads the shared timers instead of the HA
  `timer.*` entities.

## Behaviour

- A timer stores an absolute end time; every tablet computes the remaining time from its own clock (NTP), so no
  ticking is written to HA.
- A timer **rings** from its end time until someone presses « Arrêter » (which deletes it everywhere), with no
  automatic stop. A tablet coming online finds an ended, undeleted timer and rings for it too.
- Ringing tablet: wakes the screen (presence AWAKE, held while ringing), plays the alarm sound in a loop on the
  **alarm stream** (`USAGE_ALARM`, the default alarm ringtone `RingtoneManager.TYPE_ALARM`, fallback to a
  bundled sound if none), at its own alarm volume. Stopping (locally or remotely) stops the sound within ~1 s of the
  HA update.
- Ringing disabled: the tablet still shows the timers in its Minuteries panel, but no overlay and no sound.
- « +1 min » on a running timer pushes its end time; on a ringing one it is not offered (Arrêter only).
- HA unreachable: the panel shows the last known timers greyed with « Hors ligne »; creating, « +1 min » and ×
  are refused with a snackbar. A tablet whose timer ends while it is offline still rings locally from its last
  known list (kept on disk). Its « Arrêter » always works: it silences this tablet at once, remembers the stop
  (on disk), and sends `todo.remove_item` when HA is back (« item not found » counts as done, another tablet may
  have stopped it); a remembered stop also keeps the timer from ringing again when the list reloads.
  *(Builder's call: the spec first said « refused », which would leave an unstoppable ring.)*

## Home Assistant storage (builder's call, recorded here)

A Local To-do list **`todo.minuteries_foyer`** (integration « Local To-do », created once in HA's UI), one item
per timer, because it is persisted by HA across restarts and edited item by item (no whole-list overwrite races):

- `summary` = the timer name; `uid` = HA's item uid (the timer id);
- `description` = a small JSON `{"v":1,"end":"<ISO-8601 UTC>","duration":600,"origin":"Lasagnes · étape 4",
  "room":"cuisine"}`;
- create `todo.add_item`, « +1 min » `todo.update_item` (new description), delete / Arrêter `todo.remove_item`.
- Tablets read it live with the WebSocket `todo/item/subscribe` (entity `todo.minuteries_foyer`) on the existing
  HA connection, re-subscribed on reconnect. Malformed items are ignored (and left alone).

## Removal

- App: `SystemTimer.kt`, `Entities.KITCHEN_TIMERS`, `timerStartScript`, the `timer.finished` subscription and its
  announcement path (`DeviceFeatures.timerEvents`, `AnnouncementController` timer branch) and the settings text
  mentioning « fin des minuteurs de cuisine ».
- HA: helpers `timer.minuteur_1..3` and scripts `script.demarrer_minuteur{1,2,3}_{5,10}min`, deleted only after
  checking no automation or dashboard uses them (announced to the user before touching the live HA).

## Constraints

- Tablets: Galaxy Tab S2 LineageOS 16 (Android 9) and 18.1 (Android 11), Lenovo TB-X606F LineageOS 21
  (Android 14). The alarm must play on all three while the app is in the foreground (kiosk).
- Reuse the existing look (Theme.kt Amber palette, panel cards, buttons) — mockup A is the reference.
- Unit tests for the timer model (parsing items, remaining time, ringing, ordering) like the other `*Test.kt`.

## Left to the builder

Class layout, how the ring player is wired to presence and the overlay, the exact WebSocket message plumbing,
and the bundled fallback sound.
