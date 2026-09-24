# Validation de VueConfort Vision 0.2

Version expérimentale du 24 septembre 2026, application indépendante `fr.vueconfort.vision`. Ces vérifications concernent le fonctionnement logiciel et son modèle optique. Elles ne démontrent ni amélioration de la vision humaine, ni correction personnelle, ni remplacement de lunettes.

## Logiciel

- 34 tests réussis : 12 pour la géométrie, 12 pour les réglages de pixels, 6 pour le moteur optique et 4 pour la transformée de Fourier rectangulaire.
- Compilation Debug réussie ; contrôle Android : 0 erreur, 25 avertissements (notamment textes français intégrés et direction de mise en page).
- Cas modéré : vrai candidat optique appliqué. Cas saturé : retour exact à l’original gris. Annulation, domaines autorisés et neutralité contrôlés.
- La copie publiée du code Android et des tests correspond à la version vérifiée sur le S25 ; les fichiers locaux de SDK, les clés et les captures privées ne sont pas versionnés.

## Calcul indépendant

L’audit utilise une intégration pupillaire différente de celle du moteur et les transformées NumPy :

- 144 points de transfert : écart maximal environ 1,10 × 10⁻⁸.
- Trois grilles complètes : écart maximal environ 8,74 × 10⁻⁸ ; composante continue, symétries et signe conservés.
- Huit rendus : décisions concordantes. Les rares différences de pixels sont limitées à un niveau de gris sur six pixels au total.
- Texte synthétique testé sur S25 : candidat reproduit exactement par la référence indépendante.

Voir [les résultats enregistrés](independent-results-0.2.json) et [les méthodes et la reproduction](../audit/README.md). Les résultats complets à 400 mm ne qualifient pas automatiquement tous les paramètres possibles ; les contrôles à 300 et 600 mm portent sur les points de transfert sélectionnés.

## Affichage physique sur Galaxy S25

- Image optique native de 768 × 256 pixels, sans redimensionnement supplémentaire, entourée du gris sRGB 188.
- Original, candidat et marge grise comparés à la capture de l’écran : aucun pixel différent.
- Centre de l’image neutre de la loupe transféré sans agrandissement supplémentaire ; les molettes de contraste et contours ne sont pas intégrées silencieusement à l’entrée optique.
- Partage d’écran arrêté lors du passage à l’essai.
- Calcul initial observé autour de 3,9 secondes, dont 2,8 secondes pour préparer le modèle. Ce délai ne représente pas un traitement optique en direct.
- Mode normal : aucune image écrite sur disque ; seules les préférences choisies par l’utilisateur peuvent être conservées. Les preuves techniques proviennent d’un mode explicitement activé sur des contenus de démonstration.

Voir le [compte rendu machine](device-verification-0.2.json). L’empreinte APK qui y figure identifie le binaire local testé ; une compilation sur une autre machine avec une autre clé Debug ne reproduit pas nécessairement cette empreinte.

## Limites

Image fixe monochrome, trois hypothèses sphériques, pupille supposée de 3 mm, distance déclarée et pas nominal du S25. Ni astigmatisme, ni colorimétrie OLED mesurée, ni profil clinique personnel ne sont qualifiés. La comparaison utilisateur reste à effectuer ; aucun avis subjectif n’a été inventé pendant les tests.
