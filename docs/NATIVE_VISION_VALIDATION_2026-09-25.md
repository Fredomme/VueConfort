# Native Vision — rapport de validation du 25 septembre 2026

**Point de recette historique du 25 septembre 2026.** À ce point de contrôle, l’incrément est codé et compilé. La voie commerciale sans privilège et le refus Lab sans permission ont été exercés sur S25. La commande publique de grossissement est en cours de qualification ; la nouvelle recette Relumino privilégiée n’a pas encore été exécutée, faute d’accord explicite reçu à ce stade pour cette attribution de permission. Les attentes et références Git ci-dessous décrivent ce point de contrôle, sans présumer des autorisations ou validations ultérieures. Ce rapport ne déclare donc pas l’ensemble de la mission validé.

Ce document traite les 25 livrables demandés. Les reçus cités par leur nom sont conservés dans le dossier local de preuves de l’incrément. Les tableaux complets de réglages du téléphone ne sont pas recopiés dans Git. Les compteurs ci-dessous proviennent des reçus et sorties d’instrumentation, pas seulement du résumé « OK » d’Android, qui inclut les tests ignorés.

## 1. Architecture finale de l’incrément

Le profil existant alimente `NativeVisionController`, les observations runtime et le resolver. Une commande va au contrôleur Android public, au guidage Samsung commercial, ou au Lab séparé si son accès est qualifié. Chaque branche produit un résultat explicite ; aucun curseur n’est assimilé arbitrairement à une fonction OEM. Le moteur de l’égaliseur reste propre à l’aperçu. Voir [architecture Native Vision](NATIVE_VISION.md).

## 2. Capacités détectées sur le S25

Appareil contrôlé : Samsung SM-S931B, Android 16/API 36, incrément S931BXXSCCZH1. Le reçu `Commercial-capabilities.json` est produit par l’APK Aperçu sous son UID ordinaire, sans permission de développement. Toutes les fonctions ci-dessous sont identifiées comme présentes sur ce firmware.

| Capacité | Lecture par l’APK lors du premier contrôle | État commercial observé | Automatisation dans cet incrément |
|---|---|---|---|
| Grossissement | Non : service Aperçu non connecté | Autorisation utilisateur nécessaire | API publique conditionnelle ; qualification réelle en cours |
| Relumino | Oui ; trois clés absentes, défauts du firmware attesté distingués des valeurs stockées | Action utilisateur | Lab seulement, si autorisé |
| Extra Dim | Non : accès refusé | Action utilisateur | Aucune, même en Lab |
| Filtre coloré | Oui, pour les lignes consultées | Action utilisateur | Aucune qualifiée |
| Correction des couleurs | Oui, pour les lignes consultées | Action utilisateur | Aucune qualifiée |
| Inversion | Oui | Action utilisateur | Aucune qualifiée |
| Contraste des polices | Oui | Action utilisateur | Aucune qualifiée |
| Confort visuel Samsung | Oui, activation consultée | Action utilisateur | Aucune qualifiée |
| Luminosité système | Oui | Action utilisateur | Aucune ajoutée |
| Taille de police | Oui | Action utilisateur | Aucune ajoutée |
| Zoom écran | Non dans ce modèle | Action utilisateur | Aucune ajoutée |

Une ligne lisible ne garantit ni une valeur effective complète ni un droit d’écriture. Les champs absents ne sont pas remplacés par des défauts inventés. La présence des fonctions OEM ne s’étend pas automatiquement à d’autres téléphones Samsung.

## 3. Niveau d’accès

Trois niveaux opérationnels restent distincts : API publique avec service volontairement activé pour le grossissement ; action utilisateur dans les réglages pour les fonctions protégées ; commande Relumino de laboratoire sous permission de développement explicitement accordée. La version commerciale ne demande ni ADB, ni Shizuku, ni root, ni `WRITE_SECURE_SETTINGS`, ni `WRITE_SETTINGS`.

La preuve Extra Dim antérieure provenait du shell ADB. Sa généralisation à l’APK était incorrecte : le contrôle de lecture du fournisseur refuse ses clés à l’APK cible. Ce refus est maintenant explicite et Extra Dim reste guidé dans les deux variantes.

