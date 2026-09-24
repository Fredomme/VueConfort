# Import local d’un bilan visuel

Cette fonctionnalité appartient à l’application commerciale VueConfort (`app/`). VueConfort Vision (`vision/`) reste une expérience distincte : aucune ordonnance importée n’alimente son moteur de précompensation optique.

## Parcours

L’accueil donne accès à « Mon bilan visuel ». Une image JPEG, PNG ou WebP, ou une page de PDF est lue sur l’appareil. L’utilisateur peut agrandir la page, vérifier le texte reconnu, reprendre les valeurs proposées puis les corriger. Toute modification annule la confirmation. L’enregistrement du bilan et du profil de confort est atomique ; la calibration ne commence qu’après sa réussite.

Les seules propositions automatiques concernent des champs explicitement associés à OD/OG et SPH/CYL/AXE/ADD, ou un tableau dont les colonnes sont explicites. Les dispositions inconnues, valeurs répétées, nombres illisibles et prescriptions loin/près multiples ne sont pas choisis automatiquement. Un OCT, un champ visuel, une acuité seule ou une prescription de lentilles ne sont pas convertis en correction de lunettes.

La reconnaissance reste faillible. Elle ne fournit ni diagnostic ni mesure du défaut résiduel à la distance de l’écran. Les réglages proposés sont des points de départ de confort à comparer, sans bénéfice individuel garanti.

## Données et limites

- Modèle Latin ML Kit embarqué : `com.google.mlkit:text-recognition:16.0.1`.
- Le manifeste retire les permissions réseau transmises par les dépendances.
- Aucun document, URI permanent, texte OCR ou nom de fichier personnel n’est enregistré dans le bilan. Les valeurs confirmées et leur historique restent dans le stockage privé de l’application, avec sauvegardes Android désactivées.
- Le lecteur borne les fichiers à 20 Mio, les PDF à 20 pages et les bitmaps à 4 millions de pixels / 2 048 pixels de côté. Il utilise une copie temporaire privée, effacée avant OCR ; les orphelins éventuels sont nettoyés au démarrage et avant l’import suivant.
- Les PDF sont traités page par page. Une nouvelle page efface le brouillon précédent. Les pages ne sont pas fusionnées.
- La suppression retire le bilan actuel, son historique et les anciennes autorisations de documents associées. Les originaux choisis dans le sélecteur Android ne sont pas supprimés.

## Validation

Les tests unitaires couvrent les signes Unicode, virgules décimales, yeux distincts, colonnes réordonnées, champs ambigus, confirmation obligatoire et cohérence cylindre/axe. Les tests sur appareil utilisent uniquement des documents synthétiques ; ils couvrent le rendu PDF, l’OCR, les formats invalides et le nettoyage après annulation.

La variante `preview` utilise les sources commerciales avec l’identifiant `fr.vueconfort.app.preview`, sans remplacer les applications existantes. Ses tests instrumentés sont sélectionnés avec `-PcommercialPreviewTests`. Les sources et bibliothèques expérimentales de recherche ne font pas partie de cette variante.

Validation locale du port commercial : 46 tests unitaires release réussis, APK preview et APK de tests instrumentés compilés, lint release sans erreur. Les huit tests instrumentés sont livrés mais ne sont pas comptabilisés comme exécutés par cette compilation. Le manifeste fusionné des variantes release et preview ne contient aucune permission Internet ; la sauvegarde est désactivée et tous les domaines de transfert Android sont exclus.

Le workflow `commercial-android.yml` exécute les tests unitaires release, le lint release et compile les deux APK preview sur les changements de l’application ou de Gradle. Il n’exécute aucun test instrumenté : ces huit tests nécessitent un appareil ou un émulateur Android. Le workflow distinct de VueConfort Vision reste inchangé.

Validation de cette branche sur Samsung Galaxy S25 (Android 16) : huit tests instrumentés réussis, avec documents synthétiques uniquement. Le test PDF conserve le refus des libellés ambigus (par exemple 0G au lieu de OG). Cette validation technique ne constitue pas une preuve clinique ni une mesure de fiabilité sur toutes les ordonnances.
