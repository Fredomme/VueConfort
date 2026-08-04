# Signature Release de VueConfort

VueConfort utilise une configuration de signature locale optionnelle. Aucune clé ni aucun secret ne doit être ajouté au dépôt.

## 1. Créer la clé d’envoi

Créer un dossier privé hors du dépôt, puis exécuter par exemple :

```bash
keytool -genkeypair -v \
  -keystore /chemin/prive/vueconfort-upload.jks \
  -alias vueconfort-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Utiliser des mots de passe forts et uniques. Le nom, l’organisation et le pays décrivent l’éditeur; ils ne modifient pas le package Android.

## 2. Configurer le poste local

Copier `keystore.properties.example` vers `keystore.properties`, puis renseigner le chemin et les secrets :

```properties
storeFile=/chemin/prive/vueconfort-upload.jks
storePassword=SECRET
keyAlias=vueconfort-upload
keyPassword=SECRET
```

`keystore.properties`, `*.jks`, `*.keystore` et `keys/` sont ignorés par `.gitignore`. Sans ce fichier, Gradle produit des artefacts Release non signés utilisables pour contrôler R8, mais non publiables.

## 3. Play App Signing

À la création de l’application dans Play Console, activer Play App Signing. Google conserve alors la clé de signature d’application; la clé locale devient la clé d’envoi (« upload key »). Envoyer l’AAB signé par cette clé. Ne jamais partager la clé de signature Play.

## 4. Sauvegarde et récupération

Conserver au moins deux copies chiffrées de la clé d’envoi et des secrets, sur supports distincts, avec accès limité. Documenter l’alias et les responsables. En cas de perte ou compromission, suivre la procédure Play Console de réinitialisation de la clé d’envoi. Ne pas envoyer la clé par courriel ou messagerie.

## 5. Génération

```bash
./gradlew clean bundleRelease
```

Vérifier ensuite la signature avec `jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab` et tester l’AAB via une piste interne Google Play.
