# Résultats Galaxy S25

Test du 4 août 2026 sur SM-S931B, Android 16, 1080×2340, VirtualDisplay à 75 %.

- shader OES compilé par le pilote Adreno et premier frame rendu ;
- Chrome partagé après consentement système explicite ;
- 120 frames environ toutes les 1,01 s pendant cinq défilements, soit ~118 fps actifs ;
- 157 591 Ko PSS, dont 68 536 Ko Graphics après le test ;
- aucun crash, `Bitmap`, fichier image produit par le moteur ou transfert réseau ;
- `FLAG_SECURE` exclut l’overlay des captures ADB et prévient la récursion.

Le rendu transformé est techniquement actif. Aucun observateur humain n’a encore confirmé une amélioration de lisibilité; halos, confort sur 2–5 minutes, latence précise, charge CPU/GPU active, température et batterie restent à mesurer. Le verdict produit demeure donc expérimental.