## 4. Paramètres automatisables commercialement

Le grossissement Android est la seule commande système automatique ajoutée : facteur, mode, centre et activation, via le service existant. Elle exige Android 14+, service connecté et état entièrement restaurable. Elle ne requiert aucun accès de développement. La qualification S25 de cette nouvelle session n’est pas terminée à ce point du rapport.

Les réglages de l’égaliseur continuent d’agir dans l’aperçu VueConfort ; ils ne sont pas annoncés comme un traitement de toutes les applications.

## 5. Paramètres nécessitant une action utilisateur

Relumino, Extra Dim, filtre coloré, correction, inversion, contraste des polices, confort Samsung, luminosité, taille de police et zoom écran sont guidés. Pour Relumino, la navigation publique ouvre réellement `Settings$AccessibilitySettingsActivity`, puis indique **Améliorations pour la vision → Contour Relumino**. Une confirmation par relecture ou par l’utilisateur conserve sa provenance.

Configurer manuellement une fois ne confère pas ensuite un droit de pilotage automatique. Une nouvelle valeur protégée demandée nécessite une nouvelle intervention dans Samsung.

## 6. Paramètres Lab uniquement

Seules les trois commandes Relumino sont qualifiées par l’attestation : activation 0/1 ; cinq épaisseurs 1.0, 2.0, 3.0, 4.0, 4.99 ; type adaptatif, noir, blanc ou vert. Elles restent subordonnées à une permission réellement accordée et à des paramètres relus.

Les mappings typés supplémentaires ne signifient pas qu’ils sont accessibles : le resolver ne permet pas leurs écritures. Aucune nouvelle commande Relumino du binaire du 25 septembre n’est comptabilisée comme exécutée à ce stade.

## 7. CapabilityResolver

Le resolver pur examine appareil, Android, firmware connu, observations, permissions et contexte. Il distingue disponibilité publique, permission standard, action utilisateur, laboratoire, indisponibilité, appareil incompatible et refus. La version One UI reste inconnue hors attestation. Les capacités sont réévaluées au retour et avant commande ; une marque seule ou une simple page Accessibilité ne suffit pas.

## 8. AndroidNativeVisionAdapter

Le pont ajouté au service historique lit et utilise son `MagnificationController`. Les anciennes commandes de la loupe n’ont pas été remplacées. La nouvelle session refuse les états incomplets et vérifie les valeurs après commande. Le journal conserve activation, mode, facteur et centre ; une modification extérieure empêche sa restauration forcée.

## 9. SamsungNativeVisionAdapter

L’adaptateur commercial ne contient que lecture et navigation. Ses lectures scalaires distinguent valeur présente, absence, accès refusé et erreur. Les activités sont résolues et contrôlées avant lancement : système, exportation, activation et permission. Le raccourci Relumino protégé n’est pas utilisé. Les setters Lab sont absents de la factory commerciale.

## 10. SamsungNativeVisionLabAdapter

Le code Lab applique une liste fermée de paramètres, journalise avant écriture, recontrôle permission et génération de demande puis relit. Il est compilé dans le paquet distinct `fr.vueconfort.app.lab`. Son test sans permission retourne `NEEDS_USER_PERMISSION` sans changer les clés Relumino. Les essais avec permission, retrait en cours de session, séquence complète et journal sur appareil restent à qualifier.

## 11. Modifications du profil

`EqualizerProfile` passe au schéma 2 ; `NativeVisionProfile` et les capacités ont leur version indépendante 1. Demandes, recommandations, résultats, capacités et politique de désactivation sont conservés dans le profil personnel existant. L’écriture atomique protège chaque sous-ensemble contre un brouillon périmé. Une confirmation d’une ancienne révision n’est pas présentée comme actuelle.

Le champ sérialisable `restorationReferences` prépare une évolution : **il reste vide dans le parcours de production actuel**. Les restaurations opérationnelles utilisent leurs journaux privés ; le test de stockage qui injecte une référence synthétique ne prouve pas une alimentation automatique de ce champ.

## 12. UX commerciale

