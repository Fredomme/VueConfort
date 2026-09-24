# VueConfort Vision

Application Android indépendante de la loupe VueConfort et de Loupe Lab.

- Nom affiché : **VueConfort Vision**, icône violette.
- Identifiant : `fr.vueconfort.vision` (version 0.2.0).
- Android 14 minimum ; contrôlée sur Samsung Galaxy S25, Android 16, portrait 1080 × 2340.
- Aucun lien de service, aucune lecture de préférences, aucun identifiant commun avec les anciennes applications.

## Utilisation

1. Fermer temporairement toute autre loupe active, ouvrir VueConfort Vision et autoriser sa fenêtre flottante.
2. Appuyer sur **Démarrer Vision**, accepter le partage de l’écran, puis ouvrir un contenu à lire.
3. L’image du cadre violet est actualisée automatiquement dans le panneau inférieur. Faire glisser le **bandeau violet** pour déplacer la zone source ; faire défiler l’application dans la partie haute.
4. Les quatre molettes sont disponibles ensemble : zoom, lumière, contraste et contours. Glisser vers le haut pour augmenter, vers le bas pour diminuer.
5. **Pause** fige la source, tout en laissant les réglages de lumière, contraste et contours actifs. Un changement de zoom ou de cadrage reprend le direct pour obtenir les nouveaux pixels nécessaires.
6. **Voir original** compare au même grossissement. **Effet fort** rend les changements très visibles. **Neutre** remet les trois traitements à zéro et conserve le zoom.
7. **Centrer** recentre la source. **Fermer** arrête cette loupe et le partage. Les quatre réglages sont mémorisés dans cette application uniquement.

## Nouvel essai optique guidé

Depuis l’accueil, **Essai optique guidé** affiche un texte de comparaison. Depuis la fenêtre de loupe, **Essai optique sur cette image** fige le centre du contenu et ferme le partage d’écran. Choisir une distance, confirmer que l’agrandissement du système est désactivé, puis calculer l’un des essais A/B/C. Le calcul peut prendre un moment ; comparer ensuite Original et Traité. Les choix Mieux/Pareil/Moins bien sont des préférences, pas des mesures de la vue.

Voir [le périmètre du moteur optique](OPTICAL-EXPERIMENT.md) pour ses hypothèses et ses limites. Ce mode traite une image fixe en niveaux de gris ; les quatre molettes de la loupe continuent de fonctionner en direct.

## Portée de cette version

Cette loupe maîtrise les pixels qu’elle affiche : elle recadre et agrandit d’abord l’image, applique les traitements à cette taille finale, puis l’affiche sans nouvel agrandissement dans sa surface. Sur le S25 testé, cette surface mesure 1008 × 432 pixels.

Le contenu reste en couleur. Lumière agit sur la luminance de l’image et ne change pas la luminosité matérielle du téléphone. L’accentuation de contours peut produire des halos lorsqu’on la pousse ; revenir vers Neutre permet de comparer.

Il s’agit d’une base pour les futurs traitements visuels personnalisés, **pas d’une correction optique personnelle déjà calibrée**. Le moteur optique V2.4 limité à 128 × 128 n’est pas étiré artificiellement dans cette nouvelle fenêtre. Le nouveau mode optique travaille sur 768 × 256 pixels à partir d’hypothèses explicites ; une correction personnelle et son bénéfice réel restent à déterminer.

Le panneau et la zone source restent séparés pour éviter la recapture de la loupe. Les écrans protégés par Android ne sont pas contournés. Cette version s’utilise en portrait ; un changement d’orientation ferme la capture. Un autre partage d’écran ou le verrouillage peuvent également arrêter la session Android. L’image dans la loupe ne transmet pas les clics à l’application dessous : interagir dans la partie haute.

En usage normal, les images sont traitées localement en mémoire, sans sauvegarde ni transmission. Seuls les réglages sont persistés. Le mode de preuve technique, activé explicitement par un extra `technical_evidence`, conserve des images de la page de test dans l’espace propre à cette application ; il n’est pas activé par le lancement habituel.

## Vérifications de la base 0.1

Les résultats suivants concernent la version précédente. Voir le [bilan de validation 0.2](docs/VALIDATION-0.2.md) pour les tests, l’audit indépendant et les essais sur S25 du nouveau mode optique.

- Compilation Debug réussie ; contrôle Android sans erreur bloquante.
- 24 tests unitaires : géométrie de source, zoom, marges et limites ; rendu, identité, couleur, absence de mutation et de fuite entre lignes.
- Contrôles sur S25 : image actualisée automatiquement lors du changement de page, déplacement du cadre, zoom, réglages combinés sur une image en pause, comparaison original et remise à zéro.
- Sept comparaisons de l’aperçu avec les bitmaps calculés : zéro pixel différent. Aucun pixel violet de la bordure recapturé après zoom et déplacement sur la page de test.
- Calculs observés pendant ces essais : environ 15 à 53 ms selon l’opération. La capture est échantillonnée toutes les 120 ms, sans file de traitements en attente ; cette version ne vise pas un affichage vidéo à 60 images/s.
- Fermeture vérifiée ; la session n’utilise aucun service de la loupe habituelle.

## Architecture et provenance

L’entrée de capture, les molettes et les principes de rendu reprennent le travail effectué dans Loupe Lab. Ils sont adaptés dans un nouveau projet minimal indépendant, sans copier ses anciens services d’accessibilité ni ses outils annexes.

Pipeline : MediaProjection → recadrage de la source → redimensionnement final → traitement de luminance en couleur → bitmap affiché à 1:1. Le traitement est réalisé sur un worker unique ; les résultats périmés sont abandonnés. Le zoom et le déplacement attendent une nouvelle capture après mise à jour du cadre. L’arrêt libère l’ImageReader après la fermeture de toute image en calcul.

Le moteur pur `VisionPixelRenderer`, la géométrie `VisionGeometry` et leurs tests sont indépendants d’Android. Le projet réutilise Gradle 8.9.1 pour le plugin Android et Kotlin 2.1.20 disponibles sur le poste ; aucun composant de l’ancienne application n’est mis à jour.

## Compiler depuis ce dépôt

Ouvrir le dossier `vision/` comme projet Android Studio indépendant, ou se placer dans ce dossier pour utiliser son propre lanceur Gradle. Le projet Android historique à la racine n’inclut pas Vision comme module.

Pré-requis : JDK 17 ou supérieur compatible avec Gradle 9.3, SDK Android 36 et Build Tools 35.0.0. Définir `JAVA_HOME` et `ANDROID_HOME`, ou le chemin du SDK dans un fichier local `local.properties` (non versionné). Le premier lancement nécessite le réseau pour récupérer les dépendances.

Depuis la racine du dépôt :

```sh
cd vision
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Le workflow GitHub Actions `Validate VueConfort Vision` exécute ces contrôles sur les propositions et modifications concernant `vision/`. L’audit scientifique indépendant possède ses propres [instructions de reproduction](audit/README.md).
