# Impact Google Play

La Release actuelle reste hors périmètre : pas de composant MediaProjection, pas de permission `FOREGROUND_SERVICE_MEDIA_PROJECTION`, feature flag désactivée. L’AAB préparé pour le test interne ne doit pas être remplacé par ce prototype.

Une intégration future nécessiterait une validation explicite, la déclaration du type de service de premier plan mediaProjection dans Play Console, une vidéo de démonstration, une politique de confidentialité mise à jour, une explication du consentement et une nouvelle vérification Data Safety. L’AccessibilityService demeure destiné à l’assistance visuelle et à l’hébergement de l’overlay; il ne fournit pas l’image capturée.
