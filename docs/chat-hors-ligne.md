# Chat hors ligne

Ce qu'on attend : écrire dans le métro, fermer l'app, et que les messages partent tout seuls dès que le
réseau revient, dans l'ordre et sans doublon. Rouvrir l'app sans réseau et retrouver ses discussions.

## PowerSync ou SQLDelight

|                                  | PowerSync                                                                                                                         | SQLDelight + file d'envoi maison                                                                                   |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Ce que c'est                     | Un service de synchronisation entre Postgres et un SQLite sur l'appareil, avec un SDK Kotlin Multiplatform                       | Une bibliothèque SQLite typée (requêtes SQL vérifiées à la compilation), rien côté serveur                         |
| Pièce en plus                    | Le service PowerSync, hébergé chez eux ou chez nous, branché sur la réplication de la base Supabase                              | Aucune                                                                                                             |
| Qui voit quels messages          | Les règles de synchronisation de PowerSync : une deuxième copie des règles RLS à tenir à jour, sinon fuite                        | Les règles RLS de Supabase, comme aujourd'hui : une seule source                                                    |
| Données personnelles             | Un sous-traitant de plus (messages privés), à déclarer dans le registre RGPD et la politique de confidentialité                   | Rien de nouveau : Supabase (UE) et l'appareil                                                                      |
| Envoi hors ligne                 | Fourni : file d'écritures locale, on écrit `uploadData` vers Supabase                                                           | À écrire : environ 150 lignes, simples parce que les messages ont déjà un identifiant créé par l'app (upsert idempotent) |
| Temps réel                       | Fourni par le service                                                                                                             | Déjà en place avec Supabase Realtime (PR #15)                                                                      |
| Plateformes                      | Android, iOS, JVM ; le web est expérimental et déconseillé en production                                                         | Android, iOS, JVM, web                                                                                             |
| Coût                             | Offre gratuite limitée, puis payant ; ou un serveur de plus à faire tourner                                                      | Gratuit                                                                                                            |
| Si on change d'avis              | Difficile : le schéma local et les règles vivent chez PowerSync                                                                    | Facile : tout est dans le dépôt                                                                                    |

**Choix : SQLDelight.** PowerSync règle un problème plus large que le nôtre (synchroniser beaucoup de tables
modifiables des deux côtés). Ici, les messages ne sont jamais modifiés après l'envoi, et chacun a déjà un
identifiant créé par l'app : un envoi rejoué est sans effet. Il suffit donc d'une file locale et d'un cache
des derniers messages. PowerSync ajouterait un service, un sous-traitant qui lit les messages privés, et une
deuxième copie des règles d'accès, contre la règle « collecter le minimum ».

À reconsidérer si un jour beaucoup de données sont modifiées hors ligne des deux côtés (fiches de
vocabulaire partagées, par exemple).

## Comment ça marche

- `composeApp/src/commonMain/sqldelight/app/kiko/db/Chat.sq` : le schéma local (liste des discussions,
  derniers messages, file d'envoi, fichiers en attente).
- `ChatCache` : l'interface du cache. `SqlChatCache` sur Android et iOS, `MemoryChatCache` pour l'aperçu
  web, la démo et les tests.
- `ChatStore` : chaque message écrit va d'abord dans la file de l'appareil, puis une boucle l'envoie.
  - Les messages partent dans l'ordre où ils ont été écrits, toutes discussions confondues.
  - Pas de réseau, délai dépassé, serveur en 5xx : le message reste « en attente », et la boucle réessaie
    après 2 s, 5 s, 10 s, 30 s, puis chaque minute. Elle repart tout de suite quand l'app revient au premier
    plan, ou quand le temps réel se reconnecte.
  - Refus du serveur (demande pas encore acceptée, conversation bloquée, fichier trop lourd) : le message
    est marqué « non envoyé », un toucher le remet dans la file. Il ne bloque pas les suivants.
  - Une photo ou un vocal est rangé dans le stockage avant le message ; le chemin suit l'identifiant du
    message, donc un envoi rejoué ne le duplique pas.
- Sans réseau, la liste et les 200 derniers messages de chaque discussion viennent de l'appareil, avec un
  bandeau « Hors ligne ».

## Ce que l'appareil garde, et pas plus

- La liste des discussions (prénom, fuseau, langue, dernier message, non-lus, série, fond).
  **Pas** la date de dernière connexion de l'autre : elle revient avec le réseau.
- Les 200 derniers messages envoyés de chaque discussion, autant que l'écran en charge.
- Les messages pas encore partis, et leur photo ou vocal, **seulement** jusqu'à leur envoi.
- **Pas** les photos et vocaux reçus : ils restent en mémoire le temps de la session, comme avant.

Effacé :

- à la déconnexion et à la suppression du compte (tout le cache) ;
- quand un autre compte se connecte sur le même appareil (tout le cache) ;
- quand une discussion disparaît côté serveur (refusée, bloquée, l'autre a supprimé son compte) :
  ses messages, y compris ceux en attente ;
- dès qu'un message en attente est parti : son fichier.

La base est dans le dossier privé de l'app. Android : exclue des sauvegardes (`allowBackup="false"`).
iOS : protégée par le chiffrement de l'appareil (classe par défaut). Pas de chiffrement en plus
(SQLCipher) pour l'instant : à ajouter si une analyse d'impact le demande.
