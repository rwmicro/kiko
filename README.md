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

1. Créer un projet sur [supabase.com](https://supabase.com), région UE.
2. Appliquer les migrations : `supabase link --project-ref <ref>` puis `supabase db push`.
3. Renseigner l'URL et la clé `anon` dans `SupabaseConfig.Default`
   (`composeApp/src/commonMain/kotlin/app/kiko/data/SupabaseProvider.kt`).
   La clé `anon` est publique par nature : la sécurité repose sur les politiques RLS.
   Ne jamais mettre la clé `service_role` dans l'app.

Tester les politiques sur un Postgres local (la CI fait la même chose à chaque PR qui touche `supabase/`) :

```bash
PGHOST=localhost PGUSER=postgres tools/ci/rls-tests.sh
```

Le script rejoue toutes les migrations sur des bases jetables, vérifie que chaque ligne marquée
« doit échouer » est bien refusée, rejoue les scénarios, puis contrôle le schéma
(RLS sur chaque table, fonctions `SECURITY DEFINER` avec `search_path`, rien d'écrivable sans connexion).

## Prochaines étapes du prototype

- [ ] Connexion (e-mail, Apple, Google) et création du profil
- [ ] Chat temps réel entre deux comptes, avec envoi hors ligne (comparer PowerSync et SQLDelight)
- [ ] Appel audio LiveKit entre un iPhone et un Android, avec CallKit
