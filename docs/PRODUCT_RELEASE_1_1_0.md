# VueConfort 1.1.0 — dossier de livraison

Préparation du 25 septembre 2026, sans publication. Le rapport final de l’intégration fait foi pour les résultats effectivement exécutés et les identifiants Git ; les étapes ci-dessous ne sont pas des attestations de réussite.

## Artefact commercial

- Package : `fr.vueconfort.app`, version `1.1.0`, code `4` dans `gradle.properties`.
- AAB : `app/build/outputs/bundle/release/app-release.aab` après `:app:bundleRelease`.
- Contrôle : `:app:verifyCommercialReleaseManifest`, également lancé par `bundleRelease`, `assembleRelease` et `lintRelease`. Il vérifie le manifeste fusionné et les composants exposés. Les moteurs numériques intégrés n’ajoutent ni écran expérimental ni permission.
- État actuel : aucune configuration locale `keystore.properties` ; l’AAB préparé est **non signé** et **ne peut pas être soumis en l’état**. La réussite de sa génération ne constitue pas une signature. Il faut configurer la clé d’envoi attendue par l’application Play puis reconstruire et vérifier l’AAB signé ; aucune clé n’est créée pour cette mission.
- Aperçu : `fr.vueconfort.app.preview`, signé pour les essais locaux, isolé de la loupe historique. Son activité de recette est absente de Release. Lab et Debug restent dans leurs sources séparées.
- Le manifeste fusionné Release 1.1.0 ne déclare que `MainActivity` comme activité VueConfort ; l’activité de recette `PublicMagnificationRecipeActivity` appartient exclusivement à Aperçu. La permission `WRITE_SECURE_SETTINGS` appartient exclusivement au manifeste Lab. Ces variantes ne sont pas des artefacts à soumettre.

## Recette utilisateur S25

Utiliser uniquement des valeurs et documents synthétiques. Préserver les réglages et profils existants ; le paquet Aperçu sert à isoler les essais. Aucune commande ADB ne constitue une fonction nécessaire au client. L’activation du service et les réglages Samsung passent par les écrans Android normaux.

| Étape | Action et résultat attendu | Preuve adaptée |
|---|---|---|
| 1–2 | Installer puis ouvrir le paquet commercial d’essai ; le premier écran propose les quatre départs, sans permission préalable. | Test de parcours réel, package et permissions |
| 3–4 | Essayer sans bilan, régler puis créer son profil ; aucune ordonnance n’est exigée. | Navigation réelle et profil enregistré |
| 5–6 | Modifier plusieurs réglages de l’Égaliseur, comparer Original, enregistrer ; effet immédiat limité à l’aperçu. | Callback du renderer et préférences enregistrées |
| 7 | Fermer/réouvrir l’activité ; retrouver les valeurs sans redemander la configuration. | Recréation de l’activité, puis relance du processus |
| 8–10 | Choisir un document synthétique photo/PDF ; vérifier OD/OG, SPH/CYL/AXE/ADD ; confirmer avant de l’utiliser. | OCR/import local et référence du bilan dans le profil |
| 11–12 | Ouvrir Mon affichage, comprendre l’usage du service, autoriser volontairement le grossissement. | Refus possible, écran Android, état relu |
| 13 | Utiliser la loupe existante ; agrandir puis revenir à l’état initial. | Recette publique hors instrumentation et restauration |
| 14–16 | Ouvrir le guidage Samsung puis revenir ; afficher l’état relu sans assimiler ouverture d’un écran et activation. | Navigation système et nouvelle observation |
| 17–18 | Affiner le profil ; appliquer puis restaurer les réglages natifs autorisés. | Persistance, journal de restauration, état exact relu |
| 19 | Supprimer le bilan synthétique et le profil d’essai ; conserver les autres outils et les réglages externes. | Tests de suppression et invalidation des références |
| 20 | Relancer l’application après suppression ; aucune ancienne donnée ne réapparaît. | Rechargement du stockage, résultat du lancement |

La confirmation logicielle du grossissement ne constitue pas une mesure d’amélioration de la vue. Le bénéfice optique personnel et l’apparence de certains traitements Samsung demandent une observation humaine distincte. Un arrêt/redémarrage du processus n’est pas un redémarrage complet du téléphone : consigner précisément lequel a été essayé.

## Couverture de non-régression

Les suites existantes couvrent le stockage et ses migrations, les références de bilan, l’import local, l’Égaliseur, les reçus de l’orchestrateur et les parcours Native Vision. Le nouveau test produit complète ces contrôles par une navigation entre les vrais écrans. La loupe est vérifiée dans le processus normal : l’instrumentation peut reconnecter le service d’accessibilité et fausser son état.

## Résultats vérifiés de l’intégration

- **Build final réussi** : tests JVM, lint Release, assemblage Aperçu et de ses tests, AAB Release et contrôle du manifeste commercial. Journal local : `/tmp/vueconfort-final-idempotent-build.log` (`BUILD SUCCESSFUL`, 1 min 16 s). L’AAB reste **non signé**.
- **207 tests JVM réussis**, zéro échec, erreur ou test ignoré, vérifiés dans les 22 rapports XML de `app/build/test-results/testReleaseUnitTest/`.
- **47 tests instrumentés distincts réussis sur S25**, après correction et nouvelle exécution des deux tests de parcours initialement en échec. Le décompte est effectué par classe et méthode, sans compter deux fois les répétitions :

