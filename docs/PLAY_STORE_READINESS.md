# VueConfort — préparation Google Play

## État technique

- Nom : VueConfort
- Package : `fr.vueconfort.app`
- Version : `0.1.0` (`versionCode` 1)
- SDK minimal : 28
- SDK cible : 36
- Langues : français par défaut et anglais selon la langue Android
- Permission runtime : `POST_NOTIFICATIONS` sur Android 13+
- Services protégés : service d’accessibilité avec `BIND_ACCESSIBILITY_SERVICE` et tuile rapide avec `BIND_QUICK_SETTINGS_TILE`
- Overlay : `TYPE_ACCESSIBILITY_OVERLAY`, sans permission `SYSTEM_ALERT_WINDOW`
- Capture d’écran : aucune ; pas de MediaProjection, VirtualDisplay, ImageReader ou OCR global
- Réseau, compte, publicité, analytique : absents
- Données : profils, réglages, calibrations, résultats/historique et règles stockés localement ; texte accessible utilisé temporairement à la demande
- Sauvegarde Android : désactivée dans le manifeste

## Service d’accessibilité

L’usage doit être déclaré précisément dans Play Console : commandes flottantes d’assistance, contrôle du grossissement Android, détection de l’application active pour les profils automatiques et extraction locale à la demande du texte accessible. Préparer une démonstration vidéo et des instructions de test. Ne pas présenter l’activation comme automatique ou obligatoire pour les écrans qui restent consultables sans elle.

## Éléments restant nécessaires

- Remplacer l’icône Android générique par une icône adaptative finale.
- Définir l’adresse de contact et l’URL publique de la politique de confidentialité.
- Préparer les textes de fiche en français et anglais.
- Préparer des captures téléphone montrant accueil, configuration, barre, profils, lecteur et confidentialité.
- Préparer l’icône Play 512 × 512 et la bannière 1024 × 500.
- Ouvrir/configurer le compte développeur et compléter les informations éditeur.
- Générer un AAB `release`, créer et protéger la clé de signature, puis configurer Play App Signing.
- Compléter les formulaires Data Safety, contenu de l’application, public cible et classification.
- Compléter la déclaration Accessibility API et fournir la justification et la vidéo demandées.
- Vérifier les exigences de niveau d’API cible au moment de la publication.
- Effectuer des essais réels sur Galaxy S25, Galaxy A53 et au moins un Android de référence, en français et anglais.

## Validation recommandée avant production

Tester premier lancement, refus/réactivation des notifications, retour des réglages d’accessibilité, redémarrage, rotation, mode sombre, grandes polices, TalkBack, veille One UI, tuile rapide, suppression sélective des données et migration depuis une installation existante. Contrôler également la fiche Play et la politique publiée contre le binaire release final.
