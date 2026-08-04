# Confidentialité et sécurité

La fonction reçoit temporairement l’image finale affichée par Android après consentement explicite. Elle ne consulte pas `AccessibilityNodeInfo`, n’analyse pas l’application source, ne fait ni OCR ni reconnaissance d’image.

- aucun fichier, cache de frame, MediaStore ou base d’images ;
- aucun réseau, compte, analytics ou télémétrie ;
- aucun son demandé ou capturé ;
- aucune donnée de pixel dans les journaux ;
- overlay marqué `FLAG_SECURE` ;
- contenus DRM/`FLAG_SECURE` laissés protégés ;
- notification permanente et arrêt immédiat sur `MediaProjection.onStop()`.

Limite : Android peut restituer une zone protégée noire. Le prototype ne tente aucun contournement et doit basculer vers la loupe Android pour ce contenu.
