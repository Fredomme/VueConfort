# Égaliseur visuel — premier incrément

Cette fondation interactive appartient à VueConfort Aperçu (`fr.vueconfort.app.preview`).
Elle prépare le raccordement progressif des bilans et des moteurs optiques sans les simuler.
Ce document décrit le code et les procédures ; les résultats mesurés appartiennent au rapport
de recette associé au commit, à l’APK et à l’appareil utilisés.

## Parcours et scènes

L’accueil ouvre directement l’égaliseur. Les parcours sans correction connue et les parcours
avec saisie ou import d’un bilan y convergent. Un bilan reste facultatif ; ses valeurs doivent
être confirmées avant leur enregistrement. Cet enregistrement ne choisit plus une loupe.

L’aperçu reste au-dessus des commandes. Les scènes Lecture, Détails et Image partagent les
mêmes préférences ; changer de scène ne remet aucun curseur à zéro.

- **Lecture** : petits caractères, lecture normale et texte à faible contraste.
- **Détails** : gris, lignes fines, cercles et contours de différentes épaisseurs.
- **Image** : illustration originale locale, embarquée dans
  `app/src/main/res/drawable-nodpi/equalizer_reference.png`.
  Elle représente des livres, une plante et un verre ; ce n’est pas une photographie.
  Source : illustration créée pour ce projet. Licence : ressource originale du projet,
  sans licence tierce associée ; aucune licence libre supplémentaire n’est déclarée ici.

## Architecture et responsabilités

| Composant | Responsabilité |
| --- | --- |
| `equalizer/EqualizerScreen.kt` | Scènes, commandes continues, comparaison temporaire et actions du profil. |
| `equalizer/EqualizerViewModel.kt` | Brouillon, restauration, erreurs, opérations attendues et révisions de rendu. |
| `equalizer/EqualizerProfile.kt` | Modèle versionné, validation et encodage déterministe. |
| `equalizer/EqualizerRendering.kt` | Traduction explicite des préférences vers le moteur et reçu de rendu. |
| `optical/OpticalSettings.kt` | Paramètres bornés et plan effectif, dont l’identité. |
| `optical/OpticalRenderer.kt` | Traitements AGSL et mise à jour des uniforms. |
| `data/VisualProfileRepository.kt` | Transaction unique du profil et références au bilan canonique. |
| `equalizer/EqualizerPerformance.kt` | Instrumentation facultative, active uniquement pendant une session de mesure. |

Ces chemins sont relatifs à `app/src/main/java/fr/vueconfort/app/`.
Un shader est conservé par composition. Les changements mettent à jour ses uniforms et
créent un instantané `RenderEffect` pour invalider aussi une image immobile. Il n’existe
ni reconstruction de bitmap à chaque curseur ni file de calcul optique lourd.

## Paramètres raccordés

Les bornes ci-dessous sont des limites logicielles, pas des limites médicalement validées.

| Commande | Plage ; neutre | Effet réellement appliqué |
| --- | --- | --- |
| Taille | `sizeScale` 1–2 ; 1 | Échelle du contenu, ancrée en haut à gauche, dans un cadre fixe. |
| Netteté | `sharpness` 0–0,8 ; 0 | Accentuation isotrope avec quatre voisins ; détail borné à ±0,12 et dépassement local limité à 0,02 dans l’espace RGB du shader. |
| Contraste | `contrast` 0,7–1,5 ; 1 | Contraste global autour de 0,5 ; le nom historique `localContrast` ne signifie pas contraste local. |
| Lumière douce | `lightComfort` 0–1 ; 0 | Gain RGB `1 − 0,18 × valeur` et atténuation des blancs `0,30 × valeur`. Ne modifie pas la luminosité système. |
| Épaisseur | `fontWeight` 400–800 ; 400 | Sur Lecture uniquement : remplissage et contour des mêmes glyphes, sans changement de métriques. Contour de 0 à 0,65 dp, multiplié par l’intensité avant l’échelle de scène. Ce n’est pas un changement de graisse typographique. |
| Intensité | `intensity` 0–1 ; 1 | Mélange source/traitement et amplitude du contour de texte ; conserve l’échelle choisie. |

