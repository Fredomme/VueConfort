# VueConfort 1.0.1 — assistance visuelle Android

Application Android personnelle destinée aux personnes qui ont du mal à lire sur écran mais n’ont pas — ou pas encore — de lunettes.

## Projets du dépôt

- **VueConfort 1.0.1**, à la racine : application historique d’assistance visuelle décrite ci-dessous.
- **[VueConfort Vision 0.2](vision/README.md)**, dans `vision/` : application Android indépendante (`fr.vueconfort.vision`) avec loupe par capture, quatre molettes simultanées et essai de précompensation optique sur image fixe. Elle possède son propre projet Gradle et ne remplace pas la loupe historique.

Vision 0.2 reste expérimentale : ses calculs et ses pixels affichés ont été vérifiés, sans démonstration d’un bénéfice visuel humain ni d’un remplacement de lunettes. Voir son [bilan de validation](vision/docs/VALIDATION-0.2.md).

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

- Les documents historiques mentionnent une piste de test fermé Alpha pour 1.0.1 ; son état actuel n’a pas été vérifié dans la console. L’intégration 1.1.0/code 4 prépare un AAB non signé, sans publication. Voir [le dossier de livraison](docs/PRODUCT_RELEASE_1_1_0.md) pour les prérequis de signature et de soumission.
- Une validation physique a été effectuée sur Galaxy S25 ; la couverture Galaxy A53 et d’autres appareils reste à compléter.
- Android ne fournit pas d’accès direct public à tous les sous-menus Samsung.
- Le lecteur utilise localement le texte d’accessibilité explicitement demandé ; certaines applications n’exposent aucun texte exploitable.
- La loupe agrandit via Android ; elle n’applique pas globalement netteté, gamma, température ou contraste.
- La Release n’utilise aucune capture d’écran, MediaProjection ou enregistrement d’écran.
- Le verrouillage, la veille, la rotation et les restrictions d’arrière-plan One UI peuvent interrompre temporairement le grossissement ou l’overlay et nécessitent des essais continus.

## Stockage et suppression

Les préférences du client restent dans le DataStore local existant. Le profil personnel de l’Égaliseur contient les demandes et les états Native Vision ; le bilan confirmé et les résultats de calibration conservent leurs enregistrements distincts et leur provenance. L’orchestrateur en assemble une vue sans créer de stockage concurrent. Les parcours initiaux réutilisent ce profil ; une calibration de confort ne remplace jamais les réglages de l’Égaliseur déjà personnalisés.

Supprimer les bilans retire leurs valeurs et leur historique, invalide les références et les calculs associés, et conserve les préférences perceptives ainsi que la loupe. Réinitialiser les seuls profils de loupe ne modifie pas le profil personnel, les demandes natives ou le bilan. Supprimer le profil personnel seul conserve les réglages actuels du téléphone et son éventuel journal de restauration. L’effacement de toutes les données attend au contraire une restauration réussie des réglages pilotés par VueConfort ; si elle échoue ou reste en attente, l’effacement est refusé et le journal reste disponible. Les documents sources importés et le texte OCR ne sont pas persistés.

## Publication

Les procédures de signature, conformité et publication se trouvent dans `docs/`.
La version et son code sont centralisés dans `gradle.properties`.
