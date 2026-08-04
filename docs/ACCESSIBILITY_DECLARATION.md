# Déclaration Accessibility API — VueConfort

## Finalité

VueConfort est une application d’assistance visuelle destinée à améliorer temporairement le confort de lecture. Son service `ScreenMagnifierService` est une fonction principale, visible et déclenchée après activation volontaire dans les réglages Android.

## Fonctions réalisées

- afficher une barre et un lecteur au moyen de `TYPE_ACCESSIBILITY_OVERLAY`;
- contrôler le grossissement Android avec `MagnificationController`;
- détecter l’application active afin d’appliquer une règle de profil choisie par l’utilisateur;
- parcourir `rootInActiveWindow` uniquement lorsque l’utilisateur appuie sur **Lire**, afin d’afficher localement le texte et les descriptions accessibles exposés par l’application courante.

Le service écoute les changements de fenêtre, de contenu et de défilement. Il demande la capacité de récupérer le contenu de fenêtre et de contrôler le grossissement.

## Ce que le service ne fait pas

- aucune activation automatique du service;
- aucun clic, geste, saisie ou achat automatisé;
- aucune capture d’écran, MediaProjection, OCR global ou enregistrement;
- aucun contournement de `FLAG_SECURE` ou d’un écran protégé;
- aucune collecte de mot de passe, frappe, message, contact ou contenu d’application;
- aucun envoi réseau, serveur, publicité, analytique ou profilage commercial.

Le texte accessible est conservé seulement en mémoire pour le lecteur courant et n’est pas enregistré dans DataStore. Les noms de packages peuvent être comparés localement aux règles automatiques définies par l’utilisateur; ils ne sont pas transmis.

## Contrôle utilisateur

L’application explique l’usage pendant la configuration, ouvre la page officielle des réglages et vérifie l’état au retour. L’utilisateur peut réduire ou fermer les commandes, désactiver le service à tout moment, supprimer ses règles et désinstaller l’application.

## Éléments à fournir dans Play Console

Utiliser cette déclaration comme base factuelle, compléter le formulaire AccessibilityService API et joindre une vidéo montrant l’activation manuelle, la barre, le grossissement et l’action Lire. Le texte final doit rester cohérent avec le binaire soumis.
