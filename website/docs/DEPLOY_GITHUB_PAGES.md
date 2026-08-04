# Déploiement GitHub Pages

Le workflow `.github/workflows/deploy.yml` compile et publie automatiquement le sous-dossier `website/`.

1. Publier le dépôt sur GitHub avec la branche `main`.
2. Dans **Settings > Pages**, choisir **GitHub Actions** comme source.
3. Pousser une modification dans `website/` ou lancer manuellement le workflow.
4. Le workflow installe Node 22, utilise `npm ci`, construit puis publie `website/dist`.
5. Dans **Settings > Pages > Custom domain**, saisir `vueconfort.fr` et activer **Enforce HTTPS** quand disponible.
6. Configurer le DNS selon `DNS.md`. Le fichier `public/CNAME` conserve le domaine lors des déploiements.
7. Faire rediriger `www.vueconfort.fr` vers le domaine canonique chez le fournisseur DNS ou via le registrar.

Le `site` Astro est déjà `https://vueconfort.fr`; aucun `base` n’est requis avec le domaine personnalisé. Si le site est publié temporairement sous `username.github.io/repository`, il faut adapter `site` et `base` avant ce déploiement temporaire, puis les remettre pour la production.

Documentation officielle : [déployer Astro sur GitHub Pages](https://docs.astro.build/fr/guides/deploy/github/).