Gamma et saturation restent à 1 ; température, déformation et renforcement directionnel
restent neutres. Aucune SPH/CYL/AXE/ADD n’est convertie ici en PSF ou en filtre correcteur.
Sur Android antérieur à 13, les commandes nécessitant AGSL sont désactivées et la limite
est affichée ; taille et épaisseur restent disponibles. Un profil demandant un traitement
pixel indisponible produit un reçu `REJECTED` avec le motif correspondant.

## Neutralité et comparaison

Le cadre, le contenu, l’origine et l’échelle sont établis séparément des traitements.
Maintenir Original retire les traitements de pixels et le contour de texte, sans changer
la scène, la position, le cadrage ou le zoom. Le relâchement restitue les préférences du
brouillon ; aucun de ces gestes ne sauvegarde ou ne modifie le profil.

À intensité zéro, le rendu traité rejoint le rendu neutre **à la même taille choisie**.
Pour retrouver aussi l’échelle initiale, utiliser Remettre au neutre : taille 1,
netteté 0, contraste 1, lumière douce 0, épaisseur 400, intensité 1.
Un plan pixel neutre n’installe aucun `RenderEffect`. Désactivation et intensité zéro
suppriment également les anciennes transformations internes du renderer.
La comparaison temporaire du renderer générique conserve sa géométrie active ; dans
l’égaliseur, cette géométrie interne est toujours neutre, le zoom étant extérieur.

## Profil personnel et évolution

Le schéma actuel vaut `1`, avec l’identifiant `personal-equalizer`.

| Champ | Sens et propriétaire |
| --- | --- |
| `preferences`, `scene` | Choix perceptifs et scène de l’utilisateur, sans interprétation clinique. |
| `revision` | Révision des entrées ; reste monotone pendant la session, y compris après annulation ou suppression. |
| `confirmedBilan` | Référence datée à la source confirmée. Les données médicales restent dans leur stockage distinct. |
| `context` | Distance déclarée et port d’une correction, facultatifs ; une valeur inconnue reste absente. |
| `calculated` | Résultats d’un moteur identifié, avec paramètres, provenance et révision source ; absent dans ce premier raccordement perceptif. |
| `applied` | Reçu des paramètres transmis au rendu observé, version du moteur, décision, motifs et révision. |
| `provenance` | Origine `USER_PERCEPTUAL`, dates de création et de sauvegarde. |
| `extensions` | Champs nommés extensibles, conservés par l’encodage sans activation implicite de fonctions. |

Le moteur actuel s’identifie par `agsl-perceptual-1`. Les paramètres du reçu incluent
`viewportScale`, `textStrokeDp`, `sharpness`, `contrast`, `brightness`, `whiteReduction`
et `pixelIntensity`. L’échelle reste distincte de l’identité des traitements.
`APPLY`, `IDENTITY` et `REJECTED` décrivent la décision de ce moteur ; ils ne prouvent ni
la présentation physique d’une frame ni une correction du défaut visuel.
Le reçu est émis après observation du dessin de l’aperçu ajusté, jamais pendant Original.
Il est mémorisé sans provoquer de nouvelle composition puis sauvegardé seulement si sa
révision correspond encore. Un changement d’entrées invalide les anciens reçus.

Les futurs champs peuvent être regroupés par espaces de noms, par exemple
`spatialBands.*`, `directional.*`, `distance.*` et `opticalPrecompensation.*`.
Ce sont des conventions d’extension proposées, pas des contrôles opérationnels.
Chaque raccordement devra définir unités, espace couleur, grille de pixels, paramètres
demandés/calculés/appliqués, provenance, décision et tests de parité entre backends.
Le futur affichage inter-applications pourra consommer ce contrat ; MediaProjection et
les moteurs V2.x/D4/D5 ne sont pas portés ou commercialisés dans cet incrément.

## Persistance, confidentialité et opérations

