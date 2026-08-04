# Architecture

Flux Android final → consentement MediaProjection → VirtualDisplay → SurfaceTexture OES → shader GPU VueConfort → surface EGL → overlay sécurisé.

MediaProjection fournit uniquement les pixels finaux autorisés. VueConfort ne lit pas `AccessibilityNodeInfo` pour l’image, ne comprend pas l’application source et n’automatise aucune action. Le service d’accessibilité héberge l’overlay; il ne reconstruit pas le contenu.

Le moteur existant `OpticalRenderer` reste utilisé dans les composables internes. Le moteur expérimental OES reprend les mêmes familles de paramètres pour le flux global Debug, sans `Bitmap`, `ImageReader`, lecture CPU ou `glReadPixels`.
