# Référence optique conservée, raccord V2.4

Les quatre fichiers de `java/fr/vueconfort/app/precompensation/` sont des copies **octet pour octet** des sources de `work/vueconfort-commercial/project/app/src/debug/java/fr/vueconfort/app/precompensation/` dans le patrimoine local VueConfort. `SHA256SUMS` fixe leurs empreintes ; un test vérifie leur intégrité. Aucun seuil, modèle, régularisation, Fourier ou critère de décision n’a changé.

`OpticalModelV2.kt` est conservé entier pour réutiliser son `FourierReferenceV2` sans réécrire ni extraire une nouvelle variante scientifique. Son ancien commentaire « Debug-only » décrit son origine ; son ajout au source set partagé permet le raccord typé de V2.4, sans activité ou route UI scientifique. L’adaptateur a la maturité **EXPERIMENTAL**, explicitement interdite dans le contexte commercial.

L’adaptateur `OpticalV24VisionEngine` consomme uniquement un `OpticalV24Input` complet : contrat original, résidu de près explicite, pupille/géométrie/provenance, image fixe 128 × 128 en luminance linéaire et observation fraîche d’un affichage 1:1 dans le champ gris 512 × 512. Les entrées sont copiées, jamais modifiées. L’ordonnance ne devient pas automatiquement un résidu de près. Les profils clients actuels renvoient `UNAVAILABLE`, sans modèle synthétique injecté.

L’exécution ne choisit que la condition personnalisée `P` du moteur existant. Les contrôles génériques `G` et faux profil `S` ne constituent jamais un remplacement silencieux. Le résultat conserve APPLY / IDENTITY / REJECTED, les raisons et les diagnostics. Un calcul CPU ne prouve aucun affichage : aucun reçu de dessin n’est fabriqué et toute présentation doit encore vérifier le contexte, le profil et la surface réels. Il n’existe pas de capture globale ou de transport Display-Lens dans ce raccord.

Les preuves historiques restent dans `outputs/v24-capture/Bilan.md` et `outputs/v24-capture/tests-capture.xml` du patrimoine local. Elles ne qualifient pas un bénéfice humain, une nouvelle surface d’affichage ou une commercialisation de la précompensation.
