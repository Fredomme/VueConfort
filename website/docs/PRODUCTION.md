# Guide de mise en production

## Préparation

1. Vérifier tous les textes FR/EN, les limites médicales et la politique de confidentialité contre les applications publiées.
2. Confirmer que `contact@`, `support@` et `privacy@vueconfort.fr` reçoivent les messages et disposent de SPF, DKIM et DMARC.
3. Ajouter les URLs Google Play et App Store uniquement lorsqu’elles sont publiques.
4. Renseigner l’identité juridique complète de l’éditeur et l’hébergeur effectivement choisi dans les mentions légales.
5. Valider l’usage de la marque, de la carte sociale et des icônes.

## Publication

1. Exécuter `npm ci` puis `npm run build` dans un environnement propre.
2. Contrôler les 28 routes localisées, la 404, le sitemap, robots, canonical et `hreflang`.
3. Déployer sur une URL de préproduction et tester mobile, tablette, ordinateur, écran large, clair/sombre, clavier et lecteur d’écran.
4. Associer `vueconfort.fr`, puis `www`; forcer HTTPS et la redirection canonique.
5. Tester les boutons de courriel et de téléchargement sans envoyer de données réelles.
6. Purger le cache seulement si les documents HTML ne se mettent pas à jour; les fichiers `_astro` sont immuables.

## Exploitation

Le site ne comporte ni base de données ni secret. Chaque modification passe par une revue du contenu et un build. Vérifier trimestriellement les liens externes et, à chaque version d’application, la confidentialité, le support, les compatibilités et les pages boutiques.
