# Prototype expérimental de loupe par capture — Debug uniquement

Ce prototype R&D est distinct de la **Loupe VueConfort** officielle, laquelle utilise le grossissement natif Android avec `MagnificationController` et reste une fonction essentielle de Production.

Le prototype par capture a été validé le 4 août 2026 sur Galaxy S25 SM-S931B sous Android 16. Son code, ses ressources et sa capacité `canTakeScreenshot` sont réservés au variant Debug `fr.vueconfort.app.debug` et sont absents de l’AAB Release.

## Fonctionnement

- capture publique `AccessibilityService.takeScreenshot()` ;
- conversion du `HardwareBuffer` en bitmap mémoire, puis fermeture immédiate du buffer ;
- source fixe centrée dans la moitié inférieure ;
- affichage dans un `TYPE_ACCESSIBILITY_OVERLAY` couvrant la moitié supérieure ;
- zoom borné de 1,5× à 4×, dernier niveau conservé localement ;
- cadence prudente de 2 images/seconde ;
- pause, reprise et fermeture explicites ;
- arrêt préalable de la Loupe VueConfort officielle pendant l’essai du prototype ;
- retour au grossissement natif Android depuis le sélecteur du panneau Debug.

Aucune image n’est écrite dans `cacheDir`, `filesDir`, MediaStore ou un autre stockage. Il n’existe ni MediaProjection, VirtualDisplay, ImageReader, réseau, OCR ou télémétrie.

## Mesures Galaxy S25

Test continu de 303 076 ms : 600 images, 1,98 image/seconde, latence moyenne 63 ms, aucune erreur. Température batterie observée entre 29,3 °C et 29,8 °C. PSS observé entre environ 188 et 219 Mo, dont 78 à 133 Mo attribués aux ressources graphiques système ; cette empreinte élevée interdit de considérer le prototype comme prêt pour la production.

## Limites

- rendu actualisé, pas une vidéo fluide ;
- mode source fixe uniquement : le suivi tactile absorberait les gestes destinés à l’application sous-jacente ;
- les erreurs d’intervalle provoquent un recul automatique de 800 ms ;
- toute autre erreur suspend le rafraîchissement ;
- `FLAG_SECURE` n’est jamais contourné : l’erreur système dédiée et les zones presque entièrement noires affichent un message de contenu protégé ;
- la commande de rotation ADB n’a pas provoqué de rotation visible sur ce S25, donc la rotation réelle reste à valider manuellement ;
- la fluidité et la mémoire restent insuffisantes pour une activation dans le variant Release.

Les PNG de `captures/` proviennent du S25 réel et sont séparés des ressources de la fiche Google Play.
