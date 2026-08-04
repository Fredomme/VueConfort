# Audit Release final — VueConfort 1.0.0

Date : 4 août 2026

Légende : ✔ conforme dans le projet · ⚠ action externe ou validation restante · ✖ bloquant pour une publication professionnelle

| Domaine | État | Constat vérifié |
|---|---:|---|
| Release | ✔ | Build type dédié, R8 et réduction des ressources configurés; informations techniques visibles seulement avec `BuildConfig.DEBUG`. |
| Google Play | ⚠ | Procédure, confidentialité, Data Safety et déclaration Accessibility préparées; saisie Play Console et tests de piste restent externes. |
| Accessibilité | ✔ | Service déclaré avec permission système, finalité documentée, activation manuelle, aucune capture ou automatisation de saisie. |
| Privacy | ⚠ | Politique FR/EN cohérente avec le code local; coordonnées éditeur et URL HTTPS publique restent à ajouter. |
| SDK | ✔ | `minSdk 28`, `targetSdk 36`, `compileSdk 36`. Revalider l’exigence Play au jour du dépôt. |
| Manifest | ✔ | Une activité launcher, un service d’accessibilité, une tuile et un receiver interne; aucun provider ou composant inutilisé détecté. |
| Permissions | ✔ | Seule `POST_NOTIFICATIONS` est demandée; `BIND_ACCESSIBILITY_SERVICE` et `BIND_QUICK_SETTINGS_TILE` protègent les services. Pas d’Internet, caméra ou overlay applicatif. |
| AAB | ✔ | `bundleRelease` est configuré pour produire `app/build/outputs/bundle/release/app-release.aab`; l’artefact doit être régénéré après chaque changement de version. |
| Signature | ⚠ | Infrastructure externe prête; aucune vraie clé n’est incluse. Un AAB sans `keystore.properties` n’est pas publiable. |
| Version | ✔ | `1.0.0` / code `1`, centralisés dans `gradle.properties`. Incrémenter le code à chaque dépôt; utiliser 1.0.1/1.1.0/2.0.0 selon correctif/fonction/rupture. |
| Licences | ✔ | Dépendances déclarées inventoriées avec licences; recontrôler les transitives si Gradle évolue. |
| Internationalisation | ⚠ | Ressources françaises et anglaises présentes, mais des écrans historiques et commandes du service conservent des chaînes françaises codées en dur. |
| Icônes | ✖ | Le manifeste utilise encore `@android:drawable/ic_menu_view`; aucune icône adaptative, ronde ou identité finale n’existe. À remplacer avant envoi. |
| Captures/captions | ✖ | Captures, textes de fiche localisés, légendes et feature graphic ne sont pas présents; production graphique et validation éditoriale nécessaires. |
| Data Safety | ✔ | Inventaire technique créé : traitement local, aucune collecte hors appareil, aucun partage ni transmission. Réponses finales à confirmer selon les définitions Play du jour. |

## Qualité et risques

- Aucun `TODO`, `FIXME`, `HACK`, `println` ou `printStackTrace` actif détecté dans les sources principales.
- Les journaux techniques ne contiennent pas le texte extrait ni les résultats personnels détaillés.
- Overlay, retrait de fenêtres, grossissement, notification et extraction sont protégés contre les erreurs courantes.
- Les erreurs de lecture DataStore de type I/O produisent des valeurs sûres sans effacement automatique; les données encodées invalides sont ignorées élément par élément.
- Les ressources du service sont libérées à la désactivation/destruction; capteur et coroutines sont arrêtés.
- Aucune dépendance PDF, caméra, réseau, publicité, analytique ou crash reporting externe n’est déclarée.

## Conclusion

Le projet est préparé techniquement pour générer un APK et un AAB Release optimisés. La publication reste bloquée par l’icône temporaire et les éléments Play graphiques; elle exige aussi une vraie clé d’envoi privée, une URL publique de confidentialité, les coordonnées de l’éditeur, la fin de l’internationalisation historique et les essais physiques/pistes Play.
