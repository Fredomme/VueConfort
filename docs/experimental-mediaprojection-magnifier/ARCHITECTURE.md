# Architecture

## Avant

La loupe native pilote `MagnificationController`. Le mode semi-statique Debug utilise `takeScreenshot()`, un `HardwareBuffer`, puis un bitmap recadré et affiché dans un overlay.

## Après

`MediaProjection` → `VirtualDisplay` → `Surface` → `SurfaceTexture` externe OES → shader OpenGL ES 2 (cadrage et zoom) → surface EGL du `TextureView` → `TYPE_ACCESSIBILITY_OVERLAY`.

Aucun `ImageReader`, `Bitmap`, `Canvas` logiciel, `glReadPixels` ou copie de pixels CPU n’est présent dans la boucle. La source est la moitié inférieure; la destination est la fenêtre supérieure. Les coordonnées de texture, le zoom et le déplacement de source sont appliqués par le shader. Le service enregistre `MediaProjection.Callback` avant la création du `VirtualDisplay` et libère display, surfaces, textures, programme EGL et projection à l’arrêt.

Le variant Debug porte seul l’activité de consentement, le service mediaProjection, les permissions FGS et la feature flag. La Release les exclut au manifest merge.
