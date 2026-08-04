# Data Safety — inventaire factuel

Ce document prépare les réponses Play Console. La qualification juridique et les libellés exacts doivent être revérifiés contre le formulaire en vigueur au jour de la soumission.

| Donnée traitée | Stockage | Collectée hors appareil | Partagée | Transmise | Supprimable |
|---|---|---:|---:|---:|---:|
| Profils d’affichage et réglages optiques | DataStore privé local | Non | Non | Non | Oui |
| Position, transparence et état de l’overlay | DataStore privé local | Non | Non | Non | Oui |
| Calibration physique de l’écran | Historique local encodé | Non | Non | Non | Oui |
| Résultats de dépistage visuel et Amsler | Historique local encodé | Non | Non | Non | Oui |
| Réponses de confort (âge par tranche, lunettes, symptômes déclarés, usages) | DataStore privé local | Non | Non | Non | Oui |
| Règles d’automatisation, package d’application, heure et niveau lumineux choisi | DataStore privé local | Non | Non | Non | Oui |
| Niveau de luminosité ambiante courant | Mémoire du service | Non | Non | Non | Oui, à l’arrêt |
| Texte et descriptions accessibles demandés par Lire | Mémoire du service | Non | Non | Non | Oui, à fermeture/arrêt |
| État de fin d’accueil | DataStore privé local | Non | Non | Non | Oui |

## Réseau et tiers

Le manifeste ne demande pas `INTERNET`. Le code n’intègre ni compte, backend, SDK publicitaire, analytique, crash reporting externe ou achat. Aucune donnée n’est transmise à un tiers par VueConfort.

## Sauvegarde

`allowBackup=false`; les règles d’extraction excluent les données de la sauvegarde cloud et du transfert d’appareil. La suppression sélective est disponible dans l’application; la réinitialisation efface DataStore et la désinstallation supprime normalement le stockage privé.

## Play Console

Évaluer avec prudence la section Santé : les résultats de dépistage et symptômes sont traités localement mais ne quittent pas l’appareil. Les réponses « collecté » dans Play dépendent de la définition officielle en vigueur, notamment du traitement exclusivement sur appareil. Ne pas déclarer de chiffrement en transit puisqu’aucune transmission n’a lieu.
