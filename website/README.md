# Site officiel VueConfort

Site statique bilingue officiel de [vueconfort.fr](https://vueconfort.fr), construit avec Astro. Aucun backend, cookie, tracker, analytics, appel réseau client ou police externe.

## Pourquoi Astro

Astro pré-rend chaque route en HTML, n’envoie que le JavaScript nécessaire au thème, au menu et au formulaire `mailto:`, génère le sitemap et fonctionne sans adaptateur sur Cloudflare Pages, GitHub Pages et Vercel. Cette architecture réduit la surface de maintenance et privilégie SEO, rapidité et robustesse.

## Installation

Prérequis : Node.js 22.12 ou supérieur et npm.

```bash
npm install
npm run dev
```

Le serveur local indique l’URL à ouvrir. Pour le build de production :

```bash
npm run build
npm run preview
```

La sortie publiable se trouve dans `dist/`.

Pour Cloudflare Pages, le projet utilise le dossier racine `website`, la commande
`npm run build` et la sortie `dist`. `wrangler.jsonc` permet également un déploiement
direct du résultat compilé avec Wrangler.

## Architecture

- `src/content/site.ts` : contenu français/anglais et correspondance des routes;
- `src/layouts/BaseLayout.astro` : SEO, navigation, langues, thème et pied de page;
- `src/pages/[lang]/` : génération statique des pages localisées;
- `public/` : robots, manifeste, icônes, carte sociale et en-têtes Cloudflare;
- `docs/` : déploiement, DNS, production, SEO, performances et checklist.

Pour ajouter une langue, ajouter son code dans `languages`, ses routes, textes UI et contenus dans `src/content/site.ts`. Les routes, alternatives `hreflang` et sitemap suivent automatiquement.

## Liens des boutiques

Avant leur publication, les boutons Android et iPhone conduisent à la page officielle de téléchargement puis ouvrent un courriel d’information. Quand les URLs publiques existent, remplacer uniquement les deux liens dans `src/pages/[lang]/[slug].astro`, dans le bloc `isDownload`.

## Déploiement

- [Cloudflare Pages](docs/DEPLOY_CLOUDFLARE.md)
- [GitHub Pages](docs/DEPLOY_GITHUB_PAGES.md)
- [Vercel](docs/DEPLOY_VERCEL.md)
- [DNS vueconfort.fr](docs/DNS.md)
- [Mise en production](docs/PRODUCTION.md)

## Validation

`npm run build` exécute le contrôle TypeScript/Astro avant la génération. Aucune donnée personnelle ne doit être ajoutée aux sources, logs ou métadonnées.
