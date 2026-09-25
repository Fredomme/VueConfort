# Checklist Google Play — VueConfort 1.1.0

Révision du 25 septembre 2026. Liste de préparation, aucune soumission effectuée. Les cases non cochées demandent une vérification du binaire final ou de la console ; elles n’effacent pas les validations historiques de 1.0.x, qui ne prouvent pas l’état courant.

## Préparation dans le dépôt

- [x] Version de travail 1.1.0 / code 4 centralisée dans `gradle.properties`
- [x] Package commercial `fr.vueconfort.app` conservé
- [x] Signature locale optionnelle documentée, secrets exclus du dépôt
- [x] Contrôle automatique du manifeste fusionné avant Release/AAB/lint
- [x] Textes Store FR/EN alignés sur les fonctions et leurs domaines réels
- [x] Déclarations Accessibility, Data Safety et politiques locales préparées
- [x] Script de vidéo Accessibility mis à jour, avec parcours de refus
- [x] Recette S25 de bout en bout définie dans `docs/PRODUCT_RELEASE_1_1_0.md`

## Vérification du binaire final

Consulter le rapport final de l’intégration pour la compilation et les tests exécutés. L’AAB 1.1.0 préparé est **non signé** : aucune clé d’envoi n’est configurée localement. Il n’est pas soumissible en l’état ; un AAB généré n’est ni signé ni accepté par Play.

- [ ] Configurer la clé d’envoi attendue par l’application Play, reconstruire puis vérifier l’AAB signé
- [ ] Confirmer que le code 4 n’a jamais été envoyé à Play
- [ ] Installer le binaire final signé depuis une piste de test
- [ ] Vérifier veille One UI, grandes polices, lecteur d’écran et appareils annoncés
- [ ] Vérifier les traductions de l’expérience anglaise

## Fiche et console

- [ ] Actualiser les captures 1.0.x avec les vrais écrans 1.1.0
- [ ] Vérifier l’icône et la Feature Graphic existantes dans les champs Play
- [ ] Confirmer catégorie, tags, classification et public cible
- [ ] Déclarer absence de compte et de publicité
- [ ] Compléter Data Safety et la déclaration Santé selon les fonctions présentes
- [ ] Compléter Accessibility API et joindre la vidéo réelle du parcours commercial
- [ ] Vérifier les URL FR/EN déjà référencées et les contacts ; publier séparément les politiques actualisées si leur version en ligne diffère
- [ ] Vérifier les exigences du compte et de la piste au moment du dépôt
- [ ] Soumettre uniquement après une instruction distincte de publication

Aucun statut de piste, d’acceptation d’AAB ou de Play App Signing n’a été consulté ni confirmé pendant cette intégration.
