# Bibliothèques open source

Inventaire établi à partir des dépendances déclarées dans `app/build.gradle.kts` pour VueConfort 1.0.0. Les dépendances transitives AndroidX suivent majoritairement la même licence; l’archive finale et les métadonnées Gradle doivent être revérifiées avant distribution.

| Bibliothèque | Version | Licence principale | Source de licence |
|---|---:|---|---|
| Android Gradle Plugin | 8.9.1 | Apache License 2.0 | Projet Android Open Source |
| Kotlin / plugin Android / plugin Compose | 2.1.20 | Apache License 2.0 | JetBrains Kotlin |
| Jetpack Compose BOM | 2024.12.01 | Apache License 2.0 | AndroidX |
| Activity Compose | 1.10.1 | Apache License 2.0 | AndroidX |
| Compose Material 3 | BOM | Apache License 2.0 | AndroidX |
| Compose Foundation | BOM | Apache License 2.0 | AndroidX |
| Compose UI, tooling preview | BOM | Apache License 2.0 | AndroidX |
| Lifecycle Runtime Compose | 2.8.7 | Apache License 2.0 | AndroidX |
| Lifecycle ViewModel Compose / KTX | 2.8.7 | Apache License 2.0 | AndroidX |
| Navigation Compose | 2.8.5 | Apache License 2.0 | AndroidX |
| DataStore Preferences | 1.2.1 | Apache License 2.0 | AndroidX |
| kotlinx-coroutines Android | 1.9.0 | Apache License 2.0 | JetBrains |
| JUnit 4 (tests seulement) | 4.13.2 | Eclipse Public License 1.0 | JUnit |
| kotlinx-coroutines-test (tests seulement) | 1.9.0 | Apache License 2.0 | JetBrains |

Les bibliothèques de test et `ui-tooling` ne sont pas embarqués comme fonctions du build Release. Aucune bibliothèque PDF, caméra, réseau, publicité ou analytique n’est déclarée. Conserver les avis Apache 2.0 applicables et vérifier le rapport de dépendances du binaire Release final si les versions changent.
