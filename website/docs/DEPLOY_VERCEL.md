# Déploiement Vercel

1. Importer le dépôt Git dans Vercel.
2. Définir `website` comme **Root Directory** si le dépôt contient l’application Android.
3. Vercel détecte Astro. Vérifier :
   - Build command : `npm run build`
   - Output directory : `dist`
   - Install command : `npm install`
   - Node.js : `22`
4. Déployer sans variable d’environnement.
5. Dans **Project Settings > Domains**, ajouter `vueconfort.fr` et `www.vueconfort.fr`.
6. Choisir `vueconfort.fr` comme domaine principal et rediriger `www` vers l’apex.
7. Appliquer les enregistrements DNS affichés par Vercel, puis vérifier TLS et les métadonnées sociales.

`vercel.json` applique les principaux en-têtes de sécurité. Aucun adaptateur Vercel n’est utile, car toutes les pages sont statiques.

Documentation officielle : [déployer Astro sur Vercel](https://docs.astro.build/en/guides/deploy/vercel/).