Le profil complet occupe une seule clé `equalizer_profile_v1` dans le DataStore privé.
L’encodage conserve exactement les flottants valides et les champs nommés. Le Base64 sert
à délimiter les valeurs, **pas à les chiffrer**. Une version inconnue ou un contenu illisible
bloque la lecture et le remplacement, y compris lors de la transaction de sauvegarde.
Les permissions réseau restent retirées et les sauvegardes Android désactivées.

- **Enregistrer** attend l’écriture atomique avant d’annoncer la réussite.
- **Annuler** restaure les choix enregistrés, ou le neutre sans profil ; aucune écriture.
- **Remettre au neutre** modifie le brouillon et conserve la scène ; Enregistrer rend ce choix persistant.
- **Supprimer le profil** efface le profil et revient au neutre ; conserve bilan, lecteur et loupe.

Le brouillon survit aux changements de configuration. Sa sérialisation Android se fait
lors de la sauvegarde d’état, jamais à chaque mouvement. Après arrêt complet, le profil
enregistré est la référence durable. Modifier ou supprimer un bilan actualise/détache sa
référence et invalide les calculs associés, sans convertir les préférences en données cliniques.

L’ancien champ expérimental global `vision_refinement_v1` est supprimé lors d’un
enregistrement ou d’une suppression explicite de l’égaliseur. Il n’est pas recopié dans
un deuxième propriétaire. La typographie historique du lecteur et les profils/positions
de la loupe appartiennent à leurs fonctions respectives et sont conservés.

## Construction, tests et mesures

Depuis la racine du dépôt, avec Java 17 et le SDK Android 36 configurés :

```sh
./gradlew --no-daemon -PcommercialPreviewTests :app:testReleaseUnitTest :app:lintRelease :app:assemblePreview :app:assemblePreviewAndroidTest
./gradlew -PcommercialPreviewTests -Pandroid.testInstrumentationRunnerArguments.notClass=fr.vueconfort.app.equalizer.EqualizerPerformanceDeviceTest :app:connectedPreviewAndroidTest
./gradlew -PcommercialPreviewTests -Pandroid.testInstrumentationRunnerArguments.class=fr.vueconfort.app.equalizer.EqualizerPerformanceDeviceTest :app:connectedPreviewAndroidTest
```

Les tests couvrent contrats neutres, pixels de l’aperçu, commandes, comparaison, scènes,
réconciliation des révisions et persistance réelle après fermeture/réouverture du stockage.
Les tests de stockage utilisent des fichiers privés uniques et ne réinitialisent pas les données utilisateur.
La mesure prolongée dure 180 secondes par défaut ; elle manipule les commandes réelles sans
enregistrer le brouillon. Son JSON se trouve dans `files/performance/equalizer-performance.json`.

Les P50/P95/P99 commande→dessin et commande→fin de frame applicative sont distincts de la
présentation écran. Les traces Perfetto/SurfaceFlinger doivent établir cette dernière,
ainsi que les frames réellement abandonnées. Les événements fusionnés, échantillons non
raccordés et limites de buffers doivent rester visibles. Les allocations concernent tout
le processus instrumenté ; les pics mémoire sont les maxima des prélèvements périodiques.
60 mises à jour/s et P95 commande→présentation ≤ 50 ms sont des objectifs à mesurer.
Aucun nombre de cette section ne constitue un résultat de recette.

## Retour arrière et limites

Conserver le point Git précédent et les données privées avant un retour arrière. Préparer
un retour des seuls commits de cet incrément dans une branche de travail, sans écraser les
autres modifications. Pour réinstaller un ancien code, construire un Aperçu de même identité
et même signature avec un numéro de version accepté par Android ; utiliser une mise à jour
qui conserve les données. Ne pas désinstaller ou effacer les données pour contourner la version.
L’ancien code ignore la clé d’égaliseur ajoutée ; son bilan et sa loupe restent séparés.

Aucune publication Google Play ni fusion automatique de la PR nº 2 ne fait partie de cette
procédure. Les résultats devront distinguer codé, compilé, testé automatiquement, exécuté
sur S25, observé physiquement et bénéfice perceptif démontré. Ce dernier, ainsi qu’une
capacité de remplacement des lunettes, n’est pas établi par cet incrément.
