# Ressources Google Play — VueConfort 1.0.0

Ce dossier contient les ressources préparées pour Google Play Console à partir du logo officiel fourni le 4 août 2026 et de captures réelles réalisées le même jour sur un Galaxy S25 SM-S931B. Deux corrections purement visuelles ont été appliquées avant les captures ; aucun comportement métier n’a été modifié.

## Icônes

- `icons/vueconfort_adaptive_1024.png` : source PNG transparente 1024 × 1024 pour l’icône adaptative Android. À intégrer comme foreground avec un fond validé avant un futur build.
- `icons/vueconfort_play_512.png` : icône haute résolution 512 × 512 à importer dans la fiche Play Store.
- `icons/vueconfort_monochrome_white_1024.png` : variante monochrome blanche transparente.
- `icons/vueconfort_monochrome_black_1024.png` : variante monochrome noire transparente.
- `icons/vueconfort_mark_source.png` : extraction de travail du symbole officiel, conservée pour traçabilité.
- `logo_officiel_extrait.png` : panneau du logo extrait sans redessin depuis le fichier fourni.

Les fichiers d’icône ne contiennent ni texte ni angle arrondi ajouté. L’extraction disponible dans la planche fournie est de définition limitée ; demander le master SVG ou PNG haute définition avant la production publique est recommandé.

## Feature Graphic

- `feature_graphic_1024x500.png` : fichier final 1024 × 500, sans canal alpha, prêt pour l’emplacement Feature Graphic.
- `feature_graphic_background_source.png` : fond abstrait généré utilisé pour la composition. Le logo et les textes du fichier final ont été composés séparément afin de conserver leur fidélité.

## Textes

- `texts/fr.txt` : titre, description courte, description longue, nouveautés, catégorie, tags et contacts en français.
- `texts/en.txt` : mêmes éléments en anglais.

Copier chaque section dans le champ correspondant de la fiche principale Google Play. Vérifier les longueurs dans la console, qui reste l’autorité finale.

## Vidéo Accessibility API

- `accessibility_video_script.txt` : script chronométré, plans, voix et éléments à montrer ou éviter.

La vidéo doit être filmée sur un Galaxy S25 réel, téléversée en mode non répertorié, puis fournie dans la déclaration Accessibility API. Elle doit montrer la divulgation préalable, le consentement Android, l’overlay, le grossissement et la fonction Lire.

## Captures téléphone réelles

Les dix PNG du dossier `screenshots/` proviennent exclusivement du Galaxy S25 SM-S931B connecté par ADB. Ils sont en français, non recadrés et conservent la définition native 1080 × 2340. Aucun mockup, texte injecté ou écran artificiel n’a été utilisé.

La sélection recommandée pour la fiche Play Store est :

1. `01_accueil.png` — accueil/tableau de bord ;
2. `02_barre_flottante_chrome.png` — panneau `TYPE_ACCESSIBILITY_OVERLAY` dans Chrome ;
3. `03_grossissement.png` — grossissement Android actif à 2× et commandes +/− ;
4. `04_profils.png` — profils ;
5. `05_reglages_visuels.png` — réglages optiques ;
6. `06_lecteur_adapte.png` — lecteur adapté ;
7. `07_tests_visuels.png` — tests visuels ;
8. `08_regles_automatiques.png` — règles automatiques.

`09_comparaison.png` et `10_accessibility_disclosure.png` sont conservées comme captures complémentaires et preuves de conformité. Voir `screenshots/README.md` pour la traçabilité.

## Import Play Console

1. Fiche Play Store principale : icône 512, Feature Graphic, textes et captures.
2. Contenu de l’application : politique de confidentialité, Data Safety, public cible, annonces, classification et accès à l’application.
3. Déclaration Accessibility API : description fidèle, vidéo non répertoriée et instructions d’activation.
4. Test interne : importer l’AAB signé approprié, ajouter les testeurs et vérifier que le `versionCode` n’a jamais été utilisé.

Voir `CHECKLIST.md` avant tout import.
