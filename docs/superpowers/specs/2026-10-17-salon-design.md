# Tablette du salon : dashboard global et annonces vocales de l'agenda

Date : 2026-10-17 · Prototype validé : https://claude.ai/artifact/WDCUeGMHFPbYuJfzy4x4Tg (version 14)
S'appuie sur l'app existante (`fr.alex.chambre`, v1.1.0 : Compose Material 3, WebSocket HA, OpenCV, Mobile App).

## 1. Intention

Une tablette fixée dans le salon sert de **tableau de bord de toute la maison** et de **haut-parleur
d'agenda**, « comme Alexa ». Ce que l'utilisateur a dit compter le plus : **entendre** ses réunions de
travail et ses alertes domestiques (ex. la litière) sans regarder son téléphone.

Par défaut elle pilote le salon (volet, lumières) ; elle permet aussi d'aller piloter les autres pièces.

### Objectifs
- Écran Salon par défaut : calendrier du jour en vedette, volet, lumières.
- Navigation vers Cuisine et Chambre ; l'onglet Chambre est **le plus proche possible** du dashboard de
  la chambre.
- Annonces vocales depuis Google Calendar :
  - calendrier **Travail** : 5 min avant chaque réunion, la tablette dit son **titre**, une fois ;
  - calendrier **Alertes** (nouveau calendrier Google, ex. évènement récurrent « Litière ») : à l'heure
    de l'évènement, alerte plein écran + annonce, **répétée jusqu'à confirmation** (« C'est fait »).
- Même identité que la chambre : heure, date, batterie + réglages, météo Heures/Semaine, thème ambre.

### Non-objectifs (v1)
- Assistant vocal qui écoute (pas de micro, pas de commande vocale).
- Créer / modifier des évènements depuis la tablette.
- Annoncer les autres calendriers (ils ne sont pas affichés non plus).
- Musique, caméras, alarme.

### Anti-objectifs (échec même si « ça marche »)
- Une alerte qu'on rate parce que l'écran dormait ou que l'app était en arrière-plan.
- Annoncer deux fois la même réunion, ou continuer à répéter une alerte déjà confirmée.
- Un onglet Chambre qui ne ressemble pas à la tablette de la chambre.
- Des commandes qui agissent sur la mauvaise pièce.

## 2. Décisions prises par l'utilisateur

- Agenda = **Google Calendar**, plusieurs calendriers ; seuls **Travail** et **Alertes** comptent.
- Réunion : annonce du **titre**, **5 min avant**.
- Alerte : dans un calendrier nommé **« Alertes »**, **à confirmer** sur l'écran.
- Salon (prototype v14) : de gauche à droite, barre des pièces | **calendrier** (« Prochaine annonce » en
  grande carte + « Ensuite aujourd'hui ») | **volet** (widget identique à la chambre : jauge verticale
  remplie depuis le haut, 4 boutons ↑ ■ ↓ ½ empilés à droite, état dessous ; colonne ≈ 236 dp) |
  **lumières** (colonne étroite ≈ 236 dp : 2 jauges verticales luminosité + température, bouton
  Allumées/Éteintes, bouton Par défaut).
- Lumières du salon : **un seul groupe** `light.lumieres_salon` (température seule, 2202–4000 K, pas de
  couleur) ; pas de bandeau vitrine.
- Onglet Chambre : mêmes 3 colonnes que l'app chambre (Ambiance | Lumière avec 2 jauges, Allumée/Par
  défaut, 9 pastilles + roue | Volet).

## 3. Contraintes

- **Une seule base de code** : l'app actuelle devient multi-pièces. Un réglage **« Pièce de cette
  tablette »** (Chambre / Salon) choisit l'écran d'accueil et les fonctions actives. La tablette chambre
  reste identique.
- Android **11 minimum** (minSdk 30), ARM 32 bits ; paysage ; app lanceur (kiosque).
- Connexion HA existante (WebSocket + jeton). Batterie publiée via Mobile App sous un nom d'appareil
  propre à la pièce (« Tablette salon »).
- Les commandes de chaque pièce ciblent ses propres entités (table §5).

## 4. Annonces

