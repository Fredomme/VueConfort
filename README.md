# VueConfort 1.0.1 — assistance visuelle Android

Application Android personnelle destinée aux personnes qui ont du mal à lire sur écran mais n’ont pas — ou pas encore — de lunettes.

## Positionnement

VueConfort ne corrige pas la vue et ne remplace pas des lunettes. L’application recherche des réglages d’affichage plus confortables : taille, graisse, interlignage, fond et contraste.

## Fonctions de la version 1.0.1

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

La **Loupe VueConfort** désigne la fonction Release qui pilote le grossissement natif Android en mode fenêtre avec `MagnificationController`. Un **prototype expérimental de loupe par capture**, distinct et réservé au variant Debug, est conservé uniquement pour la R&D. La Release n’embarque pas son code de capture.

## Ouvrir le projet

1. Installer Android Studio sur le Mac.
2. Décompresser l’archive.
3. Ouvrir le dossier `VueConfort-S25`.
4. Laisser Gradle synchroniser le projet.
5. Activer les options développeur et le débogage USB sur le Galaxy S25.
6. Brancher le téléphone puis lancer l’application.

## Limites actuelles

- La Release 1.0.1 est diffusée uniquement sur la piste Google Play de test fermé Alpha ; elle n’est pas disponible publiquement.
- Une validation physique a été effectuée sur Galaxy S25 ; la couverture Galaxy A53 et d’autres appareils reste à compléter.
- Android ne fournit pas d’accès direct public à tous les sous-menus Samsung.
- Le lecteur utilise localement le texte d’accessibilité explicitement demandé ; certaines applications n’exposent aucun texte exploitable.
- La loupe agrandit via Android ; elle n’applique pas globalement netteté, gamma, température ou contraste.
- La Release n’utilise aucune capture d’écran, MediaProjection ou enregistrement d’écran.
- Le verrouillage, la veille, la rotation et les restrictions d’arrière-plan One UI peuvent interrompre temporairement le grossissement ou l’overlay et nécessitent des essais continus.

## Publication

Les procédures de signature, conformité et publication se trouvent dans `docs/`.
La version et son code sont centralisés dans `gradle.properties`.