L’accueil donne accès à **Mon affichage**. Les contours Samsung ont leurs propres commandes ; les états indiquent demande, action requise, configuration vérifiée ou confirmation humaine. Un réglage protégé n’est jamais annoncé `APPLIED_AUTO`. La configuration du grossissement explique aussi le lecteur et les règles du service avant d’ouvrir Android.

Le retour depuis les réglages revalide les capacités et valeurs. La navigation réelle depuis l’accueil, son retour et la conservation des demandes ont réussi sans capture d’écran.

## 13. UX Lab

Le paquet Lab est explicitement identifié. Les cinq positions et quatre types Relumino sont raccordés à des demandes sérialisées. La file fusionne les mouvements encore en attente, et une désactivation invalide les commandes antérieures. Conserver/restaurer sont deux choix distincts. L’interface Lab est codée ; son automatisation privilégiée et sa fluidité sur dalle ne sont pas déclarées validées par les tests sans permission.

## 14. Tests unitaires

`Tests-unitaires.json` rapporte **108 tests Aperçu et 121 tests Lab réussis**, sans échec ni test ignoré. Les suites se recouvrent : 71 tests antérieurs, 37 tests communs supplémentaires, puis 13 tests de transaction Lab. Il ne s’agit donc pas de 229 cas indépendants.

Ils couvrent notamment accès, firmware, bornes, profils et migration, révisions et confirmations, clé absente, valeur présente, permission retirée, concurrence, sauvegarde préalable et reprise de transaction. Les transactions unitaires utilisent des ports simulés ; ce succès ne vaut pas preuve de restauration Secure sur le S25.

## 15. Tests instrumentés

| Exécution | Résultat réellement retenu |
|---|---|
| Suite commerciale initiale, 22 cas | **21 réussites + 1 ignoré** : service Aperçu non connecté pour le grossissement |
| Suite d’interface commerciale, 4 cas | **4 réussites** : navigation et trois contrôles d’interaction de l’égaliseur |
| Lab sans permission, 1 cas | **1 réussite**, après correction de la fixture pour distinguer Extra Dim illisible d’une clé absente |
| Nouvelle qualification du grossissement public | **En cours** ; le premier nouvel appel isolé est encore ignoré, pas validé |
| Lab avec permission | **Non exécuté** à ce stade |

Sources : `tests-S25-commercial.txt`, `tests-S25-commercial-interface.txt`, `tests-S25-lab-sans-permission.txt`, `tests-S25-grossissement-public.txt`. L’`AssumptionViolatedException` et le code d’instrumentation `-4` sont traités comme un test ignoré, même si Android termine avec « OK ».

## 16. Recette S25 commerciale

`Commercial-permissions.json` confirme une APK sans permission Secure ni passerelle Lab. `Commercial-read-only.json` confirme paramètres et empreinte du profil inchangés pendant demandes et vérifications guidées. `Commercial-navigation.json` valide la route publique ; `native-vision-commercial-ui-navigation.json` prouve ensuite l’ouverture réelle depuis l’accueil, le retour et la revalidation sans changer Relumino.

La suite comprend aussi la persistance/migration du profil, le bilan et les imports synthétiques. Les tests ne parcourent ni ne capturent le contenu personnel du téléphone.

## 17. Recette S25 Lab

Le reçu `native-vision-lab-without-permission.json` constate une permission absente, le refus contrôlé et les trois clés Relumino demeurées absentes. Les lectures Extra Dim sont `UNREADABLE`, pas « null donc absent ».

L’attribution explicite de la permission du nouveau paquet Lab est encore en attente de réponse. Il n’existe donc ici aucune preuve nouvelle de la séquence OFF → cinq crans → types → restauration, de commandes rapides privilégiées ou de retrait de permission après application. **Aucun effet de ce nouveau binaire n’a été observé par l’utilisateur le 25 septembre à ce stade.** L’observation du 24 septembre reste une preuve historique du banc précédent.

## 18. Preuves de restauration

Prouvé sur appareil : absence de modifications protégées pendant les recettes commerciales et refus Lab sans permission. Prouvé en tests unitaires : journal préalable, restauration de valeurs et d’absences, refus des conflits et conservation des journaux en erreur.

