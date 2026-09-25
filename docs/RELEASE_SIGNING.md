# Signature Release de VueConfort

VueConfort utilise une configuration de signature locale optionnelle. Aucune clé ni aucun secret ne doit être ajouté au dépôt.

État de l’intégration 1.1.0 : aucune configuration `keystore.properties` locale. L’AAB préparé est non signé, donc non soumissible. Les tests ne remplacent pas la configuration et la vérification de signature.

## 1. Retrouver la clé d’envoi, ou la créer pour une première inscription

Pour une application déjà enregistrée dans Play Console, utiliser sa clé d’envoi existante et vérifier son certificat dans la console. L’absence de clé sur ce poste ne signifie pas qu’il faut en créer une autre. Si la clé est perdue, suivre la récupération prévue par la console avant toute nouvelle soumission.

Uniquement si aucune clé d’envoi n’existe encore pour cette application, créer un dossier privé hors du dépôt, puis exécuter par exemple :

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
