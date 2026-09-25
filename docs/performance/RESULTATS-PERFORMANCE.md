# Mesure finale sur S25 — égaliseur VueConfort Aperçu

La manipulation continue a tenu pendant **180,915 secondes**, sans crash ni blocage du parcours. La cible d’environ 60 changements présentés par seconde est étayée pour Netteté, Contraste, Confort lumineux, Épaisseur du texte et Intensité. **Elle reste non validée pour Taille** : environ 50 changements par seconde sont prouvés dans ce protocole, avec davantage de premières frames perdues. Aucune nouvelle optimisation n’a été ajoutée après ce constat.

## Couverture et latence réelle

Le parcours comporte 215 gestes, 9 030 événements de déplacement injectés et 8 723 commandes observées dans les callbacks. Tous ont produit un premier dessin. La trace finale contient **8 723 / 8 723 commandes**, sur 233,831 secondes de données et 135 499 938 octets, sous son plafond de 1 GiB. Les runs précédents tronqués ne servent pas à certifier cette couverture.

La présentation est établie par l’ancêtre exact Choreographer du dessin, la SurfaceFrame de l’application et la DisplayFrame SurfaceFlinger liée. Les snapshots MONOTONIC alignent le vrai début du callback avec l’horloge de trace. La latence du capteur tactile et de distribution des événements avant le callback n’est pas incluse ; il s’agit d’une preuve système, sans photodiode.

**8 425 présentations sont prouvées, soit 96,58 % des commandes.** Il reste 298 commandes dont la présentation n’est pas prouvée : 297 premières frames explicitement `Dropped Frame` (269 pour Taille et 28 pour les changements d’exemple), et une commande Taille exclue par prudence après désaccord de tokens. Aucune SurfaceFrame n’est absente. Les frames prouvées sont toutes distinctes côté application et affichage.

Sur ces 8 425 présentations, la latence callback → présentation est **P50 23,64 ms ; P95 32,30 ms ; P99 40,35 ms ; maximum 58,45 ms**. Six cas prouvés dépassent 50 ms. Ce percentile conditionnel ne valide pas une limite de 50 ms pour chacune des commandes, et les 298 cas non prouvés ne sont pas remplacés par une latence inventée.

| Réglage | Présentations prouvées / commandes | P50 ms | P95 ms | P99 ms | Changements prouvés/s pendant les gestes | Premières frames perdues |
|---|---:|---:|---:|---:|---:|---:|
| Taille | 1370 / 1640 | 23,46 | 26,51 | 32,12 | 50,02 | 269 |
| Netteté | 1600 / 1600 | 23,60 | 32,21 | 37,61 | 60,26 | 0 |
| Contraste | 1600 / 1600 | 23,55 | 32,19 | 37,77 | 60,23 | 0 |
| Confort lumineux | 1600 / 1600 | 23,68 | 32,30 | 37,72 | 60,22 | 0 |
| Épaisseur du texte | 560 / 560 | 23,89 | 40,39 | 45,87 | 60,28 | 0 |
| Intensité | 1600 / 1600 | 23,71 | 32,33 | 38,26 | 60,18 | 0 |

Les callbacks effectifs tournent entre 60,18 et 60,28 par seconde pendant les gestes. La cadence de présentation du tableau compte les changements distincts prouvés sur ces mêmes plages actives ; les pauses et changements d’exemple n’augmentent pas artificiellement le résultat. Pour Taille, l’écart est explicite : **60,18 callbacks/s mais 50,02 changements présentés/s prouvés**, soit 1 370 preuves sur 1 640 commandes (83,54 %).

Les trois exemples sont Lecture, Détails et Image. Les transitions d’exemple ont seulement 13 présentations prouvées sur 41 (28 premières frames perdues) ; leur P95 conditionnel est 58,45 ms et n’est pas représentatif des transitions non prouvées. Le maintien d’Original a 41/41 preuves à l’appui (P95 31,39 ms) et 41/41 au relâchement (P95 44,11 ms).

