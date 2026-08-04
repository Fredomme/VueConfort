# SEO et performances

## SEO intégré

- titres et descriptions uniques par page et langue;
- canonical absolu sur `vueconfort.fr`;
- `hreflang` FR, EN et `x-default`;
- sitemap généré par Astro et robots explicite;
- OpenGraph et Twitter Card avec image locale;
- JSON-LD WebSite, Organization et FAQPage;
- structure sémantique, un H1 par page et liens HTML explorables;
- routes lisibles et trailing slash uniforme.

## Performances intégrées

- pré-rendu statique et HTML compressé;
- aucune police externe, image distante, tracker ou framework client hydraté;
- JavaScript minimal pour thème, menu et formulaire local;
- CSS unique, responsive et sans animation lourde;
- cache immuable des actifs `_astro`;
- aucune image de contenu bloquant le premier affichage.

## Contrôles avant chaque publication

Tester avec Lighthouse en navigation privée sur une URL de production : Performance ≥ 95, Accessibilité/SEO/Best Practices à 100. Vérifier aussi PageSpeed Insights mobile, Rich Results Test, Schema Validator, partage OpenGraph, absence d’erreur console et taille totale transférée. Un score dépend du réseau, de l’hébergeur et de l’outil; ne pas le déclarer avant mesure réelle.
