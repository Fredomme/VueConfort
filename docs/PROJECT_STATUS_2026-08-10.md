# VueConfort — État du projet au 10 août 2026

Ce document est la source de vérité synthétique pour l'état courant de VueConfort.

## Android

- Application : `fr.vueconfort.app`
- Version actuellement diffusée sur Google Play : **1.0.1**
- Version code : **2**
- Canal : **Test fermé — Alpha**
- Statut du canal : **Actif**
- Release 1.0.1 disponible pour les testeurs autorisés.
- Distribution publique Production : **pas encore ouverte**.

### Condition Google Play avant demande de Production

Le tableau de bord Play Console affiche actuellement :

- test fermé publié : **fait** ;
- minimum requis : **12 testeurs inscrits** ;
- testeurs actuellement comptabilisés par Google au 10/08/2026 : **2** ;
- durée requise : au moins **12 testeurs pendant 14 jours**.

La liste de diffusion `VueConfort Internal` contient actuellement **8 utilisateurs autorisés**. Une adresse présente dans cette liste n'est pas nécessairement un testeur inscrit : chaque testeur doit rejoindre effectivement le programme de test via le lien d'inscription Google Play avec le compte Google autorisé.

### Prochain objectif Play Console

1. Atteindre au moins 12 testeurs réellement inscrits (cible recommandée : 14–15 pour marge).
2. Maintenir au moins 12 testeurs pendant 14 jours conformément au critère affiché par Play Console.
3. Tester la build 1.0.1 réellement installée depuis Google Play.
4. Finaliser les déclarations et éléments de conformité Play Console restants.
5. Demander l'accès à la Production dès que Google déverrouille cette étape.

## Site officiel

- Domaine : `vueconfort.fr`
- Statut : **site public en production**.
- Site bilingue FR/EN.
- Pages/fonctions principales : fonctionnalités, tests visuels, profils, lecteur, bêta Android, blog, support, confidentialité, mentions légales, presse.
- Positionnement : confort de lecture et dépistage sur écran ; aucun diagnostic ni ordonnance ; ne remplace pas un professionnel de santé.
- Adresses prévues : `contact@vueconfort.fr`, `support@vueconfort.fr`, `privacy@vueconfort.fr`.

### À finaliser côté Web

- vérifier/finaliser les mentions légales avec les informations définitives ;
- vérifier le fonctionnement réel des adresses e-mail du domaine ;
- contrôler SPF, DKIM et DMARC ;
- terminer l'audit Cloudflare/HTTPS/redirections/en-têtes ;
- contrôler robots, sitemap, canonical, hreflang et indexation ;
- ajouter/remplacer le lien Google Play par la fiche publique lors de la mise en Production.

## Fonctionnalités Android déjà présentes

- onboarding et configuration guidée ;
- calibration ;
- questionnaire ;
- tests visuels standardisés ;
- historique ;
- profils visuels ;
- règles automatiques ;
- lecteur adapté ;
- service d'accessibilité ;
- grossissement Android ;
- panneau flottant ;
- tuile rapide ;
- paramètres, aide, état du système, confidentialité et informations de publication ;
- fonctionnement local privilégié et absence de compte utilisateur.

## Loupe officielle et prototype expérimental

La Loupe VueConfort officielle de la Release utilise le grossissement natif Android en mode fenêtre via `MagnificationController` et `MagnificationConfig`. Le prototype de loupe par capture utilisant `AccessibilityService.takeScreenshot()` est physiquement isolé dans le source set Debug et ne fait pas partie du binaire Release 1.0.1.

## Priorité projet

La priorité n'est plus d'ajouter de grosses fonctionnalités avant la sortie publique. La priorité est désormais :

**stabiliser → tester → conformité Play → atteindre les critères du test fermé → demander Production → publier.**

Les fonctionnalités expérimentales et évolutions importantes doivent être réservées à une version ultérieure afin de ne pas déstabiliser la Release actuellement en validation.
