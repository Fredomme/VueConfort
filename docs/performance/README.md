# Égaliseur VueConfort — protocole réel et preuves de performance

Mesure de VueConfort Aperçu (`fr.vueconfort.app.preview`) sur Samsung S25 SM-S931B, Android 16/API 36. Le test utilise `ActivityScenario` et injecte de vrais événements Android dans les curseurs, avec une horloge réelle. Il n’utilise aucun `ComposeTestRule`, aucune écriture directe du ViewModel, aucun GC forcé. Les tableaux de mesure sont préalloués et les calculs/exports sont faits après la manipulation.

Le premier smoke utilisant l’horloge de test Compose a été invalidé : il bloquait les recompositions pendant l’injection. Il ne décrit pas les performances du produit. Le smoke réel de 10 secondes puis le parcours de 180 secondes ont bien été exécutés. La première capture lourde s’est arrêtée à 66,91 secondes au plafond de 256 MiB ; ses 3 068 commandes ne représentent que 35,17 % des 8 723 commandes du parcours. Le deuxième replay gfx/view s’est également arrêté au plafond de 512 MiB : 172,568 secondes de trace, 169,336 secondes de commandes et 8 222/8 723 commandes. La configuration finale fournie conserve uniquement view, les marqueurs de l’application et FrameTimeline, avec 1 GiB de marge. Les fichiers d’analyse indiquent eux-mêmes leur couverture effective ; le nom d’un fichier n’atteste jamais une capture complète. Le résultat final appartient au rapport de résultats séparé.

## Définition des mesures

- **Commande** : entrée dans le callback réel du contrôle, enregistrée avec `System.nanoTime`. La latence matérielle du capteur tactile et la distribution de l’événement avant ce callback sont exclues.
- **Dessin** : premier appel de dessin de l’aperçu pour cet identifiant, après `drawContent`. Ce n’est pas une preuve de présentation.
- **Présentation système** : ancêtre exact `Choreographer#doFrame <token>` contenant le marqueur `VC_EQ_DRAW`, puis SurfaceFrame de l’application portant ce token, puis DisplayFrame SurfaceFlinger liée. La fin de cette dernière donne l’instant de présentation rapporté par Android. Ce n’est pas une mesure photodiode du panneau.
- **Horloges** : les `clock_snapshot` MONOTONIC de la trace alignent le timestamp réel du callback sur l’horloge de trace. La conversion suit le snapshot précédent ; le rapport fournit la dispersion des offsets et le délai entre callback et marqueur. Aucun rapprochement approximatif par proximité temporelle de frames n’est utilisé.
- **FrameMetrics** : mesures diagnostiques uniquement. Sur Android 16, la récupération de Buffer Stuffing peut décaler l’horloge des callbacks Choreographer sans décaler de façon identique le VSYNC de FrameMetrics. L’ancien rapprochement exact par timestamp peut manquer **ou mal associer** des frames. Le rapport compare les tokens connus avec les ancêtres et exclut conservativement leurs désaccords des latences de présentation.

La requête exige un seul processus émetteur des marqueurs, une unique SurfaceFrame MainActivity, une unique DisplayFrame liée, un dessin situé à l’intérieur de la SurfaceFrame, des durées valides et un type réellement présenté. Le nom de processus peut rester `zygote64` après le fork Android : l’attribution utilise l’UPID unique des marqueurs et la couche MainActivity. Vérifier aussi les libellés de buffers contenant le package/PID. Les tokens ne sont pas interpolés.

Les percentiles P50/P95/P99 sont au rang le plus proche et **conditionnels aux présentations prouvées**. Le nombre total de commandes, la couverture, les premières frames explicitement perdues, les surfaces absentes et les désaccords de tokens les accompagnent toujours. Le premier dessin d’un état peut être perdu puis l’état transporté dans une frame ultérieure : l’instrumentation actuelle ne prouve pas cette propagation. Une présentation non prouvée ne signifie donc ni délai nul, ni état jamais affiché.

## Exécution reproductible

Préconditions : APK Aperçu et APK de test correspondants installés, téléphone déverrouillé et laissé disponible, sauvegarde de l’état utilisateur et de la liste des services existants effectuée par l’opérateur. La classe de test ne presse ni Enregistrer, ni Réinitialiser, ni Supprimer. Elle conserve puis restaure le seul booléen d’accueil qu’elle peut temporairement activer. Aucun autre package ne doit être nettoyé ou désinstallé.

Les commandes suivantes sont à lancer depuis ce dossier, en remplaçant `SERIAL` par le téléphone déjà autorisé. L’entrée standard est utilisée pour la configuration : la tentative de lecture de configuration depuis `/data/local/tmp` avait échoué sur ce téléphone.