### Source
- Intégration **Google Calendar** de Home Assistant (à installer par l'utilisateur, §8) → entités
  `calendar.*`. Les deux entités sont choisies dans les réglages (défauts `calendar.travail`,
  `calendar.alertes`).
- L'app lit les évènements du jour via le service `calendar.get_events` (réponse de service, WebSocket),
  au démarrage, toutes les **5 min**, et à minuit. Elle planifie les annonces localement.

### Réunion (Travail)
- À `début − 5 min` : bandeau en haut (« RÉUNION DANS 5 MIN · 14:00 », titre, ✕) pendant 60 s +
  synthèse vocale « Réunion dans 5 minutes : {titre} ».
- Évènements « toute la journée » : jamais annoncés.
- Chaque évènement (uid + début) n'est annoncé **qu'une fois**, même après redémarrage de l'app.

### Alerte (Alertes)
- À `début` : écran plein « ALERTE · {heure} · {titre} » + grand bouton **C'est fait** + voix
  « Alerte : {titre} ».
- Sans confirmation : répétition de la voix toutes les **5 min**, au plus **1 h** (12 fois) ; l'écran
  reste affiché.
- « C'est fait » arrête l'alerte pour cette occurrence (uid + début), persistant au redémarrage.
- Évènements toute la journée : alerte à **9:00**.

### Comportement commun
- Une annonce **réveille l'écran** (sort de la veille) et le garde allumé tant qu'elle est affichée.
- Voix : **synthèse vocale Android hors ligne** (`TextToSpeech`, français), volume du flux média.
- Deux annonces simultanées : l'alerte passe devant, la réunion attend la fin de l'alerte.

## 5. Écrans

Bandeau commun (identique à la chambre) : heure, date, ligne batterie + engrenage, météo Heures/Semaine.
Barre des pièces à gauche (≈ 212 dp) : Salon, Cuisine, Chambre — icône, nom, résumé d'état
(« Lumières à 65 % · volet ouvert ») ; la pièce active en surbrillance. Retour automatique à l'écran Salon
après **2 min** sans toucher.

| Écran | Contenu | Entités |
|---|---|---|
| Salon | calendrier · volet · lumières | `cover.volet_salon`, `light.lumieres_salon`, calendriers |
| Cuisine | volet, 3 minuteurs (5 min / 10 min), liste de courses (nombre d'articles), prise tablette cuisine | `cover.volet_cuisine`, `timer.minuteur_1..3` + `script.demarrer_minuteurX_5min/10min`, `todo.liste_dachats`, `switch.grillplats_plug` |
| Chambre | Ambiance (4 scènes) · Lumière · Volet, comme l'app chambre | entités de la v1 chambre |

Le bloc « Climat » sous la barre des pièces affiche `sensor.salon_temperature` / `sensor.salon_humidity`.
Le calendrier Salon montre les évènements **Travail** et **Alertes** restant aujourd'hui ; toucher un
évènement ne fait rien en v1 (dans le prototype il simulait l'annonce).

Mode Chambre (tablette de la chambre) : inchangé ; pas de barre des pièces, pas d'annonces.

## 6. Réglages

Ajouts à l'écran Réglages existant : **Pièce de cette tablette** (Chambre / Salon) ; pour Salon :
entités des calendriers Travail et Alertes, **test de la voix** (« Ceci est un test »), volume des
annonces.

## 7. Tests

- JVM : planification des annonces (5 min avant, toute la journée ignorée, alerte à 9:00 pour
  toute la journée), « une seule fois » et « C'est fait » persistants, répétition 5 min / 1 h max,
  priorité alerte > réunion, lecture de la réponse `calendar.get_events`, retour auto au Salon.
- Émulateur Android 11 avec un faux HA (MockWebServer côté tests ou URL injoignable pour l'UI) :
  captures des écrans Salon / Cuisine / Chambre / bandeau / alerte.
- Tablette salon réelle : test de voix et d'une alerte de test ; **aucune action sur les appareils
  sans l'annoncer**.

## 8. Prérequis côté utilisateur

1. Ajouter l'intégration **Google Calendar** dans Home Assistant (connexion Google, OAuth).
2. Créer dans Google le calendrier **« Alertes »** et l'évènement récurrent « Litière ».
3. Indiquer le modèle et la version d'Android de la **tablette du salon**.

## 9. Laissé au constructeur

Structure des modules, nommage, persistance des annonces déjà faites (DataStore ou fichier), choix de
la voix française disponible, animations, détails visuels de la Cuisine (le prototype fait foi).

## 10. Précisions après relecture (décisions du constructeur, à valider)

- **Veille / réveil** *(choix du constructeur)* : comme la chambre — l'app est le lanceur et reste au
  premier plan, l'écran reste allumé (`FLAG_KEEP_SCREEN_ON`) et la « veille » est le voile noir +
  luminosité minimale, réveillé par la caméra (OpenCV) ou le toucher. Une annonce réveille simplement
  ce voile. Pas de service de premier plan ni d'alarmes système en v1. Le mode Salon garde la section
  détection de mouvement des réglages. *À revoir si la tablette du salon ne peut pas rester allumée.*
- **Clé d'un évènement** *(choix)* : `calendrier + titre + début` (la réponse de `calendar.get_events`
  n'a pas d'uid). Un évènement déplacé ou renommé compte comme nouveau.
- **Rattrapage** *(choix)* : au (re)démarrage ou après une coupure HA,
  - réunion : annoncée si l'on est entre `début − 5 min` et `début` ; ignorée si elle a commencé ;
  - alerte : affichée (en retard) si l'on est dans l'heure qui suit son début et qu'elle n'est pas
    confirmée ; les répétitions restent calées sur l'heure d'origine (fin à `début + 1 h`).
- **Après 1 h sans confirmation** *(choix)* : la voix s'arrête, l'écran d'alerte **reste affiché**
  jusqu'à « C'est fait ». Si plusieurs alertes sont en attente, elles sont empilées (la plus récente
  devant, « 2 alertes » indiqué) ; « C'est fait » confirme celle affichée.
- **Cuisine** : minuteurs avec temps restant et bouton Annuler (`timer.cancel`) ; les scripts
  `script.demarrer_minuteurX_5min/10min` existent déjà dans HA. Liste de courses : nombre d'articles
  (état de `todo.liste_dachats`). Prise tablette cuisine : **affichage seul** (la couper éteindrait la
  tablette de la cuisine). La fin des minuteurs n'est pas annoncée en v1 (non demandé).
