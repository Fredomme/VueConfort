# Audit final — VueConfort

Date de l'audit : 30 juillet 2026  
Dépôt audité : `/Users/ribeirofrederic/Desktop/VueConfort-S25`  
Périmètre : fichiers actifs uniquement. Les fichiers `*.backup-*` et dossiers `.vueconfort-backup-*` ont été exclus.

## Verdict

Le projet compile et produit un APK debug. Il s'agit d'une application Compose mono-module avec questionnaire, réglage rapide, calibration comparative, profil persistant et un `AccessibilityService` qui crée des commandes flottantes et pilote le grossissement natif Android. Ce n'est pas une application caméra et elle ne capture actuellement aucun pixel de l'écran.

L'assistance au-dessus des autres applications est réelle, mais limitée au grossissement système : l'overlay ne contient que les commandes. Netteté, gamma, contraste, température et désaturation ne sont pas appliqués globalement. Les champs correspondants sont, selon le cas, des préférences ou des simulations dans l'interface de calibration. Une transformation globale des pixels nécessiterait `MediaProjection`, avec consentement explicite, service de premier plan et notification persistante ; cette architecture n'existe pas et n'est pas nécessaire au grossissement actuel.

## 1. Architecture actuelle réelle

- Projet Android mono-module `:app`, Kotlin 2.1.20, AGP 8.9.1, Gradle 9.3, Java 17, `minSdk 28`, `targetSdk 36`.
- Interface Jetpack Compose/Material 3 pilotée par Navigation Compose.
- État global : `VueConfortViewModel` (`AndroidViewModel`) et `VisualProfileRepository`.
- Persistance locale : Preferences DataStore `vueconfort_settings`.
- Domaine : `VisualProfile`, `UserVisualContext`, `ReadingAssessment`.
- Calibration : moteur Kotlin pur, session et `CalibrationViewModel`.
- Recommandation : moteur Kotlin pur, mais non raccordé au parcours réel.
- Assistance inter-applications : `ScreenMagnifierService : AccessibilityService`, vues Android classiques ajoutées au `WindowManager`.
- Aucune couche réseau, base distante, caméra, MediaProjection ou moteur GPU.

## 2. Fonctionnalités effectivement présentes

- Accueil et navigation vers les parcours.
- Réglage rapide de taille, graisse, espacement et fond.
- Questionnaire en 11 étapes, mais ses réponses sont seulement renvoyées au callback.
- Calibration comparative de huit paramètres avec score de confiance.
- Persistance DataStore d'un profil et d'un contexte utilisateur.
- Bouton flottant et panneau déplaçables en `TYPE_ACCESSIBILITY_OVERLAY`.
- Activation/désactivation du grossissement natif Android, mode fenêtre sur Android 13+.
- APK debug installable généré.

## 3. Fonctionnalités partielles

- Questionnaire : UI complète, mais réponses non sauvegardées et `RecommendationEngine` non appelé.
- Calibration : `CONTRAST` et `BACKGROUND` existent dans l'énumération, mais aucun essai n'est créé pour eux.
- Chaleur/désaturation : valeurs calibrées, mais l'aperçu ne leur applique aucun traitement visuel.
- Profil : persistant, mais écran « Mon profil » absent.
- Lecture optimisée : route présente, écran réel absent.
- Paramètres : route présente, écran réel absent ; aucun guidage pour activer le service.
- Zoom local : préférence présente, mais ne démarre ni ne configure le service.
- Luminosité : persistée, jamais appliquée.
- Export JSON annoncé dans le README, absent du code.

## 4. Fonctionnalités absentes

- Netteté, gamma et contraste réglables.
- Moteur visuel GPU (OpenGL/Vulkan/AGSL/RenderEffect).
- Capture d'écran en temps réel.
- Gestion de profils multiples.
- Service de premier plan, canal/notification et action d'arrêt.
- Gestion explicite de rotation, verrouillage, arrière-plan et révocation.
- Tests unitaires, instrumentés, UI et tests sur appareil.
- Mesures de fréquence d'image, température, mémoire ou batterie.
- Validation physique Galaxy S25/One UI.

