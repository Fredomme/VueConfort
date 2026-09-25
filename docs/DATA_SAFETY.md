# Data Safety — inventaire factuel

Révision du 25 septembre 2026 pour la variante commerciale. Document de préparation : aucune réponse Play Console n’est soumise par ce commit.

| Donnée traitée | Stockage | Transmise hors appareil par VueConfort | Partagée par VueConfort | Effacement |
|---|---|---|---|---|
| Profils, préférences de l’égaliseur et de lecture | DataStore privé | Non | Non | Suppression/réinitialisation des profils |
| Demandes natives, recommandations, résultats et confirmations | Sous-ensemble du même profil privé | Non | Non | Suppression du profil |
| Capacités disponibles, modèle et version du téléphone | État local et profil privé | Non | Non | Suppression du profil/données de l’app |
| Anciennes valeurs d’affichage pour restauration | Journal privé distinct du profil | Non | Non | Restauration terminée ou choix explicite de conserver ; données de l’app |
| Valeurs confirmées du bilan, notes associées et historique | DataStore privé | Non | Non | Suppression du bilan et de son historique |
| Image/PDF sélectionné et texte OCR | Copie temporaire privée, aperçu et texte en mémoire | Non | Non | Nettoyage de l’import ; reprise au démarrage après interruption |
| Calibration et résultats d’exercices visuels/Amsler | Historique local | Non | Non | Suppression d’historique |
| Réponses de confort : tranche d’âge, lunettes, symptômes, usages | DataStore privé | Non | Non | Réinitialisation des données correspondantes |
| Positions, transparence et préférences de la loupe | DataStore privé | Non | Non | Réinitialisation des réglages |
| Règles : package d’application, horaire et seuil lumineux | DataStore privé | Non | Non | Suppression des règles |
| Niveau lumineux courant | Mémoire du service | Non | Non | Fin du service/processus |
| Texte et descriptions accessibles demandés avec Lire | Mémoire du service | Non | Non | Remplacement ou destruction du service/processus ; aucun fichier |
| État d’accueil | DataStore privé | Non | Non | Réinitialisation |

Les documents originaux choisis dans Android ne sont pas effacés par la suppression du bilan. Le nouvel import ne persiste ni URI permanent, ni document, ni texte OCR ni nom personnel du fichier ; seules les valeurs confirmées et un libellé de source générique sont conservés. Voir [import local](LOCAL_PRESCRIPTION_IMPORT.md).

## Réseau, capture et variantes

Le manifeste commercial exclut les permissions réseau, y compris celles apportées par les dépendances OCR. Aucun compte, SDK publicitaire, analytique d’écran ou serveur n’est ajouté. L’OCR du bilan utilise son modèle embarqué à la demande ; il ne lit pas en continu les applications affichées. Native Vision ne capture aucun écran.

Release et Aperçu n’ont aucun writer Secure de laboratoire. Lab possède un paquet, des sources et une permission de développement distincts ; son journal contient seulement les paramètres d’affichage nécessaires à la restauration. Ne pas utiliser ses diagnostics ou permissions pour renseigner la déclaration du binaire commercial.

## Conservation et restauration

`allowBackup=false` et les règles d’extraction excluent les données des sauvegardes/transferts Android. Supprimer un profil ne doit pas rendre impossible une restauration en attente : son journal reste privé jusqu’au choix prévu dans Mon affichage. Réinitialiser DataStore ne signifie donc pas restaurer les paramètres système ni effacer tous les journaux. Effacer les données Android ou désinstaller supprime normalement le stockage privé ; cela ne rétablit pas nécessairement les réglages natifs.

## Préparation Play Console

Vérifier les définitions et les réponses du formulaire contre le binaire final. « Traitement local » décrit ici le comportement technique ; ce n’est pas une dispense automatique des déclarations applicables. Les fonctions de bilan et tests visuels demandent aussi une analyse de la section Santé. Ne pas déclarer de chiffrement en transit pour une transmission inexistante. Voir [préparation Play](PLAY_STORE_READINESS.md), qui cite les règles officielles consultées.
