# Shaders

Le shader central effectue en une passe : cadrage/zoom, étirement orienté, distorsion cylindrique légère, luminosité, contraste, gamma, saturation, température, réduction des blancs et intensité globale.

La préaccentuation utilise le résidu entre le pixel et quatre voisins, un seuil, une limite anti-halo et une régularisation. Une seconde composante compare deux échantillons le long de l’axe choisi pour une accentuation directionnelle prudente. Il ne s’agit pas d’une inversion de flou ni d’une correction cylindrique médicale.

Tous les paramètres sont bornés et envoyés comme uniforms. L’original et le rendu sont commutables instantanément avec A/B sur le même flux.
