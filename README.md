# Foyer

App Android native (Jetpack Compose, Material 3) qui remplace Fully Kiosk + Lovelace sur la
Galaxy Tab S2 (LineageOS, Android 9 minimum) de la chambre : contrôle lumière / volet / scènes,
heure et météo, veille de l'écran pilotée par la caméra frontale.

La même app sert aussi de **dashboard de la cuisine** (recettes Mealie) et de **dashboard global du salon** (Réglages → « Pièce de cette tablette » → Salon) :
volet et lumières du salon, onglets Cuisine et Chambre, et annonces vocales de l'agenda.
Sur un **téléphone**, elle affiche ce dashboard global en portrait, et propose trois **widgets** d'écran d'accueil.

Specs : `docs/superpowers/specs/2026-10-16-dashboard-chambre-design.md`,
`docs/superpowers/specs/2026-10-17-salon-design.md`,
`docs/superpowers/specs/2026-10-21-foyer-mobile-widgets-design.md`

## Compiler

Prérequis : JDK 21 (chemin dans `gradle.properties`), Android SDK (`local.properties` → `sdk.dir`).

```bash
./gradlew testDebugUnitTest   # tests unitaires
./gradlew assembleRelease     # APK signé : app/build/outputs/apk/release/app-release.apk
```

La signature release lit `keystore.properties` (non versionné) :

```properties
storeFile=C:/Users/Alex/.android-keys/chambre-release.jks
storePassword=…
keyAlias=chambre
keyPassword=…
```

Garde le keystore : les mises à jour de l'app sur la tablette doivent être signées avec la même clé.

## Installer sur la tablette

1. Sur la tablette : **Options pour les développeurs** → activer **Débogage USB** (et **Débogage ADB
   sur le réseau** pour le Wi-Fi).
2. Depuis le PC :
   ```bash
   adb connect <ip-tablette>:5555        # ou brancher en USB
   adb install -r -g app/build/outputs/apk/release/app-release.apk
   ```
   `-g` accorde directement la permission caméra.
3. Ouvrir l'app **Chambre** ; dans les Réglages → Démarrage, activer **Démarrage automatique** pour qu'elle
   revienne seule après un redémarrage (Android 10+ : bouton « Autoriser »).
