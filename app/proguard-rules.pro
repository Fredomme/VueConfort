# Components instantiated by Android from the manifest.
-keep class fr.vueconfort.app.magnifier.ScreenMagnifierService { *; }
-keep class fr.vueconfort.app.core.VueConfortTileService { *; }
-keep class fr.vueconfort.app.core.CoreActionReceiver { *; }

# Keep generic signatures and annotations used by AndroidX and Kotlin metadata.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Compose and DataStore are supported by their published consumer rules. VueConfort
# does not use reflection-based model serialization, so broad model keeps are avoided.