```sh
adb -s SERIAL shell perfetto --txt --background-wait -c - -o /data/misc/perfetto-traces/vueconfort-equalizer-final.pftrace < equalizer-perfetto-final.pbtxt
adb -s SERIAL shell am instrument -w -r -e class fr.vueconfort.app.equalizer.EqualizerPerformanceDeviceTest -e equalizerDurationSeconds 180 -e equalizerTouchHz 60 fr.vueconfort.app.preview.test/androidx.test.runner.AndroidJUnitRunner
```

Vérifier le PID retourné et le succès du test. La trace dure 240 secondes pour inclure le démarrage et la fin des mesures ; attendre la terminaison de **cette** session avant de copier le fichier. Ne pas interrompre un autre Perfetto. La configuration finale conserve uniquement `view`, les marqueurs de l’application et FrameTimeline ; elle supprime `gfx`, les événements ordonnanceur/CPU/entrée et le polling des compteurs de processus. Choreographer utilise la catégorie `view` ; la source FrameTimeline ne dépend pas des événements `gfx`. Les descriptions de processus restent découvertes à la demande. Le plafond est de 1 GiB ; même avec cette marge, vérifier durée, IDs de début/fin et compteurs de pertes après chaque capture.

```sh
adb -s SERIAL pull /data/misc/perfetto-traces/vueconfort-equalizer-final.pftrace s25-equalizer-final.pftrace
adb -s SERIAL exec-out run-as fr.vueconfort.app.preview cat files/performance/equalizer-performance.json > s25-equalizer-final-app.json
TRACE_PROCESSOR=/chemin/trace_processor_shell python3 analyze_equalizer.py s25-equalizer-final-app.json s25-equalizer-final.pftrace analysis-final
```

L’analyse utilise Python ≥3.11 et Trace Processor v58.2. Le binaire macOS ARM64 utilisé a été vérifié contre le manifeste officiel : SHA-256 `d29864d1ba3b36855527bb1b0ca3aa7f703cdce338b9680bb922c5c151b358fa`. Aucun binaire ni trace ne doit être ajouté au dépôt. Le script accepte `TRACE_PROCESSOR`, ou un binaire voisin s’il existe déjà.

Sorties : `.json` contient les agrégats, contrôles, mémoire, couverture et empreintes des sources ; `.raw.csv` conserve les jointures SQL et la latence depuis le marqueur ; `.commands.csv` ajoute la latence depuis le vrai callback après synchronisation des horloges. Conserver traces et CSV détaillés localement, et publier uniquement le protocole, les scripts et les agrégats nécessaires à la revue.

## Cadence, mémoire et limites

Le parcours alterne Lecture/Détails/Image, manipule Taille/Netteté/Contraste/Confort/Intensité et Épaisseur du texte, puis maintient Original. Une cadence d’injection demandée de 60 Hz ne suffit pas à prouver 60 changements affichés par seconde. L’analyse isole les gestes complets (40 callbacks modifiant effectivement la valeur dans ce harnais), mesure leurs intervalles et compte les changements dont la présentation est prouvée sur les mêmes plages actives. Les groupes incomplets sont comptés séparément. Les lacunes de preuve restent visibles, surtout pour Taille et les changements de scène.

La mémoire est échantillonnée après échauffement, environ toutes les 10 secondes et à la fin. Allocations ART, heap Java/natif, PSS et graphique concernent **tout le processus instrumenté**, incluant Compose, le rendu, l’accessibilité/injection et l’observateur. Ils n’attribuent pas précisément le coût au renderer. Des gigaoctets alloués cumulativement ne sont pas des gigaoctets conservés en mémoire. Le pic est le maximum des échantillons, pas un pic continu ; `art.gc.gc-time` mesure l’activité GC cumulée, pas uniquement les pauses de l’interface. Trois minutes sans crash ne prouvent ni absence de fuite à long terme, ni comportement thermique soutenu, ni performances identiques sur d’autres écrans/appareils. Aucun profil mémoire GPU dédié n’a été capturé.

La loupe historique se vérifie séparément : activation, déplacement/zoom, pause/fermeture et restauration des services/données existants. Ses résultats fonctionnels et empreintes avant/après appartiennent à la recette principale. Les FrameMetrics de MainActivity de l’égaliseur ne mesurent pas la loupe Android rendue par d’autres composants. Il n’existe ici aucune comparaison chiffrée avant/après du débit ou de la latence de cette loupe.

Sources primaires : [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline), [CLI Perfetto](https://perfetto.dev/docs/reference/perfetto-cli), [synchronisation des horloges](https://perfetto.dev/docs/concepts/clock-sync), [ProcessStatsConfig](https://raw.githubusercontent.com/google/perfetto/main/protos/perfetto/config/process_stats/process_stats_config.proto). Le cas de décalage Choreographer est également documenté dans le SDK local Android 36.1, `android/view/Choreographer.java`, autour de `offsetFrameTimeNanos` et `mFrameInfo.setVsync`.
