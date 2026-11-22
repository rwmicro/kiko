# Connexion avec Apple et Google

L'app propose trois façons de créer un compte ou de se connecter : Apple, Google et e-mail.
Les boutons sont sur « Crée ton compte » et sur « J'ai déjà un compte ».

## Ce qui se passe, par plateforme

| | Apple | Google |
| --- | --- | --- |
| iPhone, iPad | Fenêtre native (Face ID), jeton envoyé à Supabase | Fenêtre de connexion intégrée (ASWebAuthenticationSession), retour sur `kiko://login` |
| Android | Onglet du navigateur (Custom Tabs), retour sur `kiko://login` | Fenêtre native du Gestionnaire d'identifiants si `kiko.googleWebClientId` est renseigné, sinon onglet du navigateur |
| Aperçu web | Redirection | Redirection |

Le retour par le navigateur utilise PKCE : seul un code à usage unique passe dans le lien, jamais les jetons.

## Données gardées

On ne demande au fournisseur que l'e-mail. Apple n'envoie pas le nom (on ne demande pas cette autorisation).
Google envoie toujours nom et photo : la migration `20261121020000_social_sign_in.sql` les efface à l'écriture,
dans `auth.users` comme dans `auth.identities`, à chaque connexion. Seuls restent l'e-mail, l'identifiant
du compte chez le fournisseur (`sub`) et l'émetteur (`iss`).

Le prénom et la date de naissance (18 ans minimum) sont demandés dans l'app, à l'étape « Faisons connaissance »,
puis enregistrés seulement dans le profil. Un compte Apple ou Google sans profil au bout de 30 jours est supprimé
par le ménage de nuit (voir `20261121010000_privacy.sql`).

## Mise en place, une fois

### Supabase (projet de test, puis de production)

1. Authentication > URL Configuration > Redirect URLs : ajouter `kiko://login`.
2. Authentication > Sign In / Providers > **Google** : activer, puis
   - Client IDs : l'ID client « Application Web » (en premier), puis l'ID client iOS s'il existe ;
   - Client Secret : le secret du client « Application Web » ;
   - laisser « Skip nonce check » décoché.
3. Authentication > Sign In / Providers > **Apple** : activer, puis
   - Client IDs : l'ID de services (par exemple `app.kiko.signin`), puis `app.kiko` (identifiant du bundle iOS) ;
   - Secret Key : le secret généré à partir de la clé `.p8` (Supabase explique comment ; il expire tous les 6 mois).

### Google Cloud (console.cloud.google.com > API et services > Identifiants)

1. Écran de consentement OAuth : nom « Kiko », portées `openid` et `email` seulement.
2. ID client **Application Web** : URI de redirection autorisée
   `https://<projet>.supabase.co/auth/v1/callback`. Son ID va dans `kiko.googleWebClientId`
   (gradle.properties pour le test, `-Pkiko.googleWebClientId=…` en production). Ce n'est pas un secret.
3. ID client **Android** : paquet `app.kiko`, empreinte SHA-1 de la clé de signature
   (debug : `./gradlew :androidApp:signingReport` ; production : celle de Google Play > Intégrité de l'app).
   Sans lui, la fenêtre native Google répond « aucun identifiant ».

### Apple (developer.apple.com, une fois le compte développeur créé)

1. Identifiers > `app.kiko` : cocher **Sign in with Apple**.
2. Identifiers > Services IDs : créer `app.kiko.signin`, cocher Sign in with Apple, domaine
   `<projet>.supabase.co`, URL de retour `https://<projet>.supabase.co/auth/v1/callback`.
   C'est lui qui sert pour Android et l'aperçu web.
3. Keys : créer une clé avec Sign in with Apple, télécharger le `.p8` (une seule fois) pour générer le secret.

L'app iOS déclare déjà la capacité (`com.apple.developer.applesignin` dans `iosApp/project.yml`).

## Essayer

- Android : avec `kiko.googleWebClientId` vide, les deux boutons ouvrent un onglet et reviennent dans l'app.
- iPhone : Apple ne marche que sur un appareil ou un simulateur connecté à un compte Apple, avec l'app signée.
- Une erreur « Cette méthode de connexion n'est pas encore activée » veut dire que le fournisseur
  n'est pas activé dans Supabase.

## À faire ensuite

- Suppression de compte : Apple demande de révoquer le jeton Apple quand la personne supprime son compte
  (règle 5.1.1(v)). À ajouter dans la fonction Edge `delete-account`.
