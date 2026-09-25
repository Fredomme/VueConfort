# Déclaration Accessibility API — VueConfort

Révision du 25 septembre 2026. Base factuelle à aligner sur le binaire Release soumis ; aucune approbation Google Play n’est déclarée ici.

## Finalité et fonctions

VueConfort propose des aides de confort de lecture. Le service `ScreenMagnifierService` est visible et activé volontairement dans Android. Il conserve :

- les commandes flottantes et le lecteur avec `TYPE_ACCESSIBILITY_OVERLAY` ;
- le grossissement natif `MagnificationController`, sans reconstruction de la loupe ;
- la détection du package actif pour les règles de profil définies par l’utilisateur ;
- la lecture de `rootInActiveWindow` seulement après l’action **Lire**, pour présenter localement le texte et les descriptions accessibles exposés par l’application courante.

Le service écoute les événements de fenêtre, contenu et défilement. Native Vision réutilise son contrôleur pour le facteur, le centre, le mode et l’activation. Sa nouvelle orchestration exige Android 14+ et un état complet restaurable ; les commandes historiques restent présentes. Elle ne transforme pas le service en moyen d’écrire les réglages Samsung protégés.

## Limites et données

Les fonctions natives n’exigent aucune capture d’écran. Release et Aperçu n’embarquent ni MediaProjection, ni capture d’applications, ni OCR global. Le prototype historique de capture reste propre à Debug ; Lab Native Vision utilise le pont de loupe Release.

Le service ne clique, ne saisit et n’achète rien à la place de l’utilisateur. Les règles existantes sont déterministes et choisies par l’utilisateur. **Lire** peut traiter un texte personnel que l’application courante expose à l’accessibilité ; ce texte reste en mémoire du service, sans sauvegarde dans DataStore ni envoi réseau. VueConfort ne contourne pas les fenêtres protégées ni l’absence de texte accessible. Les packages des règles et les préférences sont locaux ; aucun serveur, publicité ou analytique d’écran n’est ajouté.

## Choix et explications

L’accueil explique les commandes, le grossissement, la détection de l’application active et la lecture sur demande avant l’ouverture des réglages Android. Un parcours sans activation reste possible. Mon affichage présente aussi l’usage du service avant son lien de configuration. La loupe peut être fermée et le service désactivé dans Android. Un consentement d’accessibilité ne donne pas à VueConfort un privilège Relumino : la voie commerciale utilise les écrans Samsung.

## Dossier Google Play

Le fichier de métadonnées actuel ne déclare pas `isAccessibilityTool`. Il faut préparer la déclaration correspondante, une information visible dans l’application et un consentement explicite ; la description système ou la politique seule ne suffit pas. La vidéo doit montrer le parcours d’information, acceptation/refus, activation et utilisation réelle. Tout changement d’usage doit être reflété dans la déclaration. L’éligibilité éventuelle comme outil d’accessibilité doit être justifiée par sa finalité et son public, pas supposée. [Règles officielles AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), consultées le 25 septembre 2026.

La recette doit vérifier chaque entrée vers Accessibilité, la possibilité de refuser et les textes FR/EN. Filmer la Release/Aperçu commerciale, jamais l’expérience Relumino privilégiée Lab comme démonstration du comportement client. Voir [Native Vision](NATIVE_VISION.md) et [préparation Play](PLAY_STORE_READINESS.md).
