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
- Les textes et leurs variantes (façon Duolingo, sans suivi des ouvertures) sont dans `_shared/copy.ts` et `_shared/variants.ts`.
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
   des discussions), puis `20261121010000_privacy.sql`. Elles activent `pg_net` et `pg_cron`, créent la
   file d'envoi, la suppression de compte et les tâches planifiées (envois, relances, ménage de nuit).
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
   | `BREVO_API_KEY` | la clé API de Brevo (étape 3.5) |
4. E-mail aux comptes inactifs depuis 2 ans (prévenus 30 jours avant d'être supprimés) :
   - brevo.com → compte gratuit (300 e-mails par jour, service français, données en Europe) ;
   - Expéditeurs et domaines → ajoute le domaine `ziko.help` et vérifie-le (enregistrements DNS
     fournis par Brevo), puis l'expéditeur `contact@ziko.help` ;
   - SMTP & API → Clés API → nouvelle clé, à ranger dans le secret `BREVO_API_KEY` (étape 3).
     **Elle est secrète.**
   Sans cette clé, aucun e-mail ne part et aucun compte inactif n'est supprimé.
5. Déploie les fonctions `push`, `reminders` et `delete-account` :
   - soit l'onglet Actions de GitHub → « Déployer les fonctions Edge » (secrets GitHub
     `SUPABASE_ACCESS_TOKEN` et `SUPABASE_PROJECT_REF` à créer une fois) ;
   - soit, en local : `supabase functions deploy push --no-verify-jwt` puis la même chose pour `reminders`
     et `delete-account`. Sans `delete-account`, le bouton « Supprimer mon compte » de l'onglet Moi échoue.

## Données gardées

Le reste de l'app (profil, discussions, signalements, comptes) suit les mêmes règles : voir
`supabase/migrations/20261121010000_privacy.sql`.

- `push_devices` : un jeton par téléphone connecté, supprimé à la déconnexion, quand Apple ou Google le
  disent périmé, ou après 90 jours sans ouvrir l'app.
- `push_outbox` : le contenu d'une notification est effacé dès l'envoi. Il reste la nature et la date
  (pour la règle « une relance par jour » et pour ne pas répéter un texte), 30 jours au plus.
- Aucune mesure des ouvertures, aucun outil d'analyse ou de publicité (Firebase sert seulement à l'envoi).
- Les textes passent par les serveurs de Google (Android) et d'Apple (iPhone), chiffrés en transit,
  comme pour toute app qui envoie des notifications.

## 4. Vérifier

- Android : installe l'APK de la CI, connecte-toi, accepte les notifications, puis demande à quelqu'un de t'écrire.
- Table `push_outbox` : chaque ligne a `sent_at` une fois partie ; `error` dit pourquoi un envoi a échoué.
- Table `push_devices` : un jeton par téléphone connecté.

## Tests

```sh
deno test --no-check supabase/functions/_shared      # textes, variantes, contenu des envois
# SQL : supabase/tests/notifications_scenario.sql et privacy_scenario.sql, sur une base locale
#       chargée avec les migrations
```
