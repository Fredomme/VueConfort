# Ressources Google Play — VueConfort 1.0.0

Ce dossier contient les ressources préparées pour Google Play Console à partir du logo officiel fourni le 4 août 2026. Le développement applicatif n’a pas été modifié.

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

## Captures téléphone

Le dossier `screenshots/` est volontairement vide : aucun Galaxy S25 ni émulateur n’était connecté pendant la préparation, et aucune capture existante exploitable n’a été trouvée. Aucune fausse interface n’a été créée.

Créer en français, au format portrait 1080 × 1920 :

1. `01_accueil.png` — accueil/tableau de bord ;
2. `02_barre_flottante.png` — barre utilisée dans Chrome, sans donnée personnelle ;
3. `03_grossissement.png` — overlay et commandes +/− ;
4. `04_profils.png` — liste et paramètres des profils ;
5. `05_lecteur.png` — lecteur adapté ;
6. `06_tests_visuels.png` — écran principal des tests.

Procédure conseillée : connecter le Galaxy S25 par ADB, exécuter `adb exec-out screencap -p > fichier.png`, puis recadrer uniquement la barre système si nécessaire sans modifier l’interface. Vérifier chaque capture avant import : 1080 × 1920, français, aucun nom, e-mail, notification personnelle, URL privée ou autre donnée personnelle.

## Import Play Console

1. Fiche Play Store principale : icône 512, Feature Graphic, textes et captures.
2. Contenu de l’application : politique de confidentialité, Data Safety, public cible, annonces, classification et accès à l’application.
3. Déclaration Accessibility API : description fidèle, vidéo non répertoriée et instructions d’activation.
4. Test interne : importer l’AAB signé approprié, ajouter les testeurs et vérifier que le `versionCode` n’a jamais été utilisé.

Voir `CHECKLIST.md` avant tout import.
