# Validation de l’égaliseur — Galaxy S25, 24 septembre 2026

Application testée : VueConfort Aperçu 1.0.2-preview, code 3, Android 16/API 36, SM-S931B.
Source applicative : `ff307a489790cc6700b4a701f7aa28e5a0ea77f5`.
APK SHA-256 : `c4a9b0c4c0a387359d0fa4cb436d1d2103e418913b15a25f6bfaec04b787cdbf`.
Les commits suivants ne portent que sur la documentation et l’analyse des mesures.

## Fonctionnement et neutralité

- 71 tests unitaires Release et 74 Debug réussis ; les suites se recouvrent, avec trois tests supplémentaires de géométrie de loupe en Debug.
- Lint Release : 0 erreur, 57 avertissements, 4 informations. APK Aperçu et instrumentation compilés.
- 22 tests instrumentés fonctionnels réussis sur le S25 : import, stockage isolé, identité des pixels, commandes réelles et comparaison Original.
- Tests PixelCopy : rendu neutre/désactivé/intensité nulle identique à la source ; Original conserve la géométrie et le zoom ; chaque contrôle raccordé modifie effectivement le rendu.
- Recette par touches Android : sauvegarde des six réglages, arrêt complet, réouverture et stockage strictement identique ; annulation sans écriture ; neutralisation du brouillon distincte de sa sauvegarde ; suppression et absence de retour au redémarrage. Entrées préexistantes conservées.
- Comparaison de captures : sur 676 992 pixels de l’aperçu, l’appui Original change 666 014 pixels ; au relâchement, zéro pixel diffère du traitement précédent. La preuve d’identité avec la source vient séparément des tests PixelCopy.
- Aucun bilan réel dans la recette manuelle : la conservation d’un bilan confirmé est testée avec des données synthétiques dans des fichiers privés isolés.
- Loupe Lab historique : activation 1,5×, augmentation 2×, déplacement, retour 1,5×, arrêt. Sauvegarde privée restaurée, empreintes APK/données et liste des services d’accessibilité identiques à l’état initial. Pas de mesure comparative de fréquence de la loupe.

Les trois scènes partagent les préférences. Le shader est conservé, les uniforms changent ; aucun moteur optique lourd n’est exécuté pendant les gestes. Le format versionné sépare préférences, référence au bilan confirmé, résultats calculés et reçu de rendu. Voir [architecture et paramètres](EQUALIZER_V1.md).

## Mesure prolongée

Le protocole exact, le script d’analyse et les règles de jointure se trouvent dans [performance/README.md](performance/README.md). Les agrégats publics excluent les sauvegardes privées et les traces système brutes.

Voir [les résultats détaillés](performance/RESULTATS-PERFORMANCE.md) et [les agrégats](performance/analysis-final.json).


## Portée et suites

Codé, compilé et testé automatiquement : écran central, six commandes, contrat de profil et neutralité. Exécuté sur le S25 : contrôles, comparaison, persistance, trois répétitions du scénario de performance de 180 secondes et recette de la loupe. Observé sur captures : interface et changements de pixels. Bénéfice perceptif individuel et remplacement des lunettes : non démontrés.

Aucune régression fonctionnelle détectée dans les scénarios exécutés. La cadence prouvée de Taille et le coût d’allocation du processus instrumenté restent à examiner ; les résultats ne qualifient ni tous les téléphones ni un usage prolongé de plusieurs heures.

Sont volontairement reportés : bandes spatiales, modèle optique individualisé, raccord scientifique du bilan, précompensation, affichage inter-applications, qualification commerciale et publication Google Play. V2.x, D4/D5 et la loupe historique n’ont pas été réécrits. La PR nº 2 doit rester en brouillon, sans fusion automatique.
