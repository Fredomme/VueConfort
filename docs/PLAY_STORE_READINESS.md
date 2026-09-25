# VueConfort — préparation Google Play

Révision du 25 septembre 2026. Cette branche prépare l’intégration produit 1.1.0 ; elle ne constitue ni une publication ni une approbation Google Play. Les statuts de pistes de la console n’ont pas été déduits de documents historiques.

## Binaire et expérience à évaluer

- Release : `fr.vueconfort.app`, version de travail `1.1.0`, code 4 ; référence dans `gradle.properties`. Le code doit encore être comparé aux envois déjà présents dans Play Console avant soumission.
- L’AAB préparé est **non signé**, faute de configuration locale de la clé d’envoi. Il ne peut pas être soumis en l’état. Configurer la clé attendue dans Play Console, reconstruire et vérifier la signature fait partie des prérequis, distincts de la compilation et des essais.
- Aperçu : `fr.vueconfort.app.preview`, sources commerciales dans un paquet d’essai distinct.
- Lab : `fr.vueconfort.app.lab`, développement uniquement ; ne pas soumettre ni promouvoir cette variante comme expérience client normale.
- Android minimal 28, cible 36. La nouvelle orchestration du grossissement exige Android 14+, un service autorisé et un état restaurable ; la loupe historique conserve ses voies antérieures.
- Service d’accessibilité et tuile rapide protégés par leurs permissions de liaison ; notifications avec consentement Android 13+.
- Barre `TYPE_ACCESSIBILITY_OVERLAY`, sans `SYSTEM_ALERT_WINDOW`.
- Commercial : aucun `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, ADB, Shizuku ou root. Relumino et les autres réglages protégés restent guidés.
- Absence de capture d’applications, MediaProjection, OCR global, réseau, compte et publicité dans Release/Aperçu. L’OCR local d’un document volontairement importé reste distinct.
- Profil personnel unique, confirmations explicites, restauration locale et sauvegardes Android désactivées. Voir [architecture et limites](NATIVE_VISION.md).
- `verifyCommercialReleaseManifest` contrôle le manifeste fusionné, y compris les dépendances : paquet, permissions autorisées, composants propres à VueConfort, absence de mode Debug et de sauvegarde. Ce contrôle est une dépendance de Release/AAB et du lint Release ; il ne certifie ni la signature ni l’acceptation Play.

## Accessibility API

Le service conserve les commandes flottantes, le grossissement, les règles choisies et le lecteur textuel sur demande. `isAccessibilityTool` n’est pas déclaré : ne pas supposer une exemption. La [déclaration préparée](ACCESSIBILITY_DECLARATION.md) précise information, consentement, déclaration et vidéo selon la [règle officielle](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), consultée le 25 septembre 2026. La recette doit vérifier chaque entrée vers le service et le parcours sans activation.

## Bilan et fonctions liées à la santé

La règle Santé couvre aussi les fonctions secondaires et l’accès aux données de santé pour une autre fonction. Le bilan traité localement ne permet donc pas d’écarter ce périmètre. La politique publique doit être accessible, refléter les données traitées et figurer dans la console et l’application. Exclure les promesses trompeuses de correction. La classification du produit et, selon celle-ci, les mentions ou preuves requises restent à déterminer avant soumission. [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en-GB), consultée le 25 septembre 2026.

La déclaration Santé doit refléter les fonctions réellement présentes, même secondaires. Revoir les choix après évolution ; ne pas assimiler « aucune transmission réseau » à « aucune fonction de santé ». [Informations pour la déclaration Santé](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en), consultées le 25 septembre 2026.

Cette analyse ciblée n’est pas une conclusion juridique ni une validation réglementaire. Ces textes n’établissent pas un statut de dispositif médical ou une efficacité thérapeutique.

## Travail restant avant soumission

1. Consulter le rapport de l’intégration et [la recette produit](PRODUCT_RELEASE_1_1_0.md) pour les résultats du binaire exact ; distinguer les tests ignorés. La recette Lab ne remplace pas une recette client.
2. Valider activation, refus, retour des réglages, service désactivé, mise à jour système, modification extérieure et restauration après fermeture.
3. Vérifier migration, suppression du bilan et du profil, journaux en attente, redémarrage, rotation, grandes polices, TalkBack et veille One UI.
4. Compléter les essais hors Samsung et sur d’autres firmwares avant d’élargir les attestations.
5. Les textes Store FR/EN sont préparés pour 1.1.0. Actualiser les captures avec le parcours final ; les images de 1.0.x sont des preuves historiques. Vérifier les traductions de l’application avant de promettre une expérience anglaise complète.
6. Mettre en cohérence les formulaires Accessibility, Santé et Data Safety, le parcours d’information/consentement et la vidéo commerciale.
7. Publier séparément les politiques mises à jour aux URL prévues, puis vérifier leur accessibilité et les contacts. Modifier ces fichiers Git ne met pas le site à jour.
8. Contrôler l’AAB final, sa signature, son manifeste fusionné, ses dépendances, le niveau d’API cible et les exigences Play au moment de la soumission.

Aucune fusion de PR, publication Web ou mise en production Google Play n’est effectuée par cet incrément documentaire.