## 5. Compilation et lint initiaux

Commandes exécutées avant modification :

- `./gradlew clean --stacktrace` : succès.
- `./gradlew assembleDebug --stacktrace --warning-mode all` : succès, APK d'environ 26 Mio.
- `./gradlew testDebugUnitTest --stacktrace` : succès technique mais `NO-SOURCE`; zéro test exécuté.
- `./gradlew lintDebug --stacktrace` : succès, 0 erreur et 9 avertissements.

Constats :

- Aucune erreur de compilation.
- API `MagnificationController.setScale(float, boolean)` dépréciée sur la branche Android 12 et antérieure.
- DSL `kotlinOptions` déprécié.
- Avertissements internes AGP incompatibles à terme avec Gradle 10.
- Lint : icône d'application absente, trois chaînes non externalisées et cinq dépendances plus anciennes que les versions stables proposées.

## 6. Tests existants et manquants

Aucun fichier n'existe sous `app/src/test` ou `app/src/androidTest`. Manquent au minimum :

- tests de `CalibrationEngine`, score de confiance et bornes ;
- tests de `RecommendationEngine` ;
- tests de calcul `ReadingAssessment`;
- tests de persistance DataStore ;
- tests du cycle du `CalibrationViewModel` ;
- tests UI Compose et navigation ;
- tests instrumentés d'activation/désactivation du service et de retrait de l'overlay ;
- matrice appareil : rotation, écran éteint/allumé, verrouillage, multi-fenêtre, changement de densité, révocation du service, redémarrage et One UI.

## 7. Permissions Android

- `BIND_ACCESSIBILITY_SERVICE` apparaît sur la déclaration du service. C'est une permission de signature accordée par Android lors de l'activation manuelle du service, pas une permission runtime demandable par l'application.
- `SYSTEM_ALERT_WINDOW` : absent.
- `CAMERA`, stockage, réseau, micro et notifications : absents.
- Le service est actuellement `android:exported="true"` mais protégé par `BIND_ACCESSIBILITY_SERVICE`.

## 8. Fonctionnement réel de l'overlay

À la connexion du service, `onServiceConnected()` obtient le `WindowManager` et ajoute un bouton « VC ». Un clic affiche un panneau. Les deux fenêtres utilisent `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_NO_LIMITS` et un format translucide. Le glisser-déposer modifie leurs coordonnées.

L'overlay n'affiche pas une copie agrandie de l'écran. Le panneau appelle le contrôleur de grossissement Android. Sur Android 13+, il demande `MAGNIFICATION_MODE_WINDOW`, un facteur 1x–8x et le centre de l'écran. La zone grossie est donc produite par Android, pas par VueConfort.

`TYPE_APPLICATION_OVERLAY` : absent. `SYSTEM_ALERT_WINDOW` : absent et inutile pour l'architecture actuelle.

## 9. MediaProjection et capture

`MediaProjection`, `VirtualDisplay` et `ImageReader` : absents. Aucun écran de consentement, jeton de projection, surface, frame, encodeur, fichier ou transmission n'existe. Il n'y a donc ni enregistrement ni capture de l'écran dans la version auditée.

`FLAG_SECURE` n'est ni inspecté ni contourné. Le grossissement système et Android déterminent ce qui peut être rendu. Si MediaProjection était ajoutée, les surfaces sécurisées seraient masquées par le système et doivent le rester.

## 10. AccessibilityService

Le service demande `canControlMagnification="true"` et `canRetrieveWindowContent="false"`. `onAccessibilityEvent()` ne lit rien. La configuration demande pourtant `typeAllMask` et `flagReportViewIds`, capacités excessives pour un service qui n'utilise aucun événement ni contenu de fenêtre ; elles doivent être réduites.

## 11. Cycle de vie réel du service

