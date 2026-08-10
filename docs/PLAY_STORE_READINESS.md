# VueConfort — préparation Google Play

## État technique

- Nom : VueConfort
- Package : `fr.vueconfort.app`
- Version : `1.0.1` (`versionCode` 2)
- Diffusion : piste Google Play de test fermé Alpha, réservée aux testeurs autorisés ; aucune publication Production
- SDK minimal : 28
- SDK cible : 36
- Langues : français par défaut et anglais selon la langue Android
- Permission runtime : `POST_NOTIFICATIONS` sur Android 13+
- Services protégés : service d’accessibilité avec `BIND_ACCESSIBILITY_SERVICE` et tuile rapide avec `BIND_QUICK_SETTINGS_TILE`
- Overlay : `TYPE_ACCESSIBILITY_OVERLAY`, sans permission `SYSTEM_ALERT_WINDOW`
- Loupe VueConfort : grossissement natif Android en mode fenêtre avec `MagnificationController`, fonction essentielle du variant Release
- Capture d’écran Release : aucune ; le prototype R&D utilisant `takeScreenshot` est physiquement limité au variant Debug ; pas de MediaProjection, VirtualDisplay, ImageReader ou OCR global
- Réseau, compte, publicité, analytique : absents
- Données : profils, réglages, calibrations, résultats/historique et règles stockés localement ; texte accessible utilisé temporairement à la demande
- Sauvegarde Android : désactivée dans le manifeste

## Service d’accessibilité

L’usage doit être déclaré précisément dans Play Console : commandes flottantes d’assistance, contrôle du grossissement Android, détection de l’application active pour les profils automatiques et extraction locale à la demande du texte accessible. Préparer une démonstration vidéo et des instructions de test. Ne pas présenter l’activation comme automatique ou obligatoire pour les écrans qui restent consultables sans elle.

## Éléments restant nécessaires

- Conserver l’icône officielle intégrée et vérifier ses masques sur les appareils ciblés.
- Maintenir les coordonnées de contact et l’URL publique de confidentialité à jour.
- Maintenir les textes FR/EN et les captures réelles cohérents avec le binaire diffusé.
- Revalider Data Safety, contenu, public cible, classification et déclaration Accessibility API avant toute future demande de Production.
- Vérifier les exigences de niveau d’API cible au moment de la publication.
- Poursuivre les essais réels sur Galaxy S25, Galaxy A53 et au moins un Android de référence, en français et anglais.

## Validation recommandée avant production

Tester premier lancement, refus/réactivation des notifications, retour des réglages d’accessibilité, redémarrage, rotation, mode sombre, grandes polices, TalkBack, veille One UI, tuile rapide, suppression sélective des données et migration depuis une installation existante. Contrôler également la fiche Play et la politique publiée contre le binaire release final.
