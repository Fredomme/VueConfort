# DNS de vueconfort.fr

Le domaine canonique est `https://vueconfort.fr`. `https://www.vueconfort.fr` doit répondre en HTTPS puis rediriger en `301` vers l’apex en conservant chemin et paramètres.

## Cloudflare Pages recommandé

1. Ajouter `vueconfort.fr` comme zone Cloudflare et remplacer chez le registrar les serveurs de noms par ceux fournis par Cloudflare.
2. Associer `vueconfort.fr` au projet depuis **Pages > Custom domains**. Ne pas créer manuellement un CNAME avant cette association : Cloudflare avertit que cela peut produire une erreur 522.
3. Associer ensuite `www.vueconfort.fr`; Cloudflare crée ou demande le CNAME adapté.
4. Ajouter une Redirect Rule permanente de `www` vers l’apex.
5. Conserver SSL/TLS en mode Full et Always Use HTTPS.

## GitHub Pages ou Vercel

Utiliser exclusivement les valeurs DNS affichées par la plateforme au moment de l’association, car elles peuvent évoluer. Pour GitHub Pages, suivre les enregistrements apex officiels et le CNAME `www`; pour Vercel, appliquer l’A/ALIAS/CNAME demandé par le tableau de bord. Ne jamais maintenir deux hébergeurs actifs sur les mêmes noms.

## Vérification

- `https://vueconfort.fr/fr/` retourne 200;
- `https://www.vueconfort.fr/fr/` retourne une redirection permanente vers l’apex;
- aucun avertissement certificat sur apex ou `www`;
- le canonical reste `https://vueconfort.fr/...`;
- SPF, DKIM et DMARC sont configurés séparément chez le fournisseur de courriel pour `contact`, `support` et `privacy`.
