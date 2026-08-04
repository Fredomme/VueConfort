# Checklist Google Play — VueConfort

## Binaire

- [x] AAB Release généré
- [x] Signature locale valide
- [x] Package `fr.vueconfort.app` vérifié
- [x] Version 1.0.0 / code 1 vérifiée
- [x] Lint Release sans erreur
- [x] Tests unitaires réussis
- [ ] Vérifier que le code de version 1 n’a jamais été importé dans Play Console
- [ ] Valider Play App Signing et la clé d’import

## Fiche Play Store

- [x] Icône Play Store 512 × 512 préparée
- [x] Sources d’icône applicative préparées
- [x] Feature Graphic 1024 × 500 préparée
- [ ] Remplacer l’icône système du binaire par l’icône applicative validée lors d’une prochaine version autorisée
- [x] Captures d’écran téléphone réelles en français (Galaxy S25 SM-S931B)
- [x] Texte court FR
- [x] Description courte FR
- [x] Description longue FR
- [x] Nouveautés FR
- [x] Texte court EN
- [x] Description courte EN
- [x] Description longue EN
- [x] Nouveautés EN
- [ ] Confirmer la catégorie Outils
- [ ] Confirmer les tags proposés

## Contenu et conformité

- [ ] Classification du contenu complétée
- [ ] Public cible complété
- [ ] Déclaration sur les annonces : aucune annonce
- [ ] Accès à l’application : aucun compte requis
- [ ] Data Safety complété selon `docs/DATA_SAFETY.md`
- [ ] Accessibility API déclarée
- [x] Script de vidéo Accessibility préparé
- [ ] Vidéo Accessibility filmée, téléversée et liée
- [x] Politique de confidentialité publique FR
- [x] Politique de confidentialité publique EN
- [ ] Coordonnées développeur vérifiées dans Play Console

## Tests et publication

- [ ] Testeurs internes ajoutés
- [ ] AAB importé dans la piste Test interne
- [ ] Déploiement du test interne
- [ ] Installation depuis Google Play sur Galaxy S25
- [x] Test local Release : onboarding, notification, service, overlay, grossissement, lecteur et profils
- [ ] Test verrouillage/déverrouillage et veille One UI
- [ ] Test fermé si requis pour l’accès à la production
- [ ] Publication production après validation
