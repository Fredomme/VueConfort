# Publication Google Play — procédure

Procédure pour une future étape autorisée. L’AAB 1.1.0 préparé pendant l’intégration est non signé et ne peut pas être soumis en l’état. Aucun envoi n’est effectué ici.

1. Vérifier le compte Google Play Console de l’éditeur; compléter identité, coordonnées et profil de paiement si demandé. Créer un compte uniquement s’il n’existe pas déjà.
2. Vérifier l’identité déjà intégrée : icône adaptative, ronde, foreground/background et icône Play 512 × 512. Actualiser les captures historiques avec le parcours final 1.1.0.
3. Vérifier les contacts et les URL de confidentialité FR/EN déjà référencées ; comparer leur contenu aux politiques locales et publier séparément une mise à jour si nécessaire.
4. Mettre à jour uniquement `VERSION_NAME` et `VERSION_CODE` dans `gradle.properties`. Chaque envoi Play exige un `VERSION_CODE` strictement supérieur.
5. Retrouver la clé d’envoi attendue par l’application Play selon `RELEASE_SIGNING.md`, renseigner localement `keystore.properties`, sauvegarder la clé et ses secrets. Ne pas remplacer une clé existante par une nouvelle uniquement parce qu’elle manque sur ce poste.
6. Générer `./gradlew bundleRelease` (inclut `verifyCommercialReleaseManifest`), vérifier la signature et tester le binaire Release sur les appareils annoncés. Une recette Aperçu valide le code commercial dans un paquet séparé ; elle ne remplace pas l’installation du binaire final signé et distribué par Play.
7. Ouvrir la fiche de l’application au package immuable `fr.vueconfort.app`, nom VueConfort ; la créer uniquement si elle n’existe pas. Vérifier les langues annoncées et leur couverture réelle.
8. Vérifier la configuration Play App Signing et le certificat d’envoi attendus ; l’initialiser seulement pour une première inscription. Envoyer ensuite l’AAB signé par cette clé lors de l’étape de publication autorisée.
9. Préparer les descriptions courte/longue, catégorie, coordonnées, icône 512 × 512, feature graphic 1024 × 500 et captures téléphone localisées.
10. Compléter l’accès à l’application (aucun compte), la classification, le public cible, la présence éventuelle de contenu de santé et les autres déclarations exigées.
11. Compléter Data Safety à partir de `DATA_SAFETY.md` et vérifier les définitions Play au moment du dépôt.
12. Compléter la déclaration AccessibilityService API à partir de `ACCESSIBILITY_DECLARATION.md`; joindre justification, instructions et vidéo de démonstration.
13. Lier l’URL publique de confidentialité et vérifier sa cohérence avec le binaire.
14. Publier d’abord en test interne, installer depuis Play, vérifier onboarding, permissions, service, overlay, lecteur, profils, historique, veille One UI et FR/EN.
15. Corriger les rapports pré-lancement, puis utiliser la piste fermée selon les conditions de compte applicables.
16. Lancer progressivement en production seulement après validation fonctionnelle, conformité et surveillance des avis/crashs Play.

Ne pas envoyer l’APK debug. L’AAB Release signé est l’artefact de publication; l’APK Release sert aux contrôles locaux.
