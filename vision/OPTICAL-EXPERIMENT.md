# Essai optique de VueConfort Vision 0.2

Ce mode ajoute une précompensation fondée sur un modèle optique aux réglages de loupe existants. Il ne mesure pas la vue et ne constitue pas une ordonnance. Aucun profil visuel personnel n’est présumé dans cette version expérimentale.

## Ce qui est calculé

Une image fixe de 768 × 256 pixels physiques est convertie en niveaux de gris à partir de sa luminance linéaire sRGB. Le moteur modélise la réponse d’une pupille circulaire à une hypothèse de défocalisation sphérique, puis calcule une image susceptible de limiter ce flou dans le modèle. Le transfert inclut l’ouverture carrée des pixels et les répliques de fréquence qui restent dans la bande optique. Le résultat est borné, quantifié et réévalué après ces opérations.

Les essais A, B et C correspondent à des hypothèses de défocalisation de 0,15, 0,25 et 0,40 dioptrie. Ce sont des paramètres de simulation, **pas des valeurs attribuées à l’utilisateur**. Ils n’explorent ni tous les défauts visuels ni l’astigmatisme. La distance doit être choisie par l’utilisateur ; la pupille de 3 mm est une hypothèse. Le pas des pixels utilise l’estimation nominale du S25. Le modèle monochromatique à 555 nm et l’émission carrée idéalisée ne caractérisent pas le spectre ni les sous-pixels réels de l’écran OLED.

## Géométrie et limites

L’image optique est affichée sans agrandissement supplémentaire, dans un champ gris de code sRGB 188 avec au moins 128 pixels de marge. Dans une capture provenant de Vision, le moteur reçoit le centre de l’image agrandie neutre, avant les réglages de lumière, contraste et contours. La capture s’arrête lorsque l’essai s’ouvre. L’activité ne conserve pas cette image sur disque en utilisation normale.

Cette implémentation possède un nouveau domaine rectangulaire. Elle ne prétend pas hériter automatiquement de la qualification du précédent moteur V2.4 de 128 × 128 pixels. Deux tailles de calcul, 1 024 × 512 et 2 048 × 1 024, servent à vérifier l’effet des frontières numériques dans les deux axes. Le champ gris physique reste fini ; cette vérification numérique ne démontre pas que les conditions optiques réelles suivent exactement le modèle.

Le gain inverse est régularisé et plafonné. Une image qui dépasse les limites numériques ou n’améliore pas suffisamment l’erreur simulée est remplacée par l’original avec une explication. Aucune accentuation générique n’est substituée en secret. Sur les images déjà très contrastées, la saturation peut empêcher l’application du traitement.

## Comparaison avec l’utilisateur

L’original et le résultat utilisent la même taille et la même position. Les réponses « Mieux », « Pareil » et « Moins bien » enregistrent une préférence subjective dans cette application. Elles ne suffisent pas à établir une correction médicale ni à démontrer une amélioration durable de la lecture. Une préférence obtenue une seule fois doit être vérifiée par des essais répétés, idéalement en masquant le traitement, avec une distance stable et plusieurs contenus.

La version 0.2 traite une image fixe. Elle ne transforme pas encore toutes les applications en affichage optiquement corrigé en continu. Le fonctionnement du calcul, la fidélité des pixels affichés et l’amélioration de la vision sont trois validations distinctes.

## Sources de méthode et de limites

- Huang et al., *Eyeglasses-free Display: Towards Correcting Visual Aberrations with Computational Light Field Displays*, 2014 : https://www.computationalimaging.org/wp-content/uploads/2017/06/SIG2014-VisionCorrectingDisplay.pdf
- Le moteur mathématique antérieur V2.4 de VueConfort fournit les conventions et les seuils de départ ; ses fichiers historiques restent inchangés.

Le travail de 2014 distingue le préfiltrage sur écran ordinaire des écrans à champ lumineux comportant une modification optique matérielle. Les résultats de ces derniers ne prouvent pas qu’un programme seul sur le S25 puisse remplacer une paire de lunettes.
