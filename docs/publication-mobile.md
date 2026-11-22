# Publication mobile (Play Store, App Store)

Le workflow `.github/workflows/release-mobile.yml` construit, signe et envoie les apps.

| Déclencheur | Base visée | Android | iOS |
| --- | --- | --- | --- |
| Fusion sur `main` | Test (staging) | Piste « test interne » Play | TestFlight (testeurs internes) |
| Tag `v1.2.0` | Production | Production Play, déploiement progressif 10 % | TestFlight, puis soumission à Apple depuis App Store Connect |

Le job de production attend ton approbation (environnement GitHub `production`, « Required reviewers »).
Tant que les secrets d'un store manquent, son job est simplement sauté.

> **Avant le premier envoi : renommage en Ziko.** L'identifiant de l'app (`app.kiko` aujourd'hui, dans
> `androidApp/build.gradle.kts` et `iosApp/project.yml`) ne peut plus changer une fois l'app créée sur
> les stores. Il faut donc faire le renommage (identifiants, nom affiché, icônes) avant de créer les fiches.

## Mise en place, une fois

### Google Play (compte Play Console, 25 $ une fois)

1. Créer l'app dans la Play Console, activer **Play App Signing** (Google garde la clé de signature finale).
2. Générer la clé d'envoi (upload key), sur ton poste :
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
3. Faire le **premier envoi à la main** (l'API Play refuse de créer la toute première version) :
   un AAB signé avec cette clé, sur la piste de test interne.
4. Créer un compte de service Google Cloud avec le seul rôle « Release manager » sur cette app, et télécharger sa clé JSON.
5. Dans GitHub (Settings > Environments), pour `staging` **et** `production` :
   - secrets `ANDROID_UPLOAD_KEYSTORE_BASE64` (`base64 -i upload.jks`), `ANDROID_UPLOAD_KEYSTORE_PASSWORD`,
     `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` ;
   - variable `ANDROID_PACKAGE_NAME` (l'identifiant de l'app).
6. Ranger `upload.jks` et ses mots de passe dans un gestionnaire de mots de passe, puis l'effacer du poste.
   Perdue, la clé d'envoi se remplace via le support Play ; la clé finale reste chez Google.

### Apple (Apple Developer Program, 99 $ / an)

1. Créer l'identifiant d'app et la fiche dans App Store Connect.
2. App Store Connect > Users and Access > Integrations : créer une clé API avec le rôle **App Manager**.
3. Dans GitHub, pour `staging` et `production` :
   - secrets `ASC_API_KEY_P8` (contenu du fichier .p8), `ASC_KEY_ID`, `ASC_ISSUER_ID` ;
   - variable `APPLE_TEAM_ID`.
   La signature est automatique : Xcode crée certificats et profils avec la clé API, rien d'autre à stocker.

### Production

L'environnement `production` reçoit aussi `SUPABASE_URL` et `SUPABASE_PUBLISHABLE_KEY` du projet de prod
(déjà demandés par « Déploiement de la base ») : le build de prod les intègre à l'app.

## Ce que le pipeline garantit

- Les clés ne sont lisibles que par les jobs de leur environnement, et effacées du runner après usage.
- Version release minifiée par R8 (vérifiée à chaque PR par `bundleRelease` dans la CI).
- Trafic en clair interdit (`network_security_config.xml`), pas de sauvegarde cloud des données de l'app.
- Chaque AAB et binaire iOS reçoit une **attestation de provenance** signée, visible dans l'onglet
  Actions > Attestations et vérifiable avec `gh attestation verify <fichier> --repo rwmicro/kiko`.
- Déploiement Play progressif : à 10 %, on surveille les crashs dans la Play Console avant d'élargir.
  En cas de problème : « Arrêter le déploiement » dans la Play Console.
