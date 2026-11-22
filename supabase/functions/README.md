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
   | `LIVEKIT_URL` | l'adresse du projet LiveKit, `wss://….livekit.cloud` (section « Appels audio ») |
   | `LIVEKIT_API_KEY` | la clé d'API LiveKit (section « Appels audio ») |
   | `LIVEKIT_API_SECRET` | le secret qui va avec (section « Appels audio »). **Secret.** |
4. E-mail aux comptes inactifs depuis 2 ans (prévenus 30 jours avant d'être supprimés) :
   - brevo.com → compte gratuit (300 e-mails par jour, service français, données en Europe) ;
   - Expéditeurs et domaines → ajoute le domaine `ziko.help` et vérifie-le (enregistrements DNS
     fournis par Brevo), puis l'expéditeur `contact@ziko.help` ;
   - SMTP & API → Clés API → nouvelle clé, à ranger dans le secret `BREVO_API_KEY` (étape 3).
     **Elle est secrète.**
   Sans cette clé, aucun e-mail ne part et aucun compte inactif n'est supprimé.
5. Déploie les fonctions `push`, `reminders`, `delete-account` et `livekit-token` :
   - soit l'onglet Actions de GitHub → « Déployer les fonctions Edge » (secrets GitHub
     `SUPABASE_ACCESS_TOKEN` et `SUPABASE_PROJECT_REF` à créer une fois) ;
   - soit, en local : `supabase functions deploy push --no-verify-jwt` puis la même chose pour `reminders`
     `delete-account` et `livekit-token`. Sans `delete-account`, le bouton « Supprimer mon compte » de l'onglet
     Moi échoue ; sans `livekit-token`, les appels ne se connectent pas.

## Appels audio (LiveKit, CallKit, Telecom)

```
« Appeler » ──► start_call ──► push_outbox (call_ring) ──► push VoIP (iPhone, CallKit sonne)
                    │                                 └──► FCM prioritaire (Android, sonnerie plein écran)
                    └──► fonction « livekit-token » ──► salle LiveKit « call-<id> », micro seulement
```

1. cloud.livekit.io → nouveau projet (offre gratuite suffisante pour la bêta). Choisis une région en
   Europe si on te le propose. Settings → **API Keys** → nouvelle clé : note l'URL `wss://…`, la clé et
   le secret, et range-les dans les secrets des fonctions Edge (étape 3). On peut aussi héberger LiveKit
   soi-même plus tard : seules ces trois valeurs changent.
2. Apple : sur l'identifiant `app.kiko`, rien de plus à cocher (le push VoIP utilise la même clé `.p8`
   que les notifications). Sans compte Apple Developer, l'iPhone ne reçoit pas les appels, mais peut appeler.
3. Android : rien à faire de plus que Firebase (étape 2). Sur Android 14 et plus, la sonnerie plein
   écran demande l'autorisation « Notifications plein écran » ; sans elle, l'appel arrive en bannière.
4. Google Play demandera, à la publication, pourquoi l'app utilise `USE_FULL_SCREEN_INTENT` et un service
   au premier plan de type « appel téléphonique » : réponse, « appels audio entre partenaires ».

Règles (dans `supabase/migrations/20261122000000_calls.sql`) : on n'appelle que dans une discussion
acceptée, jamais quelqu'un qu'on a bloqué ; pas de sonnerie entre 22 h et 8 h chez l'autre, sauf pour un
appel prévu ensemble ; 10 appels par heure au plus ; sans réponse au bout de 45 secondes, l'appel est manqué.

## Données gardées

Le reste de l'app (profil, discussions, signalements, comptes) suit les mêmes règles : voir
`supabase/migrations/20261121010000_privacy.sql`.

- `push_devices` : un jeton par téléphone connecté, supprimé à la déconnexion, quand Apple ou Google le
  disent périmé, ou après 90 jours sans ouvrir l'app.
- `push_outbox` : le contenu d'une notification est effacé dès l'envoi. Il reste la nature et la date
  (pour la règle « une relance par jour » et pour ne pas répéter un texte), 30 jours au plus.
- `calls` : qui a appelé qui, quand, et l'issue (décroché, refusé, manqué), 30 jours au plus, puis effacé.
  Le son passe par LiveKit, chiffré en transit, et n'est jamais enregistré. LiveKit ne reçoit que
  l'identifiant du compte, ni prénom ni e-mail. Les appels n'apparaissent pas dans l'historique
  « Récents » de l'iPhone, ni dans le journal d'appels d'Android.
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
# SQL : supabase/tests/notifications_scenario.sql, privacy_scenario.sql et calls_scenario.sql, sur une base locale
#       chargée avec les migrations
```
