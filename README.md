# Kiko

App d'échange linguistique entre natifs (iPhone, iPad, Android, tablettes Android).
Bêta : **français ↔ indonésien**.

Stack : Kotlin Multiplatform + Compose Multiplatform, Supabase (Postgres, Auth, Storage, Realtime),
LiveKit pour les appels, API Claude côté serveur pour la traduction et l'aide à la correction.

## Structure

| Dossier | Contenu |
| --- | --- |
| `composeApp/` | Code partagé (interface Compose, logique, accès Supabase) et code propre à Android et iOS |
| `androidApp/` | Application Android (AGP 9 impose un module séparé du code partagé) |
| `iosApp/` | Application iOS. Le projet Xcode est décrit dans `project.yml` et généré par [XcodeGen](https://github.com/yonaskolb/XcodeGen) |
| `supabase/migrations/` | Schéma SQL de la base, avec les politiques Row Level Security |
| `supabase/tests/` | Test des politiques de sécurité sur un Postgres local |
| `.github/workflows/` | CI : build Android + tests, build iOS sur simulateur |

## Lancer le projet

**Android** (Linux, Windows ou Mac, JDK 17+, Android SDK 37) :

```bash
./gradlew :androidApp:assembleDebug          # APK de debug
./gradlew :composeApp:testAndroidHostTest    # tests du code partagé
```

Ou ouvrir le dossier dans Android Studio et lancer la configuration `androidApp`.
L'APK de chaque build est aussi téléchargeable dans l'onglet Actions de GitHub (artefact `kiko-debug-apk`).

**Aperçu web** (même interface, compilée en WebAssembly ; sans les fonctions natives) :

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # ouvre l'app dans le navigateur
```

La CI publie aussi la version web compilée (artefact `kiko-web-preview`).

**iOS** (Mac requis en local, sinon GitHub Actions) :

```bash
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

Sans Mac, la CI compile l'app iOS sur un runner macOS à chaque pull request.
L'envoi sur TestFlight sera ajouté quand le compte Apple Developer sera créé.

## Supabase

Deux projets Supabase séparés, région UE :

| Environnement | Projet | Qui y applique les migrations |
| --- | --- | --- |
| Test (staging) | `lescjpotipglrlpfqlsu` (l'ancien projet du prototype) | La CI, à chaque fusion sur `main` qui touche `supabase/migrations` |
| Production | à créer | La CI, sur un tag `v*`, après approbation dans GitHub |

**Les migrations ne se collent plus dans le SQL Editor.** On ajoute un fichier dans `supabase/migrations/`,
la PR le teste sur un Postgres jetable, puis le workflow « Déploiement de la base » l'applique.

L'app vise l'environnement de test par défaut (`gradle.properties`). Un build de production passe
`-Pkiko.env=prod -Pkiko.supabaseUrl=… -Pkiko.supabaseKey=…` ; la tâche `generateBuildEnv` refuse un build
de prod pointé sur la base de test, et toute clé secrète. Ne jamais mettre la clé `service_role` dans l'app.

**Mise en place, une fois** (Settings > Environments du dépôt) :

1. Environnement `staging` : variables `SUPABASE_PROJECT_REF`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` ;
   secrets `SUPABASE_ACCESS_TOKEN` (supabase.com > Account > Access Tokens) et `SUPABASE_DB_PASSWORD`.
2. Environnement `production` : les mêmes, avec les valeurs du projet de prod, et **Required reviewers** coché.
3. Variable de dépôt `STAGING_SUPABASE_URL` + secret `STAGING_SUPABASE_KEY` pour activer les scans de nuit.
4. Les migrations déjà collées à la main : lancer « Déploiement de la base » à la main avec
   `repair_until` = la dernière version déjà appliquée (par exemple `20261118030000`), pour que la CI ne les rejoue pas.

Supabase en local (Docker) : `supabase start`, puis `supabase db reset` pour rejouer migrations et `seed.sql`.

Tester les politiques sur un Postgres local :

```bash
psql -f supabase/tests/00_local_stub.sql -f supabase/migrations/*.sql -f supabase/tests/rls_prototype_test.sql
```

Chaque ligne marquée « doit échouer » dans le test doit afficher une erreur.

## Prochaines étapes du prototype

- [ ] Connexion (e-mail, Apple, Google) et création du profil
- [ ] Chat temps réel entre deux comptes, avec envoi hors ligne (comparer PowerSync et SQLDelight)
- [ ] Appel audio LiveKit entre un iPhone et un Android, avec CallKit