Non encore prouvé sur appareil dans cette recette : restauration de la nouvelle session de grossissement, journal Relumino après vraie écriture par le Lab, fermeture/réouverture avec transaction privilégiée. Les reçus manquants ne sont pas remplacés par des affirmations. Les états finaux du téléphone et des permissions doivent être joints lors de la clôture.

## 19. Non-régression de la loupe

Le service historique n’a reçu qu’un pont vers le contrôleur déjà utilisé ; ses commandes, profils et moteur restent en place. Les tests de stockage conservent ses réglages lors des mises à jour natives. Cela ne constitue pas encore une recette visuelle complète de tous ses gestes, modes, redémarrages et états de centre.

La qualification réelle du grossissement public est en cours. Le paquet historique n’est pas remplacé par l’installation d’Aperçu ou Lab. L’état initial externe, conservé localement dans `Etat-avant-installation.json` et `Magnification-avant-service.txt`, sert à la vérification finale sans publier son inventaire complet dans Git.

## 20. Non-régression de l’égaliseur

Les cinq curseurs modifient leur brouillon attendu ; trois scènes conservent préférences et géométrie. Maintenir Original puis relâcher conserve le brouillon et le cadrage. Remise au neutre et Annuler restent distincts et préservent profil enregistré et bilan. Reçus : `native-vision-equalizer-semantics-controls.json`, `native-vision-equalizer-semantics-original.json` et sortie de la suite interface.

Cette recette du 25 septembre n’effectue aucune nouvelle comparaison de pixels ni mesure prolongée de cadence. Les preuves de pixels antérieures restent datées de leur propre binaire ; elles ne sont pas recomptées ici.

## 21. Analyse Google Play

Les documents [Accessibilité](ACCESSIBILITY_DECLARATION.md), [Data Safety](DATA_SAFETY.md), [confidentialité FR](PRIVACY_POLICY_FR.md), [confidentialité EN](PRIVACY_POLICY_EN.md) et [préparation Play](PLAY_STORE_READINESS.md) sont mis à jour avec sources officielles. `isAccessibilityTool` reste absent : déclaration, information et consentement doivent correspondre au parcours final. Le bilan et les tests visuels requièrent aussi la déclaration Santé appropriée malgré leur traitement local.

Aucune approbation Google, classification médicale, publication de politique Web ou commercialisabilité acquise n’est annoncée. La compilation ne vaut pas validation Play.

## 22. Fichiers modifiés

L’incrément comprend :

- Build/manifeste : `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/lab/AndroidManifest.xml`, factory dans `app/src/nativeCommercial` et `app/src/lab`.
- Modèle/profil : `NativeVisionModels.kt`, `NativeVisionCodec.kt`, `NativeVisionCapabilityResolver.kt`, `NativeVisionReceiptReconciliation.kt`, `EqualizerProfile.kt`, `VisualProfileRepository.kt`.
- Plateforme : `AndroidNativeVisionAdapter.kt`, `NativeMagnificationSession.kt`, `SamsungNativeVisionAdapter.kt`, `NativeVisionEnvironment.kt`, `NativeVisionLabGateway.kt`, pont de `ScreenMagnifierService.kt`.
- Lab : `SamsungNativeVisionLabAdapter.kt`, `LabSettingsTransaction.kt`, `AndroidLabSettingsStore.kt`.
- UX : `NativeVisionController.kt`, `NativeVisionScreen.kt`, `HomeScreen.kt`, `AppRoute.kt`, `VueConfortNavigation.kt`, intégration dans `EqualizerScreen.kt` et protection de suppression dans `EqualizerViewModel.kt`.
- Tests : modèle, résolution, réconciliation, politique plateforme, transaction Lab, stockage/migration, `NativeVisionCommercialDeviceTest`, `NativeVisionNavigationDeviceTest`, `EqualizerSemanticsDeviceTest`, fixture et recettes Lab.
- Documentation : ce rapport, `NATIVE_VISION.md` et les cinq documents de préparation/confidentialité cités au point 21.

Les noms courts de classes Native Vision correspondent à `app/src/main/java/fr/vueconfort/app/nativevision` sauf mention Lab. Les changements de `gradlew.bat` existaient avant cette mission : ils sont exclus de l’incrément et ne doivent pas être inclus dans son commit.

