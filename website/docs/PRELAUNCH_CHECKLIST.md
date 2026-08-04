# Checklist avant mise en ligne

## Contenu

- [ ] Identité juridique et hébergeur final renseignés dans les mentions légales
- [ ] Adresses contact, support et privacy opérationnelles
- [ ] Textes FR et EN relus
- [ ] Limites médicales cohérentes sur toutes les pages
- [ ] URLs Google Play/App Store ajoutées après publication
- [ ] Politique conforme aux binaires réellement distribués

## Technique

- [ ] `npm ci` et `npm run build` réussissent
- [ ] Toutes les routes et la page 404 répondent
- [ ] `robots.txt` et `sitemap-index.xml` répondent
- [ ] Canonical, `hreflang`, OpenGraph et JSON-LD validés
- [ ] Favicons et manifeste chargés
- [ ] CSP et autres en-têtes présents sur l’hébergement retenu
- [ ] Aucun cookie, tracker, analytics ou appel distant ajouté

## Qualité

- [ ] Smartphone, tablette, ordinateur et 4K contrôlés
- [ ] Safari, Chrome, Firefox et Edge contrôlés
- [ ] Navigation clavier et focus visibles
- [ ] TalkBack ou VoiceOver contrôlé
- [ ] Zoom navigateur à 200 % et grandes polices contrôlés
- [ ] Modes clair, sombre et réduction des animations contrôlés
- [ ] Lighthouse mesuré sur production

## Domaine

- [ ] Apex et `www` ont un certificat valide
- [ ] `www` redirige en 301 vers l’apex
- [ ] HTTPS forcé
- [ ] SPF, DKIM et DMARC configurés pour le courriel
- [ ] Une seule plateforme répond comme origine de production
