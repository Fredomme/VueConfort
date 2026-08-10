# VueConfort 1.0.0 — assistance visuelle Android

Application Android personnelle destinée aux personnes qui ont du mal à lire sur écran mais n’ont pas — ou pas encore — de lunettes.

## Positionnement

VueConfort ne corrige pas la vue et ne remplace pas des lunettes. L’application recherche des réglages d’affichage plus confortables : taille, graisse, interlignage, fond et contraste.

## Fonctions de la version 1.0.0

- Questionnaire basé sur les gênes quotidiennes
- Test comparatif en sept étapes
- Choix « aucune différence / je ne sais pas »
- Profil adapté aux réponses et au ressenti initial
- Sauvegarde locale permanente du profil
- Écran d’ajustement manuel avec aperçu en direct
- Mode lecture
- Accès aux réglages Samsung utiles
- Aucune connexion réseau, aucun compte et aucun transfert de données
- Loupe inter-applications fondée sur le grossissement natif Android, après activation manuelle du service d’accessibilité
- Lecture locale avec le profil visuel, sans capture de l’écran

### Terminologie de la loupe

La **Loupe VueConfort** désigne la fonction Production qui pilote le grossissement natif Android en mode fenêtre avec `MagnificationController`. Un **prototype expérimental de loupe par capture**, distinct et réservé au variant Debug, est conservé uniquement pour la R&D. La Release n’embarque pas son code de capture.

## Ouvrir le projet

1. Installer Android Studio sur le Mac.
2. Décompresser l’archive.
3. Ouvrir le dossier `VueConfort-S25`.
4. Laisser Gradle synchroniser le projet.
5. Activer les options développeur et le débogage USB sur le Galaxy S25.
6. Brancher le téléphone puis lancer l’application.

## Limites actuelles

- Le build Release doit encore être validé physiquement sur Galaxy S25 et Galaxy A53.
- Android ne fournit pas d’accès direct public à tous les sous-menus Samsung.
- Le mode lecture accepte pour l’instant du texte collé ou saisi manuellement.
- La loupe agrandit via Android ; elle n’applique pas globalement netteté, gamma, température ou contraste.
- La Release n’utilise aucune capture d’écran, MediaProjection ou enregistrement d’écran.
- Le fonctionnement physique doit encore être validé sur Galaxy S25 et la version de One UI installée.

## Publication

Les procédures de signature, conformité et publication se trouvent dans `docs/`.
La version et son code sont centralisés dans `gradle.properties`.