- Création des vues dans `onServiceConnected`.
- Aucun travail périodique, thread, coroutine ou capture.
- `onInterrupt` vide.
- `onDestroy` retire les vues puis désactive le grossissement.
- Pas de traitement dédié de `onUnbind`, changement de configuration, écran éteint, verrouillage, faible mémoire ou arrêt forcé.
- Le service est géré par le système d'accessibilité, pas démarré comme service standard ou premier plan.

La persistance d'un service d'accessibilité ne requiert pas de notification permanente. Un futur pipeline MediaProjection, lui, requerrait un service de premier plan de type `mediaProjection` et une notification visible.

## 12. Mémoire, chauffe et batterie

Le risque initial est faible : deux petites vues, aucun flux d'images et aucun traitement continu. Les références de vues sont annulées dans `onDestroy`. Risques résiduels :

- `addView` n'est pas protégé contre une exception de fenêtre ;
- aucune réaction à la révocation/déconnexion hormis le cycle système ;
- le grossissement pourrait rester dans un état inattendu si le processus est tué sans callback ;
- l'absence de capture implique 0 FPS applicatif et aucun coût GPU continu propre à VueConfort.

Une capture 60/120 FPS avec filtres augmenterait fortement GPU, mémoire, chauffe et batterie. Il faudrait viser une cadence adaptative, éviter les copies bitmap, fermer chaque image, libérer `VirtualDisplay`, `ImageReader`, surfaces et projection dans tous les chemins d'arrêt.

## 13. Limites Android et Samsung One UI

- L'API publique de grossissement dépend de l'activation manuelle du service dans Réglages > Accessibilité.
- Un seul service peut entrer en conflit avec le contrôleur de grossissement ou les gestes configurés par l'utilisateur.
- Le support effectif du mode fenêtre, sa géométrie et son apparence sont contrôlés par Android/One UI.
- Android ne permet pas à une application ordinaire d'imposer globalement gamma, température, netteté ou contraste aux autres applications.
- Les modes écran Samsung, Extra dim, Eye Comfort Shield, économie d'énergie et taux adaptatif peuvent modifier le résultat perçu.
- Rotation, écran verrouillé, DeX, multi-fenêtre, 120 Hz et mises à jour One UI exigent des essais physiques.
- « Fonctionne sur Galaxy S25 » n'est pas prouvé sans installation et essais instrumentés sur cet appareil.

## 14. Confidentialité

État favorable : aucune permission réseau/caméra/micro/stockage, aucune capture et DataStore local. Risques :

- les réponses utilisateur (âge, symptômes, lunettes, migraines) sont sensibles même si non qualifiées médicalement ;
- `allowBackup="true"` peut inclure les préférences dans les sauvegardes Android ;
- le texte de description doit expliquer précisément que le service ne lit pas les fenêtres ;
- toute MediaProjection future élargirait radicalement le risque et nécessiterait consentement, indicateur permanent, traitement en mémoire seulement, zéro journalisation et zéro transmission.

## 15. Matrice fonctionnelle