4. Désactiver Fully Kiosk (ou le désinstaller) pour qu'il ne reprenne pas la main au démarrage.
5. Dans Home Assistant : profil → **Sécurité** → **Jetons d'accès longue durée** → créer un jeton.
6. Dans l'app : saisir l'URL (`http://homeassistant.local:8123` ou l'IP) et le jeton →
   **Enregistrer et connecter**.

## Utilisation

- **Réglages** : toucher la ligne « batterie + engrenage » sous la date. L'écran Réglages montre l'aperçu
  caméra en direct (zones en mouvement en vert) ; il empêche la mise en veille tant qu'il est ouvert.
- **Veille** : écran noir après le délai réglé (1 min par défaut) sans mouvement devant la caméra ni toucher.
  Un mouvement ou un toucher réveille l'écran ; le premier toucher ne déclenche rien d'autre.
- **Détection de mouvement** (OpenCV, soustraction de fond MOG2) : une zone qui bouge sur au moins 2 images
  sur 3 réveille l'écran ; les changements de lumière (plus de 60 % de l'image d'un coup) et la propre
  lumière de l'écran sont ignorés. Réglage « Taille minimale d'un mouvement » (3 % par défaut).
- **Démarrage automatique** (Réglages → Démarrage) : ouvre le dashboard au démarrage de la tablette et
  après une mise à jour (l'app n'est pas un lanceur). Android 10+ demande l'autorisation « Afficher par-dessus
  d'autres applications » (bouton « Autoriser » dans la carte) ; Android 9 n'en a pas besoin.
- **API caméra** (Réglages) : CameraX par défaut ; **Camera1** pour les tablettes dont l'aperçu reste
  « indisponible » avec CameraX (pilote caméra ancien, ex. Galaxy Tab S2 SM-T813).
- **Météo** : toucher le bloc météo pour passer de « Heures » (7 prochaines heures) à « Semaine » (6 jours) ;
  retour automatique aux heures après 30 s.
- **Par défaut** : remet la lumière à 70 % · 2700 K, comme l'appui long des télécommandes.

## Mode salon

- **Onglets** (rail à gauche) : Salon (par défaut), Cuisine (identique au dashboard de la cuisine : volet et
  recettes Mealie, adresse et clé Mealie dans Réglages → Salon), Chambre (identique au dashboard de la chambre).
  Retour automatique sur Salon après 2 min sans toucher, sauf pendant une recette.
- **Annonces vocales** (synthèse vocale Android, voix française) :
  - agenda **Travail** : « Réunion dans 5 minutes : <titre> », une seule fois,
    avec un bandeau en haut de l'écran ;
  - agenda **Alertes** (ex. « Litière » récurrente) : à l'heure de l'événement
    (9 h pour un événement « journée »), écran plein « C'est fait » ; la voix répète toutes les 5 min
    pendant 1 h, l'écran reste jusqu'à confirmation ;
  - fin d'un minuteur de cuisine : « Minuteur N terminé », répété toutes les 30 s (5 fois) jusqu'à OK.
- Une annonce réveille l'écran. « Tester la voix » (Réglages) vérifie le haut-parleur.

Les agendas ne passent pas par Home Assistant : l'app lit ceux **synchronisés sur la tablette**
(relus chaque minute, même sans HA).

1. Synchroniser Google Agenda sur la tablette : compte Google dans les paramètres Android si la tablette a
   les services Google, sinon l'app **DAVx5** (F-Droid) → connexion avec Google → cocher les agendas.
2. Dans l'app : Réglages → Pièce : Salon → **Autoriser l'accès aux agendas**, puis choisir
   l'agenda Travail et l'agenda Alertes dans les listes → Enregistrer.

Si la voix ne sort pas, installer « Services vocaux Google » (ou RHVoice) et une voix française ;
le volume des annonces suit le volume multimédia.

## Mode cuisine

Réglages → Pièce : **Cuisine**, puis l'**adresse Mealie** et une **clé API Mealie** (Mealie → profil → Clés API).

- Volet de la cuisine, et les recettes Mealie (lecture seule) : recherche, filtres (Moins de 45 min, catégories),
  bandeau « Au menu » quand un repas est planifié dans Mealie aujourd'hui.
- Fiche recette : portions − / + (quantités recalculées pour les ingrédients analysés par Mealie), ingrédients à
  cocher, étapes une par une. Chaque durée du texte (« 10 minutes », « 30 à 45 secondes ») est une pastille ⏱ :
  un appui crée une minuterie dans l'horloge Android. L'écran ne se met pas en veille pendant une recette.
- Les ingrédients « non analysés » dans Mealie s'affichent tels quels : utiliser « Analyser les ingrédients » dans
  Mealie pour qu'ils suivent les portions.

## Téléphone

Sur un téléphone (écran de moins de 600 dp, ex. Pixel 9), l'app affiche automatiquement le **dashboard global**,
quelle que soit la pièce réglée : les panneaux des tablettes (Lumière, Volet, Ambiance, recettes) empilés en
portrait, et une **barre flottante** en bas pour passer de Salon à Cuisine ou Chambre.

- Salon : lumières et volet du salon, température et humidité ; Cuisine : le dashboard cuisine (volet et recettes
  Mealie) ; Chambre : le dashboard de la chambre.
- Pas sur le téléphone : agenda, veille par la caméra, annonces vocales, retour automatique sur Salon, batterie
  envoyée à Home Assistant, démarrage automatique, mode kiosque. L'écran reste allumé seulement pendant une recette.
- Réglages : URL + jeton Home Assistant, adresse + clé Mealie. L'app ne marche que sur le réseau de la maison ;
  à distance, passer par WireGuard et saisir l'**adresse IP** de Home Assistant (`homeassistant.local` ne passe
  généralement pas par un VPN).
- Le même APK s'installe sur les tablettes (32 bits) et le téléphone (64 bits).

## Widgets

Trois widgets d'écran d'accueil (appui long sur l'écran d'accueil → Widgets → Foyer), au style Google Home,
couleurs du fond d'écran (Material You, Android 12+ ; Ambre sur les tablettes) :

- **Chambre** (4×2) et **Salon** (4×2, avec température · humidité) : tuile Lumière (toucher = allumer / éteindre,
  − / + = luminosité de 10 en 10 %, une lumière éteinte s'allume à 10 %) et tuile Volet (toucher = ouvrir s'il est
  fermé, fermer s'il est ouvert, arrêter s'il bouge ; ▲ ■ ▼).
- **Cuisine** (4×1) : tuile Volet avec ▲ ■ ▼.
- Le bouton ↗ en haut à droite (ou le titre) ouvre Foyer sur la pièce.

Les widgets parlent à Home Assistant directement (API REST, mêmes URL et jeton que l'app), même app fermée. Ils se
mettent à jour après chaque appui (puis 3 s et 20 s après pour un volet), toutes les 15 min, et quand on quitte
l'app. Si Home Assistant ne répond pas, ils gardent le dernier état connu, grisé.

## Batterie dans Home Assistant

L'app s'enregistre via l'intégration **Mobile App** (appareil « Tablette chambre », ou « Tablette salon »
en mode salon, avec des entités `…tablette_salon_…`) et publie :
`sensor.tablette_chambre_batterie`, `binary_sensor.tablette_chambre_en_charge`,
`sensor.tablette_chambre_temperature_batterie` (immédiatement au (dé)branchement, sinon au plus toutes les
5 min, et toutes les 30 min dans tous les cas).

L'automatisation HA « Tablette chambre – protection batterie » garde la batterie entre 40 et 80 % en pilotant
la prise `switch.grillplats_plug_2` (coupure aussi au-delà de 40 °C, rallumage de sécurité sans nouvelles depuis 2 h).

Les entités sont codées en dur dans `app/src/main/java/fr/alex/chambre/ha/Entities.kt`.
