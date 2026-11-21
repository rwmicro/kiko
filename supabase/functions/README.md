# Notifications, widgets et activités en direct : mise en route

Le code est prêt. Il manque des comptes et des clés, que **toi seul** dois créer et ranger. Ne les colle
jamais dans une discussion, un ticket ou un commit (sauf les identifiants Firebase de l'étape 2, qui
sont publics).

## Comment ça marche

```
message envoyé ──┐
                 ├─► push_outbox (file d'envoi) ──► fonction « push » ──► FCM ──► Android
tâche planifiée ─┘        (base Supabase)                           └──► APNs ──► iPhone
(toutes les 5 min : fonction « reminders »)
```

- Les règles (pas la nuit, une relance par jour, arrêt après 14 jours) sont dans la migration
  `supabase/migrations/20261121000000_notifications.sql`.
- Les textes et le choix des variantes (façon Duolingo) sont dans `_shared/copy.ts` et `_shared/bandit.ts`.
- Android reçoit des données et construit la notification lui-même (`androidApp/.../push/KikoNotifier.kt`).
- L'iPhone reçoit une alerte APNs, enrichie par `iosApp/KikoNotificationService`, ou un push
  d'activité en direct (iOS 17.2+).

## 1. Apple (iPhone) : compte développeur payant, 99 $ par an

Sans ce compte, l'iPhone n'a ni notifications, ni widgets qui lisent les données de l'app, ni îlot dynamique.

1. developer.apple.com → Certificates, Identifiers & Profiles → **Identifiers** :
   - `app.kiko` avec **Push Notifications**, **App Groups** (`group.app.kiko`),
     **Communication Notifications** et **Time Sensitive Notifications** ;
   - `app.kiko.widgets` et `app.kiko.notifications` avec **App Groups** (`group.app.kiko`).
2. **Keys** → nouvelle clé avec **Apple Push Notifications service (APNs)**. Télécharge le fichier `.p8`
   (une seule fois possible), note le **Key ID** et ton **Team ID** (en haut à droite du portail).

## 2. Firebase (Android)

1. console.firebase.google.com → nouveau projet (Analytics inutile).
2. Ajoute une app **Android** avec le nom de paquet `app.kiko`.
3. Paramètres du projet → Vos applications → recopie dans `androidApp/firebase.properties` :
   `app_id` (ID d'application), `api_key` (clé API Web), `project_id`, `sender_id` (numéro du projet).
   Ce sont des identifiants publics, ils peuvent être commités. Gitleaks risque de signaler la clé API :
   à toi de choisir entre une exception pour ce fichier ou des variables d'environnement
   `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` dans la CI.
4. Paramètres du projet → **Comptes de service** → « Générer une nouvelle clé privée » : un fichier JSON.
   **Celui-là est secret.**

## 3. Supabase

1. SQL Editor : exécute `supabase/migrations/20261121000000_notifications.sql` (après les migrations
   des discussions). Elle active `pg_net` et `pg_cron`, crée la file d'envoi et les tâches planifiées.
2. SQL Editor, une seule fois, avec l'URL de ton projet :
   ```sql
   select vault.create_secret('https://lescjpotipglrlpfqlsu.supabase.co', 'project_url');
   ```
   (Le secret partagé entre la base et les fonctions, `push_secret`, est créé par la migration.)
3. Edge Functions → **Secrets** :
   | Nom | Valeur |
   | --- | --- |
   | `FCM_SERVICE_ACCOUNT` | le contenu entier du JSON de l'étape 2.4 |
   | `APNS_KEY` | le contenu du fichier `.p8` (lignes BEGIN/END comprises) |
   | `APNS_KEY_ID` | le Key ID |
   | `APNS_TEAM_ID` | le Team ID |
4. Déploie les fonctions `push` et `reminders` :
   - soit l'onglet Actions de GitHub → « Déployer les fonctions Edge » (secrets GitHub
     `SUPABASE_ACCESS_TOKEN` et `SUPABASE_PROJECT_REF` à créer une fois) ;
   - soit, en local : `supabase functions deploy push --no-verify-jwt` puis la même chose pour `reminders`.

## 4. Vérifier

- Android : installe l'APK de la CI, connecte-toi, accepte les notifications, puis demande à quelqu'un de t'écrire.
- Table `push_outbox` : chaque ligne a `sent_at` une fois partie ; `error` dit pourquoi un envoi a échoué.
- Table `push_devices` : un jeton par téléphone connecté.

## Tests

```sh
deno test --no-check supabase/functions/_shared      # textes, bandit, contenu des envois
# SQL : supabase/tests/notifications_scenario.sql, sur une base locale chargée avec les migrations
```
