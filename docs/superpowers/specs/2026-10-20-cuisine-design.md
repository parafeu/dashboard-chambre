# Mode Cuisine — design

Validated mockup: https://claude.ai/artifact/HXsYvgEQwRKmWrBNfqQCKn (screens « Cuisine — volet et recettes »,
« Cuisine — fiche recette »).

## Intent

The kitchen tablet mostly showed Mealie in a browser. It becomes a third room mode of the app (Chambre / Salon /
**Cuisine**): the kitchen shutter plus the Mealie recipes, readable from a distance while cooking.

## What it does

- **Header** identical to the other rooms (clock, date, battery + settings, weather).
- **Shutter** widget identical to the bedroom's, on `cover.volet_cuisine` (no half-position script: 50 %).
- **Recipes** (Mealie API, read only): search box, filters « Toutes », « Moins de 45 min », and one chip per
  Mealie category present; a grid of cards (photo, name, total time, servings). A « Au menu ce soir » banner when
  Mealie's meal plan has an entry today (`GET /api/households/mealplans/today`, Mealie v3), opening that recipe.
- **Recipe screen** (tap a card): back, photo, title, prep/cook/total times, servings stepper (− / +, 1–24).
  - Ingredients grouped by their Mealie section title, tick boxes. Structured ingredients (quantity + food) are
    scaled by servings / base servings and shown as readable fractions (¼ ⅓ ½ ⅔ ¾); g/ml ≥ 10 rounded to 5.
    Free-text ingredients (Mealie « not parsed ») are shown as written, with a hint to run « Analyser les
    ingrédients » in Mealie.
  - Steps with their titles; the current step is expanded in large type, the others show their title only.
    Previous / next buttons.
  - **Timer chips inside the sentence**: every duration in the current step's text (« 10 minutes »,
    « 30 à 45 secondes », « 1 h 30 ») becomes a tappable chip in place; a tap creates an Android system timer in
    the Clock app (`AlarmClock.ACTION_SET_TIMER`, skip UI), labelled with the step title. A range uses its upper bound.
  - While a recipe is open the screen never sleeps.
- Base servings: `recipeServings`, else `recipeYieldQuantity`, else the first number in `recipeYield`, else 1.

## Settings

Room « Cuisine »; « Adresse Mealie » (normalised like the HA URL) and « Clé API Mealie » (whitespace removed, like
the HA token). The key never leaves the tablet's settings and is never committed.

## Home Assistant

Cuisine subscribes to the weather and `cover.volet_cuisine` only; Mobile App device « Tablette cuisine »; no
calendar announcements (salon only).

## Builder's calls

- Images load without the API key (Mealie media is public) with Coil, cached.
- Mealie errors (unreachable, 401) show in the recipes panel and in the settings card.
- Recipes list is fetched on start and every 30 min; a recipe detail is fetched when opened.
- Sample recipes in tests are synthetic (the repository is public).
