# Orchestration additive de VueConfort

Le profil existant reste la source des choix. Aucun moteur historique n’est remplacé ou réimporté dans cette branche. Le natif est un backend supplémentaire ; aucun accès `WRITE_SECURE_SETTINGS` ni transport Display-Lens n’est ajouté.

```text
bilan facultatif + calibration enregistrée + égaliseur + contexte
  → VisionProfileSnapshot (vue du profil existant, sans stockage concurrent)
  → VisionOrchestrator + registre VisionEngine
  → VisionRenderPlan (demandes, décisions, transports, refus et replis)
  → contrôleurs / rendu existants
  → VisionApplicationReceipt → VisionObservedState
```

## Contrats et raccordements

`VisionEngine` décrit identité, version, catégorie, maturité, preuves, contraintes et entrées. Son évaluation est pure. Les paramètres optiques restent propres à chaque moteur via `OpticalEngineInput` ; ils ne sont pas convertis en réglages Samsung.

`VisionRenderPlan` conserve la révision du profil, la révision propre à chaque demande, le contexte, les candidats refusés, les transformations applicables et l’ordre de dispatch. Cet ordre ne prétend pas contrôler le compositeur Android/Samsung. `VisionTransport` distingue aperçu interne, Android natif, Samsung natif et futur Display-Lens. Le calcul d’un moteur ne dépend d’aucun objet MediaProjection ou SurfaceFlinger.

| Adaptateur | Chemin existant réutilisé | Niveau et portée |
|---|---|---|
| Android public | `NativeVisionCapabilityResolver`, `NativeMagnificationSession`, `AndroidNativeVisionAdapter`, contrôleur de la loupe | Prototype, contrôles publics et relecture ; aucune certification commerciale |
| Samsung guidé | `SamsungNativeVisionAdapter` | Prototype ; réglages système, sans écriture protégée |
| Réglages Android guidés | Lecture/guidage existants des autres capacités | Prototype ; aucune assimilation à une fonction Samsung |
| Aperçu perceptif | `renderRecord`, `toOpticalSettings`, `EqualizerPreview` | Exécution appareil documentée ; uniquement l’aperçu interne |
| Recherche optique / Vision / GPU | Catalogue de signatures et références existantes | Expérimental et inaccessible depuis cette APK |
| Display-Lens | Contrat de transport futur | Indisponible, aucune implémentation |

Le catalogue référence V2, V2.1, V2.2, V2.3, V2.4, D4/D5, Vision pixels, Vision optique et GPU. V3 et D1–D3 restent les sources des contrats D4/D5. Les chemins `work/`, `outputs/` et `Desktop/` du catalogue désignent le patrimoine local existant : ce ne sont pas des bibliothèques chargées par l’application, ni des fichiers nouvellement publiés.

Les preuves distinguent présence du code, compilation, tests unitaires, tests numériques, instrumentation, exécution appareil, effet physique, bénéfice perceptif et usage commercial. `NUMERICAL_TESTED` décrit l’existence d’un essai, jamais son succès universel : le résultat défavorable V2.1 est conservé explicitement. Aucun moteur n’est promu `COMMERCIAL_VALIDATED` dans cet incrément.

## Profil et vérité de l’application

- Aucun nouveau schéma persistant : les profils versions 1/2 et leur migration Native Vision sont conservés.
- Bilan OD/OG confirmé, préférences, résultats calculés et résultats appliqués restent distincts. Les valeurs cliniques proviennent exclusivement du bilan confirmé.
- Une référence de bilan contradictoire est signalée ; les choix perceptifs restent utilisables sans bilan.
- Les résultats de calibration exposent leurs sources et conditions réellement enregistrées. L’incertitude quantitative manquante reste inconnue. Une préférence, une distance de questionnaire ou un score personnel n’est pas une mesure optique.
- Les demandes natives et l’aperçu gardent leurs révisions indépendantes. La façade conserve les objets de calibration existants et leurs détails, sans lancer de nouveau calcul.
- Un reçu sauvegardé ne prouve jamais une activation actuelle. L’interface relit les réglages accessibles, à l’affichage et environ toutes les deux secondes tant que l’écran est actif.
- L’aperçu exige son callback après dessin. Son reçu est invalidé au changement de surface, au retour d’arrière-plan, lors du refus du plan et pendant Original.
- Le contrat exige une confirmation adaptée au transport, un contexte et une demande identiques, ainsi qu’un reçu de moins de trente secondes. Une confirmation déclarative n’est pas une preuve technique.

## Arbitrage et replis

L’arbitrage rejette d’abord les transformations d’une autre identité, les révisions périmées, les routes indisponibles et les maturités non étayées. Il départage ensuite selon accès réel, maturité, préférence explicite, qualité attendue, coût et identifiant stable. Deux moteurs ne sont composés que si leurs politiques réciproques et leurs domaines le permettent.

Les domaines de couleur, luminance, épaisseur et géométrie évitent les doubles traitements non qualifiés. Les effets natifs actuellement observés peuvent réserver un domaine et suspendre le traitement correspondant de l’aperçu ; ce refus ne prétend pas éteindre une aide activée ailleurs. Les commandes de désactivation restent possibles sans conflit.

| Situation | Repli explicite |
|---|---|
| Autorisation Android absente | Demander l’action utilisateur, aucun effet annoncé |
| Samsung protégé | Guidage vers les réglages ; lecture si possible |
| Capacité absente / moteur refusé | Identité et raison conservée |
| Précompensation optique indisponible | Identité, jamais Relumino ou netteté présentés comme correction optique |
| Effets de pixels indisponibles avant Android 13 | Demande de secours distincte pour taille/épaisseur ; préférences complètes conservées |
| Combinaison non qualifiée | Refus explicite, aucune activation automatique de remplacement |

Le contrôleur Native Vision commercial consulte le plan avant ses commandes. Sa barrière d’annulation et son journal de restauration restent responsables des effets. Le dispatch Lab préexistant est conservé séparément ; il n’est ni qualifié ni exécuté par cette mission.

## Vérification reproductible

```sh
./gradlew -PcommercialPreviewTests :app:testReleaseUnitTest :app:lintRelease :app:assemblePreview :app:assemblePreviewAndroidTest
```

Les suites `VisionOrchestratorTest`, `VisionProfileSnapshotTest`, `VisionEngineAdaptersTest`, `VisionEngineCatalogTest` et `VisionRuntimeMatchingTest` vérifient décision, identité, absence de bilan, migration, refus, composition et preuves périmées. `VisionOrchestrationDeviceTest` vérifie le raccord réel à l’interface et au rendu sans enregistrer ses demandes synthétiques.

Une activité de recette existe uniquement dans `src/preview`. Elle utilise le processus normal de l’application pour éviter le redémarrage du service par instrumentation, sauvegarde l’état réel, passe par le plan puis le pont public et restaure dans un bloc non annulable. Elle ne fait pas partie de Release. Les commandes historiques de la loupe et les moteurs scientifiques restent inchangés.
