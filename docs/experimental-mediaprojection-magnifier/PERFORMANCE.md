# Performance Galaxy S25

Appareil : Samsung SM-S931B, Android 16, 1080×2340. Profil testé : Équilibré, VirtualDisplay à 75 %, rendu OpenGL ES externe OES.

Preuve du 4 août 2026 : pendant cinq balayages Chrome, le compteur est passé de 300 à 600 frames en 2,519 s, soit environ 119 fps produits/rendus pendant la séquence active. Le seuil de faisabilité de 20 fps est dépassé. Une page statique ne produit pas continuellement des buffers; la moyenne depuis le début de session (2–4 fps) n’est donc pas une mesure de fluidité et a été écartée.

La boucle ne fait aucune allocation de bitmap ni copie GPU→CPU. Les emplacements shader et le tableau de coordonnées sont réutilisés. La latence visuelle, les essais continus 1/5/15 minutes, CPU, PSS, température, batterie et les profils 50/100 % restent à mesurer formellement avant validation produit. Aucun chiffre non mesuré n’est revendiqué.
