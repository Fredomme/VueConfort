# Changelog

## Non publié

- distinction explicite entre la Loupe VueConfort Production, fondée sur le grossissement natif Android, et le prototype R&D par capture;
- isolation physique du prototype de capture dans les source sets Debug; aucun symbole `takeScreenshot` ou `custommagnifier` n’est destiné au binaire Release;
- conservation sans changement fonctionnel du service d’accessibilité, du grossissement natif, de l’overlay, des profils, du lecteur, de la notification et de la tuile rapide.

## 1.0.1 — test fermé Google Play

- intégration de l’icône officielle VueConfort dans l’application et la tuile rapide;
- onboarding de premier lancement centré sur l’activation volontaire de la loupe;
- action directe d’activation de la Loupe VueConfort depuis l’accueil;
- diffusion limitée à la piste Google Play de test fermé Alpha, sans disponibilité publique.

## 1.0.0 — 2026-08-04

### Fonctionnalités principales

- assistance visuelle par grossissement Android et barre d’accessibilité;
- lecteur local du texte accessible demandé par l’utilisateur;
- profils prédéfinis, personnalisés et règles automatiques;
- calibration de confort et bilan visuel STANDARDIZED_V2 avec historique;
- accueil guidé, aide, état du service, confidentialité et réinitialisation locale;
- interface française et ressources anglaises.

### Préparation Release

- versionnement centralisé;
- build Release optimisé par R8 et réduction des ressources;
- infrastructure de signature externe sans secret dans le projet;
- documents Play, Accessibility API, Data Safety, RGPD et licences.

### Limitations connues

- l’icône officielle VueConfort est intégrée depuis la version 1.0.1;
- une partie des écrans historiques conserve des textes français codés en dur;
- le fonctionnement final doit être validé physiquement sur Galaxy S25, Galaxy A53 et la version One UI ciblée;
- la clé d’envoi et les coordonnées de l’éditeur ne sont volontairement pas incluses.