| Campagne locale | Résultat | Contribution au total des succès distincts |
|---|---|---|
| `/tmp/vueconfort-product-device-tests.log` | 47 cas : 43 réussis, 2 échecs de parcours liés aux dates instables du profil par défaut, 2 ignorés faute de service prêt | 43 |
| `/tmp/vueconfort-product-final-device-tests.log` | 19 réussis : les 2 parcours corrigés, 16 contrôles de stockage rejoués et 1 nouveau contrôle des dates inconnues stables | +3, soit 46 |
| `/tmp/vueconfort-final-performance.log` | 1 contrôle de manipulation réelle de l’Égaliseur pendant 10 s réussi | +1, soit 47 |

Les deux cas instrumentés de grossissement restent comptés comme **ignorés**, sans être transformés en succès. Leur fonction a été vérifiée séparément par deux recettes publiques dans le processus normal de l’application, sans permission de développement :

- `outputs/vueconfort-final-integration/grossissement-public.json` : plan `APPLICABLE`, moteur `android-public`, grossissement plein écran à 1,8× `APPLIED_AUTO`, confirmation `READ_BACK_CONFIRMED` et activité confirmée par l’orchestrateur. Profil inchangé ; retour exact à l’état initial désactivé, facteur 1 et mode fenêtre ; aucune restauration en attente.
- `outputs/vueconfort-final-integration/loupe-habituelle.json` : commandes historiques de la loupe actives à 1,5× en mode fenêtre ; profils de loupe et d’Égaliseur inchangés ; restauration exacte de l’état initial.

Ces reçus sont des preuves locales de la session d’intégration, extérieures au dépôt. Ils attestent les états relus par Android. Le reçu de grossissement porte `physicalEffectObserved: NOT_RECORDED` : aucune nouvelle observation humaine de l’effet sur la dalle ni amélioration de la vue n’est déduite de ces contrôles.

La recette manuelle a également vérifié les huit choix neutres d’un ajustement fictif, le retour à l’Égaliseur avec ses valeurs conservées (taille 1,54×, contraste 1,20×), puis la modification/enregistrement de la netteté à 24 %. La suppression du bilan retire ses clés et son historique sans effacer ces préférences.

L’effacement global depuis les réglages a restauré le grossissement avant de vider le DataStore. Après relance du processus, l’écran de bienvenue revient ; seules les deux clés des modèles standard de lecteur/loupe sont recréées par la migration existante, aucune donnée personnelle ne revient. Le service Aperçu a été désactivé et aucun journal de restauration ne reste. Les données d’origine ont ensuite été remises en place à l’octet près sur le téléphone, sans les télécharger ; le PDF fictif a été retiré.

Un redémarrage complet du S25 est confirmé par le changement d’identifiant de démarrage. Avant relance de VueConfort, l’empreinte des données restaurées reste strictement identique. Preuves locales : `restauration-donnees.json`, `suppression-bilan-verifiee.json` et `redemarrage-s25.json` dans le même dossier de livrables. Les autres applications VueConfort, dont la loupe historique et Lab, n’ont pas été modifiées.

Le dernier correctif de grossissement a été vérifié dans l’interface : un choix de mode/facteur pendant l’arrêt confirme l’état relu sans envoyer une commande inutile ; ON/OFF conserve les préférences dormantes. Samsung s’ouvre par l’accessibilité, puis le chemin guidé mène à « Améliorations pour la vision → Contour Relumino » ; aucun réglage Samsung protégé n’a été modifié.

## Avant l’envoi Google Play

1. Configurer la clé d’envoi attendue par l’application Play, reconstruire et vérifier l’AAB signé. Confirmer dans la console que le code 4 est disponible ; sinon augmenter ce code avant de reconstruire. Installer ensuite le binaire final distribué par une piste de test.
2. Reporter le résultat de recette exact, les cas non exécutés et les appareils réellement couverts ; compléter les essais d’accessibilité, veille One UI et appareils annoncés.
3. Actualiser les captures et filmer la démonstration Accessibility avec information, refus, activation, grossissement et lecteur. Les médias 1.0.x restent des archives, pas la présentation certifiée de 1.1.0.
4. Compléter les formulaires Accessibility, Data Safety, public cible, classification et coordonnées depuis les documents préparés. Renseigner la déclaration Santé selon les fonctions de bilan/exercices et déterminer les exigences applicables avant soumission ; aucune exemption ni approbation n’est présumée. Joindre la démonstration Accessibility réelle.
5. Vérifier les URL déjà référencées, `https://vueconfort.fr/fr/confidentialite/` et `https://vueconfort.fr/en/privacy/` : accessibilité publique, contact et correspondance avec les politiques locales actualisées. Leur contenu en ligne n’a pas été revérifié dans cette mission. Toute mise à jour du site demande une publication distincte ; ces modifications Git ne la réalisent pas.
6. Vérifier l’expérience anglaise avant de l’annoncer comme entièrement traduite.

Les descriptions Store sont prêtes à relire dans `play-store-assets/texts/`. Les moteurs avancés restent conservés ; un moteur ne peut s’exécuter que si ses entrées, sa maturité autorisée et son transport sont valides. Aucun traitement de pixels global personnalisé non disponible n’est annoncé comme actif.