Le protocole marque seulement le **premier dessin** de chaque état. Si sa frame est perdue, cet état peut être repris dans une frame ultérieure, mais ce transport n’est pas mesuré ici. « Non prouvé » ne signifie donc pas « jamais affiché ». La baisse de preuve pour Taille doit être levée avant de revendiquer une cadence générale de 60 états affichés/s.

## Stabilité, mémoire et limites

Le P95 callback → dessin est de 6,13 ms ; il est distinct de la présentation. Il passe de 7,35 ms pendant la première minute à 4,85 ms pendant la dernière minute complète. Le P95 conditionnel de présentation reste proche de 32,2–32,4 ms dans les trois minutes. Aucune dégradation progressive de ces indicateurs n’est observée pendant ce seul parcours.

| Mesure du processus instrumenté | Départ | Fin | Maximum échantillonné |
|---|---:|---:|---:|
| PSS | 159,49 MiB | 215,39 MiB | 254,67 MiB |
| Heap Java utilisé | 7,88 MiB | 11,59 MiB | 29,25 MiB |
| Heap natif alloué | 14,92 MiB | 81,18 MiB | 91,80 MiB |
| Mémoire graphique privée rapportée | 81,68 MiB | 61,01 MiB | 81,68 MiB |

Les allocations ART cumulées atteignent **3 565,38 MiB, soit 19,67 MiB/s**. Ce volume est de l’allocation cumulée, pas de la mémoire conservée. Le processus a effectué 150 GC ; leur temps cumulé est 2,469 s et ne mesure pas seulement les pauses de l’interface. La dernière minute présente un PSS compris entre 215,39 et 254,67 MiB et un heap natif entre 76,32 et 91,80 MiB. La montée initiale, les oscillations et le coût d’allocation restent visibles ; ces trois minutes ne prouvent pas l’absence de fuite à long terme.

Ces chiffres incluent l’interface Compose, le renderer, l’accessibilité/injection et l’observateur. Leur coût n’est pas attribuable précisément au seul renderer et aucun profil mémoire GPU dédié n’a été réalisé. Le téléphone rapporte un mode nominal d’environ 120 Hz au début ; ce mode ne vaut pas promesse de débit du contenu. Il n’y a ni certification thermique prolongée, ni généralisation à d’autres appareils.

## Diagnostics et reproductibilité

Le rapprochement FrameMetrics par timestamp est **diagnostique uniquement** : 104 tokens connus diffèrent du token d’ancêtre exact pendant ce run, essentiellement sous Buffer Stuffing Android 16. Les latences applicatives issues de ce rapprochement ne sont jamais utilisées comme temps de présentation. FrameMetrics compte 17 860 frames de fenêtre, dont 771 hors de leur deadline système ; ce ne sont pas 771 pertes physiques d’affichage. Aucun rapport FrameMetrics ni identifiant de commande n’a débordé les tableaux de collecte.

FrameTimeline classe 7 127 premières frames de commande `Late Present`, 1 299 `On-time Present` et 297 `Dropped Frame`. Parmi les DisplayFrames liées aux commandes, 2 005 sont `Late Present`. Ces catégories concernent les échéances propres au pipeline Android ; leurs détails et combinaisons de jank restent dans le JSON, séparés des percentiles et des pertes.

La trace rapporte trois `traced_chunks_discarded` de sévérité informative et aucune statistique non nulle de sévérité erreur/perte de données. Les 8 723 commandes, dessins et SurfaceFrames attendus ont néanmoins tous été retrouvés ; la capture n’est pas qualifiée globalement de « sans perte » au-delà de cette couverture vérifiée.

Les preuves agrégées sont dans [analysis-final.json](analysis-final.json), le protocole dans [README.md](README.md), et les empreintes dans [evidence-manifest.json](evidence-manifest.json). Les traces brutes et les CSV de commandes restent locaux et ne sont pas ajoutés au dépôt. La non-régression fonctionnelle de la loupe historique et la restauration de ses données/services sont consignées dans la recette principale ; aucune latence comparative de loupe n’est déduite de ce test.
