# Publication Google Play — procédure

1. Créer et vérifier le compte Google Play Console de l’éditeur; compléter identité, coordonnées et profil de paiement si demandé.
2. Remplacer les icônes système temporaires par une identité validée : icône adaptative, ronde, foreground/background et icône Play 512 × 512.
3. Ajouter l’adresse de contact dans l’application et publier les politiques FR/EN sur une URL HTTPS publique stable.
4. Mettre à jour uniquement `VERSION_NAME` et `VERSION_CODE` dans `gradle.properties`. Chaque envoi Play exige un `VERSION_CODE` strictement supérieur.
5. Créer la clé d’envoi selon `RELEASE_SIGNING.md`, renseigner localement `keystore.properties`, sauvegarder la clé et ses secrets.
6. Générer `./gradlew bundleRelease`, vérifier la signature et tester le binaire Release sur Galaxy S25, Galaxy A53 et Android de référence.
7. Créer l’application Play avec le package immuable `fr.vueconfort.app`, nom VueConfort et langues français/anglais.
8. Activer Play App Signing et envoyer l’AAB signé par la clé d’envoi.
9. Préparer les descriptions courte/longue, catégorie, coordonnées, icône 512 × 512, feature graphic 1024 × 500 et captures téléphone localisées.
10. Compléter l’accès à l’application (aucun compte), la classification, le public cible, la présence éventuelle de contenu de santé et les autres déclarations exigées.
11. Compléter Data Safety à partir de `DATA_SAFETY.md` et vérifier les définitions Play au moment du dépôt.
12. Compléter la déclaration AccessibilityService API à partir de `ACCESSIBILITY_DECLARATION.md`; joindre justification, instructions et vidéo de démonstration.
13. Lier l’URL publique de confidentialité et vérifier sa cohérence avec le binaire.
14. Publier d’abord en test interne, installer depuis Play, vérifier onboarding, permissions, service, overlay, lecteur, profils, historique, veille One UI et FR/EN.
15. Corriger les rapports pré-lancement, puis utiliser la piste fermée selon les conditions de compte applicables.
16. Lancer progressivement en production seulement après validation fonctionnelle, conformité et surveillance des avis/crashs Play.

Ne pas envoyer l’APK debug. L’AAB Release signé est l’artefact de publication; l’APK Release sert aux contrôles locaux.