## 23. Commits déjà créés

| Commit | Objet |
|---|---|
| `1beca0f` | Modèles, capacités et persistance Native Vision versionnés |
| `0833e16` | Guidage public séparé des commandes Samsung Lab restaurables |
| `4af1c5f` | Contrôles d’affichage, états vérifiés et invalidation des commandes périmées |

Les commits de preuves/tests instrumentés/documentation, et leurs éventuelles corrections finales, sont à ajouter à cette liste lors de la clôture. Aucun numéro de commit futur n’est inventé.

## 24. État Git au point de contrôle

Branche : `codex/commercial-bilan-import`, HEAD `4af1c5f`. Les tests instrumentés supplémentaires et les documents sont encore en cours d’intégration à cet instant. Le changement préexistant de `gradlew.bat` reste séparé. Ce point de contrôle ne déclare ni push final réussi, ni PR fusionnée, ni binaire publié. L’intégrateur complétera l’état final après ses actions effectives sur la branche existante.

## 25. Limites et travail restant

Terminer la qualification publique du grossissement et la recette Lab explicitement autorisée, puis obtenir les preuves de restauration et le constat utilisateur réellement daté. Le champ `restorationReferences` reste réservé ; les journaux effectifs existent indépendamment. Extra Dim et les autres commandes protégées restent guidés.

Avant commercialisation : compléter essais multi-OEM, autres firmwares, grandes polices, TalkBack, veille/redémarrage et accessibilité des parcours ; vérifier la signature et l’AAB final ; terminer les traductions ; publier séparément les politiques mises à jour ; finaliser les déclarations et la vidéo Play. Aucun moteur de correction optique globale, aucune commande publique Relumino universelle et aucune preuve clinique ne sont fournis.

**Réponse pour un client normal :** VueConfort peut proposer son aperçu et guider les réglages Samsung sans privilège. Il peut commander le grossissement par l’API officielle après activation volontaire de son service, sous les conditions de lecture/restauration de cet incrément. Relumino demeure une configuration manuelle commerciale ; sa commande automatique reste réservée au laboratoire autorisé. La mission complète ne peut être déclarée validée tant que les étapes de recette encore indiquées ci-dessus ne sont pas terminées.

## Reproduire les contrôles sans capture

Construction et tests locaux, en deux invocations séparées :

```sh
./gradlew --offline --no-daemon -PcommercialPreviewTests :app:testPreviewUnitTest :app:assemblePreview :app:assemblePreviewAndroidTest
./gradlew --offline --no-daemon -PnativeLabTests :app:testLabUnitTest :app:assembleLab :app:assembleLabAndroidTest
```

Après installation explicite des APK d’essai correspondants, sélectionner les classes de test, sans lancer globalement les anciens tests de pixels :

```sh
adb shell am instrument -w -e class fr.vueconfort.app.nativevision.NativeVisionCommercialDeviceTest,fr.vueconfort.app.data.EqualizerStorageDeviceTest,fr.vueconfort.app.data.PrescriptionStorageDeviceTest,fr.vueconfort.app.prescription.LocalDocumentReaderDeviceTest fr.vueconfort.app.preview.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'fr.vueconfort.app.nativevision.NativeVisionNavigationDeviceTest,fr.vueconfort.app.equalizer.EqualizerSemanticsDeviceTest,fr.vueconfort.app.equalizer.EqualizerInteractionDeviceTest#resetAndCancelAreDistinctDraftActionsAndKeepTheSavedProfileAndBilan' fr.vueconfort.app.preview.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class fr.vueconfort.app.nativevision.NativeVisionLabPermissionDeviceTest fr.vueconfort.app.lab.test/androidx.test.runner.AndroidJUnitRunner
```

La dernière commande suppose la permission Lab absente. Ces commandes ne l’accordent pas et ne lancent pas les recettes protégées. Le test de grossissement annonce explicitement son impossibilité d’exécution si son service ou sa restauration ne sont pas disponibles. Les recettes Lab protégées sont à lancer seulement après autorisation et établissement d’un état initial restaurable.
