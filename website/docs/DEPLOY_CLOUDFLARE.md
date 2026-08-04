# Déploiement Cloudflare Pages

Cloudflare Pages est le choix recommandé pour `vueconfort.fr` : site statique, CDN mondial, TLS et gestion du domaine dans la même interface.

1. Placer le dossier `website/` dans un dépôt Git, ou sélectionner ce sous-dossier comme racine de projet.
2. Dans Cloudflare : **Workers & Pages > Create application > Pages > Connect to Git**.
3. Sélectionner le dépôt et configurer :
   - Framework preset : `Astro`
   - Root directory : `website` si le dépôt contient aussi l’application Android
   - Build command : `npm run build`
   - Build output directory : `dist`
   - Node.js : `22`
4. Déployer. Aucune variable d’environnement n’est requise.
5. Dans le projet Pages, ouvrir **Custom domains** et ajouter d’abord `vueconfort.fr`, puis `www.vueconfort.fr`.
6. Créer une Redirect Rule permanente `www.vueconfort.fr/*` vers `https://vueconfort.fr/$1` en conservant le chemin et la requête.
7. Vérifier le certificat, les en-têtes de sécurité, `robots.txt` et `sitemap-index.xml`.

Le fichier `public/_headers` configure la CSP, HSTS, la politique de permissions et le cache des actifs. Ne pas activer une injection Analytics, Web Analytics ou un outil de consentement : le site n’en a pas besoin.

Documentation officielle : [Astro sur Cloudflare Pages](https://docs.astro.build/en/guides/deploy/cloudflare/) et [domaines personnalisés Cloudflare Pages](https://developers.cloudflare.com/pages/configuration/custom-domains/).
