# Loupe MediaProjection expérimentale

Prototype Debug uniquement (`fr.vueconfort.app.debug`). Il ajoute un troisième mode sans remplacer la loupe Android ni la loupe semi-statique `takeScreenshot()`.

La session est toujours initiée par l’utilisateur, après une divulgation VueConfort puis le consentement système. Une notification persistante permet pause, reprise et arrêt. Le flux n’existe qu’en mémoire GPU et n’est ni enregistré, ni transmis, ni analysé.

| Fonction | Release actuelle | Debug expérimental |
|---|---:|---:|
| Loupe Android | Oui | Oui |
| Loupe capture lente | Non | Oui |
| Loupe MediaProjection | Non | Oui |

État : preuve GPU validée sur SM-S931B, mais prototype non approuvé pour une publication Play.