| Fonctionnalité | Présente | Fonctionnelle | Testée | Limitation | Action nécessaire |
|---|---:|---:|---:|---|---|
| Build APK debug | Oui | Oui | Hôte seulement | Pas d'appareil | Installer sur S25 |
| Questionnaire | Oui | Partielle | Non | Réponses perdues | Sauvegarder et recommander |
| Réglage rapide | Oui | Oui dans l'app | Non | N'agit pas globalement | Tests et libellés |
| Calibration | Oui | Partielle | Non | 8/10 paramètres, filtres simulés | Compléter et tester |
| Profil DataStore | Oui | Oui | Non | Un profil, backup autorisé | Tests, écran profil |
| Lecture optimisée | Route | Non | Non | Placeholder | Écran réel |
| AccessibilityService | Oui | Oui au niveau code | Non sur appareil | Activation manuelle | Écran de statut/test |
| TYPE_ACCESSIBILITY_OVERLAY | Oui | Oui au niveau code | Non sur appareil | Commandes seulement | Robustesse cycle |
| Grossissement système | Oui | Probable | Non sur S25 | Dépend d'Android/One UI | Test physique |
| SYSTEM_ALERT_WINDOW | Non | Sans objet | Sans objet | Inutile ici | Ne pas ajouter |
| TYPE_APPLICATION_OVERLAY | Non | Sans objet | Sans objet | Inutile ici | Ne pas ajouter |
| MediaProjection | Non | Non | Non | Consentement/FGS requis | N'ajouter que si indispensable |
| VirtualDisplay/ImageReader | Non | Non | Non | Aucun pipeline pixel | Idem |
| Service premier plan | Non | Sans objet actuellement | Non | Obligatoire pour projection | Ajouter seulement avec projection |
| Notification persistante | Non | Sans objet actuellement | Non | Obligatoire avec FGS | Idem |
| Netteté | Non | Non | Non | Impossible globalement sans pixels | Moteur local ou projection |
| Contraste/gamma/température | Modèle partiel | Non globalement | Non | Valeurs non rendues | Moteur local documenté |
| GPU | Non | Non | Non | Aucun filtre | Profilage avant ajout |
| Rotation/verrouillage/reprise | Non explicite | Non prouvé | Non | Dépend du système | Tests et callbacks |
| Révocation permission | Non explicite | Non prouvé | Non | Service coupé par Android | Nettoyage `onUnbind` |
| Libération ressources | Partielle | Oui dans `onDestroy` | Non | Pas `onUnbind` | Centraliser nettoyage |
| FLAG_SECURE | Respect système | Oui par absence de capture | Non | Non vérifié sur appareil | Test de non-capture |
| Absence d'enregistrement | Oui | Oui par inspection | Inspection code | À revalider à chaque évolution | Test/architecture sans I/O |
| Galaxy S25 | Ciblé | Non prouvé | Non | Aucun essai physique | Procédure ADB + matrice |

## 16. Fichiers à modifier ou créer

Liste exhaustive prévue pour rendre le prototype cohérent et testable sans introduire de capture :

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/vueconfort_accessibility_service.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/fr/vueconfort/app/magnifier/ScreenMagnifierService.kt`
- `app/src/main/java/fr/vueconfort/app/navigation/VueConfortNavigation.kt`
- `app/src/main/java/fr/vueconfort/app/ui/screens/HomeScreen.kt`
- nouveaux écrans Lecture, Profil et Paramètres
- `app/src/main/java/fr/vueconfort/app/recommendation/RecommendationEngine.kt`
- `app/src/main/java/fr/vueconfort/app/model/VisualProfile.kt`
- tests Kotlin sous `app/src/test/...`
- `README.md`
- `CODEX_IMPLEMENTATION_REPORT.md`

Les fichiers de sauvegarde ne seront pas modifiés.

## 17. Plan priorisé

1. Stabiliser Gradle et ajouter une vraie base de tests.
2. Réduire les capacités d'accessibilité au strict nécessaire et désactiver la sauvegarde des données sensibles.
3. Durcir le retrait des vues et la remise à zéro du grossissement à la déconnexion.
4. Fournir un écran Paramètres qui vérifie l'état du service et ouvre le bon réglage Android.
5. Conserver l'architecture sans MediaProjection tant qu'un traitement global des pixels n'est pas une exigence acceptant ses coûts.
6. Implémenter un moteur visuel local pour l'écran Lecture avec paramètres honnêtement limités à VueConfort.
7. Raccorder questionnaire, recommandation, profil et calibration.
8. Remplacer les placeholders et clarifier l'interface.
9. Documenter confidentialité, absence de capture et limites non médicales.
10. Ajouter tests unitaires/UI, puis exécuter une matrice physique Galaxy S25 (rotation, verrouillage, arrière-plan, révocation, 60/120 Hz et batterie).
